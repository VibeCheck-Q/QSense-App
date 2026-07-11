package com.example.qsense.domain.usecase

import com.example.qsense.data.repository.InMemoryAlertStore
import com.example.qsense.domain.model.FaultAlert
import com.example.qsense.domain.model.ResolvedAck
import com.example.qsense.testutil.FakeMqttGateway
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class IngestAlertsUseCaseTest {
    private val alert = FaultAlert("a1", "M-101", "Pump", "HP-1", "high", "2026-07-11T10:30:00Z")

    @Test
    fun addsAlertAndPublishesUnresolvedAckOnArrival() = runTest {
        val gateway = FakeMqttGateway()
        val store = InMemoryAlertStore()
        val useCase = IngestAlertsUseCase(gateway, store)

        val job = launch { useCase() }
        gateway.alertsFlow.emit(alert)
        advanceUntilIdle()

        assertTrue(store.alerts.value.any { it.alert.alertId == "a1" })
        assertEquals(listOf(ResolvedAck("a1", resolved = false)), gateway.publishedAcks)
        job.cancel()
    }

    @Test
    fun ackFailureOnArrivalStillKeepsTheAlert() = runTest {
        val gateway = FakeMqttGateway().apply { ackError = RuntimeException("broker down") }
        val store = InMemoryAlertStore()
        val useCase = IngestAlertsUseCase(gateway, store)

        val job = launch { useCase() }
        gateway.alertsFlow.emit(alert)
        advanceUntilIdle()

        // Best-effort ack: the failure is swallowed, the alert is still ingested.
        assertTrue(store.alerts.value.any { it.alert.alertId == "a1" })
        job.cancel()
    }
}
