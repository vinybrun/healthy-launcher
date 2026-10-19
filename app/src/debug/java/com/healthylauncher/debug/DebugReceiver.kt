package com.healthylauncher.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.healthylauncher.HealthyApp
import com.healthylauncher.domain.SessionEvent
import kotlinx.coroutines.launch

/** Debug builds only. Advances the app clock and runs the same tick the alarm uses. */
class DebugReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as HealthyApp
        val advance = intent.getLongExtra("advance_elapsed_ms", 0L)
        if (advance != 0L) app.clock.advance(advance)
        val pending = goAsync()
        app.scope.launch {
            try {
                if (intent.getBooleanExtra("blocked", false)) {
                    app.repository.dispatch(SessionEvent.PhoneBlocked)
                }
                app.repository.dispatch(SessionEvent.Tick)
            } finally {
                pending.finish()
            }
        }
    }
}
