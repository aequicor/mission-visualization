package io.aequicor.visualization.editor.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.isTertiaryPressed
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.time.TimeSource
import io.aequicor.visualization.MissionEditorStateHolder
import io.aequicor.visualization.editor.presentation.BoundsBox
import io.aequicor.visualization.editor.presentation.CanvasOperation
import io.aequicor.visualization.editor.presentation.DesignEditorIntent
import io.aequicor.visualization.editor.presentation.DesignEditorState
import io.aequicor.visualization.editor.presentation.DocumentRect
import io.aequicor.visualization.editor.presentation.DeviceMode
import io.aequicor.visualization.editor.presentation.EditorMode
import io.aequicor.visualization.editor.presentation.EditorTool
import io.aequicor.visualization.editor.presentation.FocusMode
import io.aequicor.visualization.editor.presentation.GapMeasurement
import io.aequicor.visualization.editor.presentation.GeoPoint
import io.aequicor.visualization.editor.presentation.HandleSide
import io.aequicor.visualization.editor.presentation.VectorVertexPart
import io.aequicor.visualization.editor.presentation.VectorVertexRef
import io.aequicor.visualization.editor.presentation.NewObjectKind
import io.aequicor.visualization.editor.presentation.PendingFit
import io.aequicor.visualization.editor.presentation.ResizeCursorKind
import io.aequicor.visualization.editor.presentation.SelectableBounds
import io.aequicor.visualization.editor.presentation.ResizeHandle
import io.aequicor.visualization.editor.presentation.WorkspaceLimits
import io.aequicor.visualization.editor.presentation.ZOrderMove
import io.aequicor.visualization.editor.presentation.angleFromCenterDegrees
import io.aequicor.visualization.editor.presentation.axisAlignedBounds
import io.aequicor.visualization.editor.presentation.LineSegment
import io.aequicor.visualization.editor.presentation.AnchorGuide
import io.aequicor.visualization.editor.presentation.AnchorKind
import io.aequicor.visualization.editor.presentation.AnchorResult
import io.aequicor.visualization.editor.presentation.SpacingBar
import io.aequicor.visualization.editor.presentation.centerAnchorLines
import io.aequicor.visualization.editor.presentation.computeResize
import io.aequicor.visualization.editor.presentation.computeAnchors
import io.aequicor.visualization.editor.presentation.translate
import io.aequicor.visualization.editor.presentation.flowInsertionIndex
import io.aequicor.visualization.editor.presentation.flowInsertionLine
import io.aequicor.visualization.editor.presentation.isCoordinatePositioned
import io.aequicor.visualization.editor.presentation.isSelfOrAncestor
import io.aequicor.visualization.editor.presentation.marqueeSelection
import io.aequicor.visualization.editor.presentation.measureGaps
import io.aequicor.visualization.editor.presentation.normalizeAngleDegrees
import io.aequicor.visualization.editor.presentation.parentNodeOf
import io.aequicor.visualization.editor.presentation.pressHitBelongsToSelection
import io.aequicor.visualization.editor.presentation.resizeCursorKindForHandle
import io.aequicor.visualization.editor.presentation.rotateAffordancePoint
import io.aequicor.visualization.editor.presentation.rotatePointAroundCenter
import io.aequicor.visualization.editor.presentation.rotateVector
import io.aequicor.visualization.editor.presentation.rotatedCorners
import io.aequicor.visualization.editor.presentation.rotatedHandlePoints
import io.aequicor.visualization.editor.presentation.snapAngleToIncrement
import io.aequicor.visualization.editor.presentation.zoomFactorForScroll
import io.aequicor.visualization.editor.ui.theme.LocalEditorColors
import io.aequicor.visualization.engine.backend.compose.CanvasViewport
import io.aequicor.visualization.engine.backend.compose.DesignArtboard
import io.aequicor.visualization.engine.backend.compose.selectableNodeId
import io.aequicor.visualization.engine.ir.geometry.Affine2D
import io.aequicor.visualization.engine.ir.geometry.RectD
import io.aequicor.visualization.engine.ir.geometry.meetFit
import io.aequicor.visualization.engine.ir.layout.LayoutBox
import io.aequicor.visualization.engine.ir.model.DesignDocument
import io.aequicor.visualization.engine.ir.model.DesignPoint
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.engine.ir.model.LayoutMode
import io.aequicor.visualization.engine.ir.model.ShapeType
import io.aequicor.visualization.engine.ir.model.VectorNetwork
import io.aequicor.visualization.engine.ir.model.VectorVertex
import io.aequicor.visualization.engine.ir.model.literalOrNull
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.round
import kotlin.math.sin

/**
 * Center canvas: viewport (zoom/pan/fit), the rendered artboard, and all direct
 * manipulation — hover, click / shift-click / marquee selection, drag-move, handle
 * resize, canvas rotation and tool-driven object creation — plus keyboard
 * nudge/duplicate/delete/undo. Every gesture maps into a [DesignEditorIntent]; the
 * workspace owns zoom/pan. The artboard renders content only ([DesignArtboard] is called
 * with `showSelection = false`): every overlay — hover, selection, handles, rotate
 * affordance, center lines and the Alt measurement preview — lives here so it can follow
 * rotated geometry and never touches the document model itself.
 */
@Composable
fun EditorCanvasPane(state: MissionEditorStateHolder, modifier: Modifier = Modifier) {
    val colors = LocalEditorColors.current
    val ws = state.workspace

    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(
            modifier = Modifier.fillMaxWidth().weight(1f),
            shape = RoundedCornerShape(8.dp),
            color = colors.paneSurface,
            shadowElevation = 0.dp,
        ) {
            CanvasSurface(state)
        }

        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            DeviceControl(ws.deviceMode) { mode -> state.updateWorkspace { it.copy(deviceMode = mode) } }
            SceneModeToggle(ws.mode) { mode -> state.updateWorkspace { it.copy(mode = mode) } }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                FloatingToolbar(ws.tool) { tool -> state.updateWorkspace { it.copy(tool = tool) } }
            }
            ZoomControls(state)
        }
    }
}

// --- Canvas surface with viewport + gestures --------------------------------

