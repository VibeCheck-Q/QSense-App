package com.example.qsense.presentation.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * QSense Factory design system (v2, light/minimal). Applies the token colors, uniform radii, and
 * (Android-provided) Clash Display + Satoshi typography. [typography] is injected by the platform
 * layer so the font families can live in Android resources; it defaults to Material's typography.
 */
@Composable
fun AppTheme(
    typography: Typography = Typography(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = QSenseColors.scheme,
        shapes = QSenseShapes,
        typography = typography,
        content = content,
    )
}

private val QSenseShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(18.dp),
)

/**
 * Design-system colors. [scheme] is the neutral + primary Material palette; the four pipeline-stage
 * accents (coral/amber/slate/sage) live outside the Material scheme because it can't carry four
 * semantic accents. Per the design guardrail, accents are used only on status dots, badges, and
 * accent bars — never as page backgrounds or button fills.
 */
object QSenseColors {
    val ink = Color(0xFF17191C)
    val inkSoft = Color(0xFF6B6E72)
    val border = Color(0xFFE8E8E5)
    val bg = Color(0xFFFFFFFF)
    val bgSoft = Color(0xFFF7F7F6)
    val tealCta = Color(0xFF1F6F68)
    val navy = Color(0xFF1E2A38)

    // Pipeline-stage accents: Detect / Alert / Diagnose / Resolve.
    val coral = Color(0xFFEA6F56)
    val amber = Color(0xFFF0B94D)
    val slate = Color(0xFF445067)
    val sage = Color(0xFF6FA980)
    val off = Color(0xFFC7C9C6)
    val coralSoft = Color(0xFFFBE6E0)
    val amberSoft = Color(0xFFFCF1DC)
    val sageSoft = Color(0xFFE6F1E9)

    val scheme = lightColorScheme(
        primary = tealCta,
        onPrimary = Color.White,
        background = bg,
        onBackground = ink,
        surface = bg,
        onSurface = ink,
        surfaceVariant = bgSoft,
        onSurfaceVariant = inkSoft,
        outline = border,
        outlineVariant = border,
        error = coral,
        onError = Color.White,
    )
}

/** Sensor-reading colors (demo thresholds), used for the temperature/humidity readouts. */
fun temperatureColor(celsius: Float): Color = when {
    celsius > 70f -> QSenseColors.coral
    celsius >= 45f -> QSenseColors.amber
    else -> QSenseColors.sage
}

fun humidityColor(percent: Float): Color = when {
    percent > 80f -> QSenseColors.coral
    percent >= 60f -> QSenseColors.amber
    else -> QSenseColors.sage
}
