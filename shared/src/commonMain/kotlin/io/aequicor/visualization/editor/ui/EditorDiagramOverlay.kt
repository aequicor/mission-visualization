package io.aequicor.visualization.editor.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.isAltPressed
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.isTertiaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.aequicor.visualization.MissionEditorStateHolder
import io.aequicor.visualization.editor.presentation.DesignEditorIntent
import io.aequicor.visualization.editor.presentation.DiagramEditorIntent
import io.aequicor.visualization.editor.presentation.DiagramSelection
import io.aequicor.visualization.editor.presentation.DiagramTool
import io.aequicor.visualization.editor.presentation.zoomFactorForScroll
import io.aequicor.visualization.editor.platform.canvasWheelPanAxes
import io.aequicor.visualization.editor.platform.canvasWheelZoomAxis
import io.aequicor.visualization.editor.platform.platformCanvasWheelPanPixels
import io.aequicor.visualization.editor.platform.platformCanvasWheelZoomUnits
import io.aequicor.visualization.editor.ui.theme.LocalEditorColors
import io.aequicor.visualization.editor.ui.strings.LocalStrings
import io.aequicor.visualization.engine.backend.compose.CanvasViewport
import io.aequicor.visualization.engine.ir.layout.LayoutBox
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.subsystems.anchoring.AnchorGuide
import io.aequicor.visualization.subsystems.anchoring.MoveSnapState
import io.aequicor.visualization.subsystems.anchoring.SnapBox
import io.aequicor.visualization.subsystems.anchoring.SpacingBar
import io.aequicor.visualization.subsystems.anchoring.solveMoveSnap
import io.aequicor.visualization.subsystems.diagrams.compose.DIAGRAM_DETAIL_FONT_SIZE
import io.aequicor.visualization.subsystems.diagrams.compose.DIAGRAM_LABEL_FONT_SIZE
import io.aequicor.visualization.subsystems.diagrams.compose.DiagramConnectTargetOverlay
import io.aequicor.visualization.subsystems.diagrams.compose.DiagramDirectionalArrowsOverlay
import io.aequicor.visualization.subsystems.diagrams.compose.DiagramOverlayStyle
import io.aequicor.visualization.subsystems.diagrams.compose.DiagramPortsOverlay
import io.aequicor.visualization.subsystems.diagrams.compose.DiagramPreviewStyle
import io.aequicor.visualization.subsystems.diagrams.compose.DiagramSelectionOverlay
import io.aequicor.visualization.subsystems.diagrams.compose.DiagramWaypointOverlay
import io.aequicor.visualization.subsystems.diagrams.compose.rememberDiagramRoutes
import io.aequicor.visualization.subsystems.diagrams.geometry.containsPoint
import io.aequicor.visualization.subsystems.diagrams.geometry.labelBox
import io.aequicor.visualization.subsystems.diagrams.geometry.labelPadding
import io.aequicor.visualization.subsystems.diagrams.geometry.anchorPoint
import io.aequicor.visualization.subsystems.diagrams.geometry.intersectsOutline
import io.aequicor.visualization.subsystems.diagrams.hittest.ConnectTarget
import io.aequicor.visualization.subsystems.diagrams.hittest.connectionPorts
import io.aequicor.visualization.subsystems.diagrams.hittest.DiagramHit
import io.aequicor.visualization.subsystems.diagrams.hittest.DiagramNodeHitPart
import io.aequicor.visualization.subsystems.diagrams.hittest.DiagramResizeHandle
import io.aequicor.visualization.subsystems.diagrams.hittest.edgeLabelAnchorPoint
import io.aequicor.visualization.subsystems.diagrams.hittest.edgeLabelAvoidRects
import io.aequicor.visualization.subsystems.diagrams.hittest.edgeLabelObstacleRoutes
import io.aequicor.visualization.subsystems.diagrams.hittest.hitTest
import io.aequicor.visualization.subsystems.diagrams.hittest.pointAlongPolyline
import io.aequicor.visualization.subsystems.diagrams.hittest.resolveConnectTarget
import io.aequicor.visualization.subsystems.diagrams.hittest.resolveEndpointPoint
import io.aequicor.visualization.subsystems.diagrams.model.BpmnNodeKind
import io.aequicor.visualization.subsystems.diagrams.model.DiagramEdge
import io.aequicor.visualization.subsystems.diagrams.model.DiagramEdgeId
import io.aequicor.visualization.subsystems.diagrams.model.DiagramEdgeLabelPosition
import io.aequicor.visualization.subsystems.diagrams.model.DiagramArrowhead
import io.aequicor.visualization.subsystems.diagrams.model.DiagramArrowheadKind
import io.aequicor.visualization.subsystems.diagrams.model.DiagramEndpoint
import io.aequicor.visualization.subsystems.diagrams.model.attachedNodeId
import io.aequicor.visualization.subsystems.diagrams.model.DiagramGraph
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNode
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodeId
import io.aequicor.visualization.subsystems.diagrams.model.DiagramPort
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodePayload
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodeSizing
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodeSide
import io.aequicor.visualization.subsystems.diagrams.model.DiagramRelation
import io.aequicor.visualization.subsystems.diagrams.model.DiagramRoutingStyle
import io.aequicor.visualization.subsystems.diagrams.model.withEdge
import io.aequicor.visualization.subsystems.diagrams.model.DiagramShapeKind
import io.aequicor.visualization.subsystems.diagrams.model.ErAttribute
import io.aequicor.visualization.subsystems.diagrams.model.FlowchartNodeKind
import io.aequicor.visualization.subsystems.diagrams.model.SwimlaneLane
import io.aequicor.visualization.subsystems.diagrams.model.TableColumn
import io.aequicor.visualization.subsystems.diagrams.model.TableNode
import io.aequicor.visualization.subsystems.diagrams.model.TableRow
import io.aequicor.visualization.subsystems.diagrams.model.UmlActivityKind
import io.aequicor.visualization.subsystems.diagrams.model.UmlActivityNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlActorNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlClassNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlComponentNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlDeploymentNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlLifelineNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlNoteNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlPackageNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlStateNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlUseCaseNode
import io.aequicor.visualization.subsystems.diagrams.ops.DiagramEdgeEnd
import io.aequicor.visualization.subsystems.diagrams.ops.addCustomPort
import io.aequicor.visualization.subsystems.diagrams.ops.directionalCloneOffset
import io.aequicor.visualization.subsystems.diagrams.ops.DiagramNodeDefaults
import io.aequicor.visualization.subsystems.diagrams.text.DiagramTextMeasurer
import io.aequicor.visualization.subsystems.diagrams.compose.ComposeDiagramTextMeasurer
import io.aequicor.visualization.subsystems.typography.compose.ComposeTypographyMeasurer
import io.aequicor.visualization.subsystems.typography.compose.rememberBundledFontProvider
import androidx.compose.ui.text.rememberTextMeasurer
import io.aequicor.visualization.subsystems.diagrams.ops.fitNodeToText
import io.aequicor.visualization.subsystems.diagrams.ops.setNodeText
import io.aequicor.visualization.subsystems.diagrams.ops.primaryText
import io.aequicor.visualization.subsystems.diagrams.path.DiagramPoint
import io.aequicor.visualization.subsystems.diagrams.path.DiagramRect
import io.aequicor.visualization.subsystems.diagrams.routing.RoutingOptions
import io.aequicor.visualization.subsystems.diagrams.routing.routeAllEdgesLenient
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.roundToInt
import kotlin.time.TimeSource

// --- Node-type palette (shared by the toolbar flyout and the inspector) -------

/** One row of the diagram node-type palette: display label + representative payload + stamp size. */
internal data class DiagramPaletteEntry(
    val label: String,
    val payload: DiagramNodePayload,
) {
    /**
     * Stamp size, from the one table shared with the templates and hug's floor. The palette
     * used to keep its own numbers, so a use case started at 150x70 here but 160x70 from a
     * template.
     */
    val width: Double get() = DiagramNodeDefaults.defaultSizeFor(payload).width
    val height: Double get() = DiagramNodeDefaults.defaultSizeFor(payload).height
}

/**
 * The node-type palette: basic shapes, table, container/swimlane, the full UML set,
 * flowchart, ER and BPMN primitives. Order groups families for the flyout menu.
 */
internal val DiagramNodePalette: List<DiagramPaletteEntry> = listOf(
    DiagramPaletteEntry("Rectangle", DiagramNodePayload.BasicShape(DiagramShapeKind.RECTANGLE)),
    DiagramPaletteEntry("Rounded rectangle", DiagramNodePayload.BasicShape(DiagramShapeKind.ROUNDED_RECTANGLE)),
    DiagramPaletteEntry("Ellipse", DiagramNodePayload.BasicShape(DiagramShapeKind.ELLIPSE)),
    DiagramPaletteEntry("Text", DiagramNodePayload.BasicShape(DiagramShapeKind.TEXT)),
    DiagramPaletteEntry("Rhombus", DiagramNodePayload.BasicShape(DiagramShapeKind.RHOMBUS)),
    DiagramPaletteEntry("Triangle", DiagramNodePayload.BasicShape(DiagramShapeKind.TRIANGLE)),
    DiagramPaletteEntry("Hexagon", DiagramNodePayload.BasicShape(DiagramShapeKind.HEXAGON)),
    DiagramPaletteEntry("Parallelogram", DiagramNodePayload.BasicShape(DiagramShapeKind.PARALLELOGRAM)),
    DiagramPaletteEntry("Trapezoid", DiagramNodePayload.BasicShape(DiagramShapeKind.TRAPEZOID)),
    DiagramPaletteEntry("Cylinder", DiagramNodePayload.BasicShape(DiagramShapeKind.CYLINDER)),
    DiagramPaletteEntry("Cloud", DiagramNodePayload.BasicShape(DiagramShapeKind.CLOUD)),
    DiagramPaletteEntry(
        "Table",
        TableNode(rows = List(3) { TableRow() }, columns = List(3) { TableColumn() })
    ),
    DiagramPaletteEntry("Container", DiagramNodePayload.ContainerNode()),
    DiagramPaletteEntry(
        "Swimlane",
        DiagramNodePayload.SwimlaneNode(lanes = listOf(SwimlaneLane(), SwimlaneLane()))
    ),
    DiagramPaletteEntry("UML class", UmlClassNode(name = "Class")),
    DiagramPaletteEntry("UML lifeline", UmlLifelineNode(name = "Object")),
    DiagramPaletteEntry("UML state", UmlStateNode(name = "State")),
    DiagramPaletteEntry("UML activity", UmlActivityNode(UmlActivityKind.ACTION, name = "Action")),
    DiagramPaletteEntry("UML actor", UmlActorNode(name = "Actor")),
    DiagramPaletteEntry("UML use case", UmlUseCaseNode(name = "Use case")),
    DiagramPaletteEntry("UML component", UmlComponentNode(name = "Component")),
    DiagramPaletteEntry("UML deployment", UmlDeploymentNode(name = "Node")),
    DiagramPaletteEntry("UML note", UmlNoteNode(text = "Note")),
    DiagramPaletteEntry("UML package", UmlPackageNode(name = "Package")),
    DiagramPaletteEntry("Flowchart process", DiagramNodePayload.FlowchartNode(FlowchartNodeKind.PROCESS)),
    DiagramPaletteEntry("Flowchart decision", DiagramNodePayload.FlowchartNode(FlowchartNodeKind.DECISION)),
    DiagramPaletteEntry("Flowchart input/output", DiagramNodePayload.FlowchartNode(FlowchartNodeKind.INPUT_OUTPUT)),
    DiagramPaletteEntry("Flowchart terminator", DiagramNodePayload.FlowchartNode(FlowchartNodeKind.TERMINATOR)),
    DiagramPaletteEntry(
        "ER entity",
        DiagramNodePayload.ErEntityNode(name = "Entity", attributes = listOf(ErAttribute("id", "int", primaryKey = true)))
    ),
    DiagramPaletteEntry("BPMN task", DiagramNodePayload.BpmnNode(BpmnNodeKind.TASK)),
    DiagramPaletteEntry("BPMN event", DiagramNodePayload.BpmnNode(BpmnNodeKind.EVENT)),
    DiagramPaletteEntry("BPMN gateway", DiagramNodePayload.BpmnNode(BpmnNodeKind.GATEWAY)),
)

