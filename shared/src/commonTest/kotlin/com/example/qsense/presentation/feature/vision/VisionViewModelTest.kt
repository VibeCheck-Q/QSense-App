package com.example.qsense.presentation.feature.vision

import com.example.qsense.data.repository.InMemoryAlertStore
import com.example.qsense.di.AppContainer
import com.example.qsense.domain.model.VisionResponse
import com.example.qsense.testutil.FakeMqttGateway
import com.example.qsense.testutil.FakeTextGenerator
import com.example.qsense.testutil.FixedClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VisionViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private fun container(gateway: FakeMqttGateway) =
        AppContainer(FakeTextGenerator(), gateway, InMemoryAlertStore(), FixedClock(), dispatcher)

    @Test
    fun submitPublishesRequestAndResolvesMatchingResponse() = runTest(dispatcher) {
        val gateway = FakeMqttGateway()
        val vm = VisionViewModel(container(gateway))

        vm.submitImage("M1", "blade-1", "b64data")
        runCurrent() // publish + start waiting, without advancing to the timeout

        val published = gateway.publishedVisionRequests.single()
        assertEquals("M1", published.machineNo)
        assertEquals("blade-1", published.partNo)
        assertEquals("b64data", published.imageB64)
        assertEquals(VisionUiState.Waiting, vm.state.value)

        val resp = VisionResponse(published.requestId, "annotated", emptyList(), "Detected: 1 blade.")
        gateway.visionResponsesFlow.tryEmit(resp)
        runCurrent()

        assertEquals(VisionUiState.Result(resp), vm.state.value)
    }

    @Test
    fun ignoresMismatchedResponseThenTimesOut() = runTest(dispatcher) {
        val gateway = FakeMqttGateway()
        val vm = VisionViewModel(container(gateway))

        vm.submitImage("M1", "blade-1", "b64data")
        runCurrent()

        gateway.visionResponsesFlow.tryEmit(
            VisionResponse("some-other-id", "x", emptyList(), "nope"),
        )
        runCurrent()
        // mismatched id filtered out — still waiting, timeout not yet reached
        assertTrue(vm.state.value is VisionUiState.Waiting)

        advanceUntilIdle() // fire the 15s timeout
        assertTrue(vm.state.value is VisionUiState.Error)
    }

    @Test
    fun publishFailureSurfacesError() = runTest(dispatcher) {
        val gateway = FakeMqttGateway().apply { visionError = RuntimeException("broker down") }
        val vm = VisionViewModel(container(gateway))

        vm.submitImage("M1", "blade-1", "b64data")
        advanceUntilIdle()

        val state = vm.state.value
        assertTrue(state is VisionUiState.Error)
        assertEquals("broker down", state.message)
    }
}
