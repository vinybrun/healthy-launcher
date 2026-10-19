package com.healthylauncher.domain

enum class Mode { NORMAL, SOCIAL }

enum class EndReason { TIMER, EARLY, PHONE_BLOCKED }

data class SessionTiming(
    val socialDurationMs: Long = SOCIAL_DURATION_MS,
    val lockReturnMs: Long = LOCK_RETURN_MS,
    val warnRemainingMs: List<Long> = WARN_REMAINING_MS,
) {
    init {
        require(socialDurationMs > 0L)
        require(lockReturnMs > 0L)
        require(warnRemainingMs.all { it in 1 until socialDurationMs })
        require(warnRemainingMs.distinct().size == warnRemainingMs.size)
    }

    companion object {
        const val SOCIAL_DURATION_MS: Long = 30L * 60L * 1000L
        const val LOCK_RETURN_MS: Long = 5L * 60L * 1000L
        val WARN_REMAINING_MS: List<Long> = listOf(5L * 60L * 1000L, 60L * 1000L)
        val PRODUCTION = SessionTiming()
    }
}

data class SessionState(
    val mode: Mode = Mode.NORMAL,
    val endsAtElapsed: Long? = null,
    val blockedSinceElapsed: Long? = null,
    val warnedRemainingMs: Set<Long> = emptySet(),
    val openedPackages: Set<String> = emptySet(),
)

sealed class SessionEvent {
    data object StartSocial : SessionEvent()
    data object EndEarly : SessionEvent()
    data object Tick : SessionEvent()
    data object PhoneBlocked : SessionEvent()
    data object PhoneUnblocked : SessionEvent()
    data class AppOpened(val packageName: String) : SessionEvent()
}

sealed class SessionEffect {
    data object SocialStarted : SessionEffect()
    data class Warn(val remainingMs: Long) : SessionEffect()
    data class Ended(val reason: EndReason, val openedPackages: Set<String>) : SessionEffect()
}

fun reduceSession(
    state: SessionState,
    event: SessionEvent,
    nowElapsed: Long,
    timing: SessionTiming = SessionTiming.PRODUCTION,
): Pair<SessionState, List<SessionEffect>> {
    return when (event) {
        SessionEvent.StartSocial -> startSocial(state, nowElapsed, timing)
        SessionEvent.EndEarly -> end(state, EndReason.EARLY)
        SessionEvent.Tick -> tick(state, nowElapsed, timing)
        SessionEvent.PhoneBlocked -> onBlocked(state, nowElapsed, timing)
        SessionEvent.PhoneUnblocked -> onUnblocked(state)
        is SessionEvent.AppOpened -> onOpened(state, event.packageName)
    }
}

fun remainingMs(state: SessionState, nowElapsed: Long): Long {
    val ends = state.endsAtElapsed ?: return 0L
    return (ends - nowElapsed).coerceAtLeast(0L)
}

fun formatRemaining(remainingMs: Long): String {
    val sec = remainingMs.coerceAtLeast(0L) / 1000L
    val minutes = sec / 60L
    val seconds = sec % 60L
    return "%d:%02d".format(minutes, seconds)
}

fun warningCopy(remainingMs: Long): String = when (remainingMs) {
    5L * 60L * 1000L -> "5 minutes of social mode left"
    60L * 1000L -> "1 minute of social mode left"
    else -> "Social mode is almost over"
}

fun endCopy(reason: EndReason): String = when (reason) {
    EndReason.TIMER -> "Social mode is over"
    EndReason.EARLY -> "Social mode ended"
    EndReason.PHONE_BLOCKED -> "Social mode ended because the phone was locked"
}

/** Screen off counts as blocked. A lit lock screen counts as blocked. */
fun isPhoneBlocked(screenInteractive: Boolean, keyguardLocked: Boolean): Boolean {
    if (!screenInteractive) return true
    return keyguardLocked
}

private fun startSocial(
    state: SessionState,
    nowElapsed: Long,
    timing: SessionTiming,
): Pair<SessionState, List<SessionEffect>> {
    if (state.mode == Mode.SOCIAL) return state to emptyList()
    val next = SessionState(
        mode = Mode.SOCIAL,
        endsAtElapsed = nowElapsed + timing.socialDurationMs,
    )
    return next to listOf(SessionEffect.SocialStarted)
}

private fun end(
    state: SessionState,
    reason: EndReason,
): Pair<SessionState, List<SessionEffect>> {
    if (state.mode != Mode.SOCIAL) return state to emptyList()
    return SessionState() to listOf(SessionEffect.Ended(reason, state.openedPackages))
}

private fun tick(
    state: SessionState,
    nowElapsed: Long,
    timing: SessionTiming,
): Pair<SessionState, List<SessionEffect>> {
    if (state.mode != Mode.SOCIAL) return state to emptyList()
    val ends = state.endsAtElapsed ?: return end(state, EndReason.TIMER)
    if (nowElapsed >= ends) {
        return end(state, EndReason.TIMER)
    }
    val blockedSince = state.blockedSinceElapsed
    if (blockedSince != null && nowElapsed - blockedSince >= timing.lockReturnMs) {
        return end(state, EndReason.PHONE_BLOCKED)
    }
    val remaining = ends - nowElapsed
    val due = timing.warnRemainingMs
        .sortedDescending()
        .filter { threshold -> threshold !in state.warnedRemainingMs && remaining <= threshold }
    if (due.isEmpty()) return state to emptyList()
    val next = state.copy(warnedRemainingMs = state.warnedRemainingMs + due)
    return next to due.map { SessionEffect.Warn(it) }
}

private fun onBlocked(
    state: SessionState,
    nowElapsed: Long,
    timing: SessionTiming,
): Pair<SessionState, List<SessionEffect>> {
    if (state.mode != Mode.SOCIAL) return state to emptyList()
    val since = state.blockedSinceElapsed ?: nowElapsed
    return tick(state.copy(blockedSinceElapsed = since), nowElapsed, timing)
}

private fun onUnblocked(state: SessionState): Pair<SessionState, List<SessionEffect>> {
    if (state.mode != Mode.SOCIAL || state.blockedSinceElapsed == null) return state to emptyList()
    return state.copy(blockedSinceElapsed = null) to emptyList()
}

private fun onOpened(state: SessionState, packageName: String): Pair<SessionState, List<SessionEffect>> {
    if (state.mode != Mode.SOCIAL) return state to emptyList()
    if (packageName.isBlank() || packageName in state.openedPackages) return state to emptyList()
    return state.copy(openedPackages = state.openedPackages + packageName) to emptyList()
}
