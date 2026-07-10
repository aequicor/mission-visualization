package io.aequicor.visualization.editor.presentation

import io.aequicor.visualization.engine.ir.model.DesignPaint
import io.aequicor.visualization.engine.ir.model.DesignTextStyle
import io.aequicor.visualization.engine.ir.model.TextStyleRange
import io.aequicor.visualization.engine.ir.model.bindable
import io.aequicor.visualization.engine.ir.model.literalOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TextRangeEditingTest {

    private fun weight(w: Double) = DesignTextStyle(fontWeight = w.bindable())
    private fun italic() = DesignTextStyle(italic = true)

    @Test
    fun appliesOverrideToFreshRange() {
        val result = TextRangeEditing.applyRange(
            ranges = emptyList(),
            length = 10,
            start = 2,
            end = 5,
            override = weight(700.0),
        )
        assertEquals(1, result.size)
        assertEquals(2, result[0].start)
        assertEquals(5, result[0].end)
        assertEquals(700.0, result[0].style.fontWeight?.literalOrNull())
    }

    @Test
    fun splitsAnExistingRangeOnPartialOverlap() {
        val existing = listOf(TextStyleRange(0, 10, weight(700.0)))
        val result = TextRangeEditing.applyRange(existing, 10, 4, 8, italic())
        // [0,4) weight only, [4,8) weight+italic, [8,10) weight only
        assertEquals(3, result.size)
        assertEquals(listOf(0, 4, 8), result.map { it.start })
        assertEquals(listOf(4, 8, 10), result.map { it.end })
        assertTrue(result[1].style.italic == true)
        assertEquals(700.0, result[1].style.fontWeight?.literalOrNull())
        assertNull(result[0].style.italic)
    }

    @Test
    fun coalescesAdjacentEqualRunsAfterEdit() {
        val existing = listOf(TextStyleRange(0, 4, weight(400.0)), TextStyleRange(4, 8, weight(700.0)))
        // Override the whole span to the same weight -> a single coalesced range.
        val result = TextRangeEditing.applyRange(existing, 8, 0, 8, weight(500.0))
        assertEquals(1, result.size)
        assertEquals(0, result[0].start)
        assertEquals(8, result[0].end)
        assertEquals(500.0, result[0].style.fontWeight?.literalOrNull())
    }

    @Test
    fun clearingFillsRemovesRangeFillsButKeepsStyle() {
        val fill = listOf<DesignPaint>(DesignPaint.Solid("#ff0000".let { c ->
            io.aequicor.visualization.engine.ir.model.DesignColor.fromHex(c)!!
        }.bindable()))
        val existing = listOf(TextStyleRange(0, 5, weight(700.0), fills = fill))
        val cleared = TextRangeEditing.applyRange(
            existing, 5, 0, 5, DesignTextStyle(), fillsChange = TextRangeEditing.FillsChange.Clear,
        )
        assertEquals(1, cleared.size)
        assertNull(cleared[0].fills)
        assertEquals(700.0, cleared[0].style.fontWeight?.literalOrNull())
    }

    @Test
    fun healsRangesAcrossInsertion() {
        val ranges = listOf(TextStyleRange(2, 5, weight(700.0)))
        // Insert 3 chars at offset 0 -> range shifts right by 3.
        val (healed, _) = TextRangeEditing.healForTextChange(ranges, emptyList(), "abcdef", "XYZabcdef")
        assertEquals(1, healed.size)
        assertEquals(5, healed[0].start)
        assertEquals(8, healed[0].end)
    }

    @Test
    fun healingDropsFullyDeletedRange() {
        val ranges = listOf(TextStyleRange(2, 5, weight(700.0)))
        // Delete offsets [1,6) -> the range is entirely removed.
        val (healed, _) = TextRangeEditing.healForTextChange(ranges, emptyList(), "abcdef", "af")
        assertTrue(healed.isEmpty())
    }

    @Test
    fun runsInRangeReportsMixedWeights() {
        val base = DesignTextStyle()
        val ranges = listOf(TextStyleRange(0, 3, weight(400.0)), TextStyleRange(3, 6, weight(700.0)))
        val runs = TextRangeEditing.runsInRange(base, ranges, 6, 0, 6)
        val weights = runs.map { it.first.fontWeight?.literalOrNull() }.distinct()
        assertTrue(weights.size > 1, "selection spans two weights -> Mixed")
    }
}
