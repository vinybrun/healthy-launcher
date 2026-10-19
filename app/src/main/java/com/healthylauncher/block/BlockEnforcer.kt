package com.healthylauncher.block

import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.admin.DevicePolicyManager
import android.app.usage.UsageEvents
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import com.healthylauncher.MainActivity
import com.healthylauncher.R
import com.healthylauncher.data.AppCatalog
import com.healthylauncher.data.LauncherRepository
import com.healthylauncher.domain.Appearance
import com.healthylauncher.domain.EndReason
import com.healthylauncher.domain.ShellCommands
import com.healthylauncher.domain.endCopy
import com.healthylauncher.domain.filterCloseable
import com.healthylauncher.domain.grayscaleDecision
import com.healthylauncher.domain.managedPackages
import com.healthylauncher.domain.packagesToClose
import com.healthylauncher.domain.packagesToSuspend
import com.healthylauncher.domain.warningCopy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class BlockEnforcer(
    private val context: Context,
    private val repository: LauncherRepository,
    private val catalog: AppCatalog,
) {
    private val admin = ComponentName(context, AdminReceiver::class.java)
    private val dpm = context.getSystemService(DevicePolicyManager::class.java)
    private val gate = Mutex()

    fun isDeviceOwner(): Boolean = dpm.isDeviceOwnerApp(context.packageName)

    suspend fun reconcile() = gate.withLock { reconcileLocked() }

    private suspend fun reconcileLocked() = withContext(Dispatchers.IO) {
        val snap0 = repository.snapshot(
            launchable = catalog.refresh().map { it.packageName }.toSet(),
            ime = currentImePackage(context),
            dialer = currentDialerPackage(context),
        )
        val snap = snap0.copy(protectedPackages = snap0.protectedPackages + inputMethodPackages(context))
        val home = isDefaultHome(context)
        val settings = repository.settings.value
        val managed = managedPackages(snap)
        if (home && settings.suspendPackages) {
            applyLists(suspend = packagesToSuspend(snap), unsuspend = managed - packagesToSuspend(snap))
        } else if (managed.isNotEmpty()) {
            applyLists(suspend = emptySet(), unsuspend = managed)
        }
        applyGrayscale(settings.appearance == Appearance.TEXT_GRAYSCALE, settings.grayscaleOwned)
    }

    suspend fun closeAndLock(opened: Set<String>) = gate.withLock {
        closeAndLockLocked(opened)
    }

    private suspend fun closeAndLockLocked(opened: Set<String>) = withContext(Dispatchers.IO) {
        val power = context.getSystemService(PowerManager::class.java)
        val wake = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "healthylauncher:close")
        wake.acquire(15_000L)
        try {
            runCatching { goHome(context) }
            val launchable = catalog.refresh().map { it.packageName }.toSet()
            val snap0 = repository.snapshot(launchable, currentImePackage(context), currentDialerPackage(context))
            val snap = snap0.copy(protectedPackages = snap0.protectedPackages + inputMethodPackages(context))
            val inCall = runCatching {
                context.getSystemService(android.telecom.TelecomManager::class.java)?.isInCall == true
            }.getOrDefault(false)
            val targets = filterCloseable(packagesToClose(snap, opened), inCall, currentDialerPackage(context))
            val activity = context.getSystemService(ActivityManager::class.java)
            for (packageName in targets) {
                runCatching { activity.killBackgroundProcesses(packageName) }
                if (repository.settings.value.shizuku && ShizukuShell.ready()) {
                    ShizukuShell.run(ShellCommands.forceStop(packageName))
                }
            }
            reconcileLocked()
        } finally {
            if (wake.isHeld) wake.release()
        }
    }

    private fun applyLists(suspend: Set<String>, unsuspend: Set<String>) {
        val owner = isDeviceOwner()
        if (owner) {
            setSuspended(unsuspend, false)
            setSuspended(suspend, true)
        }
        if (repository.settings.value.shizuku && ShizukuShell.ready()) {
            if (unsuspend.isNotEmpty()) ShizukuShell.run(ShellCommands.unsuspendPackages(unsuspend))
            if (suspend.isNotEmpty()) ShizukuShell.run(ShellCommands.suspendPackages(suspend))
        }
        if (!owner && suspend.isNotEmpty()) {
            Log.i(TAG, "suspend requested for ${suspend.size} apps; device owner and shizuku are unavailable")
        }
    }

    private fun setSuspended(packages: Set<String>, suspended: Boolean) {
        if (packages.isEmpty() || !isDeviceOwner()) return
        packages.chunked(50).forEach { chunk ->
            runCatching {
                dpm.setPackagesSuspended(admin, chunk.toTypedArray(), suspended)
            }.onFailure { error ->
                Log.w(TAG, "setPackagesSuspended failed", error)
            }
        }
    }

    private suspend fun applyGrayscale(want: Boolean, owned: Boolean) {
        val currently = Settings.Secure.getInt(context.contentResolver, DALTONIZER_ENABLED, 0) == 1
        val mode = Settings.Secure.getInt(context.contentResolver, DALTONIZER, -1)
        when (grayscaleDecision(want, currently, mode, owned)) {
            com.healthylauncher.domain.GrayscaleAction.LEAVE -> Unit
            com.healthylauncher.domain.GrayscaleAction.ENABLE -> {
                if (writeSecure(DALTONIZER_ENABLED, "1") && writeSecure(DALTONIZER, "0")) {
                    if (!owned) repository.updateSettings { it.copy(grayscaleOwned = true) }
                }
            }
            com.healthylauncher.domain.GrayscaleAction.DISABLE -> {
                if (writeSecure(DALTONIZER_ENABLED, "0")) {
                    repository.updateSettings { it.copy(grayscaleOwned = false) }
                }
            }
        }
    }

    private fun writeSecure(key: String, value: String): Boolean {
        if (context.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            val wrote = runCatching {
                Settings.Secure.putString(context.contentResolver, key, value)
            }.getOrDefault(false)
            if (wrote) return true
        }
        if (isDeviceOwner()) {
            val wrote = runCatching {
                dpm.setSecureSetting(admin, key, value)
                true
            }.getOrDefault(false)
            if (wrote) return true
        }
        if (repository.settings.value.shizuku && ShizukuShell.ready()) {
            return ShizukuShell.run("settings put secure $key $value") == 0
        }
        return false
    }

    private companion object {
        const val TAG = "healthy-launcher"
        const val DALTONIZER_ENABLED = "accessibility_display_daltonizer_enabled"
        const val DALTONIZER = "accessibility_display_daltonizer"
    }
}

