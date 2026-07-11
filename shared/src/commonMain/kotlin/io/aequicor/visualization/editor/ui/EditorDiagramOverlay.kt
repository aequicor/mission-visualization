package io.aequicor.visualization.editor.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.aequicor.visualization.MissionEditorStateHolder
import io.aequicor.visualization.editor.presentation.DesignEditorIntent
import io.aequicor.visualization.editor.presentation.DiagramEditorIntent
import io.aequicor.visualization.editor.presentation.DiagramSelection
import io.aequicor.visualization.editor.presentation.DiagramTool
import io.aequicor.visualization.editor.presentation.zoomFactorForScroll
import io.aequicor.visualization.editor.ui.theme.LocalEditorColors
import io.aequicor.visualization.engine.backend.compose.CanvasViewport
import io.aequicor.visualization.engine.ir.layout.LayoutBox
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.subsystems.diagrams.compose.DiagramDirectionalArrowsOverlay
import io.aequicor.visualization.subsystems.diagrams.compose.DiagramOverlayStyle
import io.aequicor.visualization.subsystems.diagrams.compose.DiagramPortsOverlay
import io.aequicor.visualization.subsystems.diagrams.compose.DiagramPreviewStyle
import io.aequicor.visualization.subsystems.diagrams.compose.DiagramSelectionOverlay
import io.aequicor.visualization.subsystems.diagrams.compose.DiagramWaypointOverlay
import io.aequicor.visualization.subsystems.diagrams.compose.rememberDiagramRoutes
import io.aequicor.visualization.subsystems.diagrams.hittest.DiagramHit
import io.aequicor.visualization.subsystems.diagrams.hittest.DiagramNodeHitPart
import io.aequicor.visualization.subsystems.diagrams.hittest.DiagramResizeHandle
import io.aequicor.visualization.subsystems.diagrams.hittest.edgeLabelAnchorPoint
import io.aequicor.visualization.subsystems.diagrams.hittest.hitTest
import io.aequicor.visualization.subsystems.diagrams.model.BpmnNodeKind
import io.aequicor.visualization.subsystems.diagrams.model.DiagramEdgeId
import io.aequicor.visualization.subsystems.diagrams.model.DiagramEdgeLabelPosition
import io.aequicor.visualization.subsystems.diagrams.model.DiagramEndpoint
import io.aequicor.visualization.subsystems.diagrams.model.DiagramGraph
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNode
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodeId
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodePayload
import io.aequicor.visualization.subsystems.diagrams.model.DiagramRelation
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
import io.aequicor.visualization.subsystems.diagrams.path.DiagramPoint
import io.aequicor.visualization.subsystems.diagrams.path.DiagramRect
import io.aequicor.visualization.subsystems.diagrams.routing.RoutingOptions
import io.aequicor.visualization.subsystems.diagrams.routing.routeAllEdgesLenient
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.time.TimeSource

// --- Node-type palette (shared by the toolbar flyout and the inspector) -------

/** One row of the diagram node-type palette: display label + representative payload + stamp size. */
internal data class DiagramPaletteEntry(
    val label: String,
    val payload: DiagramNodePayload,
    val width: Double = 120.0,
    val height: Double = 60.0,
)

/**
 * The node-type palette: basic shapes, table, container/swimlane, the full UML set,
 * flowchart, ER and BPMN primitives. Order groups families for the flyout menu.
 */
