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
    val all: List<FaultAlert> = listOf(
        // A single motor anomaly on the blade, with hot/humid sensor readings that exercise the
        // color coding and feed the diagnosis prompt as operating context.
        FaultAlert(
            alertId = "dev-sample-001",
            machineNo = "MTR-07",
            partName = "Blade",
            partNo = "BLD-330",
            severity = "high",
            timestamp = "2026-07-11T10:30:00Z",
            temperature = 78.0f,
            humidity = 82.0f,
        ),
    )
}