@Composable
private fun CanvasSurface(state: MissionEditorStateHolder) {
    val colors = LocalEditorColors.current
    val density = LocalDensity.current.density
    val design = state.designState
    val ws = state.workspace
    val document = design.document
    val pageId = design.selectedPageId
    val rootNode = document?.pageById(pageId)?.children?.firstOrNull()
    val textMeasurer = rememberTextMeasurer()

    BoxWithConstraints(Modifier.fillMaxSize().padding(4.dp)) {
        val canvasWpx = maxWidth.value * density
        val canvasHpx = maxHeight.value * density
        val layout = state.artboardLayout

        // Fit the current screen once per page (keeps the user's later zoom/pan).
        var fittedPage by remember { mutableStateOf("") }
        if (layout != null && canvasWpx > 0f && fittedPage != pageId) {
            fittedPage = pageId
            fitViewport(state, layout.x, layout.y, layout.width, layout.height, canvasWpx, canvasHpx, density)
        }
        // Honor an explicit fit-screen / fit-selection request from the zoom controls.
        if (layout != null && canvasWpx > 0f && ws.pendingFit != PendingFit.None) {
            val rect = when (ws.pendingFit) {
                PendingFit.Selection -> selectionBounds(layout, design.selectedNodeIds) ?: FitRect(layout.x, layout.y, layout.width, layout.height)
                else -> FitRect(layout.x, layout.y, layout.width, layout.height)
            }
            state.updateWorkspace { it.copy(pendingFit = PendingFit.None) }
            fitViewport(state, rect.x, rect.y, rect.w, rect.h, canvasWpx, canvasHpx, density)
        }

        // Smoothly ease +/-/1:1 button zoom around the canvas center (see [animateZoomTo]).
        LaunchedEffect(ws.pendingZoomTo, canvasWpx, canvasHpx) {
            val target = ws.pendingZoomTo ?: return@LaunchedEffect
            if (canvasWpx <= 0f || canvasHpx <= 0f) return@LaunchedEffect
            animateZoomTo(state, target, canvasWpx / 2f, canvasHpx / 2f, density)
            state.updateWorkspace { if (it.pendingZoomTo == target) it.copy(pendingZoomTo = null) else it }
        }

        val viewportModel = ws.viewport
        val zoomPx = viewportModel.zoomPx(density)
        val viewport = CanvasViewport(zoomPx, viewportModel.panXPx(density), viewportModel.panYPx(density))
        val multiSelectionBox = if (design.selectedNodeIds.size > 1) selectionHandleBounds(layout, design.selectedNodeIds) else null

        // Primary (single) selection geometry — the only case that gets rotated handles,
        // a rotate affordance and center lines; multi-selection keeps its plain bbox.
        val primarySelectionId = design.selectedNodeId.takeIf { design.selectedNodeIds.size == 1 && it.isNotBlank() }
        val primarySelectionLayoutBox = primarySelectionId?.let { layout?.findBySourceId(it) }
        val primarySelectionBox = primarySelectionLayoutBox?.toBoundsBox()
        val primarySelectionRotation = primarySelectionLayoutBox?.node?.rotation ?: 0.0
        val primarySelectionLocked = primarySelectionId?.let { document?.nodeById(it)?.locked == true } ?: false
        val primarySelectionInVectorEdit = primarySelectionId != null && primarySelectionId == ws.vectorEditNodeId
        val parentOfPrimarySelection = primarySelectionId
            ?.let { document?.parentNodeOf(it)?.id }
            ?.let { layout?.findBySourceId(it) }
            ?.let { it.toBoundsBox().visualBounds(it.node.rotation) }

        // Transient gesture visuals.
        var marquee by remember { mutableStateOf<Rect?>(null) }
        var createRect by remember { mutableStateOf<Rect?>(null) }
        var badge by remember { mutableStateOf<String?>(null) }
        var dragMoveActive by remember { mutableStateOf(false) }
        var hoverCursor by remember { mutableStateOf<PointerIcon?>(null) }
        // Live insertion-line preview while dragging an Auto layout child (design-book §18
        // "Auto layout children should reorder ... during drag"); null outside such a drag.
        var reorderPreview by remember { mutableStateOf<ReorderPreview?>(null) }
        // Beautiful-anchor guides drawn while free-moving a node (design-book §18 + "beautiful
        // positions": center, golden ratio, simple proportions); empty outside a move drag.
        var snapGuides by remember { mutableStateOf<List<AnchorGuide>>(emptyList()) }
        // Equal-spacing distribution bars for the same drag; empty otherwise.
        var spacingBars by remember { mutableStateOf<List<SpacingBar>>(emptyList()) }

        // Keyboard focus + modifier tracking (Shift for additive select / big nudge, Space
        // for pan, Alt for the read-only measurement overlay).
        val focusRequester = remember { FocusRequester() }
        var shiftHeld by remember { mutableStateOf(false) }
        var altHeld by remember { mutableStateOf(false) }
        var spaceHeld by remember { mutableStateOf(false) }
        var lastTapMark by remember { mutableStateOf<TimeSource.Monotonic.ValueTimeMark?>(null) }
        var lastTapId by remember { mutableStateOf("") }
        LaunchedEffect(pageId) { runCatching { focusRequester.requestFocus() } }
        // A stale handle cursor must not survive a tool/selection change without a pointer move.
        LaunchedEffect(ws.tool, spaceHeld, design.selectedNodeId, design.selectedNodeIds, primarySelectionRotation) { hoverCursor = null }

        // Background dots.
        Canvas(Modifier.matchParentSize()) {
            val step = 14.dp.toPx()
            var x = 0f
            while (x < size.width) {
                var y = 0f
                while (y < size.height) {
                    drawCircle(colors.canvasDot, radius = 0.7.dp.toPx(), center = Offset(x, y))
                    y += step
                }
                x += step
            }
        }

        if (document != null && rootNode != null && ws.mode == EditorMode.Scene) {
            // Scene mode: play prototype behavior instead of editing (design-book §19).
            SceneStage(state, viewport, Modifier.fillMaxSize())
        } else if (document != null && rootNode != null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .focusRequester(focusRequester)
                    .focusTarget()
                    .onPreviewKeyEvent { event ->
                        shiftHeld = event.isShiftPressed
                        altHeld = event.isAltPressed
                        if (event.key == Key.Spacebar && state.designState.editingTextNodeId.isBlank()) {
                            spaceHeld = event.type == KeyEventType.KeyDown
                            return@onPreviewKeyEvent true
                        }
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        handleCanvasKey(state, event.key, event.isShiftPressed, event.isCtrlPressed || event.isMetaPressed)
                    }
                    // Hover + scroll (pan / ctrl-zoom) + per-position resize cursor.
                    .pointerInput(pageId, viewport, ws.tool) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                when (event.type) {
                                    PointerEventType.Scroll -> {
                                        val change = event.changes.firstOrNull() ?: continue
                                        // A wheel/trackpad interaction supersedes any in-flight button-zoom animation.
                                        if (state.workspace.pendingZoomTo != null) {
                                            state.updateWorkspace { it.copy(pendingZoomTo = null) }
                                        }
                                        val modifiers = event.keyboardModifiers
                                        if (modifiers.isCtrlPressed || modifiers.isMetaPressed) {
                                            zoomAt(state, change.position, -change.scrollDelta.y, density)
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
                                            // Compare against live values: `design`/`ws` are frozen at
                                            // pointerInput setup, so this must re-read current state.
                                            val liveDesign = state.designState
                                            val liveLayout = state.artboardLayout
                                            val hit = hitNode(liveLayout, liveDesign.document, viewport, change.position)
                                            if (hit != state.workspace.hoveredNodeId) {
                                                state.updateWorkspace { it.copy(hoveredNodeId = hit) }
                                            }
                                            hoverCursor = resolveHandleCursor(
                                                liveDesign, liveLayout, viewport, change.position, ws.tool, spaceHeld,
                                                vectorEditNodeId = state.workspace.vectorEditNodeId,
                                            )
                                        }
                                    }
                                    else -> Unit
                                }
                            }
                        }
                    }
                    // Press / drag / tap.
                    .pointerInput(pageId, viewport, ws.tool, design.selectedNodeId, design.selectedNodeIds, spaceHeld) {
                        val slop = viewConfiguration.touchSlop
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            runCatching { focusRequester.requestFocus() }
                            val start = down.position
                            // The pointerInput coroutine can outlive document/layout recompositions
                            // during resize. Read a fresh press-time snapshot so the next press hit-tests
                            // the handles that are currently drawn, not the geometry from setup time.
                            val pressDesign = state.designState
                            val pressWorkspace = state.workspace
                            val pressDocument = pressDesign.document ?: return@awaitEachGesture
                            val pressLayout = state.artboardLayout
                            val pressRootId = pressDocument.pageById(pressDesign.selectedPageId)
                                ?.children
                                ?.firstOrNull()
                                ?.id
                                .orEmpty()
                            val forcePan = currentEvent.buttons.isTertiaryPressed || spaceHeld
                            val primaryBox = pressDesign.selectedNodeId.takeIf { it.isNotBlank() }?.let { pressLayout?.findBySourceId(it) }
                            // A locked selection exposes no resize handles (design-book §7).
                            val selectionLocked = pressDesign.selectedNodeIds.any { id -> pressDocument.nodeById(id)?.locked == true }
                            val isSingleSelection = pressDesign.selectedNodeIds.size == 1
                            val inVectorEdit = isSingleSelection && pressDesign.selectedNodeId == pressWorkspace.vectorEditNodeId
                            val selectedResizeBox = if (pressDesign.selectedNodeIds.size > 1) {
                                selectionHandleBounds(pressLayout, pressDesign.selectedNodeIds)
                            } else {
                                primaryBox?.toBoundsBox()
                            }
                            val selectionRotation = if (isSingleSelection) primaryBox?.node?.rotation ?: 0.0 else 0.0
                            val handlesActive = !forcePan && pressWorkspace.tool == EditorTool.Select && !selectionLocked && !inVectorEdit
                            val handle = selectedResizeBox?.takeIf { handlesActive }
                                ?.let { box -> rotatedHandleAt(box, selectionRotation, viewport, start) }
                            val rotateHit = handle == null && isSingleSelection &&
                                selectedResizeBox?.takeIf { handlesActive }?.let { box ->
                                    val offsetDoc = (RotateHandleScreenOffsetPx / zoomPx).toDouble()
                                    val point = rotateAffordancePoint(box, selectionRotation, offsetDoc)
                                    (viewport.toScreen(point.x, point.y) - start).getDistance() <= HandleHitRadiusPx
                                } == true
                            val hitId = hitNode(pressLayout, pressDocument, viewport, start)
                            val mode = resolveCanvasOperation(pressWorkspace.tool, forcePan, handle, rotateHit, hitId)

                            // A press whose top-most hit is the current selection — or a descendant
                            // showing through inside a selected container — drags the selection instead
                            // of grabbing the nested/behind object under the cursor (design-book §10
                            // "drag moves object"; a nested object is reached by double-click). An
                            // unrelated object stacked on top is not part of the selection, so it still
                            // wins the press (§10 "topmost selectable layer gets priority").
                            val pressOnSelection = pressDocument.pressHitBelongsToSelection(pressDesign.selectedNodeIds, hitId)

                            // Pre-press selection so a drag moves the pressed node — but never when the
                            // press already lands on the current selection (reselecting there would grab
                            // the nested element) nor on a shift-add.
                            if (mode == CanvasOperation.Move && !pressOnSelection && hitId !in pressDesign.selectedNodeIds && !shiftHeld) {
                                state.dispatch(DesignEditorIntent.SelectNode(hitId))
                            }
                            val moveStartPositions = if (mode == CanvasOperation.Move) {
                                state.designState.selectedNodeIds.mapNotNull { id ->
                                    state.designState.document?.nodeById(id)?.position?.let { position -> id to position }
                                }.toMap()
                            } else {
                                emptyMap()
                            }
                            // A single Auto layout child (no free/absolute positioning) can't be
                            // moved via MoveNodes (the layout engine ignores its `position`), so a
                            // Move drag on one instead previews a reorder within its flow parent.
                            val reorderBaseline = (mode == CanvasOperation.Move).let {
                                if (!it) return@let null
                                val singleId = state.designState.selectedNodeIds.singleOrNull() ?: return@let null
                                if (pressDocument.isCoordinatePositioned(singleId)) return@let null
                                val parentId = pressDocument.parentNodeOf(singleId)?.id ?: return@let null
                                val parentBox = pressLayout?.findBySourceId(parentId) ?: return@let null
                                val horizontal = when (parentBox.node.layout.mode) {
                                    LayoutMode.Horizontal -> true
                                    LayoutMode.Vertical -> false
                                    else -> return@let null
                                }
                                val flowChildren = parentBox.children.filter { child -> !child.node.layoutChild.absolute }
                                ReorderBaseline(
                                    nodeId = singleId,
                                    parentId = parentId,
                                    parentBox = parentBox.toBoundsBox(),
                                    horizontal = horizontal,
                                    siblings = flowChildren.filterNot { child -> child.node.sourceId == singleId }.map { child -> child.toBoundsBox() },
                                    originalIndex = flowChildren.indexOfFirst { child -> child.node.sourceId == singleId },
                                )
                            }
                            // Beautiful-anchor candidates for a free move (not a reorder): the dragged
                            // selection's start union bounds, its sibling peers, and the containers to
                            // anchor against — the immediate parent frame plus its unrotated ancestors
                            // up to the root, so a nested node can still find the outer/root container's
                            // center, edges, golden and proportion lines. Disabled when the parent frame
                            // is rotated (snapping assumes an axis-aligned container coordinate space).
                            val snapBaseline = if (mode == CanvasOperation.Move && reorderBaseline == null) {
                                val ids = state.designState.selectedNodeIds
                                val boxes = ids.mapNotNull { id -> pressLayout?.findBySourceId(id) }
                                val parentBox = pressDocument.parentNodeOf(state.designState.selectedNodeId)?.id
                                    ?.let { pressLayout?.findBySourceId(it) }
                                if (boxes.isEmpty() || parentBox == null || parentBox.node.rotation != 0.0) {
                                    null
                                } else {
                                    val corners = boxes.flatMap { box ->
                                        val vb = box.toBoundsBox().visualBounds(box.node.rotation)
                                        listOf(GeoPoint(vb.x, vb.y), GeoPoint(vb.right, vb.bottom))
                                    }
                                    val siblings = parentBox.children
                                        .filter { it.node.sourceId !in ids }
                                        .map { it.toBoundsBox() }
                                    val containers = buildList {
                                        add(parentBox.toBoundsBox())
                                        var ancestorId = pressDocument.parentNodeOf(parentBox.node.sourceId)?.id
                                        while (ancestorId != null) {
                                            val ancestorBox = pressLayout?.findBySourceId(ancestorId)
                                            if (ancestorBox != null && ancestorBox.node.rotation == 0.0) {
                                                add(ancestorBox.toBoundsBox())
                                            }
                                            ancestorId = pressDocument.parentNodeOf(ancestorId)?.id
                                        }
                                    }
                                    SnapBaseline(axisAlignedBounds(corners), containers, siblings)
                                }
                            } else {
                                null
                            }

                            var moved = false
                            var operationBegan = false
                            var documentBegan = false
                            var canceled = false
                            var last = start
                            val resizeBaseline = (mode as? CanvasOperation.Resize)?.let { selectedResizeBox }
                            val resizeTargets = (mode as? CanvasOperation.Resize)?.let {
                                resizeTargets(pressDocument, pressLayout, state.designState.selectedNodeIds)
                            }.orEmpty()
                            val rotateBaseline = if (mode == CanvasOperation.Rotate && selectedResizeBox != null && pressDesign.selectedNodeId.isNotBlank()) {
                                val center = GeoPoint(selectedResizeBox.centerX, selectedResizeBox.centerY)
                                RotateBaseline(
                                    nodeId = pressDesign.selectedNodeId,
                                    center = center,
                                    startAngle = angleFromCenterDegrees(center, GeoPoint(viewport.toDocX(start.x), viewport.toDocY(start.y))),
                                    startRotation = selectionRotation,
                                )
                            } else {
                                null
                            }
                            var accX = 0f
                            var accY = 0f
                            // Total snapped displacement already dispatched this drag, so each frame
                            // dispatches only the delta needed to reach the (absolute) snapped target
                            // — the incremental MoveNodes model can't stably snap a raw per-frame delta.
                            var appliedDx = 0.0
                            var appliedDy = 0.0

                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: break
                                // Escape (routed through the key handler) aborts a live drag; check
                                // before the up-test so an Escape-then-release still cancels.
                                if (state.consumeCancelDrag()) { canceled = true; break }
                                if (change.changedToUp()) break
                                val pos = change.position
                                val delta = pos - last
                                last = pos
                                if (delta == Offset.Zero) continue
                                accX += delta.x
                                accY += delta.y
                                // Movement threshold: an accidental click with sub-slop jitter must not
                                // start a move/resize (and must not open an undo checkpoint).
                                if (!moved && mode != CanvasOperation.Pan && hypot(accX, accY) < slop) { change.consume(); continue }
                                moved = true
                                if (!operationBegan) {
                                    state.beginDrag()
                                    operationBegan = true
                                }
                                if (!documentBegan && (mode == CanvasOperation.Move || mode is CanvasOperation.Resize || mode == CanvasOperation.Rotate)) {
                                    state.dispatch(DesignEditorIntent.BeginInteraction)
                                    documentBegan = true
                                }
                                when (mode) {
                                    CanvasOperation.Pan -> state.updateWorkspace {
                                        it.copy(viewport = it.viewport.panByScreenDelta(delta.x, delta.y, density))
                                    }
                                    CanvasOperation.Marquee -> marquee = Rect(min(start.x, pos.x), min(start.y, pos.y), max(start.x, pos.x), max(start.y, pos.y))
                                    is CanvasOperation.Create -> {
                                        val end = constrainCreatePoint(start, pos, mode.kind, shiftHeld)
                                        createRect = Rect(min(start.x, end.x), min(start.y, end.y), max(start.x, end.x), max(start.y, end.y))
                                        badge = "${(abs(end.x - start.x) / zoomPx).roundToInt()} x ${(abs(end.y - start.y) / zoomPx).roundToInt()}"
                                    }
                                    CanvasOperation.Move -> if (reorderBaseline != null) {
                                        val pointerMain = if (reorderBaseline.horizontal) viewport.toDocX(pos.x) else viewport.toDocY(pos.y)
                                        val index = flowInsertionIndex(reorderBaseline.siblings, pointerMain, reorderBaseline.horizontal)
                                        reorderPreview = ReorderPreview(reorderBaseline, index)
                                        badge = null
                                    } else {
                                        val rawDx = (accX / zoomPx).toDouble()
                                        val rawDy = (accY / zoomPx).toDouble()
                                        // Shift locks movement to the dominant axis (design-book §18).
                                        val lockX = shiftHeld && abs(accX) < abs(accY)
                                        val lockY = shiftHeld && abs(accY) <= abs(accX)
                                        val dragDx = if (lockX) 0.0 else rawDx
                                        val dragDy = if (lockY) 0.0 else rawDy
                                        val anchor = snapBaseline?.let { base ->
                                            computeAnchors(base.startUnionBounds.translate(dragDx, dragDy), base.containers, base.siblings, (SnapThresholdPx / zoomPx).toDouble())
                                        } ?: AnchorResult(0.0, 0.0, emptyList())
                                        val totalDx = dragDx + (if (lockX) 0.0 else anchor.dx)
                                        val totalDy = dragDy + (if (lockY) 0.0 else anchor.dy)
                                        // Read the live selection: a new node may have been selected on press.
                                        state.dispatch(DesignEditorIntent.MoveNodes(state.designState.selectedNodeIds, totalDx - appliedDx, totalDy - appliedDy))
                                        appliedDx = totalDx
                                        appliedDy = totalDy
                                        snapGuides = anchor.guides
                                        spacingBars = anchor.spacing
                                        dragMoveActive = true
                                        badge = null
                                    }
                                    is CanvasOperation.Resize -> if (resizeBaseline != null) {
                                        // Shift forces aspect-preserving corner resize.
                                        if (resizeTargets.size > 1) {
                                            applyGroupResize(
                                                state = state,
                                                targets = resizeTargets,
                                                baseline = resizeBaseline,
                                                handle = mode.handle,
                                                docDx = accX / zoomPx,
                                                docDy = accY / zoomPx,
                                                lockRatio = state.workspace.lockAspectRatio || shiftHeld,
                                            )
                                        } else {
                                            resizeTargets.firstOrNull()?.let { target ->
                                                applyResize(
                                                    state, target.nodeId, resizeBaseline, target.originPosition, mode.handle,
                                                    accX / zoomPx, accY / zoomPx, state.workspace.lockAspectRatio || shiftHeld,
                                                    // The rotation compensation must match the rotation the
                                                    // handle/baseline were resolved against at press time
                                                    // (0 for a multi-selection's axis-aligned group box), not
                                                    // the individual target's own rotation — otherwise a
                                                    // selection that collapses to one top-level resize target
                                                    // (e.g. a rotated frame plus one of its own children) would
                                                    // apply rotation compensation the on-screen unrotated
                                                    // handle never accounted for.
                                                    rotationDegrees = selectionRotation,
                                                )
                                            }
                                        }
                                        badge = null
                                    }
                                    CanvasOperation.Rotate -> if (rotateBaseline != null) {
                                        val pointerDoc = GeoPoint(viewport.toDocX(pos.x), viewport.toDocY(pos.y))
                                        val currentAngle = angleFromCenterDegrees(rotateBaseline.center, pointerDoc)
                                        var nextRotation = normalizeAngleDegrees(rotateBaseline.startRotation + (currentAngle - rotateBaseline.startAngle))
                                        if (shiftHeld) nextRotation = normalizeAngleDegrees(snapAngleToIncrement(nextRotation, 15.0))
                                        state.dispatch(DesignEditorIntent.SetRotation(rotateBaseline.nodeId, nextRotation))
                                        badge = null
                                    }
                                }
                                change.consume()
                            }

                            if (canceled) {
                                if (documentBegan) state.dispatch(DesignEditorIntent.CancelInteraction)
                            } else {
                                // Release.
                                when (mode) {
                                    CanvasOperation.Marquee -> {
                                        if (moved) {
                                            val rect = marquee
                                            if (rect != null) {
                                                val ids = nodesIn(pressLayout, pressDocument, viewport, rect)
                                                val next = if (shiftHeld) pressDesign.selectedNodeIds + ids else ids
                                                state.dispatch(DesignEditorIntent.SelectNodes(next))
                                            }
                                        } else if (hitId.isBlank()) {
                                            // Clicking inside the root frame's body selects the root
                                            // (Figma: clicking a frame's empty area selects the frame);
                                            // clicking the canvas outside all frames clears the selection.
                                            val rootBox = pressLayout
                                            if (rootBox != null && rootBox.hitTest(viewport.toDocX(start.x), viewport.toDocY(start.y)) != null) {
                                                state.dispatch(DesignEditorIntent.SelectNode(rootBox.node.sourceId))
                                            } else {
                                                state.dispatch(DesignEditorIntent.ClearSelection)
                                            }
                                        }
                                    }
                                    is CanvasOperation.Create -> {
                                        val end = constrainCreatePoint(start, last, mode.kind, shiftHeld)
                                        commitCreate(state, mode, start, end, zoomPx, viewport, pressRootId, moved)
                                        state.updateWorkspace { it.copy(tool = EditorTool.Select) }
                                    }
                                    CanvasOperation.Move -> {
                                        if (moved && reorderBaseline != null) {
                                            val target = reorderPreview?.index ?: reorderBaseline.originalIndex
                                            if (target != reorderBaseline.originalIndex) {
                                                state.dispatch(DesignEditorIntent.ReparentNode(reorderBaseline.nodeId, reorderBaseline.parentId, target))
                                            }
                                        } else if (moved) {
                                            commitMovedPositions(state, moveStartPositions)
                                        } else if (hitId.isNotBlank()) {
                                            val now = TimeSource.Monotonic.markNow()
                                            val doubleClick = lastTapId == hitId &&
                                                (lastTapMark?.let { (now - it).inWholeMilliseconds < 320 } ?: false)
                                            when {
                                                doubleClick -> enterEditMode(state, hitId)
                                                shiftHeld -> state.dispatch(DesignEditorIntent.ToggleNodeSelection(hitId))
                                                else -> state.dispatch(DesignEditorIntent.SelectNode(hitId))
                                            }
                                            lastTapMark = now
                                            lastTapId = hitId
                                        }
                                    }
                                    is CanvasOperation.Resize -> if (moved && resizeBaseline != null) {
                                        if (resizeTargets.size > 1) {
                                            commitGroupResizeWriteBack(
                                                state = state,
                                                targets = resizeTargets,
                                                baseline = resizeBaseline,
                                                handle = mode.handle,
                                                docDx = accX / zoomPx,
                                                docDy = accY / zoomPx,
                                                lockRatio = state.workspace.lockAspectRatio || shiftHeld,
                                            )
                                        } else {
                                            resizeTargets.firstOrNull()?.let { target ->
                                                commitResizeWriteBack(
                                                    state = state,
                                                    nodeId = target.nodeId,
                                                    baseline = resizeBaseline,
                                                    originPos = target.originPosition,
                                                    handle = mode.handle,
                                                    docDx = accX / zoomPx,
                                                    docDy = accY / zoomPx,
                                                    lockRatio = state.workspace.lockAspectRatio || shiftHeld,
                                                    // The rotation compensation must match the rotation the
                                                    // handle/baseline were resolved against at press time
                                                    // (0 for a multi-selection's axis-aligned group box), not
                                                    // the individual target's own rotation — otherwise a
                                                    // selection that collapses to one top-level resize target
                                                    // (e.g. a rotated frame plus one of its own children) would
                                                    // apply rotation compensation the on-screen unrotated
                                                    // handle never accounted for.
                                                    rotationDegrees = selectionRotation,
                                                )
                                            }
                                        }
                                    }
                                    else -> Unit
                                }
                                if (documentBegan) state.dispatch(DesignEditorIntent.EndInteraction)
                            }
                            state.endDrag()
                            marquee = null
                            createRect = null
                            badge = null
                            dragMoveActive = false
                            reorderPreview = null
                            snapGuides = emptyList()
                            spacingBars = emptyList()
                        }
                    }
                    .pointerHoverIcon(hoverCursor ?: if (spaceHeld) PointerIcon.Hand else cursorFor(ws.tool)),
            ) {
                DesignArtboard(
                    document = document,
                    pageId = pageId,
                    modifier = Modifier.fillMaxSize(),
                    deviceWidth = ws.deviceMode.width,
                    deviceHeight = ws.deviceMode.height,
                    viewport = viewport,
                    interactive = false,
                    showSelection = false,
                    onLayoutComputed = state::onArtboardLayout,
                )
            }
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No preview", color = colors.mutedInk)
            }
        }

        // Hover + selection + center-line + Alt-measurement overlays (screen space; never
        // touches the document — see design-book §18). Scene mode hides all edit affordances (§19).
        Canvas(Modifier.matchParentSize()) {
            if (ws.mode != EditorMode.Canvas) return@Canvas
            if (ws.hoveredNodeId.isNotBlank() && ws.hoveredNodeId !in design.selectedNodeIds && !altHeld) {
                layout?.findBySourceId(ws.hoveredNodeId)?.let { box ->
                    drawRotatedOutline(box.toBoundsBox(), box.node.rotation, viewport, colors.accent.copy(alpha = 0.85f), width = 1.5f)
                }
            }

            marquee?.let { r ->
                drawRect(colors.accent.copy(alpha = 0.12f), topLeft = r.topLeft, size = r.size)
                drawRect(colors.accent, topLeft = r.topLeft, size = r.size, style = Stroke(width = 1f))
            }
            createRect?.let { r ->
                drawRect(colors.accent.copy(alpha = 0.10f), topLeft = r.topLeft, size = r.size)
                drawRect(colors.accent, topLeft = r.topLeft, size = r.size, style = Stroke(width = 1.5f))
            }
            reorderPreview?.let { preview ->
                drawInsertionLine(preview, viewport, colors.accent)
            }
            // Beautiful-anchor guides (additive to the always-on center anchor lines below):
            // blue alignment lines, amber golden-ratio lines, dashed proportion lines, and green
            // equal-spacing distribution bars with px badges.
            snapGuides.forEach { guide -> drawAnchorGuide(guide, viewport, colors, textMeasurer) }
            spacingBars.forEach { bar -> drawSpacingBar(bar, viewport, colors, textMeasurer) }

            if (multiSelectionBox != null && ws.vectorEditNodeId.isBlank()) {
                drawRotatedOutline(multiSelectionBox, 0.0, viewport, colors.accent, width = 1.5f)
                drawRotatedHandles(multiSelectionBox, 0.0, viewport, colors.accent)
                drawSizeBadge(multiSelectionBox, 0.0, viewport, textMeasurer, colors)
            }

            if (primarySelectionBox != null) {
                // Baseline dashed center lines when idle; a bold emphasized pair while the
                // component is actively being dragged (design-book §18, "critical feature").
                if (parentOfPrimarySelection != null) {
                    val lines = centerAnchorLines(primarySelectionBox, parentOfPrimarySelection)
                    if (dragMoveActive) {
                        drawEmphasizedAnchorLines(lines, viewport, colors.accent)
                    } else {
                        drawDashedCenterLines(lines, viewport, colors.accent.copy(alpha = 0.5f))
                    }
                }

                drawRotatedOutline(primarySelectionBox, primarySelectionRotation, viewport, colors.accent, width = 1.5f)
                // Point-edit mode replaces object handles with path anchors (VectorEditOverlay),
                // so suppress handles and the rotate affordance but keep the rest of the overlay.
                if (!primarySelectionLocked && !primarySelectionInVectorEdit) {
                    drawRotatedHandles(primarySelectionBox, primarySelectionRotation, viewport, colors.accent)
                    if (!dragMoveActive) {
                        val offsetDoc = (RotateHandleScreenOffsetPx / zoomPx).toDouble()
                        drawRotateAffordance(primarySelectionBox, primarySelectionRotation, offsetDoc, viewport, colors.accent)
                    }
                }
                drawSizeBadge(primarySelectionBox, primarySelectionRotation, viewport, textMeasurer, colors)
            }

            if (altHeld && primarySelectionBox != null) {
                val altTargetBox = altMeasurementTarget(design, ws, layout, parentOfPrimarySelection, primarySelectionId)
                if (altTargetBox != null) {
                    drawAltMeasurement(
                        selected = primarySelectionBox,
                        selectedRotation = primarySelectionRotation,
                        target = altTargetBox.bounds,
                        targetRotation = altTargetBox.rotation,
                        viewport = viewport,
                        textMeasurer = textMeasurer,
                        colors = colors,
                    )
                }
            }
        }
        badge?.let { text ->
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp),
                shape = RoundedCornerShape(6.dp),
                color = colors.accent,
            ) {
                Text(text, modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp), color = Color.White, style = MaterialTheme.typography.labelSmall)
            }
        }

        // Vector/text edit overlays are Canvas-only: in Scene mode they must not render over the
        // prototype nor let a leaked in-progress edit dispatch document mutations (§19 read-only).
        if (ws.mode == EditorMode.Canvas) {
            // Vector edit mode: draw/drag path anchors of the target shape.
            VectorEditOverlay(state, layout, viewport, zoomPx)

            // Inline text editing overlay for a double-clicked text node.
            TextEditOverlay(state, layout, viewport)
        }
    }
}

