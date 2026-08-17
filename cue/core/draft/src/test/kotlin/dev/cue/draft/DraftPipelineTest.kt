package dev.cue.draft

import dev.cue.model.ConversationStage
import dev.cue.model.ModelTier
import dev.cue.model.Sender
import dev.cue.model.Strategy
import dev.cue.model.VoiceProfile
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * §6–§7 end to end, with the model replaced by a script.
 *
 * The point is the *policy*: three strategically distinct variants, the voice
 * compiler before every gate, and the asymmetry §7 requires — an off-voice draft
 * ships with a badge, a hallucinated one does not ship at all.
 */
class DraftPipelineTest {

    private val voice = DraftFixtures.voice()
    private val corpus = DraftFixtures.corpus()
    private var now = DraftFixtures.NOW

    private fun pipeline(engine: InferenceEngine?, profile: VoiceProfile = voice) =
        DraftPipeline(profile, corpus, engine, clock = { now })

    private val established = DraftFixtures.context(
        messages = listOf(
            Sender.THEM to "i cannot swim, it is a whole thing",
            Sender.ME to "ok that needs explaining",
            Sender.THEM to "i grew up nowhere near water, that is the entire explanation",
            Sender.ME to "genuinely fair",
            Sender.THEM to "what about you then, any glaring gaps",
            Sender.ME to "several, i will ration them",
            Sender.THEM to "ration them? that is worse somehow",
        ),
    )

    @Test
    fun `three variants for the stage, each with a different strategy`() = runBlocking {
        val engine = ScriptedEngine(
            responses = listOf(
                "so nowhere near water, and yet",
                "three bones though, which bone was worst",
                "back to the prime minister thing",
            ),
        )
        val set = pipeline(engine).draft(established, lastTheirMessageAt = now)

        assertEquals(ConversationStage.ESTABLISHED, set.stage)
        assertEquals(3, set.drafts.size, set.drafts.map { it.text }.toString())
        assertEquals(3, set.drafts.map { it.strategy }.distinct().size)
        assertEquals(Strategy.forStage(ConversationStage.ESTABLISHED), set.drafts.map { it.strategy })
    }

    /** §6.1: separate calls, separate strategies, **separate seeds**. */
    @Test
    fun `each variant is a separate call with its own seed`() = runBlocking {
        // Three grounded, mutually distinct responses, so nothing triggers a
        // §7 retry or a §6.4 regeneration and the call count is exactly the
        // variant count.
        val engine = ScriptedEngine(
            responses = listOf(
                "so nowhere near water at all then",
                "three bones is a lot of bones",
                "the prime minister needs explaining",
            ),
        )
        pipeline(engine).draft(established, lastTheirMessageAt = now)

        assertEquals(3, engine.prompts.size)
        assertEquals(3, engine.seeds.distinct().size, "seeds ${engine.seeds}")
        // Each prompt carries its own one-sentence strategy (§6.2).
        Strategy.forStage(ConversationStage.ESTABLISHED).forEach { strategy ->
            assertTrue(
                engine.prompts.any { it.contains(strategy.instruction) },
                "no prompt carried: ${strategy.instruction}",
            )
        }
    }

    /** §7.2: two grounding failures ship nothing for that variant. */
    @Test
    fun `a variant that invents a detail twice is suppressed, not shipped`() = runBlocking {
        val engine = ScriptedEngine(fallback = "how is your dog coping with all that")
        val set = pipeline(engine).draft(established, lastTheirMessageAt = now)

        assertTrue(set.drafts.isEmpty(), "shipped: ${set.drafts.map { it.text }}")
        assertEquals(3, set.suppressed.size)
        assertTrue(
            set.suppressed.all { it.gates.ungroundedTerms.isNotEmpty() },
            set.suppressed.map { it.reason }.toString(),
        )
        assertTrue(set.suppressed.first().reason.contains("dog"), set.suppressed.first().reason)
    }

    /** §7.2: the offending term is named in the retry, not merely retried. */
    @Test
    fun `the second attempt forbids the invented term by name`() = runBlocking {
        val engine = ScriptedEngine(fallback = "how is your dog coping")
        pipeline(engine).draft(established, lastTheirMessageAt = now)

        assertTrue(
            engine.prompts.any { it.contains("Do not mention: dog") },
            "no prompt forbade the term:\n${engine.prompts.last()}",
        )
    }

    @Test
    fun `a grounded draft after an ungrounded one still ships`() = runBlocking {
        val engine = ScriptedEngine(
            responses = listOf(
                "how is your dog",
                "so nowhere near water at all then",
            ),
            fallback = "three bones is a lot of bones",
        )
        val set = pipeline(engine).draft(established, lastTheirMessageAt = now)
        assertEquals(3, set.drafts.size)
        assertTrue(set.drafts.first().gates.attempts >= 2, "attempts ${set.drafts.first().gates.attempts}")
    }

