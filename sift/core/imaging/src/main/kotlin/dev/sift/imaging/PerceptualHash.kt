package dev.sift.imaging

/**
 * Perceptual hashing and burst clustering (§7).
 *
 * dHash rather than aHash or pHash: it compares each pixel to its right
 * neighbour, so it encodes *gradient direction* rather than absolute level. That
 * makes it robust to the exposure and white-balance drift between consecutive
 * frames of a burst — which is exactly the population it has to cluster — while
 * still separating genuinely different compositions.
 */
object PerceptualHash {

    /** §7 — downsample to 9x8, compare horizontally, pack 64 bits. */
    const val HASH_WIDTH = 9
    const val HASH_HEIGHT = 8

    /**
     * 64-bit dHash of a linear-light image.
     *
     * The downsample runs in linear light like every other resize in the app
     * (§6.10) — a gamma-space box filter would weight highlights differently
     * from shadows and shift the comparisons near hard edges.
     */
    fun dHash(linearImage: FloatImage): Long {
        linearImage.requireSpace(ColorSpaceTag.LINEAR_SRGB, "dHash")
        val small = if (linearImage.width == HASH_WIDTH && linearImage.height == HASH_HEIGHT) {
            linearImage
        } else {
            Resample.resize(linearImage.copy(), HASH_WIDTH, HASH_HEIGHT)
        }

        val luma = FrameAnalyzer.lightnessPlane(small)
        var hash = 0L
        var bit = 0
        for (y in 0 until HASH_HEIGHT) {
            for (x in 0 until HASH_WIDTH - 1) {
                if (luma[y * HASH_WIDTH + x] > luma[y * HASH_WIDTH + x + 1]) {
                    hash = hash or (1L shl bit)
                }
                bit++
            }
        }
        return hash
    }

    fun hammingDistance(a: Long, b: Long): Int = java.lang.Long.bitCount(a xor b)
}

/**
 * Burst clustering (§7).
 *
 * Two photos cluster when **both** hold:
 * - `abs(dateTaken_a - dateTaken_b) < 10_000` ms
 * - Hamming distance ≤ 8
 *
 * Both conditions, because either alone over-clusters badly: a timestamp window
 * on its own merges everything shot at an event, and a hash threshold on its own
 * merges every photo of the same wall taken hours apart.
 */
object BurstClustering {

    const val TIME_WINDOW_MS = 10_000L
    const val HAMMING_THRESHOLD = 8

    data class Candidate(
        val id: Long,
        val dateTaken: Long,
        val dHash: Long,
        /** Ranks keepers within a cluster; P90, never mean (trap #11). */
        val sharpnessP90: Float,
    )

    data class Cluster(
        val id: String,
        val members: List<Candidate>,
        /** Pre-selected keeper: the sharpest member. */
        val suggestedKeeperId: Long,
    ) {
        val size: Int get() = members.size
        val isBurst: Boolean get() = members.size > 1
    }

    /**
     * Cluster greedily in timestamp order.
     *
     * A candidate joins the open cluster when it matches that cluster's most
     * recent member — chaining, not all-pairs. A burst is a chain of near
     * neighbours: the first and last frame of a ten-shot sequence can differ by
     * more than the threshold while every consecutive pair matches, and requiring
     * agreement with the cluster's first frame would split exactly the sequences
     * this exists to collapse.
     */
    fun cluster(candidates: List<Candidate>): List<Cluster> {
        if (candidates.isEmpty()) return emptyList()
        val ordered = candidates.sortedBy { it.dateTaken }

        val clusters = mutableListOf<MutableList<Candidate>>()
        var current = mutableListOf(ordered.first())

        for (i in 1 until ordered.size) {
            val candidate = ordered[i]
            val previous = current.last()
            val closeInTime = candidate.dateTaken - previous.dateTaken < TIME_WINDOW_MS
            val closeInLook =
                PerceptualHash.hammingDistance(candidate.dHash, previous.dHash) <= HAMMING_THRESHOLD

            if (closeInTime && closeInLook) {
                current.add(candidate)
            } else {
                clusters.add(current)
                current = mutableListOf(candidate)
            }
        }
        clusters.add(current)

        return clusters.map { members ->
            val keeper = members.maxByOrNull { it.sharpnessP90 } ?: members.first()
            Cluster(
                id = "cluster-${members.first().id}",
                members = members,
                suggestedKeeperId = keeper.id,
            )
        }
    }
}
