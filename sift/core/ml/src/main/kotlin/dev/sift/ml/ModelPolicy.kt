package dev.sift.ml

import dev.sift.imaging.FaceDetector
import dev.sift.imaging.FloatImage
import dev.sift.imaging.FrameAnalyzer
import dev.sift.imaging.Quantize
import dev.sift.imaging.Resample
import dev.sift.imaging.Statistics
import dev.sift.imaging.Upscale
import kotlin.math.abs

/**
 * Why this module currently contains no models.
 *
 * §18 lists three still-open decisions. The first of them governs everything
 * that would live here:
 *
 * > **3. Does upscale survive the §6.6 A/B?** Answer before writing ONNX code —
 * > it can delete M8 entirely.
 *
 * And §6.6 spells out the procedure: *"Before writing any ONNX code: A/B it.
 * Build a Lanczos + unsharp baseline. Compare on ten of your real photos at
 * 100%. If the difference doesn't justify 30 seconds per image and a native
 * dependency, ship the baseline and delete this section."*
 *
 * So the baseline exists ([Upscale.LanczosBaseline]), the seam for a learned
 * model exists ([Upscale.SuperResolver]), the treatments that keep a learned
 * upscaler from looking synthetic exist and are written against that seam
 * ([Upscale.detailPreservingBlend], [Upscale.softenSmallFaces]), and
 * [UpscaleComparison] runs the comparison. What does not exist is the ONNX
 * Runtime dependency and a Real-ESRGAN session, because building those first
 * would be doing the work the spec says to justify.
 *
 * ## The same reasoning, applied to face detection
 *
 * §6.3 prefers YuNet over ML Kit on the grounds that YuNet is "already in the
 * stack" — true when OpenCV was a dependency, and no longer true now that the
 * pipeline is pure Kotlin (see `:core:imaging`). Pulling in ONNX Runtime *solely*
 * for face detection is a different trade from getting it for free alongside a
 * super-resolution model, so the choice is downstream of the same A/B:
 *
 * - **If the A/B keeps Real-ESRGAN**, ONNX Runtime is in the app regardless and
 *   YuNet costs a 340KB model file. Take YuNet.
 * - **If the A/B drops it**, ML Kit's bundled face detector is the cheaper
 *   dependency, and it is free and offline, which is all §3 requires.
 *
 * Meanwhile the router degrades rather than breaking: §6.4's portrait rule is
 * `(faceCount > 0 || skinFraction > 0.08)`, so with [FaceDetector.None] the
 * skin-fraction term carries the decision alone. Faces are a second guard on the
 * terracotta failure (trap #13), not the only guard — the contiguity check is
 * the first.
 */
object ModelPolicy {

    /**
     * The detector the app runs with today.
     *
     * Replacing this is a one-line change once the A/B above is settled.
     */
    val faceDetector: FaceDetector = FaceDetector.None

    const val RATIONALE: String =
        "No face detector wired up. §6.4's portrait rule falls back to its " +
            "skin-fraction term, and the contiguity guard still rejects terracotta. " +
            "See ModelPolicy for why this waits on the §6.6 A/B."
}

/**
 * The §6.6 A/B harness.
 *
 * Runs both candidates over the same sources and reports the numbers the
 * decision needs: how much real detail each recovered, how much each cost, and
 * whether the difference justifies 30 seconds per image and a native dependency.
 *
 * Deliberately not a test. §6.6 says to compare *"on ten of your real photos at
 * 100%"* — the judgement is visual and the photos are the author's, so this
 * produces artefacts to look at and figures to look at them with, rather than
 * asserting a threshold nobody has agreed on yet.
 */
object UpscaleComparison {

    data class Candidate(
        val name: String,
        val output: FloatImage,
        val elapsedMs: Long,
        /** P90 tile sharpness — real recovered detail, not invented (§6.3, §2.7). */
        val sharpnessP90: Float,
        /** Mean absolute difference from the baseline, in 8-bit levels. */
        val deltaFromBaseline: Float,
    )

