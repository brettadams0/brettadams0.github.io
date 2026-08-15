package dev.sift.model

import kotlinx.serialization.Serializable

/**
 * Every parameter the pipeline chose for one frame, serialized to
 * `EditJob.derivedParamsJson` (§5, §6.3).
 *
 * This is not optional. When a photo comes out wrong you need to know exactly
 * what the pipeline decided and why, without re-running it.
 */
@Serializable
data class DerivedParams(
    val profile: GradeProfile,
    val contentClass: ContentClass,
    val strengthScale: Float,

    val denoise: DenoiseParams? = null,
    val portrait: PortraitParams? = null,
    val scene: SceneParams? = null,
    val localContrast: LocalContrastParams? = null,
    val resize: ResizeParams? = null,
    val outputSharpen: SharpenParams? = null,
    val upscale: UpscaleParams? = null,
) {
    @Serializable
    data class DenoiseParams(
        val ran: Boolean,
        val reasonSkipped: String? = null,
        val hLuma: Float = 0f,
        val hChroma: Float = 0f,
    )

    @Serializable
    data class PortraitParams(
        val targetL: Float,
        val targetA: Float,
        val targetB: Float,
        val measuredL: Float,
        val measuredA: Float,
        val measuredB: Float,
        val appliedDeltaL: Float,
        val appliedDeltaA: Float,
        val appliedDeltaB: Float,
        val iterations: Int,
        val converged: Boolean,
        val damping: Float,
        val finalSkinL: Float,
        val finalSkinA: Float,
        val finalSkinB: Float,
        val exposureAmount: Float,
    )

    @Serializable
    data class SceneParams(
        val highlightReconstructionApplied: Boolean,
        val highlightRolloffStrength: Float,
        val shadowLift: Float,
        val blackPointIn: Float,
        val whitePointIn: Float,
        val midtoneGamma: Float,
        val contrastAmplitude: Float,
        val vibranceAmount: Float,
        val whiteBalanceDeltaA: Float,
        val whiteBalanceDeltaB: Float,
        val whiteBalanceClamped: Boolean,
    )

    @Serializable
    data class LocalContrastParams(
        val radiusPx: Float,
        val amount: Float,
        val haloClampL: Float,
    )

    @Serializable
    data class ResizeParams(
        val fromWidth: Int,
        val fromHeight: Int,
        val toWidth: Int,
        val toHeight: Int,
        val method: String,
    )

    @Serializable
    data class SharpenParams(
        val radiusPx: Float,
        val amount: Float,
        val thresholdL: Float,
    )

    @Serializable
    data class UpscaleParams(
        val requestedFactor: Float,
        val effectiveFactor: Float,
        val cappedBySharpness: Boolean,
        val method: String,
        val detailBlendFraction: Float,
    )
}
