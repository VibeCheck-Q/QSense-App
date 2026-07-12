package com.example.qsense.domain.model

import kotlinx.serialization.Serializable

/** Published by the phone on the vision request topic; carries a 640px-max JPEG as base64. */
@Serializable
data class VisionRequest(
    val requestId: String,
    val machineNo: String,
    val partNo: String,
    val imageB64: String,
    val timestamp: String,
)

/** One detected object in the returned annotated image. [box] is [x1, y1, x2, y2] in pixels. */
@Serializable
data class Detection(
    val cls: String,
    val score: Float,
    val box: List<Int>,
)

/** Returned by the PC service on the vision response topic; correlated by [requestId]. */
@Serializable
data class VisionResponse(
    val requestId: String,
    val annotatedImageB64: String,
    val detections: List<Detection>,
    val diagnosis: String,
)
