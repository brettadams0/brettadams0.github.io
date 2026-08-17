package dev.cue.draft

import dev.cue.model.ConversationStage
import dev.cue.model.SentMessage
import kotlin.math.ln

data class Retrieved(
    val message: SentMessage,
    val score: Double,
)

/**
 * §4.3. BM25 over your own message corpus.
 *
 * > This is the single highest-leverage technique in the spec for small-model
 * > quality. A 2B model given five real examples of how you write outperforms a
 * > 4B model given a paragraph describing how you write.
 *
 * No embedding model, no extra memory, no second thing to load at startup. The
 * spec calls it "~20 lines of code"; this is longer, and the extra length is
 * all in the two decisions that make retrieval match on *situation*:
 *
 *  - a document is her message **plus** your reply, so "she asked about my
 *    week" retrieves what you say to that, not every message containing "week";
 *  - the stage is part of the score, because your openers and your Thursday-
 *    logistics messages are different registers and one is never an example of
 *    the other.
 *
 * Postings are built once at construction. §12 budgets 50ms for 5k messages;
 * scoring touches only the documents containing a query term.
 */
class Bm25Index(
    corpus: List<SentMessage>,
    private val k1: Double = 1.2,
    private val b: Double = 0.75,
) {

    private data class Document(
        val message: SentMessage,
        val termFrequencies: Map<String, Int>,
        val length: Int,
    )

    private val documents: List<Document> = corpus.map { message ->
        // Indexed on the pair; §4.3.
        val text = listOfNotNull(message.precedingTheirMessage, message.text).joinToString(" ")
        val terms = Stopwords.contentTerms(text)
        Document(
            message = message,
            termFrequencies = terms.groupingBy { it }.eachCount(),
            length = terms.size,
        )
    }

    private val averageLength: Double =
        documents.map { it.length }.average().takeIf { !it.isNaN() } ?: 0.0

    /** term -> indices of documents containing it. */
    private val postings: Map<String, List<Int>> = buildMap<String, MutableList<Int>> {
        documents.forEachIndexed { index, document ->
            document.termFrequencies.keys.forEach { term ->
                getOrPut(term) { mutableListOf() }.add(index)
            }
        }
    }

    val size: Int get() = documents.size

    /**
     * The [limit] messages most like this situation.
     *
     * [stage] is a bias and not a filter: a corpus that has never produced an
     * opener should still return your five best messages rather than nothing,
     * because five imperfect examples beat a paragraph of description and an
     * empty list beats neither.
     */
    fun search(
        query: String,
        limit: Int = 5,
        stage: ConversationStage? = null,
    ): List<Retrieved> {
        if (documents.isEmpty()) return emptyList()
        val queryTerms = Stopwords.contentTerms(query).distinct()
        if (queryTerms.isEmpty()) return emptyList()

        val scores = mutableMapOf<Int, Double>()
        queryTerms.forEach { term ->
            val matching = postings[term] ?: return@forEach
            val idf = ln(1.0 + (documents.size - matching.size + 0.5) / (matching.size + 0.5))
            matching.forEach { index ->
                val document = documents[index]
                val tf = document.termFrequencies.getValue(term).toDouble()
                val norm = tf + k1 * (1 - b + b * document.length / averageLength.coerceAtLeast(1.0))
                scores.merge(index, idf * tf * (k1 + 1) / norm, Double::plus)
            }
        }

        return scores.entries
            .map { (index, score) ->
                val document = documents[index]
                Retrieved(document.message, score * modifiers(document.message, stage))
            }
            .sortedWith(compareByDescending<Retrieved> { it.score }.thenBy { it.message.id })
            .take(limit)
    }

    /**
     * §8's double weight for your own corrections, and the stage bias.
     *
     * A message you wrote after rejecting a draft is worth more as an example
     * than one you wrote cold: it is a correction, and it is the closest thing
     * the app has to supervision.
     */
    private fun modifiers(message: SentMessage, stage: ConversationStage?): Double {
        val weight = 1.0 + WEIGHT_BONUS * (message.weight - 1)
        val stageBonus = when {
            stage == null || message.stage == null -> 1.0
            message.stage == stage -> STAGE_MATCH_BONUS
            else -> 1.0
        }
        return weight * stageBonus
    }

    private companion object {
        const val WEIGHT_BONUS = 0.5
        const val STAGE_MATCH_BONUS = 1.25
    }
}