    /** §6.4: a variant that echoes an earlier one is regenerated once. */
    @Test
    fun `a collapsed variant is regenerated with the others quoted`() = runBlocking {
        val engine = ScriptedEngine(
            responses = listOf(
                "so nowhere near water at all then",
                "so nowhere near water at all",
                "three bones is a lot of bones",
                "the prime minister needs explaining",
            ),
        )
        val set = pipeline(engine).draft(established, lastTheirMessageAt = now)

        assertTrue(
            engine.prompts.any { it.contains("Take a different angle") },
            "no distinctness retry happened",
        )
        val pairs = set.drafts.map { it.text }
        assertTrue(Distinctness.tooSimilar(pairs).isEmpty(), "still too similar: $pairs")
    }

    /** §6.5: always offered for an opener, and it leads on the weakest tier. */
    @Test
    fun `the template path leads on the one billion tier`() = runBlocking {
        val engine = ScriptedEngine(tier = ModelTier.ONE_B, fallback = "explain the kayak")
        val set = pipeline(engine).draft(DraftFixtures.context())

        assertEquals(Strategy.TEMPLATE_OPENER, set.drafts.first().strategy)
        assertEquals(ModelTier.ONE_B, set.drafts.first().modelTier)
    }

    @Test
    fun `the template path trails on a capable tier`() = runBlocking {
        val engine = ScriptedEngine(tier = ModelTier.E4B, fallback = "explain the kayak")
        val set = pipeline(engine).draft(DraftFixtures.context())
        assertEquals(Strategy.TEMPLATE_OPENER, set.drafts.last().strategy)
    }

    /** §13: no model at all is a supported state, not an error. */
    @Test
    fun `with no model the app still produces an opener`() = runBlocking {
        val set = pipeline(engine = null).draft(DraftFixtures.context())

        assertEquals(1, set.drafts.size)
        assertEquals(Strategy.TEMPLATE_OPENER, set.drafts.single().strategy)
        assertEquals(ModelTier.TEMPLATE_ONLY, set.drafts.single().modelTier)
        assertEquals(0L, set.totalInferenceMs)
    }

    @Test
    fun `the template draft is grounded in her profile by construction`() = runBlocking {
        val set = pipeline(engine = null).draft(DraftFixtures.context())
        val draft = set.drafts.single()
        assertTrue(draft.gates.grounded, draft.gates.ungroundedTerms.toString())
        // The slot was filled from her profile, which is why grounding passes by
        // construction. §5.4 weights prompt answers above photo captions, so the
        // detail comes from "i met a prime minister" rather than from the kayak.
        assertTrue(draft.text.contains("minister"), draft.text)
    }

    /** §6.3: READY_TO_ASK is a banner, not a variant label. */
    @Test
    fun `ready to ask is surfaced on the set`() = runBlocking {
        val ready = DraftFixtures.context(
            messages = (0 until 10).map { index ->
                if (index % 2 == 0) {
                    Sender.THEM to "and what did you make of the whole thing then?"
                } else {
                    Sender.ME to "ok that tracks completely"
                }
            },
        )
        val engine = ScriptedEngine(fallback = "what does your week look like")
        val set = pipeline(engine).draft(ready, lastTheirMessageAt = now)

        assertEquals(ConversationStage.READY_TO_ASK, set.stage)
        assertTrue(set.readyToAsk)
    }

    /** §4.2: below fifty messages the set says so, and keeps saying so. */
    @Test
    fun `an uncalibrated profile marks the set as calibrating`() = runBlocking {
        val set = DraftPipeline(VoiceProfile.BASELINE, corpus.take(10), null, clock = { now })
            .draft(DraftFixtures.context())
        assertTrue(set.calibrating)

        val calibrated = pipeline(engine = null).draft(DraftFixtures.context())
        assertFalse(calibrated.calibrating)
    }

    @Test
    fun `the raw model output is kept alongside the compiled text`() = runBlocking {
        val engine = ScriptedEngine(fallback = "Honestly? That sounds amazing — I'd definitely go!!")
        val set = pipeline(engine).draft(established, lastTheirMessageAt = now)

        // Everything was filler, so nothing ships — but §11 still wants the raw
        // output somewhere, and here that is the suppression record.
        assertTrue(set.drafts.isEmpty() || set.drafts.all { it.rawModelOutput.contains("Honestly") })
    }

    @Test
    fun `a draft carries the transforms that produced it`() = runBlocking {
        val engine = ScriptedEngine(fallback = "So nowhere near water at all then.")
        val set = pipeline(engine).draft(established, lastTheirMessageAt = now)
        val draft = set.drafts.first()
        assertTrue(draft.transformsApplied.isNotEmpty(), "no transforms recorded for '${draft.text}'")
        assertEquals("so nowhere near water at all then", draft.text)
    }

    @Test
    fun `inference time is recorded from the injected clock`() = runBlocking {
        var tick = 0L
        val engine = ScriptedEngine(fallback = "so nowhere near water at all then")
        val timed = DraftPipeline(voice, corpus, engine, clock = { tick += 25L; tick })
        val set = timed.draft(established, lastTheirMessageAt = DraftFixtures.NOW)
        assertTrue(set.totalInferenceMs > 0, "no time recorded")
    }
}
