package io.aequicor.visualization.subsystems.typography

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [SpanAlgebra] coverage: normalization, gap-covering runs, range application with
 * base pruning and fills patches, point queries and mixed-selection reporting.
 */
class SpanAlgebraTest {

    private val bold = TypographyStyle(fontWeight = 700)
    private val big = TypographyStyle(fontSize = 20.0)
    private val red = listOf(TextFill.Solid(Rgba(1.0, 0.0, 0.0)))
    private val blue = listOf(TextFill.Solid(Rgba(0.0, 0.0, 1.0)))

    // --- normalize ---

    @Test
    fun normalizeFlattensOverlapsWithLaterSpanWinning() {
        val spans = listOf(
            StyleSpan(0, 10, TypographyStyle(fontWeight = 400, fontSize = 20.0)),
            StyleSpan(3, 6, bold),
        )
        assertEquals(
            listOf(
                StyleSpan(0, 3, TypographyStyle(fontWeight = 400, fontSize = 20.0)),
                StyleSpan(3, 6, TypographyStyle(fontWeight = 700, fontSize = 20.0)),
                StyleSpan(6, 10, TypographyStyle(fontWeight = 400, fontSize = 20.0)),
            ),
            SpanAlgebra.normalize(spans, 10),
        )
    }

    @Test
    fun normalizeClampsToLengthAndDropsEmptyOrOutsideSpans() {
        val spans = listOf(
            StyleSpan(-5, 3, bold),
            StyleSpan(8, 20, bold),
            StyleSpan(4, 4, bold),
            StyleSpan(12, 15, bold),
        )
        assertEquals(
            listOf(StyleSpan(0, 3, bold), StyleSpan(8, 10, bold)),
            SpanAlgebra.normalize(spans, 10),
        )
    }

    @Test
    fun normalizeCoalescesAdjacentEqualSpans() {
        val spans = listOf(StyleSpan(0, 5, bold), StyleSpan(5, 10, bold))
        assertEquals(listOf(StyleSpan(0, 10, bold)), SpanAlgebra.normalize(spans, 10))
    }

    @Test
    fun normalizeDoesNotCoalesceAcrossGapsOrDifferentFills() {
        val gapped = listOf(StyleSpan(0, 4, bold), StyleSpan(6, 10, bold))
        assertEquals(gapped, SpanAlgebra.normalize(gapped, 10))

        val filled = listOf(StyleSpan(0, 5, bold, fills = red), StyleSpan(5, 10, bold, fills = blue))
        assertEquals(filled, SpanAlgebra.normalize(filled, 10))
    }

    @Test
    fun normalizeDropsSpansStylingNothingButKeepsFillsOnlySpans() {
        assertEquals(emptyList(), SpanAlgebra.normalize(listOf(StyleSpan(0, 5)), 10))

        val fillsOnly = listOf(StyleSpan(0, 5, fills = red))
        assertEquals(fillsOnly, SpanAlgebra.normalize(fillsOnly, 10))
    }

    @Test
    fun normalizeInnerSpanWithoutFillsInheritsOuterFills() {
        val spans = listOf(
            StyleSpan(0, 10, fills = red),
            StyleSpan(3, 5, bold),
        )
        assertEquals(
            listOf(
                StyleSpan(0, 3, fills = red),
                StyleSpan(3, 5, bold, fills = red),
                StyleSpan(5, 10, fills = red),
            ),
            SpanAlgebra.normalize(spans, 10),
        )
    }

    // --- runsCovering ---

    @Test
    fun runsCoveringFillsGapsWithEmptyRuns() {
        assertEquals(
            listOf(
                SpanAlgebra.Run(0, 3, TypographyStyle.EMPTY, null),
                SpanAlgebra.Run(3, 6, bold, null),
                SpanAlgebra.Run(6, 10, TypographyStyle.EMPTY, null),
            ),
            SpanAlgebra.runsCovering(listOf(StyleSpan(3, 6, bold)), 10),
        )
    }

    @Test
    fun runsCoveringHandlesNoSpansAndZeroLength() {
        assertEquals(
            listOf(SpanAlgebra.Run(0, 10, TypographyStyle.EMPTY, null)),
            SpanAlgebra.runsCovering(emptyList(), 10),
        )
        assertEquals(emptyList(), SpanAlgebra.runsCovering(emptyList(), 0))
    }

