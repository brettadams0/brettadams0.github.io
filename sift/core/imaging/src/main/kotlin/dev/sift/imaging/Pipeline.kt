package dev.sift.imaging

import dev.sift.model.ContentClass
import dev.sift.model.DerivedParams
import dev.sift.model.ExportPreset
import dev.sift.model.FrameAnalysis
import dev.sift.model.GateReport
import dev.sift.model.GradeProfile
import dev.sift.model.GradeSettings
import dev.sift.model.QualityGate
import dev.sift.model.Rect
import dev.sift.model.Size

/**
 * The canonical pipeline (§6.1).
 *
 * The order is not negotiable and each step's position is load-bearing:
 *
 * ```
 *  1. Decode                     caller's job — see SourceFrame
 *  2. Bake EXIF orientation      move it later and every crop is wrong
 *  3. Colour space normalise     to linear sRGB float
 *  4. Promote to float           move it later and you quantise repeatedly
 *  5. ANALYSE                    one measurement pass, drives everything after
 *  6. Content routing            portrait / scene / skip
 *  7. Denoise                    after tone lift and you amplify noise
 *  8. Upscale                    after grading and the model sees data it wasn't trained on
 *  9. Grade                      the profile-specific work
 * 10. Local contrast             before grading and the tone curve fights it
 * 11. Resize to output           in linear light, always
 * 12. Output sharpen             before resize and it's the wrong radius
 * 13. Quantise + dither          exactly once, here
 * 14. Encode + metadata          4:4:4, EXIF copied by the caller
 * 15. Quality gates              verify, fall back if failed
 * ```
 *
 * Steps 1, 2 and the EXIF half of 14 need platform APIs and live in the Android
 * layer; everything else is here, which is what makes the module that decides
 * output quality testable without a device (§4.1).
 */
object Pipeline {

    /** A decoded source frame, oriented, ready for step 3. */
    data class SourceFrame(
        /** Gamma-encoded sRGB (or Display P3 — see [SourceMetadata.isDisplayP3]). */
        val image: FloatImage,
        val metadata: SourceMetadata = SourceMetadata.UNKNOWN,
    )

    data class Request(
        val source: SourceFrame,
        val settings: GradeSettings = GradeSettings.VALIDATED_DEFAULTS,
        val preset: ExportPreset = ExportPreset.MASTER,
        /** Set when the user overrode the router; recorded as `profileWasManual`. */
        val forcedProfile: GradeProfile? = null,
        val faceDetector: FaceDetector = FaceDetector.None,
        val resolver: Upscale.SuperResolver = Upscale.LanczosBaseline,
        /** Makes dither reproducible; real exports pass the asset id. */
        val ditherSeed: Long = 0L,
    )

    data class Result(
        val jpeg: ByteArray,
        val rgb: ByteArray,
        val width: Int,
        val height: Int,
        val profile: GradeProfile,
        val contentClass: ContentClass,
        val analysisBefore: FrameAnalysis,
        val analysisAfter: FrameAnalysis,
        val derived: DerivedParams,
        val gates: GateReport,
        val processingMs: Long,
    ) {
        val fellBackToOriginal: Boolean get() = gates.fellBackToOriginal

        override fun equals(other: Any?): Boolean = this === other
        override fun hashCode(): Int = System.identityHashCode(this)
    }

    /** §6.12 allows one retry per remedy; three attempts covers the chain. */
    const val MAX_ATTEMPTS = 3

