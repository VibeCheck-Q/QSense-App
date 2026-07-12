package com.example.qsense.presentation

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.qsense.di.AppContainer
import com.example.qsense.presentation.feature.dashboard.DashboardScreen
import com.example.qsense.presentation.feature.onboarding.LoginScreen
import com.example.qsense.presentation.feature.onboarding.SplashScreen
import com.example.qsense.presentation.feature.vision.VisionScreen
import com.example.qsense.presentation.theme.AppTheme

private enum class Screen { Splash, Login, Dashboard, Vision }

@Composable
fun App(container: AppContainer, typography: Typography = Typography()) {
    AppTheme(typography = typography) {
        // Saved across config changes (e.g. rotation) so we don't bounce back to the splash mid-use.
        var screenName by rememberSaveable { mutableStateOf(Screen.Splash.name) }
        val screen = Screen.valueOf(screenName)

        Crossfade(targetState = screen, animationSpec = tween(400), label = "flow") { s ->
            when (s) {
                Screen.Splash -> SplashScreen(onDone = { screenName = Screen.Login.name })
                Screen.Login -> LoginScreen(onSignIn = { _, _ -> screenName = Screen.Dashboard.name })
                Screen.Dashboard -> DashboardScreen(
                    container = container,
                    onOpenVision = { screenName = Screen.Vision.name },
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .fillMaxSize(),
                )
                Screen.Vision -> VisionScreen(
                    container = container,
                    onBack = { screenName = Screen.Dashboard.name },
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .fillMaxSize(),
                )
            }
        }
    }
}