/** The palette label a payload instance falls under (drives the inspector type dropdown). */
internal fun diagramPayloadTypeLabel(payload: DiagramNodePayload): String = when (payload) {
    is DiagramNodePayload.BasicShape -> when (payload.shape) {
        DiagramShapeKind.RECTANGLE -> "Rectangle"
        DiagramShapeKind.ROUNDED_RECTANGLE -> "Rounded rectangle"
        DiagramShapeKind.ELLIPSE -> "Ellipse"
        DiagramShapeKind.TEXT -> "Text"
        DiagramShapeKind.RHOMBUS -> "Rhombus"
        DiagramShapeKind.TRIANGLE -> "Triangle"
        DiagramShapeKind.HEXAGON -> "Hexagon"
        DiagramShapeKind.PARALLELOGRAM -> "Parallelogram"
        DiagramShapeKind.TRAPEZOID -> "Trapezoid"
        DiagramShapeKind.CYLINDER -> "Cylinder"
        DiagramShapeKind.CLOUD -> "Cloud"
    }
    is TableNode -> "Table"
    is DiagramNodePayload.ContainerNode -> "Container"
    is DiagramNodePayload.SwimlaneNode -> "Swimlane"
    is UmlClassNode -> "UML class"
    is UmlLifelineNode -> "UML lifeline"
    is UmlStateNode -> "UML state"
    is UmlActivityNode -> "UML activity"
    is UmlActorNode -> "UML actor"
    is UmlUseCaseNode -> "UML use case"
    is UmlComponentNode -> "UML component"
    is UmlDeploymentNode -> "UML deployment"
    is UmlNoteNode -> "UML note"
    is UmlPackageNode -> "UML package"
    is DiagramNodePayload.FlowchartNode -> when (payload.kind) {
        FlowchartNodeKind.PROCESS -> "Flowchart process"
        FlowchartNodeKind.DECISION -> "Flowchart decision"
        FlowchartNodeKind.INPUT_OUTPUT -> "Flowchart input/output"
        FlowchartNodeKind.TERMINATOR -> "Flowchart terminator"
    }
    is DiagramNodePayload.ErEntityNode -> "ER entity"
    is DiagramNodePayload.BpmnNode -> when (payload.kind) {
        BpmnNodeKind.TASK -> "BPMN task"
        BpmnNodeKind.EVENT -> "BPMN event"
        BpmnNodeKind.GATEWAY -> "BPMN gateway"
    }
}

/** Theme bridge for the diagrams-compose mini previews (same convention as figures). */
@Composable
internal fun editorDiagramPreviewStyle(): DiagramPreviewStyle {
    val colors = LocalEditorColors.current
    return DiagramPreviewStyle(
        ink = colors.controlInk,
        fill = colors.selectionFill,
        accent = colors.accent,
        surface = colors.raisedSurface,
    )
}

// --- Overlay ------------------------------------------------------------------

/** Screen-space pick radius for diagram element hits. */
private const val DiagramHitRadiusPx = 8f

/** Precise screen-space radius that turns a connection point green and makes it actionable. */
private const val DiagramPortHitRadiusPx = 4f

/** Screen-space distance of the hover directional chevrons from the node bounds. */
private const val DiagramArrowDistance = 14f

/** What an in-flight edge-draw drag looks like (graph coordinates). */
/** In-flight edge-draw preview: the routed (bent) polyline the finished connector will take. */
private data class DiagramEdgePreview(val points: List<DiagramPoint>)

private enum class DiagramEdgeToolbarMenu { SOURCE, TARGET, ROUTING }

private val DiagramEdgeToolbarWidth = 106.dp
private val DiagramEdgeToolbarHeight = 36.dp
private val DiagramEdgeToolbarButtonSize = 34.dp
private const val DiagramEdgeToolbarMarginPx = 8f
private const val DiagramEdgeToolbarGapPx = 12f

/** Target of the inline diagram text editor. */
private sealed interface DiagramTextEditTarget {
    data class NodeLabel(val elementId: String) : DiagramTextEditTarget

    data class TableCell(val elementId: String, val row: Int, val column: Int) : DiagramTextEditTarget

    data class EdgeLabel(val edgeId: String, val position: DiagramEdgeLabelPosition) : DiagramTextEditTarget
}

/**
 * Diagram edit mode overlay: while [io.aequicor.visualization.editor.presentation.EditorWorkspaceState.diagramEditNodeId]
 * targets a diagram IR node, this layer owns every gesture inside the node's box —
 * click/Ctrl-Cmd-Shift-click element selection via the core hit-test, node drag-move and
 * handle-resize, hover directional arrows that drag out new floating edges (Alt pins the
 * end to the nearest declared port), edge-segment drags that mint waypoints, waypoint and
 * edge-label drags, and double-click inline text editing of labels/cells. Every mutation
 * dispatches a [DiagramEditorIntent] (write-back reducer); the overlay itself never touches
 * the document. Chrome is the diagrams-compose overlay set, mapped doc→screen through a
 * graphics layer so drawn geometry and hit geometry always agree.
 */
