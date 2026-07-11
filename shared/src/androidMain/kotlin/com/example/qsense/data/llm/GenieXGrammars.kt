package com.example.qsense.data.llm

import com.example.qsense.domain.service.OutputConstraint

/**
 * Maps backend-agnostic [OutputConstraint]s to llama.cpp GBNF grammar strings for GenieX.
 * Kept in androidMain because GBNF is specific to the GenieX/llama.cpp runtime.
 */
internal object GenieXGrammars {

    fun forConstraint(constraint: OutputConstraint): String = when (constraint) {
        OutputConstraint.DiagnosisJsonAscii -> DIAGNOSIS_JSON_ASCII
    }

    // Minimal ASCII-only grammar: constrains output to single-byte printable ASCII (plus tab/CR/LF)
    // so the model can never emit the invalid multi-byte UTF-8 that makes GenieX's native
    // NewStringUTF abort under CheckJNI. Deliberately does NOT force the JSON structure: a heavily
    // structured grammar masks almost every candidate token and can empty the sampler's candidate
    // set mid-stream (the "stream error" failure). Structure instead comes from the master prompt
    // and the tolerant JsonDiagnosisParser.
    private val DIAGNOSIS_JSON_ASCII: String = buildString {
        appendLine("""root ::= char+""")
        appendLine("""char ::= [\t\n\r\x20-\x7E]""")
    }
}
