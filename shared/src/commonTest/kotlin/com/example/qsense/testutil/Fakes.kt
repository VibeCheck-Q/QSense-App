package com.example.qsense.testutil

import com.example.qsense.domain.model.FaultAlert
import com.example.qsense.domain.model.Resolution
import com.example.qsense.domain.model.ResolvedAck
import com.example.qsense.domain.model.VisionRequest
import com.example.qsense.domain.model.VisionResponse
import com.example.qsense.domain.service.Clock
import com.example.qsense.domain.service.ConnectionState
import com.example.qsense.domain.service.GenerationParams
import com.example.qsense.domain.service.ModelStatus
import com.example.qsense.domain.service.MqttGateway
import com.example.qsense.domain.service.TextGenerator
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeTextGenerator(
    var response: String = """{"causes":[{"cause":"c","fix":"f"}]}""",
) : TextGenerator {
    private val _status = MutableStateFlow<ModelStatus>(ModelStatus.Ready)
    override val status: StateFlow<ModelStatus> = _status

    var error: Throwable? = null
    var delayMs: Long = 0

    // Record the last call so tests can assert what the caller requested (e.g. output constraint).
    var lastPrompt: String? = null
        private set
    var lastParams: GenerationParams? = null
        private set
    var lastSystem: String? = null
        private set

    fun setStatus(status: ModelStatus) { _status.value = status }

    override suspend fun generate(prompt: String, params: GenerationParams, system: String?): String {
        lastPrompt = prompt
        lastParams = params
        lastSystem = system
        if (delayMs > 0) delay(delayMs)
        error?.let { throw it }
        return response
    }
}

class FakeMqttGateway : MqttGateway {
    val alertsFlow = MutableSharedFlow<FaultAlert>(replay = 1, extraBufferCapacity = 64)
    override val alerts: Flow<FaultAlert> = alertsFlow

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Connected)
    override val connectionState: StateFlow<ConnectionState> = _connectionState

    val visionResponsesFlow = MutableSharedFlow<VisionResponse>(replay = 1, extraBufferCapacity = 16)
    override val visionResponses: Flow<VisionResponse> = visionResponsesFlow

    val published = mutableListOf<Resolution>()
    val publishedAcks = mutableListOf<ResolvedAck>()
    val publishedVisionRequests = mutableListOf<VisionRequest>()
    var publishError: Throwable? = null
    var ackError: Throwable? = null
    var visionError: Throwable? = null

    fun setConnectionState(state: ConnectionState) { _connectionState.value = state }

    override suspend fun connect() { _connectionState.value = ConnectionState.Connected }
    override suspend fun disconnect() { _connectionState.value = ConnectionState.Disconnected }

    override suspend fun publishResolution(resolution: Resolution) {
        publishError?.let { throw it }
        published.add(resolution)
    }

    override suspend fun publishResolvedAck(ack: ResolvedAck) {
        ackError?.let { throw it }
        publishedAcks.add(ack)
    }

    override suspend fun publishVisionRequest(request: VisionRequest) {
        visionError?.let { throw it }
        publishedVisionRequests.add(request)
    }
}

class FixedClock(private val value: String = "2026-07-11T10:45:00Z") : Clock {
    override fun nowIso(): String = value
}
