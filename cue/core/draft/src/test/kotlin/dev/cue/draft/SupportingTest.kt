package dev.cue.draft

import dev.cue.model.Conversation
import dev.cue.model.ConversationStage
import dev.cue.model.Draft
import dev.cue.model.DraftAction
import dev.cue.model.DraftOutcome
import dev.cue.model.Platform
import dev.cue.model.Sender
import dev.cue.model.Strategy
import dev.cue.model.StrategyStats
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** §6.2 — the prompt's shape, which matters more at 2–4B than at frontier scale. */
class PromptBuilderTest {

    private val voice = DraftFixtures.voice()
    private val builder = PromptBuilder()
    private val context = DraftFixtures.context(
        messages = listOf(
            Sender.THEM to "i cannot swim, it is a whole thing",
            Sender.ME to "ok that needs explaining",
        ),
    )

    @Test
    fun `constraints come last because small models weight recent tokens`() {
        val prompt = builder.build(
            context,
            Strategy.ESTABLISHED_DEEPEN,
            DraftFixtures.corpus().take(5),
            voice,
        )
        val rulesAt = prompt.text.indexOf("RULES")
        val profileAt = prompt.text.indexOf("HER PROFILE")
        val examplesAt = prompt.text.indexOf("HOW I WRITE")

        assertTrue(profileAt in 0 until examplesAt, prompt.text)
        assertTrue(examplesAt in 0 until rulesAt, prompt.text)
        assertTrue(prompt.text.trimEnd().endsWith("no quotes."), prompt.text.takeLast(120))
    }

    @Test
    fun `the voice is demonstrated, never described`() {
        val prompt = builder.build(
            context,
            Strategy.ESTABLISHED_DEEPEN,
            DraftFixtures.corpus().take(5),
            voice,
        )
        assertTrue(prompt.text.contains("I wrote:"), "no examples in the prompt")
        // §4.3: instruction-following degrades fast at this size; nothing here
        // should be trying to describe orthography in prose.
        listOf("lowercase", "capitalis", "capitaliz", "punctuation", "emoji").forEach {
            assertFalse(prompt.text.contains(it, ignoreCase = true), "prompt describes '$it'")
        }
    }

    @Test
    fun `history is capped at six messages`() {
        val long = DraftFixtures.context(
            messages = (0 until 20).map { Sender.THEM to "message number $it about something" },
        )
        val prompt = builder.build(long, Strategy.ESTABLISHED_DEEPEN, emptyList(), voice)
        assertEquals(PromptBuilder.MAX_HISTORY, prompt.historyUsed)
    }

    @Test
    fun `the prompt stays under the token ceiling by dropping history first`() {
        val huge = DraftFixtures.context(
            messages = (0 until 20).map {
                Sender.THEM to "a very long message ".repeat(30)
            },
        )
        val prompt = builder.build(huge, Strategy.ESTABLISHED_DEEPEN, DraftFixtures.corpus(), voice)

        assertTrue(
            prompt.estimatedTokens <= PromptBuilder.MAX_CONTEXT_TOKENS ||
                prompt.historyUsed == PromptBuilder.MIN_HISTORY,
            "tokens ${prompt.estimatedTokens}, history ${prompt.historyUsed}",
        )
        assertTrue(prompt.examplesUsed >= PromptBuilder.MIN_EXAMPLES)
    }

    @Test
    fun `the word ceiling in the rules comes from the profile`() {
        val prompt = builder.build(context, Strategy.ESTABLISHED_DEEPEN, emptyList(), voice)
        assertTrue(prompt.text.contains("At most ${voice.maxDraftWords} words."), prompt.text)
    }

    @Test
    fun `an empty profile says so rather than pretending`() {
        val blank = DraftFixtures.context(profile = dev.cue.model.MatchProfile())
        val prompt = builder.build(blank, Strategy.OPENER_CALLBACK, emptyList(), voice)
        assertTrue(prompt.text.contains("(nothing captured)"), prompt.text)
    }
}

/** §6.5 — pattern extraction and detail selection. */
class TemplatePathTest {

    @Test
    fun `an opener becomes a frame with its specific word removed`() {
        val patterns = TemplatePath.extractPatterns(DraftFixtures.corpus())
        assertTrue(patterns.isNotEmpty())
        assertTrue(patterns.all { it.skeleton.contains(TemplatePath.SLOT) })
        assertTrue(
            patterns.any { it.skeleton == "ok how did you end up with a ${TemplatePath.SLOT}" },
            patterns.map { it.skeleton }.toString(),
        )
    }

    @Test
    fun `replies are not mistaken for openers`() {
        val patterns = TemplatePath.extractPatterns(DraftFixtures.corpus())
        assertTrue(
            patterns.none { it.skeleton.contains("queue") },
            patterns.map { it.skeleton }.toString(),
        )
    }

    @Test
    fun `the detail comes from her prompt answers first`() {
        val detail = TemplatePath.pickDetail(DraftFixtures.herProfile())
        assertEquals("minister", detail)
    }

