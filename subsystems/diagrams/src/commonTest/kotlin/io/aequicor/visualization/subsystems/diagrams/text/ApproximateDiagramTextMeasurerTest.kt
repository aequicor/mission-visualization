package io.aequicor.visualization.subsystems.diagrams.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** [ApproximateDiagramTextMeasurer]: natural width, word wrapping, and the repro's numbers. */
class ApproximateDiagramTextMeasurerTest {

    private val measurer = ApproximateDiagramTextMeasurer()
    private val label = "Вести контакты собственников и совета дома"

    @Test
    fun theReproLabelMeasuresAboutTwoHundredNinetyFivePixels() {
        // 42 Cyrillic characters at Inter 13px, average advance 0.540 -> ~294.8px. The authored
        // ellipse around it was 930px wide: the size was guessed, not measured.
        assertEquals(42, label.length)

        val measured = measurer.measure(label, DiagramTextStyle(fontSize = 13.0))

        assertEquals(294.8, measured.width, 294.8 * 0.02)
        assertEquals(listOf(label), measured.lines, "unbounded measurement must not wrap")
    }

    @Test
    fun nullMaxWidthYieldsTheNaturalWidth() {
        val measured = measurer.measure("hello", DiagramTextStyle(fontSize = 10.0))

        assertEquals(5 * 10.0 * 0.54, measured.width, 1e-9)
        assertEquals(1, measured.lines.size)
    }

    @Test
    fun wrappingBreaksOnWordBoundariesNotMidWord() {
        // 5 chars * 10px * 0.54 = 27px per word; a 60px box fits two words ("alpha beta" = 59.4).
        val measured = measurer.measure("alpha beta gamma", DiagramTextStyle(fontSize = 10.0), maxWidth = 60.0)

        assertEquals(listOf("alpha beta", "gamma"), measured.lines)
        measured.lines.forEach { line ->
            assertTrue(line.trim() == line, "no line may keep the wrap space: '$line'")
        }
    }

    @Test
    fun noLineExceedsTheBoxWidth() {
        val measured = measurer.measure(label, DiagramTextStyle(fontSize = 13.0), maxWidth = 120.0)

        assertTrue(measured.lines.size > 1, "a 295px label must wrap inside a 120px box")
        assertTrue(measured.width <= 120.0, "wrapped width ${measured.width} exceeds the box")
    }

    @Test
    fun aWordWiderThanTheBoxBreaksMidWord() {
        val measured = measurer.measure("supercalifragilistic", DiagramTextStyle(fontSize = 10.0), maxWidth = 30.0)

        assertTrue(measured.lines.size > 1)
        assertEquals("supercalifragilistic", measured.lines.joinToString(""), "no characters may be lost")
    }

    @Test
    fun heightGrowsWithTheLineCount() {
        val one = measurer.measure("alpha", DiagramTextStyle(fontSize = 10.0))
        val two = measurer.measure("alpha beta", DiagramTextStyle(fontSize = 10.0), maxWidth = 30.0)

        assertEquals(2, two.lines.size)
        assertEquals(one.height * 2, two.height, 1e-9)
    }

    @Test
    fun explicitNewlinesStartNewLines() {
        val measured = measurer.measure("a\nb", DiagramTextStyle(fontSize = 10.0))

        assertEquals(listOf("a", "b"), measured.lines)
    }
}
