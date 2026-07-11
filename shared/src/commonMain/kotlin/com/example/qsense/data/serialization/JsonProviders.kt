package com.example.qsense.data.serialization

import kotlinx.serialization.json.Json

/** Shared JSON configurations: strict for MQTT payloads, lenient for LLM output. */
object JsonProviders {
    /** For MQTT payloads: tolerant of unknown keys but otherwise strict. */
    val strict: Json = Json { ignoreUnknownKeys = true }

    /** For messy LLM output: also relaxes quoting/formatting rules. */
    val lenient: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
}