// --- Text editing overlay ----------------------------------------------------

@Composable
private fun TextEditOverlay(state: MissionEditorStateHolder, layout: LayoutBox?, viewport: CanvasViewport) {
    val colors = LocalEditorColors.current
    val editingId = state.designState.editingTextNodeId
    if (editingId.isBlank()) return
    val node = state.designState.document?.nodeById(editingId) ?: return
    val kind = node.kind as? DesignNodeKind.Text ?: return
    val box = layout?.findBySourceId(editingId) ?: return
    val density = LocalDensity.current.density
    val tl = viewport.toScreen(box.x, box.y)
    val initial = kind.characters.literalOrNull()?.takeIf { it.isNotBlank() } ?: kind.content?.defaultText.orEmpty()
    var draft by remember(editingId) { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(editingId) { runCatching { focus.requestFocus() } }
    val wDp = (box.width * viewport.zoom / density).coerceAtLeast(40.0)
    val hDp = (box.height * viewport.zoom / density).coerceAtLeast(20.0)
    Box(
        modifier = Modifier
            .offset { androidx.compose.ui.unit.IntOffset(tl.x.roundToInt(), tl.y.roundToInt()) }
            .size(wDp.dp, hDp.dp)
            .background(Color.White.copy(alpha = 0.9f))
            .border(1.dp, colors.accent),
    ) {
        androidx.compose.foundation.text.BasicTextField(
            value = draft,
            onValueChange = { draft = it; state.dispatch(DesignEditorIntent.SetTextCharacters(editingId, it)) },
            modifier = Modifier.fillMaxSize().padding(4.dp)
                .focusRequester(focus)
                .onPreviewKeyEvent { e ->
                    if (e.type == KeyEventType.KeyDown && e.key == Key.Escape) {
                        state.dispatch(DesignEditorIntent.SetEditingText("")); true
                    } else {
                        false
                    }
                },
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.ink),
        )
    }
}

// --- Vector edit overlay -----------------------------------------------------

/** Screen-space grab radius for a bezier control-handle dot. */
private const val HandleGrabRadiusPx = 11f

/** Screen-space grab radius for an on-path vertex anchor. */
private const val AnchorGrabRadiusPx = 12f

/** Screen-space distance within which an empty-canvas pen press splits a segment. */
private const val SegmentGrabRadiusPx = 8f

