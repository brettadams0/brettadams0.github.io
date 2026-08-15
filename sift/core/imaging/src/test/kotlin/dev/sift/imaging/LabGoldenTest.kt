package dev.sift.imaging

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.random.Random

/**
 * §14.1 / trap #1 — the LAB scaling golden test.
 *
 * §6.2 calls this out as the one failure that "silently produces garbage that
 * still looks like a plausible image". The spec's intended fixture is three
 * portraits with committed Python outputs; that fixture is user-supplied and its
 * slot lives in `:core:testing`. What is asserted here is stronger in one
 * specific way and available now: the conversion is pinned to *published*
 * CIELAB reference values rather than to another implementation, so it cannot
 * drift in lockstep with a mistaken reference.
 *
 * Reference values are the standard sRGB/D65 → CIELAB figures (Lindbloom).
 */
class LabGoldenTest {

    private val tolerance = 0.02f

    private fun assertLab(
        expectedL: Float,
        expectedA: Float,
        expectedB: Float,
        actual: FloatArray,
        label: String,
    ) {
        assertEquals(expectedL, actual[0], tolerance, "$label L*")
        assertEquals(expectedA, actual[1], tolerance, "$label a*")
        assertEquals(expectedB, actual[2], tolerance, "$label b*")
    }

    @Test
    @DisplayName("sRGB primaries land on published CIELAB values")
    fun primaries() {
        assertLab(53.2408f, 80.0925f, 67.2032f, ColorSpaces.srgb8ToLab(255, 0, 0), "red")
        assertLab(87.7347f, -86.1827f, 83.1793f, ColorSpaces.srgb8ToLab(0, 255, 0), "green")
        assertLab(32.2970f, 79.1875f, -107.8602f, ColorSpaces.srgb8ToLab(0, 0, 255), "blue")
    }

    @Test
    @DisplayName("Neutrals are achromatic and L* spans 0..100")
    fun neutrals() {
        assertLab(100f, 0f, 0f, ColorSpaces.srgb8ToLab(255, 255, 255), "white")
        assertLab(0f, 0f, 0f, ColorSpaces.srgb8ToLab(0, 0, 0), "black")
        assertLab(53.5850f, 0f, 0f, ColorSpaces.srgb8ToLab(128, 128, 128), "mid grey")
    }

    /**
     * The trap itself, stated as an assertion.
     *
     * In OpenCV's 8-bit LAB a neutral grey has `a = b = 128` and `L = 137`.
     * If this codebase ever acquires an 8-bit LAB path, or someone ports a
     * `-128` offset in from a Python reference, these bounds fail loudly instead
     * of quietly shifting every skin correction by an entire colour axis.
     */
    @Test
    @DisplayName("LAB is true CIELAB, not the 8-bit scaled variety (trap #1)")
    fun notTheEightBitScaling() {
        val grey = ColorSpaces.srgb8ToLab(128, 128, 128)
        assertTrue(abs(grey[1]) < 1f, "a* of neutral grey is ${grey[1]}, expected ~0 not ~128")
        assertTrue(abs(grey[2]) < 1f, "b* of neutral grey is ${grey[2]}, expected ~0 not ~128")
        assertTrue(grey[0] in 50f..57f, "L* of mid grey is ${grey[0]}, expected ~53.6 not ~137")

        val white = ColorSpaces.srgb8ToLab(255, 255, 255)
        assertTrue(white[0] in 99f..101f, "L* of white is ${white[0]}, expected 100 not 255")

        // The §6.7 portrait target must be reachable in these units: a skin
        // patch measured by this converter has to land near L*68 / a*12.5 / b*17,
        // not near 128.
        val skinSwatch = ColorSpaces.srgb8ToLab(224, 172, 143)
        assertTrue(skinSwatch[0] in 55f..85f, "skin L* ${skinSwatch[0]} outside plausible range")
        assertTrue(skinSwatch[1] in 2f..30f, "skin a* ${skinSwatch[1]} outside plausible range")
        assertTrue(skinSwatch[2] in 5f..40f, "skin b* ${skinSwatch[2]} outside plausible range")
    }

    @Test
    @DisplayName("LAB round trip is lossless within float precision, including out of range")
    fun labRoundTrip() {
        val rng = Random(0x5117)
        repeat(2000) {
            // Deliberately unbounded: §2.1 permits values above 1.0 and highlight
            // reconstruction produces them.
            val r = rng.nextFloat() * 1.6f - 0.2f
            val g = rng.nextFloat() * 1.6f - 0.2f
            val b = rng.nextFloat() * 1.6f - 0.2f

            val lab = ColorSpaces.linearRgbToLab(r, g, b)
            val back = ColorSpaces.labToLinearRgb(lab[0], lab[1], lab[2])

            assertEquals(r, back[0], 1e-3f, "R round trip")
            assertEquals(g, back[1], 1e-3f, "G round trip")
            assertEquals(b, back[2], 1e-3f, "B round trip")
        }
    }

