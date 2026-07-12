package com.example.qsense.integration

import com.example.qsense.data.repository.InMemoryAlertStore
import com.example.qsense.di.AppContainer
import com.example.qsense.domain.model.FaultAlert
import com.example.qsense.presentation.feature.dashboard.DashboardViewModel
import com.example.qsense.presentation.feature.dashboard.DiagnosisState
import com.example.qsense.presentation.feature.dashboard.ResolveState
import com.example.qsense.testutil.FakeMqttGateway
import com.example.qsense.testutil.FakeTextGenerator
import com.example.qsense.testutil.FixedClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Automated end-to-end flow with real use cases, AppContainer, parser, AlertStore, and
 * ViewModel wired together; only the two platform services (LLM, MQTT) are faked.
 * Alert arrives -> ingested -> diagnosed -> resolved -> resolution captured.
 */
class EndToEndFlowTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test
    fun alertInDiagnosisResolutionOut() = runTest(dispatcher) {
        val generator = FakeTextGenerator(
            response = """{"causes":[{"cause":"Seal degradation","fix":"Replace seal kit"}]}""",
        )
        val gateway = FakeMqttGateway()
        val store = InMemoryAlertStore()
        val container = AppContainer(generator, gateway, store, FixedClock("2026-07-11T10:45:00Z"), dispatcher)

        // Ingestion runs for the app's lifetime (cancelled at the end of the test).
        val ingestJob = launch { container.ingestAlertsUseCase() }
        val vm = DashboardViewModel(container)
        advanceUntilIdle()

        // An alert arrives over MQTT.
        gateway.alertsFlow.emit(
            FaultAlert("a1", "M-101", "Hydraulic Pump", "HP-2045", "high", "2026-07-11T10:30:00Z"),
        )
        advanceUntilIdle()
        assertEquals(1, store.alerts.value.size, "ingestion should reach the store")
        assertEquals(1, vm.uiState.value.alerts.size, "VM should observe the store")

        // Operator opens it and a diagnosis is generated.
        vm.onSelectAlert("a1")
        advanceUntilIdle()
        val diagnosis = vm.uiState.value.diagnosis
        assertTrue(diagnosis is DiagnosisState.Ready)
        assertEquals("Seal degradation", diagnosis.diagnosis.causes[0].cause)

        // Operator resolves; the resolution is published back. Resolve is gated on confirming the fix.
        vm.onSelectCause(0)
        vm.onNotesChange("Confirmed leak at inlet flange")
        vm.onFixConfirmedChange(true)
        vm.onResolve()
        advanceTimeBy(100) // publish completes; before the 5s auto-dismiss window

        assertEquals(ResolveState.Done, vm.uiState.value.resolve)
        assertEquals(1, gateway.published.size)
        val resolution = gateway.published.first()
        assertEquals("a1", resolution.alertId)
        assertEquals("HP-2045", resolution.partNo)
        assertEquals("Seal degradation", resolution.chosenCause)
        assertEquals("Replace seal kit", resolution.appliedFix)
        assertEquals("2026-07-11T10:45:00Z", resolution.resolvedAt)
        assertTrue(store.alerts.value.first().resolved)

        // After the timeout the resolved alert auto-dismisses back to live monitoring.
        advanceUntilIdle()
        assertTrue(store.alerts.value.isEmpty())

        ingestJob.cancel()
    }
}
