package dev.cue.capture

import dev.cue.capture.ChatFixtures.LabelledBubble
import dev.cue.model.Sender
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * §5.1 — multi-screenshot stitching, and the bubble reassembly that has to
 * happen first.
 */
class ConversationStitcherTest {

    private val conversation = listOf(
        LabelledBubble("what are you up to this weekend", Sender.THEM),
        LabelledBubble("i've been meaning to try that place for months honestly", Sender.ME),
        LabelledBubble("it was so much better than i expected, we stayed until they closed", Sender.THEM),
        LabelledBubble("that tracks", Sender.ME),
    )

    @Test
    fun `a wrapped message is one message, not one per line`() {
        val screen = ChatFixtures.screen("wrap", conversation)
        val result = ConversationStitcher.stitch(listOf(screen.screen), "c1")

        assertEquals(4, result.messages.size, result.messages.map { it.text }.toString())
        assertTrue(
            result.messages.any { it.text == "i've been meaning to try that place for months honestly" },
            result.messages.map { it.text }.toString(),
        )
    }

    @Test
    fun `ordering follows the scroll, not the clock`() {
        val screen = ChatFixtures.screen("order", conversation)
        val result = ConversationStitcher.stitch(listOf(screen.screen), "c1")

        assertEquals(conversation.map { it.sender }, result.messages.map { it.sender })
        assertEquals(listOf(0, 1, 2, 3), result.messages.map { it.sequence })
        // Trap 12: OCR'd relative times are not evidence, so nothing carries one.
        assertTrue(result.messages.all { it.sentAt == null })
    }

    @Test
    fun `overlapping screenshots are deduplicated`() {
        val first = ChatFixtures.screen("a", conversation.take(3), capturedAt = 1L)
        // The next capture repeats the last two bubbles, as a real scroll does.
        val second = ChatFixtures.screen("b", conversation.drop(1), capturedAt = 2L)

        val result = ConversationStitcher.stitch(listOf(first.screen, second.screen), "c1")

        assertEquals(4, result.messages.size, result.messages.map { it.text }.toString())
        assertEquals(conversation.map { it.text }, result.messages.map { it.text })
    }

    @Test
    fun `screenshots are ordered by capture time before stitching`() {
        val first = ChatFixtures.screen("a", conversation.take(2), capturedAt = 100L)
        val second = ChatFixtures.screen("b", conversation.drop(2), capturedAt = 200L)

        val reversed = ConversationStitcher.stitch(listOf(second.screen, first.screen), "c1")
        assertEquals(conversation.map { it.text }, reversed.messages.map { it.text })
    }

    /**
     * A genuinely repeated message survives. This is why the dedup is a
     * suffix/prefix alignment rather than a global set of text hashes: people
     * really do send "same" twice.
     */
    @Test
    fun `a repeated message is not treated as an overlap`() {
        val repeated = listOf(
            LabelledBubble("no shot", Sender.ME),
            LabelledBubble("no shot", Sender.ME),
        )
        val screen = ChatFixtures.screen("repeat", repeated)
        val result = ConversationStitcher.stitch(listOf(screen.screen), "c1")
        assertEquals(2, result.messages.size)
    }

    @Test
    fun `a bubble cut in half by the screen edge is rejoined once`() {
        val head = ChatFixtures.screen(
            "head",
            listOf(LabelledBubble("i've been meaning to try that place", Sender.ME)),
            capturedAt = 1L,
        )
        val whole = ChatFixtures.screen(
            "whole",
            listOf(LabelledBubble("i've been meaning to try that place for months honestly", Sender.ME)),
            capturedAt = 2L,
        )

        val result = ConversationStitcher.stitch(listOf(head.screen, whole.screen), "c1")
        assertEquals(1, result.messages.size, result.messages.map { it.text }.toString())
        assertEquals(
            "i've been meaning to try that place for months honestly",
            result.messages.single().text,
        )
    }

    @Test
    fun `senders are never merged into one bubble`() {
        val adjacent = listOf(
            LabelledBubble("that tracks", Sender.ME),
            LabelledBubble("haha ok fair", Sender.THEM),
        )
        val screen = ChatFixtures.screen("adjacent", adjacent)
        val result = ConversationStitcher.stitch(listOf(screen.screen), "c1")
        assertEquals(2, result.messages.size)
        assertEquals(listOf(Sender.ME, Sender.THEM), result.messages.map { it.sender })
    }

    @Test
    fun `the header name comes back for the conversation pseudonym`() {
        val screen = ChatFixtures.screen("named", conversation, headerName = "Priya")
        assertEquals("Priya", ConversationStitcher.stitch(listOf(screen.screen), "c1").headerName)
    }

    @Test
    fun `no screenshots is not an error`() {
        val result = ConversationStitcher.stitch(emptyList(), "c1")
        assertTrue(result.messages.isEmpty())
        assertEquals(false, result.needsConfirmation)
    }
}
