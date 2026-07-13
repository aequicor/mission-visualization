package io.aequicor.visualization.subsystems.diagrams.slm

import io.aequicor.visualization.engine.frontend.blocks.CnlContainerLine
import io.aequicor.visualization.engine.frontend.diagnostics.DiagnosticCollector
import io.aequicor.visualization.engine.frontend.markdown.SlmSourceSpan
import io.aequicor.visualization.engine.ir.model.DesignDiagnostic
import io.aequicor.visualization.engine.ir.model.DesignSeverity
import io.aequicor.visualization.subsystems.diagrams.model.BpmnNodeKind
import io.aequicor.visualization.subsystems.diagrams.model.DiagramArrowhead
import io.aequicor.visualization.subsystems.diagrams.model.DiagramArrowheadKind
import io.aequicor.visualization.subsystems.diagrams.model.DiagramColor
import io.aequicor.visualization.subsystems.diagrams.model.DiagramConnectionMode
import io.aequicor.visualization.subsystems.diagrams.model.DiagramCornerStyle
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
import io.aequicor.visualization.subsystems.diagrams.model.DiagramOrientation
import io.aequicor.visualization.subsystems.diagrams.model.DiagramPort
import io.aequicor.visualization.subsystems.diagrams.model.DiagramPortAnchor
import io.aequicor.visualization.subsystems.diagrams.model.DiagramPortId
import io.aequicor.visualization.subsystems.diagrams.model.DiagramRelation
import io.aequicor.visualization.subsystems.diagrams.model.DiagramRoutingStyle
import io.aequicor.visualization.subsystems.diagrams.model.DiagramShapeKind
import io.aequicor.visualization.subsystems.diagrams.model.DiagramStrokePattern
import io.aequicor.visualization.subsystems.diagrams.model.DiagramStyle
import io.aequicor.visualization.subsystems.diagrams.model.ErAttribute
import io.aequicor.visualization.subsystems.diagrams.model.ErCardinality
import io.aequicor.visualization.subsystems.diagrams.model.FlowchartNodeKind
import io.aequicor.visualization.subsystems.diagrams.model.LineJumpStyle
import io.aequicor.visualization.subsystems.diagrams.model.SwimlaneLane
import io.aequicor.visualization.subsystems.diagrams.model.TableCell
import io.aequicor.visualization.subsystems.diagrams.model.TableColumn
import io.aequicor.visualization.subsystems.diagrams.model.TableNode
import io.aequicor.visualization.subsystems.diagrams.model.TableRow
import io.aequicor.visualization.subsystems.diagrams.model.UmlActivation
import io.aequicor.visualization.subsystems.diagrams.model.UmlActivityKind
import io.aequicor.visualization.subsystems.diagrams.model.UmlActivityNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlActorNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlClassNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlComponentNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlDeploymentNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlLifelineNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlMember
import io.aequicor.visualization.subsystems.diagrams.model.UmlMessageKind
import io.aequicor.visualization.subsystems.diagrams.model.UmlNoteNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlPackageNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlStateKind
import io.aequicor.visualization.subsystems.diagrams.model.UmlStateNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlUseCaseNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlVisibility
import io.aequicor.visualization.subsystems.diagrams.path.DiagramPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Grammar–emitter symmetry over the CNL sentence surface: `parse(emit(graph)) == graph`
 * for graphs covering every payload variant, relation, endpoint form, arrowhead, port
 * anchor, label shape, style key, layer/group/parent reference and flag — plus
 * canonicalization (`emit ∘ parse` idempotent on canonical text).
 */
class DiagramCnlRoundTripTest {

    private data class ParseResult(val graph: DiagramGraph, val diagnostics: List<DesignDiagnostic>)

    private fun parseBody(lines: List<String>): ParseResult {
        val diagnostics = DiagnosticCollector("test.layout.md")
        val elements = mutableListOf<DiagramCnlSentence>()
        lines.forEachIndexed { index, line ->
            when (val result = DiagramSlmExtension.parseSentence(line, index + 1, diagnostics)) {
                is CnlContainerLine.Sentence -> elements += result.element
                CnlContainerLine.Invalid -> {}
                CnlContainerLine.Prose -> {}
            }
        }
        val graph = DiagramSlmExtension.aggregateSentences(
            elements = elements,
            span = SlmSourceSpan(1, lines.size.coerceAtLeast(1)),
            diagnostics = diagnostics,
        )
        return ParseResult(graph, diagnostics.diagnostics)
    }

