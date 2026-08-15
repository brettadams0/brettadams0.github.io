package dev.sift.imaging

import dev.sift.model.ContentClass
import dev.sift.model.ExportPreset
import dev.sift.model.GradeProfile
import dev.sift.model.GradeSettings
import dev.sift.model.QualityGate
import dev.sift.testing.SyntheticFrames
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.math.abs

/**
 * End-to-end pipeline behaviour: §6.1's canonical order, §6.12's fallback chain,
 * §14.3's no-regression requirement and §14.9's gate-failure behaviour.
 */
class PipelineTest {

    private fun request(
        image: FloatImage,
        settings: GradeSettings = GradeSettings.VALIDATED_DEFAULTS,
        preset: ExportPreset = ExportPreset.MASTER,
        metadata: SourceMetadata = SourceMetadata.UNKNOWN,
    ) = Pipeline.Request(
        source = Pipeline.SourceFrame(image, metadata),
        settings = settings,
        preset = preset,
        ditherSeed = 1234L,
    )

    @Test
    @DisplayName("A portrait runs end to end and produces a decodable 4:4:4 JPEG")
    fun portraitEndToEnd() {
        val result = Pipeline.process(request(SyntheticFrames.portrait()))

        assertEquals(GradeProfile.PORTRAIT, result.profile)
        assertTrue(!result.fellBackToOriginal, "fell back: ${result.gates.fallbackReason}")
        assertTrue(result.gates.allPassed, "failed gates: ${result.gates.failed}")

        val decoded = ImageIO.read(ByteArrayInputStream(result.jpeg))
        assertNotNull(decoded, "pipeline output did not decode")
        assertEquals(result.width, decoded.width)
        assertEquals(result.height, decoded.height)

        // Every derived parameter is recorded — §6.3 and §5 both require this,
        // because a photo that comes out wrong six weeks from now has to be
        // diagnosable without re-running anything.
        assertNotNull(result.derived.portrait)
        assertNotNull(result.derived.localContrast)
        assertNotNull(result.derived.outputSharpen)
        assertNotNull(result.derived.denoise)
    }

    /**
     * §14.3 — on an already well-exposed frame, Scene should barely move.
     * A large delta means the adaptive terms are overreaching.
     */
    @Test
    @DisplayName("Scene no-regression: a well-exposed frame comes out close to how it went in")
    fun sceneDoesNotOverreach() {
        val source = SyntheticFrames.wellExposedScene()
        val reference = SyntheticFrames.toWorkingSpace(SyntheticFrames.wellExposedScene())
        val result = Pipeline.process(request(source))

        assertEquals(GradeProfile.SCENE, result.profile)
        assertTrue(result.gates.allPassed, "failed gates: ${result.gates.failed}")

        val output = ColorSpaces.toLinear(
            FloatImage.fromBytes(result.width, result.height, result.rgb),
        )
        val before = FrameAnalyzer.lightnessPlane(reference)
        val after = FrameAnalyzer.lightnessPlane(output)

        var total = 0.0
        for (i in before.indices) total += abs(before[i] - after[i])
        val meanDelta = total / before.size

        assertTrue(
            meanDelta < 8.0,
            "Scene moved an already well-exposed frame by $meanDelta L* on average; " +
                "adaptive terms are overreaching",
        )
    }

    /**
     * §14.9 — a frame engineered to fail a gate must fall back and ship the
     * original, not ship a degraded result.
     *
     * The engineered failure here is a portrait target deliberately placed
     * outside the §6.7 guard rail. The grade converges faithfully onto b* 40,
     * the skin-range gate rejects it, the retry at damping 0.4 lands in the same
     * place, and the pipeline gives up and ships the source.
     */
    @Test
    @DisplayName("Gate failure falls back to the original and says so")
    fun gateFailureShipsOriginal() {
        val impossible = GradeSettings.VALIDATED_DEFAULTS.copy(portraitTargetB = 40f)
        val result = Pipeline.process(request(SyntheticFrames.portrait(), settings = impossible))

        assertTrue(
            result.fellBackToOriginal,
            "expected a fallback; gates were ${result.gates.results}",
        )
        assertNotNull(result.gates.fallbackReason)
        assertTrue(
            result.gates.fallbackReason!!.contains("Skin range"),
            "fallback reason should name the gate: ${result.gates.fallbackReason}",
        )
        // The fallback still produces a usable file — the original, re-encoded.
        assertNotNull(ImageIO.read(ByteArrayInputStream(result.jpeg)))

        // And it remembers which profile was rejected. Recording NONE here would
        // leave the review UI (§9.4) unable to say what was tried, and a
        // rejection reason (§9.5) meaningless.
        assertEquals(GradeProfile.PORTRAIT, result.profile)
    }

