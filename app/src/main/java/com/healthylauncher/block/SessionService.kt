package com.healthylauncher.block

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import com.healthylauncher.HealthyApp
import com.healthylauncher.domain.Mode
import com.healthylauncher.domain.SessionEvent
import com.healthylauncher.domain.formatRemaining
import com.healthylauncher.domain.isPhoneBlocked
import com.healthylauncher.domain.remainingMs
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SessionService : Service() {
    private val app by lazy { application as HealthyApp }
    private var loop: Job? = null
    private var receiverRegistered = false
    private val screen = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> onBlockedChanged(blocked = true)
                Intent.ACTION_SCREEN_ON,
                Intent.ACTION_USER_PRESENT,
                -> onBlockedChanged(blocked = phoneBlockedNow())
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(Notifier.SOCIAL_ID, app.notifier.social(currentText()))
        ensureLoop()
        return START_STICKY
    }

    override fun onDestroy() {
        loop?.cancel()
        if (receiverRegistered) {
            unregisterReceiver(screen)
            receiverRegistered = false
        }
        super.onDestroy()
    }

    private fun ensureLoop() {
        if (!receiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            }
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                registerReceiver(screen, filter, RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(screen, filter)
            }
            receiverRegistered = true
        }
        if (loop != null) return
        if (phoneBlockedNow()) onBlockedChanged(blocked = true)
        loop = app.scope.launch {
            while (true) {
                val session = app.repository.session.value
                if (session.mode != Mode.SOCIAL) break
                app.repository.dispatch(SessionEvent.Tick)
                if (app.repository.session.value.mode != Mode.SOCIAL) break
                val text = currentText()
                startForeground(Notifier.SOCIAL_ID, app.notifier.social(text))
                delay(1_000L)
            }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun onBlockedChanged(blocked: Boolean) {
        app.scope.launch {
            app.repository.dispatch(if (blocked) SessionEvent.PhoneBlocked else SessionEvent.PhoneUnblocked)
            val session = app.repository.session.value
            if (session.mode == Mode.SOCIAL && session.blockedSinceElapsed != null) {
                app.alarms.scheduleLock(session.blockedSinceElapsed)
            } else {
                app.alarms.cancelLock()
            }
        }
    }

    private fun phoneBlockedNow(): Boolean {
        val power = getSystemService(android.os.PowerManager::class.java)
        val keyguard = getSystemService(android.app.KeyguardManager::class.java)
        return isPhoneBlocked(power.isInteractive, keyguard.isKeyguardLocked)
    }

    private fun currentText(): String {
        val session = app.repository.session.value
        if (session.mode != Mode.SOCIAL) return "Social mode"
        val remaining = remainingMs(session, app.clock.elapsedRealtime())
        return "Social mode · ${formatRemaining(remaining)}"
    }

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, SessionService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SessionService::class.java))
        }
    }
}