    fun process(request: Request): Result {
        val started = System.nanoTime()

        // ---- 3-4. Normalise to unbounded linear float ---------------------
        val linear = ColorSpaces.toLinear(request.source.image.copy())
        if (request.source.metadata.isDisplayP3) {
            // Perceptual soft-clip, never a hard clamp — see §6.2 and
            // ColorSpaces.displayP3ToSrgb.
            ColorSpaces.displayP3ToSrgb(linear)
        }

        // ---- 5. Analyse ----------------------------------------------------
        val analysis = FrameAnalyzer.analyze(linear, request.source.metadata, request.faceDetector)

        // ---- 6. Route ------------------------------------------------------
        val contentClass = analysis.route()
        val profile = resolveProfile(contentClass, request.settings, request.forcedProfile)

        // Non-photographic content is a large fraction of any real camera roll
        // and grading it is nonsense (§6.4). It still gets exported at the
        // requested preset so the rest of the app has one code path.
        if (profile == GradeProfile.NONE) {
            return exportWithoutGrading(
                linear = linear,
                analysis = analysis,
                request = request,
                contentClass = contentClass,
                attemptedProfile = GradeProfile.NONE,
                reason = "non-photographic content routed out of grading",
                fellBack = false,
                started = started,
            )
        }

        var attempt = 0
        var damping = GradeSettings.PORTRAIT_DAMPING
        var toneScale = 1f
        var denoiseScale = 1f
        var lastFailure: String? = null

        while (attempt < MAX_ATTEMPTS) {
            attempt++
            val outcome = runAttempt(
                linear = linear,
                analysis = analysis,
                request = request,
                profile = profile,
                contentClass = contentClass,
                damping = damping,
                toneScale = toneScale,
                denoiseScale = denoiseScale,
            )

            val failures = outcome.gateResults.filter { !it.passed }
            if (failures.isEmpty()) {
                return outcome.toResult(
                    gates = GateReport(outcome.gateResults, attempt, fellBackToOriginal = false),
                    processingMs = (System.nanoTime() - started) / 1_000_000,
                )
            }

            // §6.12's fallback chain. Take the most severe remedy the failures
            // demand; SHIP_ORIGINAL short-circuits because no retry helps.
            val remedies = failures.map { QualityGates.remedyFor(it.gate) }.toSet()
            lastFailure = failures.joinToString(", ") {
                "${it.gate.displayName} (${it.measured} vs ${it.threshold})"
            }

            if (QualityGates.Remedy.SHIP_ORIGINAL in remedies || attempt >= MAX_ATTEMPTS) {
                break
            }

            var adjusted = false
            if (QualityGates.Remedy.RETRY_LOWER_DAMPING in remedies &&
                damping != GradeSettings.PORTRAIT_DAMPING_RETRY
            ) {
                damping = GradeSettings.PORTRAIT_DAMPING_RETRY
                adjusted = true
            }
            if (QualityGates.Remedy.RETRY_SOFTER_TONE in remedies && toneScale > 0.5f) {
                toneScale = 0.5f
                adjusted = true
            }
            if (QualityGates.Remedy.RETRY_LESS_DENOISE in remedies && denoiseScale > 0.5f) {
                denoiseScale = 0.5f
                adjusted = true
            }
            // Nothing left to turn down: retrying identically would fail identically.
            if (!adjusted) break
        }

        // ---- Ship the original. A degraded photo is worse than an
        //      unprocessed one (§0 rule 3), and `fellBackToOriginal` is what
        //      stops §9.3 from ever trashing this asset's source.
        return exportWithoutGrading(
            linear = linear,
            analysis = analysis,
            request = request,
            contentClass = contentClass,
            attemptedProfile = profile,
            reason = "quality gate failed after $attempt attempts: $lastFailure",
            fellBack = true,
            started = started,
        )
    }

    // ---- Internals ---------------------------------------------------------

    private fun resolveProfile(
        contentClass: ContentClass,
        settings: GradeSettings,
        forced: GradeProfile?,
    ): GradeProfile {
        if (forced != null) return forced
        return when (settings.routing) {
            GradeSettings.RoutingMode.OFF -> GradeProfile.NONE
            GradeSettings.RoutingMode.FORCE_PORTRAIT -> GradeProfile.PORTRAIT
            GradeSettings.RoutingMode.FORCE_SCENE -> GradeProfile.SCENE
            GradeSettings.RoutingMode.AUTO -> GradeProfile.forContentClass(contentClass)
        }
    }

    private class Attempt(
        val jpeg: ByteArray,
        val rgb: ByteArray,
        val width: Int,
        val height: Int,
        val profile: GradeProfile,
        val contentClass: ContentClass,
        val analysisBefore: FrameAnalysis,
        val analysisAfter: FrameAnalysis,
        val derived: DerivedParams,
        val gateResults: List<dev.sift.model.GateResult>,
    ) {
        fun toResult(gates: GateReport, processingMs: Long) = Result(
            jpeg = jpeg,
            rgb = rgb,
            width = width,
            height = height,
            profile = profile,
            contentClass = contentClass,
            analysisBefore = analysisBefore,
            analysisAfter = analysisAfter,
            derived = derived,
            gates = gates,
            processingMs = processingMs,
        )
    }

