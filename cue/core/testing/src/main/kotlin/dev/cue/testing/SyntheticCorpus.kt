package dev.cue.testing

import dev.cue.model.ConversationStage
import dev.cue.model.SentMessage
import kotlin.random.Random

/**
 * A described writing style, rendered into messages.
 *
 * The voice engine's tests need a corpus whose true features are known, which
 * a handful of hand-written strings cannot provide: §14.3 asks whether two
 * disjoint halves of a corpus produce profiles that agree within 15%, and that
 * question is only meaningful over a hundred-plus messages drawn from one
 * consistent style.
 *
 * Generating them from a style specification also inverts the test usefully.
 * Rather than asserting that the profiler returns particular numbers, the tests
 * assert that it *recovers the style it was given* — which is the property that
 * matters when the corpus is a stranger's real messages.
 */
data class SyntheticVoice(
    val capitalize: Boolean,
    val terminalPunctuation: Boolean,
    val lowercaseI: Boolean,
    val emojiEvery: Int,
    val emoji: List<String>,
    val useAbbreviations: Boolean,
    val useContractions: Boolean,
    val questionEvery: Int,
    val commasPerMessage: Int = 0,
) {
    companion object {
        /** Lowercase, unpunctuated, emoji-light. The style §4.4 exists to serve. */
        val LOWERCASE = SyntheticVoice(
            capitalize = false,
            terminalPunctuation = false,
            lowercaseI = true,
            emojiEvery = 8,
            emoji = listOf("😂", "🙃"),
            useAbbreviations = true,
            useContractions = true,
            questionEvery = 3,
        )

        /** Sentence case, full stops, no abbreviations. Equally real, opposite. */
        val PROPER = SyntheticVoice(
            capitalize = true,
            terminalPunctuation = true,
            lowercaseI = false,
            emojiEvery = 0,
            emoji = emptyList(),
            useAbbreviations = false,
            useContractions = false,
            questionEvery = 3,
            commasPerMessage = 1,
        )
    }
}

object SyntheticCorpus {

    private val OPENINGS = listOf(
        "I am definitely going to that", "I have been meaning to try it",
        "that place is good", "you are not wrong about that",
        "I do not know how you manage it", "we are going on saturday",
        "it is the best one near me", "I would have said the same",
        "you have got to see it", "that is a strong opinion",
        "I will be around later", "they are all pretty similar",
        "I am about to leave", "you are going to like it",
        "it is closer than I thought", "I have not been in years",
    )

    private val MIDDLES = listOf(
        "the coffee there is unreasonable", "my flatmate keeps recommending it",
        "the walk back is the good part", "everyone says the same thing about it",
        "I only found out last week", "it took me three tries to get in",
        "the whole street smells like bread", "nobody warned me about the stairs",
        "the second half is better", "I still think about that trip",
        "my sister sent me a photo of it", "the queue moves faster than it looks",
    )

    private val QUESTIONS = listOf(
        "what do you think", "have you been", "how was your week",
        "when are you free", "do you want to go", "is that the one on king",
        "what did you end up doing", "are you around this weekend",
    )

    private val PROMPTS_FROM_HER = listOf(
        "what are you up to this weekend", "have you been to that new place",
        "how was work today", "what is your go to order",
        "did you end up going", "tell me something surprising",
        "how long have you lived here", "what are you reading",
    )

    /**
     * [count] messages in [voice], deterministic for a given [seed].
     *
     * Deterministic because a flaky voice test is worse than no voice test: the
     * failure it would report is indistinguishable from the profiler having
     * genuinely drifted.
     */
    fun messages(
        count: Int,
        voice: SyntheticVoice,
        seed: Int = 7,
        idPrefix: String = "m",
        stage: ConversationStage? = ConversationStage.EARLY_RAPPORT,
    ): List<SentMessage> {
        val random = Random(seed)
        return (0 until count).map { index ->
            val body = buildString {
                append(OPENINGS[random.nextInt(OPENINGS.size)])
                if (random.nextInt(3) != 0) {
                    append(" and ")
                    append(MIDDLES[random.nextInt(MIDDLES.size)])
                }
                if (index % voice.questionEvery == 0) {
                    append(", ")
                    append(QUESTIONS[random.nextInt(QUESTIONS.size)])
                    append("?")
                }
            }
            SentMessage(
                id = "$idPrefix$index",
                text = render(body, voice, index),
                precedingTheirMessage = PROMPTS_FROM_HER[index % PROMPTS_FROM_HER.size],
                stage = stage,
                sentAt = 1_700_000_000_000L + index * 90_000L,
            )
        }
    }

    private fun render(body: String, voice: SyntheticVoice, index: Int): String {
        var text = body

        if (voice.useContractions) {
            CONTRACTIONS.forEach { (long, short) -> text = text.replace(long, short) }
        }
        if (voice.useAbbreviations) {
            text = text.replace(Regex("""\byou\b"""), "u").replace(Regex("""\byour\b"""), "ur")
        }
        if (!voice.lowercaseI) {
            text = text.replace(Regex("""\bi\b"""), "I")
        } else {
            text = text.replace(Regex("""\bI\b"""), "i")
        }

        if (voice.commasPerMessage == 0) {
            text = text.replace(",", "")
        }

        if (voice.capitalize) {
            text = text.replaceFirstChar { it.uppercaseChar() }
        } else {
            text = text.replaceFirstChar { it.lowercaseChar() }
        }

        if (voice.terminalPunctuation && !text.endsWith("?")) {
            text = "$text."
        }

        if (voice.emojiEvery > 0 && index % voice.emojiEvery == 0 && voice.emoji.isNotEmpty()) {
            // Rotate over the emoji that actually appear, not over the message
            // index: `index % size` picks the same emoji every time whenever
            // emojiEvery is even, which quietly makes a two-emoji voice a
            // one-emoji voice and hides bugs in `topEmoji`.
            val occurrence = index / voice.emojiEvery
            text = "$text ${voice.emoji[occurrence % voice.emoji.size]}"
        }

        return text
    }

    private val CONTRACTIONS = mapOf(
        "I am" to "I'm", "i am" to "i'm",
        "you are" to "you're", "it is" to "it's", "that is" to "that's",
        "I have" to "I've", "i have" to "i've",
        "do not" to "don't", "I will" to "I'll", "i will" to "i'll",
        "I would" to "I'd", "i would" to "i'd",
        "we are" to "we're", "they are" to "they're", "have not" to "haven't",
    )
}
