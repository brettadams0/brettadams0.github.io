package dev.cue.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import dev.cue.inference.EngineHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * §3.3, trap 7: initialise the inference session here, not on first use.
 *
 * > Lazy init produces a multi-second stall on first use that reads as a broken
 * > app.
 *
 * The launch is on [Dispatchers.Default] rather than blocking `onCreate`, which
 * is the part the trap does not say out loud: loading a 3 GB model on the main
 * thread trades a stall before the first draft for an ANR before the first
 * screen. Everything the UI needs while it loads — the corpus, the voice
 * profile, §6.5's template path — is available without a model.
 */
@HiltAndroidApp
class CueApplication : Application() {

    @Inject lateinit var engines: EngineHolder

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        scope.launch { engines.initialise() }
    }

    override fun onTerminate() {
        engines.close()
        super.onTerminate()
    }
}
