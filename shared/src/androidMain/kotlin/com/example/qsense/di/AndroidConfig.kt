package com.example.qsense.di

/** Runtime configuration for the Android adapters. Tweak for the demo environment. */
data class AndroidConfig(
    val mqtt: MqttConfig = MqttConfig(),
    val geniex: GenieXConfig = GenieXConfig(),
    // Dev-only: seed sample fault alerts at startup so the dashboard + GenieX diagnosis can be
    // exercised without a reachable MQTT broker. Wired to the debuggable flag by the app layer.
    val seedSampleAlerts: Boolean = false,
)

data class MqttConfig(
    val host: String = "test.mosquitto.org",
    val port: Int = 1883,
    // Raw MQTT (1883) is reset by some networks ("Connection reset by peer"). If that happens,
    // set useWebSocket=true and port=8080 to tunnel over the broker's WebSocket listener.
    val useWebSocket: Boolean = false,
    val webSocketPath: String = "mqtt",
    // Inbound monitoring/alerts topic the app subscribes to.
    val alertsTopic: String = "qsense/machine/monitoring",
    // Outbound resolutions topic (a distinct sibling so the app never re-ingests its own acks).
    val resolutionsTopic: String = "qsense/machine/resolutions",
    // Outbound minimal ack topic, published alongside the rich resolution on every resolve.
    val ackTopic: String = "qsense/machine/ack",
    // Vision (v2): outbound image requests + inbound annotated responses (PC service processes them).
    val visionRequestTopic: String = "qsense/vision/request",
    val visionResponseTopic: String = "qsense/vision/response",
    val publishTimeoutMs: Long = 10_000,
)

data class GenieXConfig(
    // Model bundle folder name under getExternalFilesDir(null)/models/, and its registry id.
    val modelName: String = "qsense-llm",
    val nCtx: Int = 4096,
    val generationTimeoutMs: Long = 60_000,
)
