package dev.sift.imaging

import dev.sift.model.ContentClass
import dev.sift.model.GradeSettings
import dev.sift.testing.SyntheticFrames
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.math.abs

/**
 * §6.7 and §14.2 — the skin-anchored portrait grade and its guard rail.
 */
class PortraitGradeTest {

    private val settings = GradeSettings.VALIDATED_DEFAULTS

    private fun gradedSkin(
        skinRgb: Triple<Int, Int, Int>,
        settingsOverride: GradeSettings = settings,
    ): Pair<Triple<Float, Float, Float>, dev.sift.model.DerivedParams.PortraitParams> {
        val image = SyntheticFrames.toWorkingSpace(SyntheticFrames.portrait(skinRgb = skinRgb))
        val analysis = FrameAnalyzer.analyze(image)
        val result = PortraitGrade.apply(image, analysis, settingsOverride)

        // Re-measure the graded frame the same way the gate does.
        val after = FrameAnalyzer.analyze(image)
        val measured = after.skinMedianLab
        assertNotNull(measured, "graded frame lost its skin mask entirely")
        return measured!! to result.params
    }

    @Test
    @DisplayName("A warm subject converges onto the §6.7 target in at most 6 iterations")
    fun convergesOnTarget() {
        val (_, params) = gradedSkin(Triple(232, 160, 120))

        assertTrue(
            params.iterations <= GradeSettings.MAX_PORTRAIT_ITERATIONS,
            "ran ${params.iterations} iterations, spec caps at 6",
        )
        assertTrue(params.converged, "did not converge; final skin was ${params.finalSkinLabString()}")

        // Damped at 0.7 the residual after n passes is 0.3^n of the initial
        // error, so three or four passes is the expected behaviour (§6.7).
        assertTrue(
            params.iterations in 1..4,
            "expected 3-4 passes with damping 0.7, got ${params.iterations}",
        )

        assertEquals(settings.portraitTargetL, params.finalSkinL, 0.5f, "final skin L*")
        assertEquals(settings.portraitTargetA, params.finalSkinA, 0.5f, "final skin a*")
        assertEquals(settings.portraitTargetB, params.finalSkinB, 0.5f, "final skin b*")
    }

    /**
     * §14.2 — the guard rail. Across a spread of starting skin tones, every
     * graded output lands with `b* ∈ [15,22]`. Zero silent violations.
     */
    @Test
    @DisplayName("Guard rail: graded skin b* lands in [15,22] across a spread of subjects")
    fun guardRailHolds() {
        val subjects = listOf(
            Triple(240, 200, 180), // very fair
            Triple(232, 160, 120), // warm
            Triple(222, 168, 140), // neutral fair
            Triple(198, 140, 110),
            Triple(168, 118, 92),
            Triple(140, 96, 74), // deeper
            Triple(206, 156, 148), // cool / pink
            Triple(214, 176, 132), // yellow-leaning
        )

        val violations = mutableListOf<String>()
        for (subject in subjects) {
            val (_, params) = gradedSkin(subject)
            val b = params.finalSkinB
            if (b < GradeSettings.SKIN_B_GUARD_MIN || b > GradeSettings.SKIN_B_GUARD_MAX) {
                violations += "$subject -> b* $b"
            }
        }
        assertTrue(violations.isEmpty(), "guard-rail violations: $violations")
    }

