package com.healthylauncher.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.healthylauncher.HealthyApp
import com.healthylauncher.Tags
import com.healthylauncher.block.isDefaultHome
import com.healthylauncher.block.launchPackage
import com.healthylauncher.domain.Appearance
import com.healthylauncher.domain.BlockSnapshot
import com.healthylauncher.domain.ChallengeKind
import com.healthylauncher.domain.LaunchableApp
import com.healthylauncher.domain.Mode
import com.healthylauncher.domain.SessionEvent
import com.healthylauncher.domain.formatRemaining
import com.healthylauncher.domain.remainingMs
import com.healthylauncher.domain.showsIcons
import com.healthylauncher.domain.visibleOnHome
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(onSettings: () -> Unit, onChallenge: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as HealthyApp
    val settings by app.repository.settings.collectAsStateWithLifecycle()
    val session by app.repository.session.collectAsStateWithLifecycle()
    val banner by app.repository.banner.collectAsStateWithLifecycle()
    var now by remember { mutableStateOf(app.clock.elapsedRealtime()) }
    var confirmEnd by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf("") }
    var home by remember { mutableStateOf(isDefaultHome(context)) }
    val apps by produceState(initialValue = app.catalog.current(), session.mode, settings.allowlist) {
        value = app.catalog.refresh()
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            home = isDefaultHome(context)
            while (true) {
                now = app.clock.elapsedRealtime()
                delay(500L)
            }
        }
    }
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        home = isDefaultHome(context)
    }
    val snap = remember(settings, session, apps) {
        app.repository.snapshot(apps.map { it.packageName }.toSet(), null, null)
    }
    val visible = apps.filter { appRowVisible(it, snap) && it.label.contains(filter, ignoreCase = true) }
    val accent = accentFor(session.mode)

    Column(Modifier.fillMaxSize().padding(horizontal = 28.dp)) {
        Spacer(Modifier.height(28.dp))
        Text(
            text = if (session.mode == Mode.SOCIAL) "Social" else "Normal",
            color = accent,
            fontSize = 14.sp,
            modifier = Modifier.testTag(Tags.MODE_LABEL),
        )
        if (session.mode == Mode.SOCIAL) {
            Text(
                text = formatRemaining(remainingMs(session, now)),
                color = Palette.Ink,
                fontSize = 36.sp,
                modifier = Modifier.testTag(Tags.TIMER).padding(top = 4.dp),
            )
        }
        if (banner != null) {
            Text(
                text = banner.orEmpty(),
                color = accent,
                fontSize = 14.sp,
                modifier = Modifier.testTag(Tags.WARNING).padding(top = 8.dp),
            )
        }
        if (!home) {
            Text(
                text = "Set as home screen",
                color = accent,
                modifier = Modifier
                    .testTag(Tags.SET_HOME)
                    .clickable {
                        val intent = homeIntent(context)
                        if (intent != null) roleLauncher.launch(intent)
                    }
                    .padding(top = 16.dp),
            )
        }
        if (visible.size > 8) {
            BasicTextField(
                value = filter,
                onValueChange = { filter = it },
                textStyle = androidx.compose.ui.text.TextStyle(color = Palette.Ink, fontSize = 16.sp),
                cursorBrush = SolidColor(accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp)
                    .testTag(Tags.HOME_FILTER),
                decorationBox = { inner ->
                    if (filter.isEmpty()) Text("Filter", color = Palette.Mute)
                    inner()
                },
            )
        }
        if (visible.isEmpty() && session.mode == Mode.NORMAL) {
            Text(
                text = "No operational apps yet",
                color = Palette.Ink,
                fontSize = 22.sp,
                modifier = Modifier.testTag("empty_home").padding(top = 36.dp),
            )
            Text(
                text = "Choose the apps that stay available in normal mode.",
                color = Palette.Mute,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        LazyColumn(Modifier.weight(1f).padding(top = 12.dp)) {
            items(visible, key = { it.packageName }) { item ->
                AppNameRow(item, settings.appearance) {
                    if (launchPackage(context, item.packageName)) {
                        app.scope.launch { app.repository.dispatch(SessionEvent.AppOpened(item.packageName)) }
                    }
                }
            }
        }
        if (session.mode == Mode.SOCIAL) {
            ActionText("End social mode", Tags.END_SOCIAL, accent) { confirmEnd = true }
        } else {
            ActionText("Enter social mode", Tags.ENTER_SOCIAL, accent, onChallenge)
        }
        ActionText("Settings", Tags.OPEN_SETTINGS, Palette.Mute, onSettings)
        Spacer(Modifier.height(12.dp))
    }

    if (confirmEnd) {
        AlertDialog(
            onDismissRequest = { confirmEnd = false },
            containerColor = Palette.Line,
            title = { Text("End social mode?") },
            text = { Text("Other apps will close, and the phone goes back to normal mode.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmEnd = false
                        app.scope.launch { app.repository.dispatch(SessionEvent.EndEarly) }
                    },
                    modifier = Modifier.testTag(Tags.END_CONFIRM),
                ) { Text("End", color = accent) }
            },
            dismissButton = {
                TextButton(
                    onClick = { confirmEnd = false },
                    modifier = Modifier.testTag(Tags.END_CANCEL),
                ) { Text("Keep going", color = Palette.Mute) }
            },
        )
    }
}

