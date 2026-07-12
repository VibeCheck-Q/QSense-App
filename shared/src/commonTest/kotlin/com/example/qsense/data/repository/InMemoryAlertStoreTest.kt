package com.example.qsense.data.repository

import com.example.qsense.domain.model.FaultAlert
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InMemoryAlertStoreTest {
    private fun alert(id: String, machine: String, part: String, severity: String) =
        FaultAlert(id, machine, part, part, severity, "2026-07-12T10:00:00Z")

    @Test
    fun sameMachinePartRefreshesInPlaceInsteadOfDuplicating() {
        val store = InMemoryAlertStore()
        store.add(alert("id-1", "M-01", "Fan Motor", "7.1"))
        store.add(alert("id-2", "M-01", "Fan Motor", "52.3")) // same machine/part, new reading
        store.add(alert("id-3", "M-02", "Spindle Bearing", "5.9"))

        val items = store.alerts.value
        assertEquals(2, items.size, "same machine/part must not duplicate")
        val fan = items.first { it.alert.machineNo == "M-01" }
        assertEquals("52.3", fan.alert.severity, "reading should refresh to the latest")
        assertEquals("id-1", fan.alert.alertId, "id stays stable so selection isn't lost")
    }

    @Test
    fun firstRealAlertPurgesSeededFixtures() {
        val store = InMemoryAlertStore()
        store.seed(alert("seed-1", "MTR-07", "Blade", "48.9"))
        assertTrue(store.alerts.value.single().seeded)

        store.add(alert("real-1", "M-01", "Fan Motor", "50.0"))

        val items = store.alerts.value
        assertEquals(1, items.size, "seeded fixture should be purged by the first real alert")
        assertFalse(items.single().seeded)
        assertEquals("Fan Motor", items.single().alert.partName)
    }

    @Test
    fun markResolvedByIdStillWorks() {
        val store = InMemoryAlertStore()
        store.add(alert("id-1", "M-01", "Fan Motor", "50"))
        store.markResolved("id-1")
        assertTrue(store.alerts.value.single().resolved)
    }

    @Test
    fun reSentAlertReactivatesAResolvedSite() {
        val store = InMemoryAlertStore()
        store.add(alert("M-01", "M-01", "Fan Motor", "50"))
        store.markResolved("M-01")
        assertTrue(store.alerts.value.single().resolved, "should be resolved after markResolved")

        // The same fault recurs (re-sent) — it must show active again, not stay resolved.
        store.add(alert("M-01", "M-01", "Fan Motor", "55"))
        assertFalse(store.alerts.value.single().resolved, "a recurring alert must clear the resolved flag")
        assertEquals("55", store.alerts.value.single().alert.severity)
    }
}
