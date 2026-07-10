package io.aequicor.visualization.subsystems.typography

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** [FontStyles] coverage: the standard 18 styles, name round-trips and CSS-like matching. */
class FontStylesTest {

    @Test
    fun standardHasEighteenUniqueStyles() {
        assertEquals(18, FontStyles.STANDARD.size)
        assertEquals(18, FontStyles.STANDARD.map { it.name }.distinct().size)
        assertTrue(NamedFontStyle("Regular", 400, italic = false) in FontStyles.STANDARD)
        assertTrue(NamedFontStyle("Italic", 400, italic = true) in FontStyles.STANDARD)
        assertTrue(NamedFontStyle("Bold Italic", 700, italic = true) in FontStyles.STANDARD)
    }

    @Test
    fun nameForMapsWeightsAndItalic() {
        assertEquals("Regular", FontStyles.nameFor(400, italic = false))
        assertEquals("Italic", FontStyles.nameFor(400, italic = true))
        assertEquals("Bold", FontStyles.nameFor(700, italic = false))
        assertEquals("Thin", FontStyles.nameFor(100, italic = false))
        assertEquals("Black Italic", FontStyles.nameFor(900, italic = true))
        // Off-scale weights snap to the nearest named weight.
        assertEquals("SemiBold", FontStyles.nameFor(620, italic = false))
    }

    @Test
    fun parseRoundTripsEveryStandardName() {
        FontStyles.STANDARD.forEach { style ->
            assertEquals(style, FontStyles.parse(style.name))
        }
    }

    @Test
    fun parseIsCaseInsensitiveAndTrimsWhitespace() {
        assertEquals(NamedFontStyle("Bold Italic", 700, italic = true), FontStyles.parse(" bold italic "))
        assertEquals(NamedFontStyle("Bold Italic", 700, italic = true), FontStyles.parse("BOLD ITALIC"))
        assertEquals(NamedFontStyle("Medium", 500, italic = false), FontStyles.parse("medium"))
    }

    @Test
    fun parseUnknownNameFallsBackToRegular() {
        assertEquals(NamedFontStyle("Regular", 400, italic = false), FontStyles.parse("Wobbly"))
        assertEquals(NamedFontStyle("Italic", 400, italic = true), FontStyles.parse("Wobbly Italic"))
    }

    @Test
    fun closestPrefersRequestedItalicPoolOverWeight() {
        val available = listOf(
            NamedFontStyle("Regular", 400, italic = false),
            NamedFontStyle("Bold Italic", 700, italic = true),
        )
        assertEquals(available[1], FontStyles.closest(available, weight = 400, italic = true))
        assertEquals(available[0], FontStyles.closest(available, weight = 700, italic = false))
    }

    @Test
    fun closestFallsBackToOppositeSlantWhenPoolIsEmpty() {
        val romanOnly = listOf(NamedFontStyle("Bold", 700, italic = false))
        assertEquals(romanOnly[0], FontStyles.closest(romanOnly, weight = 700, italic = true))
    }

    @Test
    fun closestPicksNearestWeightWithinPool() {
        val available = listOf(
            NamedFontStyle("Light", 300, italic = false),
            NamedFontStyle("Bold", 700, italic = false),
        )
        assertEquals(available[0], FontStyles.closest(available, weight = 400, italic = false))
        assertEquals(available[1], FontStyles.closest(available, weight = 600, italic = false))
    }

    @Test
    fun closestBreaksWeightTiesDirectionally() {
        val available = listOf(
            NamedFontStyle("Light", 300, italic = false),
            NamedFontStyle("Medium", 500, italic = false),
            NamedFontStyle("Bold", 700, italic = false),
        )
        // Requests <= 500 prefer the lighter side of a tie, > 500 the heavier side.
        assertEquals(available[0], FontStyles.closest(available, weight = 400, italic = false))
        assertEquals(available[2], FontStyles.closest(available, weight = 600, italic = false))
    }

    @Test
    fun closestOfEmptyListIsNull() {
        assertNull(FontStyles.closest(emptyList(), weight = 400, italic = false))
    }
}