internal val DiagramNodePalette: List<DiagramPaletteEntry> = listOf(
    DiagramPaletteEntry("Rectangle", DiagramNodePayload.BasicShape(DiagramShapeKind.RECTANGLE)),
    DiagramPaletteEntry("Rounded rectangle", DiagramNodePayload.BasicShape(DiagramShapeKind.ROUNDED_RECTANGLE)),
    DiagramPaletteEntry("Ellipse", DiagramNodePayload.BasicShape(DiagramShapeKind.ELLIPSE)),
    DiagramPaletteEntry("Text", DiagramNodePayload.BasicShape(DiagramShapeKind.TEXT)),
    DiagramPaletteEntry("Rhombus", DiagramNodePayload.BasicShape(DiagramShapeKind.RHOMBUS), width = 120.0, height = 80.0),
    DiagramPaletteEntry("Triangle", DiagramNodePayload.BasicShape(DiagramShapeKind.TRIANGLE), width = 100.0, height = 80.0),
    DiagramPaletteEntry("Hexagon", DiagramNodePayload.BasicShape(DiagramShapeKind.HEXAGON), width = 130.0, height = 70.0),
    DiagramPaletteEntry("Parallelogram", DiagramNodePayload.BasicShape(DiagramShapeKind.PARALLELOGRAM), width = 140.0, height = 60.0),
    DiagramPaletteEntry("Trapezoid", DiagramNodePayload.BasicShape(DiagramShapeKind.TRAPEZOID), width = 140.0, height = 60.0),
    DiagramPaletteEntry("Cylinder", DiagramNodePayload.BasicShape(DiagramShapeKind.CYLINDER), width = 100.0, height = 80.0),
    DiagramPaletteEntry("Cloud", DiagramNodePayload.BasicShape(DiagramShapeKind.CLOUD), width = 140.0, height = 80.0),
    DiagramPaletteEntry(
        "Table",
        TableNode(rows = List(3) { TableRow() }, columns = List(3) { TableColumn() }),
        width = 300.0,
        height = 120.0,
    ),
    DiagramPaletteEntry("Container", DiagramNodePayload.ContainerNode(), width = 240.0, height = 160.0),
    DiagramPaletteEntry(
        "Swimlane",
        DiagramNodePayload.SwimlaneNode(lanes = listOf(SwimlaneLane(), SwimlaneLane())),
        width = 360.0,
        height = 240.0,
    ),
    DiagramPaletteEntry("UML class", UmlClassNode(name = "Class"), width = 160.0, height = 108.0),
    DiagramPaletteEntry("UML lifeline", UmlLifelineNode(name = "Object"), width = 120.0, height = 200.0),
    DiagramPaletteEntry("UML state", UmlStateNode(name = "State"), width = 140.0, height = 56.0),
    DiagramPaletteEntry("UML activity", UmlActivityNode(UmlActivityKind.ACTION, name = "Action"), width = 140.0, height = 56.0),
    DiagramPaletteEntry("UML actor", UmlActorNode(name = "Actor"), width = 60.0, height = 90.0),
    DiagramPaletteEntry("UML use case", UmlUseCaseNode(name = "Use case"), width = 150.0, height = 70.0),
    DiagramPaletteEntry("UML component", UmlComponentNode(name = "Component"), width = 160.0, height = 80.0),
    DiagramPaletteEntry("UML deployment", UmlDeploymentNode(name = "Node"), width = 160.0, height = 100.0),
    DiagramPaletteEntry("UML note", UmlNoteNode(text = "Note"), width = 140.0, height = 80.0),
    DiagramPaletteEntry("UML package", UmlPackageNode(name = "Package"), width = 160.0, height = 100.0),
    DiagramPaletteEntry("Flowchart process", DiagramNodePayload.FlowchartNode(FlowchartNodeKind.PROCESS), width = 140.0, height = 60.0),
    DiagramPaletteEntry("Flowchart decision", DiagramNodePayload.FlowchartNode(FlowchartNodeKind.DECISION), width = 130.0, height = 90.0),
    DiagramPaletteEntry("Flowchart input/output", DiagramNodePayload.FlowchartNode(FlowchartNodeKind.INPUT_OUTPUT), width = 150.0, height = 60.0),
    DiagramPaletteEntry("Flowchart terminator", DiagramNodePayload.FlowchartNode(FlowchartNodeKind.TERMINATOR), width = 140.0, height = 50.0),
    DiagramPaletteEntry(
        "ER entity",
        DiagramNodePayload.ErEntityNode(name = "Entity", attributes = listOf(ErAttribute("id", "int", primaryKey = true))),
        width = 180.0,
        height = 100.0,
    ),
    DiagramPaletteEntry("BPMN task", DiagramNodePayload.BpmnNode(BpmnNodeKind.TASK), width = 140.0, height = 70.0),
    DiagramPaletteEntry("BPMN event", DiagramNodePayload.BpmnNode(BpmnNodeKind.EVENT), width = 60.0, height = 60.0),
    DiagramPaletteEntry("BPMN gateway", DiagramNodePayload.BpmnNode(BpmnNodeKind.GATEWAY), width = 70.0, height = 70.0),
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

/** Distance (graph units) of the hover directional chevrons from the node bounds. */
private const val DiagramArrowDistance = 14f

/** What an in-flight edge-draw drag looks like (graph coordinates). */
private data class DiagramEdgePreview(val from: DiagramPoint, val to: DiagramPoint)

/** Target of the inline diagram text editor. */
private sealed interface DiagramTextEditTarget {
    data class NodeLabel(val elementId: String) : DiagramTextEditTarget

    data class TableCell(val elementId: String, val row: Int, val column: Int) : DiagramTextEditTarget

    data class EdgeLabel(val edgeId: String, val position: DiagramEdgeLabelPosition) : DiagramTextEditTarget
}

/**
 * Diagram edit mode overlay: while [io.aequicor.visualization.editor.presentation.EditorWorkspaceState.diagramEditNodeId]
 * targets a diagram IR node, this layer owns every gesture inside the node's box —
 * click/shift-click element selection via the core hit-test, node drag-move and
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
    var edgePreview by remember(editId) { mutableStateOf<DiagramEdgePreview?>(null) }
    var textEdit by remember(editId) { mutableStateOf<DiagramTextEditTarget?>(null) }
    var lastTapMark by remember(editId) { mutableStateOf<TimeSource.Monotonic.ValueTimeMark?>(null) }
    var lastTapKey by remember(editId) { mutableStateOf("") }

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
        DiagramSelectionOverlay(
            graph = graph,
            selectedNodeIds = selectedNodeIds,
            modifier = Modifier.fillMaxSize(),
            style = overlayStyle,
            handleSize = (7f / zoomPx).coerceIn(2f, 24f),
        )
        val portNodeIds = selectedNodeIds + setOfNotNull(hoveredElementId?.let(::DiagramNodeId))
        DiagramPortsOverlay(graph, portNodeIds, Modifier.fillMaxSize(), overlayStyle)
        DiagramWaypointOverlay(graph, selectedEdgeIds, routes, Modifier.fillMaxSize(), overlayStyle)
        DiagramDirectionalArrowsOverlay(
            graph = graph,
            nodeId = hoveredElementId?.let(::DiagramNodeId),
            modifier = Modifier.fillMaxSize(),
            style = overlayStyle,
            distance = DiagramArrowDistance,
        )
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
                                    val factor = zoomFactorForScroll(-change.scrollDelta.y)
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
                                    val scrollX = if (modifiers.isShiftPressed) change.scrollDelta.y else change.scrollDelta.x
                                    val scrollY = if (modifiers.isShiftPressed) 0f else change.scrollDelta.y
                                    state.updateWorkspace {
                                        it.copy(viewport = it.viewport.panByScreenDelta(-scrollX, -scrollY, density))
                                    }
                                }
                                change.consume()
                            }
                            PointerEventType.Move -> {
                                val change = event.changes.firstOrNull() ?: continue
                                if (!change.pressed) {
                                    val liveBox = state.artboardLayout?.findBySourceId(editId)
                                    val live = liveGraph()
                                    hoveredElementId = if (liveBox != null && live != null) {
                                        val point = DiagramPoint(
                                            latestViewport.toDocX(change.position.x) - liveBox.x,
                                            latestViewport.toDocY(change.position.y) - liveBox.y,
                                        )
                                        hitDiagramNodeAt(live, point)?.value
                                    } else {
                                        null
                                    }
                                }
                            }
                            PointerEventType.Exit -> hoveredElementId = null
                            else -> Unit
                        }
                    }
                }
            }
            .pointerInput(editId) {
                awaitEachGesture {
                    val down = awaitFirstDown()
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
                    val shiftHeld = currentEvent.keyboardModifiers.isShiftPressed

                    fun graphPointOf(position: Offset): DiagramPoint = DiagramPoint(
                        latestViewport.toDocX(position.x) - liveBox.x,
                        latestViewport.toDocY(position.y) - liveBox.y,
                    )

                    // Presses outside the diagram box (plus the arrow margin) leave edit mode and
                    // stay unconsumed so the main canvas handler processes them normally.
                    val margin = ((DiagramArrowDistance + 10f) / zoom).toDouble()
                    val insideBox = docX >= liveBox.x - margin && docX <= liveBox.right + margin &&
                        docY >= liveBox.y - margin && docY <= liveBox.bottom + margin
                    if (!insideBox) {
                        textEdit = null
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
                                x = point.x - width / 2,
                                y = point.y - height / 2,
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

                    // A press on a hovered node's directional chevron drags out a new edge.
                    val hoverNode = hoveredElementId?.let { live.nodeById(DiagramNodeId(it)) }
                    if (hoverNode != null &&
                        directionalArrowHit(hoverNode, point, DiagramArrowDistance.toDouble(), tolerance + 4.0)
                    ) {
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
                        )
                        return@awaitEachGesture
                    }

                    val routedPoints = routeAllEdgesLenient(live, RoutingOptions.Default)
                        .mapValues { (_, routed) -> routed.points }
                    val typedSelectedNodes = state.workspace.diagramSelection.elementIds.map(::DiagramNodeId).toSet()
                    val typedSelectedEdges = state.workspace.diagramSelection.edgeIds.map(::DiagramEdgeId).toSet()
                    val hit = hitTest(live, routedPoints, point, tolerance, typedSelectedNodes, typedSelectedEdges)

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

                        is DiagramHit.LabelHandle -> {
                            val edge = live.edgeById(hit.edgeId) ?: return@awaitEachGesture
                            val label = edge.labels.firstOrNull { it.position == hit.position } ?: return@awaitEachGesture
                            val route = routedPoints[hit.edgeId] ?: return@awaitEachGesture
                            val anchor = edgeLabelAnchorPoint(route, label)
                            val base = DiagramPoint(anchor.x - label.offsetX, anchor.y - label.offsetY)
                            dragDiagramFrames(state, down) { position ->
                                val at = graphPointOf(position)
                                state.dispatch(
                                    DiagramEditorIntent.MoveDiagramEdgeLabel(
                                        editId, hit.edgeId.value, hit.position, at.x - base.x, at.y - base.y,
                                    ),
                                )
                            }
                        }

                        is DiagramHit.Port -> {
                            // Dragging from a declared port draws a new edge pinned to it.
                            dragNewDiagramEdge(
                                state = state,
                                editId = editId,
                                source = DiagramEndpoint.FixedPort(hit.nodeId, hit.portId),
                                sourceNodeId = hit.nodeId,
                                down = down,
                                start = point,
                                relation = DiagramRelation.Plain,
                                graphPointOf = ::graphPointOf,
                                liveGraph = ::liveGraph,
                                setPreview = { edgePreview = it },
                            )
                        }

                        is DiagramHit.Edge -> {
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
                                    shiftHeld && edgeId in current.edgeIds ->
                                        current.copy(edgeIds = current.edgeIds - edgeId)
                                    shiftHeld -> current.copy(edgeIds = current.edgeIds + edgeId)
                                    else -> DiagramSelection(edgeIds = setOf(edgeId))
                                }
                                it.copy(diagramSelection = next)
                            }
                        }

                        is DiagramHit.Node -> {
                            val elementId = hit.nodeId.value
                            val now = TimeSource.Monotonic.markNow()
                            val tapKey = "node:$elementId"
                            val doubleClick = lastTapKey == tapKey &&
                                (lastTapMark?.let { (now - it).inWholeMilliseconds < 320 } ?: false)
                            lastTapMark = now
                            lastTapKey = tapKey

                            if (doubleClick) {
                                textEdit = when (val part = hit.part) {
                                    is DiagramNodeHitPart.TableCellPart ->
                                        DiagramTextEditTarget.TableCell(elementId, part.row, part.column)
                                    else -> DiagramTextEditTarget.NodeLabel(elementId)
                                }
                                return@awaitEachGesture
                            }

                            // Pre-press selection so the drag moves the pressed element.
                            val current = state.workspace.diagramSelection
                            if (!shiftHeld && elementId !in current.elementIds) {
                                state.updateWorkspace {
                                    it.copy(diagramSelection = DiagramSelection(elementIds = setOf(elementId)))
                                }
                            }
                            val moved = dragDiagramFrames(state, down) { position, last ->
                                val at = graphPointOf(position)
                                val prev = graphPointOf(last)
                                val dx = at.x - prev.x
                                val dy = at.y - prev.y
                                if (dx != 0.0 || dy != 0.0) {
                                    state.workspace.diagramSelection.elementIds.forEach { movedId ->
                                        state.dispatch(DiagramEditorIntent.MoveDiagramNode(editId, movedId, dx, dy))
                                    }
                                }
                            }
                            if (!moved && shiftHeld) {
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
                            // Empty diagram area: a plain click clears the element selection.
                            val moved = dragDiagramFrames(state, down) { _ -> }
                            if (!moved && !shiftHeld) {
                                state.updateWorkspace { it.copy(diagramSelection = DiagramSelection.Empty) }
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
        // Live preview of an edge being dragged out.
        edgePreview?.let { preview ->
            val from = viewport.toScreen(box.x + preview.from.x, box.y + preview.from.y)
            val to = viewport.toScreen(box.x + preview.to.x, box.y + preview.to.y)
            drawLine(
                color = colors.accent,
                start = from,
                end = to,
                strokeWidth = 1.5f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 4f)),
            )
            drawCircle(colors.accent, radius = 3.5f, center = to)
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
            onDone = { textEdit = null },
        )
    }
}

/** Small floating text field over the edited label; Enter commits, Escape cancels. */
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
    onDone: () -> Unit,
) {
    val colors = LocalEditorColors.current
    val anchorRect = diagramTextEditRect(target, graph, routes) ?: run {
        onDone()
        return
    }
    val initial = diagramTextEditInitialText(target, graph)
    var draft by remember(target) { mutableStateOf(initial) }
    val focusRequester = remember(target) { FocusRequester() }

    fun commit() {
        val text = draft.takeIf { it.isNotBlank() }?.trim()
        when (target) {
            is DiagramTextEditTarget.NodeLabel ->
                state.dispatch(DiagramEditorIntent.SetDiagramNodeLabel(editId, target.elementId, text))
            is DiagramTextEditTarget.TableCell ->
                state.dispatch(
                    DiagramEditorIntent.SetDiagramTableCellText(editId, target.elementId, target.row, target.column, text),
                )
            is DiagramTextEditTarget.EdgeLabel ->
                state.dispatch(DiagramEditorIntent.SetDiagramEdgeLabel(editId, target.edgeId, target.position, text))
        }
        onDone()
    }

    val topLeft = viewport.toScreen(box.x + anchorRect.x, box.y + anchorRect.y)
    val widthPx = (anchorRect.width * zoomPx).toFloat().coerceAtLeast(96f)
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    Surface(
        modifier = Modifier
            .offset { IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt()) }
            .width((widthPx / density).dp),
        shape = RoundedCornerShape(5.dp),
        color = colors.raisedSurface,
        border = BorderStroke(1.dp, colors.accent),
        shadowElevation = 4.dp,
    ) {
        BasicTextField(
            value = draft,
            onValueChange = { draft = it },
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .focusRequester(focusRequester)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.Enter, Key.NumPadEnter -> {
                            commit()
                            true
                        }
                        Key.Escape -> {
                            onDone()
                            true
                        }
                        else -> false
                    }
                },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall.copy(color = colors.ink),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(colors.accent),
        )
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
 * Drags a new connector out of [source]: dashed preview while dragging; on release the
 * edge lands as floating on the node under the pointer (Alt pins to its nearest declared
 * port), or as a free point in empty space. Self-loops are dropped.
 */
