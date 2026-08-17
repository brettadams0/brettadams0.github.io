package dev.cue.voice

import dev.cue.model.VoiceProfile
import dev.cue.model.VoiceTransform
import kotlin.math.roundToInt

/**
 * The result of compiling one model output into your voice.
 *
 * [needsRegeneration] is set when the transforms left too little behind to be a
 * message — a draft that was mostly filler comes out of here as two words, and
 * shipping that is worse than shipping nothing. §7.1's retry loop reads it.
 */
data class CompiledDraft(
    val text: String,
    val transforms: List<VoiceTransform>,
    val removedPhrases: List<String> = emptyList(),
    val needsRegeneration: Boolean = false,
)

/**
 * §4.4. The voice compiler.
 *
 * The model produces content. This produces voice, deterministically, with no
 * inference involved. Every transform here is something a small model reliably
 * fails at and a regex reliably succeeds at, which is why §4.4 calls spending
 * inference budget on capitalisation a waste.
 *
 * Order matters and is not arbitrary:
 *
 *  1. scaffolding and forbidden phrases first, so later measurements (comma
 *     rate, length) describe the message rather than the model's packaging;
 *  2. word-level rewrites next, which change length;
 *  3. punctuation and length after that, once the words are final;
 *  4. capitalisation last, so nothing downstream re-capitalises what we lowered.
 *
 * Instant, deterministic, and testable — the three things inference is not.
 */
class VoiceCompiler(private val profile: VoiceProfile) {

    private val forbidden = ForbiddenTokens.forProfile(profile)

    /** Below this, the message is fragments rather than a message. */
    private val minimumViableWords = 2

    fun compile(raw: String): CompiledDraft {
        val transforms = mutableListOf<VoiceTransform>()
        val removed = mutableListOf<String>()

        var text = ForbiddenTokens.stripScaffolding(raw)

        text = applyForbidden(text, transforms, removed)
        text = tidy(text)

        if (VoicePolicy.contracts(profile)) {
            val contracted = applyContractions(text)
            if (contracted != text) {
                transforms += VoiceTransform.CONTRACT
                text = contracted
            }
        }

        val abbreviated = applyAbbreviations(text)
        if (abbreviated != text) {
            transforms += VoiceTransform.ABBREVIATED
            text = abbreviated
        }

        text = applyEmojiPolicy(text, transforms)
        text = applyExclamationPolicy(text, transforms)
        text = applyEllipsisPolicy(text, transforms)
        text = applyCommaThinning(text, transforms)
        text = applyLengthCeiling(text, transforms)
        text = applyTerminalPunctuation(text, transforms)
        text = applyCapitalization(text, transforms)
        text = applyLowercaseI(text, transforms)

        text = tidy(text)

        return CompiledDraft(
            text = text,
            transforms = transforms.distinct(),
            removedPhrases = removed,
            needsRegeneration = Text.wordCount(text) < minimumViableWords,
        )
    }

    // -- §4.4 forbidden tokens ------------------------------------------

    private fun applyForbidden(
        input: String,
        transforms: MutableList<VoiceTransform>,
        removed: MutableList<String>,
    ): String {
        var text = input
        forbidden.forEach { phrase ->
            if (phrase.pattern.containsMatchIn(text)) {
                removed += phrase.name
                text = phrase.pattern.replace(text, phrase.replacement)
                transforms += VoiceTransform.FORBIDDEN_TOKEN_DELETED
            }
        }
        return text
    }

    // -- §4.4 contraction ------------------------------------------------

    private fun applyContractions(input: String): String {
        var text = input
        // Longest first: "can not" must win over any shorter key inside it.
        Lexicons.CONTRACTIONS.entries
            .sortedByDescending { it.key.length }
            .forEach { (long, short) ->
                val pattern = Regex(
                    """(?<![\p{L}])${long.split(" ").joinToString("""\s+""")}(?![\p{L}])""",
                    RegexOption.IGNORE_CASE,
                )
                text = pattern.replace(text) { match ->
                    if (match.value.first().isUpperCase()) {
                        short.replaceFirstChar { it.uppercaseChar() }
                    } else {
                        short
                    }
                }
            }
        return text
    }

    // -- §4.4 abbreviation ------------------------------------------------

    /**
     * Substitutes only where your corpus shows the short form winning.
     *
     * Restricted to single-word expansions on purpose. Multi-word ones ("right
     * now" → "rn") cannot be dominance-tested, because the profile counts words
     * and not phrases, and substituting on a guess is exactly the kind of
     * confident wrongness §4 exists to remove.
     */
    private fun applyAbbreviations(input: String): String {
        var text = input
        Lexicons.ABBREVIATION_EXPANSIONS.forEach { (abbreviation, expansions) ->
            val abbreviationCount = profile.vocabulary[abbreviation] ?: 0
            expansions.filter { !it.contains(' ') }.forEach { expansion ->
                val expansionCount = profile.vocabulary[expansion] ?: 0
                if (abbreviationCount > expansionCount && abbreviationCount >= 3) {
                    val pattern = Regex("""(?<![\p{L}])$expansion(?![\p{L}])""", RegexOption.IGNORE_CASE)
                    text = pattern.replace(text) { match ->
                        if (match.value.first().isUpperCase()) {
                            abbreviation.replaceFirstChar { it.uppercaseChar() }
                        } else {
                            abbreviation
                        }
                    }
                }
            }
        }
        return text
    }

