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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.qsense.di.AppContainer
import com.example.qsense.presentation.feature.dashboard.DashboardScreen
import com.example.qsense.presentation.feature.onboarding.LoginScreen
import com.example.qsense.presentation.feature.onboarding.SplashScreen
import com.example.qsense.presentation.theme.AppTheme

private enum class Screen { Splash, Login, Dashboard }

@Composable
fun App(container: AppContainer, typography: Typography = Typography()) {
    AppTheme(typography = typography) {
        var screen by remember { mutableStateOf(Screen.Splash) }

        Crossfade(targetState = screen, animationSpec = tween(400), label = "flow") { s ->
            when (s) {
                Screen.Splash -> SplashScreen(onDone = { screen = Screen.Login })
                Screen.Login -> LoginScreen(onSignIn = { _, _ -> screen = Screen.Dashboard })
                Screen.Dashboard -> DashboardScreen(
                    container = container,
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .fillMaxSize(),
                )
            }
        }
    }
}
