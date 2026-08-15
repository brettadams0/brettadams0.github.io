package dev.sift.imaging

import kotlin.math.abs
import kotlin.math.cbrt
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sqrt

/**
 * Colour management for the whole pipeline (§6.2).
 *
 * ## The porting trap, resolved by construction
 *
 * §6.2 and trap #1 warn that OpenCV ships *two different* LAB scalings — 8-bit
 * LAB puts `L ∈ [0,255]` and `a,b ∈ [0,255]` offset by 128, while float LAB uses
 * true CIELAB. Porting `-128` arithmetic from an 8-bit reference implementation
 * into a float pipeline silently produces garbage that still looks like a
 * plausible image, and only a golden test catches it.
 *
 * Sift has no 8-bit LAB path at all. This object is the only implementation of
 * the conversion in the codebase, it produces true CIELAB and nothing else, and
 * `LabGoldenTest` pins it against published reference values. The trap is not
 * mitigated here — it is removed.
 *
 * ## Ranges
 *
 * - `L* ∈ [0,100]` (may exceed on over-range highlights — deliberate, §2.1)
 * - `a*, b*` centred on zero, roughly ±127
 *
 * Every transfer function extends oddly through zero so that negative samples —
 * legal in an unbounded pipeline and routine after a gamut conversion — survive
 * a round trip instead of turning into NaN.
 */
object ColorSpaces {

    // ---- sRGB transfer function (IEC 61966-2-1) ---------------------------

    private const val SRGB_LINEAR_CUTOFF_ENCODED = 0.04045
    private const val SRGB_LINEAR_CUTOFF_LINEAR = 0.0031308
    private const val SRGB_SLOPE = 12.92
    private const val SRGB_ALPHA = 0.055
    private const val SRGB_GAMMA = 2.4

    /** Gamma-encoded sRGB sample to linear light. Odd-symmetric for negatives. */
    fun srgbToLinear(v: Float): Float {
        val a = abs(v.toDouble())
        val lin = if (a <= SRGB_LINEAR_CUTOFF_ENCODED) {
            a / SRGB_SLOPE
        } else {
            ((a + SRGB_ALPHA) / (1.0 + SRGB_ALPHA)).pow(SRGB_GAMMA)
        }
        return (lin * v.sign).toFloat()
    }

    /** Linear light to gamma-encoded sRGB. Odd-symmetric for negatives. */
    fun linearToSrgb(v: Float): Float {
        val a = abs(v.toDouble())
        val enc = if (a <= SRGB_LINEAR_CUTOFF_LINEAR) {
            a * SRGB_SLOPE
        } else {
            (1.0 + SRGB_ALPHA) * a.pow(1.0 / SRGB_GAMMA) - SRGB_ALPHA
        }
        return (enc * v.sign).toFloat()
    }

    // ---- Whole-image transfer ---------------------------------------------

    /** In-place gamma-encoded sRGB → linear sRGB. */
    fun toLinear(image: FloatImage): FloatImage {
        image.requireSpace(ColorSpaceTag.GAMMA_SRGB, "toLinear")
        val d = image.data
        for (i in d.indices) d[i] = srgbToLinear(d[i])
        ConversionLog.record(ColorSpaceTag.GAMMA_SRGB, ColorSpaceTag.LINEAR_SRGB, "toLinear")
        image.space = ColorSpaceTag.LINEAR_SRGB
        return image
    }

    /** In-place linear sRGB → gamma-encoded sRGB. */
    fun toGamma(image: FloatImage): FloatImage {
        image.requireSpace(ColorSpaceTag.LINEAR_SRGB, "toGamma")
        val d = image.data
        for (i in d.indices) d[i] = linearToSrgb(d[i])
        ConversionLog.record(ColorSpaceTag.LINEAR_SRGB, ColorSpaceTag.GAMMA_SRGB, "toGamma")
        image.space = ColorSpaceTag.GAMMA_SRGB
        return image
    }

    // ---- Linear sRGB <-> XYZ (D65) ----------------------------------------

