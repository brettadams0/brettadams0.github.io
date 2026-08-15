package dev.sift.imaging

import dev.sift.model.DerivedParams
import dev.sift.model.FrameAnalysis
import dev.sift.model.GradeSettings
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Scene profile — tone-anchored (§6.8).
 *
 * For everything without people. Every parameter is a function of
 * [FrameAnalysis]; nothing here is a constant that applies to all images.
 *
 * The eight stages run in the order §6.8 gives them, but they are not eight
 * passes over the pixels. Stages 2–6 are all tone curves in gamma-encoded space,
 * so they are composed into a single LUT (§2.2, [ToneCurve]) and applied once.
 * Composition is not just a speed trick — it also means each stage derives its
 * parameters from the tonal landmarks *as the previous stage left them*, which
 * is why the black point, median and white point are carried through the chain
 * rather than re-read from the original analysis.
 *
 * The deliberate asymmetry with Portrait (§6.7) is stage 8: Scene neutralises,
 * Portrait does not. Portrait's skin measurement *is* its white balance, and a
 * second correction double-counts. Scene has no such anchor, so a **clamped**
 * neutral pass earns its place — clamped being the operative word, because
 * unclamped grey-world turns golden hour and blue hour grey and destroys exactly
 * the frames most worth keeping (trap #9).
 */
object SceneGrade {

    // ---- Bounds and targets (§0 rule 1: never per-image values) -----------

    /** §6.8.2 — rolloff engages above this clipped fraction. */
    const val HIGHLIGHT_ROLLOFF_TRIGGER = 0.005f
    const val HIGHLIGHT_ROLLOFF_SATURATION = 0.05f
    const val HIGHLIGHT_ROLLOFF_MIN_STRENGTH = 0.20f
    const val HIGHLIGHT_ROLLOFF_MAX_STRENGTH = 0.60f

    /** §6.8.3 — shadow lift engages above this crushed fraction. */
    const val SHADOW_LIFT_TRIGGER = 0.01f
    const val SHADOW_LIFT_SATURATION = 0.10f
    const val SHADOW_LIFT_MIN = 0.05f
    const val SHADOW_LIFT_MAX = 0.25f

    /** §6.8.3 — "cap the lift so blackPointL never rises above 3". */
    const val BLACK_POINT_CEILING_L = 3f

    /** §6.8.4 — "never 0/100, that clips". */
    const val LEVELS_BLACK_TARGET_L = 1f
    const val LEVELS_WHITE_TARGET_L = 99f

    /**
     * Floor and ceiling the *composed* curve must respect, in L*.
     *
     * §6.8.4 says the auto-levels must be conservative, and this is what makes
     * that word mean something. Mapping the 0.1 percentile onto L* 1 is only
     * conservative for a frame that already has blacks; on a flat, hazy frame
     * whose darkest pixels sit at L* 24 it is an enormous contrast stretch, and
     * the gamma and S-curve that follow push a chunk of the shadows below the
     * L* 2 crush line. The levels endpoints are therefore chosen against the
     * output of the *whole* tone chain rather than their own stage, so the
     * composed curve cannot manufacture crushed shadows or new clipping — the
     * exact conditions §6.12's gates two and three test for.
     *
     * Both sit inside the §6.3 thresholds (L* < 2 crushed, L* > 98 clipped) with
     * margin, so a gate failure means something else went wrong.
     */
    const val COMPOSED_BLACK_FLOOR_L = 2.5f
    const val COMPOSED_WHITE_CEILING_L = 97.5f

    /** §6.8.5 — midtone target. */
    const val MIDTONE_TARGET_L = 50f
    const val MIDTONE_GAMMA_MIN = 0.6f
    const val MIDTONE_GAMMA_MAX = 1.6f

    /** §6.8.6 — S-curve amplitude for a completely flat frame. */
    const val CONTRAST_BASE_AMPLITUDE = 0.35f

    /** §6.8.7 — "base amount +20", as a fraction. */
    const val VIBRANCE_BASE_AMOUNT = 0.20f

    /** Chroma at which content counts as already vivid and needs no help. */
    const val VIVID_CHROMA_REFERENCE = 60f

    /** Chroma at which a pixel is considered fully saturated for the weighting. */
    const val VIBRANCE_SATURATION_REFERENCE = 50f

    /** §6.8.8 — the clamp that saves sunsets. */
    const val WHITE_BALANCE_CLAMP = 5f

    /** Ceiling on a reconstructed channel, in linear light. */
    const val RECONSTRUCTION_CEILING = 4f

    /** Below this per-channel clipped fraction there is nothing to reconstruct. */
    const val RECONSTRUCTION_TRIGGER = 0.0005f

    /**
     * Grade [image] in place. Must be handed linear sRGB; returns linear sRGB.
     */
    fun apply(
        image: FloatImage,
        analysis: FrameAnalysis,
        settings: GradeSettings,
        /** §6.12 retry: halve the contrast and exposure terms after a gate failure. */
        toneScale: Float = 1f,
    ): DerivedParams.SceneParams {
        image.requireSpace(ColorSpaceTag.LINEAR_SRGB, "SceneGrade.apply")
        val scale = settings.strengthScale * toneScale

        // ---- 1. Per-channel highlight reconstruction (linear light) -------
        val reconstructed = reconstructHighlights(image, analysis)

        // ---- 2-6. One composed tone curve, in gamma-encoded space ---------
        ColorSpaces.toGamma(image)

        var blackNow = ColorSpaces.gammaValueForL(analysis.blackPointL)
        var whiteNow = ColorSpaces.gammaValueForL(analysis.whitePointL)
        var medianNow = ColorSpaces.gammaValueForL(analysis.medianL)

        // 2. Highlight rolloff — a shoulder, not a hard curve.
        val rolloffStrength = if (analysis.clippedHighlightFraction > HIGHLIGHT_ROLLOFF_TRIGGER) {
            val t = ((analysis.clippedHighlightFraction - HIGHLIGHT_ROLLOFF_TRIGGER) /
                (HIGHLIGHT_ROLLOFF_SATURATION - HIGHLIGHT_ROLLOFF_TRIGGER)).coerceIn(0f, 1f)
            (HIGHLIGHT_ROLLOFF_MIN_STRENGTH +
                t * (HIGHLIGHT_ROLLOFF_MAX_STRENGTH - HIGHLIGHT_ROLLOFF_MIN_STRENGTH)) * scale
        } else {
            0f
        }
        val knee = (1f - rolloffStrength).coerceIn(0.2f, 1f)
        val shoulder: (Float) -> Float = { x -> shoulderCurve(x, knee) }
        blackNow = shoulder(blackNow); whiteNow = shoulder(whiteNow); medianNow = shoulder(medianNow)

        // 3. Shadow lift, capped so the frame does not go milky.
        val shadowLift = if (analysis.crushedShadowFraction > SHADOW_LIFT_TRIGGER) {
            val t = ((analysis.crushedShadowFraction - SHADOW_LIFT_TRIGGER) /
                (SHADOW_LIFT_SATURATION - SHADOW_LIFT_TRIGGER)).coerceIn(0f, 1f)
            val desired = (SHADOW_LIFT_MIN + t * (SHADOW_LIFT_MAX - SHADOW_LIFT_MIN)) * scale
            val ceiling = ColorSpaces.gammaValueForL(BLACK_POINT_CEILING_L)
            val headroom = (1f - blackNow).pow(3)
            val maxLift = if (headroom > 1e-4f) {
                ((ceiling - blackNow) / headroom).coerceAtLeast(0f)
            } else {
                0f
            }
            minOf(desired, maxLift, SHADOW_LIFT_MAX)
        } else {
            0f
        }
        val lift: (Float) -> Float = { x -> x + shadowLift * (1f - x).pow(3) }
        blackNow = lift(blackNow); whiteNow = lift(whiteNow); medianNow = lift(medianNow)

        // Stages 4, 5 and 6 are interdependent: the gamma is derived from the
        // median *after* levels, while the levels endpoints have to be chosen so
        // that the gamma and S-curve downstream do not push the result past the
        // crush and clip lines. That is resolved by deriving 5 and 6 from a
        // provisional levels pass, then fixing the real endpoints against the
        // finished tail of the chain.

        val blackIn = blackNow
        val whiteIn = whiteNow
        val inputRange = whiteIn - blackIn
        val hasRange = inputRange > MIN_LEVELS_RANGE

        val provisionalBlack = ColorSpaces.gammaValueForL(LEVELS_BLACK_TARGET_L)
        val provisionalWhite = ColorSpaces.gammaValueForL(LEVELS_WHITE_TARGET_L)
        val provisionalMedian = if (hasRange) {
            provisionalBlack + (medianNow - blackIn) *
                (provisionalWhite - provisionalBlack) / inputRange
        } else {
            medianNow
        }

        // 5. Midtone gamma toward L* 50, damped by histogram entropy: a
        //    deliberately high-key or low-key frame has low entropy and moves less.
        val midtoneGamma = run {
            val target = ColorSpaces.gammaValueForL(MIDTONE_TARGET_L)
            if (provisionalMedian <= 1e-3f || provisionalMedian >= 1f - 1e-3f || target <= 0f) {
                1f
            } else {
                val raw = (ln(target.toDouble()) / ln(provisionalMedian.toDouble())).toFloat()
                val damped = 1f + (raw - 1f) * analysis.histogramEntropy * scale
                damped.coerceIn(MIDTONE_GAMMA_MIN, MIDTONE_GAMMA_MAX)
            }
        }
        val gamma: (Float) -> Float = { x -> if (x <= 0f) x else x.pow(midtoneGamma) }

        // 6. Contrast S-curve, amplitude inversely proportional to the frame's
        //    *existing* dynamic range — a flat frame gets more, a contrasty one
        //    almost none.
        val contrastAmplitude = (
            CONTRAST_BASE_AMPLITUDE *
                (1f - analysis.dynamicRange / 100f).coerceIn(0f, 1f) * scale
            ).coerceIn(0f, CONTRAST_BASE_AMPLITUDE)
        val sCurve: (Float) -> Float = { x -> sCurveAt(x, contrastAmplitude) }

        // 4. Conservative auto-levels. Percentiles, never min/max, never onto
        //    0/100, and never so far that the rest of the chain crushes or clips.
        val tail: (Float) -> Float = { x -> sCurve(gamma(x)) }
        val blackTarget = maxOf(
            provisionalBlack,
            solveMonotonic(tail, ColorSpaces.gammaValueForL(COMPOSED_BLACK_FLOOR_L)),
        )
        val whiteTarget = minOf(
            provisionalWhite,
            solveMonotonic(tail, ColorSpaces.gammaValueForL(COMPOSED_WHITE_CEILING_L)),
        )

        val levelsGain: Float
        val levelsOffset: Float
        if (hasRange && whiteTarget > blackTarget) {
            levelsGain = (whiteTarget - blackTarget) / inputRange
            levelsOffset = blackTarget - blackIn * levelsGain
        } else {
            levelsGain = 1f
            levelsOffset = 0f
        }
        val levels: (Float) -> Float = { x -> x * levelsGain + levelsOffset }

        blackNow = tail(levels(blackIn))
        whiteNow = tail(levels(whiteIn))
        medianNow = tail(levels(medianNow))

        val curve = ToneCurve.build { x ->
            tail(levels(lift(shoulder(x))))
        }
        curve.applyTo(image)

        // ---- 7-8. Chroma work, in LAB ------------------------------------
        ColorSpaces.toLinear(image)
        ColorSpaces.linearToLab(image)

        // 7. Vibrance, not saturation: weighted by (1 - localSaturation) so
        //    muted areas gain and already-vivid areas do not blow out. Scaled
        //    down by chromaP95 — a vivid frame needs less.
        val vibranceAmount = (
            VIBRANCE_BASE_AMOUNT *
                (1f - (analysis.chromaP95 / VIVID_CHROMA_REFERENCE).coerceIn(0f, 1f)) *
                settings.strengthScale
            ).coerceAtLeast(0f)
        if (vibranceAmount > 1e-4f) applyVibrance(image, vibranceAmount)

        // 8. Clamped grey-world white balance.
        val rawA = -analysis.greyWorldCastA
        val rawB = -analysis.greyWorldCastB
        val wbA = rawA.coerceIn(-WHITE_BALANCE_CLAMP, WHITE_BALANCE_CLAMP) * settings.strengthScale
        val wbB = rawB.coerceIn(-WHITE_BALANCE_CLAMP, WHITE_BALANCE_CLAMP) * settings.strengthScale
        val wasClamped = abs(rawA) > WHITE_BALANCE_CLAMP || abs(rawB) > WHITE_BALANCE_CLAMP
        if (wbA != 0f || wbB != 0f) {
            val d = image.data
            var i = 0
            while (i < d.size) {
                d[i + 1] += wbA
                d[i + 2] += wbB
                i += 3
            }
        }

        ColorSpaces.labToLinear(image)

        return DerivedParams.SceneParams(
            highlightReconstructionApplied = reconstructed,
            highlightRolloffStrength = rolloffStrength,
            shadowLift = shadowLift,
            blackPointIn = analysis.blackPointL,
            whitePointIn = analysis.whitePointL,
            midtoneGamma = midtoneGamma,
            contrastAmplitude = contrastAmplitude,
            vibranceAmount = vibranceAmount,
            whiteBalanceDeltaA = wbA,
            whiteBalanceDeltaB = wbB,
            whiteBalanceClamped = wasClamped,
        )
    }

    private const val MIN_LEVELS_RANGE = 0.02f

    /**
     * Smallest `x` with `f(x) >= target`, for a monotonic non-decreasing `f` on
     * [0,1]. Bisection: 24 steps resolves finer than an 8-bit code value, and
     * the whole search runs once per frame.
     */
    private fun solveMonotonic(f: (Float) -> Float, target: Float): Float {
        if (f(0f) >= target) return 0f
        if (f(1f) <= target) return 1f
        var lo = 0f
        var hi = 1f
        repeat(24) {
            val mid = (lo + hi) * 0.5f
            if (f(mid) < target) lo = mid else hi = mid
        }
        return (lo + hi) * 0.5f
    }

    /**
     * Smooth shoulder above [knee], asymptotic to 1.
     *
     * `out = k + (1-k)(1 - e^-t)` where `t = (x-k)/(1-k)`. It is C1-continuous
     * with the identity at the knee — the derivative is exactly 1 there — which
     * is what "shoulder, not a hard curve" means in §6.8.2. A curve with a
     * derivative discontinuity puts a visible tonal step across a sky.
     *
     * Being asymptotic rather than clamping also means the reconstructed
     * highlights from stage 1, which legitimately exceed 1.0, land back inside
     * range with their relative order intact instead of all flattening onto white.
     */
    fun shoulderCurve(x: Float, knee: Float): Float {
        if (knee >= 1f || x <= knee) return x
        val t = (x - knee) / (1f - knee)
        return knee + (1f - knee) * (1f - kotlin.math.exp(-t))
    }

    /**
     * `out = x - amp·sin(2πx)/2π` — steeper through the midtones, flatter at
     * both ends, fixed at 0 and 1, and monotonic for any amplitude below 1.
     */
    fun sCurveAt(x: Float, amplitude: Float): Float {
        if (amplitude <= 0f) return x
        val clamped = x.coerceIn(0f, 1f)
        val shaped = clamped - amplitude * sin(2.0 * PI * clamped).toFloat() / (2f * PI.toFloat())
        return if (x > 1f) x - (clamped - shaped) else shaped
    }

    private fun applyVibrance(labImage: FloatImage, amount: Float) {
        labImage.requireSpace(ColorSpaceTag.LAB, "applyVibrance")
        val d = labImage.data
        var i = 0
        while (i < d.size) {
            val a = d[i + 1]
            val b = d[i + 2]
            val c = sqrt(a * a + b * b)
            val saturation = (c / VIBRANCE_SATURATION_REFERENCE).coerceIn(0f, 1f)
            val boost = 1f + amount * (1f - saturation)
            d[i + 1] = a * boost
            d[i + 2] = b * boost
            i += 3
        }
    }

    /**
     * Per-channel highlight reconstruction (§6.8.1).
     *
     * When one channel clips and the others do not, the clipped one can be
     * estimated from the ratio the unclipped channels hold in bright,
     * *unclipped* parts of the same frame. This recovers detail in blown skies
     * and light sources that a luminance-only highlight pull cannot touch —
     * a luminance measurement cannot even see the case, because the pixel is not
     * uniformly bright, it is bright in one channel.
     *
     * Reconstructed values deliberately exceed 1.0. That is legal (§2.1) and
     * necessary: the shoulder in stage 2 is what maps them back, and clamping
     * here would throw the recovered detail away before it is used.
     */
    private fun reconstructHighlights(image: FloatImage, analysis: FrameAnalysis): Boolean {
        val clip = ColorSpaces.srgbToLinear(254f / 255f)
        val fractions = analysis.channelClipFractions
        if (fractions.isEmpty()) return false

        if (fractions.none { it > RECONSTRUCTION_TRIGGER }) return false

        val d = image.data

        // Channel means over bright-but-unclipped pixels: the frame's own
        // highlight colour balance, not an assumed one.
        val low = clip * 0.5f
        var sumR = 0.0
        var sumG = 0.0
        var sumB = 0.0
        var n = 0L
        var i = 0
        while (i < d.size) {
            val r = d[i]; val g = d[i + 1]; val b = d[i + 2]
            if (r < clip && g < clip && b < clip && (r > low || g > low || b > low)) {
                sumR += r; sumG += g; sumB += b; n++
            }
            i += 3
        }
        if (n < MIN_RATIO_SAMPLES) return false

        val meanR = (sumR / n).toFloat()
        val meanG = (sumG / n).toFloat()
        val meanB = (sumB / n).toFloat()
        if (meanR <= 1e-5f || meanG <= 1e-5f || meanB <= 1e-5f) return false
        val means = floatArrayOf(meanR, meanG, meanB)

        var touched = false
        val values = FloatArray(3)
        i = 0
        while (i < d.size) {
            values[0] = d[i]; values[1] = d[i + 1]; values[2] = d[i + 2]
            var clippedCount = 0
            for (c in 0..2) if (values[c] >= clip) clippedCount++

            // Exactly the mixed case. A pixel with all three channels clipped is
            // a specular highlight with no unclipped survivor to reconstruct
            // from, and inventing detail there is what §2.7 forbids.
            if (clippedCount in 1..2) {
                for (c in 0..2) {
                    if (values[c] < clip) continue
                    var estimate = 0f
                    var contributors = 0
                    for (u in 0..2) {
                        if (u == c || values[u] >= clip) continue
                        estimate += values[u] * (means[c] / means[u])
                        contributors++
                    }
                    if (contributors == 0) continue
                    estimate /= contributors
                    val recovered = estimate.coerceIn(values[c], RECONSTRUCTION_CEILING)
                    if (recovered > values[c]) {
                        d[i + c] = recovered
                        touched = true
                    }
                }
            }
            i += 3
        }
        return touched
    }

    private const val MIN_RATIO_SAMPLES = 256L
}
