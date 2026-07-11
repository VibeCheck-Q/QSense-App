package com.example.qsense.presentation.brand

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.qsense.presentation.theme.QSenseColors

/**
 * The QSense round badge, drawn in pure Compose so it stays in commonMain with no Android
 * resource plumbing: a navy disc, white "sense" tick marks + meter box + ECG waveform, and a
 * coral peak dot. Geometry mirrors the round badge (qsense-badge.svg, 128-unit design space),
 * scaled to [size].
 *
 * [waveProgress] (0f..1f) draws the ECG waveform in progressively (via PathMeasure) and [dotScale]
 * (0f..1f) scales the coral dot — both default to 1f (fully drawn) so static callers are unchanged;
 * the splash animation drives them from 0f to 1f.
 */
@Composable
fun QSenseLogo(
    size: Dp = 40.dp,
    modifier: Modifier = Modifier,
    waveProgress: Float = 1f,
    dotScale: Float = 1f,
) {
    Canvas(modifier = modifier.size(size)) {
        val s = this.size.minDimension
        val u = s / 128f // design-space unit -> px

        // Navy disc background (circle cx64 cy64 r60).
        drawCircle(color = QSenseColors.navy, radius = 60f * u, center = Offset(64f * u, 64f * u))

        // Six white tick marks (top + bottom rows).
        val tickStroke = 5f * u
        listOf(50f, 64f, 78f).forEach { x ->
            drawLine(Color.White, Offset(x * u, 34f * u), Offset(x * u, 44f * u), tickStroke, StrokeCap.Round)
            drawLine(Color.White, Offset(x * u, 84f * u), Offset(x * u, 94f * u), tickStroke, StrokeCap.Round)
        }

        // White meter box (rounded rect x42 y44 w44 h40 rx9), stroked.
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(42f * u, 44f * u),
            size = Size(44f * u, 40f * u),
            cornerRadius = CornerRadius(9f * u, 9f * u),
            style = Stroke(width = 6f * u, join = StrokeJoin.Round),
        )

        // White ECG waveform inside the box, drawn in up to waveProgress via PathMeasure.
        val ecg = listOf(
            50f to 65f, 57f to 65f, 61f to 55f, 67f to 75f, 71f to 65f, 78f to 65f,
        )
        val wave = Path().apply {
            moveTo(ecg.first().first * u, ecg.first().second * u)
            ecg.drop(1).forEach { (x, y) -> lineTo(x * u, y * u) }
        }
        val pm = PathMeasure().apply { setPath(wave, false) }
        val clamped = waveProgress.coerceIn(0f, 1f)
        if (pm.length > 0f && clamped > 0f) {
            val drawn = Path()
            pm.getSegment(0f, pm.length * clamped, drawn, true)
            drawPath(
                path = drawn,
                color = Color.White,
                style = Stroke(width = 4.5f * u, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }

        // Coral peak dot (cx78 cy52 r3.6), scaled by dotScale for the pop.
        if (dotScale > 0f) {
            drawCircle(color = QSenseColors.coral, radius = 3.6f * u * dotScale, center = Offset(78f * u, 52f * u))
        }
    }
}
