package io.aequicor.visualization.subsystems.diagrams.routing

import io.aequicor.visualization.subsystems.diagrams.arrows.arrowheadHalfWidth
import io.aequicor.visualization.subsystems.diagrams.lint.DiagramLintFinding
import io.aequicor.visualization.subsystems.diagrams.lint.lintDiagram
import io.aequicor.visualization.subsystems.diagrams.model.DiagramArrowhead
import io.aequicor.visualization.subsystems.diagrams.model.DiagramArrowheadKind
import io.aequicor.visualization.subsystems.diagrams.model.DiagramEdgeId
import io.aequicor.visualization.subsystems.diagrams.model.DiagramEndpoint
import io.aequicor.visualization.subsystems.diagrams.model.DiagramRoutingStyle
import io.aequicor.visualization.subsystems.diagrams.model.diagramGraph
import io.aequicor.visualization.subsystems.diagrams.path.DiagramPoint
import io.aequicor.visualization.subsystems.diagrams.path.DiagramRect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Endpoint markers occupy real space: the box from the tip back along the line by the
 * glyph's half-width. Foreign lanes must stay out of it — a crow's foot with a
 * stranger's line through it stops reading as a crow's foot. The A* prices such cuts
 * above plain crossings, and the MarkerCovered lint rule reports any that survive.
 */
class MarkerZonesTest {

    private val zeroOrMany = DiagramArrowhead(kind = DiagramArrowheadKind.ER_ZERO_OR_MANY, size = 8.0)

    @Test
    fun halfWidthIsMeasuredOffTheRealGeometry() {
        // Crow's foot spreads size/2 to each side; the trailing circle bulges its radius
        // off the axis, which its on-axis endpoints alone would hide.
        assertEquals(4.0, arrowheadHalfWidth(DiagramArrowhead(DiagramArrowheadKind.ER_MANY, size = 8.0)), 1e-9)
        assertEquals(4.0, arrowheadHalfWidth(zeroOrMany), 1e-9)
        assertEquals(4.0, arrowheadHalfWidth(DiagramArrowhead(DiagramArrowheadKind.OVAL, size = 8.0)), 1e-9)
        assertEquals(0.0, arrowheadHalfWidth(DiagramArrowhead.None), 1e-9)
    }

    @Test
    fun markerZoneSpansExtentTimesHalfWidthBehindTheTip() {
        val graph = diagramGraph {
            val a = node("a", x = 0.0, y = 0.0, width = 100.0, height = 60.0)
            val b = node("b", x = 300.0, y = 0.0, width = 100.0, height = 60.0)
            edge(
                "m",
                source = DiagramEndpoint.FloatingAnchor(a),
                target = DiagramEndpoint.FloatingAnchor(b),
                targetArrowhead = zeroOrMany,
            )
        }
        val route = RoutedEdge(
            DiagramEdgeId("m"),
            DiagramRoutingStyle.ORTHOGONAL,
            listOf(DiagramPoint(100.0, 30.0), DiagramPoint(300.0, 30.0)),
        )

        val zone = endpointMarkerZones(graph.edges.single(), route).single()

        assertEquals(DiagramPoint(300.0, 30.0), zone.tip)
        assertEquals(DiagramRect(x = 280.0, y = 26.0, width = 20.0, height = 8.0), zone.rect)
    }

    @Test
    fun fittedZoneShrinksToTheRunTheEndActuallyGot() {
        val graph = diagramGraph {
            val a = node("a", x = 0.0, y = 0.0, width = 100.0, height = 60.0)
            val b = node("b", x = 300.0, y = 0.0, width = 100.0, height = 60.0)
            edge(
                "m",
                source = DiagramEndpoint.FloatingAnchor(a),
                target = DiagramEndpoint.FloatingAnchor(b),
                targetArrowhead = zeroOrMany,
            )
        }
        // Final straight run of 10 units: the renderers scale the 20-long marker in half.
        val route = RoutedEdge(
            DiagramEdgeId("m"),
            DiagramRoutingStyle.ORTHOGONAL,
            listOf(
                DiagramPoint(100.0, 60.0),
                DiagramPoint(290.0, 60.0),
                DiagramPoint(290.0, 30.0),
                DiagramPoint(300.0, 30.0),
            ),
        )

        val zone = endpointMarkerZones(graph.edges.single(), route, fitToRun = true).last()

        assertEquals(10.0, zone.rect.width, 1e-9)
        assertEquals(290.0, zone.rect.left, 1e-9)
    }

