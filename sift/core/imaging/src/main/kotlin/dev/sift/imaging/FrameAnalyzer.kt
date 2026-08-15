package dev.sift.imaging

import dev.sift.model.FrameAnalysis
import dev.sift.model.Rect
import dev.sift.model.Size
import kotlin.math.abs
import kotlin.math.cbrt
import kotlin.math.max
import kotlin.math.sqrt

/**
 * The analysis pass (§6.3) — the "unique treatment" engine.
 *
 * **One measurement pass produces one struct. Every downstream parameter is a
 * function of that struct.** This is the architectural answer to per-image
 * treatment, and the reason no stage below is allowed to introduce a constant
 * that is not a target or a bound.
 *
 * ## Where each figure is measured, and why
 *
 * Not everything is measured at the same resolution, and the split is not an
 * optimisation for its own sake — it is what each measurement actually needs.
 *
 * - **Full resolution** — tone histogram, clipping fractions, sharpness, edge
 *   density, luma noise. Clipping fractions in particular *must* be full-res:
 *   §6.12 gates on a 0.002 change, and any downsampling averages clipped pixels
 *   away and reports a fiction.
 * - **Stride-sampled proxy** (long edge ≤ 512) — chroma statistics, grey-world
 *   cast, the skin mask and its median. These are distribution statistics over
 *   the whole frame, and stride sampling is a true random subsample, so the
 *   estimates are unbiased. Box-averaging a proxy would *not* be: it would pull
 *   chroma toward grey and skew the skin median.
 * - **Full-resolution flat blocks** — chroma noise. Noise cannot be measured on
 *   a stride-sampled proxy at all, because neighbouring proxy samples are far
 *   apart in the original and their difference is signal, not noise.
 */
object FrameAnalyzer {

    /** Long edge of the stride-sampled proxy used for distribution statistics. */
    const val PROXY_LONG_EDGE = 512

    /** L* above which a pixel counts as a clipped highlight (§6.3). */
    const val CLIPPED_HIGHLIGHT_L = 98f

    /** L* below which a pixel counts as a crushed shadow (§6.3). */
    const val CRUSHED_SHADOW_L = 2f

    /** Mid-luminance band sampled for the grey-world estimate (§6.8.8). */
    const val MID_LUMA_LOW = 30f
    const val MID_LUMA_HIGH = 70f

    /**
     * Per-pixel luminance step, in L*, above which a pixel counts as an edge.
     * A bound: below roughly 2 L* a step is not visible as an edge at all.
     */
    const val EDGE_GRADIENT_THRESHOLD_L = 2.0f

    /** Document signals (§6.3). All three must hold. */
    const val DOCUMENT_EDGE_DENSITY_MIN = 0.10f
    const val DOCUMENT_CHROMA_MAX = 8f
    const val DOCUMENT_BIMODALITY_MIN = 0.55f

    /** Cap on flat blocks converted to LAB for the chroma-noise estimate. */
    private const val MAX_CHROMA_NOISE_BLOCKS = 256