    @Test
    fun `a profile with nothing in it yields no detail`() {
        assertNull(TemplatePath.pickDetail(dev.cue.model.MatchProfile()))
    }

    @Test
    fun `a corpus with no openers falls back to frames`() {
        val patterns = TemplatePath.extractPatterns(emptyList())
        assertTrue(patterns.isEmpty())
        assertTrue(TemplatePath.fallbackPatterns().all { it.skeleton.contains(TemplatePath.SLOT) })
    }

    @Test
    fun `filling a frame substitutes the slot`() {
        val pattern = TemplatePath.fallbackPatterns().first()
        assertEquals("ok how did you get into kayaking", TemplatePath.fill(pattern, "kayaking"))
    }
}

/** §8 — the outcome loop. */
class OutcomeLoopTest {

    private val draft = Draft(
        id = "d1",
        conversationId = "c1",
        strategy = Strategy.ESTABLISHED_DEEPEN,
        rawModelOutput = "So nowhere near water at all then.",
        text = "so nowhere near water at all then",
    )

    @Test
    fun `an edited draft enters the corpus at double weight`() {
        val outcome = DraftOutcome(
            draftId = "d1",
            variantStrategy = draft.strategy,
            action = DraftAction.SENT_EDITED,
            finalText = "so nowhere near water at all, got it",
        )
        val entry = OutcomeLoop.toCorpusEntry(outcome, draft, "i cannot swim", ConversationStage.ESTABLISHED, 1L)

        assertEquals("so nowhere near water at all, got it", entry?.text)
        assertEquals(OutcomeLoop.CORRECTION_WEIGHT, entry?.weight)
        assertEquals("i cannot swim", entry?.precedingTheirMessage)
    }

    @Test
    fun `an unedited send counts once`() {
        val outcome = DraftOutcome("d1", draft.strategy, DraftAction.SENT_CLEAN)
        assertEquals(1, OutcomeLoop.toCorpusEntry(outcome, draft, null, null, 1L)?.weight)
    }

    @Test
    fun `a discarded draft teaches nothing about your voice`() {
        val outcome = DraftOutcome("d1", draft.strategy, DraftAction.DISCARDED)
        assertNull(OutcomeLoop.toCorpusEntry(outcome, draft, null, null, 1L))
    }

    @Test
    fun `stats separate shown from sent from replied`() {
        val outcomes = listOf(
            DraftOutcome("1", Strategy.OPENER_CALLBACK, DraftAction.SENT_CLEAN, gotReply = true),
            DraftOutcome("2", Strategy.OPENER_CALLBACK, DraftAction.DISCARDED),
            DraftOutcome("3", Strategy.OPENER_CHALLENGE, DraftAction.SENT_CLEAN, gotReply = false),
        )
        val stats = OutcomeLoop.stats(outcomes).associateBy { it.strategy }

        assertEquals(2, stats.getValue(Strategy.OPENER_CALLBACK).shown)
        assertEquals(1, stats.getValue(Strategy.OPENER_CALLBACK).sent)
        assertEquals(1f, stats.getValue(Strategy.OPENER_CALLBACK).replyRate)
        assertEquals(0f, stats.getValue(Strategy.OPENER_CHALLENGE).replyRate)
    }

    @Test
    fun `ranking waits for enough sends to mean something`() {
        val order = Strategy.forStage(ConversationStage.OPENER)
        val thin = listOf(StrategyStats(Strategy.OPENER_BRIDGE, shown = 2, sent = 2, replied = 2))
        assertEquals(order, OutcomeLoop.rank(order, thin))

        val real = listOf(
            StrategyStats(Strategy.OPENER_BRIDGE, shown = 40, sent = 30, replied = 24),
            StrategyStats(Strategy.OPENER_CALLBACK, shown = 40, sent = 30, replied = 6),
        )
        assertEquals(Strategy.OPENER_BRIDGE, OutcomeLoop.rank(order, real).first())
    }

    @Test
    fun `edit distance measures a fix against a rewrite`() {
        assertEquals(0, OutcomeLoop.editDistance("same", "same"))
        assertEquals(1, OutcomeLoop.editDistance("ok that tracks", "ok that track"))
        assertTrue(OutcomeLoop.editDistance("ok that tracks", "completely different text") > 10)
    }
}

/** §9 — conversation health. */
class ConversationHealthTest {

    private val day = 86_400_000L

    @Test
    fun `her messages getting shorter is a cooling signal`() {
        val context = DraftFixtures.context(
            messages = listOf(
                Sender.THEM to "that was such a good day and we stayed for hours honestly",
                Sender.THEM to "the walk back through the park was the best part of it",
                Sender.THEM to "i would happily do the entire thing again next weekend",
                Sender.THEM to "yeah",
                Sender.THEM to "maybe",
                Sender.THEM to "ok",
            ),
        )
        val report = ConversationHealth.assess(context, DraftFixtures.NOW)
        assertTrue(report.cooling, report.signals.toString())
        assertTrue(report.signals.any { it.contains("shorter") }, report.signals.toString())
    }