    private fun assertRoundTrips(graph: DiagramGraph, context: String) {
        val emitted = DiagramCnlWriter.sentences(graph)
        val parsed = parseBody(emitted)
        assertTrue(
            parsed.diagnostics.none { it.severity == DesignSeverity.Error },
            "$context: unexpected errors ${parsed.diagnostics} in:\n${emitted.joinToString("\n")}",
        )
        assertEquals(graph, parsed.graph, "$context: parse(emit(graph)) != graph for:\n${emitted.joinToString("\n")}")
        // Canonicalization: re-emitting the parsed graph reproduces the same text.
        assertEquals(emitted, DiagramCnlWriter.sentences(parsed.graph), "$context: emit not canonical")
    }

    private fun node(
        id: String,
        payload: DiagramNodePayload,
        x: Double = 10.0,
        y: Double = 20.0,
        w: Double = 120.0,
        h: Double = 60.0,
    ): DiagramNode = DiagramNode(
        id = DiagramNodeId(id),
        x = x,
        y = y,
        width = w,
        height = h,
        payload = payload,
    )

    // --- payload variants ---

    @Test
    fun everyBasicShapeKindRoundTrips() {
        DiagramShapeKind.entries.forEach { kind ->
            val graph = DiagramGraph(nodes = listOf(node("s", DiagramNodePayload.BasicShape(kind))))
            assertRoundTrips(graph, "shape $kind")
        }
    }

