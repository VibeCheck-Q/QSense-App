package com.example.qsense.domain.usecase

import com.example.qsense.domain.model.Resolution
import com.example.qsense.domain.model.ResolvedAck
import com.example.qsense.domain.repository.AlertStore
import com.example.qsense.domain.service.Clock
import com.example.qsense.domain.service.MqttGateway

/**
 * Publishes a resolution for an alert, then a minimal ack on the ack topic. Both are
 * PUBACK-bound; the alert is marked resolved locally only after both publishes succeed, so a
 * failure in either leaves the alert unresolved (surfacing the existing publish error).
 */
class PublishResolutionUseCase(
    private val mqttGateway: MqttGateway,
    private val alertStore: AlertStore,
    private val clock: Clock,
) {
    suspend operator fun invoke(
        alertId: String,
        machineNo: String,
        partNo: String,
        chosenCause: String,
        appliedFix: String,
        notes: String,
    ) {
        val resolution = Resolution(
            alertId = alertId,
            machineNo = machineNo,
            partNo = partNo,
            chosenCause = chosenCause,
            appliedFix = appliedFix,
            notes = notes,
            resolvedAt = clock.nowIso(),
        )
        mqttGateway.publishResolution(resolution)
        mqttGateway.publishResolvedAck(ResolvedAck(alertId, resolved = true))
        alertStore.markResolved(alertId)
    }
}
