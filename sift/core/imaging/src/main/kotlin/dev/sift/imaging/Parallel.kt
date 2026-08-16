package dev.sift.imaging

import java.util.stream.IntStream

/**
 * Row-parallel execution for the per-pixel passes.
 *
 * §4.3 caps *concurrent frames* at 2, because a 12MP frame is ~144MB as
 * unbounded float and three at once will OOM. That is a limit on how many
 * photos are in flight, not on how many cores may work on one of them — and
 * every per-pixel pass in this pipeline is embarrassingly parallel over rows.
 * Running them on a single thread left seven cores idle while a batch of 200
 * photos ground through one frame at a time.
 *
 * Uses the JVM's common fork-join pool via [IntStream], which needs no new
 * dependency and is available well below `minSdk`. Small images stay sequential:
 * below [PARALLEL_THRESHOLD_PIXELS] the fork-join overhead costs more than the
 * work, and it keeps the unit tests — which run on tiny fixtures — deterministic
 * in their scheduling.
 *
 * **Anything order-dependent must not use this.** Dither draws from a seeded
 * RNG in a fixed sequence (§2.3), and reproducible output is what lets the
 * banding test assert anything at all, so quantisation stays single-threaded.
 */
internal object Parallel {

    const val PARALLEL_THRESHOLD_PIXELS = 200_000

    /** Run [block] once per row index, in parallel when the image is large enough. */
    inline fun rows(width: Int, height: Int, crossinline block: (Int) -> Unit) {
        if (width * height < PARALLEL_THRESHOLD_PIXELS) {
            for (y in 0 until height) block(y)
        } else {
            IntStream.range(0, height).parallel().forEach { y -> block(y) }
        }
    }

    /**
     * Run [block] once per index, parallelising on total work rather than count.
     *
     * For loops whose item count is small but whose per-item cost is large — a
     * row of 8x8 JPEG blocks is a few hundred items doing thousands of
     * multiplies each, so item count alone would wrongly keep it sequential.
     */
    inline fun range(count: Int, workPerItem: Int, crossinline block: (Int) -> Unit) {
        if (count.toLong() * workPerItem < PARALLEL_THRESHOLD_PIXELS) {
            for (i in 0 until count) block(i)
        } else {
            IntStream.range(0, count).parallel().forEach { i -> block(i) }
        }
    }

    /**
     * Run [block] over contiguous index chunks, in parallel when worthwhile.
     *
     * For flat buffers with no row structure — the interleaved RGB passes in
     * [ColorSpaces], where every sample is independent of every other.
     */
    inline fun chunks(size: Int, crossinline block: (Int, Int) -> Unit) {
        if (size < PARALLEL_THRESHOLD_PIXELS) {
            block(0, size)
            return
        }
        val workers = Runtime.getRuntime().availableProcessors().coerceIn(1, 8)
        val chunk = (size + workers - 1) / workers
        IntStream.range(0, workers).parallel().forEach { w ->
            val from = w * chunk
            if (from < size) block(from, minOf(from + chunk, size))
        }
    }
}