    @Test
    fun everyStructuredPayloadVariantRoundTrips() {
        val payloads: List<DiagramNodePayload> = listOf(
            DiagramNodePayload.ContainerNode(),
            DiagramNodePayload.ContainerNode(title = DiagramLabel("Ingest zone"), collapsed = true),
            DiagramNodePayload.ContainerNode(title = DiagramLabel("**Zone**", markdown = true)),
            DiagramNodePayload.SwimlaneNode(),
            DiagramNodePayload.SwimlaneNode(
                orientation = DiagramOrientation.VERTICAL,
                lanes = listOf(
                    SwimlaneLane(title = DiagramLabel("Intake"), size = 140.0),
                    SwimlaneLane(title = DiagramLabel("Review")),
                    SwimlaneLane(size = 100.0),
                    SwimlaneLane(title = DiagramLabel("_md_", markdown = true), size = 90.0),
                ),
                title = DiagramLabel("Fulfilment"),
            ),
            DiagramNodePayload.FlowchartNode(FlowchartNodeKind.PROCESS),
            DiagramNodePayload.FlowchartNode(FlowchartNodeKind.DECISION),
            DiagramNodePayload.FlowchartNode(FlowchartNodeKind.INPUT_OUTPUT),
            DiagramNodePayload.FlowchartNode(FlowchartNodeKind.TERMINATOR),
            DiagramNodePayload.ErEntityNode(name = "Customer"),
            DiagramNodePayload.ErEntityNode(
                name = "Order",
                attributes = listOf(
                    ErAttribute(name = "id", type = "UUID", primaryKey = true),
                    ErAttribute(name = "name", type = "String"),
                    ErAttribute(name = "customerId", type = "UUID", foreignKey = true),
                    ErAttribute(name = "note"),
                ),
            ),
            DiagramNodePayload.BpmnNode(BpmnNodeKind.TASK),
            DiagramNodePayload.BpmnNode(BpmnNodeKind.EVENT),
            DiagramNodePayload.BpmnNode(BpmnNodeKind.GATEWAY),
            TableNode(
                rows = listOf(TableRow(height = 32.0, header = true), TableRow(height = 32.0), TableRow(height = 40.0)),
                columns = listOf(TableColumn(width = 160.0, header = true), TableColumn(width = 100.0), TableColumn(width = 100.0)),
                cells = listOf(
                    TableCell(row = 0, column = 0, colSpan = 3, label = DiagramLabel("Plans"), style = DiagramStyle(fill = DiagramColor(0xFFEEF2F7u))),
                    TableCell(row = 1, column = 0, label = DiagramLabel("Basic")),
                    TableCell(row = 1, column = 1, rowSpan = 2, label = DiagramLabel("**9€**", markdown = true)),
                ),
            ),
            UmlClassNode(name = "Shape", abstract = true),
            UmlClassNode(
                name = "Registry",
                stereotype = "singleton",
                attributes = listOf(
                    UmlMember(text = "instance: Registry", visibility = UmlVisibility.PRIVATE, static = true),
                    UmlMember(text = "origin: Point"),
                    UmlMember(text = "cache: Map", visibility = UmlVisibility.PROTECTED),
                    UmlMember(text = "tag: String", visibility = UmlVisibility.PACKAGE),
                ),
                operations = listOf(
                    UmlMember(text = "get(): Registry", static = true),
                    UmlMember(text = "area(): Double", abstract = true),
                    UmlMember(text = "move(dx: Int, dy: Int): Unit"),
                ),
            ),
            UmlLifelineNode(name = "User", actor = true, activations = listOf(UmlActivation(0.1, 0.35), UmlActivation(0.6, 0.8))),
            UmlLifelineNode(name = "System"),
            UmlStateNode(),
            UmlStateNode(name = "Working"),
            UmlStateNode(kind = UmlStateKind.INITIAL),
            UmlStateNode(name = "Machine", kind = UmlStateKind.COMPOSITE),
            UmlStateNode(kind = UmlStateKind.FINAL),
            UmlActivityNode(kind = UmlActivityKind.ACTION, name = "Validate order"),
            UmlActivityNode(kind = UmlActivityKind.DECISION),
            UmlActivityNode(kind = UmlActivityKind.FORK),
            UmlActivityNode(kind = UmlActivityKind.JOIN),
            UmlActivityNode(kind = UmlActivityKind.START),
            UmlActivityNode(kind = UmlActivityKind.END),
            UmlActorNode(name = "Buyer"),
            UmlUseCaseNode(name = "Checkout"),
            UmlComponentNode(name = "androidApp", stereotype = "app"),
            UmlComponentNode(name = "shared"),
            UmlDeploymentNode(name = "App server", stereotype = "device"),
            UmlDeploymentNode(name = "Edge"),
            UmlNoteNode(text = "Ports follow the node when it moves."),
            UmlPackageNode(name = "engine"),
        )
        payloads.forEachIndexed { index, payload ->
            val graph = DiagramGraph(nodes = listOf(node("n$index", payload)))
            assertRoundTrips(graph, "payload[$index] ${payload::class.simpleName}")
        }
    }

    @Test
    fun nodeCommonPartRoundTrips() {
        val layer = DiagramLayer(id = DiagramLayerId("wiring"), name = "Wiring", visible = false, locked = true)
        val parent = node("machine", UmlStateNode(name = "Machine", kind = UmlStateKind.COMPOSITE), w = 320.0, h = 200.0)
        val full = DiagramNode(
            id = DiagramNodeId("inner"),
            x = -20.5,
            y = 40.25,
            width = 100.0,
            height = 48.0,
            rotation = 3.5,
            payload = UmlStateNode(name = "Inner"),
            ports = listOf(
                DiagramPort(DiagramPortId("out"), DiagramPortAnchor.SideOffset(DiagramNodeSide.RIGHT, 0.25)),
                DiagramPort(DiagramPortId("in"), DiagramPortAnchor.SideOffset(DiagramNodeSide.LEFT)),
                DiagramPort(DiagramPortId("hook"), DiagramPortAnchor.RelativePoint(x = 0.1, y = 1.2)),
            ),
            style = DiagramStyle(
                fill = DiagramColor(0x80336699u),
                stroke = DiagramColor(0xFFD8DEE9u),
                strokeWidth = 2.0,
                pattern = DiagramStrokePattern.DASHED,
                opacity = 0.8,
                cornerStyle = DiagramCornerStyle.ROUNDED,
                sketch = true,
                shadow = true,
            ),
            labels = listOf(DiagramLabel("draft"), DiagramLabel("**bold**", markdown = true)),
            parentId = parent.id,
            layerId = layer.id,
            locked = true,
            visible = false,
        )
        val graph = DiagramGraph(nodes = listOf(parent, full), layers = listOf(layer))
        assertRoundTrips(graph, "node common part")
    }

