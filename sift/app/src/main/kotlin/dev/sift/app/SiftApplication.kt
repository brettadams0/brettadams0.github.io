package dev.sift.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import dev.sift.app.work.GradeLog
import javax.inject.Inject

@HiltAndroidApp
class SiftApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()
        // §12 — failures go to a rotating local file; there is no network to
        // report them to and silently losing them is how a systematic problem
        // stays invisible.
        GradeLog.install(this)
    }
}
