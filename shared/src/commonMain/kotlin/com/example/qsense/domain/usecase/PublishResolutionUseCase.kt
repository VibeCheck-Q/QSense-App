package com.example.qsense.domain.usecase

import com.example.qsense.domain.model.Resolution
import com.example.qsense.domain.repository.AlertStore
import com.example.qsense.domain.service.Clock
import com.example.qsense.domain.service.MqttGateway

/**
 * Publishes a resolution for an alert. The alert is marked resolved locally only
 * after the broker acknowledges the publish (publishResolution returns).
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
        alertStore.markResolved(alertId)
    }
}
