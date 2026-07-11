package com.example.qsense.domain.parse

import com.example.qsense.data.serialization.JsonProviders
import com.example.qsense.domain.model.Diagnosis
import com.example.qsense.domain.model.PossibleCause
import kotlinx.serialization.Serializable

/**
 * Tolerant parser for LLM output. Strips markdown fences, isolates the JSON object,
 * decodes leniently, caps to 5 causes, and returns an empty diagnosis on any failure.
 */
class JsonDiagnosisParser : DiagnosisParser {

    @Serializable
    private data class CausesHolder(val causes: List<PossibleCause> = emptyList())

    override fun parse(raw: String, alertId: String): Diagnosis {
        val json = extractJsonObject(raw) ?: return Diagnosis(alertId, emptyList())
        return try {
            val holder = JsonProviders.lenient.decodeFromString<CausesHolder>(json)
            Diagnosis(alertId, holder.causes.take(MAX_CAUSES))
        } catch (e: Exception) {
            Diagnosis(alertId, emptyList())
        }
    }

    /** Removes ```json fences (if any) and returns the substring spanning the JSON object. */
    private fun extractJsonObject(raw: String): String? {
        val defenced = raw
            .replace(Regex("```(?:json)?", RegexOption.IGNORE_CASE), "")
            .trim()
        val start = defenced.indexOf('{')
        val end = defenced.lastIndexOf('}')
        if (start == -1 || end == -1 || end < start) return null
        return defenced.substring(start, end + 1)
    }

    private companion object {
        const val MAX_CAUSES = 5
    }
}
