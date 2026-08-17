package dev.cue.draft

import dev.cue.model.ConversationStage
import dev.cue.model.Draft
import dev.cue.model.DraftAction
import dev.cue.model.DraftOutcome
import dev.cue.model.SentMessage
import dev.cue.model.Strategy
import dev.cue.model.StrategyStats

/**
 * §8. The outcome loop.
 *
 * Two payoffs, and the first is much larger than the second.
 *
 * **Your edits are supervision.** The delta between what was generated and what
 * you sent is a direct correction. It enters the corpus at double weight and
 * the retrieval index immediately, so your fixes become the few-shot examples
 * for the next draft. That is how a 2B model gets better at sounding like you
 * without a single gradient step.
 *
 * **Strategy win rates.** After a hundred drafts you know whether playful
 * openers or specific callbacks get replies *for you*, and the variant order
 * can stop being the author's guess.
 */
object OutcomeLoop {

    /** §8: fold `finalText` into the profile at double weight. */
    const val CORRECTION_WEIGHT = 2

    /**
     * The corpus entry a sent draft becomes, or null if nothing was sent.
     *
     * A discarded draft teaches nothing about your voice — you did not write
     * it, and it was not good enough to fix. It still counts in [stats], where
     * "shown but never sent" is exactly the signal that a strategy is not
     * working.
     */
    fun toCorpusEntry(
        outcome: DraftOutcome,
        draft: Draft,
        precedingTheirMessage: String?,
        stage: ConversationStage?,
        sentAt: Long,
    ): SentMessage? {
        val text = when (outcome.action) {
            DraftAction.SENT_CLEAN -> draft.text
            DraftAction.SENT_EDITED -> outcome.finalText ?: draft.text
            DraftAction.DISCARDED -> return null
        }
        if (text.isBlank()) return null

        return SentMessage(
            id = "outcome:${outcome.draftId}",
            text = text,
            precedingTheirMessage = precedingTheirMessage,
            stage = stage,
            sentAt = sentAt,
            // An edit is a correction and is worth double. An unedited send is
            // agreement, which is weaker evidence: it says the draft was good
            // enough, not that it is how you would have put it.
            weight = if (outcome.action == DraftAction.SENT_EDITED) CORRECTION_WEIGHT else 1,
        )
    }

    fun stats(outcomes: List<DraftOutcome>): List<StrategyStats> =
        outcomes.groupBy { it.variantStrategy }
            .map { (strategy, group) ->
                StrategyStats(
                    strategy = strategy,
                    shown = group.size,
                    sent = group.count { it.action != DraftAction.DISCARDED },
                    replied = group.count { it.gotReply == true },
                )
            }
            .sortedByDescending { it.replyRate }

    /**
     * §8: reorder variants by measured reply rate.
     *
     * Only strategies with enough sends to mean anything move. The rest keep
     * their spec order, so a single lucky reply cannot promote a strategy above
     * one with twenty sends behind it.
     */
    fun rank(strategies: List<Strategy>, stats: List<StrategyStats>): List<Strategy> {
        val byStrategy = stats.filter { it.trustworthy }.associateBy { it.strategy }
        if (byStrategy.isEmpty()) return strategies
        return strategies.sortedWith(
            compareByDescending<Strategy> { byStrategy[it]?.replyRate ?: -1f }
                .thenBy { strategies.indexOf(it) },
        )
    }

    /**
     * Levenshtein distance, for [DraftOutcome.editDistance].
     *
     * Recorded rather than acted on: a two-character fix and a rewrite are both
     * "SENT_EDITED", and the number is what tells them apart when the strategy
     * stats are read months later.
     */
    fun editDistance(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length

        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)

        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                val substitution = previous[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(substitution, previous[j] + 1, current[j - 1] + 1)
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[b.length]
    }
}