private fun appRowVisible(app: LaunchableApp, snap: BlockSnapshot): Boolean {
    return visibleOnHome(app.packageName, snap)
}

@Composable
private fun AppNameRow(app: LaunchableApp, appearance: Appearance, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .testTag(Tags.appRow(app.packageName))
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showsIcons(appearance)) {
            AppIcon(app.packageName, mono = appearance == Appearance.MONO_ICONS)
            Spacer(Modifier.width(14.dp))
        }
        Text(app.label, color = Palette.Ink, fontSize = 20.sp)
    }
}

@Composable
private fun AppIcon(packageName: String, mono: Boolean) {
    val context = LocalContext.current
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, packageName) {
        value = runCatching {
            context.packageManager.getApplicationIcon(packageName).toBitmap(96, 96).asImageBitmap()
        }.getOrNull()
    }
    val image = bitmap ?: return
    val filter = if (mono) {
        ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
    } else {
        null
    }
    Image(
        bitmap = image,
        contentDescription = null,
        colorFilter = filter,
        modifier = Modifier.size(22.dp).testTag(Tags.icon(packageName, mono)),
    )
}

@Composable
private fun ActionText(label: String, tag: String, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Text(
        text = label,
        color = color,
        fontSize = 16.sp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
    )
}

@Composable
fun SettingsScreen(onBack: () -> Unit, onApps: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as HealthyApp
    val settings by app.repository.settings.collectAsStateWithLifecycle()
    val session by app.repository.session.collectAsStateWithLifecycle()
    var note by remember { mutableStateOf<String?>(null) }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 24.dp),
    ) {
        Text("Back", color = Palette.Mute, modifier = Modifier.testTag(Tags.BACK).clickable(onClick = onBack))
        Text("Settings", color = Palette.Ink, fontSize = 28.sp, modifier = Modifier.padding(top = 18.dp))
        note?.let { Text(it, color = accentFor(session.mode), modifier = Modifier.padding(top = 12.dp)) }

        Section("Operational apps")
        Text(
            "${settings.allowlist.size} selected",
            color = Palette.Mute,
        )
        ActionText("Choose apps", Tags.OPEN_APPS, accentFor(session.mode), onApps)

        Section("Appearance")
        Text(
            "The list stays the same shape in both modes. Normal and social differ by the apps you see and a slight background shift.",
            color = Palette.Mute,
            fontSize = 14.sp,
        )
        Choice(Tags.APPEARANCE_GRAY, "Text, grayscale", settings.appearance == Appearance.TEXT_GRAYSCALE) {
            setAppearance(app, Appearance.TEXT_GRAYSCALE)
        }
        Choice(Tags.APPEARANCE_COLOR_TEXT, "Text, color", settings.appearance == Appearance.TEXT_COLOR) {
            setAppearance(app, Appearance.TEXT_COLOR)
        }
        Choice(Tags.APPEARANCE_MONO, "Mono icons", settings.appearance == Appearance.MONO_ICONS) {
            setAppearance(app, Appearance.MONO_ICONS)
        }
        Choice(Tags.APPEARANCE_ICONS, "Color icons", settings.appearance == Appearance.COLOR_ICONS) {
            setAppearance(app, Appearance.COLOR_ICONS)
        }

        Section("Challenge")
        Text("Default is a fixed set of drills, about two minutes of work. Finishing early still unlocks.", color = Palette.Mute, fontSize = 14.sp)
        Choice(Tags.CHALLENGE_DRILLS, "Drills", settings.challenge == ChallengeKind.DRILLS) {
            setChallenge(app, ChallengeKind.DRILLS)
        }
        Choice(Tags.CHALLENGE_MINDFUL, "Urge, breathe, retype", settings.challenge == ChallengeKind.MINDFUL) {
            setChallenge(app, ChallengeKind.MINDFUL)
        }
        Choice(Tags.CHALLENGE_JOURNAL, "Journal and wait", settings.challenge == ChallengeKind.JOURNAL) {
            setChallenge(app, ChallengeKind.JOURNAL)
        }
        Choice(Tags.CHALLENGE_BREATHING, "Breathing", settings.challenge == ChallengeKind.BREATHING) {
            setChallenge(app, ChallengeKind.BREATHING)
        }

        Section("Blocking")
        Text(
            "Every method that this phone allows is on. Suspending apps needs device owner or Shizuku. The accessibility guard still sends you home. healthy-launcher enforces this once it is the home screen. Phone, Settings, the keyboard, and emergency calling stay unsuspended.",
            color = Palette.Mute,
            fontSize = 14.sp,
        )
        Toggle(Tags.BLOCK_ACCESSIBILITY, "Accessibility guard", settings.accessibility) { value ->
            app.scope.launch { app.repository.updateSettings { it.copy(accessibility = value) } }
            if (value) openQuietly(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) { note = it }
        }
        Toggle(Tags.BLOCK_SUSPEND, "Suspend disallowed apps", settings.suspendPackages) { value ->
            app.scope.launch {
                app.repository.updateSettings { it.copy(suspendPackages = value) }
                app.enforcer.reconcile()
            }
        }
        Toggle(Tags.BLOCK_SHIZUKU, "Shizuku force-stop", settings.shizuku) { value ->
            app.scope.launch { app.repository.updateSettings { it.copy(shizuku = value) } }
            if (value) requestShizuku(context) { note = it }
        }
        Toggle(Tags.BLOCK_NOTIFICATIONS, "Hide blocked notifications", settings.hideNotifications) { value ->
            app.scope.launch { app.repository.updateSettings { it.copy(hideNotifications = value) } }
            if (value) openQuietly(context, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) { note = it }
        }
        Toggle(Tags.BLOCK_USAGE, "Watch the foreground app", settings.usageWatch) { value ->
            app.scope.launch { app.repository.updateSettings { it.copy(usageWatch = value) } }
            if (value) openQuietly(context, Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) { note = it }
        }
        ActionText("Ignore battery limits", "open_battery", accentFor(session.mode)) {
            openQuietly(
                context,
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")),
            ) { note = it }
        }
        ActionText("Allow exact alarms", "open_alarms", accentFor(session.mode)) {
            openQuietly(
                context,
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")),
            ) { note = it }
        }

        Section("Poco X7 Pro / HyperOS")
        Text(
            "On HyperOS, also allow autostart, unrestricted battery, and opening windows in the background. Otherwise the timer and the guard are killed.",
            color = Palette.Mute,
            fontSize = 14.sp,
        )
        ActionText("Autostart", "open_autostart", accentFor(session.mode)) {
            val intent = Intent().setComponent(
                ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity",
                ),
            )
            openQuietly(context, intent) { note = it }
        }
        ActionText("Other permissions", "open_miui_perms", accentFor(session.mode)) {
            val intent = Intent("miui.intent.action.APP_PERM_EDITOR").apply {
                component = ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.permissions.PermissionsEditorActivity",
                )
                putExtra("extra_pkgname", context.packageName)
            }
            openQuietly(context, intent) { note = it }
        }

        Section("Session")
        Text("Social mode lasts 30 minutes. You can end it early.", color = Palette.Mute, fontSize = 14.sp)
        Text("Warnings at 5 minutes and 1 minute.", color = Palette.Mute, fontSize = 14.sp)
        Text("If the phone stays locked for 5 minutes, social mode ends.", color = Palette.Mute, fontSize = 14.sp)
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
fun AppPickerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as HealthyApp
    val settings by app.repository.settings.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    val apps = remember { app.catalog.refresh() }.filter {
        it.packageName != context.packageName && it.label.contains(query, ignoreCase = true)
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 24.dp)) {
        Text("Back", color = Palette.Mute, modifier = Modifier.testTag(Tags.BACK).clickable(onClick = onBack))
        Text("Operational apps", color = Palette.Ink, fontSize = 28.sp, modifier = Modifier.padding(top = 18.dp))
        Text(
            "These stay available in normal mode. Nothing is selected until you pick it.",
            color = Palette.Mute,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
        )
        BasicTextField(
            value = query,
            onValueChange = { query = it },
            textStyle = androidx.compose.ui.text.TextStyle(color = Palette.Ink, fontSize = 16.sp),
            cursorBrush = SolidColor(accentFor(Mode.NORMAL)),
            modifier = Modifier.fillMaxWidth().testTag(Tags.PICKER_SEARCH).padding(bottom = 8.dp),
            decorationBox = { inner ->
                if (query.isEmpty()) Text("Search", color = Palette.Mute)
                inner()
            },
        )
        LazyColumn(Modifier.weight(1f)) {
            items(apps, key = { it.packageName }) { item ->
                val checked = item.packageName in settings.allowlist
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .testTag(Tags.pickRow(item.packageName))
                        .clickable {
                            app.scope.launch {
                                app.repository.updateSettings { current ->
                                    val next = current.allowlist.toMutableSet()
                                    if (checked) next.remove(item.packageName) else next.add(item.packageName)
                                    current.copy(allowlist = next)
                                }
                                app.enforcer.reconcile()
                            }
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(if (checked) "●" else "○", color = Palette.Ink)
                    Spacer(Modifier.width(14.dp))
                    Text(item.label, color = Palette.Ink)
                }
            }
        }
    }
}

