package com.example.qsense.domain.service

import com.example.qsense.domain.model.FaultAlert
import com.example.qsense.domain.model.Resolution
import com.example.qsense.domain.model.ResolvedAck
import com.example.qsense.domain.model.VisionRequest
import com.example.qsense.domain.model.VisionResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * MQTT transport for inbound fault alerts and outbound resolutions.
 * The single Android implementation wraps the HiveMQ client.
 */
interface MqttGateway {
    val alerts: Flow<FaultAlert>
    val connectionState: StateFlow<ConnectionState>

    /** Annotated results returned by the PC vision service; correlate by [VisionResponse.requestId]. */
    val visionResponses: Flow<VisionResponse>

    suspend fun connect()
    suspend fun disconnect()

    /** Publishes [resolution]; returns only once the broker acknowledges (PUBACK). */
    suspend fun publishResolution(resolution: Resolution)

    /** Publishes the minimal [ack] on the ack topic; returns only once the broker PUBACKs. */
    suspend fun publishResolvedAck(ack: ResolvedAck)

    /** Publishes an image [request] for PC-side detection; returns once the broker PUBACKs. */
    suspend fun publishVisionRequest(request: VisionRequest)
}
