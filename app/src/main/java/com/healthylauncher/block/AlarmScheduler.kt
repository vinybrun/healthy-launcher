package com.healthylauncher.block

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.healthylauncher.clock.AdjustableClock
import com.healthylauncher.domain.SessionTiming
import com.healthylauncher.domain.realElapsed

class AlarmScheduler(
    private val context: Context,
    private val clock: AdjustableClock,
) {
    private val alarms = context.getSystemService(AlarmManager::class.java)

    fun scheduleSocial(endsAtElapsed: Long?) {
        if (endsAtElapsed == null) return
        schedule(TYPE_END, endsAtElapsed)
        val warn5 = endsAtElapsed - 5L * 60L * 1000L
        val warn1 = endsAtElapsed - 60_000L
        if (warn5 > clock.elapsedRealtime()) schedule(TYPE_WARN5, warn5)
        if (warn1 > clock.elapsedRealtime()) schedule(TYPE_WARN1, warn1)
    }

    fun scheduleLock(blockedSinceElapsed: Long) {
        schedule(TYPE_LOCK, blockedSinceElapsed + SessionTiming.LOCK_RETURN_MS)
    }

    fun cancelLock() {
        cancel(TYPE_LOCK)
    }

    fun cancelAll() {
        listOf(TYPE_END, TYPE_WARN5, TYPE_WARN1, TYPE_LOCK).forEach { cancel(it) }
    }

    fun schedule(type: String, domainElapsed: Long) {
        val trigger = realElapsed(domainElapsed, clock.offsetMs).coerceAtLeast(0L)
        val pending = pending(type)
        try {
            val exact = Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()
            if (exact) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, pending)
            } else {
                alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, pending)
            }
        } catch (error: SecurityException) {
            Log.w(TAG, "exact alarm denied for $type", error)
            runCatching { alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, pending) }
        }
    }

    private fun cancel(type: String) {
        alarms.cancel(pending(type))
    }

    private fun pending(type: String): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).putExtra(EXTRA_TYPE, type)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, requestCode(type), intent, flags)
    }

    companion object {
        const val TYPE_END = "end"
        const val TYPE_WARN5 = "warn5"
        const val TYPE_WARN1 = "warn1"
        const val TYPE_LOCK = "lock"
        const val EXTRA_TYPE = "type"
        private const val TAG = "healthy-launcher"

        private fun requestCode(type: String): Int = when (type) {
            TYPE_END -> 1
            TYPE_WARN5 -> 2
            TYPE_WARN1 -> 3
            TYPE_LOCK -> 4
            else -> 9
        }
    }
}
