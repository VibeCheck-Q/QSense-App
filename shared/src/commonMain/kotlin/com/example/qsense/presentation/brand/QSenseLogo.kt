package com.example.qsense.presentation.brand

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.qsense.presentation.theme.QSenseColors

/**
 * The QSense mark, drawn in pure Compose so it stays in commonMain with no Android resource
 * plumbing: a navy rounded square, a white waveform ("sense" signal), and a coral peak dot.
 * Geometry mirrors exports/qsense-appicon-square.svg (128-unit design space), scaled to [size].
 */
@Composable
fun QSenseLogo(size: Dp = 40.dp, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(size)) {
        val s = this.size.minDimension
        val u = s / 128f // design-space unit -> px

        // Navy rounded-square background.
        drawRoundRect(color = QSenseColors.navy, cornerRadius = CornerRadius(29f * u, 29f * u))

        // White waveform polyline (closed at the baseline), matching the SVG path.
        val pts = listOf(
            34f to 92f, 34f to 52f, 48f to 66f, 48f to 52f, 64f to 66f,
            64f to 34f, 80f to 66f, 80f to 52f, 94f to 66f, 94f to 92f,
        )
        val wave = Path().apply {
            moveTo(pts.first().first * u, pts.first().second * u)
            pts.drop(1).forEach { (x, y) -> lineTo(x * u, y * u) }
            close()
        }
        drawPath(
            path = wave,
            color = Color.White,
            style = Stroke(width = 8f * u, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )

        // Coral peak dot.
        drawCircle(color = QSenseColors.coral, radius = 6f * u, center = Offset(64f * u, 34f * u))
    }
}