@Composable
internal fun DiagramEditOverlay(
    state: MissionEditorStateHolder,
    layout: LayoutBox?,
    viewport: CanvasViewport,
    zoomPx: Float,
    panActive: Boolean = false,
    onCanvasFocus: () -> Unit = {},
) {
    val colors = LocalEditorColors.current
    val editId = state.workspace.diagramEditNodeId
    if (editId.isBlank()) return
    val node = state.designState.document?.nodeById(editId) ?: return
    val kind = node.kind as? DesignNodeKind.Diagram ?: return
    val box = layout?.findBySourceId(editId) ?: return
    val graph = kind.graph
    val routes = rememberDiagramRoutes(graph)
    val latestViewport by rememberUpdatedState(viewport)
    val latestZoomPx by rememberUpdatedState(zoomPx)
    val latestPanActive by rememberUpdatedState(panActive)
    val density = androidx.compose.ui.platform.LocalDensity.current.density

    val selection = state.workspace.diagramSelection
    val selectedNodeIds = selection.elementIds.map(::DiagramNodeId).toSet()
    val selectedEdgeIds = selection.edgeIds.map(::DiagramEdgeId).toSet()

    var hoveredElementId by remember(editId) { mutableStateOf<String?>(null) }
    var hoveredPort by remember(editId) { mutableStateOf<DiagramHit.Port?>(null) }
    var edgePreview by remember(editId) { mutableStateOf<DiagramEdgePreview?>(null) }
    var connectTarget by remember(editId) { mutableStateOf<ConnectTarget?>(null) }
    var marquee by remember(editId) { mutableStateOf<DiagramRect?>(null) }
    var snapGuides by remember(editId) { mutableStateOf<List<AnchorGuide>>(emptyList()) }
    var snapSpacing by remember(editId) { mutableStateOf<List<SpacingBar>>(emptyList()) }
    var textEdit by remember(editId) { mutableStateOf<DiagramTextEditTarget?>(null) }
    // Commit hook published by the open inline editor so a press elsewhere commits the draft
    // instead of discarding it (draw.io: invokesStopCellEditing = true — blur commits).
    val inlineTextCommit = remember(editId) { mutableStateOf<(() -> Unit)?>(null) }
    var lastTapMark by remember(editId) { mutableStateOf<TimeSource.Monotonic.ValueTimeMark?>(null) }
    var lastTapKey by remember(editId) { mutableStateOf("") }

    // F2 / begin-rename: the key handler sets diagramTextEditRequest; open the inline editor for
    // that element and consume the request.
    val textEditRequest = state.workspace.diagramTextEditRequest
    LaunchedEffect(textEditRequest) {
        if (textEditRequest != null) {
            textEdit = DiagramTextEditTarget.NodeLabel(textEditRequest)
            state.updateWorkspace { it.copy(diagramTextEditRequest = null) }
        }
    }

    // Mirror the inline editor's presence into workspace state so the canvas preview key
    // handler stands down (it sees Enter/Escape before the text field and would otherwise
    // leave edit mode, dropping the draft). Cleared on dispose so the flag can't go stale
    // when edit mode ends with the editor still open.
    val inlineTextEditing = textEdit != null
    LaunchedEffect(inlineTextEditing) {
        if (state.workspace.diagramTextEditing != inlineTextEditing) {
            state.updateWorkspace { it.copy(diagramTextEditing = inlineTextEditing) }
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            if (state.workspace.diagramTextEditing) {
                state.updateWorkspace { it.copy(diagramTextEditing = false) }
            }
        }
    }

    fun liveGraph(): DiagramGraph? =
        (state.designState.document?.nodeById(editId)?.kind as? DesignNodeKind.Diagram)?.graph

    // Reused diagrams-compose chrome, drawn in graph coordinates and mapped to the screen
    // with the same doc→screen transform the artboard uses (graph origin = box top-left).
    val origin = viewport.toScreen(box.x, box.y)
    val overlayStyle = DiagramOverlayStyle(
        accent = colors.accent,
        handleFill = colors.raisedSurface,
        handleStroke = colors.accent,
        floatingIndicator = colors.accent,
        fixedIndicator = colors.statusPositive,
    )
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                transformOrigin = TransformOrigin(0f, 0f)
                scaleX = zoomPx
                scaleY = zoomPx
                translationX = origin.x
                translationY = origin.y
            },
    ) {
        val chromeScale = 1f / zoomPx.coerceAtLeast(0.01f)
        DiagramSelectionOverlay(
            graph = graph,
            selectedNodeIds = selectedNodeIds,
            modifier = Modifier.fillMaxSize(),
            style = overlayStyle,
            chromeScale = chromeScale,
        )
        val hoveredConnectionNodeId = hoveredElementId
            ?.let(::DiagramNodeId)
            ?.takeUnless { it in selectedNodeIds }
        val portNodeIds = setOfNotNull(hoveredConnectionNodeId)
        DiagramPortsOverlay(
            graph = graph,
            nodeIds = portNodeIds,
            modifier = Modifier.fillMaxSize(),
            style = overlayStyle,
            highlightedNodeId = hoveredPort?.nodeId?.takeIf { it == hoveredConnectionNodeId },
            highlightedPortId = hoveredPort
                ?.takeIf { it.nodeId == hoveredConnectionNodeId }
                ?.portId,
            chromeScale = chromeScale,
        )
        DiagramWaypointOverlay(
            graph,
            selectedEdgeIds,
            routes,
            Modifier.fillMaxSize(),
            overlayStyle,
            chromeScale,
        )
        DiagramDirectionalArrowsOverlay(
            graph = graph,
            nodeId = hoveredConnectionNodeId,
            modifier = Modifier.fillMaxSize(),
            style = overlayStyle,
            distance = DiagramArrowDistance,
            chromeScale = chromeScale,
        )
        DiagramConnectTargetOverlay(
            graph,
            connectTarget,
            Modifier.fillMaxSize(),
            overlayStyle,
            chromeScale,
        )
        // Alignment guides + equal-spacing bars from the snap solver (graph coords; strokes scaled
        // by 1/zoom so they stay ~constant on screen inside the graph→screen graphics layer).
        if (snapGuides.isNotEmpty() || snapSpacing.isNotEmpty()) {
            Canvas(Modifier.fillMaxSize()) {
                val guideStroke = 1.2f / zoomPx
                val dash = PathEffect.dashPathEffect(floatArrayOf(4f / zoomPx, 3f / zoomPx))
                snapGuides.forEach { guide ->
                    val l = guide.line
                    drawLine(
                        color = colors.statusDanger,
                        start = Offset(l.x1.toFloat(), l.y1.toFloat()),
                        end = Offset(l.x2.toFloat(), l.y2.toFloat()),
                        strokeWidth = guideStroke,
                        pathEffect = dash,
                    )
                }
                snapSpacing.forEach { bar ->
                    val s = bar.segment
                    drawLine(
                        color = colors.statusDanger,
                        start = Offset(s.x1.toFloat(), s.y1.toFloat()),
                        end = Offset(s.x2.toFloat(), s.y2.toFloat()),
                        strokeWidth = guideStroke,
                    )
                }
            }
        }
    }

    // Gestures + screen-space extras (edit-mode frame, edge-draw preview line).
    Canvas(
        Modifier
            .fillMaxSize()
            .pointerInput(editId) {
                // Hover tracking for the directional arrows / port hints. This overlay sits ON TOP
                // of the canvas gesture surface (a sibling, not a child), so pointer events over it
                // never reach the canvas pane's handlers — viewport scroll gestures (wheel pan,
                // ctrl/meta-wheel zoom) must be replayed here or zoom/pan dies in edit mode.
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        when (event.type) {
                            PointerEventType.Scroll -> {
                                val change = event.changes.firstOrNull() ?: continue
                                val modifiers = event.keyboardModifiers
                                if (state.workspace.pendingZoomTo != null) {
                                    state.updateWorkspace { it.copy(pendingZoomTo = null) }
                                }
                                if (modifiers.isCtrlPressed || modifiers.isMetaPressed) {
                                    val factor = zoomFactorForScroll(
                                        platformCanvasWheelZoomUnits(
                                            -canvasWheelZoomAxis(change.scrollDelta.x, change.scrollDelta.y),
                                        ),
                                    )
                                    if (factor != 1f) {
                                        state.updateWorkspace {
                                            it.copy(
                                                viewport = it.viewport.zoomAround(
                                                    change.position.x,
                                                    change.position.y,
                                                    factor,
                                                    density,
                                                ),
                                            )
                                        }
                                    }
                                } else {
                                    val scroll = canvasWheelPanAxes(
                                        change.scrollDelta.x,
                                        change.scrollDelta.y,
                                        modifiers.isShiftPressed,
                                    )
                                    val panX = platformCanvasWheelPanPixels(-scroll.x, density)
                                    val panY = platformCanvasWheelPanPixels(-scroll.y, density)
                                    state.updateWorkspace {
                                        it.copy(viewport = it.viewport.panByScreenDelta(panX, panY, density))
                                    }
                                }
                                change.consume()
                            }
                            PointerEventType.Move -> {
                                val change = event.changes.firstOrNull() ?: continue
                                if (!change.pressed) {
                                    val liveBox = state.artboardLayout?.findBySourceId(editId)
                                    val live = liveGraph()
                                    if (liveBox != null && live != null) {
                                        val point = DiagramPoint(
                                            latestViewport.toDocX(change.position.x) - liveBox.x,
                                            latestViewport.toDocY(change.position.y) - liveBox.y,
                                        )
                                        val safeZoom = latestZoomPx.coerceAtLeast(0.01f)
                                        val portTolerance = (DiagramPortHitRadiusPx / safeZoom)
                                            .toDouble()
                                            .coerceAtLeast(1.0)
                                        val selectedNow = state.workspace.diagramSelection.elementIds
                                            .map(::DiagramNodeId)
                                            .toSet()
                                        hoveredPort = diagramConnectionPortForSelection(
                                            hoverDiagramPortAt(live, point, portTolerance),
                                            selectedNow,
                                        )
                                        hoveredElementId = (
                                            hoveredPort?.nodeId ?: hoverDiagramNodeAt(
                                                live,
                                                point,
                                                ((DiagramArrowDistance + DiagramHitRadiusPx) / safeZoom).toDouble(),
                                            )
                                        )?.value
                                    } else {
                                        hoveredPort = null
                                        hoveredElementId = null
                                    }
                                }
                            }
                            PointerEventType.Exit -> {
                                hoveredPort = null
                                hoveredElementId = null
                            }
                            else -> Unit
                        }
                    }
                }
            }
            .pointerInput(editId) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    // The overlay consumes this press, so the canvas pane's own press handler —
                    // the one that moves keyboard focus onto the canvas — never sees it. Without
                    // this hand-off every Delete/Cmd+Z after a diagram click lands in whatever
                    // text field held focus last (the inspector label, the source editor).
                    if (!state.workspace.diagramTextEditing) onCanvasFocus()
                    // Pan gestures (space-drag / middle-button) belong to the canvas viewport, not
                    // the diagram. The canvas pane's pan handler never sees this press (the overlay
                    // covers it as a top sibling), so the overlay drives the same viewport pan here.
                    if (latestPanActive || currentEvent.buttons.isTertiaryPressed) {
                        down.consume()
                        var last = down.position
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            if (change.changedToUp()) {
                                change.consume()
                                break
                            }
                            val delta = change.position - last
                            last = change.position
                            if (delta != Offset.Zero) {
                                state.updateWorkspace {
                                    it.copy(viewport = it.viewport.panByScreenDelta(delta.x, delta.y, density))
                                }
                            }
                            change.consume()
                        }
                        return@awaitEachGesture
                    }
                    val live = liveGraph() ?: return@awaitEachGesture
                    val liveBox = state.artboardLayout?.findBySourceId(editId) ?: return@awaitEachGesture
                    val zoom = latestZoomPx
                    if (zoom <= 0f) return@awaitEachGesture
                    val press = down.position
                    val docX = latestViewport.toDocX(press.x)
                    val docY = latestViewport.toDocY(press.y)
                    val point = DiagramPoint(docX - liveBox.x, docY - liveBox.y)
                    val modifiers = currentEvent.keyboardModifiers
                    val shiftHeld = modifiers.isShiftPressed
                    val additiveSelectionHeld = shiftHeld || modifiers.isCtrlPressed || modifiers.isMetaPressed

                    fun graphPointOf(position: Offset): DiagramPoint = DiagramPoint(
                        latestViewport.toDocX(position.x) - liveBox.x,
                        latestViewport.toDocY(position.y) - liveBox.y,
                    )

                    // Presses outside the diagram box (plus the arrow margin) leave edit mode. If the
                    // press lands on ANOTHER diagram's element, switch straight to it and select that
                    // element in one click (unified selection — no intermediate "select the whole other
                    // diagram" step). Anything else stays unconsumed so the main canvas handler selects
                    // the plain node / whole diagram / empty target normally.
                    val margin = ((DiagramArrowDistance + 10f) / zoom).toDouble()
                    val insideBox = docX >= liveBox.x - margin && docX <= liveBox.right + margin &&
                        docY >= liveBox.y - margin && docY <= liveBox.bottom + margin
                    if (!insideBox) {
                        inlineTextCommit.value?.invoke()
                        textEdit = null
                        val switch = if (additiveSelectionHeld) {
                            null
                        } else {
                            resolveDiagramElementSelection(
                                state.artboardLayout, state.designState.document, docX, docY, zoom,
                            )?.takeIf { it.diagramId != editId }
                        }
                        if (switch != null) {
                            state.dispatch(DesignEditorIntent.SelectNode(switch.diagramId))
                            state.updateWorkspace {
                                it.copy(
                                    diagramEditNodeId = switch.diagramId,
                                    diagramTool = DiagramTool.Select,
                                    diagramSelection = switch.selection,
                                )
                            }
                            down.consume()
                            return@awaitEachGesture
                        }
                        state.updateWorkspace {
                            it.copy(
                                diagramEditNodeId = "",
                                diagramTool = DiagramTool.Select,
                                diagramSelection = DiagramSelection.Empty,
                            )
                        }
                        return@awaitEachGesture
                    }
                    down.consume() // diagram edit mode owns the press; never move the IR node
                    inlineTextCommit.value?.invoke()
                    textEdit = null

                    // Add-node tool stamps a new element centered on the press point.
                    val tool = state.workspace.diagramTool
                    if (tool is DiagramTool.AddNode) {
                        val entry = DiagramNodePalette.firstOrNull { it.payload == tool.payload }
                        val width = entry?.width ?: 120.0
                        val height = entry?.height ?: 60.0
                        val elementId = mintDiagramId(live, "node")
                        state.dispatch(
                            DiagramEditorIntent.AddDiagramNode(
                                nodeId = editId,
                                elementId = elementId,
                                payload = tool.payload,
                                x = snapToDiagramGrid(point.x - width / 2),
                                y = snapToDiagramGrid(point.y - height / 2),
                                width = width,
                                height = height,
                            ),
                        )
                        state.updateWorkspace {
                            it.copy(
                                diagramTool = DiagramTool.Select,
                                diagramSelection = DiagramSelection(elementIds = setOf(elementId)),
                            )
                        }
                        return@awaitEachGesture
                    }

                    val tolerance = (DiagramHitRadiusPx / zoom).toDouble().coerceAtLeast(2.0)
                    val portTolerance = (DiagramPortHitRadiusPx / zoom).toDouble().coerceAtLeast(1.0)
                    val selectedNodesAtPress = state.workspace.diagramSelection.elementIds
                        .map(::DiagramNodeId)
                        .toSet()

                    // A press on a hovered node's directional arrow: dragging draws a new edge;
                    // a click (no drag past slop) clones the node in that direction, connected.
                    val hoverNode = hoveredElementId
                        ?.let(::DiagramNodeId)
                        ?.takeUnless { it in selectedNodesAtPress }
                        ?.let(live::nodeById)
                    val arrowSide = hoverNode?.let {
                        directionalArrowSideHit(
                            it,
                            point,
                            (DiagramArrowDistance / zoom).toDouble(),
                            tolerance + (4f / zoom).toDouble(),
                        )
                    }
                    if (hoverNode != null && arrowSide != null) {
                        dragNewDiagramEdge(
                            state = state,
                            editId = editId,
                            source = DiagramEndpoint.FloatingAnchor(hoverNode.id),
                            sourceNodeId = hoverNode.id,
                            down = down,
                            start = point,
                            relation = DiagramRelation.Plain,
                            graphPointOf = ::graphPointOf,
                            liveGraph = ::liveGraph,
                            setPreview = { edgePreview = it },
                            setConnectTarget = { connectTarget = it },
                            onClickWithoutMove = {
                                cloneDiagramNodeInDirection(state, editId, hoverNode.id, arrowSide)
                            },
                        )
                        return@awaitEachGesture
                    }

                    val routedPoints = routeAllEdgesLenient(live, RoutingOptions.Default)
                        .mapValues { (_, routed) -> routed.points }
                    val typedSelectedNodes = selectedNodesAtPress
                    val typedSelectedEdges = state.workspace.diagramSelection.edgeIds.map(::DiagramEdgeId).toSet()
                    val baseHit = hitTest(live, routedPoints, point, tolerance, typedSelectedNodes, typedSelectedEdges)
                    val hit = preferHighlightedDiagramPort(
                        hit = baseHit,
                        highlightedPort = diagramConnectionPortForSelection(
                            hoverDiagramPortAt(live, point, portTolerance),
                            typedSelectedNodes,
                        ),
                    )

                    // Shared double-click detection (draw.io modal-free text): a node opens its label,
                    // an edge opens/creates a mid-line label, empty canvas creates a shape with a caret.
                    val tapNow = TimeSource.Monotonic.markNow()
                    // Selection-independent label probe. The first tap on a label selects its edge,
                    // which exposes waypoint/endpoint handles that outrank labels in hitTest — so the
                    // second tap would hit a handle, key as "other" and never open the editor (UML
                    // multiplicity labels sit exactly on the endpoint rings). Probing with empty
                    // selections keeps the tap key stable across the pair.
                    val labelHit = hitTest(live, routedPoints, point, tolerance) as? DiagramHit.LabelHandle
                    val tapKey = when {
                        labelHit != null -> "label:${labelHit.edgeId.value}:${labelHit.position}"
                        hit is DiagramHit.Node -> "node:${hit.nodeId.value}"
                        hit is DiagramHit.Edge -> "edge:${hit.edgeId.value}"
                        hit == null -> "empty"
                        else -> "other"
                    }
                    val doubleClick = tapKey != "other" && lastTapKey == tapKey &&
                        (lastTapMark?.let { (tapNow - it).inWholeMilliseconds < 320 } ?: false)
                    lastTapMark = tapNow
                    lastTapKey = tapKey

                    if (doubleClick && labelHit != null && !additiveSelectionHeld) {
                        textEdit = DiagramTextEditTarget.EdgeLabel(labelHit.edgeId.value, labelHit.position)
                        return@awaitEachGesture
                    }

                    when (hit) {
                        is DiagramHit.ResizeHandle -> {
                            val target = live.nodeById(hit.nodeId) ?: return@awaitEachGesture
                            dragDiagramResize(state, editId, target, hit.handle, down, ::graphPointOf, point)
                        }

                        is DiagramHit.WaypointHandle -> {
                            dragDiagramFrames(state, down) { position ->
                                val at = graphPointOf(position)
                                state.dispatch(
                                    DiagramEditorIntent.MoveDiagramWaypoint(editId, hit.edgeId.value, hit.waypointIndex, at.x, at.y),
                                )
                            }
                        }

                        is DiagramHit.EndpointHandle -> {
                            // Drag an endpoint ring to re-attach that end: reuse the live snap
                            // preview, anchored at the OTHER (fixed) end so the dashed guide routes
                            // from it, and dispatch ReconnectDiagramEdge on release. Dropping on a
                            // node/port re-pins there (any node, incl. a different port of the same
                            // node); dropping on empty space makes it a free point.
                            val edge = live.edgeById(hit.edgeId) ?: return@awaitEachGesture
                            val fixedEnd = when (hit.end) {
                                DiagramEdgeEnd.SOURCE -> edge.target
                                DiagramEdgeEnd.TARGET -> edge.source
                            }
                            dragNewDiagramEdge(
                                state = state,
                                editId = editId,
                                source = fixedEnd,
                                sourceNodeId = fixedEnd.attachedNodeId,
                                down = down,
                                start = point,
                                relation = edge.relation,
                                graphPointOf = ::graphPointOf,
                                liveGraph = ::liveGraph,
                                setPreview = { edgePreview = it },
                                setConnectTarget = { connectTarget = it },
                                reconnect = hit.edgeId.value to hit.end,
                            )
                        }

                        is DiagramHit.LabelHandle -> {
                            // The label is part of the edge (draw.io): it is a hit target from hover,
                            // no prior selection needed. Double-click edits it; press+drag moves it;
                            // a plain click selects the owning edge.
                            val edge = live.edgeById(hit.edgeId) ?: return@awaitEachGesture
                            val label = edge.labels.firstOrNull { it.position == hit.position } ?: return@awaitEachGesture
                            val route = routedPoints[hit.edgeId] ?: return@awaitEachGesture
                            // Drag base deliberately uses the UNSHIFTED anchor (no crossing-slide
                            // context): the moment the drag mints a non-zero offset the label is
                            // rendered relative to the center fraction, so offset = pointer - center
                            // keeps it under the pointer; a slid base would displace the whole drag.
                            val anchor = edgeLabelAnchorPoint(route, label)
                            val base = DiagramPoint(anchor.x - label.offsetX, anchor.y - label.offsetY)
                            val moved = dragDiagramFrames(state, down) { position ->
                                val at = graphPointOf(position)
                                state.dispatch(
                                    DiagramEditorIntent.MoveDiagramEdgeLabel(
                                        editId, hit.edgeId.value, hit.position, at.x - base.x, at.y - base.y,
                                    ),
                                )
                            }
                            if (!moved) {
                                // A click (no drag) selects the label's edge, honoring the additive modifier like the
                                // Edge branch, so you can pick an edge straight from its label.
                                state.updateWorkspace {
                                    val current = it.diagramSelection
                                    val edgeId = hit.edgeId.value
                                    val next = when {
                                        additiveSelectionHeld && edgeId in current.edgeIds ->
                                            current.copy(edgeIds = current.edgeIds - edgeId)
                                        additiveSelectionHeld -> current.copy(edgeIds = current.edgeIds + edgeId)
                                        else -> DiagramSelection(edgeIds = setOf(edgeId))
                                    }
                                    it.copy(diagramSelection = next)
                                }
                            }
                        }

                        is DiagramHit.Port -> {
                            // Both persisted ports and the virtual draw.io connection grid are
                            // draggable. A virtual source point is persisted only after a
                            // successful drop, together with the edge in one undo transaction.
                            val sourceNode = live.nodeById(hit.nodeId) ?: return@awaitEachGesture
                            val sourcePort = sourceNode.connectionPorts()
                                .firstOrNull { it.id == hit.portId } ?: return@awaitEachGesture
                            dragNewDiagramEdge(
                                state = state,
                                editId = editId,
                                source = DiagramEndpoint.FixedPort(hit.nodeId, hit.portId),
                                sourceNodeId = hit.nodeId,
                                sourcePortToAdd = sourcePort.takeIf { sourceNode.portById(it.id) == null },
                                down = down,
                                start = point,
                                relation = DiagramRelation.Plain,
                                graphPointOf = ::graphPointOf,
                                liveGraph = ::liveGraph,
                                setPreview = { edgePreview = it },
                                setConnectTarget = { connectTarget = it },
                            )
                        }

                        is DiagramHit.Edge -> {
                            // Double-click opens (or creates) the edge's mid-line label for editing.
                            if (doubleClick && !additiveSelectionHeld) {
                                textEdit = DiagramTextEditTarget.EdgeLabel(hit.edgeId.value, DiagramEdgeLabelPosition.MIDDLE)
                                return@awaitEachGesture
                            }
                            // Click selects the edge; dragging the segment mints a waypoint there.
                            val edge = live.edgeById(hit.edgeId) ?: return@awaitEachGesture
                            val route = routedPoints[hit.edgeId]
                            val insertionIndex = route?.let {
                                waypointInsertionIndex(it, edge.waypoints, hit.segmentIndex)
                            } ?: edge.waypoints.size
                            var added = false
                            val moved = dragDiagramFrames(state, down) { position ->
                                val at = graphPointOf(position)
                                if (!added) {
                                    added = true
                                    state.dispatch(
                                        DiagramEditorIntent.AddDiagramWaypoint(editId, hit.edgeId.value, insertionIndex, at.x, at.y),
                                    )
                                } else {
                                    state.dispatch(
                                        DiagramEditorIntent.MoveDiagramWaypoint(editId, hit.edgeId.value, insertionIndex, at.x, at.y),
                                    )
                                }
                            }
                            state.updateWorkspace {
                                val current = it.diagramSelection
                                val edgeId = hit.edgeId.value
                                val next = when {
                                    moved -> DiagramSelection(edgeIds = setOf(edgeId))
                                    additiveSelectionHeld && edgeId in current.edgeIds ->
                                        current.copy(edgeIds = current.edgeIds - edgeId)
                                    additiveSelectionHeld -> current.copy(edgeIds = current.edgeIds + edgeId)
                                    else -> DiagramSelection(edgeIds = setOf(edgeId))
                                }
                                it.copy(diagramSelection = next)
                            }
                        }

                        is DiagramHit.Node -> {
                            val elementId = hit.nodeId.value
                            if (doubleClick && !additiveSelectionHeld) {
                                textEdit = when (val part = hit.part) {
                                    is DiagramNodeHitPart.TableCellPart ->
                                        DiagramTextEditTarget.TableCell(elementId, part.row, part.column)
                                    else -> DiagramTextEditTarget.NodeLabel(elementId)
                                }
                                return@awaitEachGesture
                            }

                            // Pre-press selection so the drag moves the pressed element.
                            val current = state.workspace.diagramSelection
                            if (!additiveSelectionHeld && elementId !in current.elementIds) {
                                state.updateWorkspace {
                                    it.copy(diagramSelection = DiagramSelection(elementIds = setOf(elementId)))
                                }
                            }
                            // Snap-aware move: union-bounds baseline + solveMoveSnap (sticky hysteresis,
                            // siblings = the other nodes). Feed the RAW box, apply the correction, and
                            // dispatch the absolute target as a step delta. Alt disables snapping; the
                            // alignment guides / spacing bars render live.
                            val dragIds = state.workspace.diagramSelection.elementIds.ifEmpty { setOf(elementId) }
                            val unionBase = diagramUnionBounds(dragIds, live)
                            val snapCatch = 7.0 / zoom
                            val snapRelease = 16.0 / zoom
                            var snapState = MoveSnapState.None
                            var lastDx = 0.0
                            var lastDy = 0.0
                            val moved = dragDiagramFrames(state, down) { position ->
                                val at = graphPointOf(position)
                                val rawDx = at.x - point.x
                                val rawDy = at.y - point.y
                                var finalDx = rawDx
                                var finalDy = rawDy
                                val alt = currentEvent.keyboardModifiers.isAltPressed
                                if (!alt && unionBase != null) {
                                    val rawBox = SnapBox(unionBase.x + rawDx, unionBase.y + rawDy, unionBase.width, unionBase.height)
                                    val siblings = live.nodes
                                        .filter { it.visible && it.id.value !in dragIds }
                                        .map { SnapBox(it.x, it.y, it.width, it.height) }
                                    val snap = solveMoveSnap(rawBox, emptyList(), siblings, snapCatch, snapRelease, true, true, snapState)
                                    finalDx = rawDx + snap.dx
                                    finalDy = rawDy + snap.dy
                                    snapState = snap.state
                                    snapGuides = snap.guides
                                    snapSpacing = snap.spacing
                                } else {
                                    snapState = MoveSnapState.None
                                    snapGuides = emptyList()
                                    snapSpacing = emptyList()
                                }
                                val stepDx = finalDx - lastDx
                                val stepDy = finalDy - lastDy
                                lastDx = finalDx
                                lastDy = finalDy
                                if (stepDx != 0.0 || stepDy != 0.0) {
                                    dragIds.forEach { movedId ->
                                        state.dispatch(DiagramEditorIntent.MoveDiagramNode(editId, movedId, stepDx, stepDy))
                                    }
                                }
                            }
                            snapGuides = emptyList()
                            snapSpacing = emptyList()
                            if (!moved && additiveSelectionHeld) {
                                state.updateWorkspace {
                                    val sel = it.diagramSelection
                                    val next = if (elementId in sel.elementIds) {
                                        sel.copy(elementIds = sel.elementIds - elementId)
                                    } else {
                                        sel.copy(elementIds = sel.elementIds + elementId)
                                    }
                                    it.copy(diagramSelection = next)
                                }
                            }
                        }

                        null -> {
                            // Double-click empty canvas → drop a default shape with the caret already live.
                            if (doubleClick && !additiveSelectionHeld) {
                                val width = 120.0
                                val height = 60.0
                                val newId = mintDiagramId(live, "node")
                                state.dispatch(
                                    DiagramEditorIntent.AddDiagramNode(
                                        nodeId = editId,
                                        elementId = newId,
                                        payload = DiagramNodePayload.BasicShape(DiagramShapeKind.RECTANGLE),
                                        x = snapToDiagramGrid(point.x - width / 2),
                                        y = snapToDiagramGrid(point.y - height / 2),
                                        width = width,
                                        height = height,
                                    ),
                                )
                                state.updateWorkspace { it.copy(diagramSelection = DiagramSelection(elementIds = setOf(newId))) }
                                textEdit = DiagramTextEditTarget.NodeLabel(newId)
                                return@awaitEachGesture
                            }
                            // Empty diagram area: a plain click selects the whole diagram (the container
                            // of that space) and leaves the block-edit layer — Figma's "click empty space
                            // = select container". A drag rubber-bands a marquee that selects every node it
                            // intersects (Ctrl/Cmd/Shift adds to the selection). Pure selection — no document edit —
                            // so it never touches undo/redo.
                            var selectionBox: DiagramRect? = null
                            var marqueeMoved = false
                            val marqueeSlop = viewConfiguration.touchSlop
                            while (true) {
                                val ev = awaitPointerEvent()
                                val ch = ev.changes.firstOrNull() ?: break
                                if (ch.changedToUp()) break
                                if ((ch.position - down.position).getDistance() >= marqueeSlop) marqueeMoved = true
                                if (marqueeMoved) {
                                    selectionBox = marqueeRect(point, graphPointOf(ch.position))
                                    marquee = selectionBox
                                }
                                ch.consume()
                            }
                            marquee = null
                            val boxSelection = selectionBox
                            if (marqueeMoved && boxSelection != null) {
                                val hitIds = live.nodes
                                    .filter { it.visible && !it.locked && it.intersectsOutline(boxSelection) }
                                    .map { it.id.value }
                                    .toSet()
                                state.updateWorkspace {
                                    val base = if (additiveSelectionHeld) it.diagramSelection.elementIds else emptySet()
                                    it.copy(diagramSelection = DiagramSelection(elementIds = base + hitIds))
                                }
                            } else if (!marqueeMoved && !additiveSelectionHeld) {
                                state.updateWorkspace {
                                    it.copy(
                                        diagramEditNodeId = "",
                                        diagramTool = DiagramTool.Select,
                                        diagramSelection = DiagramSelection.Empty,
                                    )
                                }
                                state.dispatch(DesignEditorIntent.SelectNode(editId))
                            }
                        }
                    }
                }
            },
    ) {
        // Edit-mode frame: a dashed accent outline around the diagram node's box.
        val topLeft = viewport.toScreen(box.x, box.y)
        val bottomRight = viewport.toScreen(box.right, box.bottom)
        drawRect(
            color = colors.accent.copy(alpha = 0.9f),
            topLeft = topLeft,
            size = Size(bottomRight.x - topLeft.x, bottomRight.y - topLeft.y),
            style = Stroke(width = 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))),
        )
        // Live preview of an edge being dragged out — the routed (bent) polyline the finished
        // connector will take, so the guide matches how the edge will actually be laid out.
        edgePreview?.let { preview ->
            val screen = preview.points.map { viewport.toScreen(box.x + it.x, box.y + it.y) }
            if (screen.size >= 2) {
                val path = Path().apply {
                    moveTo(screen.first().x, screen.first().y)
                    for (i in 1 until screen.size) lineTo(screen[i].x, screen[i].y)
                }
                drawPath(
                    path = path,
                    color = colors.accent,
                    style = Stroke(width = 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 4f))),
                )
                drawCircle(colors.accent, radius = 3.5f, center = screen.last())
            }
        }
        // Rubber-band marquee selection box.
        marquee?.let { rect ->
            val topLeft = viewport.toScreen(box.x + rect.x, box.y + rect.y)
            val bottomRight = viewport.toScreen(box.x + rect.right, box.y + rect.bottom)
            val size = Size(bottomRight.x - topLeft.x, bottomRight.y - topLeft.y)
            drawRect(color = colors.accent.copy(alpha = 0.12f), topLeft = topLeft, size = size)
            drawRect(
                color = colors.accent,
                topLeft = topLeft,
                size = size,
                style = Stroke(1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 3f))),
            )
        }
    }

    // Draw.io-style contextual controls for a single selected connector. Keeping these on the
    // canvas makes the three common edits (start marker, end marker, route) one click away while
    // the inspector retains the complete edge property set.
    if (edgePreview == null && textEdit == null) {
        val selectedEdge = selectedEdgeIds.singleOrNull()?.let(graph::edgeById)
        val selectedRoute = selectedEdge?.let { routes[it.id]?.points }
        if (selectedEdge != null && selectedRoute != null) {
            DiagramEdgeQuickToolbar(
                state = state,
                editId = editId,
                edge = selectedEdge,
                route = selectedRoute,
                box = box,
                viewport = viewport,
            )
        }
    }

    // Inline text editor for a double-clicked label / table cell / edge label.
    textEdit?.let { target ->
        DiagramInlineTextEditor(
            state = state,
            editId = editId,
            target = target,
            graph = graph,
            routes = routes.mapValues { it.value.points },
            box = box,
            viewport = viewport,
            zoomPx = zoomPx,
            onRegisterCommit = { inlineTextCommit.value = it },
            onDone = { textEdit = null },
        )
    }
}

