package dev.sift.imaging

import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Separable spatial filters over single-channel planar float data.
 *
 * Everything here works on one channel at a time. That is not an
 * implementation detail — §2.2 and trap #4 require sharpening and local
 * contrast to touch **L only**, because running them on chroma fringes every
 * edge with colour. Keeping the primitives planar makes the correct thing the
 * easy thing.
 *
 * Edges replicate. Zero-padding would darken frame borders, which shows up as a
 * visible vignette after local contrast.
 */
object Convolve {

    /** Above this sigma the three-box approximation is used instead of a true kernel. */
    private const val BOX_APPROXIMATION_THRESHOLD = 8f

    /**
     * Gaussian blur of a planar channel.
     *
     * For the small radii of output sharpening (§6.10, ~0.9–5px) an exact
     * separable kernel is used. For the large radii of local contrast (§6.9,
     * 60–100px on a 12MP frame) an exact kernel would be a 600-tap convolution
     * per axis; three box passes are visually indistinguishable at that scale
     * and run in O(1) per pixel regardless of radius.
     */
    fun gaussianBlur(
        plane: FloatArray,
        width: Int,
        height: Int,
        sigma: Float,
        scratch: FloatArray = FloatArray(plane.size),
    ): FloatArray {
        require(plane.size == width * height) { "plane size mismatch" }
        if (sigma <= 0.01f) return plane
        return if (sigma >= BOX_APPROXIMATION_THRESHOLD) {
            boxApproximatedGaussian(plane, width, height, sigma, scratch)
        } else {
            exactGaussian(plane, width, height, sigma, scratch)
        }
    }

