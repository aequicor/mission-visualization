package io.aequicor.visualization.subsystems.diagrams.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class TableNodeTest {

    private fun table3x3(): TableNode = TableNode(
        rows = listOf(TableRow(header = true), TableRow(), TableRow()),
        columns = listOf(TableColumn(), TableColumn(), TableColumn()),
        cells = (0..2).flatMap { r ->
            (0..2).map { c -> TableCell(row = r, column = c, label = DiagramLabel("$r$c")) }
        },
    )

    @Test
    fun mergeCellsProducesSpanningCellKeepingTopLeftLabel() {
        val merged = table3x3().mergeCells(rowRange = 0..1, columnRange = 1..2)

        val cell = merged.cellAt(0, 1)!!
        assertEquals(2, cell.rowSpan)
        assertEquals(2, cell.colSpan)
        assertEquals("01", cell.label?.text)

        // Every covered position resolves to the same merged cell.
        assertEquals(cell, merged.cellAt(1, 2))
        // Cells outside the region survive.
        assertEquals("00", merged.cellAt(0, 0)?.label?.text)
        assertEquals("22", merged.cellAt(2, 2)?.label?.text)
        assertEquals(5 + 1, merged.cells.size)
    }

    @Test
    fun mergeRejectsPartialOverlapWithExistingMergedCell() {
        val once = table3x3().mergeCells(rowRange = 0..1, columnRange = 0..0)
        assertFailsWith<IllegalArgumentException> {
            once.mergeCells(rowRange = 1..2, columnRange = 0..1)
        }
    }

    @Test
    fun mergeRejectsOutOfBoundsRanges() {
        assertFailsWith<IllegalArgumentException> {
            table3x3().mergeCells(rowRange = 0..3, columnRange = 0..0)
        }
    }

    @Test
    fun splitCellRestoresAnchorAndClearsCoverage() {
        val merged = table3x3().mergeCells(rowRange = 1..2, columnRange = 1..2)
        val split = merged.splitCell(2, 2)

        val anchor = split.cellAt(1, 1)!!
        assertEquals(1, anchor.rowSpan)
        assertEquals(1, anchor.colSpan)
        assertNull(split.cellAt(2, 2))
    }

    @Test
    fun overlappingCellsRejectedAtConstruction() {
        assertFailsWith<IllegalArgumentException> {
            TableNode(
                rows = listOf(TableRow(), TableRow()),
                columns = listOf(TableColumn(), TableColumn()),
                cells = listOf(
                    TableCell(row = 0, column = 0, rowSpan = 2, colSpan = 2),
                    TableCell(row = 1, column = 1),
                ),
            )
        }
    }

    @Test
    fun spanExceedingGridRejected() {
        assertFailsWith<IllegalArgumentException> {
            TableNode(
                rows = listOf(TableRow()),
                columns = listOf(TableColumn()),
                cells = listOf(TableCell(row = 0, column = 0, rowSpan = 2)),
            )
        }
    }
}
