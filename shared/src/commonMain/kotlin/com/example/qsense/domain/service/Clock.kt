package com.example.qsense.domain.service

/** Supplies the current time as an ISO-8601 string. Injected so tests stay deterministic. */
interface Clock {
    fun nowIso(): String
}