    @Test
    @DisplayName("sRGB transfer round trips and extends oddly through zero")
    fun transferRoundTrip() {
        val rng = Random(0x2A)
        repeat(2000) {
            val v = rng.nextFloat() * 3f - 1f
            val back = ColorSpaces.linearToSrgb(ColorSpaces.srgbToLinear(v))
            assertEquals(v, back, 1e-4f, "transfer round trip at $v")
        }
        // Odd symmetry: negatives must not collapse to zero or NaN, or a gamut
        // conversion followed by a tone curve silently eats shadow detail.
        assertEquals(-ColorSpaces.srgbToLinear(0.5f), ColorSpaces.srgbToLinear(-0.5f), 1e-6f)
        assertTrue(ColorSpaces.srgbToLinear(-0.5f) < 0f)
        assertTrue(!ColorSpaces.linearToSrgb(-0.3f).isNaN())
    }

    @Test
    @DisplayName("Whole-image conversion agrees with the per-pixel path and retags the buffer")
    fun wholeImageMatchesPerPixel() {
        val rng = Random(7)
        val w = 32
        val h = 16
        val data = FloatArray(w * h * 3) { rng.nextFloat() }
        val image = FloatImage(w, h, ColorSpaceTag.GAMMA_SRGB, data.copyOf())

        ColorSpaces.toLinear(image)
        assertEquals(ColorSpaceTag.LINEAR_SRGB, image.space)
        ColorSpaces.linearToLab(image)
        assertEquals(ColorSpaceTag.LAB, image.space)

        for (p in 0 until w * h) {
            val i = p * 3
            val expected = ColorSpaces.linearRgbToLab(
                ColorSpaces.srgbToLinear(data[i]),
                ColorSpaces.srgbToLinear(data[i + 1]),
                ColorSpaces.srgbToLinear(data[i + 2]),
            )
            assertEquals(expected[0], image.data[i], 1e-3f)
            assertEquals(expected[1], image.data[i + 1], 1e-3f)
            assertEquals(expected[2], image.data[i + 2], 1e-3f)
        }

        ColorSpaces.labToLinear(image)
        ColorSpaces.toGamma(image)
        assertEquals(ColorSpaceTag.GAMMA_SRGB, image.space)
        for (i in data.indices) {
            assertEquals(data[i], image.data[i], 1e-3f, "full round trip at $i")
        }
    }

    /**
     * §2.2 is enforced by the type system, not by convention. Attempting a
     * linear-light operation on gamma-encoded data is the source of trap #3.
     */
    @Test
    @DisplayName("Operating in the wrong space throws rather than producing muddy output")
    fun spaceTagIsEnforced() {
        val image = FloatImage.alloc(4, 4, ColorSpaceTag.GAMMA_SRGB)
        val error = runCatching { ColorSpaces.linearToLab(image) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException, "expected a space violation, got $error")
        assertTrue(error!!.message!!.contains("§2.2"))
    }

    @Test
    @DisplayName("Display P3 soft-clip preserves gradient in saturated reds instead of flattening")
    fun p3SoftClipKeepsGradient() {
        // A ramp of increasingly saturated P3 reds. Under a hard clamp every one
        // of these lands on exactly (1,0,0) and the ramp becomes a flat block.
        val n = 24
        val image = FloatImage.alloc(n, 1, ColorSpaceTag.LINEAR_SRGB)
        for (x in 0 until n) {
            val t = x / (n - 1f)
            image.data[x * 3] = 0.55f + 0.45f * t
            image.data[x * 3 + 1] = 0.02f * (1f - t)
            image.data[x * 3 + 2] = 0.02f * (1f - t)
        }

        ColorSpaces.displayP3ToSrgb(image)

        // Everything must be inside sRGB after mapping.
        for (v in image.data) {
            assertTrue(v >= -1e-3f && v <= 1f + 1e-3f, "sample $v escaped sRGB")
        }

        // And the ramp must still be a ramp: consecutive steps in the upper half
        // stay distinguishable rather than collapsing onto the boundary.
        var distinctSteps = 0
        for (x in n / 2 until n - 1) {
            val a = ColorSpaces.linearRgbToLab(
                image.data[x * 3], image.data[x * 3 + 1], image.data[x * 3 + 2],
            )
            val b = ColorSpaces.linearRgbToLab(
                image.data[(x + 1) * 3], image.data[(x + 1) * 3 + 1], image.data[(x + 1) * 3 + 2],
            )
            val deltaC = abs(ColorSpaces.chroma(a[1], a[2]) - ColorSpaces.chroma(b[1], b[2]))
            val deltaL = abs(a[0] - b[0])
            if (deltaC + deltaL > 0.05f) distinctSteps++
        }
        assertTrue(
            distinctSteps >= (n / 2 - 2),
            "$distinctSteps of ${n / 2 - 1} steps survived the gamut map; " +
                "the saturated end is flattening into a featureless block",
        )
    }
}
