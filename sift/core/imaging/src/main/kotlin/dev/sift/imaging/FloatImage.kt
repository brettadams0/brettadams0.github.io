package dev.sift.imaging

import dev.sift.model.Size

/**
 * Which space the samples in a [FloatImage] currently live in.
 *
 * §2.2 is the single most commonly botched thing in an imaging pipeline —
 * resizing gamma-encoded data darkens edges, tone curves in linear space crush
 * midtones, sharpening chroma fringes every edge. Tagging the buffer turns
 * "getting it backwards" from a subtle visual bug into a `require()` failure.
 */
enum class ColorSpaceTag {
    /** Linear light. Resize, blur, blend, upscale belong here. */
    LINEAR_SRGB,

    /** Gamma-encoded sRGB. Tone curves, levels and gamma belong here. */
    GAMMA_SRGB,

    /** True CIELAB, D65. `L* ∈ [0,100]`, `a*, b*` centred on zero. */
    LAB,
}

/**
 * The working image format: **unbounded 32-bit float, 3 interleaved channels**
 * (§2.1). The direct equivalent of the spec's `CV_32FC3`.
 *
 * Values may exceed 1.0 and may go negative during processing. Highlight
 * reconstruction (§6.8) deliberately pushes channels above 1.0 and the shoulder
 * brings them back; clamping mid-pipeline would destroy exactly the detail that
 * stage exists to recover. Quantisation to 8 bits happens exactly once, at
 * encode (§6.11).
 *
 * Operations mutate [data] in place and update [space] where that avoids an
 * allocation — a 12MP buffer is ~144MB (§4.3) and gratuitous copies are how a
 * batch of 200 photos runs the heap out. Anything that needs the previous state
 * takes an explicit [copy].
 */
class FloatImage(
    val width: Int,
    val height: Int,
    space: ColorSpaceTag,
    val data: FloatArray,
) {
    var space: ColorSpaceTag = space
        internal set

    init {
        require(width > 0 && height > 0) { "empty image ${width}x$height" }
        require(data.size == width * height * CHANNELS) {
            "buffer is ${data.size}, expected ${width * height * CHANNELS} for ${width}x$height"
        }
    }

    val pixelCount: Int get() = width * height
    val longEdge: Int get() = maxOf(width, height)
    val shortEdge: Int get() = minOf(width, height)
    val size: Size get() = Size(width, height)

    /** Index of the first channel of pixel (x, y). */
    fun index(x: Int, y: Int): Int = (y * width + x) * CHANNELS

    fun copy(): FloatImage = FloatImage(width, height, space, data.copyOf())

    /** An all-zero buffer of the same geometry, tagged [newSpace]. */
    fun likeThis(newSpace: ColorSpaceTag = space): FloatImage =
        FloatImage(width, height, newSpace, FloatArray(data.size))

    fun requireSpace(expected: ColorSpaceTag, operation: String) {
        require(space == expected) {
            "$operation must run in $expected but the buffer is in $space (§2.2)"
        }
    }

    /** Extract one channel into a fresh planar array. */
    fun channel(c: Int, into: FloatArray = FloatArray(pixelCount)): FloatArray {
        require(c in 0 until CHANNELS)
        require(into.size == pixelCount)
        var src = c
        for (i in 0 until pixelCount) {
            into[i] = data[src]
            src += CHANNELS
        }
        return into
    }

    /** Write a planar array back into channel [c]. */
    fun setChannel(c: Int, plane: FloatArray) {
        require(c in 0 until CHANNELS)
        require(plane.size == pixelCount)
        var dst = c
        for (i in 0 until pixelCount) {
            data[dst] = plane[i]
            dst += CHANNELS
        }
    }

    companion object {
        const val CHANNELS = 3

        fun alloc(width: Int, height: Int, space: ColorSpaceTag): FloatImage =
            FloatImage(width, height, space, FloatArray(width * height * CHANNELS))

        /**
         * Promote 8-bit samples to float, dividing by 255 — the *only* place
         * 8-bit data enters the pipeline (§6.1 steps 1–4). The result is tagged
         * [ColorSpaceTag.GAMMA_SRGB]; linearise before doing anything physical
         * with it.
         */
        fun fromBytes(width: Int, height: Int, rgb: ByteArray): FloatImage {
            require(rgb.size == width * height * CHANNELS) {
                "byte buffer is ${rgb.size}, expected ${width * height * CHANNELS}"
            }
            val out = FloatArray(rgb.size)
            for (i in rgb.indices) {
                out[i] = (rgb[i].toInt() and 0xFF) / 255f
            }
            return FloatImage(width, height, ColorSpaceTag.GAMMA_SRGB, out)
        }

        /** Same, from packed 0xAARRGGBB ints (the Android `ARGB_8888` layout). */
        fun fromArgb(width: Int, height: Int, argb: IntArray): FloatImage {
            require(argb.size == width * height)
            val out = FloatArray(argb.size * CHANNELS)
            var d = 0
            for (p in argb) {
                out[d++] = ((p ushr 16) and 0xFF) / 255f
                out[d++] = ((p ushr 8) and 0xFF) / 255f
                out[d++] = (p and 0xFF) / 255f
            }
            return FloatImage(width, height, ColorSpaceTag.GAMMA_SRGB, out)
        }
    }
}

/**
 * Conversion audit trail (§2.2: "Log the conversions in debug builds so you can
 * audit them"). Off by default; the pipeline turns it on when
 * [dev.sift.model.GradeSettings.dumpDebugJson] is set.
 */
object ConversionLog {
    @Volatile
    var enabled: Boolean = false

    private val entries = ArrayDeque<String>()

    fun record(from: ColorSpaceTag, to: ColorSpaceTag, stage: String) {
        if (!enabled) return
        synchronized(entries) {
            entries.addLast("$stage: $from -> $to")
            while (entries.size > MAX_ENTRIES) entries.removeFirst()
        }
    }

    fun drain(): List<String> = synchronized(entries) {
        val copy = entries.toList()
        entries.clear()
        copy
    }

    private const val MAX_ENTRIES = 512
}
