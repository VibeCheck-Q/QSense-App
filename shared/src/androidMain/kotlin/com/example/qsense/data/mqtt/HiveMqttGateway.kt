package com.example.qsense.data.mqtt

import android.util.Log
import com.example.qsense.data.serialization.JsonProviders
import com.example.qsense.di.MqttConfig
import com.example.qsense.domain.model.FaultAlert
import com.example.qsense.domain.model.Resolution
import com.example.qsense.domain.model.ResolvedAck
import com.example.qsense.domain.service.ConnectionState
import com.example.qsense.domain.service.MqttGateway
import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.datatypes.MqttQos
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.util.UUID

/**
 * [MqttGateway] backed by the HiveMQ MQTT 5 async client. Uses QoS 1 throughout; publishes
 * only complete once the broker returns PUBACK. Because a clean-start session discards
 * subscriptions, an explicit resubscribe runs on every (re)connect.
 */
class HiveMqttGateway(
    private val config: MqttConfig,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : MqttGateway {

    // replay = 1 so an alert arriving before the collector subscribes is not lost.
    private val _alerts = MutableSharedFlow<FaultAlert>(replay = 1, extraBufferCapacity = 64)
    override val alerts: Flow<FaultAlert> = _alerts.asSharedFlow()

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val client: Mqtt5AsyncClient = run {
        var builder = MqttClient.builder()
            .useMqttVersion5()
            .identifier("qsense-" + UUID.randomUUID().toString().take(8))
            .serverHost(config.host)
            .serverPort(config.port)
        // Optional WebSocket transport: some networks reset raw MQTT on 1883 with "Connection
        // reset by peer"; the broker's WebSocket listener (e.g. port 8080, path "mqtt") tunnels
        // through the HTTP-friendly path instead. Default is plain MQTT on 1883.
        if (config.useWebSocket) {
            builder = builder.webSocketConfig().serverPath(config.webSocketPath).applyWebSocketConfig()
        }
        builder
            .automaticReconnectWithDefaultConfig()
            .addConnectedListener {
                Log.i(TAG, "connected to ${config.host}:${config.port}; subscribing")
                // Connected is reported only after the subscription is acknowledged (see below).
                subscribeToAlerts()
            }
            .addDisconnectedListener { ctx ->
                Log.w(TAG, "disconnected (source=${ctx.source}): ${ctx.cause}", ctx.cause)
                _connectionState.value = ConnectionState.Disconnected
            }
            .buildAsync()
    }

    override suspend fun connect() = withContext(io) {
        Log.i(TAG, "connecting to ${config.host}:${config.port}…")
        _connectionState.value = ConnectionState.Connecting
        try {
            client.connectWith().cleanStart(true).keepAlive(60).send().await()
            Log.i(TAG, "CONNACK received")
        } catch (e: Exception) {
            Log.e(TAG, "connect failed", e)
            _connectionState.value = ConnectionState.Error(e.message ?: "Connect failed")
        }
        Unit
    }

    override suspend fun disconnect() = withContext(io) {
        runCatching { client.disconnect().await() }
        _connectionState.value = ConnectionState.Disconnected
    }

    override suspend fun publishResolution(resolution: Resolution) = withContext(io) {
        val payload = JsonProviders.strict.encodeToString(resolution)
            .toByteArray(Charsets.UTF_8)
        // Bound the publish so a half-open connection / missing PUBACK fails into a
        // retryable error instead of leaving the UI stuck in Publishing.
        val result = withTimeout(config.publishTimeoutMs) {
            client.publishWith()
                .topic(config.resolutionsTopic)
                .qos(MqttQos.AT_LEAST_ONCE)
                .payload(payload)
                .send()
                .await()
        }
        // A QoS 1 publish can complete "normally" yet carry a broker rejection in getError();
        // surface it so the alert is not falsely marked resolved.
        result.error.ifPresent { throw it }
        Unit
    }

    override suspend fun publishResolvedAck(ack: ResolvedAck) = withContext(io) {
        val payload = JsonProviders.strict.encodeToString(ack)
            .toByteArray(Charsets.UTF_8)
        val result = withTimeout(config.publishTimeoutMs) {
            client.publishWith()
                .topic(config.ackTopic)
                .qos(MqttQos.AT_LEAST_ONCE)
                .payload(payload)
                .send()
                .await()
        }
        result.error.ifPresent { throw it }
        Unit
    }

    private fun subscribeToAlerts() {
        client.subscribeWith()
            .topicFilter(config.alertsTopic)
            .qos(MqttQos.AT_LEAST_ONCE)
            .callback { publish ->
                runCatching {
                    val json = String(publish.payloadAsBytes, Charsets.UTF_8)
                    JsonProviders.strict.decodeFromString<FaultAlert>(json)
                }.onSuccess { alert -> if (alert.isValid()) _alerts.tryEmit(alert) }
            }
            .send()
            .whenComplete { subAck, throwable ->
                _connectionState.value = when {
                    throwable != null -> {
                        Log.e(TAG, "subscribe failed", throwable)
                        ConnectionState.Error(throwable.message ?: "Subscribe failed")
                    }
                    subAck.reasonCodes.any { it.isError } -> {
                        Log.e(TAG, "subscription denied: ${subAck.reasonCodes}")
                        ConnectionState.Error("Subscription denied: ${subAck.reasonCodes}")
                    }
                    else -> {
                        Log.i(TAG, "subscribed to ${config.alertsTopic}; CONNECTED")
                        ConnectionState.Connected
                    }
                }
            }
    }

    // Reject alerts with blank identifiers (blank alertId collapses dedupe), cap field lengths so an
    // oversized broker payload can't distort the UI/store, and require a non-blank timestamp. The
    // timestamp is accepted free-form (the monitoring producer sends a local date-time with
    // microseconds and no zone, e.g. 2026-07-11T17:57:05.435079); it is displayed, not computed on.
    private fun FaultAlert.isValid(): Boolean =
        alertId.isNotBlank() &&
            machineNo.isNotBlank() &&
            partNo.isNotBlank() &&
            timestamp.isNotBlank() &&
            listOf(alertId, machineNo, partName, partNo, severity, timestamp)
                .all { it.length <= MAX_FIELD_LEN }

    private companion object {
        const val MAX_FIELD_LEN = 200
        const val TAG = "QSenseMqtt"
    }
}
