package io.aequicor.visualization.subsystems.typography

import kotlin.test.Test
import kotlin.test.assertEquals

/** [ListLayout] coverage: paragraph splitting, markers, levels and indent geometry. */
class ListLayoutTest {

    @Test
    fun splitsParagraphsOnNewlines() {
        val paragraphs = ListLayout.paragraphs("ab\ncd", TextListSpec())
        assertEquals(listOf(0, 1), paragraphs.map { it.index })
        assertEquals(listOf(0 to 2, 3 to 5), paragraphs.map { it.start to it.end })
    }

    @Test
    fun trailingNewlineYieldsEmptyLastParagraph() {
        val paragraphs = ListLayout.paragraphs("ab\n", TextListSpec())
        assertEquals(2, paragraphs.size)
        assertEquals(3 to 3, paragraphs[1].start to paragraphs[1].end)
    }

    @Test
    fun emptyTextIsOneEmptyParagraph() {
        assertEquals(
            listOf(ListLayout.Paragraph(index = 0, start = 0, end = 0, marker = "", level = 0)),
            ListLayout.paragraphs("", TextListSpec()),
        )
    }

    @Test
    fun orderedListNumbersEveryParagraphIncludingEmptyOnes() {
        val markers = ListLayout.paragraphs("a\nb\n\nc", TextListSpec(type = ListType.Ordered))
            .map { it.marker }
        assertEquals(listOf("1.", "2.", "3.", "4."), markers)
    }

    @Test
    fun bulletListUsesBulletMarkerAndIndentLevel() {
        val paragraphs = ListLayout.paragraphs("a\nb", TextListSpec(type = ListType.Bullet, indent = 2))
        assertEquals(listOf(ListLayout.BULLET, ListLayout.BULLET), paragraphs.map { it.marker })
        assertEquals(listOf(2, 2), paragraphs.map { it.level })
    }

    @Test
    fun noneListHasNoMarkerAndZeroLevelRegardlessOfIndent() {
        val paragraph = ListLayout.paragraphs("a", TextListSpec(type = ListType.None, indent = 3)).single()
        assertEquals("", paragraph.marker)
        assertEquals(0, paragraph.level)
    }

    @Test
    fun negativeIndentClampsToZero() {
        val list = TextListSpec(type = ListType.Bullet, indent = -2)
        assertEquals(0, ListLayout.paragraphs("a", list).single().level)
        assertEquals(ListLayout.INDENT_EM * 10.0, ListLayout.indentPx(list, 10.0), 1e-9)
    }

    @Test
    fun indentPxScalesWithLevelAndFontSize() {
        assertEquals(0.0, ListLayout.indentPx(TextListSpec(), 16.0), 1e-9)
        assertEquals(1.5 * 16.0, ListLayout.indentPx(TextListSpec(ListType.Bullet, 0), 16.0), 1e-9)
        assertEquals(3 * 1.5 * 16.0, ListLayout.indentPx(TextListSpec(ListType.Ordered, 2), 16.0), 1e-9)
    }
}
