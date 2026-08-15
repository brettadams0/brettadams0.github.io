package dev.sift.imaging

import dev.sift.model.GradeProfile
import dev.sift.model.GradeSettings
import dev.sift.model.QualityGate
import dev.sift.testing.GoldenFixtures
import dev.sift.testing.SyntheticFrames
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * EXIF orientation (§6.1 step 2, trap #2) and the §14.1 golden-parity slot.
 */
class OrientationTest {

    private fun marked(): FloatImage {
        // A frame with a unique value in each corner, so a wrong rotation is
        // unambiguous rather than plausible.
        val image = FloatImage.alloc(4, 6, ColorSpaceTag.GAMMA_SRGB)
        for (y in 0 until 6) {
            for (x in 0 until 4) {
                val i = image.index(x, y)
                image.data[i] = x / 8f
                image.data[i + 1] = y / 8f
                image.data[i + 2] = 0.5f
            }
        }
        return image
    }

    private fun pixel(image: FloatImage, x: Int, y: Int): Triple<Float, Float, Float> {
        val i = image.index(x, y)
        return Triple(image.data[i], image.data[i + 1], image.data[i + 2])
    }

    @Test
    @DisplayName("Orientation 1 is a no-op and returns the same instance")
    fun normalIsIdentity() {
        val image = marked()
        assertTrue(Orientation.bake(image, Orientation.NORMAL) === image)
    }

    @Test
    @DisplayName("Rotations that swap axes produce swapped dimensions")
    fun swappedAxes() {
        val image = marked()
        for (orientation in listOf(
            Orientation.ROTATE_90, Orientation.ROTATE_270,
            Orientation.TRANSPOSE, Orientation.TRANSVERSE,
        )) {
            val rotated = Orientation.bake(marked(), orientation)
            assertEquals(image.height, rotated.width, "orientation $orientation width")
            assertEquals(image.width, rotated.height, "orientation $orientation height")
            assertTrue(Orientation.swapsAxes(orientation))
        }
    }

    @Test
    @DisplayName("Rotate 90 CW moves the source bottom-left corner to the destination top-left")
    fun rotate90IsClockwise() {
        val source = marked()
        val bottomLeft = pixel(source, 0, source.height - 1)
        val rotated = Orientation.bake(source, Orientation.ROTATE_90)
        assertEquals(bottomLeft, pixel(rotated, 0, 0))
    }

    @Test
    @DisplayName("Each rotation is invertible, so nothing is lost or mirrored by accident")
    fun rotationsRoundTrip() {
        val original = marked()
        val once = Orientation.bake(marked(), Orientation.ROTATE_90)
        val back = Orientation.bake(once, Orientation.ROTATE_270)
        assertEquals(original.width, back.width)
        assertEquals(original.height, back.height)
        for (i in original.data.indices) {
            assertEquals(original.data[i], back.data[i], 1e-6f, "sample $i survived the round trip")
        }
    }

    @Test
    @DisplayName("Flips are their own inverse")
    fun flipsAreInvolutions() {
        for (orientation in listOf(
            Orientation.FLIP_HORIZONTAL, Orientation.FLIP_VERTICAL, Orientation.ROTATE_180,
        )) {
            val original = marked()
            val twice = Orientation.bake(Orientation.bake(marked(), orientation), orientation)
            for (i in original.data.indices) {
                assertEquals(original.data[i], twice.data[i], 1e-6f, "orientation $orientation")
            }
        }
    }

    @Test
    @DisplayName("An unknown orientation tag is ignored, never fatal (§12)")
    fun unknownOrientationIsSurvivable() {
        val image = marked()
        assertTrue(Orientation.bake(image, 99) === image)
        assertTrue(Orientation.bake(image, 0) === image)
    }
}

/**
 * §14.1 — golden-image parity against the reference Python outputs.
 *
 * The fixtures are the author's own photographs and their committed Python
 * results; they cannot be synthesised. Until they are dropped into
 * `core/testing/src/main/resources/golden/` and listed in [GoldenFixtures],
 * this reports as **skipped**, not passed.
 *
 * That distinction is deliberate. A parity test that silently passes with no
 * fixtures present is worse than no parity test, because it reads as green on
 * the one gate §6.2 says is the only defence against the LAB scaling trap.
 * `LabGoldenTest` covers that trap independently by pinning the conversion to
 * published CIELAB reference values, so the trap is not unguarded in the
 * meantime — but this file is where end-to-end parity with the validated
 * pipeline gets proven.
 */
class GoldenParityTest {

    @Test
    @DisplayName("§14.1 portrait parity: per-channel mean delta below 1.0")
    fun parityWithReferenceOutputs() {
        assumeTrue(
            GoldenFixtures.isPopulated(),
            "No golden fixtures committed yet. Add source/expected portrait pairs to " +
                "core/testing/src/main/resources/golden/ and list them in GoldenFixtures " +
                "to enable the §14.1 M3 gate.",
        )

        for ((sourceName, expectedName) in GoldenFixtures.portraitPairs) {
            val source = loadResource(sourceName)
            val expected = loadResource(expectedName)

            val result = Pipeline.process(
                Pipeline.Request(
                    source = Pipeline.SourceFrame(source),
                    settings = GradeSettings.VALIDATED_DEFAULTS,
                    forcedProfile = GradeProfile.PORTRAIT,
                    ditherSeed = 0L,
                ),
            )

            assertEquals(expected.width, result.width, "$sourceName width")
            assertEquals(expected.height, result.height, "$sourceName height")

            val expectedBytes = Quantize.toBytes(expected, seed = 0L, dither = false)
            var total = 0.0
            for (i in expectedBytes.indices) {
                total += kotlin.math.abs(
                    (expectedBytes[i].toInt() and 0xFF) - (result.rgb[i].toInt() and 0xFF),
                )
            }
            val meanDelta = total / expectedBytes.size
            assertTrue(meanDelta < 1.0, "$sourceName mean per-channel delta $meanDelta exceeds 1.0")
        }
    }

    private fun loadResource(name: String): FloatImage {
        val stream = javaClass.getResourceAsStream("${GoldenFixtures.RESOURCE_ROOT}/$name")
            ?: error("golden fixture $name is listed but not present on the classpath")
        val decoded = javax.imageio.ImageIO.read(stream)
            ?: error("golden fixture $name could not be decoded")
        val argb = IntArray(decoded.width * decoded.height)
        decoded.getRGB(0, 0, decoded.width, decoded.height, argb, 0, decoded.width)
        return FloatImage.fromArgb(decoded.width, decoded.height, argb)
    }
}

/**
 * §14.9 — gate evaluation itself, exercised directly so each gate's threshold is
 * pinned independently of whether a synthetic frame happens to trip it.
 */
class GateEvaluationTest {

    private val baseline = SyntheticFrames.toWorkingSpace(SyntheticFrames.wellExposedScene())
        .let { FrameAnalyzer.analyze(it) }

    @Test
    @DisplayName("Each gate fails on its own threshold and maps to the §6.12 remedy")
    fun gatesFailIndependently() {
        // New clipping.
        val clipped = QualityGates.evaluate(
            before = baseline,
            after = baseline.copy(
                clippedHighlightFraction = baseline.clippedHighlightFraction + 0.01f,
            ),
            profile = GradeProfile.SCENE,
            finalSkinB = null,
            sharpnessBefore = 100f,
            sharpnessAfter = 100f,
            bandingScore = 0f,
        )
        assertTrue(clipped.first { it.gate == QualityGate.NO_NEW_CLIPPING }.passed.not())
        assertEquals(
            QualityGates.Remedy.RETRY_SOFTER_TONE,
            QualityGates.remedyFor(QualityGate.NO_NEW_CLIPPING),
        )

        // Shadow crush.
        val crushed = QualityGates.evaluate(
            baseline,
            baseline.copy(crushedShadowFraction = baseline.crushedShadowFraction + 0.01f),
            GradeProfile.SCENE, null, 100f, 100f, 0f,
        )
        assertTrue(crushed.first { it.gate == QualityGate.NO_SHADOW_CRUSH }.passed.not())

        // Sharpness: denoise ate the detail.
        val soft = QualityGates.evaluate(baseline, baseline, GradeProfile.SCENE, null, 100f, 70f, 0f)
        assertTrue(soft.first { it.gate == QualityGate.SHARPNESS_PRESERVED }.passed.not())
        assertEquals(
            QualityGates.Remedy.RETRY_LESS_DENOISE,
            QualityGates.remedyFor(QualityGate.SHARPNESS_PRESERVED),
        )

        // Banding: no retry helps, ship the original.
        val banded = QualityGates.evaluate(baseline, baseline, GradeProfile.SCENE, null, 100f, 100f, 0.95f)
        assertTrue(banded.first { it.gate == QualityGate.NO_BANDING }.passed.not())
        assertEquals(QualityGates.Remedy.SHIP_ORIGINAL, QualityGates.remedyFor(QualityGate.NO_BANDING))

        // Chroma sanity.
        val garish = QualityGates.evaluate(
            baseline,
            baseline.copy(meanChroma = baseline.meanChroma * 2f),
            GradeProfile.SCENE, null, 100f, 100f, 0f,
        )
        assertTrue(garish.first { it.gate == QualityGate.CHROMA_SANITY }.passed.not())
        assertEquals(QualityGates.Remedy.SHIP_ORIGINAL, QualityGates.remedyFor(QualityGate.CHROMA_SANITY))
    }

    @Test
    @DisplayName("The skin gate applies to portraits only and enforces b* in [15,22]")
    fun skinGateIsPortraitOnly() {
        val scene = QualityGates.evaluate(baseline, baseline, GradeProfile.SCENE, 40f, 100f, 100f, 0f)
        assertTrue(
            scene.none { it.gate == QualityGate.SKIN_RANGE },
            "the skin gate must not be applied to a scene",
        )

        for (b in listOf(9f, 14.9f, 22.1f, 40f)) {
            val failing = QualityGates.evaluate(
                baseline, baseline, GradeProfile.PORTRAIT, b, 100f, 100f, 0f,
            )
            assertTrue(
                failing.first { it.gate == QualityGate.SKIN_RANGE }.passed.not(),
                "skin b* $b should have failed the guard rail",
            )
        }
        for (b in listOf(15f, 17f, 22f)) {
            val passing = QualityGates.evaluate(
                baseline, baseline, GradeProfile.PORTRAIT, b, 100f, 100f, 0f,
            )
            assertTrue(
                passing.first { it.gate == QualityGate.SKIN_RANGE }.passed,
                "skin b* $b should have passed the guard rail",
            )
        }
        assertEquals(
            QualityGates.Remedy.RETRY_LOWER_DAMPING,
            QualityGates.remedyFor(QualityGate.SKIN_RANGE),
        )
    }
}
