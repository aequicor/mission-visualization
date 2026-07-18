package io.aequicor.visualization.subsystems.diagrams.routing

import io.aequicor.visualization.subsystems.diagrams.arrows.arrowheadExtent
import io.aequicor.visualization.subsystems.diagrams.arrows.fittedTo
import io.aequicor.visualization.subsystems.diagrams.model.DiagramArrowhead
import io.aequicor.visualization.subsystems.diagrams.model.DiagramArrowheadKind
import io.aequicor.visualization.subsystems.diagrams.model.DiagramEdgeId
import io.aequicor.visualization.subsystems.diagrams.model.DiagramEndpoint
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodeSide
import io.aequicor.visualization.subsystems.diagrams.model.DiagramRoutingStyle
import io.aequicor.visualization.subsystems.diagrams.model.diagramGraph
import io.aequicor.visualization.subsystems.diagrams.path.DiagramPoint
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * An endpoint marker needs a straight run to sit on. Where the route bends too soon, the
 * marker slides across the bend and merges into the line body instead of reading as a tip.
 */
class EndpointMarkerClearanceTest {

    private val zeroOrMany = DiagramArrowhead(kind = DiagramArrowheadKind.ER_ZERO_OR_MANY, size = 8.0)

    /** Length of the straight run the marker at [source]/target sits on. */
    private fun endRun(points: List<DiagramPoint>, source: Boolean): Double {
        val a = if (source) points.first() else points.last()
        val b = if (source) points[1] else points[points.size - 2]
        return sqrt((b.x - a.x) * (b.x - a.x) + (b.y - a.y) * (b.y - a.y))
    }

    @Test
    fun markerExtentIsMeasuredOffTheRealGeometry() {
        // Crow's foot with the "zero" circle behind it: the circle's far edge is 2.5 sizes
        // back from the tip.
        assertEquals(20.0, arrowheadExtent(zeroOrMany), 1e-9)
        // A bare crow's foot reaches only its own size back.
        assertEquals(8.0, arrowheadExtent(DiagramArrowhead(DiagramArrowheadKind.ER_MANY, size = 8.0)), 1e-9)
        assertEquals(0.0, arrowheadExtent(DiagramArrowhead.None), 1e-9)
    }

    @Test
    fun insetCountsTowardTheExtent() {
        val extent = arrowheadExtent(zeroOrMany.copy(inset = 3.0))
        assertEquals(23.0, extent, 1e-9)
    }

    @Test
    fun fittingScalesAMarkerDownToTheRunItGot() {
        val fitted = zeroOrMany.fittedTo(10.0)
        assertEquals(10.0, arrowheadExtent(fitted), 1e-9)
        assertTrue(fitted.size < zeroOrMany.size, "a marker longer than its run must shrink")
    }

    @Test
    fun fittingLeavesAMarkerThatAlreadyFits() {
        assertEquals(zeroOrMany, zeroOrMany.fittedTo(40.0))
    }

    @Test
    fun endpointCarryingALongMarkerGetsAStubToHoldIt() {
        // Offset entities, so the route bends between them and the arrival stub is finite.
        val graph = diagramGraph {
            val a = node("a", x = 0.0, y = 0.0, width = 100.0, height = 60.0)
            val b = node("b", x = 300.0, y = 100.0, width = 100.0, height = 60.0)
            edge(
                "e",
                source = DiagramEndpoint.FloatingAnchor(a),
                target = DiagramEndpoint.FloatingAnchor(b),
                routing = DiagramRoutingStyle.ENTITY_RELATION,
                targetArrowhead = zeroOrMany,
            )
        }
        val routed = routeEdge(graph, graph.edges.single())
        assertEquals(DiagramNodeSide.LEFT, routed.targetSide)
        assertTrue(
            endRun(routed.points, source = false) > arrowheadExtent(zeroOrMany),
            "the 20-long marker must not overrun its ${endRun(routed.points, false)} stub: ${routed.points}",
        )
        assertTrue(routed.targetReach >= arrowheadExtent(zeroOrMany), "reach must be reported for nudging")
    }

