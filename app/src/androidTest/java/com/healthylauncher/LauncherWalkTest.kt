package com.healthylauncher

import android.app.admin.DevicePolicyManager
import android.graphics.Color
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.util.regex.Pattern
import com.healthylauncher.block.isDefaultHome
import com.healthylauncher.domain.Appearance
import com.healthylauncher.domain.ChallengeKind
import com.healthylauncher.domain.CriticalPackages
import com.healthylauncher.domain.LauncherSettings
import com.healthylauncher.domain.Mode
import com.healthylauncher.domain.SessionEvent
import com.healthylauncher.domain.SessionTiming
import com.healthylauncher.domain.evalMathPrompt
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

/**
 * Layer 2 is the clicks. Layer 3 is the device: suspend, alarms, accessibility, lock.
 * Both run on a booted Android image with this app as device owner and home.
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class LauncherWalkTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)
    private val context = instrumentation.targetContext
    private val app = context.applicationContext as HealthyApp
    private val pkg = context.packageName

    @Before
    fun resetToNormal() {
        runBlocking {
            if (app.repository.session.value.mode == Mode.SOCIAL) {
                app.repository.dispatch(SessionEvent.EndEarly)
            }
            app.repository.updateSettings { LauncherSettings() }
            app.enforcer.reconcile()
        }
        ensureAccess()
        if (!device.isScreenOn) {
            device.wakeUp()
            device.executeShellCommand("wm dismiss-keyguard")
        }
        device.pressHome()
        assertTrue(
            "home did not show mode_label\n${textDump()}",
            device.wait(Until.hasObject(res(Tags.MODE_LABEL)), 8_000L),
        )
    }

    private fun res(id: String): BySelector {
        return By.res(Pattern.compile("(?:^|:|/)${Pattern.quote(id)}$"))
    }

    private fun ensureAccess() {
        device.executeShellCommand(
            "settings put secure enabled_accessibility_services $pkg/$pkg.block.GuardService",
        )
        device.executeShellCommand("settings put secure accessibility_enabled 1")
        device.executeShellCommand("pm grant $pkg android.permission.WRITE_SECURE_SETTINGS")
        val current = device.executeShellCommand("settings get secure enabled_notification_listeners").trim()
        val ours = "$pkg/$pkg.block.NotifBlocker"
        if (!current.contains("NotifBlocker")) {
            val next = if (current.isBlank() || current == "null") ours else "$current:$ours"
            device.executeShellCommand("settings put secure enabled_notification_listeners $next")
        }
    }

    @Test
    fun a_deviceIsOwnerHomeAndGuard() {
        val dpm = context.getSystemService(DevicePolicyManager::class.java)
        assertTrue("device owner is required", dpm.isDeviceOwnerApp(pkg))
        assertTrue("home role is required", isDefaultHome(context))
        val services = device.executeShellCommand("settings get secure enabled_accessibility_services")
        assertTrue(services, services.contains("GuardService"))
        val listeners = device.executeShellCommand("settings get secure enabled_notification_listeners")
        assertTrue(listeners, listeners.contains("NotifBlocker"))
    }

    @Test
    fun b_normalModeStartsEmpty() {
        runBlocking {
            app.repository.updateSettings { it.copy(appearance = Appearance.TEXT_COLOR) }
            app.enforcer.reconcile()
        }
        device.pressHome()
        assertEquals("Normal", text(Tags.MODE_LABEL))
        assertTrue(device.hasObject(res("empty_home")))
        assertFalse(device.hasObject(res(Tags.TIMER)))
        shot("01-empty-normal")
        val pixel = pixel()
        assertEquals("normal background ${Integer.toHexString(pixel)}", PaletteNormal, pixel)
    }

    @Test
    fun c_userPicksAppsAndOnlyThoseShow() {
        val clock = requireApp("clock", "deskclock")
        val other = requireOther(clock.packageName)
        openPicker()
        toggle(clock.packageName)
        assertTrue(
            "picker did not allow ${clock.packageName}",
            clock.packageName in app.repository.settings.value.allowlist,
        )
        device.pressBack()
        device.pressBack()
        assertTrue(device.wait(Until.hasObject(res(Tags.MODE_LABEL)), 5_000L))
        assertTrue(
            "home missing ${clock.packageName}\n${textDump()}",
            device.hasObject(res(Tags.appRow(clock.packageName))),
        )
        assertFalse(device.hasObject(res(Tags.appRow(other.packageName))))
        assertFalse(textDump().contains("Settings") && device.hasObject(res(Tags.appRow("com.android.settings"))))
        shot("02-allowlist")
        device.findObject(res(Tags.appRow(clock.packageName))).click()
        val opened = waitFocused(clock.packageName, 8_000L)
        assertTrue("clock should open, focus=$opened", opened.contains(clock.packageName))
        device.pressHome()
        assertTrue(device.wait(Until.hasObject(res(Tags.MODE_LABEL)), 8_000L))
    }

    @Test
    fun d_appearancesKeepTheSameListShape() {
        val clock = requireApp("clock", "deskclock")
        runBlocking {
            app.repository.updateSettings { it.copy(allowlist = setOf(clock.packageName), appearance = Appearance.TEXT_COLOR) }
            app.enforcer.reconcile()
        }
        device.pressHome()
        click(Tags.OPEN_SETTINGS)
        click(Tags.APPEARANCE_COLOR_TEXT)
        assertIcon(clock.packageName, present = false)
        val off = secure("accessibility_display_daltonizer_enabled")
        assertTrue("daltonizer should be off, was $off", off == "0" || off == "null")
        shot("03-text-color")

        click(Tags.APPEARANCE_GRAY)
        device.wait(Until.hasObject(res(Tags.APPEARANCE_GRAY)), 3_000L)
        assertEquals("1", secure("accessibility_display_daltonizer_enabled"))
        assertEquals("0", secure("accessibility_display_daltonizer"))
        shot("04-text-gray")

        click(Tags.APPEARANCE_MONO)
        device.pressBack()
        device.wait(Until.hasObject(res(Tags.appRow(clock.packageName))), 5_000L)
        assertTrue(device.hasObject(res(Tags.icon(clock.packageName, mono = true))))
        shot("05-mono-icons")

        click(Tags.OPEN_SETTINGS)
        click(Tags.APPEARANCE_ICONS)
        device.pressBack()
        device.wait(Until.hasObject(res(Tags.icon(clock.packageName, mono = false))), 5_000L)
        assertTrue(device.hasObject(res(Tags.icon(clock.packageName, mono = false))))
        assertFalse(device.hasObject(res(Tags.icon(clock.packageName, mono = true))))
        shot("06-color-icons")
        assertEquals("Normal", text(Tags.MODE_LABEL))
    }

    @Test
    fun e_wrongDrillDoesNotAdvance() {
        runBlocking {
            app.repository.updateSettings { it.copy(challenge = ChallengeKind.DRILLS, appearance = Appearance.TEXT_COLOR) }
        }
        device.pressHome()
        click(Tags.ENTER_SOCIAL)
        assertTrue(text(Tags.PROGRESS), text(Tags.PROGRESS).contains("1 of "))
        setField(Tags.ANSWER, "0")
        click(Tags.SUBMIT)
        assertEquals("Not quite", text(Tags.ERROR))
        assertTrue(text(Tags.PROGRESS), text(Tags.PROGRESS).contains("1 of "))
        shot("07-wrong-drill")
        click(Tags.CANCEL)
        assertEquals("Normal", text(Tags.MODE_LABEL))
    }

    @Test
    fun f_drillsUnlockSocialForThirtyMinutes() {
        val other = prepareAllowClockOnly()
        device.pressHome()
        click(Tags.ENTER_SOCIAL)
        solveDrills()
        assertEquals("Social", text(Tags.MODE_LABEL))
        val timer = text(Tags.TIMER)
        assertTrue("timer was $timer", timer == "30:00" || timer == "29:59" || timer == "29:58")
        assertTrue(device.hasObject(res(Tags.appRow(other.packageName))))
        val socialPixel = pixel()
        assertEquals("social background", PaletteSocial, socialPixel)
        assertNotEquals(PaletteNormal, socialPixel)
        shot("08-social")
        assertFalse(isSuspended(other.packageName))
        val session = app.repository.session.value
        val left = (session.endsAtElapsed ?: 0L) - SystemClock.elapsedRealtime()
        assertTrue("deadline delta $left", left in (29 * 60_000L)..(30 * 60_000L + 2_000L))
        assertAlarmNear(session.endsAtElapsed ?: 0L, "end")
        assertAlarmNear((session.endsAtElapsed ?: 0L) - 5 * 60_000L, "warn5")
        assertAlarmNear((session.endsAtElapsed ?: 0L) - 60_000L, "warn1")
    }

    @Test
    fun g_endEarlyClosesAndSuspends() {
        val other = prepareAllowClockOnly()
        device.pressHome()
        click(Tags.ENTER_SOCIAL)
        solveDrills()
        device.findObject(res(Tags.appRow(other.packageName))).click()
        assertTrue(waitFocused(other.packageName, 8_000L).contains(other.packageName))
        device.pressHome()
        click(Tags.END_SOCIAL)
        assertTrue(device.wait(Until.hasObject(By.text("End")), 5_000L))
        device.findObject(By.text("End")).click()
        device.wait(Until.gone(res(Tags.TIMER)), 8_000L)
        assertEquals("Normal", text(Tags.MODE_LABEL))
        assertFalse(device.hasObject(res(Tags.appRow(other.packageName))))
        waitUntil("suspend ${other.packageName}") { isSuspended(other.packageName) }
        val start = device.executeShellCommand("monkey -p ${other.packageName} -c android.intent.category.LAUNCHER 1")
        assertTrue(start, isSuspended(other.packageName) || start.contains("Error") || start.contains("aborted") || focusedPackage() == pkg)
        assertFalse(isSuspended("com.android.settings"))
        assertFalse(isSuspended(pkg))
        shot("09-back-to-normal")
    }

    @Test
    fun h_lockFiveMinutesEndsSocialMode() {
        prepareAllowClockOnly()
        device.pressHome()
        click(Tags.ENTER_SOCIAL)
        solveDrills()
        device.sleep()
        Thread.sleep(1_000L)
        val session = app.repository.session.value
        val since = session.blockedSinceElapsed
        assertTrue("blockedSince=$since", since != null)
        assertAlarmNear(since!! + SessionTiming.LOCK_RETURN_MS, "lock")
        device.wakeUp()
        device.executeShellCommand("wm dismiss-keyguard")
        device.executeShellCommand("input keyevent 82")
        Thread.sleep(800L)
        assertTrue(app.repository.session.value.mode == Mode.SOCIAL)
        assertTrue(app.repository.session.value.blockedSinceElapsed == null)
        device.sleep()
        Thread.sleep(800L)
        device.executeShellCommand(
            "am broadcast -a com.healthylauncher.DEBUG_CLOCK -n $pkg/.debug.DebugReceiver --el advance_elapsed_ms ${SessionTiming.LOCK_RETURN_MS}",
        )
        waitUntil("back to normal after lock") { app.repository.session.value.mode == Mode.NORMAL }
        device.wakeUp()
        device.executeShellCommand("wm dismiss-keyguard")
        assertTrue(device.wait(Until.hasObject(res(Tags.MODE_LABEL)), 8_000L))
        assertEquals("Normal", text(Tags.MODE_LABEL))
        shot("10-lock-returned")
    }

    @Test
    fun i_accessibilitySendsBlockedAppsHome() {
        val other = prepareAllowClockOnly()
        runBlocking {
            app.repository.updateSettings { it.copy(suspendPackages = false, appearance = Appearance.TEXT_COLOR) }
            app.enforcer.reconcile()
        }
        assertFalse(isSuspended(other.packageName))
        val component = context.packageManager.getLaunchIntentForPackage(other.packageName)?.component
        assertTrue(component != null)
        device.executeShellCommand("am start -n ${component!!.flattenToShortString()}")
        assertTrue(
            device.wait(Until.hasObject(res(Tags.MODE_LABEL)), 8_000L),
        )
        waitUntil("guard returns home from ${other.packageName}, focus=${focusedPackage()}") {
            focusedPackage() == pkg || device.hasObject(res(Tags.ENTER_SOCIAL))
        }
        shot("11-guard")
    }

    @Test
    fun j_otherChallengesCannotBeSkipped() {
        runBlocking {
            app.repository.updateSettings {
                it.copy(challenge = ChallengeKind.BREATHING, appearance = Appearance.TEXT_COLOR)
            }
        }
        device.pressHome()
        click(Tags.ENTER_SOCIAL)
        assertTrue(device.wait(Until.hasObject(res(Tags.BREATH)), 5_000L))
        click(Tags.SUBMIT)
        assertEquals("Stay with it", text(Tags.ERROR))
        assertTrue(text(Tags.PROGRESS), text(Tags.PROGRESS).contains("1 of 1"))
        shot("12-breathing")
        click(Tags.CANCEL)

        runBlocking {
            app.repository.updateSettings { it.copy(challenge = ChallengeKind.JOURNAL) }
        }
        device.pressHome()
        click(Tags.ENTER_SOCIAL)
        setField(Tags.ANSWER, "I want to answer one message from a friend")
        click(Tags.SUBMIT)
        setField(Tags.ANSWER, "Reply to that message")
        click(Tags.SUBMIT)
        assertTrue(device.hasObject(res(Tags.BREATH)))
        click(Tags.SUBMIT)
        assertEquals("Stay until the time is up", text(Tags.ERROR))
        shot("13-journal")
        click(Tags.CANCEL)

        runBlocking {
            app.repository.updateSettings { it.copy(challenge = ChallengeKind.MINDFUL) }
        }
        device.pressHome()
        click(Tags.ENTER_SOCIAL)
        assertEquals("What are you hoping to feel?", text(Tags.PROMPT))
        setField(Tags.ANSWER, "no")
        click(Tags.SUBMIT)
        assertEquals("Write a real answer", text(Tags.ERROR))
        shot("14-mindful")
        click(Tags.CANCEL)
        assertEquals("Normal", text(Tags.MODE_LABEL))
    }

    private fun prepareAllowClockOnly(): com.healthylauncher.domain.LaunchableApp {
        val clock = requireApp("clock", "deskclock")
        val other = requireOther(clock.packageName)
        runBlocking {
            app.repository.updateSettings {
                LauncherSettings(
                    allowlist = setOf(clock.packageName),
                    appearance = Appearance.TEXT_COLOR,
                    challenge = ChallengeKind.DRILLS,
                )
            }
            app.enforcer.reconcile()
        }
        return other
    }

    private fun requireApp(vararg hints: String): com.healthylauncher.domain.LaunchableApp {
        val apps = app.catalog.refresh()
        val found = apps.firstOrNull { app ->
            hints.any { hint ->
                app.label.contains(hint, ignoreCase = true) || app.packageName.contains(hint, ignoreCase = true)
            }
        }
        assertTrue("none of ${hints.toList()} in ${apps.map { it.label }}", found != null)
        return found!!
    }

    private fun requireOther(skip: String): com.healthylauncher.domain.LaunchableApp {
        val protected = CriticalPackages.resolve(pkg, null, null)
        val found = app.catalog.current().firstOrNull {
            it.packageName != skip && it.packageName != pkg && it.packageName !in protected
        }
        assertTrue("need a second launchable app", found != null)
        return found!!
    }

    private fun openPicker() {
        click(Tags.OPEN_SETTINGS)
        click(Tags.OPEN_APPS)
        assertTrue(device.wait(Until.hasObject(res(Tags.PICKER_SEARCH)), 5_000L))
    }

    private fun toggle(packageName: String) {
        val id = Tags.pickRow(packageName)
        var guard = 0
        while (!device.hasObject(res(id)) && guard++ < 8) {
            device.swipe(
                device.displayWidth / 2,
                device.displayHeight * 3 / 4,
                device.displayWidth / 2,
                device.displayHeight / 3,
                40,
            )
        }
        click(id)
    }

    private fun solveDrills() {
        var guard = 0
        while (guard++ < 16) {
            if (device.wait(Until.hasObject(res(Tags.TIMER)), 400L)) return
            if (device.hasObject(res(Tags.REVEAL))) {
                val digits = text(Tags.REVEAL).filter { it.isDigit() }
                click(Tags.HIDE)
                device.wait(Until.hasObject(res(Tags.ANSWER)), 3_000L)
                setField(Tags.ANSWER, digits)
                click(Tags.SUBMIT)
                continue
            }
            val prompt = text(Tags.PROMPT)
            val answer = if (prompt.contains(" + ") || prompt.contains(" - ") || prompt.contains(" * ")) {
                evalMathPrompt(prompt).toString()
            } else {
                prompt
            }
            setField(Tags.ANSWER, answer)
            click(Tags.SUBMIT)
        }
        fail("drills did not finish, screen=${textDump()}")
    }

    private fun click(id: String) {
        assertTrue("missing $id\n${textDump()}", device.wait(Until.hasObject(res(id)), 8_000L))
        device.findObject(res(id)).click()
    }

    private fun text(id: String): String {
        assertTrue("missing $id\n${textDump()}", device.wait(Until.hasObject(res(id)), 8_000L))
        return device.findObject(res(id)).text ?: ""
    }

    private fun setField(id: String, value: String) {
        assertTrue("missing field $id\n${textDump()}", device.wait(Until.hasObject(res(id)), 8_000L))
        val field = device.findObject(res(id))
        field.click()
        field.setText(value)
    }

    private fun secure(name: String): String {
        return device.executeShellCommand("settings get secure $name").trim()
    }

    private fun isSuspended(packageName: String): Boolean {
        val dump = device.executeShellCommand("dumpsys package $packageName")
        return dump.contains("suspended=true") || Regex("pkgFlags=\\[[^\\]]*SUSPENDED").containsMatchIn(dump)
    }

    private fun focusedPackage(): String {
        val dump = device.executeShellCommand("dumpsys window")
        val line = dump.lineSequence().firstOrNull { it.contains("mCurrentFocus") || it.contains("mFocusedApp") }.orEmpty()
        return Regex("""([A-Za-z0-9._]+)/""").find(line)?.groupValues?.get(1).orEmpty()
    }

    private fun waitFocused(packageName: String, timeoutMs: Long): String {
        val start = SystemClock.elapsedRealtime()
        var focus = focusedPackage()
        while (SystemClock.elapsedRealtime() - start < timeoutMs) {
            focus = focusedPackage()
            if (focus.contains(packageName)) return focus
            Thread.sleep(200L)
        }
        return focus
    }

    private fun waitUntil(message: String, timeoutMs: Long = 8_000L, predicate: () -> Boolean) {
        val start = SystemClock.elapsedRealtime()
        while (SystemClock.elapsedRealtime() - start < timeoutMs) {
            if (predicate()) return
            Thread.sleep(200L)
        }
        fail(message)
    }

    private fun assertIcon(packageName: String, present: Boolean) {
        device.pressBack()
        device.wait(Until.hasObject(res(Tags.appRow(packageName))), 5_000L)
        val mono = device.hasObject(res(Tags.icon(packageName, true)))
        val color = device.hasObject(res(Tags.icon(packageName, false)))
        assertEquals(present, mono || color)
        click(Tags.OPEN_SETTINGS)
    }

    private fun assertAlarmNear(domainElapsed: Long, label: String) {
        val dump = device.executeShellCommand("dumpsys alarm")
        val times = Regex("(?:whenElapsed|origWhen)[= ](\\d+)").findAll(dump).map { it.groupValues[1].toLong() }.toList()
        val hit = times.any { kotlin.math.abs(it - domainElapsed) < 15_000L }
        if (!hit) {
            val snippet = dump.lineSequence().filter {
                it.contains("healthylauncher") || it.contains("whenElapsed") || it.contains("Alarm{")
            }.take(60).joinToString("\n")
            fail("no $label alarm near $domainElapsed\n$snippet")
        }
    }

    private fun pixel(): Int {
        val shot = instrumentation.uiAutomation.takeScreenshot()
        assertTrue(shot != null)
        return shot.getPixel(4, 4)
    }

    private fun shot(name: String) {
        val dir = File(context.getExternalFilesDir("film"), "")
        dir.mkdirs()
        device.takeScreenshot(File(dir, "$name.png"))
    }

    private fun textDump(): String {
        return device.findObjects(By.clickable(true)).mapNotNull { node ->
            runCatching { node.text ?: node.resourceName }.getOrNull()
        }.joinToString(" | ")
    }

    private companion object {
        val PaletteNormal = Color.parseColor("#0E0F12")
        val PaletteSocial = Color.parseColor("#16130F")
    }
}
