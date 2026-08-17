package dev.cue.voice

import dev.cue.model.VoiceProfile
import dev.cue.model.VoiceTransform
import dev.cue.testing.SyntheticCorpus
import dev.cue.testing.SyntheticVoice
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * §14.2 — voice compiler correctness, one test per transform in §4.4.
 *
 * "Pure functions, trivially testable, and they carry most of the voice
 * quality." That is the trade the whole architecture rests on: these tests run
 * in milliseconds and cover the failures a 2B model makes most often.
 */
class VoiceCompilerTest {

    private val lowercase = VoiceProfile.BASELINE.copy(
        sampleCount = 200,
        medianWords = 8f,
        p90Words = 16f,
        capitalizationRate = 0.05f,
        lowercaseIRate = 0.95f,
        terminalPunctuationRate = 0.04f,
        ellipsisRate = 0.02f,
        commaRate = 0.4f,
        exclamationRate = 0.1f,
        emojiRate = 0.1f,
        topEmoji = listOf("😂"),
        contractionRate = 0.95f,
        vocabulary = mapOf("sounds" to 40, "good" to 30, "ferry" to 12),
    )

    private val proper = VoiceProfile.BASELINE.copy(
        sampleCount = 200,
        medianWords = 14f,
        p90Words = 28f,
        capitalizationRate = 0.98f,
        lowercaseIRate = 0.0f,
        terminalPunctuationRate = 0.95f,
        commaRate = 4f,
        exclamationRate = 1.5f,
        emojiRate = 0f,
        contractionRate = 0.2f,
    )

    private fun compile(profile: VoiceProfile, raw: String) = VoiceCompiler(profile).compile(raw)

    // -- §4.4 lowercase ---------------------------------------------------

    @Test
    fun `downcases the leading character for a lowercase writer`() {
        val result = compile(lowercase, "Sounds good to me")
        assertEquals("sounds good to me", result.text)
        assertTrue(VoiceTransform.LOWERCASE_LEAD in result.transforms)
    }

    @Test
    fun `leaves capitals alone for a writer who uses them`() {
        val result = compile(proper, "Sounds good to me.")
        assertEquals("Sounds good to me.", result.text)
        assertFalse(VoiceTransform.LOWERCASE_LEAD in result.transforms)
    }

    @Test
    fun `downcases a later sentence only when it cannot be a name`() {
        val result = compile(lowercase, "yeah. That works")
        assertEquals("yeah. that works", result.text)

        val name = compile(lowercase, "yeah. Toronto works")
        assertEquals("yeah. Toronto works", name.text)
    }

    // -- §4.4 de-punctuate -------------------------------------------------

    @Test
    fun `strips a trailing period but never a question mark`() {
        assertEquals("that works", compile(lowercase, "that works.").text)
        assertEquals("does that work?", compile(lowercase, "does that work?").text)
    }

    // -- §4.4 lowercase-I ---------------------------------------------------

    @Test
    fun `lowercases standalone I without touching other capitals`() {
        val result = compile(lowercase, "I think I saw Toronto from the ferry")
        assertEquals("i think i saw Toronto from the ferry", result.text)
        assertTrue(VoiceTransform.LOWERCASE_I in result.transforms)
    }

    // -- §4.4 emoji ---------------------------------------------------------

    @Test
    fun `trims emoji beyond the profile rate and substitutes from topEmoji`() {
        val result = compile(lowercase, "sounds good 🙌 really 🎉")
        assertEquals(0, Emoji.count(result.text))
        assertTrue(VoiceTransform.EMOJI_TRIM in result.transforms)
    }

    @Test
    fun `keeps one emoji and makes it yours when your rate allows one`() {
        val emojiUser = lowercase.copy(emojiRate = 1.2f, topEmoji = listOf("😂", "🙃"))
        val result = compile(emojiUser, "that is wild 🎉")
        assertEquals("😂", Emoji.findAll(result.text).single())
        assertTrue(VoiceTransform.EMOJI_SUBSTITUTE in result.transforms)
    }

    // -- §4.4 comma thinning -------------------------------------------------

    @Test
    fun `drops serial commas first`() {
        val result = compile(lowercase, "we could do coffee, a walk, or the market")
        assertFalse(result.text.contains(", or"), result.text)
        assertTrue(VoiceTransform.COMMA_THINNING in result.transforms)
    }

    /**
     * A comma-user's message keeps its commas. It does not keep the serial one:
     * a nine-word list at 4 commas per 100 words is allowed one, and the Oxford
     * comma is the one that goes.
     */
    @Test
    fun `does not strip a comma user's message bare`() {
        val result = compile(proper, "We could do coffee, a walk, or the market.")
        assertEquals(1, Text.countChar(result.text, ','), result.text)
        assertTrue(result.text.contains("coffee,"), result.text)
    }

    // -- §4.4 contraction ----------------------------------------------------

