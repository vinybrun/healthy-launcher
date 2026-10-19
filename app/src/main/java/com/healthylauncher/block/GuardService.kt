package com.healthylauncher.block

import android.accessibilityservice.AccessibilityService
import android.app.usage.UsageStatsManager
import android.view.accessibility.AccessibilityEvent
import com.healthylauncher.HealthyApp
import com.healthylauncher.domain.launchBlocked
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class GuardService : AccessibilityService() {
    private val app by lazy { application as HealthyApp }
    private var watch: Job? = null

    override fun onServiceConnected() {
        watch = app.scope.launch {
            while (true) {
                delay(400L)
                val settings = app.repository.settings.value
                if (!settings.usageWatch || !settings.accessibility) continue
                val usm = getSystemService(UsageStatsManager::class.java) ?: continue
                val events = usm.queryEvents(System.currentTimeMillis() - 4_000L, System.currentTimeMillis())
                val foreground = foregroundPackage(events) ?: continue
                bounceIfBlocked(foreground)
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (!app.repository.settings.value.accessibility) return
        val type = event.eventType
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            type != AccessibilityEvent.TYPE_WINDOWS_CHANGED
        ) {
            return
        }
        val packageName = event.packageName?.toString() ?: return
        bounceIfBlocked(packageName)
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        watch?.cancel()
        super.onDestroy()
    }

    private fun bounceIfBlocked(packageName: String) {
        val snap = app.repository.snapshot(
            launchable = app.catalog.packages(),
            ime = currentImePackage(this),
            dialer = currentDialerPackage(this),
        ).let { it.copy(protectedPackages = it.protectedPackages + inputMethodPackages(this)) }
        if (launchBlocked(packageName, snap)) {
            performGlobalAction(GLOBAL_ACTION_HOME)
        }
    }
}
