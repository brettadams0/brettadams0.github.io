package dev.sift.imaging

/**
 * A tone curve evaluated through a lookup table.
 *
 * §2.2: tone curves, levels and gamma are *perceptual* operations and belong in
 * **gamma-encoded** space. Running them in linear light crushes midtones and
 * feels wrong — it is the mirror image of trap #3.
 *
 * The domain deliberately extends above 1.0. Highlight reconstruction (§6.8.1)
 * pushes recovered channels past white on purpose, and the shoulder in §6.8.2 is
 * what brings them back; a LUT clamped at 1.0 would throw away the recovered
 * detail before the curve that exists to use it ever ran.
 */
class ToneCurve private constructor(
    private val lut: FloatArray,
    private val maxInput: Float,
) {
    private val scale = (lut.size - 1) / maxInput

    fun apply(v: Float): Float {
        // Sub-black samples pass through unchanged. They are legal in an
        // unbounded pipeline and clamping them here would quietly re-introduce
        // the mid-pipeline clipping §2.1 forbids.
        if (v <= 0f) return v
        if (v >= maxInput) return lut[lut.size - 1]
        val pos = v * scale
        val i = pos.toInt()
        val frac = pos - i
        val a = lut[i]
        val b = lut[(i + 1).coerceAtMost(lut.size - 1)]
        return a + (b - a) * frac
    }

    /** Apply to every channel of a gamma-encoded image, in place. */
    fun applyTo(image: FloatImage): FloatImage {
        image.requireSpace(ColorSpaceTag.GAMMA_SRGB, "ToneCurve.applyTo")
        val d = image.data
        for (i in d.indices) d[i] = apply(d[i])
        return image
    }

    companion object {
        const val DEFAULT_SIZE = 8192

        /** Headroom above white, so reconstructed highlights survive to the shoulder. */
        const val DEFAULT_MAX_INPUT = 4f

        fun build(
            maxInput: Float = DEFAULT_MAX_INPUT,
            size: Int = DEFAULT_SIZE,
            transfer: (Float) -> Float,
        ): ToneCurve {
            val lut = FloatArray(size)
            val step = maxInput / (size - 1)
            for (i in 0 until size) {
                lut[i] = transfer(i * step)
            }
            return ToneCurve(lut, maxInput)
        }

        /** Identity, for stages that end up with nothing to do. */
        fun identity(): ToneCurve = build { it }
    }
}