    /**
     * Measure [image], which must be in linear sRGB (§6.1 step 5, after the
     * promotion to float and before anything is changed).
     *
     * The image is **not modified**.
     */
    fun analyze(
        image: FloatImage,
        metadata: SourceMetadata = SourceMetadata.UNKNOWN,
        faceDetector: FaceDetector = FaceDetector.None,
    ): FrameAnalysis {
        image.requireSpace(ColorSpaceTag.LINEAR_SRGB, "FrameAnalyzer.analyze")
        val w = image.width
        val h = image.height

        // ---- Full-resolution luminance ------------------------------------
        val lPlane = lightnessPlane(image)
        val lHistogram = Statistics.histogram(lPlane, 0f, 100f)

        val medianL = lHistogram.median()
        val blackPointL = lHistogram.percentile(0.001f)
        val whitePointL = lHistogram.percentile(0.999f)
        val clippedHighlightFraction = lHistogram.fractionAtOrAbove(CLIPPED_HIGHLIGHT_L)
        val crushedShadowFraction = lHistogram.fractionBelow(CRUSHED_SHADOW_L)
        val histogramEntropy = lHistogram.normalisedEntropy()
        val bimodality = lHistogram.bimodality()

        // ---- Detail, noise, edges (full resolution) -----------------------
        val laplace = Convolve.laplacian(lPlane, w, h)
        val laplacianVariance = Statistics.variance(laplace)
        val laplacianVarianceP90 = Statistics.laplacianVarianceP90From(laplace, w, h)

        val flatBlocks = Statistics.findFlatBlocks(lPlane, w, h)
        val noiseSigmaLuma = Statistics.sigmaOverBlocks(laplace, w, h, flatBlocks)
        val noiseSigmaChroma = measureChromaNoise(image, flatBlocks)

        val edgeDensity = measureEdgeDensity(lPlane, w, h)

        // ---- Distribution statistics on the stride proxy ------------------
        val proxy = strideProxy(image)
        val proxyGamma = ColorSpaces.toGamma(proxy.copy().also { it.space = ColorSpaceTag.LINEAR_SRGB })
        val skinMaskArray = SkinMask.build(proxyGamma)
        val skinFraction = SkinMask.fraction(skinMaskArray)
        val largestSkinRegionFraction =
            SkinMask.largestRegionFraction(skinMaskArray, proxy.width, proxy.height)

        val proxyLab = ColorSpaces.linearToLab(proxy)
        val chromaStats = measureChroma(proxyLab)
        val skinMedian = SkinMask.medianLab(proxyLab, skinMaskArray)

        // ---- Per-channel clipping (full resolution) -----------------------
        val channelClipFractions = measureChannelClipping(image)

        // ---- Content ------------------------------------------------------
        val faceBoxes = runCatching { faceDetector.detect(proxyGamma, Size(w, h)) }
            .getOrDefault(emptyList())

        val isLikelyScreenshot = DeviceResolutions.matches(w, h) && !metadata.hasExifExposure
        val isLikelyDocument = edgeDensity > DOCUMENT_EDGE_DENSITY_MIN &&
            chromaStats.mean < DOCUMENT_CHROMA_MAX &&
            bimodality > DOCUMENT_BIMODALITY_MIN

        return FrameAnalysis(
            medianL = medianL,
            clippedHighlightFraction = clippedHighlightFraction,
            crushedShadowFraction = crushedShadowFraction,
            blackPointL = blackPointL,
            whitePointL = whitePointL,
            dynamicRange = whitePointL - blackPointL,
            histogramEntropy = histogramEntropy,
            channelClipFractions = channelClipFractions,
            greyWorldCastA = chromaStats.midBandA,
            greyWorldCastB = chromaStats.midBandB,
            meanChroma = chromaStats.mean,
            chromaP95 = chromaStats.p95,
            skinFraction = skinFraction,
            largestSkinRegionFraction = largestSkinRegionFraction,
            skinMedianL = skinMedian?.first,
            skinMedianA = skinMedian?.second,
            skinMedianB = skinMedian?.third,
            laplacianVariance = laplacianVariance,
            laplacianVarianceP90 = laplacianVarianceP90,
            noiseSigmaLuma = noiseSigmaLuma,
            noiseSigmaChroma = noiseSigmaChroma,
            flatRegionFraction = flatBlocks.flatRegionFraction,
            faceCount = faceBoxes.size,
            faceBoxes = faceBoxes,
            isLikelyScreenshot = isLikelyScreenshot,
            isLikelyDocument = isLikelyDocument,
            edgeDensity = edgeDensity,
            sourceWidth = w,
            sourceHeight = h,
        )
    }

    // ---- Building blocks ---------------------------------------------------

    private const val YR = 0.2126729
    private const val YG = 0.7151522
    private const val YB = 0.0721750
    private const val DELTA = 6.0 / 29.0
    private const val DELTA_CUBED = DELTA * DELTA * DELTA
    private const val DELTA_SQ_TIMES_3 = 3.0 * DELTA * DELTA
    private const val FOUR_TWENTY_NINTHS = 4.0 / 29.0

