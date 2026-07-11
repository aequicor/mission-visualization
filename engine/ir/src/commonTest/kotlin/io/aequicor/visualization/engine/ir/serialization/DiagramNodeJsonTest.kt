package io.aequicor.visualization.engine.ir.serialization

import io.aequicor.visualization.engine.ir.model.DesignNode
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.subsystems.diagrams.model.DiagramArrowhead
import io.aequicor.visualization.subsystems.diagrams.model.DiagramArrowheadKind
import io.aequicor.visualization.subsystems.diagrams.model.DiagramColor
import io.aequicor.visualization.subsystems.diagrams.model.DiagramConnectionMode
import io.aequicor.visualization.subsystems.diagrams.model.DiagramEdge
import io.aequicor.visualization.subsystems.diagrams.model.DiagramEdgeId
import io.aequicor.visualization.subsystems.diagrams.model.DiagramEdgeLabel
import io.aequicor.visualization.subsystems.diagrams.model.DiagramEdgeLabelPosition
import io.aequicor.visualization.subsystems.diagrams.model.DiagramEndpoint
import io.aequicor.visualization.subsystems.diagrams.model.DiagramGraph
import io.aequicor.visualization.subsystems.diagrams.model.DiagramGroup
import io.aequicor.visualization.subsystems.diagrams.model.DiagramGroupId
import io.aequicor.visualization.subsystems.diagrams.model.DiagramLabel
import io.aequicor.visualization.subsystems.diagrams.model.DiagramLayer
import io.aequicor.visualization.subsystems.diagrams.model.DiagramLayerId
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNode
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodeId
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodePayload
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodeSide
import io.aequicor.visualization.subsystems.diagrams.model.DiagramPort
import io.aequicor.visualization.subsystems.diagrams.model.DiagramPortAnchor
import io.aequicor.visualization.subsystems.diagrams.model.DiagramPortId
import io.aequicor.visualization.subsystems.diagrams.model.DiagramRelation
import io.aequicor.visualization.subsystems.diagrams.model.DiagramRoutingStyle
import io.aequicor.visualization.subsystems.diagrams.model.DiagramShapeKind
import io.aequicor.visualization.subsystems.diagrams.model.DiagramStrokePattern
import io.aequicor.visualization.subsystems.diagrams.model.DiagramStyle
import io.aequicor.visualization.subsystems.diagrams.model.ErCardinality
import io.aequicor.visualization.subsystems.diagrams.model.LineJumpStyle
import io.aequicor.visualization.subsystems.diagrams.model.TableCell
import io.aequicor.visualization.subsystems.diagrams.model.TableColumn
import io.aequicor.visualization.subsystems.diagrams.model.TableNode
import io.aequicor.visualization.subsystems.diagrams.model.TableRow
import io.aequicor.visualization.subsystems.diagrams.model.UmlClassNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlMember
import io.aequicor.visualization.subsystems.diagrams.model.UmlMessageKind
import io.aequicor.visualization.subsystems.diagrams.model.UmlVisibility
import io.aequicor.visualization.subsystems.diagrams.path.DiagramPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DiagramNodeJsonTest {

    private val classNode = DiagramNode(
        id = DiagramNodeId("user"),
        x = 40.0,
        y = 24.0,
        width = 180.0,
        height = 120.0,
        payload = UmlClassNode(
            name = "User",
            stereotype = "entity",
            abstract = true,
            attributes = listOf(
                UmlMember("id: Long", visibility = UmlVisibility.PRIVATE),
                UmlMember("name: String", visibility = UmlVisibility.PROTECTED, static = true),
            ),
            operations = listOf(
                UmlMember("rename(next: String)", abstract = true),
            ),
        ),
        ports = DiagramPort.standardPorts() +
            DiagramPort(DiagramPortId("custom"), DiagramPortAnchor.RelativePoint(0.25, 1.5)),
        style = DiagramStyle(
            fill = DiagramColor.fromArgb(alpha = 0x80, red = 0x11, green = 0x22, blue = 0x33),
            stroke = DiagramColor.Black,
            strokeWidth = 2.0,
            pattern = DiagramStrokePattern.DASHED,
            shadow = true,
        ),
        labels = listOf(DiagramLabel("**User**", markdown = true)),
        layerId = DiagramLayerId("flow"),
    )

    private val accountNode = DiagramNode(
        id = DiagramNodeId("account"),
        x = 320.0,
        y = 60.0,
        width = 160.0,
        height = 90.0,
        payload = DiagramNodePayload.BasicShape(DiagramShapeKind.ROUNDED_RECTANGLE),
        ports = listOf(DiagramPort.side(DiagramNodeSide.LEFT, offset = 0.25)),
    )

    private val tableNode = DiagramNode(
        id = DiagramNodeId("grid"),
        x = 40.0,
        y = 220.0,
        width = 240.0,
        height = 64.0,
        payload = TableNode(
            rows = listOf(TableRow(header = true), TableRow(height = 40.0)),
            columns = listOf(TableColumn(), TableColumn(width = 80.0)),
            cells = listOf(
                TableCell(0, 0, colSpan = 2, label = DiagramLabel("Header")),
                TableCell(1, 1, label = DiagramLabel("Cell")),
            ),
        ),
        parentId = DiagramNodeId("user"),
    )

    private val graph = DiagramGraph(
        nodes = listOf(classNode, accountNode, tableNode),
        edges = listOf(
            DiagramEdge(
                id = DiagramEdgeId("owns"),
                source = DiagramEndpoint.FixedPort(DiagramNodeId("user"), DiagramPortId("right")),
                target = DiagramEndpoint.FloatingAnchor(DiagramNodeId("account")),
                relation = DiagramRelation.Generalization,
                routing = DiagramRoutingStyle.STRAIGHT,
                waypoints = listOf(DiagramPoint(260.0, 84.0)),
                labels = listOf(
                    DiagramEdgeLabel(DiagramLabel("owns"), DiagramEdgeLabelPosition.MIDDLE),
                    DiagramEdgeLabel(DiagramLabel("1"), DiagramEdgeLabelPosition.SOURCE, offsetX = 4.0),
                ),
                sourceArrowhead = DiagramArrowhead(DiagramArrowheadKind.OVAL_FILLED, size = 6.0, inset = 2.0),
                lineJumps = LineJumpStyle.ARC,
                connectionMode = DiagramConnectionMode.ARROW,
                flowAnimation = true,
                layerId = DiagramLayerId("flow"),
            ),
            DiagramEdge(
                id = DiagramEdgeId("msg"),
                source = DiagramEndpoint.FreePoint(10.0, 10.0),
                target = DiagramEndpoint.FloatingAnchor(DiagramNodeId("user")),
                relation = DiagramRelation.Message(UmlMessageKind.ASYNC),
            ),
            DiagramEdge(
                id = DiagramEdgeId("rel"),
                source = DiagramEndpoint.FloatingAnchor(DiagramNodeId("user")),
                target = DiagramEndpoint.FloatingAnchor(DiagramNodeId("account")),
                relation = DiagramRelation.EntityRelation(
                    sourceCardinality = ErCardinality.ZERO_OR_ONE,
                    targetCardinality = ErCardinality.ONE_OR_MANY,
                ),
            ),
        ),
        layers = listOf(DiagramLayer(DiagramLayerId("flow"), name = "Flow", locked = true)),
        groups = listOf(
            DiagramGroup(
                id = DiagramGroupId("pair"),
                memberIds = listOf(DiagramNodeId("user"), DiagramNodeId("account")),
                name = "Pair",
            ),
        ),
    )

    private fun diagramDesignNode(graph: DiagramGraph): DesignNode =
        DesignNode(id = "d1", type = "diagram", kind = DesignNodeKind.Diagram(graph))

    private fun parseDiagram(json: String): DesignNodeKind.Diagram {
        val result = assertIs<DesignNodeParseResult.Success>(parseDesignNode(json))
        return assertIs<DesignNodeKind.Diagram>(result.node.kind)
    }

    @Test
    fun diagramGraphRoundTripsThroughWriter() {
        val json = writeDesignNode(diagramDesignNode(graph)).toJsonString()
        val rewritten = parseDiagram(json)
        assertEquals(graph, rewritten.graph)
    }

    @Test
    fun readsDiagramNodeFromDocumentJson() {
        val result = assertIs<DesignParseResult.Success>(
            parseDesignDocument(
                """
                {
                  "schemaVersion": "slm-ir/1.0",
                  "pages": [
                    { "id": "p", "children": [
                      { "id": "d1", "type": "diagram", "diagram": {
                          "nodes": [
                            { "id": "a", "x": 10, "y": 20, "width": 100, "height": 50,
                              "payload": { "type": "umlClass", "name": "A",
                                "attributes": [ { "text": "id: Long", "visibility": "private" } ] },
                              "ports": [ { "id": "out", "anchor": { "type": "side", "side": "right" } } ] },
                            { "id": "b", "x": 200, "y": 20, "width": 100, "height": 50 }
                          ],
                          "edges": [
                            { "id": "e", "source": { "type": "port", "nodeId": "a", "portId": "out" },
                              "target": { "type": "floating", "nodeId": "b" },
                              "relation": { "type": "generalization" }, "routing": "straight" }
                          ]
                      } }
                    ] }
                  ]
                }
                """.trimIndent(),
            ),
        )
        val node = result.document.pages.single().children.single()
        val kind = assertIs<DesignNodeKind.Diagram>(node.kind)
        val a = kind.graph.nodeById(DiagramNodeId("a"))!!
        val payload = assertIs<UmlClassNode>(a.payload)
        assertEquals("A", payload.name)
        assertEquals(UmlVisibility.PRIVATE, payload.attributes.single().visibility)
        assertEquals(
            DiagramPortAnchor.SideOffset(DiagramNodeSide.RIGHT),
            a.portById(DiagramPortId("out"))!!.anchor,
        )
        val edge = kind.graph.edgeById(DiagramEdgeId("e"))!!
        assertEquals(DiagramEndpoint.FixedPort(DiagramNodeId("a"), DiagramPortId("out")), edge.source)
        assertEquals(DiagramRelation.Generalization, edge.relation)
        assertEquals(DiagramRoutingStyle.STRAIGHT, edge.routing)
    }

    @Test
    fun emptyDiagramIsOmittedFromOutput() {
        val json = writeDesignNode(diagramDesignNode(DiagramGraph.Empty)).toJsonString()
        assertFalse(json.contains("\"diagram\":"), "empty diagram must be omitted: $json")
        // ...and the type alone reads back as an empty diagram.
        assertEquals(DiagramGraph.Empty, parseDiagram(json).graph)
    }

    @Test
    fun malformedDiagramWarnsAndFallsBackToEmptyGraph() {
        val result = assertIs<DesignNodeParseResult.Success>(
            parseDesignNode("""{ "id": "d1", "type": "diagram", "diagram": { "nodes": [ { "x": 1 } ] } }"""),
        )
        assertEquals(DiagramGraph.Empty, assertIs<DesignNodeKind.Diagram>(result.node.kind).graph)
        assertTrue(
            result.diagnostics.any { "Malformed diagram graph" in it.message },
            "expected a malformed-diagram warning; got: ${result.diagnostics}",
        )
    }

    @Test
    fun unknownEnumValuesFallBackToDefaults() {
        val kind = parseDiagram(
            """
            { "id": "d1", "type": "diagram", "diagram": {
                "nodes": [ { "id": "a", "payload": { "type": "shape", "shape": "dodecahedron" } } ],
                "edges": [ { "id": "e",
                  "source": { "type": "floating", "nodeId": "a" },
                  "target": { "type": "free", "x": 5, "y": 5 },
                  "routing": "quantum" } ]
            } }
            """.trimIndent(),
        )
        val payload = assertIs<DiagramNodePayload.BasicShape>(
            kind.graph.nodeById(DiagramNodeId("a"))!!.payload,
        )
        assertEquals(DiagramShapeKind.RECTANGLE, payload.shape)
        assertEquals(DiagramRoutingStyle.ORTHOGONAL, kind.graph.edges.single().routing)
    }
}
