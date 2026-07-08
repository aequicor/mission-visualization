package io.aequicor.visualization.editor.ui

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.isTertiaryPressed
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.time.TimeSource
import io.aequicor.visualization.MissionEditorStateHolder
import io.aequicor.visualization.editor.presentation.CanvasOperation
import io.aequicor.visualization.editor.presentation.DesignEditorIntent
import io.aequicor.visualization.editor.presentation.DocumentRect
import io.aequicor.visualization.editor.presentation.DeviceMode
import io.aequicor.visualization.editor.presentation.EditorTool
import io.aequicor.visualization.editor.presentation.FocusMode
import io.aequicor.visualization.editor.presentation.NewObjectKind
import io.aequicor.visualization.editor.presentation.PendingFit
import io.aequicor.visualization.editor.presentation.SelectableBounds
import io.aequicor.visualization.editor.presentation.ResizeHandle
import io.aequicor.visualization.editor.presentation.WorkspaceLimits
import io.aequicor.visualization.editor.presentation.computeResize
import io.aequicor.visualization.editor.presentation.isSelfOrAncestor
import io.aequicor.visualization.editor.presentation.marqueeSelection
import io.aequicor.visualization.editor.ui.theme.LocalEditorColors
import io.aequicor.visualization.engine.backend.compose.CanvasViewport
import io.aequicor.visualization.engine.backend.compose.DesignArtboard
import io.aequicor.visualization.engine.backend.compose.selectableNodeId
import io.aequicor.visualization.engine.ir.layout.LayoutBox
import io.aequicor.visualization.engine.ir.model.DesignDocument
import io.aequicor.visualization.engine.ir.model.DesignPoint
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.engine.ir.model.ShapeType
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
 * resize and tool-driven object creation — plus keyboard nudge/duplicate/delete/undo.
 * Every gesture maps into a [DesignEditorIntent]; the workspace owns zoom/pan.
 */
