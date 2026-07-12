package com.example.qsense.di

import com.example.qsense.data.llm.Backend

/** Runtime configuration for the Android adapters. Tweak for the demo environment. */
data class AndroidConfig(
    val mqtt: MqttConfig = MqttConfig(),
    val geniex: GenieXConfig = GenieXConfig(),
    // Dev-only: seed sample fault alerts at startup so the dashboard + GenieX diagnosis can be
    // exercised without a reachable MQTT broker. Wired to the debuggable flag by the app layer.
    val seedSampleAlerts: Boolean = false,
)

data class MqttConfig(
    // Local LAN broker — the public test.mosquitto.org:1883 was being reset by the network
    // ("Connection reset by peer"). The phone must be on the same Wi-Fi/LAN as this host.
    val host: String = "192.168.8.153",
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
    // GGUF (llama.cpp/CPU) bundle folder under getExternalFilesDir(null)/models/, and its registry id.
    val modelName: String = "qsense-llm",
    // QAIRT/NPU bundle folder (QNN context binary + tokenizer + genie-config), side-loaded alongside
    // the GGUF. Used only on supported Snapdragon SoCs.
    val qnnModelName: String = "qsense-llm-qnn",
    // Which runtime to drive. AUTO uses the Hexagon NPU on a supported chip (SM8750/SM8850): the
    // QAIRT plugin when a QNN bundle is present, otherwise the llama.cpp plugin — which on these
    // chips still offloads to the Hexagon HTP DSP (see libggml-htp-v8x.so). So AUTO runs on the NPU
    // today with a GGUF and auto-upgrades to QAIRT the moment a QNN bundle is side-loaded. Force
    // Backend.QAIRT to require the QNN plugin (RAG-only if the QNN bundle is missing).
    val backend: Backend = Backend.AUTO,
    // SoC model substrings eligible for the QAIRT/NPU path (8 Elite / 8 Elite Gen 5). Matched
    // case-insensitively against Build.SOC_MODEL.
    val qnnChipsets: Set<String> = setOf("SM8750", "SM8850"),
    val nCtx: Int = 4096,
    val generationTimeoutMs: Long = 60_000,
)
