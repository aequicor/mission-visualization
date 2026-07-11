package io.aequicor.visualization.subsystems.diagrams.slm

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
 * Write -> compile -> read must be the identity on the model: a graph exercising every
 * payload family, endpoint form, relation, style and edge property survives byte-exactly.
 */
class DiagramSlmRoundTripTest {

    private fun node(
        id: String,
        payload: DiagramNodePayload,
        x: Double = 0.0,
        y: Double = 0.0,
    ): DiagramNode = DiagramNode(
        id = DiagramNodeId(id),
        x = x,
        y = y,
        width = 120.0,
        height = 60.0,
        payload = payload,
    )

    private val fullGraph: DiagramGraph = DiagramGraph(
        layers = listOf(
            DiagramLayer(DiagramLayerId("back"), name = "Background"),
            DiagramLayer(DiagramLayerId("front"), name = "front", visible = false, locked = true),
        ),
        nodes = listOf(
            DiagramNode(
                id = DiagramNodeId("box"),
                x = 10.0,
                y = 20.0,
                width = 140.0,
                height = 80.0,
                rotation = 45.0,
                payload = DiagramNodePayload.BasicShape(DiagramShapeKind.ROUNDED_RECTANGLE),
                ports = listOf(
                    DiagramPort.side(DiagramNodeSide.RIGHT),
                    DiagramPort(
                        id = DiagramPortId("custom"),
                        anchor = DiagramPortAnchor.RelativePoint(x = 0.25, y = -0.1),
                    ),
                    DiagramPort(
                        id = DiagramPortId("low"),
                        anchor = DiagramPortAnchor.SideOffset(DiagramNodeSide.BOTTOM, offset = 0.75),
                    ),
                ),
                style = DiagramStyle(
                    fill = DiagramColor(0xFF3366AAu),
                    stroke = DiagramColor(0x80FF0000u),
                    strokeWidth = 2.5,
                    pattern = DiagramStrokePattern.DASHED,
                    opacity = 0.8,
                    cornerStyle = DiagramCornerStyle.ROUNDED,
                    sketch = true,
                    shadow = true,
                ),
                labels = listOf(
                    DiagramLabel("Box **bold**", markdown = true),
                    DiagramLabel("plain"),
                ),
                layerId = DiagramLayerId("back"),
                locked = true,
                visible = false,
            ),
            node("pool", DiagramNodePayload.SwimlaneNode(
                orientation = DiagramOrientation.VERTICAL,
                lanes = listOf(
                    SwimlaneLane(size = 140.0),
                    SwimlaneLane(title = DiagramLabel("Lane B"), size = 160.0),
                ),
                title = DiagramLabel("Pool"),
            ), x = 200.0),
            node("crate", DiagramNodePayload.ContainerNode(
                title = DiagramLabel("Crate"),
                collapsed = true,
            ), x = 400.0),
            DiagramNode(
                id = DiagramNodeId("inner"),
                x = 420.0,
                y = 20.0,
                width = 60.0,
                height = 30.0,
                parentId = DiagramNodeId("crate"),
            ),
            node("decide", DiagramNodePayload.FlowchartNode(FlowchartNodeKind.DECISION), y = 150.0),
            node("orders", DiagramNodePayload.ErEntityNode(
                name = "Order",
                attributes = listOf(
                    ErAttribute("id", type = "uuid", primaryKey = true),
                    ErAttribute("customerId", type = "uuid", foreignKey = true),
                    ErAttribute("note"),
                ),
            ), y = 250.0),
            node("gate", DiagramNodePayload.BpmnNode(BpmnNodeKind.GATEWAY), y = 350.0),
            node("grid", TableNode(
                rows = listOf(TableRow(height = 28.0, header = true), TableRow()),
                columns = listOf(TableColumn(width = 90.0), TableColumn(header = true)),
                cells = listOf(
                    TableCell(row = 0, column = 0, colSpan = 2, label = DiagramLabel("Head")),
                    TableCell(
                        row = 1,
                        column = 1,
                        label = DiagramLabel("Cell"),
                        style = DiagramStyle(fill = DiagramColor(0xFFEEEEEEu)),
                    ),
                ),
            ), y = 450.0),
            node("classy", UmlClassNode(
                name = "Classy",
                stereotype = "interface",
                abstract = true,
                attributes = listOf(
                    UmlMember("count: Int", visibility = UmlVisibility.PROTECTED, static = true),
                ),
                operations = listOf(
                    UmlMember("run(): Unit", visibility = UmlVisibility.PACKAGE),
                ),
            ), y = 550.0),
            node("life", UmlLifelineNode(
                name = "Service",
                actor = true,
                activations = listOf(UmlActivation(0.1, 0.4), UmlActivation(0.6, 0.9)),
            ), y = 650.0),
            node("st", UmlStateNode(name = "Waiting", kind = UmlStateKind.COMPOSITE), y = 750.0),
            node("act", UmlActivityNode(kind = UmlActivityKind.FORK, name = "Split"), y = 850.0),
            node("who", UmlActorNode("User"), y = 950.0),
            node("uc", UmlUseCaseNode("Login"), y = 1050.0),
            node("comp", UmlComponentNode("Engine", stereotype = "subsystem"), y = 1150.0),
            node("dep", UmlDeploymentNode("Server"), y = 1250.0),
            node("memo", UmlNoteNode("remember me"), y = 1350.0),
            node("pkg", UmlPackageNode("io.aequicor"), y = 1450.0),
        ),
        edges = listOf(
            DiagramEdge(
                id = DiagramEdgeId("wired"),
                source = DiagramEndpoint.FixedPort(DiagramNodeId("box"), DiagramPortId("right")),
                target = DiagramEndpoint.FloatingAnchor(DiagramNodeId("pool")),
                relation = DiagramRelation.Association(directed = true),
                routing = DiagramRoutingStyle.CURVED,
                waypoints = listOf(DiagramPoint(180.0, 40.0), DiagramPoint(190.0, 60.5)),
                style = DiagramStyle(stroke = DiagramColor(0xFF222222u), strokeWidth = 3.0),
                labels = listOf(
                    DiagramEdgeLabel(DiagramLabel("mid")),
                    DiagramEdgeLabel(
                        DiagramLabel("src", markdown = true),
                        position = DiagramEdgeLabelPosition.SOURCE,
                        offsetX = 4.0,
                        offsetY = -2.0,
                    ),
                    DiagramEdgeLabel(
                        DiagramLabel("dst"),
                        position = DiagramEdgeLabelPosition.TARGET,
                    ),
                ),
                sourceArrowhead = DiagramArrowhead(
                    kind = DiagramArrowheadKind.DIAMOND_FILLED,
                    size = 12.0,
                    inset = 2.0,
                ),
                targetArrowhead = DiagramArrowhead.Open,
                lineJumps = LineJumpStyle.ARC,
                connectionMode = DiagramConnectionMode.LINK,
                flowAnimation = true,
                layerId = DiagramLayerId("front"),
            ),
            DiagramEdge(
                id = DiagramEdgeId("free"),
                source = DiagramEndpoint.FreePoint(5.0, 6.0),
                target = DiagramEndpoint.FreePoint(50.0, 60.0),
                routing = DiagramRoutingStyle.STRAIGHT,
            ),
            DiagramEdge(
                id = DiagramEdgeId("msg"),
                source = DiagramEndpoint.FloatingAnchor(DiagramNodeId("life")),
                target = DiagramEndpoint.FloatingAnchor(DiagramNodeId("comp")),
                relation = DiagramRelation.Message(UmlMessageKind.ASYNC),
            ),
            DiagramEdge(
                id = DiagramEdgeId("rel"),
                source = DiagramEndpoint.FloatingAnchor(DiagramNodeId("orders")),
                target = DiagramEndpoint.FloatingAnchor(DiagramNodeId("grid")),
                relation = DiagramRelation.EntityRelation(
                    sourceCardinality = ErCardinality.ZERO_OR_ONE,
                    targetCardinality = ErCardinality.ZERO_OR_MANY,
                ),
                routing = DiagramRoutingStyle.ENTITY_RELATION,
            ),
        ),
        groups = listOf(
            DiagramGroup(
                id = DiagramGroupId("g1"),
                memberIds = listOf(DiagramNodeId("box"), DiagramNodeId("pool")),
                name = "Pair",
            ),
        ),
    )