    // sRGB primaries under D65, the standard Bradford-adapted matrix.
    private const val M00 = 0.4124564; private const val M01 = 0.3575761; private const val M02 = 0.1804375
    private const val M10 = 0.2126729; private const val M11 = 0.7151522; private const val M12 = 0.0721750
    private const val M20 = 0.0193339; private const val M21 = 0.1191920; private const val M22 = 0.9503041

    private const val I00 = 3.2404542; private const val I01 = -1.5371385; private const val I02 = -0.4985314
    private const val I10 = -0.9692660; private const val I11 = 1.8760108; private const val I12 = 0.0415560
    private const val I20 = 0.0556434; private const val I21 = -0.2040259; private const val I22 = 1.0572252

    /** D65 reference white, normalised so Yn = 1. */
    private const val XN = 0.95047
    private const val YN = 1.00000
    private const val ZN = 1.08883

    // CIELAB companding constants: delta = 6/29.
    private const val DELTA = 6.0 / 29.0
    private const val DELTA_CUBED = DELTA * DELTA * DELTA
    private const val DELTA_SQ_TIMES_3 = 3.0 * DELTA * DELTA
    private const val FOUR_TWENTY_NINTHS = 4.0 / 29.0

    private fun labF(t: Double): Double =
        if (t > DELTA_CUBED) cbrt(t) else t / DELTA_SQ_TIMES_3 + FOUR_TWENTY_NINTHS

    private fun labFInv(t: Double): Double =
        if (t > DELTA) t * t * t else DELTA_SQ_TIMES_3 * (t - FOUR_TWENTY_NINTHS)

    /**
     * In-place linear sRGB → true CIELAB (D65).
     *
     * Note there is no `+128` anywhere in this function and no 8-bit rescale.
     * The targets quoted in §6.7 (`L* 68.0, a* 12.5, b* 17.0`) map directly onto
     * this output.
     */
    fun linearToLab(image: FloatImage): FloatImage {
        image.requireSpace(ColorSpaceTag.LINEAR_SRGB, "linearToLab")
        val d = image.data
        var i = 0
        while (i < d.size) {
            val r = d[i].toDouble()
            val g = d[i + 1].toDouble()
            val b = d[i + 2].toDouble()

            val x = (M00 * r + M01 * g + M02 * b) / XN
            val y = (M10 * r + M11 * g + M12 * b) / YN
            val z = (M20 * r + M21 * g + M22 * b) / ZN

            val fx = labF(x)
            val fy = labF(y)
            val fz = labF(z)

            d[i] = (116.0 * fy - 16.0).toFloat()
            d[i + 1] = (500.0 * (fx - fy)).toFloat()
            d[i + 2] = (200.0 * (fy - fz)).toFloat()
            i += 3
        }
        ConversionLog.record(ColorSpaceTag.LINEAR_SRGB, ColorSpaceTag.LAB, "linearToLab")
        image.space = ColorSpaceTag.LAB
        return image
    }

    /** In-place true CIELAB (D65) → linear sRGB. Exact inverse of [linearToLab]. */
    fun labToLinear(image: FloatImage): FloatImage {
        image.requireSpace(ColorSpaceTag.LAB, "labToLinear")
        val d = image.data
        var i = 0
        while (i < d.size) {
            val l = d[i].toDouble()
            val a = d[i + 1].toDouble()
            val bb = d[i + 2].toDouble()

            val fy = (l + 16.0) / 116.0
            val fx = fy + a / 500.0
            val fz = fy - bb / 200.0

            val x = labFInv(fx) * XN
            val y = labFInv(fy) * YN
            val z = labFInv(fz) * ZN

            d[i] = (I00 * x + I01 * y + I02 * z).toFloat()
            d[i + 1] = (I10 * x + I11 * y + I12 * z).toFloat()
            d[i + 2] = (I20 * x + I21 * y + I22 * z).toFloat()
            i += 3
        }
        ConversionLog.record(ColorSpaceTag.LAB, ColorSpaceTag.LINEAR_SRGB, "labToLinear")
        image.space = ColorSpaceTag.LINEAR_SRGB
        return image
    }

