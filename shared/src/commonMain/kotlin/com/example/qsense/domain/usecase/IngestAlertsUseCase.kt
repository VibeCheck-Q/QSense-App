package com.example.qsense.domain.usecase

import com.example.qsense.domain.repository.AlertStore
import com.example.qsense.domain.service.MqttGateway

/** Pipes inbound MQTT alerts into the [AlertStore]. Runs for the app's lifetime. */
class IngestAlertsUseCase(
    private val mqttGateway: MqttGateway,
    private val alertStore: AlertStore,
) {
    suspend operator fun invoke() {
        mqttGateway.alerts.collect { alert -> alertStore.add(alert) }
    }
}
