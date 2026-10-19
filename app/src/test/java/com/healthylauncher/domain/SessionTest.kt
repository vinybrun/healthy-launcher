package com.healthylauncher.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionTest {
    private val timing = SessionTiming.PRODUCTION

    @Test
    fun productionWindowsAreThirtyMinutesFiveMinutesAndTheTwoWarnings() {
        assertEquals(30L * 60L * 1000L, SessionTiming.SOCIAL_DURATION_MS)
        assertEquals(5L * 60L * 1000L, SessionTiming.LOCK_RETURN_MS)
        assertEquals(listOf(5L * 60L * 1000L, 60L * 1000L), SessionTiming.WARN_REMAINING_MS)
    }

    @Test
    fun startSocialSetsAThirtyMinuteDeadline() {
        val (state, effects) = reduceSession(SessionState(), SessionEvent.StartSocial, nowElapsed = 1_000L)
        assertEquals(Mode.SOCIAL, state.mode)
        assertEquals(1_000L + timing.socialDurationMs, state.endsAtElapsed)
        assertNull(state.blockedSinceElapsed)
        assertTrue(state.warnedRemainingMs.isEmpty())
        assertEquals(listOf(SessionEffect.SocialStarted), effects)
    }

    @Test
    fun startWhileSocialDoesNotResetTheDeadline() {
        val (started, _) = reduceSession(SessionState(), SessionEvent.StartSocial, 0L)
        val (again, effects) = reduceSession(started, SessionEvent.StartSocial, 5_000L)
        assertEquals(started, again)
        assertTrue(effects.isEmpty())
    }

    @Test
    fun tickAtTheDeadlineEndsOnTheTimerWithoutAWarning() {
        val (started, _) = reduceSession(SessionState(), SessionEvent.StartSocial, 0L)
        val (state, effects) = reduceSession(started, SessionEvent.Tick, timing.socialDurationMs)
        assertEquals(SessionState(), state)
        assertEquals(listOf(SessionEffect.Ended(EndReason.TIMER, emptySet())), effects)
    }

    @Test
    fun tickOneMillisecondEarlyStaysInSocialMode() {
        val (started, _) = reduceSession(SessionState(), SessionEvent.StartSocial, 0L)
        val (state, effects) = reduceSession(started, SessionEvent.Tick, timing.socialDurationMs - 1L)
        assertEquals(Mode.SOCIAL, state.mode)
        assertTrue(effects.none { it is SessionEffect.Ended })
        assertEquals(
            listOf(SessionEffect.Warn(5L * 60L * 1000L), SessionEffect.Warn(60_000L)),
            effects,
        )
    }

    @Test
    fun fiveMinuteWarningFiresOnceAtExactlyFiveMinutesLeft() {
        val (started, _) = reduceSession(SessionState(), SessionEvent.StartSocial, 0L)
        val at = timing.socialDurationMs - 5L * 60L * 1000L
        val justBefore = reduceSession(started, SessionEvent.Tick, at - 1L)
        assertTrue(justBefore.second.isEmpty())
        val (warned, effects) = reduceSession(started, SessionEvent.Tick, at)
        assertEquals(listOf(SessionEffect.Warn(5L * 60L * 1000L)), effects)
        val (again, more) = reduceSession(warned, SessionEvent.Tick, at + 1_000L)
        assertEquals(warned.warnedRemainingMs, again.warnedRemainingMs)
        assertTrue(more.isEmpty())
    }

    @Test
    fun oneMinuteWarningFiresOnce() {
        val (started, _) = reduceSession(SessionState(), SessionEvent.StartSocial, 0L)
        val at = timing.socialDurationMs - 60_000L
        val (state, effects) = reduceSession(started, SessionEvent.Tick, at)
        assertEquals(
            listOf(SessionEffect.Warn(5L * 60L * 1000L), SessionEffect.Warn(60_000L)),
            effects,
        )
        assertEquals(setOf(5L * 60L * 1000L, 60_000L), state.warnedRemainingMs)
    }

    @Test
    fun jumpingFromSixMinutesToThirtySecondsFiresBothWarningsAndDoesNotEnd() {
        val (started, _) = reduceSession(SessionState(), SessionEvent.StartSocial, 0L)
        val (state, effects) = reduceSession(started, SessionEvent.Tick, timing.socialDurationMs - 30_000L)
        assertEquals(Mode.SOCIAL, state.mode)
        assertEquals(
            listOf(SessionEffect.Warn(5L * 60L * 1000L), SessionEffect.Warn(60_000L)),
            effects,
        )
    }

    @Test
    fun endingEarlyClearsTheSession() {
        var state = reduceSession(SessionState(), SessionEvent.StartSocial, 0L).first
        state = reduceSession(state, SessionEvent.AppOpened("com.example.social"), 10L).first
        val (next, effects) = reduceSession(state, SessionEvent.EndEarly, 20L)
        assertEquals(SessionState(), next)
        assertEquals(listOf(SessionEffect.Ended(EndReason.EARLY, setOf("com.example.social"))), effects)
    }

    @Test
    fun endingEarlyInNormalModeIsANoOp() {
        val (state, effects) = reduceSession(SessionState(), SessionEvent.EndEarly, 0L)
        assertEquals(SessionState(), state)
        assertTrue(effects.isEmpty())
    }

    @Test
    fun phoneBlockedStartsTheFiveMinuteWindowAndDoesNotResetIt() {
        val (started, _) = reduceSession(SessionState(), SessionEvent.StartSocial, 0L)
        val (blocked, effects) = reduceSession(started, SessionEvent.PhoneBlocked, 1_000L)
        assertTrue(effects.isEmpty())
        assertEquals(1_000L, blocked.blockedSinceElapsed)
        val (again, more) = reduceSession(blocked, SessionEvent.PhoneBlocked, 50_000L)
        assertEquals(1_000L, again.blockedSinceElapsed)
        assertTrue(more.isEmpty())
    }

    @Test
    fun fiveMinutesLockedReturnsToNormal() {
        val (started, _) = reduceSession(SessionState(), SessionEvent.StartSocial, 0L)
        val (blocked, _) = reduceSession(started, SessionEvent.PhoneBlocked, 10_000L)
        val justBefore = reduceSession(blocked, SessionEvent.Tick, 10_000L + timing.lockReturnMs - 1L)
        assertEquals(Mode.SOCIAL, justBefore.first.mode)
        val (ended, effects) = reduceSession(blocked, SessionEvent.Tick, 10_000L + timing.lockReturnMs)
        assertEquals(SessionState(), ended)
        assertEquals(listOf(SessionEffect.Ended(EndReason.PHONE_BLOCKED, emptySet())), effects)
    }

    @Test
    fun unlockCancelsTheLockWindowAndANewLockStartsOver() {
        val (started, _) = reduceSession(SessionState(), SessionEvent.StartSocial, 0L)
        val (blocked, _) = reduceSession(started, SessionEvent.PhoneBlocked, 1_000L)
        val (open, _) = reduceSession(blocked, SessionEvent.PhoneUnblocked, 200_000L)
        assertNull(open.blockedSinceElapsed)
        val (endedTooSoon, effects) = reduceSession(open, SessionEvent.Tick, 200_000L + timing.lockReturnMs)
        assertEquals(Mode.SOCIAL, endedTooSoon.mode)
        assertTrue(effects.none { it is SessionEffect.Ended })
        val (relocked, _) = reduceSession(open, SessionEvent.PhoneBlocked, 210_000L)
        val (still, _) = reduceSession(relocked, SessionEvent.Tick, 210_000L + timing.lockReturnMs - 1L)
        assertEquals(Mode.SOCIAL, still.mode)
        val (ended, endEffects) = reduceSession(relocked, SessionEvent.Tick, 210_000L + timing.lockReturnMs)
        assertEquals(listOf(SessionEffect.Ended(EndReason.PHONE_BLOCKED, emptySet())), endEffects)
    }

    @Test
    fun timerWinsWhenTheDeadlineAndTheLockExpireTogether() {
        val (started, _) = reduceSession(SessionState(), SessionEvent.StartSocial, 0L)
        val (blocked, _) = reduceSession(started, SessionEvent.PhoneBlocked, 0L)
        val (_, effects) = reduceSession(blocked, SessionEvent.Tick, timing.socialDurationMs)
        assertEquals(listOf(SessionEffect.Ended(EndReason.TIMER, emptySet())), effects)
    }

    @Test
    fun aRestoredSessionPastItsDeadlineEndsOnTheNextTick() {
        val restored = SessionState(mode = Mode.SOCIAL, endsAtElapsed = 500L)
        val (state, effects) = reduceSession(restored, SessionEvent.Tick, 500L)
        assertEquals(SessionState(), state)
        assertEquals(listOf(SessionEffect.Ended(EndReason.TIMER, emptySet())), effects)
    }

    @Test
    fun phoneBlockedAndUnblockedDoNothingInNormalMode() {
        val blocked = reduceSession(SessionState(), SessionEvent.PhoneBlocked, 1L)
        val open = reduceSession(SessionState(), SessionEvent.PhoneUnblocked, 1L)
        assertEquals(SessionState() to emptyList<SessionEffect>(), blocked)
        assertEquals(SessionState() to emptyList<SessionEffect>(), open)
    }

    @Test
    fun openedAppsAreTrackedOnlyDuringSocialMode() {
        val ignored = reduceSession(SessionState(), SessionEvent.AppOpened("com.example.a"), 0L).first
        assertTrue(ignored.openedPackages.isEmpty())
        val (started, _) = reduceSession(SessionState(), SessionEvent.StartSocial, 0L)
        val (opened, effects) = reduceSession(started, SessionEvent.AppOpened("com.example.a"), 1L)
        assertEquals(setOf("com.example.a"), opened.openedPackages)
        assertTrue(effects.isEmpty())
        val duplicate = reduceSession(opened, SessionEvent.AppOpened("com.example.a"), 2L).first
        assertEquals(setOf("com.example.a"), duplicate.openedPackages)
    }

    @Test
    fun remainingTimeFormatsByWholeSeconds() {
        assertEquals("30:00", formatRemaining(30L * 60L * 1000L))
        assertEquals("29:59", formatRemaining(30L * 60L * 1000L - 1_000L))
        assertEquals("5:00", formatRemaining(5L * 60L * 1000L))
        assertEquals("1:00", formatRemaining(60_000L))
        assertEquals("0:09", formatRemaining(9_999L))
        assertEquals("0:00", formatRemaining(0L))
        assertEquals("0:00", formatRemaining(-20L))
    }

    @Test
    fun warningAndEndCopyAreStable() {
        assertEquals("5 minutes of social mode left", warningCopy(5L * 60L * 1000L))
        assertEquals("1 minute of social mode left", warningCopy(60_000L))
        assertEquals("Social mode ended because the phone was locked", endCopy(EndReason.PHONE_BLOCKED))
        assertEquals("Social mode is over", endCopy(EndReason.TIMER))
        assertEquals("Social mode ended", endCopy(EndReason.EARLY))
    }

    @Test
    fun phoneBlockedMatrix() {
        assertTrue(isPhoneBlocked(screenInteractive = false, keyguardLocked = false))
        assertTrue(isPhoneBlocked(screenInteractive = false, keyguardLocked = true))
        assertTrue(isPhoneBlocked(screenInteractive = true, keyguardLocked = true))
        assertFalse(isPhoneBlocked(screenInteractive = true, keyguardLocked = false))
    }
}
