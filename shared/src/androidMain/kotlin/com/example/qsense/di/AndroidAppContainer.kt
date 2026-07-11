package com.example.qsense.di

import android.content.Context
import com.example.qsense.data.llm.GenieXTextGenerator
import com.example.qsense.data.mqtt.HiveMqttGateway
import com.example.qsense.data.repository.InMemoryAlertStore
import com.example.qsense.data.time.SystemClock
import com.example.qsense.demo.SampleAlerts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Builds the [AppContainer] from Android platform services and kicks off model loading and
 * the MQTT connection on [scope]. Called once from the application layer.
 */
fun createAndroidAppContainer(
    context: Context,
    scope: CoroutineScope,
    config: AndroidConfig = AndroidConfig(),
): AppContainer {
    val generator = GenieXTextGenerator(context.applicationContext, config.geniex)
    val gateway = HiveMqttGateway(config.mqtt)

    val container = AppContainer(
        textGenerator = generator,
        mqttGateway = gateway,
        alertStore = InMemoryAlertStore(),
        clock = SystemClock(),
        dispatcher = Dispatchers.Main.immediate,
    )

    // Dev-only: seed sample alerts as fixtures so the dashboard isn't empty offline. They are flagged
    // seeded and purged automatically as soon as the first real MQTT alert arrives.
    if (config.seedSampleAlerts) {
        SampleAlerts.all.forEach(container.alertStore::seed)
    }

    scope.launch { generator.load() }
    // Start ingesting before connecting so no early alert is missed.
    scope.launch { container.ingestAlertsUseCase() }
    scope.launch { gateway.connect() }

    return container
}
