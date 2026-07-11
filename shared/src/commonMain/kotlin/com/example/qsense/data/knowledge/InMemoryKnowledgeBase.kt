package com.example.qsense.data.knowledge

import com.example.qsense.domain.knowledge.KnowledgeBase
import com.example.qsense.domain.knowledge.KnowledgeEntry
import com.example.qsense.domain.model.FaultAlert

/**
 * A small, in-memory reference knowledge base of known failure modes (RAG grounding). Retrieval is
 * deterministic keyword matching on the alert's part name — no model/embeddings — which is reliable
 * for the demo. The matched entries are handed to the prompt builder and injected into the GenieX
 * prompt; GenieX still produces the final causes/fixes, grounded in these known issues.
 */
class InMemoryKnowledgeBase(
    private val entries: List<KnowledgeEntry> = DEFAULT_ENTRIES,
    private val maxResults: Int = 6,
) : KnowledgeBase {

    override fun retrieve(alert: FaultAlert): List<KnowledgeEntry> {
        val part = alert.partName.lowercase()
        val matches = entries.filter { entry ->
            entry.partKeywords.any { keyword -> part.contains(keyword) }
        }
        // Fall back to generic motor knowledge when the specific part isn't in the KB, so the
        // prompt is still grounded rather than empty.
        val chosen = matches.ifEmpty { entries.filter { "motor" in it.partKeywords } }
        return chosen.take(maxResults)
    }

    private companion object {
        val DEFAULT_ENTRIES: List<KnowledgeEntry> = listOf(
            KnowledgeEntry(
                partKeywords = listOf("blade"),
                symptom = "Blade slows down / loses speed",
                cause = "Grease/lubrication degradation on the shaft bearing",
                fix = "Re-grease bearing or replace with correct lubricant",
            ),
            KnowledgeEntry(
                partKeywords = listOf("blade"),
                symptom = "Excessive vibration",
                cause = "Debris buildup causing blade imbalance",
                fix = "Clean the blade and rebalance",
            ),
            KnowledgeEntry(
                partKeywords = listOf("blade"),
                symptom = "Poor / rough cut quality",
                cause = "Worn or dull blade edge",
                fix = "Replace the blade",
            ),
            KnowledgeEntry(
                partKeywords = listOf("blade"),
                symptom = "Grinding noise",
                cause = "Shaft or blade misalignment",
                fix = "Realign blade and shaft to spec",
            ),
            KnowledgeEntry(
                partKeywords = listOf("blade"),
                symptom = "Overheating near the hub",
                cause = "Insufficient lubrication / friction",
                fix = "Apply proper grease and check bearing play",
            ),
            KnowledgeEntry(
                partKeywords = listOf("blade", "motor"),
                symptom = "Blade will not spin",
                cause = "Seized bearing or motor fault",
                fix = "Inspect motor windings and bearing, free or replace",
            ),
            KnowledgeEntry(
                partKeywords = listOf("motor"),
                symptom = "Motor overheating",
                cause = "Overload or cooling airflow blockage",
                fix = "Reduce load and clear cooling vents",
            ),
            KnowledgeEntry(
                partKeywords = listOf("motor"),
                symptom = "Intermittent stalling",
                cause = "Loose or corroded power connection",
                fix = "Inspect and re-terminate power wiring",
            ),
        )
    }
}
