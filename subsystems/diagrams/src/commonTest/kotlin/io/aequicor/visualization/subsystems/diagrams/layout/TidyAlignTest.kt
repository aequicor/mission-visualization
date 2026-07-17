package io.aequicor.visualization.subsystems.diagrams.layout

import io.aequicor.visualization.subsystems.diagrams.model.DiagramGraph
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNode
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodeId
import io.aequicor.visualization.subsystems.diagrams.model.diagramGraph
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TidyAlignTest {

    private fun DiagramGraph.node(id: String): DiagramNode =
        nodeById(DiagramNodeId(id)) ?: error("node $id missing")

    @Test
    fun snapsAlmostAlignedRowToOneAxis() {
        val graph = diagramGraph {
            node("a", x = 0.0, y = 100.0)
            node("b", x = 200.0, y = 108.0)
            node("c", x = 400.0, y = 94.0)
        }
        val tidied = tidyAlign(graph)
        assertEquals(tidied.node("a").y, tidied.node("b").y, "near-row snaps to a shared y")
        assertEquals(tidied.node("b").y, tidied.node("c").y)
        assertEquals(0.0, tidied.node("a").x, "x spacing is left alone")
        assertEquals(200.0, tidied.node("b").x)
        assertEquals(400.0, tidied.node("c").x)
    }

    @Test
    fun keepsDistinctRowsApart() {
        val graph = diagramGraph {
            node("top", x = 0.0, y = 0.0)
            node("bottom", x = 0.0, y = 180.0)
        }
        val tidied = tidyAlign(graph)
        assertNotEquals(
            tidied.node("top").y,
            tidied.node("bottom").y,
            "rows a full pitch apart must not merge",
        )
    }

    @Test
    fun snapsAlmostAlignedColumn() {
        val graph = diagramGraph {
            node("a", x = 300.0, y = 0.0)
            node("b", x = 312.0, y = 200.0)
            node("c", x = 291.0, y = 400.0)
        }
        val tidied = tidyAlign(graph)
        assertEquals(tidied.node("a").x, tidied.node("b").x, "near-column snaps to a shared x")
        assertEquals(tidied.node("b").x, tidied.node("c").x)
    }

    @Test
    fun resolvesOverlapsKeepingPairOrder() {
        val graph = diagramGraph {
            node("left", x = 0.0, y = 300.0)
            node("right", x = 60.0, y = 302.0) // width 120 → heavy x-overlap, same row
        }
        val tidied = tidyAlign(graph)
        val left = tidied.node("left")
        val right = tidied.node("right")
        assertTrue(!left.bounds.intersects(right.bounds), "overlap must dissolve")
        assertTrue(left.x < right.x, "colliding pair keeps its order")
    }

    @Test
    fun deterministicIntegerAndAnchored() {
        val graph = diagramGraph {
            node("a", x = 250.3, y = 130.7)
            node("b", x = 401.2, y = 133.9)
            node("c", x = 252.1, y = 340.4)
        }
        val first = tidyAlign(graph)
        val second = tidyAlign(graph)
        assertEquals(first, second, "tidy must be run-twice equal")
        // The scope anchor keeps its original (fractional) top-left; the grid snap makes
        // every offset from that anchor integral.
        val minX = first.nodes.minOf { it.x }
        val minY = first.nodes.minOf { it.y }
        assertEquals(250.3, minX, "keeps the previous bounding-box left")
        assertEquals(130.7, minY, "keeps the previous bounding-box top")
        for (node in first.nodes) {
            val offsetX = node.x - minX
            val offsetY = node.y - minY
            assertEquals(kotlin.math.round(offsetX), offsetX, 1e-6, "x offset of ${node.id.value} on grid")
            assertEquals(kotlin.math.round(offsetY), offsetY, 1e-6, "y offset of ${node.id.value} on grid")
        }
    }

    @Test
    fun containerChildrenTidyInPlace() {
        // Tidy is position-preserving: container children snap onto shared axes but keep
        // their absolute placement — they must NOT teleport to the padded corner.
        val graph = diagramGraph {
            val parent = node(
                "p",
                x = 100.0,
                y = 50.0,
                width = 400.0,
                height = 300.0,
                payload = io.aequicor.visualization.subsystems.diagrams.model.DiagramNodePayload.ContainerNode(),
            )
            node("x", x = 130.0, y = 90.0, parentId = parent)
            node("y", x = 260.0, y = 96.0, parentId = parent)
        }
        val tidied = tidyAlign(graph)
        val x = tidied.node("x")
        val y = tidied.node("y")
        assertEquals(x.y, y.y, "children snap onto one row")
        assertEquals(130.0, x.x, "child keeps its absolute x (no re-anchor to the padded corner)")
        assertEquals(90.0, x.y, "child stays on its original row")
        assertEquals(260.0, y.x, "second child keeps its absolute x")
    }

    @Test
    fun tidyIsIdempotent() {
        // A near-row plus a near-column plus a mild overlap: once tidied, a second Tidy
        // must be a no-op (else the button would drift the layout on every press).
        val graph = diagramGraph {
            node("a", x = 0.0, y = 100.0)
            node("b", x = 200.0, y = 108.0)
            node("c", x = 6.0, y = 300.0)
            node("d", x = 60.0, y = 302.0)
        }
        val once = tidyAlign(graph)
        val twice = tidyAlign(once)
        assertEquals(once, twice, "second Tidy on a tidy diagram changes nothing")
    }

    @Test
    fun keepsTopAlignmentOfUnequalHeightNodes() {
        // Author top-aligned two nodes of different height; center snapping would break
        // that. Tidy snaps top edges, so the shared top survives.
        val graph = diagramGraph {
            node("tall", x = 0.0, y = 200.0, height = 120.0)
            node("short", x = 200.0, y = 206.0, height = 40.0)
        }
        val tidied = tidyAlign(graph)
        assertEquals(tidied.node("tall").y, tidied.node("short").y, "top edges snap together")
    }
}