/**
 * Point/handle editing overlay for the shape in vector-edit mode. When the node carries a
 * structural [VectorNetwork] it draws and drags that (anchors, sharp-corner squares, bezier
 * handles), mapping network/view-box space to screen through the SAME `meetFit` the renderer
 * uses so painted geometry, drawn anchors and hit-testing all agree. A network-less shape that
 * still has legacy `paths[].d` falls back to [LegacyVectorEditOverlay].
 *
 * Drags are in-memory per frame (MoveVectorVertex / MoveVectorHandle) bracketed by
 * BeginInteraction … CommitVectorNetwork + EndInteraction, so the whole drag surgically
 * rewrites the SLM network once and yields a single undo entry (see [dragNetwork]). Discrete
 * pen affordances (add vertex on a segment, close the path) commit on their own.
 */
@Composable
private fun VectorEditOverlay(state: MissionEditorStateHolder, layout: LayoutBox?, viewport: CanvasViewport, zoomPx: Float) {
    val colors = LocalEditorColors.current
    val editId = state.workspace.vectorEditNodeId
    if (editId.isBlank()) return
    val node = state.designState.document?.nodeById(editId) ?: return
    val kind = node.kind as? DesignNodeKind.Shape ?: return
    val box = layout?.findBySourceId(editId) ?: return
    val network = kind.network

    // Legacy `d`-string shapes (no structural network) keep the pre-network anchor tooling.
    if (network == null || network.isEmpty()) {
        LegacyVectorEditOverlay(state, kind, box, viewport, zoomPx)
        return
    }

    // View-box → box fit shared with the renderer (aspect-preserving "meet"); identity-safe.
    val boxRect = RectD(box.x, box.y, box.right, box.bottom)
    val fit = meetFit(overlayViewBox(kind, network) ?: boxRect, boxRect)
    val selected = state.workspace.vectorSelectedVertex

    Canvas(
        Modifier.fillMaxSize().pointerInput(editId, viewport) {
            awaitEachGesture {
                val down = awaitFirstDown()
                // Re-read live geometry (the captured `network`/`fit` are frozen at pointerInput
                // setup and go stale after edits), mirroring the main gesture handler.
                val liveNode = state.designState.document?.nodeById(editId) ?: return@awaitEachGesture
                val liveKind = liveNode.kind as? DesignNodeKind.Shape ?: return@awaitEachGesture
                val liveNetwork = liveKind.network?.takeIf { it.isNotEmpty() } ?: return@awaitEachGesture
                val liveBox = state.artboardLayout?.findBySourceId(editId) ?: return@awaitEachGesture
                val liveRect = RectD(liveBox.x, liveBox.y, liveBox.right, liveBox.bottom)
                val liveFit = meetFit(overlayViewBox(liveKind, liveNetwork) ?: liveRect, liveRect)
                val liveScale = liveFit.a
                if (!liveScale.isFinite() || liveScale <= 0.0) return@awaitEachGesture
                val press = down.position
                val tool = state.workspace.tool
                down.consume() // vector-edit mode owns the press; never let it also move the node

                // Priority 1: a bezier control handle.
                val handleHit = pickHandle(liveNetwork, liveFit, viewport, press)
                if (handleHit != null) {
                    val (vertexIndex, side) = handleHit
                    state.updateWorkspace { it.copy(vectorSelectedVertex = VectorVertexRef(vertexIndex, side.toVertexPart())) }
                    dragNetwork(state, editId, down) { delta ->
                        DesignEditorIntent.MoveVectorHandle(
                            editId, vertexIndex, side,
                            delta.x / zoomPx / liveScale, delta.y / zoomPx / liveScale,
                        )
                    }
                    return@awaitEachGesture
                }

                // Priority 2: an on-path anchor.
                val anchorHit = pickAnchor(liveNetwork, liveFit, viewport, press)
                if (anchorHit != null) {
                    // Pen: clicking the first vertex of an open loop closes the path.
                    if (tool == EditorTool.Pen && anchorHit == 0 && !liveNetwork.isClosedLoop() && liveNetwork.vertices.size >= 3) {
                        state.dispatch(DesignEditorIntent.CloseVectorNetwork(editId))
                        return@awaitEachGesture
                    }
                    state.updateWorkspace { it.copy(vectorSelectedVertex = VectorVertexRef(anchorHit, VectorVertexPart.Anchor)) }
                    dragNetwork(state, editId, down) { delta ->
                        DesignEditorIntent.MoveVectorVertex(
                            editId, anchorHit,
                            delta.x / zoomPx / liveScale, delta.y / zoomPx / liveScale,
                        )
                    }
                    return@awaitEachGesture
                }

                // Empty press: outside the box leaves edit mode; a pen press near a segment
                // splits it at the pointer (inserting a new vertex there).
                if (!press.insideScreenBox(liveBox, viewport)) {
                    state.updateWorkspace { it.copy(vectorEditNodeId = "", vectorSelectedPoint = null, vectorSelectedVertex = null) }
                    return@awaitEachGesture
                }
                if (tool == EditorTool.Pen) {
                    val segmentIndex = pickSegment(liveNetwork, liveFit, viewport, press)
                    if (segmentIndex != null) {
                        val (shapeX, shapeY) = liveFit.inverse().apply(viewport.toDocX(press.x), viewport.toDocY(press.y))
                        val newIndex = liveNetwork.vertices.size
                        state.dispatch(DesignEditorIntent.AddVectorVertex(editId, segmentIndex, shapeX, shapeY))
                        state.updateWorkspace { it.copy(vectorSelectedVertex = VectorVertexRef(newIndex, VectorVertexPart.Anchor)) }
                    }
                }
            }
        },
    ) {
        // Tangent lines + control-handle dots first, so anchors draw on top.
        network.vertices.forEach { vertex ->
            val anchor = vertexScreen(fit, viewport, vertex)
            handleTip(fit, viewport, vertex, HandleSide.In)?.let { tip ->
                drawLine(colors.softStroke, anchor, tip, strokeWidth = 1f)
                drawCircle(colors.accent, radius = 3.5f, center = tip)
            }
            handleTip(fit, viewport, vertex, HandleSide.Out)?.let { tip ->
                drawLine(colors.softStroke, anchor, tip, strokeWidth = 1f)
                drawCircle(colors.accent, radius = 3.5f, center = tip)
            }
        }
        network.vertices.forEachIndexed { index, vertex ->
            val anchor = vertexScreen(fit, viewport, vertex)
            val isSelected = selected?.vertexIndex == index && selected.part == VectorVertexPart.Anchor
            val fill = if (isSelected) colors.accent else Color.White
            if (vertex.corner) {
                val half = 5f
                val topLeft = Offset(anchor.x - half, anchor.y - half)
                drawRect(fill, topLeft = topLeft, size = Size(half * 2, half * 2))
                drawRect(colors.accent, topLeft = topLeft, size = Size(half * 2, half * 2), style = Stroke(width = 1.5f))
            } else {
                drawCircle(fill, radius = 5f, center = anchor)
                drawCircle(colors.accent, radius = 5f, center = anchor, style = Stroke(width = 1.5f))
            }
        }
    }
}

/**
 * Legacy `d`-string vector editing (no structural network): flat on-path anchors from
 * [vectorAnchors], dragged through [DesignEditorIntent.MoveVectorPoint]. Kept for shapes the
 * compiler emitted as inline paths without a network.
 */
@Composable
private fun LegacyVectorEditOverlay(
    state: MissionEditorStateHolder,
    kind: DesignNodeKind.Shape,
    box: LayoutBox,
    viewport: CanvasViewport,
    zoomPx: Float,
) {
    val colors = LocalEditorColors.current
    val editId = state.workspace.vectorEditNodeId
    val viewBox = kind.viewBox
    val scaleX = if (viewBox != null && viewBox.width > 0) box.width / viewBox.width else 1.0
    val scaleY = if (viewBox != null && viewBox.height > 0) box.height / viewBox.height else 1.0
    val anchors = remember(editId, kind.paths) {
        kind.paths.flatMapIndexed { pathIndex, path ->
            io.aequicor.visualization.editor.presentation.vectorAnchors(path.d).map { pathIndex to it }
        }
    }
    Canvas(
        Modifier.fillMaxSize().pointerInput(editId, viewport) {
            awaitEachGesture {
                val down = awaitFirstDown()
                val nearest = anchors.minByOrNull { (_, a) ->
                    val sx = viewport.toScreen(box.x + a.x * scaleX, box.y + a.y * scaleY)
                    (sx - down.position).getDistanceSquared()
                }
                val within = nearest?.let { (_, a) ->
                    val sx = viewport.toScreen(box.x + a.x * scaleX, box.y + a.y * scaleY)
                    (sx - down.position).getDistance() <= AnchorGrabRadiusPx
                } ?: false
                if (!within || nearest == null) {
                    val tl = viewport.toScreen(box.x, box.y)
                    val br = viewport.toScreen(box.right, box.bottom)
                    val outside = down.position.x < tl.x || down.position.y < tl.y ||
                        down.position.x > br.x || down.position.y > br.y
                    if (outside) state.updateWorkspace { it.copy(vectorEditNodeId = "", vectorSelectedPoint = null, vectorSelectedVertex = null) }
                    return@awaitEachGesture
                }
                state.updateWorkspace {
                    it.copy(vectorSelectedPoint = io.aequicor.visualization.editor.presentation.VectorPointRef(nearest.first, nearest.second.index))
                }
                state.dispatch(DesignEditorIntent.BeginInteraction)
                var last = down.position
                while (true) {
                    val e = awaitPointerEvent()
                    val ch = e.changes.firstOrNull() ?: break
                    if (ch.changedToUp()) break
                    val d = ch.position - last
                    last = ch.position
                    if (d != Offset.Zero) {
                        state.dispatch(
                            DesignEditorIntent.MoveVectorPoint(
                                editId, nearest.first, nearest.second.index,
                                (d.x / zoomPx / scaleX).toDouble(), (d.y / zoomPx / scaleY).toDouble(),
                            ),
                        )
                        ch.consume()
                    }
                }
                state.dispatch(DesignEditorIntent.EndInteraction)
            }
        },
    ) {
        anchors.forEach { (_, a) ->
            val p = viewport.toScreen(box.x + a.x * scaleX, box.y + a.y * scaleY)
            drawCircle(Color.White, radius = 5f, center = p)
            drawCircle(colors.accent, radius = 5f, center = p, style = Stroke(width = 1.5f))
        }
    }
}

// --- Vector-network overlay geometry helpers ---------------------------------

/** Maps [HandleSide] to the workspace selection part. */
private fun HandleSide.toVertexPart(): VectorVertexPart =
    if (this == HandleSide.Out) VectorVertexPart.OutHandle else VectorVertexPart.InHandle

/** True when a segment closes the loop (last vertex back to the first) — see [VectorNetwork.closePath]. */
private fun VectorNetwork.isClosedLoop(): Boolean =
    segments.any { it.from == vertices.lastIndex && it.to == 0 }

/**
 * The network's own coordinate space as a view-box: the shape's authored [DesignViewBox] when
 * present, else the axis-aligned extent of its vertices and handle tips (mirroring the resolver's
 * `geometry.bounds()` fallback so the overlay fit matches what is painted).
 */
private fun overlayViewBox(kind: DesignNodeKind.Shape, network: VectorNetwork): RectD? {
    kind.viewBox?.let { return RectD(it.x, it.y, it.x + it.width, it.y + it.height) }
    if (network.vertices.isEmpty()) return null
    var minX = Double.POSITIVE_INFINITY
    var minY = Double.POSITIVE_INFINITY
    var maxX = Double.NEGATIVE_INFINITY
    var maxY = Double.NEGATIVE_INFINITY
    fun include(x: Double, y: Double) {
        if (x < minX) minX = x
        if (y < minY) minY = y
        if (x > maxX) maxX = x
        if (y > maxY) maxY = y
    }
    network.vertices.forEach { vertex ->
        include(vertex.x, vertex.y)
        vertex.inHandle?.let { include(vertex.x + it.dx, vertex.y + it.dy) }
        vertex.outHandle?.let { include(vertex.x + it.dx, vertex.y + it.dy) }
    }
    if (minX > maxX || minY > maxY) return null
    return RectD(minX, minY, maxX, maxY)
}

/** Screen position of a vertex anchor under [fit]. */
private fun vertexScreen(fit: Affine2D, viewport: CanvasViewport, vertex: VectorVertex): Offset {
    val (x, y) = fit.apply(vertex.x, vertex.y)
    return viewport.toScreen(x, y)
}

/** Screen position of a vertex's [side] bezier handle tip, or null when that handle is absent. */
private fun handleTip(fit: Affine2D, viewport: CanvasViewport, vertex: VectorVertex, side: HandleSide): Offset? {
    val handle = if (side == HandleSide.Out) vertex.outHandle else vertex.inHandle
    return handle?.let {
        val (x, y) = fit.apply(vertex.x + it.dx, vertex.y + it.dy)
        viewport.toScreen(x, y)
    }
}

/** Nearest bezier control handle within [HandleGrabRadiusPx] of [press], or null. */
private fun pickHandle(network: VectorNetwork, fit: Affine2D, viewport: CanvasViewport, press: Offset): Pair<Int, HandleSide>? {
    var best: Pair<Int, HandleSide>? = null
    var bestDist = HandleGrabRadiusPx
    network.vertices.forEachIndexed { index, vertex ->
        listOf(HandleSide.In, HandleSide.Out).forEach { side ->
            handleTip(fit, viewport, vertex, side)?.let { tip ->
                val dist = (tip - press).getDistance()
                if (dist <= bestDist) {
                    bestDist = dist
                    best = index to side
                }
            }
        }
    }
    return best
}

