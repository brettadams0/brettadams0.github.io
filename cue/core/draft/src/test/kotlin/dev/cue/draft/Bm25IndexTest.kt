package dev.cue.draft

import dev.cue.model.ConversationStage
import dev.cue.model.SentMessage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * §14.9 — retrieval quality. Twenty labelled situations; the right past message
 * must appear in the top five.
 *
 * This is the test that decides whether §4.3's claim holds: "a 2B model given
 * five real examples of how you write outperforms a 4B model given a paragraph
 * describing how you write." If retrieval returns five irrelevant messages, the
 * examples teach the wrong register and the whole architecture leans on nothing.
 */
class Bm25IndexTest {

    private val index = Bm25Index(DraftFixtures.corpus())

    @Test
    fun `the right past message is in the top five for every labelled situation`() {
        val misses = mutableListOf<String>()

        DraftFixtures.SITUATIONS.forEachIndexed { position, situation ->
            val results = index.search(situation.probe, limit = 5)
            if (results.none { it.message.id == "s$position" }) {
                misses += "'${situation.probe}' -> ${results.map { it.message.text }}"
            }
        }

        assertTrue(misses.isEmpty(), "missed ${misses.size} of 20:\n${misses.joinToString("\n")}")
    }

    @Test
    fun `the right past message is usually first, not merely present`() {
        val firstPlace = DraftFixtures.SITUATIONS.withIndex().count { (position, situation) ->
            index.search(situation.probe, limit = 5).firstOrNull()?.message?.id == "s$position"
        }
        // Top-five is the spec's bar. Ranking first most of the time is what
        // makes the first example the most relevant one.
        assertTrue(firstPlace >= 16, "only $firstPlace of 20 ranked first")
    }

    /**
     * §4.3 indexes her message plus your reply, so a probe that only matches
     * *her* side still retrieves your response to it. Without that, retrieval
     * matches vocabulary rather than situation.
     */
    @Test
    fun `a probe matching only her side still finds your reply`() {
        val corpus = listOf(
            SentMessage("a", "i'll bring the good speakers", "are you coming to the barbecue on sunday", null, null),
            SentMessage("b", "that tracks", "how was your week", null, null),
        )
        val results = Bm25Index(corpus).search("the barbecue on sunday", limit = 2)
        assertEquals("a", results.first().message.id)
    }

    @Test
    fun `messages from the same stage are preferred among equals`() {
        val corpus = listOf(
            SentMessage("opener", "the kayak needs explaining", null, ConversationStage.OPENER, null),
            SentMessage("late", "the kayak needs explaining", null, ConversationStage.READY_TO_ASK, null),
        )
        val results = Bm25Index(corpus).search("kayak", limit = 2, stage = ConversationStage.OPENER)
        assertEquals("opener", results.first().message.id)
    }

    /** §8: your corrections carry double weight into retrieval too. */
    @Test
    fun `a correction outranks an identical uncorrected message`() {
        val corpus = listOf(
            SentMessage("plain", "the night market dumplings", null, null, null),
            SentMessage("edited", "the night market dumplings", null, null, null, weight = 2),
        )
        val results = Bm25Index(corpus).search("night market", limit = 2)
        assertEquals("edited", results.first().message.id)
    }

    @Test
    fun `an empty corpus and an empty query both return nothing`() {
        assertTrue(Bm25Index(emptyList()).search("anything").isEmpty())
        assertTrue(index.search("the and of").isEmpty())
    }

    @Test
    fun `results are stable for identical scores`() {
        val first = index.search("coffee", limit = 5).map { it.message.id }
        val second = index.search("coffee", limit = 5).map { it.message.id }
        assertEquals(first, second)
    }
}
