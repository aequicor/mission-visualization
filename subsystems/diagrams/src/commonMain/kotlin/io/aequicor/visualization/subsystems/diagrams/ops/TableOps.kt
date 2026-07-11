package io.aequicor.visualization.subsystems.diagrams.ops

import io.aequicor.visualization.subsystems.diagrams.model.DiagramGraph
import io.aequicor.visualization.subsystems.diagrams.model.DiagramLabel
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodeId
import io.aequicor.visualization.subsystems.diagrams.model.TableCell
import io.aequicor.visualization.subsystems.diagrams.model.TableColumn
import io.aequicor.visualization.subsystems.diagrams.model.TableNode
import io.aequicor.visualization.subsystems.diagrams.model.TableRow
import io.aequicor.visualization.subsystems.diagrams.model.mergeCells
import io.aequicor.visualization.subsystems.diagrams.model.splitCell
import io.aequicor.visualization.subsystems.diagrams.model.updateNode

/**
 * Table node ops. All are no-ops when the node is missing or its payload is not a
 * [TableNode]. Row/column ops also grow/shrink the node's height/width by the
 * inserted/removed track size (draw.io behavior).
 */

/** Inserts a row track at [index] (coerced; default appends). Spans crossing it grow. */
public fun DiagramGraph.addTableRow(
    id: DiagramNodeId,
    index: Int = Int.MAX_VALUE,
    row: TableRow = TableRow(),
): DiagramGraph = updateTable(id) { table ->
    val at = index.coerceIn(0, table.rowCount)
    val cells = table.cells.map { cell ->
        when {
            cell.row >= at -> cell.copy(row = cell.row + 1)
            cell.row + cell.rowSpan > at -> cell.copy(rowSpan = cell.rowSpan + 1)
            else -> cell
        }
    }
    table.copy(rows = table.rows.take(at) + row + table.rows.drop(at), cells = cells) to row.height
}

/** Inserts a column track at [index] (coerced; default appends). Spans crossing it grow. */
public fun DiagramGraph.addTableColumn(
    id: DiagramNodeId,
    index: Int = Int.MAX_VALUE,
    column: TableColumn = TableColumn(),
): DiagramGraph = updateTable(id, horizontal = true) { table ->
    val at = index.coerceIn(0, table.columnCount)
    val cells = table.cells.map { cell ->
        when {
            cell.column >= at -> cell.copy(column = cell.column + 1)
            cell.column + cell.colSpan > at -> cell.copy(colSpan = cell.colSpan + 1)
            else -> cell
        }
    }
    table.copy(
        columns = table.columns.take(at) + column + table.columns.drop(at),
        cells = cells,
    ) to column.width
}

/**
 * Removes the row track at [index]. 1-track cells anchored there disappear; spans
 * covering it shrink by one. No-op if the index is out of bounds.
 */
public fun DiagramGraph.removeTableRow(id: DiagramNodeId, index: Int): DiagramGraph =
    updateTable(id) { table ->
        if (index !in table.rows.indices) return@updateTable null
        val cells = table.cells.mapNotNull { cell ->
            when {
                cell.row > index -> cell.copy(row = cell.row - 1)
                index < cell.row + cell.rowSpan ->
                    if (cell.rowSpan == 1) null else cell.copy(rowSpan = cell.rowSpan - 1)

                else -> cell
            }
        }
        table.copy(
            rows = table.rows.filterIndexed { i, _ -> i != index },
            cells = cells,
        ) to -table.rows[index].height
    }

/**
 * Removes the column track at [index]. 1-track cells anchored there disappear; spans
 * covering it shrink by one. No-op if the index is out of bounds.
 */
public fun DiagramGraph.removeTableColumn(id: DiagramNodeId, index: Int): DiagramGraph =
    updateTable(id, horizontal = true) { table ->
        if (index !in table.columns.indices) return@updateTable null
        val cells = table.cells.mapNotNull { cell ->
            when {
                cell.column > index -> cell.copy(column = cell.column - 1)
                index < cell.column + cell.colSpan ->
                    if (cell.colSpan == 1) null else cell.copy(colSpan = cell.colSpan - 1)

                else -> cell
            }
        }
        table.copy(
            columns = table.columns.filterIndexed { i, _ -> i != index },
            cells = cells,
        ) to -table.columns[index].width
    }

/** Merges the cells covered by the ranges (see [mergeCells]); no-op on invalid ranges. */
public fun DiagramGraph.mergeTableCells(
    id: DiagramNodeId,
    rowRange: IntRange,
    columnRange: IntRange,
): DiagramGraph = updateTable(id) { table ->
    val merged = try {
        table.mergeCells(rowRange, columnRange)
    } catch (_: IllegalArgumentException) {
        return@updateTable null
    }
    merged to 0.0
}

/** Splits the merged cell covering (`row`, `column`) back to 1×1 (see [splitCell]). */
public fun DiagramGraph.splitTableCell(id: DiagramNodeId, row: Int, column: Int): DiagramGraph =
    updateTable(id) { table -> table.splitCell(row, column) to 0.0 }

/**
 * Sets the text of the cell covering (`row`, `column`), creating the cell when the grid
 * position is empty; `null` clears the label. No-op on out-of-bounds positions.
 */
public fun DiagramGraph.setTableCellText(
    id: DiagramNodeId,
    row: Int,
    column: Int,
    text: String?,
): DiagramGraph = updateTable(id) { table ->
    if (row !in 0 until table.rowCount || column !in 0 until table.columnCount) {
        return@updateTable null
    }
    val covering = table.cellAt(row, column)
    val updated = when {
        covering != null -> table.copy(
            cells = table.cells.map { cell ->
                if (cell === covering) cell.copy(label = text?.let { DiagramLabel(it) }) else cell
            },
        )

        text == null -> table
        else -> table.copy(cells = table.cells + TableCell(row, column, label = DiagramLabel(text)))
    }
    updated to 0.0
}

/**
 * Applies [transform] to the node's table payload; the second value of the returned pair
 * is the size delta applied to the node's height (or width when [horizontal]).
 * `null` result = no-op.
 */
private fun DiagramGraph.updateTable(
    id: DiagramNodeId,
    horizontal: Boolean = false,
    transform: (TableNode) -> Pair<TableNode, Double>?,
): DiagramGraph {
    val node = nodeById(id) ?: return this
    val table = node.payload as? TableNode ?: return this
    val (updated, sizeDelta) = transform(table) ?: return this
    return updateNode(id) {
        it.copy(
            payload = updated,
            width = if (horizontal) (it.width + sizeDelta).coerceAtLeast(0.0) else it.width,
            height = if (horizontal) it.height else (it.height + sizeDelta).coerceAtLeast(0.0),
        )
    }
}
