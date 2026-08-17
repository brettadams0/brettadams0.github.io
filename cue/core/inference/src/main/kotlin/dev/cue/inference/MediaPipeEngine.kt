package dev.cue.inference

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import dev.cue.draft.GenerationRequest
import dev.cue.draft.InferenceEngine
import dev.cue.draft.InferenceOutOfMemoryException
import dev.cue.draft.InferenceUnavailableException
import dev.cue.model.ModelTier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * §3.2. Gemma 3n through MediaPipe's LLM Inference API.
 *
 * The whole file is a shim. Everything that decides what a draft *says* or how
 * it *sounds* is in `:core:draft` and `:core:voice`, and the deliberate
 * consequence is that this class has no policy in it to get wrong: it takes a
 * prompt, returns text, and translates two failure modes into the types §13
 * responds to.
 *
 * Two implementation notes from §3.3, both of which cost a session to learn:
 *
 * **Initialise eagerly.** The engine is built in `Application.onCreate` (trap 7).
 * Lazy initialisation produces a multi-second stall the first time you ask for a
 * draft, which reads as a broken app rather than as a loading model.
 *
 * **Benchmark the backend.** Reports on Gemma 3n have CPU beating GPU by roughly
 * 20%, especially on cold start (trap 6). [Backend] exists so that is a measured
 * choice on the device in your hand, not an assumption.
 */
class MediaPipeEngine private constructor(
    override val tier: ModelTier,
    private val inference: LlmInference,
    private val maxTopK: Int,
) : InferenceEngine, AutoCloseable {

    /**
     * One generation at a time.
     *
     * MediaPipe's session is not safe to drive concurrently, and §6.1 asks for
     * three separate calls. They are sequential rather than parallel anyway —
     * §12's 8-second budget for three variants assumes one model, and running
     * them together on a phone would thermally throttle the second two (trap 13).
     */
    private val lock = Mutex()

    override suspend fun generate(request: GenerationRequest): String = lock.withLock {
        withContext(Dispatchers.Default) {
            try {
                newSession(request).use { session ->
                    session.addQueryChunk(request.prompt)
                    session.generateResponse()
                }
            } catch (e: OutOfMemoryError) {
                // §13: drop one tier permanently, notify, retry. Catching an
                // Error is normally wrong; here the alternative is a crash loop
                // on a device whose only fault is being the one you own.
                throw InferenceOutOfMemoryException("Out of memory on $tier", e)
            } catch (e: IllegalStateException) {
                throw InferenceUnavailableException("Model session failed on $tier", e)
            }
        }
    }

    private fun newSession(request: GenerationRequest): LlmInferenceSession =
        LlmInferenceSession.createFromOptions(
            inference,
            LlmInferenceSession.LlmInferenceSessionOptions.builder()
                .setTemperature(request.temperature)
                .setTopK(maxTopK)
                .setRandomSeed(request.seed)
                .build(),
        )

    override fun close() = inference.close()

    enum class Backend { CPU, GPU }

    companion object {

        /**
         * §12: ~40 tokens per variant. The window is the prompt ceiling plus
         * generation, with headroom — §6.2 keeps the prompt under 1,500 tokens
         * and a window smaller than the prompt fails at load rather than at use.
         */
        private const val MAX_TOKENS = 2_048
        private const val TOP_K = 40

        /**
         * Loads [tier], or returns null if its file is missing.
         *
         * Null rather than throwing: §13's answer to a missing model is the
         * template path, and a null engine is exactly what `DraftPipeline`
         * already handles.
         */
        fun load(
            context: Context,
            tier: ModelTier,
            files: ModelFiles = ModelFiles(context),
            backend: Backend = Backend.CPU,
        ): MediaPipeEngine? {
            if (tier == ModelTier.TEMPLATE_ONLY) return null
            val file = files.installed(tier) ?: return null

            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(file.absolutePath)
                .setMaxTokens(MAX_TOKENS)
                .setPreferredBackend(
                    when (backend) {
                        Backend.CPU -> LlmInference.Backend.CPU
                        Backend.GPU -> LlmInference.Backend.GPU
                    },
                )
                .build()

            return try {
                MediaPipeEngine(tier, LlmInference.createFromOptions(context, options), TOP_K)
            } catch (e: RuntimeException) {
                throw InferenceUnavailableException(
                    "Could not load ${tier.modelId} from ${file.name}",
                    e,
                )
            }
        }
    }
}
