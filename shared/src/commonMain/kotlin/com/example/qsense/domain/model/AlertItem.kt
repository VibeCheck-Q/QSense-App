package com.example.qsense.domain.model

/**
 * An alert as tracked by the dashboard, with its resolved state. [seeded] marks dev/demo fixtures
 * so they can be purged the moment real MQTT data arrives.
 */
data class AlertItem(
    val alert: FaultAlert,
    val resolved: Boolean = false,
    val seeded: Boolean = false,
)
