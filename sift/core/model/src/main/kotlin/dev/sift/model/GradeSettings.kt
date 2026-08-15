package dev.sift.model

import kotlinx.serialization.Serializable

/** Settings §11. Everything here is a *target* or a *bound*, never a per-frame value. */
@Serializable
data class GradeSettings(
    val routing: RoutingMode = RoutingMode.AUTO,
    val autoGradeOnCommit: Boolean = true,
    val upscale: UpscaleMode = UpscaleMode.GATED,

    /**
     * Portrait reference target for healthy fair skin, **true CIELAB units** (§6.7).
     * Editable because it wants tuning for different lighting; hardcoding it
     * would mean editing Kotlin to change a number.
     */
    val portraitTargetL: Float = DEFAULT_PORTRAIT_TARGET_L,
    val portraitTargetA: Float = DEFAULT_PORTRAIT_TARGET_A,
    val portraitTargetB: Float = DEFAULT_PORTRAIT_TARGET_B,

    /** §6.6 detail-preserving blend: fraction of Lanczos high-pass added back. */
    val detailBlendFraction: Float = DEFAULT_DETAIL_BLEND,

    val enabledPresets: Set<ExportPreset> = setOf(ExportPreset.MASTER),

    /** Debug: dump FrameAnalysis and derived parameters alongside the output. */
    val dumpDebugJson: Boolean = false,

    /** All adaptive amounts × this. §9.5 "regrade at reduced strength" sets 0.5. */
    val strengthScale: Float = 1.0f,
) {
    @Serializable
    enum class RoutingMode { AUTO, FORCE_PORTRAIT, FORCE_SCENE, OFF }

    @Serializable
    enum class UpscaleMode { OFF, GATED, ALWAYS }

    fun scaled(factor: Float): GradeSettings = copy(strengthScale = strengthScale * factor)

    companion object {
        // §6.7 reference target (healthy fair skin), true CIELAB units.
        const val DEFAULT_PORTRAIT_TARGET_L = 68.0f
        const val DEFAULT_PORTRAIT_TARGET_A = 12.5f
        const val DEFAULT_PORTRAIT_TARGET_B = 17.0f

        /**
         * §6.6 says to tune this once against real photos and record it in the
         * repo. 15% is the midpoint of the specified 12–18% band and stands as
         * the committed value until an A/B replaces it.
         */
        const val DEFAULT_DETAIL_BLEND = 0.15f

        /** §6.7 guard rail: final skin b* must land here or the gate fails. */
        const val SKIN_B_GUARD_MIN = 15.0f
        const val SKIN_B_GUARD_MAX = 22.0f

        /** §6.7 convergence. */
        const val MAX_PORTRAIT_ITERATIONS = 6
        const val PORTRAIT_CONVERGENCE_TOLERANCE = 0.5f
        const val PORTRAIT_DAMPING = 0.7f
        const val PORTRAIT_DAMPING_RETRY = 0.4f

        val VALIDATED_DEFAULTS = GradeSettings()
    }
}
