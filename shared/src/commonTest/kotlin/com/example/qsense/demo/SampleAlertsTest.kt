package com.example.qsense.demo

import com.example.qsense.data.repository.InMemoryAlertStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Verifies the dev fixtures are well-formed. This is fixture verification, not a mirror of the
 * (private) gateway validator — the samples bypass the gateway and go straight to the store.
 */
class SampleAlertsTest {

    private val isoUtc = Regex("""\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z""")

    @Test
    fun samplesAreWellFormed() {
        assertTrue(SampleAlerts.all.isNotEmpty(), "expected at least one sample alert")
        SampleAlerts.all.forEach { a ->
            assertTrue(a.alertId.isNotBlank(), "alertId blank")
            assertTrue(a.machineNo.isNotBlank(), "machineNo blank for ${a.alertId}")
            assertTrue(a.partNo.isNotBlank(), "partNo blank for ${a.alertId}")
            listOf(a.alertId, a.machineNo, a.partName, a.partNo, a.severity, a.timestamp)
                .forEach { assertTrue(it.length <= 200, "field over 200 chars for ${a.alertId}") }
            assertTrue(isoUtc.matches(a.timestamp), "timestamp not ISO-8601 UTC for ${a.alertId}")
        }
    }

    @Test
    fun sampleIdsAreReservedAndUnique() {
        val ids = SampleAlerts.all.map { it.alertId }
        assertEquals(ids.size, ids.toSet().size, "duplicate sample ids")
        assertTrue(ids.all { it.startsWith("dev-sample-") }, "sample ids must use the dev- prefix")
    }

    @Test
    fun seedingStoreDedupsAndPreservesCount() {
        val store = InMemoryAlertStore()
        SampleAlerts.all.forEach(store::add)
        SampleAlerts.all.forEach(store::add) // second pass must be deduped by alertId
        assertEquals(SampleAlerts.all.size, store.alerts.value.size)
        assertTrue(store.alerts.value.none { it.resolved }, "seeded alerts must be unresolved")
    }
}
