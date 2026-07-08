package io.aequicor.visualization

import android.app.Application
import io.aequicor.visualization.editor.data.AndroidPersistence

/** Captures the application context so the local draft store can use SharedPreferences. */
class MissionApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AndroidPersistence.appContext = applicationContext
    }
}
