package com.example.qsense.domain.model

/** An alert as tracked by the dashboard, with its resolved state. */
data class AlertItem(
    val alert: FaultAlert,
    val resolved: Boolean = false,
)