    // -- §4.4 emoji -------------------------------------------------------

    /**
     * Trims to your rate, then substitutes from [VoiceProfile.topEmoji].
     *
     * The substitution matters more than the trim. A model reaches for 😊 and
     * 🙌 regardless of who it is writing as; using the two emoji you actually
     * use is a stronger signal of authorship than the count.
     */
    private fun applyEmojiPolicy(input: String, transforms: MutableList<VoiceTransform>): String {
        val allowed = VoicePolicy.allowedEmoji(profile)
        var text = input

        val ranges = Emoji.ranges(text)
        if (ranges.size > allowed) {
            val builder = StringBuilder(text)
            ranges.drop(allowed).asReversed().forEach { range ->
                builder.delete(range.first, range.last + 1)
            }
            text = builder.toString()
            transforms += VoiceTransform.EMOJI_TRIM
        }

        if (profile.topEmoji.isNotEmpty()) {
            val preferred = profile.topEmoji.first()
            val remaining = Emoji.ranges(text)
            val builder = StringBuilder(text)
            var substituted = false
            remaining.asReversed().forEach { range ->
                val emoji = text.substring(range.first, range.last + 1)
                if (emoji !in profile.topEmoji) {
                    builder.replace(range.first, range.last + 1, preferred)
                    substituted = true
                }
            }
            if (substituted) {
                text = builder.toString()
                transforms += VoiceTransform.EMOJI_SUBSTITUTE
            }
        }
        return text
    }

    // -- §4.4 exclamation marks beyond your rate --------------------------

    private fun applyExclamationPolicy(input: String, transforms: MutableList<VoiceTransform>): String {
        var text = input.replace(Regex("""!{2,}"""), "!")
        // One in a short message is inside your range even when the
        // per-100-words expectation rounds to nothing — see VoicePolicy.
        val allowed = VoicePolicy.allowedExclamations(profile, Text.wordCount(text))
        val present = Text.countChar(text, '!')
        if (present <= allowed) return text

        val replacement = if (VoicePolicy.stripsTerminalPeriod(profile)) "" else "."
        var seen = 0
        text = buildString {
            text.forEach { c ->
                if (c == '!') {
                    seen++
                    if (seen <= allowed) append(c) else append(replacement)
                } else {
                    append(c)
                }
            }
        }
        transforms += VoiceTransform.EXCLAMATION_TRIM
        return text
    }

    // -- §4.1 ellipsis ----------------------------------------------------

    private fun applyEllipsisPolicy(input: String, transforms: MutableList<VoiceTransform>): String {
        val hasEllipsis = input.contains("…") || Regex("""\.{2,}""").containsMatchIn(input)
        if (!hasEllipsis) return input
        return if (VoicePolicy.stripsEllipsis(profile)) {
            transforms += VoiceTransform.ELLIPSIS_NORMALIZED
            input.replace(Regex("""\s*(?:\.{2,}|…)\s*"""), " ")
        } else {
            // Typed, not typeset: nobody reaches for the single-character form
            // on a phone keyboard.
            transforms += VoiceTransform.ELLIPSIS_NORMALIZED
            input.replace("…", "...").replace(Regex("""\.{4,}"""), "...")
        }
    }

    // -- §4.4 comma thinning ----------------------------------------------

    /**
     * Thins commas toward an allowance, not toward the rate.
     *
     * A per-100-words rate cannot be applied directly to a nine-word message:
     * one comma in nine words is 11 per 100, so a rate of 4 would strip a
     * comma-user's list bare. The allowance is the expected count for *this*
     * length, with a floor of one for anyone who demonstrably uses commas at
     * all — the same shape as the exclamation rule above, for the same reason.
     */
    private fun applyCommaThinning(input: String, transforms: MutableList<VoiceTransform>): String {
        val words = Text.wordCount(input)
        if (words == 0) return input

        val present = Text.countChar(input, ',')
        if (present == 0) return input

        val allowed = VoicePolicy.allowedCommas(profile, words)
        if (present <= allowed) return input

        // Serial commas first: they are the ones a model adds and a phone
        // keyboard never produces.
        var text = input.replace(Regex(""",(\s+(?:and|or)\s)"""), "$1")
        while (Text.countChar(text, ',') > allowed) {
            val last = text.lastIndexOf(',')
            if (last < 0) break
            text = text.removeRange(last, last + 1)
        }
        if (text != input) transforms += VoiceTransform.COMMA_THINNING
        return text
    }

