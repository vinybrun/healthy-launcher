package com.healthylauncher.domain

import kotlin.random.Random

enum class ChallengeKind { DRILLS, MINDFUL, JOURNAL, BREATHING }

enum class StepKind { MATH, MEMORY, RETYPE, PROSE, BREATH, WAIT }

data class ChallengeStep(
    val id: String,
    val kind: StepKind,
    val prompt: String,
    val expected: String? = null,
    val reveal: String? = null,
    val minChars: Int = 0,
    val durationMs: Long = 0L,
)

data class ChallengeRun(
    val kind: ChallengeKind,
    val steps: List<ChallengeStep>,
    val startedAtElapsed: Long,
    val index: Int = 0,
    val stepStartedAtElapsed: Long = startedAtElapsed,
    val memoryHidden: Boolean = false,
) {
    val current: ChallengeStep? get() = steps.getOrNull(index)
    val finished: Boolean get() = index >= steps.size
}

data class Attempt(
    val run: ChallengeRun,
    val advanced: Boolean,
    val finished: Boolean,
    val error: String?,
)

object ChallengePolicy {
    const val DRILL_MATH = 7
    const val DRILL_MEMORY = 2
    const val DRILL_RETYPE = 1
    val MEMORY_LENGTHS = listOf(6, 7)
    const val MINDFUL_BREATH_MS = 60_000L
    const val JOURNAL_WINDOW_MS = 120_000L
    const val BREATHING_MS = 120_000L
    const val PROSE_MIN_URGE = 12
    const val PROSE_MIN_COMMIT = 12
    const val PROSE_MIN_WHY = 24
    const val PROSE_MIN_WHAT = 12

    val SENTENCES: List<String> = listOf(
        "Finish the one thing you came here to do and then leave before the half hour is gone for good.",
        "Name the single reason you are opening these apps and stop when that one thing is done.",
        "Keep your hands still for a moment and decide the one task these apps are actually for.",
        "Type this line slowly so the choice to open everything else is a choice and not a reflex.",
    )
}

fun buildChallenge(kind: ChallengeKind, seed: Long): List<ChallengeStep> {
    return when (kind) {
        ChallengeKind.DRILLS -> drills(seed)
        ChallengeKind.MINDFUL -> mindful(seed)
        ChallengeKind.JOURNAL -> journal()
        ChallengeKind.BREATHING -> listOf(
            ChallengeStep(
                id = "breath",
                kind = StepKind.BREATH,
                prompt = "Breathe with the circle.",
                durationMs = ChallengePolicy.BREATHING_MS,
            ),
        )
    }
}

fun startChallenge(kind: ChallengeKind, seed: Long, nowElapsed: Long): ChallengeRun {
    return ChallengeRun(
        kind = kind,
        steps = buildChallenge(kind, seed),
        startedAtElapsed = nowElapsed,
        stepStartedAtElapsed = nowElapsed,
    )
}

fun hideMemory(run: ChallengeRun): ChallengeRun {
    val step = run.current ?: return run
    if (step.kind != StepKind.MEMORY || run.memoryHidden) return run
    return run.copy(memoryHidden = true)
}

fun attempt(run: ChallengeRun, answer: String, nowElapsed: Long): Attempt {
    val step = run.current ?: return Attempt(run, advanced = false, finished = run.finished, error = "Already finished")
    val failure = gate(run, step, answer, nowElapsed)
    if (failure != null) return Attempt(run, advanced = false, finished = false, error = failure)
    val nextIndex = run.index + 1
    val next = run.copy(
        index = nextIndex,
        stepStartedAtElapsed = nowElapsed,
        memoryHidden = false,
    )
    return Attempt(next, advanced = true, finished = next.finished, error = null)
}

fun evalMathPrompt(prompt: String): Int {
    val parts = prompt.trim().split(Regex("\\s+"))
    require(parts.size == 3) { "not a math prompt: $prompt" }
    val a = parts[0].toInt()
    val b = parts[2].toInt()
    return when (parts[1]) {
        "+" -> a + b
        "-" -> a - b
        "*" -> a * b
        else -> error("unknown op ${parts[1]}")
    }
}

fun normalizeRetype(value: String): String = value.trim().replace(Regex("\\s+"), " ")

fun proseOk(answer: String, minChars: Int): Boolean {
    val trimmed = normalizeRetype(answer)
    if (trimmed.length < minChars) return false
    if (!trimmed.any { it.isLetter() }) return false
    if (trimmed.all { it == trimmed.first() }) return false
    return true
}