    /**
     * The single most important constraint in the document (§6.7).
     *
     * A portrait shot against a strongly coloured wall must receive **one**
     * correction, derived from skin. If a grey-world pass were also running, the
     * wall would drag the correction and the skin would end up cooled twice —
     * the failure that drove b* to about 7 and produced an ill, grey subject.
     * The test states it as an outcome: the skin lands on target regardless of
     * what colour the background is.
     */
    @Test
    @DisplayName("No second white balance: background colour does not shift the skin result")
    fun backgroundDoesNotDoubleCorrect() {
        val skin = Triple(226, 166, 134)
        val neutralBackground = Triple(96, 96, 96)
        val warmBackground = Triple(150, 96, 60) // terracotta wall
        val coolBackground = Triple(60, 96, 150)

        val results = listOf(neutralBackground, warmBackground, coolBackground).map { background ->
            val image = SyntheticFrames.toWorkingSpace(
                SyntheticFrames.portrait(skinRgb = skin, backgroundRgb = background),
            )
            val analysis = FrameAnalyzer.analyze(image)
            PortraitGrade.apply(image, analysis, settings).params
        }

        for (params in results) {
            assertEquals(settings.portraitTargetB, params.finalSkinB, 0.6f, "skin b* moved with background")
        }
        val spread = results.maxOf { it.finalSkinB } - results.minOf { it.finalSkinB }
        assertTrue(
            spread < 0.5f,
            "skin b* varied by $spread across background colours; " +
                "something other than the skin measurement is influencing the correction",
        )
    }

    @Test
    @DisplayName("The correction is global, not masked: background moves by the same delta as skin")
    fun correctionIsAppliedGlobally() {
        val image = SyntheticFrames.toWorkingSpace(
            SyntheticFrames.portrait(skinRgb = Triple(232, 160, 120)),
        )
        val before = image.copy()
        val analysis = FrameAnalyzer.analyze(image)
        val result = PortraitGrade.apply(image, analysis, settings)

        // Sample a background pixel far from the face and confirm it moved by
        // the same LAB delta the skin did. If the correction were applied
        // through the mask this delta would be zero and the frame would blotch
        // at the mask boundary.
        ColorSpaces.linearToLab(before)
        val after = image.copy()
        ColorSpaces.linearToLab(after)

        val corner = before.index(4, before.height - 5)
        val deltaA = after.data[corner + 1] - before.data[corner + 1]
        val deltaB = after.data[corner + 2] - before.data[corner + 2]

        assertEquals(result.params.appliedDeltaA, deltaA, 0.05f, "background a* delta")
        assertEquals(result.params.appliedDeltaB, deltaB, 0.05f, "background b* delta")
    }

    @Test
    @DisplayName("Reduced-strength regrade moves the frame roughly half as far (§9.5)")
    fun reducedStrengthHalvesTheMove() {
        val full = gradedSkin(Triple(232, 160, 120), settings).second
        val half = gradedSkin(Triple(232, 160, 120), settings.scaled(0.5f)).second

        assertEquals(full.appliedDeltaB * 0.5f, half.appliedDeltaB, 0.05f)
        assertTrue(
            abs(half.finalSkinB - full.finalSkinB) > 0.5f,
            "reduced strength produced the same result as full strength",
        )
    }

    @Test
    @DisplayName("A portrait fixture routes to PORTRAIT and a document does not")
    fun routerAgreesWithFixtures() {
        val portrait = SyntheticFrames.toWorkingSpace(SyntheticFrames.portrait())
        assertEquals(ContentClass.PORTRAIT, FrameAnalyzer.analyze(portrait).route())

        val document = SyntheticFrames.toWorkingSpace(SyntheticFrames.document())
        val documentAnalysis = FrameAnalyzer.analyze(document)
        assertEquals(
            ContentClass.NON_PHOTOGRAPHIC,
            documentAnalysis.route(),
            "document scored edgeDensity=${documentAnalysis.edgeDensity} " +
                "meanChroma=${documentAnalysis.meanChroma}",
        )

        val scene = SyntheticFrames.toWorkingSpace(SyntheticFrames.wellExposedScene())
        assertEquals(ContentClass.SCENE, FrameAnalyzer.analyze(scene).route())
    }
}

private fun dev.sift.model.DerivedParams.PortraitParams.finalSkinLabString() =
    "L*%.2f a*%.2f b*%.2f".format(finalSkinL, finalSkinA, finalSkinB)