    // ---- Single-pixel helpers (used by gamut mapping and tests) -----------

    /** Linear sRGB triple → true CIELAB triple. */
    fun linearRgbToLab(r: Float, g: Float, b: Float): FloatArray {
        val x = (M00 * r + M01 * g + M02 * b) / XN
        val y = (M10 * r + M11 * g + M12 * b) / YN
        val z = (M20 * r + M21 * g + M22 * b) / ZN
        val fx = labF(x); val fy = labF(y); val fz = labF(z)
        return floatArrayOf(
            (116.0 * fy - 16.0).toFloat(),
            (500.0 * (fx - fy)).toFloat(),
            (200.0 * (fy - fz)).toFloat(),
        )
    }

    /** True CIELAB triple → linear sRGB triple. */
    fun labToLinearRgb(l: Float, a: Float, b: Float): FloatArray {
        val fy = (l + 16.0) / 116.0
        val fx = fy + a / 500.0
        val fz = fy - b / 200.0
        val x = labFInv(fx) * XN
        val y = labFInv(fy) * YN
        val z = labFInv(fz) * ZN
        return floatArrayOf(
            (I00 * x + I01 * y + I02 * z).toFloat(),
            (I10 * x + I11 * y + I12 * z).toFloat(),
            (I20 * x + I21 * y + I22 * z).toFloat(),
        )
    }

    /** Gamma-encoded 8-bit sRGB → true CIELAB. The reference path for tests. */
    fun srgb8ToLab(r: Int, g: Int, b: Int): FloatArray = linearRgbToLab(
        srgbToLinear(r / 255f),
        srgbToLinear(g / 255f),
        srgbToLinear(b / 255f),
    )

    fun chroma(a: Float, b: Float): Float = sqrt(a * a + b * b)

    /** Linear luminance Y (Yn = 1) for a given CIELAB L*. */
    fun luminanceForL(l: Float): Float = labFInv((l + 16.0) / 116.0).toFloat()

    /** CIELAB L* for a given linear luminance Y. */
    fun lForLuminance(y: Float): Float = (116.0 * labF(y.toDouble()) - 16.0).toFloat()

    /**
     * The gamma-encoded sRGB value of a neutral at lightness [l].
     *
     * Scene's tone stages are expressed as L* landmarks (§6.8: black at the 0.1
     * percentile, midtone toward L* 50, black point never above 3) but operate
     * on a gamma-encoded curve (§2.2). This is the bridge between the two.
     */
    fun gammaValueForL(l: Float): Float = linearToSrgb(luminanceForL(l))

    /** Inverse of [gammaValueForL]. */
    fun lForGammaValue(v: Float): Float = lForLuminance(srgbToLinear(v))

    // ---- Display P3 → sRGB, perceptual soft-clip (§6.2) -------------------

    // Display P3 primaries → XYZ (D65). P3 shares the sRGB transfer curve.
    private const val P00 = 0.4865709; private const val P01 = 0.2656677; private const val P02 = 0.1982173
    private const val P10 = 0.2289746; private const val P11 = 0.6917385; private const val P12 = 0.0792869
    private const val P20 = 0.0000000; private const val P21 = 0.0451134; private const val P22 = 1.0439444

    /**
     * Fraction of the in-gamut chroma at which the shoulder starts. §6.2:
     * "compresses the out-of-gamut region smoothly over the top 10% of chroma".
     */
    private const val GAMUT_KNEE = 0.90f
    private const val GAMUT_SEARCH_ITERATIONS = 12

