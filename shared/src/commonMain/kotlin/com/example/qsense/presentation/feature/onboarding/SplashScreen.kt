package com.example.qsense.presentation.feature.onboarding

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.qsense.presentation.brand.QSenseLogo
import com.example.qsense.presentation.theme.QSenseColors
import kotlinx.coroutines.delay

/**
 * Animated splash: the badge's waveform draws itself in, the coral dot pops (spring), the wordmark
 * fades up, then it auto-advances to login. Tap anywhere to skip.
 */
@Composable
fun SplashScreen(onDone: () -> Unit) {
    val wave = remember { Animatable(0f) }
    val dot = remember { Animatable(0f) }
    val text = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        wave.animateTo(1f, tween(durationMillis = 650, delayMillis = 400))
        dot.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = Spring.StiffnessMedium))
        text.animateTo(1f, tween(500))
        delay(1100)
        onDone()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(QSenseColors.navy)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onDone() },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            QSenseLogo(size = 128.dp, waveProgress = wave.value, dotScale = dot.value)
            Spacer(Modifier.height(28.dp))
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = (14 * (1f - text.value)).dp),
            ) {
                Text(
                    "QSense",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = text.value),
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "FACTORY SIGNAL MONITOR",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 3.sp,
                    textAlign = TextAlign.Center,
                    color = SplashSub.copy(alpha = text.value),
                )
            }
        }

        Text(
            "TAP TO CONTINUE",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            letterSpacing = 2.sp,
            color = Color(0xFF4C5666).copy(alpha = text.value),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 40.dp),
        )
    }
}

private val SplashSub = Color(0xFF7F8A99)