    @Test
    fun materializedGridPortsRoundTrip() {
        // When an edge pins to a draw.io connection-point grid point, the editor materializes
        // it as a real port: corners become RelativePoint anchors, side quarter-points a
        // SideOffset at 0.25/0.75. Both must survive emit → parse with identical ids + anchors.
        val ports = listOf(
            DiagramPort(DiagramPortId("top-left"), DiagramPortAnchor.RelativePoint(x = 0.0, y = 0.0)),
            DiagramPort(DiagramPortId("bottom-right"), DiagramPortAnchor.RelativePoint(x = 1.0, y = 1.0)),
            DiagramPort(DiagramPortId("top-q1"), DiagramPortAnchor.SideOffset(DiagramNodeSide.TOP, 0.25)),
            DiagramPort(DiagramPortId("right-q3"), DiagramPortAnchor.SideOffset(DiagramNodeSide.RIGHT, 0.75)),
        )
        val n = node("n", DiagramNodePayload.BasicShape()).copy(ports = ports)
        val graph = DiagramGraph(nodes = listOf(n))
        assertRoundTrips(graph, "materialized grid ports")

        // Explicit: the parsed node carries exactly the same ports (ids + anchors, order).
        val parsed = parseBody(DiagramCnlWriter.sentences(graph))
        assertEquals(ports, parsed.graph.nodes.single().ports, "grid ports must survive write-back")
    }

    // --- edges ---

    @Test
    fun everyRelationFormRoundTrips() {
        val relations: List<DiagramRelation> = listOf(
            DiagramRelation.Plain,
            DiagramRelation.Association(),
            DiagramRelation.Association(directed = true),
            DiagramRelation.Aggregation,
            DiagramRelation.Composition,
            DiagramRelation.Generalization,
            DiagramRelation.Dependency,
            DiagramRelation.Realization,
            DiagramRelation.Transition,
            DiagramRelation.Include,
            DiagramRelation.Extend,
            DiagramRelation.EntityRelation(),
            DiagramRelation.EntityRelation(ErCardinality.ZERO_OR_ONE, ErCardinality.ZERO_OR_MANY),
            DiagramRelation.EntityRelation(ErCardinality.ONE_OR_MANY, ErCardinality.MANY),
        ) + UmlMessageKind.entries.map { DiagramRelation.Message(it) }
        relations.forEachIndexed { index, relation ->
            val graph = DiagramGraph(
                nodes = listOf(node("a", DiagramNodePayload.BasicShape()), node("b", DiagramNodePayload.BasicShape())),
                edges = listOf(
                    DiagramEdge(
                        id = DiagramEdgeId("e$index"),
                        source = DiagramEndpoint.FloatingAnchor(DiagramNodeId("a")),
                        target = DiagramEndpoint.FloatingAnchor(DiagramNodeId("b")),
                        relation = relation,
                    ),
                ),
            )
            assertRoundTrips(graph, "relation[$index] $relation")
        }
    }

    @Test
    fun everyArrowheadKindRoundTrips() {
        DiagramArrowheadKind.entries.filter { it != DiagramArrowheadKind.NONE }.forEach { kind ->
            val graph = DiagramGraph(
                nodes = listOf(node("a", DiagramNodePayload.BasicShape()), node("b", DiagramNodePayload.BasicShape())),
                edges = listOf(
                    DiagramEdge(
                        id = DiagramEdgeId("e"),
                        source = DiagramEndpoint.FloatingAnchor(DiagramNodeId("a")),
                        target = DiagramEndpoint.FloatingAnchor(DiagramNodeId("b")),
                        sourceArrowhead = DiagramArrowhead(kind = kind, size = 12.0, inset = 2.0),
                        targetArrowhead = DiagramArrowhead(kind = kind),
                    ),
                ),
            )
            assertRoundTrips(graph, "arrowhead $kind")
        }
    }

