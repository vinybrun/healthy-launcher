package com.healthylauncher.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.healthylauncher.HealthyApp
import com.healthylauncher.domain.ChallengeRun
import com.healthylauncher.domain.SessionEvent
import com.healthylauncher.domain.attempt
import com.healthylauncher.domain.hideMemory
import com.healthylauncher.domain.startChallenge
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ChallengeViewModel(app: Application) : AndroidViewModel(app) {
    private val healthy = app as HealthyApp
    var run by mutableStateOf<ChallengeRun?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var now by mutableLongStateOf(0L)
        private set
    private var ticker: Job? = null

    fun begin() {
        if (run != null && run?.finished != true) return
        val kind = healthy.repository.settings.value.challenge
        val started = healthy.clock.elapsedRealtime()
        run = startChallenge(kind, seed = started, nowElapsed = started)
        error = null
        now = started
        ticker?.cancel()
        ticker = viewModelScope.launch {
            while (true) {
                now = healthy.clock.elapsedRealtime()
                delay(200L)
            }
        }
    }

    fun hide() {
        run = run?.let(::hideMemory)
    }

    fun submit(answer: String, onFinished: () -> Unit) {
        val current = run ?: return
        if (current.finished) return
        val result = attempt(current, answer, healthy.clock.elapsedRealtime())
        run = result.run
        error = result.error
        if (!result.finished) return
        viewModelScope.launch {
            healthy.repository.dispatch(SessionEvent.StartSocial)
            healthy.enforcer.reconcile()
            onFinished()
        }
    }

    fun cancel() {
        ticker?.cancel()
        run = null
        error = null
    }
}
