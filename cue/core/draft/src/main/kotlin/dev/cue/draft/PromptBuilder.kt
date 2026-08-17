package dev.cue.draft

import dev.cue.model.CapturedContext
import dev.cue.model.MatchProfile
import dev.cue.model.Message
import dev.cue.model.SentMessage
import dev.cue.model.Sender
import dev.cue.model.Strategy
import dev.cue.model.VoiceProfile
import kotlin.math.ceil

data class BuiltPrompt(
    val text: String,
    val estimatedTokens: Int,
    val examplesUsed: Int,
    val historyUsed: Int,
)

/**
 * §6.2. The prompt, assembled for a 2–4B model rather than a frontier one.
 *
 * Two rules from the spec drive every choice here.
 *
 * **Constraints last.** "Order matters more at 2–4B than at frontier scale.
 * Put constraints last — small models weight recent tokens heavily." So the
 * word ceiling and the grounding rule are the final lines, not the opening
 * system prompt.
 *
 * **Under ~1,500 tokens.** "Small models degrade sharply with long context, and
 * prefill dominates latency. Six messages of history is plenty; the whole
 * conversation is not better." When the budget is tight this drops examples and
 * history rather than truncating mid-section, because half a retrieved example
 * teaches the wrong lesson.
 *
 * Notice what is *not* here: any description of your voice. §4.3 — instruction-
 * following degrades fast at this size, example-following does not. The voice
 * arrives as five real messages and is finished afterwards by the compiler.
 */
class PromptBuilder(
    private val maxTokens: Int = MAX_CONTEXT_TOKENS,
) {

    fun build(
        context: CapturedContext,
        strategy: Strategy,
        examples: List<SentMessage>,
        voice: VoiceProfile,
        extraConstraints: List<String> = emptyList(),
    ): BuiltPrompt {
        var exampleCount = examples.size.coerceAtMost(MAX_EXAMPLES)
        var historyCount = MAX_HISTORY

        while (true) {
            val text = render(
                context = context,
                strategy = strategy,
                examples = examples.take(exampleCount),
                history = context.messages.takeLast(historyCount),
                voice = voice,
                extraConstraints = extraConstraints,
            )
            val tokens = estimateTokens(text)
            val canTrim = exampleCount > MIN_EXAMPLES || historyCount > MIN_HISTORY
            if (tokens <= maxTokens || !canTrim) {
                return BuiltPrompt(text, tokens, exampleCount, minOf(historyCount, context.messages.size))
            }
            // History goes first. An example is a demonstration of how you
            // write; a sixth message of backstory is atmosphere.
            if (historyCount > MIN_HISTORY) historyCount-- else exampleCount--
        }
    }

    private fun render(
        context: CapturedContext,
        strategy: Strategy,
        examples: List<SentMessage>,
        history: List<Message>,
        voice: VoiceProfile,
        extraConstraints: List<String>,
    ): String = buildString {
        appendLine("HER PROFILE")
        appendLine(renderProfile(context.profile))
        appendLine()

        if (history.isNotEmpty()) {
            appendLine("CONVERSATION SO FAR")
            history.forEach { message ->
                val who = if (message.sender == Sender.ME) "ME" else "HER"
                appendLine("$who: ${message.text.trim()}")
            }
            appendLine()
        }

        if (examples.isNotEmpty()) {
            appendLine("HOW I WRITE (real messages I sent, copy this register exactly)")
            examples.forEach { example ->
                example.precedingTheirMessage?.let { appendLine("  when she said: ${it.trim()}") }
                appendLine("  I wrote: ${example.text.trim()}")
            }
            appendLine()
        }

        appendLine("WRITE THE NEXT MESSAGE FROM ME.")
        appendLine(strategy.instruction)
        appendLine()

        appendLine("RULES")
        appendLine("- At most ${voice.maxDraftWords} words.")
        appendLine("- One idea. Not two.")
        appendLine("- Only mention things written above. Invent nothing about her.")
        appendLine("- Output the message only. No greeting, no explanation, no quotes.")
        extraConstraints.forEach { appendLine("- $it") }
    }.trim()

    /**
     * Terse and structured, per §6.2, and prompts first: §5.4 weights them
     * heavily because she volunteered them and they are designed to be
     * responded to.
     */
    private fun renderProfile(profile: MatchProfile): String = buildString {
        val identity = listOfNotNull(profile.displayName, profile.age?.toString()).joinToString(", ")
        if (identity.isNotBlank()) appendLine(identity)

        profile.prompts.forEach { prompt ->
            appendLine("${prompt.prompt.trim()} -> ${prompt.answer.trim()}")
        }
        profile.bio?.takeIf { it.isNotBlank() }?.let { appendLine("bio: ${it.trim()}") }
        profile.attributes.forEach { (key, value) -> appendLine("$key: $value") }
        profile.photoCaptions.filter { it.isNotBlank() }.forEach { appendLine("in a photo: $it") }

        if (isEmpty()) appendLine("(nothing captured)")
    }.trimEnd()

    /**
     * Four characters per token, the usual English approximation.
     *
     * Exact for nothing and adequate for the one decision it informs: whether
     * to drop a section. Counting properly would mean loading the tokeniser
     * before the model, which costs more than the error does.
     */
    fun estimateTokens(text: String): Int = ceil(text.length / 4.0).toInt()

    companion object {
        const val MAX_CONTEXT_TOKENS = 1_500

        /** §4.3: "the 5 most contextually similar messages you've actually sent". */
        const val MAX_EXAMPLES = 5
        const val MIN_EXAMPLES = 2

        /** §6.2: "Last 6 messages, labeled HER / ME". */
        const val MAX_HISTORY = 6
        const val MIN_HISTORY = 2
    }
}
