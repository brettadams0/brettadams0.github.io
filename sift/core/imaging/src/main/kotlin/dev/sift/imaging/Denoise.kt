package dev.sift.imaging

import dev.sift.model.DerivedParams
import dev.sift.model.FrameAnalysis
import kotlin.math.max

/**
 * Denoise (§6.5).
 *
 * Three things about this stage are load-bearing:
 *
 * 1. **It usually does not run.** Measured noise below the visible threshold
 *    means denoising costs sharpness and returns nothing. The skip is the
 *    default, not the exception.
 * 2. **Chroma is treated about three times harder than luma.** Chroma noise
 *    reads as ugly coloured mottling; luma noise reads as film grain and is
 *    often desirable. Flattening both equally is what makes a denoised photo
 *    look like plastic.
 * 3. **It is never region-selective.** §6.5 is explicit: denoising only the flat
 *    regions leaves visible texture boundaries where the treatment stops. Noise
 *    is measured in flat regions (§6.3); it is *applied* everywhere.
 *
 * The filter is a self-guided filter rather than non-local means. It is
 * edge-preserving, runs in O(1) per pixel regardless of radius, and — the part
 * that matters for §2.1 — it works directly on float data. Round-tripping to
 * 8-bit to call a `fastNlMeansDenoisingColored`-style routine would defeat the
 * entire precision argument for a six-iteration LAB pipeline.
 */
object Denoise {

    /** §6.5 — below both of these, denoising costs sharpness and returns nothing. */
    const val LUMA_SKIP_THRESHOLD = 2.0f
    const val CHROMA_SKIP_THRESHOLD = 3.0f

    /** §6.5 — `h_luma = clamp(noiseSigmaLuma * 1.5, 1, 10)`. */
    const val H_FROM_SIGMA = 1.5f
    const val H_LUMA_MIN = 1f
    const val H_LUMA_MAX = 10f

    /** "Strength ratio roughly 3:1 chroma:luma." */
    const val CHROMA_LUMA_RATIO = 3f
    const val H_CHROMA_MAX = 30f

    /** Guide radius as a fraction of the long edge; a bound, not a per-frame value. */
    const val RADIUS_DIVISOR = 500
    const val MIN_RADIUS = 2

    /**
     * Denoise [image] in place if warranted. Takes and returns linear sRGB.
     */
    fun apply(image: FloatImage, analysis: FrameAnalysis): DerivedParams.DenoiseParams {
        image.requireSpace(ColorSpaceTag.LINEAR_SRGB, "Denoise.apply")

        if (analysis.noiseSigmaLuma < LUMA_SKIP_THRESHOLD &&
            analysis.noiseSigmaChroma < CHROMA_SKIP_THRESHOLD
        ) {
            return DerivedParams.DenoiseParams(
                ran = false,
                reasonSkipped = "measured noise below visible threshold " +
                    "(luma ${analysis.noiseSigmaLuma}, chroma ${analysis.noiseSigmaChroma})",
            )
        }

        val hLuma = (analysis.noiseSigmaLuma * H_FROM_SIGMA).coerceIn(H_LUMA_MIN, H_LUMA_MAX)
        val hChroma = (analysis.noiseSigmaChroma * H_FROM_SIGMA * CHROMA_LUMA_RATIO)
            .coerceIn(H_LUMA_MIN, H_CHROMA_MAX)

        val radius = max(MIN_RADIUS, image.longEdge / RADIUS_DIVISOR)

        ColorSpaces.linearToLab(image)
        val w = image.width
        val h = image.height

        // Luma: gentle. Grain is not the enemy.
        val l = image.channel(0)
        guidedFilter(l, w, h, radius, hLuma * hLuma)
        image.setChannel(0, l)

        // Chroma: firm. Mottling is.
        val a = image.channel(1)
        guidedFilter(a, w, h, radius, hChroma * hChroma)
        image.setChannel(1, a)

        val b = image.channel(2)
        guidedFilter(b, w, h, radius, hChroma * hChroma)
        image.setChannel(2, b)

        ColorSpaces.labToLinear(image)

        return DerivedParams.DenoiseParams(ran = true, hLuma = hLuma, hChroma = hChroma)
    }

    /**
     * Self-guided filter (He, Sun & Tang), in place.
     *
     * For each window, fits `q = a·I + b` by ridge regression with regularisation
     * [epsilon]. Where local variance is large compared to [epsilon] — an edge —
     * `a` approaches 1 and the pixel passes through untouched. Where variance is
     * small — flat noise — `a` approaches 0 and the pixel is replaced by the
     * local mean. That is the edge-preserving behaviour, and it comes out of one
     * parameter with a physical meaning: [epsilon] is the variance below which
     * signal is treated as noise.
     */
    fun guidedFilter(
        plane: FloatArray,
        width: Int,
        height: Int,
        radius: Int,
        epsilon: Float,
    ): FloatArray {
        val n = plane.size
        val squares = FloatArray(n) { plane[it] * plane[it] }

        val meanI = boxMean(plane.copyOf(), width, height, radius)
        val meanII = boxMean(squares, width, height, radius)

        val a = FloatArray(n)
        val b = FloatArray(n)
        for (i in 0 until n) {
            val variance = (meanII[i] - meanI[i] * meanI[i]).coerceAtLeast(0f)
            a[i] = variance / (variance + epsilon)
            b[i] = meanI[i] * (1f - a[i])
        }

        val meanA = boxMean(a, width, height, radius)
        val meanB = boxMean(b, width, height, radius)

        for (i in 0 until n) {
            plane[i] = meanA[i] * plane[i] + meanB[i]
        }
        return plane
    }

    /** Separable box mean via running sums, O(1) per pixel. */
    private fun boxMean(plane: FloatArray, width: Int, height: Int, radius: Int): FloatArray {
        val scratch = FloatArray(plane.size)
        val norm = 1f / (2 * radius + 1)

        for (y in 0 until height) {
            val row = y * width
            var acc = plane[row] * (radius + 1)
            for (x in 1..radius) acc += plane[row + x.coerceAtMost(width - 1)]
            for (x in 0 until width) {
                scratch[row + x] = acc * norm
                acc += plane[row + (x + radius + 1).coerceAtMost(width - 1)] -
                    plane[row + (x - radius).coerceAtLeast(0)]
            }
        }
        for (x in 0 until width) {
            var acc = scratch[x] * (radius + 1)
            for (y in 1..radius) acc += scratch[y.coerceAtMost(height - 1) * width + x]
            for (y in 0 until height) {
                plane[y * width + x] = acc * norm
                acc += scratch[(y + radius + 1).coerceAtMost(height - 1) * width + x] -
                    scratch[(y - radius).coerceAtLeast(0) * width + x]
            }
        }
        return plane
    }
}