/** Arc-length midpoint of the visible route, used as the stable toolbar attachment point. */
internal fun diagramEdgeToolbarAnchor(route: List<DiagramPoint>): DiagramPoint? =
    route.takeIf { it.size >= 2 }?.let { pointAlongPolyline(it, 0.5) }

/**
 * Places the toolbar to the left of its connector anchor and clamps it to the visible canvas.
 * The result is screen-pixel geometry, so zoom never changes the control's physical size.
 */
internal fun diagramEdgeToolbarOffset(
    anchor: Offset,
    viewportSize: Size,
    toolbarSize: Size,
    margin: Float = DiagramEdgeToolbarMarginPx,
    gap: Float = DiagramEdgeToolbarGapPx,
): Offset {
    val maxX = max(margin, viewportSize.width - toolbarSize.width - margin)
    val maxY = max(margin, viewportSize.height - toolbarSize.height - margin)
    return Offset(
        x = (anchor.x - toolbarSize.width - gap).coerceIn(margin, maxX),
        y = (anchor.y - toolbarSize.height / 2f).coerceIn(margin, maxY),
    )
}

/** Compact on-canvas edge toolbar matching the start / end / route controls in draw.io. */
@Composable
private fun DiagramEdgeQuickToolbar(
    state: MissionEditorStateHolder,
    editId: String,
    edge: DiagramEdge,
    route: List<DiagramPoint>,
    box: LayoutBox,
    viewport: CanvasViewport,
) {
    val anchor = diagramEdgeToolbarAnchor(route) ?: return
    val anchorScreen = viewport.toScreen(box.x + anchor.x, box.y + anchor.y)
    val colors = LocalEditorColors.current
    val strings = LocalStrings.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    var expandedMenu by remember(edge.id) { mutableStateOf<DiagramEdgeToolbarMenu?>(null) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val toolbarSizePx = with(density) {
            Size(DiagramEdgeToolbarWidth.toPx(), DiagramEdgeToolbarHeight.toPx())
        }
        val toolbarOffset = diagramEdgeToolbarOffset(
            anchor = anchorScreen,
            viewportSize = Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat()),
            toolbarSize = toolbarSizePx,
        )
        Surface(
            modifier = Modifier
                .offset { IntOffset(toolbarOffset.x.roundToInt(), toolbarOffset.y.roundToInt()) }
                .width(DiagramEdgeToolbarWidth)
                .height(DiagramEdgeToolbarHeight),
            shape = RoundedCornerShape(8.dp),
            color = colors.raisedSurface,
            border = BorderStroke(1.dp, colors.controlStroke),
            shadowElevation = 5.dp,
        ) {
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                DiagramEdgeToolbarDropdownButton(
                    label = strings.inspector.startHead,
                    expanded = expandedMenu == DiagramEdgeToolbarMenu.SOURCE,
                    onExpandedChange = { expandedMenu = if (it) DiagramEdgeToolbarMenu.SOURCE else null },
                    preview = {
                        DiagramArrowheadPreview(
                            edge.sourceArrowhead.kind,
                            modifier = Modifier.size(20.dp),
                            atStart = true,
                        )
                    },
                ) { dismiss ->
                    DiagramArrowheadKind.entries.forEach { kind ->
                        EditorDropdownMenuItem(
                            text = strings.inspector.diagramArrowhead(kind),
                            leadingContent = { DiagramArrowheadPreview(kind, atStart = true) },
                            selected = kind == edge.sourceArrowhead.kind,
                            onClick = {
                                dismiss()
                                state.dispatch(
                                    DiagramEditorIntent.SetDiagramEdgeArrowheads(
                                        editId,
                                        edge.id.value,
                                        source = DiagramArrowhead(kind),
                                    ),
                                )
                            },
                        )
                    }
                }

                DiagramEdgeToolbarDivider()

                DiagramEdgeToolbarDropdownButton(
                    label = strings.inspector.endHead,
                    expanded = expandedMenu == DiagramEdgeToolbarMenu.TARGET,
                    onExpandedChange = { expandedMenu = if (it) DiagramEdgeToolbarMenu.TARGET else null },
                    preview = {
                        DiagramArrowheadPreview(edge.targetArrowhead.kind, modifier = Modifier.size(20.dp))
                    },
                ) { dismiss ->
                    DiagramArrowheadKind.entries.forEach { kind ->
                        EditorDropdownMenuItem(
                            text = strings.inspector.diagramArrowhead(kind),
                            leadingContent = { DiagramArrowheadPreview(kind) },
                            selected = kind == edge.targetArrowhead.kind,
                            onClick = {
                                dismiss()
                                state.dispatch(
                                    DiagramEditorIntent.SetDiagramEdgeArrowheads(
                                        editId,
                                        edge.id.value,
                                        target = DiagramArrowhead(kind),
                                    ),
                                )
                            },
                        )
                    }
                }

                DiagramEdgeToolbarDivider()

                DiagramEdgeToolbarDropdownButton(
                    label = strings.inspector.routing,
                    expanded = expandedMenu == DiagramEdgeToolbarMenu.ROUTING,
                    onExpandedChange = { expandedMenu = if (it) DiagramEdgeToolbarMenu.ROUTING else null },
                    preview = { DiagramRoutingPreview(edge.routing, modifier = Modifier.size(20.dp)) },
                ) { dismiss ->
                    DiagramRoutingStyle.entries.forEach { routing ->
                        EditorDropdownMenuItem(
                            text = strings.inspector.diagramRouting(routing),
                            leadingContent = { DiagramRoutingPreview(routing) },
                            selected = routing == edge.routing,
                            onClick = {
                                dismiss()
                                state.dispatch(DiagramEditorIntent.SetDiagramEdgeRouting(editId, edge.id.value, routing))
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagramEdgeToolbarDropdownButton(
    label: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    preview: @Composable () -> Unit,
    menuContent: @Composable (dismiss: () -> Unit) -> Unit,
) {
    val colors = LocalEditorColors.current
    Box(Modifier.size(DiagramEdgeToolbarButtonSize), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(DiagramEdgeToolbarButtonSize)
                .clip(RoundedCornerShape(7.dp))
                .background(if (expanded) colors.selectionFill else colors.raisedSurface)
                .clickable { onExpandedChange(!expanded) }
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            preview()
        }
        EditorDropdownMenu(expanded = expanded, onDismissRequest = { onExpandedChange(false) }) {
            menuContent { onExpandedChange(false) }
        }
    }
}

@Composable
private fun DiagramEdgeToolbarDivider() {
    val colors = LocalEditorColors.current
    Box(Modifier.width(1.dp).height(20.dp).background(colors.controlStroke))
}

/** Horizontal breathing room inside the edit plate, in raw px (the plate is sized in px too). */
private const val PlateInsetPx = 4f

/**
 * The bounds a hug node should take after its caption becomes [text], or `null` when the node
 * does not hug (draw.io keeps authored geometry authoritative) or nothing would change.
 *
 * The fit runs through the pure [fitNodeToText] op with the injected [measurer], so the reducer
 * stays free of platform text metrics — the UI, which already has the real ones, measures and
 * hands the reducer plain numbers via the existing resize intent.
 */
private fun hugFittedBounds(
    graph: DiagramGraph,
    elementId: String,
    text: String?,
    measurer: DiagramTextMeasurer,
): DiagramRect? {
    if (text.isNullOrBlank()) return null
    val id = DiagramNodeId(elementId)
    val node = graph.nodeById(id) ?: return null
    if (node.sizing != DiagramNodeSizing.Hug) return null
    val fitted = graph.setNodeText(id, text).fitNodeToText(id, measurer).nodeById(id) ?: return null
    return fitted.bounds.takeIf { it != node.bounds }
}

/** Small floating text field over the edited label; text is selected on open, Enter, Escape and
 *  blur all commit (draw.io semantics — the caret is already live, just type).
 *
 *  [onRegisterCommit] publishes the commit hook to the owner for the blur path; the callback
 *  reads the draft at invocation time, so the owner always commits the current text. */
@Composable
private fun DiagramInlineTextEditor(
    state: MissionEditorStateHolder,
    editId: String,
    target: DiagramTextEditTarget,
    graph: DiagramGraph,
    routes: Map<DiagramEdgeId, List<DiagramPoint>>,
    box: LayoutBox,
    viewport: CanvasViewport,
    zoomPx: Float,
    onRegisterCommit: ((() -> Unit)?) -> Unit,
    onDone: () -> Unit,
) {
    val colors = LocalEditorColors.current
    val anchorRect = diagramTextEditRect(target, graph, routes) ?: run {
        onDone()
        return
    }
    // Real font metrics for the hug fit, built exactly like the artboard's (never inside the
    // reducer: dependencies are injected, and the core must stay Compose-free).
    val textMeasurer = rememberTextMeasurer()
    val hugDensity = LocalDensity.current
    val hugFontProvider = rememberBundledFontProvider()
    val diagramMeasurer = remember(textMeasurer, hugDensity, hugFontProvider) {
        ComposeDiagramTextMeasurer(ComposeTypographyMeasurer(textMeasurer, hugDensity, hugFontProvider))
    }
    val initial = diagramTextEditInitialText(target, graph)
    // Open with the existing text fully selected so the first keystroke replaces it (F2 / rename).
    var draft by remember(target) { mutableStateOf(TextFieldValue(initial, TextRange(0, initial.length))) }
    val focusRequester = remember(target) { FocusRequester() }

    // Guards against committing the same draft twice: the editor can be closed explicitly AND
    // then disposed, and both paths commit.
    var committed by remember(target) { mutableStateOf(false) }

    fun dispatchCommit() {
        val text = draft.text.takeIf { it.isNotBlank() }?.trim()
        // Blur commits on every press outside the field, so skip untouched drafts: an unchanged
        // commit would still rewrite the source and push an undo entry.
        if (committed || text == initial.takeIf { it.isNotBlank() }?.trim()) return
        committed = true
        when (target) {
            is DiagramTextEditTarget.NodeLabel -> {
                state.dispatch(DiagramEditorIntent.SetDiagramNodeLabel(editId, target.elementId, text))
                hugFittedBounds(graph, target.elementId, text, diagramMeasurer)?.let { fitted ->
                    state.dispatch(
                        DiagramEditorIntent.ResizeDiagramNode(
                            nodeId = editId,
                            elementId = target.elementId,
                            x = fitted.x,
                            y = fitted.y,
                            width = fitted.width,
                            height = fitted.height,
                        ),
                    )
                }
            }
            is DiagramTextEditTarget.TableCell ->
                state.dispatch(
                    DiagramEditorIntent.SetDiagramTableCellText(editId, target.elementId, target.row, target.column, text),
                )
            is DiagramTextEditTarget.EdgeLabel ->
                state.dispatch(DiagramEditorIntent.SetDiagramEdgeLabel(editId, target.edgeId, target.position, text))
        }
    }

    fun commit() {
        dispatchCommit()
        onDone()
    }

    // Blur = commit (draw.io's invokesStopCellEditing). Two paths reach it:
    //  - a press this overlay handles calls the published hook;
    //  - anything that closes edit mode from OUTSIDE the overlay (a press the canvas pane
    //    handles, Escape/Enter at canvas level, selecting another node) simply unmounts this
    //    composable, so disposal is the only place left to save the draft. Without this the
    //    draft vanished silently, which is precisely what blur=commit is supposed to prevent.
    DisposableEffect(target) {
        onRegisterCommit(::commit)
        onDispose {
            onRegisterCommit(null)
            dispatchCommit()
        }
    }

    // Overlay the edited label's exact box and typeset the draft like the canvas does
    // (canonical diagram font in document px scaled by zoom, centered both ways), so
    // entering edit mode doesn't visually jump the text.
    val density = LocalDensity.current
    val fontSizeDoc = when (target) {
        // A note renders at the smaller detail size, like every member/attribute row.
        is DiagramTextEditTarget.NodeLabel ->
            if (graph.nodeById(DiagramNodeId(target.elementId))?.payload is UmlNoteNode) {
                DIAGRAM_DETAIL_FONT_SIZE
            } else {
                DIAGRAM_LABEL_FONT_SIZE
            }

        else -> DIAGRAM_DETAIL_FONT_SIZE
    }
    val headerCell = (target as? DiagramTextEditTarget.TableCell)?.let { cell ->
        val table = graph.nodeById(DiagramNodeId(cell.elementId))?.payload as? TableNode
        table != null && (
            table.rows.getOrNull(cell.row)?.header == true ||
                table.columns.getOrNull(cell.column)?.header == true
            )
    } ?: false
    // Match the canvas per payload, not just in font size: a note is left-aligned and top-anchored,
    // a package caption is semibold. Commit 446750a matched only the size and the outer rect, which
    // is why entering edit mode still nudged those captions.
    val editedPayload = (target as? DiagramTextEditTarget.NodeLabel)
        ?.let { graph.nodeById(DiagramNodeId(it.elementId))?.payload }
    val plateWeight = when {
        headerCell -> FontWeight.SemiBold
        editedPayload is UmlPackageNode -> FontWeight.SemiBold
        editedPayload is DiagramNodePayload.ContainerNode -> FontWeight.SemiBold
        else -> FontWeight.Normal
    }
    val plateAlign = when (editedPayload) {
        is UmlNoteNode, is DiagramNodePayload.ContainerNode -> TextAlign.Left
        else -> TextAlign.Center
    }
    val plateVerticalAlignment = when (editedPayload) {
        is UmlNoteNode -> Alignment.TopStart
        else -> Alignment.Center
    }
    val fontSizePx = (fontSizeDoc * zoomPx).toFloat()
    val widthPx = (anchorRect.width * zoomPx).toFloat().coerceAtLeast(96f)
    val heightPx = (anchorRect.height * zoomPx).toFloat().coerceAtLeast(fontSizePx + 14f)
    val center = viewport.toScreen(
        box.x + anchorRect.x + anchorRect.width / 2.0,
        box.y + anchorRect.y + anchorRect.height / 2.0,
    )
    Surface(
        modifier = Modifier
            .offset { IntOffset((center.x - widthPx / 2f).roundToInt(), (center.y - heightPx / 2f).roundToInt()) }
            .size(
                width = with(density) { widthPx.toDp() },
                height = with(density) { heightPx.toDp() },
            ),
        shape = RoundedCornerShape(3.dp),
        color = colors.raisedSurface,
        border = BorderStroke(1.dp, colors.accent),
        shadowElevation = 4.dp,
    ) {
        Box(
            // Padding in raw px like the slot itself: mixing a dp padding with a px-derived
            // width made the field wider than its plate wherever density > 1 (wasm is 2).
            modifier = Modifier.fillMaxSize().padding(horizontal = with(density) { PlateInsetPx.toDp() }),
            contentAlignment = plateVerticalAlignment,
        ) {
            BasicTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier
                    .width(with(density) { (widthPx - 2f * PlateInsetPx).coerceAtLeast(8f).toDp() })
                    .focusRequester(focusRequester)
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (event.key) {
                            // Enter commits, Shift+Enter inserts a line break. draw.io inherits
                            // the opposite from its HTML textarea (Enter = newline, Ctrl+Enter =
                            // commit); the modern convention this editor follows elsewhere wins.
                            Key.Enter, Key.NumPadEnter -> {
                                if (event.isShiftPressed) {
                                    false
                                } else {
                                    commit()
                                    true
                                }
                            }
                            Key.Escape -> {
                                commit()
                                true
                            }
                            else -> false
                        }
                    },
                singleLine = false,
                textStyle = MaterialTheme.typography.bodySmall.copy(
                    color = colors.ink,
                    fontSize = with(density) { fontSizePx.toSp() },
                    fontWeight = plateWeight,
                    textAlign = plateAlign,
                ),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(colors.accent),
            )
        }
    }
    LaunchedEffect(target) { runCatching { focusRequester.requestFocus() } }
}

// --- Gesture helpers ------------------------------------------------------------

/**
 * Per-frame drag loop bracketed by Begin/EndInteraction so the whole drag coalesces into
 * one undo entry. [onFrame] receives the current pointer position; returns whether the
 * pointer actually moved past the slop.
 */
private suspend fun AwaitPointerEventScope.dragDiagramFrames(
    state: MissionEditorStateHolder,
    down: PointerInputChange,
    onFrame: (position: Offset) -> Unit,
): Boolean = dragDiagramFrames(state, down) { position, _ -> onFrame(position) }

/** As [dragDiagramFrames] but also hands the previous frame position (for deltas). */
private suspend fun AwaitPointerEventScope.dragDiagramFrames(
    state: MissionEditorStateHolder,
    down: PointerInputChange,
    onFrame: (position: Offset, last: Offset) -> Unit,
): Boolean {
    val slop = viewConfiguration.touchSlop
    var began = false
    var moved = false
    var last = down.position
    while (true) {
        val event = awaitPointerEvent()
        val change = event.changes.firstOrNull() ?: break
        if (change.changedToUp()) break
        val position = change.position
        if (!moved && (position - down.position).getDistance() < slop) {
            change.consume()
            continue
        }
        moved = true
        if (!began) {
            state.dispatch(DesignEditorIntent.BeginInteraction)
            began = true
        }
        onFrame(position, last)
        last = position
        change.consume()
    }
    if (began) state.dispatch(DesignEditorIntent.EndInteraction)
    return moved
}

/** Resize drag of one diagram node: per-frame [DiagramEditorIntent.ResizeDiagramNode]. */
private suspend fun AwaitPointerEventScope.dragDiagramResize(
    state: MissionEditorStateHolder,
    editId: String,
    target: DiagramNode,
    handle: DiagramResizeHandle,
    down: PointerInputChange,
    graphPointOf: (Offset) -> DiagramPoint,
    start: DiagramPoint,
) {
    val baseline = target.bounds
    dragDiagramFrames(state, down) { position ->
        val at = graphPointOf(position)
        val next = resizedDiagramBounds(baseline, handle, at.x - start.x, at.y - start.y)
        state.dispatch(
            DiagramEditorIntent.ResizeDiagramNode(
                nodeId = editId,
                elementId = target.id.value,
                x = next.x,
                y = next.y,
                width = next.width,
                height = next.height,
            ),
        )
    }
}

/**
 * Routed (bent) polyline for the in-flight edge drag: builds a provisional edge from [source]
 * to whatever [target] resolves to and routes it with the same orthogonal router the finished
 * connector uses, so the dashed guide follows the real path (off the facing side, around
 * obstacles) instead of a straight line. A standard side-point is materialized in the throwaway
 * graph so a pinned end routes exactly; falls back to a two-point line if routing yields nothing.
 */
private fun previewRouteFor(
    graph: DiagramGraph?,
    source: DiagramEndpoint,
    sourcePortToAdd: DiagramPort?,
    target: ConnectTarget,
    fromPoint: DiagramPoint,
): DiagramEdgePreview {
    if (graph == null) return DiagramEdgePreview(listOf(fromPoint, target.snapPoint))
    var provisional = graph
    val sourceNodeId = source.attachedNodeId
    if (sourceNodeId != null && sourcePortToAdd != null) {
        provisional = provisional.addCustomPort(sourceNodeId, sourcePortToAdd)
    }
    val endpoint: DiagramEndpoint = when (target) {
        is ConnectTarget.Port -> {
            val node = graph.nodeById(target.nodeId)
            if (node != null && node.portById(target.port.id) == null) {
                provisional = provisional.addCustomPort(target.nodeId, target.port)
            }
            DiagramEndpoint.FixedPort(target.nodeId, target.port.id)
        }
        is ConnectTarget.Floating -> DiagramEndpoint.FloatingAnchor(target.nodeId)
        is ConnectTarget.Free -> DiagramEndpoint.FreePoint(target.snapPoint.x, target.snapPoint.y)
    }
    val previewId = DiagramEdgeId("__mv_edge_preview__")
    val routed = provisional
        .withEdge(DiagramEdge(id = previewId, source = source, target = endpoint))
        .let { routeAllEdgesLenient(it, RoutingOptions.Default)[previewId] }
    val points = routed?.points?.takeIf { it.size >= 2 } ?: listOf(fromPoint, target.snapPoint)
    return DiagramEdgePreview(points)
}

/**
 * Drags a connector end anchored at [source]: a live preview that snaps to whatever the
 * pointer is over — a specific connection point (green cross, pinned) or a node's perimeter
 * (blue, floating) — mirrored to [setConnectTarget] so the target lights up. On release the
 * end lands on the resolved target: a fixed port (materialized if it was one of the standard
 * side points, so the pin resolves and round-trips), a floating anchor, or a free point in
 * empty space. Releasing back on the anchor node cancels.
 *
 * By default this mints a NEW edge from [source] to the landing. Pass [reconnect] (the
 * `edgeId` + which [DiagramEdgeEnd] is being dragged) to instead re-pin that end of an
 * existing edge via [DiagramEditorIntent.ReconnectDiagramEdge]; then [source] is the OTHER,
 * fixed end so the dashed preview routes from it to the pointer.
 *
 * [sourceNodeId] is the anchor node excluded from snapping (null when the anchor is a free
 * point). If the pointer never moved past the touch slop (a click, not a drag),
 * [onClickWithoutMove] fires instead — the directional arrow uses that to clone the node.
 */
private suspend fun AwaitPointerEventScope.dragNewDiagramEdge(
    state: MissionEditorStateHolder,
    editId: String,
    source: DiagramEndpoint,
    sourceNodeId: DiagramNodeId?,
    sourcePortToAdd: DiagramPort? = null,
    down: PointerInputChange,
    start: DiagramPoint,
    relation: DiagramRelation,
    graphPointOf: (Offset) -> DiagramPoint,
    liveGraph: () -> DiagramGraph?,
    setPreview: (DiagramEdgePreview?) -> Unit,
    setConnectTarget: (ConnectTarget?) -> Unit = {},
    onClickWithoutMove: (() -> Unit)? = null,
    reconnect: Pair<String, DiagramEdgeEnd>? = null,
) {
    // The source anchor is fixed for the whole drag. Virtual grid ports are not in the graph
    // yet, so resolve their exact geometry directly instead of falling back to the node center.
    val fromPoint = liveGraph()?.let { graph ->
        if (sourceNodeId != null && sourcePortToAdd != null) {
            graph.nodeById(sourceNodeId)?.let { sourceNode -> anchorPoint(sourceNode, sourcePortToAdd) }
        } else {
            resolveEndpointPoint(graph, source)
        }
    } ?: start
    var target: ConnectTarget = ConnectTarget.Free(start)
    var moved = false
    val slop = viewConfiguration.touchSlop
    while (true) {
        val event = awaitPointerEvent()
        val change = event.changes.firstOrNull() ?: break
        val current = graphPointOf(change.position)
        if (change.changedToUp()) break
        if ((change.position - down.position).getDistance() >= slop) moved = true
        if (moved) {
            val g = liveGraph()
            target = g?.resolveConnectTarget(
                from = fromPoint,
                pointer = current,
                excludeNodeId = sourceNodeId,
            ) ?: ConnectTarget.Free(current)
            setPreview(previewRouteFor(g, source, sourcePortToAdd, target, fromPoint))
            setConnectTarget(target)
        }
        change.consume()
    }
    setPreview(null)
    setConnectTarget(null)
    if (!moved) {
        onClickWithoutMove?.invoke()
        return
    }
    val graph = liveGraph() ?: return
    // Resolve where the dragged end lands, plus any standard side-point that must be
    // materialized as a real port first so a fixed pin resolves and round-trips.
    val (endpoint, portToAdd) = when (val landing = target) {
        is ConnectTarget.Free -> {
            // Released back inside the anchored end's node: cancel rather than mint a stub.
            val anchorNode = sourceNodeId?.let { graph.nodeById(it) }
            if (anchorNode != null && anchorNode.containsPoint(landing.snapPoint)) return
            DiagramEndpoint.FreePoint(landing.snapPoint.x, landing.snapPoint.y) to null
        }

        is ConnectTarget.Floating -> DiagramEndpoint.FloatingAnchor(landing.nodeId) to null

        is ConnectTarget.Port -> {
            val node = graph.nodeById(landing.nodeId) ?: return
            val fixed = DiagramEndpoint.FixedPort(landing.nodeId, landing.port.id)
            val toAdd = if (node.portById(landing.port.id) != null) null else landing.nodeId.value to landing.port
            fixed to toAdd
        }
    }

    val portsToAdd = buildList {
        if (sourceNodeId != null && sourcePortToAdd != null) {
            add(sourceNodeId.value to sourcePortToAdd)
        }
        if (portToAdd != null) add(portToAdd)
    }.distinctBy { (nodeId, port) -> nodeId to port.id }

    if (reconnect != null) {
        // Re-pin the dragged end of an existing edge. The optional port materialization and the
        // reconnect coalesce into one undo entry (the interacting gate suppresses the middle push).
        val (reconnectEdgeId, end) = reconnect
        state.dispatch(DesignEditorIntent.BeginInteraction)
        portsToAdd.forEach { (nodeId, port) ->
            state.dispatch(DiagramEditorIntent.AddDiagramPort(editId, nodeId, port))
        }
        state.dispatch(DiagramEditorIntent.ReconnectDiagramEdge(editId, reconnectEdgeId, end, endpoint))
        state.dispatch(DesignEditorIntent.EndInteraction)
        state.updateWorkspace { it.copy(diagramSelection = DiagramSelection(edgeIds = setOf(reconnectEdgeId))) }
        return
    }

    if (portsToAdd.isNotEmpty()) {
        // Materialize virtual connection points at either end, then connect. All writes
        // coalesce into one undo entry; cancelled drags never leave unused ports behind.
        val edgeId = mintDiagramId(graph, "edge")
        state.dispatch(DesignEditorIntent.BeginInteraction)
        portsToAdd.forEach { (nodeId, port) ->
            state.dispatch(DiagramEditorIntent.AddDiagramPort(editId, nodeId, port))
        }
        state.dispatch(
            DiagramEditorIntent.ConnectDiagramNodes(
                nodeId = editId,
                edgeId = edgeId,
                source = source,
                target = endpoint,
                relation = relation,
            ),
        )
        state.dispatch(DesignEditorIntent.EndInteraction)
        state.updateWorkspace { it.copy(diagramSelection = DiagramSelection(edgeIds = setOf(edgeId))) }
    } else {
        connectDiagramEdge(state, editId, graph, source, endpoint, relation)
    }
}

/** Mints an edge id, dispatches the connect, and selects the new edge (one undo entry). */
private fun connectDiagramEdge(
    state: MissionEditorStateHolder,
    editId: String,
    graph: DiagramGraph,
    source: DiagramEndpoint,
    target: DiagramEndpoint,
    relation: DiagramRelation,
) {
    val edgeId = mintDiagramId(graph, "edge")
    state.dispatch(
        DiagramEditorIntent.ConnectDiagramNodes(
            nodeId = editId,
            edgeId = edgeId,
            source = source,
            target = target,
            relation = relation,
        ),
    )
    state.updateWorkspace { it.copy(diagramSelection = DiagramSelection(edgeIds = setOf(edgeId))) }
}

// --- Pure geometry helpers --------------------------------------------------------

/** Topmost visible, unlocked diagram node whose rendered body contains [point] (list order = z-order). */
private fun hitDiagramNodeAt(graph: DiagramGraph, point: DiagramPoint): DiagramNodeId? =
    graph.nodes.asReversed()
        .firstOrNull { it.visible && !it.locked && it.containsPoint(point) }
        ?.id

/**
 * Node under [point] for hover purposes: an exact body hit first, else the topmost node whose
 * outline halo contains [point]. Without this margin the hover (and the directional arrows)
 * would vanish the instant the pointer leaves the rendered body.
 */
private fun hoverDiagramNodeAt(
    graph: DiagramGraph,
    point: DiagramPoint,
    margin: Double,
): DiagramNodeId? {
    hitDiagramNodeAt(graph, point)?.let { return it }
    return graph.nodes.asReversed()
        .firstOrNull { node -> node.visible && !node.locked && node.containsPoint(point, margin) }
        ?.id
}

/**
 * Nearest visible connection point within the screen-normalized hover radius. A foreground
 * body owns the pointer first, so a covered node cannot expose a green port through it.
 */
internal fun hoverDiagramPortAt(
    graph: DiagramGraph,
    point: DiagramPoint,
    tolerance: Double,
): DiagramHit.Port? {
    val toleranceSquared = tolerance * tolerance
    val candidates = graph.nodes.asReversed().filter { it.visible && !it.locked }

    fun nearestPort(node: DiagramNode): DiagramHit.Port? {
        var nearestPort: DiagramPort? = null
        var nearestDistanceSquared = Double.MAX_VALUE
        node.connectionPorts().forEach { port ->
            val position = anchorPoint(node, port)
            val dx = position.x - point.x
            val dy = position.y - point.y
            val distanceSquared = dx * dx + dy * dy
            if (distanceSquared < nearestDistanceSquared) {
                nearestPort = port
                nearestDistanceSquared = distanceSquared
            }
        }
        return nearestPort
            ?.takeIf { nearestDistanceSquared <= toleranceSquared }
            ?.let { DiagramHit.Port(node.id, it.id) }
    }

    candidates.firstOrNull { it.containsPoint(point) }?.let { node ->
        return nearestPort(node)
    }
    candidates.forEach { node ->
        nearestPort(node)?.let { return it }
    }
    return null
}

/**
 * Connection affordances belong to hover mode. Once a node is selected, its perimeter handles
 * resize geometry instead, so the same pointer position must not start a connection.
 */
internal fun diagramConnectionPortForSelection(
    port: DiagramHit.Port?,
    selectedNodeIds: Set<DiagramNodeId>,
): DiagramHit.Port? = port?.takeUnless { it.nodeId in selectedNodeIds }

/**
 * The green port marker is an explicit interaction promise. It beats the same node's body or
 * resize handle, while existing edge endpoint/waypoint/label grabs retain their own priority.
 */
internal fun preferHighlightedDiagramPort(
    hit: DiagramHit?,
    highlightedPort: DiagramHit.Port?,
): DiagramHit? = when {
    highlightedPort == null -> hit
    hit is DiagramHit.ResizeHandle && hit.nodeId == highlightedPort.nodeId -> highlightedPort
    hit is DiagramHit.Node && hit.nodeId == highlightedPort.nodeId -> highlightedPort
    else -> hit
}

/** Normalized rect (non-negative width/height) spanning two graph points, for the marquee. */
private fun marqueeRect(a: DiagramPoint, b: DiagramPoint): DiagramRect =
    DiagramRect(min(a.x, b.x), min(a.y, b.y), max(a.x, b.x) - min(a.x, b.x), max(a.y, b.y) - min(a.y, b.y))

/** Union bounds of the given diagram nodes (snap baseline), or null when none resolve. */
private fun diagramUnionBounds(ids: Set<String>, graph: DiagramGraph): DiagramRect? {
    val boxes = ids.mapNotNull { graph.nodeById(DiagramNodeId(it))?.bounds }
    if (boxes.isEmpty()) return null
    val left = boxes.minOf { it.left }
    val top = boxes.minOf { it.top }
    val right = boxes.maxOf { it.right }
    val bottom = boxes.maxOf { it.bottom }
    return DiagramRect(left, top, right - left, bottom - top)
}

/** Which of the four hover directional arrows of [node] [point] lands on, or null. */
private fun directionalArrowSideHit(
    node: DiagramNode,
    point: DiagramPoint,
    distance: Double,
    radius: Double,
): DiagramNodeSide? {
    val bounds = node.bounds
    val tips = listOf(
        DiagramNodeSide.TOP to DiagramPoint(bounds.centerX, bounds.top - distance),
        DiagramNodeSide.RIGHT to DiagramPoint(bounds.right + distance, bounds.centerY),
        DiagramNodeSide.BOTTOM to DiagramPoint(bounds.centerX, bounds.bottom + distance),
        DiagramNodeSide.LEFT to DiagramPoint(bounds.left - distance, bounds.centerY),
    )
    return tips.firstOrNull { (_, tip) -> hypot(tip.x - point.x, tip.y - point.y) <= radius }?.first
}

/**
 * Click-on-arrow clone: copies [sourceId] one node-extent-plus-gap toward [side]
 * (center-aligned on the shared axis, pushed past any node it would overlap via the pure
 * [directionalCloneOffset]), connects original → clone with a floating edge, and selects
 * the clone. One dispatch = one undo entry.
 */
private fun cloneDiagramNodeInDirection(
    state: MissionEditorStateHolder,
    editId: String,
    sourceId: DiagramNodeId,
    side: DiagramNodeSide,
) {
    val graph = (state.designState.document?.nodeById(editId)?.kind as? DesignNodeKind.Diagram)?.graph ?: return
    val (offsetX, offsetY) = graph.directionalCloneOffset(sourceId, side) ?: return
    val cloneId = mintDiagramId(graph, "node")
    val edgeId = mintDiagramId(graph, "edge")
    state.dispatch(
        DiagramEditorIntent.CloneDiagramNodeAndConnect(
            nodeId = editId,
            sourceElementId = sourceId.value,
            cloneId = cloneId,
            edgeId = edgeId,
            offsetX = offsetX,
            offsetY = offsetY,
        ),
    )
    state.updateWorkspace { it.copy(diagramSelection = DiagramSelection(elementIds = setOf(cloneId))) }
}

/** New bounds for a handle-resize by ([dx], [dy]) with a 10-unit minimum size. */
private fun resizedDiagramBounds(
    baseline: DiagramRect,
    handle: DiagramResizeHandle,
    dx: Double,
    dy: Double,
): DiagramRect {
    val minSize = 10.0
    var left = baseline.left
    var top = baseline.top
    var right = baseline.right
    var bottom = baseline.bottom
    when (handle) {
        DiagramResizeHandle.TOP_LEFT -> {
            left += dx
            top += dy
        }
        DiagramResizeHandle.TOP -> top += dy
        DiagramResizeHandle.TOP_RIGHT -> {
            right += dx
            top += dy
        }
        DiagramResizeHandle.RIGHT -> right += dx
        DiagramResizeHandle.BOTTOM_RIGHT -> {
            right += dx
            bottom += dy
        }
        DiagramResizeHandle.BOTTOM -> bottom += dy
        DiagramResizeHandle.BOTTOM_LEFT -> {
            left += dx
            bottom += dy
        }
        DiagramResizeHandle.LEFT -> left += dx
    }
    left = min(left, baseline.right - minSize)
    top = min(top, baseline.bottom - minSize)
    right = max(right, left + minSize)
    bottom = max(bottom, top + minSize)
    return DiagramRect(left, top, right - left, bottom - top)
}

/**
 * Waypoint-list insertion index for a grab on routed segment [segmentIndex]: the number of
 * existing waypoints whose nearest route vertex lies at or before the grabbed segment.
 */
private fun waypointInsertionIndex(
    route: List<DiagramPoint>,
    waypoints: List<DiagramPoint>,
    segmentIndex: Int,
): Int {
    if (waypoints.isEmpty()) return 0
    fun nearestVertex(p: DiagramPoint): Int =
        route.indices.minByOrNull { index ->
            val v = route[index]
            (v.x - p.x) * (v.x - p.x) + (v.y - p.y) * (v.y - p.y)
        } ?: 0
    return waypoints.count { nearestVertex(it) <= segmentIndex }
}

/** Logical placement grid for newly created diagram elements (draw.io's default step). */
internal const val DIAGRAM_GRID_STEP = 10.0

/**
 * Snaps a creation coordinate to the placement grid: stamped and dropped shapes land on
 * round positions instead of writing the pointer's fractional document coordinate into
 * the source. Moves/resizes are not snapped here — the magnet owns those gestures.
 */
internal fun snapToDiagramGrid(value: Double): Double =
    round(value / DIAGRAM_GRID_STEP) * DIAGRAM_GRID_STEP

/** Fresh graph-unique id `prefix-N` across nodes, edges, layers and groups. */
internal fun mintDiagramId(graph: DiagramGraph, prefix: String): String {
    val taken = buildSet {
        graph.nodes.forEach { add(it.id.value) }
        graph.edges.forEach { add(it.id.value) }
        graph.layers.forEach { add(it.id.value) }
        graph.groups.forEach { add(it.id.value) }
    }
    var index = 1
    while ("$prefix-$index" in taken) index++
    return "$prefix-$index"
}

/** Graph-local rect the inline text editor anchors to, or null when the target vanished. */
private fun diagramTextEditRect(
    target: DiagramTextEditTarget,
    graph: DiagramGraph,
    routes: Map<DiagramEdgeId, List<DiagramPoint>>,
): DiagramRect? = when (target) {
    // The same box the renderer draws the caption in, so entering edit mode does not move the
    // text and the plate cannot swallow the shape around it.
    is DiagramTextEditTarget.NodeLabel ->
        graph.nodeById(DiagramNodeId(target.elementId))?.let { it.labelBox(it.labelPadding()) }

    is DiagramTextEditTarget.TableCell -> {
        val node = graph.nodeById(DiagramNodeId(target.elementId))
        val table = node?.payload as? TableNode
        if (node == null || table == null || table.rowCount == 0 || table.columnCount == 0) {
            null
        } else {
            val rowSpans = scaledTrackSpans(table.rows.map { it.height }, node.height)
            val columnSpans = scaledTrackSpans(table.columns.map { it.width }, node.width)
            val row = target.row.coerceIn(0, table.rowCount - 1)
            val column = target.column.coerceIn(0, table.columnCount - 1)
            DiagramRect(
                x = node.x + columnSpans[column].first,
                y = node.y + rowSpans[row].first,
                width = columnSpans[column].second,
                height = rowSpans[row].second,
            )
        }
    }

    is DiagramTextEditTarget.EdgeLabel -> {
        val edge = graph.edgeById(DiagramEdgeId(target.edgeId))
        val route = routes[DiagramEdgeId(target.edgeId)]
        if (edge == null || route == null || route.size < 2) {
            null
        } else {
            val label = edge.labels.firstOrNull { it.position == target.position }
            val anchor = if (label != null) {
                edgeLabelAnchorPoint(route, label, edgeLabelObstacleRoutes(graph, routes, edge.id), edgeLabelAvoidRects(graph, edge.id))
            } else {
                route[route.size / 2]
            }
            DiagramRect(anchor.x - 60.0, anchor.y - 12.0, 120.0, 24.0)
        }
    }
}

/** Current text of the edited label / cell, as the inline editor's initial draft. */
private fun diagramTextEditInitialText(target: DiagramTextEditTarget, graph: DiagramGraph): String = when (target) {
    is DiagramTextEditTarget.NodeLabel ->
        graph.nodeById(DiagramNodeId(target.elementId))?.primaryText().orEmpty()

    is DiagramTextEditTarget.TableCell -> {
        val table = graph.nodeById(DiagramNodeId(target.elementId))?.payload as? TableNode
        table?.cellAt(target.row, target.column)?.label?.text.orEmpty()
    }

    is DiagramTextEditTarget.EdgeLabel ->
        graph.edgeById(DiagramEdgeId(target.edgeId))
            ?.labels
            ?.firstOrNull { it.position == target.position }
            ?.label
            ?.text
            .orEmpty()
}

/** (start, size) of each track scaled so the tracks fill [extent] (hit-test parity). */
private fun scaledTrackSpans(sizes: List<Double>, extent: Double): List<Pair<Double, Double>> {
    if (sizes.isEmpty()) return emptyList()
    val sum = sizes.sum()
    val scaled = if (sum > 0.0 && extent > 0.0) {
        sizes.map { it * extent / sum }
    } else {
        List(sizes.size) { if (extent > 0.0) extent / sizes.size else 0.0 }
    }
    var offset = 0.0
    return scaled.map { size ->
        val start = offset
        offset += size
        start to size
    }
}