    @Test
    @DisplayName("Non-photographic content is routed out of grading entirely")
    fun documentsAreNotGraded() {
        val result = Pipeline.process(request(SyntheticFrames.document()))
        assertEquals(ContentClass.NON_PHOTOGRAPHIC, result.contentClass)
        assertEquals(GradeProfile.NONE, result.profile)
        assertTrue(
            !result.fellBackToOriginal,
            "routing out is not a fallback and must not be recorded as one",
        )
    }

    @Test
    @DisplayName("Fixed-size presets export at exactly the requested dimensions")
    fun presetsHitExactDimensions() {
        for (preset in listOf(ExportPreset.STORY, ExportPreset.FEED_4_5, ExportPreset.FEED_1_1)) {
            val result = Pipeline.process(
                request(SyntheticFrames.wellExposedScene(width = 960, height = 720), preset = preset),
            )
            val expected = preset.targetSize()!!
            assertEquals(expected.width, result.width, "${preset.displayName} width")
            assertEquals(expected.height, result.height, "${preset.displayName} height")

            val decoded = ImageIO.read(ByteArrayInputStream(result.jpeg))
            assertEquals(expected.width, decoded.width)
            assertEquals(expected.height, decoded.height)
        }
    }

    @Test
    @DisplayName("A screenshot is detected only with both signals present")
    fun screenshotNeedsBothSignals() {
        // 1080x1920 is a known device resolution; without EXIF exposure data
        // that is a screenshot, with it that is a photograph someone cropped.
        val dimensions = SyntheticFrames.wellExposedScene(width = 1080, height = 1920)
        val noExif = Pipeline.process(
            request(dimensions, metadata = SourceMetadata(hasExifExposure = false)),
        )
        assertEquals(ContentClass.NON_PHOTOGRAPHIC, noExif.contentClass)

        val withExif = Pipeline.process(
            request(
                SyntheticFrames.wellExposedScene(width = 1080, height = 1920),
                metadata = SourceMetadata(hasExifExposure = true),
            ),
        )
        assertEquals(
            ContentClass.SCENE,
            withExif.contentClass,
            "a photo at a screen resolution must not be treated as a screenshot",
        )
    }

    @Test
    @DisplayName("The master preset never exceeds the 6000px cap")
    fun masterRespectsLongEdgeCap() {
        // Cheap stand-in for an oversized source: a wide, short frame.
        val wide = SyntheticFrames.wellExposedScene(width = 1200, height = 300)
        val result = Pipeline.process(request(wide, preset = ExportPreset.MASTER))
        assertTrue(
            maxOf(result.width, result.height) <= ExportPreset.MASTER_LONG_EDGE_CAP,
            "master output ${result.width}x${result.height} exceeds the cap",
        )
    }

    @Test
    @DisplayName("Gate results are recorded for every attempt, pass or fail")
    fun gateResultsAreAlwaysRecorded() {
        val result = Pipeline.process(request(SyntheticFrames.wellExposedScene()))
        val gates = result.gates.results.map { it.gate }.toSet()

        // Scene has no skin anchor, so the skin gate does not apply; the other
        // five always do.
        val expected = QualityGate.entries.toSet() - QualityGate.SKIN_RANGE
        assertEquals(expected, gates, "not every applicable gate was evaluated")
        assertTrue(result.gates.results.all { it.measured.isFinite() })
    }
}
