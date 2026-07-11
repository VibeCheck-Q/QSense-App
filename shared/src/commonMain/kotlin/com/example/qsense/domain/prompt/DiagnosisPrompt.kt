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
        "You are an expert industrial-machine maintenance engineer. You are given a fault on a " +
            "machine part together with a list of REFERENCE known issues (each a likely cause paired " +
            "with a corrective fix) already retrieved for that part. Select the reference issues most " +
            "relevant to this specific alert, order them most to least likely, and rewrite each " +
            "concisely for this machine/part and its sensor readings.\n" +
            "Rules:\n" +
            "- Reply with ONLY a JSON object. No prose, no explanation, no markdown fences.\n" +
            "- Shape exactly: {\"causes\":[{\"cause\":\"...\",\"fix\":\"...\"}]}\n" +
            "- Prefer the provided reference known issues: pick and adapt them. Add a cause of your " +
            "own only if the reference list is insufficient. Never invent parts not named in the alert.\n" +
            "- Give exactly 5 causes when possible (never fewer than 3), ordered most to least " +
            "likely, each clearly distinct — no duplicates or overlap.\n" +
            "- Each \"cause\" is a specific probable fault reason; each \"fix\" is a specific " +
            "corrective action. Keep each to one short phrase under 12 words. No vague filler.\n" +
            "Example of the exact format:\n" +
            "{\"causes\":[{\"cause\":\"Worn bearing\",\"fix\":\"Replace the bearing\"}," +
            "{\"cause\":\"Insufficient lubrication\",\"fix\":\"Re-grease the shaft\"}]}"

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
            if (knowledge.isNotEmpty()) {
                append("From the reference known issues above, select and adapt the most relevant. ")
            }
            append("""Respond with ONLY: {"causes":[{"cause":"...","fix":"..."}]}""")
        }
    }

    private fun sanitize(value: String): String =
        value.replace(Regex("[\\r\\n]+"), " ").trim().take(MAX_FIELD_LEN)
}
