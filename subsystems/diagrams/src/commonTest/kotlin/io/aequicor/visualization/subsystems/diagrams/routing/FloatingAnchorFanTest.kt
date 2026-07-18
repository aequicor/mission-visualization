package io.aequicor.visualization.subsystems.diagrams.routing

import io.aequicor.visualization.subsystems.diagrams.model.DiagramEndpoint
import io.aequicor.visualization.subsystems.diagrams.model.DiagramRelation
import io.aequicor.visualization.subsystems.diagrams.model.diagramGraph
import io.aequicor.visualization.subsystems.diagrams.path.DiagramPoint
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A planned floating arrival must not stack onto the perimeter point a waypointed
 * edge's floating end already occupies (the via dictates that lane): the planner
 * spreads its anchors around such pinned lanes, so no two edges share one point.
 */
class FloatingAnchorFanTest {

    @Test
    fun plannedAnchorStepsOffAViaDictatedLane() {
        // Mirrors the gas-room reference: 'pressure' approaches the assessment's bottom
        // face through vias at x=2155; 'requirements' (no vias) would plan the exact
        // same x — the projection-overlap midpoint.
        val graph = diagramGraph {
            val assessment = node("assessment", x = 2055.0, y = 670.0, width = 200.0, height = 100.0)
            val requirement = node("requirement", x = 2055.0, y = 930.0, width = 200.0, height = 100.0)
            val pressure = node("pressure", x = 1700.0, y = 1150.0, width = 190.0, height = 130.0)
            edge(
                "pressure_feeds",
                source = DiagramEndpoint.FloatingAnchor(pressure),
                target = DiagramEndpoint.FloatingAnchor(assessment),
                relation = DiagramRelation.Association(directed = true),
                waypoints = listOf(
                    DiagramPoint(1915.0, 1215.0),
                    DiagramPoint(1915.0, 820.0),
                    DiagramPoint(2155.0, 820.0),
                ),
            )
            edge(
                "requirements_feed",
                source = DiagramEndpoint.FloatingAnchor(requirement),
                target = DiagramEndpoint.FloatingAnchor(assessment),
                relation = DiagramRelation.Dependency,
            )
        }

        val routes = routeAllEdges(graph).associateBy { it.edgeId.value }
        val pinnedArrival = routes.getValue("pressure_feeds").points.last()
        val plannedArrival = routes.getValue("requirements_feed").points.last()

        assertTrue(
            abs(pinnedArrival.x - plannedArrival.x) >= 20.0,
            "planned arrival must step off the via-dictated lane: pinned=$pinnedArrival planned=$plannedArrival",
        )
    }

    @Test
    fun runOfPlannedAnchorsAfterAPinKeepsItsSeparations() {
        // Two planned arrivals whose ideals sit just past the pinned lane: the run's
        // interval starts a separation above the pin, so the ideals fall below it.
        // Clamping the spread OUTPUT instead of the input collapsed such runs into a
        // near-stack right next to the pin.
        val graph = diagramGraph {
            val assessment = node("assessment", x = 2055.0, y = 670.0, width = 200.0, height = 100.0)
            val requirement = node("requirement", x = 2065.0, y = 930.0, width = 200.0, height = 100.0)
            val norms = node("norms", x = 2075.0, y = 1080.0, width = 200.0, height = 100.0)
            val pressure = node("pressure", x = 1700.0, y = 1250.0, width = 190.0, height = 130.0)
            edge(
                "pressure_feeds",
                source = DiagramEndpoint.FloatingAnchor(pressure),
                target = DiagramEndpoint.FloatingAnchor(assessment),
                relation = DiagramRelation.Association(directed = true),
                waypoints = listOf(
                    DiagramPoint(1915.0, 1315.0),
                    DiagramPoint(1915.0, 820.0),
                    DiagramPoint(2155.0, 820.0),
                ),
            )
            edge(
                "requirements_feed",
                source = DiagramEndpoint.FloatingAnchor(requirement),
                target = DiagramEndpoint.FloatingAnchor(assessment),
                relation = DiagramRelation.Dependency,
            )
            edge(
                "norms_feed",
                source = DiagramEndpoint.FloatingAnchor(norms),
                target = DiagramEndpoint.FloatingAnchor(assessment),
                relation = DiagramRelation.Dependency,
            )
        }

        val routes = routeAllEdges(graph).associateBy { it.edgeId.value }
        val arrivals = listOf("pressure_feeds", "requirements_feed", "norms_feed")
            .map { it to routes.getValue(it).points.last().x }
        for (i in arrivals.indices) {
            for (j in i + 1 until arrivals.size) {
                assertTrue(
                    abs(arrivals[i].second - arrivals[j].second) >= 20.0,
                    "arrivals must stay separated: ${arrivals[i]} vs ${arrivals[j]} (all: $arrivals)",
                )
            }
        }
    }
}
