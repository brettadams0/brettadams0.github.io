package dev.cue.draft

import dev.cue.model.CapturedContext
import dev.cue.model.Conversation
import dev.cue.model.Sender

/** §9. What the passive signals say about one conversation. */
data class HealthReport(
    val cooling: Boolean,
    val signals: List<String>,
    val ballInYourCourt: Boolean,
    val stale: Boolean,
) {
    val healthy: Boolean get() = !cooling && !stale
}

/**
 * §9. Conversation health — passive, free, no inference.
 *
 * Everything here is arithmetic over things already stored. That is what makes
 * it worth having: it costs nothing to compute on every capture, so it can flag
 * a conversation *before* it is dead rather than explaining afterwards why it
 * died.
 */
object ConversationHealth {

    private const val MILLIS_PER_DAY = 86_400_000L

    /** §9: past 7 days with no reply, archive and suggest closure. */
    const val STALE_DAYS = 7

    fun assess(context: CapturedContext, now: Long = context.nowMillis): HealthReport {
        val signals = mutableListOf<String>()
        val hers = context.theirMessages

        val recent = hers.takeLast(3).map { wordCount(it.text) }
        val earlier = hers.dropLast(3).takeLast(3).map { wordCount(it.text) }
        if (recent.isNotEmpty() && earlier.isNotEmpty()) {
            val recentMedian = median(recent)
            val earlierMedian = median(earlier)
            if (earlierMedian > 0 && recentMedian < earlierMedian * 0.6) {
                signals += "Her messages are getting shorter " +
                    "(${earlierMedian.toInt()} words to ${recentMedian.toInt()})"
            }
        }

        // §9's question reciprocity: a conversation where only one person is
        // asking is one person carrying it.
        val herQuestions = hers.count { it.text.contains('?') }
        val myQuestions = context.myMessages.count { it.text.contains('?') }
        if (myQuestions >= 3 && herQuestions == 0) {
            signals += "You have asked $myQuestions questions and she has asked none"
        }

        val lastTheirs = hers.lastOrNull()?.sentAt
        val daysSince = lastTheirs?.let { (now - it) / MILLIS_PER_DAY }
        if (daysSince != null && daysSince >= 2) {
            signals += "Nothing from her in $daysSince days"
        }

        val ballInYourCourt = context.lastMessage?.sender == Sender.THEM

        return HealthReport(
            cooling = signals.isNotEmpty() && daysSince?.let { it < STALE_DAYS } != false,
            signals = signals,
            ballInYourCourt = ballInYourCourt,
            stale = daysSince != null && daysSince >= STALE_DAYS,
        )
    }

    /**
     * §9's ball-in-your-court list: "the cheapest high-value screen in the app".
     *
     * Conversations where she replied last, oldest first — the ordering is the
     * feature, since the one at the top is the one about to expire.
     */
    fun ballInYourCourt(conversations: List<Conversation>): List<Conversation> =
        conversations
            .filterNot { it.excluded }
            .filter { conversation ->
                val theirs = conversation.lastTheirMessageAt ?: return@filter false
                val mine = conversation.lastMyMessageAt
                mine == null || theirs > mine
            }
            .sortedBy { it.lastTheirMessageAt }

    private fun wordCount(text: String): Int = Regex("""[\p{L}\p{N}]+""").findAll(text).count()

    private fun median(values: List<Int>): Double {
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) {
            sorted[middle].toDouble()
        } else {
            (sorted[middle - 1] + sorted[middle]) / 2.0
        }
    }
}
