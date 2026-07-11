package com.example.qsense.domain.service

import kotlinx.coroutines.flow.StateFlow

/**
 * On-device text-generation model. The single Android implementation wraps GenieX;
 * the rest of the app depends only on this interface.
 */
interface TextGenerator {
    val status: StateFlow<ModelStatus>

    /**
     * Generate a completion for [prompt]. [system] is an optional system/master prompt sent as a
     * separate system-role message. Suspends until the model produces output.
     */
    suspend fun generate(
        prompt: String,
        params: GenerationParams = GenerationParams(),
        system: String? = null,
    ): String
}
