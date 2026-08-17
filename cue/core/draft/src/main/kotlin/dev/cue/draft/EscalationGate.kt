package dev.cue.draft

import dev.cue.model.CapturedContext
import dev.cue.model.ConversationStage

/**
 * §7.3. Escalation sanity.
 *
 * > Reject drafts proposing to meet before `ESTABLISHED`, or referencing
 * > anything sexual before she has. **Not a moral filter — a calibration one.**
 * > Mistimed escalation is the most common way a good conversation dies.
 *
 * Both halves are relative to something measurable: the stage the classifier
 * computed, and what she has actually written. Neither is a judgement about
 * what the message says, which is why the sexual-content half unlocks the
 * moment she opens that door and not before.
 */
object EscalationGate {

    /** Null when the draft is fine. Otherwise the reason, for [dev.cue.model.GateReport]. */
    fun check(
        draft: String,
        stage: ConversationStage,
        context: CapturedContext,
    ): String? {
        val text = draft.lowercase()

        if (stage == ConversationStage.OPENER || stage == ConversationStage.EARLY_RAPPORT) {
            val proposal = MEETING_PROPOSALS.firstOrNull { containsPhrase(text, it) }
            if (proposal != null) {
                return "Proposes meeting (\"$proposal\") at $stage, before the conversation is established"
            }
        }

        val sexual = SEXUAL_TERMS.firstOrNull { containsPhrase(text, it) }
        if (sexual != null && !sheOpenedThatDoor(context)) {
            return "References \"$sexual\" before she has"
        }

        return null
    }

    /**
     * A meeting proposal, not a mention.
     *
     * "Coffee" alone is a topic; "grab a coffee" is an ask. The phrases here all
     * carry the invitation, which keeps the gate from rejecting the EARLY_RAPPORT
     * variants for mentioning that a place exists.
     */
    private val MEETING_PROPOSALS = listOf(
        "grab a drink", "grab drinks", "grab a coffee", "grab coffee",
        "get a drink", "get drinks", "get coffee", "go for a drink",
        "go for a walk", "go for coffee", "meet up", "meet you", "let's meet",
        "we should meet", "we should go", "you should come", "come over",
        "my place", "your place", "dinner sometime", "take you out",
        "are you free", "what are you doing this weekend", "want to go out",
        "let's go out", "on a date", "grab food", "grab dinner",
    )

    /**
     * Kept narrow on purpose. "Hot", "body" and "wearing" all belong to
     * innocuous sentences far more often than not — "it's hot out", "what are
     * you wearing to the wedding" — and a gate that rejects those costs a
     * variant every time the weather comes up. Each phrase here is escalatory
     * in essentially every context it appears in.
     */
    private val SEXUAL_TERMS = listOf(
        "sexy", "naked", "nudes", "hook up", "hookup", "in bed",
        "netflix and chill", "turn me on", "turn you on", "kiss you",
        "make out", "sleep with", "spend the night", "take you home",
        "come home with me", "in my bed",
    )

    /**
     * True when she has used comparable language first.
     *
     * The check is on her messages only. §7.3's rule is about *her* pace: what
     * you said earlier does not establish consent to keep going, and a gate
     * that read your own escalation as permission would unlock itself.
     */
    private fun sheOpenedThatDoor(context: CapturedContext): Boolean =
        context.theirMessages.any { message ->
            val text = message.text.lowercase()
            SEXUAL_TERMS.any { containsPhrase(text, it) }
        }

    private fun containsPhrase(haystack: String, phrase: String): Boolean =
        Regex("""(?<![\p{L}])${Regex.escape(phrase)}(?![\p{L}])""").containsMatchIn(haystack)
}
