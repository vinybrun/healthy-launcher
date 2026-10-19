package com.healthylauncher.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChallengeTest {
    @Test
    fun drillsAreAFixedAmountOfWorkWithNoClockGate() {
        val steps = buildChallenge(ChallengeKind.DRILLS, seed = 1L)
        assertEquals(ChallengePolicy.DRILL_MATH, steps.count { it.kind == StepKind.MATH })
        assertEquals(ChallengePolicy.DRILL_MEMORY, steps.count { it.kind == StepKind.MEMORY })
        assertEquals(ChallengePolicy.DRILL_RETYPE, steps.count { it.kind == StepKind.RETYPE })
        assertEquals(10, steps.size)
        assertTrue(steps.all { it.durationMs == 0L })
        assertEquals(listOf(6, 7), steps.filter { it.kind == StepKind.MEMORY }.map { it.reveal!!.length })
    }

    @Test
    fun drillsAreDeterministicAndMathChecksOut() {
        assertEquals(buildChallenge(ChallengeKind.DRILLS, 42L), buildChallenge(ChallengeKind.DRILLS, 42L))
        assertNotEquals(buildChallenge(ChallengeKind.DRILLS, 1L), buildChallenge(ChallengeKind.DRILLS, 2L))
        buildChallenge(ChallengeKind.DRILLS, 7L).filter { it.kind == StepKind.MATH }.forEach { step ->
            assertEquals(step.expected, evalMathPrompt(step.prompt).toString())
        }
    }

    @Test
    fun finishingDrillsQuicklyStillUnlocks() {
        var run = startChallenge(ChallengeKind.DRILLS, seed = 3L, nowElapsed = 0L)
        var now = 1_000L
        while (!run.finished) {
            val step = run.current!!
            if (step.kind == StepKind.MEMORY && !run.memoryHidden) {
                run = hideMemory(run)
            }
            val answer = when (step.kind) {
                StepKind.MATH, StepKind.MEMORY, StepKind.RETYPE -> step.expected!!
                else -> error(step.kind)
            }
            val attempt = attempt(run, answer, now)
            assertNull(attempt.error)
            assertTrue(attempt.advanced)
            run = attempt.run
            now += 50L
        }
        assertTrue(now < 60_000L)
    }

    @Test
    fun wrongMathDoesNotAdvance() {
        val run = startChallenge(ChallengeKind.DRILLS, seed = 4L, nowElapsed = 0L)
        val attempt = attempt(run, "0", nowElapsed = 10L)
        assertEquals("Not quite", attempt.error)
        assertFalse(attempt.advanced)
        assertEquals(0, attempt.run.index)
    }

    @Test
    fun memoryMustBeHiddenBeforeItCanBeAnswered() {
        var run = startChallenge(ChallengeKind.DRILLS, seed = 5L, nowElapsed = 0L)
        while (run.current?.kind != StepKind.MEMORY) {
            run = attempt(run, run.current!!.expected!!, 10L).run
        }
        val early = attempt(run, run.current!!.expected!!, 20L)
        assertEquals("Hide the digits first", early.error)
        val hidden = hideMemory(run)
        val ok = attempt(hidden, hidden.current!!.expected!!, 30L)
        assertTrue(ok.advanced)
    }

    @Test
    fun retypeCollapsesWhitespaceAndKeepsCase() {
        val sentence = ChallengePolicy.SENTENCES.first()
        assertTrue(sentence.split(Regex("\\s+")).size >= 18)
        val step = ChallengeStep("retype", StepKind.RETYPE, sentence, expected = sentence)
        val run = ChallengeRun(ChallengeKind.DRILLS, listOf(step), startedAtElapsed = 0L)
        val padded = attempt(run, "  $sentence  ", 1L)
        assertTrue(padded.finished)
        val spaced = attempt(run, sentence.replace(" ", "  "), 1L)
        assertTrue(spaced.finished)
        val wrongCase = attempt(run, sentence.uppercase(), 1L)
        assertEquals("Type it exactly", wrongCase.error)
    }

    @Test
    fun mindfulBreathCannotFinishBeforeOneMinuteOnThatStep() {
        val run = startChallenge(ChallengeKind.MINDFUL, seed = 1L, nowElapsed = 0L)
        val afterUrge = attempt(run, "I want a break", nowElapsed = 10_000L)
        assertEquals(StepKind.BREATH, afterUrge.run.current?.kind)
        val early = attempt(afterUrge.run, "", nowElapsed = 10_000L + ChallengePolicy.MINDFUL_BREATH_MS - 1L)
        assertEquals("Stay with it", early.error)
        val done = attempt(afterUrge.run, "", nowElapsed = 10_000L + ChallengePolicy.MINDFUL_BREATH_MS)
        assertTrue(done.advanced)
        assertEquals(StepKind.RETYPE, done.run.current?.kind)
    }

    @Test
    fun proseRejectsShortOrEmptyAnswers() {
        val run = startChallenge(ChallengeKind.MINDFUL, seed = 1L, nowElapsed = 0L)
        assertEquals("Write a real answer", attempt(run, "hi", 1L).error)
        assertEquals("Write a real answer", attempt(run, "aaaaaaaaaaaa", 1L).error)
        assertEquals("Write a real answer", attempt(run, "123456789012", 1L).error)
        assertTrue(attempt(run, "I want quiet", 1L).advanced)
    }

    @Test
    fun journalWaitsOutTwoMinutesFromTheStart() {
        var run = startChallenge(ChallengeKind.JOURNAL, seed = 1L, nowElapsed = 0L)
        run = attempt(run, "I want to check one message from a friend", 1_000L).run
        run = attempt(run, "Reply to that message", 2_000L).run
        assertEquals(StepKind.WAIT, run.current?.kind)
        val early = attempt(run, "", ChallengePolicy.JOURNAL_WINDOW_MS - 1L)
        assertEquals("Stay until the time is up", early.error)
        val done = attempt(run, "", ChallengePolicy.JOURNAL_WINDOW_MS)
        assertTrue(done.finished)
    }

    @Test
    fun journalWaitIsInstantWhenWritingAlreadyTookTwoMinutes() {
        var run = startChallenge(ChallengeKind.JOURNAL, seed = 1L, nowElapsed = 0L)
        run = attempt(run, "I want to check one message from a friend", 100_000L).run
        run = attempt(run, "Reply to that message", 130_000L).run
        val done = attempt(run, "", 130_000L)
        assertTrue(done.finished)
    }

    @Test
    fun breathingIsASingleTwoMinuteStep() {
        val run = startChallenge(ChallengeKind.BREATHING, seed = 1L, nowElapsed = 5L)
        assertEquals(1, run.steps.size)
        assertEquals(ChallengePolicy.BREATHING_MS, run.current?.durationMs)
        assertFalse(attempt(run, "", 5L + ChallengePolicy.BREATHING_MS - 1L).advanced)
        assertTrue(attempt(run, "", 5L + ChallengePolicy.BREATHING_MS).finished)
    }

    @Test
    fun defaultsAreDrillsGrayscaleAndEveryBlockMethod() {
        val settings = LauncherSettings()
        assertTrue(settings.allowlist.isEmpty())
        assertEquals(Appearance.TEXT_GRAYSCALE, settings.appearance)
        assertEquals(ChallengeKind.DRILLS, settings.challenge)
        assertTrue(settings.accessibility)
        assertTrue(settings.suspendPackages)
        assertTrue(settings.shizuku)
        assertTrue(settings.hideNotifications)
        assertTrue(settings.usageWatch)
        assertEquals(
            setOf(
                Appearance.TEXT_GRAYSCALE,
                Appearance.TEXT_COLOR,
                Appearance.MONO_ICONS,
                Appearance.COLOR_ICONS,
            ),
            Appearance.entries.toSet(),
        )
        assertEquals(
            setOf(
                ChallengeKind.DRILLS,
                ChallengeKind.MINDFUL,
                ChallengeKind.JOURNAL,
                ChallengeKind.BREATHING,
            ),
            ChallengeKind.entries.toSet(),
        )
    }
}
