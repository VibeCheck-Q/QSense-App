package com.example.qsense.presentation.theme

import kotlin.test.Test
import kotlin.test.assertEquals

class CriticalityTest {
    @Test
    fun numericSeverityMapsToLevels() {
        assertEquals(Criticality.CRITICAL, classifySeverity("52.3"))
        assertEquals(Criticality.CRITICAL, classifySeverity("48.896"))
        assertEquals(Criticality.WARNING, classifySeverity("12.0"))
        assertEquals(Criticality.OK, classifySeverity("5.955"))
        assertEquals(Criticality.OK, classifySeverity("7.174"))
    }

    @Test
    fun labelSeverityStillMaps() {
        assertEquals(Criticality.CRITICAL, classifySeverity("high"))
        assertEquals(Criticality.WARNING, classifySeverity("medium"))
        assertEquals(Criticality.OK, classifySeverity("low"))
    }
}
