package dev.cue.voice

/**
 * The shared text primitives. Everything in §4 and §7 is built on these, so
 * they are defined once and tested directly rather than re-derived per call
 * site with slightly different regexes.
 */
object Text {
    /**
     * A word is letters and digits, optionally carrying an internal apostrophe
     * so "don't" and "i'm" survive tokenisation intact — the contraction rate
     * (§4.1) is meaningless if the tokeniser splits them.
     */
    private val WORD = Regex("""[\p{L}\p{N}]+(?:['’][\p{L}]+)*""")

    /** Sentence-ending punctuation as it appears in messages, ellipsis included. */
    val TERMINAL_PUNCTUATION = charArrayOf('.', '!', '?', '…')

    fun words(text: String): List<String> = WORD.findAll(text).map { it.value }.toList()

    fun wordCount(text: String): Int = WORD.findAll(text).count()

    /** Lowercased and stripped of curly apostrophes, for comparison and counting. */
    fun normalize(word: String): String = word.lowercase().replace('’', '\'')

    fun normalizedWords(text: String): List<String> = words(text).map(::normalize)

    /** The first alphabetic character, skipping leading emoji, quotes and spaces. */
    fun firstLetter(text: String): Char? = text.firstOrNull { it.isLetter() }

    /** True when the message ends in terminal punctuation, ignoring trailing emoji. */
    fun endsWithTerminalPunctuation(text: String): Boolean {
        val last = trimTrailingNonPunctuation(text).lastOrNull() ?: return false
        return last in TERMINAL_PUNCTUATION
    }

    /**
     * True when the message is a question, ignoring trailing emoji.
     *
     * Shares [trimTrailingNonPunctuation] with [endsWithTerminalPunctuation] on
     * purpose. When the two disagreed — a plain `endsWith('?')` here and an
     * emoji-tolerant check there — "how was your week? 😂" counted as a
     * statement *and* as punctuated, which is the only combination that cannot
     * be true, and it put a lowercase writer's statement-punctuation rate above
     * zero.
     */
    fun endsWithQuestionMark(text: String): Boolean =
        trimTrailingNonPunctuation(text).endsWith('?')

    /**
     * Walks backwards by code point, not by `Char`.
     *
     * Every modern emoji is a surrogate pair, so a `Char`-wise scan sees a low
     * surrogate that matches no emoji range, stops there, and concludes that
     * "that's the plan. 😂" does not end in punctuation. The consequence is a
     * `terminalPunctuationRate` measured on the wrong messages.
     */
    private fun trimTrailingNonPunctuation(text: String): String {
        var end = text.length
        while (end > 0) {
            val cp = text.codePointBefore(end)
            val skippable = Character.isWhitespace(cp) ||
                Emoji.isEmojiCodePoint(cp) ||
                cp in EMOJI_MODIFIERS ||
                cp == '"'.code ||
                cp == '\''.code
            if (!skippable) break
            end -= Character.charCount(cp)
        }
        return text.substring(0, end)
    }

    /** Variation selectors, ZWJ and skin tones — trailing, never terminal. */
    private val EMOJI_MODIFIERS = setOf(0xFE0F, 0xFE0E, 0x200D, 0x20E3) +
        (0x1F3FB..0x1F3FF).toSet()

    fun countChar(text: String, c: Char): Int = text.count { it == c }

    /**
     * Occurrences of the standalone first-person pronoun, in both cases.
     *
     * Standalone means not part of a longer word — "in" and "island" must not
     * count — but "I'm" and "I'd" do, because they are the same pronoun and the
     * capitalisation decision is identical.
     */
    private val STANDALONE_I = Regex("""(?<![\p{L}])([Ii])(?=(?:['’][\p{L}]+)?(?![\p{L}]))""")

    fun standaloneIOccurrences(text: String): List<MatchResult> =
        STANDALONE_I.findAll(text).toList()

