package dev.sift.imaging

import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * The single quantisation point (§2.3, §6.11 steps 1–2).
 *
 * Float hits 8 bits **exactly once**, here, at the very end. Everything before
 * this stays unbounded float (§2.1); everything after is a file.
 *
 * ## Why dither is not optional
 *
 * Rounding a smooth gradient to 8 bits puts a hard step wherever the signal
 * crosses a quantisation boundary. In a sky, a studio backdrop or a blurred
 * background those steps line up into visible contour bands — the single most
 * recognisable "amateur digital" artifact, and one that survives everything else
 * being right.
 *
 * Adding a small amount of noise *before* rounding decorrelates the error from
 * the signal: the banding becomes a fine, essentially invisible grain, and the
 * average value over any small area stays correct. It costs nothing.
 *
 * The noise is triangular (TPDF), formed as the sum of two independent uniform
 * ±0.5 LSB variables. TPDF rather than uniform because it also removes the
 * *modulation* of the residual error by the signal, which is what makes uniform
 * dither still show faint structure in very smooth ramps.
 */
object Quantize {

    /** One least-significant bit in normalised 0..1 units. */
    const val LSB = 1f / 255f

    /**
     * Convert a linear-light working image to 8-bit gamma-encoded sRGB bytes.
     *
     * [seed] makes the dither deterministic, which is what lets §14.6 assert on
     * the result at all. Real exports pass a seed derived from the asset id, so
     * two runs over the same photo produce identical files.
     */
    fun toBytes(image: FloatImage, seed: Long = 0L, dither: Boolean = true): ByteArray {
        val working = when (image.space) {
            ColorSpaceTag.LINEAR_SRGB -> ColorSpaces.toGamma(image)
            ColorSpaceTag.GAMMA_SRGB -> image
            ColorSpaceTag.LAB -> ColorSpaces.toGamma(ColorSpaces.labToLinear(image))
        }

        val rng = Random(seed)
        val out = ByteArray(working.data.size)
        val d = working.data

        for (i in d.indices) {
            val dithered = if (dither) d[i] + tpdf(rng) else d[i]
            val level = (dithered * 255f).roundToInt().coerceIn(0, 255)
            out[i] = level.toByte()
        }
        return out
    }

    /** Packed 0xFFRRGGBB, for handing straight to an Android `Bitmap`. */
    fun toArgb(image: FloatImage, seed: Long = 0L, dither: Boolean = true): IntArray {
        val bytes = toBytes(image, seed, dither)
        val out = IntArray(image.pixelCount)
        var i = 0
        for (p in out.indices) {
            val r = bytes[i].toInt() and 0xFF
            val g = bytes[i + 1].toInt() and 0xFF
            val b = bytes[i + 2].toInt() and 0xFF
            out[p] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            i += 3
        }
        return out
    }

    /** Triangular PDF over ±1 LSB, peaking at zero. */
    private fun tpdf(rng: Random): Float =
        ((rng.nextFloat() - 0.5f) + (rng.nextFloat() - 0.5f)) * LSB
}
