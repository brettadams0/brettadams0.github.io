package dev.sift.imaging

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Measurement helpers for the analysis pass (§6.3).
 *
 * Percentiles run off a histogram rather than a sort: sorting 12 million floats
 * to find the 0.1 percentile costs more than the entire analysis budget of
 * §13, and a 4096-bin histogram over `L* ∈ [0,100]` resolves to 0.024 L* —
 * far finer than any decision made from it.
 */
object Statistics {

    const val HISTOGRAM_BINS = 4096

    /**
     * A histogram over a known range, with percentile lookup.
     *
     * Samples outside `[min, max]` are counted into the end bins rather than
     * dropped. In an unbounded pipeline (§2.1) over-range highlights are real
     * data and discarding them would bias the white point downward.
     */
    class Histogram(
        val min: Float,
        val max: Float,
        val bins: IntArray,
    ) {
        val total: Long = bins.sumOf { it.toLong() }
        private val binWidth = (max - min) / bins.size

        /** Value at [fraction] of the distribution, 0..1, linearly interpolated within the bin. */
        fun percentile(fraction: Float): Float {
            if (total == 0L) return min
            val target = (fraction.coerceIn(0f, 1f) * total).toLong().coerceAtLeast(1L)
            var cumulative = 0L
            for (i in bins.indices) {
                val next = cumulative + bins[i]
                if (next >= target) {
                    val within = if (bins[i] == 0) {
                        0f
                    } else {
                        (target - cumulative).toFloat() / bins[i]
                    }
                    return min + (i + within) * binWidth
                }
                cumulative = next
            }
            return max
        }

        fun median(): Float = percentile(0.5f)

        /** Fraction of samples strictly below [value]. */
        fun fractionBelow(value: Float): Float {
            if (total == 0L) return 0f
            val edge = ((value - min) / binWidth).toInt().coerceIn(0, bins.size)
            var count = 0L
            for (i in 0 until edge) count += bins[i]
            return count.toFloat() / total
        }

        /** Fraction of samples at or above [value]. */
        fun fractionAtOrAbove(value: Float): Float = 1f - fractionBelow(value)

        /**
         * Shannon entropy of the distribution, normalised to [0,1].
         *
         * §6.8 uses this to damp midtone correction: a deliberately high-key or
         * low-key frame concentrates its mass into few bins, scores low, and is
         * moved less — which is what keeps Scene from flattening an intentional
         * look into a snapshot.
         */
        fun normalisedEntropy(): Float {
            if (total == 0L) return 0f
            var entropy = 0.0
            var occupied = 0
            for (count in bins) {
                if (count == 0) continue
                occupied++
                val p = count.toDouble() / total
                entropy -= p * ln(p)
            }
            if (occupied <= 1) return 0f
            return (entropy / ln(bins.size.toDouble())).toFloat().coerceIn(0f, 1f)
        }

        /**
         * Otsu between-class variance as a fraction of total variance.
         *
         * Near 1 means the distribution is cleanly bimodal — ink on paper, which
         * is one of the three document signals in §6.3.
         */
        fun bimodality(): Float {
            if (total == 0L) return 0f
            val binWidthLocal = binWidth
            var sumAll = 0.0
            for (i in bins.indices) sumAll += (min + i * binWidthLocal) * bins[i].toDouble()
            val meanAll = sumAll / total

            var varianceAll = 0.0
            for (i in bins.indices) {
                if (bins[i] == 0) continue
                val v = min + i * binWidthLocal - meanAll
                varianceAll += v * v * bins[i]
            }
            varianceAll /= total
            if (varianceAll < 1e-9) return 0f

            var bestBetween = 0.0
            var weightBackground = 0L
            var sumBackground = 0.0
            for (i in bins.indices) {
                weightBackground += bins[i]
                if (weightBackground == 0L) continue
                val weightForeground = total - weightBackground
                if (weightForeground == 0L) break
                sumBackground += (min + i * binWidthLocal) * bins[i].toDouble()
                val meanBackground = sumBackground / weightBackground
                val meanForeground = (sumAll - sumBackground) / weightForeground
                val diff = meanBackground - meanForeground
                val between =
                    weightBackground.toDouble() * weightForeground.toDouble() * diff * diff /
                        (total.toDouble() * total.toDouble())
                if (between > bestBetween) bestBetween = between
            }
            return (bestBetween / varianceAll).toFloat().coerceIn(0f, 1f)
        }
    }

