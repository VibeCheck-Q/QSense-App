package com.example.qsense.data.knowledge

import com.example.qsense.domain.model.FaultAlert
import kotlin.test.Test
import kotlin.test.assertTrue

class InMemoryKnowledgeBaseTest {
    private val kb = InMemoryKnowledgeBase()

    private fun alert(part: String) =
        FaultAlert("a1", "MTR-07", part, "P-1", "high", "2026-07-11T10:30:00Z")

    @Test
    fun bladeAlertRetrievesBladeKnowledgeIncludingGreaseSlowdown() {
        val results = kb.retrieve(alert("Blade"))
        assertTrue(results.isNotEmpty(), "expected blade knowledge")
        assertTrue(
            results.any { it.partKeywords.contains("blade") },
            "results should be blade-scoped",
        )
        assertTrue(
            results.any { it.cause.contains("grease", ignoreCase = true) && it.symptom.contains("slow", ignoreCase = true) },
            "expected the slowdown -> grease/lubrication entry",
        )
    }

    @Test
    fun matchIsCaseInsensitiveAndSubstring() {
        assertTrue(kb.retrieve(alert("Main BLADE assembly")).isNotEmpty())
    }

    @Test
    fun unknownPartFallsBackToMotorKnowledge() {
        val results = kb.retrieve(alert("Widget"))
        assertTrue(results.isNotEmpty(), "expected motor fallback")
        assertTrue(results.all { it.partKeywords.contains("motor") }, "fallback should be motor entries")
    }

    @Test
    fun fanMotorAlertRetrievesFanAndMotorKnowledge() {
        // The live monitoring feed sends "Fan Motor" — retrieval must ground it (not just fall back).
        val results = kb.retrieve(alert("Fan Motor"))
        assertTrue(results.isNotEmpty(), "expected fan-motor knowledge")
        assertTrue(
            results.any { it.partKeywords.contains("fan") },
            "expected fan-specific entries for a Fan Motor",
        )
    }

    @Test
    fun spindleBearingAlertRetrievesBearingKnowledge() {
        val results = kb.retrieve(alert("Spindle Bearing"))
        assertTrue(results.isNotEmpty(), "expected bearing knowledge")
        assertTrue(
            results.any { it.partKeywords.contains("bearing") || it.partKeywords.contains("spindle") },
            "expected bearing/spindle entries",
        )
    }

    @Test
    fun resultsAreCapped() {
        assertTrue(kb.retrieve(alert("Blade")).size <= 6)
    }
}
