package io.aequicor.visualization.subsystems.typography

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * [OffsetHealing] coverage: single-replacement edits against span/link ranges and lone
 * offsets, including the typing conventions at span boundaries.
 */
class OffsetHealingTest {

    private val bold = TypographyStyle(fontWeight = 700)

    /** Heals the span [5, 10) through the given edit; null when it collapsed. */
    private fun healed(editStart: Int, editEnd: Int, inserted: Int): StyleSpan? =
        OffsetHealing.healSpans(listOf(StyleSpan(5, 10, bold)), editStart, editEnd, inserted)
            .singleOrNull()

    // --- insertion ---

    @Test
    fun insertionBeforeSpanShiftsIt() {
        assertEquals(StyleSpan(8, 13, bold), healed(editStart = 2, editEnd = 2, inserted = 3))
    }

    @Test
    fun insertionInsideSpanGrowsIt() {
        assertEquals(StyleSpan(5, 12, bold), healed(editStart = 7, editEnd = 7, inserted = 2))
    }

    @Test
    fun insertionAfterSpanLeavesIt() {
        assertEquals(StyleSpan(5, 10, bold), healed(editStart = 11, editEnd = 11, inserted = 4))
    }

    @Test
    fun insertionExactlyAtSpanStartPushesIt() {
        assertEquals(StyleSpan(7, 12, bold), healed(editStart = 5, editEnd = 5, inserted = 2))
    }

    @Test
    fun insertionExactlyAtSpanEndExtendsIt() {
        assertEquals(StyleSpan(5, 12, bold), healed(editStart = 10, editEnd = 10, inserted = 2))
    }

    // --- deletion ---

    @Test
    fun deletionOverlappingSpanStartTrimsIt() {
        assertEquals(StyleSpan(3, 6, bold), healed(editStart = 3, editEnd = 7, inserted = 0))
    }

    @Test
    fun deletionOverlappingSpanEndTrimsIt() {
        assertEquals(StyleSpan(5, 8, bold), healed(editStart = 8, editEnd = 12, inserted = 0))
    }

    @Test
    fun deletionInsideSpanShrinksIt() {
        assertEquals(StyleSpan(5, 8, bold), healed(editStart = 6, editEnd = 8, inserted = 0))
    }

    @Test
    fun deletionBeforeSpanShiftsIt() {
        assertEquals(StyleSpan(2, 7, bold), healed(editStart = 1, editEnd = 4, inserted = 0))
    }

    @Test
    fun deletionCoveringEntireSpanDropsIt() {
        assertNull(healed(editStart = 4, editEnd = 11, inserted = 0))
        assertNull(healed(editStart = 5, editEnd = 10, inserted = 0))
    }

    // --- replacement ---

    @Test
    fun replacementOfExactSpanBodyKeepsStyling() {
        assertEquals(StyleSpan(5, 8, bold), healed(editStart = 5, editEnd = 10, inserted = 3))
    }

    @Test
    fun replacementInsideSpanResizesIt() {
        assertEquals(StyleSpan(5, 13, bold), healed(editStart = 6, editEnd = 8, inserted = 5))
    }

    @Test
    fun collapsedSpanIsAlwaysDropped() {
        // A degenerate [5, 5) range never survives healing, even for a distant edit.
        assertEquals(
            emptyList(),
            OffsetHealing.healSpans(listOf(StyleSpan(5, 5, bold)), 20, 20, 3),
        )
    }

    // --- healOffset ---

    @Test
    fun healOffsetBeforeEditIsUnchanged() {
        assertEquals(3, OffsetHealing.healOffset(3, editStart = 5, editEnd = 8, insertedLength = 2))
        assertEquals(5, OffsetHealing.healOffset(5, editStart = 5, editEnd = 8, insertedLength = 2))
    }

    @Test
    fun healOffsetInsideReplacedRangeCollapsesToInsertionEnd() {
        assertEquals(7, OffsetHealing.healOffset(6, editStart = 5, editEnd = 8, insertedLength = 2))
    }

    @Test
    fun healOffsetAfterEditShiftsByDelta() {
        assertEquals(7, OffsetHealing.healOffset(8, editStart = 5, editEnd = 8, insertedLength = 2))
        assertEquals(9, OffsetHealing.healOffset(10, editStart = 5, editEnd = 8, insertedLength = 2))
    }

    @Test
    fun healOffsetPureInsertionAtOffsetKeepsIt() {
        assertEquals(5, OffsetHealing.healOffset(5, editStart = 5, editEnd = 5, insertedLength = 4))
        assertEquals(10, OffsetHealing.healOffset(6, editStart = 5, editEnd = 5, insertedLength = 4))
    }

    // --- links ---

    @Test
    fun linksHealTheSameWayAsSpans() {
        val link = LinkSpan(5, 10, url = "https://example.com")
        assertEquals(
            listOf(LinkSpan(5, 12, url = "https://example.com")),
            OffsetHealing.healLinks(listOf(link), editStart = 10, editEnd = 10, insertedLength = 2),
        )
        assertEquals(
            emptyList(),
            OffsetHealing.healLinks(listOf(link), editStart = 4, editEnd = 11, insertedLength = 0),
        )
    }
}
