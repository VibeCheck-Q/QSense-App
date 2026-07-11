package com.example.qsense.data.serialization

import com.example.qsense.domain.model.FaultAlert
import com.example.qsense.domain.model.Resolution
import com.example.qsense.domain.model.ResolvedAck
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
    fun resolvedAckSerializesToExactMinimalPayload() {
        assertEquals(
            """{"alertId":"a1","resolved":1}""",
            json.encodeToString(ResolvedAck("a1", resolved = 1)),
        )
        assertEquals(
            """{"alertId":"a1","resolved":0}""",
            json.encodeToString(ResolvedAck("a1", resolved = 0)),
        )
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
    fun decodesMonitoringPayloadWithNumericSeverityAndLocalTimestamp() {
        // The canonical monitoring payload: numeric severity, microsecond local timestamp (no Z).
        val raw = """
            {"alertId":"e2d69c69-f6a6-4850-b76b-7912fc491e61","machineNo":"M-01",
             "partName":"Fan Motor","partNo":"PN-001","severity":48.896,
             "timestamp":"2026-07-11T17:57:05.435079"}
        """.trimIndent()
        val decoded = json.decodeFromString<FaultAlert>(raw)
        assertEquals("Fan Motor", decoded.partName)
        assertEquals("48.896", decoded.severity)
        assertEquals("2026-07-11T17:57:05.435079", decoded.timestamp)
    }

    @Test
    fun decodesStringSeverityToo() {
        val raw = """{"alertId":"a1","machineNo":"M-1","partName":"P","partNo":"PN","severity":"high","timestamp":"2026-07-11T10:30:00Z"}"""
        assertEquals("high", json.decodeFromString<FaultAlert>(raw).severity)
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