    @Test
    fun endpointFormsAndEdgeExtrasRoundTrip() {
        val gateway = node("gateway", DiagramNodePayload.BasicShape()).copy(
            ports = listOf(DiagramPort(DiagramPortId("out"), DiagramPortAnchor.SideOffset(DiagramNodeSide.RIGHT))),
        )
        val service = node("service", DiagramNodePayload.BasicShape()).copy(
            ports = listOf(DiagramPort(DiagramPortId("in"), DiagramPortAnchor.SideOffset(DiagramNodeSide.LEFT))),
        )
        val dotted = node("svc.v2", DiagramNodePayload.BasicShape()).copy(
            ports = listOf(DiagramPort(DiagramPortId("out"), DiagramPortAnchor.SideOffset(DiagramNodeSide.TOP))),
        )
        val layer = DiagramLayer(id = DiagramLayerId("wiring"), name = "wiring")
        val edges = listOf(
            DiagramEdge(
                id = DiagramEdgeId("e_fixed"),
                source = DiagramEndpoint.FixedPort(DiagramNodeId("gateway"), DiagramPortId("out")),
                target = DiagramEndpoint.FixedPort(DiagramNodeId("service"), DiagramPortId("in")),
                routing = DiagramRoutingStyle.STRAIGHT,
                waypoints = listOf(DiagramPoint(420.0, 160.0), DiagramPoint(-15.5, 8.0)),
            ),
            DiagramEdge(
                id = DiagramEdgeId("e_free"),
                source = DiagramEndpoint.FreePoint(x = -40.0, y = 12.0),
                target = DiagramEndpoint.FloatingAnchor(DiagramNodeId("gateway")),
                style = DiagramStyle(pattern = DiagramStrokePattern.DOTTED, stroke = DiagramColor(0xFF99AABBu)),
                targetArrowhead = DiagramArrowhead(DiagramArrowheadKind.OPEN, size = 12.0, inset = 2.0),
            ),
            DiagramEdge(
                id = DiagramEdgeId("e_dotted"),
                source = DiagramEndpoint.FloatingAnchor(DiagramNodeId("svc.v2")),
                target = DiagramEndpoint.FixedPort(DiagramNodeId("svc.v2"), DiagramPortId("out")),
                labels = listOf(
                    DiagramEdgeLabel(label = DiagramLabel("self-loop"), position = DiagramEdgeLabelPosition.SOURCE),
                ),
            ),
            DiagramEdge(
                id = DiagramEdgeId("e_flow"),
                source = DiagramEndpoint.FloatingAnchor(DiagramNodeId("gateway")),
                target = DiagramEndpoint.FloatingAnchor(DiagramNodeId("service")),
                relation = DiagramRelation.Transition,
                lineJumps = LineJumpStyle.ARC,
                connectionMode = DiagramConnectionMode.LINK,
                flowAnimation = true,
                layerId = layer.id,
                routing = DiagramRoutingStyle.ENTITY_RELATION,
            ),
            DiagramEdge(
                id = DiagramEdgeId("e_labels"),
                source = DiagramEndpoint.FloatingAnchor(DiagramNodeId("gateway")),
                target = DiagramEndpoint.FloatingAnchor(DiagramNodeId("service")),
                labels = listOf(
                    DiagramEdgeLabel(label = DiagramLabel("mid"), offsetX = 4.0, offsetY = -6.0),
                    DiagramEdgeLabel(label = DiagramLabel("src", markdown = true), position = DiagramEdgeLabelPosition.SOURCE),
                    DiagramEdgeLabel(label = DiagramLabel("tgt"), position = DiagramEdgeLabelPosition.TARGET),
                ),
            ),
            DiagramEdge(
                id = DiagramEdgeId("e_short_label"),
                source = DiagramEndpoint.FloatingAnchor(DiagramNodeId("gateway")),
                target = DiagramEndpoint.FloatingAnchor(DiagramNodeId("service")),
                labels = listOf(DiagramEdgeLabel(label = DiagramLabel("owns"))),
            ),
        )
        val graph = DiagramGraph(
            nodes = listOf(gateway, service, dotted),
            edges = edges,
            layers = listOf(layer),
        )
        assertRoundTrips(graph, "endpoint forms + edge extras")
    }

