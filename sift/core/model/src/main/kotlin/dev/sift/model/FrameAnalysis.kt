package dev.sift.model

import kotlinx.serialization.Serializable

/**
 * The output of the single measurement pass (§6.3) — the "unique treatment" engine.
 *
 * **One measurement pass produces one struct. Every downstream parameter is a
 * function of this struct.** No downstream stage may introduce a constant that
 * is not a target or a bound (§0 rule 1).
 *
 * All luminance and colour figures are in **true CIELAB units**: `L* ∈ [0,100]`,
 * `a*, b* ∈ [-127,127]` centred on zero. They are never the OpenCV 8-bit scaled
 * variety offset by 128 — see the porting trap in §6.2 and trap #1 in §16.
 * [dev.sift.imaging.ColorSpaces] is the only place that conversion lives, and
 * `LabGoldenTest` pins it to published reference values.
 *
 * Serialized whole into `MediaAsset.analysisJson`. When a photo comes out wrong
 * six weeks from now, this is how you find out why.
 */
@Serializable
data class FrameAnalysis(
    // ---- Exposure & tone -------------------------------------------------
    /** Median CIELAB L*. */
    val medianL: Float,
    /** Fraction of pixels with L* > 98. */
    val clippedHighlightFraction: Float,
    /** Fraction of pixels with L* < 2. */
    val crushedShadowFraction: Float,
    /** 0.1 percentile of L*. */
    val blackPointL: Float,
    /** 99.9 percentile of L*. */
    val whitePointL: Float,
    /** [whitePointL] − [blackPointL]. */
    val dynamicRange: Float,
    /** Shannon entropy of the L* histogram, normalised to [0,1]. Tonal health. */
    val histogramEntropy: Float,

    // ---- Per-channel clipping — drives highlight reconstruction (§6.8.1) --
    /** R, G, B independently. A channel clipping alone is reconstructible. */
    val channelClipFractions: List<Float>,

    // ---- Colour ----------------------------------------------------------
    /** Measured a*, b* drift over the mid-luma band (L* 30–70). */
    val greyWorldCastA: Float,
    val greyWorldCastB: Float,
    /** Mean sqrt(a*² + b*²). */
    val meanChroma: Float,
    /** 95th percentile chroma — how saturated the content already is. */
    val chromaP95: Float,

    // ---- Skin ------------------------------------------------------------
    val skinFraction: Float,
    /** Largest *contiguous* skin region as a fraction of frame area. Guards §6.4. */
    val largestSkinRegionFraction: Float,
    val skinMedianL: Float?,
    val skinMedianA: Float?,
    val skinMedianB: Float?,

    // ---- Detail & noise --------------------------------------------------
    /** Global sharpness: variance of the Laplacian of L*. */
    val laplacianVariance: Float,
    /**
     * Sharpness of the *sharpest* regions — P90 over tiles.
     *
     * A shallow-depth-of-field portrait has low mean sharpness but is perfectly
     * sharp where it matters. Mean alone wrongly trips the upscale gate (trap #11).
     */
    val laplacianVarianceP90: Float,
    /** Noise sigma in L* units, measured in flat regions only (trap #10). */
    val noiseSigmaLuma: Float,
    /** Noise sigma over the a* and b* channels, same restriction. */
    val noiseSigmaChroma: Float,
    val flatRegionFraction: Float,

    // ---- Content ---------------------------------------------------------
    val faceCount: Int,
    val faceBoxes: List<Rect>,
    val isLikelyScreenshot: Boolean,
    val isLikelyDocument: Boolean,
    val edgeDensity: Float,

    // ---- Provenance ------------------------------------------------------
    val sourceWidth: Int,
    val sourceHeight: Int,
) {
    val sourceLongEdge: Int get() = maxOf(sourceWidth, sourceHeight)
    val sourcePixels: Long get() = sourceWidth.toLong() * sourceHeight.toLong()

    val skinMedianLab: Triple<Float, Float, Float>?
        get() = if (skinMedianL != null && skinMedianA != null && skinMedianB != null) {
            Triple(skinMedianL, skinMedianA, skinMedianB)
        } else {
            null
        }

    val isNonPhotographic: Boolean get() = isLikelyScreenshot || isLikelyDocument

    /**
     * Content routing (§6.4).
     *
     * The contiguity check exists because brick, sand, wood and terracotta all
     * clear a raw 2% skin threshold. The face-count term is a second guard on
     * the same failure (trap #13). Where no face detector is wired up,
     * [faceCount] is 0 and the `skinFraction > 0.08` term carries the decision
     * on its own — the router degrades, it does not break.
     */
    fun route(): ContentClass = when {
        isNonPhotographic -> ContentClass.NON_PHOTOGRAPHIC
        skinFraction > SKIN_FRACTION_MIN &&
            largestSkinRegionFraction > LARGEST_SKIN_REGION_MIN &&
            (faceCount > 0 || skinFraction > SKIN_FRACTION_NO_FACE) -> ContentClass.PORTRAIT
        else -> ContentClass.SCENE
    }

    companion object {
        const val SKIN_FRACTION_MIN = 0.02f
        const val LARGEST_SKIN_REGION_MIN = 0.01f
        const val SKIN_FRACTION_NO_FACE = 0.08f
    }
}
