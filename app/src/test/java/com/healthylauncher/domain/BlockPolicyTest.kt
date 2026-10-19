package com.healthylauncher.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockPolicyTest {
    private val self = "com.healthylauncher"
    private val launchable = setOf(
        self,
        "com.example.clock",
        "com.example.social",
        "com.android.settings",
        "com.google.android.gms",
    )

    private fun snap(
        mode: Mode = Mode.NORMAL,
        allow: Set<String> = emptySet(),
    ) = BlockSnapshot(
        mode = mode,
        allowlist = allow,
        launchable = launchable,
        protectedPackages = CriticalPackages.resolve(self, ime = "com.example.ime", dialer = "com.example.dialer"),
        selfPackage = self,
    )

    @Test
    fun emptyAllowlistShowsNothingInNormalModeAndEverythingInSocialMode() {
        val normal = snap()
        val social = snap(mode = Mode.SOCIAL)
        assertTrue(launchable.none { visibleOnHome(it, normal) })
        assertEquals(
            setOf("com.example.clock", "com.example.social", "com.android.settings", "com.google.android.gms"),
            launchable.filter { visibleOnHome(it, social) }.toSet(),
        )
    }

    @Test
    fun normalHomeIsExactlyTheAllowlist() {
        val normal = snap(allow = setOf("com.example.clock", "com.example.missing"))
        assertTrue(visibleOnHome("com.example.clock", normal))
        assertFalse(visibleOnHome("com.example.social", normal))
        assertFalse(visibleOnHome("com.example.missing", normal))
        assertFalse(visibleOnHome(self, normal))
    }

    @Test
    fun socialModeDoesNotBlockLaunchesAndNormalModeBlocksTheRest() {
        val normal = snap(allow = setOf("com.example.clock"))
        val social = snap(mode = Mode.SOCIAL, allow = setOf("com.example.clock"))
        assertFalse(launchBlocked("com.example.clock", normal))
        assertTrue(launchBlocked("com.example.social", normal))
        assertFalse(launchBlocked("com.example.social", social))
        assertFalse(launchBlocked("com.example.not.launchable", normal))
    }

    @Test
    fun protectedPackagesAndSelfAreNeverSuspended() {
        val normal = snap()
        val suspend = packagesToSuspend(normal)
        assertEquals(setOf("com.example.clock", "com.example.social"), suspend)
        assertFalse(self in suspend)
        assertFalse("com.android.settings" in suspend)
        assertFalse("com.google.android.gms" in suspend)
        assertFalse(launchBlocked("com.android.settings", normal))
        assertFalse(launchBlocked(self, normal))
        assertFalse(visibleOnHome("com.android.settings", normal))
    }

    @Test
    fun socialModeSuspendsNothing() {
        assertTrue(packagesToSuspend(snap(mode = Mode.SOCIAL)).isEmpty())
    }

    @Test
    fun sessionEndClosesEveryManagedAppEvenIfItWasNotOpened() {
        val snap = snap(allow = setOf("com.example.clock"))
        val close = packagesToClose(snap, opened = setOf("com.example.social", "com.side.loaded"))
        assertEquals(
            setOf("com.example.clock", "com.example.social", "com.side.loaded"),
            close,
        )
        assertFalse("com.android.settings" in close)
        assertFalse("com.google.android.gms" in close)
        assertFalse(self in close)
    }

    @Test
    fun anActiveCallIsNotForceClosed() {
        val packages = setOf("com.example.social", "com.google.android.dialer", "com.android.phone")
        val filtered = filterCloseable(packages, inCall = true, dialer = "com.google.android.dialer")
        assertEquals(setOf("com.example.social"), filtered)
        assertEquals(packages, filterCloseable(packages, inCall = false, dialer = "com.google.android.dialer"))
    }

    @Test
    fun suspendDiffOnlyTouchesWhatChanged() {
        val diff = suspendDiff(
            currently = setOf("a", "b"),
            desired = setOf("b", "c"),
        )
        assertEquals(setOf("c"), diff.suspend)
        assertEquals(setOf("a"), diff.unsuspend)
    }

    @Test
    fun criticalSetCoversThePhoneAndPocoEscapeHatches() {
        val resolved = CriticalPackages.resolve(self, ime = "com.google.android.inputmethod.latin", dialer = null)
        listOf(
            self,
            "com.android.systemui",
            "com.android.settings",
            "com.android.phone",
            "com.google.android.gms",
            "com.miui.securitycenter",
            "com.google.android.inputmethod.latin",
            "com.android.emergency",
        ).forEach { assertTrue(it, it in resolved) }
    }

    @Test
    fun notificationPolicyKeepsCallsAndAlarms() {
        val snap = snap(allow = setOf("com.example.clock"))
        assertTrue(shouldCancelNotification("com.example.social", snap, category = null))
        assertFalse(shouldCancelNotification("com.example.clock", snap, category = null))
        assertFalse(shouldCancelNotification("com.example.social", snap, category = "call"))
        assertFalse(shouldCancelNotification("com.example.social", snap, category = "alarm"))
        assertFalse(shouldCancelNotification("com.android.settings", snap, category = "status"))
    }

    @Test
    fun lastResumedEventWins() {
        val events = listOf(1 to "com.a", 2 to "com.b", 1 to "com.c")
        assertEquals("com.c", lastResumed(events, resumedType = 1))
        assertEquals("com.b", lastResumed(events, resumedType = 2))
        assertEquals(null, lastResumed(emptyList(), resumedType = 1))
    }
}
