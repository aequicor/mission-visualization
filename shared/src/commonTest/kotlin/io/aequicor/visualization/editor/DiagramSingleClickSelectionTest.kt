package io.aequicor.visualization.editor

import io.aequicor.visualization.editor.domain.MissionDocumentSource
import io.aequicor.visualization.editor.domain.compileMissionDocuments
import io.aequicor.visualization.editor.presentation.DiagramSelection
import io.aequicor.visualization.editor.ui.resolveDiagramElementSelection
import io.aequicor.visualization.engine.ir.layout.DesignLayoutEngine
import io.aequicor.visualization.engine.ir.layout.LayoutBox
import io.aequicor.visualization.engine.ir.resolve.DesignResolver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Unified single-click selection resolves a diagram element (block/edge) from a canvas press —
 * the seam that lets one click pick a UML block instead of the whole diagram container, and that
 * lets a click switch straight from one diagram's edit layer to another's element.
 *
 * Coordinate contract: [resolveDiagramElementSelection] takes document coordinates. Graph node
 * positions are relative to the diagram node's laid-out box top-left, so a node's document point is
 * `box.x/y + graphPosition`.
 */
class DiagramSingleClickSelectionTest {

    private val source = """
        |---
        |screen: diagramsTest
        |page: Diagrams Test
        |---
        |
        |# Diagrams Test id frame_root name «Root»
        |
        |## Diagram: Canvas id canvas
        |
        |Node rectangle a 120 by 60 position 20 20 label «A»
        |Node rectangle b 120 by 60 position 220 20 label «B»
        |Node rectangle c 120 by 60 position 20 260 label «C»
        |Edge e1 from a to b
    """.trimMargin()

    private fun layoutRoot(): LayoutBox {
        val document = assertNotNull(
            compileMissionDocuments(listOf(MissionDocumentSource("diagrams-test.layout.md", source))).document,
            "document compiles",
        )
        val page = document.pages.first()
        val resolved = assertNotNull(DesignResolver(document).resolvePage(page).firstOrNull(), "root frame resolves")
        return DesignLayoutEngine().layout(resolved)
    }

    private fun documentOf() = assertNotNull(
        compileMissionDocuments(listOf(MissionDocumentSource("diagrams-test.layout.md", source))).document,
    )

    @Test
    fun clickOnBlockSelectsThatBlockInsideItsDiagram() {
        val root = layoutRoot()
        val document = documentOf()
        val box = assertNotNull(root.findBySourceId("canvas"), "diagram laid out")

        // Node "a" center: graph-local (80, 50) → document (box.x + 80, box.y + 50).
        val target = assertNotNull(
            resolveDiagramElementSelection(root, document, box.x + 80.0, box.y + 50.0, zoomPx = 1f),
            "press on block A resolves to a diagram element",
        )
        assertEquals("canvas", target.diagramId)
        assertEquals(DiagramSelection(elementIds = setOf("a")), target.selection)

        // Node "b" center: graph-local (280, 50).
        val targetB = assertNotNull(
            resolveDiagramElementSelection(root, document, box.x + 280.0, box.y + 50.0, zoomPx = 1f),
        )
        assertEquals(DiagramSelection(elementIds = setOf("b")), targetB.selection)
    }

    @Test
    fun clickOnEmptyDiagramAreaResolvesToNoElement() {
        val root = layoutRoot()
        val document = documentOf()
        val box = assertNotNull(root.findBySourceId("canvas"))

        // Graph-local (180, 160): between the a/b row (y 20..80) and node c (y 260..320), and in the
        // gap between column a (x ≤ 140) and column b (x ≥ 220) — no block, no edge. The caller then
        // selects the whole diagram (container) instead of drilling in.
        assertNull(resolveDiagramElementSelection(root, document, box.x + 180.0, box.y + 160.0, zoomPx = 1f))
    }

    @Test
    fun clickOutsideAnyDiagramResolvesToNull() {
        val root = layoutRoot()
        val document = documentOf()
        val box = assertNotNull(root.findBySourceId("canvas"))

        assertNull(resolveDiagramElementSelection(root, document, box.right + 500.0, box.bottom + 500.0, zoomPx = 1f))
    }
}
