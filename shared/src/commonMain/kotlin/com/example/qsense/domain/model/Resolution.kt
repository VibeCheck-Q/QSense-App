package com.example.qsense.domain.model

import kotlinx.serialization.Serializable

/** Outbound acknowledgement published when an operator resolves an alert. */
@Serializable
data class Resolution(
    val alertId: String,
    val machineNo: String,
    val partNo: String,
    val chosenCause: String,
    val appliedFix: String,
    val notes: String,
    val resolvedAt: String,
)
