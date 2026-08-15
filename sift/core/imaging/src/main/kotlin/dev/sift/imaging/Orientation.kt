package dev.sift.imaging

/**
 * EXIF orientation, baked into pixels (§6.1 step 2).
 *
 * This runs **second**, immediately after decode and before anything measures
 * or crops. Trap #2: bake it late and every crop is wrong — face boxes land on
 * the wrong side of the frame, a 4:5 crop takes the wrong half, and the error is
 * invisible in a thumbnail and glaring at full size.
 *
 * Once applied, the exported file records `TAG_ORIENTATION = 1` (§6.11 step 4),
 * because the rotation now lives in the pixels and a viewer applying it again
 * would rotate twice.
 */
object Orientation {

    /** The eight EXIF orientation values. */
    const val NORMAL = 1
    const val FLIP_HORIZONTAL = 2
    const val ROTATE_180 = 3
    const val FLIP_VERTICAL = 4
    const val TRANSPOSE = 5
    const val ROTATE_90 = 6
    const val TRANSVERSE = 7
    const val ROTATE_270 = 8

    /**
     * Return [image] with EXIF [orientation] applied. Returns the same instance
     * for [NORMAL] or an unrecognised value — an unknown orientation tag is not
     * a reason to fail a frame (§12: never fail a frame over metadata).
     */
    fun bake(image: FloatImage, orientation: Int): FloatImage = when (orientation) {
        NORMAL -> image
        FLIP_HORIZONTAL -> remap(image, image.width, image.height) { x, y -> (image.width - 1 - x) to y }
        ROTATE_180 -> remap(image, image.width, image.height) { x, y ->
            (image.width - 1 - x) to (image.height - 1 - y)
        }
        FLIP_VERTICAL -> remap(image, image.width, image.height) { x, y -> x to (image.height - 1 - y) }
        TRANSPOSE -> remap(image, image.height, image.width) { x, y -> y to x }
        ROTATE_90 -> remap(image, image.height, image.width) { x, y -> y to (image.height - 1 - x) }
        TRANSVERSE -> remap(image, image.height, image.width) { x, y ->
            (image.width - 1 - y) to (image.height - 1 - x)
        }
        ROTATE_270 -> remap(image, image.height, image.width) { x, y -> (image.width - 1 - y) to x }
        else -> image
    }

    /** True when [orientation] swaps width and height. */
    fun swapsAxes(orientation: Int): Boolean =
        orientation == TRANSPOSE || orientation == ROTATE_90 ||
            orientation == TRANSVERSE || orientation == ROTATE_270

    /**
     * [source] maps a destination coordinate to the source coordinate it reads
     * from, in the *original* image's coordinate space.
     */
    private inline fun remap(
        image: FloatImage,
        outWidth: Int,
        outHeight: Int,
        source: (Int, Int) -> Pair<Int, Int>,
    ): FloatImage {
        val out = FloatImage.alloc(outWidth, outHeight, image.space)
        for (y in 0 until outHeight) {
            for (x in 0 until outWidth) {
                val (sx, sy) = source(x, y)
                val s = image.index(sx.coerceIn(0, image.width - 1), sy.coerceIn(0, image.height - 1))
                val d = out.index(x, y)
                out.data[d] = image.data[s]
                out.data[d + 1] = image.data[s + 1]
                out.data[d + 2] = image.data[s + 2]
            }
        }
        return out
    }
}
