package dev.cue.voice

import dev.cue.model.VoiceProfile
import kotlin.math.roundToInt

/**
 * The thresholds §4.4 and §7.1 must agree on.
 *
 * They are here rather than in each class because they were briefly in both,
 * and the copies disagreed. The compiler allowed a comma-user one comma in a
 * short message; the verifier measured the same message as 25 commas per 100
 * words and flagged it. The result was §7.1 rejecting drafts the compiler had
 * deliberately produced, retrying twice, and badging a draft that was correct —
 * a spinner and a warning label caused entirely by two functions rounding
 * differently.
 *
 * The shape repeats because the problem repeats: a per-100-words rate cannot be
 * applied to a nine-word message, so each rate becomes an expected count for
 * *this* length, with a floor of one for anyone who demonstrably uses the thing
 * at all.
 */
object VoicePolicy {

    fun lowercasesLead(profile: VoiceProfile): Boolean = profile.capitalizationRate < 0.3f

    fun stripsTerminalPeriod(profile: VoiceProfile): Boolean = profile.terminalPunctuationRate < 0.3f

    fun lowercasesI(profile: VoiceProfile): Boolean = profile.lowercaseIRate > 0.7f

    fun contracts(profile: VoiceProfile): Boolean = profile.contractionRate > 0.8f

    fun stripsEllipsis(profile: VoiceProfile): Boolean = profile.ellipsisRate < 0.1f

    fun allowedEmoji(profile: VoiceProfile): Int = profile.emojiRate.roundToInt()

    fun allowedExclamations(profile: VoiceProfile, words: Int): Int =
        allowance(profile.exclamationRate, words, floorThreshold = 1f)

    fun allowedCommas(profile: VoiceProfile, words: Int): Int =
        allowance(profile.commaRate, words, floorThreshold = 1.5f)

    private fun allowance(ratePer100Words: Float, words: Int, floorThreshold: Float): Int {
        val expected = ratePer100Words * words / 100f
        return when {
            expected >= 0.5f -> expected.roundToInt()
            ratePer100Words >= floorThreshold -> 1
            else -> 0
        }
    }
}