    @Test
    fun `one-sided question asking is a cooling signal`() {
        val context = DraftFixtures.context(
            messages = listOf(
                Sender.ME to "how was your week?",
                Sender.THEM to "fine",
                Sender.ME to "what did you get up to?",
                Sender.THEM to "not much",
                Sender.ME to "any plans?",
                Sender.THEM to "nope",
            ),
        )
        val report = ConversationHealth.assess(context, DraftFixtures.NOW)
        assertTrue(report.signals.any { it.contains("asked") }, report.signals.toString())
    }

    @Test
    fun `seven days without a reply is stale`() {
        val context = DraftFixtures.context(
            messages = listOf(Sender.THEM to "sounds good"),
            now = DraftFixtures.NOW,
        ).let { it.copy(messages = it.messages.map { m -> m.copy(sentAt = DraftFixtures.NOW - 8 * day) }) }

        val report = ConversationHealth.assess(context, DraftFixtures.NOW)
        assertTrue(report.stale)
        assertFalse(report.healthy)
    }

    @Test
    fun `the ball is in your court when she replied last`() {
        val context = DraftFixtures.context(
            messages = listOf(Sender.ME to "explain the kayak", Sender.THEM to "it was a phase"),
        )
        assertTrue(ConversationHealth.assess(context, DraftFixtures.NOW).ballInYourCourt)
    }

    @Test
    fun `the ball-in-your-court list is oldest first and skips excluded matches`() {
        val conversations = listOf(
            Conversation(
                "recent", Platform.HINGE, "A",
                lastTheirMessageAt = DraftFixtures.NOW - day,
                lastMyMessageAt = DraftFixtures.NOW - 2 * day,
            ),
            Conversation(
                "old", Platform.TINDER, "B",
                lastTheirMessageAt = DraftFixtures.NOW - 5 * day,
                lastMyMessageAt = null,
            ),
            Conversation(
                "mine", Platform.HINGE, "C",
                lastTheirMessageAt = DraftFixtures.NOW - 6 * day,
                lastMyMessageAt = DraftFixtures.NOW - day,
            ),
            Conversation(
                "excluded", Platform.HINGE, "D",
                lastTheirMessageAt = DraftFixtures.NOW - 9 * day,
                lastMyMessageAt = null,
                excluded = true,
            ),
        )
        assertEquals(
            listOf("old", "recent"),
            ConversationHealth.ballInYourCourt(conversations).map { it.id },
        )
    }
}

/** §12 — the thermal budget. */
class GenerationBudgetTest {

    @Test
    fun `twenty generations an hour, then exhausted`() {
        val budget = GenerationBudget()
        repeat(20) { budget.record(1_000L + it) }
        assertEquals(0, budget.remaining(2_000L))
        assertTrue(budget.exhausted(2_000L))
    }

    @Test
    fun `the window slides`() {
        val budget = GenerationBudget()
        repeat(20) { budget.record(1_000L) }
        assertTrue(budget.exhausted(1_000L))
        assertFalse(budget.exhausted(1_000L + 3_600_001L))
        assertEquals(GenerationBudget.MAX_PER_HOUR, budget.remaining(1_000L + 3_600_001L))
    }
}

/** §3.3 — tiering by measured RAM, never hardcoded (trap 8). */
class ModelTierTest {

    @Test
    fun `the tier follows measured free memory`() {
        assertEquals(dev.cue.model.ModelTier.E4B, dev.cue.model.ModelTier.forFreeRamMb(7000))
        assertEquals(dev.cue.model.ModelTier.E2B, dev.cue.model.ModelTier.forFreeRamMb(4000))
        assertEquals(dev.cue.model.ModelTier.ONE_B, dev.cue.model.ModelTier.forFreeRamMb(2000))
    }

    /** §13: OOM drops one tier permanently, and the floor is the template path. */
    @Test
    fun `demotion walks down to template only and stops`() {
        var tier = dev.cue.model.ModelTier.E4B
        tier = dev.cue.model.ModelTier.demote(tier)
        assertEquals(dev.cue.model.ModelTier.E2B, tier)
        tier = dev.cue.model.ModelTier.demote(tier)
        assertEquals(dev.cue.model.ModelTier.ONE_B, tier)
        tier = dev.cue.model.ModelTier.demote(tier)
        assertEquals(dev.cue.model.ModelTier.TEMPLATE_ONLY, tier)
        assertEquals(dev.cue.model.ModelTier.TEMPLATE_ONLY, dev.cue.model.ModelTier.demote(tier))
    }

    @Test
    fun `the weakest tiers prefer the template path`() {
        assertTrue(dev.cue.model.ModelTier.ONE_B.prefersTemplatePath)
        assertTrue(dev.cue.model.ModelTier.TEMPLATE_ONLY.prefersTemplatePath)
        assertFalse(dev.cue.model.ModelTier.E2B.prefersTemplatePath)
    }
}