    data class Comparison(
        val sourceName: String,
        val factor: Float,
        val baseline: Candidate,
        val challenger: Candidate?,
    ) {
        /**
         * §6.6's actual question, stated as a number: does the challenger recover
         * meaningfully more detail than the baseline?
         */
        val sharpnessGain: Float
            get() = challenger?.let { it.sharpnessP90 / baseline.sharpnessP90.coerceAtLeast(1e-3f) } ?: 1f

        val extraSecondsPerImage: Float
            get() = ((challenger?.elapsedMs ?: 0L) - baseline.elapsedMs) / 1000f

        fun verdict(): String = when {
            challenger == null -> "no challenger supplied — baseline only"
            sharpnessGain < 1.10f ->
                "baseline wins: challenger recovered ${"%.0f".format((sharpnessGain - 1) * 100)}% " +
                    "more detail for ${"%.1f".format(extraSecondsPerImage)}s more per image"
            else ->
                "challenger recovered ${"%.0f".format((sharpnessGain - 1) * 100)}% more detail " +
                    "for ${"%.1f".format(extraSecondsPerImage)}s more per image — inspect at 100%"
        }
    }

    /**
     * @param source linear sRGB, as decoded.
     * @param challenger the learned upscaler under evaluation, or null to
     *   characterise the baseline alone.
     */
    fun compare(
        sourceName: String,
        source: FloatImage,
        factor: Float,
        challenger: Upscale.SuperResolver? = null,
        detailBlendFraction: Float = 0.15f,
    ): Comparison {
        val baselineStart = System.nanoTime()
        val baselineOut = Upscale.LanczosBaseline.upscale(source, factor)
        val baselineMs = (System.nanoTime() - baselineStart) / 1_000_000

        val baseline = Candidate(
            name = "lanczos4 + unsharp",
            output = baselineOut,
            elapsedMs = baselineMs,
            sharpnessP90 = FrameAnalyzer.sharpnessP90(baselineOut),
            deltaFromBaseline = 0f,
        )

        val challengerCandidate = challenger?.let { resolver ->
            val start = System.nanoTime()
            val out = resolver.upscale(source, factor)
            Upscale.detailPreservingBlend(out, baselineOut, detailBlendFraction)
            val ms = (System.nanoTime() - start) / 1_000_000

            Candidate(
                name = "super-resolution + ${"%.0f".format(detailBlendFraction * 100)}% detail blend",
                output = out,
                elapsedMs = ms,
                sharpnessP90 = FrameAnalyzer.sharpnessP90(out),
                deltaFromBaseline = meanAbsoluteDifference(out, baselineOut),
            )
        }

        return Comparison(sourceName, factor, baseline, challengerCandidate)
    }

    /**
     * Characterise the detail blend itself, so the 12–18% band in §6.6 can be
     * settled with a number instead of a guess (§18 open decision 4).
     */
    fun sweepDetailBlend(
        source: FloatImage,
        factor: Float,
        resolver: Upscale.SuperResolver,
        fractions: List<Float> = listOf(0.00f, 0.06f, 0.12f, 0.15f, 0.18f, 0.24f),
    ): List<Pair<Float, Float>> {
        val lanczos = Upscale.LanczosBaseline.upscale(source, factor)
        return fractions.map { fraction ->
            val out = resolver.upscale(source, factor)
            Upscale.detailPreservingBlend(out, lanczos, fraction)
            val plane = FrameAnalyzer.lightnessPlane(out)
            fraction to Statistics.laplacianVarianceP90(plane, out.width, out.height)
        }
    }

    private fun meanAbsoluteDifference(a: FloatImage, b: FloatImage): Float {
        require(a.width == b.width && a.height == b.height)
        val aBytes = Quantize.toBytes(a.copy(), dither = false)
        val bBytes = Quantize.toBytes(b.copy(), dither = false)
        var total = 0.0
        for (i in aBytes.indices) {
            total += abs((aBytes[i].toInt() and 0xFF) - (bBytes[i].toInt() and 0xFF))
        }
        return (total / aBytes.size).toFloat()
    }

    /** Convenience: the natural factor for a source aiming at [targetLongEdge]. */
    fun naturalFactor(source: FloatImage, targetLongEdge: Int): Float =
        (targetLongEdge.toFloat() / source.longEdge).coerceIn(1f, Upscale.MAX_FACTOR)

    /** Downscale a source to simulate a low-resolution original for the A/B. */
    fun simulateLowResolution(source: FloatImage, factor: Float): FloatImage =
        Resample.areaDownsample(
            source,
            (source.width / factor).toInt().coerceAtLeast(1),
            (source.height / factor).toInt().coerceAtLeast(1),
        )
}
