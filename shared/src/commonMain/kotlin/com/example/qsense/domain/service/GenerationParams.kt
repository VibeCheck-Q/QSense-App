package com.example.qsense.domain.service

/** Tunable inference parameters for a single generation. */
data class GenerationParams(
    val maxTokens: Int = 512,
    val temperature: Float = 0.2f,
    // Sampler pre-filters are DISABLED by default (topP=1, topK=0, minP=0): combined with a
    // grammar they can filter out every grammar-valid token, emptying the candidate set and
    // failing the stream. The grammar does the constraining; temperature + repetitionPenalty
    // shape quality without ever emptying the candidates.
    val topP: Float = 1.0f,
    val topK: Int = 0,
    val minP: Float = 0.0f,
    // >1 discourages the degenerate repetition loops small models fall into.
    val repetitionPenalty: Float = 1.15f,
    // Set for reproducible generation (e.g. regression tests); null lets the backend choose.
    val seed: Int? = null,
    // Optional constraint on output shape; the generator adapter maps it to its runtime.
    val outputConstraint: OutputConstraint? = null,
)
