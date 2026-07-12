package com.example.qsense.presentation.feature.dashboard

import com.example.qsense.data.repository.InMemoryAlertStore
import com.example.qsense.di.AppContainer
import com.example.qsense.domain.model.FaultAlert
import com.example.qsense.domain.service.ConnectionState
import com.example.qsense.domain.service.ModelStatus
import com.example.qsense.testutil.FakeMqttGateway
import com.example.qsense.testutil.FakeTextGenerator
import com.example.qsense.testutil.FixedClock
import kotlinx.coroutines.Dispatchers
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DashboardViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val alert = FaultAlert("a1", "M-101", "Pump", "HP-1", "high", "2026-07-11T10:30:00Z")

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private fun container(
        generator: FakeTextGenerator = FakeTextGenerator(),
        gateway: FakeMqttGateway = FakeMqttGateway(),
        store: InMemoryAlertStore = InMemoryAlertStore(),
    ) = AppContainer(generator, gateway, store, FixedClock(), dispatcher)

    @Test
    fun propagatesModelAndConnectionState() = runTest(dispatcher) {
        val generator = FakeTextGenerator().apply { setStatus(ModelStatus.Loading) }
        val gateway = FakeMqttGateway().apply { setConnectionState(ConnectionState.Connecting) }
        val vm = DashboardViewModel(container(generator, gateway))
        advanceUntilIdle()

        assertEquals(ModelStatus.Loading, vm.uiState.value.modelStatus)
        assertEquals(ConnectionState.Connecting, vm.uiState.value.connectionState)
    }

    @Test
    fun selectingAlertGeneratesDiagnosis() = runTest(dispatcher) {
        val store = InMemoryAlertStore().apply { add(alert) }
        val vm = DashboardViewModel(container(store = store))
        advanceUntilIdle()

        vm.onSelectAlert("a1")
        advanceUntilIdle()

        val diagnosis = vm.uiState.value.diagnosis
        assertTrue(diagnosis is DiagnosisState.Ready)
        assertEquals(1, diagnosis.diagnosis.causes.size)
    }

    @Test
    fun resolvePublishesAndMarksResolved() = runTest(dispatcher) {
        val gateway = FakeMqttGateway()
        val store = InMemoryAlertStore().apply { add(alert) }
        val vm = DashboardViewModel(container(gateway = gateway, store = store))
        advanceUntilIdle()

        vm.onSelectAlert("a1")
        advanceUntilIdle()
        vm.onSelectCause(0)
        vm.onNotesChange("checked flange")
        vm.onFixConfirmedChange(true)
        vm.onResolve()
        advanceTimeBy(100) // publish completes; the 5s auto-dismiss window has not elapsed yet

        assertEquals(1, gateway.published.size)
        assertEquals("checked flange", gateway.published.first().notes)
        assertEquals(ResolveState.Done, vm.uiState.value.resolve)
        assertTrue(store.alerts.value.first { it.alert.alertId == "a1" }.resolved)
    }

    @Test
    fun onlyActualAlertsAreShownNotOkReadings() = runTest(dispatcher) {
        val okReading = alert.copy(alertId = "ok1", machineNo = "M-OK", severity = "3.0")
        val criticalAlert = alert.copy(alertId = "c1", machineNo = "M-CRIT", severity = "80.0")
        val store = InMemoryAlertStore().apply { add(okReading); add(criticalAlert) }
        val vm = DashboardViewModel(container(store = store))
        advanceUntilIdle()

        val shown = vm.uiState.value.alerts.map { it.alert.alertId }
        assertTrue("c1" in shown, "a critical reading is an alert and must show")
        assertTrue("ok1" !in shown, "an OK reading is live data, not an alert — must not show")
    }

    @Test
    fun stillFaultingMachineIsNotAutoDismissed() = runTest(dispatcher) {
        val store = InMemoryAlertStore().apply { add(alert) } // "high" → CRITICAL
        val gateway = FakeMqttGateway()
        val vm = DashboardViewModel(container(gateway = gateway, store = store))
        advanceUntilIdle()

        vm.onSelectAlert("a1")
        advanceUntilIdle()
        vm.onSelectCause(0)
        vm.onFixConfirmedChange(true)
        vm.onResolve()
        advanceTimeBy(2_000) // inside the 5s window

        // The machine keeps faulting: a new critical reading recurs for the same machine/part.
        store.add(alert.copy(alertId = "recur", severity = "90"))
        advanceUntilIdle() // past the 5s window

        assertTrue(
            store.alerts.value.any { it.alert.machineNo == "M-101" },
            "a machine still in fault must stay visible, not be auto-dismissed after resolve",
        )
    }

    @Test
    fun resolvedAlertAutoDismissesAfterTimeout() = runTest(dispatcher) {
        val store = InMemoryAlertStore().apply { add(alert) }
        val vm = DashboardViewModel(container(store = store))
        advanceUntilIdle()

        vm.onSelectAlert("a1")
        advanceUntilIdle()
        vm.onSelectCause(0)
        vm.onFixConfirmedChange(true)
        vm.onResolve()

        advanceTimeBy(4_000) // still inside the 5s confirmation window
        assertEquals(1, store.alerts.value.size, "resolved alert stays during the confirmation window")

        advanceUntilIdle() // past the 5s window
        assertTrue(store.alerts.value.isEmpty(), "resolved alert is auto-dismissed after the timeout")
        assertEquals(null, vm.uiState.value.selectedAlertId, "panel resets to live monitoring")
    }

    @Test
    fun publishFailureSurfacesErrorAndKeepsUnresolved() = runTest(dispatcher) {
        val gateway = FakeMqttGateway().apply { publishError = RuntimeException("broker down") }
        val store = InMemoryAlertStore().apply { add(alert) }
        val vm = DashboardViewModel(container(gateway = gateway, store = store))
        advanceUntilIdle()

        vm.onSelectAlert("a1")
        advanceUntilIdle()
        vm.onSelectCause(0)
        vm.onFixConfirmedChange(true)
        vm.onResolve()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.resolve is ResolveState.Error)
        assertTrue(!store.alerts.value.first { it.alert.alertId == "a1" }.resolved)
    }

    @Test
    fun defersGenerationUntilModelReady() = runTest(dispatcher) {
        val generator = FakeTextGenerator().apply { setStatus(ModelStatus.Loading) }
        val store = InMemoryAlertStore().apply { add(alert) }
        val vm = DashboardViewModel(container(generator = generator, store = store))
        advanceUntilIdle()

        vm.onSelectAlert("a1")
        advanceUntilIdle()
        assertTrue(vm.uiState.value.diagnosis is DiagnosisState.Generating)

        generator.setStatus(ModelStatus.Ready)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.diagnosis is DiagnosisState.Ready)
    }

    @Test
    fun ignoresReselectingSameAlert() = runTest(dispatcher) {
        val store = InMemoryAlertStore().apply { add(alert) }
        val vm = DashboardViewModel(container(store = store))
        advanceUntilIdle()

        vm.onSelectAlert("a1")
        advanceUntilIdle()
        vm.onSelectCause(0)
        vm.onSelectAlert("a1") // reselect same alert — must not reset the panel

        assertEquals(0, vm.uiState.value.selectedCauseIndex)
    }

    @Test
    fun resolveBlockedUntilFixConfirmed() = runTest(dispatcher) {
        val gateway = FakeMqttGateway()
        val store = InMemoryAlertStore().apply { add(alert) }
        val vm = DashboardViewModel(container(gateway = gateway, store = store))
        advanceUntilIdle()

        vm.onSelectAlert("a1")
        advanceUntilIdle()
        vm.onSelectCause(0)

        // A cause is picked but the box is unchecked: resolve is blocked and nothing publishes.
        assertFalse(vm.uiState.value.canResolve)
        vm.onResolve()
        advanceUntilIdle()
        assertEquals(0, gateway.published.size)

        // Ticking the box enables resolve; now it publishes.
        vm.onFixConfirmedChange(true)
        assertTrue(vm.uiState.value.canResolve)
        vm.onResolve()
        advanceUntilIdle()
        assertEquals(1, gateway.published.size)
    }

    @Test
    fun fixConfirmationResetsWhenSwitchingAlert() = runTest(dispatcher) {
        val second = alert.copy(alertId = "a2", machineNo = "M-202")
        val store = InMemoryAlertStore().apply { add(alert); add(second) }
        val vm = DashboardViewModel(container(store = store))
        advanceUntilIdle()

        vm.onSelectAlert("a1")
        advanceUntilIdle()
        vm.onFixConfirmedChange(true)
        assertTrue(vm.uiState.value.fixConfirmed)

        // Switching to another alert clears the confirmation — it must be re-checked per alert.
        vm.onSelectAlert("a2")
        advanceUntilIdle()
        assertFalse(vm.uiState.value.fixConfirmed)
    }

    @Test
    fun switchingAlertKeepsLatestSelection() = runTest(dispatcher) {
        val second = alert.copy(alertId = "a2", machineNo = "M-202")
        val generator = FakeTextGenerator().apply { delayMs = 50 }
        val store = InMemoryAlertStore().apply { add(alert); add(second) }
        val vm = DashboardViewModel(container(generator = generator, store = store))
        advanceUntilIdle()

        vm.onSelectAlert("a1")
        vm.onSelectAlert("a2")
        advanceUntilIdle()

        assertEquals("a2", vm.uiState.value.selectedAlertId)
        assertTrue(vm.uiState.value.diagnosis is DiagnosisState.Ready)
    }
}
