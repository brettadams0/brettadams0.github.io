package dev.sift.imaging

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sin

/**
 * Resizing (§6.10).
 *
 * **Every function here requires linear light and says so.** Downsampling
 * gamma-encoded data darkens the result — averaging two pixels of encoded 0.0
 * and 1.0 gives encoded 0.5, which is only 21% of the light, not 50%. It is
 * trap #3, it is visible as muddy midtones on any high-frequency content, and
 * the [FloatImage.requireSpace] call at the top of each function is what stops
 * it happening by accident.
 *
 * - Downsample uses a box average over the exact source footprint of each
 *   destination pixel — the correct behaviour, and what `INTER_AREA` does.
 * - Upsample uses Lanczos-4, matching `INTER_LANCZOS4`.
 */
object Resample {

    /** Dispatch on direction: area-average down, Lanczos up. */
    fun resize(image: FloatImage, targetWidth: Int, targetHeight: Int): FloatImage {
        image.requireSpace(ColorSpaceTag.LINEAR_SRGB, "Resample.resize")
        if (targetWidth == image.width && targetHeight == image.height) return image
        val shrinking = targetWidth <= image.width && targetHeight <= image.height
        return if (shrinking) {
            areaDownsample(image, targetWidth, targetHeight)
        } else {
            lanczosUpsample(image, targetWidth, targetHeight)
        }
    }

    /**
     * Box average over each destination pixel's exact source footprint.
     *
     * Fractional edge coverage is weighted, not rounded — rounding produces
     * aliasing on regular structures (brick, railings, fabric) that is very
     * hard to distinguish from a bad sharpen later on.
     */
    fun areaDownsample(image: FloatImage, targetWidth: Int, targetHeight: Int): FloatImage {
        image.requireSpace(ColorSpaceTag.LINEAR_SRGB, "areaDownsample")
        require(targetWidth in 1..image.width && targetHeight in 1..image.height) {
            "areaDownsample only shrinks: ${image.width}x${image.height} -> ${targetWidth}x$targetHeight"
        }
        val out = FloatImage.alloc(targetWidth, targetHeight, ColorSpaceTag.LINEAR_SRGB)
        val scaleX = image.width.toDouble() / targetWidth
        val scaleY = image.height.toDouble() / targetHeight

        for (dy in 0 until targetHeight) {
            val sy0 = dy * scaleY
            val sy1 = (dy + 1) * scaleY
            val y0 = floor(sy0).toInt()
            val y1 = ceil(sy1).toInt().coerceAtMost(image.height)

            for (dx in 0 until targetWidth) {
                val sx0 = dx * scaleX
                val sx1 = (dx + 1) * scaleX
                val x0 = floor(sx0).toInt()
                val x1 = ceil(sx1).toInt().coerceAtMost(image.width)

                var accR = 0.0
                var accG = 0.0
                var accB = 0.0
                var accW = 0.0

                for (y in y0 until y1) {
                    val wy = (minOf(sy1, (y + 1).toDouble()) - maxOf(sy0, y.toDouble()))
                        .coerceAtLeast(0.0)
                    if (wy <= 0.0) continue
                    for (x in x0 until x1) {
                        val wx = (minOf(sx1, (x + 1).toDouble()) - maxOf(sx0, x.toDouble()))
                            .coerceAtLeast(0.0)
                        if (wx <= 0.0) continue
                        val w = wx * wy
                        val i = image.index(x, y)
                        accR += image.data[i] * w
                        accG += image.data[i + 1] * w
                        accB += image.data[i + 2] * w
                        accW += w
                    }
                }

                val o = out.index(dx, dy)
                if (accW > 0.0) {
                    out.data[o] = (accR / accW).toFloat()
                    out.data[o + 1] = (accG / accW).toFloat()
                    out.data[o + 2] = (accB / accW).toFloat()
                }
            }
        }
        return out
    }

    /** Lanczos-4 windowed sinc, the `INTER_LANCZOS4` kernel. */
    const val LANCZOS_A = 4

    private fun lanczos(x: Float): Float {
        val ax = abs(x)
        if (ax < 1e-6f) return 1f
        if (ax >= LANCZOS_A) return 0f
        val px = PI * x
        val pxa = px / LANCZOS_A
        return (sin(px.toDouble()) / px * (sin(pxa.toDouble()) / pxa)).toFloat()
    }