    /** Histogram of a planar channel over an explicit range. */
    fun histogram(
        plane: FloatArray,
        min: Float,
        max: Float,
        bins: Int = HISTOGRAM_BINS,
    ): Histogram {
        val counts = IntArray(bins)
        val scale = bins / (max - min)
        for (v in plane) {
            val bin = ((v - min) * scale).toInt().coerceIn(0, bins - 1)
            counts[bin]++
        }
        return Histogram(min, max, counts)
    }

    /** Histogram of a planar channel restricted to a boolean mask. */
    fun maskedHistogram(
        plane: FloatArray,
        mask: BooleanArray,
        min: Float,
        max: Float,
        bins: Int = HISTOGRAM_BINS,
    ): Histogram {
        require(plane.size == mask.size)
        val counts = IntArray(bins)
        val scale = bins / (max - min)
        for (i in plane.indices) {
            if (!mask[i]) continue
            val bin = ((plane[i] - min) * scale).toInt().coerceIn(0, bins - 1)
            counts[bin]++
        }
        return Histogram(min, max, counts)
    }

    /** Percentile of an arbitrary small array. Sorts a copy; for block grids only. */
    fun percentileOf(values: FloatArray, fraction: Float): Float {
        if (values.isEmpty()) return 0f
        val sorted = values.copyOf()
        sorted.sort()
        val idx = ((sorted.size - 1) * fraction.coerceIn(0f, 1f)).toInt()
        return sorted[idx]
    }

    fun mean(values: FloatArray): Float {
        if (values.isEmpty()) return 0f
        var sum = 0.0
        for (v in values) sum += v
        return (sum / values.size).toFloat()
    }

    fun variance(values: FloatArray): Float {
        if (values.size < 2) return 0f
        var sum = 0.0
        var sumSq = 0.0
        for (v in values) {
            sum += v
            sumSq += v.toDouble() * v
        }
        val m = sum / values.size
        return ((sumSq / values.size) - m * m).coerceAtLeast(0.0).toFloat()
    }

    /**
     * Variance of the Laplacian response — the global sharpness figure of §6.3.
     */
    fun laplacianVariance(plane: FloatArray, width: Int, height: Int): Float =
        variance(Convolve.laplacian(plane, width, height))

    /**
     * Laplacian variance estimated from a row subsample.
     *
     * The full-frame figure requires materialising a Laplacian over every pixel.
     * Where the number is only used to *choose a strength* — as in §6.10's
     * output sharpening — sampling every [VARIANCE_ROW_STRIDE]th row gives the
     * same answer to well within the precision the decision needs.
     */
    fun sampledLaplacianVariance(
        plane: FloatArray,
        width: Int,
        height: Int,
        stride: Int = VARIANCE_ROW_STRIDE,
    ): Float {
        if (height < stride * 4) return laplacianVariance(plane, width, height)
        val rows = height / stride
        val sampled = FloatArray(width * rows)
        for (r in 0 until rows) {
            System.arraycopy(plane, r * stride * width, sampled, r * width, width)
        }
        return variance(Convolve.laplacian(sampled, width, rows))
    }

    const val VARIANCE_ROW_STRIDE = 4

