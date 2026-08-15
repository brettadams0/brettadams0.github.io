package dev.sift.imaging

import dev.sift.model.DerivedParams
import dev.sift.model.FrameAnalysis
import dev.sift.model.GradeSettings
import kotlin.math.abs
import kotlin.math.exp

/**
 * Portrait profile — skin-anchored (§6.7).
 *
 * ## The one constraint that matters most
 *
 * §6.7 is unusually emphatic, and the emphasis is earned by a specific past
 * failure: **one correction, derived from skin, applied globally.**
 *
 * - **No global white balance.** The phone's AWB has already corrected, often
 *   over-corrected against a warm wall. Adding a neutralisation pass on top,
 *   plus a red-pull on skin, is the triple-cooling failure that drove b* down to
 *   about 7 and produced an ill, grey subject. In Portrait the skin measurement
 *   *is* the white balance; a second correction double-counts. Note the
 *   deliberate asymmetry with Scene (§6.8.8), which does neutralise — Scene has
 *   no skin anchor, so a clamped neutral pass earns its place there.
 * - **No masked application.** The mask is imperfect. Applying through it
 *   blotches wherever it misclassifies. Measuring through it and applying
 *   globally means mask errors nudge the estimate rather than leaving artifacts.
 *
 * ## Why the iteration runs on a proxy
 *
 * The correction is a single global LAB offset, and the median of a
 * stride-sampled subsample is an unbiased estimator of the full-frame median.
 * So the loop converges on the *offset* using a 512px proxy, and that offset is
 * then applied once to the full-resolution frame. This is arithmetically the
 * same answer as iterating at full resolution and roughly sixty times cheaper,
 * which is what keeps six LAB iterations inside the §13 budget.
 */
object PortraitGrade {

    /**
     * Width of the luminance band around the skin anchor that the exposure
     * correction leaves alone, in L*. A bound.
     */
    const val SKIN_PRESERVE_BAND_L = 25f

    /** §6.7: exposure correction targets this median L*, clamped to ±15. */
    const val EXPOSURE_TARGET_L = 50f
    const val EXPOSURE_MAX_SHIFT = 15f

    data class Result(
        val params: DerivedParams.PortraitParams,
        val skinAnchored: Boolean,
    )

    /**
     * Grade [image] in place. [image] must be in linear sRGB; it is returned in
     * linear sRGB.
     *
     * [damping] exists so §6.12's skin-range gate can retry at 0.4 — a lower
     * damping converges more slowly but does not overshoot on frames with mixed
     * lighting where the skin median is being pulled by two light sources.
     */
    fun apply(
        image: FloatImage,
        analysis: FrameAnalysis,
        settings: GradeSettings,
        damping: Float = GradeSettings.PORTRAIT_DAMPING,
    ): Result {
        image.requireSpace(ColorSpaceTag.LINEAR_SRGB, "PortraitGrade.apply")

        val targetL = settings.portraitTargetL
        val targetA = settings.portraitTargetA
        val targetB = settings.portraitTargetB

        // ---- 1. Build the skin mask. Measure only. ------------------------
        val proxy = FrameAnalyzer.strideProxy(image)
        val proxyGamma = ColorSpaces.toGamma(proxy.copy())
        val mask = SkinMask.build(proxyGamma)
        val proxyLab = ColorSpaces.linearToLab(proxy)

        val initial = SkinMask.medianLab(proxyLab, mask)
        if (initial == null) {
            // Routed as a portrait but no measurable skin — usually a very small
            // or heavily shadowed subject. Anchoring on a handful of pixels is
            // worse than not anchoring, so no colour move is made and the frame
            // gets exposure treatment only.
            val exposure = applyExposure(image, analysis, targetL, settings.strengthScale)
            return Result(
                params = DerivedParams.PortraitParams(
                    targetL = targetL, targetA = targetA, targetB = targetB,
                    measuredL = Float.NaN, measuredA = Float.NaN, measuredB = Float.NaN,
                    appliedDeltaL = 0f, appliedDeltaA = 0f, appliedDeltaB = 0f,
                    iterations = 0, converged = false, damping = damping,
                    finalSkinL = Float.NaN, finalSkinA = Float.NaN, finalSkinB = Float.NaN,
                    exposureAmount = exposure,
                ),
                skinAnchored = false,
            )
        }

        // ---- 2-5. Measure, apply damped delta globally, re-measure --------
        var accumulatedL = 0f
        var accumulatedA = 0f
        var accumulatedB = 0f
        var iterations = 0
        var converged = false
        var current: Triple<Float, Float, Float> = initial

        while (iterations < GradeSettings.MAX_PORTRAIT_ITERATIONS) {
            val deltaL = targetL - current.first
            val deltaA = targetA - current.second
            val deltaB = targetB - current.third

            if (abs(deltaL) <= GradeSettings.PORTRAIT_CONVERGENCE_TOLERANCE &&
                abs(deltaA) <= GradeSettings.PORTRAIT_CONVERGENCE_TOLERANCE &&
                abs(deltaB) <= GradeSettings.PORTRAIT_CONVERGENCE_TOLERANCE
            ) {
                converged = true
                break
            }

            // Adaptive damping (§6.7): applying the full measured delta each
            // iteration oscillates on frames with mixed lighting. 0.7 converges
            // in three or four passes instead, and the result is stable.
            val stepL = deltaL * damping
            val stepA = deltaA * damping
            val stepB = deltaB * damping

            shiftLab(proxyLab, stepL, stepA, stepB)
            accumulatedL += stepL
            accumulatedA += stepA
            accumulatedB += stepB
            iterations++

            current = SkinMask.medianLab(proxyLab, mask) ?: break
        }

        if (!converged) {
            // §6.7 step 5: accept the closest result, do not loop further.
            val deltaL = targetL - current.first
            val deltaA = targetA - current.second
            val deltaB = targetB - current.third
            converged = abs(deltaL) <= GradeSettings.PORTRAIT_CONVERGENCE_TOLERANCE &&
                abs(deltaA) <= GradeSettings.PORTRAIT_CONVERGENCE_TOLERANCE &&
                abs(deltaB) <= GradeSettings.PORTRAIT_CONVERGENCE_TOLERANCE
        }

        // "Reduced strength" regrades (§9.5) scale the colour move too — the
        // whole point is a gentler version of the same decision.
        val scale = settings.strengthScale
        val appliedL = accumulatedL * scale
        val appliedA = accumulatedA * scale
        val appliedB = accumulatedB * scale

        // ---- 3. Apply the delta globally to the entire frame. -------------
        ColorSpaces.linearToLab(image)
        shiftLab(image, appliedL, appliedA, appliedB)

        val finalSkinL = current.first + (appliedL - accumulatedL)
        val finalSkinA = current.second + (appliedA - accumulatedA)
        val finalSkinB = current.third + (appliedB - accumulatedB)

        // ---- Exposure correction, if warranted ----------------------------
        val exposureAmount = exposureShift(analysis, scale)
        if (exposureAmount != 0f) {
            applyWeightedExposure(image, exposureAmount, finalSkinL)
        }

        ColorSpaces.labToLinear(image)

        return Result(
            params = DerivedParams.PortraitParams(
                targetL = targetL, targetA = targetA, targetB = targetB,
                measuredL = initial.first, measuredA = initial.second, measuredB = initial.third,
                appliedDeltaL = appliedL, appliedDeltaA = appliedA, appliedDeltaB = appliedB,
                iterations = iterations,
                converged = converged,
                damping = damping,
                finalSkinL = finalSkinL, finalSkinA = finalSkinA, finalSkinB = finalSkinB,
                exposureAmount = exposureAmount,
            ),
            skinAnchored = true,
        )
    }