@Composable
fun ChallengeScreen(onDone: () -> Unit, onCancel: () -> Unit) {
    val model: ChallengeViewModel = viewModel()
    val run = model.run
    LaunchedEffect(Unit) { if (model.run == null) model.begin() }
    val step = run?.current
    if (run == null || step == null) {
        Column(Modifier.padding(28.dp)) {
            Text("Preparing the challenge", color = Palette.Ink)
        }
        return
    }
    var answer by remember(step.id) { mutableStateOf("") }
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 24.dp)) {
        Text(
            "Step ${run.index + 1} of ${run.steps.size}",
            color = Palette.Mute,
            modifier = Modifier.testTag(Tags.PROGRESS),
        )
        Text(
            step.prompt,
            color = Palette.Ink,
            fontSize = 26.sp,
            modifier = Modifier.testTag(Tags.PROMPT).padding(top = 20.dp),
        )
        when (step.kind) {
            com.healthylauncher.domain.StepKind.MEMORY -> {
                if (!run.memoryHidden) {
                    Text(
                        step.reveal.orEmpty(),
                        color = Palette.Ink,
                        fontSize = 40.sp,
                        modifier = Modifier.testTag(Tags.REVEAL).padding(top = 28.dp),
                    )
                    ActionText("Hide them", Tags.HIDE, accentFor(Mode.NORMAL)) { model.hide() }
                } else {
                    AnswerField(answer, KeyboardType.Number, { answer = it }) { model.submit(answer, onDone) }
                }
            }
            com.healthylauncher.domain.StepKind.MATH -> {
                AnswerField(answer, KeyboardType.Number, { answer = it }) { model.submit(answer, onDone) }
            }
            com.healthylauncher.domain.StepKind.RETYPE,
            com.healthylauncher.domain.StepKind.PROSE,
            -> {
                AnswerField(answer, KeyboardType.Text, { answer = it }) { model.submit(answer, onDone) }
            }
            com.healthylauncher.domain.StepKind.BREATH,
            com.healthylauncher.domain.StepKind.WAIT,
            -> {
                val total = step.durationMs
                val elapsed = if (step.kind == com.healthylauncher.domain.StepKind.WAIT) {
                    model.now - run.startedAtElapsed
                } else {
                    model.now - run.stepStartedAtElapsed
                }
                val left = (total - elapsed).coerceAtLeast(0L)
                BreathCircle()
                Text(
                    formatRemaining(left),
                    color = Palette.Ink,
                    fontSize = 36.sp,
                    modifier = Modifier.testTag(Tags.BREATH).padding(top = 12.dp),
                )
                ActionText("Continue", Tags.SUBMIT, accentFor(Mode.NORMAL)) { model.submit("", onDone) }
            }
        }
        model.error?.let {
            Text(it, color = Palette.SocialAccent, modifier = Modifier.testTag(Tags.ERROR).padding(top = 8.dp))
        }
        Spacer(Modifier.weight(1f))
        ActionText("Cancel", Tags.CANCEL, Palette.Mute) {
            model.cancel()
            onCancel()
        }
        if (context is Activity) {
            // keeps the activity reference live for the input method
        }
    }
}

