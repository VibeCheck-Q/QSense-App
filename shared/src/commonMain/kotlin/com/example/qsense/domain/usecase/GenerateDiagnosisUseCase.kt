package com.example.qsense.domain.usecase

import com.example.qsense.domain.knowledge.KnowledgeBase
import com.example.qsense.domain.model.Diagnosis
import com.example.qsense.domain.model.FaultAlert
import com.example.qsense.domain.parse.DiagnosisParser
import com.example.qsense.domain.prompt.DiagnosisPrompt
import com.example.qsense.domain.service.GenerationParams
import com.example.qsense.domain.service.OutputConstraint
import com.example.qsense.domain.service.TextGenerator

/**
 * Retrieves reference knowledge for an alert, builds the grounded prompt, runs the model with the
 * master system prompt, and parses the result. The [knowledgeBase] only grounds the prompt — the
 * [textGenerator] (GenieX on device) still produces the diagnosis.
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
        // Always constrain diagnosis output to ASCII — this is what keeps the GenieX JNI layer from
        // aborting on invalid UTF-8; the JSON structure comes from the prompt + tolerant parser.
        val raw = textGenerator.generate(
            prompt = prompt,
            params = params.copy(outputConstraint = OutputConstraint.DiagnosisJsonAscii),
            system = DiagnosisPrompt.SYSTEM,
        )
        return parser.parse(raw, alert.alertId)
    }
}
