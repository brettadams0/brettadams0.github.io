package dev.sift.imaging

import dev.sift.model.DerivedParams
import dev.sift.model.ExportPreset
import dev.sift.model.FrameAnalysis
import dev.sift.model.GradeSettings
import dev.sift.model.Rect
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.PI

/**
 * Upscale gating and the baseline resampler (§6.6).
 *
 * ## Why there is no ONNX code here
 *
 * §6.6 ends with an instruction, and §18 repeats it as open decision 3:
 * *"Before writing any ONNX code: A/B it. Build a Lanczos + unsharp baseline.
 * Compare on ten of your real photos at 100%. If the difference doesn't justify
 * 30 seconds per image and a native dependency, ship the baseline and delete
 * this section."*
 *
 * So the baseline is what exists. [SuperResolver] is the seam a Real-ESRGAN
 * implementation drops into if the A/B says it earns its place, and
 * [detailPreservingBlend] and [softenSmallFaces] — the two pieces that make a
 * learned upscaler look photographic rather than waxy — are written against that
 * interface rather than against a specific model, so they apply to whatever wins.
 * Writing the ONNX path before running the comparison would be building the
 * thing the spec says to justify first.
 */
object Upscale {

    /** §6.6 — sources below this fraction of the target are genuinely short of pixels. */
    const val RESOLUTION_SHORTFALL = 0.9f

    /** §6.6 — a crop this small needs help regardless of the target. */
    const val SMALL_CROP_PIXELS = 2_000_000L

    /** §6.6 — sharpness ceilings on the factor. This is §2.7 made concrete. */
    const val SOFT_P90 = 50f
    const val MODERATE_P90 = 150f
    const val MAX_FACTOR = 4f

    /** §6.6 — faces below this share of the frame get conservative treatment. */
    const val SMALL_FACE_AREA_FRACTION = 0.05f

    /** §6.6 — blend SR toward Lanczos by this much inside a small face. */
    const val SMALL_FACE_LANCZOS_BLEND = 0.5f

    /** §6.6 — feather width for that blend, in pixels. */
    const val SMALL_FACE_FEATHER_PX = 16

    data class Decision(
        val shouldRun: Boolean,
        val requestedFactor: Float,
        val effectiveFactor: Float,
        val cappedBySharpness: Boolean,
        val reason: String,
    )

    /**
     * Decide whether to upscale and by how much.
     *
     * The sharpness cap is the part that matters. A soft source upscaled 4x
     * produces confident, plausible, fictional texture — which is the opposite
     * of professional (§2.7). The measurement used is the **P90** of per-tile
     * sharpness, never the mean: a shallow-depth-of-field portrait has a sharp
     * subject against a blurred background, and judging it by the mean rejects
     * exactly the frames most worth upscaling (trap #11).
     */
    fun decide(
        analysis: FrameAnalysis,
        targetLongEdge: Int,
        mode: GradeSettings.UpscaleMode,
    ): Decision {
        if (mode == GradeSettings.UpscaleMode.OFF) {
            return Decision(false, 1f, 1f, false, "upscale disabled in settings")
        }

        val sourceLongEdge = analysis.sourceLongEdge
        val shortOfTarget = sourceLongEdge < targetLongEdge * RESOLUTION_SHORTFALL
        val smallCrop = analysis.sourcePixels < SMALL_CROP_PIXELS
        val explicit = mode == GradeSettings.UpscaleMode.ALWAYS

        if (!shortOfTarget && !smallCrop && !explicit) {
            return Decision(
                false, 1f, 1f, false,
                "source ${sourceLongEdge}px already covers target ${targetLongEdge}px",
            )
        }

        val needed = if (sourceLongEdge <= 0) {
            1f
        } else {
            (targetLongEdge.toFloat() / sourceLongEdge).coerceAtLeast(1f)
        }
        val requested = ceil(needed).coerceIn(1f, MAX_FACTOR)

        val maxBySharpness = when {
            analysis.laplacianVarianceP90 < SOFT_P90 -> 1f
            analysis.laplacianVarianceP90 < MODERATE_P90 -> 2f
            else -> MAX_FACTOR
        }

        val effective = minOf(maxBySharpness, requested, MAX_FACTOR)
        val capped = effective < requested

        // Never exceed the 6000px output cap, whatever the factor says.
        val capLimited = (ExportPreset.MASTER_LONG_EDGE_CAP.toFloat() / sourceLongEdge)
            .coerceAtLeast(1f)
        val final = minOf(effective, capLimited)

        return Decision(
            shouldRun = final > 1.01f,
            requestedFactor = requested,
            effectiveFactor = final,
            cappedBySharpness = capped,
            reason = when {
                final <= 1.01f && maxBySharpness == 1f ->
                    "source too soft to upscale (P90 ${analysis.laplacianVarianceP90}); " +
                        "SR would invent detail"
                capped -> "factor capped at $maxBySharpness by measured sharpness"
                explicit -> "explicit user request"
                smallCrop -> "post-crop pixels below 2MP"
                else -> "source short of target resolution"
            },
        )
    }

