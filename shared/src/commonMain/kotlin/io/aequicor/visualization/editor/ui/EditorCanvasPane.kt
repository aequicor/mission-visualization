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
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
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
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isAltPressed as isPointerAltPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.isTertiaryPressed
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
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
import io.aequicor.visualization.editor.presentation.AncestorRotation
import io.aequicor.visualization.editor.presentation.BoundsBox
import io.aequicor.visualization.editor.presentation.CanvasOperation
import io.aequicor.visualization.editor.presentation.CanvasScrollbarsMetrics
import io.aequicor.visualization.editor.presentation.CanvasScrollbarAxisMetrics
import io.aequicor.visualization.editor.presentation.DesignEditorIntent
import io.aequicor.visualization.editor.presentation.DesignEditorState
import io.aequicor.visualization.editor.presentation.DocumentRect
import io.aequicor.visualization.editor.presentation.DeviceMode
import io.aequicor.visualization.editor.presentation.EditorMode
import io.aequicor.visualization.editor.presentation.EditorTool
import io.aequicor.visualization.editor.presentation.FocusMode
import io.aequicor.visualization.editor.presentation.GapMeasurement
import io.aequicor.visualization.editor.presentation.GeoPoint
import io.aequicor.visualization.editor.presentation.VectorVertexPart
import io.aequicor.visualization.editor.presentation.VectorVertexRef
import io.aequicor.visualization.editor.presentation.NewObjectKind
import io.aequicor.visualization.editor.presentation.PendingFit
import io.aequicor.visualization.editor.presentation.ResizeCursorKind
import io.aequicor.visualization.editor.presentation.SelectableBounds
import io.aequicor.visualization.editor.presentation.ResizableEdges
import io.aequicor.visualization.editor.presentation.ResizeHandle
import io.aequicor.visualization.editor.presentation.WorkspaceLimits
import io.aequicor.visualization.editor.presentation.ZOrderMove
import io.aequicor.visualization.editor.presentation.ancestorRotationDegrees
import io.aequicor.visualization.editor.presentation.angleFromCenterDegrees
import io.aequicor.visualization.editor.presentation.axisAlignedBounds
import io.aequicor.visualization.editor.presentation.effectiveTransform
import io.aequicor.visualization.editor.presentation.EffectiveTransform
import io.aequicor.visualization.editor.presentation.LineSegment
import io.aequicor.visualization.editor.presentation.computeResize
import io.aequicor.visualization.editor.presentation.canvasScrollbarsFor
import io.aequicor.visualization.editor.presentation.toMovingEdges
import io.aequicor.visualization.editor.presentation.toSnapBox
import io.aequicor.visualization.subsystems.anchoring.AnchorGuide
import io.aequicor.visualization.subsystems.anchoring.CenterAnchorLines
import io.aequicor.visualization.subsystems.anchoring.KeyRotationAngles
import io.aequicor.visualization.subsystems.anchoring.MoveSnapState
import io.aequicor.visualization.subsystems.anchoring.ResizeSnapOutput
import io.aequicor.visualization.subsystems.anchoring.ResizeSnapState
import io.aequicor.visualization.subsystems.anchoring.RotateSnapState
import io.aequicor.visualization.subsystems.anchoring.SnapBox
import io.aequicor.visualization.subsystems.anchoring.SpacingBar
import io.aequicor.visualization.subsystems.anchoring.centerAnchorLines
import io.aequicor.visualization.subsystems.anchoring.neighborRotationCandidates
import io.aequicor.visualization.subsystems.anchoring.solveMoveSnap
import io.aequicor.visualization.subsystems.anchoring.solveResizeSnap
import io.aequicor.visualization.subsystems.anchoring.solveRotateSnap
import io.aequicor.visualization.subsystems.anchoring.compose.GuideStyle
import io.aequicor.visualization.subsystems.anchoring.compose.drawAnchorGuide
import io.aequicor.visualization.subsystems.anchoring.compose.drawCenterAnchorLines
import io.aequicor.visualization.subsystems.anchoring.compose.drawSpacingBar
import io.aequicor.visualization.editor.presentation.translate
import io.aequicor.visualization.editor.presentation.flowInsertionIndex
import io.aequicor.visualization.editor.presentation.flowInsertionLine
import io.aequicor.visualization.editor.presentation.isCoordinatePositioned
import io.aequicor.visualization.editor.presentation.resizableEdges
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
import io.aequicor.visualization.editor.platform.CanvasExportBounds
import io.aequicor.visualization.editor.ui.theme.LocalEditorColors
import io.aequicor.visualization.engine.backend.compose.CanvasViewport
import io.aequicor.visualization.engine.backend.compose.DesignArtboard
import io.aequicor.visualization.engine.backend.compose.selectableNodeId
import io.aequicor.visualization.subsystems.figures.Affine2D
import io.aequicor.visualization.subsystems.figures.compose.FigurePreviewStyle
import io.aequicor.visualization.subsystems.figures.compose.FigureShapePreview
import io.aequicor.visualization.subsystems.figures.HandleSide
import io.aequicor.visualization.subsystems.figures.closePath
import io.aequicor.visualization.subsystems.figures.contains
import io.aequicor.visualization.subsystems.figures.networkRegionGeometry
import io.aequicor.visualization.subsystems.figures.vectorAnchors
import io.aequicor.visualization.subsystems.figures.RectD
import io.aequicor.visualization.subsystems.figures.meetFit
import io.aequicor.visualization.engine.ir.layout.LayoutBox
import io.aequicor.visualization.engine.ir.model.DesignDocument
import io.aequicor.visualization.engine.ir.model.DesignPaint
import io.aequicor.visualization.engine.ir.model.DesignPoint
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.engine.ir.model.bindable
import io.aequicor.visualization.engine.ir.model.LayoutMode
import io.aequicor.visualization.subsystems.figures.ShapeType
import io.aequicor.visualization.subsystems.figures.VectorNetwork
import io.aequicor.visualization.subsystems.figures.VectorVertex
import io.aequicor.visualization.engine.ir.model.literalOrNull
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.round
import kotlin.math.sin
import kotlinx.coroutines.channels.Channel

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
                FloatingToolbar(ws.tool, ws.lastShapeTool) { tool ->
                    state.updateWorkspace {
                        it.copy(tool = tool, lastShapeTool = if (tool.isShapeTool) tool else it.lastShapeTool)
                    }
                }
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

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .padding(4.dp)
            .onGloballyPositioned { coordinates ->
                val bounds = coordinates.boundsInWindow()
                state.onCanvasExportBounds(
                    CanvasExportBounds(
                        left = bounds.left.toDouble(),
                        top = bounds.top.toDouble(),
                        width = bounds.width.toDouble(),
                        height = bounds.height.toDouble(),
                        density = density,
                    ),
                )
            },
    ) {
        val canvasWpx = maxWidth.value * density
        val canvasHpx = maxHeight.value * density
        val layout = state.artboardLayout

        // Fit the current screen once per page (keeps the user's later zoom/pan).
        var fittedPage by remember { mutableStateOf("") }
        LaunchedEffect(layout, canvasWpx, canvasHpx, pageId, density) {
            val currentLayout = layout ?: return@LaunchedEffect
            if (canvasWpx <= 0f || canvasHpx <= 0f || fittedPage == pageId) return@LaunchedEffect
            fittedPage = pageId
            fitViewport(
                state,
                currentLayout.x,
                currentLayout.y,
                currentLayout.width,
                currentLayout.height,
                canvasWpx,
                canvasHpx,
                density,
            )
        }
        // Honor an explicit fit-screen / fit-selection request from the zoom controls.
        LaunchedEffect(layout, canvasWpx, canvasHpx, ws.pendingFit, design.selectedNodeIds, density) {
            val currentLayout = layout ?: return@LaunchedEffect
            val pendingFit = ws.pendingFit
            if (canvasWpx <= 0f || canvasHpx <= 0f || pendingFit == PendingFit.None) return@LaunchedEffect
            val rect = when (pendingFit) {
                PendingFit.Selection -> selectionBounds(currentLayout, design.selectedNodeIds)
                    ?: FitRect(currentLayout.x, currentLayout.y, currentLayout.width, currentLayout.height)
                else -> FitRect(currentLayout.x, currentLayout.y, currentLayout.width, currentLayout.height)
            }
            fitViewport(state, rect.x, rect.y, rect.w, rect.h, canvasWpx, canvasHpx, density, consumePendingFit = true)
        }

        // Smoothly ease +/-/1:1 button zoom around the canvas center (see [animateZoomTo]).
        LaunchedEffect(ws.pendingZoomTo, canvasWpx, canvasHpx, density) {
            val target = ws.pendingZoomTo ?: return@LaunchedEffect
            if (canvasWpx <= 0f || canvasHpx <= 0f) return@LaunchedEffect
            animateZoomTo(state, target, canvasWpx / 2f, canvasHpx / 2f, density)
            state.updateWorkspace { if (it.pendingZoomTo == target) it.copy(pendingZoomTo = null) else it }
        }

        // Smoothly ease ctrl/meta-wheel zoom toward a target, keeping the cursor anchored,
        // instead of snapping the zoom on each discrete wheel notch. Two subtle bugs sank the
        // earlier attempts; both are designed out here rather than patched:
        //
        //  1. Lost wake-up at the zoom clamp. Gating the ease loop on a `wheelZoomActive`
        //     boolean that the loop itself flips false on convergence races the scroll handler:
        //     a notch that reads a stale `active == true` skips re-arming, then the loop commits
        //     `active = false` — leaving a live target with nothing chasing it. At the min-zoom
        //     clamp this fired almost every notch (each converges in one frame), so zoom got
        //     stuck at 5% and refused to zoom back in. Fixed by waking the loop through a
        //     CONFLATED channel: a pulse queued during convergence is still pending when the
        //     loop loops back to receive(), so a wake-up can never be dropped.
        //  2. Reversal trap at the clamp. Accumulating the target off the *pending* target
        //     (`wheelZoomTarget ?: current`) pins it to the clamp; a reversal then crawls up
        //     from 5% while the visible zoom is elsewhere. Fixed by anchoring every notch to the
        //     live *visible* zoom (see the scroll handler), so a reversal re-aims from what the
        //     user actually sees.
        //
        // Convergence tolerance is relative (WheelZoomConvergeFraction) so it behaves the same
        // at 0.05 as at 16; the ease uses frame-rate-independent exponential decay.
        var wheelZoomTarget by remember { mutableStateOf<Float?>(null) }
        var wheelZoomFocus by remember { mutableStateOf(Offset.Zero) }
        val wheelZoomPulse = remember { Channel<Unit>(Channel.CONFLATED) }
        DisposableEffect(wheelZoomPulse) {
            onDispose { wheelZoomPulse.close() }
        }
        LaunchedEffect(density) {
            while (true) {
                wheelZoomPulse.receive()
                var lastFrameNanos = -1L
                while (true) {
                    val target = wheelZoomTarget ?: break
                    val frameNanos = withFrameNanos { it }
                    val dtSeconds = if (lastFrameNanos < 0) 0f else (frameNanos - lastFrameNanos) / 1_000_000_000f
                    lastFrameNanos = frameNanos
                    val current = state.workspace.viewport.zoom
                    val diff = target - current
                    if (abs(diff) <= max(target, current) * WheelZoomConvergeFraction) {
                        if (current > 0f && current != target) {
                            state.updateWorkspace {
                                it.copy(viewport = it.viewport.zoomAround(wheelZoomFocus.x, wheelZoomFocus.y, target / current, density))
                            }
                        }
                        wheelZoomTarget = null
                        break
                    }
                    val ease = 1f - exp(-WheelZoomFollowRate * dtSeconds)
                    val next = current + diff * ease
                    if (current > 0f && next > 0f) {
                        state.updateWorkspace {
                            it.copy(viewport = it.viewport.zoomAround(wheelZoomFocus.x, wheelZoomFocus.y, next / current, density))
                        }
                    }
                }
            }
        }

        val viewportModel = ws.viewport
        // Backed by rememberUpdatedState (not a plain val) so the two big pointerInput
        // gesture blocks below can drop `viewport` from their restart keys: those blocks
        // read `zoomPx`/`viewport` directly at each point of use (never snapshot them into a
        // gesture-local val), so a live-updating delegate keeps every read fresh without ever
        // needing to cancel and relaunch the coroutine. Keeping viewport as a restart key was
        // fine while it only changed once per discrete input event, but the eased wheel-zoom
        // follow loop now mutates it every animation frame — as a key that would restart (and
        // thus drop pending events from) the whole gesture handler for the entire zoom.
        val zoomPx by rememberUpdatedState(viewportModel.zoomPx(density))
        val viewport by rememberUpdatedState(CanvasViewport(zoomPx, viewportModel.panXPx(density), viewportModel.panYPx(density)))
        val hoveredNodeId = state.hoveredNodeId
        val scrollbars = layout?.canvasContentBounds()?.let { contentBounds ->
            canvasScrollbarsFor(
                viewport = viewportModel,
                contentBounds = contentBounds,
                viewportWidthPx = canvasWpx,
                viewportHeightPx = canvasHpx,
                density = density,
                scrollbarThicknessPx = CanvasScrollbarThicknessDp * density,
                minThumbLengthPx = CanvasScrollbarMinThumbDp * density,
            )
        } ?: CanvasScrollbarsMetrics()
        val multiSelectionBox = if (design.selectedNodeIds.size > 1) selectionHandleBounds(layout, design.selectedNodeIds) else null

        // Primary (single) selection geometry — the only case that gets rotated handles,
        // a rotate affordance and center lines; multi-selection keeps its plain bbox. Every
        // box/rotation here is the node's *effective* on-screen transform, so the overlay
        // follows a rotated ancestor (e.g. the root frame) exactly as the renderer does.
        val primarySelectionId = design.selectedNodeId.takeIf { design.selectedNodeIds.size == 1 && it.isNotBlank() }
        val primarySelectionTransform = primarySelectionId?.let { layout?.effectiveTransformFor(it) }
        val primarySelectionBox = primarySelectionTransform?.box
        val primarySelectionRotation = primarySelectionTransform?.rotation ?: 0.0
        val primarySelectionLocked = primarySelectionId?.let { document?.nodeById(it)?.locked == true } ?: false
        val primarySelectionInVectorEdit = primarySelectionId != null && primarySelectionId == ws.vectorEditNodeId
        val parentOfPrimarySelection = primarySelectionId
            ?.let { document?.parentNodeOf(it)?.id }
            ?.let { layout?.effectiveTransformFor(it) }
            ?.let { it.box.visualBounds(it.rotation) }

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
        // True while a resize drag snaps the box's width/height to equal a neighbour's — tints the size badge.
        var resizeMatched by remember { mutableStateOf(false) }
        // Live rotation-snap feedback (angle + whether magnetically caught); null outside a rotate drag.
        var rotateIndicator by remember { mutableStateOf<RotateIndicator?>(null) }
        var rotateDragActive by remember { mutableStateOf(false) }

        // Keyboard focus + modifier tracking (Shift for additive select / big nudge, Space
        // for pan, Alt for the read-only measurement overlay).
        val focusRequester = remember { FocusRequester() }
        var shiftHeld by remember { mutableStateOf(false) }
        var altHeld by remember { mutableStateOf(false) }
        var spaceHeld by remember { mutableStateOf(false) }
        val latestSpaceHeld by rememberUpdatedState(spaceHeld)
        val windowFocused = LocalWindowInfo.current.isWindowFocused
        var lastTapMark by remember { mutableStateOf<TimeSource.Monotonic.ValueTimeMark?>(null) }
        var lastTapId by remember { mutableStateOf("") }
        fun resetHeldModifiers() {
            shiftHeld = false
            altHeld = false
            spaceHeld = false
        }
        LaunchedEffect(pageId) { runCatching { focusRequester.requestFocus() } }
        LaunchedEffect(windowFocused) {
            if (!windowFocused) resetHeldModifiers()
        }
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
                    // Hover + scroll (pan / ctrl-zoom) + per-position resize cursor. `viewport`
                    // is deliberately not a key here — see the rememberUpdatedState comment
                    // above; keying on it would restart this block (dropping scroll events)
                    // on every frame of an in-flight wheel-zoom animation.
                    .pointerInput(pageId, ws.tool) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                val modifiers = event.keyboardModifiers
                                shiftHeld = modifiers.isShiftPressed
                                altHeld = modifiers.isPointerAltPressed
                                when (event.type) {
                                    PointerEventType.Scroll -> {
                                        val change = event.changes.firstOrNull() ?: continue
                                        // A wheel/trackpad interaction supersedes any in-flight button-zoom animation.
                                        if (state.workspace.pendingZoomTo != null) {
                                            state.updateWorkspace { it.copy(pendingZoomTo = null) }
                                        }
                                        if (modifiers.isCtrlPressed || modifiers.isMetaPressed) {
                                            val factor = zoomFactorForScroll(-change.scrollDelta.y)
                                            if (factor != 1f) {
                                                // Anchor to the live *visible* zoom, not the pending
                                                // target: a direction reversal (notably at the min/max
                                                // clamp) then re-aims from what the user sees rather than
                                                // crawling out of the clamp. The ease loop keeps up while
                                                // scrolling, so continuous notches still accumulate.
                                                val base = state.workspace.viewport.zoom
                                                wheelZoomTarget = (base * factor)
                                                    .coerceIn(WorkspaceLimits.MinZoom, WorkspaceLimits.MaxZoom)
                                                wheelZoomFocus = change.position
                                                wheelZoomPulse.trySend(Unit)
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
                                            // Compare against live values: `design`/`ws` are frozen at
                                            // pointerInput setup, so this must re-read current state.
                                            val liveDesign = state.designState
                                            val liveLayout = state.artboardLayout
                                            val hit = hitNode(liveLayout, liveDesign.document, viewport, change.position)
                                            state.updateHoveredNode(hit)
                                            hoverCursor = resolveHandleCursor(
                                                liveDesign, liveLayout, viewport, change.position, ws.tool, latestSpaceHeld,
                                                vectorEditNodeId = state.workspace.vectorEditNodeId,
                                            )
                                        }
                                    }
                                    PointerEventType.Exit -> {
                                        state.updateHoveredNode("")
                                        hoverCursor = null
                                    }
                                    else -> Unit
                                }
                            }
                        }
                    }
                    // Press / drag / tap. `viewport` is intentionally not a key — see the
                    // rememberUpdatedState comment above; an in-flight zoom animation would
                    // otherwise cancel any drag/resize/rotate gesture in progress every frame.
                    .pointerInput(pageId, ws.tool, design.selectedNodeId, design.selectedNodeIds, spaceHeld) {
                        val slop = viewConfiguration.touchSlop
                        awaitEachGesture {
                            val gestureStart = awaitCanvasGestureStart()
                            val down = gestureStart.change
                            shiftHeld = gestureStart.shiftPressed
                            altHeld = gestureStart.altPressed
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
                            val panFromTertiaryButton = gestureStart.tertiaryButton || currentEvent.buttons.isTertiaryPressed
                            val forcePan = panFromTertiaryButton || spaceHeld
                            if (forcePan) down.consume()
                            // Root→selection path (single selection) resolves the node's own layout box,
                            // its effective on-screen transform (with rotated ancestors) and the net
                            // ancestor rotation, all from one tree walk.
                            val primaryPath = pressDesign.selectedNodeId.takeIf { it.isNotBlank() }
                                ?.let { pressLayout?.pathToSourceId(it) }
                            val primaryBox = primaryPath?.last()
                            val primaryTransform = primaryPath?.let {
                                effectiveTransform(it.last().toBoundsBox(), it.last().node.rotation, ancestorRotationsOf(it))
                            }
                            // A locked selection exposes no resize handles (design-book §7).
                            val selectionLocked = pressDesign.selectedNodeIds.any { id -> pressDocument.nodeById(id)?.locked == true }
                            val isSingleSelection = pressDesign.selectedNodeIds.size == 1
                            val inVectorEdit = isSingleSelection && pressDesign.selectedNodeId == pressWorkspace.vectorEditNodeId
                            val multiSelectionUnion = if (pressDesign.selectedNodeIds.size > 1) {
                                selectionHandleBounds(pressLayout, pressDesign.selectedNodeIds)
                            } else {
                                null
                            }
                            // Resize baseline is the node's *own* root-local box (size + position live
                            // in the shared root-local frame); the multi-selection group uses its union.
                            val selectedResizeBox = multiSelectionUnion ?: primaryBox?.toBoundsBox()
                            // Own rotation drives the rotate-gesture baseline and the resize position
                            // shift (root-local); the ancestor rotation is undone separately for moves.
                            val selectionRotation = if (isSingleSelection) primaryBox?.node?.rotation ?: 0.0 else 0.0
                            val ancestorRotation = if (isSingleSelection) primaryPath?.let { ancestorRotationDegrees(ancestorRotationsOf(it)) } ?: 0.0 else 0.0
                            // Handle geometry (where handles/affordance are drawn and grabbed) uses the
                            // *effective* box + rotation, so a rotated ancestor is followed on screen.
                            val handleGeometryBox = multiSelectionUnion ?: primaryTransform?.box
                            val handleRotation = if (isSingleSelection) primaryTransform?.rotation ?: 0.0 else 0.0
                            val handlesActive = !forcePan && pressWorkspace.tool == EditorTool.Select && !selectionLocked && !inVectorEdit
                            val handle = handleGeometryBox?.takeIf { handlesActive }
                                ?.let { box -> rotatedHandleAt(box, handleRotation, viewport, start) }
                            val rotateHit = handle == null && isSingleSelection &&
                                handleGeometryBox?.takeIf { handlesActive }?.let { box ->
                                    val offsetDoc = (RotateHandleScreenOffsetPx / zoomPx).toDouble()
                                    val point = rotateAffordancePoint(box, handleRotation, offsetDoc)
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
                            // is rotated — by its own rotation or an inherited ancestor (e.g. the root)
                            // rotation — since snapping assumes an axis-aligned container coordinate space.
                            val snapBaseline = if (mode == CanvasOperation.Move && reorderBaseline == null) {
                                val ids = state.designState.selectedNodeIds
                                val boxes = ids.mapNotNull { id -> pressLayout?.findBySourceId(id) }
                                val parentBox = pressDocument.parentNodeOf(state.designState.selectedNodeId)?.id
                                    ?.let { pressLayout?.findBySourceId(it) }
                                // Net rotation of the parent's coordinate frame relative to the document
                                // (its own rotation plus every ancestor up to the root).
                                val parentFrameRotation = parentBox
                                    ?.let { pressLayout?.pathToSourceId(it.node.sourceId)?.sumOf { box -> box.node.rotation } }
                                    ?: 0.0
                                if (boxes.isEmpty() || parentBox == null || parentFrameRotation != 0.0) {
                                    null
                                } else {
                                    val corners = boxes.flatMap { box ->
                                        val vb = box.toBoundsBox().visualBounds(box.node.rotation)
                                        listOf(GeoPoint(vb.x, vb.y), GeoPoint(vb.right, vb.bottom))
                                    }
                                    val context = collectSnapContext(pressDocument, pressLayout, parentBox, ids)
                                    SnapBaseline(axisAlignedBounds(corners), context.containers, context.siblings)
                                }
                            } else {
                                null
                            }
                            // Resize snap context (unrotated single selection only): the box's siblings +
                            // its unrotated ancestor containers, to align the dragged edge / match sizes.
                            val resizeSnapBaseline: SnapContext? = (mode as? CanvasOperation.Resize)?.let {
                                val id = pressDesign.selectedNodeId.takeIf { sid -> sid.isNotBlank() } ?: return@let null
                                if (!isSingleSelection || selectionLocked || selectionRotation != 0.0) return@let null
                                val parentId = pressDocument.parentNodeOf(id)?.id ?: return@let null
                                val parentBox = pressLayout?.findBySourceId(parentId) ?: return@let null
                                // Bail under any inherited rotation (parent's own + every ancestor) so the
                                // snapped resize runs in an axis-aligned frame — this keeps handleRotation == 0
                                // in the apply/commit path, where snapped deltas assume the document frame.
                                val parentFrameRotation = pressLayout?.pathToSourceId(parentBox.node.sourceId)?.sumOf { it.node.rotation } ?: 0.0
                                if (parentFrameRotation != 0.0) return@let null
                                collectSnapContext(pressDocument, pressLayout, parentBox, setOf(id))
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
                            val rotateBaseline = if (mode == CanvasOperation.Rotate && handleGeometryBox != null && pressDesign.selectedNodeId.isNotBlank()) {
                                // Pivot about the *visual* center (effective, so it sits under a rotated
                                // ancestor correctly); the gesture still edits only the node's own
                                // rotation, so the baseline keeps the own rotation.
                                val center = GeoPoint(handleGeometryBox.centerX, handleGeometryBox.centerY)
                                // Sibling rotations feed the rotation magnet so the box can line up with a neighbour.
                                val neighborRotations = pressDocument.parentNodeOf(pressDesign.selectedNodeId)?.id
                                    ?.let { pressLayout?.findBySourceId(it) }
                                    ?.children
                                    ?.filter { it.node.sourceId != pressDesign.selectedNodeId }
                                    ?.map { it.node.rotation }
                                    .orEmpty()
                                RotateBaseline(
                                    nodeId = pressDesign.selectedNodeId,
                                    center = center,
                                    startAngle = angleFromCenterDegrees(center, GeoPoint(viewport.toDocX(start.x), viewport.toDocY(start.y))),
                                    startRotation = selectionRotation,
                                    neighborAngles = neighborRotationCandidates(neighborRotations),
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
                            // Sticky-snap latches carried across this gesture's frames (hysteresis).
                            var moveSnapState = MoveSnapState.None
                            var resizeSnapState = ResizeSnapState.None
                            var rotateSnapState = RotateSnapState.None

                            while (true) {
                                val event = awaitPointerEvent()
                                val modifiers = event.keyboardModifiers
                                shiftHeld = modifiers.isShiftPressed
                                altHeld = modifiers.isPointerAltPressed
                                val change = event.changes.firstOrNull() ?: break
                                if (mode == CanvasOperation.Pan && panFromTertiaryButton && !event.buttons.isTertiaryPressed) break
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
                                    is CanvasOperation.Create -> if (mode.kind == NewObjectKind.Vector) {
                                        // Pen click-to-place draws no rubber-band rect; it seeds a
                                        // single-vertex path on release (see commitPenStart).
                                        badge = null
                                    } else {
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
                                        val snap = snapBaseline?.let { base ->
                                            // Feed the RAW pointer-driven box (never a snapped one) so the sticky
                                            // release distance is measured against the pointer — else an axis
                                            // would keep measuring against itself and latch forever.
                                            solveMoveSnap(
                                                base.startUnionBounds.translate(dragDx, dragDy).toSnapBox(),
                                                base.containers.map { it.toSnapBox() },
                                                base.siblings.map { it.toSnapBox() },
                                                catch = (SnapCatchPx / zoomPx).toDouble(),
                                                release = (SnapReleasePx / zoomPx).toDouble(),
                                                allowX = !lockX,
                                                allowY = !lockY,
                                                prior = moveSnapState,
                                            )
                                        }
                                        moveSnapState = snap?.state ?: MoveSnapState.None
                                        val totalDx = dragDx + (snap?.dx ?: 0.0)
                                        val totalDy = dragDy + (snap?.dy ?: 0.0)
                                        // The drag is measured in the document (screen) frame, but a node's
                                        // position lives in the shared root-local frame; under a rotated
                                        // ancestor undo that rotation so the node tracks the pointer. Snapping
                                        // is gated to ancestorRotation == 0, so when it runs this is identity.
                                        val local = if (ancestorRotation != 0.0) rotateVector(totalDx, totalDy, -ancestorRotation) else GeoPoint(totalDx, totalDy)
                                        // Read the live selection: a new node may have been selected on press.
                                        state.dispatch(DesignEditorIntent.MoveNodes(state.designState.selectedNodeIds, local.x - appliedDx, local.y - appliedDy))
                                        appliedDx = local.x
                                        appliedDy = local.y
                                        snapGuides = snap?.guides ?: emptyList()
                                        spacingBars = snap?.spacing ?: emptyList()
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
                                                val lockRatio = state.workspace.lockAspectRatio || shiftHeld
                                                val snap = snapResizeDeltas(
                                                    resizeBaseline, mode.handle, accX / zoomPx, accY / zoomPx, lockRatio,
                                                    resizeSnapBaseline, (SnapCatchPx / zoomPx).toDouble(), (SnapReleasePx / zoomPx).toDouble(), resizeSnapState,
                                                )
                                                resizeSnapState = snap.output?.state ?: ResizeSnapState.None
                                                applyResize(
                                                    state, target, resizeBaseline, mode.handle,
                                                    snap.docDx, snap.docDy, lockRatio,
                                                    // De-rotate the drag by the *effective* handle rotation the
                                                    // on-screen handle was grabbed at (own + inherited ancestor
                                                    // rotation; 0 for a multi-selection's axis-aligned group box).
                                                    // Resize snap is gated to ancestorRotation == 0, so a snapped
                                                    // resize only ever runs with handleRotation == selectionRotation.
                                                    rotationDegrees = handleRotation,
                                                    // ...and re-express the position shift with only the node's
                                                    // own rotation (position lives in the shared root-local frame).
                                                    positionDegrees = selectionRotation,
                                                )
                                                snapGuides = snap.output?.guides ?: emptyList()
                                                resizeMatched = snap.output?.match?.let { it.widthMatched || it.heightMatched } == true
                                            }
                                        }
                                        badge = null
                                    }
                                    CanvasOperation.Rotate -> if (rotateBaseline != null) {
                                        val pointerDoc = GeoPoint(viewport.toDocX(pos.x), viewport.toDocY(pos.y))
                                        val currentAngle = angleFromCenterDegrees(rotateBaseline.center, pointerDoc)
                                        val freeAngle = normalizeAngleDegrees(rotateBaseline.startRotation + (currentAngle - rotateBaseline.startAngle))
                                        val nextRotation = if (shiftHeld) {
                                            // Shift = the classic hard 15° step (magnet off, latch cleared).
                                            rotateSnapState = RotateSnapState.None
                                            normalizeAngleDegrees(snapAngleToIncrement(freeAngle, 15.0)).also {
                                                rotateIndicator = RotateIndicator(it, caught = false)
                                            }
                                        } else {
                                            val snap = solveRotateSnap(
                                                freeAngle, KeyRotationAngles, rotateBaseline.neighborAngles,
                                                RotateCatchDeg, RotateReleaseDeg, rotateSnapState,
                                            )
                                            rotateSnapState = snap.state
                                            rotateIndicator = RotateIndicator(snap.angle, snap.caught)
                                            snap.angle
                                        }
                                        state.dispatch(DesignEditorIntent.SetRotation(rotateBaseline.nodeId, nextRotation))
                                        rotateDragActive = true
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
                                    is CanvasOperation.Create -> if (mode.kind == NewObjectKind.Vector) {
                                        // Pen: first click seeds a single-vertex vector node and enters
                                        // vector-edit mode WITHOUT resetting the tool, so the next click
                                        // appends the second vertex (VectorEditOverlay owns it thereafter).
                                        commitPenStart(state, start, viewport, pressRootId)
                                    } else {
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
                                                val lockRatio = state.workspace.lockAspectRatio || shiftHeld
                                                // Re-derive the snapped deltas (prior = the final latch) so the
                                                // magnetic edge/size persists to the SLM source, not the raw drag.
                                                val snap = snapResizeDeltas(
                                                    resizeBaseline, mode.handle, accX / zoomPx, accY / zoomPx, lockRatio,
                                                    resizeSnapBaseline, (SnapCatchPx / zoomPx).toDouble(), (SnapReleasePx / zoomPx).toDouble(), resizeSnapState,
                                                )
                                                commitResizeWriteBack(
                                                    state = state,
                                                    target = target,
                                                    baseline = resizeBaseline,
                                                    handle = mode.handle,
                                                    docDx = snap.docDx,
                                                    docDy = snap.docDy,
                                                    lockRatio = lockRatio,
                                                    // De-rotate the drag by the effective handle rotation the
                                                    // on-screen handle was grabbed at (own + inherited ancestor
                                                    // rotation; 0 for a multi-selection's axis-aligned group box).
                                                    rotationDegrees = handleRotation,
                                                    // ...and re-express the position shift with only the node's
                                                    // own rotation (position is stored in the root-local frame).
                                                    positionDegrees = selectionRotation,
                                                )
                                            }
                                        }
                                    }
                                    CanvasOperation.Rotate -> if (moved && rotateBaseline != null) {
                                        // Persist the final (possibly magnet-snapped) angle to the SLM source
                                        // — SetRotation only mutated the working document per frame.
                                        val finalAngle = state.designState.document?.nodeById(rotateBaseline.nodeId)?.rotation
                                            ?: rotateBaseline.startRotation
                                        state.dispatch(DesignEditorIntent.RotateNode(rotateBaseline.nodeId, finalAngle))
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
                            resizeMatched = false
                            rotateIndicator = null
                            rotateDragActive = false
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
                    vectorAssets = rememberVectorAssetProvider(document),
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
            // Anchoring overlays paint through the :subsystems:anchoring-compose renderer, fed a
            // doc→screen projection and the theme colors so that module stays free of viewport/theme.
            val project: (Double, Double) -> Offset = { docX, docY -> viewport.toScreen(docX, docY) }
            val guideStyle = colors.guideStyle()
            if (hoveredNodeId.isNotBlank() && hoveredNodeId !in design.selectedNodeIds && !altHeld) {
                layout?.effectiveTransformFor(hoveredNodeId)?.let { t ->
                    drawRotatedOutline(t.box, t.rotation, viewport, colors.accent.copy(alpha = 0.85f), width = 1.5f)
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
            snapGuides.forEach { guide -> drawAnchorGuide(guide, project, guideStyle, textMeasurer) }
            spacingBars.forEach { bar -> drawSpacingBar(bar, project, guideStyle, textMeasurer) }

            if (multiSelectionBox != null && ws.vectorEditNodeId.isBlank()) {
                drawRotatedOutline(multiSelectionBox, 0.0, viewport, colors.accent, width = 1.5f)
                drawRotatedHandles(multiSelectionBox, 0.0, viewport, colors.accent)
                drawSizeBadge(multiSelectionBox, 0.0, viewport, textMeasurer, colors)
            }

            if (primarySelectionBox != null) {
                // Center anchor lines are the baseline crosshair, so they stay dashed during drag.
                // Actual magnetic catches are drawn separately by snapGuides; if a guide coincides
                // with the center line, that guide provides the solid visual.
                if (parentOfPrimarySelection != null) {
                    val lines = centerAnchorLines(primarySelectionBox.toSnapBox(), parentOfPrimarySelection.toSnapBox())
                    drawCenterAnchorLines(
                        lines,
                        verticalSolid = false,
                        horizontalSolid = false,
                        project = project,
                        style = guideStyle,
                    )
                }

                drawRotatedOutline(primarySelectionBox, primarySelectionRotation, viewport, colors.accent, width = 1.5f)
                // Point-edit mode replaces object handles with path anchors (VectorEditOverlay),
                // so suppress handles and the rotate affordance but keep the rest of the overlay.
                if (!primarySelectionLocked && !primarySelectionInVectorEdit) {
                    drawRotatedHandles(primarySelectionBox, primarySelectionRotation, viewport, colors.accent)
                    if (!dragMoveActive && !rotateDragActive) {
                        val offsetDoc = (RotateHandleScreenOffsetPx / zoomPx).toDouble()
                        drawRotateAffordance(primarySelectionBox, primarySelectionRotation, offsetDoc, viewport, colors.accent)
                    }
                }
                // Green badge while a resize snap matches a neighbour's width/height (design-book §18).
                drawSizeBadge(
                    primarySelectionBox, primarySelectionRotation, viewport, textMeasurer, colors,
                    background = if (resizeMatched) colors.statusPositive else colors.accent,
                )
                // Rotation magnet feedback: the live angle, green when it magnetically caught (design-book §18).
                rotateIndicator?.let { ind ->
                    val offsetDoc = (RotateHandleScreenOffsetPx / zoomPx).toDouble()
                    val at = rotateAffordancePoint(primarySelectionBox, primarySelectionRotation, offsetDoc)
                    val bg = if (ind.caught) colors.statusPositive else colors.accent
                    drawFilledBadge("${ind.angle.roundToInt()}°", viewport.toScreen(at.x, at.y), bg, textMeasurer)
                }
            }

            if (altHeld && primarySelectionBox != null) {
                val altTargetBox = altMeasurementTarget(design, hoveredNodeId, layout, parentOfPrimarySelection, primarySelectionId)
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

            // On-canvas arc handles for a single, unlocked ellipse authored with an arc/donut, while
            // the Select tool is active and it is not in point-edit mode.
            val arcNode = primarySelectionId
                ?.takeIf { ws.tool == EditorTool.Select && !primarySelectionLocked && !primarySelectionInVectorEdit }
                ?.let { document?.nodeById(it) }
            val arcShape = (arcNode?.kind as? DesignNodeKind.Shape)?.takeIf { hasEllipseArc(it) }
            if (primarySelectionId != null && primarySelectionBox != null && arcShape != null) {
                ArcHandlesOverlay(state, primarySelectionId, primarySelectionBox, primarySelectionRotation, viewport)
            }

            // Inline text editing overlay for a double-clicked text node.
            TextEditOverlay(state, layout, viewport)
        }
        CanvasScrollbars(state, scrollbars)
    }
}

@Composable
private fun BoxScope.CanvasScrollbars(state: MissionEditorStateHolder, metrics: CanvasScrollbarsMetrics) {
    if (!metrics.horizontal.visible && !metrics.vertical.visible) return
    val colors = LocalEditorColors.current
    val density = LocalDensity.current.density
    val trackColor = Color.Transparent
    val thumbColor = colors.ink.copy(alpha = 0.30f)

    if (metrics.horizontal.visible) {
        CanvasScrollbar(
            metrics = metrics.horizontal,
            horizontal = true,
            trackColor = trackColor,
            thumbColor = thumbColor,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(CanvasScrollbarThicknessDp.dp)
                .padding(end = if (metrics.vertical.visible) CanvasScrollbarThicknessDp.dp else 0.dp),
            onThumbOffset = { thumbOffset ->
                val documentX = metrics.horizontal.documentStartForThumbOffset(thumbOffset)
                state.updateWorkspace { it.copy(viewport = it.viewport.panToDocumentStartX(documentX)) }
            },
            density = density,
        )
    }
    if (metrics.vertical.visible) {
        CanvasScrollbar(
            metrics = metrics.vertical,
            horizontal = false,
            trackColor = trackColor,
            thumbColor = thumbColor,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(CanvasScrollbarThicknessDp.dp)
                .padding(bottom = if (metrics.horizontal.visible) CanvasScrollbarThicknessDp.dp else 0.dp),
            onThumbOffset = { thumbOffset ->
                val documentY = metrics.vertical.documentStartForThumbOffset(thumbOffset)
                state.updateWorkspace { it.copy(viewport = it.viewport.panToDocumentStartY(documentY)) }
            },
            density = density,
        )
    }
    if (metrics.horizontal.visible && metrics.vertical.visible) {
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .size(CanvasScrollbarThicknessDp.dp)
                .background(trackColor),
        )
    }
}

@Composable
private fun CanvasScrollbar(
    metrics: CanvasScrollbarAxisMetrics,
    horizontal: Boolean,
    trackColor: Color,
    thumbColor: Color,
    modifier: Modifier,
    onThumbOffset: (Float) -> Unit,
    density: Float,
) {
    val latestMetrics by rememberUpdatedState(metrics)
    val latestOnThumbOffset by rememberUpdatedState(onThumbOffset)
    var dragThumbOffset by remember { mutableStateOf(0f) }
    var dragGrabOffset by remember { mutableStateOf(0f) }
    Box(
        modifier
            .background(trackColor)
            .pointerHoverIcon(PointerIcon.Hand)
            .pointerInput(horizontal) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    run {
                        val current = latestMetrics
                        val pointerOffset = if (horizontal) down.position.x else down.position.y
                        val thumbStart = current.thumbOffsetPx
                        val thumbEnd = thumbStart + current.thumbLengthPx
                        dragGrabOffset = if (pointerOffset in thumbStart..thumbEnd) {
                            pointerOffset - thumbStart
                        } else {
                            current.thumbLengthPx / 2f
                        }
                        dragThumbOffset = (pointerOffset - dragGrabOffset).coerceIn(0f, current.maxThumbOffsetPx)
                        latestOnThumbOffset(dragThumbOffset)
                        down.consume()
                    }
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.changedToUp()) {
                            change.consume()
                            break
                        }
                        val current = latestMetrics
                        val pointerOffset = if (horizontal) change.position.x else change.position.y
                        dragThumbOffset = (pointerOffset - dragGrabOffset).coerceIn(0f, current.maxThumbOffsetPx)
                        latestOnThumbOffset(dragThumbOffset)
                        change.consume()
                    }
                }
            },
    ) {
        Box(
            Modifier
                .offset {
                    if (horizontal) {
                        androidx.compose.ui.unit.IntOffset(metrics.thumbOffsetPx.roundToInt(), 0)
                    } else {
                        androidx.compose.ui.unit.IntOffset(0, metrics.thumbOffsetPx.roundToInt())
                    }
                }
                .then(
                    if (horizontal) {
                        Modifier.width((metrics.thumbLengthPx / density).dp).fillMaxHeight()
                    } else {
                        Modifier.fillMaxWidth().height((metrics.thumbLengthPx / density).dp)
                    },
                )
                .padding(2.dp)
                .background(thumbColor, RoundedCornerShape(99.dp)),
        )
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
    val latestViewport by rememberUpdatedState(viewport)
    val latestZoomPx by rememberUpdatedState(zoomPx)

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
        Modifier.fillMaxSize().pointerInput(editId) {
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
                val liveViewport = latestViewport
                val press = down.position
                val tool = state.workspace.tool
                down.consume() // vector-edit mode owns the press; never let it also move the node

                // Paint-bucket sub-mode: a press hit-tests the network's regions (in shape space, via
                // the same viewBox-aware geometry the renderer fills) and paints the first region that
                // contains the point with the current bucket color, bypassing anchor/handle editing.
                if (state.workspace.vectorPaintBucket) {
                    val (shapeX, shapeY) = liveFit.inverse().apply(liveViewport.toDocX(press.x), liveViewport.toDocY(press.y))
                    val hitRegion = liveNetwork.regions.indices.firstOrNull { regionIndex ->
                        val geometry = networkRegionGeometry(liveNetwork, regionIndex, liveKind.viewBox)
                        geometry != null && contains(geometry, shapeX, shapeY)
                    }
                    if (hitRegion != null) {
                        val fill = DesignPaint.Solid(state.workspace.vectorPaintBucketColor.bindable())
                        state.dispatch(DesignEditorIntent.SetRegionFill(editId, hitRegion, listOf(fill)))
                    }
                    return@awaitEachGesture
                }

                // Priority 0: the per-vertex corner-radius handle of the selected anchor. Dragging it
                // sets that vertex's rounding (clamped >= 0), bracketed Begin…End so the per-frame
                // SetVertexCornerRadius commits coalesce into one undo entry.
                val selectedAnchor = state.workspace.vectorSelectedVertex
                    ?.takeIf { it.part == VectorVertexPart.Anchor }
                    ?.vertexIndex
                    ?.takeIf { it in liveNetwork.vertices.indices }
                if (selectedAnchor != null) {
                    val vertex = liveNetwork.vertices[selectedAnchor]
                    val anchorScreen = vertexScreen(liveFit, liveViewport, vertex)
                    val radiusScreen = vertex.cornerRadius * liveScale * latestZoomPx
                    val handlePos = cornerRadiusHandleScreen(anchorScreen, radiusScreen.toFloat())
                    if ((handlePos - press).getDistance() <= HandleGrabRadiusPx) {
                        dragCornerRadius(state, editId, selectedAnchor, down, anchorScreen, liveScale * latestZoomPx)
                        return@awaitEachGesture
                    }
                }

                // Priority 1: a bezier control handle.
                val handleHit = pickHandle(liveNetwork, liveFit, liveViewport, press)
                if (handleHit != null) {
                    val (vertexIndex, side) = handleHit
                    state.updateWorkspace { it.copy(vectorSelectedVertex = VectorVertexRef(vertexIndex, side.toVertexPart())) }
                    dragNetwork(state, editId, down) { delta ->
                        DesignEditorIntent.MoveVectorHandle(
                            editId, vertexIndex, side,
                            delta.x / latestZoomPx / liveScale, delta.y / latestZoomPx / liveScale,
                        )
                    }
                    return@awaitEachGesture
                }

                // Priority 2: an on-path anchor.
                val anchorHit = pickAnchor(liveNetwork, liveFit, liveViewport, press)
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
                            delta.x / latestZoomPx / liveScale, delta.y / latestZoomPx / liveScale,
                        )
                    }
                    return@awaitEachGesture
                }

                // Empty press with the Pen tool extends the path (anywhere — a growing path may reach
                // past the node's fixed box): splitting the nearest segment when the press lands on an
                // edge, otherwise appending a fresh vertex at the end. A drag off the just-placed vertex
                // pulls its (symmetric) out-handle, so click-drag draws a smooth curve.
                if (tool == EditorTool.Pen) {
                    val (shapeX, shapeY) = liveFit.inverse().apply(liveViewport.toDocX(press.x), liveViewport.toDocY(press.y))
                    val newIndex = liveNetwork.vertices.size
                    val segmentIndex = pickSegment(liveNetwork, liveFit, liveViewport, press)
                    if (segmentIndex != null) {
                        state.dispatch(DesignEditorIntent.AddVectorVertex(editId, segmentIndex, shapeX, shapeY))
                        state.updateWorkspace { it.copy(vectorSelectedVertex = VectorVertexRef(newIndex, VectorVertexPart.Anchor)) }
                    } else {
                        state.dispatch(DesignEditorIntent.AppendVectorVertex(editId, shapeX, shapeY))
                        state.updateWorkspace { it.copy(vectorSelectedVertex = VectorVertexRef(newIndex, VectorVertexPart.Anchor)) }
                        dragNetwork(state, editId, down) { delta ->
                            DesignEditorIntent.MoveVectorHandle(
                                editId, newIndex, HandleSide.Out,
                                delta.x / latestZoomPx / liveScale, delta.y / latestZoomPx / liveScale,
                            )
                        }
                    }
                    return@awaitEachGesture
                }

                // Empty press with any other tool leaves edit mode when it falls outside the box.
                if (!press.insideScreenBox(liveBox, liveViewport)) {
                    state.updateWorkspace { it.copy(vectorEditNodeId = "", vectorSelectedPoint = null, vectorSelectedVertex = null) }
                    return@awaitEachGesture
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
        // Per-vertex corner-radius handle for the selected anchor: a small ring offset up-right of
        // the vertex by its current radius, joined by a thin guide. Dragging it edits the rounding.
        val selectedIndex = selected?.takeIf { it.part == VectorVertexPart.Anchor }?.vertexIndex
        if (selectedIndex != null && selectedIndex in network.vertices.indices) {
            val vertex = network.vertices[selectedIndex]
            val anchor = vertexScreen(fit, viewport, vertex)
            val radiusScreen = (vertex.cornerRadius * fit.a * zoomPx).toFloat()
            val handlePos = cornerRadiusHandleScreen(anchor, radiusScreen)
            drawLine(colors.softStroke, anchor, handlePos, strokeWidth = 1f)
            drawCircle(Color.White, radius = 4.5f, center = handlePos)
            drawCircle(colors.accent, radius = 4.5f, center = handlePos, style = Stroke(width = 1.5f))
        }
    }
}

