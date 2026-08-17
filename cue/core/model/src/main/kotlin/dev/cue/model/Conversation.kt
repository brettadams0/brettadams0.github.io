package dev.cue.model

import kotlinx.serialization.Serializable

/** §5.4. A Hinge prompt and the answer she wrote under it. */
@Serializable
data class PromptAnswer(
    val prompt: String,
    val answer: String,
)

/**
 * §5.4. What the capture pass could read off her profile.
 *
 * Every field is nullable or empty-able because OCR routinely gets some of it
 * and never all of it. Nothing downstream may assume a field is present, and
 * §7.2 will reject a draft that asserts anything not in here.
 */
@Serializable
data class MatchProfile(
    val displayName: String? = null,
    val age: Int? = null,
    val bio: String? = null,
    /** §5.4: the highest-signal field in the app. Volunteered and specific. */
    val prompts: List<PromptAnswer> = emptyList(),
    val attributes: Map<String, String> = emptyMap(),
    /** Text visible *in* photos. Her photos are never otherwise processed (§10). */
    val photoCaptions: List<String> = emptyList(),
    val capturedAt: Long = 0L,
) {
    val isEmpty: Boolean
        get() = bio.isNullOrBlank() && prompts.isEmpty() &&
            attributes.isEmpty() && photoCaptions.isEmpty()
}

/**
 * §11. One message in a captured conversation.
 *
 * [sentAt] is usually null: both apps show relative times ("2h ago") that OCR
 * poorly, and trap 12 says not to trust them. [sequence] is the ordering that
 * actually holds.
 */
@Serializable
data class Message(
    val id: String,
    val conversationId: String,
    val sender: Sender,
    val text: String,
    val sentAt: Long? = null,
    val sequence: Int,
    /**
     * §4.2's bounding-box attribution, as a number. Below 0.8 the message is
     * excluded from the voice profile — a misattributed message poisons the
     * profile silently, and an excluded one costs nothing.
     */
    val attributionConfidence: Float = 1f,
) {
    val trustedForVoiceProfile: Boolean
        get() = sender == Sender.ME && attributionConfidence >= MIN_ATTRIBUTION_CONFIDENCE

    companion object {
        const val MIN_ATTRIBUTION_CONFIDENCE = 0.8f
    }
}

/** §11. */
@Serializable
data class Conversation(
    val id: String,
    val platform: Platform,
    /** Never her real name unless she wrote it herself; §10's default posture. */
    val matchPseudonym: String,
    val profile: MatchProfile? = null,
    val stage: ConversationStage = ConversationStage.OPENER,
    val lastCapturedAt: Long = 0L,
    val lastTheirMessageAt: Long? = null,
    val lastMyMessageAt: Long? = null,
    /** §10: halts all capture and processing for this match. */
    val excluded: Boolean = false,
)

/**
 * Everything the drafting pipeline is allowed to know about this reply.
 *
 * This type is the fact-grounding boundary (§7.2): if a specific is not
 * reachable from here, no draft may assert it. Passing the conversation and
 * profile around separately made that boundary a convention; making it one
 * object makes it a signature.
 */
@Serializable
data class CapturedContext(
    val conversationId: String,
    val platform: Platform,
    val profile: MatchProfile,
    /** Oldest first. The pipeline uses the last six (§6.2). */
    val messages: List<Message>,
    val capturedAt: Long,
    /** Null when unknown, which is the common case for a fresh capture. */
    val nowMillis: Long = capturedAt,
) {
    val theirMessages: List<Message> get() = messages.filter { it.sender == Sender.THEM }
    val myMessages: List<Message> get() = messages.filter { it.sender == Sender.ME }
    val lastMessage: Message? get() = messages.lastOrNull()
}