    // --- applyToRange ---

    @Test
    fun applyToRangeSplitsAtRangeBoundaries() {
        val spans = listOf(StyleSpan(0, 6, bold))
        assertEquals(
            listOf(
                StyleSpan(0, 3, bold),
                StyleSpan(3, 6, TypographyStyle(fontWeight = 700, fontSize = 20.0)),
                StyleSpan(6, 9, big),
            ),
            SpanAlgebra.applyToRange(TypographyStyle.EMPTY, spans, 10, 3, 9, big),
        )
    }

    @Test
    fun applyToRangeMergesPatchOverExistingOverride() {
        val spans = listOf(StyleSpan(0, 10, bold))
        assertEquals(
            listOf(StyleSpan(0, 10, TypographyStyle(fontWeight = 700, italic = true))),
            SpanAlgebra.applyToRange(
                TypographyStyle.EMPTY, spans, 10, 0, 10, TypographyStyle(italic = true),
            ),
        )
    }

    @Test
    fun applyToRangePrunesFieldsEqualToExplicitBase() {
        val base = TypographyStyle(fontWeight = 400)
        val spans = listOf(StyleSpan(0, 10, bold))
        assertEquals(
            emptyList(),
            SpanAlgebra.applyToRange(base, spans, 10, 0, 10, TypographyStyle(fontWeight = 400)),
        )
    }

    @Test
    fun applyToRangeKeepsFieldsEqualToImplicitDefault() {
        // Base has no explicit weight; an override of 400 renders identically but is kept.
        assertEquals(
            listOf(StyleSpan(0, 10, TypographyStyle(fontWeight = 400))),
            SpanAlgebra.applyToRange(
                TypographyStyle.EMPTY, emptyList(), 10, 0, 10, TypographyStyle(fontWeight = 400),
            ),
        )
    }

    @Test
    fun applyWholeRangeThenRevertToBaseYieldsNoSpans() {
        val base = TypographyStyle(fontWeight = 400, fontSize = 14.0)
        val once = SpanAlgebra.applyToRange(base, emptyList(), 10, 0, 10, bold)
        assertEquals(listOf(StyleSpan(0, 10, bold)), once)

        val reverted = SpanAlgebra.applyToRange(base, once, 10, 0, 10, TypographyStyle(fontWeight = 400))
        assertEquals(emptyList(), reverted)
    }

    @Test
    fun applyToRangeSetsFills() {
        assertEquals(
            listOf(StyleSpan(2, 5, fills = red)),
            SpanAlgebra.applyToRange(
                TypographyStyle.EMPTY, emptyList(), 10, 2, 5,
                patch = TypographyStyle.EMPTY,
                fillsPatch = FillsPatch.Set(red),
            ),
        )
    }

    @Test
    fun applyToRangeClearsFillsInsideRangeOnly() {
        val spans = listOf(StyleSpan(0, 10, fills = red))
        assertEquals(
            listOf(StyleSpan(0, 3, fills = red), StyleSpan(6, 10, fills = red)),
            SpanAlgebra.applyToRange(
                TypographyStyle.EMPTY, spans, 10, 3, 6,
                patch = TypographyStyle.EMPTY,
                fillsPatch = FillsPatch.Clear,
            ),
        )
    }

    @Test
    fun applyToRangeKeepLeavesFillsUntouched() {
        val spans = listOf(StyleSpan(0, 10, fills = red))
        assertEquals(
            listOf(StyleSpan(0, 10, bold, fills = red)),
            SpanAlgebra.applyToRange(TypographyStyle.EMPTY, spans, 10, 0, 10, bold),
        )
    }

    @Test
    fun applyToRangeWithCollapsedRangeJustNormalizes() {
        val spans = listOf(StyleSpan(5, 5, bold), StyleSpan(0, 3, bold))
        assertEquals(
            listOf(StyleSpan(0, 3, bold)),
            SpanAlgebra.applyToRange(TypographyStyle.EMPTY, spans, 10, 4, 4, big),
        )
    }

    // --- styleAt / fillsAt ---