    /**
     * Separable Lanczos-4 upsample.
     *
     * Ringing is inherent to the kernel and is exactly why §6.10 puts output
     * sharpening *after* the resize with a threshold — sharpening ring artifacts
     * is how upscaled output starts looking crunchy.
     */
    fun lanczosUpsample(image: FloatImage, targetWidth: Int, targetHeight: Int): FloatImage {
        image.requireSpace(ColorSpaceTag.LINEAR_SRGB, "lanczosUpsample")
        val horizontal = resampleAxis(
            src = image.data,
            srcWidth = image.width,
            srcHeight = image.height,
            dstWidth = targetWidth,
            horizontal = true,
        )
        val vertical = resampleAxis(
            src = horizontal,
            srcWidth = targetWidth,
            srcHeight = image.height,
            dstWidth = targetHeight,
            horizontal = false,
        )
        return FloatImage(targetWidth, targetHeight, ColorSpaceTag.LINEAR_SRGB, vertical)
    }

    private fun resampleAxis(
        src: FloatArray,
        srcWidth: Int,
        srcHeight: Int,
        dstWidth: Int,
        horizontal: Boolean,
    ): FloatArray {
        val srcLength = if (horizontal) srcWidth else srcHeight
        val otherLength = if (horizontal) srcHeight else srcWidth
        val outWidth = if (horizontal) dstWidth else srcWidth
        val out = FloatArray(
            if (horizontal) dstWidth * srcHeight * 3 else srcWidth * dstWidth * 3,
        )

        val scale = srcLength.toFloat() / dstWidth
        // Upsampling only, so the kernel is not stretched; the support stays 4.
        val support = LANCZOS_A

        val weights = FloatArray(2 * support + 2)
        for (d in 0 until dstWidth) {
            val center = (d + 0.5f) * scale - 0.5f
            val first = floor(center).toInt() - support + 1
            var weightSum = 0f
            for (k in 0 until 2 * support) {
                val w = lanczos(center - (first + k))
                weights[k] = w
                weightSum += w
            }
            if (weightSum == 0f) weightSum = 1f

            for (o in 0 until otherLength) {
                var accR = 0f
                var accG = 0f
                var accB = 0f
                for (k in 0 until 2 * support) {
                    val w = weights[k]
                    if (w == 0f) continue
                    val s = (first + k).coerceIn(0, srcLength - 1)
                    val idx = if (horizontal) {
                        (o * srcWidth + s) * 3
                    } else {
                        (s * srcWidth + o) * 3
                    }
                    accR += src[idx] * w
                    accG += src[idx + 1] * w
                    accB += src[idx + 2] * w
                }
                val outIdx = if (horizontal) {
                    (o * outWidth + d) * 3
                } else {
                    (d * outWidth + o) * 3
                }
                out[outIdx] = accR / weightSum
                out[outIdx + 1] = accG / weightSum
                out[outIdx + 2] = accB / weightSum
            }
        }
        return out
    }

    /**
     * Fit [image] inside a [targetWidth] x [targetHeight] box, cropping to fill.
     *
     * [focusX] and [focusY] are normalised 0..1 and bias which part of the frame
     * survives the crop — §10 uses them to put the dominant face on a
     * rule-of-thirds line rather than dead centre.
     */
    fun cropToAspect(
        image: FloatImage,
        targetWidth: Int,
        targetHeight: Int,
        focusX: Float = 0.5f,
        focusY: Float = 0.5f,
    ): FloatImage {
        image.requireSpace(ColorSpaceTag.LINEAR_SRGB, "cropToAspect")
        val targetAspect = targetWidth.toFloat() / targetHeight
        val sourceAspect = image.width.toFloat() / image.height

        val cropW: Int
        val cropH: Int
        if (sourceAspect > targetAspect) {
            cropH = image.height
            cropW = (image.height * targetAspect).toInt().coerceIn(1, image.width)
        } else {
            cropW = image.width
            cropH = (image.width / targetAspect).toInt().coerceIn(1, image.height)
        }

        val left = ((image.width - cropW) * focusX).toInt().coerceIn(0, image.width - cropW)
        val top = ((image.height - cropH) * focusY).toInt().coerceIn(0, image.height - cropH)

        val out = FloatImage.alloc(cropW, cropH, ColorSpaceTag.LINEAR_SRGB)
        for (y in 0 until cropH) {
            val srcRow = image.index(left, top + y)
            val dstRow = out.index(0, y)
            System.arraycopy(image.data, srcRow, out.data, dstRow, cropW * FloatImage.CHANNELS)
        }
        return out
    }
}
