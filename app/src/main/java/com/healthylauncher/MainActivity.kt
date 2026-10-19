package com.healthylauncher

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.healthylauncher.ui.AppPickerScreen
import com.healthylauncher.ui.ChallengeScreen
import com.healthylauncher.ui.HealthyTheme
import com.healthylauncher.ui.HomeScreen
import com.healthylauncher.ui.SettingsScreen
import kotlinx.coroutines.flow.MutableSharedFlow

class MainActivity : ComponentActivity() {
    private val homePulse = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        setContent {
            val app = application as HealthyApp
            val session by app.repository.session.collectAsStateWithLifecycle()
            val nav = rememberNavController()
            LaunchedEffect(nav) {
                homePulse.collect {
                    nav.popBackStack(route = "home", inclusive = false)
                }
            }
            HealthyTheme(mode = session.mode) {
                NavHost(navController = nav, startDestination = "home") {
                    composable("home") {
                        HomeScreen(
                            onSettings = { nav.navigate("settings") },
                            onChallenge = { nav.navigate("challenge") },
                        )
                    }
                    composable("settings") {
                        SettingsScreen(
                            onBack = { nav.popBackStack() },
                            onApps = { nav.navigate("apps") },
                        )
                    }
                    composable("apps") {
                        AppPickerScreen(onBack = { nav.popBackStack() })
                    }
                    composable("challenge") {
                        ChallengeScreen(
                            onDone = {
                                nav.popBackStack(route = "home", inclusive = false)
                            },
                            onCancel = { nav.popBackStack() },
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) {
            homePulse.tryEmit(Unit)
        }
    }
}
