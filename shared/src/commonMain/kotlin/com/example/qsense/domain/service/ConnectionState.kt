package com.example.qsense.domain.service

/** Connection status of the MQTT gateway. */
sealed interface ConnectionState {
    data object Connecting : ConnectionState
    data object Connected : ConnectionState
    data object Disconnected : ConnectionState
    data class Error(val message: String) : ConnectionState
}
