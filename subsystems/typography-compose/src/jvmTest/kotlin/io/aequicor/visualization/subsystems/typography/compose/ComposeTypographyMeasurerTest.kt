package io.aequicor.visualization.subsystems.typography.compose

import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import io.aequicor.visualization.subsystems.typography.FontDescriptor
import io.aequicor.visualization.subsystems.typography.ListLayout
import io.aequicor.visualization.subsystems.typography.ListType
import io.aequicor.visualization.subsystems.typography.RichText
import io.aequicor.visualization.subsystems.typography.TextListSpec
import io.aequicor.visualization.subsystems.typography.Truncation
import io.aequicor.visualization.subsystems.typography.TypographyStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Exercises [ComposeTypographyMeasurer] against a real desktop [TextMeasurer]
 * (skiko-backed, no running app required).
 */
class ComposeTypographyMeasurerTest {

    private fun measurer(fontProvider: FontProvider = NoFonts): ComposeTypographyMeasurer =
        ComposeTypographyMeasurer(SharedMeasurer.textMeasurer, TestDensity, fontProvider)

    @Test
    fun lineTopsIncreaseMonotonically() {
        val rich = RichText(
            text = "first paragraph with several words\nsecond paragraph also has words",
            base = TypographyStyle(fontSize = 14.0),
        )
        val measured = measurer().measure(rich, maxWidth = 120.0)

        assertTrue(measured.lineCount >= 3, "expected wrapping across paragraphs, got ${measured.lineCount} lines")
        measured.lines.zipWithNext().forEach { (a, b) ->
            assertTrue(b.top > a.top, "line tops must increase: ${a.top} then ${b.top}")
        }
        measured.lines.forEach { assertTrue(it.height > 0.0) }
    }

    @Test
    fun paragraphSpacingSeparatesParagraphs() {
        val spaced = measurer().layout(
            RichText("first\nsecond", base = TypographyStyle(fontSize = 14.0, paragraphSpacing = 10.0)),
        )
        val unspaced = measurer().layout(
            RichText("first\nsecond", base = TypographyStyle(fontSize = 14.0)),
        )

        assertEquals(2, spaced.measured.lineCount)
        // The second paragraph's origin sits exactly one paragraph height + spacing down.
        val firstParagraphHeight = spaced.paragraphs[0].result.size.height.toDouble()
        assertEquals(firstParagraphHeight + 10.0, spaced.paragraphs[1].y, 1e-6)
        // Spacing shifts the second line down by exactly the configured amount.
        // (Line tops themselves carry skia's ink metrics: the first line's top can be
        // slightly negative, so absolute tops are compared as deltas.)
        assertEquals(10.0, spaced.measured.lines[1].top - unspaced.measured.lines[1].top, 1e-6)
        assertTrue(spaced.measured.lines[1].top > spaced.measured.lines[0].top + spaced.measured.lines[0].height - EPSILON * 2)
    }

    @Test
    fun maxLinesTruncationCapsTotalLineCountAcrossParagraphs() {
        val rich = RichText(
            text = "a\nb\nc\nd\ne",
            truncate = Truncation(maxLines = 2, ellipsis = false),
        )
        val measured = measurer().measure(rich)

        assertEquals(2, measured.lineCount)
    }

    @Test
    fun maxLinesTruncationCapsWrappedSingleParagraph() {
        val rich = RichText(
            text = "lorem ipsum dolor sit amet consectetur adipiscing elit sed do eiusmod tempor",
            base = TypographyStyle(fontSize = 14.0),
            truncate = Truncation(maxLines = 3, ellipsis = false),
        )
        val measured = measurer().measure(rich, maxWidth = 90.0)

        assertEquals(3, measured.lineCount)
    }

    @Test
    fun bulletListIndentsLinesAndLaysOutMarkers() {
        val rich = RichText(
            text = "item one\nitem two",
            base = TypographyStyle(fontSize = 14.0),
            list = TextListSpec(type = ListType.Bullet, indent = 0),
        )
        val laidOut = measurer().layout(rich)
        val indent = ListLayout.indentPx(rich.list, 14.0)

        assertTrue(indent > 0.0)
        laidOut.measured.lines.forEach { line ->
            assertTrue(line.left >= indent - EPSILON, "line left ${line.left} must be indented by $indent")
        }
        assertEquals(2, laidOut.paragraphs.size)
        laidOut.paragraphs.forEach { paragraph ->
            assertNotNull(paragraph.markerLayout, "bullet paragraphs must lay out a marker")
        }
    }

