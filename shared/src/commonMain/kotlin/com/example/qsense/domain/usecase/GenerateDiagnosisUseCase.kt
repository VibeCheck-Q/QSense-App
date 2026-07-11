package com.example.qsense.domain.usecase

import com.example.qsense.domain.knowledge.KnowledgeBase
import com.example.qsense.domain.model.Diagnosis
import com.example.qsense.domain.model.FaultAlert
import com.example.qsense.domain.model.PossibleCause
import com.example.qsense.domain.parse.DiagnosisParser
import com.example.qsense.domain.prompt.DiagnosisPrompt
import com.example.qsense.domain.service.GenerationParams
import com.example.qsense.domain.service.OutputConstraint
import com.example.qsense.domain.service.TextGenerator
import kotlin.coroutines.cancellation.CancellationException

/**
 * Retrieves reference knowledge for an alert, builds the grounded prompt, runs the model, and parses
 * the result. If the on-device model fails (e.g. GenieX stream error) or returns text that can't be
 * parsed into causes, it falls back to the retrieved RAG knowledge so the operator ALWAYS gets
 * relevant, grounded causes/fixes rather than an empty or error panel.
 */
class GenerateDiagnosisUseCase(
    private val textGenerator: TextGenerator,
    private val parser: DiagnosisParser,
    private val knowledgeBase: KnowledgeBase,
) {
    suspend operator fun invoke(
        alert: FaultAlert,
        params: GenerationParams = GenerationParams(),
    ): Diagnosis {
        val knowledge = knowledgeBase.retrieve(alert)
        val prompt = DiagnosisPrompt.build(alert, knowledge)

        val modelCauses = try {
            // Always constrain output to ASCII — this keeps the GenieX JNI layer from aborting on
            // invalid UTF-8. The JSON structure comes from the prompt + tolerant parser.
            val raw = textGenerator.generate(
                prompt = prompt,
                params = params.copy(outputConstraint = OutputConstraint.DiagnosisJsonAscii),
                system = DiagnosisPrompt.SYSTEM,
            )
            parser.parse(raw, alert.alertId).causes
        } catch (c: CancellationException) {
            throw c // operator selected another alert — let cancellation propagate
        } catch (t: Throwable) {
            emptyList()
        }

        if (modelCauses.isNotEmpty()) return Diagnosis(alert.alertId, modelCauses)

        // Fallback: ground the diagnosis directly in the retrieved knowledge.
        val fallback = knowledge.take(MAX_CAUSES).map { PossibleCause(it.cause, it.fix) }
        return Diagnosis(alert.alertId, fallback)
    }

    private companion object {
        const val MAX_CAUSES = 5
    }
}