@Composable
private fun AnswerField(
    value: String,
    keyboard: KeyboardType,
    onChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    BasicTextField(
        value = value,
        onValueChange = onChange,
        textStyle = androidx.compose.ui.text.TextStyle(color = Palette.Ink, fontSize = 22.sp),
        cursorBrush = SolidColor(Palette.Ink),
        keyboardOptions = KeyboardOptions(keyboardType = keyboard, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onSubmit() }),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 28.dp)
            .testTag(Tags.ANSWER),
        decorationBox = { inner ->
            Column {
                if (value.isEmpty()) Text("Type here", color = Palette.Mute, fontSize = 22.sp)
                inner()
                Spacer(Modifier.height(8.dp))
                androidx.compose.foundation.layout.Box(
                    Modifier.fillMaxWidth().height(1.dp).padding(top = 4.dp),
                )
            }
        },
    )
    ActionText("Continue", Tags.SUBMIT, accentFor(Mode.NORMAL), onSubmit)
}

@Composable
private fun BreathCircle() {
    val transition = rememberInfiniteTransition(label = "breath")
    val scale by transition.animateFloat(
        initialValue = 0.72f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(4_000),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "scale",
    )
    Spacer(Modifier.height(32.dp))
    androidx.compose.foundation.Canvas(Modifier.size((120 * scale).dp)) {
        drawCircle(color = Palette.NormalAccent.copy(alpha = 0.85f))
    }
}