    @Test
    fun `contracts for a contractor and preserves case`() {
        val result = compile(lowercase, "I do not think that is right")
        assertTrue(result.text.contains("don't"), result.text)
        assertTrue(result.text.contains("that's"), result.text)
        assertTrue(VoiceTransform.CONTRACT in result.transforms)
    }

    @Test
    fun `does not contract for someone who writes it out`() {
        val result = compile(proper, "I do not think that is right.")
        assertTrue(result.text.contains("do not"), result.text)
    }

    // -- §4.4 forbidden tokens -----------------------------------------------

    @Test
    fun `deletes the cheerful register`() {
        val result = compile(lowercase, "That sounds amazing, I'd love to. Can't wait!")
        listOf("sounds amazing", "love to", "can't wait").forEach {
            assertFalse(result.text.contains(it, ignoreCase = true), "left '$it' in: ${result.text}")
        }
        assertTrue(VoiceTransform.FORBIDDEN_TOKEN_DELETED in result.transforms)
    }

    @Test
    fun `keeps lol when your corpus contains it`() {
        val lolUser = lowercase.copy(vocabulary = lowercase.vocabulary + ("lol" to 90))
        assertTrue(compile(lolUser, "lol that tracks").text.contains("lol"))
        assertFalse(compile(lowercase, "lol that tracks").text.contains("lol"))
    }

    @Test
    fun `replaces em dashes and semicolons rather than deleting them`() {
        val result = compile(proper, "I went there — it was fine; the walk back was better.")
        assertFalse(result.text.contains("—"), result.text)
        assertFalse(result.text.contains(";"), result.text)
        assertTrue(result.text.contains("it was fine"), result.text)
        assertTrue(result.text.contains("the walk back"), result.text)
    }

    // -- §4.4 length ----------------------------------------------------------

    @Test
    fun `truncates at a clause boundary under the ceiling`() {
        val long = "that place is genuinely great, the coffee is unreasonable and " +
            "the walk back is the best part of the whole thing honestly"
        val result = compile(lowercase, long)
        assertTrue(Text.wordCount(result.text) <= lowercase.maxDraftWords, result.text)
        assertTrue(VoiceTransform.TRUNCATED_TO_LENGTH in result.transforms)
        assertFalse(result.text.trim().endsWith("and"), "left a dangling conjunction: ${result.text}")
    }

    @Test
    fun `hard truncation does not end on a dangling word`() {
        val noBoundaries = (1..40).joinToString(" ") { "word$it" } + " and"
        val result = compile(lowercase, noBoundaries)
        assertFalse(result.text.trim().endsWith(" and"), result.text)
    }

    // -- model scaffolding -----------------------------------------------------

    @Test
    fun `strips the wrapper a small model puts around the message`() {
        assertEquals(
            "that place is great",
            compile(lowercase, "Sure! Here's a reply: \"That place is great.\"").text,
        )
        assertEquals(
            "that works for me",
            compile(lowercase, "Draft A: That works for me.\n\nThis keeps the tone light.").text,
        )
    }

    @Test
    fun `flags a draft that was mostly filler`() {
        val result = compile(lowercase, "Absolutely, totally!")
        assertTrue(result.needsRegeneration, "left: '${result.text}'")
    }

    // -- §7.1's claim, tested rather than assumed --------------------------------

    /**
     * §7.1 says the compiler "should make most of these pass by construction".
     * If that were false the retry loop would spin on every draft, so it is
     * asserted over a corpus-derived profile and a spread of model-flavoured
     * inputs rather than trusted.
     */
    @Test
    fun `compiler output passes its own verifier`() {
        val profile = VoiceProfiler.profile(SyntheticCorpus.messages(120, SyntheticVoice.LOWERCASE))
        val compiler = VoiceCompiler(profile)
        val verifier = StyleVerifier(profile)

        val modelOutputs = listOf(
            "That sounds amazing! I'd love to hear more about it 😊",
            "Here's my reply: \"Absolutely — I totally get that, honestly.\"",
            "I definitely think the ferry is the best part; can't wait to try it!!!",
            "Sure! You should definitely go, it's great, and I would love to join.",
            "Two truths and a lie is a strong opener, I have to say. Which one is it?",
            "I am not sure that is true, but I would like to know more about it.",
        )

        modelOutputs.forEach { raw ->
            val compiled = compiler.compile(raw)
            if (compiled.needsRegeneration) return@forEach
            val deviations = verifier.verify(compiled.text)
            assertTrue(
                deviations.isEmpty(),
                "compiled '${compiled.text}' still deviates: $deviations",
            )
        }
    }

    @Test
    fun `compilation is deterministic`() {
        val raw = "Honestly? That sounds amazing — I'd definitely go!!"
        assertEquals(compile(lowercase, raw).text, compile(lowercase, raw).text)
    }
}
