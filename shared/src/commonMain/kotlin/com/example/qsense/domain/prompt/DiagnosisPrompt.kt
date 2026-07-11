package com.example.qsense.domain.prompt

import com.example.qsense.domain.knowledge.KnowledgeEntry
import com.example.qsense.domain.model.FaultAlert

/**
 * Builds the prompt sent to the on-device LLM. [SYSTEM] is the fixed master prompt (persona +
 * output contract); [build] produces the per-alert user message, grounded in retrieved reference
 * knowledge and any sensor readings. MQTT-provided fields are sanitized (line breaks stripped,
 * length-capped) before being embedded, so a malformed alert can't distort the instruction.
 */
object DiagnosisPrompt {
    private const val MAX_FIELD_LEN = 120

    /** Master/system prompt: persona + strict, unambiguous output contract. */
    val SYSTEM: String =
        "You are an expert industrial-machine maintenance engineer. Given a fault on a machine " +
            "part, output the most likely causes, each paired with a concrete corrective fix a " +
            "technician can apply.\n" +
            "Rules:\n" +
            "- Reply with ONLY a JSON object. No prose, no explanation, no markdown fences.\n" +
            "- Shape exactly: {\"causes\":[{\"cause\":\"...\",\"fix\":\"...\"}]}\n" +
            "- Give exactly 5 causes when possible (never fewer than 3), ordered most to least " +
            "likely, each clearly distinct — no duplicates or overlap.\n" +
            "- Each \"cause\" is a specific probable fault reason; each \"fix\" is a specific " +
            "corrective action. Keep each to one short phrase under 12 words. No vague filler.\n" +
            "- Ground your answer in the provided reference knowledge and sensor readings; prefer " +
            "those known causes. Do not invent parts not mentioned in the alert."

    fun build(alert: FaultAlert, knowledge: List<KnowledgeEntry> = emptyList()): String {
        val machineNo = sanitize(alert.machineNo)
        val partName = sanitize(alert.partName)
        val partNo = sanitize(alert.partNo)
        val severity = sanitize(alert.severity)

        return buildString {
            appendLine("Machine number: $machineNo")
            appendLine("Part name: $partName")
            appendLine("Part number: $partNo")
            appendLine("Severity: $severity")

            val readings = buildList {
                alert.temperature?.let { add("temperature ${it}°C") }
                alert.humidity?.let { add("humidity ${it}%") }
            }
            if (readings.isNotEmpty()) {
                appendLine("Sensor readings: ${readings.joinToString(", ")}")
            }

            if (knowledge.isNotEmpty()) {
                appendLine()
                appendLine("Reference knowledge (known issues for this part):")
                knowledge.forEach { entry ->
                    appendLine("- ${entry.symptom}: ${entry.cause} -> ${entry.fix}")
                }
            }

            appendLine()
            append("""Respond with ONLY: {"causes":[{"cause":"...","fix":"..."}]}""")
        }
    }

    private fun sanitize(value: String): String =
        value.replace(Regex("[\\r\\n]+"), " ").trim().take(MAX_FIELD_LEN)
}