    /**
     * In-place linear Display P3 → linear sRGB with a perceptual soft-clip.
     *
     * A hard clamp is what makes saturated reds and greens collapse into
     * featureless blocks — every P3 red past the sRGB boundary lands on exactly
     * (1,0,0) and the gradient inside a flower or a brake light disappears.
     * Instead each out-of-gamut pixel keeps its lightness and hue, and only its
     * chroma is compressed, smoothly, into the top 10% of what sRGB can hold.
     *
     * The buffer is expected to be tagged [ColorSpaceTag.LINEAR_SRGB] already
     * (the decoder hands over linear samples); this reinterprets those samples
     * as P3 primaries and rewrites them as sRGB primaries.
     */
    fun displayP3ToSrgb(image: FloatImage): FloatImage {
        image.requireSpace(ColorSpaceTag.LINEAR_SRGB, "displayP3ToSrgb")
        val d = image.data
        var i = 0
        while (i < d.size) {
            val r = d[i].toDouble()
            val g = d[i + 1].toDouble()
            val b = d[i + 2].toDouble()

            // P3 → XYZ → sRGB
            val x = P00 * r + P01 * g + P02 * b
            val y = P10 * r + P11 * g + P12 * b
            val z = P20 * r + P21 * g + P22 * b

            var sr = (I00 * x + I01 * y + I02 * z).toFloat()
            var sg = (I10 * x + I11 * y + I12 * z).toFloat()
            var sb = (I20 * x + I21 * y + I22 * z).toFloat()

            if (outOfGamut(sr, sg, sb)) {
                val lab = linearRgbToLab(sr, sg, sb)
                val compressed = softClipChroma(lab[0], lab[1], lab[2])
                sr = compressed[0]; sg = compressed[1]; sb = compressed[2]
            }

            d[i] = sr; d[i + 1] = sg; d[i + 2] = sb
            i += 3
        }
        ConversionLog.record(
            ColorSpaceTag.LINEAR_SRGB,
            ColorSpaceTag.LINEAR_SRGB,
            "displayP3ToSrgb (soft-clip)",
        )
        return image
    }

    private const val GAMUT_EPSILON = 1e-4f

    private fun outOfGamut(r: Float, g: Float, b: Float): Boolean =
        r < -GAMUT_EPSILON || g < -GAMUT_EPSILON || b < -GAMUT_EPSILON ||
            r > 1f + GAMUT_EPSILON || g > 1f + GAMUT_EPSILON || b > 1f + GAMUT_EPSILON

    /**
     * Compress chroma at fixed L* and hue until the colour sits inside sRGB,
     * with a smooth shoulder over the top [GAMUT_KNEE] of the available chroma
     * rather than a hard landing on the boundary.
     */
    private fun softClipChroma(l: Float, a: Float, b: Float): FloatArray {
        val c = chroma(a, b)
        if (c < 1e-6f) {
            // Achromatic and still out of gamut: only lightness can be at fault.
            val clampedL = l.coerceIn(0f, 100f)
            return labToLinearRgb(clampedL, a, b).also { clampInPlace(it) }
        }

        // Largest in-gamut chroma at this lightness and hue, by bisection.
        var lo = 0f
        var hi = c
        repeat(GAMUT_SEARCH_ITERATIONS) {
            val mid = (lo + hi) * 0.5f
            val s = mid / c
            val rgb = labToLinearRgb(l, a * s, b * s)
            if (outOfGamut(rgb[0], rgb[1], rgb[2])) hi = mid else lo = mid
        }
        val cMax = lo

        // Shoulder: everything below the knee passes through untouched, the
        // region above it is compressed asymptotically onto cMax.
        val knee = cMax * GAMUT_KNEE
        val target = if (c <= knee) {
            c
        } else {
            val range = cMax - knee
            if (range <= 1e-6f) cMax else knee + range * (1f - 1f / (1f + (c - knee) / range))
        }

        val scale = target / c
        val rgb = labToLinearRgb(l, a * scale, b * scale)
        clampInPlace(rgb)
        return rgb
    }

    /** Final safety net after gamut mapping — residue is at the 1e-4 level. */
    private fun clampInPlace(rgb: FloatArray) {
        for (i in rgb.indices) rgb[i] = rgb[i].coerceIn(0f, 1f)
    }
}