    /**
     * Splits into clauses at punctuation and coordinating conjunctions, keeping
     * the boundary offsets. §4.4's length transform truncates *at* a boundary
     * rather than mid-phrase, which is the difference between a short message
     * and a message that got cut off.
     */
    fun clauseBoundaries(text: String): List<Int> {
        val boundaries = mutableListOf<Int>()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c in TERMINAL_PUNCTUATION || c == ',' || c == ';') {
                // Consume a run, so "..." and "?!" yield one boundary.
                var end = i + 1
                while (end < text.length && (text[end] in TERMINAL_PUNCTUATION || text[end] == ',' || text[end] == ';')) {
                    end++
                }
                boundaries += end
                i = end
            } else {
                i++
            }
        }
        for (conjunction in CONJUNCTIONS) {
            var from = 0
            while (true) {
                val at = text.indexOf(conjunction, from, ignoreCase = true)
                if (at < 0) break
                boundaries += at
                from = at + 1
            }
        }
        return boundaries.filter { it in 1 until text.length }.distinct().sorted()
    }

    private val CONJUNCTIONS = listOf(" and ", " but ", " so ", " because ", " though ", " while ")

    /** Collapses runs of whitespace and trims. Applied before anything measures length. */
    fun tidy(text: String): String = text.replace(Regex("""[ \t]+"""), " ")
        .replace(Regex(""" ?\n ?"""), "\n")
        .trim()
}

/**
 * Emoji scanning by code point.
 *
 * Kotlin has no emoji API, and treating each `Char` as one emoji is wrong for
 * everything above the BMP — which is most emoji. §4.1's `emojiRate` and §4.4's
 * trim both need whole graphemes: a family emoji is one emoji, not four, and
 * removing "the third char" of one produces mojibake in a message you are about
 * to send someone.
 */
object Emoji {
    private const val ZWJ = 0x200D
    private const val VS16 = 0xFE0F
    private const val VS15 = 0xFE0E
    private const val KEYCAP = 0x20E3
    private val SKIN_TONES = 0x1F3FB..0x1F3FF
    private val REGIONAL_INDICATORS = 0x1F1E6..0x1F1FF

    private val BASE_RANGES = listOf(
        0x1F000..0x1F02F, // mahjong, dominoes
        0x1F0A0..0x1F0FF, // playing cards
        0x1F300..0x1FAFF, // the bulk of modern emoji
        0x2600..0x27BF, // misc symbols and dingbats
        0x2B00..0x2BFF, // stars, arrows
        0x2190..0x21FF, // arrows (only ever emoji with VS16, but harmless here)
        0x2900..0x297F,
        0x3030..0x3030,
        0x303D..0x303D,
        0x3297..0x3299,
    )

    fun isEmojiCodePoint(cp: Int): Boolean =
        cp in REGIONAL_INDICATORS || BASE_RANGES.any { cp in it }

    /**
     * Returns each emoji's character range, with ZWJ sequences, skin-tone
     * modifiers, keycaps, flags and variation selectors folded into one entry.
     */
    fun ranges(text: String): List<IntRange> {
        val out = mutableListOf<IntRange>()
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            val width = Character.charCount(cp)
            if (!isEmojiCodePoint(cp)) {
                // A lone digit or '#' followed by a keycap is one emoji.
                if (i + width < text.length && text.codePointAt(i + width) == VS16 &&
                    i + width + 1 < text.length && text.codePointAt(i + width + 1) == KEYCAP
                ) {
                    out += i..(i + width + 1)
                    i += width + 2
                    continue
                }
                i += width
                continue
            }
            var end = i + width
            if (cp in REGIONAL_INDICATORS && end < text.length) {
                val next = text.codePointAt(end)
                if (next in REGIONAL_INDICATORS) end += Character.charCount(next)
            }
            while (end < text.length) {
                val next = text.codePointAt(end)
                val nextWidth = Character.charCount(next)
                when {
                    next == VS16 || next == VS15 || next == KEYCAP -> end += nextWidth
                    next in SKIN_TONES -> end += nextWidth
                    next == ZWJ -> {
                        val after = end + nextWidth
                        if (after >= text.length) break
                        end = after + Character.charCount(text.codePointAt(after))
                    }
                    else -> break
                }
            }
            out += i until end
            i = end
        }
        return out
    }

    fun findAll(text: String): List<String> = ranges(text).map { text.substring(it.first, it.last + 1) }

    fun count(text: String): Int = ranges(text).size
}
