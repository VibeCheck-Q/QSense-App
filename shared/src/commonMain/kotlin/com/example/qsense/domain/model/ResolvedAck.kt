package com.example.qsense.domain.model

import kotlinx.serialization.Serializable

/**
 * Minimal ack published on the ack topic across the alert lifecycle: `resolved = 0` when an alert
 * arrives, `1` when the operator resolves it. Serializes to {"alertId":"…","resolved":0|1}.
 */
@Serializable
data class ResolvedAck(
    val alertId: String,
    val resolved: Int,
)
