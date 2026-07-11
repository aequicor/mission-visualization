package io.aequicor.visualization.subsystems.diagrams.model

/**
 * A table node: explicit row/column tracks plus a sparse list of cells.
 *
 * A cell is anchored at (`row`, `column`) and may span multiple tracks via
 * `rowSpan`/`colSpan` (merged cells). Grid positions not covered by any cell
 * render as empty cells.
 */
data class TableNode(
    val rows: List<TableRow>,
    val columns: List<TableColumn>,
    val cells: List<TableCell> = emptyList(),
) : DiagramNodePayload {
    init {
        cells.forEach { cell ->
            require(cell.row in rows.indices) {
                "cell row ${cell.row} out of bounds 0..${rows.size - 1}"
            }
            require(cell.column in columns.indices) {
                "cell column ${cell.column} out of bounds 0..${columns.size - 1}"
            }
            require(cell.row + cell.rowSpan <= rows.size) {
                "cell at (${cell.row}, ${cell.column}) rowSpan ${cell.rowSpan} exceeds ${rows.size} rows"
            }
            require(cell.column + cell.colSpan <= columns.size) {
                "cell at (${cell.row}, ${cell.column}) colSpan ${cell.colSpan} exceeds ${columns.size} columns"
            }
        }
        val covered = mutableSetOf<Pair<Int, Int>>()
        cells.forEach { cell ->
            cell.coveredPositions().forEach { position ->
                require(covered.add(position)) {
                    "cells overlap at (${position.first}, ${position.second})"
                }
            }
        }
    }

    val rowCount: Int get() = rows.size
    val columnCount: Int get() = columns.size

    /** The cell covering grid position (`row`, `column`), if any (span-aware). */
    fun cellAt(row: Int, column: Int): TableCell? = cells.firstOrNull { cell ->
        row in cell.row until cell.row + cell.rowSpan &&
            column in cell.column until cell.column + cell.colSpan
    }
}

/** A row track. */
data class TableRow(
    val height: Double = 32.0,
    val header: Boolean = false,
) {
    init {
        require(height >= 0.0) { "row height must be >= 0, got $height" }
    }
}

/** A column track. */
data class TableColumn(
    val width: Double = 120.0,
    val header: Boolean = false,
) {
    init {
        require(width >= 0.0) { "column width must be >= 0, got $width" }
    }
}

/** A cell anchored at (`row`, `column`) spanning `rowSpan`×`colSpan` tracks. */
data class TableCell(
    val row: Int,
    val column: Int,
    val rowSpan: Int = 1,
    val colSpan: Int = 1,
    val label: DiagramLabel? = null,
    val style: DiagramStyle? = null,
) {
    init {
        require(row >= 0) { "row must be >= 0, got $row" }
        require(column >= 0) { "column must be >= 0, got $column" }
        require(rowSpan >= 1) { "rowSpan must be >= 1, got $rowSpan" }
        require(colSpan >= 1) { "colSpan must be >= 1, got $colSpan" }
    }

    val isMerged: Boolean get() = rowSpan > 1 || colSpan > 1

    internal fun coveredPositions(): List<Pair<Int, Int>> =
        (row until row + rowSpan).flatMap { r ->
            (column until column + colSpan).map { c -> r to c }
        }
}

/**
 * Merges every cell whose area lies inside the given ranges into a single spanning cell
 * anchored at (`rowRange.first`, `columnRange.first`). The merged cell keeps the label and
 * style of the top-left-most existing cell in the region (if any).
 *
 * Fails with [IllegalArgumentException] if the ranges are out of bounds or a cell only
 * partially overlaps the region (merge would tear an existing merged cell).
 */
fun TableNode.mergeCells(rowRange: IntRange, columnRange: IntRange): TableNode {
    require(!rowRange.isEmpty() && rowRange.first >= 0 && rowRange.last < rowCount) {
        "rowRange $rowRange out of bounds 0..${rowCount - 1}"
    }
    require(!columnRange.isEmpty() && columnRange.first >= 0 && columnRange.last < columnCount) {
        "columnRange $columnRange out of bounds 0..${columnCount - 1}"
    }
    val (inside, outside) = cells.partition { cell ->
        cell.row >= rowRange.first && cell.row + cell.rowSpan - 1 <= rowRange.last &&
            cell.column >= columnRange.first && cell.column + cell.colSpan - 1 <= columnRange.last
    }
    outside.forEach { cell ->
        val overlaps = cell.coveredPositions().any { (r, c) -> r in rowRange && c in columnRange }
        require(!overlaps) {
            "cell at (${cell.row}, ${cell.column}) partially overlaps merge region"
        }
    }
    val seed = inside.minWithOrNull(compareBy({ it.row }, { it.column }))
    val merged = TableCell(
        row = rowRange.first,
        column = columnRange.first,
        rowSpan = rowRange.last - rowRange.first + 1,
        colSpan = columnRange.last - columnRange.first + 1,
        label = seed?.label,
        style = seed?.style,
    )
    return copy(cells = outside + merged)
}

/**
 * Splits the merged cell covering (`row`, `column`) back into 1×1 cells. The anchor
 * position keeps the label/style; the other uncovered positions become implicit empty
 * cells (i.e. are simply removed from [TableNode.cells]). No-op if the position holds
 * no cell or a 1×1 cell.
 */
fun TableNode.splitCell(row: Int, column: Int): TableNode {
    val cell = cellAt(row, column) ?: return this
    if (!cell.isMerged) return this
    val restored = cell.copy(rowSpan = 1, colSpan = 1)
    return copy(cells = cells - cell + restored)
}
