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

    // ASCII-only, structurally-UNCONSTRAINED grammar. It restricts output to single-byte printable
    // ASCII (plus tab/CR/LF) so the model can never emit the invalid multi-byte UTF-8 that aborts
    // the GenieX JNI layer — but it does NOT force the JSON structure. A structured GBNF grammar
    // makes this GenieX/llama.cpp build fail generation outright (ErrorCode -200101 after the first
    // few tokens), so JSON structure is elicited by the prompt and recovered by JsonDiagnosisParser.
    private val DIAGNOSIS_JSON_ASCII: String = buildString {
        appendLine("""root ::= char+""")
        appendLine("""char ::= [\t\n\r\x20-\x7E]""")
    }
}