    /**
     * Sharpness of the *sharpest* regions: P90 of per-tile Laplacian variance.
     *
     * Trap #11 — a shallow-depth-of-field portrait has a sharp subject against a
     * blurred background, so its mean sharpness is low while it is perfectly
     * sharp where it matters. Gating the upscale on the mean wrongly rejects
     * exactly the frames most worth upscaling.
     */
    fun laplacianVarianceP90(
        plane: FloatArray,
        width: Int,
        height: Int,
        tiles: Int = SHARPNESS_TILE_GRID,
    ): Float = laplacianVarianceP90From(Convolve.laplacian(plane, width, height), width, height, tiles)

    /**
     * As [laplacianVarianceP90] but over a Laplacian response computed once by
     * the caller. The analysis pass needs the same response three times and a
     * full-resolution Laplacian is a 48MB allocation each time.
     */
    fun laplacianVarianceP90From(
        laplace: FloatArray,
        width: Int,
        height: Int,
        tiles: Int = SHARPNESS_TILE_GRID,
    ): Float {
        val tileW = (width + tiles - 1) / tiles
        val tileH = (height + tiles - 1) / tiles
        val variances = ArrayList<Float>(tiles * tiles)

        for (ty in 0 until tiles) {
            for (tx in 0 until tiles) {
                val x0 = tx * tileW
                val y0 = ty * tileH
                val x1 = (x0 + tileW).coerceAtMost(width)
                val y1 = (y0 + tileH).coerceAtMost(height)
                if (x0 >= x1 || y0 >= y1) continue
                var sum = 0.0
                var sumSq = 0.0
                var n = 0
                for (y in y0 until y1) {
                    val row = y * width
                    for (x in x0 until x1) {
                        val v = laplace[row + x].toDouble()
                        sum += v
                        sumSq += v * v
                        n++
                    }
                }
                if (n < 2) continue
                val m = sum / n
                variances.add(((sumSq / n) - m * m).coerceAtLeast(0.0).toFloat())
            }
        }
        if (variances.isEmpty()) return 0f
        return percentileOf(variances.toFloatArray(), 0.90f)
    }

    /**
     * Noise sigma, in the units of [plane], measured **only in flat regions**.
     *
     * Trap #10: measuring noise globally conflates texture with noise, so a
     * photo of grass reads as extremely noisy and gets denoised into plastic.
     * Flat blocks are those whose local standard deviation falls in the bottom
     * quartile — the frame's own quietest areas, whatever the frame is.
     *
     * The estimator is the median absolute deviation of the Laplacian response,
     * scaled by 1.4826 (MAD → sigma for a normal distribution) and by
     * `1/sqrt(20)`, the gain the 3x3 Laplacian applies to uncorrelated noise.
     */
    fun noiseSigmaInFlatRegions(
        plane: FloatArray,
        width: Int,
        height: Int,
        blockSize: Int = NOISE_BLOCK_SIZE,
    ): NoiseEstimate {
        if (width < blockSize * 2 || height < blockSize * 2) {
            return NoiseEstimate(0f, 0f, IntArray(0), blockSize, 0)
        }
        val flat = findFlatBlocks(plane, width, height, blockSize)
        val laplace = Convolve.laplacian(plane, width, height)
        val sigma = sigmaOverBlocks(laplace, width, height, flat)
        return flat.copy(sigma = sigma)
    }

    /**
     * Locate the frame's quietest blocks — the bottom quartile of local standard
     * deviation. Shared by the luma and chroma noise measurements so both look
     * at the same places (§6.3, trap #10).
     */
    fun findFlatBlocks(
        plane: FloatArray,
        width: Int,
        height: Int,
        blockSize: Int = NOISE_BLOCK_SIZE,
    ): NoiseEstimate {
        val blocks = Convolve.blockStdDev(plane, width, height, blockSize)
        val flatThreshold = percentileOf(blocks.stdDev, FLAT_REGION_PERCENTILE)

        val indices = ArrayList<Int>()
        for (i in blocks.stdDev.indices) {
            if (blocks.stdDev[i] <= flatThreshold) indices.add(i)
        }

        // "Flat" as a share of the frame is reported against an absolute
        // smoothness bound, not against the quartile used to pick the sample —
        // the quartile would make this trivially 0.25 for every image.
        var absolutelyFlat = 0
        for (s in blocks.stdDev) if (s < FLAT_REGION_ABSOLUTE_STD) absolutelyFlat++

        return NoiseEstimate(
            sigma = 0f,
            flatRegionFraction = absolutelyFlat.toFloat() / blocks.stdDev.size,
            flatBlockIndices = indices.toIntArray(),
            blockSize = blockSize,
            blockCols = blocks.cols,
        )
    }

