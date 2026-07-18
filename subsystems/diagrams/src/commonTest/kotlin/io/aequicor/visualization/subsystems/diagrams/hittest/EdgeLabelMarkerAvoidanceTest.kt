package io.aequicor.visualization.subsystems.diagrams.hittest

import io.aequicor.visualization.subsystems.diagrams.lint.DiagramLintFinding
import io.aequicor.visualization.subsystems.diagrams.lint.lintDiagram
import io.aequicor.visualization.subsystems.diagrams.model.DiagramEdgeId
import io.aequicor.visualization.subsystems.diagrams.model.DiagramEdgeLabel
import io.aequicor.visualization.subsystems.diagrams.model.DiagramEdgeLabelPosition
import io.aequicor.visualization.subsystems.diagrams.model.DiagramEndpoint
import io.aequicor.visualization.subsystems.diagrams.model.DiagramLabel
import io.aequicor.visualization.subsystems.diagrams.model.DiagramRelation
import io.aequicor.visualization.subsystems.diagrams.model.diagramGraph
import io.aequicor.visualization.subsystems.diagrams.path.DiagramPoint
import io.aequicor.visualization.subsystems.diagrams.routing.RoutedEdge
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The label push context must include foreign endpoint-marker glyphs: a label anchored
 * next to a port fan used to park exactly on a neighbouring crow's foot, which no rule
 * measured and no surface avoided.
 */
class EdgeLabelMarkerAvoidanceTest {

    private fun graphAndRoutes(): Pair<io.aequicor.visualization.subsystems.diagrams.model.DiagramGraph, Map<DiagramEdgeId, RoutedEdge>> {
        val graph = diagramGraph {
            edge(
                "marked",
                source = DiagramEndpoint.FreePoint(28.0, 50.0),
                target = DiagramEndpoint.FreePoint(28.0, 195.0),
                relation = DiagramRelation.Association(directed = true),
            )
            edge(
                "labeled",
                source = DiagramEndpoint.FreePoint(0.0, 206.0),
                target = DiagramEndpoint.FreePoint(280.0, 206.0),
                labels = listOf(
                    DiagramEdgeLabel(DiagramLabel("abcd"), DiagramEdgeLabelPosition.SOURCE),
                ),
            )
        }
        val routes = mapOf(
            DiagramEdgeId("marked") to RoutedEdge(
                DiagramEdgeId("marked"),
                io.aequicor.visualization.subsystems.diagrams.model.DiagramRoutingStyle.ORTHOGONAL,
                listOf(DiagramPoint(28.0, 50.0), DiagramPoint(28.0, 195.0)),
            ),
            DiagramEdgeId("labeled") to RoutedEdge(
                DiagramEdgeId("labeled"),
                io.aequicor.visualization.subsystems.diagrams.model.DiagramRoutingStyle.ORTHOGONAL,
                listOf(DiagramPoint(0.0, 206.0), DiagramPoint(280.0, 206.0)),
            ),
        )
        return graph to routes
    }

    @Test
    fun avoidRectsCarryForeignMarkersButNotOwn() {
        val (graph, routes) = graphAndRoutes()
        val routePoints = routes.mapValues { it.value.points }
        val forLabeled = edgeLabelAvoidRects(graph, DiagramEdgeId("labeled"), routePoints)
        assertTrue(
            forLabeled.any { it.containsApprox(28.0, 190.0) },
            "labeled edge's context must carry the foreign arrowhead at (28,195): $forLabeled",
        )
        val forMarked = edgeLabelAvoidRects(graph, DiagramEdgeId("marked"), routePoints)
        assertTrue(
            forMarked.none { it.containsApprox(28.0, 190.0) },
            "an edge must not avoid its own markers: $forMarked",
        )
    }

    @Test
    fun sourceLabelStepsOffAForeignArrowheadAndLintStaysSilent() {
        val (graph, routes) = graphAndRoutes()
        val routePoints = routes.mapValues { it.value.points }
        val labeled = graph.edges.first { it.id.value == "labeled" }
        val label = labeled.labels.single()
        val anchor = edgeLabelAnchorPoint(
            routePoints.getValue(labeled.id),
            label,
            edgeLabelObstacleRoutes(graph, routePoints, labeled.id),
            edgeLabelAvoidRects(graph, labeled.id, routePoints),
        )
        // Unpushed the anchor sits at (28, 193) — its text box on the arrowhead glyph.
        assertTrue(
            abs(anchor.x - 28.0) > 1e-6 || abs(anchor.y - 193.0) > 1e-6,
            "label must be pushed off the foreign arrowhead, got $anchor",
        )
        val findings = lintDiagram(graph, routes)
        assertTrue(
            findings.none { it is DiagramLintFinding.LabelOverMarker },
            "pushed label must silence LabelOverMarker: $findings",
        )
    }

    private fun io.aequicor.visualization.subsystems.diagrams.path.DiagramRect.containsApprox(
        px: Double,
        py: Double,
    ): Boolean = px >= left - 1e-6 && px <= right + 1e-6 && py >= top - 1e-6 && py <= bottom + 1e-6
}