    /**
     * A resampler that can produce a larger image than it was given.
     *
     * [LanczosBaseline] is the §6.6 A/B baseline and the current shipping
     * implementation. A Real-ESRGAN `general-x4v3` implementation in `:core:ml`
     * would satisfy the same contract — including the tiling and streaming that
     * §6.6 requires, since a 4x upscale of a 12MP source is 192MP and must never
     * be materialised as a single bitmap.
     */
    fun interface SuperResolver {
        fun upscale(image: FloatImage, factor: Float): FloatImage
    }

    /** Lanczos-4 in linear light. The baseline every alternative must beat. */
    val LanczosBaseline = SuperResolver { image, factor ->
        image.requireSpace(ColorSpaceTag.LINEAR_SRGB, "LanczosBaseline")
        Resample.lanczosUpsample(
            image,
            (image.width * factor).toInt().coerceAtLeast(1),
            (image.height * factor).toInt().coerceAtLeast(1),
        )
    }

    /**
     * Run the decided upscale, applying the treatments that keep a learned
     * upscaler from looking synthetic.
     */
    fun apply(
        image: FloatImage,
        decision: Decision,
        analysis: FrameAnalysis,
        settings: GradeSettings,
        resolver: SuperResolver = LanczosBaseline,
    ): Pair<FloatImage, DerivedParams.UpscaleParams> {
        if (!decision.shouldRun) {
            return image to DerivedParams.UpscaleParams(
                requestedFactor = decision.requestedFactor,
                effectiveFactor = 1f,
                cappedBySharpness = decision.cappedBySharpness,
                method = "none",
                detailBlendFraction = 0f,
            )
        }

        val factor = decision.effectiveFactor
        val upscaled = resolver.upscale(image, factor)
        val isBaseline = resolver === LanczosBaseline

        // The detail blend exists to counter a learned model's over-smoothing.
        // Lanczos does not over-smooth — it rings — so adding its own high-pass
        // back onto itself would just amplify that ringing. The blend is applied
        // only when the resolver is something other than the baseline.
        var blendFraction = 0f
        if (!isBaseline) {
            blendFraction = settings.detailBlendFraction
            val lanczos = LanczosBaseline.upscale(image, factor)
            detailPreservingBlend(upscaled, lanczos, blendFraction)
            softenSmallFaces(upscaled, lanczos, analysis, factor)
        }

        return upscaled to DerivedParams.UpscaleParams(
            requestedFactor = decision.requestedFactor,
            effectiveFactor = factor,
            cappedBySharpness = decision.cappedBySharpness,
            method = if (isBaseline) "lanczos4" else "sr+detail-blend",
            detailBlendFraction = blendFraction,
        )
    }

