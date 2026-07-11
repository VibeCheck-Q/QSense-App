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
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
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
        vm.onResolve()
        advanceUntilIdle()

        assertEquals(1, gateway.published.size)
        assertEquals("checked flange", gateway.published.first().notes)
        assertEquals(ResolveState.Done, vm.uiState.value.resolve)
        assertTrue(store.alerts.value.first { it.alert.alertId == "a1" }.resolved)
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
