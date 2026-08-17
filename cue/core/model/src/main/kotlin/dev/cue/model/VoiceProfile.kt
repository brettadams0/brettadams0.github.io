package dev.cue.model

import kotlinx.serialization.Serializable

/**
 * §4.1. Your voice, measured as numbers rather than described in a sentence.
 *
 * Every field exists because a small model gets it wrong in a specific,
 * recognisable way. "Casual tone" as an instruction produces LinkedIn-casual;
 * `capitalizationRate = 0.08` produces lowercase. The compiler (§4.4) reads
 * these and edits the draft directly — no inference is spent on any of it.
 *
 * Rates are fractions in `0f..1f` unless the name says otherwise.
 */
@Serializable
data class VoiceProfile(
    val sampleCount: Int,

    // --- Length ---------------------------------------------------------
    val medianWords: Float,
    val p90Words: Float,

    // --- Orthography — where "AI voice" leaks in worst -------------------
    /** Fraction of messages whose first letter is capitalised. */
    val capitalizationRate: Float,

    /** Fraction of standalone first-person pronouns written lowercase. */
    val lowercaseIRate: Float,

    /** Fraction of messages ending in `.`, `!`, `?`, or `…`. */
    val terminalPunctuationRate: Float,
    val ellipsisRate: Float,

    /** Commas per 100 words. Not a fraction. */
    val commaRate: Float,

    /** Exclamation marks per 100 words. Not a fraction. */
    val exclamationRate: Float,

    // --- Register -------------------------------------------------------
    /** Emoji per message. Not a fraction — two per message is normal for some. */
    val emojiRate: Float,
    val topEmoji: List<String>,

    /** Raw counts, so the compiler can prefer the abbreviation you actually use. */
    val abbreviations: Map<String, Int>,

    /** Fraction of contractible pairs you actually contract. */
    val contractionRate: Float,
    val profanityRate: Float,

    // --- Behaviour ------------------------------------------------------
    val questionRate: Float,
    /** Fraction of your messages sent within 60s of your previous one. */
    val burstRate: Float,

    /** Top TF-IDF tokens against generic English. Your tells. */
    val characteristicTokens: List<String>,

    /**
     * Every word you have actually used, capped and frequency-ordered.
     *
     * Not in §4.1's list, and added because two gates are unimplementable
     * without it. §4.4's forbidden-token list is conditional — "lol (if absent
     * from your corpus)" — so the compiler has to know what your corpus
     * contains. And §7.2 says to check content nouns "against the context"
     * after dropping "your own known vocabulary", which is this. Deriving it
     * twice from the raw corpus at each call site would be slower and would
     * drift; deriving it once at profile time makes it exportable to the
     * extension (§3.4) along with everything else.
     *
     * Counts, not a set, because §4.4's abbreviation transform needs to know
     * whether you write "u" *more* than "you" — membership alone would tell it
     * only that you have used both at least once, which is true of everyone.
     */
    val vocabulary: Map<String, Int> = emptyMap(),
) {
    /**
     * §4.2. Below 50 messages the profile is not trusted: small corpora overfit
     * brutally, and three messages ending in "lol" become a law. The UI shows a
     * persistent calibrating banner until this is true (§13).
     */
    val isCalibrated: Boolean get() = sampleCount >= MIN_SAMPLES

    /** §4.4's length transform. A hard ceiling, not a target. */
    val maxDraftWords: Int get() = (p90Words + 3f).toInt().coerceAtLeast(4)

    companion object {
        const val MIN_SAMPLES = 50

        /**
         * §13's fallback for an uncalibrated corpus.
         *
         * Deliberately *not* neutral. A profile of all-zeroes would make the
         * compiler strip every capital and every full stop from someone who
         * writes in full sentences; a profile of all-ones would let the model's
         * native register through untouched, which is the failure this whole
         * section exists to prevent. These numbers are the middle of the
         * distribution for casual phone messaging — wrong for most people in
         * some particular, and wrong in a way that reads as ordinary rather
         * than as a bug.
         */
        val BASELINE = VoiceProfile(
            sampleCount = 0,
            medianWords = 9f,
            p90Words = 22f,
            capitalizationRate = 0.35f,
            lowercaseIRate = 0.4f,
            terminalPunctuationRate = 0.3f,
            ellipsisRate = 0.05f,
            commaRate = 2.5f,
            exclamationRate = 1.0f,
            emojiRate = 0.15f,
            topEmoji = emptyList(),
            abbreviations = emptyMap(),
            contractionRate = 0.9f,
            profanityRate = 0.02f,
            questionRate = 0.5f,
            burstRate = 0.2f,
            characteristicTokens = emptyList(),
        )
    }
}

/**
 * A message you actually sent, kept for retrieval (§4.3) and profiling (§4.1).
 *
 * [precedingTheirMessage] is what makes retrieval match on *situation* rather
 * than vocabulary — §4.3 indexes the pair, so "she asked about my week" finds
 * your replies to that, not every message where you said "week".
 */
@Serializable
data class SentMessage(
    val id: String,
    val text: String,
    val precedingTheirMessage: String?,
    val stage: ConversationStage?,
    val sentAt: Long?,
    /**
     * §8: your edit of a draft is the best training signal available, so it
     * enters the corpus at double weight. Nothing else ever sets this above 1.
     */
    val weight: Int = 1,
)
