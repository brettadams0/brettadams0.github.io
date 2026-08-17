package dev.cue.capture

import dev.cue.model.Sender
import kotlin.random.Random

/**
 * Synthetic chat screenshots with known labels.
 *
 * §14.1 asks for 50 hand-labelled screenshots from both apps at >98%
 * attribution accuracy. **These are not those.** They are generated from the
 * geometry both apps actually use — a bubble hugging one margin, a wrapped
 * message arriving as several blocks with the same horizontal extent, a header,
 * timestamps, a composer — with the sender recorded as ground truth.
 *
 * What that buys is real: it catches a reversed axis, a broken chrome filter, a
 * bubble merged across senders, and every regression in those. What it cannot
 * do is tell you the margins are right, because the margins came from the same
 * head as the fixture. Until 50 real screenshots exist, §14.1 is *exercised*
 * here and *unvalidated* in the README.
 */
object ChatFixtures {

    const val SCREEN_WIDTH = 1080
    const val SCREEN_HEIGHT = 2400

    /** Both apps leave roughly this much gutter beside a bubble. */
    private const val MARGIN = 40

    /** A bubble never spans more than this share of the width in either app. */
    private const val MAX_BUBBLE_FRACTION = 0.78

    private const val LINE_HEIGHT = 56
    private const val LINE_GAP = 4
    private const val BUBBLE_GAP = 34
    private const val CHARS_PER_LINE = 32

    data class LabelledBubble(val text: String, val sender: Sender)

    data class LabelledScreen(
        val screen: RecognizedScreen,
        val bubbles: List<LabelledBubble>,
    )

    /**
     * Lays out [bubbles] as a chat screenshot.
     *
     * Wrapped lines become separate blocks sharing an x-extent, which is what
     * ML Kit returns for a multi-line bubble and the thing
     * [ConversationStitcher] has to put back together.
     */
    fun screen(
        id: String,
        bubbles: List<LabelledBubble>,
        capturedAt: Long = 0L,
        includeChrome: Boolean = true,
        headerName: String = "Maya",
        startY: Int = (SCREEN_HEIGHT * 0.12).toInt(),
    ): LabelledScreen {
        val blocks = mutableListOf<TextBlock>()

        if (includeChrome) {
            blocks += TextBlock(
                headerName,
                BoundingBox(left = 460, top = 60, right = 620, bottom = 116),
            )
            blocks += TextBlock(
                "Type a message",
                BoundingBox(left = 60, top = 2290, right = 420, bottom = 2346),
            )
        }

        var y = startY
        bubbles.forEachIndexed { index, bubble ->
            val lines = wrap(bubble.text)
            val widest = lines.maxOf { it.length }
            val bubbleWidth = (widest * 22)
                .coerceAtMost((SCREEN_WIDTH * MAX_BUBBLE_FRACTION).toInt())
                .coerceAtLeast(140)

            val left: Int
            val right: Int
            if (bubble.sender == Sender.ME) {
                right = SCREEN_WIDTH - MARGIN
                left = right - bubbleWidth
            } else {
                left = MARGIN
                right = left + bubbleWidth
            }

            lines.forEach { line ->
                blocks += TextBlock(
                    line,
                    BoundingBox(left = left, top = y, right = right, bottom = y + LINE_HEIGHT),
                )
                y += LINE_HEIGHT + LINE_GAP
            }

            // Both apps punctuate the scroll with time separators.
            if (includeChrome && index % 3 == 2) {
                blocks += TextBlock(
                    "2:1$index PM",
                    BoundingBox(left = 480, top = y, right = 600, bottom = y + 40),
                )
                y += 44
            }
            y += BUBBLE_GAP
        }

        return LabelledScreen(
            screen = RecognizedScreen(
                id = id,
                screenWidth = SCREEN_WIDTH,
                screenHeight = SCREEN_HEIGHT,
                // OCR does not return blocks in reading order. Shuffling with a
                // fixed seed keeps the fixture honest about that without making
                // the test flaky.
                blocks = blocks.shuffled(Random(id.hashCode())),
                capturedAt = capturedAt,
            ),
            bubbles = bubbles,
        )
    }

    /** 50 screens of varied conversation, for the §14.1 accuracy run. */
    fun accuracyCorpus(): List<LabelledScreen> = (0 until 50).map { index ->
        val random = Random(1000 + index)
        val count = 4 + random.nextInt(5)
        val bubbles = (0 until count).map { position ->
            val sender = if (random.nextInt(100) < 45) Sender.ME else Sender.THEM
            LabelledBubble(
                text = if (sender == Sender.ME) {
                    MY_MESSAGES[random.nextInt(MY_MESSAGES.size)]
                } else {
                    HER_MESSAGES[random.nextInt(HER_MESSAGES.size)]
                },
                sender = sender,
            )
        }
        screen(
            id = "screen-$index",
            bubbles = bubbles,
            capturedAt = index * 1_000L,
            headerName = HEADER_NAMES[index % HEADER_NAMES.size],
        )
    }

    private fun wrap(text: String): List<String> {
        if (text.length <= CHARS_PER_LINE) return listOf(text)
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var current = StringBuilder()
        words.forEach { word ->
            if (current.isEmpty()) {
                current.append(word)
            } else if (current.length + 1 + word.length <= CHARS_PER_LINE) {
                current.append(' ').append(word)
            } else {
                lines += current.toString()
                current = StringBuilder(word)
            }
        }
        if (current.isNotEmpty()) lines += current.toString()
        return lines
    }

    private val HEADER_NAMES = listOf("Maya", "Priya", "Jules", "Nora", "Sam")

    val MY_MESSAGES = listOf(
        "ok that's a strong opinion",
        "i've been meaning to try that place for months honestly",
        "how was the rest of your week",
        "no shot",
        "i'd have said the same thing",
        "the walk back is the good part, everyone gets that wrong",
        "genuinely what got you into it",
        "that tracks",
    )

    val HER_MESSAGES = listOf(
        "haha ok fair",
        "it was so much better than i expected, we stayed until they closed",
        "what are you up to this weekend",
        "i've never been!",
        "my flatmate keeps telling me to go",
        "ok but where's the best coffee near you",
        "that's a bold claim",
        "i'm free thursday if you are",
    )
}
