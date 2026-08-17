package dev.cue.draft

import dev.cue.model.ConversationStage
import dev.cue.model.Sender
import dev.cue.model.VoiceProfile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * §14.4 — fact grounding, **100% rejection required, zero tolerance**.
 *
 * §0 rule 3 is the reason: "Inventing that she likes climbing is unrecoverable
 * in a live conversation." Every invented specific in this file is the kind a
 * small model produces when it pattern-matches a dating profile instead of
 * reading one.
 */
class GroundingGateTest {

    private val voice = DraftFixtures.voice()
    private val gate = GroundingGate(voice)
    private val context = DraftFixtures.context(
        messages = listOf(
            Sender.THEM to "i cannot swim, it is a whole thing",
            Sender.ME to "ok that needs explaining",
        ),
    )

    /** §14.4: inject drafts referencing invented details; all must be rejected. */
    @Test
    fun `every invented detail is rejected`() {
        val invented = listOf(
            "how's your dog",
            "does your dog like the kayak",
            "the climbing thing is impressive",
            "how long have you played the cello",
            "your trip to lisbon sounds unreal",
            "so you're a nurse",
            "how's the marathon training going",
            "you mentioned your brother",
            "is the bakery still your favourite",
            "tell me about berlin",
        )

        val survivors = invented.filter { gate.check(it, context).isEmpty() }
        assertTrue(survivors.isEmpty(), "these got through the gate: $survivors")
    }

    @Test
    fun `things she actually said are grounded`() {
        val grounded = listOf(
            "wait you cannot swim",
            "the swimming thing needs explaining",
            "three bones is a lot of bones",
            "a prime minister though",
            "the first coffee before anyone is awake is the correct answer",
            "explain the kayak",
        )

        grounded.forEach { draft ->
            assertTrue(gate.check(draft, context).isEmpty(), "'$draft' -> ${gate.check(draft, context)}")
        }
    }

    /**
     * §6.1's ask variants have to be able to name a plan. A gate that treated
     * "coffee on thursday" as an invented fact would silently delete the stage
     * §6.3 calls the most valuable output in the app.
     */
    @Test
    fun `proposing a plan is not asserting a fact`() {
        listOf(
            "drinks thursday?",
            "are you free saturday for a walk",
            "coffee sometime this weekend",
            "i'm free after 7 if you are",
        ).forEach { draft ->
            assertTrue(gate.check(draft, context).isEmpty(), "'$draft' -> ${gate.check(draft, context)}")
        }
    }

    @Test
    fun `a proper noun is checked even when it is in your own vocabulary`() {
        val withOssington = GroundingGate(
            voice.copy(vocabulary = voice.vocabulary + ("ossington" to 40)),
        )
        // You say it constantly. Saying it to *her* still asserts something.
        assertEquals(listOf("Ossington"), withOssington.check("the Ossington place then", context))
    }

    @Test
    fun `your own earlier message counts as context for a callback`() {
        val callback = "back to the explaining thing"
        assertTrue(gate.check(callback, context).isEmpty(), gate.check(callback, context).toString())
    }

    @Test
    fun `an uncalibrated profile still grounds against the capture`() {
        val baseline = GroundingGate(VoiceProfile.BASELINE)
        assertTrue(baseline.check("wait you cannot swim", context).isEmpty())
        assertFalse(baseline.check("how's your dog", context).isEmpty())
    }
}

/** §14 has no numbered entry for §7.3, so this covers it directly. */
class EscalationGateTest {

    private val context = DraftFixtures.context(
        messages = listOf(Sender.THEM to "the first coffee is sacred"),
    )

    @Test
    fun `proposing a meeting before established is rejected`() {
        listOf(ConversationStage.OPENER, ConversationStage.EARLY_RAPPORT).forEach { stage ->
            assertNotNull(
                EscalationGate.check("we should grab a drink this week", stage, context),
                "allowed a meeting proposal at $stage",
            )
        }
    }

    @Test
    fun `proposing a meeting once established is fine`() {
        listOf(ConversationStage.ESTABLISHED, ConversationStage.READY_TO_ASK).forEach { stage ->
            assertNull(
                EscalationGate.check("we should grab a drink this week", stage, context),
                "blocked a meeting proposal at $stage",
            )
        }
    }

    @Test
    fun `mentioning a place is not proposing to go there`() {
        assertNull(
            EscalationGate.check("that coffee place is genuinely good", ConversationStage.OPENER, context),
        )
    }

    @Test
    fun `sexual content is rejected until she raises it`() {
        assertNotNull(
            EscalationGate.check("you look sexy in that photo", ConversationStage.ESTABLISHED, context),
        )

        val sheDid = DraftFixtures.context(
            messages = listOf(Sender.THEM to "ok that was a sexy answer"),
        )
        assertNull(
            EscalationGate.check("that was a sexy answer back", ConversationStage.ESTABLISHED, sheDid),
        )
    }

    /** Your own escalation is not permission. The gate must not unlock itself. */
    @Test
    fun `your earlier escalation does not open the door`() {
        val onlyMe = DraftFixtures.context(
            messages = listOf(Sender.ME to "that was a sexy answer"),
        )
        assertNotNull(
            EscalationGate.check("sexy answer again", ConversationStage.ESTABLISHED, onlyMe),
        )
    }
}

/** §14.5 — no two shipped variants may exceed 0.6 Jaccard. */
class DistinctnessTest {

    @Test
    fun `near-identical variants are detected`() {
        val a = "the ramen place needs explaining"
        val b = "the ramen place really needs explaining"
        assertTrue(Distinctness.similarity(a, b) > Distinctness.THRESHOLD)
    }

    @Test
    fun `genuinely different moves are distinct`() {
        val callback = "three bones is a lot of bones"
        val ask = "are you free thursday"
        assertTrue(Distinctness.similarity(callback, ask) < Distinctness.THRESHOLD)
    }

    @Test
    fun `the later duplicate is the one flagged`() {
        val drafts = listOf(
            "the ramen place needs explaining",
            "are you free thursday",
            "the ramen place really needs explaining",
        )
        assertEquals(listOf(2), Distinctness.tooSimilar(drafts))
    }

    @Test
    fun `a flagged draft is not itself used to flag others`() {
        val drafts = listOf("same words here", "same words here", "same words here")
        // The second echoes the first. The third echoes the second, which is
        // already being replaced — flagging it too would replace the whole set.
        assertEquals(listOf(1, 2), Distinctness.tooSimilar(drafts))
    }

    @Test
    fun `similarity ignores stopwords`() {
        assertEquals(0.0, Distinctness.similarity("i went to the thing", "you came from a place"))
    }
}