    /** Add a constant offset to every pixel of a LAB image. */
    private fun shiftLab(labImage: FloatImage, dl: Float, da: Float, db: Float) {
        labImage.requireSpace(ColorSpaceTag.LAB, "shiftLab")
        if (dl == 0f && da == 0f && db == 0f) return
        val d = labImage.data
        var i = 0
        while (i < d.size) {
            d[i] += dl
            d[i + 1] += da
            d[i + 2] += db
            i += 3
        }
    }

    private fun exposureShift(analysis: FrameAnalysis, strengthScale: Float): Float {
        val raw = (EXPOSURE_TARGET_L - analysis.medianL)
            .coerceIn(-EXPOSURE_MAX_SHIFT, EXPOSURE_MAX_SHIFT)
        // Clamped to ±15 so a deliberately low-key portrait is not flattened
        // into a snapshot (§6.7).
        return raw * strengthScale
    }

    /**
     * Exposure correction that does not fight the skin anchor.
     *
     * A flat L* offset would undo the thing the previous forty lines just
     * established: it would move skin off its target as surely as it moves
     * everything else. The correction is therefore weighted to zero at the skin's
     * own lightness and to full strength well away from it, so a dark background
     * behind a correctly-exposed subject gets lifted and the subject does not
     * move. That is what "if warranted" is doing in §6.7 — the frame median and
     * the skin anchor disagree precisely when the *background* is the thing that
     * is mis-exposed.
     */
    private fun applyWeightedExposure(labImage: FloatImage, amount: Float, skinL: Float) {
        labImage.requireSpace(ColorSpaceTag.LAB, "applyWeightedExposure")
        val d = labImage.data
        var i = 0
        while (i < d.size) {
            val distance = (d[i] - skinL) / SKIN_PRESERVE_BAND_L
            val weight = 1f - exp(-distance * distance)
            d[i] += amount * weight
            i += 3
        }
    }

    /** Exposure-only path for frames with no measurable skin. */
    private fun applyExposure(
        image: FloatImage,
        analysis: FrameAnalysis,
        skinTargetL: Float,
        strengthScale: Float,
    ): Float {
        val amount = exposureShift(analysis, strengthScale)
        if (amount == 0f) return 0f
        ColorSpaces.linearToLab(image)
        applyWeightedExposure(image, amount, skinTargetL)
        ColorSpaces.labToLinear(image)
        return amount
    }
}