    @Test
    fun endpointWithoutAMarkerKeepsTheCorridorMargin() {
        // Reserving room for absent markers would push every plain route out for nothing, so
        // the clearance must be inert wherever there is no marker to protect.
        val graph = diagramGraph {
            val a = node("a", x = 0.0, y = 0.0, width = 100.0, height = 60.0)
            node("mid", x = 150.0, y = 0.0, width = 100.0, height = 60.0)
            val b = node("b", x = 400.0, y = 100.0, width = 100.0, height = 60.0)
            edge("e", from = a, to = b, routing = DiagramRoutingStyle.ENTITY_RELATION)
        }
        val edge = graph.edges.single()
        val margin = RoutingOptions.Default.obstacleMargin
        val withoutClearance = routeEdge(graph, edge, RoutingOptions.Default.copy(endpointClearance = margin))
        assertEquals(withoutClearance.points, routeEdge(graph, edge).points)
        assertEquals(margin, routeEdge(graph, edge).sourceReach, 1e-9)
    }

    @Test
    fun nudgingNeverShortensAStubBelowItsReach() {
        // Two routes leaving the same face into one vertical corridor: spreading them slides
        // each corner along its own stub, which used to eat the marker's room.
        val reach = 24.0
        val routes = listOf(
            RoutedEdge(
                edgeId = DiagramEdgeId("e1"),
                routing = DiagramRoutingStyle.ENTITY_RELATION,
                points = listOf(
                    DiagramPoint(100.0, 0.0),
                    DiagramPoint(124.0, 0.0),
                    DiagramPoint(124.0, 100.0),
                    DiagramPoint(200.0, 100.0),
                ),
                sourceSide = DiagramNodeSide.RIGHT,
                sourceReach = reach,
            ),
            RoutedEdge(
                edgeId = DiagramEdgeId("e2"),
                routing = DiagramRoutingStyle.ENTITY_RELATION,
                points = listOf(
                    DiagramPoint(100.0, 50.0),
                    DiagramPoint(124.0, 50.0),
                    DiagramPoint(124.0, 150.0),
                    DiagramPoint(200.0, 150.0),
                ),
                sourceSide = DiagramNodeSide.RIGHT,
                sourceReach = reach,
            ),
        )
        val nudged = nudgeRoutedEdges(routes)
        nudged.forEach {
            assertTrue(
                endRun(it.points, source = true) >= reach - 1e-6,
                "${it.edgeId.value} stub shrank to ${endRun(it.points, source = true)}: ${it.points}",
            )
        }
        // The spread still happened — the pass is not simply disabled near endpoints.
        val lanes = nudged.map { it.points[1].x }.distinct()
        assertTrue(lanes.size == 2, "co-running corridors must still separate, got $lanes")
    }

    @Test
    fun aStubShortenedByANeighborReportsTheReachItCouldNotGive() {
        // A foreign entity parked right against the exit face caps the stub; the renderer
        // scales the marker down for exactly this case.
        val graph = diagramGraph {
            val a = node("a", x = 0.0, y = 0.0, width = 100.0, height = 60.0)
            node("wall", x = 108.0, y = -40.0, width = 20.0, height = 140.0)
            val b = node("b", x = 300.0, y = 100.0, width = 100.0, height = 60.0)
            edge(
                "e",
                source = DiagramEndpoint.FloatingAnchor(a),
                target = DiagramEndpoint.FloatingAnchor(b),
                routing = DiagramRoutingStyle.ENTITY_RELATION,
                sourceArrowhead = zeroOrMany,
            )
        }
        val routed = routeEdge(graph, graph.edges.single())
        val run = endRun(routed.points, source = true)
        assertTrue(run <= 8.0 + 1e-6, "stub must stop at the wall's near face, got $run")
        assertTrue(abs(arrowheadExtent(zeroOrMany.fittedTo(run)) - run) < 1e-9, "marker must fit the run it got")
    }
}