    private fun runAttempt(
        linear: FloatImage,
        analysis: FrameAnalysis,
        request: Request,
        profile: GradeProfile,
        contentClass: ContentClass,
        damping: Float,
        toneScale: Float,
        denoiseScale: Float,
    ): Attempt {
        val settings = request.settings
        val work = linear.copy()

        // ---- 7. Denoise (before anything amplifies what it would leave) ----
        val denoiseSettings = if (denoiseScale == 1f) settings else settings.scaled(denoiseScale)
        val denoiseParams = Denoise.apply(
            work,
            if (denoiseScale == 1f) {
                analysis
            } else {
                // A softer retry means less denoise, expressed the only honest
                // way: as a smaller measured sigma feeding the same derivation.
                analysis.copy(
                    noiseSigmaLuma = analysis.noiseSigmaLuma * denoiseScale,
                    noiseSigmaChroma = analysis.noiseSigmaChroma * denoiseScale,
                )
            },
        )

        // ---- 8. Upscale (before grading: a model trained on ordinary photos
        //         should not be shown graded ones) --------------------------
        val targetLongEdge = targetLongEdgeFor(request.preset, analysis)
        val decision = Upscale.decide(analysis, targetLongEdge, settings.upscale)
        val (upscaled, upscaleParams) =
            Upscale.apply(work, decision, analysis, settings, request.resolver)

        // ---- 9. Grade ------------------------------------------------------
        var portraitParams: DerivedParams.PortraitParams? = null
        var sceneParams: DerivedParams.SceneParams? = null
        when (profile) {
            GradeProfile.PORTRAIT -> {
                portraitParams =
                    PortraitGrade.apply(upscaled, analysis, settings, damping, toneScale).params
            }
            GradeProfile.SCENE -> {
                sceneParams = SceneGrade.apply(upscaled, analysis, settings, toneScale)
            }
            GradeProfile.NONE -> Unit
        }

        // ---- 10. Local contrast --------------------------------------------
        val localContrastParams = LocalContrast.apply(upscaled, analysis, settings.strengthScale)

        // ---- 11. Resize to output, in linear light -------------------------
        val outputSize = outputSizeFor(request.preset, upscaled.size)
        val cropped = cropFor(request.preset, upscaled, analysis, decision.effectiveFactor)
        val resized = Resample.resize(cropped, outputSize.width, outputSize.height)

        // ---- 12. Output sharpen, sized to the output -----------------------
        val sharpenParams = OutputSharpen.apply(
            resized,
            analysis.noiseSigmaLuma,
            settings.strengthScale,
        )

        // Like-for-like sharpness reference: the untouched source put through
        // the same geometry. Comparing a 1080px export against a 12MP original
        // would fail every downscaled preset for reasons unrelated to quality.
        //
        // When the output has the source's geometry — the common case for the
        // master preset — that reference *is* the source, and its P90 was
        // already measured by the analysis pass. Rebuilding it meant a redundant
        // full-frame copy, resize and Laplacian per photo.
        val sameGeometry = outputSize.width == linear.width && outputSize.height == linear.height
        val sharpnessBefore = if (sameGeometry) {
            analysis.laplacianVarianceP90
        } else {
            FrameAnalyzer.sharpnessP90(
                Resample.resize(
                    cropFor(request.preset, linear.copy(), analysis, 1f),
                    outputSize.width,
                    outputSize.height,
                ),
            )
        }
        val sharpnessAfter = FrameAnalyzer.sharpnessP90(resized)

        // ---- 13. Quantise + dither, exactly once ---------------------------
        val rgb = Quantize.toBytes(resized.copy(), request.ditherSeed)

        // ---- 14. Encode ----------------------------------------------------
        val jpeg = JpegEncoder.encode(rgb, outputSize.width, outputSize.height, request.preset.jpegQuality)

        // ---- 15. Gates -----------------------------------------------------
        val outputLinear = ColorSpaces.toLinear(
            FloatImage.fromBytes(outputSize.width, outputSize.height, rgb),
        )
        // Gate-only measurement. The gates need three figures — clipped
        // highlights, crushed shadows and mean chroma — and a full analysis pass
        // additionally computes sharpness tiles, noise sigmas, edge density, the
        // skin mask and document detection, none of which any gate reads.
        val analysisAfter = FrameAnalyzer.analyzeForGates(outputLinear)
        val banding = QualityGates.bandingScore(rgb, outputSize.width, outputSize.height)

        val gateResults = QualityGates.evaluate(
            before = analysis,
            after = analysisAfter,
            profile = profile,
            finalSkinB = portraitParams?.finalSkinB,
            sharpnessBefore = sharpnessBefore,
            sharpnessAfter = sharpnessAfter,
            bandingScore = banding,
        )

        return Attempt(
            jpeg = jpeg,
            rgb = rgb,
            width = outputSize.width,
            height = outputSize.height,
            profile = profile,
            contentClass = contentClass,
            analysisBefore = analysis,
            analysisAfter = analysisAfter,
            derived = DerivedParams(
                profile = profile,
                contentClass = contentClass,
                strengthScale = denoiseSettings.strengthScale,
                denoise = denoiseParams,
                portrait = portraitParams,
                scene = sceneParams,
                localContrast = localContrastParams,
                resize = DerivedParams.ResizeParams(
                    fromWidth = cropped.width,
                    fromHeight = cropped.height,
                    toWidth = outputSize.width,
                    toHeight = outputSize.height,
                    method = if (outputSize.width <= cropped.width) "area" else "lanczos4",
                ),
                outputSharpen = sharpenParams,
                upscale = upscaleParams,
            ),
            gateResults = gateResults,
        )
    }

