package com.healthylauncher.block

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.healthylauncher.HealthyApp
import com.healthylauncher.domain.shouldCancelNotification

class NotifBlocker : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val app = application as? HealthyApp ?: return
        if (!app.repository.settings.value.hideNotifications) return
        val snap = app.repository.snapshot(
            launchable = app.catalog.packages(),
            ime = currentImePackage(this),
            dialer = currentDialerPackage(this),
        ).let { it.copy(protectedPackages = it.protectedPackages + inputMethodPackages(this)) }
        if (shouldCancelNotification(sbn.packageName, snap, sbn.notification.category)) {
            cancelNotification(sbn.key)
        }
    }
}