class Notifier(private val context: Context) {
    private val notifications = context.getSystemService(NotificationManager::class.java)

    fun ensureChannels() {
        notifications.createNotificationChannel(
            NotificationChannel(CHANNEL_SOCIAL, "Social mode", NotificationManager.IMPORTANCE_LOW),
        )
        val warnings = NotificationChannel(CHANNEL_WARN, "Social mode warnings", NotificationManager.IMPORTANCE_HIGH)
        notifications.createNotificationChannel(warnings)
    }

    fun social(text: String): android.app.Notification {
        ensureChannels()
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, CHANNEL_SOCIAL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(open)
            .setOnlyAlertOnce(true)
            .build()
    }

    fun warn(remainingMs: Long) {
        ensureChannels()
        notifications.notify(
            WARN_ID,
            NotificationCompat.Builder(context, CHANNEL_WARN)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(context.getString(R.string.app_name))
                .setContentText(warningCopy(remainingMs))
                .setAutoCancel(true)
                .build(),
        )
    }

    fun ended(reason: EndReason) {
        ensureChannels()
        notifications.cancel(WARN_ID)
        notifications.notify(
            END_ID,
            NotificationCompat.Builder(context, CHANNEL_WARN)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(context.getString(R.string.app_name))
                .setContentText(endCopy(reason))
                .setAutoCancel(true)
                .build(),
        )
    }

    companion object {
        const val CHANNEL_SOCIAL = "social"
        const val CHANNEL_WARN = "warnings"
        const val SOCIAL_ID = 7
        const val WARN_ID = 8
        const val END_ID = 9
    }
}

fun foregroundPackage(events: UsageEvents): String? {
    val parsed = ArrayList<Pair<Int, String>>()
    val event = UsageEvents.Event()
    while (events.hasNextEvent()) {
        events.getNextEvent(event)
        val pkg = event.packageName ?: continue
        parsed += event.eventType to pkg
    }
    val resumed = listOf(UsageEvents.Event.MOVE_TO_FOREGROUND, UsageEvents.Event.ACTIVITY_RESUMED)
    return parsed.lastOrNull { it.first in resumed }?.second
}
