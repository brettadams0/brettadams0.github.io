package dev.sift.model

import kotlinx.serialization.Serializable

/** The six quality gates of §6.12. Every output is verified before it is kept. */
@Serializable
enum class QualityGate(val displayName: String) {
    SKIN_RANGE("Skin range"),
    NO_NEW_CLIPPING("No new clipping"),
    NO_SHADOW_CRUSH("No shadow crush"),
    SHARPNESS_PRESERVED("Sharpness preserved"),
    NO_BANDING("No banding introduced"),
    CHROMA_SANITY("Chroma sanity"),
    ;

    companion object {
        // Bounds, not per-frame values.
        const val CLIPPING_INCREASE_MAX = 0.002f
        const val SHADOW_INCREASE_MAX = 0.002f
        const val SHARPNESS_RETENTION_MIN = 0.85f
        const val CHROMA_CHANGE_MAX = 0.40f

        /**
         * Banding tolerance.
         *
         * The metric is the fraction of horizontally adjacent pixel pairs that
         * are *bit-identical* inside shallow-gradient flat regions. A smooth ramp
         * quantised without dither produces long runs of one level with an abrupt
         * step between them — that is precisely what a contour band is — so the
         * identical-pair fraction goes very high. Dither breaks those runs up,
         * scattering the transition, and the fraction drops sharply.
         *
         * Solid areas would also score high, so the measurement only considers
         * blocks whose L* range is a shallow gradient rather than flat colour
         * (see `QualityGates.BANDING_MIN_RANGE_L`/`BANDING_MAX_RANGE_L`).
         */
        const val BANDING_IDENTICAL_PAIR_FRACTION_MAX = 0.75f
    }
}

@Serializable
data class GateResult(
    val gate: QualityGate,
    val passed: Boolean,
    /** The number that decided it, so a failure is diagnosable without a re-run. */
    val measured: Float,
    val threshold: Float,
    val note: String? = null,
)

/**
 * The full verification record for one output, serialized to
 * `EditJob.gateResultsJson`. [fellBackToOriginal] is load-bearing far beyond
 * reporting: §9.3 invariant 2 forbids ever trashing the original of an asset
 * whose grade fell back, and that is the one genuinely unrecoverable bug in the
 * app (trap #14).
 */
@Serializable
data class GateReport(
    val results: List<GateResult>,
    val attempts: Int,
    val fellBackToOriginal: Boolean,
    val fallbackReason: String? = null,
) {
    val allPassed: Boolean get() = results.all { it.passed }
    val failed: List<GateResult> get() = results.filter { !it.passed }

    fun result(gate: QualityGate): GateResult? = results.firstOrNull { it.gate == gate }

    companion object {
        fun fallback(reason: String, results: List<GateResult> = emptyList(), attempts: Int = 1) =
            GateReport(
                results = results,
                attempts = attempts,
                fellBackToOriginal = true,
                fallbackReason = reason,
            )
    }
}
