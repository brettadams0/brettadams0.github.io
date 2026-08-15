package dev.sift.imaging

import dev.sift.model.Rect
import dev.sift.model.Size

/**
 * Face detection, abstracted so `:core:imaging` stays a pure-JVM module (§4.1).
 *
 * §6.3 prefers OpenCV's bundled YuNet ONNX detector over adding an ML Kit
 * dependency. That implementation belongs in `:core:ml`, behind this interface,
 * for two reasons: it keeps ONNX Runtime out of the module that has to be unit
 * testable without a device, and it means the pipeline has a defined behaviour
 * when no detector is available.
 *
 * **The router degrades rather than breaking without one.** §6.4's portrait
 * term is `(faceCount > 0 || skinFraction > 0.08)`; with [None] the face count
 * is zero and the skin-fraction term carries the decision alone. Faces are a
 * second guard on the terracotta failure (trap #13), not the only guard.
 */
fun interface FaceDetector {

    /**
     * Detect faces in [proxy], a gamma-encoded sRGB image that may be smaller
     * than the frame being analysed.
     *
     * Boxes must be returned in **[fullSize] coordinates**, not proxy
     * coordinates — callers use them for crop biasing (§10) and for the
     * conservative small-face upscale treatment (§6.6), both of which operate on
     * the full-resolution frame.
     */
    fun detect(proxy: FloatImage, fullSize: Size): List<Rect>

    companion object {
        /** No detector wired up. See the class note on graceful degradation. */
        val None = FaceDetector { _, _ -> emptyList() }
    }
}

/**
 * What the pipeline knows about the source file beyond its pixels.
 *
 * [hasExifExposure] is half of the screenshot test in §6.3, which requires
 * **both** conditions — matching a device resolution *and* carrying no capture
 * metadata. Either one alone misfires: plenty of real photographs are exactly
 * 1080x2400 after a crop, and plenty of screenshots get resized.
 */
data class SourceMetadata(
    /** True when EXIF carries `TAG_F_NUMBER` or `TAG_EXPOSURE_TIME`. */
    val hasExifExposure: Boolean = true,
    /** True when the decoder reported Display P3 rather than sRGB. */
    val isDisplayP3: Boolean = false,
    val mimeType: String? = null,
) {
    companion object {
        val UNKNOWN = SourceMetadata()
    }
}

/**
 * Screen resolutions that indicate a screenshot when EXIF capture data is also
 * absent. Both orientations are matched.
 *
 * Aimed at the Samsung devices this app is built for, plus the common Android
 * and desktop sizes that end up in a camera roll via share sheets.
 */
object DeviceResolutions {
    private val known: Set<Long> = buildSet {
        fun add(w: Int, h: Int) {
            add(key(w, h))
            add(key(h, w))
        }
        // Samsung flagships (S-series, Note, Fold/Flip outer + inner)
        add(1440, 3120); add(1440, 3200); add(1440, 3088); add(1440, 2960)
        add(1080, 2340); add(1080, 2400); add(1080, 2316); add(1080, 2220)
        add(1080, 1920); add(1440, 2560); add(1768, 2208); add(1812, 2176)
        add(904, 2316); add(1080, 2640)
        // Common non-Samsung Android and tablets
        add(1084, 2412); add(1179, 2556); add(1290, 2796); add(1170, 2532)
        add(1200, 1920); add(1600, 2560); add(1848, 2960)
        // Desktop captures that arrive via share
        add(1920, 1080); add(2560, 1440); add(3840, 2160); add(1366, 768)
        add(2880, 1800); add(2560, 1600); add(3024, 1964)
    }

    private fun key(w: Int, h: Int): Long = (w.toLong() shl 32) or h.toLong()

    fun matches(width: Int, height: Int): Boolean = key(width, height) in known
}
