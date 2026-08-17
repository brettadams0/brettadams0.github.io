package dev.cue.voice

import dev.cue.model.VoiceProfile

/**
 * One phrase the compiler deletes, and what it leaves behind.
 *
 * [replacement] is almost always empty — these are fillers, and removing them
 * leaves a grammatical sentence. The exceptions are punctuation the model
 * reaches for and you never type, where deleting outright would fuse two
 * clauses into one run-on.
 */
data class ForbiddenPhrase(
    val pattern: Regex,
    val replacement: String,
    val name: String,
)

/**
 * §4.4's highest-value entry: "models default to a specific cheerful register;
 * deleting the tells does more than any positive instruction."
 *
 * The list splits in two. **Phrases** are deleted unconditionally — nobody's
 * corpus is improved by keeping "that sounds amazing". **Interjections** are
 * deleted only when absent from your corpus, which is what §4.4 means by
 * "lol (if absent from your corpus)". Generalising that condition from `lol` to
 * every single-word tell follows from §4's premise: if you genuinely write
 * "totally", deleting it makes the draft sound *less* like you, and this
 * module exists to do the opposite.
 */
object ForbiddenTokens {

    private val PHRASES = listOf(
        "i'd love to" to "",
        "i would love to" to "",
        "that sounds amazing" to "",
        "sounds amazing" to "",
        "can't wait" to "",
        "cant wait" to "",
        "for sure" to "",
        "let me know" to "",
        "feel free to" to "",
        "no worries" to "",
        "that's awesome" to "",
        "i'm so glad" to "",
    )

    private val INTERJECTIONS = listOf(
        "haha", "hahaha", "lol", "lmao", "totally", "absolutely",
        "definitely", "honestly", "truly", "literally", "amazing",
    )

    /**
     * Model scaffolding: the wrapper a small model puts around the thing you
     * asked for. Not "voice" at all, but it arrives on the same string and
     * nothing else would strip it.
     */
    private val SCAFFOLDING = listOf(
        Regex("""^\s*(?:sure|okay|ok|got it|absolutely)\s*[,!.]\s*""", RegexOption.IGNORE_CASE),
        Regex("""^\s*here(?:'s| is)[^:\n]{0,40}:\s*""", RegexOption.IGNORE_CASE),
        Regex("""^\s*(?:draft|option|reply|response|message|variant)\s*[a-c1-3]?\s*[:\-–—]\s*""", RegexOption.IGNORE_CASE),
        Regex("""^\s*\*\*[^*\n]{1,40}\*\*\s*:?\s*"""),
    )

    /** Paragraphs after the message itself, where a model explains its choice. */
    private val COMMENTARY = Regex(
        """^\s*(?:this|note|i (?:chose|went|kept|used|avoided)|the (?:draft|reply|strategy|tone))\b""",
        RegexOption.IGNORE_CASE,
    )

    fun forProfile(profile: VoiceProfile): List<ForbiddenPhrase> {
        val phrases = PHRASES.map { (phrase, replacement) ->
            ForbiddenPhrase(phraseRegex(phrase), replacement, phrase)
        }
        val interjections = INTERJECTIONS
            .filterNot { it in profile.vocabulary || it in profile.abbreviations }
            .map { ForbiddenPhrase(phraseRegex(it), "", it) }
        return phrases + interjections + punctuationRules(profile)
    }

    /**
     * §4.4 forbids em dashes and semicolons. Deleting them outright fuses the
     * clauses either side into a run-on, so each becomes whatever *you* would
     * have typed instead, read off the profile.
     */
    private fun punctuationRules(profile: VoiceProfile): List<ForbiddenPhrase> {
        val emDashReplacement = if (profile.commaRate >= 1.0f) ", " else " "
        val semicolonReplacement = when {
            profile.commaRate >= 1.0f -> ", "
            profile.terminalPunctuationRate >= 0.3f -> ". "
            else -> " "
        }
        return listOf(
            ForbiddenPhrase(Regex("""\s*[—–]\s*"""), emDashReplacement, "em dash"),
            ForbiddenPhrase(Regex("""\s*;\s*"""), semicolonReplacement, "semicolon"),
        )
    }

    /**
     * Whole-word, case-insensitive, and tolerant of the curly apostrophe an
     * on-device keyboard inserts without asking.
     *
     * Escaped by hand rather than with [Regex.escape], which wraps the whole
     * string in a quote block and would swallow the apostrophe alternation.
     */
    private fun phraseRegex(phrase: String): Regex {
        val body = phrase.split(" ").joinToString("""\s+""", transform = ::literal)
        return Regex("""(?<![\p{L}])$body(?![\p{L}])""", RegexOption.IGNORE_CASE)
    }

    private fun literal(word: String): String = buildString {
        word.forEach { c ->
            when {
                c == '\'' || c == '’' -> append("['’]")
                c.isLetterOrDigit() -> append(c)
                else -> append('\\').append(c)
            }
        }
    }

    /** Strips the wrapper a model puts around the message. */
    fun stripScaffolding(raw: String): String {
        var text = raw.trim()
        // Alternating, because the two nest in both directions: a model writes
        // `Here's a reply: "..."` (quotes inside scaffolding) about as often as
        // it writes `"Sure! ..."` (scaffolding inside quotes). One pass in a
        // fixed order leaves whichever layer it checked first.
        repeat(3) {
            val before = text
            text = stripSurroundingQuotes(text)
            SCAFFOLDING.forEach { text = it.replace(text, "") }
            text = stripSurroundingQuotes(text).trim()
            if (text == before) return@repeat
        }

        val paragraphs = text.split(Regex("""\n\s*\n""")).map { it.trim() }.filter { it.isNotEmpty() }
        if (paragraphs.size > 1) {
            val kept = paragraphs.takeWhile { !COMMENTARY.containsMatchIn(it) }
            text = (kept.ifEmpty { listOf(paragraphs.first()) }).joinToString("\n")
        }
        return text.trim()
    }

    private fun stripSurroundingQuotes(input: String): String {
        val text = input.trim()
        if (text.length <= 1) return text
        return if (text.first() in OPENING_QUOTES && text.last() in CLOSING_QUOTES) {
            text.substring(1, text.length - 1).trim()
        } else {
            text
        }
    }

    private const val OPENING_QUOTES = "\"'“‘"
    private const val CLOSING_QUOTES = "\"'”’"
}
