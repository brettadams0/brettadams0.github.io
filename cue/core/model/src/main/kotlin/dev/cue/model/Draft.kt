package dev.cue.model

import kotlinx.serialization.Serializable

/** §4.4. One deterministic edit the compiler made, named so the UI can show it. */
@Serializable
enum class VoiceTransform {
    LOWERCASE_LEAD,
    STRIP_TERMINAL_PERIOD,
    LOWERCASE_I,
    EMOJI_TRIM,
    EMOJI_SUBSTITUTE,
    COMMA_THINNING,
    CONTRACT,
    FORBIDDEN_TOKEN_DELETED,
    EXCLAMATION_TRIM,
    ELLIPSIS_NORMALIZED,
    TRUNCATED_TO_LENGTH,
    ABBREVIATED,
}

/** §7.1. One measured feature that still disagrees with the profile. */
@Serializable
data class StyleDeviation(
    val feature: String,
    val profileValue: Float,
    val draftValue: Float,
) {
    override fun toString(): String = "$feature: profile $profileValue, draft $draftValue"
}

/**
 * §7. What the gates concluded, kept verbatim on the draft.
 *
 * Stored rather than reduced to a boolean because "why did this draft not
 * appear" is otherwise unanswerable, and §7.2 deliberately ships *nothing* for
 * a variant that fails twice — an empty slot with no explanation looks like a
 * crash.
 */
@Serializable
data class GateReport(
    val styleDeviations: List<StyleDeviation> = emptyList(),
    /** §7.2: specifics in the draft that are absent from the captured context. */
    val ungroundedTerms: List<String> = emptyList(),
    /** §7.3: mistimed meeting or sexual content for this stage. */
    val escalationViolation: String? = null,
    val attempts: Int = 1,
) {
    val grounded: Boolean get() = ungroundedTerms.isEmpty()
    val escalationOk: Boolean get() = escalationViolation == null

    /**
     * §7.1 ships a style failure with a visible badge; §7.2 and §7.3 ship
     * nothing at all. The asymmetry is the point: an off-voice draft is a draft
     * you can edit, and a hallucinated one is a message you cannot unsend.
     */
    val shippable: Boolean get() = grounded && escalationOk
    val offVoice: Boolean get() = styleDeviations.isNotEmpty()
}

/** §11. */
@Serializable
data class Draft(
    val id: String,
    val conversationId: String,
    val strategy: Strategy,
    /** Pre-compiler. Kept because "model or compiler?" is otherwise unanswerable. */
    val rawModelOutput: String,
    /** Post-compiler. What you actually see. */
    val text: String,
    val transformsApplied: List<VoiceTransform> = emptyList(),
    val gates: GateReport = GateReport(),
    val modelTier: ModelTier = ModelTier.TEMPLATE_ONLY,
    val inferenceMs: Long = 0L,
    val createdAt: Long = 0L,
)

/**
 * §8. The outcome loop.
 *
 * [finalText] is the highest-value field in the app after the corpus itself:
 * the delta between what was generated and what you sent is a direct
 * correction, and it re-enters retrieval immediately (§4.3) so your fixes
 * become future few-shot examples. That is how a 2B model improves at sounding
 * like you with no fine-tuning anywhere.
 */
@Serializable
data class DraftOutcome(
    val draftId: String,
    val variantStrategy: Strategy,
    val action: DraftAction,
    val editDistance: Int? = null,
    val finalText: String? = null,
    /** Resolved on the next capture of that conversation, not at send time. */
    val gotReply: Boolean? = null,
    val replyLatencyMs: Long? = null,
)

/** §8's second payoff, computed over accumulated outcomes. */
@Serializable
data class StrategyStats(
    val strategy: Strategy,
    val shown: Int,
    val sent: Int,
    val replied: Int,
) {
    val sendRate: Float get() = if (shown == 0) 0f else sent.toFloat() / shown
    val replyRate: Float get() = if (sent == 0) 0f else replied.toFloat() / sent

    /**
     * §8: reorder variants by measured reply rate — but not before there is
     * something to measure. Twenty sends is not statistical significance; it is
     * the point past which the ordering is better than the author's guess.
     */
    val trustworthy: Boolean get() = sent >= 20
}
