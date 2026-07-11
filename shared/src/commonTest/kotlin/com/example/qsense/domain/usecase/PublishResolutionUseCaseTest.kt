package com.example.qsense.domain.usecase

import com.example.qsense.data.repository.InMemoryAlertStore
import com.example.qsense.domain.model.FaultAlert
import com.example.qsense.testutil.FakeMqttGateway
import com.example.qsense.testutil.FixedClock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PublishResolutionUseCaseTest {
    private val alert = FaultAlert("a1", "M-101", "Pump", "HP-1", "high", "2026-07-11T10:30:00Z")

    @Test
    fun publishesResolutionAndMarksResolved() = runTest {
        val gateway = FakeMqttGateway()
        val store = InMemoryAlertStore().apply { add(alert) }
        val useCase = PublishResolutionUseCase(gateway, store, FixedClock("2026-07-11T10:45:00Z"))

        useCase("a1", "M-101", "HP-1", "Seal degradation", "Replace seal", "notes")

        assertEquals(1, gateway.published.size)
        val published = gateway.published.first()
        assertEquals("Seal degradation", published.chosenCause)
        assertEquals("Replace seal", published.appliedFix)
        assertEquals("2026-07-11T10:45:00Z", published.resolvedAt)
        assertTrue(store.alerts.value.first { it.alert.alertId == "a1" }.resolved)
    }

    @Test
    fun doesNotMarkResolvedWhenPublishFails() = runTest {
        val gateway = FakeMqttGateway().apply { publishError = RuntimeException("broker down") }
        val store = InMemoryAlertStore().apply { add(alert) }
        val useCase = PublishResolutionUseCase(gateway, store, FixedClock())

        assertFailsWith<RuntimeException> {
            useCase("a1", "M-101", "HP-1", "cause", "fix", "notes")
        }
        assertFalse(store.alerts.value.first { it.alert.alertId == "a1" }.resolved)
    }
}