@Composable
fun EditorCanvasPane(state: MissionEditorStateHolder, modifier: Modifier = Modifier) {
    val colors = LocalEditorColors.current
    val ws = state.workspace
    val pageName = state.designState.document
        ?.pageById(state.designState.selectedPageId)?.name.orEmpty().ifBlank { "Untitled" }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Canvas — $pageName",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HeaderIconButton(
                    icon = EditorIcon.Source,
                    contentDescription = "Toggle source panel",
                    onClick = { state.updateWorkspace { it.copy(sourceCollapsed = !it.sourceCollapsed) } },
                    active = ws.sourceCollapsed,
                )
                HeaderIconButton(
                    icon = EditorIcon.Inspector,
                    contentDescription = "Toggle inspector panel",
                    onClick = { state.updateWorkspace { it.copy(inspectorCollapsed = !it.inspectorCollapsed) } },
                    active = ws.inspectorCollapsed,
                )
                HeaderIconButton(
                    icon = EditorIcon.ZoomFit,
                    contentDescription = "Focus canvas",
                    onClick = { state.updateWorkspace { it.copy(focusMode = FocusMode.MainOnly) } },
                )
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth().weight(1f),
            shape = RoundedCornerShape(8.dp),
            color = colors.paneSurface,
            border = BorderStroke(1.dp, colors.panelStroke),
        ) {
            CanvasSurface(state)
        }

        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            DeviceControl(ws.deviceMode) { mode -> state.updateWorkspace { it.copy(deviceMode = mode) } }
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

    BoxWithConstraints(Modifier.fillMaxSize().padding(4.dp)) {
        val canvasWpx = maxWidth.value * density
        val canvasHpx = maxHeight.value * density
        val layout = state.artboardLayout

        // Fit the current screen once per page (keeps the user's later zoom/pan).
        var fittedPage by remember { mutableStateOf("") }
        if (layout != null && canvasWpx > 0f && fittedPage != pageId) {
            fittedPage = pageId
            fitViewport(state, 0.0, 0.0, layout.width, layout.height, canvasWpx, canvasHpx, density)
        }
        // Honor an explicit fit-screen / fit-selection request from the zoom controls.
        if (layout != null && canvasWpx > 0f && ws.pendingFit != PendingFit.None) {
            val rect = when (ws.pendingFit) {
                PendingFit.Selection -> selectionBounds(layout, design.selectedNodeIds) ?: FitRect(0.0, 0.0, layout.width, layout.height)
                else -> FitRect(0.0, 0.0, layout.width, layout.height)
            }
            state.updateWorkspace { it.copy(pendingFit = PendingFit.None) }
            fitViewport(state, rect.x, rect.y, rect.w, rect.h, canvasWpx, canvasHpx, density)
        }

        val viewportModel = ws.viewport
        val zoomPx = viewportModel.zoomPx(density)
        val viewport = CanvasViewport(zoomPx, viewportModel.panXPx(density), viewportModel.panYPx(density))
        val multiSelectionBox = if (design.selectedNodeIds.size > 1) selectionHandleBounds(layout, design.selectedNodeIds) else null

        // Transient gesture visuals.
        var marquee by remember { mutableStateOf<Rect?>(null) }
        var createRect by remember { mutableStateOf<Rect?>(null) }
        var badge by remember { mutableStateOf<String?>(null) }

        // Keyboard focus + modifier tracking (Shift for additive select / big nudge, Space for pan).
        val focusRequester = remember { FocusRequester() }
        var shiftHeld by remember { mutableStateOf(false) }
        var spaceHeld by remember { mutableStateOf(false) }
        var lastTapMark by remember { mutableStateOf<TimeSource.Monotonic.ValueTimeMark?>(null) }
        var lastTapId by remember { mutableStateOf("") }
        LaunchedEffect(pageId) { runCatching { focusRequester.requestFocus() } }

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

        if (document != null && rootNode != null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .focusRequester(focusRequester)
                    .focusTarget()
                    .onPreviewKeyEvent { event ->
                        shiftHeld = event.isShiftPressed
                        if (event.key == Key.Spacebar && state.designState.editingTextNodeId.isBlank()) {
                            spaceHeld = event.type == KeyEventType.KeyDown
                            return@onPreviewKeyEvent true
                        }
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        handleCanvasKey(state, event.key, event.isShiftPressed, event.isCtrlPressed || event.isMetaPressed)
                    }
                    // Hover + scroll (pan / ctrl-zoom).
                    .pointerInput(pageId, viewport, ws.tool) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                when (event.type) {
                                    PointerEventType.Scroll -> {
                                        val change = event.changes.firstOrNull() ?: continue
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
                                            val hit = hitNode(layout, document, viewport, change.position)
                                            // Compare against the live value: `ws` is frozen at pointerInput
                                            // setup, so an empty hit must clear whatever is hovered now.
                                            if (hit != state.workspace.hoveredNodeId) {
                                                state.updateWorkspace { it.copy(hoveredNodeId = hit) }
                                            }
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
                            val forcePan = currentEvent.buttons.isTertiaryPressed || spaceHeld
                            val primaryBox = design.selectedNodeId.takeIf { it.isNotBlank() }?.let { layout?.findBySourceId(it) }
                            // A locked selection exposes no resize handles (design-book §7).
                            val selectionLocked = state.designState.selectedNodeIds.any { id -> document.nodeById(id)?.locked == true }
                            val selectedResizeBox = if (state.designState.selectedNodeIds.size > 1) {
                                selectionHandleBounds(layout, state.designState.selectedNodeIds)
                            } else {
                                primaryBox?.toBoundsBox()
                            }
                            val handle = if (!forcePan && ws.tool == EditorTool.Select && selectedResizeBox != null && !selectionLocked) {
                                handleAt(selectedResizeBox, viewport, start)
                            } else {
                                null
                            }
                            val hitId = hitNode(layout, document, viewport, start)
                            val mode = resolveCanvasOperation(ws.tool, forcePan, handle, hitId)

                            // Pre-press selection so a drag moves the pressed node (not on shift-add).
                            if (mode == CanvasOperation.Move && hitId.isNotBlank() && hitId !in design.selectedNodeIds && !shiftHeld) {
                                state.dispatch(DesignEditorIntent.SelectNode(hitId))
                            }
                            val moveStartPositions = if (mode == CanvasOperation.Move) {
                                state.designState.selectedNodeIds.mapNotNull { id ->
                                    state.designState.document?.nodeById(id)?.position?.let { position -> id to position }
                                }.toMap()
                            } else {
                                emptyMap()
                            }

                            var moved = false
                            var operationBegan = false
                            var documentBegan = false
                            var canceled = false
                            var last = start
                            val resizeBaseline = (mode as? CanvasOperation.Resize)?.let { selectedResizeBox }
                            val resizeTargets = (mode as? CanvasOperation.Resize)?.let {
                                resizeTargets(document, layout, state.designState.selectedNodeIds)
                            }.orEmpty()
                            var accX = 0f
                            var accY = 0f

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
                                if (!documentBegan && (mode == CanvasOperation.Move || mode is CanvasOperation.Resize)) {
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
                                    CanvasOperation.Move -> {
                                        // Read the live selection: a new node may have been selected on press.
                                        state.dispatch(DesignEditorIntent.MoveNodes(state.designState.selectedNodeIds, (delta.x / zoomPx).toDouble(), (delta.y / zoomPx).toDouble()))
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
                                                applyResize(state, target.nodeId, resizeBaseline, target.originPosition, mode.handle, accX / zoomPx, accY / zoomPx, state.workspace.lockAspectRatio || shiftHeld)
                                            }
                                        }
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
                                                val ids = nodesIn(layout, document, viewport, rect)
                                                val next = if (shiftHeld) design.selectedNodeIds + ids else ids
                                                state.dispatch(DesignEditorIntent.SelectNodes(next))
                                            }
                                        } else if (hitId.isBlank()) {
                                            state.dispatch(DesignEditorIntent.ClearSelection)
                                        }
                                    }
                                    is CanvasOperation.Create -> {
                                        val end = constrainCreatePoint(start, last, mode.kind, shiftHeld)
                                        commitCreate(state, mode, start, end, zoomPx, viewport, rootNode.id, moved)
                                        state.updateWorkspace { it.copy(tool = EditorTool.Select) }
                                    }
                                    CanvasOperation.Move -> {
                                        if (moved) {
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
                        }
                    }
                    .pointerHoverIcon(cursorFor(if (spaceHeld) EditorTool.Hand else ws.tool)),
            ) {
                DesignArtboard(
                    document = document,
                    pageId = pageId,
                    modifier = Modifier.fillMaxSize(),
                    deviceWidth = ws.deviceMode.width,
                    deviceHeight = ws.deviceMode.height,
                    selectedNodeId = design.selectedNodeId,
                    selectedNodeIds = design.selectedNodeIds,
                    hoveredNodeId = ws.hoveredNodeId,
                    vectorEditNodeId = ws.vectorEditNodeId,
                    viewport = viewport,
                    interactive = false,
                    onLayoutComputed = state::onArtboardLayout,
                )
            }
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No preview", color = colors.mutedInk)
            }
        }

        // Transient overlays (marquee / creation rect / dimension badge).
        Canvas(Modifier.matchParentSize()) {
            marquee?.let { r ->
                drawRect(colors.accent.copy(alpha = 0.12f), topLeft = r.topLeft, size = r.size)
                drawRect(colors.accent, topLeft = r.topLeft, size = r.size, style = Stroke(width = 1f))
            }
            createRect?.let { r ->
                drawRect(colors.accent.copy(alpha = 0.10f), topLeft = r.topLeft, size = r.size)
                drawRect(colors.accent, topLeft = r.topLeft, size = r.size, style = Stroke(width = 1.5f))
            }
            if (multiSelectionBox != null && ws.vectorEditNodeId.isBlank()) {
                drawSelectionBounds(multiSelectionBox, viewport, colors.accent, handles = true)
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

        // Vector edit mode: draw/drag path anchors of the target shape.
        VectorEditOverlay(state, layout, viewport, zoomPx)

        // Inline text editing overlay for a double-clicked text node.
        TextEditOverlay(state, layout, viewport)
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

@Composable
private fun VectorEditOverlay(state: MissionEditorStateHolder, layout: LayoutBox?, viewport: CanvasViewport, zoomPx: Float) {
    val colors = LocalEditorColors.current
    val editId = state.workspace.vectorEditNodeId
    if (editId.isBlank()) return
    val node = state.designState.document?.nodeById(editId) ?: return
    val kind = node.kind as? DesignNodeKind.Shape ?: return
    val box = layout?.findBySourceId(editId) ?: return
    // Anchors are authored in the shape's viewBox / path space, scaled to the box.
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
                // Find the nearest anchor to the press.
                val nearest = anchors.minByOrNull { (_, a) ->
                    val sx = viewport.toScreen(box.x + a.x * scaleX, box.y + a.y * scaleY)
                    (sx - down.position).getDistanceSquared()
                }
                val within = nearest?.let { (_, a) ->
                    val sx = viewport.toScreen(box.x + a.x * scaleX, box.y + a.y * scaleY)
                    (sx - down.position).getDistance() <= 12f
                } ?: false
                if (!within || nearest == null) {
                    // A press outside the shape's bounds leaves vector edit mode (Figma-like).
                    val tl = viewport.toScreen(box.x, box.y)
                    val br = viewport.toScreen(box.right, box.bottom)
                    val outside = down.position.x < tl.x || down.position.y < tl.y ||
                        down.position.x > br.x || down.position.y > br.y
                    if (outside) state.updateWorkspace { it.copy(vectorEditNodeId = "", vectorSelectedPoint = null) }
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

/** Enters text-editing (text nodes) or vector-edit (vector shapes) on double-click. */
private fun enterEditMode(state: MissionEditorStateHolder, nodeId: String) {
    val node = state.designState.document?.nodeById(nodeId) ?: return
    when {
        node.kind is DesignNodeKind.Text -> state.dispatch(DesignEditorIntent.SetEditingText(nodeId))
        node.kind is DesignNodeKind.Shape && (node.kind as DesignNodeKind.Shape).shape == ShapeType.Vector ->
            state.updateWorkspace { it.copy(vectorEditNodeId = nodeId, vectorSelectedPoint = null) }
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
    return when {
        key == Key.DirectionLeft -> nudge(-step, 0.0)
        key == Key.DirectionRight -> nudge(step, 0.0)
        key == Key.DirectionUp -> nudge(0.0, -step)
        key == Key.DirectionDown -> nudge(0.0, step)
        (key == Key.Delete || key == Key.Backspace) && selection.isNotEmpty() -> {
            state.dispatch(DesignEditorIntent.DeleteNodes(selection)); true
        }
        ctrl && key == Key.D && selection.isNotEmpty() -> { state.dispatch(DesignEditorIntent.DuplicateNodes(selection)); true }
        ctrl && key == Key.A -> { state.dispatch(DesignEditorIntent.SelectAll); true }
        ctrl && shift && key == Key.Z -> { state.dispatch(DesignEditorIntent.Redo); true }
        ctrl && key == Key.Z -> { state.dispatch(DesignEditorIntent.Undo); true }
        key == Key.Escape -> {
            when {
                // A live drag takes priority: abort it (revert + no undo entry).
                state.activeDrag -> state.requestCancelDrag()
                state.workspace.vectorEditNodeId.isNotBlank() -> state.updateWorkspace { it.copy(vectorEditNodeId = "", vectorSelectedPoint = null) }
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
    hitId: String,
): CanvasOperation = when {
    forcePan || tool == EditorTool.Hand -> CanvasOperation.Pan
    tool.creates != null -> CanvasOperation.Create(tool.creates)
    handle != null -> CanvasOperation.Resize(handle)
    hitId.isNotBlank() -> CanvasOperation.Move
    else -> CanvasOperation.Marquee
}

private data class BoundsBox(
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,
) {
    val right: Double get() = x + width
    val bottom: Double get() = y + height
}

private data class ResizeTarget(
    val nodeId: String,
    val baseline: BoundsBox,
    val originPosition: DesignPoint?,
)

private fun LayoutBox.toBoundsBox(): BoundsBox =
    BoundsBox(x = x, y = y, width = width, height = height)

private const val HandleHitRadiusPx = 11f

private fun handleAt(box: BoundsBox, viewport: CanvasViewport, pos: Offset): ResizeHandle? {
    val tl = viewport.toScreen(box.x, box.y)
    val br = viewport.toScreen(box.right, box.bottom)
    val cx = (tl.x + br.x) / 2f
    val cy = (tl.y + br.y) / 2f
    val points = mapOf(
        ResizeHandle.TopLeft to Offset(tl.x, tl.y),
        ResizeHandle.Top to Offset(cx, tl.y),
        ResizeHandle.TopRight to Offset(br.x, tl.y),
        ResizeHandle.Left to Offset(tl.x, cy),
        ResizeHandle.Right to Offset(br.x, cy),
        ResizeHandle.BottomLeft to Offset(tl.x, br.y),
        ResizeHandle.Bottom to Offset(cx, br.y),
        ResizeHandle.BottomRight to Offset(br.x, br.y),
    )
    return points.minByOrNull { (_, p) -> (p - pos).getDistanceSquared() }
        ?.takeIf { (_, p) -> (p - pos).getDistance() <= HandleHitRadiusPx }?.key
}

private fun DrawScope.drawSelectionBounds(box: BoundsBox, viewport: CanvasViewport, color: Color, handles: Boolean) {
    val tl = viewport.toScreen(box.x, box.y)
    val br = viewport.toScreen(box.right, box.bottom)
    val rect = Rect(tl, br)
    drawRect(color = color, topLeft = rect.topLeft, size = rect.size, style = Stroke(width = 1.5f))
    if (!handles) return
    val handle = 8f
    val positions = listOf(
        rect.topLeft,
        Offset(rect.center.x, rect.top),
        rect.topRight,
        Offset(rect.left, rect.center.y),
        Offset(rect.right, rect.center.y),
        rect.bottomLeft,
        Offset(rect.center.x, rect.bottom),
        rect.bottomRight,
    )
    positions.forEach { center ->
        val topLeft = Offset(center.x - handle / 2f, center.y - handle / 2f)
        drawRect(color = Color.White, topLeft = topLeft, size = Size(handle, handle))
        drawRect(color = color, topLeft = topLeft, size = Size(handle, handle), style = Stroke(width = 1.5f))
    }
}

/**
 * Applies a handle resize via the pure [computeResize] (all geometry rules and the
 * min-size/position clamp live there and are unit-tested). Math derives from fixed
 * drag-start references — [baseline] (the box at press) and [originPos] (the authored
 * position at press) — plus the cumulative pointer displacement [docDx]/[docDy], so each
 * frame sets absolute geometry rather than compounding on the already-mutated live node.
 */
private fun applyResize(
    state: MissionEditorStateHolder,
    nodeId: String,
    baseline: BoundsBox,
    originPos: DesignPoint?,
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
) {
    val result = computeResize(
        baseWidth = baseline.width,
        baseHeight = baseline.height,
        handle = handle,
        docDx = docDx.toDouble(),
        docDy = docDy.toDouble(),
        lockRatio = lockRatio,
    )
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
        ResizeTarget(nodeId = id, baseline = box.toBoundsBox(), originPosition = node.position)
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

private fun zoomAt(state: MissionEditorStateHolder, focus: Offset, wheel: Float, density: Float) {
    val factor = if (wheel > 0) 1.1f else 0.9f
    state.updateWorkspace {
        it.copy(viewport = it.viewport.zoomAround(focus.x, focus.y, factor, density))
    }
}

private fun cursorFor(tool: EditorTool) = when (tool) {
    EditorTool.Hand -> androidx.compose.ui.input.pointer.PointerIcon.Hand
    EditorTool.Text -> androidx.compose.ui.input.pointer.PointerIcon.Text
    EditorTool.Select -> androidx.compose.ui.input.pointer.PointerIcon.Default
    else -> androidx.compose.ui.input.pointer.PointerIcon.Crosshair
}

// --- Bottom controls ---------------------------------------------------------

@Composable
private fun DeviceControl(selected: DeviceMode, onSelect: (DeviceMode) -> Unit) {
    val colors = LocalEditorColors.current
    Surface(
        modifier = Modifier.height(48.dp),
        shape = RoundedCornerShape(8.dp),
        color = Color.White,
        border = BorderStroke(1.dp, colors.panelStroke),
        shadowElevation = 2.dp,
    ) {
        Row {
            DeviceMode.entries.forEach { mode ->
                val active = mode == selected
                Box(
                    modifier = Modifier.width(64.dp).fillMaxHeight()
                        .background(if (active) colors.selectionFill else Color.White)
                        .clickable { onSelect(mode) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        mode.title,
                        color = if (active) colors.accent else Color.Black,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold,
                        style = MaterialTheme.typography.labelMedium,
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
                Box(
                    modifier = Modifier.size(36.dp)
                        .background(if (active) colors.accent else Color.White, RoundedCornerShape(8.dp))
                        .clickable { onSelect(tool) },
                    contentAlignment = Alignment.Center,
                ) {
                    EditorSvgIcon(
                        icon = toolIcon(tool),
                        contentDescription = tool.label,
                        modifier = Modifier.size(19.dp),
                        tint = if (active) Color.White else colors.ink,
                    )
                }
            }
        }
    }
}

private fun toolIcon(tool: EditorTool): EditorIcon = when (tool) {
    EditorTool.Select -> EditorIcon.Select
    EditorTool.Hand -> EditorIcon.HandPan
    EditorTool.Frame -> EditorIcon.Frame
    EditorTool.Component -> EditorIcon.Component
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
            ZoomButton("-") {
                state.updateWorkspace {
                    it.copy(viewport = it.viewport.copy(zoom = (it.zoom * 0.9f).coerceIn(WorkspaceLimits.MinZoom, WorkspaceLimits.MaxZoom)))
                }
            }
            Text("${(ws.zoom * 100).roundToInt()}%", modifier = Modifier.widthIn(min = 44.dp), style = MaterialTheme.typography.labelMedium, color = colors.ink)
            ZoomButton("+") {
                state.updateWorkspace {
                    it.copy(viewport = it.viewport.copy(zoom = (it.zoom * 1.1f).coerceIn(WorkspaceLimits.MinZoom, WorkspaceLimits.MaxZoom)))
                }
            }
            Spacer(Modifier.width(2.dp))
            ZoomButton("1:1") { state.updateWorkspace { it.copy(pendingFit = PendingFit.None, viewport = it.viewport.copy(zoom = 1f)) } }
            ZoomIconButton(EditorIcon.ZoomFit, "Fit screen") { requestFit(state, fitSelection = false) }
            ZoomIconButton(EditorIcon.Marquee, "Fit selection") { requestFit(state, fitSelection = true) }
        }
    }
}

@Composable
private fun ZoomIconButton(icon: EditorIcon, contentDescription: String, onClick: () -> Unit) {
    val colors = LocalEditorColors.current
    Box(
        Modifier.size(30.dp).background(colors.raisedSurface, RoundedCornerShape(6.dp)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        EditorSvgIcon(icon = icon, contentDescription = contentDescription, modifier = Modifier.size(16.dp), tint = colors.ink)
    }
}

@Composable
private fun ZoomButton(label: String, onClick: () -> Unit) {
    val colors = LocalEditorColors.current
    Box(
        Modifier.size(30.dp).background(colors.raisedSurface, RoundedCornerShape(6.dp)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = colors.ink)
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
