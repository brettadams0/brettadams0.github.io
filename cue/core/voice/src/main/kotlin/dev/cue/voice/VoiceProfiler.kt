package dev.cue.voice

import dev.cue.model.SentMessage
import dev.cue.model.VoiceProfile
import kotlin.math.ln

/**
 * §4.1–4.2. Turns a corpus of messages you actually sent into numbers.
 *
 * Nothing here is inference. The whole point of §4 is that the expensive,
 * unreliable part of "write in my voice" is measurable in a few hundred lines
 * of arithmetic, leaving the model with only the part it is good at.
 *
 * Every statistic is weighted by [SentMessage.weight] so that §8's corrections
 * — the text you actually sent after editing a draft — count double without
 * being duplicated in storage.
 */
object VoiceProfiler {

    /** How many of your top tokens are worth showing as "your tells". */
    private const val CHARACTERISTIC_TOKEN_COUNT = 12

    /** A token seen twice is noise. §16 trap 9: small corpora overfit brutally. */
    private const val MIN_CHARACTERISTIC_OCCURRENCES = 3

    private const val VOCABULARY_CAP = 1500

    /** Above this generic frequency a word is furniture, not a characteristic. */
    private const val MAX_TELL_FREQUENCY = 0.005

    /** §4.1's burst definition: sent within 60s of your previous message. */
    private const val BURST_WINDOW_MS = 60_000L

    fun profile(corpus: List<SentMessage>): VoiceProfile {
        if (corpus.isEmpty()) return VoiceProfile.BASELINE

        val totalWeight = corpus.sumOf { it.weight }.toFloat()
        val totalWords = corpus.sumOf { Text.wordCount(it.text) * it.weight }.toFloat()

        fun fractionOf(predicate: (SentMessage) -> Boolean): Float =
            corpus.filter(predicate).sumOf { it.weight } / totalWeight

        fun per100Words(count: (String) -> Int): Float =
            if (totalWords == 0f) {
                0f
            } else {
                100f * corpus.sumOf { count(it.text) * it.weight } / totalWords
            }

        val wordCounts = corpus.map { Text.wordCount(it.text).toFloat() to it.weight }

        val withFirstLetter = corpus.filter { Text.firstLetter(it.text) != null }
        val capitalizationRate = if (withFirstLetter.isEmpty()) {
            VoiceProfile.BASELINE.capitalizationRate
        } else {
            withFirstLetter.filter { Text.firstLetter(it.text)!!.isUpperCase() }
                .sumOf { it.weight }.toFloat() / withFirstLetter.sumOf { it.weight }
        }

        var lowercaseI = 0
        var totalI = 0
        corpus.forEach { message ->
            Text.standaloneIOccurrences(message.text).forEach { match ->
                totalI += message.weight
                if (match.value.first() == 'i') lowercaseI += message.weight
            }
        }

        val emojiCounts = corpus.associateWith { Emoji.count(it.text) }
        val emojiFrequency = mutableMapOf<String, Int>()
        corpus.forEach { message ->
            Emoji.findAll(message.text).forEach { emoji ->
                emojiFrequency.merge(emoji, message.weight, Int::plus)
            }
        }

        val wordFrequency = mutableMapOf<String, Int>()
        corpus.forEach { message ->
            Text.normalizedWords(message.text).forEach { word ->
                wordFrequency.merge(word, message.weight, Int::plus)
            }
        }

        return VoiceProfile(
            sampleCount = corpus.size,
            medianWords = weightedPercentile(wordCounts, 0.5f),
            p90Words = weightedPercentile(wordCounts, 0.9f),
            capitalizationRate = capitalizationRate,
            lowercaseIRate = if (totalI == 0) {
                VoiceProfile.BASELINE.lowercaseIRate
            } else {
                lowercaseI.toFloat() / totalI
            },
            terminalPunctuationRate = statementPunctuationRate(corpus),
            ellipsisRate = fractionOf { it.text.contains("...") || it.text.contains('…') },
            commaRate = per100Words { Text.countChar(it, ',') },
            exclamationRate = per100Words { Text.countChar(it, '!') },
            emojiRate = corpus.sumOf { (emojiCounts[it] ?: 0) * it.weight } / totalWeight,
            topEmoji = emojiFrequency.entries.sortedByDescending { it.value }.take(5).map { it.key },
            abbreviations = abbreviationCounts(wordFrequency),
            contractionRate = contractionRate(corpus),
            profanityRate = fractionOf { message ->
                Text.normalizedWords(message.text).any { it in Lexicons.PROFANITY }
            },
            questionRate = fractionOf { it.text.contains('?') },
            burstRate = burstRate(corpus),
            characteristicTokens = characteristicTokens(wordFrequency),
            vocabulary = wordFrequency.entries
                .sortedByDescending { it.value }
                .take(VOCABULARY_CAP)
                .associate { it.key to it.value },
        )
    }

