package com.example.qsense.presentation.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.qsense.di.AppContainer
import com.example.qsense.domain.model.PossibleCause
import com.example.qsense.domain.service.ModelStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DashboardViewModel(
    private val container: AppContainer,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private var generationJob: Job? = null

    init {
        // Alerts, model status, and connection state are collected into UI state.
        viewModelScope.launch(container.dispatcher) {
            container.observeAlertsUseCase().collect { alerts ->
                _uiState.update { state ->
                    // If the selected alert recurred (reset to unresolved) after we'd resolved it,
                    // clear the stale "resolved" banner so the operator can resolve it again.
                    val reactivated = state.resolve is ResolveState.Done &&
                        alerts.firstOrNull { it.alert.alertId == state.selectedAlertId }?.resolved == false
                    state.copy(
                        alerts = alerts,
                        resolve = if (reactivated) ResolveState.Idle else state.resolve,
                    )
                }
            }
        }
        viewModelScope.launch(container.dispatcher) {
            container.textGenerator.status.collect { status ->
                _uiState.update { it.copy(modelStatus = status) }
            }
        }
        viewModelScope.launch(container.dispatcher) {
            container.mqttGateway.connectionState.collect { state ->
                _uiState.update { it.copy(connectionState = state) }
            }
        }
    }

    fun onSelectAlert(alertId: String) {
        // Ignore re-selecting the current alert (avoids resetting an in-flight publish, which
        // could otherwise re-enable Resolve and allow a duplicate resolution).
        if (alertId == _uiState.value.selectedAlertId) return
        val alert = _uiState.value.alerts.firstOrNull { it.alert.alertId == alertId } ?: return
        // Stale-result guard + reset the detail panel.
        val previousJob = generationJob
        _uiState.update {
            it.copy(
                selectedAlertId = alertId,
                diagnosis = DiagnosisState.Generating,
                selectedCauseIndex = null,
                notes = "",
                resolve = ResolveState.Idle,
            )
        }
        generationJob = viewModelScope.launch(container.dispatcher) {
            // Wait for the previous generation's native cleanup (stopStream) before starting
            // a new one — the single LlmWrapper does not support concurrent generation.
            previousJob?.cancelAndJoin()
            // Defer generation until the model finishes loading (common right after launch).
            container.textGenerator.status.first { it !is ModelStatus.Loading }
            if (_uiState.value.selectedAlertId != alertId) return@launch
            // Even if the model errored, still run the use case — it falls back to RAG knowledge so
            // the operator always gets grounded causes/fixes rather than an error panel.
            val result = runCatching { container.generateDiagnosisUseCase(alert.alert) }
            // Ignore if the operator has since selected a different alert.
            if (_uiState.value.selectedAlertId != alertId) return@launch
            _uiState.update {
                it.copy(
                    diagnosis = result.fold(
                        onSuccess = { diagnosis -> DiagnosisState.Ready(diagnosis) },
                        onFailure = { e -> DiagnosisState.Error(e.message ?: "Generation failed") },
                    ),
                )
            }
        }
    }

    fun onSelectCause(index: Int) {
        _uiState.update { it.copy(selectedCauseIndex = index) }
    }

    fun onNotesChange(notes: String) {
        _uiState.update { it.copy(notes = notes) }
    }

    fun onResolve() {
        val state = _uiState.value
        if (!state.canResolve) return
        val alert = state.selectedAlert?.alert ?: return
        val diagnosis = state.diagnosis as? DiagnosisState.Ready ?: return
        val cause: PossibleCause = diagnosis.diagnosis.causes.getOrNull(
            state.selectedCauseIndex ?: return,
        ) ?: return

        val resolvingAlertId = alert.alertId
        _uiState.update { it.copy(resolve = ResolveState.Publishing) }
        viewModelScope.launch(container.dispatcher) {
            val result = runCatching {
                container.publishResolutionUseCase(
                    alertId = alert.alertId,
                    machineNo = alert.machineNo,
                    partNo = alert.partNo,
                    chosenCause = cause.cause,
                    appliedFix = cause.fix,
                    notes = state.notes,
                )
            }
            // Ignore if the operator has since selected a different alert.
            if (_uiState.value.selectedAlertId != resolvingAlertId) return@launch
            _uiState.update {
                it.copy(
                    resolve = result.fold(
                        onSuccess = { ResolveState.Done },
                        onFailure = { e -> ResolveState.Error(e.message ?: "Publish failed") },
                    ),
                )
            }
        }
    }
}
