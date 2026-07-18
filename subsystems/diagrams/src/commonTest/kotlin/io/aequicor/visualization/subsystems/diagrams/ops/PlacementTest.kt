package io.aequicor.visualization.subsystems.diagrams.ops

import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodePayload
import io.aequicor.visualization.subsystems.diagrams.model.diagramGraph
import io.aequicor.visualization.subsystems.diagrams.path.DiagramRect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlacementTest {

    @Test
    fun freeIdealSpotIsKeptAsIs() {
        val graph = diagramGraph {
            node("a", x = 500.0, y = 500.0, width = 100.0, height = 60.0)
        }
        val spot = findFreeDiagramPlacement(graph, width = 120.0, height = 60.0, idealX = 40.0, idealY = 40.0)
        assertEquals(40.0, spot.x)
        assertEquals(40.0, spot.y)
    }

    @Test
    fun occupiedIdealSpotMovesToTheNearestFreeLatticePoint() {
        val graph = diagramGraph {
            node("table", x = 100.0, y = 100.0, width = 200.0, height = 160.0)
        }
        val spot = findFreeDiagramPlacement(graph, width = 120.0, height = 60.0, idealX = 140.0, idealY = 140.0)
        val placedWithAir = DiagramRect(spot.x - 10.0, spot.y - 10.0, 140.0, 80.0)
        assertTrue(!placedWithAir.intersects(DiagramRect(100.0, 100.0, 200.0, 160.0)), "landed on the table: $spot")
        // Nearest free lattice point, not a teleport across the canvas.
        assertTrue(kotlin.math.abs(spot.x - 140.0) <= 200.0 && kotlin.math.abs(spot.y - 140.0) <= 200.0, "$spot")
        // Lattice keeps the grid alignment of the ideal spot.
        assertTrue(kotlin.math.abs((spot.x - 140.0).mod(20.0)) < 1e-9, "x off-lattice: $spot")
        assertTrue(kotlin.math.abs((spot.y - 140.0).mod(20.0)) < 1e-9, "y off-lattice: $spot")
    }

    @Test
    fun containersAndSwimlanesAreNotObstacles() {
        val graph = diagramGraph {
            node(
                "background",
                x = 0.0,
                y = 0.0,
                width = 2000.0,
                height = 2000.0,
                payload = DiagramNodePayload.ContainerNode(),
            )
        }
        val spot = findFreeDiagramPlacement(graph, width = 120.0, height = 60.0, idealX = 300.0, idealY = 300.0)
        assertEquals(300.0, spot.x)
        assertEquals(300.0, spot.y)
    }

    @Test
    fun hiddenNodesAreNotObstacles() {
        val graph = diagramGraph {
            node("ghost", x = 100.0, y = 100.0, width = 200.0, height = 160.0, visible = false)
        }
        val spot = findFreeDiagramPlacement(graph, width = 120.0, height = 60.0, idealX = 140.0, idealY = 140.0)
        assertEquals(140.0, spot.x)
        assertEquals(140.0, spot.y)
    }

    @Test
    fun boundsConstrainTheSearchAndFullyBlockedFallsBackToIdeal() {
        val graph = diagramGraph {
            node("wall", x = 0.0, y = 0.0, width = 400.0, height = 400.0)
        }
        val spot = findFreeDiagramPlacement(
            graph,
            width = 120.0,
            height = 60.0,
            idealX = 100.0,
            idealY = 100.0,
            maxRings = 2,
            maxX = 280.0,
            maxY = 340.0,
        )
        // Everything within two rings of the lattice is covered by the wall: keep ideal.
        assertEquals(100.0, spot.x)
        assertEquals(100.0, spot.y)
    }

    @Test
    fun candidatesOutsideBoundsAreSkipped() {
        val graph = diagramGraph {
            node("table", x = 0.0, y = 0.0, width = 300.0, height = 100.0)
        }
        val spot = findFreeDiagramPlacement(
            graph,
            width = 120.0,
            height = 60.0,
            idealX = 100.0,
            idealY = 20.0,
            maxX = 180.0,
            maxY = 200.0,
        )
        assertTrue(spot.x in 0.0..180.0 && spot.y in 0.0..200.0, "$spot")
        val placedWithAir = DiagramRect(spot.x - 10.0, spot.y - 10.0, 140.0, 80.0)
        assertTrue(!placedWithAir.intersects(DiagramRect(0.0, 0.0, 300.0, 100.0)), "$spot")
    }
}