    /**
     * CIELAB L* for every pixel, without paying for a full LAB conversion.
     *
     * L* depends only on Y, so this is one dot product and one cube root per
     * pixel instead of three. On a 12MP frame that is the difference between
     * comfortably inside the §13 analysis budget and well outside it.
     */
    fun lightnessPlane(linearImage: FloatImage): FloatArray {
        linearImage.requireSpace(ColorSpaceTag.LINEAR_SRGB, "lightnessPlane")
        val d = linearImage.data
        val out = FloatArray(linearImage.pixelCount)
        var i = 0
        for (p in out.indices) {
            val y = YR * d[i] + YG * d[i + 1] + YB * d[i + 2]
            val fy = if (y > DELTA_CUBED) cbrt(y) else y / DELTA_SQ_TIMES_3 + FOUR_TWENTY_NINTHS
            out[p] = (116.0 * fy - 16.0).toFloat()
            i += 3
        }
        return out
    }

    /**
     * P90 tile sharpness of a linear image, without a full analysis pass.
     *
     * §6.12's sharpness gate needs this figure for an output and for a
     * like-for-like reference, and paying for two complete [analyze] calls to
     * get one number each would double the cost of every graded frame.
     */
    fun sharpnessP90(linearImage: FloatImage): Float {
        val l = lightnessPlane(linearImage)
        return Statistics.laplacianVarianceP90(l, linearImage.width, linearImage.height)
    }

    /**
     * Stride-sampled proxy in linear sRGB. Sampling, not averaging — see the
     * class note; averaging biases every statistic taken from it.
     */
    fun strideProxy(linearImage: FloatImage, longEdge: Int = PROXY_LONG_EDGE): FloatImage {
        val stride = max(1, (linearImage.longEdge + longEdge - 1) / longEdge)
        if (stride == 1) return linearImage.copy()

        val pw = (linearImage.width + stride - 1) / stride
        val ph = (linearImage.height + stride - 1) / stride
        val out = FloatImage.alloc(pw, ph, ColorSpaceTag.LINEAR_SRGB)
        var d = 0
        for (y in 0 until ph) {
            val sy = (y * stride).coerceAtMost(linearImage.height - 1)
            for (x in 0 until pw) {
                val sx = (x * stride).coerceAtMost(linearImage.width - 1)
                val s = linearImage.index(sx, sy)
                out.data[d] = linearImage.data[s]
                out.data[d + 1] = linearImage.data[s + 1]
                out.data[d + 2] = linearImage.data[s + 2]
                d += 3
            }
        }
        return out
    }

    private class ChromaStats(
        val mean: Float,
        val p95: Float,
        val midBandA: Float,
        val midBandB: Float,
    )

    private fun measureChroma(labImage: FloatImage): ChromaStats {
        labImage.requireSpace(ColorSpaceTag.LAB, "measureChroma")
        val d = labImage.data
        val n = labImage.pixelCount
        val chroma = FloatArray(n)

        var sumChroma = 0.0
        var midA = 0.0
        var midB = 0.0
        var midCount = 0

        var i = 0
        for (p in 0 until n) {
            val l = d[i]
            val a = d[i + 1]
            val b = d[i + 2]
            val c = sqrt(a * a + b * b)
            chroma[p] = c
            sumChroma += c
            // §6.8.8: sample the mid-luminance band only. Highlights and shadows
            // carry unreliable colour and would drag a grey-world estimate around.
            if (l in MID_LUMA_LOW..MID_LUMA_HIGH) {
                midA += a
                midB += b
                midCount++
            }
            i += 3
        }

        return ChromaStats(
            mean = (sumChroma / n).toFloat(),
            p95 = Statistics.percentileOf(chroma, 0.95f),
            midBandA = if (midCount > 0) (midA / midCount).toFloat() else 0f,
            midBandB = if (midCount > 0) (midB / midCount).toFloat() else 0f,
        )
    }

