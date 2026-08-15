package dev.sift.imaging

import dev.sift.model.QualityGate
import dev.sift.testing.SyntheticFrames
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * §14.6 — banding.
 *
 * "Synthetic smooth gradient through the full pipeline. Assert no visible steps.
 * **Run with dither disabled to confirm the test actually detects banding.**"
 *
 * That second sentence is the important half. A banding test that passes with
 * dither switched off is measuring nothing, and would keep passing if someone
 * later deleted the dither call.
 */
class BandingTest {

    private fun scoreFor(dither: Boolean): Float {
        val image = SyntheticFrames.toWorkingSpace(
            SyntheticFrames.smoothGradient(width = 512, height = 256, from = 0.45f, to = 0.55f),
        )
        val rgb = Quantize.toBytes(image, seed = 7, dither = dither)
        return QualityGates.bandingScore(rgb, 512, 256)
    }

    @Test
    @DisplayName("A smooth gradient quantised with dither shows no banding")
    fun ditheredGradientIsClean() {
        val score = scoreFor(dither = true)
        assertTrue(
            score <= QualityGate.BANDING_IDENTICAL_PAIR_FRACTION_MAX,
            "dithered gradient scored $score, above the " +
                "${QualityGate.BANDING_IDENTICAL_PAIR_FRACTION_MAX} tolerance",
        )
    }

    /**
     * The control. Without this the test above proves nothing.
     */
    @Test
    @DisplayName("The same gradient WITHOUT dither bands — the detector actually works")
    fun undtheredGradientBands() {
        val undithered = scoreFor(dither = false)
        val dithered = scoreFor(dither = true)

        assertTrue(
            undithered > QualityGate.BANDING_IDENTICAL_PAIR_FRACTION_MAX,
            "undithered gradient scored $undithered — the banding metric is not " +
                "detecting quantisation steps, so the passing case above is vacuous",
        )
        assertTrue(
            undithered > dithered + 0.2f,
            "dither made little difference (undithered $undithered vs dithered $dithered)",
        )
    }

    @Test
    @DisplayName("Dither is deterministic for a given seed")
    fun ditherIsReproducible() {
        val a = SyntheticFrames.toWorkingSpace(SyntheticFrames.smoothGradient())
        val b = SyntheticFrames.toWorkingSpace(SyntheticFrames.smoothGradient())
        val first = Quantize.toBytes(a, seed = 42)
        val second = Quantize.toBytes(b, seed = 42)
        assertTrue(first.contentEquals(second), "same seed produced different output")

        val third = Quantize.toBytes(
            SyntheticFrames.toWorkingSpace(SyntheticFrames.smoothGradient()),
            seed = 43,
        )
        assertTrue(!first.contentEquals(third), "different seeds produced identical output")
    }

    @Test
    @DisplayName("Dither is unbiased: it does not shift the mean level")
    fun ditherDoesNotShiftMean() {
        val plain = Quantize.toBytes(
            SyntheticFrames.toWorkingSpace(SyntheticFrames.smoothGradient()),
            seed = 1,
            dither = false,
        )
        val dithered = Quantize.toBytes(
            SyntheticFrames.toWorkingSpace(SyntheticFrames.smoothGradient()),
            seed = 1,
            dither = true,
        )

        var plainSum = 0L
        var ditheredSum = 0L
        for (i in plain.indices) {
            plainSum += (plain[i].toInt() and 0xFF)
            ditheredSum += (dithered[i].toInt() and 0xFF)
        }
        val plainMean = plainSum.toDouble() / plain.size
        val ditheredMean = ditheredSum.toDouble() / dithered.size
        assertTrue(
            kotlin.math.abs(plainMean - ditheredMean) < 0.15,
            "dither shifted the mean from $plainMean to $ditheredMean",
        )
    }
}