/** Screen-space grab radius for the arc start/end/ratio drag handles. */
private const val ArcHandleGrabRadiusPx = 12f

/** Baseline screen gap between a vertex and its corner-radius handle (added to the radius). */
private const val CornerRadiusHandleBaseOffsetPx = 16f

/** Fixed up-and-right screen direction the corner-radius handle is offset along. */
private val CornerRadiusHandleDir = Offset(0.70710677f, -0.70710677f)

/** Screen position of the corner-radius handle for a vertex whose radius maps to [radiusScreen] px. */
private fun cornerRadiusHandleScreen(anchor: Offset, radiusScreen: Float): Offset =
    anchor + CornerRadiusHandleDir * (CornerRadiusHandleBaseOffsetPx + radiusScreen.coerceAtLeast(0f))

/**
 * Drags the selected vertex's corner-radius handle: each frame projects the pointer onto the fixed
 * offset ray from [anchorScreen] and converts the beyond-baseline distance (screen px) back to
 * network units via [networkPxPerUnit] (fit scale × zoom). Bracketed Begin…End so the per-frame
 * [DesignEditorIntent.SetVertexCornerRadius] commits coalesce into a single undo entry.
 */
private suspend fun AwaitPointerEventScope.dragCornerRadius(
    state: MissionEditorStateHolder,
    nodeId: String,
    vertexIndex: Int,
    down: PointerInputChange,
    anchorScreen: Offset,
    networkPxPerUnit: Double,
) {
    var began = false
    while (true) {
        val event = awaitPointerEvent()
        val change = event.changes.firstOrNull() ?: break
        if (change.changedToUp()) break
        if (!began) {
            state.dispatch(DesignEditorIntent.BeginInteraction)
            began = true
        }
        val along = (change.position - anchorScreen).let { it.x * CornerRadiusHandleDir.x + it.y * CornerRadiusHandleDir.y }
        val radius = if (networkPxPerUnit > 0.0) {
            ((along - CornerRadiusHandleBaseOffsetPx) / networkPxPerUnit).coerceAtLeast(0.0)
        } else {
            0.0
        }
        state.dispatch(DesignEditorIntent.SetVertexCornerRadius(nodeId, vertexIndex, radius))
        change.consume()
    }
    if (began) state.dispatch(DesignEditorIntent.EndInteraction)
}