    @Test
    fun fixedPortCollidingWithDottedNodeIdEmitsExplicitGroup() {
        // Node "a" declares port "b" AND a node literally named "a.b" exists: the bare
        // token would be ambiguous, so the emitter must fall back to `(node a port b)`.
        val a = node("a", DiagramNodePayload.BasicShape()).copy(
            ports = listOf(DiagramPort(DiagramPortId("b"), DiagramPortAnchor.SideOffset(DiagramNodeSide.RIGHT))),
        )
        val ab = node("a.b", DiagramNodePayload.BasicShape())
        val graph = DiagramGraph(
            nodes = listOf(a, ab),
            edges = listOf(
                DiagramEdge(
                    id = DiagramEdgeId("e"),
                    source = DiagramEndpoint.FixedPort(DiagramNodeId("a"), DiagramPortId("b")),
                    target = DiagramEndpoint.FloatingAnchor(DiagramNodeId("a.b")),
                ),
            ),
        )
        val emitted = DiagramCnlWriter.sentences(graph)
        assertTrue(
            emitted.any { "from (node a port b)" in it && "to (node a.b)" in it },
            "explicit endpoint groups expected in: $emitted",
        )
        assertRoundTrips(graph, "dotted collision")
    }

    // --- layers and groups ---

    @Test
    fun layersAndGroupsRoundTrip() {
        val graph = DiagramGraph(
            nodes = listOf(node("user", DiagramNodePayload.BasicShape()), node("base", DiagramNodePayload.BasicShape())),
            layers = listOf(
                DiagramLayer(id = DiagramLayerId("back"), name = "back"),
                DiagramLayer(id = DiagramLayerId("annotations"), name = "Review notes"),
                DiagramLayer(id = DiagramLayerId("wiring"), name = "Wiring", visible = false),
                DiagramLayer(id = DiagramLayerId("base_layer"), name = "base_layer", locked = true),
                DiagramLayer(id = DiagramLayerId("legacy"), name = "Old flow", visible = false, locked = true),
            ),
            groups = listOf(
                DiagramGroup(id = DiagramGroupId("g1"), memberIds = listOf(DiagramNodeId("user"), DiagramNodeId("base"))),
                DiagramGroup(id = DiagramGroupId("g2"), memberIds = listOf(DiagramNodeId("user")), name = "Engine cluster"),
            ),
        )
        assertRoundTrips(graph, "layers + groups")
    }

    @Test
    fun textEscapingRoundTrips() {
        val graph = DiagramGraph(
            nodes = listOf(
                node("n1", UmlNoteNode(text = "a \\ b » c\nline2\rline3")),
                node("n2", DiagramNodePayload.BasicShape()).copy(
                    labels = listOf(DiagramLabel("quote \" and «guillemets»")),
                ),
            ),
        )
        assertRoundTrips(graph, "escaping")
    }

    @Test
    fun idsNeedingQuotingRoundTrip() {
        val weird = node("weird id", DiagramNodePayload.BasicShape())
        val graph = DiagramGraph(
            nodes = listOf(weird, node("plain", DiagramNodePayload.BasicShape())),
            edges = listOf(
                DiagramEdge(
                    id = DiagramEdgeId("e (1)"),
                    source = DiagramEndpoint.FloatingAnchor(DiagramNodeId("weird id")),
                    target = DiagramEndpoint.FloatingAnchor(DiagramNodeId("plain")),
                ),
            ),
            groups = listOf(
                DiagramGroup(id = DiagramGroupId("g"), memberIds = listOf(DiagramNodeId("weird id"))),
            ),
        )
        assertRoundTrips(graph, "quoted ids")
    }

    @Test
    fun emptyGraphEmitsNoSentences() {
        assertEquals(emptyList(), DiagramCnlWriter.sentences(DiagramGraph.Empty))
        assertEquals(DiagramGraph.Empty, parseBody(emptyList()).graph)
    }
}
