package com.example.qsense.domain.repository

import com.example.qsense.domain.model.AlertItem
import com.example.qsense.domain.model.FaultAlert
import kotlinx.coroutines.flow.StateFlow

/** Owns the transient (in-memory) list of alerts shown on the dashboard. */
interface AlertStore {
    val alerts: StateFlow<List<AlertItem>>

    /**
     * Ingests a real inbound alert. Alerts are keyed by machine + part: a new reading for the same
     * machine/part refreshes that row in place (keeping its id + resolved state) rather than adding
     * a duplicate. The first real alert purges any [seed]ed demo fixtures.
     */
    fun add(alert: FaultAlert)

    /** Adds a dev/demo fixture, flagged [AlertItem.seeded] so a real [add] can purge it. */
    fun seed(alert: FaultAlert)

    /** Marks the alert with [alertId] as resolved, if present. */
    fun markResolved(alertId: String)

    /**
     * Removes the alert with [alertId], if present. Used to auto-dismiss a resolved alert shortly
     * after it is resolved, so it stops being a standing alert and the machine returns to normal
     * live monitoring.
     */
    fun remove(alertId: String)
}
