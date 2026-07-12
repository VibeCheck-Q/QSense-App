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
        // prompt is still grounded rather than empty. (Single cluster — no balancing needed.)
        if (matches.isEmpty()) {
            return entries.filter { "motor" in it.partKeywords }.take(maxResults)
        }
        // Round-robin across the matched clusters so a multi-word part like "Fan Motor" surfaces
        // both fan- and motor-specific entries, instead of the cap slicing off whichever cluster
        // happens to come later in the list.
        return balanceAcrossClusters(matches, part).take(maxResults)
    }

    /** Group matches by the first part-keyword each one matches, then interleave the groups. */
    private fun balanceAcrossClusters(matches: List<KnowledgeEntry>, part: String): List<KnowledgeEntry> {
        val clusters = LinkedHashMap<String, MutableList<KnowledgeEntry>>()
        for (entry in matches) {
            val key = entry.partKeywords.first { part.contains(it) }
            clusters.getOrPut(key) { mutableListOf() }.add(entry)
        }
        val result = mutableListOf<KnowledgeEntry>()
        val cursors = clusters.values.map { it.iterator() }
        var advanced = true
        while (advanced) {
            advanced = false
            for (cursor in cursors) {
                if (cursor.hasNext()) {
                    result.add(cursor.next())
                    advanced = true
                }
            }
        }
        return result
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
            KnowledgeEntry(
                partKeywords = listOf("motor"),
                symptom = "Trips the breaker on start",
                cause = "Winding insulation breakdown / short to ground",
                fix = "Megger-test the windings; rewind or replace the motor",
            ),
            KnowledgeEntry(
                partKeywords = listOf("motor"),
                symptom = "Burning smell / scorched insulation",
                cause = "Sustained overload overheating the windings",
                fix = "De-energize and cool down, reduce the load, check winding resistance",
            ),
            KnowledgeEntry(
                partKeywords = listOf("motor"),
                symptom = "Hums but will not start",
                cause = "Failed start/run capacitor",
                fix = "Test and replace the capacitor",
            ),
            KnowledgeEntry(
                partKeywords = listOf("motor"),
                symptom = "Excessive vibration",
                cause = "Rotor imbalance or a bent shaft",
                fix = "Balance the rotor or replace the shaft",
            ),
            KnowledgeEntry(
                partKeywords = listOf("motor"),
                symptom = "Runs slow / low torque under load",
                cause = "Low supply voltage or worn brushes",
                fix = "Check supply voltage and terminals; inspect and replace brushes",
            ),
            // Fan / fan-motor cluster (matches "Fan Motor" from the live monitoring feed).
            KnowledgeEntry(
                partKeywords = listOf("fan"),
                symptom = "Fan overheating",
                cause = "Dust buildup blocking airflow",
                fix = "Clean fan blades and clear air vents",
            ),
            KnowledgeEntry(
                partKeywords = listOf("fan"),
                symptom = "Excessive vibration",
                cause = "Dust/debris imbalance on blades",
                fix = "Clean and rebalance the fan",
            ),
            KnowledgeEntry(
                partKeywords = listOf("fan"),
                symptom = "Grinding / bearing noise",
                cause = "Worn fan bearing",
                fix = "Replace the fan bearing",
            ),
            KnowledgeEntry(
                partKeywords = listOf("fan"),
                symptom = "Fan will not spin",
                cause = "Seized bearing or failed start capacitor",
                fix = "Inspect capacitor and bearing, free or replace",
            ),
            // Bearing / spindle cluster (matches "Spindle Bearing").
            KnowledgeEntry(
                partKeywords = listOf("bearing", "spindle"),
                symptom = "Bearing overheating",
                cause = "Lubrication breakdown",
                fix = "Re-lubricate or replace the bearing",
            ),
            KnowledgeEntry(
                partKeywords = listOf("bearing", "spindle"),
                symptom = "Excessive play / runout",
                cause = "Worn bearing races",
                fix = "Replace the bearing",
            ),
            KnowledgeEntry(
                partKeywords = listOf("bearing", "spindle"),
                symptom = "Vibration",
                cause = "Shaft misalignment",
                fix = "Realign the spindle to spec",
            ),
            KnowledgeEntry(
                partKeywords = listOf("bearing", "spindle"),
                symptom = "Clicking / knocking noise",
                cause = "Brinelling — dents in the races from shock load or press-fit damage",
                fix = "Replace the bearing and avoid impact when fitting the new one",
            ),
            KnowledgeEntry(
                partKeywords = listOf("bearing", "spindle"),
                symptom = "Runs hot shortly after re-greasing",
                cause = "Over-greasing or the wrong grease grade",
                fix = "Purge the excess grease and use the specified lubricant",
            ),
            KnowledgeEntry(
                partKeywords = listOf("bearing", "spindle"),
                symptom = "Rapid / premature wear",
                cause = "Contamination ingress (dust, coolant, or water)",
                fix = "Replace the bearing and improve the seals/shielding",
            ),
            KnowledgeEntry(
                partKeywords = listOf("bearing", "spindle"),
                symptom = "Seizes on a VFD-driven shaft",
                cause = "Electrical fluting from circulating shaft currents",
                fix = "Fit a shaft grounding ring or an insulated bearing",
            ),
            KnowledgeEntry(
                partKeywords = listOf("bearing", "spindle"),
                symptom = "Grinding / rough rotation",
                cause = "Loss of lubrication film and metal-to-metal contact",
                fix = "Re-lubricate; if roughness persists, replace the bearing",
            ),
        )
    }
}