    /**
     * The p-th percentile with each message counted [SentMessage.weight] times.
     *
     * Percentile rather than mean throughout §4.1, because one 90-word message
     * about your job drags a mean far enough to change the length ceiling the
     * compiler enforces.
     */
    internal fun weightedPercentile(values: List<Pair<Float, Int>>, p: Float): Float {
        if (values.isEmpty()) return 0f
        val sorted = values.sortedBy { it.first }
        val total = sorted.sumOf { it.second }
        val target = p * total
        var seen = 0
        sorted.forEach { (value, weight) ->
            seen += weight
            if (seen >= target) return value
        }
        return sorted.last().first
    }

    private fun abbreviationCounts(wordFrequency: Map<String, Int>): Map<String, Int> =
        Lexicons.ABBREVIATIONS
            .mapNotNull { abbreviation ->
                wordFrequency[abbreviation]?.let { abbreviation to it }
            }
            .sortedByDescending { it.second }
            .toMap()

    /**
     * How often you punctuate the end of a **statement**.
     *
     * Questions are excluded from both halves of the ratio. §4.4's rule is
     * "strip trailing `.` (never `?`)", so the decision this number drives is
     * only ever about the optional punctuation — and counting question marks as
     * evidence of punctuating would put a lowercase, full-stop-free writer who
     * asks a lot of questions above the 0.3 threshold and leave every period in
     * place.
     */
    private fun statementPunctuationRate(corpus: List<SentMessage>): Float {
        val statements = corpus.filterNot { Text.endsWithQuestionMark(it.text) }
        if (statements.isEmpty()) return VoiceProfile.BASELINE.terminalPunctuationRate
        val punctuated = statements
            .filter { Text.endsWithTerminalPunctuation(it.text) }
            .sumOf { it.weight }
        return punctuated.toFloat() / statements.sumOf { it.weight }
    }

    /**
     * The share of contractible pairs you actually contract.
     *
     * Counting apostrophes alone would be wrong: someone who never writes "do
     * not" in the first place is not a 0% contractor, they simply never had the
     * choice. Only occasions where both forms were available are counted.
     */
    private fun contractionRate(corpus: List<SentMessage>): Float {
        var contracted = 0
        var expanded = 0
        corpus.forEach { message ->
            val lower = message.text.lowercase().replace('’', '\'')
            Lexicons.CONTRACTIONS.forEach { (long, short) ->
                expanded += countOccurrences(lower, long) * message.weight
                contracted += countOccurrences(lower, short) * message.weight
            }
        }
        val total = contracted + expanded
        return if (total == 0) VoiceProfile.BASELINE.contractionRate else contracted.toFloat() / total
    }

    private fun countOccurrences(haystack: String, needle: String): Int {
        var count = 0
        var from = 0
        while (true) {
            val at = haystack.indexOf(needle, from)
            if (at < 0) return count
            // Whole-phrase only: "can not" must not also match inside "scan nothing".
            val beforeOk = at == 0 || !haystack[at - 1].isLetter()
            val afterIndex = at + needle.length
            val afterOk = afterIndex >= haystack.length || !haystack[afterIndex].isLetter()
            if (beforeOk && afterOk) count++
            from = at + 1
        }
    }

    /**
     * §4.1's burst rate, over the messages that carry usable timestamps.
     *
     * Trap 12 says not to trust OCR'd relative times, so in practice this is
     * near-zero for a screenshot-bootstrapped corpus and real once the outcome
     * loop (§8) starts recording sends. It is measured rather than assumed
     * because it is the one feature the compiler cannot fake: bursts are a
     * property of how you send, not of what a single message looks like.
     */
    private fun burstRate(corpus: List<SentMessage>): Float {
        val timed = corpus.mapNotNull { message -> message.sentAt?.let { it to message.weight } }
            .sortedBy { it.first }
        if (timed.size < 2) return 0f
        var bursts = 0
        var eligible = 0
        for (i in 1 until timed.size) {
            eligible += timed[i].second
            if (timed[i].first - timed[i - 1].first <= BURST_WINDOW_MS) bursts += timed[i].second
        }
        return if (eligible == 0) 0f else bursts.toFloat() / eligible
    }

    /**
     * Your tells: words you use far more than English does.
     *
     * Score is your relative frequency over the generic one, log-damped so a
     * single very rare word does not crowd out a word you genuinely lean on.
     */
    private fun characteristicTokens(wordFrequency: Map<String, Int>): List<String> {
        val total = wordFrequency.values.sum().toDouble()
        if (total == 0.0) return emptyList()
        return wordFrequency.entries
            .filter { it.value >= MIN_CHARACTERISTIC_OCCURRENCES }
            .filter { entry -> entry.key.any { it.isLetter() } }
            // "the" cannot be a tell no matter how often you type it. Without
            // this, a small corpus returns its own function words, because
            // twelve slots is more than the number of distinct words in it.
            .filterNot { (Lexicons.GENERIC_ENGLISH[it.key] ?: 0.0) > MAX_TELL_FREQUENCY }
            .map { (word, count) ->
                val mine = count / total
                val generic = Lexicons.GENERIC_ENGLISH[word] ?: Lexicons.RARE_WORD_FREQUENCY
                word to ln(1.0 + count) * (mine / generic)
            }
            .sortedByDescending { it.second }
            .take(CHARACTERISTIC_TOKEN_COUNT)
            .map { it.first }
    }
}
