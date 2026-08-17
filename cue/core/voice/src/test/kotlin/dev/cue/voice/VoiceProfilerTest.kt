package dev.cue.voice

import dev.cue.model.SentMessage
import dev.cue.model.VoiceProfile
import dev.cue.testing.SyntheticCorpus
import dev.cue.testing.SyntheticVoice
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.math.max

/**
 * §14.3 — voice profile stability, plus the property that matters more: the
 * profiler recovers the style it was given.
 *
 * Asserting particular numbers would test the fixture. Asserting that a
 * lowercase corpus profiles as lowercase, and that the same corpus split in
 * half profiles the same way twice, tests the thing that has to hold when the
 * corpus is real.
 */
class VoiceProfilerTest {

    @Test
    fun `recovers a lowercase unpunctuated style`() {
        val profile = VoiceProfiler.profile(
            SyntheticCorpus.messages(80, SyntheticVoice.LOWERCASE),
        )

        assertTrue(profile.capitalizationRate < 0.05f, "capitalization ${profile.capitalizationRate}")
        assertTrue(
            profile.terminalPunctuationRate < 0.05f,
            "statement punctuation ${profile.terminalPunctuationRate}",
        )
        assertTrue(profile.lowercaseIRate > 0.95f, "lowercase i ${profile.lowercaseIRate}")
        assertTrue(profile.contractionRate > 0.9f, "contractions ${profile.contractionRate}")
        assertTrue(profile.commaRate < 0.5f, "commas ${profile.commaRate}")
        assertTrue(profile.abbreviations.containsKey("u"), "abbreviations ${profile.abbreviations}")
        assertTrue(profile.emojiRate in 0.05f..0.3f, "emoji ${profile.emojiRate}")
        assertEquals(listOf("😂", "🙃").sorted(), profile.topEmoji.take(2).sorted())
    }

    @Test
    fun `recovers a capitalised punctuated style from the same content`() {
        val profile = VoiceProfiler.profile(
            SyntheticCorpus.messages(80, SyntheticVoice.PROPER),
        )

        assertTrue(profile.capitalizationRate > 0.95f, "capitalization ${profile.capitalizationRate}")
        assertTrue(
            profile.terminalPunctuationRate > 0.95f,
            "statement punctuation ${profile.terminalPunctuationRate}",
        )
        assertTrue(profile.lowercaseIRate < 0.05f, "lowercase i ${profile.lowercaseIRate}")
        assertEquals(0f, profile.emojiRate)
        assertFalse(profile.abbreviations.containsKey("u"))
    }

    /**
     * §14.3. Two disjoint halves of one corpus must produce profiles that agree
     * within 15%.
     *
     * The absolute term in [agreesWithin] is not a loosening of that: a rate
     * whose true value is 0.02 cannot be compared relatively at all, and
     * demanding 15% of nearly-nothing would make the test fail on rounding
     * rather than on drift.
     */
    @Test
    fun `disjoint halves of a corpus agree within fifteen percent`() {
        val corpus = SyntheticCorpus.messages(160, SyntheticVoice.LOWERCASE)
        val first = VoiceProfiler.profile(corpus.take(80))
        val second = VoiceProfiler.profile(corpus.drop(80))

        val comparisons = listOf<Triple<String, Float, Float>>(
            Triple("medianWords", first.medianWords, second.medianWords),
            Triple("p90Words", first.p90Words, second.p90Words),
            Triple("capitalizationRate", first.capitalizationRate, second.capitalizationRate),
            Triple("lowercaseIRate", first.lowercaseIRate, second.lowercaseIRate),
            Triple(
                "terminalPunctuationRate",
                first.terminalPunctuationRate,
                second.terminalPunctuationRate,
            ),
            Triple("commaRate", first.commaRate, second.commaRate),
            Triple("emojiRate", first.emojiRate, second.emojiRate),
            Triple("contractionRate", first.contractionRate, second.contractionRate),
            Triple("questionRate", first.questionRate, second.questionRate),
        )

        comparisons.forEach { (name, a, b) ->
            assertTrue(agreesWithin(a, b, 0.15f), "$name drifted: $a vs $b")
        }
    }

    @Test
    fun `an empty corpus profiles as the baseline rather than as zero`() {
        val profile = VoiceProfiler.profile(emptyList())
        assertEquals(VoiceProfile.BASELINE, profile)
        assertFalse(profile.isCalibrated)
    }

    @Test
    fun `fifty messages is the calibration line`() {
        assertFalse(VoiceProfiler.profile(SyntheticCorpus.messages(49, SyntheticVoice.LOWERCASE)).isCalibrated)
        assertTrue(VoiceProfiler.profile(SyntheticCorpus.messages(50, SyntheticVoice.LOWERCASE)).isCalibrated)
    }

    /** §8: a correction counts twice, so it moves the profile twice as far. */
    @Test
    fun `weighted messages pull the profile harder`() {
        val plain = List(10) { SentMessage("p$it", "sounds good", null, null, null) }
        val shouty = SentMessage("edit", "SOUNDS GOOD!!!!!!!!!!", null, null, null, weight = 2)

        val single = VoiceProfiler.profile(plain + shouty.copy(weight = 1))
        val doubled = VoiceProfiler.profile(plain + shouty)

        assertTrue(
            doubled.exclamationRate > single.exclamationRate,
            "double weight should move the rate: ${single.exclamationRate} -> ${doubled.exclamationRate}",
        )
    }

    @Test
    fun `characteristic tokens surface the words you actually lean on`() {
        val corpus = List(40) { index ->
            SentMessage("m$index", "the ferry was unreal honestly", null, null, null)
        }
        val profile = VoiceProfiler.profile(corpus)
        assertTrue(
            profile.characteristicTokens.contains("ferry") ||
                profile.characteristicTokens.contains("unreal"),
            "expected a rare word in ${profile.characteristicTokens}",
        )
        assertFalse(profile.characteristicTokens.contains("the"), "'the' is not a tell")
    }

    private fun agreesWithin(a: Float, b: Float, tolerance: Float): Boolean =
        abs(a - b) <= tolerance * max(abs(a), abs(b)) + ABSOLUTE_EPSILON

    private companion object {
        const val ABSOLUTE_EPSILON = 0.02f
    }
}
