package com.example.qsense.presentation.feature.vision

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Live camera preview with a capture control. On capture, delivers a downscaled (longest side
 * <= 640px) JPEG encoded as base64. Android-only (CameraX); provided via actual in androidMain.
 */
@Composable
expect fun CameraCapture(onCaptured: (imageB64: String) -> Unit, modifier: Modifier)

/** Decodes and displays a base64-encoded JPEG (the annotated result). Android-only. */
@Composable
expect fun AnnotatedImage(imageB64: String, modifier: Modifier)
