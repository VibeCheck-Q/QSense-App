package com.example.qsense.presentation.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontFamily

/**
 * Builds the QSense Material3 [Typography]: Clash Display for display/headline/title (glance-worthy
 * headings and numbers), Satoshi for body text. The [clash] and [satoshi] families are provided by
 * the platform layer (Android font resources) so this stays in commonMain. Sizes/line-heights are
 * kept at Material defaults; only the font family is swapped.
 *
 * Lives in shared (which owns the Compose Multiplatform material3 dependency) so the Typography
 * constructor resolves against the correct material3 version.
 */
fun buildQSenseTypography(clash: FontFamily, satoshi: FontFamily): Typography {
    val d = Typography()
    return Typography(
        displayLarge = d.displayLarge.copy(fontFamily = clash),
        displayMedium = d.displayMedium.copy(fontFamily = clash),
        displaySmall = d.displaySmall.copy(fontFamily = clash),
        headlineLarge = d.headlineLarge.copy(fontFamily = clash),
        headlineMedium = d.headlineMedium.copy(fontFamily = clash),
        headlineSmall = d.headlineSmall.copy(fontFamily = clash),
        titleLarge = d.titleLarge.copy(fontFamily = clash),
        titleMedium = d.titleMedium.copy(fontFamily = clash),
        titleSmall = d.titleSmall.copy(fontFamily = clash),
        bodyLarge = d.bodyLarge.copy(fontFamily = satoshi),
        bodyMedium = d.bodyMedium.copy(fontFamily = satoshi),
        bodySmall = d.bodySmall.copy(fontFamily = satoshi),
    )
}
