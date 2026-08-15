package dev.sift.imaging

import dev.sift.model.FrameAnalysis
import dev.sift.model.GateResult
import dev.sift.model.GradeProfile
import dev.sift.model.GradeSettings
import dev.sift.model.QualityGate
import kotlin.math.abs

/**
 * The six quality gates of §6.12.
 *
 * **This is what "professional standard" actually requires** — not just good
 * processing, but automated refusal to ship a bad result. Every output is
 * verified against the frame it came from, and a failure that cannot be fixed by
 * retrying means the original ships unchanged. A degraded photo is worse than an
 * unprocessed one (§0 rule 3).
 *
 * Gates run on the **quantised 8-bit buffer**, before JPEG encoding. That is
 * where dither's effect is measurable and where the pipeline's own decisions are
 * visible without the codec's DCT noise on top. Verifying the *file* — that it
 * exists, decodes, and has the expected dimensions — is a separate check that
 * §9.3 invariant 3 deliberately repeats at approval time, because write-time
 * success is not read-time success.
 */
object QualityGates {

    /** Shallow-gradient window for the banding metric, in L*. */
    const val BANDING_MIN_RANGE_L = 0.3f
    const val BANDING_MAX_RANGE_L = 8f

    /**
     * What a gate failure means the pipeline should do next (§6.12's
     * "On failure" column).
     */
    enum class Remedy {
        /** Retry the portrait grade with damping 0.4. */
        RETRY_LOWER_DAMPING,

        /** Reduce contrast and exposure terms 50%, retry once. */
        RETRY_SOFTER_TONE,

        /** Reduce denoise strength 50%, retry once. */
        RETRY_LESS_DENOISE,

        /** No retry helps. Ship the original. */
        SHIP_ORIGINAL,
    }

    fun remedyFor(gate: QualityGate): Remedy = when (gate) {
        QualityGate.SKIN_RANGE -> Remedy.RETRY_LOWER_DAMPING
        QualityGate.NO_NEW_CLIPPING, QualityGate.NO_SHADOW_CRUSH -> Remedy.RETRY_SOFTER_TONE
        QualityGate.SHARPNESS_PRESERVED -> Remedy.RETRY_LESS_DENOISE
        QualityGate.NO_BANDING, QualityGate.CHROMA_SANITY -> Remedy.SHIP_ORIGINAL
    }

