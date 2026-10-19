package com.healthylauncher.domain

enum class GrayscaleAction { ENABLE, DISABLE, LEAVE }

object ShellCommands {
    private val SAFE = Regex("^[A-Za-z0-9._]+$")

    fun suspendPackages(packages: Collection<String>): String {
        return pm("suspend", packages)
    }

    fun unsuspendPackages(packages: Collection<String>): String {
        return pm("unsuspend", packages)
    }

    fun forceStop(packageName: String): String {
        val safe = sanitize(listOf(packageName)).single()
        return "am force-stop --user 0 $safe"
    }

    fun grayscale(enabled: Boolean): List<String> {
        return if (enabled) {
            listOf(
                "settings put secure accessibility_display_daltonizer_enabled 1",
                "settings put secure accessibility_display_daltonizer 0",
            )
        } else {
            listOf("settings put secure accessibility_display_daltonizer_enabled 0")
        }
    }

    fun sanitize(packages: Collection<String>): List<String> {
        val bad = packages.filter { !SAFE.matches(it) }
        require(bad.isEmpty()) { "unsafe package names: $bad" }
        return packages.distinct().sorted()
    }

    private fun pm(verb: String, packages: Collection<String>): String {
        val safe = sanitize(packages)
        require(safe.isNotEmpty()) { "no packages" }
        return "pm $verb --user 0 ${safe.joinToString(" ")}"
    }
}

/**
 * System grayscale is a secure setting. Leave a filter the user already turned on alone.
 * Only undo a filter this app turned on.
 */
fun grayscaleDecision(
    want: Boolean,
    currentlyEnabled: Boolean,
    mode: Int,
    ownedByUs: Boolean,
): GrayscaleAction {
    // Mode 0 is the monochrome filter this app applies. Any other mode belongs to the user.
    val ourFilter = ownedByUs || mode == 0
    if (want) {
        if (currentlyEnabled && !ourFilter) return GrayscaleAction.LEAVE
        return GrayscaleAction.ENABLE
    }
    if (currentlyEnabled && ourFilter) return GrayscaleAction.DISABLE
    if (ownedByUs) return GrayscaleAction.DISABLE
    return GrayscaleAction.LEAVE
}
