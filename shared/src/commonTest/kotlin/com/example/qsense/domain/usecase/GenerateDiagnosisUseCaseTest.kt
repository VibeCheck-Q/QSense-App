package com.example.qsense.domain.usecase

import com.example.qsense.data.knowledge.InMemoryKnowledgeBase
import com.example.qsense.domain.model.FaultAlert
import com.example.qsense.domain.parse.JsonDiagnosisParser
import com.example.qsense.domain.service.OutputConstraint
import com.example.qsense.testutil.FakeTextGenerator
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GenerateDiagnosisUseCaseTest {
    private val alert = FaultAlert("a1", "MTR-07", "Blade", "BLD-330", "high", "2026-07-11T10:30:00Z")

    private fun useCase(generator: FakeTextGenerator) =
        GenerateDiagnosisUseCase(generator, JsonDiagnosisParser(), InMemoryKnowledgeBase())

    @Test
    fun buildsDiagnosisFromModelOutput() = runTest {
        val generator = FakeTextGenerator(
            response = """{"causes":[{"cause":"c1","fix":"f1"},{"cause":"c2","fix":"f2"}]}""",
        )

        val diagnosis = useCase(generator)(alert)

        assertEquals("a1", diagnosis.alertId)
        assertEquals(2, diagnosis.causes.size)
        assertEquals("c1", diagnosis.causes[0].cause)
    }

    @Test
    fun emptyCausesWhenModelOutputUnparseable() = runTest {
        val generator = FakeTextGenerator(response = "sorry, I cannot help")

        val diagnosis = useCase(generator)(alert)

        assertTrue(diagnosis.causes.isEmpty())
    }

    @Test
    fun alwaysRequestsDiagnosisJsonAsciiConstraint() = runTest {
        // The crash fix depends on diagnosis always requesting the ASCII constraint, which is what
        // stops GenieX aborting on invalid UTF-8.
        val generator = FakeTextGenerator(response = """{"causes":[{"cause":"c1","fix":"f1"}]}""")

        useCase(generator)(alert)

        assertEquals(OutputConstraint.DiagnosisJsonAscii, generator.lastParams?.outputConstraint)
    }

    @Test
    fun sendsMasterSystemPromptAndGroundsInReferenceKnowledge() = runTest {
        val generator = FakeTextGenerator(response = """{"causes":[{"cause":"c1","fix":"f1"}]}""")

        useCase(generator)(alert)

        // The master/system prompt is sent as a system message…
        assertTrue(generator.lastSystem?.isNotBlank() == true, "system prompt not sent")
        // …and retrieved blade knowledge (the grease/slowdown entry) grounds the user prompt.
        val prompt = generator.lastPrompt ?: ""
        assertTrue(prompt.contains("Reference knowledge"), "knowledge block missing")
        assertTrue(prompt.contains("grease", ignoreCase = true), "expected grounded grease cause")
    }
}
