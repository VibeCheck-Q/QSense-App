package com.example.qsense.domain.model

import kotlinx.serialization.Serializable

/** A single likely cause of the fault paired with a suggested corrective fix. */
@Serializable
data class PossibleCause(
    val cause: String,
    val fix: String,
)
