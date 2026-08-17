package dev.cue.draft

import dev.cue.model.ConversationStage
import dev.cue.model.Sender
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** §6.3 — every rule, and the one place this build departs from the spec's order. */
class StageClassifierTest {

    private val day = 86_400_000L

    private fun exchange(count: Int, herQuestions: Boolean = false): List<Pair<Sender, String>> =
        (0 until count).map { index ->
            val sender = if (index % 2 == 0) Sender.THEM else Sender.ME
            val text = when {
                sender == Sender.THEM && herQuestions ->
                    "and what did you make of the whole thing then?"
                sender == Sender.THEM -> "that is a long and considered reply about the topic"
                else -> "ok that tracks completely"
            }
            sender to text
        }

    @Test
    fun `no messages is an opener`() {
        assertEquals(ConversationStage.OPENER, StageClassifier.classify(DraftFixtures.context()))
    }

    @Test
    fun `under six messages is early rapport`() {
        val context = DraftFixtures.context(exchange(4))
        assertEquals(ConversationStage.EARLY_RAPPORT, StageClassifier.classify(context))
    }

    @Test
    fun `over three days of silence is stalling`() {
        val context = DraftFixtures.context(exchange(8))
        val stage = StageClassifier.classify(
            context,
            lastTheirMessageAt = DraftFixtures.NOW - 4 * day,
        )
        assertEquals(ConversationStage.STALLING, stage)
    }

    @Test
    fun `over seven days of silence is dead`() {
        val context = DraftFixtures.context(exchange(8))
        val stage = StageClassifier.classify(
            context,
            lastTheirMessageAt = DraftFixtures.NOW - 9 * day,
        )
        assertEquals(ConversationStage.DEAD, stage)
    }

    /**
     * The documented departure from §6.3's listed order.
     *
     * §6.3 puts `messageCount < 6 → EARLY_RAPPORT` above the silence rules.
     * Read as first-match-wins, a four-message thread abandoned five days ago
     * classifies as early rapport, and the app offers three ways to build on a
     * conversation that is over.
     */
    @Test
    fun `silence outranks a short message count`() {
        val context = DraftFixtures.context(exchange(4))
        val stage = StageClassifier.classify(
            context,
            lastTheirMessageAt = DraftFixtures.NOW - 5 * day,
        )
        assertEquals(ConversationStage.STALLING, stage)
    }

    @Test
    fun `her messages getting much shorter is stalling`() {
        val messages = listOf(
            Sender.THEM to "that was such a good day, we ended up staying for hours and hours",
            Sender.ME to "sounds like it",
            Sender.THEM to "honestly the best part was the walk back through the whole park",
            Sender.ME to "agreed",
            Sender.THEM to "yeah it was lovely and i would happily do the entire thing again",
            Sender.ME to "next time then",
            Sender.THEM to "for sure, that would be really good, i am in whenever you are",
            Sender.ME to "ok",
            Sender.THEM to "yeah",
            Sender.ME to "ok",
            Sender.THEM to "maybe",
            Sender.ME to "ok",
            Sender.THEM to "sure",
            Sender.ME to "ok",
            Sender.THEM to "ha",
        )
        val context = DraftFixtures.context(messages)
        assertEquals(
            ConversationStage.STALLING,
            StageClassifier.classify(context, lastTheirMessageAt = DraftFixtures.NOW),
        )
    }

    @Test
    fun `eight messages with her asking questions and no logistics is ready to ask`() {
        val context = DraftFixtures.context(exchange(10, herQuestions = true))
        assertEquals(
            ConversationStage.READY_TO_ASK,
            StageClassifier.classify(context, lastTheirMessageAt = DraftFixtures.NOW),
        )
    }

    @Test
    fun `logistics already on the table is not ready to ask`() {
        val withPlan = exchange(10, herQuestions = true) +
            (Sender.ME to "drinks thursday then")
        val context = DraftFixtures.context(withPlan)
        assertEquals(
            ConversationStage.ESTABLISHED,
            StageClassifier.classify(context, lastTheirMessageAt = DraftFixtures.NOW),
        )
    }

    /** Her proposal counts too: the app must not tell you to ask twice. */
    @Test
    fun `logistics she raised also blocks ready to ask`() {
        val herPlan = exchange(10, herQuestions = true) +
            (Sender.THEM to "i'm free saturday if you are")
        val context = DraftFixtures.context(herPlan)
        assertEquals(
            ConversationStage.ESTABLISHED,
            StageClassifier.classify(context, lastTheirMessageAt = DraftFixtures.NOW),
        )
    }

    @Test
    fun `an unknown last-reply time never fabricates staleness`() {
        val context = DraftFixtures.context(exchange(10)).let {
            it.copy(messages = it.messages.map { message -> message.copy(sentAt = null) })
        }
        val signals = StageClassifier.signals(context)
        assertEquals(null, signals.daysSinceTheirLast)
        assertTrue(StageClassifier.classify(signals) != ConversationStage.DEAD)
    }

    @Test
    fun `signals are inspectable`() {
        val context = DraftFixtures.context(exchange(10, herQuestions = true))
        val signals = StageClassifier.signals(context, DraftFixtures.NOW)
        assertEquals(10, signals.messageCount)
        assertTrue(signals.herQuestionRate > 0.3f, "questions ${signals.herQuestionRate}")
        assertEquals(false, signals.logisticsMentioned)
    }
}
