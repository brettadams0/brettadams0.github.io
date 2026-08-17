package dev.cue.voice

import dev.cue.model.StyleDeviation
import dev.cue.model.VoiceProfile

/**
 * §7.1. Re-measures §4.1's features on a compiled draft and reports what still
 * disagrees with the profile.
 *
 * "The compiler should make most of these pass by construction; the check exists
 * to catch cases it missed." That is a testable claim, not a hope — see
 * `VoiceCompilerTest.compilerOutputPassesItsOwnVerifier`, which asserts the
 * stronger version: on the inputs a small model actually produces, the compiler
 * leaves *nothing* for this class to find.
 *
 * Every threshold comes from [VoicePolicy], the same source the compiler uses.
 * When the two computed their own, §7.1 flagged drafts §4.4 had deliberately
 * produced.
 *
 * A deviation is not a rejection. §7.1 ships the best candidate with a visible
 * off-voice badge after two retries, because an off-voice draft is still a draft
 * you can edit, and the alternative is a spinner.
 */
class StyleVerifier(private val profile: VoiceProfile) {

    private val forbidden = ForbiddenTokens.forProfile(profile)

    fun verify(text: String): List<StyleDeviation> {
        val deviations = mutableListOf<StyleDeviation>()
        val words = Text.wordCount(text)

        if (words > profile.maxDraftWords) {
            deviations += StyleDeviation("length", profile.maxDraftWords.toFloat(), words.toFloat())
        }

        if (VoicePolicy.lowercasesLead(profile)) {
            val lead = Text.firstLetter(text)
            if (lead != null && lead.isUpperCase()) {
                deviations += StyleDeviation("capitalization", profile.capitalizationRate, 1f)
            }
        }

        if (VoicePolicy.stripsTerminalPeriod(profile) && text.trimEnd().endsWith('.')) {
            deviations += StyleDeviation("terminalPunctuation", profile.terminalPunctuationRate, 1f)
        }

        if (VoicePolicy.lowercasesI(profile) &&
            Text.standaloneIOccurrences(text).any { it.value == "I" }
        ) {
            deviations += StyleDeviation("lowercaseI", profile.lowercaseIRate, 0f)
        }

        val emoji = Emoji.count(text)
        val allowedEmoji = VoicePolicy.allowedEmoji(profile)
        if (emoji > allowedEmoji) {
            deviations += StyleDeviation("emojiRate", allowedEmoji.toFloat(), emoji.toFloat())
        }

        if (words > 0) {
            val commas = Text.countChar(text, ',')
            val allowedCommas = VoicePolicy.allowedCommas(profile, words)
            if (commas > allowedCommas) {
                deviations += StyleDeviation("commaRate", allowedCommas.toFloat(), commas.toFloat())
            }

            val exclamations = Text.countChar(text, '!')
            val allowedExclamations = VoicePolicy.allowedExclamations(profile, words)
            if (exclamations > allowedExclamations) {
                deviations += StyleDeviation(
                    "exclamationRate",
                    allowedExclamations.toFloat(),
                    exclamations.toFloat(),
                )
            }
        }

        forbidden.forEach { phrase ->
            if (phrase.pattern.containsMatchIn(text)) {
                deviations += StyleDeviation("forbidden:${phrase.name}", 0f, 1f)
            }
        }

        return deviations
    }
}