    private fun exactGaussian(
        plane: FloatArray,
        width: Int,
        height: Int,
        sigma: Float,
        scratch: FloatArray,
    ): FloatArray {
        val radius = ceil(3.0 * sigma).toInt().coerceAtLeast(1)
        val kernel = FloatArray(2 * radius + 1)
        val twoSigmaSq = 2.0 * sigma * sigma
        var sum = 0.0
        for (i in -radius..radius) {
            val w = exp(-(i * i) / twoSigmaSq)
            kernel[i + radius] = w.toFloat()
            sum += w
        }
        for (i in kernel.indices) kernel[i] = (kernel[i] / sum).toFloat()

        // Horizontal
        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                var acc = 0f
                for (k in -radius..radius) {
                    val sx = (x + k).coerceIn(0, width - 1)
                    acc += plane[row + sx] * kernel[k + radius]
                }
                scratch[row + x] = acc
            }
        }
        // Vertical
        for (y in 0 until height) {
            for (x in 0 until width) {
                var acc = 0f
                for (k in -radius..radius) {
                    val sy = (y + k).coerceIn(0, height - 1)
                    acc += scratch[sy * width + x] * kernel[k + radius]
                }
                plane[y * width + x] = acc
            }
        }
        return plane
    }

    /**
     * Three successive box blurs, sized by Kovesi's method so the composite
     * matches the requested Gaussian sigma.
     */
    private fun boxApproximatedGaussian(
        plane: FloatArray,
        width: Int,
        height: Int,
        sigma: Float,
        scratch: FloatArray,
    ): FloatArray {
        for (boxWidth in boxSizesForGaussian(sigma, 3)) {
            val radius = (boxWidth - 1) / 2
            if (radius < 1) continue
            boxBlurHorizontal(plane, scratch, width, height, radius)
            boxBlurVertical(scratch, plane, width, height, radius)
        }
        return plane
    }

    internal fun boxSizesForGaussian(sigma: Float, passes: Int): IntArray {
        val n = passes
        val wIdeal = sqrt((12.0 * sigma * sigma / n) + 1.0)
        var wl = wIdeal.toInt()
        if (wl % 2 == 0) wl--
        if (wl < 1) wl = 1
        val wu = wl + 2
        val mIdeal =
            (12.0 * sigma * sigma - (n * wl * wl).toDouble() - (4 * n * wl).toDouble() - (3 * n).toDouble()) /
                (-4.0 * wl - 4.0)
        val m = mIdeal.roundToInt()
        return IntArray(n) { if (it < m) wl else wu }
    }

    private fun boxBlurHorizontal(
        src: FloatArray,
        dst: FloatArray,
        width: Int,
        height: Int,
        radius: Int,
    ) {
        val norm = 1f / (2 * radius + 1)
        for (y in 0 until height) {
            val row = y * width
            // Prime the running sum with the clamped left edge.
            var acc = src[row] * (radius + 1)
            for (x in 1..radius) acc += src[row + x.coerceAtMost(width - 1)]
            for (x in 0 until width) {
                val add = src[row + (x + radius).coerceAtMost(width - 1)]
                val remove = src[row + (x - radius - 1).coerceAtLeast(0)]
                dst[row + x] = acc * norm
                acc += add - remove
            }
        }
    }

    private fun boxBlurVertical(
        src: FloatArray,
        dst: FloatArray,
        width: Int,
        height: Int,
        radius: Int,
    ) {
        val norm = 1f / (2 * radius + 1)
        for (x in 0 until width) {
            var acc = src[x] * (radius + 1)
            for (y in 1..radius) acc += src[y.coerceAtMost(height - 1) * width + x]
            for (y in 0 until height) {
                val add = src[(y + radius).coerceAtMost(height - 1) * width + x]
                val remove = src[(y - radius - 1).coerceAtLeast(0) * width + x]
                dst[y * width + x] = acc * norm
                acc += add - remove
            }
        }
    }

    /**
     * 3x3 Laplacian, `[[0,1,0],[1,-4,1],[0,1,0]]`.
     *
     * Its variance is the sharpness measure of §6.3, and the median absolute
     * deviation of its response over flat regions is the noise estimate.
     */
    fun laplacian(plane: FloatArray, width: Int, height: Int): FloatArray {
        val out = FloatArray(plane.size)
        for (y in 0 until height) {
            val up = (y - 1).coerceAtLeast(0) * width
            val mid = y * width
            val down = (y + 1).coerceAtMost(height - 1) * width
            for (x in 0 until width) {
                val left = (x - 1).coerceAtLeast(0)
                val right = (x + 1).coerceAtMost(width - 1)
                out[mid + x] = plane[up + x] + plane[down + x] +
                    plane[mid + left] + plane[mid + right] -
                    4f * plane[mid + x]
            }
        }
        return out
    }

    /** Sobel gradient magnitude, used for [dev.sift.model.FrameAnalysis.edgeDensity]. */
    fun sobelMagnitude(plane: FloatArray, width: Int, height: Int): FloatArray {
        val out = FloatArray(plane.size)
        for (y in 0 until height) {
            val up = (y - 1).coerceAtLeast(0) * width
            val mid = y * width
            val down = (y + 1).coerceAtMost(height - 1) * width
            for (x in 0 until width) {
                val l = (x - 1).coerceAtLeast(0)
                val r = (x + 1).coerceAtMost(width - 1)

                val gx = (plane[up + r] + 2f * plane[mid + r] + plane[down + r]) -
                    (plane[up + l] + 2f * plane[mid + l] + plane[down + l])
                val gy = (plane[down + l] + 2f * plane[down + x] + plane[down + r]) -
                    (plane[up + l] + 2f * plane[up + x] + plane[up + r])

                out[mid + x] = sqrt(gx * gx + gy * gy)
            }
        }
        return out
    }

    /**
     * Per-block standard deviation over a [blockSize] grid.
     *
     * Returns the block grid, not a per-pixel map. Used to find flat regions for
     * noise measurement (§6.3) — measuring noise globally conflates texture with
     * noise and denoises a photo of grass into plastic (trap #10).
     */
    fun blockStdDev(
        plane: FloatArray,
        width: Int,
        height: Int,
        blockSize: Int,
    ): BlockStats {
        val cols = (width + blockSize - 1) / blockSize
        val rows = (height + blockSize - 1) / blockSize
        val std = FloatArray(cols * rows)
        for (by in 0 until rows) {
            for (bx in 0 until cols) {
                val x0 = bx * blockSize
                val y0 = by * blockSize
                val x1 = (x0 + blockSize).coerceAtMost(width)
                val y1 = (y0 + blockSize).coerceAtMost(height)
                var sum = 0.0
                var sumSq = 0.0
                var n = 0
                for (y in y0 until y1) {
                    val row = y * width
                    for (x in x0 until x1) {
                        val v = plane[row + x].toDouble()
                        sum += v
                        sumSq += v * v
                        n++
                    }
                }
                val mean = sum / n
                val variance = (sumSq / n - mean * mean).coerceAtLeast(0.0)
                std[by * cols + bx] = sqrt(variance).toFloat()
            }
        }
        return BlockStats(cols, rows, blockSize, std)
    }

    data class BlockStats(
        val cols: Int,
        val rows: Int,
        val blockSize: Int,
        val stdDev: FloatArray,
    ) {
        override fun equals(other: Any?): Boolean =
            other is BlockStats && cols == other.cols && rows == other.rows &&
                blockSize == other.blockSize && stdDev.contentEquals(other.stdDev)

        override fun hashCode(): Int =
            ((cols * 31 + rows) * 31 + blockSize) * 31 + stdDev.contentHashCode()
    }
}
