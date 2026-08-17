package dev.cue.capture

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * §5.4 — profile capture, with the prompt answers as the field that matters.
 */
class ProfileParserTest {

    private fun screenOf(vararg lines: String): RecognizedScreen {
        var y = 400
        val blocks = lines.map { line ->
            val block = TextBlock(line, BoundingBox(left = 80, top = y, right = 1000, bottom = y + 60))
            y += 90
            block
        }
        return RecognizedScreen("profile", 1080, 2400, blocks, capturedAt = 42L)
    }

    @Test
    fun `pairs a Hinge prompt with the answer under it`() {
        val profile = ProfileParser.parse(
            listOf(
                screenOf(
                    "Maya, 27",
                    "Two truths and a lie",
                    "i've broken three bones, i can't swim, i've met a prime minister",
                    "My simple pleasures",
                    "the first coffee, before anyone else is up",
                ),
            ),
            capturedAt = 42L,
        )

        assertEquals("Maya", profile.displayName)
        assertEquals(27, profile.age)
        assertEquals(2, profile.prompts.size)
        assertEquals("Two truths and a lie", profile.prompts.first().prompt)
        assertTrue(profile.prompts.first().answer.startsWith("i've broken three bones"))
        assertEquals("My simple pleasures", profile.prompts[1].prompt)
    }

    @Test
    fun `a prompt she left blank produces no pair`() {
        val profile = ProfileParser.parse(
            listOf(screenOf("Typical Sunday", "My greatest strength", "stubbornness")),
            capturedAt = 0L,
        )
        assertEquals(1, profile.prompts.size)
        assertEquals("My greatest strength", profile.prompts.single().prompt)
    }

    @Test
    fun `height and intent are the only chips it will claim`() {
        val profile = ProfileParser.parse(
            listOf(screenOf("Jules, 31", "5' 7\"", "Long-term relationship", "Toronto")),
            capturedAt = 0L,
        )
        assertEquals("5'7\"", profile.attributes["height"])
        assertEquals("Long-term relationship", profile.attributes["intent"])
        // "Toronto" is a bare string next to an icon OCR cannot read. It is not
        // given a key it might not deserve.
        assertTrue(profile.attributes.keys.toSet() == setOf("height", "intent"), profile.attributes.toString())
    }

    @Test
    fun `a Tinder bio blob becomes the bio`() {
        val bio = "engineer, ex-competitive swimmer, will argue about coffee for hours"
        val profile = ProfileParser.parse(listOf(screenOf("Nora, 29", bio)), capturedAt = 0L)
        assertEquals(bio, profile.bio)
    }

    @Test
    fun `an empty profile reports itself as empty`() {
        assertTrue(ProfileParser.parse(emptyList(), capturedAt = 0L).isEmpty)
    }
}
