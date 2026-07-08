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
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.time.TimeSource
import io.aequicor.visualization.MissionEditorStateHolder
import io.aequicor.visualization.editor.presentation.DesignEditorIntent
import io.aequicor.visualization.editor.presentation.DeviceMode
import io.aequicor.visualization.editor.presentation.EditorTool
import io.aequicor.visualization.editor.presentation.FocusMode
import io.aequicor.visualization.editor.presentation.NewObjectKind
import io.aequicor.visualization.editor.presentation.PendingFit
import io.aequicor.visualization.editor.presentation.WorkspaceLimits
import io.aequicor.visualization.editor.ui.theme.LocalEditorColors
import io.aequicor.visualization.engine.backend.compose.CanvasViewport
import io.aequicor.visualization.engine.backend.compose.DesignArtboard
import io.aequicor.visualization.engine.backend.compose.selectableNodeId
import io.aequicor.visualization.engine.ir.layout.LayoutBox
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.engine.ir.model.ShapeType
import io.aequicor.visualization.engine.ir.model.literalOrNull
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

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
                    "src",
                    onClick = { state.updateWorkspace { it.copy(sourceCollapsed = !it.sourceCollapsed) } },
                    active = ws.sourceCollapsed,
                )
                HeaderIconButton(
                    "insp",
                    onClick = { state.updateWorkspace { it.copy(inspectorCollapsed = !it.inspectorCollapsed) } },
                    active = ws.inspectorCollapsed,
                )
                HeaderIconButton(
                    "focus",
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

        val zoomPx = ws.zoom * density
        val viewport = CanvasViewport(zoomPx, ws.panXDp * density, ws.panYDp * density)

        // Transient gesture visuals.
        var marquee by remember { mutableStateOf<Rect?>(null) }
        var createRect by remember { mutableStateOf<Rect?>(null) }
        var badge by remember { mutableStateOf<String?>(null) }

        // Keyboard focus + modifier tracking (Shift for additive select / big nudge).
        val focusRequester = remember { FocusRequester() }
        var shiftHeld by remember { mutableStateOf(false) }
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
                                        // Scroll zooms toward the cursor (pan is available via the Hand tool).
                                        zoomAt(state, change.position, -change.scrollDelta.y, density)
                                        change.consume()
                                    }
                                    PointerEventType.Move -> {
                                        val change = event.changes.firstOrNull() ?: continue
                                        if (!change.pressed) {
                                            val hit = hitNode(layout, viewport, change.position)
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
                    .pointerInput(pageId, viewport, ws.tool, design.selectedNodeId, design.selectedNodeIds) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            runCatching { focusRequester.requestFocus() }
                            val start = down.position
                            val primaryBox = design.selectedNodeId.takeIf { it.isNotBlank() }?.let { layout?.findBySourceId(it) }
                            val handle = if (ws.tool == EditorTool.Select && primaryBox != null) {
                                handleAt(primaryBox, viewport, start)
                            } else {
                                null
                            }
                            val hitId = hitNode(layout, viewport, start)
                            val mode = resolveDragMode(ws.tool, handle, hitId, design.selectedNodeIds)

                            // Pre-press selection so a drag moves the pressed node (not on shift-add).
                            if (mode is DragMode.Move && hitId.isNotBlank() && hitId !in design.selectedNodeIds && !shiftHeld) {
                                state.dispatch(DesignEditorIntent.SelectNode(hitId))
                            }

                            var moved = false
                            var began = false
                            var last = start
                            val resizeBaseline = (mode as? DragMode.Resize)?.let { primaryBox }
                            // Authored position of the resize target at drag start (fixed reference).
                            val resizeOrigin = (mode as? DragMode.Resize)?.let { design.document?.nodeById(design.selectedNodeId)?.position }
                            var accX = 0f
                            var accY = 0f

                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: break
                                if (change.changedToUp()) break
                                val pos = change.position
                                val delta = pos - last
                                last = pos
                                if (delta != Offset.Zero) {
                                    moved = true
                                    accX += delta.x
                                    accY += delta.y
                                    // Begin the undo checkpoint lazily: a plain click never coalesces.
                                    if (!began && (mode == DragMode.Move || mode is DragMode.Resize)) {
                                        state.dispatch(DesignEditorIntent.BeginInteraction)
                                        began = true
                                    }
                                    when (mode) {
                                        DragMode.Pan -> state.updateWorkspace {
                                            it.copy(panXDp = it.panXDp + delta.x / density, panYDp = it.panYDp + delta.y / density)
                                        }
                                        DragMode.Marquee -> marquee = Rect(min(start.x, pos.x), min(start.y, pos.y), max(start.x, pos.x), max(start.y, pos.y))
                                        is DragMode.Create -> {
                                            createRect = Rect(min(start.x, pos.x), min(start.y, pos.y), max(start.x, pos.x), max(start.y, pos.y))
                                            badge = "${(abs(pos.x - start.x) / zoomPx).roundToInt()} x ${(abs(pos.y - start.y) / zoomPx).roundToInt()}"
                                        }
                                        DragMode.Move -> {
                                            // Read the live selection: a new node may have been selected on press.
                                            state.dispatch(DesignEditorIntent.MoveNodes(state.designState.selectedNodeIds, (delta.x / zoomPx).toDouble(), (delta.y / zoomPx).toDouble()))
                                            badge = null
                                        }
                                        is DragMode.Resize -> if (resizeBaseline != null) {
                                            applyResize(state, state.designState.selectedNodeId, resizeBaseline, resizeOrigin, mode.handle, accX / zoomPx, accY / zoomPx, state.workspace.lockAspectRatio)
                                            badge = null
                                        }
                                    }
                                    change.consume()
                                }
                            }

                            // Release.
                            when (mode) {
                                DragMode.Marquee -> {
                                    if (moved) {
                                        val rect = marquee
                                        if (rect != null) {
                                            val ids = nodesIn(layout, viewport, rect)
                                            val next = if (shiftHeld) design.selectedNodeIds + ids else ids
                                            state.dispatch(DesignEditorIntent.SelectNodes(next))
                                        }
                                    } else if (hitId.isBlank()) {
                                        state.dispatch(DesignEditorIntent.ClearSelection)
                                    }
                                }
                                is DragMode.Create -> {
                                    commitCreate(state, mode, start, last, zoomPx, viewport, rootNode.id, moved)
                                    state.updateWorkspace { it.copy(tool = EditorTool.Select) }
                                }
                                DragMode.Move -> if (!moved && hitId.isNotBlank()) {
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
                                else -> Unit
                            }
                            if (began) {
                                state.dispatch(DesignEditorIntent.EndInteraction)
                            }
                            marquee = null
                            createRect = null
                            badge = null
                        }
                    }
                    .pointerHoverIcon(cursorFor(ws.tool)),
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
                if (!within || nearest == null) return@awaitEachGesture
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
        state.dispatch(DesignEditorIntent.BeginInteraction)
        state.dispatch(DesignEditorIntent.MoveNodes(selection, dx, dy))
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

