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

    // A machine/part is one logical fault site; the monitoring feed re-sends it with a fresh id each
    // time, so identity is machine+part, not alertId.
    private fun key(a: FaultAlert) = a.machineNo + "|" + a.partNo

    override fun add(alert: FaultAlert) {
        _alerts.update { current ->
            // First real alert clears any demo fixtures so we never mix "dumb" data with live data.
            val live = current.filterNot { it.seeded }
            val idx = live.indexOfFirst { key(it.alert) == key(alert) }
            if (idx >= 0) {
                // Same machine/part: refresh the reading in place, keeping the original id (so the
                // selection stays stable while the feed streams) and the resolved flag.
                val existing = live[idx]
                val refreshed = alert.copy(alertId = existing.alert.alertId)
                live.toMutableList().also { it[idx] = existing.copy(alert = refreshed) }
            } else {
                live + AlertItem(alert)
            }
        }
    }

    override fun seed(alert: FaultAlert) {
        _alerts.update { current ->
            if (current.any { key(it.alert) == key(alert) }) current
            else current + AlertItem(alert, seeded = true)
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
