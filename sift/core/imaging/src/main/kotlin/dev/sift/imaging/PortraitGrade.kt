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
        /**
         * §6.12's "reduce contrast/exposure terms 50%, retry once".
         *
         * This used to be plumbed only into [SceneGrade], which meant the remedy
         * did nothing at all for a portrait: the retry recomputed exactly the
         * same correction, failed exactly the same gate, and fell back. Any
         * portrait needing a real exposure move was therefore guaranteed to ship
         * ungraded.
         */
        toneScale: Float = 1f,
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
        val scale = settings.strengthScale * toneScale
        val appliedL = accumulatedL * scale
        val appliedA = accumulatedA * scale
        val appliedB = accumulatedB * scale

        // ---- 3. Apply the delta globally to the entire frame. -------------
        ColorSpaces.linearToLab(image)
        shiftWithRolloff(image, appliedL, appliedA, appliedB)

        val finalSkinL = initial.first + appliedL
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

    /** Add a constant offset to every pixel of a LAB image. Proxy measurement only. */
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

    /**
     * Apply the skin correction to the whole frame, rolling off only the end of
     * the range that would otherwise clip.
     *
     * A flat `L += delta` is the obvious reading of §6.7's "apply the delta
     * globally", and it is wrong in a way that only shows up on real
     * photographs. An indoor portrait with skin at L*45 and a target of 68 needs
     * +23, and translating everything by +23 drives every highlight above L*77
     * into clipping. §6.12's no-new-clipping gate then rejects the result —
     * correctly — and the original ships. The subject would have been right and
     * everything bright behind them destroyed.
     *
     * The fix is *not* to pin white and compress everything below it. That
     * scales down contrast across the whole upper half of the range, which reads
     * as lost detail and trips the sharpness gate instead — trading one
     * false rejection for another.
     *
     * Instead the translation is exact everywhere except a band at the very top
     * as wide as the shift itself, where an exponential shoulder bends the
     * overflow back under 100. The shoulder is C1-continuous at the knee, so
     * there is no tonal step, and when the shift is zero the band has zero width
     * and the map is the identity. A frame that needs no correction is therefore
     * untouched, and a frame that needs a large one keeps its midtone contrast
     * intact while its brightest few percent compress rather than clip.
     *
     * The same shape is mirrored at the bottom for negative shifts. Chroma stays
     * a straight translation: the a* and b* moves are small and cannot clip
     * luminance.
     */
    private fun shiftWithRolloff(
        labImage: FloatImage,
        dl: Float,
        da: Float,
        db: Float,
    ) {
        labImage.requireSpace(ColorSpaceTag.LAB, "shiftWithRolloff")
        if (dl == 0f && da == 0f && db == 0f) return

        // Only the overflow needs bending, so the band is as wide as the shift.
        val band = kotlin.math.abs(dl).coerceAtMost(MAX_ROLLOFF_BAND_L)
        val liftingUp = dl > 0f
        val knee = if (liftingUp) 100f - band else band

        val d = labImage.data
        var i = 0
        while (i < d.size) {
            val shifted = d[i] + dl
            d[i] = when {
                band <= 0.01f -> shifted
                liftingUp && shifted > knee ->
                    knee + band * (1f - exp(-(shifted - knee) / band))
                !liftingUp && shifted < knee ->
                    knee - band * (1f - exp(-(knee - shifted) / band))
                else -> shifted
            }
            d[i + 1] += da
            d[i + 2] += db
            i += 3
        }
    }

    /**
     * Widest tonal band the roll-off may occupy, in L*.
     *
     * A bound. Beyond about 20 L* the shoulder would start eating real midtone
     * contrast, and a correction that large is better served by the exposure
     * term than by bending the curve further.
     */
    const val MAX_ROLLOFF_BAND_L = 20f

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