/** Nearest on-path anchor within [AnchorGrabRadiusPx] of [press], or null. */
private fun pickAnchor(network: VectorNetwork, fit: Affine2D, viewport: CanvasViewport, press: Offset): Int? {
    var best: Int? = null
    var bestDist = AnchorGrabRadiusPx
    network.vertices.forEachIndexed { index, vertex ->
        val dist = (vertexScreen(fit, viewport, vertex) - press).getDistance()
        if (dist <= bestDist) {
            bestDist = dist
            best = index
        }
    }
    return best
}

/** Nearest segment (as a straight screen chord) within [SegmentGrabRadiusPx] of [press], or null. */
private fun pickSegment(network: VectorNetwork, fit: Affine2D, viewport: CanvasViewport, press: Offset): Int? {
    var best: Int? = null
    var bestDist = SegmentGrabRadiusPx
    network.segments.forEachIndexed { index, segment ->
        val from = network.vertices.getOrNull(segment.from) ?: return@forEachIndexed
        val to = network.vertices.getOrNull(segment.to) ?: return@forEachIndexed
        val dist = distanceToSegment(press, vertexScreen(fit, viewport, from), vertexScreen(fit, viewport, to))
        if (dist <= bestDist) {
            bestDist = dist
            best = index
        }
    }
    return best
}

/** Perpendicular distance from [p] to the finite screen segment [a]–[b]. */
private fun distanceToSegment(p: Offset, a: Offset, b: Offset): Float {
    val abx = b.x - a.x
    val aby = b.y - a.y
    val len2 = abx * abx + aby * aby
    val t = if (len2 == 0f) 0f else (((p.x - a.x) * abx + (p.y - a.y) * aby) / len2).coerceIn(0f, 1f)
    return hypot(p.x - (a.x + t * abx), p.y - (a.y + t * aby))
}

/** Whether [this] screen point lies within [box]'s (unrotated) on-screen rectangle. */
private fun Offset.insideScreenBox(box: LayoutBox, viewport: CanvasViewport): Boolean {
    val tl = viewport.toScreen(box.x, box.y)
    val br = viewport.toScreen(box.right, box.bottom)
    return x >= min(tl.x, br.x) && x <= max(tl.x, br.x) && y >= min(tl.y, br.y) && y <= max(tl.y, br.y)
}

/**
 * Runs a network drag: each frame dispatches the in-memory move [intentForDelta] builds from the
 * screen-space delta; on release commits the mutated network to SLM once. The BeginInteraction
 * checkpoint is opened only on the first real move (a bare click selects without an undo entry),
 * so a move…commit sequence coalesces into a single undo entry (writeBackEdits skips the fork
 * while interacting).
 */
private suspend fun AwaitPointerEventScope.dragNetwork(
    state: MissionEditorStateHolder,
    nodeId: String,
    down: PointerInputChange,
    intentForDelta: (Offset) -> DesignEditorIntent,
) {
    var began = false
    var last = down.position
    while (true) {
        val event = awaitPointerEvent()
        val change = event.changes.firstOrNull() ?: break
        if (change.changedToUp()) break
        val delta = change.position - last
        last = change.position
        if (delta == Offset.Zero) continue
        if (!began) {
            state.dispatch(DesignEditorIntent.BeginInteraction)
            began = true
        }
        state.dispatch(intentForDelta(delta))
        change.consume()
    }
    if (began) {
        state.dispatch(DesignEditorIntent.CommitVectorNetwork(nodeId))
        state.dispatch(DesignEditorIntent.EndInteraction)
    }
}

/**
 * Enters text-editing (text nodes) or vector-edit (shapes) on double-click. A shape that already
 * carries a structural network — or is authored as `shape: vector` — enters point-edit mode
 * directly; a parametric primitive (rect/ellipse/polygon/star/line/arrow) is first baked into an
 * editable network via [DesignEditorIntent.ConvertToEditableVector], and the overlay picks up the
 * fresh network on the next frame.
 */
private fun enterEditMode(state: MissionEditorStateHolder, nodeId: String) {
    val node = state.designState.document?.nodeById(nodeId) ?: return
    val kind = node.kind
    when {
        kind is DesignNodeKind.Text -> state.dispatch(DesignEditorIntent.SetEditingText(nodeId))
        kind is DesignNodeKind.Shape && (kind.network?.isNotEmpty() == true || kind.shape == ShapeType.Vector) ->
            state.updateWorkspace { it.copy(vectorEditNodeId = nodeId, vectorSelectedPoint = null, vectorSelectedVertex = null) }
        kind is DesignNodeKind.Shape -> {
            state.dispatch(DesignEditorIntent.ConvertToEditableVector(nodeId))
            state.updateWorkspace { it.copy(vectorEditNodeId = nodeId, vectorSelectedPoint = null, vectorSelectedVertex = null) }
        }
        else -> Unit
    }
}

/** Keyboard shortcuts: nudge, big-nudge, delete, duplicate, undo/redo, select-all, escape. */
private fun handleCanvasKey(state: MissionEditorStateHolder, key: Key, shift: Boolean, ctrl: Boolean): Boolean {
    val design = state.designState
    val selection = design.selectedNodeIds
    val step = if (shift) 10.0 else 1.0
    fun nudge(dx: Double, dy: Double): Boolean {
        if (selection.isEmpty()) return false
        val startPositions = design.document?.let { document ->
            selection.mapNotNull { id -> document.nodeById(id)?.position?.let { position -> id to position } }.toMap()
        }.orEmpty()
        state.dispatch(DesignEditorIntent.BeginInteraction)
        state.dispatch(DesignEditorIntent.MoveNodes(selection, dx, dy))
        commitMovedPositions(state, startPositions)
        state.dispatch(DesignEditorIntent.EndInteraction)
        return true
    }
    // Bracket keys restack the primary selection (design-book §12 z-order commands): `[`/`]` send to
    // back / bring to front, Cmd/Ctrl + `[`/`]` step one layer back / forward — mirroring the Layers
    // panel's per-node reorder. A single `ReorderNode` is self-contained: the reducer returns the
    // state unchanged when the node is already at the extreme (no undo entry, redo stack untouched),
    // so no interaction bracketing or no-op guard is needed. (Restacking a whole multi-selection as a
    // block is a follow-up — see EDITOR.md.)
    fun zorder(move: ZOrderMove): Boolean {
        val nodeId = design.selectedNodeId
        if (nodeId.isBlank()) return false
        state.dispatch(DesignEditorIntent.ReorderNode(nodeId, move))
        return true
    }
    return when {
        key == Key.DirectionLeft -> nudge(-step, 0.0)
        key == Key.DirectionRight -> nudge(step, 0.0)
        key == Key.DirectionUp -> nudge(0.0, -step)
        key == Key.DirectionDown -> nudge(0.0, step)
        // In vector-edit mode Delete/Backspace removes the selected vertex, not the node.
        (key == Key.Delete || key == Key.Backspace) &&
            state.workspace.vectorEditNodeId.isNotBlank() && state.workspace.vectorSelectedVertex != null -> {
            val ws = state.workspace
            state.dispatch(DesignEditorIntent.DeleteVectorVertex(ws.vectorEditNodeId, ws.vectorSelectedVertex!!.vertexIndex))
            state.updateWorkspace { it.copy(vectorSelectedVertex = null) }
            true
        }
        (key == Key.Delete || key == Key.Backspace) && selection.isNotEmpty() -> {
            state.dispatch(DesignEditorIntent.DeleteNodes(selection)); true
        }
        ctrl && key == Key.D && selection.isNotEmpty() -> { state.dispatch(DesignEditorIntent.DuplicateNodes(selection)); true }
        ctrl && key == Key.RightBracket -> zorder(ZOrderMove.Forward)
        ctrl && key == Key.LeftBracket -> zorder(ZOrderMove.Backward)
        key == Key.RightBracket -> zorder(ZOrderMove.ToFront)
        key == Key.LeftBracket -> zorder(ZOrderMove.ToBack)
        ctrl && key == Key.A -> { state.dispatch(DesignEditorIntent.SelectAll); true }
        ctrl && shift && key == Key.Z -> { state.dispatch(DesignEditorIntent.Redo); true }
        ctrl && key == Key.Z -> { state.dispatch(DesignEditorIntent.Undo); true }
        key == Key.Escape -> {
            when {
                // A live drag takes priority: abort it (revert + no undo entry).
                state.activeDrag -> state.requestCancelDrag()
                state.workspace.vectorEditNodeId.isNotBlank() -> state.updateWorkspace { it.copy(vectorEditNodeId = "", vectorSelectedPoint = null, vectorSelectedVertex = null) }
                state.designState.editingTextNodeId.isNotBlank() -> state.dispatch(DesignEditorIntent.SetEditingText(""))
                state.workspace.isMainOnly -> state.updateWorkspace { it.copy(focusMode = FocusMode.Normal) }
                else -> state.dispatch(DesignEditorIntent.ClearSelection)
            }
            true
        }
        else -> false
    }
}

// --- Gesture model -----------------------------------------------------------

private fun resolveCanvasOperation(
    tool: EditorTool,
    forcePan: Boolean,
    handle: ResizeHandle?,
    rotateHit: Boolean,
    hitId: String,
): CanvasOperation = when {
    forcePan -> CanvasOperation.Pan
    tool.creates != null -> CanvasOperation.Create(tool.creates)
    handle != null -> CanvasOperation.Resize(handle)
    rotateHit -> CanvasOperation.Rotate
    hitId.isNotBlank() -> CanvasOperation.Move
    else -> CanvasOperation.Marquee
}

private data class ResizeTarget(
    val nodeId: String,
    val baseline: BoundsBox,
    val originPosition: DesignPoint?,
    val rotation: Double = 0.0,
)

/** Fixed reference used at drag-start to resolve a live rotate gesture. */
private data class RotateBaseline(
    val nodeId: String,
    val center: GeoPoint,
    val startAngle: Double,
    val startRotation: Double,
)

/**
 * Fixed drag-start reference for reordering an Auto layout child: its parent and flow
 * siblings (excluding itself, in document order), captured once at press time so the
 * live insertion index is computed against a stable baseline rather than the
 * already-mutating tree.
 */
private data class ReorderBaseline(
    val nodeId: String,
    val parentId: String,
    val parentBox: BoundsBox,
    val horizontal: Boolean,
    val siblings: List<BoundsBox>,
    /** The node's own index among just the flow siblings (itself included), before the drag. */
    val originalIndex: Int,
)

/** What [ReorderBaseline] resolves to on the current pointer position; drives the insertion-line draw. */
private data class ReorderPreview(
    val baseline: ReorderBaseline,
    val index: Int,
)

/**
 * Press-time snapshot for beautiful-anchor snapping during a free move (design-book §18): the
 * dragged selection's union bounds, the [containers] (immediate parent frame plus its
 * unrotated ancestors up to the root — for center/edge/golden/proportion anchors) and the
 * [siblings] (co-resident peers — for edge/center alignment and equal-spacing distribution).
 * Captured once because the laid-out [layout] is frozen for the gesture's duration.
 */
private data class SnapBaseline(
    val startUnionBounds: BoundsBox,
    val containers: List<BoundsBox>,
    val siblings: List<BoundsBox>,
)

private fun LayoutBox.toBoundsBox(): BoundsBox =
    BoundsBox(x = x, y = y, width = width, height = height)

/** The transformed visual bounds of a (possibly rotated) box: itself when unrotated, else the axis-aligned bounding box of its rotated corners. */
private fun BoundsBox.visualBounds(rotationDegrees: Double): BoundsBox =
    if (rotationDegrees == 0.0) this else axisAlignedBounds(rotatedCorners(this, rotationDegrees))

private const val HandleHitRadiusPx = 11f

/** Screen-space radius within which a free-move drag magnetically snaps to an alignment line. */
private const val SnapThresholdPx = 6f

/** Screen-space distance between the rotate affordance and the top-center handle. */
private const val RotateHandleScreenOffsetPx = 26f

/** Nearest resize handle to [pos], accounting for the component's own [rotationDegrees]. */
private fun rotatedHandleAt(box: BoundsBox, rotationDegrees: Double, viewport: CanvasViewport, pos: Offset): ResizeHandle? {
    val points = rotatedHandlePoints(box, rotationDegrees)
    return points.entries
        .map { (handle, point) -> handle to viewport.toScreen(point.x, point.y) }
        .minByOrNull { (_, screenPoint) -> (screenPoint - pos).getDistanceSquared() }
        ?.takeIf { (_, screenPoint) -> (screenPoint - pos).getDistance() <= HandleHitRadiusPx }
        ?.first
}

/**
 * Cursor for the current pointer position: a rotation-aware resize cursor when hovering a
 * handle of the (single or multi) selection, else the active tool's default cursor. Runs
 * off live state so it stays correct as selection changes without a fresh gesture setup.
 */
