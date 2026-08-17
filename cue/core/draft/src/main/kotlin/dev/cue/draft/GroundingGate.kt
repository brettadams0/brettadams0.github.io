package dev.cue.draft

import dev.cue.model.CapturedContext
import dev.cue.model.VoiceProfile
import dev.cue.voice.Lexicons

/**
 * §7.2. Fact-grounding, with zero tolerance.
 *
 * > Any ungrounded specific fails the draft outright. [...] **A missing option
 * > is strictly better than a hallucinated one** — you can't unsend a message
 * > about a dog she doesn't have, and it signals you weren't reading.
 *
 * The method is §7.2's: string matching plus a stopword list, no second model
 * call. Tokenise the draft, drop stopwords, drop common English, drop your own
 * vocabulary, and check whatever survives against the captured context.
 *
 * Two rules the spec implies rather than states, both learned from what the
 * failure actually looks like:
 *
 *  - **Proper nouns are checked even when they are in your vocabulary.** You
 *    may say "Toronto" constantly; saying it *to her* asserts something about
 *    her, and that assertion has to come from the capture.
 *  - **Bare numbers are not specifics.** "thursday at 7" is a proposal, not a
 *    claim about her, and §6.1's ask variants exist to make exactly that
 *    proposal. Rejecting them would gate away the stage that matters most.
 */
class GroundingGate(
    private val voice: VoiceProfile,
) {

    /** Words common enough that using one asserts nothing. */
    private val commonEnglish: Set<String> =
        Lexicons.GENERIC_ENGLISH.filterValues { it >= COMMON_WORD_THRESHOLD }.keys

    /**
     * Terms in [draft] that are not reachable from [context]. Empty means ship.
     */
    fun check(draft: String, context: CapturedContext): List<String> {
        val contextTerms = contextVocabulary(context)
        val ungrounded = mutableListOf<String>()

        WORD.findAll(draft).forEach { match ->
            val raw = match.value
            val word = raw.lowercase().replace('’', '\'')

            if (word.all { it.isDigit() }) return@forEach
            if (word.length < MIN_SPECIFIC_LENGTH) return@forEach
            if (word in PROPOSAL_TERMS) return@forEach

            val isProperNoun = raw.first().isUpperCase() &&
                !isSentenceInitial(draft, match.range.first) &&
                raw != "I"

            if (!isProperNoun) {
                if (word in Stopwords.COMMON) return@forEach
                if (word in commonEnglish) return@forEach
                if (word in Lexicons.GENERIC_ABSTRACT) return@forEach
                if (word in Lexicons.ABBREVIATIONS) return@forEach
                // §7.2 says "content noun". Without a tagger, the closest cheap
                // approximation is to skip the endings that mark a word as a
                // modifier. Deliberately narrow: "-ing" stays in scope, because
                // §0 rule 3's own example — inventing that she likes climbing —
                // is an "-ing" word.
                if (looksLikeModifier(word)) return@forEach
                // §7.2: "drop [...] your own known vocabulary" — but only where
                // the word is genuinely yours.
                //
                // Taken as mere membership, this rule is a hole big enough to
                // drive §0 rule 3 through. Say "dog" once, ever, and every
                // future draft may tell a stranger about her dog. What the rule
                // is for is not flagging your own idiom, and an idiom is
                // something you use repeatedly — so the exemption needs the same
                // floor §4.1 puts on a characteristic token: seen twice is noise.
                if ((voice.vocabulary[word] ?: 0) >= IDIOM_MIN_COUNT) return@forEach
            }

            val stem = Stopwords.stem(word)
            if (stem !in contextTerms && word !in contextTerms) {
                ungrounded += raw
            }
        }

        return ungrounded.distinct()
    }

    /**
     * Everything she said or wrote, plus everything you said in this
     * conversation.
     *
     * Your own prior messages count as context because referring back to what
     * you already told her is not a hallucination — it is the callback strategy
     * in §6.1.
     */
    private fun contextVocabulary(context: CapturedContext): Set<String> {
        val sources = buildList {
            add(context.profile.bio.orEmpty())
            context.profile.prompts.forEach {
                add(it.prompt)
                add(it.answer)
            }
            context.profile.attributes.forEach { (key, value) ->
                add(key)
                add(value)
            }
            addAll(context.profile.photoCaptions)
            context.profile.displayName?.let { add(it) }
            context.messages.forEach { add(it.text) }
        }

        return buildSet {
            sources.forEach { source ->
                WORD.findAll(source).forEach { match ->
                    val word = match.value.lowercase().replace('’', '\'')
                    add(word)
                    add(Stopwords.stem(word))
                }
            }
        }
    }

    /** True when the character at [index] begins the draft or a sentence. */
    private fun isSentenceInitial(draft: String, index: Int): Boolean {
        var i = index - 1
        while (i >= 0 && (draft[i].isWhitespace() || draft[i] == '"' || draft[i] == '\'')) i--
        if (i < 0) return true
        return draft[i] in charArrayOf('.', '!', '?', '…')
    }

    private fun looksLikeModifier(word: String): Boolean =
        MODIFIER_SUFFIXES.any { word.length > it.length + 2 && word.endsWith(it) }

    private companion object {
        val WORD = Regex("""[\p{L}\p{N}]+(?:['’][\p{L}]+)*""")

        val MODIFIER_SUFFIXES = listOf("ly", "able", "ible", "ous", "ive", "ful", "less", "est")

        /**
         * Words that propose rather than assert.
         *
         * §6.1's ask variants exist to name an activity and a rough day, and
         * §6.3 calls READY_TO_ASK the most valuable output in the app. A gate
         * that rejected "coffee on thursday" for being absent from her profile
         * would silently remove the stage that matters most — so a plan is
         * treated like a bare number: an offer about the future, not a claim
         * about her.
         */
        val PROPOSAL_TERMS: Set<String> = setOf(
            "monday", "tuesday", "wednesday", "thursday", "friday", "saturday",
            "sunday", "weekend", "weekday", "morning", "afternoon", "evening",
            "tonight", "tomorrow", "later", "sometime", "soon",
            "coffee", "drink", "drinks", "dinner", "lunch", "brunch", "walk",
            "food", "beer", "wine", "bar", "park", "place", "spot", "plan",
            "plans", "meet", "hang", "grab", "free", "busy", "around",
        )

        /**
         * Above this frequency a word is furniture. Below it, using the word is
         * a choice, and a choice about her needs a source.
         */
        const val COMMON_WORD_THRESHOLD = 0.000_1

        /** Two-letter tokens carry no assertion and trip on OCR noise. */
        const val MIN_SPECIFIC_LENGTH = 3

        /** Uses of a word in your corpus before it counts as your idiom. */
        const val IDIOM_MIN_COUNT = 3
    }
}
