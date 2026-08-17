package dev.cue.capture

import dev.cue.model.MatchProfile
import dev.cue.model.PromptAnswer

/**
 * §5.4. Reads a profile screenshot into structured fields.
 *
 * The prompt answers are the whole point. §5.4: "Hinge prompt answers are the
 * highest-signal field in the app — volunteered, specific, and literally
 * designed to be responded to." They are also the easiest field to parse
 * reliably, because the prompts themselves are a fixed set of strings that
 * Hinge wrote, not free text.
 *
 * What this deliberately does *not* do is guess at the attribute chips (job,
 * school, height, intent). On a screenshot they arrive as bare strings whose
 * meaning lives in an icon that OCR does not read: "Toronto" and "Monogamy"
 * look identical to a text recogniser. Inventing a key for them would hand
 * §7.2 a context full of unlabelled words and license drafts to assert them.
 * The v2 accessibility path (§5.2) gets them labelled for free; until then the
 * two that can be recognised from their own shape are parsed and the rest are
 * left out.
 */
object ProfileParser {

    /**
     * Hinge's prompt set. Matched by prefix because long prompts wrap and OCR
     * may return only the first line as its own block.
     */
    private val HINGE_PROMPTS = listOf(
        "A life goal of mine",
        "A random fact I love is",
        "All I ask is that you",
        "Believe it or not, I",
        "Best travel story",
        "Change my mind about",
        "Dating me is like",
        "Do you agree or disagree that",
        "First round is on me if",
        "Give me travel tips for",
        "Green flags I look for",
        "I bet you can't",
        "I feel most supported when",
        "I get way too excited about",
        "I go crazy for",
        "I know the best spot in town for",
        "I recently discovered that",
        "I'll fall for you if",
        "I'll pick the topic if you start the conversation",
        "I'm convinced that",
        "I'm looking for",
        "I'm weirdly attracted to",
        "Let's debate this topic",
        "My most controversial opinion is",
        "My most irrational fear",
        "My biggest date fail",
        "My cheat meal is",
        "My Friday nights are",
        "My greatest strength",
        "My love language is",
        "My simple pleasures",
        "My therapist would say I",
        "Something that's non-negotiable for me is",
        "The dorkiest thing about me is",
        "The hallmark of a good relationship is",
        "The key to my heart is",
        "The one thing you should know about me is",
        "The way to win me over is",
        "This year, I really want to",
        "Together we could",
        "Two truths and a lie",
        "Typical Sunday",
        "Unusual skills",
        "We'll get along if",
        "Weirdest gift I have given or received",
        "Worst idea I've ever had",
        "You should leave a comment if",
    )

    private val NAME_AND_AGE = Regex("""^([\p{L}][\p{L}\-' ]{1,24}?),?\s+(\d{2})$""")
    private val AGE_ONLY = Regex("""^(\d{2})$""")
    private val HEIGHT = Regex("""^\d\s?'\s?\d{1,2}\s?["”]?$""")
    private val RELATIONSHIP_INTENT = setOf(
        "long-term relationship", "short-term relationship", "life partner",
        "figuring out my dating goals", "long-term, open to short",
        "short-term, open to long", "new friends", "monogamy",
        "non-monogamy", "figuring out my relationship type",
    )

    /** A block long enough to be prose rather than a chip. */
    private const val BIO_MIN_WORDS = 6

    fun parse(screens: List<RecognizedScreen>, capturedAt: Long): MatchProfile {
        val blocks = screens
            .sortedWith(compareBy({ it.capturedAt }, { it.id }))
            .flatMap { screen ->
                screen.blocks
                    .filterNot { ScreenChrome.isChrome(it, screen) }
                    .sortedBy { it.bounds.top }
            }
            .map { it.text.trim() }
            .filter { it.isNotEmpty() }

        var displayName: String? = null
        var age: Int? = null
        val prompts = mutableListOf<PromptAnswer>()
        val attributes = mutableMapOf<String, String>()
        val leftovers = mutableListOf<String>()

        var index = 0
        while (index < blocks.size) {
            val block = blocks[index]

            val prompt = HINGE_PROMPTS.firstOrNull { matchesPrompt(block, it) }
            if (prompt != null) {
                val answer = blocks.getOrNull(index + 1)
                // A prompt whose answer is another prompt is a prompt she left
                // blank, or an OCR gap. Either way there is nothing to respond to.
                if (answer != null && HINGE_PROMPTS.none { matchesPrompt(answer, it) }) {
                    prompts += PromptAnswer(prompt = block, answer = answer)
                    index += 2
                    continue
                }
            }

            val nameAndAge = NAME_AND_AGE.matchEntire(block)
            when {
                nameAndAge != null && displayName == null -> {
                    displayName = nameAndAge.groupValues[1].trim()
                    age = nameAndAge.groupValues[2].toIntOrNull()
                }

                displayName != null && age == null && AGE_ONLY.matches(block) ->
                    age = block.toIntOrNull()

                HEIGHT.matches(block) ->
                    attributes["height"] = block.replace(Regex("""\s+"""), "")

                block.lowercase() in RELATIONSHIP_INTENT ->
                    attributes["intent"] = block

                displayName == null && looksLikeName(block) ->
                    displayName = block

                else -> leftovers += block
            }
            index++
        }

        val bio = leftovers.filter { wordCount(it) >= BIO_MIN_WORDS }
            .maxByOrNull { it.length }

        return MatchProfile(
            displayName = displayName,
            age = age,
            bio = bio,
            prompts = prompts,
            attributes = attributes,
            // §10: her photos are never processed beyond the text visible in
            // them, and that text is whatever the recogniser already returned.
            photoCaptions = leftovers.filter { it != bio && wordCount(it) < BIO_MIN_WORDS },
            capturedAt = capturedAt,
        )
    }

    private fun matchesPrompt(block: String, prompt: String): Boolean {
        val normalizedBlock = normalize(block)
        val normalizedPrompt = normalize(prompt)
        return normalizedBlock == normalizedPrompt ||
            (normalizedBlock.startsWith(normalizedPrompt) && normalizedBlock.length < normalizedPrompt.length + 4)
    }

    private fun normalize(text: String): String =
        text.lowercase().replace('’', '\'').replace(Regex("""[^\p{L}\p{N}' ]"""), "").trim()

    private fun looksLikeName(block: String): Boolean =
        wordCount(block) <= 2 &&
            block.first().isUpperCase() &&
            block.all { it.isLetter() || it.isWhitespace() || it == '-' || it == '\'' }

    private fun wordCount(text: String): Int =
        text.split(Regex("""\s+""")).count { it.isNotBlank() }
}
