package com.example.qsense.domain.service

import com.example.qsense.domain.model.FaultAlert
import com.example.qsense.domain.model.Resolution
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * MQTT transport for inbound fault alerts and outbound resolutions.
 * The single Android implementation wraps the HiveMQ client.
 */
interface MqttGateway {
    val alerts: Flow<FaultAlert>
    val connectionState: StateFlow<ConnectionState>

    suspend fun connect()
    suspend fun disconnect()

    /** Publishes [resolution]; returns only once the broker acknowledges (PUBACK). */
    suspend fun publishResolution(resolution: Resolution)
}
