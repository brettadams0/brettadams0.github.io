package dev.cue.capture

import dev.cue.model.Message
import dev.cue.model.Sender
import kotlin.math.max
import kotlin.math.min

data class StitchResult(
    val messages: List<Message>,
    val needsConfirmation: Boolean,
    val reason: String? = null,
    val headerName: String? = null,
)

/**
 * §5.1. Turns a handful of screenshots into one ordered conversation.
 *
 * Two problems, both boring and both fatal if skipped:
 *
 * **Bubbles are not blocks.** A three-line message often comes back as three
 * blocks. Left alone, that is three messages, and the voice profile learns that
 * you write four-word messages.
 *
 * **Screenshots overlap.** Nobody scrolls exactly one screen at a time, so
 * consecutive captures share their edges. §5.1 says to deduplicate by text
 * hash — done here as a suffix/prefix alignment rather than a global set, so
 * that genuinely repeated messages ("same", "same") survive while the overlap
 * does not.
 *
 * Ordering is by [RecognizedScreen.capturedAt] then by vertical position, and
 * the output carries `sequence` rather than timestamps. Trap 12: both apps show
 * relative times that OCR poorly, so the clock is not evidence and the scroll
 * order is.
 */
object ConversationStitcher {

    fun stitch(
        screens: List<RecognizedScreen>,
        conversationId: String,
        layout: ChatLayout = ChatLayout.DEFAULT,
    ): StitchResult {
        if (screens.isEmpty()) {
            return StitchResult(emptyList(), needsConfirmation = false, reason = "No screenshots")
        }

        val attribution = BubbleAttribution(layout)
        val ordered = screens.sortedWith(compareBy({ it.capturedAt }, { it.id }))

        var accumulated = emptyList<Bubble>()
        var needsConfirmation = false
        val reasons = mutableListOf<String>()

        ordered.forEach { screen ->
            val result = attribution.attribute(screen)
            if (result.needsConfirmation) {
                needsConfirmation = true
                result.reason?.let { reasons += "${screen.id}: $it" }
            }
            val bubbles = groupIntoBubbles(result.blocks, screen)
            accumulated = appendWithoutOverlap(accumulated, bubbles)
        }

        val messages = accumulated.mapIndexed { index, bubble ->
            Message(
                id = "$conversationId:$index",
                conversationId = conversationId,
                sender = bubble.sender,
                text = bubble.text,
                sentAt = null,
                sequence = index,
                attributionConfidence = bubble.confidence,
            )
        }

        return StitchResult(
            messages = messages,
            needsConfirmation = needsConfirmation,
            reason = reasons.takeIf { it.isNotEmpty() }?.joinToString("; "),
            headerName = ordered.firstNotNullOfOrNull { ScreenChrome.headerName(it) },
        )
    }

    private data class Bubble(
        val text: String,
        val sender: Sender,
        val confidence: Float,
    )

    /**
     * Merges vertically adjacent blocks from the same sender into one message.
     *
     * The horizontal-overlap requirement is what keeps two different bubbles
     * that happen to be close together from fusing: consecutive lines of one
     * bubble share almost all of their horizontal extent, and a reply from the
     * other side shares none of it.
     */
    private fun groupIntoBubbles(blocks: List<AttributedBlock>, screen: RecognizedScreen): List<Bubble> {
        val sorted = blocks.filter { it.text.isNotBlank() }.sortedBy { it.bounds.top }
        val bubbles = mutableListOf<MutableList<AttributedBlock>>()

        sorted.forEach { block ->
            val current = bubbles.lastOrNull()
            val previous = current?.last()
            if (previous != null && continuesBubble(previous, block, screen)) {
                current.add(block)
            } else {
                bubbles += mutableListOf(block)
            }
        }

        return bubbles.map { group ->
            Bubble(
                text = group.joinToString(" ") { it.text.trim() }.replace(Regex("""\s+"""), " ").trim(),
                sender = group.first().sender,
                // The least confident line decides. A bubble is only as well
                // attributed as its worst-placed fragment.
                confidence = group.minOf { it.confidence },
            )
        }
    }

    private fun continuesBubble(
        previous: AttributedBlock,
        next: AttributedBlock,
        screen: RecognizedScreen,
    ): Boolean {
        if (previous.sender != next.sender) return false

        val gap = next.bounds.top - previous.bounds.bottom
        val lineHeight = previous.bounds.height.coerceAtLeast(1)
        if (gap > max(MIN_GAP_PX, (lineHeight * MAX_GAP_LINES).toInt())) return false
        if (gap < -lineHeight) return false

        val overlap = min(previous.bounds.right, next.bounds.right) -
            max(previous.bounds.left, next.bounds.left)
        val narrower = min(previous.bounds.width, next.bounds.width).coerceAtLeast(1)
        if (overlap.toFloat() / narrower < MIN_HORIZONTAL_OVERLAP) return false

        // A bubble that reaches the composer band is not a bubble.
        return next.bounds.top <= screen.screenHeight
    }

    /**
     * Appends [incoming], dropping the part that repeats the tail of
     * [accumulated].
     *
     * Prefers the longest alignment, so a three-message overlap collapses in
     * one step rather than leaving two of the three behind. When no alignment
     * exists, the boundary case is a bubble the screenshot cut in half: the two
     * fragments are recognised by containment and the longer one wins.
     */
    private fun appendWithoutOverlap(accumulated: List<Bubble>, incoming: List<Bubble>): List<Bubble> {
        if (accumulated.isEmpty()) return incoming
        if (incoming.isEmpty()) return accumulated

        val maxOverlap = min(accumulated.size, incoming.size)
        for (k in maxOverlap downTo 1) {
            val tail = accumulated.takeLast(k)
            val head = incoming.take(k)
            if (tail.zip(head).all { (a, b) -> sameMessage(a, b) }) {
                return accumulated + incoming.drop(k)
            }
        }

        val last = accumulated.last()
        val first = incoming.first()
        if (last.sender == first.sender && isFragmentOf(last.text, first.text)) {
            val longer = if (last.text.length >= first.text.length) last else first
            return accumulated.dropLast(1) + longer + incoming.drop(1)
        }

        return accumulated + incoming
    }

    private fun sameMessage(a: Bubble, b: Bubble): Boolean =
        a.sender == b.sender && normalize(a.text) == normalize(b.text)

    /**
     * True when one string is a substantial trailing or leading part of the
     * other — the shape a bubble takes when a screenshot cuts through it.
     */
    private fun isFragmentOf(a: String, b: String): Boolean {
        val x = normalize(a)
        val y = normalize(b)
        if (x.isEmpty() || y.isEmpty()) return false
        val (shorter, longer) = if (x.length <= y.length) x to y else y to x
        if (shorter.length < MIN_FRAGMENT_CHARS) return false
        return longer.startsWith(shorter) || longer.endsWith(shorter)
    }

    private fun normalize(text: String): String =
        text.lowercase().replace(Regex("""[^\p{L}\p{N}]+"""), " ").trim()

    private const val MIN_GAP_PX = 8
    private const val MAX_GAP_LINES = 0.6f
    private const val MIN_HORIZONTAL_OVERLAP = 0.5f
    private const val MIN_FRAGMENT_CHARS = 12
}
