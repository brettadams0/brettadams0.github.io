package dev.sift.imaging

import dev.sift.model.ExportPreset
import dev.sift.testing.SyntheticFrames
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty

/**
 * §13's performance budgets, made re-runnable.
 *
 * The README quotes a 12MP grade time, and a performance number with no way to
 * reproduce it is a number that quietly stops being true. This is that way.
 *
 * Off by default: it grades several 12MP frames and takes about a minute, which
 * has no business running on every `./gradlew build`. Enable it explicitly:
 *
 * ```sh
 * ./gradlew :core:imaging:test -Dsift.bench=true -i
 * ```
 *
 * **This is a JVM benchmark, not a device measurement.** It says how the pure
 * Kotlin pipeline scales across cores and whether a change made it slower; it
 * does not tell you whether §13's 2.5s budget is met on a phone, because a
 * phone's memory bandwidth, thermal behaviour and core mix are all different
 * and none of them are represented here. Only an instrumented run answers that
 * (§14, still outstanding). Asserting nothing is deliberate — a threshold tuned
 * to whatever hardware happened to run CI would fail for reasons that have
 * nothing to do with the code.
 */
@EnabledIfSystemProperty(named = "sift.bench", matches = "true")
class PipelineBenchmark {

    /** 4000x3000 — the §13 reference frame. */
    private val width = 4000
    private val height = 3000

    private fun timeGrade(): Long {
        // A fresh frame each run: process() mutates its own linear copy, but
        // reusing one source would let the JIT and the page cache flatter the
        // later iterations.
        val frame = SyntheticFrames.portrait(width = width, height = height)
        val request = Pipeline.Request(
            source = Pipeline.SourceFrame(frame),
            preset = ExportPreset.MASTER,
            ditherSeed = 7L,
        )
        val started = System.nanoTime()
        Pipeline.process(request)
        return (System.nanoTime() - started) / 1_000_000
    }

    @Test
    @DisplayName("§13: 12MP grade, cold and warm")
    fun twelveMegapixelGrade() {
        val cores = Runtime.getRuntime().availableProcessors()
        val cold = timeGrade()
        val warm = (1..3).map { timeGrade() }

        println(
            buildString {
                appendLine("--- §13 12MP grade (${width}x$height, $cores cores) ---")
                appendLine("cold (first frame, JIT warming): ${cold}ms")
                warm.forEachIndexed { i, ms -> appendLine("warm #${i + 1}: ${ms}ms") }
                appendLine("warm median: ${warm.sorted()[warm.size / 2]}ms")
                appendLine("§13 budget on device: 2500ms — this is a JVM figure, not a phone.")
            },
        )
    }
}
