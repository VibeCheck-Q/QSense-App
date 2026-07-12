package com.example.qsense.presentation.feature.dashboard

import com.example.qsense.domain.model.AlertItem
import com.example.qsense.domain.model.Diagnosis
import com.example.qsense.domain.service.ConnectionState
import com.example.qsense.domain.service.ModelStatus

/** Everything the dashboard renders. Loading/error/selection state lives here, not in the domain. */
data class DashboardUiState(
    val alerts: List<AlertItem> = emptyList(),
    val modelStatus: ModelStatus = ModelStatus.Loading,
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    val selectedAlertId: String? = null,
    val diagnosis: DiagnosisState = DiagnosisState.Idle,
    val selectedCauseIndex: Int? = null,
    val notes: String = "",
    val fixConfirmed: Boolean = false,
    val resolve: ResolveState = ResolveState.Idle,
) {
    val selectedAlert: AlertItem?
        get() = alerts.firstOrNull { it.alert.alertId == selectedAlertId }

    /**
     * Resolve is allowed once a cause is chosen, a diagnosis exists, we're not mid-publish, and the
     * operator has ticked the "I confirm the fault is fixed" box — nothing publishes until they do.
     */
    val canResolve: Boolean
        get() = selectedCauseIndex != null &&
            diagnosis is DiagnosisState.Ready &&
            resolve !is ResolveState.Publishing &&
            fixConfirmed &&
            selectedAlert?.resolved == false
}

sealed interface DiagnosisState {
    data object Idle : DiagnosisState
    data object Generating : DiagnosisState
    data class Ready(val diagnosis: Diagnosis) : DiagnosisState
    data class Error(val message: String) : DiagnosisState
}

sealed interface ResolveState {
    data object Idle : ResolveState
    data object Publishing : ResolveState
    data object Done : ResolveState
    data class Error(val message: String) : ResolveState
}
