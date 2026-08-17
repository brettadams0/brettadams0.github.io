package dev.cue.inference

import android.content.Context
import dev.cue.data.settings.SettingsRepository
import dev.cue.draft.GenerationRequest
import dev.cue.draft.InferenceEngine
import dev.cue.draft.InferenceOutOfMemoryException
import dev.cue.draft.InferenceUnavailableException
import dev.cue.model.ModelTier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Owns the loaded model for the life of the process, and implements §13's
 * response to it failing.
 *
 * Wrapping the engine rather than injecting it directly is what makes tier
 * demotion possible at all: on OOM the model has to be closed, a smaller one
 * loaded, and the choice **persisted**, because §13 says the demotion is
 * permanent. A `@Provides` function returning `LlmInference` cannot do any of
 * that, and would reload the same too-large model on the next launch and crash
 * in the same place.
 *
 * It is also an [InferenceEngine] itself, so `DraftPipeline` never sees the
 * difference between a healthy model, a demoted one, and none at all.
 */
class EngineHolder(
    private val context: Context,
    private val files: ModelFiles,
    private val settings: SettingsRepository,
) : InferenceEngine {

    private val lock = Mutex()
    private var delegate: MediaPipeEngine? = null

    private val _state = MutableStateFlow<State>(State.NotLoaded)
    val state: StateFlow<State> = _state

    sealed interface State {
        data object NotLoaded : State

        /** §6.5 carries the app here: no model, still useful. */
        data class TemplateOnly(val reason: String) : State
        data class Ready(val tier: ModelTier) : State
    }

    override val tier: ModelTier
        get() = delegate?.tier ?: ModelTier.TEMPLATE_ONLY

    /**
     * Called from `Application.onCreate` (trap 7).
     *
     * Failure is not an exception here. Every path out of this function leaves
     * the app usable, because the alternative — refusing to start because a 3 GB
     * file is missing — makes the template path unreachable exactly when it is
     * the only thing that works.
     */
    suspend fun initialise() = lock.withLock {
        val demoted = settings.tier.first()
        val resolved = files.resolveTier(demoted)
        if (resolved == ModelTier.TEMPLATE_ONLY) {
            _state.value = State.TemplateOnly("No model file in ${files.modelDirectory().name}")
            return@withLock
        }
        _state.value = try {
            delegate = MediaPipeEngine.load(context, resolved, files)
            if (delegate == null) {
                State.TemplateOnly("${resolved.modelId} is not installed")
            } else {
                State.Ready(resolved)
            }
        } catch (e: InferenceUnavailableException) {
            State.TemplateOnly(e.message ?: "Model failed to load")
        }
    }

    override suspend fun generate(request: GenerationRequest): String {
        val engine = delegate ?: throw InferenceUnavailableException("No model loaded")
        return try {
            engine.generate(request)
        } catch (e: InferenceOutOfMemoryException) {
            demote(e)
            // One retry, on the smaller model. If that fails too, the next
            // demotion lands on TEMPLATE_ONLY and the caller gets the
            // unavailable path, which the pipeline already handles.
            val smaller = delegate ?: throw InferenceUnavailableException("Demoted past the last tier", e)
            smaller.generate(request)
        }
    }

    /** §13: drop one tier permanently, notify, retry. */
    private suspend fun demote(cause: InferenceOutOfMemoryException) = lock.withLock {
        val from = delegate?.tier ?: ModelTier.TEMPLATE_ONLY
        val to = ModelTier.demote(from)
        delegate?.close()
        delegate = null
        settings.setTier(to)

        if (to == ModelTier.TEMPLATE_ONLY) {
            _state.value = State.TemplateOnly("Out of memory on ${from.modelId}")
            return@withLock
        }
        _state.value = try {
            delegate = MediaPipeEngine.load(context, to, files)
            if (delegate == null) {
                State.TemplateOnly("${to.modelId} is not installed")
            } else {
                State.Ready(to)
            }
        } catch (e: InferenceUnavailableException) {
            State.TemplateOnly(e.message ?: cause.message ?: "Model failed to load")
        }
    }

    /**
     * The engine the pipeline should be given: `this` when a model is loaded,
     * null when there is none.
     *
     * The distinction matters because `DraftPipeline` treats a null engine as
     * "template path only" rather than as an error to report.
     */
    fun asEngineOrNull(): InferenceEngine? = if (delegate == null) null else this

    fun close() {
        delegate?.close()
        delegate = null
    }
}