    /** MAD-based sigma of a Laplacian response, restricted to the given blocks. */
    fun sigmaOverBlocks(
        laplace: FloatArray,
        width: Int,
        height: Int,
        flat: NoiseEstimate,
    ): Float {
        if (flat.flatBlockIndices.isEmpty()) return 0f
        val responses = ArrayList<Float>()
        for (blockIndex in flat.flatBlockIndices) {
            val bx = blockIndex % flat.blockCols
            val by = blockIndex / flat.blockCols
            val x0 = bx * flat.blockSize
            val y0 = by * flat.blockSize
            val x1 = (x0 + flat.blockSize).coerceAtMost(width)
            val y1 = (y0 + flat.blockSize).coerceAtMost(height)
            // Skip the block border: the Laplacian there sees the neighbouring,
            // possibly textured, block.
            for (y in (y0 + 1) until (y1 - 1)) {
                val row = y * width
                for (x in (x0 + 1) until (x1 - 1)) {
                    responses.add(abs(laplace[row + x]))
                }
            }
        }
        if (responses.size < 16) return 0f
        val values = responses.toFloatArray()
        values.sort()
        val mad = values[values.size / 2]
        return (mad * MAD_TO_SIGMA / sqrt(LAPLACIAN_NOISE_GAIN)).toFloat()
    }

    data class NoiseEstimate(
        val sigma: Float,
        val flatRegionFraction: Float,
        val flatBlockIndices: IntArray = IntArray(0),
        val blockSize: Int = NOISE_BLOCK_SIZE,
        val blockCols: Int = 0,
    ) {
        override fun equals(other: Any?): Boolean =
            other is NoiseEstimate && sigma == other.sigma &&
                flatRegionFraction == other.flatRegionFraction &&
                flatBlockIndices.contentEquals(other.flatBlockIndices) &&
                blockSize == other.blockSize && blockCols == other.blockCols

        override fun hashCode(): Int {
            var result = sigma.hashCode()
            result = 31 * result + flatRegionFraction.hashCode()
            result = 31 * result + flatBlockIndices.contentHashCode()
            result = 31 * result + blockSize
            result = 31 * result + blockCols
            return result
        }
    }

    /** Median absolute deviation of an arbitrary array. */
    fun medianAbsoluteDeviation(values: FloatArray): Float {
        if (values.isEmpty()) return 0f
        val sorted = values.copyOf()
        sorted.sort()
        val median = sorted[sorted.size / 2]
        val deviations = FloatArray(values.size) { abs(values[it] - median) }
        deviations.sort()
        return deviations[deviations.size / 2]
    }

    /** Tiles per axis for the P90 sharpness measure. */
    const val SHARPNESS_TILE_GRID = 16

    /** Block size for local-variance and noise measurement. */
    const val NOISE_BLOCK_SIZE = 8

    /** Flat regions are the quietest quarter of the frame (§6.3). */
    const val FLAT_REGION_PERCENTILE = 0.25f

    /**
     * A block is "flat" in absolute terms below this standard deviation, in L*
     * units. A bound, not a tuning knob: 1.5 L* is around the visibility
     * threshold for structure in a smooth gradient.
     */
    const val FLAT_REGION_ABSOLUTE_STD = 1.5f

    private const val MAD_TO_SIGMA = 1.4826
    private const val LAPLACIAN_NOISE_GAIN = 20.0
}
