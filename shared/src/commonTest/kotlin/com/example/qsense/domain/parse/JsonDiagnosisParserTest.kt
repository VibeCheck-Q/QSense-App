package com.example.qsense.domain.parse

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JsonDiagnosisParserTest {
    private val parser = JsonDiagnosisParser()

    @Test
    fun parsesPlainJson() {
        val raw = """{"causes":[{"cause":"Worn seal","fix":"Replace seal"}]}"""
        val result = parser.parse(raw, "a1")
        assertEquals("a1", result.alertId)
        assertEquals(1, result.causes.size)
        assertEquals("Worn seal", result.causes[0].cause)
        assertEquals("Replace seal", result.causes[0].fix)
    }

    @Test
    fun parsesFencedJson() {
        val raw = """
            ```json
            {"causes":[{"cause":"Overheating","fix":"Check coolant"}]}
            ```
        """.trimIndent()
        val result = parser.parse(raw, "a2")
        assertEquals(1, result.causes.size)
        assertEquals("Overheating", result.causes[0].cause)
    }

    @Test
    fun parsesJsonWithSurroundingProse() {
        val raw = "Here are the causes: {\"causes\":[{\"cause\":\"Loose bolt\",\"fix\":\"Torque bolt\"}]}. Done."
        val result = parser.parse(raw, "a3")
        assertEquals(1, result.causes.size)
        assertEquals("Loose bolt", result.causes[0].cause)
    }

    @Test
    fun returnsEmptyOnMalformed() {
        val result = parser.parse("this is not json at all", "a4")
        assertTrue(result.causes.isEmpty())
        assertEquals("a4", result.alertId)
    }

    @Test
    fun capsToFiveCauses() {
        val entries = (1..8).joinToString(",") { """{"cause":"c$it","fix":"f$it"}""" }
        val raw = """{"causes":[$entries]}"""
        val result = parser.parse(raw, "a5")
        assertEquals(5, result.causes.size)
    }
}
