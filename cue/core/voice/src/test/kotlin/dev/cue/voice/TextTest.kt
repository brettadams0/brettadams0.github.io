package dev.cue.voice

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The primitives everything in §4 stands on. Wrong here means wrong everywhere,
 * silently — a miscounted emoji becomes a wrong `emojiRate` becomes a compiler
 * that strips the emoji you always use.
 */
class TextTest {

    @Test
    fun `counts words with internal apostrophes as one`() {
        assertEquals(4, Text.wordCount("i don't think so"))
        assertEquals(4, Text.wordCount("i don’t think so"))
    }

    @Test
    fun `standalone I is found and island is not`() {
        assertEquals(1, Text.standaloneIOccurrences("I went to the island").size)
        assertEquals(0, Text.standaloneIOccurrences("in the interim, hi").size)
        assertEquals(2, Text.standaloneIOccurrences("I'm sure I said that").size)
    }

    @Test
    fun `terminal punctuation ignores trailing emoji`() {
        assertTrue(Text.endsWithTerminalPunctuation("that's the plan. 😂"))
        assertFalse(Text.endsWithTerminalPunctuation("that's the plan 😂"))
    }

    @Test
    fun `multi-codepoint emoji count as one`() {
        // Skin tone modifier, ZWJ sequence, keycap, flag: four emoji, twelve chars.
        assertEquals(1, Emoji.count("👍🏽"))
        assertEquals(1, Emoji.count("👨‍👩‍👧"))
        assertEquals(1, Emoji.count("🇨🇦"))
        assertEquals(1, Emoji.count("❤️"))
        assertEquals(4, Emoji.count("👍🏽 👨‍👩‍👧 🇨🇦 ❤️"))
    }

    @Test
    fun `emoji ranges do not split surrogate pairs`() {
        val text = "sounds good 👨‍👩‍👧 see you"
        val range = Emoji.ranges(text).single()
        val extracted = text.substring(range.first, range.last + 1)
        assertEquals("👨‍👩‍👧", extracted)
        // Removing by range must leave valid text on both sides.
        val without = StringBuilder(text).delete(range.first, range.last + 1).toString()
        assertEquals("sounds good  see you", without)
    }

    @Test
    fun `clause boundaries land after punctuation and before conjunctions`() {
        val text = "i went there, it was fine and the walk back was better"
        val boundaries = Text.clauseBoundaries(text)
        assertTrue(boundaries.contains(text.indexOf(',') + 1), "comma boundary")
        assertTrue(boundaries.contains(text.indexOf(" and ")), "conjunction boundary")
    }

    @Test
    fun `a run of punctuation is one boundary`() {
        assertEquals(1, Text.clauseBoundaries("really?! ok").size)
    }
}
