package com.example.qsense.domain.model

import com.example.qsense.data.serialization.FlexibleStringSerializer
import kotlinx.serialization.Serializable

/**
 * Inbound fault alert received over MQTT (machine no + faulty part).
 *
 * [severity] is decoded flexibly: the monitoring topic sends it as a numeric anomaly score
 * (e.g. `48.896`), other producers send a label (`"high"`) — both land here as a string.
 * [temperature] (°C) and [humidity] (%) are optional sensor readings: nullable with defaults so
 * payloads that omit them still decode. When present they are fed into the diagnosis prompt as
 * operating context and shown, color-coded, on the dashboard.
 */
@Serializable
data class FaultAlert(
    val alertId: String,
    val machineNo: String,
    val partName: String,
    val partNo: String,
    @Serializable(with = FlexibleStringSerializer::class)
    val severity: String,
    val timestamp: String,
    val temperature: Float? = null,
    val humidity: Float? = null,
)