// --- Ellipse arc handles -----------------------------------------------------

/** Whether [shape] is an ellipse authored with an arc/wedge or a donut hole (drives arc handles). */
private fun hasEllipseArc(shape: DesignNodeKind.Shape): Boolean =
    shape.shape == ShapeType.Ellipse && (shape.arcSweepDeg != null || (shape.innerRadius ?: 0.0) > 0.0)

/** Which arc parameter an arc-handle drag edits. */
private enum class ArcHandleKind { Start, End, Inner }

/**
 * On-canvas handles for an ellipse arc/donut: draggable dots on the rim for the start angle and the
 * end angle (start + sweep), plus an inner-ratio dot along the start radius. Angles follow the
 * renderer's convention (0° = 3 o'clock, +sweep clockwise on screen; see `ellipseArcGeometry`) and
 * ride the selection's [rotation]. Each drag brackets Begin…End so the per-frame
 * SetArcStart / SetArcSweep / SetArcRatio commits coalesce into one undo entry. The press is consumed
 * only when it lands on a handle, so the ellipse body still moves/selects normally otherwise.
 */
@Composable
private fun ArcHandlesOverlay(
    state: MissionEditorStateHolder,
    nodeId: String,
    box: BoundsBox,
    rotation: Double,
    viewport: CanvasViewport,
) {
    val colors = LocalEditorColors.current
    val latestViewport by rememberUpdatedState(viewport)
    val latestBox by rememberUpdatedState(box)
    val latestRotation by rememberUpdatedState(rotation)

    fun shapeOf(): DesignNodeKind.Shape? =
        (state.designState.document?.nodeById(nodeId)?.kind as? DesignNodeKind.Shape)?.takeIf { hasEllipseArc(it) }

    // Screen position of a rim point at ellipse-parameter [deg] (radius fraction [r], 1 = rim).
    fun rimScreen(b: BoundsBox, rot: Double, deg: Double, r: Double): Offset {
        val rad = deg * PI / 180.0
        val cx = b.centerX
        val cy = b.centerY
        val local = GeoPoint(cx + r * (b.width / 2.0) * cos(rad), cy + r * (b.height / 2.0) * sin(rad))
        val p = if (rot != 0.0) rotatePointAroundCenter(local, GeoPoint(cx, cy), rot) else local
        return latestViewport.toScreen(p.x, p.y)
    }

    // Ellipse parameter angle (deg) of a screen point, undoing the selection rotation.
    fun paramAngleOf(b: BoundsBox, rot: Double, screen: Offset): Double {
        val cx = b.centerX
        val cy = b.centerY
        val doc = GeoPoint(latestViewport.toDocX(screen.x), latestViewport.toDocY(screen.y))
        val local = if (rot != 0.0) rotatePointAroundCenter(doc, GeoPoint(cx, cy), -rot) else doc
        val ux = if (b.width != 0.0) (local.x - cx) / (b.width / 2.0) else 0.0
        val uy = if (b.height != 0.0) (local.y - cy) / (b.height / 2.0) else 0.0
        return atan2(uy, ux) * 180.0 / PI
    }

    fun innerRatioOf(b: BoundsBox, rot: Double, screen: Offset): Double {
        val cx = b.centerX
        val cy = b.centerY
        val doc = GeoPoint(latestViewport.toDocX(screen.x), latestViewport.toDocY(screen.y))
        val local = if (rot != 0.0) rotatePointAroundCenter(doc, GeoPoint(cx, cy), -rot) else doc
        val ux = if (b.width != 0.0) (local.x - cx) / (b.width / 2.0) else 0.0
        val uy = if (b.height != 0.0) (local.y - cy) / (b.height / 2.0) else 0.0
        return hypot(ux, uy).coerceIn(0.0, 0.95)
    }

    Canvas(
        Modifier.fillMaxSize().pointerInput(nodeId) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val shape = shapeOf() ?: return@awaitEachGesture
                val b = latestBox
                val rot = latestRotation
                val start = shape.arcStartDeg ?: 0.0
                val sweep = shape.arcSweepDeg ?: 360.0
                val inner = (shape.innerRadius ?: 0.0)
                val press = down.position

                val startPos = rimScreen(b, rot, start, 1.0)
                val endPos = rimScreen(b, rot, start + sweep, 1.0)
                val innerPos = rimScreen(b, rot, start, if (inner > 0.0) inner else 0.5)

                val candidates = buildList {
                    add(ArcHandleKind.Start to startPos)
                    add(ArcHandleKind.End to endPos)
                    add(ArcHandleKind.Inner to innerPos)
                }
                val grabbed = candidates
                    .minByOrNull { (_, p) -> (p - press).getDistanceSquared() }
                    ?.takeIf { (_, p) -> (p - press).getDistance() <= ArcHandleGrabRadiusPx }
                    ?.first
                    ?: return@awaitEachGesture // not a handle: let the ellipse body move/select

                down.consume()
                var began = false
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull() ?: break
                    if (change.changedToUp()) break
                    if (!began) {
                        state.dispatch(DesignEditorIntent.BeginInteraction)
                        began = true
                    }
                    when (grabbed) {
                        ArcHandleKind.Start ->
                            state.dispatch(DesignEditorIntent.SetArcStart(nodeId, normalizeAngleDegrees(paramAngleOf(b, rot, change.position))))
                        ArcHandleKind.End -> {
                            var s = (paramAngleOf(b, rot, change.position) - start) % 360.0
                            if (s <= 0.0) s += 360.0
                            state.dispatch(DesignEditorIntent.SetArcSweep(nodeId, s))
                        }
                        ArcHandleKind.Inner ->
                            state.dispatch(DesignEditorIntent.SetArcRatio(nodeId, innerRatioOf(b, rot, change.position)))
                    }
                    change.consume()
                }
                if (began) state.dispatch(DesignEditorIntent.EndInteraction)
            }
        },
    ) {
        val shape = shapeOf() ?: return@Canvas
        val b = latestBox
        val rot = latestRotation
        val start = shape.arcStartDeg ?: 0.0
        val sweep = shape.arcSweepDeg ?: 360.0
        val inner = (shape.innerRadius ?: 0.0)

        fun drawDiamond(center: Offset) {
            val r = 5f
            val path = Path().apply {
                moveTo(center.x, center.y - r)
                lineTo(center.x + r, center.y)
                lineTo(center.x, center.y + r)
                lineTo(center.x - r, center.y)
                close()
            }
            drawPath(path, Color.White)
            drawPath(path, colors.accent, style = Stroke(width = 1.5f))
        }
        drawDiamond(rimScreen(b, rot, start, 1.0))
        drawDiamond(rimScreen(b, rot, start + sweep, 1.0))
        val innerPos = rimScreen(b, rot, start, if (inner > 0.0) inner else 0.5)
        drawCircle(Color.White, radius = 4.5f, center = innerPos)
        drawCircle(colors.accent, radius = 4.5f, center = innerPos, style = Stroke(width = 1.5f))
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
            vectorAnchors(path.d).map { pathIndex to it }
        }
    }
    val latestAnchors by rememberUpdatedState(anchors)
    val latestBox by rememberUpdatedState(box)
    val latestScaleX by rememberUpdatedState(scaleX)
    val latestScaleY by rememberUpdatedState(scaleY)
    val latestViewport by rememberUpdatedState(viewport)
    val latestZoomPx by rememberUpdatedState(zoomPx)
    Canvas(
        Modifier.fillMaxSize().pointerInput(editId) {
            awaitEachGesture {
                val down = awaitFirstDown()
                val currentAnchors = latestAnchors
                val currentBox = latestBox
                val currentScaleX = latestScaleX
                val currentScaleY = latestScaleY
                val currentViewport = latestViewport
                val nearest = currentAnchors.minByOrNull { (_, a) ->
                    val sx = currentViewport.toScreen(currentBox.x + a.x * currentScaleX, currentBox.y + a.y * currentScaleY)
                    (sx - down.position).getDistanceSquared()
                }
                val within = nearest?.let { (_, a) ->
                    val sx = currentViewport.toScreen(currentBox.x + a.x * currentScaleX, currentBox.y + a.y * currentScaleY)
                    (sx - down.position).getDistance() <= AnchorGrabRadiusPx
                } ?: false
                if (!within) {
                    val tl = currentViewport.toScreen(currentBox.x, currentBox.y)
                    val br = currentViewport.toScreen(currentBox.right, currentBox.bottom)
                    val outside = down.position.x < tl.x || down.position.y < tl.y ||
                        down.position.x > br.x || down.position.y > br.y
                    if (outside) state.updateWorkspace { it.copy(vectorEditNodeId = "", vectorSelectedPoint = null, vectorSelectedVertex = null) }
                    return@awaitEachGesture
                }
                val hit = nearest
                state.updateWorkspace {
                    it.copy(vectorSelectedPoint = io.aequicor.visualization.editor.presentation.VectorPointRef(hit.first, hit.second.index))
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
                                editId, hit.first, hit.second.index,
                                d.x / latestZoomPx / currentScaleX, d.y / latestZoomPx / currentScaleY,
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
        ctrl && shift && key == Key.O && design.selectedNodeId.isNotBlank() -> {
            state.dispatch(DesignEditorIntent.OutlineStroke(design.selectedNodeId)); true
        }
        ctrl && key == Key.E && design.selectedNodeId.isNotBlank() -> {
            state.dispatch(DesignEditorIntent.FlattenNode(design.selectedNodeId)); true
        }
        ctrl && shift && key == Key.Z -> { state.dispatch(DesignEditorIntent.Redo); true }
        ctrl && key == Key.Z -> { state.dispatch(DesignEditorIntent.Undo); true }
        // Enter finishes a vector-edit session (pen path complete), keeping the node selected.
        (key == Key.Enter || key == Key.NumPadEnter) && state.workspace.vectorEditNodeId.isNotBlank() -> {
            state.updateWorkspace { it.copy(vectorEditNodeId = "", vectorSelectedPoint = null, vectorSelectedVertex = null) }
            true
        }
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
    val edges: ResizableEdges = ResizableEdges.All,
    val minWidth: Double = 1.0,
    val minHeight: Double = 1.0,
    val maxWidth: Double? = null,
    val maxHeight: Double? = null,
)

/** Fixed reference used at drag-start to resolve a live rotate gesture. */
private data class RotateBaseline(
    val nodeId: String,
    val center: GeoPoint,
    val startAngle: Double,
    val startRotation: Double,
    /** Neighbour rotation snap candidates (each sibling's rotation and its +90°); empty for none. */
    val neighborAngles: List<Double>,
)

/** Live rotation-snap feedback while rotating: the applied [angle] and whether it magnetically caught. */
private data class RotateIndicator(val angle: Double, val caught: Boolean)

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

/** The anchor targets a snap resolves against: co-resident [siblings] plus the unrotated ancestor [containers]. */
private data class SnapContext(
    val containers: List<BoundsBox>,
    val siblings: List<BoundsBox>,
)

/**
 * Gathers the [SnapContext] for a node whose parent is [parentBox]: the parent's children minus
 * [excludeIds] (siblings), and the parent plus every unrotated ancestor up to the root (containers).
 * Shared by the move and resize snap baselines.
 */
private fun collectSnapContext(
    document: DesignDocument,
    layout: LayoutBox?,
    parentBox: LayoutBox,
    excludeIds: Set<String>,
): SnapContext {
    val siblings = parentBox.children
        .filter { it.node.sourceId !in excludeIds }
        .map { it.toBoundsBox() }
    val containers = buildList {
        add(parentBox.toBoundsBox())
        var ancestorId = document.parentNodeOf(parentBox.node.sourceId)?.id
        while (ancestorId != null) {
            val ancestorBox = layout?.findBySourceId(ancestorId)
            if (ancestorBox != null && ancestorBox.node.rotation == 0.0) add(ancestorBox.toBoundsBox())
            ancestorId = document.parentNodeOf(ancestorId)?.id
        }
    }
    return SnapContext(containers, siblings)
}

/** The docDx/docDy to feed the resize (raw + snap correction) plus the snap output, or the raw deltas when snapping is off. */
private data class SnappedResize(val docDx: Float, val docDy: Float, val output: ResizeSnapOutput?)

/**
 * Folds a resize drag's raw deltas through the magnetic resize snap: builds the candidate box from
 * [computeResize], snaps its moving edges to [context]'s neighbours/containers (edge/center align +
 * dimension match), and returns the corrected deltas. Snapping is off (raw deltas) when there is no
 * [context] (rotated/grouped/locked selection) or [lockRatio] is on (aspect-preserving, unsnapped).
 */
private fun snapResizeDeltas(
    baseline: BoundsBox,
    handle: ResizeHandle,
    rawDocDx: Float,
    rawDocDy: Float,
    lockRatio: Boolean,
    context: SnapContext?,
    catchPx: Double,
    releasePx: Double,
    prior: ResizeSnapState,
): SnappedResize {
    if (context == null || lockRatio) return SnappedResize(rawDocDx, rawDocDy, null)
    val result = computeResize(baseline.width, baseline.height, handle, rawDocDx.toDouble(), rawDocDy.toDouble(), lockRatio = false)
    val candidate = SnapBox(baseline.x + result.dx, baseline.y + result.dy, result.width, result.height)
    val output = solveResizeSnap(
        candidate,
        handle.toMovingEdges(),
        context.containers.map { it.toSnapBox() },
        context.siblings.map { it.toSnapBox() },
        catch = catchPx,
        release = releasePx,
        prior = prior,
    )
    return SnappedResize(rawDocDx + output.dx.toFloat(), rawDocDy + output.dy.toFloat(), output)
}

private fun LayoutBox.toBoundsBox(): BoundsBox =
    BoundsBox(x = x, y = y, width = width, height = height)

private fun LayoutBox.toContentBoundsBox(): BoundsBox =
    BoundsBox(x = x, y = y, width = contentWidth, height = contentHeight)

private fun LayoutBox.canvasContentBounds(): BoundsBox {
    val boxes = allBoxes().flatMap { box ->
        listOf(
            box.toBoundsBox().visualBounds(box.node.rotation),
            box.toContentBoundsBox().visualBounds(box.node.rotation),
        )
    }
    val minX = boxes.minOf { it.x }
    val minY = boxes.minOf { it.y }
    val maxX = boxes.maxOf { it.right }
    val maxY = boxes.maxOf { it.bottom }
    return BoundsBox(minX, minY, maxX - minX, maxY - minY)
}

/** The transformed visual bounds of a (possibly rotated) box: itself when unrotated, else the axis-aligned bounding box of its rotated corners. */
private fun BoundsBox.visualBounds(rotationDegrees: Double): BoundsBox =
    if (rotationDegrees == 0.0) this else axisAlignedBounds(rotatedCorners(this, rotationDegrees))

/**
 * Path of layout boxes from this (root) box down to the node with [sourceId], inclusive, or null
 * when absent — the chain whose rotated ancestors a nested node's overlay/gesture geometry must
 * follow.
 */
private fun LayoutBox.pathToSourceId(sourceId: String): List<LayoutBox>? {
    if (node.sourceId == sourceId) return listOf(this)
    children.forEach { child -> child.pathToSourceId(sourceId)?.let { return listOf(this) + it } }
    return null
}

/** The rotated ancestors of the node ending [path], nearest-first (immediate parent → root), skipping unrotated ones. */
private fun ancestorRotationsOf(path: List<LayoutBox>): List<AncestorRotation> =
    path.dropLast(1).asReversed().mapNotNull { box ->
        box.node.rotation.takeIf { it != 0.0 }?.let {
            AncestorRotation(GeoPoint(box.x + box.width / 2.0, box.y + box.height / 2.0), it)
        }
    }

/**
 * The node [sourceId]'s on-screen [EffectiveTransform] within this (root) layout, composing every
 * rotated ancestor — so selection outline, handles and the rotate affordance follow the same
 * transform the renderer nests, instead of floating in the node's pre-rotation layout position.
 */
private fun LayoutBox.effectiveTransformFor(sourceId: String): EffectiveTransform? {
    val path = pathToSourceId(sourceId) ?: return null
    val target = path.last()
    return effectiveTransform(target.toBoundsBox(), target.node.rotation, ancestorRotationsOf(path))
}

private const val HandleHitRadiusPx = 11f

/** Screen-space radius within which a free-move drag magnetically *catches* an alignment line. */
private const val SnapCatchPx = 7f

/** Screen-space radius a caught line *holds* out to before releasing — the hysteresis "sticky" feel. */
private const val SnapReleasePx = 16f

/** Angular catch/release radii (degrees) for the rotation magnet — hysteresis like the move/resize snaps. */
private const val RotateCatchDeg = 4.0
private const val RotateReleaseDeg = 9.0

/** Screen-space distance between the rotate affordance and the top-center handle. */
private const val RotateHandleScreenOffsetPx = 26f

private const val CanvasScrollbarThicknessDp = 10f

private const val CanvasScrollbarMinThumbDp = 36f

/**
 * Exponential decay rate (1/s) for the wheel-zoom follow loop: at each frame the live zoom
 * closes the fraction `1 - exp(-rate * dt)` of the gap to its target, so the reach time is
 * frame-rate independent. ~18 lands ~95% of the way there in ~150ms, matching [animateZoomTo]'s
 * button-zoom tween duration.
 */
private const val WheelZoomFollowRate = 18f

/**
 * Convergence tolerance for the wheel-zoom follow loop, as a fraction of the zoom magnitude.
 * Relative (not absolute) so the loop settles the same way at 0.05 as at 16 — an absolute
 * epsilon large enough to terminate near 16 would swallow whole notches near 0.05, stalling
 * zoom-in at the minimum.
 */
private const val WheelZoomConvergeFraction = 1e-3f

private data class CanvasGestureStart(
    val change: PointerInputChange,
    val tertiaryButton: Boolean,
    val shiftPressed: Boolean,
    val altPressed: Boolean,
)

/**
 * Compose Desktop keeps [awaitFirstDown] primary-button-only for mouse input. The canvas also
 * uses the middle button for panning, so wait for that explicitly while preserving the normal
 * primary mouse/touch path used by selection, marquee, resize and object creation.
 */
private suspend fun AwaitPointerEventScope.awaitCanvasGestureStart(): CanvasGestureStart {
    while (true) {
        val event = awaitPointerEvent()
        val change = event.changes.firstOrNull() ?: continue
        val modifiers = event.keyboardModifiers
        if (!change.isConsumed && event.buttons.isTertiaryPressed) {
            return CanvasGestureStart(
                change = change,
                tertiaryButton = true,
                shiftPressed = modifiers.isShiftPressed,
                altPressed = modifiers.isPointerAltPressed,
            )
        }
        val allMouse = event.changes.all { it.type == PointerType.Mouse }
        val regularDown = event.changes.all { it.changedToDown() }
        if (regularDown && (!allMouse || event.buttons.isPrimaryPressed)) {
            return CanvasGestureStart(
                change = change,
                tertiaryButton = false,
                shiftPressed = modifiers.isShiftPressed,
                altPressed = modifiers.isPointerAltPressed,
            )
        }
    }
}

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
    val transform = layout?.effectiveTransformFor(id) ?: return null
    val handle = rotatedHandleAt(transform.box, transform.rotation, viewport, pos) ?: return null
    return cursorForResizeKind(resizeCursorKindForHandle(handle, transform.rotation))
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
    background: Color = colors.accent,
) {
    val visual = box.visualBounds(rotationDegrees)
    val bottomCenter = viewport.toScreen(visual.centerX, visual.bottom)
    val label = "${formatMeasurement(box.width)} x ${formatMeasurement(box.height)}"
    drawFilledBadge(label, Offset(bottomCenter.x, bottomCenter.y + 14f), background, textMeasurer)
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
    hoveredId: String,
    layout: LayoutBox?,
    parentBox: BoundsBox?,
    selectedId: String?,
): AltTarget? {
    if (hoveredId.isNotBlank() && hoveredId != selectedId && hoveredId !in design.selectedNodeIds) {
        layout?.effectiveTransformFor(hoveredId)?.let { t -> return AltTarget(t.box, t.rotation) }
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

/** Builds the anchoring renderer's [GuideStyle] from the editor theme tokens (colors → renderer contract). */
private fun io.aequicor.visualization.editor.ui.theme.EditorColors.guideStyle(): GuideStyle = GuideStyle(
    accent = accent,
    accentSoft = accent.copy(alpha = 0.5f),
    positive = statusPositive,
    warning = statusWarning,
    badgeSurface = badgeSurface,
)

/**
 * Resolves a handle resize via the pure [computeResize] (all geometry rules and the
 * min-size/position clamp live there and are unit-tested). Math derives from fixed
 * drag-start references — [baseline] (the box at press) — plus the cumulative pointer
 * displacement [docDx]/[docDy], so each frame sets absolute geometry rather than compounding
 * on the already-mutated live node.
 *
 * When the target is rotated, [rotationDegrees] inverse-rotates the drag delta into the
 * component's own (pre-rotation) axes first, so dragging a visually rotated handle grows the
 * component along the edge the user is actually looking at; [computeResize] then runs entirely
 * in that de-rotated frame. [rotationDegrees] is the *effective* on-screen rotation — the node's
 * own rotation plus any inherited from rotated ancestors — since that is what tilts the handle
 * the user grabs.
 *
 * Its `dx`/`dy` (the shift of the authored top-left) come back in that de-rotated frame too, so
 * they must be rotated forward before being added to the authored position. Position is stored in
 * the shared root-local frame, which differs from the box's own de-rotated frame by only the
 * node's *own* rotation — so the forward rotation uses [positionDegrees] (the own rotation), not
 * the full effective [rotationDegrees]. They coincide (and this collapses to the classic
 * single-rotation case) whenever no ancestor is rotated.
 */
private fun computeRotatedResize(
    baseline: BoundsBox,
    handle: ResizeHandle,
    docDx: Float,
    docDy: Float,
    lockRatio: Boolean,
    rotationDegrees: Double,
    positionDegrees: Double = rotationDegrees,
    edges: ResizableEdges = ResizableEdges.All,
    minWidth: Double = 1.0,
    minHeight: Double = 1.0,
    maxWidth: Double? = null,
    maxHeight: Double? = null,
): io.aequicor.visualization.editor.presentation.ResizeResult {
    val local = if (rotationDegrees != 0.0) rotateVector(docDx.toDouble(), docDy.toDouble(), -rotationDegrees) else GeoPoint(docDx.toDouble(), docDy.toDouble())
    // [edges]/min/max describe the node's own local (pre-rotation) axes — exactly the frame
    // computeResize runs in — so they pass through unrotated; only the returned dx/dy is rotated
    // forward into the shared document frame below.
    val result = computeResize(
        baseWidth = baseline.width,
        baseHeight = baseline.height,
        handle = handle,
        docDx = local.x,
        docDy = local.y,
        minWidth = minWidth,
        minHeight = minHeight,
        maxWidth = maxWidth,
        maxHeight = maxHeight,
        canMoveLeft = edges.left,
        canMoveRight = edges.right,
        canMoveTop = edges.top,
        canMoveBottom = edges.bottom,
        lockRatio = lockRatio,
    )
    if (positionDegrees == 0.0 || (result.dx == 0.0 && result.dy == 0.0)) return result
    val positionDelta = rotateVector(result.dx, result.dy, positionDegrees)
    return result.copy(dx = positionDelta.x, dy = positionDelta.y)
}

private fun applyResize(
    state: MissionEditorStateHolder,
    target: ResizeTarget,
    baseline: BoundsBox,
    handle: ResizeHandle,
    docDx: Float,
    docDy: Float,
    lockRatio: Boolean,
    rotationDegrees: Double = 0.0,
    positionDegrees: Double = rotationDegrees,
) {
    val result = computeRotatedResize(
        baseline, handle, docDx, docDy, lockRatio, rotationDegrees, positionDegrees,
        edges = target.edges,
        minWidth = target.minWidth, minHeight = target.minHeight,
        maxWidth = target.maxWidth, maxHeight = target.maxHeight,
    )
    // Position is parent-relative; the parent doesn't move during a resize, so the change
    // in absolute origin equals the change in authored position.
    val originPos = target.originPosition
    if (originPos != null && (result.dx != 0.0 || result.dy != 0.0)) {
        state.dispatch(DesignEditorIntent.UpdatePosition(target.nodeId, x = originPos.x + result.dx, y = originPos.y + result.dy))
    }
    // Dispatch size only on an axis that actually changed, so a blocked-direction drag is a true
    // no-op and an edge-only drag doesn't pin the untouched axis' sizing to Fixed (the reducer
    // pins only the non-null axis).
    val widthChanged = result.width != baseline.width
    val heightChanged = result.height != baseline.height
    if (widthChanged || heightChanged) {
        state.dispatch(
            DesignEditorIntent.UpdateSize(
                target.nodeId,
                width = result.width.takeIf { widthChanged },
                height = result.height.takeIf { heightChanged },
            ),
        )
    }
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
    target: ResizeTarget,
    baseline: BoundsBox,
    handle: ResizeHandle,
    docDx: Float,
    docDy: Float,
    lockRatio: Boolean,
    rotationDegrees: Double = 0.0,
    positionDegrees: Double = rotationDegrees,
) {
    val result = computeRotatedResize(
        baseline, handle, docDx, docDy, lockRatio, rotationDegrees, positionDegrees,
        edges = target.edges,
        minWidth = target.minWidth, minHeight = target.minHeight,
        maxWidth = target.maxWidth, maxHeight = target.maxHeight,
    )
    val originPos = target.originPosition
    if (originPos != null && (result.dx != 0.0 || result.dy != 0.0)) {
        state.dispatch(DesignEditorIntent.PositionNode(target.nodeId, x = originPos.x + result.dx, y = originPos.y + result.dy))
    }
    val widthChanged = result.width != baseline.width
    val heightChanged = result.height != baseline.height
    if (widthChanged || heightChanged) {
        state.dispatch(
            DesignEditorIntent.ResizeNode(
                target.nodeId,
                width = result.width.takeIf { widthChanged },
                height = result.height.takeIf { heightChanged },
            ),
        )
    }
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

/**
 * Pen tool, first click: creates a single-vertex `shape: vector` node ([NewObjectKind.Vector], seeded
 * by [io.aequicor.visualization.editor.presentation.EditorNodeFactory]) whose 100x100 box is centred
 * on the click so the seeded network vertex at (50,50) in its 0..100 view box lands under the cursor,
 * then enters vector-edit mode on the new node with vertex 0 selected — leaving the Pen tool active so
 * the next click appends via [DesignEditorIntent.AppendVectorVertex].
 */
private fun commitPenStart(
    state: MissionEditorStateHolder,
    start: Offset,
    viewport: CanvasViewport,
    rootId: String,
) {
    val layout = state.artboardLayout
    val parentId = hitNode(layout, state.designState.document, viewport, start).ifBlank { rootId }
    val parentBox = layout?.findBySourceId(parentId) ?: layout
    val docX = viewport.toDocX(start.x)
    val docY = viewport.toDocY(start.y)
    val size = 100.0
    val relX = (docX - size / 2.0) - (parentBox?.x ?: 0.0)
    val relY = (docY - size / 2.0) - (parentBox?.y ?: 0.0)
    state.dispatch(
        DesignEditorIntent.CreateObject(
            kind = NewObjectKind.Vector,
            parentId = parentId,
            x = relX,
            y = relY,
            width = size,
            height = size,
        ),
    )
    val newId = state.designState.selectedNodeId
    val createdVector = (state.designState.document?.nodeById(newId)?.kind as? DesignNodeKind.Shape)?.shape == ShapeType.Vector
    if (createdVector) {
        state.updateWorkspace {
            it.copy(
                vectorEditNodeId = newId,
                vectorSelectedPoint = null,
                vectorSelectedVertex = VectorVertexRef(0, VectorVertexPart.Anchor),
            )
        }
    }
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
        ResizeTarget(
            nodeId = id,
            baseline = box.toBoundsBox(),
            originPosition = originPosition,
            rotation = box.node.rotation,
            edges = document.resizableEdges(id),
            minWidth = node.minSize?.width ?: 1.0,
            minHeight = node.minSize?.height ?: 1.0,
            maxWidth = node.maxSize?.width,
            maxHeight = node.maxSize?.height,
        )
    }
}

// --- Viewport helpers --------------------------------------------------------

private data class FitRect(val x: Double, val y: Double, val w: Double, val h: Double)

private fun selectionHandleBounds(layout: LayoutBox?, ids: Set<String>): BoundsBox? {
    layout ?: return null
    // Union each node's *visual* bounds (own rotation + inherited ancestor rotation), so the
    // multi-selection bbox covers the real on-screen extent under a rotated ancestor too.
    val boxes = ids.mapNotNull { id -> layout.effectiveTransformFor(id)?.let { it.box.visualBounds(it.rotation) } }
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
    consumePendingFit: Boolean = false,
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
            pendingFit = if (consumePendingFit) PendingFit.None else it.pendingFit,
            viewport = it.viewport.copy(
                zoom = logicalZoom,
                panOffsetXDp = panXpx / density,
                panOffsetYDp = panYpx / density,
            ),
        )
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
                    EditorSvgIcon(
                        icon = deviceIcon(mode),
                        contentDescription = mode.title,
                        modifier = Modifier.size(20.dp),
                        tint = if (active) colors.accent else Color.Black,
                    )
                }
            }
        }
    }
}

