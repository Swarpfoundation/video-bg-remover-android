package com.videobgremover.app

import android.app.Application
import androidx.work.Configuration
import com.videobgremover.app.core.Logger

/**
 * Application class for Video Background Remover app.
 */
class VideoBgRemoverApp : Application(), Configuration.Provider {

    override fun onCreate() {
        super.onCreate()
        Logger.d("VideoBgRemoverApp initialized")
    }

    /**
     * Configure WorkManager.
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()
}
