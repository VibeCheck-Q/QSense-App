package com.example.qsense.data.repository

import com.example.qsense.domain.model.AlertItem
import com.example.qsense.domain.model.FaultAlert
import com.example.qsense.domain.repository.AlertStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** In-memory [AlertStore]; state is transient and cleared on restart (v1 scope). */
class InMemoryAlertStore : AlertStore {
    private val _alerts = MutableStateFlow<List<AlertItem>>(emptyList())
    override val alerts: StateFlow<List<AlertItem>> = _alerts.asStateFlow()

    override fun add(alert: FaultAlert) {
        _alerts.update { current ->
            if (current.any { it.alert.alertId == alert.alertId }) current
            else current + AlertItem(alert)
        }
    }

    override fun markResolved(alertId: String) {
        _alerts.update { current ->
            current.map { item ->
                if (item.alert.alertId == alertId) item.copy(resolved = true) else item
            }
        }
    }
}
