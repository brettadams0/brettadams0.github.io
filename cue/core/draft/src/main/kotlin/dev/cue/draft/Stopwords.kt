package dev.cue.draft

/**
 * The stopword list §7.2 asks for, and the light stemmer retrieval needs.
 *
 * §7.2 is explicit that the fact-grounding gate is "string matching plus a
 * stopword list, not another model call". That constraint is what makes the
 * gate free enough to run on every draft, every time, which is what makes zero
 * tolerance affordable.
 */
object Stopwords {

    val COMMON: Set<String> = setOf(
        "a", "about", "above", "after", "again", "all", "am", "an", "and", "any",
        "are", "aren't", "as", "at", "be", "because", "been", "before", "being",
        "below", "between", "both", "but", "by", "can", "can't", "cannot",
        "could", "couldn't", "did", "didn't", "do", "does", "doesn't", "doing",
        "don't", "down", "during", "each", "few", "for", "from", "further",
        "had", "hadn't", "has", "hasn't", "have", "haven't", "having", "he",
        "her", "here", "hers", "herself", "him", "himself", "his", "how", "i",
        "i'd", "i'll", "i'm", "i've", "if", "in", "into", "is", "isn't", "it",
        "it's", "its", "itself", "just", "let's", "me", "more", "most", "my",
        "myself", "no", "nor", "not", "of", "off", "on", "once", "only", "or",
        "other", "ought", "our", "ours", "ourselves", "out", "over", "own",
        "same", "she", "should", "shouldn't", "so", "some", "such", "than",
        "that", "that's", "the", "their", "theirs", "them", "themselves",
        "then", "there", "these", "they", "this", "those", "through", "to",
        "too", "under", "until", "up", "very", "was", "wasn't", "we", "were",
        "what", "when", "where", "which", "while", "who", "whom", "why",
        "with", "won't", "would", "wouldn't", "you", "your", "yours",
        "yourself", "yourselves", "yeah", "yep", "nah", "ok", "okay", "im",
        "u", "ur", "got", "get", "go", "going", "gonna", "wanna", "like",
        "really", "actually", "still", "even", "also", "much", "many", "one",
        "two", "way", "thing", "things", "something", "anything", "nothing",
        "someone", "anyone", "everyone", "know", "think", "want", "need",
        "make", "made", "take", "took", "come", "came", "see", "saw", "say",
        "said", "tell", "told", "look", "looks", "feel", "feels", "sounds",
        "sound", "good", "bad", "nice", "great", "sure", "maybe", "well",
        "now", "then", "today", "tomorrow", "tonight", "yesterday", "time",
        "day", "week", "weekend", "month", "year", "always", "never", "ever",
        "back", "down", "around", "though", "than", "yet", "since",
        // Quantifiers and placeholders. Not stopwords in a linguist's list, but
        // "a lot of bones" asserts nothing about bones, and §7.2 rejecting "lot"
        // as an invented specific deletes a perfectly good draft.
        "lot", "lots", "bit", "kind", "sort", "stuff", "loads", "plenty",
        "couple", "few", "bunch", "whole", "half", "part", "rest", "end",
    )

    /**
     * Strips the endings that make the same word look like two.
     *
     * Not a real stemmer, and deliberately conservative: over-stemming would
     * make §7.2 accept "climbers" as evidence for "climb" in one direction and
     * "clams" as evidence for "clam" in the other. The four rules here cover
     * plurals and progressives, which is where the recall actually is.
     */
    fun stem(word: String): String {
        if (word.length <= 3) return word
        return when {
            word.endsWith("ies") && word.length > 4 -> word.dropLast(3) + "y"
            word.endsWith("sses") -> word.dropLast(2)
            word.endsWith("s") && !word.endsWith("ss") && !word.endsWith("us") -> word.dropLast(1)
            word.endsWith("ing") && word.length > 5 -> undouble(word.dropLast(3))
            word.endsWith("ed") && word.length > 4 -> undouble(word.dropLast(2))
            else -> word
        }
    }

    /**
     * Collapses the consonant English doubles before "-ing" and "-ed".
     *
     * Without it, "swimming" stems to "swimm" while "swim" stems to itself, and
     * §7.2 reports a draft asking about swimming as ungrounded against a profile
     * that says she cannot swim. The gate is zero-tolerance, so a stemmer that
     * misses a match does not produce a warning — it deletes a draft.
     */
    private fun undouble(stem: String): String {
        if (stem.length < 3) return stem
        val last = stem[stem.length - 1]
        val previous = stem[stem.length - 2]
        val isVowel = last in "aeiou"
        return if (last == previous && !isVowel && last !in "ls") stem.dropLast(1) else stem
    }

    /** Content terms: lowercased, stopword-free, stemmed. */
    fun contentTerms(text: String): List<String> =
        Regex("""[\p{L}\p{N}]+(?:['’][\p{L}]+)*""").findAll(text)
            .map { it.value.lowercase().replace('’', '\'') }
            .filter { it !in COMMON }
            .filter { it.any { c -> c.isLetter() } }
            .map(::stem)
            .toList()
}
