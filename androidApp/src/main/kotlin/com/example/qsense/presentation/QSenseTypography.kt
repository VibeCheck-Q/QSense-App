package com.example.qsense.presentation

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.example.qsense.R
import com.example.qsense.presentation.theme.buildQSenseTypography

private val ClashDisplay = FontFamily(
    Font(R.font.clashdisplay_regular, FontWeight.Normal),
    Font(R.font.clashdisplay_medium, FontWeight.Medium),
    Font(R.font.clashdisplay_semibold, FontWeight.SemiBold),
    Font(R.font.clashdisplay_bold, FontWeight.Bold),
)

private val Satoshi = FontFamily(
    Font(R.font.satoshi_regular, FontWeight.Normal),
    Font(R.font.satoshi_medium, FontWeight.Medium),
    Font(R.font.satoshi_bold, FontWeight.Bold),
)

/** Android entry point: builds the QSense Typography from the bundled Clash Display + Satoshi fonts. */
fun qsenseTypography(): Typography = buildQSenseTypography(ClashDisplay, Satoshi)