@Composable
private fun Section(title: String) {
    Text(title, color = Palette.Ink, fontSize = 18.sp, modifier = Modifier.padding(top = 28.dp, bottom = 8.dp))
}

@Composable
private fun Choice(tag: String, label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = (if (selected) "●  " else "○  ") + label,
        color = if (selected) Palette.Ink else Palette.Mute,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
    )
}

@Composable
private fun Toggle(tag: String, label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Palette.Ink, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange, modifier = Modifier.testTag(tag))
    }
}

private fun setAppearance(app: HealthyApp, appearance: Appearance) {
    app.scope.launch {
        app.repository.updateSettings { it.copy(appearance = appearance) }
        app.enforcer.reconcile()
    }
}

private fun setChallenge(app: HealthyApp, kind: ChallengeKind) {
    app.scope.launch { app.repository.updateSettings { it.copy(challenge = kind) } }
}

private fun homeIntent(context: android.content.Context): Intent? {
    if (android.os.Build.VERSION.SDK_INT >= 29) {
        val roles = context.getSystemService(android.app.role.RoleManager::class.java) ?: return null
        if (!roles.isRoleAvailable(android.app.role.RoleManager.ROLE_HOME)) return null
        return roles.createRequestRoleIntent(android.app.role.RoleManager.ROLE_HOME)
    }
    return Intent(Settings.ACTION_HOME_SETTINGS)
}

private fun openQuietly(context: android.content.Context, intent: Intent, onFail: (String) -> Unit) {
    try {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    } catch (error: ActivityNotFoundException) {
        onFail("That screen is not on this phone.")
        Toast.makeText(context, "That screen is not on this phone.", Toast.LENGTH_SHORT).show()
    } catch (error: Exception) {
        onFail("Could not open that screen.")
    }
}

private fun requestShizuku(context: android.content.Context, onFail: (String) -> Unit) {
    if (!com.healthylauncher.block.ShizukuShell.ready() && !runCatching { rikka.shizuku.Shizuku.pingBinder() }.getOrDefault(false)) {
        onFail("Start Shizuku, then come back and allow healthy-launcher.")
        return
    }
    runCatching { rikka.shizuku.Shizuku.requestPermission(42) }
        .onFailure { onFail("Shizuku did not accept the request.") }
}
