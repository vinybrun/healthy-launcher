package com.healthylauncher

import android.app.Application
import android.util.Log
import com.healthylauncher.block.AlarmScheduler
import com.healthylauncher.block.BlockEnforcer
import com.healthylauncher.block.Notifier
import com.healthylauncher.block.SessionService
import com.healthylauncher.block.ShizukuShell
import com.healthylauncher.clock.AdjustableClock
import com.healthylauncher.data.AppCatalog
import com.healthylauncher.data.LauncherRepository
import com.healthylauncher.domain.Mode
import com.healthylauncher.domain.SessionEffect
import com.healthylauncher.domain.SessionEvent
import com.healthylauncher.domain.warningCopy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import rikka.shizuku.Shizuku

class HealthyApp : Application() {
    lateinit var clock: AdjustableClock
        private set
    lateinit var repository: LauncherRepository
        private set
    lateinit var catalog: AppCatalog
        private set
    lateinit var enforcer: BlockEnforcer
        private set
    lateinit var alarms: AlarmScheduler
        private set
    lateinit var notifier: Notifier
        private set

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        clock = AdjustableClock()
        com.healthylauncher.block.ShizukuShell.install(this)
        repository = LauncherRepository(this, packageName, clock)
        catalog = AppCatalog(this)
        enforcer = BlockEnforcer(this, repository, catalog)
        alarms = AlarmScheduler(this, clock)
        notifier = Notifier(this)
        notifier.ensureChannels()
        observe()
        runBlocking { repository.load() }
        scope.launch {
            repository.dispatch(SessionEvent.Tick)
            if (repository.session.value.mode == Mode.SOCIAL) {
                alarms.scheduleSocial(repository.session.value.endsAtElapsed)
                SessionService.start(this@HealthyApp)
            }
            enforcer.reconcile()
        }
        listenForShizuku()
    }

    private fun observe() {
        scope.launch {
            repository.effects.collect { effect ->
                when (effect) {
                    SessionEffect.SocialStarted -> {
                        alarms.scheduleSocial(repository.session.value.endsAtElapsed)
                        SessionService.start(this@HealthyApp)
                        launch { enforcer.reconcile() }
                    }
                    is SessionEffect.Warn -> {
                        notifier.warn(effect.remainingMs)
                        val text = warningCopy(effect.remainingMs)
                        launch {
                            delay(8_000L)
                            repository.clearBannerIf(text)
                        }
                    }
                    is SessionEffect.Ended -> {
                        alarms.cancelAll()
                        notifier.ended(effect.reason)
                        launch {
                            enforcer.closeAndLock(effect.openedPackages)
                            SessionService.stop(this@HealthyApp)
                        }
                    }
                }
            }
        }
        scope.launch {
            repository.settings.drop(1).collect { enforcer.reconcile() }
        }
    }

    private fun listenForShizuku() {
        val listener = Shizuku.OnBinderReceivedListener {
            if (ShizukuShell.ready()) {
                scope.launch { enforcer.reconcile() }
            }
        }
        runCatching { Shizuku.addBinderReceivedListenerSticky(listener) }
            .onFailure { Log.d(TAG, "shizuku listener not installed") }
    }

    private companion object {
        const val TAG = "healthy-launcher"
    }
}
