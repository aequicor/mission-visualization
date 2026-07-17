package io.aequicor.visualization.subsystems.diagrams.lint

import io.aequicor.visualization.subsystems.diagrams.model.DiagramEdgeId
import io.aequicor.visualization.subsystems.diagrams.model.DiagramEdgeLabel
import io.aequicor.visualization.subsystems.diagrams.model.DiagramEndpoint
import io.aequicor.visualization.subsystems.diagrams.model.DiagramLabel
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodeId
import io.aequicor.visualization.subsystems.diagrams.model.DiagramRoutingStyle
import io.aequicor.visualization.subsystems.diagrams.model.diagramGraph
import io.aequicor.visualization.subsystems.diagrams.path.DiagramPoint
import io.aequicor.visualization.subsystems.diagrams.routing.RoutedEdge
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DiagramLintTest {

    // --- rule: NodeOverlap --------------------------------------------------------------

    @Test
    fun connectedIntersectingNodesReportNodeOverlap() {
        val graph = diagramGraph {
            val a = node("a", x = 0.0, y = 0.0, width = 100.0, height = 100.0)
            val b = node("b", x = 90.0, y = 0.0, width = 100.0, height = 100.0)
            edge("a-b", a, b)
        }
        // The route stays above both bodies so only the node-overlap rule can fire.
        val routes = routesOf(
            routed("a-b", DiagramPoint(50.0, -20.0), DiagramPoint(140.0, -20.0)),
        )

        val findings = lintDiagram(graph, routes)

        assertEquals(
            listOf(DiagramLintFinding.NodeOverlap(DiagramNodeId("a"), DiagramNodeId("b"))),
            findings,
        )
        assertEquals("nodes 'a' and 'b' overlap", findings.single().message)
    }

    @Test
    fun oneUnitOverlapAndFullContainmentAreNotReported() {
        val graph = diagramGraph {
            // Exactly 1.0 units of intersection: just below the rule's tolerance.
            val a = node("a", x = 0.0, y = 0.0, width = 100.0, height = 100.0)
            val b = node("b", x = 99.0, y = 0.0, width = 100.0, height = 100.0)
            edge("a-b", a, b)
            // Full containment is deliberate grouping, not a collision.
            val outer = node("outer", x = 0.0, y = 300.0, width = 200.0, height = 200.0)
            val inner = node("inner", x = 50.0, y = 350.0, width = 50.0, height = 50.0)
            edge("outer-inner", outer, inner)
        }
        val routes = routesOf(
            routed("a-b", DiagramPoint(50.0, -20.0), DiagramPoint(150.0, -20.0)),
            routed("outer-inner", DiagramPoint(20.0, 280.0), DiagramPoint(120.0, 280.0)),
        )

        assertEquals(emptyList(), lintDiagram(graph, routes))
    }

    // --- rule: EdgeThroughNode ----------------------------------------------------------

    @Test
    fun edgeCuttingThroughAForeignNodeIsReported() {
        val graph = diagramGraph {
            val a = node("a", x = 0.0, y = 0.0, width = 100.0, height = 100.0)
            val c = node("c", x = 200.0, y = 0.0, width = 100.0, height = 100.0)
            val b = node("b", x = 400.0, y = 0.0, width = 100.0, height = 100.0)
            edge("a-b", a, b)
            edge("c-b", c, b)
        }
        val routes = routesOf(
            // Straight through the middle of c, a node a-b is not attached to.
            routed("a-b", DiagramPoint(100.0, 50.0), DiagramPoint(400.0, 50.0)),
            routed("c-b", DiagramPoint(300.0, 20.0), DiagramPoint(400.0, 20.0)),
        )

        val findings = lintDiagram(graph, routes)

        assertEquals(
            listOf(DiagramLintFinding.EdgeThroughNode(DiagramEdgeId("a-b"), DiagramNodeId("c"))),
            findings,
        )
        assertEquals("edge 'a-b' cuts through node 'c'", findings.single().message)
    }

    @Test
    fun edgeGrazingAlongANodeBorderIsNotThrough() {
        val graph = diagramGraph {
            val a = node("a", x = 0.0, y = 0.0, width = 100.0, height = 100.0)
            val c = node("c", x = 200.0, y = 0.0, width = 100.0, height = 100.0)
            val b = node("b", x = 400.0, y = 0.0, width = 100.0, height = 100.0)
            edge("a-b", a, b)
            edge("c-b", c, b)
        }
        val routes = routesOf(
            // Runs exactly along c's top border: inside the 1-unit inset, not the interior.
            routed("a-b", DiagramPoint(100.0, 0.0), DiagramPoint(400.0, 0.0)),
            routed("c-b", DiagramPoint(300.0, 20.0), DiagramPoint(400.0, 20.0)),
        )

        assertEquals(emptyList(), lintDiagram(graph, routes))
    }

    // --- rule: AnchorBunch --------------------------------------------------------------

    @Test
    fun threeEndpointsWithinTheBunchRadiusFunnelIntoOneSpot() {
        val graph = spokeAndHubGraph()
        // All three targets land within the 20-unit bunch radius on hub's left side.
        val routes = routesOf(
            routed("e1", DiagramPoint(100.0, 50.0), DiagramPoint(200.0, 45.0)),
            routed("e2", DiagramPoint(100.0, 170.0), DiagramPoint(200.0, 50.0)),
            routed("e3", DiagramPoint(100.0, 290.0), DiagramPoint(200.0, 55.0)),
        )

        val findings = lintDiagram(graph, routes)

        val bunch = assertIs<DiagramLintFinding.AnchorBunch>(findings.single())
        assertEquals(DiagramNodeId("hub"), bunch.nodeId)
        assertEquals(
            listOf(DiagramEdgeId("e1"), DiagramEdgeId("e2"), DiagramEdgeId("e3")),
            bunch.edgeIds,
        )
        assertEquals(200.0, bunch.at.x, 1e-9)
        assertEquals(50.0, bunch.at.y, 1e-9)
        assertEquals("3 edges funnel into one spot on 'hub' (e1, e2, e3)", bunch.message)
    }

    @Test
    fun endpointsSpacedAtExactlyTheBunchRadiusDoNotCluster() {
        val graph = spokeAndHubGraph()
        // Neighbouring anchors sit exactly 20 apart — the radius is exclusive, so anchors
        // spread by the router's anchorSeparation (24) can never read as a funnel either.
        val routes = routesOf(
            routed("e1", DiagramPoint(100.0, 50.0), DiagramPoint(200.0, 30.0)),
            routed("e2", DiagramPoint(100.0, 170.0), DiagramPoint(200.0, 50.0)),
            routed("e3", DiagramPoint(100.0, 290.0), DiagramPoint(200.0, 70.0)),
        )

        assertEquals(emptyList(), lintDiagram(graph, routes))
    }

    // --- rule: EdgeOverlap --------------------------------------------------------------

    @Test
    fun edgesCoRunningWithinTwoUnitsReportTheOverlapLength() {
        val graph = diagramGraph {
            edge(
                "e1",
                source = DiagramEndpoint.FreePoint(0.0, 50.0),
                target = DiagramEndpoint.FreePoint(100.0, 50.0),
            )
            edge(
                "e2",
                source = DiagramEndpoint.FreePoint(20.0, 51.0),
                target = DiagramEndpoint.FreePoint(80.0, 51.0),
            )
        }
        // e2 rides 1 unit beside e1 for 60 shared units.
        val routes = routesOf(
            routed("e1", DiagramPoint(0.0, 50.0), DiagramPoint(100.0, 50.0)),
            routed("e2", DiagramPoint(20.0, 51.0), DiagramPoint(80.0, 51.0)),
        )

        val findings = lintDiagram(graph, routes)

        val overlap = assertIs<DiagramLintFinding.EdgeOverlap>(findings.single())
        assertEquals(DiagramEdgeId("e1"), overlap.first)
        assertEquals(DiagramEdgeId("e2"), overlap.second)
        assertEquals(60.0, overlap.length, 1e-9)
        assertEquals("edges 'e1' and 'e2' overlap for 60 units", overlap.message)
    }

    @Test
    fun coRunShorterThanTheMinimumLengthIsIgnored() {
        val graph = diagramGraph {
            edge(
                "e1",
                source = DiagramEndpoint.FreePoint(0.0, 50.0),
                target = DiagramEndpoint.FreePoint(100.0, 50.0),
            )
            edge(
                "e2",
                source = DiagramEndpoint.FreePoint(20.0, 51.0),
                target = DiagramEndpoint.FreePoint(31.0, 51.0),
            )
        }
        // Only 11 shared units: just below the 12-unit overlap threshold.
        val routes = routesOf(
            routed("e1", DiagramPoint(0.0, 50.0), DiagramPoint(100.0, 50.0)),
            routed("e2", DiagramPoint(20.0, 51.0), DiagramPoint(31.0, 51.0)),
        )

        assertEquals(emptyList(), lintDiagram(graph, routes))
    }

    // --- rule: CrossingHotspot ----------------------------------------------------------

    @Test
    fun threeCrossingsPiledInOneSpotFormAHotspot() {
        val graph = diagramGraph {
            edge(
                "e1",
                source = DiagramEndpoint.FreePoint(0.0, 100.0),
                target = DiagramEndpoint.FreePoint(200.0, 100.0),
            )
            edge(
                "e2",
                source = DiagramEndpoint.FreePoint(100.0, 0.0),
                target = DiagramEndpoint.FreePoint(100.0, 200.0),
            )
            edge(
                "e3",
                source = DiagramEndpoint.FreePoint(0.0, 5.0),
                target = DiagramEndpoint.FreePoint(200.0, 205.0),
            )
        }
        // Pairwise crossings at (100,100), (95,100) and (100,105) — one tight cluster.
        val routes = routesOf(
            routed("e1", DiagramPoint(0.0, 100.0), DiagramPoint(200.0, 100.0)),
            routed("e2", DiagramPoint(100.0, 0.0), DiagramPoint(100.0, 200.0)),
            routed("e3", DiagramPoint(0.0, 5.0), DiagramPoint(200.0, 205.0)),
        )

        val findings = lintDiagram(graph, routes)

        val hotspot = assertIs<DiagramLintFinding.CrossingHotspot>(findings.single())
        assertEquals(3, hotspot.count)
        assertEquals(
            listOf(DiagramEdgeId("e1"), DiagramEdgeId("e2"), DiagramEdgeId("e3")),
            hotspot.edgeIds,
        )
        assertEquals(295.0 / 3.0, hotspot.at.x, 1e-9)
        assertEquals(305.0 / 3.0, hotspot.at.y, 1e-9)
        assertEquals("3 edge crossings pile up near (98, 101)", hotspot.message)
    }

    @Test
    fun twoNearbyCrossingsStayBelowTheHotspotLimit() {
        val graph = diagramGraph {
            edge(
                "e1",
                source = DiagramEndpoint.FreePoint(0.0, 100.0),
                target = DiagramEndpoint.FreePoint(200.0, 100.0),
            )
            edge(
                "e2",
                source = DiagramEndpoint.FreePoint(100.0, 0.0),
                target = DiagramEndpoint.FreePoint(100.0, 200.0),
            )
            edge(
                "e3",
                source = DiagramEndpoint.FreePoint(110.0, 0.0),
                target = DiagramEndpoint.FreePoint(110.0, 200.0),
            )
        }
        // Two crossings 10 apart cluster together but stay below the limit of 3.
        val routes = routesOf(
            routed("e1", DiagramPoint(0.0, 100.0), DiagramPoint(200.0, 100.0)),
            routed("e2", DiagramPoint(100.0, 0.0), DiagramPoint(100.0, 200.0)),
            routed("e3", DiagramPoint(110.0, 0.0), DiagramPoint(110.0, 200.0)),
        )

        assertEquals(emptyList(), lintDiagram(graph, routes))
    }

    // --- rule: LabelOverNode ------------------------------------------------------------

    @Test
    fun labelBuriedOnAForeignNodeIsReported() {
        val graph = diagramGraph {
            // A connected wall spans the whole route above the line, and the label's
            // authored offset parks it DEEP inside — beyond the anchor's push-out limit,
            // so no correction can rescue it and the lint must still fire. (A shallow
            // burial self-heals: the anchor slides its box off the node body.)
            val wall = node("wall", x = 0.0, y = -165.0, width = 300.0, height = 160.0)
            edge(
                "wall-up",
                source = DiagramEndpoint.FloatingAnchor(wall),
                target = DiagramEndpoint.FreePoint(150.0, -240.0),
            )
            edge(
                "e",
                source = DiagramEndpoint.FreePoint(0.0, 0.0),
                target = DiagramEndpoint.FreePoint(300.0, 0.0),
                labels = listOf(DiagramEdgeLabel(DiagramLabel("over"), offsetY = -75.0)),
            )
        }
        val routes = routesOf(
            routed("wall-up", DiagramPoint(150.0, -165.0), DiagramPoint(150.0, -240.0)),
            routed("e", DiagramPoint(0.0, 0.0), DiagramPoint(300.0, 0.0)),
        )

        val findings = lintDiagram(graph, routes)

        assertEquals(
            listOf(DiagramLintFinding.LabelOverNode(DiagramEdgeId("e"), DiagramNodeId("wall"))),
            findings,
        )
        assertEquals("label of edge 'e' covers node 'wall'", findings.single().message)
    }

    @Test
    fun labelClippingAForeignNodeSlidesOffAndStopsBeingReported() {
        // The common real defect: a route moved after the label offset was authored, the
        // box now clips a node edge by a few pixels. The anchor pushes it out; no finding.
        val graph = diagramGraph {
            val wall = node("wall", x = 0.0, y = -45.0, width = 300.0, height = 40.0)
            edge(
                "wall-up",
                source = DiagramEndpoint.FloatingAnchor(wall),
                target = DiagramEndpoint.FreePoint(150.0, -120.0),
            )
            edge(
                "e",
                source = DiagramEndpoint.FreePoint(0.0, 0.0),
                target = DiagramEndpoint.FreePoint(300.0, 0.0),
                label = "over",
            )
        }
        val routes = routesOf(
            routed("wall-up", DiagramPoint(150.0, -45.0), DiagramPoint(150.0, -120.0)),
            routed("e", DiagramPoint(0.0, 0.0), DiagramPoint(300.0, 0.0)),
        )

        assertEquals(emptyList(), lintDiagram(graph, routes))
    }

    @Test
    fun labelThatSlidesToAClearSpotIsNotReported() {
        val graph = diagramGraph {
            // The foreign node covers only the center of the route: the anchor slides
            // along the edge to a clear candidate fraction, so nothing is buried.
            val mid = node("mid", x = 130.0, y = -45.0, width = 40.0, height = 40.0)
            edge(
                "mid-up",
                source = DiagramEndpoint.FloatingAnchor(mid),
                target = DiagramEndpoint.FreePoint(150.0, -120.0),
            )
            edge(
                "e",
                source = DiagramEndpoint.FreePoint(0.0, 0.0),
                target = DiagramEndpoint.FreePoint(300.0, 0.0),
                label = "over",
            )
        }
        val routes = routesOf(
            routed("mid-up", DiagramPoint(150.0, -45.0), DiagramPoint(150.0, -120.0)),
            routed("e", DiagramPoint(0.0, 0.0), DiagramPoint(300.0, 0.0)),
        )

        assertEquals(emptyList(), lintDiagram(graph, routes))
    }

    // --- whole-report behaviour ---------------------------------------------------------

    @Test
    fun wellSpacedDiagramIsCleanWithDefaultRouting() {
        val graph = diagramGraph {
            val a = node("a", x = 0.0, y = 0.0)
            val b = node("b", x = 300.0, y = 0.0)
            edge("a-b", a, b)
        }

        val findings = lintDiagram(graph)

        assertEquals(emptyList(), findings)
        assertEquals("vision-test: clean", findings.lintReport())
    }

    @Test
    fun findingsFromDifferentRulesAggregateInRuleOrder() {
        val graph = diagramGraph {
            val a = node("a", x = 0.0, y = 0.0, width = 100.0, height = 100.0)
            val b = node("b", x = 90.0, y = 0.0, width = 100.0, height = 100.0)
            edge("a-b", a, b)
            edge(
                "e2",
                source = DiagramEndpoint.FreePoint(0.0, 300.0),
                target = DiagramEndpoint.FreePoint(100.0, 300.0),
            )
            edge(
                "e3",
                source = DiagramEndpoint.FreePoint(0.0, 301.0),
                target = DiagramEndpoint.FreePoint(60.0, 301.0),
            )
        }
        val routes = routesOf(
            routed("a-b", DiagramPoint(50.0, -20.0), DiagramPoint(140.0, -20.0)),
            routed("e2", DiagramPoint(0.0, 300.0), DiagramPoint(100.0, 300.0)),
            routed("e3", DiagramPoint(0.0, 301.0), DiagramPoint(60.0, 301.0)),
        )

        val findings = lintDiagram(graph, routes)

        assertEquals(2, findings.size)
        assertIs<DiagramLintFinding.NodeOverlap>(findings[0])
        assertIs<DiagramLintFinding.EdgeOverlap>(findings[1])
        assertEquals(
            "warning: nodes 'a' and 'b' overlap\n" +
                "warning: edges 'e2' and 'e3' overlap for 60 units",
            findings.lintReport(),
        )
    }

    // --- fixtures -----------------------------------------------------------------------

    /** Three spokes on the left, one hub on the right, edges e1/e2/e3 spoke → hub. */
    private fun spokeAndHubGraph() = diagramGraph {
        val s1 = node("s1", x = 0.0, y = 0.0, width = 100.0, height = 100.0)
        val s2 = node("s2", x = 0.0, y = 120.0, width = 100.0, height = 100.0)
        val s3 = node("s3", x = 0.0, y = 240.0, width = 100.0, height = 100.0)
        val hub = node("hub", x = 200.0, y = 0.0, width = 100.0, height = 100.0)
        edge("e1", s1, hub)
        edge("e2", s2, hub)
        edge("e3", s3, hub)
    }

    private fun routed(id: String, vararg points: DiagramPoint): RoutedEdge =
        RoutedEdge(DiagramEdgeId(id), DiagramRoutingStyle.ORTHOGONAL, points.toList())

    private fun routesOf(vararg routes: RoutedEdge): Map<DiagramEdgeId, RoutedEdge> =
        routes.associateBy { it.edgeId }
}
