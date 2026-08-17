package dev.cue.capture

/**
 * A rectangle in screen pixels, origin top-left. Mirrors what ML Kit returns
 * as `Rect`, without depending on Android for it — §4.2's attribution is the
 * logic most worth testing and least worth needing a device to test.
 */
data class BoundingBox(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
}

/** One text block as the recogniser found it. */
data class TextBlock(
    val text: String,
    val bounds: BoundingBox,
)

/**
 * One screenshot's recognised text.
 *
 * The image itself never reaches this type. §10: OCR'd on-device, image
 * discarded immediately, never stored — so the pipeline's input is text and
 * geometry, and the only place a bitmap exists is inside the recogniser call.
 */
data class RecognizedScreen(
    val id: String,
    val screenWidth: Int,
    val screenHeight: Int,
    val blocks: List<TextBlock>,
    val capturedAt: Long,
)

/**
 * The bubble-alignment convention for one app.
 *
 * §13: when attribution is ambiguous, ask which side is you and *remember it
 * per platform*. That memory needs somewhere to live, and hardcoding
 * "right means me" leaves nowhere to record the answer.
 */
data class ChatLayout(
    val myMessagesAlignedRight: Boolean = true,
    /**
     * §4.2: "a block whose right edge is within 15% of screen width is yours".
     * The same margin serves the opposite edge for her messages.
     */
    val edgeMarginFraction: Float = 0.15f,
) {
    companion object {
        /** Both apps, out of the box. Hinge and Tinder agree on this. */
        val DEFAULT = ChatLayout()
    }
}