@Composable
private fun FloatingToolbar(
    selected: EditorTool,
    lastShapeTool: EditorTool,
    onSelect: (EditorTool) -> Unit,
) {
    val colors = LocalEditorColors.current
    // Explicit slot list: the six shape tools collapse into one flyout slot (Figma W2 pattern).
    val slots = listOf(EditorTool.Select, EditorTool.Frame, EditorTool.Pen, EditorTool.Text, EditorTool.Comment, EditorTool.Link, EditorTool.Code)
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
            ToolbarButton(EditorTool.Select, selected, onSelect)
            ToolbarButton(EditorTool.Frame, selected, onSelect)
            ShapeToolFlyout(selected, lastShapeTool, onSelect)
            ToolbarButton(EditorTool.Pen, selected, onSelect)
            ToolbarButton(EditorTool.Text, selected, onSelect)
            ToolbarButton(EditorTool.Comment, selected, onSelect)
            ToolbarButton(EditorTool.Link, selected, onSelect)
            ToolbarButton(EditorTool.Code, selected, onSelect)
            // Referenced so an unused-`slots` warning never appears if the explicit list drifts.
            check(slots.isNotEmpty())
        }
    }
}

@Composable
private fun ToolbarButton(tool: EditorTool, selected: EditorTool, onSelect: (EditorTool) -> Unit) {
    val colors = LocalEditorColors.current
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

/**
 * One toolbar slot holding all six shape tools. Tapping it selects [lastShapeTool] (fast path);
 * tapping the corner chevron opens a dropdown of every shape tool, each with its mini preview.
 */
@Composable
private fun ShapeToolFlyout(selected: EditorTool, lastShapeTool: EditorTool, onSelect: (EditorTool) -> Unit) {
    val colors = LocalEditorColors.current
    val active = selected.isShapeTool
    val shape = RoundedCornerShape(8.dp)
    var expanded by remember { mutableStateOf(false) }
    val previewStyle = FigurePreviewStyle(
        ink = colors.controlInk,
        fill = colors.selectionFill,
        accent = colors.accent,
        surface = colors.raisedSurface,
    )
    Box {
        Box(
            modifier = Modifier.size(36.dp)
                .background(if (active) colors.accent else colors.controlSurface, shape)
                .border(1.dp, if (active) colors.accent else colors.controlStroke, shape)
                .clip(shape)
                .clickable { onSelect(lastShapeTool) },
            contentAlignment = Alignment.Center,
        ) {
            EditorSvgIcon(
                icon = toolIcon(lastShapeTool),
                contentDescription = "Shape tools",
                modifier = Modifier.size(24.dp),
                tint = if (active) Color.White else colors.ink,
            )
            // Corner chevron: opens the shape-tool menu without changing the active tool.
            Box(
                modifier = Modifier.align(Alignment.BottomEnd).size(12.dp).clip(shape).clickable { expanded = true },
                contentAlignment = Alignment.Center,
            ) {
                EditorSvgIcon(
                    icon = EditorIcon.ChevronDown,
                    contentDescription = "Choose shape tool",
                    modifier = Modifier.size(10.dp),
                    tint = if (active) Color.White else colors.mutedInk,
                )
            }
        }
        EditorDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ShapeTools.forEach { tool ->
                EditorDropdownMenuItem(
                    text = tool.label,
                    leadingContent = tool.shapeTypeOrNull()?.let { shapeType ->
                        { FigureShapePreview(shapeType, previewStyle, Modifier.size(18.dp)) }
                    },
                    onClick = {
                        expanded = false
                        onSelect(tool)
                    },
                )
            }
        }
    }
}

