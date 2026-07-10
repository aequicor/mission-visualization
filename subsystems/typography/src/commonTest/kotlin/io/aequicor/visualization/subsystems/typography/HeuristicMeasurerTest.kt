package io.aequicor.visualization.subsystems.typography

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [HeuristicTypographyMeasurer] coverage. Uses fontSize 10 so per-char advance is
 * exactly 6.0 px and the auto line height is exactly 12.5 px.
 */
class HeuristicMeasurerTest {

    private val eps = 1e-9
    private val measurer = HeuristicTypographyMeasurer()
    private val base = TypographyStyle(fontSize = 10.0)

    @Test
    fun singleLineNaturalWidthIsCharCountTimesAdvance() {
        val measured = measurer.measure(RichText("hello", base))
        assertEquals(1, measured.lineCount)
        assertEquals(5 * 6.0, measured.width, eps)
        assertEquals(12.5, measured.height, eps)
        val line = measured.lines.single()
        assertEquals(0 to 5, line.start to line.end)
        assertEquals(0.0, line.left, eps)
        assertEquals(5 * 6.0, line.width, eps)
    }

    @Test
    fun letterSpacingAddsToEveryAdvance() {
        val spaced = base.copy(letterSpacing = LetterSpacing.Px(1.0))
        assertEquals(3 * 7.0, measurer.measure(RichText("abc", spaced)).width, eps)
    }

    @Test
    fun wrapsGreedilyAtMaxWidth() {
        val measured = measurer.measure(RichText("aaaaaaaaaa", base), maxWidth = 30.0)
        assertEquals(2, measured.lineCount)
        assertEquals(listOf(0 to 5, 5 to 10), measured.lines.map { it.start to it.end })
        assertEquals(30.0, measured.width, eps)
        assertEquals(2 * 12.5, measured.height, eps)
    }

    @Test
    fun mixedSizeSpanRaisesLineHeightToTallestRun() {
        val rich = RichText(
            text = "abcdef",
            base = base,
            spans = listOf(StyleSpan(3, 6, TypographyStyle(fontSize = 20.0))),
        )
        val line = measurer.measure(rich).lines.single()
        assertEquals(25.0, line.height, eps) // 20 * 1.25, not the base 12.5
        assertEquals(3 * 6.0 + 3 * 12.0, line.width, eps)
        assertEquals(25.0 * TypographyDefaults.ASCENT_FACTOR, line.baseline, eps)
    }

    @Test
    fun newlineSplitsLinesAndAddsParagraphSpacing() {
        val measured = measurer.measure(RichText("ab\ncd", base.copy(paragraphSpacing = 4.0)))
        assertEquals(2, measured.lineCount)
        assertEquals(0.0, measured.lines[0].top, eps)
        assertEquals(12.5 + 4.0, measured.lines[1].top, eps)
        assertEquals(12.5 + 4.0 + 12.5, measured.height, eps)
    }

    @Test
    fun emptyMiddleParagraphOccupiesItsOwnLine() {
        val measured = measurer.measure(RichText("a\n\nb", base))
        assertEquals(3, measured.lineCount)
        assertEquals(2 to 2, measured.lines[1].let { it.start to it.end })
        assertEquals(3 * 12.5, measured.height, eps)
    }

    @Test
    fun paragraphIndentAppliesToFirstLineOnly() {
        val measured = measurer.measure(
            RichText("aaaaaaaa", base.copy(paragraphIndent = 6.0)),
            maxWidth = 30.0,
        )
        // First line: 6 px indent + 4 chars = 30 px; continuation line gets no indent.
        assertEquals(listOf(0 to 4, 4 to 8), measured.lines.map { it.start to it.end })
        assertEquals(30.0, measured.lines[0].width, eps)
        assertEquals(4 * 6.0, measured.lines[1].width, eps)
    }

    @Test
    fun truncationCapsParagraphLines() {
        val measured = measurer.measure(RichText("a\nb\nc\nd", base, truncate = Truncation(maxLines = 2)))
        assertEquals(2, measured.lineCount)
        assertEquals(2 * 12.5, measured.height, eps)
    }

    @Test
    fun truncationCapsWrappedLines() {
        val measured = measurer.measure(
            RichText("aaaaaaaaaaaaaaaaaaaa", base, truncate = Truncation(maxLines = 2)),
            maxWidth = 30.0,
        )
        assertEquals(2, measured.lineCount)
    }

    @Test
    fun emptyTextYieldsOneLineWithBaseLineHeight() {
        val measured = measurer.measure(RichText("", base))
        assertEquals(1, measured.lineCount)
        assertEquals(0.0, measured.width, eps)
        assertEquals(12.5, measured.height, eps)
        assertEquals(0 to 0, measured.lines.single().let { it.start to it.end })
    }

    @Test
    fun baselinesAreSaneAcrossTwoLines() {
        val measured = measurer.measure(RichText("ab\ncd", base))
        val first = measured.firstBaseline!!
        val last = measured.lastBaseline!!
        assertEquals(12.5 * TypographyDefaults.ASCENT_FACTOR, first, eps)
        assertTrue(last > first, "expected lastBaseline ($last) > firstBaseline ($first)")
        assertEquals(12.5 + 12.5 * TypographyDefaults.ASCENT_FACTOR, last, eps)
    }

    @Test
    fun listIndentShiftsLinesRight() {
        val rich = RichText("ab", base, list = TextListSpec(ListType.Bullet, indent = 0))
        val measured = measurer.measure(rich)
        assertEquals(15.0, measured.lines.single().left, eps) // 1.5em of 10 px
        assertEquals(15.0 + 2 * 6.0, measured.width, eps)
    }

    @Test
    fun baseCasingTransformsTextBeforeMeasuring() {
        // ß expands to SS under Upper: 3 source chars measure as 4 rendered advances.
        val measured = measurer.measure(RichText("aßb", base.copy(case = TextCasing.Upper)))
        assertEquals(4 * 6.0, measured.width, eps)
        assertEquals(0 to 4, measured.lines.single().let { it.start to it.end })
    }
}