private fun resolveHandleCursor(
    design: DesignEditorState,
    layout: LayoutBox?,
    viewport: CanvasViewport,
    pos: Offset,
    tool: EditorTool,
    spaceHeld: Boolean,
    vectorEditNodeId: String,
): PointerIcon? {
    if (spaceHeld || tool != EditorTool.Select) return null
    val document = design.document ?: return null
    if (design.selectedNodeIds.size > 1) {
        val box = selectionHandleBounds(layout, design.selectedNodeIds) ?: return null
        val locked = design.selectedNodeIds.any { document.nodeById(it)?.locked == true }
        if (locked) return null
        val handle = rotatedHandleAt(box, 0.0, viewport, pos) ?: return null
        return cursorForResizeKind(resizeCursorKindForHandle(handle, 0.0))
    }
    val id = design.selectedNodeId.takeIf { it.isNotBlank() } ?: return null
    // Point-edit mode replaces object handles with path anchors; no resize cursor there.
    if (id == vectorEditNodeId) return null
    if (document.nodeById(id)?.locked == true) return null
    val box = layout?.findBySourceId(id) ?: return null
    val rotation = box.node.rotation
    val handle = rotatedHandleAt(box.toBoundsBox(), rotation, viewport, pos) ?: return null
    return cursorForResizeKind(resizeCursorKindForHandle(handle, rotation))
}

private fun cursorForResizeKind(kind: ResizeCursorKind): PointerIcon = when (kind) {
    ResizeCursorKind.Horizontal -> horizontalResizeCursor()
    ResizeCursorKind.Vertical -> verticalResizeCursor()
    ResizeCursorKind.DiagonalTopLeftBottomRight -> diagonalResizeCursor(topLeftToBottomRight = true)
    ResizeCursorKind.DiagonalTopRightBottomLeft -> diagonalResizeCursor(topLeftToBottomRight = false)
}

// --- Overlay drawing ----------------------------------------------------------

private fun DrawScope.rotatedOutlinePath(box: BoundsBox, rotationDegrees: Double, viewport: CanvasViewport): Path {
    val corners = rotatedCorners(box, rotationDegrees).map { viewport.toScreen(it.x, it.y) }
    return Path().apply {
        moveTo(corners[0].x, corners[0].y)
        for (i in 1 until corners.size) lineTo(corners[i].x, corners[i].y)
        close()
    }
}

private fun DrawScope.drawRotatedOutline(box: BoundsBox, rotationDegrees: Double, viewport: CanvasViewport, color: Color, width: Float) {
    drawPath(rotatedOutlinePath(box, rotationDegrees, viewport), color = color, style = Stroke(width = width))
}

private fun DrawScope.drawRotatedHandles(box: BoundsBox, rotationDegrees: Double, viewport: CanvasViewport, color: Color) {
    val handle = 8f
    rotatedHandlePoints(box, rotationDegrees).values.forEach { point ->
        val center = viewport.toScreen(point.x, point.y)
        val topLeft = Offset(center.x - handle / 2f, center.y - handle / 2f)
        drawRect(color = Color.White, topLeft = topLeft, size = Size(handle, handle))
        drawRect(color = color, topLeft = topLeft, size = Size(handle, handle), style = Stroke(width = 1.5f))
    }
}

private fun DrawScope.drawRotateAffordance(
    box: BoundsBox,
    rotationDegrees: Double,
    offsetDoc: Double,
    viewport: CanvasViewport,
    color: Color,
) {
    val center = GeoPoint(box.centerX, box.centerY)
    val topCenter = rotatePointAroundCenter(GeoPoint(box.centerX, box.y), center, rotationDegrees)
    val handlePoint = rotateAffordancePoint(box, rotationDegrees, offsetDoc)
    val topCenterScreen = viewport.toScreen(topCenter.x, topCenter.y)
    val screenPoint = viewport.toScreen(handlePoint.x, handlePoint.y)
    drawLine(color.copy(alpha = 0.6f), topCenterScreen, screenPoint, strokeWidth = 1f)
    drawCircle(Color.White, radius = 6f, center = screenPoint)
    drawCircle(color, radius = 6f, center = screenPoint, style = Stroke(width = 1.5f))
}

private fun DrawScope.drawDashedCenterLines(lines: io.aequicor.visualization.editor.presentation.CenterAnchorLines, viewport: CanvasViewport, color: Color) {
    val dash = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))
    val h = lines.horizontal
    val v = lines.vertical
    drawLine(color, viewport.toScreen(h.x1, h.y1), viewport.toScreen(h.x2, h.y2), strokeWidth = 1f, pathEffect = dash)
    drawLine(color, viewport.toScreen(v.x1, v.y1), viewport.toScreen(v.x2, v.y2), strokeWidth = 1f, pathEffect = dash)
}

/** The emphasized central anchor lines shown while a component is being dragged — the main positioning feedback (design-book §18, critical feature). */
private fun DrawScope.drawEmphasizedAnchorLines(lines: io.aequicor.visualization.editor.presentation.CenterAnchorLines, viewport: CanvasViewport, color: Color) {
    val h = lines.horizontal
    val v = lines.vertical
    val hStart = viewport.toScreen(h.x1, h.y1)
    val hEnd = viewport.toScreen(h.x2, h.y2)
    val vStart = viewport.toScreen(v.x1, v.y1)
    val vEnd = viewport.toScreen(v.x2, v.y2)
    drawLine(color, hStart, hEnd, strokeWidth = 1.5f)
    drawLine(color, vStart, vEnd, strokeWidth = 1.5f)
    listOf(hStart, hEnd, vStart, vEnd).forEach { p -> drawCircle(color, radius = 2.5f, center = p) }
    // The crossing point (component center) gets its own marker.
    drawCircle(color, radius = 3f, center = Offset(vStart.x, hStart.y))
}

/** The insertion-line feedback while dragging an Auto layout child to a new position among its flow siblings. */
private fun DrawScope.drawInsertionLine(preview: ReorderPreview, viewport: CanvasViewport, color: Color) {
    val baseline = preview.baseline
    val line = flowInsertionLine(baseline.siblings, preview.index, baseline.parentBox, baseline.horizontal)
    val start = viewport.toScreen(line.x1, line.y1)
    val end = viewport.toScreen(line.x2, line.y2)
    drawLine(color, start, end, strokeWidth = 2.5f)
    drawCircle(color, radius = 3f, center = start)
    drawCircle(color, radius = 3f, center = end)
}

private fun DrawScope.drawSizeBadge(
    box: BoundsBox,
    rotationDegrees: Double,
    viewport: CanvasViewport,
    textMeasurer: TextMeasurer,
    colors: io.aequicor.visualization.editor.ui.theme.EditorColors,
) {
    val visual = box.visualBounds(rotationDegrees)
    val bottomCenter = viewport.toScreen(visual.centerX, visual.bottom)
    val label = "${formatMeasurement(box.width)} x ${formatMeasurement(box.height)}"
    drawFilledBadge(label, Offset(bottomCenter.x, bottomCenter.y + 14f), colors.accent, textMeasurer)
}

private fun formatMeasurement(value: Double): String = value.formatPx()

/** A solid-fill badge (used for the always-accent size badge). */
private fun DrawScope.drawFilledBadge(text: String, center: Offset, background: Color, textMeasurer: TextMeasurer) {
    val layout = textMeasurer.measure(
        text = AnnotatedString(text),
        style = TextStyle(color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Medium),
    )
    val rect = badgeRect(layout.size, center)
    drawRoundRect(color = background, topLeft = rect.topLeft, size = rect.size, cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f))
    drawText(layout, topLeft = Offset(rect.topLeft.x + BadgePaddingH, rect.topLeft.y + BadgePaddingV))
}

/**
 * A neutral-surface badge with a colored border/text — used for the Alt measurement badges,
 * since `statusDanger` is a warm accent and the palette rules call for it used sparingly as a
 * spot color, not as a badge's fill (`EditorColors`/theme conventions).
 */
private fun DrawScope.drawOutlinedBadge(text: String, center: Offset, accent: Color, surface: Color, textMeasurer: TextMeasurer) {
    val layout = textMeasurer.measure(
        text = AnnotatedString(text),
        style = TextStyle(color = accent, fontSize = 10.sp, fontWeight = FontWeight.Medium),
    )
    val rect = badgeRect(layout.size, center)
    val corner = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
    drawRoundRect(color = surface, topLeft = rect.topLeft, size = rect.size, cornerRadius = corner)
    drawRoundRect(color = accent, topLeft = rect.topLeft, size = rect.size, cornerRadius = corner, style = Stroke(width = 1f))
    drawText(layout, topLeft = Offset(rect.topLeft.x + BadgePaddingH, rect.topLeft.y + BadgePaddingV))
}

private const val BadgePaddingH = 6f
private const val BadgePaddingV = 3f

private fun badgeRect(textSize: androidx.compose.ui.unit.IntSize, center: Offset): Rect {
    val size = Size(textSize.width + BadgePaddingH * 2, textSize.height + BadgePaddingV * 2)
    return Rect(Offset(center.x - size.width / 2f, center.y - size.height / 2f), size)
}

/** The parent-frame or hovered-sibling bounds an Alt measurement is taken against. */
private data class AltTarget(val bounds: BoundsBox, val rotation: Double)

/**
 * Resolves the Alt-measurement target: the currently hovered node when it isn't the
 * selection itself, else the selection's parent frame (design-book §18 "if no hover
 * target is found, target defaults to parent frame").
 */
private fun altMeasurementTarget(
    design: DesignEditorState,
    ws: io.aequicor.visualization.editor.presentation.EditorWorkspaceState,
    layout: LayoutBox?,
    parentBox: BoundsBox?,
    selectedId: String?,
): AltTarget? {
    val hoveredId = ws.hoveredNodeId
    if (hoveredId.isNotBlank() && hoveredId != selectedId && hoveredId !in design.selectedNodeIds) {
        layout?.findBySourceId(hoveredId)?.let { box -> return AltTarget(box.toBoundsBox(), box.node.rotation) }
    }
    return parentBox?.let { AltTarget(it, 0.0) }
}

/**
 * The read-only Alt measurement preview: a red halo on the selected outline, a red outline
 * on the target, a dashed center-to-center line and px distance badges for whichever gaps
 * [measureGaps] resolves. Pure overlay drawing — never mutates selection/geometry.
 */
private fun DrawScope.drawAltMeasurement(
    selected: BoundsBox,
    selectedRotation: Double,
    target: BoundsBox,
    targetRotation: Double,
    viewport: CanvasViewport,
    textMeasurer: TextMeasurer,
    colors: io.aequicor.visualization.editor.ui.theme.EditorColors,
) {
    val red = colors.statusDanger
    val selectedVisual = selected.visualBounds(selectedRotation)
    val targetVisual = target.visualBounds(targetRotation)

    // Red halo under the existing blue selection outline (same rotated path, two strokes),
    // plus a plain red target outline.
    val selectedPath = rotatedOutlinePath(selected, selectedRotation, viewport)
    drawPath(selectedPath, color = red, style = Stroke(width = 3.5f))
    drawPath(selectedPath, color = colors.accent, style = Stroke(width = 1.5f))
    drawRotatedOutline(target, targetRotation, viewport, red, width = 1.5f)

    val dash = PathEffect.dashPathEffect(floatArrayOf(3f, 3f))
    drawLine(
        red.copy(alpha = 0.7f),
        viewport.toScreen(selectedVisual.centerX, selectedVisual.centerY),
        viewport.toScreen(targetVisual.centerX, targetVisual.centerY),
        strokeWidth = 1f,
        pathEffect = dash,
    )

    val gaps = measureGaps(selectedVisual, targetVisual)
    drawGapMeasurement(gaps, selectedVisual, targetVisual, viewport, red, colors.badgeSurface, textMeasurer)
}

/**
 * Draws each gap/center-distance [measureGaps] resolved. The boundary edge for a directional
 * gap is derived from the measured value itself (`boundary = selected edge -+ value`) rather
 * than re-deriving which of target's edges it is: [measureGaps] uses a different edge pairing
 * for the "target contains selected" (parent-frame padding) case than for the "separate
 * siblings" case, and this keeps the drawn line consistent with whichever one produced [gaps].
 */
private fun DrawScope.drawGapMeasurement(
    gaps: GapMeasurement,
    selected: BoundsBox,
    target: BoundsBox,
    viewport: CanvasViewport,
    accent: Color,
    surface: Color,
    textMeasurer: TextMeasurer,
) {
    fun overlapMidX() = (max(selected.x, target.x) + min(selected.right, target.right)) / 2.0
    fun overlapMidY() = (max(selected.y, target.y) + min(selected.bottom, target.bottom)) / 2.0

    gaps.top?.let { value ->
        val x = overlapMidX()
        val y1 = selected.y - value
        val y2 = selected.y
        drawMeasurementLine(viewport.toScreen(x, y1), viewport.toScreen(x, y2), accent, dashed = false)
        drawOutlinedBadge(formatMeasurement(value), viewport.toScreen(x, (y1 + y2) / 2.0), accent, surface, textMeasurer)
    }
    gaps.bottom?.let { value ->
        val x = overlapMidX()
        val y1 = selected.bottom
        val y2 = selected.bottom + value
        drawMeasurementLine(viewport.toScreen(x, y1), viewport.toScreen(x, y2), accent, dashed = false)
        drawOutlinedBadge(formatMeasurement(value), viewport.toScreen(x, (y1 + y2) / 2.0), accent, surface, textMeasurer)
    }
    gaps.left?.let { value ->
        val y = overlapMidY()
        val x1 = selected.x - value
        val x2 = selected.x
        drawMeasurementLine(viewport.toScreen(x1, y), viewport.toScreen(x2, y), accent, dashed = false)
        drawOutlinedBadge(formatMeasurement(value), viewport.toScreen((x1 + x2) / 2.0, y), accent, surface, textMeasurer)
    }
    gaps.right?.let { value ->
        val y = overlapMidY()
        val x1 = selected.right
        val x2 = selected.right + value
        drawMeasurementLine(viewport.toScreen(x1, y), viewport.toScreen(x2, y), accent, dashed = false)
        drawOutlinedBadge(formatMeasurement(value), viewport.toScreen((x1 + x2) / 2.0, y), accent, surface, textMeasurer)
    }
    gaps.centerXDistance?.let { value ->
        val y = (selected.centerY + target.centerY) / 2.0
        drawMeasurementLine(viewport.toScreen(selected.centerX, y), viewport.toScreen(target.centerX, y), accent, dashed = true)
        drawOutlinedBadge(formatMeasurement(value), viewport.toScreen((selected.centerX + target.centerX) / 2.0, y), accent, surface, textMeasurer)
    }
    gaps.centerYDistance?.let { value ->
        val x = (selected.centerX + target.centerX) / 2.0
        drawMeasurementLine(viewport.toScreen(x, selected.centerY), viewport.toScreen(x, target.centerY), accent, dashed = true)
        drawOutlinedBadge(formatMeasurement(value), viewport.toScreen(x, (selected.centerY + target.centerY) / 2.0), accent, surface, textMeasurer)
    }
}

