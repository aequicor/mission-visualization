package io.aequicor.visualization.editor.presentation

import io.aequicor.visualization.engine.ir.layout.DesignLayoutEngine
import io.aequicor.visualization.engine.ir.model.DesignAnchors
import io.aequicor.visualization.engine.ir.model.DesignAutoLayout
import io.aequicor.visualization.engine.ir.model.DesignDocument
import io.aequicor.visualization.engine.ir.model.DesignNode
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.engine.ir.model.DesignPage
import io.aequicor.visualization.engine.ir.model.DesignPoint
import io.aequicor.visualization.engine.ir.model.DesignSize
import io.aequicor.visualization.engine.ir.model.DesignSizing
import io.aequicor.visualization.engine.ir.model.LayoutMode
import io.aequicor.visualization.engine.ir.model.bindable
import io.aequicor.visualization.engine.ir.model.literalOrNull
import io.aequicor.visualization.engine.ir.resolve.DesignResolver
import io.aequicor.visualization.subsystems.figures.ShapeType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A layers-tree reparent carries no pointer coordinates, so [layerReparentKeepPutPosition]
 * re-bases the moved node into the new container's frame to keep its absolute canvas
 * position (Figma semantics) — otherwise it keeps its old parent-relative coordinates and
 * teleports (vanishing if the new container clips). Mirrors the canvas drag's
 * [reparentDropPlacement].
 */
class LayerReparentKeepPutTest {

    private val engine = DesignLayoutEngine()

    private fun frame(
        id: String,
        x: Double,
        y: Double,
        w: Double,
        h: Double,
        mode: LayoutMode = LayoutMode.None,
        anchors: DesignAnchors? = null,
        children: List<DesignNode> = emptyList(),
    ): DesignNode = DesignNode(
        id = id,
        type = "frame",
        kind = DesignNodeKind.Frame,
        name = id,
        position = DesignPoint(x, y),
        size = DesignSize(w, h),
        sizing = DesignSizing(),
        anchors = anchors,
        layout = DesignAutoLayout(mode = mode),
        children = children,
    )

    private fun shape(
        id: String,
        x: Double,
        y: Double,
        w: Double,
        h: Double,
        anchors: DesignAnchors? = null,
    ): DesignNode = DesignNode(
        id = id,
        type = "shape",
        kind = DesignNodeKind.Shape(ShapeType.Rectangle),
        name = id,
        position = DesignPoint(x, y),
        size = DesignSize(w, h),
        sizing = DesignSizing(),
        anchors = anchors,
    )

    /**
     * root (free 800×600)
     * ├── A (free, at 50,50) → node N (at 20,20) ⇒ N absolute (70,70)
     * └── B (free, at 400,400)
     */
    private fun document(
        targetMode: LayoutMode = LayoutMode.None,
        nodeAnchors: DesignAnchors? = null,
    ): DesignDocument = DesignDocument(
        pages = listOf(
            DesignPage(
                id = "page",
                name = "Page",
                children = listOf(
                    frame(
                        "root", 0.0, 0.0, 800.0, 600.0,
                        children = listOf(
                            frame(
                                "A", 50.0, 50.0, 300.0, 300.0,
                                children = listOf(shape("N", 20.0, 20.0, 40.0, 40.0, anchors = nodeAnchors)),
                            ),
                            frame("B", 400.0, 400.0, 300.0, 300.0, mode = targetMode),
                        ),
                    ),
                ),
            ),
        ),
    )

    private fun DesignDocument.layoutBoxes() =
        engine.layout(assertNotNull(DesignResolver(this).resolvePage(pages.first()).firstOrNull()))

    private fun absoluteOf(document: DesignDocument, id: String): Pair<Double, Double> {
        val box = assertNotNull(document.layoutBoxes().findBySourceId(id), "laid-out box for $id")
        return box.x to box.y
    }

    @Test
    fun keepPutPreservesTheNodesAbsolutePositionWhenReparentedIntoAFreeContainer() {
        val document = document()
        val before = absoluteOf(document, "N")
        assertEquals(70.0 to 70.0, before)

        val position = assertNotNull(
            layerReparentKeepPutPosition(document, document.layoutBoxes(), dragId = "N", newParentId = "B"),
            "free-container reparent produces a keep-put position",
        )
        // N absolute (70,70) minus B absolute (400,400) → the offset that reproduces it.
        assertEquals(-330.0, position.x.literalOrNull())
        assertEquals(-330.0, position.y.literalOrNull())

        val moved = document.reparent(nodeId = "N", newParentId = "B", position = position)
        assertEquals("B", moved.parentNodeOf("N")?.id, "N is now a child of B")
        assertEquals(before, absoluteOf(moved, "N"), "N stays put on the canvas")
    }

    @Test
    fun withoutKeepPutTheNodeTeleportsToTheWrongSpot() {
        // Pins the bug the fix addresses: a position-less reparent keeps N's old parent-
        // relative (20,20), now read against B's origin (400,400) → N jumps to (420,420).
        val document = document()
        val moved = document.reparent(nodeId = "N", newParentId = "B", position = null)
        assertEquals(420.0 to 420.0, absoluteOf(moved, "N"))
    }

    @Test
    fun keepPutIsSkippedForAnAutoLayoutTarget() {
        val document = document(targetMode = LayoutMode.Vertical)
        // A flow container lays the node out in its stack; an absolute position would be wrong.
        assertNull(layerReparentKeepPutPosition(document, document.layoutBoxes(), dragId = "N", newParentId = "B"))
    }

    @Test
    fun keepPutIsSkippedForASameParentReorder() {
        val document = document()
        // Re-homing N under A (its current parent) is a pure reorder — no reposition, and
        // forcing a position would yank a flow child out of flow.
        assertNull(layerReparentKeepPutPosition(document, document.layoutBoxes(), dragId = "N", newParentId = "A"))
    }

    @Test
    fun keepPutIsSkippedForAnAnchoredNode() {
        val document = document(nodeAnchors = DesignAnchors(inlineStart = 8.0.bindable()))
        assertNull(layerReparentKeepPutPosition(document, document.layoutBoxes(), dragId = "N", newParentId = "B"))
    }

    @Test
    fun keepPutIsSkippedWhenGeometryIsUnavailable() {
        val document = document()
        assertNull(layerReparentKeepPutPosition(document, layout = null, dragId = "N", newParentId = "B"))
    }

    @Test
    fun keepPutSurvivesADistantClippingTargetByPreservingAbsolutePosition() {
        // Even into a far, content-clipping container the contract holds: N keeps its canvas
        // position (here it lands outside B and is legitimately clipped, exactly as Figma) —
        // never the arbitrary teleport of the old behavior.
        val document = document()
        val position = assertNotNull(
            layerReparentKeepPutPosition(document, document.layoutBoxes(), dragId = "N", newParentId = "B"),
        )
        val moved = document.reparent(nodeId = "N", newParentId = "B", position = position)
        assertEquals(70.0 to 70.0, absoluteOf(moved, "N"))
        assertTrue(moved.parentNodeOf("N")?.id == "B")
    }
}
