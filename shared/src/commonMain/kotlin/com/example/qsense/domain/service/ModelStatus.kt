package com.example.qsense.domain.service

/** Lifecycle status of the on-device text-generation model. */
sealed interface ModelStatus {
    data object Loading : ModelStatus
    data object Ready : ModelStatus
    data class Error(val message: String) : ModelStatus
}