private fun DrawScope.drawMeasurementLine(start: Offset, end: Offset, color: Color, dashed: Boolean) {
    val effect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(3f, 3f)) else null
    drawLine(color, start, end, strokeWidth = 1.5f, pathEffect = effect)
}

/**
 * Draws one beautiful-anchor [guide] in overlay space, styled by kind: blue for edge/center
 * alignment, a solid amber line with a "φ" badge for a golden-ratio line, and a dashed amber line
 * with a fraction badge for a simple proportion. Equal-spacing wins are drawn as [SpacingBar]s
 * instead, so `EqualSpacing` here is just a harmless fallback.
 */
private fun DrawScope.drawAnchorGuide(
    guide: AnchorGuide,
    viewport: CanvasViewport,
    colors: io.aequicor.visualization.editor.ui.theme.EditorColors,
    textMeasurer: TextMeasurer,
) {
    val start = viewport.toScreen(guide.line.x1, guide.line.y1)
    val end = viewport.toScreen(guide.line.x2, guide.line.y2)
    when (guide.kind) {
        // The equal-distance family (EqualSpacing/EqualMargin/MatchGap) draws green bars via
        // drawSpacingBar, never guide lines — this accent-line branch is a harmless fallback.
        AnchorKind.Alignment, AnchorKind.EqualSpacing, AnchorKind.EqualMargin, AnchorKind.MatchGap ->
            drawLine(colors.accent, start, end, strokeWidth = 1f)
        AnchorKind.GoldenRatio -> {
            drawLine(colors.statusWarning, start, end, strokeWidth = 1f)
            guide.label?.let { drawFilledBadge(it, midpoint(start, end), colors.statusWarning, textMeasurer) }
        }
        AnchorKind.Proportion -> {
            val dash = PathEffect.dashPathEffect(floatArrayOf(5f, 4f))
            drawLine(colors.statusWarning.copy(alpha = 0.75f), start, end, strokeWidth = 1f, pathEffect = dash)
            guide.label?.let { drawFilledBadge(it, midpoint(start, end), colors.statusWarning.copy(alpha = 0.9f), textMeasurer) }
        }
    }
}

/** Draws an equal-spacing distribution [bar] as a green measurement line with end caps and a px gap badge. */
private fun DrawScope.drawSpacingBar(
    bar: SpacingBar,
    viewport: CanvasViewport,
    colors: io.aequicor.visualization.editor.ui.theme.EditorColors,
    textMeasurer: TextMeasurer,
) {
    val start = viewport.toScreen(bar.segment.x1, bar.segment.y1)
    val end = viewport.toScreen(bar.segment.x2, bar.segment.y2)
    val green = colors.statusPositive
    drawMeasurementLine(start, end, green, dashed = false)
    drawSpacingCap(start, end, green)
    drawSpacingCap(end, start, green)
    drawOutlinedBadge(formatMeasurement(bar.gap), midpoint(start, end), green, colors.badgeSurface, textMeasurer)
}

/** A short perpendicular tick at [at], oriented across the bar running from [other] to [at]. */
private fun DrawScope.drawSpacingCap(at: Offset, other: Offset, color: Color) {
    val dx = at.x - other.x
    val dy = at.y - other.y
    val length = hypot(dx, dy)
    if (length == 0f) return
    val px = -dy / length
    val py = dx / length
    val half = 3f
    drawLine(color, Offset(at.x - px * half, at.y - py * half), Offset(at.x + px * half, at.y + py * half), strokeWidth = 1f)
}

private fun midpoint(a: Offset, b: Offset): Offset = Offset((a.x + b.x) / 2f, (a.y + b.y) / 2f)

/**
 * Resolves a handle resize via the pure [computeResize] (all geometry rules and the
 * min-size/position clamp live there and are unit-tested). Math derives from fixed
 * drag-start references — [baseline] (the box at press) — plus the cumulative pointer
 * displacement [docDx]/[docDy], so each frame sets absolute geometry rather than compounding
 * on the already-mutated live node.
 *
 * When the target is rotated, [rotationDegrees] inverse-rotates the drag delta into the
 * component's own (pre-rotation) axes first, so dragging a visually rotated handle grows the
 * component along the edge the user is actually looking at; [computeResize] then runs
 * entirely in that de-rotated frame. Its `dx`/`dy` (the shift of the authored top-left) come
 * back in that same de-rotated frame too, so — since position is stored in the single shared
 * document frame, not a per-node rotated one — they must be rotated forward again by
 * [rotationDegrees] before being added to the authored position; skipping that final rotation
 * would add a de-rotated-frame vector directly to a document-frame point.
 */
private fun computeRotatedResize(
    baseline: BoundsBox,
    handle: ResizeHandle,
    docDx: Float,
    docDy: Float,
    lockRatio: Boolean,
    rotationDegrees: Double,
): io.aequicor.visualization.editor.presentation.ResizeResult {
    val local = if (rotationDegrees != 0.0) rotateVector(docDx.toDouble(), docDy.toDouble(), -rotationDegrees) else GeoPoint(docDx.toDouble(), docDy.toDouble())
    val result = computeResize(
        baseWidth = baseline.width,
        baseHeight = baseline.height,
        handle = handle,
        docDx = local.x,
        docDy = local.y,
        lockRatio = lockRatio,
    )
    if (rotationDegrees == 0.0 || (result.dx == 0.0 && result.dy == 0.0)) return result
    val positionDelta = rotateVector(result.dx, result.dy, rotationDegrees)
    return result.copy(dx = positionDelta.x, dy = positionDelta.y)
}

private fun applyResize(
    state: MissionEditorStateHolder,
    nodeId: String,
    baseline: BoundsBox,
    originPos: DesignPoint?,
    handle: ResizeHandle,
    docDx: Float,
    docDy: Float,
    lockRatio: Boolean,
    rotationDegrees: Double = 0.0,
) {
    val result = computeRotatedResize(baseline, handle, docDx, docDy, lockRatio, rotationDegrees)
    // Position is parent-relative; the parent doesn't move during a resize, so the change
    // in absolute origin equals the change in authored position.
    if (originPos != null && (result.dx != 0.0 || result.dy != 0.0)) {
        state.dispatch(DesignEditorIntent.UpdatePosition(nodeId, x = originPos.x + result.dx, y = originPos.y + result.dy))
    }
    state.dispatch(DesignEditorIntent.UpdateSize(nodeId, width = result.width, height = result.height))
}

private fun applyGroupResize(
    state: MissionEditorStateHolder,
    targets: List<ResizeTarget>,
    baseline: BoundsBox,
    handle: ResizeHandle,
    docDx: Float,
    docDy: Float,
    lockRatio: Boolean,
) {
    val result = computeResize(
        baseWidth = baseline.width,
        baseHeight = baseline.height,
        handle = handle,
        docDx = docDx.toDouble(),
        docDy = docDy.toDouble(),
        lockRatio = lockRatio,
    )
    val transformed = transformedTargets(targets, baseline, result)
    transformed.forEach { item ->
        item.target.originPosition?.let { origin ->
            state.dispatch(DesignEditorIntent.UpdatePosition(item.target.nodeId, x = origin.x + item.dx, y = origin.y + item.dy))
        }
        state.dispatch(DesignEditorIntent.UpdateSize(item.target.nodeId, width = item.width, height = item.height))
    }
}

private fun commitResizeWriteBack(
    state: MissionEditorStateHolder,
    nodeId: String,
    baseline: BoundsBox,
    originPos: DesignPoint?,
    handle: ResizeHandle,
    docDx: Float,
    docDy: Float,
    lockRatio: Boolean,
    rotationDegrees: Double = 0.0,
) {
    val result = computeRotatedResize(baseline, handle, docDx, docDy, lockRatio, rotationDegrees)
    if (originPos != null && (result.dx != 0.0 || result.dy != 0.0)) {
        state.dispatch(DesignEditorIntent.PositionNode(nodeId, x = originPos.x + result.dx, y = originPos.y + result.dy))
    }
    state.dispatch(DesignEditorIntent.ResizeNode(nodeId, width = result.width, height = result.height))
}

private fun commitGroupResizeWriteBack(
    state: MissionEditorStateHolder,
    targets: List<ResizeTarget>,
    baseline: BoundsBox,
    handle: ResizeHandle,
    docDx: Float,
    docDy: Float,
    lockRatio: Boolean,
) {
    val result = computeResize(
        baseWidth = baseline.width,
        baseHeight = baseline.height,
        handle = handle,
        docDx = docDx.toDouble(),
        docDy = docDy.toDouble(),
        lockRatio = lockRatio,
    )
    transformedTargets(targets, baseline, result).forEach { item ->
        item.target.originPosition?.let { origin ->
            if (item.dx != 0.0 || item.dy != 0.0) {
                state.dispatch(DesignEditorIntent.PositionNode(item.target.nodeId, x = origin.x + item.dx, y = origin.y + item.dy))
            }
        }
        state.dispatch(DesignEditorIntent.ResizeNode(item.target.nodeId, width = item.width, height = item.height))
    }
}

private data class TransformedResizeTarget(
    val target: ResizeTarget,
    val dx: Double,
    val dy: Double,
    val width: Double,
    val height: Double,
)

private fun transformedTargets(
    targets: List<ResizeTarget>,
    baseline: BoundsBox,
    result: io.aequicor.visualization.editor.presentation.ResizeResult,
): List<TransformedResizeTarget> {
    val nextX = baseline.x + result.dx
    val nextY = baseline.y + result.dy
    val scaleX = if (baseline.width > 0.0) result.width / baseline.width else 1.0
    val scaleY = if (baseline.height > 0.0) result.height / baseline.height else 1.0
    return targets.map { target ->
        val box = target.baseline
        val targetX = nextX + (box.x - baseline.x) * scaleX
        val targetY = nextY + (box.y - baseline.y) * scaleY
        TransformedResizeTarget(
            target = target,
            dx = targetX - box.x,
            dy = targetY - box.y,
            width = (box.width * scaleX).coerceAtLeast(1.0),
            height = (box.height * scaleY).coerceAtLeast(1.0),
        )
    }
}

private fun commitMovedPositions(
    state: MissionEditorStateHolder,
    startPositions: Map<String, DesignPoint>,
) {
    val document = state.designState.document ?: return
    startPositions.forEach { (nodeId, start) ->
        val current = document.nodeById(nodeId)?.position ?: return@forEach
        if (current.x != start.x || current.y != start.y) {
            state.dispatch(DesignEditorIntent.PositionNode(nodeId, x = current.x, y = current.y))
        }
    }
}

/**
 * Shift-constrained creation endpoint: box shapes snap to a square, lines/arrows snap
 * the drag direction to the nearest 45°. Returns [current] unchanged when Shift is up.
 */
private fun constrainCreatePoint(start: Offset, current: Offset, kind: NewObjectKind, shift: Boolean): Offset {
    if (!shift) return current
    val dx = current.x - start.x
    val dy = current.y - start.y
    return when (kind) {
        NewObjectKind.Line, NewObjectKind.Arrow -> {
            val len = hypot(dx, dy)
            if (len == 0f) return current
            val quarter = (PI / 4.0).toFloat()
            val snapped = round(atan2(dy, dx) / quarter) * quarter
            Offset(start.x + cos(snapped) * len, start.y + sin(snapped) * len)
        }
        else -> {
            val side = max(abs(dx), abs(dy))
            Offset(start.x + if (dx < 0) -side else side, start.y + if (dy < 0) -side else side)
        }
    }
}

private fun commitCreate(
    state: MissionEditorStateHolder,
    mode: CanvasOperation.Create,
    start: Offset,
    end: Offset,
    zoomPx: Float,
    viewport: CanvasViewport,
    rootId: String,
    dragged: Boolean,
) {
    val layout = state.artboardLayout
    // Which node contains the creation origin? Fall back to the root frame.
    val parentId = hitNode(layout, state.designState.document, viewport, start).ifBlank { rootId }
    val parentBox = layout?.findBySourceId(parentId) ?: layout
    val docStartX = viewport.toDocX(min(start.x, end.x))
    val docStartY = viewport.toDocY(min(start.y, end.y))
    val relX = docStartX - (parentBox?.x ?: 0.0)
    val relY = docStartY - (parentBox?.y ?: 0.0)
    val defaultSize = io.aequicor.visualization.editor.presentation.EditorNodeFactory.defaultSizeFor(mode.kind)
    val w: Double = if (dragged) (abs(end.x - start.x) / zoomPx).toDouble() else (defaultSize.width ?: 100.0)
    val h: Double = if (dragged) (abs(end.y - start.y) / zoomPx).toDouble() else (defaultSize.height ?: 100.0)
    val isText = mode.kind == NewObjectKind.Text
    state.dispatch(
        DesignEditorIntent.CreateObject(
            kind = mode.kind,
            parentId = parentId,
            x = relX,
            y = relY,
            width = w.coerceAtLeast(if (isText) 20.0 else 4.0),
            height = h.coerceAtLeast(if (isText) 16.0 else 4.0),
            fixedWidthText = isText && dragged,
            enterTextEditing = isText,
        ),
    )
}

