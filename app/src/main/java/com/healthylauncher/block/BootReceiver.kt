package com.healthylauncher.block

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.healthylauncher.HealthyApp
import com.healthylauncher.domain.Mode
import com.healthylauncher.domain.SessionEvent
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as HealthyApp
        val pending = goAsync()
        app.scope.launch {
            try {
                app.repository.dispatch(SessionEvent.Tick)
                if (app.repository.session.value.mode == Mode.SOCIAL) {
                    app.alarms.scheduleSocial(app.repository.session.value.endsAtElapsed)
                    SessionService.start(app)
                }
                app.enforcer.reconcile()
            } finally {
                pending.finish()
            }
        }
    }
}
