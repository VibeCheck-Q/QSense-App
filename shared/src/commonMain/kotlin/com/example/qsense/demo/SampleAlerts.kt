package com.example.qsense.demo

import com.example.qsense.domain.model.FaultAlert

/**
 * Canned fault alerts for development. Seeded into the [com.example.qsense.domain.repository.AlertStore]
 * at startup (debug builds only) so the dashboard and on-device GenieX diagnosis can be exercised
 * without a reachable MQTT broker. Not used in release builds.
 *
 * IDs are reserved (`dev-sample-*`) so they never collide with real inbound alerts, and timestamps
 * are fixed ISO-8601 (UTC) so demos stay deterministic.
 */
object SampleAlerts {
    // The single canonical monitoring payload (matches the MQTT contract + demo publish scripts).
    val all: List<FaultAlert> = listOf(
        FaultAlert(
            alertId = "e2d69c69-f6a6-4850-b76b-7912fc491e61",
            machineNo = "M-01",
            partName = "Fan Motor",
            partNo = "PN-001",
            severity = "48.896",
            timestamp = "2026-07-11T17:57:05.435079",
        ),
    )
}