private suspend fun AwaitPointerEventScope.dragNewDiagramEdge(
    state: MissionEditorStateHolder,
    editId: String,
    source: DiagramEndpoint,
    sourceNodeId: DiagramNodeId,
    down: PointerInputChange,
    start: DiagramPoint,
    relation: DiagramRelation,
    graphPointOf: (Offset) -> DiagramPoint,
    liveGraph: () -> DiagramGraph?,
    setPreview: (DiagramEdgePreview?) -> Unit,
) {
    var current = start
    var altHeld = false
    var moved = false
    val slop = viewConfiguration.touchSlop
    while (true) {
        val event = awaitPointerEvent()
        altHeld = event.keyboardModifiers.isAltPressed
        val change = event.changes.firstOrNull() ?: break
        current = graphPointOf(change.position)
        if (change.changedToUp()) break
        if ((change.position - down.position).getDistance() >= slop) moved = true
        if (moved) setPreview(DiagramEdgePreview(start, current))
        change.consume()
    }
    setPreview(null)
    if (!moved) return
    val graph = liveGraph() ?: return
    val targetNode = hitDiagramNodeAt(graph, current)?.let { graph.nodeById(it) }
    if (targetNode?.id == sourceNodeId) return
    val target: DiagramEndpoint = when {
        targetNode != null && altHeld && targetNode.ports.isNotEmpty() -> {
            val nearest = targetNode.ports.minByOrNull { port ->
                val p = targetNode.portPosition(port)
                hypot(p.x - current.x, p.y - current.y)
            }
            if (nearest != null) {
                DiagramEndpoint.FixedPort(targetNode.id, nearest.id)
            } else {
                DiagramEndpoint.FloatingAnchor(targetNode.id)
            }
        }
        targetNode != null -> DiagramEndpoint.FloatingAnchor(targetNode.id)
        else -> DiagramEndpoint.FreePoint(current.x, current.y)
    }
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

/** Topmost visible, unlocked diagram node whose bounds contain [point] (list order = z-order). */
private fun hitDiagramNodeAt(graph: DiagramGraph, point: DiagramPoint): DiagramNodeId? =
    graph.nodes.asReversed()
        .firstOrNull { it.visible && !it.locked && it.bounds.contains(point) }
        ?.id

/** Whether [point] lands on one of the four hover directional chevrons of [node]. */
private fun directionalArrowHit(
    node: DiagramNode,
    point: DiagramPoint,
    distance: Double,
    radius: Double,
): Boolean {
    val bounds = node.bounds
    val tips = listOf(
        DiagramPoint(bounds.centerX, bounds.top - distance),
        DiagramPoint(bounds.right + distance, bounds.centerY),
        DiagramPoint(bounds.centerX, bounds.bottom + distance),
        DiagramPoint(bounds.left - distance, bounds.centerY),
    )
    return tips.any { hypot(it.x - point.x, it.y - point.y) <= radius }
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
    is DiagramTextEditTarget.NodeLabel ->
        graph.nodeById(DiagramNodeId(target.elementId))?.bounds

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
                edgeLabelAnchorPoint(route, label)
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
        graph.nodeById(DiagramNodeId(target.elementId))?.labels?.firstOrNull()?.text.orEmpty()

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
