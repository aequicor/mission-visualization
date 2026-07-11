package io.aequicor.visualization.subsystems.diagrams.ops

import io.aequicor.visualization.subsystems.diagrams.model.DiagramGraph
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodeId
import io.aequicor.visualization.subsystems.diagrams.model.TableCell
import io.aequicor.visualization.subsystems.diagrams.model.TableColumn
import io.aequicor.visualization.subsystems.diagrams.model.TableNode
import io.aequicor.visualization.subsystems.diagrams.model.TableRow
import io.aequicor.visualization.subsystems.diagrams.model.UmlClassNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlMember
import io.aequicor.visualization.subsystems.diagrams.model.UmlVisibility
import io.aequicor.visualization.subsystems.diagrams.model.diagramGraph
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TableUmlOpsTest {

    private val t = DiagramNodeId("t")
    private val c = DiagramNodeId("c")

    private fun tableGraph(
        cells: List<TableCell> = emptyList(),
    ) = diagramGraph {
        node(
            "t",
            x = 0.0, y = 0.0, width = 120.0, height = 64.0,
            payload = TableNode(
                rows = listOf(TableRow(32.0), TableRow(32.0)),
                columns = listOf(TableColumn(60.0), TableColumn(60.0)),
                cells = cells,
            ),
        )
    }

    private fun DiagramGraph.table(): TableNode =
        nodeById(t)!!.payload as TableNode

    @Test
    fun addTableRowAppendsAndGrowsNodeHeight() {
        val result = tableGraph().addTableRow(t, row = TableRow(40.0))
        assertEquals(3, result.table().rowCount)
        assertEquals(104.0, result.nodeById(t)!!.height)
    }

    @Test
    fun addTableRowAtStartShiftsCellAnchors() {
        val result = tableGraph(cells = listOf(TableCell(0, 0), TableCell(1, 1)))
            .addTableRow(t, index = 0)
        val cells = result.table().cells
        assertEquals(setOf(1 to 0, 2 to 1), cells.map { it.row to it.column }.toSet())
    }

    @Test
    fun addTableRowInsideSpanGrowsTheSpan() {
        val result = tableGraph(cells = listOf(TableCell(0, 0, rowSpan = 2)))
            .addTableRow(t, index = 1)
        val cell = result.table().cells.single()
        assertEquals(0, cell.row)
        assertEquals(3, cell.rowSpan)
    }

    @Test
    fun removeTableRowShiftsShrinksAndDropsCells() {
        val cells = listOf(
            TableCell(0, 0, rowSpan = 2),
            TableCell(0, 1),
            TableCell(1, 1),
        )
        val result = tableGraph(cells = cells).removeTableRow(t, 0)
        val table = result.table()
        assertEquals(1, table.rowCount)
        assertEquals(32.0, result.nodeById(t)!!.height)
        // The spanning cell shrank to 1x1; (0,1) died with its row; (1,1) shifted up.
        assertEquals(
            setOf(TableCell(0, 0), TableCell(0, 1)),
            table.cells.toSet(),
        )
    }

    @Test
    fun addAndRemoveTableColumnAdjustWidth() {
        val added = tableGraph().addTableColumn(t, column = TableColumn(30.0))
        assertEquals(3, added.table().columnCount)
        assertEquals(150.0, added.nodeById(t)!!.width)

        val removed = added.removeTableColumn(t, 2)
        assertEquals(2, removed.table().columnCount)
        assertEquals(120.0, removed.nodeById(t)!!.width)
    }

    @Test
    fun mergeAndSplitCellsThroughGraphOps() {
        val graph = tableGraph(cells = listOf(TableCell(0, 0), TableCell(0, 1)))
        val merged = graph.mergeTableCells(t, 0..0, 0..1)
        val mergedCell = merged.table().cells.single()
        assertEquals(2, mergedCell.colSpan)
        assertTrue(mergedCell.isMerged)

        val split = merged.splitTableCell(t, 0, 0)
        val restored = split.table().cells.single()
        assertEquals(1, restored.colSpan)
    }

    @Test
    fun mergeTableCellsWithInvalidRangeIsNoOp() {
        val graph = tableGraph()
        assertSame(graph, graph.mergeTableCells(t, 0..5, 0..1))
    }

    @Test
    fun setTableCellTextCreatesReplacesAndClears() {
        val graph = tableGraph()
        val created = graph.setTableCellText(t, 1, 0, "hello")
        assertEquals("hello", created.table().cellAt(1, 0)!!.label!!.text)

        val replaced = created.setTableCellText(t, 1, 0, "world")
        assertEquals("world", replaced.table().cellAt(1, 0)!!.label!!.text)

        val cleared = replaced.setTableCellText(t, 1, 0, null)
        assertNull(cleared.table().cellAt(1, 0)!!.label)

        assertSame(graph, graph.setTableCellText(t, 9, 0, "oob"))
    }

    @Test
    fun tableOpsIgnoreNonTableNodes() {
        val graph = diagramGraph { node("t") }
        assertSame(graph, graph.addTableRow(t))
        assertSame(graph, graph.removeTableColumn(t, 0))
    }

    private fun classGraph() = diagramGraph {
        node(
            "c",
            payload = UmlClassNode(
                name = "Rocket",
                attributes = listOf(UmlMember("fuel: Double", UmlVisibility.PRIVATE)),
                operations = listOf(UmlMember("launch()")),
            ),
        )
    }

    private fun DiagramGraph.umlClass(): UmlClassNode =
        nodeById(c)!!.payload as UmlClassNode

    @Test
    fun addClassFieldAndMethodAppend() {
        val result = classGraph()
            .addClassField(c, UmlMember("mass: Double"))
            .addClassMethod(c, UmlMember("abort()"), index = 0)
        val payload = result.umlClass()
        assertEquals(listOf("fuel: Double", "mass: Double"), payload.attributes.map { it.text })
        assertEquals(listOf("abort()", "launch()"), payload.operations.map { it.text })
    }

    @Test
    fun removeClassMemberByCompartment() {
        val result = classGraph().removeClassMember(c, UmlClassMemberKind.ATTRIBUTE, 0)
        assertTrue(result.umlClass().attributes.isEmpty())
        assertEquals(1, result.umlClass().operations.size)
    }

    @Test
    fun setClassMemberVisibility() {
        val result = classGraph()
            .setClassMemberVisibility(c, UmlClassMemberKind.OPERATION, 0, UmlVisibility.PROTECTED)
        assertEquals(UmlVisibility.PROTECTED, result.umlClass().operations.single().visibility)
        // Attribute untouched.
        assertEquals(UmlVisibility.PRIVATE, result.umlClass().attributes.single().visibility)
    }

    @Test
    fun umlOpsIgnoreNonClassNodes() {
        val graph = diagramGraph { node("c") }
        assertSame(graph, graph.addClassField(c, UmlMember("x")))
        assertSame(graph, graph.setClassMemberVisibility(c, UmlClassMemberKind.ATTRIBUTE, 0, UmlVisibility.PUBLIC))
    }
}
