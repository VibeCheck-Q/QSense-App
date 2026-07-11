package com.example.qsense.domain.usecase

import com.example.qsense.domain.model.AlertItem
import com.example.qsense.domain.repository.AlertStore
import kotlinx.coroutines.flow.StateFlow

/** Exposes the current alert list for the dashboard to observe. */
class ObserveAlertsUseCase(
    private val alertStore: AlertStore,
) {
    operator fun invoke(): StateFlow<List<AlertItem>> = alertStore.alerts
}
