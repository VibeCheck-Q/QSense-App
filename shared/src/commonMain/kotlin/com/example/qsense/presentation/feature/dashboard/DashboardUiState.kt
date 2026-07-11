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
    val resolve: ResolveState = ResolveState.Idle,
) {
    val selectedAlert: AlertItem?
        get() = alerts.firstOrNull { it.alert.alertId == selectedAlertId }

    /** Resolve is allowed once a cause is chosen, a diagnosis exists, and we're not mid-publish. */
    val canResolve: Boolean
        get() = selectedCauseIndex != null &&
            diagnosis is DiagnosisState.Ready &&
            resolve !is ResolveState.Publishing &&
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
