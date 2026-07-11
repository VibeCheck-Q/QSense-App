package com.example.qsense.domain.service

/**
 * Backend-agnostic constraint on a generation's output shape. A [TextGenerator] adapter maps
 * this to whatever its runtime supports (e.g. a GBNF grammar for the llama.cpp backend). Kept
 * semantic so the domain never carries backend-specific grammar text.
 */
sealed interface OutputConstraint {
    /**
     * Force the model to emit the diagnosis JSON `{"causes":[{"cause":"...","fix":"..."}]}` with
     * 3–5 entries and ASCII-only string content. The ASCII restriction is a safety property: it
     * stops the model emitting the invalid multi-byte UTF-8 that aborts the GenieX JNI layer.
     */
    data object DiagnosisJsonAscii : OutputConstraint
}
