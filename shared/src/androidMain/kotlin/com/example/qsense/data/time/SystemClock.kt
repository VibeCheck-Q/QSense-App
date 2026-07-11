package com.example.qsense.data.time

import com.example.qsense.domain.service.Clock
import java.time.Instant

/** Real clock backed by the system time (ISO-8601 UTC). */
class SystemClock : Clock {
    override fun nowIso(): String = Instant.now().toString()
}
