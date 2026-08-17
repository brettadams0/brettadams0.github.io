package dev.cue.model

import kotlinx.serialization.Serializable

@Serializable
enum class Platform {
    HINGE,
    TINDER,
}

@Serializable
enum class Sender {
    ME,
    THEM,
}

/**
 * §6.3. Classified by rules over measurable signals, never by the model.
 *
 * The ordering is roughly chronological but the classifier is not a state
 * machine — a conversation can fall back to [STALLING] from anywhere, and
 * [READY_TO_ASK] is a claim about the present, not a stage you graduate into.
 */
@Serializable
enum class ConversationStage {
    OPENER,
    EARLY_RAPPORT,
    ESTABLISHED,

    /**
     * §6.3 calls this the most valuable output in the app: most matches die
     * because nobody moves, not because the messages were bad. Surfaced as a
     * banner, not a variant label.
     */
    READY_TO_ASK,
    STALLING,
    DEAD,
}

/**
 * §6.1. Three drafts pursuing *different outcomes*, each labelled with intent.
 *
 * The [instruction] is the single sentence handed to the model (§6.2) — one
 * sentence, because at 2–4B a paragraph of strategy dilutes the constraints
 * that follow it. The [label] is what the user sees on the draft card; it has
 * to name the move, since "three rewordings of one message is a worthless
 * choice" and the user can only tell them apart if the intent is stated.
 */
@Serializable
enum class Strategy(
    val stage: ConversationStage,
    val label: String,
    val instruction: String,
) {
    OPENER_CALLBACK(
        ConversationStage.OPENER,
        "Specific callback",
        "Respond to exactly one thing she wrote in a prompt answer. Name it directly.",
    ),
    OPENER_CHALLENGE(
        ConversationStage.OPENER,
        "Playful challenge",
        "Disagree lightly with something in her profile, or set her a small challenge about it.",
    ),
    OPENER_BRIDGE(
        ConversationStage.OPENER,
        "Shared interest",
        "Connect one thing in her profile to something you plainly have in common, then ask about hers.",
    ),

    RAPPORT_BUILD(
        ConversationStage.EARLY_RAPPORT,
        "Build on her thread",
        "Continue the topic she just raised and add something of your own to it.",
    ),
    RAPPORT_REDIRECT(
        ConversationStage.EARLY_RAPPORT,
        "New topic",
        "Acknowledge her last message briefly, then move to a different topic from her profile.",
    ),
    RAPPORT_ESCALATE(
        ConversationStage.EARLY_RAPPORT,
        "Light escalation",
        "Reply with a little more warmth or teasing than the thread currently has. No plans, no meeting.",
    ),

    ESTABLISHED_DEEPEN(
        ConversationStage.ESTABLISHED,
        "Go deeper",
        "Ask about the reason behind what she just said, not the fact of it.",
    ),
    ESTABLISHED_LOGISTICS(
        ConversationStage.ESTABLISHED,
        "Toward logistics",
        "Move the conversation toward something you could actually do together, without asking yet.",
    ),
    ESTABLISHED_CALLBACK(
        ConversationStage.ESTABLISHED,
        "Earlier callback",
        "Return to something she said earlier in the conversation and pick it back up.",
    ),

    ASK_DIRECT(
        ConversationStage.READY_TO_ASK,
        "Direct ask",
        "Propose one specific plan — an activity and a rough day. Make it easy to say yes to.",
    ),
    ASK_SOFT(
        ConversationStage.READY_TO_ASK,
        "Soft ask",
        "Float an idea for meeting without pinning a time. Leave her room to shape it.",
    ),
    ASK_AVAILABILITY(
        ConversationStage.READY_TO_ASK,
        "Availability probe",
        "Ask what her week looks like, in a way that clearly points at meeting up.",
    ),

    REVIVAL_LOW_STAKES(
        ConversationStage.STALLING,
        "Low-stakes revival",
        "Send something that costs her nothing to answer. Do not mention the silence.",
    ),
    REVIVAL_DIRECT(
        ConversationStage.STALLING,
        "Direct re-engage",
        "Acknowledge the gap lightly and give her one clear thing to respond to.",
    ),

    /**
     * §6.1 lists "offer to let it go" as the third stalling variant. It is a
     * real move, not a joke: the graceful exit sometimes gets the reply, and
     * when it does not, it ends a thread that was already over.
     */
    REVIVAL_CLOSE(
        ConversationStage.STALLING,
        "Let it go",
        "Close warmly and without complaint. No guilt, no question she has to answer.",
    ),

    /**
     * §6.5. Not a model strategy — the template path fills a historical opener
     * pattern with a detail from her profile and runs it through the compiler.
     * It carries no [instruction] because no inference happens.
     */
    TEMPLATE_OPENER(
        ConversationStage.OPENER,
        "From your patterns",
        "",
    ),
    ;

    val usesInference: Boolean get() = this != TEMPLATE_OPENER

    companion object {
        /** The three variants generated for [stage], in display order. */
        fun forStage(stage: ConversationStage): List<Strategy> = when (stage) {
            ConversationStage.OPENER ->
                listOf(OPENER_CALLBACK, OPENER_CHALLENGE, OPENER_BRIDGE)
            ConversationStage.EARLY_RAPPORT ->
                listOf(RAPPORT_BUILD, RAPPORT_REDIRECT, RAPPORT_ESCALATE)
            ConversationStage.ESTABLISHED ->
                listOf(ESTABLISHED_DEEPEN, ESTABLISHED_LOGISTICS, ESTABLISHED_CALLBACK)
            ConversationStage.READY_TO_ASK ->
                listOf(ASK_DIRECT, ASK_SOFT, ASK_AVAILABILITY)
            ConversationStage.STALLING ->
                listOf(REVIVAL_LOW_STAKES, REVIVAL_DIRECT, REVIVAL_CLOSE)
            // §9: past seven days, archive and suggest closure over a fourth
            // revival. One option, and it is the exit.
            ConversationStage.DEAD ->
                listOf(REVIVAL_CLOSE)
        }
    }
}