private fun gate(run: ChallengeRun, step: ChallengeStep, answer: String, nowElapsed: Long): String? {
    return when (step.kind) {
        StepKind.MATH ->
            if (answer.trim() == step.expected) null else "Not quite"
        StepKind.MEMORY -> when {
            !run.memoryHidden -> "Hide the digits first"
            answer.trim() == step.expected -> null
            else -> "Not quite"
        }
        StepKind.RETYPE ->
            if (normalizeRetype(answer) == normalizeRetype(step.expected.orEmpty())) null else "Type it exactly"
        StepKind.PROSE ->
            if (proseOk(answer, step.minChars)) null else "Write a real answer"
        StepKind.BREATH ->
            if (nowElapsed - run.stepStartedAtElapsed >= step.durationMs) null else "Stay with it"
        StepKind.WAIT ->
            if (nowElapsed - run.startedAtElapsed >= step.durationMs) null else "Stay until the time is up"
    }
}

private fun drills(seed: Long): List<ChallengeStep> {
    val rng = Random(seed)
    val math = List(ChallengePolicy.DRILL_MATH) { index ->
        val (prompt, expected) = mathProblem(rng)
        ChallengeStep(id = "math-$index", kind = StepKind.MATH, prompt = prompt, expected = expected)
    }
    val memory = ChallengePolicy.MEMORY_LENGTHS.mapIndexed { index, length ->
        val digits = buildString(length) { repeat(length) { append(rng.nextInt(10)) } }
        ChallengeStep(
            id = "memory-$index",
            kind = StepKind.MEMORY,
            prompt = "Remember these digits",
            expected = digits,
            reveal = digits,
        )
    }
    val sentence = ChallengePolicy.SENTENCES[rng.nextInt(ChallengePolicy.SENTENCES.size)]
    val retype = ChallengeStep(
        id = "retype",
        kind = StepKind.RETYPE,
        prompt = sentence,
        expected = sentence,
    )
    return math + memory + retype
}

private fun mindful(seed: Long): List<ChallengeStep> {
    val sentence = ChallengePolicy.SENTENCES[Random(seed).nextInt(ChallengePolicy.SENTENCES.size)]
    return listOf(
        ChallengeStep(
            id = "urge",
            kind = StepKind.PROSE,
            prompt = "What are you hoping to feel?",
            minChars = ChallengePolicy.PROSE_MIN_URGE,
        ),
        ChallengeStep(
            id = "breath",
            kind = StepKind.BREATH,
            prompt = "Breathe with the circle.",
            durationMs = ChallengePolicy.MINDFUL_BREATH_MS,
        ),
        ChallengeStep(id = "retype", kind = StepKind.RETYPE, prompt = sentence, expected = sentence),
        ChallengeStep(
            id = "commit",
            kind = StepKind.PROSE,
            prompt = "I will open social apps to",
            minChars = ChallengePolicy.PROSE_MIN_COMMIT,
        ),
    )
}

private fun journal(): List<ChallengeStep> {
    return listOf(
        ChallengeStep(
            id = "why",
            kind = StepKind.PROSE,
            prompt = "Why do you want social mode?",
            minChars = ChallengePolicy.PROSE_MIN_WHY,
        ),
        ChallengeStep(
            id = "what",
            kind = StepKind.PROSE,
            prompt = "What will you do?",
            minChars = ChallengePolicy.PROSE_MIN_WHAT,
        ),
        ChallengeStep(
            id = "wait",
            kind = StepKind.WAIT,
            prompt = "Stay here until two minutes have passed.",
            durationMs = ChallengePolicy.JOURNAL_WINDOW_MS,
        ),
    )
}

private fun mathProblem(rng: Random): Pair<String, String> {
    return when (rng.nextInt(3)) {
        0 -> {
            val a = rng.nextInt(12, 90)
            val b = rng.nextInt(12, 90)
            "$a + $b" to (a + b).toString()
        }
        1 -> {
            val left = rng.nextInt(20, 98)
            val right = rng.nextInt(10, 90)
            val a = maxOf(left, right)
            val b = minOf(left, right)
            "$a - $b" to (a - b).toString()
        }
        else -> {
            val a = rng.nextInt(6, 20)
            val b = rng.nextInt(3, 10)
            "$a * $b" to (a * b).toString()
        }
    }
}
