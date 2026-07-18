package io.aequicor.visualization.subsystems.diagrams.export

import io.aequicor.visualization.subsystems.diagrams.model.DiagramEdgeId
import io.aequicor.visualization.subsystems.diagrams.model.DiagramEndpoint
import io.aequicor.visualization.subsystems.diagrams.model.DiagramRelation
import io.aequicor.visualization.subsystems.diagrams.model.DiagramRoutingStyle
import io.aequicor.visualization.subsystems.diagrams.model.LineJumpStyle
import io.aequicor.visualization.subsystems.diagrams.model.UmlClassNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlMember
import io.aequicor.visualization.subsystems.diagrams.model.diagramGraph
import io.aequicor.visualization.subsystems.diagrams.path.DiagramPoint
import io.aequicor.visualization.subsystems.diagrams.routing.RoutedEdge
import io.aequicor.visualization.subsystems.diagrams.routing.routeAllEdges
import io.aequicor.visualization.subsystems.diagrams.templates.diagramTemplates
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DiagramSvgExportTest {

    private fun simpleGraph() = diagramGraph {
        val a = node("a", x = 0.0, y = 0.0, width = 120.0, height = 60.0, label = "Alpha")
        val b = node("b", x = 240.0, y = 0.0, width = 120.0, height = 60.0, label = "Beta")
        edge(
            id = "e1",
            from = a,
            to = b,
            relation = DiagramRelation.Association(directed = true),
            routing = DiagramRoutingStyle.STRAIGHT,
            label = "flows",
        )
    }

    @Test
    fun exportsCompleteSvgDocument() {
        val svg = diagramToSvg(simpleGraph())
        assertTrue(svg.startsWith("<svg xmlns=\"http://www.w3.org/2000/svg\""), svg.take(120))
        assertTrue(svg.endsWith("</svg>"))
        assertTrue(svg.contains("viewBox=\""))
    }

    @Test
    fun exportsNodeOutlinePaths() {
        val svg = diagramToSvg(simpleGraph())
        // Rectangle node at (0,0) 120x60 — exact outline path data from outlinePath().
        assertTrue(svg.contains("M 0 0 L 120 0 L 120 60 L 0 60 Z"), "missing node outline path")
        // Both node outlines + at least the edge line + arrowhead marker.
        assertTrue(Regex("<path ").findAll(svg).count() >= 4)
    }

    @Test
    fun exportsLabelsAsTextElements() {
        val svg = diagramToSvg(simpleGraph())
        // Node captions wrap, so each line is its own <tspan>; edge labels stay single-line.
        assertTrue(svg.contains(">Alpha</tspan>"))
        assertTrue(svg.contains(">Beta</tspan>"))
        assertTrue(svg.contains(">flows</text>"))
    }

    @Test
    fun escapesXmlInLabels() {
        val graph = diagramGraph {
            node("a", label = "<b> & \"q\"")
        }
        val svg = diagramToSvg(graph)
        assertTrue(svg.contains("&lt;b&gt; &amp; &quot;q&quot;"))
        assertTrue(!svg.contains("<b> &"))
    }

    @Test
    fun rendersClassCompartments() {
        val graph = diagramGraph {
            node(
                id = "c",
                width = 180.0,
                height = 96.0,
                payload = UmlClassNode(
                    name = "Shape",
                    attributes = listOf(UmlMember("id: String")),
                    operations = listOf(UmlMember("area(): Double")),
                ),
            )
        }
        val svg = diagramToSvg(graph)
        assertTrue(svg.contains(">Shape</text>"))
        assertTrue(svg.contains(">+ id: String</text>"))
        assertTrue(svg.contains(">+ area(): Double</text>"))
    }

    @Test
    fun acceptsPreRoutedEdges() {
        val graph = simpleGraph()
        val routed = routeAllEdges(graph)
        assertEquals(diagramToSvg(graph, routed), diagramToSvg(graph))
    }

    @Test
    fun horizontalEdgeArcsOverVerticalAtCrossings() {
        val graph = diagramGraph {
            edge(
                id = "horizontal",
                source = DiagramEndpoint.FreePoint(0.0, 50.0),
                target = DiagramEndpoint.FreePoint(100.0, 50.0),
                routing = DiagramRoutingStyle.STRAIGHT,
            )
            edge(
                id = "vertical",
                source = DiagramEndpoint.FreePoint(50.0, 0.0),
                target = DiagramEndpoint.FreePoint(50.0, 100.0),
                routing = DiagramRoutingStyle.STRAIGHT,
            )
        }
        val routes = listOf(
            RoutedEdge(
                DiagramEdgeId("horizontal"),
                DiagramRoutingStyle.STRAIGHT,
                listOf(DiagramPoint(0.0, 50.0), DiagramPoint(100.0, 50.0)),
            ),
            RoutedEdge(
                DiagramEdgeId("vertical"),
                DiagramRoutingStyle.STRAIGHT,
                listOf(DiagramPoint(50.0, 0.0), DiagramPoint(50.0, 100.0)),
            ),
        )

        val svg = diagramToSvg(graph, routes)

        // Exactly one side of the crossing jumps, and it is the horizontal one
        // (Lucid-style, independent of draw order); the vertical stays straight.
        assertTrue(svg.contains("d=\"M 50 0 L 50 100\""), svg)
        assertTrue(svg.contains("d=\"M 0 50 L 44 50 A 6 6 0 0 1 56 50 L 100 50\""), svg)
    }

    @Test
    fun jumpsNoneKeepsCrossingsStraight() {
        val graph = diagramGraph {
            edge(
                id = "horizontal",
                source = DiagramEndpoint.FreePoint(0.0, 50.0),
                target = DiagramEndpoint.FreePoint(100.0, 50.0),
                routing = DiagramRoutingStyle.STRAIGHT,
                lineJumps = LineJumpStyle.NONE,
            )
            edge(
                id = "vertical",
                source = DiagramEndpoint.FreePoint(50.0, 0.0),
                target = DiagramEndpoint.FreePoint(50.0, 100.0),
                routing = DiagramRoutingStyle.STRAIGHT,
                lineJumps = LineJumpStyle.NONE,
            )
        }
        val routes = listOf(
            RoutedEdge(
                DiagramEdgeId("horizontal"),
                DiagramRoutingStyle.STRAIGHT,
                listOf(DiagramPoint(0.0, 50.0), DiagramPoint(100.0, 50.0)),
            ),
            RoutedEdge(
                DiagramEdgeId("vertical"),
                DiagramRoutingStyle.STRAIGHT,
                listOf(DiagramPoint(50.0, 0.0), DiagramPoint(50.0, 100.0)),
            ),
        )

        val svg = diagramToSvg(graph, routes)

        assertTrue(svg.contains("d=\"M 0 50 L 100 50\""), svg)
        assertTrue(!svg.contains(" A "), svg)
    }

    @Test
    fun emptyGraphStillProducesValidSvg() {
        val svg = diagramToSvg(diagramGraph { })
        assertTrue(svg.startsWith("<svg"))
        assertTrue(svg.endsWith("</svg>"))
    }

    @Test
    fun everyTemplateExports() {
        for (template in diagramTemplates()) {
            val svg = diagramToSvg(template.graph)
            assertTrue(svg.startsWith("<svg"), "template ${template.id} failed to export")
            assertTrue(svg.contains("<path "), "template ${template.id} has no geometry")
        }
    }
}
