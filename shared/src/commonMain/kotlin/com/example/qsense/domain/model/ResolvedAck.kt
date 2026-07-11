package com.example.qsense.domain.model

import kotlinx.serialization.Serializable

/**
 * Minimal ack published on the ack topic alongside the alert lifecycle: `resolved = false` when
 * an alert arrives, `resolved = true` when the operator resolves it. Serializes to
 * {"alertId":"…","resolved":true|false}.
 */
@Serializable
data class ResolvedAck(
    val alertId: String,
    val resolved: Boolean,
)
