package com.healthylauncher.block

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.healthylauncher.HealthyApp
import com.healthylauncher.domain.SessionEvent
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as HealthyApp
        val pending = goAsync()
        app.scope.launch {
            try {
                app.repository.dispatch(SessionEvent.Tick)
            } finally {
                pending.finish()
            }
        }
    }
}