    /**
     * §6.6's detail-preserving blend — "what separates professional from plasticky".
     *
     * Learned upscalers over-smooth skin and foliage, producing the waxy look
     * that makes AI upscaling recognisable at a glance. Adding a fraction of the
     * *Lanczos* result's high-pass back onto the SR output restores micro-texture
     * and natural grain structure the model flattened, without reintroducing the
     * model's own errors.
     *
     * 1. Take the Lanczos upsample of the same source at the same factor.
     * 2. High-pass it — subtract a Gaussian blur of itself.
     * 3. Add [fraction] of that back onto the SR output.
     */
    fun detailPreservingBlend(sr: FloatImage, lanczos: FloatImage, fraction: Float) {
        require(sr.width == lanczos.width && sr.height == lanczos.height) {
            "detail blend needs matching geometry"
        }
        if (fraction <= 0f) return
        sr.requireSpace(ColorSpaceTag.LINEAR_SRGB, "detailPreservingBlend")

        // Micro-texture lives at a radius of about a pixel at output scale.
        val radius = 1.0f
        for (c in 0 until FloatImage.CHANNELS) {
            val plane = lanczos.channel(c)
            val blurred = Convolve.gaussianBlur(plane.copyOf(), lanczos.width, lanczos.height, radius)
            var i = c
            for (p in plane.indices) {
                sr.data[i] += (plane[p] - blurred[p]) * fraction
                i += FloatImage.CHANNELS
            }
        }
    }

    /**
     * §6.6 — conservative treatment for small faces.
     *
     * SR models produce uncanny results on small faces: they hallucinate
     * eyelashes and pores that were never in the source, and the result reads as
     * wrong even to someone who cannot say why. Inside any face box smaller than
     * 5% of frame area, the output is pulled halfway back to Lanczos, feathered
     * over 16px so the transition is invisible. Plausibility over invented
     * sharpness.
     */
    fun softenSmallFaces(
        sr: FloatImage,
        lanczos: FloatImage,
        analysis: FrameAnalysis,
        factor: Float,
    ) {
        if (analysis.faceBoxes.isEmpty()) return
        val frameArea = sr.width.toLong() * sr.height.toLong()

        for (sourceBox in analysis.faceBoxes) {
            val box = sourceBox.scaled(factor)
            if (box.area <= 0) continue
            if (box.area.toDouble() / frameArea >= SMALL_FACE_AREA_FRACTION) continue

            val outer = box.inflate(SMALL_FACE_FEATHER_PX, sr.width, sr.height)
            for (y in outer.top until outer.bottom) {
                for (x in outer.left until outer.right) {
                    val weight = featherWeight(x, y, box) * SMALL_FACE_LANCZOS_BLEND
                    if (weight <= 0f) continue
                    val i = sr.index(x, y)
                    for (c in 0 until FloatImage.CHANNELS) {
                        sr.data[i + c] = sr.data[i + c] * (1f - weight) + lanczos.data[i + c] * weight
                    }
                }
            }
        }
    }

    /** 1 inside [box], falling smoothly to 0 over [SMALL_FACE_FEATHER_PX] outside it. */
    private fun featherWeight(x: Int, y: Int, box: Rect): Float {
        val dx = maxOf(box.left - x, x - (box.right - 1), 0)
        val dy = maxOf(box.top - y, y - (box.bottom - 1), 0)
        val distance = maxOf(dx, dy)
        if (distance == 0) return 1f
        if (distance >= SMALL_FACE_FEATHER_PX) return 0f
        // Cosine window: linear blending leaves visible seams in gradients (§6.6).
        val t = distance.toFloat() / SMALL_FACE_FEATHER_PX
        return (0.5f * (1f + cos(PI * t).toFloat()))
    }

    /**
     * Cosine seam window for tiled resamplers (§6.6).
     *
     * Exposed here because any tiled [SuperResolver] needs it and the reason is
     * specific: linear blending across a tile seam leaves a visible crease in
     * smooth gradients, because the derivative is discontinuous at both ends of
     * the ramp. A raised cosine is smooth at both.
     */
    fun cosineSeamWeight(position: Int, overlap: Int): Float {
        if (overlap <= 0) return 1f
        val t = (position.toFloat() / overlap).coerceIn(0f, 1f)
        return 0.5f * (1f - cos(PI * t).toFloat())
    }
}
