package io.aequicor.visualization.subsystems.diagrams.routing

import io.aequicor.visualization.subsystems.diagrams.path.DiagramPoint
import io.aequicor.visualization.subsystems.diagrams.path.DiagramRect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Crossings piled into one small area (a CrossingHotspot cluster) become crowd discs,
 * and a crossing inside a disc pays a surcharge — so the router relocates crossings out
 * of the pile when a bounded detour buys that, and total crossings do not grow.
 */
class CrowdedCrossingTest {

    // Three foreign verticals the route must cross somewhere on their -100..100 span.
    private val verticals = listOf(
        listOf(DiagramPoint(95.0, -100.0), DiagramPoint(95.0, 100.0)),
        listOf(DiagramPoint(100.0, -100.0), DiagramPoint(100.0, 100.0)),
        listOf(DiagramPoint(105.0, -100.0), DiagramPoint(105.0, 100.0)),
    )

    // A pile already sits where the straight lane would cross them.
    private val disc = DiagramRect(x = 68.0, y = -32.0, width = 64.0, height = 64.0)

    // The obstacle contributes the grid rows (y = 20 / 40) that give A* an alternative lane.
    private val obstacles = listOf(DiagramRect(x = 90.0, y = 20.0, width = 20.0, height = 20.0))

    @Test
    fun crowdSurchargeMovesCrossingsOutOfTheDisc() {
        val index = RouteCrossingIndex.of(verticals, crowdRects = listOf(disc))

        val blind = orthogonalGridRoute(
            DiagramPoint(0.0, 0.0),
            null,
            DiagramPoint(200.0, 0.0),
            obstacles,
            turnPenalty = 4.0,
            crossings = index,
            crossingPenalty = 50.0,
            crowdPenalty = 0.0,
        )!!
        val aware = orthogonalGridRoute(
            DiagramPoint(0.0, 0.0),
            null,
            DiagramPoint(200.0, 0.0),
            obstacles,
            turnPenalty = 4.0,
            crossings = index,
            crossingPenalty = 50.0,
            crowdPenalty = 50.0,
        )!!

        // Without the surcharge the straight lane through the pile is cheapest.
        assertTrue(blind.all { it.y == 0.0 }, "blind route should stay on the straight lane: $blind")
        // With it, every crossing relocates outside the disc — and none is dropped.
        val simplified = cleanRoutePolyline(aware)
        val crossingRows = simplified.zipWithNext()
            .filter { (a, b) -> a.y == b.y }
            .flatMap { (a, b) ->
                listOf(95.0, 100.0, 105.0).filter { x -> x > minOf(a.x, b.x) && x < maxOf(a.x, b.x) }
                    .map { x -> DiagramPoint(x, a.y) }
            }
        assertEquals(3, crossingRows.size, "all three verticals still get crossed once: $aware")
        assertTrue(
            crossingRows.none { disc.contains(it) },
            "crossings must leave the crowd disc: $crossingRows in $aware",
        )
    }
}