    /**
     * Evaluate every applicable gate.
     *
     * [sharpnessBefore] and [sharpnessAfter] are measured at the **same
     * resolution**, before the output resize. Comparing a 1080px export against
     * a 12MP source would fail the sharpness gate on every downscaled preset for
     * reasons that have nothing to do with quality.
     */
    fun evaluate(
        before: FrameAnalysis,
        after: FrameAnalysis,
        profile: GradeProfile,
        finalSkinB: Float?,
        sharpnessBefore: Float,
        sharpnessAfter: Float,
        bandingScore: Float,
    ): List<GateResult> {
        val results = mutableListOf<GateResult>()

        // 1. Skin range — portrait only. §6.7's guard rail: below about 10 the
        //    subject reads ill and grey.
        if (profile == GradeProfile.PORTRAIT && finalSkinB != null && !finalSkinB.isNaN()) {
            val passed = finalSkinB >= GradeSettings.SKIN_B_GUARD_MIN &&
                finalSkinB <= GradeSettings.SKIN_B_GUARD_MAX
            results += GateResult(
                gate = QualityGate.SKIN_RANGE,
                passed = passed,
                measured = finalSkinB,
                threshold = GradeSettings.SKIN_B_GUARD_MIN,
                note = if (passed) null else "skin b* $finalSkinB outside [15,22]",
            )
        }

        // 2. No new clipping.
        val clippingIncrease = after.clippedHighlightFraction - before.clippedHighlightFraction
        results += GateResult(
            gate = QualityGate.NO_NEW_CLIPPING,
            passed = clippingIncrease <= QualityGate.CLIPPING_INCREASE_MAX,
            measured = clippingIncrease,
            threshold = QualityGate.CLIPPING_INCREASE_MAX,
        )

        // 3. No shadow crush.
        val shadowIncrease = after.crushedShadowFraction - before.crushedShadowFraction
        results += GateResult(
            gate = QualityGate.NO_SHADOW_CRUSH,
            passed = shadowIncrease <= QualityGate.SHADOW_INCREASE_MAX,
            measured = shadowIncrease,
            threshold = QualityGate.SHADOW_INCREASE_MAX,
        )

        // 4. Sharpness preserved.
        val retention = if (sharpnessBefore <= 1e-5f) 1f else sharpnessAfter / sharpnessBefore
        results += GateResult(
            gate = QualityGate.SHARPNESS_PRESERVED,
            passed = retention >= QualityGate.SHARPNESS_RETENTION_MIN,
            measured = retention,
            threshold = QualityGate.SHARPNESS_RETENTION_MIN,
        )

        // 5. No banding introduced.
        results += GateResult(
            gate = QualityGate.NO_BANDING,
            passed = bandingScore <= QualityGate.BANDING_IDENTICAL_PAIR_FRACTION_MAX,
            measured = bandingScore,
            threshold = QualityGate.BANDING_IDENTICAL_PAIR_FRACTION_MAX,
            note = if (bandingScore > QualityGate.BANDING_IDENTICAL_PAIR_FRACTION_MAX) {
                "flat-region runs suggest undithered quantisation — check §2.3 dither is applied"
            } else {
                null
            },
        )

        // 6. Chroma sanity.
        val chromaChange = if (before.meanChroma <= 1e-4f) {
            0f
        } else {
            abs(after.meanChroma - before.meanChroma) / before.meanChroma
        }
        results += GateResult(
            gate = QualityGate.CHROMA_SANITY,
            passed = chromaChange <= QualityGate.CHROMA_CHANGE_MAX,
            measured = chromaChange,
            threshold = QualityGate.CHROMA_CHANGE_MAX,
        )

        return results
    }

    /**
     * The banding metric: fraction of horizontally adjacent, bit-identical pixel
     * pairs inside shallow-gradient regions.
     *
     * Operates on the quantised 8-bit output, because banding is a property of
     * quantisation and does not exist in the float buffer at all.
     */
    fun bandingScore(rgb: ByteArray, width: Int, height: Int): Float {
        if (width < 16 || height < 16) return 0f

        val blockSize = 16
        var identical = 0L
        var compared = 0L

        var by = 0
        while (by + blockSize <= height) {
            var bx = 0
            while (bx + blockSize <= width) {
                // Green stands in for luminance well enough to classify a block,
                // and avoids a full LAB conversion of the output.
                var min = 255
                var max = 0
                for (y in by until by + blockSize) {
                    for (x in bx until bx + blockSize) {
                        val g = rgb[(y * width + x) * 3 + 1].toInt() and 0xFF
                        if (g < min) min = g
                        if (g > max) max = g
                    }
                }
                // Convert the 8-bit range to an approximate L* range so the
                // window is expressed perceptually rather than in code values.
                val rangeL = abs(
                    ColorSpaces.lForGammaValue(max / 255f) - ColorSpaces.lForGammaValue(min / 255f),
                )
                if (rangeL in BANDING_MIN_RANGE_L..BANDING_MAX_RANGE_L) {
                    for (y in by until by + blockSize) {
                        for (x in bx until bx + blockSize - 1) {
                            val i = (y * width + x) * 3
                            val j = i + 3
                            compared++
                            if (rgb[i] == rgb[j] && rgb[i + 1] == rgb[j + 1] && rgb[i + 2] == rgb[j + 2]) {
                                identical++
                            }
                        }
                    }
                }
                bx += blockSize
            }
            by += blockSize
        }

        if (compared < 256) return 0f
        return identical.toFloat() / compared
    }
}
