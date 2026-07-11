package com.example.qsense.domain.knowledge

/**
 * One piece of reference maintenance knowledge: a known [symptom] on a part, its likely [cause],
 * and the corrective [fix]. [partKeywords] are lowercase tokens matched against an alert's part
 * name so the right cluster is retrieved (e.g. "blade"). This is the RAG grounding data injected
 * into the on-device GenieX prompt — GenieX still generates the final diagnosis; these entries
 * just ground it in real, known failure modes.
 */
data class KnowledgeEntry(
    val partKeywords: List<String>,
    val symptom: String,
    val cause: String,
    val fix: String,
)
