package dev.cue.draft

import dev.cue.model.ModelTier

data class GenerationRequest(
    val prompt: String,
    /** §6.1: separate calls, different strategies, **different seeds**. */
    val seed: Int,
    val temperature: Float,
    val maxTokens: Int,
)

/**
 * The model, behind one method.
 *
 * Everything above this line is pure Kotlin and testable without a device;
 * everything below it is MediaPipe on Android or WebLLM in the browser. That
 * boundary is load-bearing rather than tidy: §14's suite covers the compiler,
 * the gates, retrieval and stage classification, none of which should need a
 * 3 GB model file to run.
 *
 * A null engine is a supported state, not an error. §13 lists three ways to end
 * up there — model missing, OOM demotion past the last tier, no WebGPU — and
 * §6.5's template path answers all of them.
 */
interface InferenceEngine {
    val tier: ModelTier

    /** Returns raw model text. Voice and gates are applied by the caller. */
    suspend fun generate(request: GenerationRequest): String
}

/**
 * Thrown when the model ran out of memory. §13: drop one tier permanently,
 * notify, retry — a distinct type because that response is distinct.
 */
class InferenceOutOfMemoryException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)

/** Thrown when the model file is missing or unreadable (§13). */
class InferenceUnavailableException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)

/**
 * §12. Twenty generations an hour, then a warning.
 *
 * > A hot phone that dies at 6pm is a worse outcome than a missed draft.
 *
 * Sustained inference is thermally expensive, and thermal throttling makes
 * every *subsequent* run slower (trap 13) — so the limit protects draft latency
 * as much as it protects the battery. Pure and clock-injected so the behaviour
 * at the boundary is testable without waiting an hour.
 */
class GenerationBudget(
    private val maxPerHour: Int = MAX_PER_HOUR,
    private val windowMillis: Long = 3_600_000L,
) {
    private val timestamps = ArrayDeque<Long>()

    fun record(now: Long) {
        timestamps.addLast(now)
        prune(now)
    }

    fun used(now: Long): Int {
        prune(now)
        return timestamps.size
    }

    fun remaining(now: Long): Int = (maxPerHour - used(now)).coerceAtLeast(0)

    /** True once the budget is spent. The UI warns; it does not block. */
    fun exhausted(now: Long): Boolean = remaining(now) == 0

    private fun prune(now: Long) {
        while (timestamps.isNotEmpty() && now - timestamps.first() > windowMillis) {
            timestamps.removeFirst()
        }
    }

    companion object {
        const val MAX_PER_HOUR = 20
    }
}
