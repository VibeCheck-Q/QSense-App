package com.example.qsense

import android.app.Application
import android.content.pm.ApplicationInfo
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
        // Seed dev sample alerts only in debuggable builds (never in release).
        val debuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        container = createAndroidAppContainer(
            this,
            appScope,
            AndroidConfig(seedSampleAlerts = debuggable),
        )
    }
}