    @Test
    fun wrapsAtMaxWidth() {
        val rich = RichText(
            text = "lorem ipsum dolor sit amet consectetur adipiscing elit sed do eiusmod",
            base = TypographyStyle(fontSize = 14.0),
        )
        val maxWidth = 100.0
        val measured = measurer().measure(rich, maxWidth = maxWidth)

        assertTrue(measured.lineCount > 1, "expected wrapping, got ${measured.lineCount} line(s)")
        measured.lines.forEach { line ->
            assertTrue(line.width <= maxWidth + EPSILON, "line width ${line.width} exceeds max $maxWidth")
        }
    }

    @Test
    fun cacheReturnsSameInstanceForSameInput() {
        val subject = measurer()
        val rich = RichText("cached text")

        assertSame(subject.layout(rich, maxWidth = 200.0), subject.layout(rich, maxWidth = 200.0))
    }

    @Test
    fun fontGenerationBumpMissesCache() {
        val provider = MutableGenerationFontProvider()
        val subject = measurer(provider)
        val rich = RichText("cached text")

        val first = subject.layout(rich, maxWidth = 200.0)
        provider.generation = 1
        val second = subject.layout(rich, maxWidth = 200.0)

        assertNotSame(first, second)
    }

    @Test
    fun caretAtZeroStartsAtLeftEdge() {
        val laidOut = measurer().layout(RichText("hello"))
        val rect = laidOut.caretRect(0)

        assertEquals(0.0, rect.left, EPSILON)
        assertTrue(rect.height > 0.0)
    }

    @Test
    fun caretAtZeroInListStartsAtIndent() {
        val rich = RichText(
            text = "hello",
            base = TypographyStyle(fontSize = 14.0),
            list = TextListSpec(type = ListType.Bullet, indent = 0),
        )
        val laidOut = measurer().layout(rich)
        val indent = ListLayout.indentPx(rich.list, 14.0)

        assertEquals(indent, laidOut.caretRect(0).left, EPSILON)
    }

    @Test
    fun offsetAtCaretCenterRoundTrips() {
        val laidOut = measurer().layout(RichText("hello world"))

        listOf(0, 3, 5, 8, 11).forEach { offset ->
            val rect = laidOut.caretRect(offset)
            val hit = laidOut.offsetAt(rect.left, rect.top + rect.height / 2)
            assertEquals(offset, hit, "offset $offset did not round-trip through caretRect/offsetAt")
        }
    }

    @Test
    fun wordBoundaryReturnsContainingWord() {
        val laidOut = measurer().layout(RichText("hello world"))

        assertEquals(0 until 5, laidOut.wordBoundaryAt(2))
        assertEquals(6 until 11, laidOut.wordBoundaryAt(8))
    }

    @Test
    fun selectionRectsForMidStringRangeStayWithinLineBounds() {
        val laidOut = measurer().layout(RichText("hello world"))
        val rects = laidOut.selectionRects(2, 8)

        assertTrue(rects.isNotEmpty())
        val line = laidOut.measured.lines.single()
        rects.forEach { rect ->
            assertTrue(rect.width > 0.0)
            assertTrue(rect.top >= line.top - EPSILON)
            assertTrue(rect.bottom <= line.top + line.height + EPSILON)
            assertTrue(rect.left >= line.left - EPSILON)
            assertTrue(rect.right <= line.left + line.width + EPSILON)
        }
    }

    private class MutableGenerationFontProvider : FontProvider {
        override var generation: Int = 0
        override val families: List<FontFamilyInfo> = emptyList()
        override fun resolve(descriptor: FontDescriptor): FontFamily? = null
    }

    private companion object SharedMeasurer {
        val TestDensity = Density(1f)
        val textMeasurer: TextMeasurer by lazy {
            TextMeasurer(createFontFamilyResolver(), TestDensity, LayoutDirection.Ltr)
        }
        const val EPSILON = 0.6
    }
}
