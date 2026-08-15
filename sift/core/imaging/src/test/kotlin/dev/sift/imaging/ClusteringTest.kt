package dev.sift.imaging

import dev.sift.testing.SyntheticFrames
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * §7 and §14.5 — perceptual hashing and burst clustering.
 */
class ClusteringTest {

    private fun candidate(id: Long, time: Long, hash: Long, sharpness: Float = 100f) =
        BurstClustering.Candidate(id, time, hash, sharpness)

    @Test
    @DisplayName("dHash is 64 bits of horizontal gradient and is stable for identical input")
    fun hashIsStable() {
        val a = SyntheticFrames.toWorkingSpace(SyntheticFrames.wellExposedScene())
        val b = SyntheticFrames.toWorkingSpace(SyntheticFrames.wellExposedScene())
        assertEquals(PerceptualHash.dHash(a), PerceptualHash.dHash(b))
    }

    @Test
    @DisplayName("Visually different frames hash far apart; a small exposure shift does not")
    fun hashSeparatesContentNotExposure() {
        val scene = SyntheticFrames.toWorkingSpace(SyntheticFrames.wellExposedScene())
        val portrait = SyntheticFrames.toWorkingSpace(SyntheticFrames.portrait())
        val sceneHash = PerceptualHash.dHash(scene)
        val portraitHash = PerceptualHash.dHash(portrait)

        assertTrue(
            PerceptualHash.hammingDistance(sceneHash, portraitHash) > BurstClustering.HAMMING_THRESHOLD,
            "a portrait and a landscape must not cluster together",
        )

        // The same frame a third of a stop brighter — the drift between two
        // shots in a burst. dHash compares neighbours, so a uniform gain barely
        // moves it.
        val brighter = SyntheticFrames.toWorkingSpace(SyntheticFrames.wellExposedScene())
        for (i in brighter.data.indices) brighter.data[i] *= 1.06f
        val brighterHash = PerceptualHash.dHash(brighter)

        assertTrue(
            PerceptualHash.hammingDistance(sceneHash, brighterHash) <= BurstClustering.HAMMING_THRESHOLD,
            "a small exposure change split a burst apart",
        )
    }

    @Test
    @DisplayName("A burst collapses into one cluster and the sharpest frame is pre-selected")
    fun burstCollapses() {
        val base = 0x0F0F_0F0F_0F0F_0F0FL
        val burst = listOf(
            candidate(1, 1_000, base, sharpness = 120f),
            candidate(2, 3_000, base xor 0b1L, sharpness = 340f),
            candidate(3, 5_500, base xor 0b110L, sharpness = 210f),
            candidate(4, 8_000, base xor 0b1110L, sharpness = 90f),
        )
        val clusters = BurstClustering.cluster(burst)

        assertEquals(1, clusters.size, "burst did not collapse: $clusters")
        assertEquals(4, clusters[0].size)
        assertEquals(2L, clusters[0].suggestedKeeperId, "sharpest frame was not pre-selected")
        assertTrue(clusters[0].isBurst)
    }

    @Test
    @DisplayName("Both conditions are required: time alone and look alone do not cluster")
    fun bothConditionsRequired() {
        val hash = 0x1234_5678_9ABC_DEF0L

        // Close in time, completely different look.
        val differentLook = BurstClustering.cluster(
            listOf(candidate(1, 1_000, hash), candidate(2, 2_000, hash.inv())),
        )
        assertEquals(2, differentLook.size, "clustered on timestamp alone")

        // Identical look, hours apart — the same wall photographed twice.
        val farApart = BurstClustering.cluster(
            listOf(candidate(1, 1_000, hash), candidate(2, 5_000_000, hash)),
        )
        assertEquals(2, farApart.size, "clustered on hash alone")
    }

    /**
     * A ten-frame burst can drift further than the threshold from end to end
     * while every consecutive pair stays within it. Chaining keeps it together;
     * comparing every frame against the cluster's first would split it.
     */
    @Test
    @DisplayName("A long burst chains rather than splitting on cumulative drift")
    fun longBurstChains() {
        var hash = 0L
        val frames = (0 until 10).map { i ->
            if (i > 0) hash = hash or (1L shl (i * 2)) // two new bits each step
            candidate(i.toLong(), i * 1_500L, hash, sharpness = i.toFloat())
        }
        val clusters = BurstClustering.cluster(frames)

        assertEquals(1, clusters.size, "long burst split into ${clusters.size} clusters")
        assertEquals(10, clusters[0].size)
        assertTrue(
            PerceptualHash.hammingDistance(frames.first().dHash, frames.last().dHash) >
                BurstClustering.HAMMING_THRESHOLD,
            "fixture is not exercising chaining — first and last are within threshold",
        )
    }

    @Test
    @DisplayName("Singles stay single and keep themselves as the keeper")
    fun singlesAreClustersOfOne() {
        val clusters = BurstClustering.cluster(listOf(candidate(7, 1_000, 0xABCDL)))
        assertEquals(1, clusters.size)
        assertTrue(!clusters[0].isBurst)
        assertEquals(7L, clusters[0].suggestedKeeperId)
    }

    @Test
    @DisplayName("Clustering is order-independent")
    fun orderIndependent() {
        val base = 0x0F0F_0F0F_0F0F_0F0FL
        val frames = listOf(
            candidate(3, 5_500, base xor 0b110L),
            candidate(1, 1_000, base),
            candidate(2, 3_000, base xor 0b1L),
        )
        val clusters = BurstClustering.cluster(frames)
        assertEquals(1, clusters.size)
        assertEquals(listOf(1L, 2L, 3L), clusters[0].members.map { it.id })
    }
}
