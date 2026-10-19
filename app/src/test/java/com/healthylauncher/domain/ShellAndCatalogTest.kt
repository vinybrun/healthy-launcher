package com.healthylauncher.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShellAndCatalogTest {
    @Test
    fun shellCommandsAreSortedAndRejectMetacharacters() {
        assertEquals(
            "pm suspend --user 0 com.a com.b.example",
            ShellCommands.suspendPackages(listOf("com.b.example", "com.a", "com.a")),
        )
        assertEquals(
            "pm unsuspend --user 0 com.a",
            ShellCommands.unsuspendPackages(listOf("com.a")),
        )
        assertEquals("am force-stop --user 0 com.a", ShellCommands.forceStop("com.a"))
        val thrown = runCatching { ShellCommands.forceStop("com.a;rm") }
        assertTrue(thrown.exceptionOrNull() is IllegalArgumentException)
        assertTrue(ShellCommands.grayscale(true).any { it.contains("daltonizer_enabled 1") })
        assertTrue(ShellCommands.grayscale(true).any { it.endsWith("daltonizer 0") })
        assertEquals(
            listOf("settings put secure accessibility_display_daltonizer_enabled 0"),
            ShellCommands.grayscale(false),
        )
    }

    @Test
    fun grayscaleLeavesAFilterWeDoNotOwn() {
        assertEquals(GrayscaleAction.ENABLE, grayscaleDecision(true, currentlyEnabled = false, mode = -1, ownedByUs = false))
        assertEquals(GrayscaleAction.ENABLE, grayscaleDecision(true, currentlyEnabled = true, mode = 0, ownedByUs = true))
        assertEquals(GrayscaleAction.LEAVE, grayscaleDecision(true, currentlyEnabled = true, mode = 12, ownedByUs = false))
        assertEquals(GrayscaleAction.DISABLE, grayscaleDecision(false, currentlyEnabled = true, mode = 0, ownedByUs = false))
        assertEquals(GrayscaleAction.DISABLE, grayscaleDecision(false, currentlyEnabled = true, mode = 12, ownedByUs = true))
        assertEquals(GrayscaleAction.LEAVE, grayscaleDecision(false, currentlyEnabled = true, mode = 12, ownedByUs = false))
    }

    @Test
    fun appListSortsByLabelThenPackage() {
        val sorted = sortedApps(
            listOf(
                LaunchableApp("com.z", "Clock"),
                LaunchableApp("com.a", "clock"),
                LaunchableApp("com.m", "Maps"),
            ),
        )
        assertEquals(listOf("com.a", "com.z", "com.m"), sorted.map { it.packageName })
    }

    @Test
    fun iconsOnlyForTheIconAppearances() {
        assertFalse(showsIcons(Appearance.TEXT_GRAYSCALE))
        assertFalse(showsIcons(Appearance.TEXT_COLOR))
        assertTrue(showsIcons(Appearance.MONO_ICONS))
        assertTrue(showsIcons(Appearance.COLOR_ICONS))
    }

    @Test
    fun alarmTriggerStripsTheTestClockOffset() {
        assertEquals(1_800_000L, realElapsed(domainElapsed = 1_800_000L, clockOffsetMs = 0L))
        assertEquals(1_500_000L, realElapsed(domainElapsed = 1_800_000L, clockOffsetMs = 300_000L))
    }
}
