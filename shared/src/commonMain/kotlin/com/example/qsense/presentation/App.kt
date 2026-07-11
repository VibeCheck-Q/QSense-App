package com.example.qsense.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.qsense.di.AppContainer
import com.example.qsense.presentation.feature.dashboard.DashboardScreen
import com.example.qsense.presentation.theme.AppTheme

@Composable
fun App(container: AppContainer, typography: Typography = Typography()) {
    AppTheme(typography = typography) {
        DashboardScreen(
            container = container,
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .fillMaxSize(),
        )
    }
}
