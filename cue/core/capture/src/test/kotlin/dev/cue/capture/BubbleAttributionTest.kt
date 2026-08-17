package dev.cue.capture

import dev.cue.model.Message
import dev.cue.model.Sender
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * §14.1 — attribution accuracy, "> 98%, poisons everything downstream".
 *
 * The reversal test is the one that matters. §4.2 and §16 trap 1 both single it
 * out: a flipped axis builds the voice profile from her messages and then writes
 * drafts that sound like the person you are talking to. Nothing else in the app
 * fails that quietly.
 */
class BubbleAttributionTest {

    private val attribution = BubbleAttribution()

    @Test
    fun `right-aligned bubbles are mine and left-aligned are hers`() {
        val labelled = ChatFixtures.screen(
            "basic",
            listOf(
                ChatFixtures.LabelledBubble("what are you up to this weekend", Sender.THEM),
                ChatFixtures.LabelledBubble("ok that's a strong opinion", Sender.ME),
            ),
        )
        val result = attribution.attribute(labelled.screen)

        val mine = result.blocks.filter { it.sender == Sender.ME }.map { it.text }
        val hers = result.blocks.filter { it.sender == Sender.THEM }.map { it.text }

        assertTrue(mine.any { it.contains("strong opinion") }, "mine: $mine")
        assertTrue(hers.any { it.contains("weekend") }, "hers: $hers")
        assertFalse(result.needsConfirmation)
    }

    /** §14.1's headline number, over [ChatFixtures.accuracyCorpus]. */
    @Test
    fun `attribution accuracy over fifty screens exceeds ninety eight percent`() {
        var total = 0
        var correct = 0

        ChatFixtures.accuracyCorpus().forEach { labelled ->
            val result = attribution.attribute(labelled.screen)
            // Compare per recognised line: every block carries a sender, and one
            // wrong line is one wrong message once the stitcher merges them.
            val expected = labelled.bubbles.associate { bubble ->
                bubble.text to bubble.sender
            }
            result.blocks.forEach { block ->
                val owner = expected.entries.firstOrNull { (text, _) ->
                    text.contains(block.text.trim()) || block.text.trim().contains(text)
                } ?: return@forEach
                total++
                if (owner.value == block.sender) correct++
            }
        }

        assertTrue(total > 200, "fixture produced too few blocks to mean anything: $total")
        val accuracy = correct.toDouble() / total
        assertTrue(accuracy > 0.98, "accuracy $accuracy over $total blocks")
    }

    /**
     * §13: attribution ambiguous means ask, not guess. A centred block belongs
     * to neither side and must not be silently handed to one.
     */
    @Test
    fun `a centred block is flagged rather than assigned confidently`() {
        val screen = RecognizedScreen(
            id = "centred",
            screenWidth = 1080,
            screenHeight = 2400,
            blocks = listOf(
                TextBlock("floating in the middle", BoundingBox(340, 600, 740, 660)),
            ),
            capturedAt = 0L,
        )
        val result = attribution.attribute(screen)
        assertTrue(result.needsConfirmation, "should ask: ${result.reason}")
        assertTrue(
            result.blocks.single().confidence < Message.MIN_ATTRIBUTION_CONFIDENCE,
            "confidence ${result.blocks.single().confidence}",
        )
    }

    /**
     * A one-sided screenshot is a real screenshot. §4.2's clustering pass must
     * not fit a phantom "you" cluster to a conversation where you have not
     * replied yet.
     */
    @Test
    fun `a conversation where only she has spoken attributes everything to her`() {
        val labelled = ChatFixtures.screen(
            "one-sided",
            List(3) { ChatFixtures.LabelledBubble(ChatFixtures.HER_MESSAGES[it], Sender.THEM) },
        )
        val result = attribution.attribute(labelled.screen)
        assertTrue(result.blocks.all { it.sender == Sender.THEM }, "senders: ${result.blocks.map { it.sender }}")
        assertFalse(result.needsConfirmation)
    }

    /** §13: "remember which side is you, per platform". */
    @Test
    fun `a mirrored layout flips the answer and nothing else`() {
        val labelled = ChatFixtures.screen(
            "mirrored",
            listOf(ChatFixtures.LabelledBubble("ok that's a strong opinion", Sender.ME)),
        )
        val mirrored = BubbleAttribution(ChatLayout(myMessagesAlignedRight = false))
        val result = mirrored.attribute(labelled.screen)
        assertEquals(Sender.THEM, result.blocks.first().sender)
    }

    @Test
    fun `interface copy and timestamps never become messages`() {
        val labelled = ChatFixtures.screen(
            "chrome",
            listOf(ChatFixtures.LabelledBubble("that tracks", Sender.ME)),
        )
        val texts = attribution.attribute(labelled.screen).blocks.map { it.text }
        assertFalse(texts.any { it.contains("Type a message") }, texts.toString())
        assertFalse(texts.any { it.matches(Regex("""\d{1,2}:\d{2} PM""")) }, texts.toString())
        assertFalse(texts.contains("Maya"), texts.toString())
    }

    @Test
    fun `an unknown screen width asks rather than assuming`() {
        val screen = RecognizedScreen(
            id = "no-width",
            screenWidth = 0,
            screenHeight = 2400,
            blocks = listOf(TextBlock("hello", BoundingBox(40, 600, 400, 660))),
            capturedAt = 0L,
        )
        val result = attribution.attribute(screen)
        assertTrue(result.needsConfirmation)
        assertEquals(0f, result.blocks.single().confidence)
    }
}
