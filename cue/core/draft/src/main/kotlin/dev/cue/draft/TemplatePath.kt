package dev.cue.draft

import dev.cue.model.MatchProfile
import dev.cue.model.SentMessage
import dev.cue.voice.Lexicons

/**
 * One of your openers with its specific detail removed.
 *
 * [skeleton] contains exactly one `{detail}` slot. [specificity] is how unusual
 * the removed word was, which is a decent proxy for how much of the message's
 * work the slot was doing — a pattern whose slot held "climbing" transfers; one
 * whose slot held "thing" does not.
 */
data class OpenerPattern(
    val skeleton: String,
    val specificity: Double,
    val sourceId: String,
)

/**
 * §6.5. The no-inference path.
 *
 * > Free, instant, and frequently as good as generated output — openers are
 * > mostly "notice one specific thing and ask about it," which is a template.
 *
 * This is also why §15 puts M2 before M3: with the compiler and this path, the
 * app is useful with no model at all, and if M4's generated drafts are not
 * better than these, that is worth learning for the price of a milestone rather
 * than the price of the whole project.
 */
object TemplatePath {

    /**
     * Turns your past openers into reusable frames.
     *
     * An opener is a message you sent with nothing before it. The slot is the
     * least common content word in it, which is almost always the thing you
     * noticed in her profile.
     */
    fun extractPatterns(corpus: List<SentMessage>): List<OpenerPattern> =
        corpus.asSequence()
            .filter { it.precedingTheirMessage.isNullOrBlank() }
            .mapNotNull(::toPattern)
            .sortedByDescending { it.specificity }
            .toList()

    private fun toPattern(message: SentMessage): OpenerPattern? {
        val words = WORD.findAll(message.text).toList()
        if (words.size < MIN_PATTERN_WORDS) return null

        val candidate = words
            .filter { isSlotCandidate(it.value) }
            .map { it to frequency(it.value.lowercase()) }
            .sortedWith(compareBy({ it.second }, { -it.first.value.length }))
            .firstOrNull()
            ?: return null

        val (match, wordFrequency) = candidate
        // A pattern whose slot is a common word is not a pattern, it is a
        // sentence with a hole in it.
        if (wordFrequency > MAX_SLOT_FREQUENCY) return null

        val skeleton = message.text.replaceRange(match.range, SLOT)
        if (!skeleton.contains(SLOT)) return null
        return OpenerPattern(
            skeleton = skeleton,
            specificity = 1.0 / wordFrequency,
            sourceId = message.id,
        )
    }

    /**
     * The most specific thing she volunteered, preferring prompt answers.
     *
     * §5.4: prompt answers are the highest-signal field in the app, "literally
     * designed to be responded to". Bio text is a fallback and photo captions
     * are a last resort, because OCR'd photo text is as often a brand name on a
     * jersey as it is something she chose to say.
     */
    fun pickDetail(profile: MatchProfile): String? {
        val sources = buildList {
            profile.prompts.forEach { add(it.answer) }
            profile.bio?.let { add(it) }
            addAll(profile.photoCaptions)
        }
        sources.forEach { source ->
            val detail = mostSpecificTerm(source)
            if (detail != null) return detail
        }
        return null
    }

    private fun mostSpecificTerm(source: String): String? =
        WORD.findAll(source)
            .map { it.value }
            .filter { isSlotCandidate(it) }
            .sortedWith(compareBy({ frequency(it.lowercase()) }, { -it.length }))
            .firstOrNull()

    /**
     * Whether a word can be the specific thing a template is *about*.
     *
     * Two filters, both learned from what the naive version produced. Rarity
     * alone is not enough: "end" and "broken" are as rare as "kayak" and
     * "minister" in a frequency table, and the first version of this turned
     * "ok how did you end up with a kayak" into "ok how did you {detail} up with
     * a kayak" — a frame with a hole where the verb was. So participles and
     * adverbs are out, three-letter words are out, and among equals the longer
     * word wins, because the specific noun is almost always the longest thing
     * in the sentence.
     */
    private fun isSlotCandidate(word: String): Boolean {
        val lower = word.lowercase()
        if (lower.length < MIN_DETAIL_LENGTH) return false
        if (lower in Stopwords.COMMON) return false
        return NON_NOUN_SUFFIXES.none { lower.length > it.length + 2 && lower.endsWith(it) }
    }

    /**
     * Fills a pattern with a detail. Returns null when either is missing —
     * §6.5 offers a fourth option, it does not fabricate one.
     *
     * The result still goes through the voice compiler and both gates. It will
     * pass grounding by construction, since the only specific in it came out of
     * her profile, but running the gate anyway costs nothing and means there is
     * one path through the app rather than two.
     */
    fun fill(pattern: OpenerPattern, detail: String): String =
        pattern.skeleton.replace(SLOT, detail)

    /**
     * Frames for a corpus that contains no usable openers.
     *
     * Every one of them is a question about the detail and nothing else, which
     * is §6.5's whole claim about what an opener is. They carry no
     * capitalisation or punctuation of their own — the compiler applies yours.
     */
    val FALLBACK_FRAMES: List<String> = listOf(
        "ok how did you get into $SLOT",
        "$SLOT is a strong claim, go on",
        "genuine question about the $SLOT thing",
        "i need the full story behind $SLOT",
        "$SLOT — explain",
    )

    fun fallbackPatterns(): List<OpenerPattern> =
        FALLBACK_FRAMES.mapIndexed { index, frame ->
            OpenerPattern(skeleton = frame, specificity = 0.0, sourceId = "fallback:$index")
        }

    private fun frequency(word: String): Double =
        Lexicons.GENERIC_ENGLISH[word] ?: Lexicons.RARE_WORD_FREQUENCY

    const val SLOT = "{detail}"

    private val WORD = Regex("""[\p{L}][\p{L}'’-]*""")
    private const val MIN_PATTERN_WORDS = 4
    private const val MIN_DETAIL_LENGTH = 4
    private const val MAX_SLOT_FREQUENCY = 0.000_5

    private val NON_NOUN_SUFFIXES =
        listOf("ed", "en", "ly", "est", "able", "ible", "ous", "ive", "ing")
}