    /**
     * Per-channel clipping (§6.8.1).
     *
     * Measured independently per channel because that is the whole point: when
     * one channel clips and the others do not, the clipped one is
     * *reconstructible* from the ratio of the survivors. A luminance-only
     * highlight measurement cannot see this case at all.
     */
    private fun measureChannelClipping(linearImage: FloatImage): List<Float> {
        val threshold = ColorSpaces.srgbToLinear(254f / 255f)
        val d = linearImage.data
        val n = linearImage.pixelCount
        var r = 0
        var g = 0
        var b = 0
        var i = 0
        while (i < d.size) {
            if (d[i] >= threshold) r++
            if (d[i + 1] >= threshold) g++
            if (d[i + 2] >= threshold) b++
            i += 3
        }
        return listOf(r.toFloat() / n, g.toFloat() / n, b.toFloat() / n)
    }

    private fun measureEdgeDensity(lPlane: FloatArray, width: Int, height: Int): Float {
        val sobel = Convolve.sobelMagnitude(lPlane, width, height)
        // The Sobel kernel has a gain of 4 for a unit step; normalise back so the
        // threshold is expressed as an honest per-pixel L* step.
        val threshold = EDGE_GRADIENT_THRESHOLD_L * 4f
        var count = 0
        for (v in sobel) if (v > threshold) count++
        return count.toFloat() / sobel.size
    }

    /**
     * Chroma noise, measured over the same flat blocks as the luma estimate but
     * at full resolution, converting only those blocks to LAB.
     *
     * Chroma noise is what reads as ugly coloured mottling and it is why §6.5
     * denoises chroma about three times as hard as luma. Getting it from a
     * downscaled proxy is not possible — see the class note.
     */
    private fun measureChromaNoise(
        linearImage: FloatImage,
        flat: Statistics.NoiseEstimate,
    ): Float {
        if (flat.flatBlockIndices.isEmpty() || flat.blockCols == 0) return 0f
        val w = linearImage.width
        val h = linearImage.height
        val bs = flat.blockSize

        val step = max(1, flat.flatBlockIndices.size / MAX_CHROMA_NOISE_BLOCKS)
        val responses = ArrayList<Float>()

        val aPlane = FloatArray(bs * bs)
        val bPlane = FloatArray(bs * bs)

        var k = 0
        while (k < flat.flatBlockIndices.size) {
            val blockIndex = flat.flatBlockIndices[k]
            val bx = blockIndex % flat.blockCols
            val by = blockIndex / flat.blockCols
            val x0 = bx * bs
            val y0 = by * bs
            val x1 = (x0 + bs).coerceAtMost(w)
            val y1 = (y0 + bs).coerceAtMost(h)
            val bw = x1 - x0
            val bh = y1 - y0
            if (bw >= 3 && bh >= 3) {
                for (y in 0 until bh) {
                    for (x in 0 until bw) {
                        val s = linearImage.index(x0 + x, y0 + y)
                        val lab = ColorSpaces.linearRgbToLab(
                            linearImage.data[s],
                            linearImage.data[s + 1],
                            linearImage.data[s + 2],
                        )
                        aPlane[y * bw + x] = lab[1]
                        bPlane[y * bw + x] = lab[2]
                    }
                }
                val la = Convolve.laplacian(aPlane.copyOf(bw * bh), bw, bh)
                val lb = Convolve.laplacian(bPlane.copyOf(bw * bh), bw, bh)
                for (y in 1 until bh - 1) {
                    for (x in 1 until bw - 1) {
                        responses.add(abs(la[y * bw + x]))
                        responses.add(abs(lb[y * bw + x]))
                    }
                }
            }
            k += step
        }

        if (responses.size < 16) return 0f
        val values = responses.toFloatArray()
        values.sort()
        val mad = values[values.size / 2]
        return (mad * 1.4826f / sqrt(20f))
    }
}