    /**
     * Export without grading: non-photographic content, or the §6.12 fallback.
     *
     * In the fallback case the result is a re-encode of the source. §9.3
     * invariant 2 exists precisely because of this: trashing the original of a
     * fallback would leave a generation-loss JPEG as the only master, which is
     * the one genuinely unrecoverable bug in the app (trap #14).
     */
    private fun exportWithoutGrading(
        linear: FloatImage,
        analysis: FrameAnalysis,
        request: Request,
        contentClass: ContentClass,
        /**
         * The profile that was *attempted*. On a fallback this is the profile
         * whose output was rejected, not `NONE` — the review UI (§9.4) has to
         * show which grade was tried, and a rejection reason (§9.5) is
         * meaningless without knowing what produced it.
         */
        attemptedProfile: GradeProfile,
        reason: String,
        fellBack: Boolean,
        started: Long,
    ): Result {
        val cropped = cropFor(request.preset, linear.copy(), analysis, 1f)
        val outputSize = outputSizeFor(request.preset, cropped.size)
        val resized = Resample.resize(cropped, outputSize.width, outputSize.height)
        val rgb = Quantize.toBytes(resized, request.ditherSeed)
        val jpeg = JpegEncoder.encode(rgb, outputSize.width, outputSize.height, request.preset.jpegQuality)

        val outputLinear = ColorSpaces.toLinear(
            FloatImage.fromBytes(outputSize.width, outputSize.height, rgb),
        )
        val analysisAfter = FrameAnalyzer.analyze(outputLinear, request.source.metadata)

        return Result(
            jpeg = jpeg,
            rgb = rgb,
            width = outputSize.width,
            height = outputSize.height,
            profile = attemptedProfile,
            contentClass = contentClass,
            analysisBefore = analysis,
            analysisAfter = analysisAfter,
            derived = DerivedParams(
                profile = attemptedProfile,
                contentClass = contentClass,
                strengthScale = request.settings.strengthScale,
            ),
            gates = if (fellBack) {
                GateReport.fallback(reason)
            } else {
                GateReport(emptyList(), attempts = 1, fellBackToOriginal = false, fallbackReason = reason)
            },
            processingMs = (System.nanoTime() - started) / 1_000_000,
        )
    }

    private fun targetLongEdgeFor(preset: ExportPreset, analysis: FrameAnalysis): Int =
        preset.targetSize()?.longEdge
            ?: minOf(analysis.sourceLongEdge, ExportPreset.MASTER_LONG_EDGE_CAP)

    private fun outputSizeFor(preset: ExportPreset, sourceSize: Size): Size {
        val target = preset.targetSize()
        if (target != null) return target
        // Master: source geometry, capped at 6000px on the long edge (§6.6).
        val longEdge = sourceSize.longEdge
        if (longEdge <= ExportPreset.MASTER_LONG_EDGE_CAP) return sourceSize
        val scale = ExportPreset.MASTER_LONG_EDGE_CAP.toFloat() / longEdge
        return Size(
            (sourceSize.width * scale).toInt().coerceAtLeast(1),
            (sourceSize.height * scale).toInt().coerceAtLeast(1),
        )
    }

    /**
     * Crop to the preset's aspect, biased by faces (§10).
     *
     * With no faces the crop is centre-weighted. With faces it is biased to keep
     * them inside the frame and *off* the exact centre — rule-of-thirds placement
     * on the dominant face, which is what stops every portrait export looking
     * like a passport photo.
     */
    private fun cropFor(
        preset: ExportPreset,
        image: FloatImage,
        analysis: FrameAnalysis,
        upscaleFactor: Float,
    ): FloatImage {
        val target = preset.targetSize() ?: return image
        val dominant = analysis.faceBoxes.maxByOrNull { it.area }

        if (dominant == null) {
            return Resample.cropToAspect(image, target.width, target.height)
        }

        val scaled: Rect = if (upscaleFactor != 1f) dominant.scaled(upscaleFactor) else dominant
        val focusX = (scaled.centerX / image.width).coerceIn(0f, 1f)
        val faceCenterY = (scaled.centerY / image.height).coerceIn(0f, 1f)
        // Pull the face toward the upper third rather than dead centre.
        val focusY = (faceCenterY - (RULE_OF_THIRDS - 0.5f)).coerceIn(0f, 1f)

        return Resample.cropToAspect(image, target.width, target.height, focusX, focusY)
    }

    private const val RULE_OF_THIRDS = 1f / 3f

    /** Convenience for callers that only want the gate verdict names. */
    fun failedGateNames(report: GateReport): List<QualityGate> = report.failed.map { it.gate }
}
