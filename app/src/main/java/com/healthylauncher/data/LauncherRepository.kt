package com.healthylauncher.data

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.healthylauncher.clock.AdjustableClock
import com.healthylauncher.domain.Appearance
import com.healthylauncher.domain.BlockSnapshot
import com.healthylauncher.domain.ChallengeKind
import com.healthylauncher.domain.CriticalPackages
import com.healthylauncher.domain.LauncherSettings
import com.healthylauncher.domain.Mode
import com.healthylauncher.domain.SessionEffect
import com.healthylauncher.domain.SessionEvent
import com.healthylauncher.domain.SessionState
import com.healthylauncher.domain.reduceSession
import com.healthylauncher.domain.warningCopy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val Context.launcherStore by preferencesDataStore("launcher")

class LauncherRepository(
    context: Context,
    private val selfPackage: String,
    private val clock: AdjustableClock,
) {
    private val store = context.launcherStore
    private val mutex = Mutex()
    private val _settings = MutableStateFlow(LauncherSettings())
    private val _session = MutableStateFlow(SessionState())
    private val _banner = MutableStateFlow<String?>(null)
    private val _effects = MutableSharedFlow<SessionEffect>(extraBufferCapacity = 32)

    val settings: StateFlow<LauncherSettings> = _settings.asStateFlow()
    val session: StateFlow<SessionState> = _session.asStateFlow()
    val banner: StateFlow<String?> = _banner.asStateFlow()
    val effects: SharedFlow<SessionEffect> = _effects.asSharedFlow()

    suspend fun load() {
        val prefs = store.data.first()
        _settings.value = settingsFrom(prefs)
        _session.value = sessionFrom(prefs)
    }

    suspend fun dispatch(event: SessionEvent) {
        lateinit var emitted: List<SessionEffect>
        mutex.withLock {
            val (next, effects) = reduceSession(_session.value, event, clock.elapsedRealtime())
            if (next == _session.value && effects.isEmpty()) return
            _session.value = next
            persistSession(next)
            emitted = effects
            effects.filterIsInstance<SessionEffect.Warn>().lastOrNull()?.let { warn ->
                _banner.value = warningCopy(warn.remainingMs)
            }
            if (effects.any { it is SessionEffect.Ended }) _banner.value = null
        }
        emitted.forEach { _effects.emit(it) }
    }

    suspend fun updateSettings(block: (LauncherSettings) -> LauncherSettings) {
        mutex.withLock {
            val next = block(_settings.value)
            _settings.value = next
            store.edit { prefs -> writeSettings(prefs, next) }
        }
    }

    fun clearBannerIf(text: String) {
        if (_banner.value == text) _banner.value = null
    }

    fun snapshot(launchable: Set<String>, ime: String?, dialer: String?): BlockSnapshot {
        return BlockSnapshot(
            mode = _session.value.mode,
            allowlist = _settings.value.allowlist,
            launchable = launchable,
            protectedPackages = CriticalPackages.resolve(selfPackage, ime, dialer),
            selfPackage = selfPackage,
        )
    }

    private suspend fun persistSession(state: SessionState) {
        store.edit { prefs ->
            prefs[Keys.MODE] = state.mode.name
            if (state.endsAtElapsed == null) prefs.remove(Keys.ENDS_AT) else prefs[Keys.ENDS_AT] = state.endsAtElapsed
            if (state.blockedSinceElapsed == null) {
                prefs.remove(Keys.BLOCKED_SINCE)
            } else {
                prefs[Keys.BLOCKED_SINCE] = state.blockedSinceElapsed
            }
            prefs[Keys.WARNED] = state.warnedRemainingMs.joinToString(",")
            prefs[Keys.OPENED] = state.openedPackages
        }
    }

    private fun settingsFrom(prefs: androidx.datastore.preferences.core.Preferences): LauncherSettings {
        val appearance = prefs[Keys.APPEARANCE]?.let { runCatching { Appearance.valueOf(it) }.getOrNull() }
            ?: Appearance.TEXT_GRAYSCALE
        val challenge = prefs[Keys.CHALLENGE]?.let { runCatching { ChallengeKind.valueOf(it) }.getOrNull() }
            ?: ChallengeKind.DRILLS
        return LauncherSettings(
            allowlist = prefs[Keys.ALLOW] ?: emptySet(),
            appearance = appearance,
            challenge = challenge,
            accessibility = prefs[Keys.ACCESSIBILITY] ?: true,
            suspendPackages = prefs[Keys.SUSPEND] ?: true,
            shizuku = prefs[Keys.SHIZUKU] ?: true,
            hideNotifications = prefs[Keys.HIDE_NOTIFICATIONS] ?: true,
            usageWatch = prefs[Keys.USAGE] ?: true,
            grayscaleOwned = prefs[Keys.GRAYSCALE_OWNED] ?: false,
        )
    }

    private fun sessionFrom(prefs: androidx.datastore.preferences.core.Preferences): SessionState {
        val mode = if (prefs[Keys.MODE] == Mode.SOCIAL.name) Mode.SOCIAL else Mode.NORMAL
        if (mode != Mode.SOCIAL) return SessionState()
        val warned = prefs[Keys.WARNED].orEmpty().split(',').mapNotNull { it.toLongOrNull() }.toSet()
        return SessionState(
            mode = Mode.SOCIAL,
            endsAtElapsed = prefs[Keys.ENDS_AT],
            blockedSinceElapsed = prefs[Keys.BLOCKED_SINCE],
            warnedRemainingMs = warned,
            openedPackages = prefs[Keys.OPENED] ?: emptySet(),
        )
    }

    private fun writeSettings(prefs: MutablePreferences, settings: LauncherSettings) {
        prefs[Keys.ALLOW] = settings.allowlist
        prefs[Keys.APPEARANCE] = settings.appearance.name
        prefs[Keys.CHALLENGE] = settings.challenge.name
        prefs[Keys.ACCESSIBILITY] = settings.accessibility
        prefs[Keys.SUSPEND] = settings.suspendPackages
        prefs[Keys.SHIZUKU] = settings.shizuku
        prefs[Keys.HIDE_NOTIFICATIONS] = settings.hideNotifications
        prefs[Keys.USAGE] = settings.usageWatch
        prefs[Keys.GRAYSCALE_OWNED] = settings.grayscaleOwned
    }

    private object Keys {
        val ALLOW = stringSetPreferencesKey("allow")
        val APPEARANCE = stringPreferencesKey("appearance")
        val CHALLENGE = stringPreferencesKey("challenge")
        val ACCESSIBILITY = booleanPreferencesKey("accessibility")
        val SUSPEND = booleanPreferencesKey("suspend")
        val SHIZUKU = booleanPreferencesKey("shizuku")
        val HIDE_NOTIFICATIONS = booleanPreferencesKey("hide_notifications")
        val USAGE = booleanPreferencesKey("usage")
        val GRAYSCALE_OWNED = booleanPreferencesKey("grayscale_owned")
        val MODE = stringPreferencesKey("mode")
        val ENDS_AT = longPreferencesKey("ends_at")
        val BLOCKED_SINCE = longPreferencesKey("blocked_since")
        val WARNED = stringPreferencesKey("warned")
        val OPENED = stringSetPreferencesKey("opened")
    }
}