    @Test
    fun styleAtMergesBaseWithCoveringSpan() {
        val base = TypographyStyle(fontSize = 14.0)
        val spans = listOf(StyleSpan(3, 6, bold))
        assertEquals(
            TypographyStyle(fontWeight = 700, fontSize = 14.0),
            SpanAlgebra.styleAt(base, spans, 10, 4),
        )
        assertEquals(base, SpanAlgebra.styleAt(base, spans, 10, 1))
        // Half-open range: the span's end offset is outside it.
        assertEquals(base, SpanAlgebra.styleAt(base, spans, 10, 6))
    }

    @Test
    fun fillsAtReturnsSpanFillsOrNull() {
        val spans = listOf(StyleSpan(3, 6, fills = red))
        assertEquals(red, SpanAlgebra.fillsAt(spans, 10, 3))
        assertNull(SpanAlgebra.fillsAt(spans, 10, 2))
        assertNull(SpanAlgebra.fillsAt(spans, 10, 6))
    }

    // --- mixedFields ---

    @Test
    fun mixedFieldsEmptyForUniformSelection() {
        val spans = listOf(StyleSpan(0, 10, bold))
        assertEquals(
            emptySet<TypographyField>(),
            SpanAlgebra.mixedFields(TypographyStyle.EMPTY, spans, 10, 2, 8),
        )
        // Adjacent equal spans coalesce into one run, so the selection is still uniform.
        val adjacent = listOf(StyleSpan(0, 5, bold), StyleSpan(5, 10, bold))
        assertEquals(
            emptySet<TypographyField>(),
            SpanAlgebra.mixedFields(TypographyStyle.EMPTY, adjacent, 10, 0, 10),
        )
    }

    @Test
    fun mixedFieldsReportsDifferingWeightOnly() {
        val spans = listOf(StyleSpan(0, 5, bold))
        assertEquals(
            setOf(TypographyField.FontWeight),
            SpanAlgebra.mixedFields(TypographyStyle.EMPTY, spans, 10, 0, 10),
        )
    }

    @Test
    fun mixedFieldsReportsDifferingFills() {
        val spans = listOf(StyleSpan(0, 5, fills = red), StyleSpan(5, 10, fills = blue))
        assertEquals(
            setOf(TypographyField.Fills),
            SpanAlgebra.mixedFields(TypographyStyle.EMPTY, spans, 10, 0, 10),
        )
    }

    @Test
    fun mixedFieldsEmptyForSelectionInsideOneRunOrCollapsed() {
        val spans = listOf(StyleSpan(0, 5, bold))
        assertEquals(
            emptySet<TypographyField>(),
            SpanAlgebra.mixedFields(TypographyStyle.EMPTY, spans, 10, 0, 5),
        )
        assertEquals(
            emptySet<TypographyField>(),
            SpanAlgebra.mixedFields(TypographyStyle.EMPTY, spans, 10, 5, 5),
        )
    }

    // --- selectionStyle ---

    @Test
    fun selectionStyleReportsUniformValuesAndFills() {
        val base = TypographyStyle(fontSize = 14.0)
        val spans = listOf(StyleSpan(0, 10, bold, fills = red))
        val selection = SpanAlgebra.selectionStyle(base, spans, 10, 2, 8)
        assertEquals(TypographyStyle(fontWeight = 700, fontSize = 14.0), selection.style)
        assertEquals(red, selection.fills)
        assertTrue(selection.mixed.isEmpty())
    }

    @Test
    fun selectionStyleReportsFirstRunValueWithMixedSet() {
        val spans = listOf(StyleSpan(0, 5, bold, fills = red))
        val selection = SpanAlgebra.selectionStyle(TypographyStyle.EMPTY, spans, 10, 0, 10)
        assertEquals(700, selection.style.fontWeight)
        assertEquals(red, selection.fills)
        assertEquals(setOf(TypographyField.FontWeight, TypographyField.Fills), selection.mixed)
    }

    @Test
    fun selectionStyleForCollapsedSelectionIsBase() {
        val base = TypographyStyle(fontSize = 14.0)
        val selection = SpanAlgebra.selectionStyle(base, listOf(StyleSpan(0, 5, bold)), 10, 3, 3)
        assertEquals(base, selection.style)
        assertNull(selection.fills)
        assertTrue(selection.mixed.isEmpty())
    }
}
