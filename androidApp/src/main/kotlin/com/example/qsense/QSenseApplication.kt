package com.example.qsense

import android.app.Application
import com.example.qsense.di.AndroidConfig
import com.example.qsense.di.AppContainer
import com.example.qsense.di.createAndroidAppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Owns the single [AppContainer] so it survives configuration changes / activity recreation. */
class QSenseApplication : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // No sample seeding: the seeded fixture (M-01 / Fan Motor) collides with the real device id
        // and reappears as unresolved after a restart (the in-memory store is cleared on restart by
        // design), which reads as "the resolved alert came back". The dashboard is now driven only by
        // real MQTT alerts. Flip seedSampleAlerts=true only for an offline (no-broker) demo.
        container = createAndroidAppContainer(
            this,
            appScope,
            AndroidConfig(seedSampleAlerts = false),
        )
    }
}
