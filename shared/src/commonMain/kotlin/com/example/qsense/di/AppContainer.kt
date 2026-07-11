package com.example.qsense.di

import com.example.qsense.data.knowledge.InMemoryKnowledgeBase
import com.example.qsense.domain.parse.JsonDiagnosisParser
import com.example.qsense.domain.repository.AlertStore
import com.example.qsense.domain.service.Clock
import com.example.qsense.domain.service.MqttGateway
import com.example.qsense.domain.service.TextGenerator
import com.example.qsense.domain.usecase.GenerateDiagnosisUseCase
import com.example.qsense.domain.usecase.IngestAlertsUseCase
import com.example.qsense.domain.usecase.ObserveAlertsUseCase
import com.example.qsense.domain.usecase.PublishResolutionUseCase
import kotlinx.coroutines.CoroutineDispatcher

/**
 * Manual DI container. Constructed from platform services by the Android factory
 * (see androidMain), then handed to the presentation layer via a ViewModel factory.
 */
class AppContainer(
    val textGenerator: TextGenerator,
    val mqttGateway: MqttGateway,
    val alertStore: AlertStore,
    val clock: Clock,
    val dispatcher: CoroutineDispatcher,
) {
    private val parser = JsonDiagnosisParser()
    private val knowledgeBase = InMemoryKnowledgeBase()

    val generateDiagnosisUseCase = GenerateDiagnosisUseCase(textGenerator, parser, knowledgeBase)
    val observeAlertsUseCase = ObserveAlertsUseCase(alertStore)
    val publishResolutionUseCase = PublishResolutionUseCase(mqttGateway, alertStore, clock)
    val ingestAlertsUseCase = IngestAlertsUseCase(mqttGateway, alertStore)
}
