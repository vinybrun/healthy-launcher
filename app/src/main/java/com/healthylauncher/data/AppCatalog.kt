package com.healthylauncher.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import com.healthylauncher.domain.LaunchableApp
import com.healthylauncher.domain.sortedApps

class AppCatalog(private val context: Context) {
    @Volatile var apps: List<LaunchableApp> = emptyList()
        private set

    @Volatile private var loadedAt: Long = 0L

    fun refresh(): List<LaunchableApp> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        apps = sortedApps(
            resolved.map { info ->
                LaunchableApp(
                    packageName = info.activityInfo.packageName,
                    label = info.loadLabel(context.packageManager).toString().ifBlank { info.activityInfo.packageName },
                )
            }.distinctBy { it.packageName },
        )
        loadedAt = SystemClock.elapsedRealtime()
        return apps
    }

    fun current(): List<LaunchableApp> {
        if (apps.isEmpty() || SystemClock.elapsedRealtime() - loadedAt > 5_000L) refresh()
        return apps
    }

    fun packages(): Set<String> = current().map { it.packageName }.toSet()
}
