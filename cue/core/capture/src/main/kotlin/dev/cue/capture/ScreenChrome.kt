package dev.cue.capture

/**
 * Tells message text apart from the app's own furniture.
 *
 * OCR does not know what a nav bar is. It returns "Unmatch", "Type a message"
 * and "2:14 PM" as text blocks alongside her actual messages, and every one of
 * them would otherwise become a message — polluting the voice profile with the
 * app's vocabulary, and giving §7.2 a "context" that contains words nobody
 * said. Cheap to filter, expensive to miss.
 */
object ScreenChrome {

    /** Fraction of screen height occupied by the nav bar at the top. */
    private const val HEADER_BAND = 0.06f

    /** Fraction of screen height occupied by the composer at the bottom. */
    private const val COMPOSER_BAND = 0.93f

    private val TIMESTAMP_PATTERNS = listOf(
        Regex("""^\d{1,2}:\d{2}\s?(?:[ap]\.?m\.?)?$""", RegexOption.IGNORE_CASE),
        Regex("""^\d{1,2}/\d{1,2}(?:/\d{2,4})?$"""),
        Regex("""^\d+\s?[mhdw](?:\s?ago)?$""", RegexOption.IGNORE_CASE),
        Regex(
            """^(?:today|yesterday|now|just now|sent|delivered|read|seen|online)$""",
            RegexOption.IGNORE_CASE,
        ),
        Regex(
            """^(?:mon|tue|tues|wed|thu|thur|thurs|fri|sat|sun)(?:day)?$""",
            RegexOption.IGNORE_CASE,
        ),
    )

    /**
     * Interface copy from both apps. Matched case-insensitively against the
     * whole block, since these arrive as their own blocks rather than embedded
     * in a message.
     */
    private val INTERFACE_COPY = setOf(
        "type a message", "send a message", "say something", "message",
        "send", "sent", "gif", "gifs", "photo", "camera", "sticker",
        "unmatch", "report", "block", "share", "menu", "back", "chat", "chats",
        "matches", "likes you", "your turn", "their turn", "video chat",
        "add to favorites", "favorite", "comment", "send like", "like",
        "you matched", "start the conversation", "new match",
        "send a message to start the conversation", "aa", "done", "cancel",
        "keyboard", "emoji", "voice message", "hold to record",
    )

    private val PARTIAL_INTERFACE_COPY = listOf(
        "you matched with",
        "say hi to",
        "this is the beginning of",
        "you liked",
        "liked your",
        "commented on your",
    )

    fun isChromeText(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return true
        val lower = trimmed.lowercase().trimEnd('.', '!', '·', '•')
        if (lower in INTERFACE_COPY) return true
        if (PARTIAL_INTERFACE_COPY.any { lower.startsWith(it) }) return true
        if (TIMESTAMP_PATTERNS.any { it.matches(trimmed) }) return true
        return false
    }

    /**
     * True when the block is app furniture rather than a message.
     *
     * Position is only trusted at the extremes. A block sitting in the bottom
     * 7% is the composer; a block in the top 6% is the nav bar. Everything
     * between them is judged on its text alone, because a real message can be
     * anywhere in the scroll.
     */
    fun isChrome(block: TextBlock, screen: RecognizedScreen): Boolean {
        if (isChromeText(block.text)) return true
        val height = screen.screenHeight.toFloat()
        if (height <= 0f) return false
        if (block.bounds.bottom < height * HEADER_BAND) return true
        if (block.bounds.top > height * COMPOSER_BAND) return true
        return false
    }

    /**
     * Her name as the header shows it, if the header is in frame.
     *
     * Used for the conversation's pseudonym and nothing else. §10 keeps her
     * photos unprocessed and her data on the device; the display name is the
     * one field the app needs to tell two conversations apart.
     */
    fun headerName(screen: RecognizedScreen): String? {
        val height = screen.screenHeight.toFloat()
        if (height <= 0f) return null
        return screen.blocks
            .filter { it.bounds.bottom <= height * (HEADER_BAND * 2f) }
            .map { it.text.trim() }
            .firstOrNull { candidate ->
                candidate.isNotEmpty() &&
                    !isChromeText(candidate) &&
                    candidate.split(Regex("""\s+""")).size <= 3 &&
                    candidate.any { it.isLetter() }
            }
    }
}
