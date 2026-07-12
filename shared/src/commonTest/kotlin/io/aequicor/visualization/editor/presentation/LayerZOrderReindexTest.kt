package io.aequicor.visualization.editor.presentation

import io.aequicor.visualization.engine.ir.layout.DesignLayoutEngine
import io.aequicor.visualization.engine.ir.model.DesignAutoLayout
import io.aequicor.visualization.engine.ir.model.DesignDocument
import io.aequicor.visualization.engine.ir.model.DesignNode
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.engine.ir.model.DesignPage
import io.aequicor.visualization.engine.ir.model.DesignPoint
import io.aequicor.visualization.engine.ir.model.DesignSize
import io.aequicor.visualization.engine.ir.model.DesignSizing
import io.aequicor.visualization.subsystems.figures.ShapeType
import io.aequicor.visualization.engine.ir.resolve.DesignResolver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Z-order has two runtime representations that must stay in sync: the children-list order the
 * layers tree and every structural mutator use, and the explicit [DesignNode.order] field the
 * canvas resolver paints by ([DesignResolver] sorts children by `order ?: 0`, stably). A dropped
 * image (`addResourceMedia` pins `order = maxSibling + 1`) sat above a null-order vector, and
 * reordering the vector above it in the layers tree changed only the list — the resolver still
 * painted the image on top, so the vector stayed invisible. The mutators now re-materialize
 * `order` from list position (mirroring the compiler's `resolveOrder`), keeping the two in sync.
 */
class LayerZOrderReindexTest {

    private val engine = DesignLayoutEngine()

    private fun shape(id: String, order: Int?): DesignNode = DesignNode(
        id = id,
        type = "shape",
        kind = DesignNodeKind.Shape(ShapeType.Rectangle),
        name = id,
        order = order,
        // All children overlap the same spot so paint order decides what is visible.
        position = DesignPoint(0.0, 0.0),
        size = DesignSize(100.0, 100.0),
        sizing = DesignSizing(),
    )

    /**
     * root (free 400×400)
     * ├── png  order=100  (a dropped image, pinned on top by addResourceMedia)
     * └── vec  order=null (a vector added afterwards)
     */
    private fun document(children: List<DesignNode>): DesignDocument = DesignDocument(
        pages = listOf(
            DesignPage(
                id = "page",
                name = "Page",
                children = listOf(
                    DesignNode(
                        id = "root",
                        type = "frame",
                        kind = DesignNodeKind.Frame,
                        name = "root",
                        position = DesignPoint(0.0, 0.0),
                        size = DesignSize(400.0, 400.0),
                        sizing = DesignSizing(),
                        layout = DesignAutoLayout(),
                        children = children,
                    ),
                ),
            ),
        ),
    )

    /** Source ids of root's children in canvas paint order (first painted → last on top). */
    private fun DesignDocument.paintOrder(): List<String> {
        val root = engine.layout(assertNotNull(DesignResolver(this).resolvePage(pages.first()).firstOrNull()))
        return root.children.map { assertNotNull(it.node.sourceId) }
    }

    @Test
    fun reorderingAVectorAboveAPinnedImageActuallyRepaintsItOnTop() {
        val document = document(listOf(shape("png", order = 100), shape("vec", order = null)))
        // Initially the image paints last (on top); the null-order vector is behind and hidden.
        assertEquals(listOf("vec", "png"), document.paintOrder())

        // Move the vector to the front of its sibling list (what a layers-tree drag/bring-forward does).
        val moved = document.reorderSibling("vec", newIndex = 1)
        assertEquals(listOf("png", "vec"), moved.nodeById("root")?.children?.map { it.id })

        // The canvas resolver now paints the vector on top — the reorder is visible, not swallowed
        // by the image's stale explicit order. (Before the fix this stayed ["vec","png"].)
        assertEquals(listOf("png", "vec"), moved.paintOrder())
    }

    @Test
    fun reorderReMaterializesOrderToStrictlyMatchListPosition() {
        val document = document(
            listOf(shape("a", order = null), shape("b", order = 5), shape("c", order = 100)),
        )
        val moved = document.reorderSibling("c", newIndex = 0)
        val children = assertNotNull(moved.nodeById("root")?.children)
        assertEquals(listOf("c", "a", "b"), children.map { it.id })
        // Every sibling now carries an explicit order ascending in list order, so the resolver's
        // sort-by-order reproduces the list exactly — the two z representations can no longer drift.
        assertEquals(listOf(10, 20, 30), children.map { it.order })
        assertEquals(listOf("c", "a", "b"), moved.paintOrder())
    }

    @Test
    fun aNullOrderNodeInsertedIntoAnOrderedGroupLandsOnTop() {
        // The created-node latent bug: appending a null-order node next to explicitly-ordered
        // siblings used to sort it behind them (order 0 < their order). Reindex-on-insert lifts it.
        val document = document(listOf(shape("bg", order = 10), shape("panel", order = 40)))
        val added = document.insertNode("root", shape("fresh", order = null))
        assertEquals(listOf("bg", "panel", "fresh"), added.paintOrder())
        assertTrue(added.nodeById("root")?.children?.last()?.id == "fresh")
    }
}
