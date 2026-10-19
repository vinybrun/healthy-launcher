package com.healthylauncher.ui

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import com.healthylauncher.domain.Mode

object Palette {
    val NormalBg = Color(0xFF0E0F12)
    val SocialBg = Color(0xFF16130F)
    val Ink = Color(0xFFF3F3F1)
    val Mute = Color(0xFF8E8E8A)
    val Line = Color(0xFF2C2C2A)
    val NormalAccent = Color(0xFFB7C4CE)
    val SocialAccent = Color(0xFFD7C4A8)
}

fun backgroundFor(mode: Mode): Color = if (mode == Mode.SOCIAL) Palette.SocialBg else Palette.NormalBg

fun accentFor(mode: Mode): Color = if (mode == Mode.SOCIAL) Palette.SocialAccent else Palette.NormalAccent

@Composable
fun HealthyTheme(mode: Mode, content: @Composable () -> Unit) {
    val background = backgroundFor(mode)
    val view = LocalView.current
    SideEffect {
        val window = (view.context as Activity).window
        window.statusBarColor = background.toArgb()
        window.navigationBarColor = background.toArgb()
        window.decorView.setBackgroundColor(background.toArgb())
    }
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = background,
            surface = background,
            primary = accentFor(mode),
            onPrimary = Palette.NormalBg,
            onBackground = Palette.Ink,
            onSurface = Palette.Ink,
        ),
        typography = Typography(
            bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 20.sp, color = Palette.Ink),
            bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, color = Palette.Ink),
            labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, color = Palette.Ink),
        ),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(background)
                .semantics { testTagsAsResourceId = true },
        ) {
            Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                content()
            }
        }
    }
}
