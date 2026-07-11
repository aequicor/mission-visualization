package io.aequicor.visualization.subsystems.typography.compose

import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import io.aequicor.visualization.subsystems.typography.LineHeight
import io.aequicor.visualization.subsystems.typography.LinkSpan
import io.aequicor.visualization.subsystems.typography.RichText
import io.aequicor.visualization.subsystems.typography.StyleSpan
import io.aequicor.visualization.subsystems.typography.TypographyStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Link hit-rects, paragraph-granular per-range line height, BiDi sanity, skip-ink carving. */
class LinkAndLineHeightTest {

    private val density = Density(1f)
    private val textMeasurer by lazy { TextMeasurer(createFontFamilyResolver(), density, LayoutDirection.Ltr) }

    private fun measurer(): ComposeTypographyMeasurer =
        ComposeTypographyMeasurer(textMeasurer, density, NoFonts)

    @Test
    fun linkRectsCoverTheLinkRange() {
        val rich = RichText(
            text = "Visit our site now",
            base = TypographyStyle(fontSize = 16.0),
            links = listOf(LinkSpan(6, 14, url = "https://example.com")),
        )
        val laid = measurer().layout(rich)
        val links = laid.linkRects()
        assertEquals(1, links.size, "one link")
        val (link, rects) = links.first()
        assertEquals("https://example.com", link.url)
        assertTrue(rects.isNotEmpty(), "link has hit-rects")
        rects.forEach { assertTrue(it.width > 0.0 && it.height > 0.0) }
        // A point inside the first rect resolves to the link; a point far left (before it) does not.
        val hit = rects.first()
        assertEquals(link, laid.linkAt(hit.left + hit.width / 2, hit.top + hit.height / 2))
    }

    @Test
    fun linkAtMissesOutsideAnyLink() {
        val rich = RichText(
            text = "no links here",
            base = TypographyStyle(fontSize = 16.0),
        )
        val laid = measurer().layout(rich)
        assertEquals(null, laid.linkAt(5.0, 5.0))
        assertTrue(laid.linkRects().isEmpty())
    }

    @Test
    fun paragraphAdoptsTallestRangeLineHeight() {
        // Two paragraphs; the second is fully covered by a range with a much bigger line height.
        val rich = RichText(
            text = "short line\nbig line here",
            base = TypographyStyle(fontSize = 14.0, lineHeight = LineHeight.Px(16.0)),
            spans = listOf(StyleSpan(11, 24, TypographyStyle(lineHeight = LineHeight.Px(48.0)))),
        )
        val laid = measurer().layout(rich, maxWidth = 400.0)
        val lines = laid.measured.lines
        assertTrue(lines.size >= 2, "expected two lines, got ${lines.size}")
        // The second paragraph's line is taller than the first thanks to the range line height.
        assertTrue(
            lines.last().height > lines.first().height,
            "range line height should make the 2nd line taller: ${lines.first().height} vs ${lines.last().height}",
        )
    }

    @Test
    fun rtlTextMeasuresAndSelectsWithoutError() {
        val rich = RichText(
            text = "שלום עולם זהו טקסט", // Hebrew
            base = TypographyStyle(fontSize = 16.0),
        )
        val laid = measurer().layout(rich, maxWidth = 300.0)
        assertTrue(laid.measured.width > 0.0 && laid.measured.height > 0.0)
        val rects = laid.selectionRects(0, 5)
        assertTrue(rects.all { it.width >= 0.0 && it.height > 0.0 }, "RTL selection rects are sane")
        val caret = laid.caretRect(3)
        assertTrue(caret.height > 0.0, "RTL caret has height")
    }

    @Test
    fun carveSegmentsSubtractsHoles() {
        // No holes -> the whole span.
        assertEquals(listOf(0f to 100f), carveSegments(0f, 100f, emptyList()))
        // One hole in the middle -> two segments.
        assertEquals(listOf(0f to 40f, 60f to 100f), carveSegments(0f, 100f, listOf(40f to 60f)))
        // Overlapping holes merge.
        assertEquals(listOf(0f to 30f, 70f to 100f), carveSegments(0f, 100f, listOf(30f to 55f, 50f to 70f)))
        // A hole spanning the whole range leaves nothing.
        assertEquals(emptyList(), carveSegments(0f, 100f, listOf(-10f to 110f)))
    }
}
