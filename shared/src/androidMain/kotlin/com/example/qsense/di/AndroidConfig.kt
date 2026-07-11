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
    // Namespace to avoid topic collisions on a shared/public broker. Set per team.
    val namespace: String = "qsense-demo",
    val publishTimeoutMs: Long = 10_000,
) {
    val alertsTopic: String get() = "qsense/$namespace/alerts"
    val resolutionsTopic: String get() = "qsense/$namespace/resolutions"
}

data class GenieXConfig(
    // Model bundle folder name under getExternalFilesDir(null)/models/, and its registry id.
    val modelName: String = "qsense-llm",
    val nCtx: Int = 4096,
    val generationTimeoutMs: Long = 60_000,
)
