package dev.sift.model

import kotlinx.serialization.Serializable
import kotlin.math.max
import kotlin.math.min

/**
 * Integer rectangle in pixel coordinates. Duplicated rather than reusing
 * `android.graphics.Rect` so that :core:model and :core:imaging stay free of
 * Android dependencies (§4.1) and testable on a plain JVM.
 */
@Serializable
data class Rect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val area: Int get() = max(0, width) * max(0, height)
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f

    fun intersect(other: Rect): Rect = Rect(
        left = max(left, other.left),
        top = max(top, other.top),
        right = min(right, other.right),
        bottom = min(bottom, other.bottom),
    )

    fun scaled(factor: Float): Rect = Rect(
        left = (left * factor).toInt(),
        top = (top * factor).toInt(),
        right = (right * factor).toInt(),
        bottom = (bottom * factor).toInt(),
    )

    /** Grow by [px] on every side, clamped into a [w] x [h] frame. */
    fun inflate(px: Int, w: Int, h: Int): Rect = Rect(
        left = max(0, left - px),
        top = max(0, top - px),
        right = min(w, right + px),
        bottom = min(h, bottom + px),
    )
}

@Serializable
data class Size(val width: Int, val height: Int) {
    val longEdge: Int get() = max(width, height)
    val shortEdge: Int get() = min(width, height)
    val pixels: Long get() = width.toLong() * height.toLong()
    val aspect: Float get() = width.toFloat() / height.toFloat()
}
