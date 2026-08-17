package dev.cue.draft

import dev.cue.model.CapturedContext
import dev.cue.model.ConversationStage
import dev.cue.model.Message
import dev.cue.model.Sender

/** What the rules in §6.3 actually look at, computed once and inspectable. */
data class StageSignals(
    val messageCount: Int,
    val daysSinceTheirLast: Float?,
    val herRecentMedianWords: Float,
    val herEarlierMedianWords: Float,
    val herQuestionRate: Float,
    val logisticsMentioned: Boolean,
) {
    /** §6.3: her median message length declining more than 40% over four messages. */
    val herLengthDeclining: Boolean
        get() = herEarlierMedianWords > 0f &&
            herRecentMedianWords < herEarlierMedianWords * 0.6f
}

/**
 * §6.3. Stage classification without a model.
 *
 * "Free, instant, deterministic, debuggable." Every input is countable, so the
 * classifier can explain itself, and a wrong stage is a bug with a reproducible
 * cause rather than a temperature setting.
 *
 * **One deliberate departure from the order §6.3 lists.** The spec puts
 * `messageCount < 6 → EARLY_RAPPORT` above the `daysSinceHerLast` rules. Read
 * as first-match-wins, that classifies a four-message thread she abandoned five
 * days ago as early rapport, and offers three variants for building on a
 * conversation that is over. Silence outranks length here: DEAD and STALLING
 * are checked first. Everything else keeps the spec's order and its thresholds.
 */
object StageClassifier {

    private const val MILLIS_PER_DAY = 86_400_000f

    /** §6.3's window for "declining over the last 4 messages". */
    private const val RECENT_WINDOW = 4

    fun classify(context: CapturedContext, lastTheirMessageAt: Long? = null): ConversationStage =
        classify(signals(context, lastTheirMessageAt))

    fun classify(signals: StageSignals): ConversationStage = when {
        signals.messageCount == 0 -> ConversationStage.OPENER

        signals.daysSinceTheirLast != null && signals.daysSinceTheirLast > 7f ->
            ConversationStage.DEAD

        signals.daysSinceTheirLast != null && signals.daysSinceTheirLast > 3f ->
            ConversationStage.STALLING

        signals.messageCount < 6 -> ConversationStage.EARLY_RAPPORT

        signals.herLengthDeclining -> ConversationStage.STALLING

        signals.messageCount >= 8 &&
            signals.herQuestionRate > 0.3f &&
            !signals.logisticsMentioned -> ConversationStage.READY_TO_ASK

        else -> ConversationStage.ESTABLISHED
    }

    fun signals(context: CapturedContext, lastTheirMessageAt: Long? = null): StageSignals {
        val hers = context.theirMessages
        val wordCounts = hers.map { wordCount(it.text).toFloat() }
        val recent = wordCounts.takeLast(RECENT_WINDOW)
        val earlier = wordCounts.dropLast(RECENT_WINDOW).takeLast(RECENT_WINDOW)

        val lastTheirs = lastTheirMessageAt
            ?: hers.lastOrNull()?.sentAt
            ?: context.messages.lastOrNull { it.sender == Sender.THEM }?.sentAt

        return StageSignals(
            messageCount = context.messages.size,
            daysSinceTheirLast = lastTheirs?.let { (context.nowMillis - it) / MILLIS_PER_DAY },
            herRecentMedianWords = median(recent),
            // With fewer than eight of her messages there is no "earlier" to
            // compare against, and a comparison against two messages is noise.
            herEarlierMedianWords = if (earlier.size < RECENT_WINDOW) 0f else median(earlier),
            herQuestionRate = if (hers.isEmpty()) {
                0f
            } else {
                hers.count { it.text.contains('?') }.toFloat() / hers.size
            },
            logisticsMentioned = context.messages.any { mentionsLogistics(it) },
        )
    }

    /**
     * §6.3's `logisticsMentioned`, the flag that stops the app suggesting a
     * first ask when one has already been made.
     *
     * Matches on either side of the conversation on purpose: if she proposed
     * something, the app should not be telling you to propose it.
     */
    private fun mentionsLogistics(message: Message): Boolean {
        val text = message.text.lowercase()
        return LOGISTICS_TERMS.any { term ->
            Regex("""(?<![\p{L}])${Regex.escape(term)}(?![\p{L}])""").containsMatchIn(text)
        }
    }

    private val LOGISTICS_TERMS = listOf(
        "meet up", "meet", "grab a drink", "grab drinks", "drinks", "coffee",
        "dinner", "lunch", "brunch", "date", "hang out", "hangout",
        "free this week", "free next week", "your week look", "are you around",
        "this weekend", "next weekend", "friday", "saturday", "sunday",
        "what time", "see you", "come with me", "go together",
    )

    private fun wordCount(text: String): Int =
        Regex("""[\p{L}\p{N}]+""").findAll(text).count()

    private fun median(values: List<Float>): Float {
        if (values.isEmpty()) return 0f
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) {
            sorted[middle]
        } else {
            (sorted[middle - 1] + sorted[middle]) / 2f
        }
    }
}
