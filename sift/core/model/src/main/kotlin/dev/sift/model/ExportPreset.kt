package dev.sift.model

import kotlinx.serialization.Serializable

/**
 * Export presets (§10). All derived from the graded master.
 *
 * [sharpenRadiusPx] is not stored as a constant per preset — it is quoted here
 * for reference and recomputed at export as `outputLongEdge / 1200` (§6.10),
 * which reproduces the table's 0.9px at 1080 and scales for the master.
 */
@Serializable
enum class ExportPreset(
    val displayName: String,
    val targetWidth: Int?,
    val targetHeight: Int?,
    val jpegQuality: Int,
) {
    STORY("Story", 1080, 1920, 92),
    FEED_4_5("Feed 4:5", 1080, 1350, 92),
    FEED_1_1("Feed 1:1", 1080, 1080, 92),

    /** Source or upscaled, capped at [MASTER_LONG_EDGE_CAP]. */
    MASTER("Master", null, null, 95),
    ;

    val isFixedSize: Boolean get() = targetWidth != null && targetHeight != null

    fun targetSize(): Size? =
        if (targetWidth != null && targetHeight != null) Size(targetWidth, targetHeight) else null

    companion object {
        /** §6.6 — hard cap on output long edge. */
        const val MASTER_LONG_EDGE_CAP = 6000

        /** §6.10 — output sharpen radius is sized to the output, not the source. */
        fun sharpenRadiusFor(outputLongEdge: Int): Float = outputLongEdge / 1200f
    }
}
