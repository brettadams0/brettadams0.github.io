package dev.cue.capture

import dev.cue.model.Sender
import kotlin.math.abs

/** One block, with the side of the screen it sat on resolved into a sender. */
data class AttributedBlock(
    val text: String,
    val bounds: BoundingBox,
    val sender: Sender,
    val confidence: Float,
)

/**
 * The outcome of attributing one screenshot.
 *
 * [needsConfirmation] drives §13's "show both interpretations, ask which is
 * you" path. It is set generously: a wrong answer here is the single most
 * damaging failure in the app, and the cost of asking is one tap.
 */
data class AttributionResult(
    val blocks: List<AttributedBlock>,
    val needsConfirmation: Boolean,
    val reason: String? = null,
)

/**
 * §4.2. Attribution by bounding box, not by content.
 *
 * > **Highest-stakes logic in the app.** Reverse it and you build a voice
 * > profile from *her* messages, then generate drafts that sound like the
 * > person you're talking to — subtly wrong, hard to diagnose, and it poisons
 * > everything downstream.
 *
 * Content-based attribution ("this sounds like him") is the obvious approach
 * and is exactly the trap: it is a classifier trained on the thing it is
 * supposed to produce. Geometry is not clever, but it is right, and when it is
 * not sure it can say so.
 *
 * The primary rule is absolute rather than relative — a block whose right edge
 * is within 15% of the screen width is yours — so there is no axis to flip.
 * The clustering pass only ever *raises* confidence on blocks the edge rule
 * left undecided.
 */
class BubbleAttribution(private val layout: ChatLayout = ChatLayout.DEFAULT) {

    fun attribute(screen: RecognizedScreen): AttributionResult {
        val candidates = screen.blocks.filterNot { ScreenChrome.isChrome(it, screen) }
        if (candidates.isEmpty()) {
            return AttributionResult(emptyList(), needsConfirmation = false, reason = "No message text found")
        }
        if (screen.screenWidth <= 0) {
            return AttributionResult(
                candidates.map { AttributedBlock(it.text, it.bounds, Sender.THEM, 0f) },
                needsConfirmation = true,
                reason = "Screen width unknown, so no side could be established",
            )
        }

        val margin = layout.edgeMarginFraction * screen.screenWidth
        val initial = candidates.map { block -> alignByEdges(block, screen.screenWidth, margin) }
        val refined = refineByCluster(initial)

        val attributed = refined.map { (block, alignment) ->
            AttributedBlock(
                text = block.text.trim(),
                bounds = block.bounds,
                sender = alignment.side.toSender(layout),
                confidence = alignment.confidence,
            )
        }

        val unsure = attributed.count { it.confidence < CONFIDENT }
        val needsConfirmation = unsure > 0 && unsure.toFloat() / attributed.size > AMBIGUITY_TOLERANCE
        return AttributionResult(
            blocks = attributed,
            needsConfirmation = needsConfirmation,
            reason = if (needsConfirmation) {
                "$unsure of ${attributed.size} blocks did not sit clearly on either side"
            } else {
                null
            },
        )
    }

    private enum class Side { LEFT, RIGHT }

    private data class Alignment(val side: Side, val confidence: Float)

    private fun Side.toSender(layout: ChatLayout): Sender = when (this) {
        Side.RIGHT -> if (layout.myMessagesAlignedRight) Sender.ME else Sender.THEM
        Side.LEFT -> if (layout.myMessagesAlignedRight) Sender.THEM else Sender.ME
    }

    /**
     * §4.2's rule, applied literally: within [margin] of one edge and not the
     * other is a decision. Everything else is a guess, scored as one.
     */
    private fun alignByEdges(
        block: TextBlock,
        screenWidth: Int,
        margin: Float,
    ): Pair<TextBlock, Alignment> {
        val leftGap = block.bounds.left.toFloat()
        val rightGap = (screenWidth - block.bounds.right).toFloat()

        val alignment = when {
            rightGap <= margin && leftGap > margin -> Alignment(Side.RIGHT, EDGE_CERTAIN)
            leftGap <= margin && rightGap > margin -> Alignment(Side.LEFT, EDGE_CERTAIN)
            else -> {
                // Either a bubble wide enough to touch both margins, or one
                // floating in the middle. Lean on the smaller gap and say how
                // much the lean is worth.
                val skew = abs(leftGap - rightGap) / screenWidth
                val side = if (rightGap < leftGap) Side.RIGHT else Side.LEFT
                val confidence = when {
                    skew > 0.25f -> 0.85f
                    skew > 0.10f -> 0.65f
                    else -> 0.40f
                }
                Alignment(side, confidence)
            }
        }
        return block to alignment
    }

    /**
     * §4.2's clustering pass: centroids of the blocks the edge rule was sure
     * about become anchors for the ones it was not.
     *
     * Only raises confidence, never flips a certain block, and never invents a
     * cluster that has no anchor — a screenshot where she sent everything and
     * you sent nothing is a real screenshot, not an error, and must not have a
     * phantom "you" cluster fitted to it.
     */
    private fun refineByCluster(
        blocks: List<Pair<TextBlock, Alignment>>,
    ): List<Pair<TextBlock, Alignment>> {
        val anchors = blocks.filter { it.second.confidence >= EDGE_CERTAIN }
        if (anchors.isEmpty()) return blocks

        val centroids = anchors.groupBy { it.second.side }
            .mapValues { (_, group) -> group.map { it.first.bounds.centerX }.average().toFloat() }
        if (centroids.size < 2) return blocks

        val separation = abs(centroids.getValue(Side.LEFT) - centroids.getValue(Side.RIGHT))

        return blocks.map { (block, alignment) ->
            if (alignment.confidence >= EDGE_CERTAIN) {
                block to alignment
            } else {
                val distances = centroids.mapValues { (_, centroid) ->
                    abs(block.bounds.centerX - centroid)
                }
                val nearest = distances.minByOrNull { it.value }!!
                val other = distances.filterKeys { it != nearest.key }.values.first()
                // Close to one centroid and clearly further from the other is
                // worth as much as the edge rule. Equidistant is worth nothing.
                val margin = (other - nearest.value) / separation.coerceAtLeast(1f)
                val confidence = when {
                    margin > 0.5f -> CLUSTER_CONFIDENT
                    margin > 0.2f -> 0.70f
                    else -> alignment.confidence
                }
                block to if (confidence > alignment.confidence) {
                    Alignment(nearest.key, confidence)
                } else {
                    alignment
                }
            }
        }
    }

    private companion object {
        /** Matches [dev.cue.model.Message.MIN_ATTRIBUTION_CONFIDENCE]. */
        const val CONFIDENT = 0.8f
        const val EDGE_CERTAIN = 0.95f
        const val CLUSTER_CONFIDENT = 0.85f

        /** Above this share of unsure blocks, ask rather than assume. */
        const val AMBIGUITY_TOLERANCE = 0.2f
    }
}
