package dev.cue.draft

/**
 * §6.4. Distinctness enforcement.
 *
 * > Without this, small-model variants collapse into synonyms of each other and
 * > the whole three-option premise is fake.
 *
 * Jaccard over content words, threshold 0.6, computed after generation rather
 * than prevented during it — §6.1 already generates the variants in separate
 * calls with different strategies and seeds, and this measures whether that
 * worked.
 */
object Distinctness {

    const val THRESHOLD = 0.6

    fun similarity(a: String, b: String): Double {
        val left = Stopwords.contentTerms(a).toSet()
        val right = Stopwords.contentTerms(b).toSet()
        if (left.isEmpty() && right.isEmpty()) return 1.0
        if (left.isEmpty() || right.isEmpty()) return 0.0
        val intersection = left.intersect(right).size.toDouble()
        val union = left.union(right).size.toDouble()
        return intersection / union
    }

    /**
     * Indices of drafts that are too close to an earlier one.
     *
     * The *later* draft is the one reported, per §6.4 — the first occurrence of
     * an idea is kept and the echo is regenerated, so a retry cannot cascade
     * into replacing the whole set.
     */
    fun tooSimilar(drafts: List<String>, threshold: Double = THRESHOLD): List<Int> {
        val flagged = mutableListOf<Int>()
        for (i in drafts.indices) {
            for (j in 0 until i) {
                if (j in flagged) continue
                if (similarity(drafts[i], drafts[j]) > threshold) {
                    flagged += i
                    break
                }
            }
        }
        return flagged
    }
}
