package dev.sift.testing

import dev.sift.imaging.ColorSpaceTag
import dev.sift.imaging.ColorSpaces
import dev.sift.imaging.FloatImage
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Synthetic frames with known properties, for the tests in §14.
 *
 * These are not a substitute for the real fixtures §14.1 asks for — three
 * portraits with committed Python outputs, which only the author's own photos
 * can supply, and whose drop-in slot is [GoldenFixtures]. What they *can* do is
 * exercise every property the spec states numerically: a frame engineered to
 * fail one specific gate, a gradient smooth enough that undithered quantisation
 * bands visibly, a subject whose skin sits at a known distance from target.
 *
 * Every generator returns **gamma-encoded sRGB**, the way a decoder would, so
 * tests exercise the same linearisation the pipeline does.
 */
object SyntheticFrames {

    /**
     * A smooth horizontal luminance ramp — the banding fixture of §14.6.
     *
     * Spanning a narrow range at low amplitude is deliberate: a full 0→1 ramp
     * across 1024px has a real step every four pixels and would band even with
     * dither. A shallow ramp is the case where the 8-bit grid is coarser than
     * the signal, which is what dither exists to fix.
     */
    fun smoothGradient(
        width: Int = 512,
        height: Int = 256,
        from: Float = 0.45f,
        to: Float = 0.55f,
    ): FloatImage {
        val image = FloatImage.alloc(width, height, ColorSpaceTag.GAMMA_SRGB)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val t = x / (width - 1f)
                val v = from + (to - from) * t
                val i = image.index(x, y)
                image.data[i] = v
                image.data[i + 1] = v
                image.data[i + 2] = v
            }
        }
        return image
    }

    /**
     * A portrait: a skin-toned ellipse on a neutral background, with gentle
     * shading so the skin has a distribution rather than a single value.
     *
     * [skinRgb] sets where the subject's skin actually sits, so a test can place
     * it a known distance from the §6.7 target and assert the grade closes that
     * distance.
     */
    fun portrait(
        width: Int = 480,
        height: Int = 640,
        skinRgb: Triple<Int, Int, Int> = Triple(222, 168, 140),
        backgroundRgb: Triple<Int, Int, Int> = Triple(90, 92, 96),
        faceRadiusFraction: Float = 0.34f,
        seed: Int = 11,
    ): FloatImage {
        val image = FloatImage.alloc(width, height, ColorSpaceTag.GAMMA_SRGB)
        val rng = Random(seed)
        val cx = width / 2f
        val cy = height * 0.42f
        val rx = width * faceRadiusFraction
        val ry = height * faceRadiusFraction * 0.95f

        for (y in 0 until height) {
            for (x in 0 until width) {
                val nx = (x - cx) / rx
                val ny = (y - cy) / ry
                val inside = nx * nx + ny * ny <= 1f
                val i = image.index(x, y)

                // A little shading so the skin median is a genuine median.
                val shade = 1f + 0.10f * cos(nx * PI.toFloat() * 0.5f) - 0.05f * ny
                val jitter = (rng.nextFloat() - 0.5f) * 0.008f

                val rgb = if (inside) skinRgb else backgroundRgb
                val scale = if (inside) shade else 1f
                image.data[i] = ((rgb.first / 255f) * scale + jitter).coerceIn(0f, 1f)
                image.data[i + 1] = ((rgb.second / 255f) * scale + jitter).coerceIn(0f, 1f)
                image.data[i + 2] = ((rgb.third / 255f) * scale + jitter).coerceIn(0f, 1f)
            }
        }
        return image
    }

    /**
     * A well-exposed outdoor scene: sky gradient, ground, and enough structure
     * to have a real edge density. The §14.3 no-regression fixture.
     *
     * The tonal range matters as much as the content here. §14.3 asserts that
     * Scene barely moves a frame that is *already good*, so the fixture has to
     * actually be good: a near-black in the shaded ground, a near-white in the
     * bright sky, and a healthy spread between them. A fixture confined to the
     * midtones would be a low-contrast frame, and Scene stretching it hard would
     * be correct behaviour rather than the overreach the test is looking for.
     */
    fun wellExposedScene(width: Int = 480, height: Int = 360, seed: Int = 5): FloatImage {
        val image = FloatImage.alloc(width, height, ColorSpaceTag.GAMMA_SRGB)
        val rng = Random(seed)
        val horizon = (height * 0.55f).toInt()

        for (y in 0 until height) {
            for (x in 0 until width) {
                val i = image.index(x, y)
                if (y < horizon) {
                    // Sky: bright, reaching close to but not into clipping.
                    val t = y / horizon.toFloat()
                    val cloud = 0.10f * sin(x * 0.06f) * cos(y * 0.09f)
                    image.data[i] = (0.62f + 0.30f * (1f - t) + cloud).coerceIn(0f, 0.97f)
                    image.data[i + 1] = (0.68f + 0.27f * (1f - t) + cloud).coerceIn(0f, 0.97f)
                    image.data[i + 2] = (0.80f + 0.17f * (1f - t) + cloud).coerceIn(0f, 0.97f)
                } else {
                    // Ground: falls into deep shadow at the bottom of the frame.
                    val t = (y - horizon) / (height - horizon).toFloat()
                    val texture = 0.07f * sin(x * 0.35f) * cos(y * 0.27f) +
                        (rng.nextFloat() - 0.5f) * 0.03f
                    image.data[i] = (0.46f - 0.42f * t + texture).coerceIn(0.01f, 1f)
                    image.data[i + 1] = (0.50f - 0.45f * t + texture).coerceIn(0.01f, 1f)
                    image.data[i + 2] = (0.34f - 0.30f * t + texture).coerceIn(0.01f, 1f)
                }
            }
        }
        return image
    }

    /** A scene with a blown sky: one channel clips well before the others. */
    fun blownHighlightScene(width: Int = 320, height: Int = 240): FloatImage {
        val image = FloatImage.alloc(width, height, ColorSpaceTag.GAMMA_SRGB)
        val horizon = (height * 0.4f).toInt()
        for (y in 0 until height) {
            for (x in 0 until width) {
                val i = image.index(x, y)
                if (y < horizon) {
                    // Blue clips hard, red and green retain detail — the exact
                    // case per-channel reconstruction exists for (§6.8.1).
                    val t = x / (width - 1f)
                    image.data[i] = 0.86f + 0.06f * t
                    image.data[i + 1] = 0.90f + 0.05f * t
                    image.data[i + 2] = 1.0f
                } else {
                    image.data[i] = 0.22f
                    image.data[i + 1] = 0.26f
                    image.data[i + 2] = 0.20f
                }
            }
        }
        return image
    }

    /** A dark frame with crushed shadows, for the shadow-lift path. */
    fun crushedShadowScene(width: Int = 320, height: Int = 240): FloatImage {
        val image = FloatImage.alloc(width, height, ColorSpaceTag.GAMMA_SRGB)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val i = image.index(x, y)
                val t = y / (height - 1f)
                val v = if (t < 0.5f) 0.004f else 0.10f + 0.35f * (t - 0.5f)
                image.data[i] = v
                image.data[i + 1] = v * 0.97f
                image.data[i + 2] = v * 0.92f
            }
        }
        return image
    }

    /** A document: high edge density, near-zero chroma, bimodal luminance. */
    fun document(width: Int = 400, height: Int = 520): FloatImage {
        val image = FloatImage.alloc(width, height, ColorSpaceTag.GAMMA_SRGB)
        for (i in image.data.indices) image.data[i] = 0.94f
        // Text lines.
        var y = 40
        while (y < height - 40) {
            for (row in y until (y + 6).coerceAtMost(height)) {
                var x = 40
                while (x < width - 40) {
                    val wordLength = 8 + (x / 7) % 30
                    for (col in x until (x + wordLength).coerceAtMost(width - 40)) {
                        val i = image.index(col, row)
                        image.data[i] = 0.06f
                        image.data[i + 1] = 0.06f
                        image.data[i + 2] = 0.06f
                    }
                    x += wordLength + 7
                }
            }
            y += 16
        }
        return image
    }

    /**
     * Add zero-mean Gaussian noise in gamma space, in 8-bit units.
     *
     * [sigma8] is in levels-of-255, so `sigma8 = 3f` is roughly the point §6.5
     * starts caring about.
     */
    fun withNoise(image: FloatImage, sigma8: Float, seed: Int = 99): FloatImage {
        val rng = Random(seed)
        val sigma = sigma8 / 255f
        for (i in image.data.indices) {
            // Box-Muller, one sample per call is fine at fixture scale.
            val u1 = (rng.nextFloat() + 1e-7f).coerceAtMost(1f)
            val u2 = rng.nextFloat()
            val g = kotlin.math.sqrt(-2f * kotlin.math.ln(u1)) * cos(2f * PI.toFloat() * u2)
            image.data[i] = (image.data[i] + g * sigma).coerceIn(0f, 1f)
        }
        return image
    }

    /** Convert a fixture to the linear working space the pipeline expects. */
    fun toWorkingSpace(image: FloatImage): FloatImage = ColorSpaces.toLinear(image)
}

/**
 * Slot for the §14.1 golden-image parity fixtures.
 *
 * §14.1 gates M3 on three portraits whose Python outputs are committed here,
 * asserting a per-channel mean delta below 1.0. Those files are the author's own
 * photographs and cannot be synthesised — dropping them into
 * `core/testing/src/main/resources/golden/` and listing them here turns the test
 * on.
 *
 * Until then `GoldenParityTest` reports itself as skipped rather than passing
 * vacuously, because a parity test that silently passes with no fixtures is
 * worse than no parity test at all.
 */
object GoldenFixtures {
    const val RESOURCE_ROOT = "/golden"

    /** `sourceName to expectedOutputName`, both under [RESOURCE_ROOT]. */
    val portraitPairs: List<Pair<String, String>> = emptyList()

    fun isPopulated(): Boolean = portraitPairs.isNotEmpty()
}
