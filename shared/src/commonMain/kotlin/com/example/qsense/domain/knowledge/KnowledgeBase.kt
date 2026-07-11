package com.example.qsense.domain.knowledge

import com.example.qsense.domain.model.FaultAlert

/**
 * Retrieves reference maintenance knowledge relevant to an alert. The retrieved [KnowledgeEntry]s
 * are injected into the diagnosis prompt as grounding for the on-device GenieX model.
 */
interface KnowledgeBase {
    fun retrieve(alert: FaultAlert): List<KnowledgeEntry>
}