/**
 * §3.3. Chosen at install time from measured free RAM, never hardcoded — trap 8.
 *
 * [TEMPLATE_ONLY] is not a model. It is the state the app runs in before the
 * download finishes, after an unrecoverable load failure, and in the browser
 * when WebGPU is missing (§13). Everything in §6.5 still works there, which is
 * the reason M2 lands before M3.
 */
@Serializable
enum class ModelTier(
    val modelId: String,
    val minFreeRamMb: Int,
    val approxDiskMb: Int,
) {
    E4B("gemma-3n-E4B-it-int4", minFreeRamMb = 6 * 1024, approxDiskMb = 3600),
    E2B("gemma-3n-E2B-it-int4", minFreeRamMb = 3 * 1024, approxDiskMb = 2600),
    ONE_B("gemma-3-1b-it-int4", minFreeRamMb = 0, approxDiskMb = 529),
    TEMPLATE_ONLY("none", minFreeRamMb = 0, approxDiskMb = 0),
    ;

    /**
     * §6.5: on the 1B tier the template path is the *primary* path, not a
     * fourth option, because generation quality is weakest exactly there.
     */
    val prefersTemplatePath: Boolean get() = this == ONE_B || this == TEMPLATE_ONLY

    companion object {
        /** §3.3's table, as the only place the thresholds live. */
        fun forFreeRamMb(freeRamMb: Int): ModelTier = when {
            freeRamMb >= E4B.minFreeRamMb -> E4B
            freeRamMb >= E2B.minFreeRamMb -> E2B
            else -> ONE_B
        }

        /** §13: OOM during inference drops one tier permanently. */
        fun demote(from: ModelTier): ModelTier = when (from) {
            E4B -> E2B
            E2B -> ONE_B
            ONE_B, TEMPLATE_ONLY -> TEMPLATE_ONLY
        }
    }
}

/** §8. What you did with a draft — the only signal the app ever gets. */
@Serializable
enum class DraftAction {
    SENT_CLEAN,
    SENT_EDITED,
    DISCARDED,
}
