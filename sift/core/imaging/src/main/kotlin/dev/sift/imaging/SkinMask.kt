package dev.sift.imaging

/**
 * The skin mask of §6.7.
 *
 * **This mask is for measurement only.** It is never used to apply a
 * correction. §6.7 is explicit and §7.2 of the spec's failure history backs it
 * up: the mask is imperfect, and applying a correction *through* it blotches
 * wherever it misclassifies. Measuring through it and applying globally means a
 * mask error nudges the estimate by a fraction of a unit rather than leaving a
 * visible edge across someone's cheek.
 *
 * The rule is stated in 8-bit display values, so it is evaluated on
 * gamma-encoded sRGB, not on linear light.
 */
object SkinMask {

    /**
     * §6.7:
     * ```
     * (R > G + 8) AND (G > B) AND
     * (Cr > 135) AND (Cr < 178) AND
     * (Cb > 85)  AND (Cb < 128)
     * ```
     */
    fun isSkin(r8: Float, g8: Float, b8: Float): Boolean {
        if (r8 <= g8 + 8f) return false
        if (g8 <= b8) return false
        val cr = 128f + 0.5f * r8 - 0.418688f * g8 - 0.081312f * b8
        if (cr <= 135f || cr >= 178f) return false
        val cb = 128f - 0.168736f * r8 - 0.331264f * g8 + 0.5f * b8
        return cb > 85f && cb < 128f
    }

    /**
     * Build the mask over a gamma-encoded sRGB image.
     *
     * Samples are scaled to 0..255 but not quantised — the thresholds are
     * comparisons, so there is nothing to gain from rounding and something to
     * lose in precision near the boundaries.
     */
    fun build(gammaImage: FloatImage): BooleanArray {
        gammaImage.requireSpace(ColorSpaceTag.GAMMA_SRGB, "SkinMask.build")
        val mask = BooleanArray(gammaImage.pixelCount)
        val d = gammaImage.data
        var i = 0
        for (p in mask.indices) {
            mask[p] = isSkin(d[i] * 255f, d[i + 1] * 255f, d[i + 2] * 255f)
            i += 3
        }
        return mask
    }

    fun fraction(mask: BooleanArray): Float {
        if (mask.isEmpty()) return 0f
        var count = 0
        for (m in mask) if (m) count++
        return count.toFloat() / mask.size
    }

    /**
     * Largest **contiguous** skin region as a fraction of the frame.
     *
     * This is the guard that keeps the router off brick, sand, wood and
     * terracotta (§6.4, trap #13). All of those clear a raw 2% skin threshold
     * comfortably; what they do not produce is one large connected blob the way
     * a face and neck do. Four-connected flood fill, iterative — a recursive
     * fill overflows the stack on a frame that is mostly wall.
     */
    fun largestRegionFraction(mask: BooleanArray, width: Int, height: Int): Float {
        require(mask.size == width * height)
        if (mask.isEmpty()) return 0f

        val visited = BooleanArray(mask.size)
        val queue = IntArray(mask.size)
        var largest = 0

        for (start in mask.indices) {
            if (!mask[start] || visited[start]) continue
            var head = 0
            var tail = 0
            queue[tail++] = start
            visited[start] = true
            var size = 0

            while (head < tail) {
                val p = queue[head++]
                size++
                val x = p % width
                val y = p / width

                if (x > 0) {
                    val n = p - 1
                    if (mask[n] && !visited[n]) { visited[n] = true; queue[tail++] = n }
                }
                if (x < width - 1) {
                    val n = p + 1
                    if (mask[n] && !visited[n]) { visited[n] = true; queue[tail++] = n }
                }
                if (y > 0) {
                    val n = p - width
                    if (mask[n] && !visited[n]) { visited[n] = true; queue[tail++] = n }
                }
                if (y < height - 1) {
                    val n = p + width
                    if (mask[n] && !visited[n]) { visited[n] = true; queue[tail++] = n }
                }
            }
            if (size > largest) largest = size
        }
        return largest.toFloat() / mask.size
    }

    /**
     * Median L*, a*, b* inside the mask, or null if the mask is too small to
     * produce a stable estimate.
     *
     * Median rather than mean: a handful of misclassified highlight or
     * background pixels move a mean and barely move a median, and the whole
     * point of §6.7 is that mask errors should nudge rather than steer.
     */
    fun medianLab(labImage: FloatImage, mask: BooleanArray): Triple<Float, Float, Float>? {
        labImage.requireSpace(ColorSpaceTag.LAB, "SkinMask.medianLab")
        require(mask.size == labImage.pixelCount)

        var count = 0
        for (m in mask) if (m) count++
        if (count < MIN_SAMPLES_FOR_MEDIAN) return null

        val l = FloatArray(count)
        val a = FloatArray(count)
        val b = FloatArray(count)
        var n = 0
        val d = labImage.data
        for (p in mask.indices) {
            if (!mask[p]) continue
            val i = p * 3
            l[n] = d[i]; a[n] = d[i + 1]; b[n] = d[i + 2]
            n++
        }
        l.sort(); a.sort(); b.sort()
        val mid = count / 2
        return Triple(l[mid], a[mid], b[mid])
    }

    /** Below this the median is noise, and a wrong anchor is worse than none. */
    const val MIN_SAMPLES_FOR_MEDIAN = 64
}
