package com.example.qsense.presentation.feature.vision

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.qsense.di.AppContainer
import com.example.qsense.domain.model.VisionRequest
import com.example.qsense.domain.model.VisionResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.random.Random

sealed interface VisionUiState {
    data object Idle : VisionUiState
    data object Sending : VisionUiState
    data object Waiting : VisionUiState
    data class Result(val response: VisionResponse) : VisionUiState
    data class Error(val message: String) : VisionUiState
}

/**
 * Publishes a captured image (base64 JPEG, prepared by the camera screen) to the PC vision service
 * over MQTT and awaits the annotated response, correlated by a generated requestId.
 */
class VisionViewModel(
    private val container: AppContainer,
) : ViewModel() {

    private val _state = MutableStateFlow<VisionUiState>(VisionUiState.Idle)
    val state: StateFlow<VisionUiState> = _state.asStateFlow()

    fun submitImage(machineNo: String, partNo: String, imageB64: String) {
        val current = _state.value
        if (current is VisionUiState.Sending || current is VisionUiState.Waiting) return
        _state.value = VisionUiState.Sending
        viewModelScope.launch(container.dispatcher) {
            val requestId = "req-" + Random.nextLong().toString(16).trimStart('-')
            val outcome = runCatching {
                val request = VisionRequest(
                    requestId = requestId,
                    machineNo = machineNo,
                    partNo = partNo,
                    imageB64 = imageB64,
                    timestamp = container.clock.nowIso(),
                )
                container.mqttGateway.publishVisionRequest(request)
                _state.value = VisionUiState.Waiting
                withTimeoutOrNull(RESPONSE_TIMEOUT_MS) {
                    container.mqttGateway.visionResponses.first { it.requestId == requestId }
                }
            }
            _state.value = outcome.fold(
                onSuccess = { resp ->
                    if (resp != null) VisionUiState.Result(resp)
                    else VisionUiState.Error("No response from PC (timed out)")
                },
                onFailure = { e -> VisionUiState.Error(e.message ?: "Send failed") },
            )
        }
    }

    fun reset() {
        _state.value = VisionUiState.Idle
    }

    private companion object {
        const val RESPONSE_TIMEOUT_MS = 15_000L
    }
}