    // -- §4.4 length ------------------------------------------------------

    /**
     * Truncates at the last clause boundary under the ceiling.
     *
     * Cutting mid-phrase produces a message that looks like it was interrupted,
     * which is a worse failure than a long one — so when no boundary fits, the
     * hard cut strips any dangling function word it lands on.
     */
    private fun applyLengthCeiling(input: String, transforms: MutableList<VoiceTransform>): String {
        val ceiling = profile.maxDraftWords
        if (Text.wordCount(input) <= ceiling) return input

        val boundary = Text.clauseBoundaries(input)
            .filter { Text.wordCount(input.substring(0, it)) in minimumViableWords..ceiling }
            .maxOrNull()

        val truncated = if (boundary != null) {
            input.substring(0, boundary)
        } else {
            hardTruncate(input, ceiling)
        }
        transforms += VoiceTransform.TRUNCATED_TO_LENGTH
        return truncated.trimEnd().trimEnd(',', ';', '-')
    }

    private fun hardTruncate(input: String, ceiling: Int): String {
        val wordEnds = Regex("""[\p{L}\p{N}]+(?:['’][\p{L}]+)*""").findAll(input)
            .map { it.range.last + 1 }
            .toList()
        if (wordEnds.size <= ceiling) return input
        var text = input.substring(0, wordEnds[ceiling - 1])
        // A message ending "and" or "to" reads as cut off, because it is.
        while (true) {
            val words = Text.words(text)
            val lastWord = words.lastOrNull()?.let(Text::normalize) ?: break
            if (lastWord in DANGLING_TAIL_WORDS && words.size > minimumViableWords) {
                text = text.substring(0, text.lastIndexOf(words.last())).trimEnd()
            } else {
                break
            }
        }
        return text
    }

    // -- §4.4 de-punctuate -------------------------------------------------

    private fun applyTerminalPunctuation(input: String, transforms: MutableList<VoiceTransform>): String {
        if (!VoicePolicy.stripsTerminalPeriod(profile)) return input
        val trimmed = input.trimEnd()
        // Never the question mark: it is not decoration, it is the ask.
        if (!trimmed.endsWith('.')) return input
        transforms += VoiceTransform.STRIP_TERMINAL_PERIOD
        return trimmed.trimEnd('.')
    }

    // -- §4.4 lowercase ----------------------------------------------------

    private fun applyCapitalization(input: String, transforms: MutableList<VoiceTransform>): String {
        if (!VoicePolicy.lowercasesLead(profile)) return input

        val builder = StringBuilder(input)
        var changed = false

        val leadIndex = input.indexOfFirst { it.isLetter() }
        if (leadIndex >= 0 && input[leadIndex].isUpperCase()) {
            builder.setCharAt(leadIndex, input[leadIndex].lowercaseChar())
            changed = true
        }

        // Subsequent sentences, but only where the opening word cannot be a
        // name. See Lexicons.SAFE_LEAD_WORDS for why this is not unconditional.
        Regex("""[.!?…]["')]?\s+([\p{L}]+)""").findAll(builder.toString()).forEach { match ->
            val group = match.groups[1] ?: return@forEach
            val word = group.value
            if (word.first().isUpperCase() && Text.normalize(word) in Lexicons.SAFE_LEAD_WORDS) {
                builder.setCharAt(group.range.first, word.first().lowercaseChar())
                changed = true
            }
        }

        if (changed) transforms += VoiceTransform.LOWERCASE_LEAD
        return builder.toString()
    }

    private fun applyLowercaseI(input: String, transforms: MutableList<VoiceTransform>): String {
        if (!VoicePolicy.lowercasesI(profile)) return input
        val occurrences = Text.standaloneIOccurrences(input).filter { it.value == "I" }
        if (occurrences.isEmpty()) return input
        val builder = StringBuilder(input)
        occurrences.forEach { builder.setCharAt(it.range.first, 'i') }
        transforms += VoiceTransform.LOWERCASE_I
        return builder.toString()
    }

    // -- shared -------------------------------------------------------------

    /**
     * Repairs the wreckage deletion leaves: doubled spaces, a comma with
     * nothing before it, a message that now starts with punctuation.
     */
    private fun tidy(input: String): String = input
        .replace(Regex(""" +"""), " ")
        .replace(Regex(""" +([,.!?…])"""), "$1")
        .replace(Regex("""(,\s*){2,}"""), ", ")
        .replace(Regex("""^[\s,;:.!]+"""), "")
        .replace(Regex("""[ ,;]+$"""), "")
        .trim()

    private companion object {
        val DANGLING_TAIL_WORDS = setOf(
            "and", "but", "so", "or", "the", "a", "an", "to", "of", "in", "on",
            "at", "for", "with", "about", "that", "if", "because", "than",
            "my", "your", "is", "was", "are", "were", "i", "you", "it",
        )
    }
}
