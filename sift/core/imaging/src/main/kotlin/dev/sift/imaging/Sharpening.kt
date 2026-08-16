package dev.sift.imaging

import dev.sift.model.DerivedParams
import dev.sift.model.FrameAnalysis
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Local contrast (§6.9) — what makes a photo read as "professionally finished"
 * rather than merely correct.
 *
 * A large-radius, low-amount unsharp mask on the **L channel only**. Running it
 * on chroma fringes every edge with colour (trap #4), which is why the whole
 * spatial toolkit here is planar.
 *
 * **Halo suppression is mandatory.** Without the clamp you get the grey halos
 * around horizons and rooflines that are the signature of overcooked HDR — the
 * single most recognisable "this was processed" artifact, and one no amount of
 * good grading elsewhere can excuse.
 */
object LocalContrast {

    /** §6.9 — radius as a fraction of the long edge (~67px on a 12MP frame). */
    const val RADIUS_DIVISOR = 60

    /** §6.9 — amount range, scaled inversely by edge density. */
    const val AMOUNT_MAX = 0.35f
    const val AMOUNT_MIN = 0.15f

    /** Edge density at which a frame counts as busy and gets the minimum amount. */
    const val BUSY_EDGE_DENSITY = 0.25f

    /** §6.9 — no pixel may move more than this many L* units. Mandatory. */
    const val HALO_CLAMP_L = 8f

    /**
     * Apply in place. Takes and returns linear sRGB.
     */
    fun apply(
        image: FloatImage,
        analysis: FrameAnalysis,
        strengthScale: Float = 1f,
    ): DerivedParams.LocalContrastParams {
        image.requireSpace(ColorSpaceTag.LINEAR_SRGB, "LocalContrast.apply")

        val radius = (image.longEdge.toFloat() / RADIUS_DIVISOR).coerceAtLeast(1f)
        // A busy frame needs less or it turns crunchy.
        val busyness = (analysis.edgeDensity / BUSY_EDGE_DENSITY).coerceIn(0f, 1f)
        val amount = (AMOUNT_MAX - (AMOUNT_MAX - AMOUNT_MIN) * busyness) * strengthScale

        if (amount > 1e-4f) {
            ColorSpaces.linearToLab(image)
            val l = image.channel(0)
            val blurred = lowFrequencyBlur(l, image.width, image.height, radius)

            for (i in l.indices) {
                val delta = (l[i] - blurred[i]) * amount
                l[i] += delta.coerceIn(-HALO_CLAMP_L, HALO_CLAMP_L)
            }
            image.setChannel(0, l)
            ColorSpaces.labToLinear(image)
        }

        return DerivedParams.LocalContrastParams(
            radiusPx = radius,
            amount = amount,
            haloClampL = HALO_CLAMP_L,
        )
    }

    /**
     * The large-radius blur, computed at reduced scale.
     *
     * §6.9 asks for a radius of roughly `longEdge / 60` — about 67px on a 12MP
     * frame. A blur that wide contains, by construction, nothing above a very
     * low spatial frequency, so computing it at full resolution spends most of
     * its time producing detail that the radius has already destroyed. Running
     * it on a quarter-scale plane with a quarter-scale radius and bilinearly
     * expanding the result is visually identical and roughly sixteen times less
     * work — and this stage was the second most expensive in the whole pipeline.
     *
     * The high-pass is still taken against the full-resolution L channel, so the
     * detail that local contrast actually acts on is untouched.
     */
    private fun lowFrequencyBlur(
        plane: FloatArray,
        width: Int,
        height: Int,
        radius: Float,
    ): FloatArray {
        if (radius < MIN_DOWNSCALED_RADIUS || width < 64 || height < 64) {
            return Convolve.gaussianBlur(plane.copyOf(), width, height, radius)
        }
        val scale = BLUR_DOWNSCALE
        val sw = (width + scale - 1) / scale
        val sh = (height + scale - 1) / scale

        // Box-average down: cheap, and pre-filtering is exactly right ahead of a blur.
        val small = FloatArray(sw * sh)
        for (y in 0 until sh) {
            val y0 = y * scale
            val y1 = minOf(y0 + scale, height)
            for (x in 0 until sw) {
                val x0 = x * scale
                val x1 = minOf(x0 + scale, width)
                var sum = 0f
                var n = 0
                for (yy in y0 until y1) {
                    val row = yy * width
                    for (xx in x0 until x1) { sum += plane[row + xx]; n++ }
                }
                small[y * sw + x] = if (n > 0) sum / n else 0f
            }
        }

        Convolve.gaussianBlur(small, sw, sh, radius / scale)

        // Bilinear expansion back to full resolution.
        val out = FloatArray(plane.size)
        Parallel.rows(width, height) { y ->
            val fy = ((y + 0.5f) / scale - 0.5f).coerceIn(0f, (sh - 1).toFloat())
            val y0 = fy.toInt().coerceAtMost(sh - 1)
            val y1 = (y0 + 1).coerceAtMost(sh - 1)
            val wy = fy - y0
            val row = y * width
            for (x in 0 until width) {
                val fx = ((x + 0.5f) / scale - 0.5f).coerceIn(0f, (sw - 1).toFloat())
                val x0 = fx.toInt().coerceAtMost(sw - 1)
                val x1 = (x0 + 1).coerceAtMost(sw - 1)
                val wx = fx - x0
                val top = small[y0 * sw + x0] * (1f - wx) + small[y0 * sw + x1] * wx
                val bottom = small[y1 * sw + x0] * (1f - wx) + small[y1 * sw + x1] * wx
                out[row + x] = top * (1f - wy) + bottom * wy
            }
        }
        return out
    }

    /** Below this radius the downscale saves nothing worth the extra passes. */
    private const val MIN_DOWNSCALED_RADIUS = 12f

    /** Quarter scale: the radius stays comfortably above a pixel after division. */
    private const val BLUR_DOWNSCALE = 4
}

/**
 * Output sharpening (§6.10).
 *
 * Two rules, both of which have visible failure modes if broken:
 *
 * - **Sized to the output, not the source.** A radius appropriate for a 6000px
 *   master is invisible at 1080px, and a radius appropriate for 1080px is
 *   crunchy at 6000px. Hence `outputLongEdge / 1200`, which gives the 0.9px of
 *   the §10 preset table at 1080 and about 5px on a full master.
 * - **Applied after the resize.** Sharpening first and then resizing means the
 *   sharpening was done at the wrong scale and the resize then smears it.
 *
 * The threshold is what stops the sharpener turning sky noise into visible
 * speckle: pixels whose local contrast is below twice the measured noise sigma
 * are left alone, because at that amplitude there is nothing there but noise.
 */
object OutputSharpen {

    /** §6.10 / §10 — radius as a fraction of the *output* long edge. */
    const val RADIUS_DIVISOR = 1200

    /**
     * Apparent-sharpness target, as a Laplacian variance in L* units.
     *
     * §6.10 asks for an amount "targeting a consistent apparent sharpness rather
     * than a fixed strength", which requires a target to aim at. Like the
     * detail-blend fraction of §6.6 this is a number that wants calibrating once
     * against real photographs and then committing; the value here is a
     * defensible starting point, not a measured one.
     */
    const val TARGET_LAPLACIAN_VARIANCE = 12f

    const val MAX_AMOUNT = 1.2f

    /** §6.10 — skip pixels whose local contrast is below `noiseSigmaLuma * 2`. */
    const val NOISE_THRESHOLD_MULTIPLE = 2f

    /**
     * Sharpen in place, sized to [image]'s own dimensions. Takes and returns
     * linear sRGB.
     *
     * [noiseSigmaLuma] should come from the *source* analysis: it describes the
     * grain that survived into this output, and re-measuring it post-sharpen
     * would be circular.
     */
    fun apply(
        image: FloatImage,
        noiseSigmaLuma: Float,
        strengthScale: Float = 1f,
    ): DerivedParams.SharpenParams {
        image.requireSpace(ColorSpaceTag.LINEAR_SRGB, "OutputSharpen.apply")

        val radius = (image.longEdge.toFloat() / RADIUS_DIVISOR).coerceAtLeast(0.4f)
        val threshold = noiseSigmaLuma * NOISE_THRESHOLD_MULTIPLE

        ColorSpaces.linearToLab(image)
        val l = image.channel(0)

        // Amount is derived from what this output actually needs, measured after
        // the resize that changed it.
        // Sampled, not exhaustive: this figure only chooses an amount, and a
        // variance estimated from every fourth row is within a percent of the
        // full-frame value while costing a quarter as much. It was measured as
        // the single most expensive step in the pipeline.
        val measured = Statistics.sampledLaplacianVariance(l, image.width, image.height)
        val amount = if (measured <= 1e-4f) {
            0f
        } else {
            (sqrt(TARGET_LAPLACIAN_VARIANCE / measured) - 1f)
                .coerceIn(0f, MAX_AMOUNT) * strengthScale
        }

        if (amount > 1e-4f) {
            val blurred = Convolve.gaussianBlur(l.copyOf(), image.width, image.height, radius)
            for (i in l.indices) {
                val detail = l[i] - blurred[i]
                if (abs(detail) < threshold) continue
                l[i] += detail * amount
            }
            image.setChannel(0, l)
        }

        ColorSpaces.labToLinear(image)

        return DerivedParams.SharpenParams(
            radiusPx = radius,
            amount = amount,
            thresholdL = threshold,
        )
    }
}
