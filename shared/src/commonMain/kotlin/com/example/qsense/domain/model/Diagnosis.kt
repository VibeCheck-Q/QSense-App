package com.example.qsense.domain.model

/** Completed diagnosis for an alert: the causes the model produced. */
data class Diagnosis(
    val alertId: String,
    val causes: List<PossibleCause>,
)
