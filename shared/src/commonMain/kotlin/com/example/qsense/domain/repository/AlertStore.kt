package com.example.qsense.domain.repository

import com.example.qsense.domain.model.AlertItem
import com.example.qsense.domain.model.FaultAlert
import kotlinx.coroutines.flow.StateFlow

/** Owns the transient (in-memory) list of alerts shown on the dashboard. */
interface AlertStore {
    val alerts: StateFlow<List<AlertItem>>

    /** Adds an alert, ignoring it if one with the same alertId is already present. */
    fun add(alert: FaultAlert)

    /** Marks the alert with [alertId] as resolved, if present. */
    fun markResolved(alertId: String)
}
