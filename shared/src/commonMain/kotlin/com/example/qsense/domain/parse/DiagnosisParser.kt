package com.example.qsense.domain.parse

import com.example.qsense.domain.model.Diagnosis

/** Turns raw LLM output into a [Diagnosis]. Implementations must never throw. */
interface DiagnosisParser {
    fun parse(raw: String, alertId: String): Diagnosis
}
