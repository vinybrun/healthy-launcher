package com.healthylauncher.domain

enum class Appearance {
    TEXT_GRAYSCALE,
    TEXT_COLOR,
    MONO_ICONS,
    COLOR_ICONS,
}

data class LaunchableApp(
    val packageName: String,
    val label: String,
)

data class LauncherSettings(
    val allowlist: Set<String> = emptySet(),
    val appearance: Appearance = Appearance.TEXT_GRAYSCALE,
    val challenge: ChallengeKind = ChallengeKind.DRILLS,
    val accessibility: Boolean = true,
    val suspendPackages: Boolean = true,
    val shizuku: Boolean = true,
    val hideNotifications: Boolean = true,
    val usageWatch: Boolean = true,
    val grayscaleOwned: Boolean = false,
)

fun sortedApps(apps: List<LaunchableApp>): List<LaunchableApp> {
    return apps.sortedWith(
        compareBy<LaunchableApp, String>(String.CASE_INSENSITIVE_ORDER) { it.label }
            .thenBy { it.packageName },
    )
}

fun showsIcons(appearance: Appearance): Boolean {
    return appearance == Appearance.MONO_ICONS || appearance == Appearance.COLOR_ICONS
}

/** AlarmManager speaks real elapsed time. Session deadlines may include a test offset. */
fun realElapsed(domainElapsed: Long, clockOffsetMs: Long): Long = domainElapsed - clockOffsetMs