    @Test
    fun gridRouteDetoursAroundAMarkerBoxWhenThePenaltyIsOn() {
        // One obstacle below the straight lane gives the grid its alternative corridors;
        // the marker box sits directly on the straight path.
        val obstacles = listOf(DiagramRect(x = 90.0, y = 20.0, width = 20.0, height = 20.0))
        val marker = listOf(DiagramRect(x = 95.0, y = -5.0, width = 10.0, height = 10.0))
        val index = RouteCrossingIndex.of(emptyList(), marker)

        val blind = orthogonalGridRoute(
            DiagramPoint(0.0, 0.0),
            null,
            DiagramPoint(200.0, 0.0),
            obstacles,
            turnPenalty = 4.0,
            crossings = index,
            crossingPenalty = 50.0,
            markerPenalty = 0.0,
        )
        val aware = orthogonalGridRoute(
            DiagramPoint(0.0, 0.0),
            null,
            DiagramPoint(200.0, 0.0),
            obstacles,
            turnPenalty = 4.0,
            crossings = index,
            crossingPenalty = 50.0,
            markerPenalty = 100.0,
        )!!

        assertTrue(
            blind != null && blind.all { it.y == 0.0 },
            "without the marker toll the straight lane through the box is cheapest: $blind",
        )
        val cutsMarker = aware.zipWithNext().any { (a, b) ->
            val rect = marker.single()
            if (a.y == b.y) {
                a.y > rect.top && a.y < rect.bottom &&
                    maxOf(a.x, b.x) > rect.left && minOf(a.x, b.x) < rect.right
            } else {
                a.x > rect.left && a.x < rect.right &&
                    maxOf(a.y, b.y) > rect.top && minOf(a.y, b.y) < rect.bottom
            }
        }
        assertTrue(!cutsMarker, "the aware route must keep out of the marker box: $aware")
    }

    @Test
    fun foreignLaneThroughACrowFootReportsMarkerCovered() {
        val graph = diagramGraph {
            val a = node("a", x = 0.0, y = 0.0, width = 100.0, height = 60.0)
            val b = node("b", x = 300.0, y = 0.0, width = 100.0, height = 60.0)
            val c = node("c", x = 240.0, y = -300.0, width = 100.0, height = 60.0)
            val d = node("d", x = 240.0, y = 200.0, width = 100.0, height = 60.0)
            edge(
                "m",
                source = DiagramEndpoint.FloatingAnchor(a),
                target = DiagramEndpoint.FloatingAnchor(b),
                targetArrowhead = zeroOrMany,
            )
            edge("f", source = DiagramEndpoint.FloatingAnchor(c), target = DiagramEndpoint.FloatingAnchor(d))
        }
        val routes = mapOf(
            DiagramEdgeId("m") to RoutedEdge(
                DiagramEdgeId("m"),
                DiagramRoutingStyle.ORTHOGONAL,
                listOf(DiagramPoint(100.0, 30.0), DiagramPoint(300.0, 30.0)),
            ),
            // Vertical lane at x=290: right through the crow's foot + circle of 'm'.
            DiagramEdgeId("f") to RoutedEdge(
                DiagramEdgeId("f"),
                DiagramRoutingStyle.ORTHOGONAL,
                listOf(DiagramPoint(290.0, -240.0), DiagramPoint(290.0, 200.0)),
            ),
        )

        val findings = lintDiagram(graph, routes).filterIsInstance<DiagramLintFinding.MarkerCovered>()

        assertEquals(
            listOf(
                DiagramLintFinding.MarkerCovered(
                    edgeId = DiagramEdgeId("f"),
                    markerEdgeId = DiagramEdgeId("m"),
                    at = DiagramPoint(300.0, 30.0),
                ),
            ),
            findings,
        )
    }

    @Test
    fun laneOutsideTheMarkerBoxStaysClean() {
        val graph = diagramGraph {
            val a = node("a", x = 0.0, y = 0.0, width = 100.0, height = 60.0)
            val b = node("b", x = 300.0, y = 0.0, width = 100.0, height = 60.0)
            val c = node("c", x = 200.0, y = -300.0, width = 100.0, height = 60.0)
            val d = node("d", x = 200.0, y = 200.0, width = 100.0, height = 60.0)
            edge(
                "m",
                source = DiagramEndpoint.FloatingAnchor(a),
                target = DiagramEndpoint.FloatingAnchor(b),
                targetArrowhead = zeroOrMany,
            )
            edge("f", source = DiagramEndpoint.FloatingAnchor(c), target = DiagramEndpoint.FloatingAnchor(d))
        }
        val routes = mapOf(
            DiagramEdgeId("m") to RoutedEdge(
                DiagramEdgeId("m"),
                DiagramRoutingStyle.ORTHOGONAL,
                listOf(DiagramPoint(100.0, 30.0), DiagramPoint(300.0, 30.0)),
            ),
            // Vertical at x=250: 30 units short of the 20-long marker box.
            DiagramEdgeId("f") to RoutedEdge(
                DiagramEdgeId("f"),
                DiagramRoutingStyle.ORTHOGONAL,
                listOf(DiagramPoint(250.0, -240.0), DiagramPoint(250.0, 200.0)),
            ),
        )

        assertEquals(
            emptyList(),
            lintDiagram(graph, routes).filterIsInstance<DiagramLintFinding.MarkerCovered>(),
        )
    }
}