private fun deviceIcon(mode: DeviceMode): EditorIcon = when (mode) {
    DeviceMode.Pc -> EditorIcon.DeviceDesktop
    DeviceMode.Mob -> EditorIcon.DeviceMobile
    DeviceMode.Tab -> EditorIcon.DeviceTablet
}

private fun toolIcon(tool: EditorTool): EditorIcon = when (tool) {
    EditorTool.Select -> EditorIcon.Select
    EditorTool.Frame -> EditorIcon.Frame
    EditorTool.Rectangle -> EditorIcon.Rectangle
    EditorTool.Ellipse -> EditorIcon.Ellipse
    EditorTool.Polygon -> EditorIcon.Polygon
    EditorTool.Star -> EditorIcon.Star
    EditorTool.Line -> EditorIcon.Line
    EditorTool.Arrow -> EditorIcon.Arrow
    EditorTool.Pen -> EditorIcon.Pen
    EditorTool.Text -> EditorIcon.Text
    EditorTool.Comment -> EditorIcon.Comments
    EditorTool.Link -> EditorIcon.Link
    EditorTool.Code -> EditorIcon.Code
}

/** The [ShapeType] a shape tool draws, for its dropdown mini-preview. */
private fun EditorTool.shapeTypeOrNull(): ShapeType? = when (this) {
    EditorTool.Rectangle -> ShapeType.Rectangle
    EditorTool.Ellipse -> ShapeType.Ellipse
    EditorTool.Polygon -> ShapeType.Polygon
    EditorTool.Star -> ShapeType.Star
    EditorTool.Line -> ShapeType.Line
    EditorTool.Arrow -> ShapeType.Arrow
    else -> null
}

private val ShapeTools: List<EditorTool> = listOf(
    EditorTool.Rectangle,
    EditorTool.Ellipse,
    EditorTool.Polygon,
    EditorTool.Star,
    EditorTool.Line,
    EditorTool.Arrow,
)

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
