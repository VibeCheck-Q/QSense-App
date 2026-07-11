package com.example.qsense.data.serialization

import com.example.qsense.domain.model.FaultAlert
import com.example.qsense.domain.model.Resolution
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertEquals

class MqttPayloadTest {
    private val json = JsonProviders.strict

    @Test
    fun faultAlertRoundTrips() {
        val alert = FaultAlert(
            alertId = "a1",
            machineNo = "M-101",
            partName = "Hydraulic Pump",
            partNo = "HP-2045",
            severity = "high",
            timestamp = "2026-07-11T10:30:00Z",
        )
        val decoded = json.decodeFromString<FaultAlert>(json.encodeToString(alert))
        assertEquals(alert, decoded)
    }

    @Test
    fun resolutionRoundTrips() {
        val resolution = Resolution(
            alertId = "a1",
            machineNo = "M-101",
            partNo = "HP-2045",
            chosenCause = "Seal degradation",
            appliedFix = "Replaced seal kit",
            notes = "Leak at flange",
            resolvedAt = "2026-07-11T10:45:00Z",
        )
        val decoded = json.decodeFromString<Resolution>(json.encodeToString(resolution))
        assertEquals(resolution, decoded)
    }

    @Test
    fun faultAlertWithSensorReadingsRoundTrips() {
        val alert = FaultAlert(
            alertId = "a1",
            machineNo = "MTR-07",
            partName = "Blade",
            partNo = "BLD-330",
            severity = "high",
            timestamp = "2026-07-11T10:30:00Z",
            temperature = 78.0f,
            humidity = 82.0f,
        )
        val decoded = json.decodeFromString<FaultAlert>(json.encodeToString(alert))
        assertEquals(alert, decoded)
    }

    @Test
    fun faultAlertWithoutSensorReadingsDecodesToNulls() {
        val raw = """
            {"alertId":"a1","machineNo":"M-101","partName":"Pump","partNo":"HP-1",
             "severity":"low","timestamp":"2026-07-11T10:30:00Z"}
        """.trimIndent()
        val decoded = json.decodeFromString<FaultAlert>(raw)
        assertEquals(null, decoded.temperature)
        assertEquals(null, decoded.humidity)
    }

    @Test
    fun ignoresUnknownKeysInInboundAlert() {
        val raw = """
            {"alertId":"a1","machineNo":"M-101","partName":"Pump","partNo":"HP-1",
             "severity":"low","timestamp":"2026-07-11T10:30:00Z","extraField":"ignored"}
        """.trimIndent()
        val decoded = json.decodeFromString<FaultAlert>(raw)
        assertEquals("a1", decoded.alertId)
    }
}