private sealed interface DragMode {
    data object Pan : DragMode
    data object Marquee : DragMode
    data object Move : DragMode
    data class Resize(val handle: ResizeHandle) : DragMode
    data class Create(val kind: NewObjectKind) : DragMode
}

private fun resolveDragMode(tool: EditorTool, handle: ResizeHandle?, hitId: String, selection: Set<String>): DragMode = when {
    tool == EditorTool.Hand -> DragMode.Pan
    tool.creates != null -> DragMode.Create(tool.creates!!)
    handle != null -> DragMode.Resize(handle)
    hitId.isNotBlank() -> DragMode.Move
    else -> DragMode.Marquee
}

private enum class ResizeHandle { TopLeft, Top, TopRight, Left, Right, BottomLeft, Bottom, BottomRight }

private const val HandleHitRadiusPx = 11f

private fun handleAt(box: LayoutBox, viewport: CanvasViewport, pos: Offset): ResizeHandle? {
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

/**
 * Applies a handle resize. All math is derived from fixed drag-start references —
 * [baseline] (the box at press) and [originPos] (the authored position at press) — plus
 * the cumulative pointer displacement [docDx]/[docDy], so each frame sets absolute
 * geometry rather than compounding on the already-mutated live node.
 */
private fun applyResize(
    state: MissionEditorStateHolder,
    nodeId: String,
    baseline: LayoutBox,
    originPos: io.aequicor.visualization.engine.ir.model.DesignPoint?,
    handle: ResizeHandle,
    docDx: Float,
    docDy: Float,
    lockRatio: Boolean,
) {
    var w = baseline.width
    var h = baseline.height
    var dxOrigin = 0.0
    var dyOrigin = 0.0
    val left = handle in listOf(ResizeHandle.TopLeft, ResizeHandle.Left, ResizeHandle.BottomLeft)
    val right = handle in listOf(ResizeHandle.TopRight, ResizeHandle.Right, ResizeHandle.BottomRight)
    val top = handle in listOf(ResizeHandle.TopLeft, ResizeHandle.Top, ResizeHandle.TopRight)
    val bottom = handle in listOf(ResizeHandle.BottomLeft, ResizeHandle.Bottom, ResizeHandle.BottomRight)
    if (right) w = baseline.width + docDx
    if (left) { w = baseline.width - docDx; dxOrigin = docDx.toDouble() }
    if (bottom) h = baseline.height + docDy
    if (top) { h = baseline.height - docDy; dyOrigin = docDy.toDouble() }
    w = w.coerceAtLeast(1.0)
    h = h.coerceAtLeast(1.0)
    if (lockRatio && baseline.height > 0.0 && (left || right) && (top || bottom)) {
        val ratio = baseline.width / baseline.height
        h = w / ratio
        if (top) dyOrigin = baseline.height - h
    }
    // Position is parent-relative; the parent doesn't move during a resize, so the
    // change in absolute origin equals the change in authored position.
    if (originPos != null && (dxOrigin != 0.0 || dyOrigin != 0.0)) {
        state.dispatch(DesignEditorIntent.UpdatePosition(nodeId, x = originPos.x + dxOrigin, y = originPos.y + dyOrigin))
    }
    state.dispatch(DesignEditorIntent.UpdateSize(nodeId, width = w, height = h))
}

private fun commitCreate(
    state: MissionEditorStateHolder,
    mode: DragMode.Create,
    start: Offset,
    end: Offset,
    zoomPx: Float,
    viewport: CanvasViewport,
    rootId: String,
    dragged: Boolean,
) {
    val layout = state.artboardLayout
    // Which node contains the creation origin? Fall back to the root frame.
    val parentId = hitNode(layout, viewport, start).ifBlank { rootId }
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

private fun hitNode(layout: LayoutBox?, viewport: CanvasViewport, pos: Offset): String {
    layout ?: return ""
    val docX = viewport.toDocX(pos.x)
    val docY = viewport.toDocY(pos.y)
    val hit = layout.hitTest(docX, docY) ?: return ""
    val id = selectableNodeId(hit)
    // Don't select the root frame on an empty-area press (Figma clears instead).
    return if (id == layout.node.sourceId) "" else id
}

private fun nodesIn(layout: LayoutBox?, viewport: CanvasViewport, screenRect: Rect): Set<String> {
    layout ?: return emptySet()
    val docRect = Rect(
        viewport.toDocX(screenRect.left).toFloat(),
        viewport.toDocY(screenRect.top).toFloat(),
        viewport.toDocX(screenRect.right).toFloat(),
        viewport.toDocY(screenRect.bottom).toFloat(),
    )
    val result = mutableSetOf<String>()
    // Marquee selects the root frame's direct children that intersect the rect.
    layout.children.forEach { child ->
        val r = Rect(child.x.toFloat(), child.y.toFloat(), child.right.toFloat(), child.bottom.toFloat())
        if (r.overlaps(docRect)) result += child.node.selectableId
    }
    return result
}

// --- Viewport helpers --------------------------------------------------------

private data class FitRect(val x: Double, val y: Double, val w: Double, val h: Double)

/** Union of the selected nodes' boxes in document coordinates, or null when empty. */
private fun selectionBounds(layout: LayoutBox, ids: Set<String>): FitRect? {
    val boxes = ids.mapNotNull { layout.findBySourceId(it) }
    if (boxes.isEmpty()) return null
    val minX = boxes.minOf { it.x }
    val minY = boxes.minOf { it.y }
    val maxX = boxes.maxOf { it.right }
    val maxY = boxes.maxOf { it.bottom }
    val pad = 24.0
    return FitRect(minX - pad, minY - pad, (maxX - minX) + 2 * pad, (maxY - minY) + 2 * pad)
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
    state.updateWorkspace { it.copy(zoom = logicalZoom, panXDp = panXpx / density, panYDp = panYpx / density) }
}

private fun zoomAt(state: MissionEditorStateHolder, focus: Offset, wheel: Float, density: Float) {
    val ws = state.workspace
    val factor = if (wheel > 0) 1.1f else 0.9f
    val newZoom = (ws.zoom * factor).coerceIn(WorkspaceLimits.MinZoom, WorkspaceLimits.MaxZoom)
    if (newZoom == ws.zoom) return
    // Keep the document point under the cursor fixed.
    val oldPx = ws.zoom * density
    val newPx = newZoom * density
    val panXpx = ws.panXDp * density
    val panYpx = ws.panYDp * density
    val docX = (focus.x - panXpx) / oldPx
    val docY = (focus.y - panYpx) / oldPx
    val newPanX = focus.x - docX * newPx
    val newPanY = focus.y - docY * newPx
    state.updateWorkspace { it.copy(zoom = newZoom, panXDp = newPanX / density, panYDp = newPanY / density) }
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
                    Text(
                        tool.glyph,
                        color = if (active) Color.White else colors.ink,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }
    }
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
                state.updateWorkspace { it.copy(zoom = (it.zoom * 0.9f).coerceIn(WorkspaceLimits.MinZoom, WorkspaceLimits.MaxZoom)) }
            }
            Text("${(ws.zoom * 100).roundToInt()}%", modifier = Modifier.widthIn(min = 44.dp), style = MaterialTheme.typography.labelMedium, color = colors.ink)
            ZoomButton("+") {
                state.updateWorkspace { it.copy(zoom = (it.zoom * 1.1f).coerceIn(WorkspaceLimits.MinZoom, WorkspaceLimits.MaxZoom)) }
            }
            Spacer(Modifier.width(2.dp))
            ZoomButton("1:1") { state.updateWorkspace { it.copy(pendingFit = PendingFit.None, zoom = 1f) } }
            ZoomButton("[]") { requestFit(state, fitSelection = false) }
            ZoomButton("><") { requestFit(state, fitSelection = true) }
        }
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