// --- Hit-testing helpers -----------------------------------------------------

private fun hitNode(layout: LayoutBox?, document: DesignDocument?, viewport: CanvasViewport, pos: Offset): String {
    layout ?: return ""
    val docX = viewport.toDocX(pos.x)
    val docY = viewport.toDocY(pos.y)
    val hit = layout.hitTest(docX, docY) ?: return ""
    val id = selectableNodeId(hit)
    // Don't select the root frame on an empty-area press (Figma clears instead).
    if (id == layout.node.sourceId) return ""
    val node = document?.nodeById(id) ?: return ""
    return if (node.locked || node.visible.literalOrNull() == false) "" else id
}

private fun nodesIn(layout: LayoutBox?, document: DesignDocument?, viewport: CanvasViewport, screenRect: Rect): Set<String> {
    layout ?: return emptySet()
    val docRect = DocumentRect.fromCorners(
        viewport.toDocX(screenRect.left),
        viewport.toDocY(screenRect.top),
        viewport.toDocX(screenRect.right),
        viewport.toDocY(screenRect.bottom),
    )
    val candidates = mutableListOf<SelectableBounds>()
    fun collect(box: LayoutBox) {
        box.children.forEach { child ->
            val id = child.node.selectableId
            val node = document?.nodeById(id)
            if (id.isNotBlank()) {
                candidates += SelectableBounds(
                    id = id,
                    bounds = DocumentRect.fromCorners(child.x, child.y, child.right, child.bottom),
                    locked = node?.locked == true,
                    visible = node?.visible?.literalOrNull() ?: true,
                )
            }
            collect(child)
        }
    }
    collect(layout)
    return marqueeSelection(docRect, candidates)
}

private fun resizeTargets(document: DesignDocument, layout: LayoutBox?, ids: Set<String>): List<ResizeTarget> {
    layout ?: return emptyList()
    val topLevelIds = ids.filterNot { id ->
        ids.any { candidateAncestor -> candidateAncestor != id && document.isSelfOrAncestor(id, candidateAncestor) }
    }
    return topLevelIds.mapNotNull { id ->
        val node = document.nodeById(id) ?: return@mapNotNull null
        if (node.locked || node.visible.literalOrNull() == false) return@mapNotNull null
        val box = layout.findBySourceId(id) ?: return@mapNotNull null
        val originPosition = node.position ?: DesignPoint().takeIf { document.isCoordinatePositioned(id) }
        ResizeTarget(nodeId = id, baseline = box.toBoundsBox(), originPosition = originPosition, rotation = box.node.rotation)
    }
}

// --- Viewport helpers --------------------------------------------------------

private data class FitRect(val x: Double, val y: Double, val w: Double, val h: Double)

private fun selectionHandleBounds(layout: LayoutBox?, ids: Set<String>): BoundsBox? {
    layout ?: return null
    val boxes = ids.mapNotNull { layout.findBySourceId(it)?.toBoundsBox() }
    if (boxes.isEmpty()) return null
    val minX = boxes.minOf { it.x }
    val minY = boxes.minOf { it.y }
    val maxX = boxes.maxOf { it.right }
    val maxY = boxes.maxOf { it.bottom }
    return BoundsBox(minX, minY, maxX - minX, maxY - minY)
}

/** Union of the selected nodes' boxes in document coordinates, or null when empty. */
private fun selectionBounds(layout: LayoutBox, ids: Set<String>): FitRect? {
    val box = selectionHandleBounds(layout, ids) ?: return null
    val pad = 24.0
    return FitRect(box.x - pad, box.y - pad, box.width + 2 * pad, box.height + 2 * pad)
}

private fun fitViewport(
    state: MissionEditorStateHolder,
    x: Double,
    y: Double,
    w: Double,
    h: Double,
    canvasWpx: Float,
    canvasHpx: Float,
    density: Float,
) {
    if (w <= 0 || h <= 0) return
    val margin = 0.9f
    val zoomPx = min(canvasWpx / w.toFloat(), canvasHpx / h.toFloat()) * margin
    val logicalZoom = (zoomPx / density).coerceIn(WorkspaceLimits.MinZoom, WorkspaceLimits.MaxZoom)
    val effPx = logicalZoom * density
    val panXpx = (canvasWpx - w.toFloat() * effPx) / 2f - x.toFloat() * effPx
    val panYpx = (canvasHpx - h.toFloat() * effPx) / 2f - y.toFloat() * effPx
    state.updateWorkspace {
        it.copy(
            viewport = it.viewport.copy(
                zoom = logicalZoom,
                panOffsetXDp = panXpx / density,
                panOffsetYDp = panYpx / density,
            ),
        )
    }
}

private fun zoomAt(state: MissionEditorStateHolder, focus: Offset, signedScroll: Float, density: Float) {
    val factor = zoomFactorForScroll(signedScroll)
    if (factor == 1f) return
    state.updateWorkspace {
        it.copy(viewport = it.viewport.zoomAround(focus.x, focus.y, factor, density))
    }
}

/**
 * Eases the current zoom toward [target], keeping the point at ([focusXpx], [focusYpx])
 * fixed — used by the +/-/1:1 controls so button zoom glides around the canvas center
 * instead of snapping (and drifting toward the document origin). Each animation frame
 * re-reads the live zoom and applies only the multiplicative delta, so the pass converges
 * on [target] exactly and cancels cleanly if the user starts a wheel/trackpad zoom.
 */
private suspend fun animateZoomTo(
    state: MissionEditorStateHolder,
    target: Float,
    focusXpx: Float,
    focusYpx: Float,
    density: Float,
) {
    val start = state.workspace.viewport.zoom
    if (abs(target - start) < 1e-4f) return
    Animatable(start).animateTo(target, tween(durationMillis = 150, easing = FastOutSlowInEasing)) {
        val current = state.workspace.viewport.zoom
        if (current > 0f) {
            state.updateWorkspace {
                it.copy(viewport = it.viewport.zoomAround(focusXpx, focusYpx, value / current, density))
            }
        }
    }
}

/**
 * Queues an animated zoom for the +/- buttons. The step compounds off any still-pending
 * target so rapid clicks accumulate instead of collapsing to a single step; the canvas then
 * eases to it around its center (see [animateZoomTo]).
 */
private fun requestZoom(state: MissionEditorStateHolder, step: (Float) -> Float) {
    state.updateWorkspace {
        val base = it.pendingZoomTo ?: it.zoom
        it.copy(pendingZoomTo = step(base).coerceIn(WorkspaceLimits.MinZoom, WorkspaceLimits.MaxZoom))
    }
}

private fun cursorFor(tool: EditorTool) = when (tool) {
    EditorTool.Text -> androidx.compose.ui.input.pointer.PointerIcon.Text
    EditorTool.Select -> androidx.compose.ui.input.pointer.PointerIcon.Default
    else -> androidx.compose.ui.input.pointer.PointerIcon.Crosshair
}

// --- Bottom controls ---------------------------------------------------------

/** Segmented `[Canvas | Scene]` mode switch (design-book §19). Writes only workspace — no undo entry. */
@Composable
private fun SceneModeToggle(selected: EditorMode, onSelect: (EditorMode) -> Unit) {
    val colors = LocalEditorColors.current
    val shape = RoundedCornerShape(8.dp)
    Surface(
        modifier = Modifier.height(48.dp).clip(shape),
        shape = shape,
        color = Color.White,
        border = BorderStroke(1.dp, colors.panelStroke),
        shadowElevation = 2.dp,
    ) {
        Row {
            EditorMode.entries.forEach { mode ->
                val active = mode == selected
                Box(
                    modifier = Modifier.width(72.dp).fillMaxHeight()
                        .background(if (active) colors.accent else colors.controlSurface)
                        .clickable { onSelect(mode) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        mode.title,
                        color = if (active) Color.White else colors.ink,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceControl(selected: DeviceMode, onSelect: (DeviceMode) -> Unit) {
    val colors = LocalEditorColors.current
    val shape = RoundedCornerShape(8.dp)
    Surface(
        modifier = Modifier.height(48.dp).clip(shape),
        shape = shape,
        color = Color.White,
        border = BorderStroke(1.dp, colors.panelStroke),
        shadowElevation = 2.dp,
    ) {
        Row {
            DeviceMode.entries.forEach { mode ->
                val active = mode == selected
                Box(
                    modifier = Modifier.width(64.dp).fillMaxHeight()
                        .background(if (active) colors.selectionFill else colors.controlSurface)
                        .clickable { onSelect(mode) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        mode.title,
                        color = if (active) colors.accent else Color.Black,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun FloatingToolbar(selected: EditorTool, onSelect: (EditorTool) -> Unit) {
    val colors = LocalEditorColors.current
    Surface(
        modifier = Modifier.height(50.dp).widthIn(max = 560.dp),
        shape = RoundedCornerShape(8.dp),
        color = Color.White,
        border = BorderStroke(1.dp, colors.panelStroke),
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxHeight().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            EditorTool.entries.forEach { tool ->
                val active = tool == selected
                val shape = RoundedCornerShape(8.dp)
                Box(
                    modifier = Modifier.size(36.dp)
                        .background(if (active) colors.accent else colors.controlSurface, shape)
                        .border(1.dp, if (active) colors.accent else colors.controlStroke, shape)
                        .clip(shape)
                        .clickable { onSelect(tool) },
                    contentAlignment = Alignment.Center,
                ) {
                    EditorSvgIcon(
                        icon = toolIcon(tool),
                        contentDescription = tool.label,
                        modifier = Modifier.size(24.dp),
                        tint = if (active) Color.White else colors.ink,
                    )
                }
            }
        }
    }
}

private fun toolIcon(tool: EditorTool): EditorIcon = when (tool) {
    EditorTool.Select -> EditorIcon.Select
    EditorTool.Frame -> EditorIcon.Frame
    EditorTool.Rectangle -> EditorIcon.Rectangle
    EditorTool.Pen -> EditorIcon.Pen
    EditorTool.Text -> EditorIcon.Text
    EditorTool.Comment -> EditorIcon.Comments
    EditorTool.Link -> EditorIcon.Link
    EditorTool.Code -> EditorIcon.Code
}

@Composable
private fun ZoomControls(state: MissionEditorStateHolder) {
    val colors = LocalEditorColors.current
    val ws = state.workspace
    Surface(
        modifier = Modifier.height(48.dp),
        shape = RoundedCornerShape(8.dp),
        color = Color.White,
        border = BorderStroke(1.dp, colors.panelStroke),
        shadowElevation = 2.dp,
    ) {
        Row(Modifier.padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ZoomButton("-") { requestZoom(state) { base -> base / WorkspaceLimits.ZoomButtonStep } }
            Text("${(ws.zoom * 100).roundToInt()}%", modifier = Modifier.widthIn(min = 44.dp), style = MaterialTheme.typography.labelMedium, color = colors.ink, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
            ZoomButton("+") { requestZoom(state) { base -> base * WorkspaceLimits.ZoomButtonStep } }
            Spacer(Modifier.width(2.dp))
            ZoomButton("1:1") { state.updateWorkspace { it.copy(pendingFit = PendingFit.None, pendingZoomTo = 1f) } }
            ZoomIconButton(EditorIcon.ZoomFit, "Fit screen") { requestFit(state, fitSelection = false) }
            ZoomIconButton(EditorIcon.Marquee, "Fit selection") { requestFit(state, fitSelection = true) }
        }
    }
}

@Composable
private fun ZoomIconButton(icon: EditorIcon, contentDescription: String, onClick: () -> Unit) {
    val colors = LocalEditorColors.current
    val shape = RoundedCornerShape(6.dp)
    Box(
        Modifier
            .size(30.dp)
            .background(colors.controlSurface, shape)
            .border(1.dp, colors.controlStroke, shape)
            .clip(shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        EditorSvgIcon(icon = icon, contentDescription = contentDescription, modifier = Modifier.size(16.dp), tint = colors.ink)
    }
}

@Composable
private fun ZoomButton(label: String, onClick: () -> Unit) {
    val colors = LocalEditorColors.current
    val shape = RoundedCornerShape(6.dp)
    Box(
        Modifier
            .size(30.dp)
            .background(colors.controlSurface, shape)
            .border(1.dp, colors.controlStroke, shape)
            .clip(shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = colors.ink, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * Requests a fit on the next composition by clearing the fit marker. Fit-screen frames
 * the whole root; fit-selection frames the selection bounds (falls back to screen).
 * The actual math runs in [CanvasSurface] where the canvas size is known.
 */
private fun requestFit(state: MissionEditorStateHolder, fitSelection: Boolean) {
    state.updateWorkspace { it.copy(pendingFit = if (fitSelection) PendingFit.Selection else PendingFit.Screen) }
}
