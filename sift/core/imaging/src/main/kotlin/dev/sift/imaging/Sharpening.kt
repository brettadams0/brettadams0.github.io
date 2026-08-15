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
            val blurred = Convolve.gaussianBlur(l.copyOf(), image.width, image.height, radius)

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
        val measured = Statistics.laplacianVariance(l, image.width, image.height)
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