    @Test
    fun fullFeatureGraphSurvivesWriteThenReadExactly() {
        val block = DiagramSlmExtension.write(fullGraph)
        val result = compileWithDiagrams(slmDocument(block))
        assertTrue(
            result.diagnostics.none { it.severity == DesignSeverity.Error },
            "unexpected errors: ${result.diagnostics}",
        )
        assertEquals(fullGraph, result.diagramGraphOf("canvas"))
    }

    @Test
    fun writerOutputIsStableUnderReEmission() {
        val block = DiagramSlmExtension.write(fullGraph)
        val graph = compileWithDiagrams(slmDocument(block)).diagramGraphOf("canvas")
        assertEquals(block, DiagramSlmExtension.write(graph))
    }

    @Test
    fun diagramNodeGetsIntrinsicSizeFromGraphInLayout() {
        // Smoke: the IR layout picks the graph bbox up for a hug-sized diagram node.
        val block = DiagramSlmExtension.write(
            DiagramGraph(
                nodes = listOf(
                    DiagramNode(
                        id = DiagramNodeId("a"),
                        x = 0.0,
                        y = 0.0,
                        width = 300.0,
                        height = 200.0,
                    ),
                ),
            ),
        )
        val result = compileWithDiagrams(slmDocument(block))
        val graph = result.diagramGraphOf("canvas")
        assertEquals(300.0, graph.nodes.single().width)
    }
}
