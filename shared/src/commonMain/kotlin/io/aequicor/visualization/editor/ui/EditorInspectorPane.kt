package io.aequicor.visualization.editor.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.aequicor.visualization.MissionEditorStateHolder
import io.aequicor.visualization.editor.presentation.DesignEditorIntent
import io.aequicor.visualization.editor.presentation.EditorLayoutMode
import io.aequicor.visualization.editor.presentation.EffectOp
import io.aequicor.visualization.editor.presentation.EffectType
import io.aequicor.visualization.editor.presentation.FillKind
import io.aequicor.visualization.editor.presentation.FillOp
import io.aequicor.visualization.editor.presentation.InspectorSection
import io.aequicor.visualization.editor.presentation.InspectorTab
import io.aequicor.visualization.editor.presentation.PaddingSide
import io.aequicor.visualization.editor.presentation.isCoordinatePositioned
import io.aequicor.visualization.editor.presentation.isNodeLocked
import io.aequicor.visualization.editor.presentation.StrokeOp
import io.aequicor.visualization.editor.presentation.TypographyPatch
import io.aequicor.visualization.editor.ui.theme.LocalEditorColors
import io.aequicor.visualization.engine.ir.layout.LayoutBox
import io.aequicor.visualization.engine.ir.model.AlignItems
import io.aequicor.visualization.engine.ir.model.DesignColor
import io.aequicor.visualization.engine.ir.model.DesignEffect
import io.aequicor.visualization.engine.ir.model.DesignNode
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.engine.ir.model.DesignPaint
import io.aequicor.visualization.engine.ir.model.HorizontalConstraint
import io.aequicor.visualization.engine.ir.model.JustifyContent
import io.aequicor.visualization.engine.ir.model.LayoutMode
import io.aequicor.visualization.engine.ir.model.SizingMode
import io.aequicor.visualization.engine.ir.model.StrokeAlign
import io.aequicor.visualization.engine.ir.model.TextAlignHorizontal
import io.aequicor.visualization.engine.ir.model.TextAutoResize
import io.aequicor.visualization.engine.ir.model.VerticalConstraint
import io.aequicor.visualization.engine.ir.model.literalOrNull
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.roundToInt

/** Right column: context inspector driven by the current selection. */
@Composable
fun EditorInspectorPane(state: MissionEditorStateHolder, modifier: Modifier = Modifier) {
    val colors = LocalEditorColors.current
    Column(modifier) {
        Text(
            "Inspector",
            modifier = Modifier.padding(start = 8.dp, bottom = 12.dp),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(8.dp),
            color = Color.White,
            border = BorderStroke(1.dp, colors.panelStroke),
        ) {
            Column(Modifier.fillMaxSize()) {
                TabStrip(
                    tabs = InspectorTab.entries,
                    selected = state.workspace.inspectorTab,
                    title = { it.title },
                    onSelect = { tab -> state.updateWorkspace { it.copy(inspectorTab = tab) } },
                )
                when (state.workspace.inspectorTab) {
                    InspectorTab.Design -> InspectorDesign(state)
                    InspectorTab.Comments -> EmptyInspector("No comments yet.")
                }
            }
        }
    }
}

@Composable
private fun EmptyInspector(text: String) {
    val colors = LocalEditorColors.current
    Box(Modifier.fillMaxSize().padding(16.dp)) {
        Text(text, color = colors.mutedInk, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun InspectorDesign(state: MissionEditorStateHolder) {
    val colors = LocalEditorColors.current
    val design = state.designState
    val node = design.selectedNode
    if (node == null) {
        EmptyInspector("Nothing selected — select an object on the canvas or in Layers.")
        return
    }
    val box = state.artboardLayout?.findBySourceId(design.selectedNodeId)
    val isText = node.kind is DesignNodeKind.Text
    val isFrame = node.kind is DesignNodeKind.Frame
    // Constraints govern how an absolutely-positioned child reflows; auto-layout children
    // are placed by the layout, so the section only applies to coordinate-positioned nodes.
    val positioned = design.document?.isCoordinatePositioned(design.selectedNodeId) ?: false
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SelectionHeader(state, node)
        Section(state, InspectorSection.Position) { PositionSection(state, node, box) }
        Section(state, InspectorSection.Layout, visible = isFrame) { LayoutSection(state, node) }
        Section(state, InspectorSection.Appearance) { AppearanceSection(state, node, box) }
        Section(state, InspectorSection.Fill) { FillSection(state, node) }
        Section(state, InspectorSection.Stroke) { StrokeSection(state, node) }
        Section(state, InspectorSection.Effects) { EffectsSection(state, node) }
        Section(state, InspectorSection.Typography, visible = isText) { TypographySection(state, node) }
        Section(state, InspectorSection.Constraints, visible = positioned) { ConstraintsSection(state, node) }
    }
}

@Composable
private fun SelectionHeader(state: MissionEditorStateHolder, node: DesignNode) {
    val colors = LocalEditorColors.current
    val design = state.designState
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            if (design.hasMultiSelection) "${design.selectedNodeIds.size} selected" else node.name.ifBlank { node.id },
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = colors.ink,
            maxLines = 1,
        )
        if (!design.hasMultiSelection && node.locked) {
            Text("Locked", style = MaterialTheme.typography.labelSmall, color = colors.statusWarning, fontWeight = FontWeight.SemiBold)
        }
        SmallSquareButton("dup", onClick = { state.dispatch(DesignEditorIntent.DuplicateNodes(design.selectedNodeIds)) })
        SmallSquareButton("del", onClick = { state.dispatch(DesignEditorIntent.DeleteNodes(design.selectedNodeIds)) })
    }
}

@Composable
private fun Section(
    state: MissionEditorStateHolder,
    section: InspectorSection,
    visible: Boolean = true,
    content: @Composable () -> Unit,
) {
    if (!visible) return
    val colors = LocalEditorColors.current
    val expanded = section in state.workspace.expandedSections
    Column(Modifier.fillMaxWidth().border(BorderStroke(0.5.dp, colors.softStroke)).padding(horizontal = 18.dp, vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth().clickable { state.toggleSection(section) }, verticalAlignment = Alignment.CenterVertically) {
            Text(section.title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(if (expanded) "^" else "v", color = colors.controlInk, fontWeight = FontWeight.Bold)
        }
        if (expanded) {
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

// --- Position / size ---------------------------------------------------------

@Composable
private fun PositionSection(state: MissionEditorStateHolder, node: DesignNode, box: LayoutBox?) {
    if (state.designState.hasMultiSelection) {
        MultiPositionSection(state)
        return
    }
    val design = state.designState
    val ws = state.workspace
    val nodeId = node.id
    val document = design.document
    val positioned = document?.isCoordinatePositioned(nodeId) ?: false
    val locked = design.isNodeLocked(nodeId)
    val parentBox = state.artboardLayout?.let { root -> findParentBox(root, nodeId) }
    val x = if (positioned) node.position?.x ?: 0.0 else (box?.x ?: 0.0) - (parentBox?.x ?: 0.0)
    val y = if (positioned) node.position?.y ?: 0.0 else (box?.y ?: 0.0) - (parentBox?.y ?: 0.0)
    val w = box?.width ?: node.size.width ?: 0.0
    val h = box?.height ?: node.size.height ?: 0.0

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        InspectorNumberField("X", x.formatPx(), "", nodeId, Modifier.weight(1f), enabled = positioned && !locked) {
            state.dispatch(DesignEditorIntent.UpdatePosition(nodeId, x = it))
        }
        InspectorNumberField("Y", y.formatPx(), "", nodeId, Modifier.weight(1f), enabled = positioned && !locked) {
            state.dispatch(DesignEditorIntent.UpdatePosition(nodeId, y = it))
        }
    }
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        InspectorCommitNumberField("W", w.formatPx(), "", nodeId, Modifier.weight(1f), enabled = !locked) { value ->
            state.dispatch(DesignEditorIntent.ResizeNode(nodeId, width = value))
            if (ws.lockAspectRatio && h > 0) state.dispatch(DesignEditorIntent.ResizeNode(nodeId, height = value * h / w))
        }
        InspectorCommitNumberField("H", h.formatPx(), "", nodeId, Modifier.weight(1f), enabled = !locked) { value ->
            state.dispatch(DesignEditorIntent.ResizeNode(nodeId, height = value))
            if (ws.lockAspectRatio && h > 0) state.dispatch(DesignEditorIntent.ResizeNode(nodeId, width = value * w / h))
        }
        SmallSquareButton(if (ws.lockAspectRatio) "L*" else "L", active = ws.lockAspectRatio, onClick = {
            state.updateWorkspace { it.copy(lockAspectRatio = !it.lockAspectRatio) }
        })
    }
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        InspectorNumberField("Rot", node.rotation.formatPx(), "°", nodeId, Modifier.weight(1f), enabled = !locked) {
            state.dispatch(DesignEditorIntent.SetRotation(nodeId, it))
        }
        // Flip mirrors the arrangement of a multi-selection around its shared center; a
        // single primitive has no IR flip transform, so the buttons enable only for 2+.
        val canFlip = design.selectedNodeIds.size >= 2
        SmallSquareButton("|<>|", enabled = canFlip, onClick = { state.dispatch(DesignEditorIntent.FlipHorizontal(design.selectedNodeIds)) })
        SmallSquareButton("flipV", enabled = canFlip, onClick = { state.dispatch(DesignEditorIntent.FlipVertical(design.selectedNodeIds)) })
    }
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        SelectField(
            value = (node.sizing?.horizontal ?: SizingMode.Fixed).sizingLabel(),
            options = SizingMode.entries.map { it.sizingLabel() },
            onSelect = { state.dispatch(DesignEditorIntent.UpdateSizingMode(nodeId, horizontal = sizingFromLabel(it))) },
            modifier = Modifier.weight(1f),
        )
        SelectField(
            value = (node.sizing?.vertical ?: SizingMode.Fixed).sizingLabel(),
            options = SizingMode.entries.map { it.sizingLabel() },
            onSelect = { state.dispatch(DesignEditorIntent.UpdateSizingMode(nodeId, vertical = sizingFromLabel(it))) },
            modifier = Modifier.weight(1f),
        )
    }
    Spacer(Modifier.height(8.dp))
    CheckRow("Clip content", node.layout.clipsContent) { state.dispatch(DesignEditorIntent.SetClipsContent(nodeId, it)) }
}

/**
 * Position editor for a multi-selection: shared authored values, "Mixed" where they
 * differ, and every commit applies to all selected nodes as a single undo entry.
 */
@Composable
private fun MultiPositionSection(state: MissionEditorStateHolder) {
    val design = state.designState
    val ids = design.selectedNodeIds
    val nodes = design.selectedNodes
    val anyLocked = nodes.any { it.locked }
    val key = ids.sorted().joinToString(",")

    fun shared(selector: (DesignNode) -> Double?): Double? {
        val values = nodes.map(selector)
        val first = values.firstOrNull() ?: return null
        return if (values.all { it == first }) first else null
    }

    fun bulk(make: (String) -> DesignEditorIntent) {
        state.dispatch(DesignEditorIntent.BeginInteraction)
        ids.forEach { state.dispatch(make(it)) }
        state.dispatch(DesignEditorIntent.EndInteraction)
    }

    val x = shared { it.position?.x }
    val y = shared { it.position?.y }
    val w = shared { it.size.width }
    val h = shared { it.size.height }

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        InspectorNumberField("X", x?.formatPx() ?: "", "", "mx-$key", Modifier.weight(1f), enabled = !anyLocked, placeholder = "Mixed") {
            v -> bulk { id -> DesignEditorIntent.UpdatePosition(id, x = v) }
        }
        InspectorNumberField("Y", y?.formatPx() ?: "", "", "my-$key", Modifier.weight(1f), enabled = !anyLocked, placeholder = "Mixed") {
            v -> bulk { id -> DesignEditorIntent.UpdatePosition(id, y = v) }
        }
    }
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        InspectorNumberField("W", w?.formatPx() ?: "", "", "mw-$key", Modifier.weight(1f), enabled = !anyLocked, placeholder = "Mixed") {
            v -> bulk { id -> DesignEditorIntent.UpdateSize(id, width = v) }
        }
        InspectorNumberField("H", h?.formatPx() ?: "", "", "mh-$key", Modifier.weight(1f), enabled = !anyLocked, placeholder = "Mixed") {
            v -> bulk { id -> DesignEditorIntent.UpdateSize(id, height = v) }
        }
    }
    Spacer(Modifier.height(8.dp))
    MutedNote("${ids.size} layers selected — edits apply to all.")
}

// --- Layout ------------------------------------------------------------------

@Composable
private fun LayoutSection(state: MissionEditorStateHolder, node: DesignNode) {
    val nodeId = node.id
    val current = when (node.layout.mode) {
        LayoutMode.Horizontal -> EditorLayoutMode.Horizontal
        LayoutMode.Vertical -> EditorLayoutMode.Vertical
        LayoutMode.Grid -> EditorLayoutMode.Grid
        LayoutMode.None -> EditorLayoutMode.Free
    }
    SegmentedControl(
        options = listOf(EditorLayoutMode.Free, EditorLayoutMode.Vertical, EditorLayoutMode.Horizontal, EditorLayoutMode.Grid),
        selected = current,
        label = { it.displayName.take(4) },
        onSelect = { state.dispatch(DesignEditorIntent.SetLayoutMode(nodeId, it)) },
        modifier = Modifier.fillMaxWidth(),
    )
    if (node.layout.mode != LayoutMode.None) {
        Spacer(Modifier.height(10.dp))
        val gap = (node.layout.gap as? io.aequicor.visualization.engine.ir.model.DesignGap.Fixed)?.value?.literalOrNull() ?: 0.0
        InspectorNumberField("Gap", gap.formatPx(), "", nodeId) { state.dispatch(DesignEditorIntent.SetLayoutGap(nodeId, it)) }
        Spacer(Modifier.height(8.dp))
        val pad = node.layout.padding
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InspectorNumberField("T", (pad.top.literalOrNull() ?: 0.0).formatPx(), "", nodeId, Modifier.weight(1f)) { state.dispatch(DesignEditorIntent.SetLayoutPadding(nodeId, PaddingSide.Top, it)) }
            InspectorNumberField("R", (pad.right.literalOrNull() ?: 0.0).formatPx(), "", nodeId, Modifier.weight(1f)) { state.dispatch(DesignEditorIntent.SetLayoutPadding(nodeId, PaddingSide.Right, it)) }
            InspectorNumberField("B", (pad.bottom.literalOrNull() ?: 0.0).formatPx(), "", nodeId, Modifier.weight(1f)) { state.dispatch(DesignEditorIntent.SetLayoutPadding(nodeId, PaddingSide.Bottom, it)) }
            InspectorNumberField("L", (pad.left.literalOrNull() ?: 0.0).formatPx(), "", nodeId, Modifier.weight(1f)) { state.dispatch(DesignEditorIntent.SetLayoutPadding(nodeId, PaddingSide.Left, it)) }
        }
        Spacer(Modifier.height(10.dp))
        LabeledField("Align") {
            SegmentedControl(
                options = listOf(AlignItems.Start, AlignItems.Center, AlignItems.End, AlignItems.Stretch),
                selected = node.layout.alignItems,
                label = { it.alignItemsLabel() },
                onSelect = { state.dispatch(DesignEditorIntent.SetLayoutAlign(nodeId, alignItems = it)) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(8.dp))
        LabeledField("Distribute") {
            SegmentedControl(
                options = listOf(JustifyContent.Start, JustifyContent.Center, JustifyContent.End, JustifyContent.SpaceBetween),
                selected = node.layout.justifyContent,
                label = { it.justifyLabel() },
                onSelect = { state.dispatch(DesignEditorIntent.SetLayoutAlign(nodeId, justifyContent = it)) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// --- Appearance --------------------------------------------------------------

@Composable
private fun AppearanceSection(state: MissionEditorStateHolder, node: DesignNode, box: LayoutBox?) {
    val colors = LocalEditorColors.current
    val nodeId = node.id
    val locked = state.designState.isNodeLocked(nodeId)
    val opacity = (node.opacity.literalOrNull() ?: 1.0)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Opacity", style = MaterialTheme.typography.bodySmall, color = colors.controlInk)
        Text("${(opacity * 100).roundToInt()}%", style = MaterialTheme.typography.bodySmall)
        // Whole drag = one undo entry (layer opacity, separate from fill opacity); applies
        // across the whole selection so a multi-selection opacity edit is uniform.
        UndoableSlider(
            value = (opacity * 100).toFloat(),
            valueRange = 0f..100f,
            enabled = !locked,
            onBegin = { state.dispatch(DesignEditorIntent.BeginInteraction) },
            onChange = { v -> state.designState.selectedNodeIds.forEach { id -> state.dispatch(DesignEditorIntent.UpdateOpacity(id, v / 100.0)) } },
            onEnd = { state.dispatch(DesignEditorIntent.EndInteraction) },
            modifier = Modifier.weight(1f),
        )
    }
    Spacer(Modifier.height(8.dp))
    LabeledField("Blend") {
        SelectField(
            value = node.blendMode.ifBlank { "normal" },
            options = BlendModes,
            onSelect = { state.dispatch(DesignEditorIntent.SetBlendMode(nodeId, it)) },
        )
    }
    Spacer(Modifier.height(8.dp))
    val radius = node.cornerRadius?.topLeft?.literalOrNull() ?: 0.0
    InspectorNumberField("Radius", radius.formatPx(), "", nodeId) { state.dispatch(DesignEditorIntent.UpdateCornerRadius(nodeId, it)) }
}

// --- Fill --------------------------------------------------------------------

@Composable
private fun FillSection(state: MissionEditorStateHolder, node: DesignNode) {
    val nodeId = node.id
    val enabled = !state.designState.isNodeLocked(nodeId)
    val fills = node.fills.orEmpty()
    SectionHeaderAdd("Fills") { if (enabled) state.dispatch(DesignEditorIntent.FillCommand(nodeId, FillOp.Add)) }
    if (fills.isEmpty()) {
        MutedNote("No fills. Add one with +.")
        return
    }
    fills.forEachIndexed { index, paint ->
        FillRow(state, nodeId, index, paint, enabled)
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun FillRow(state: MissionEditorStateHolder, nodeId: String, index: Int, paint: DesignPaint, enabled: Boolean) {
    val kind = paint.fillKind()
    val visible = paint.visible.literalOrNull() ?: true
    val opacity = paint.opacity.literalOrNull() ?: 1.0
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        LayerToggle(visible) { state.dispatch(DesignEditorIntent.FillCommand(nodeId, FillOp.ToggleAt(index))) }
        Box(Modifier.weight(1f)) {
            when (paint) {
                is DesignPaint.Solid -> {
                    val color = paint.color.literalOrNull() ?: DesignColor.Black
                    InspectorColorField(
                        state = state,
                        color = color,
                        opacity = opacity,
                        label = color.toHex(),
                        enabled = enabled,
                        onColor = { state.dispatch(DesignEditorIntent.FillCommand(nodeId, FillOp.SetColor(index, it))) },
                        onOpacity = { state.dispatch(DesignEditorIntent.FillCommand(nodeId, FillOp.SetOpacity(index, it))) },
                    )
                }
                is DesignPaint.Gradient -> GradientPreview(paint)
                else -> FillTypeChip(kind.displayName)
            }
        }
        SmallSelect(kind.displayName, FillKind.entries.map { it.displayName }) { label ->
            FillKind.entries.firstOrNull { it.displayName == label }?.let { state.dispatch(DesignEditorIntent.FillCommand(nodeId, FillOp.SetType(index, it))) }
        }
        RemoveButton { state.dispatch(DesignEditorIntent.FillCommand(nodeId, FillOp.RemoveAt(index))) }
    }
    if (paint is DesignPaint.Gradient) {
        GradientStops(state, nodeId, index, paint, enabled)
    }
}

/** Read-only horizontal preview of a gradient fill (the stops are edited in the sub-rows). */
@Composable
private fun GradientPreview(gradient: DesignPaint.Gradient) {
    val colors = LocalEditorColors.current
    val stops = gradient.stops.sortedBy { it.position }.mapNotNull { it.color.literalOrNull()?.toComposeColor() }
    val brush = when {
        stops.size >= 2 -> Brush.horizontalGradient(stops)
        stops.size == 1 -> Brush.horizontalGradient(listOf(stops.first(), stops.first()))
        else -> Brush.horizontalGradient(listOf(Color.Gray, Color.Gray))
    }
    Surface(
        modifier = Modifier.fillMaxWidth().height(36.dp),
        shape = RoundedCornerShape(6.dp),
        color = Color.White,
        border = BorderStroke(1.dp, colors.softStroke),
    ) {
        Box(Modifier.padding(6.dp)) {
            Box(Modifier.fillMaxWidth().height(24.dp).background(brush, RoundedCornerShape(4.dp)))
        }
    }
}

/** Compact label chip for fill types with no inline editor here (image / unknown). */
@Composable
private fun FillTypeChip(text: String) {
    val colors = LocalEditorColors.current
    Surface(
        modifier = Modifier.fillMaxWidth().height(36.dp),
        shape = RoundedCornerShape(6.dp),
        color = Color.White,
        border = BorderStroke(1.dp, colors.softStroke),
    ) {
        Row(Modifier.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, style = MaterialTheme.typography.bodySmall, color = colors.ink)
        }
    }
}

@Composable
private fun GradientStops(state: MissionEditorStateHolder, nodeId: String, index: Int, gradient: DesignPaint.Gradient, enabled: Boolean) {
    Column(Modifier.padding(start = 26.dp, top = 4.dp)) {
        // Direction angle (0° = left→right, 90° = top→bottom).
        InspectorNumberField("Angle", gradientAngleDegrees(gradient).formatPx(), "°", "$nodeId-fill-$index-angle", enabled = enabled) {
            state.dispatch(DesignEditorIntent.FillCommand(nodeId, FillOp.SetGradientAngle(index, it)))
        }
        Spacer(Modifier.height(6.dp))
        gradient.stops.forEachIndexed { stopIndex, stop ->
            val stopColor = stop.color.literalOrNull() ?: DesignColor.Black
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.weight(1f)) {
                    InspectorArgbColorField(
                        state = state,
                        color = stopColor,
                        label = stopColor.toHex(),
                        enabled = enabled,
                        onArgb = { state.dispatch(DesignEditorIntent.FillCommand(nodeId, FillOp.SetGradientStopColor(index, stopIndex, it))) },
                    )
                }
                Box(Modifier.width(78.dp)) {
                    InspectorNumberField("", (stop.position * 100).formatPx(), "%", "$nodeId-fill-$index-stop-$stopIndex", enabled = enabled) {
                        state.dispatch(DesignEditorIntent.FillCommand(nodeId, FillOp.SetGradientStopPosition(index, stopIndex, it / 100.0)))
                    }
                }
                RemoveButton { state.dispatch(DesignEditorIntent.FillCommand(nodeId, FillOp.RemoveGradientStop(index, stopIndex))) }
            }
            Spacer(Modifier.height(4.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TinyButton("+ stop") { state.dispatch(DesignEditorIntent.FillCommand(nodeId, FillOp.AddGradientStop(index))) }
            TinyButton("reverse") { state.dispatch(DesignEditorIntent.FillCommand(nodeId, FillOp.ReverseGradient(index))) }
        }
    }
}

/** Current gradient direction as an angle in degrees, derived from its from→to line. */
private fun gradientAngleDegrees(gradient: DesignPaint.Gradient): Double =
    atan2(gradient.to.y - gradient.from.y, gradient.to.x - gradient.from.x) * 180.0 / PI

// --- Stroke ------------------------------------------------------------------

@Composable
private fun StrokeSection(state: MissionEditorStateHolder, node: DesignNode) {
    val nodeId = node.id
    val enabled = !state.designState.isNodeLocked(nodeId)
    val strokes = node.strokes
    SectionHeaderAdd("Stroke") {
        if (strokes == null && enabled) state.dispatch(DesignEditorIntent.StrokeCommand(nodeId, StrokeOp.Add))
    }
    if (strokes == null) {
        MutedNote("No stroke. Add one with +.")
        return
    }
    val paint = strokes.paints.firstOrNull()
    val color = paint?.displayColor() ?: DesignColor.fromHex("#1E88FF") ?: DesignColor.Black
    val visible = paint?.visible?.literalOrNull() ?: true
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        LayerToggle(visible) { state.dispatch(DesignEditorIntent.StrokeCommand(nodeId, StrokeOp.SetVisible(!visible))) }
        Box(Modifier.weight(1f)) {
            InspectorColorField(
                state = state,
                color = color,
                opacity = paint?.opacity?.literalOrNull() ?: 1.0,
                label = color.toHex(),
                enabled = enabled,
                onColor = { state.dispatch(DesignEditorIntent.StrokeCommand(nodeId, StrokeOp.SetColor(it))) },
                onOpacity = { state.dispatch(DesignEditorIntent.StrokeCommand(nodeId, StrokeOp.SetOpacity(it))) },
            )
        }
        RemoveButton { state.dispatch(DesignEditorIntent.StrokeCommand(nodeId, StrokeOp.Remove)) }
    }
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        InspectorNumberField("W", (strokes.weight.literalOrNull() ?: 1.0).formatPx(), "", nodeId, Modifier.weight(1f), enabled = enabled) {
            state.dispatch(DesignEditorIntent.StrokeCommand(nodeId, StrokeOp.SetWeight(it)))
        }
        SmallSelect(strokes.align.strokeLabel(), StrokeAlign.entries.map { it.strokeLabel() }) { label ->
            StrokeAlign.entries.firstOrNull { it.strokeLabel() == label }?.let { state.dispatch(DesignEditorIntent.StrokeCommand(nodeId, StrokeOp.SetAlign(it))) }
        }
    }
    Spacer(Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val dashed = strokes.dashPattern.isNotEmpty()
        CheckRow("Dashed", dashed) { state.dispatch(DesignEditorIntent.StrokeCommand(nodeId, StrokeOp.SetDashed(it))) }
    }
    if (node.kind is DesignNodeKind.Shape) {
        val shape = (node.kind as DesignNodeKind.Shape).shape
        if (shape == io.aequicor.visualization.engine.ir.model.ShapeType.Line || shape == io.aequicor.visualization.engine.ir.model.ShapeType.Arrow) {
            Spacer(Modifier.height(6.dp))
            LabeledField("Ends") {
                SelectField(strokes.cap, listOf("butt", "round", "square", "arrow"), onSelect = { state.dispatch(DesignEditorIntent.StrokeCommand(nodeId, StrokeOp.SetCap(it))) })
            }
        }
    }
}

// --- Effects -----------------------------------------------------------------

@Composable
private fun EffectsSection(state: MissionEditorStateHolder, node: DesignNode) {
    val nodeId = node.id
    SectionHeaderAdd("Effects") { state.dispatch(DesignEditorIntent.EffectCommand(nodeId, EffectOp.Add(EffectType.DropShadow))) }
    if (node.effects.isEmpty()) {
        MutedNote("No effects. Add drop shadow with +.")
        return
    }
    node.effects.forEachIndexed { index, effect ->
        val visible = effect.visible.literalOrNull() ?: true
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            LayerToggle(visible) { state.dispatch(DesignEditorIntent.EffectCommand(nodeId, EffectOp.ToggleAt(index))) }
            SmallSelect(effect.effectLabel(), EffectType.entries.map { it.displayName }, modifier = Modifier.weight(1f)) { label ->
                EffectType.entries.firstOrNull { it.displayName == label }?.let { state.dispatch(DesignEditorIntent.EffectCommand(nodeId, EffectOp.SetType(index, it))) }
            }
            RemoveButton { state.dispatch(DesignEditorIntent.EffectCommand(nodeId, EffectOp.RemoveAt(index))) }
        }
        if (effect is DesignEffect.DropShadow || effect is DesignEffect.InnerShadow) {
            val dx = (effect as? DesignEffect.DropShadow)?.offset?.x ?: (effect as? DesignEffect.InnerShadow)?.offset?.x ?: 0.0
            val dy = (effect as? DesignEffect.DropShadow)?.offset?.y ?: (effect as? DesignEffect.InnerShadow)?.offset?.y ?: 0.0
            val blur = (effect as? DesignEffect.DropShadow)?.blur ?: (effect as? DesignEffect.InnerShadow)?.blur ?: 0.0
            Row(Modifier.padding(start = 26.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                InspectorNumberField("X", dx.formatPx(), "", "$nodeId-fx-$index", Modifier.weight(1f)) { state.dispatch(DesignEditorIntent.EffectCommand(nodeId, EffectOp.UpdateShadow(index, dx = it))) }
                InspectorNumberField("Y", dy.formatPx(), "", "$nodeId-fx-$index", Modifier.weight(1f)) { state.dispatch(DesignEditorIntent.EffectCommand(nodeId, EffectOp.UpdateShadow(index, dy = it))) }
                InspectorNumberField("Blur", blur.formatPx(), "", "$nodeId-fx-$index", Modifier.weight(1f)) { state.dispatch(DesignEditorIntent.EffectCommand(nodeId, EffectOp.UpdateShadow(index, blur = it))) }
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}

// --- Typography --------------------------------------------------------------

@Composable
private fun TypographySection(state: MissionEditorStateHolder, node: DesignNode) {
    val nodeId = node.id
    val kind = node.kind as? DesignNodeKind.Text ?: return
    val style = kind.textStyle
    val size = style?.fontSize?.literalOrNull() ?: 16.0
    val weight = style?.fontWeight?.literalOrNull() ?: 400.0
    val lineHeight = style?.lineHeight?.value ?: 120.0
    val letter = style?.letterSpacing?.value ?: 0.0
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        InspectorNumberField("Size", size.formatPx(), "", nodeId, Modifier.weight(1f)) { state.dispatch(DesignEditorIntent.UpdateTypography(nodeId, TypographyPatch(fontSize = it))) }
        InspectorNumberField("Wt", weight.formatPx(), "", nodeId, Modifier.weight(1f)) { state.dispatch(DesignEditorIntent.UpdateTypography(nodeId, TypographyPatch(fontWeight = it))) }
    }
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        InspectorNumberField("Line", lineHeight.formatPx(), "%", nodeId, Modifier.weight(1f)) { state.dispatch(DesignEditorIntent.UpdateTypography(nodeId, TypographyPatch(lineHeightPercent = it))) }
        InspectorNumberField("Ltr", letter.formatPx(), "px", nodeId, Modifier.weight(1f)) { state.dispatch(DesignEditorIntent.UpdateTypography(nodeId, TypographyPatch(letterSpacing = it))) }
    }
    Spacer(Modifier.height(8.dp))
    SegmentedControl(
        options = listOf(TextAlignHorizontal.Left, TextAlignHorizontal.Center, TextAlignHorizontal.Right, TextAlignHorizontal.Justified),
        selected = style?.textAlignHorizontal ?: TextAlignHorizontal.Left,
        label = { it.alignLabel() },
        onSelect = { state.dispatch(DesignEditorIntent.UpdateTypography(nodeId, TypographyPatch(alignHorizontal = it))) },
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(8.dp))
    LabeledField("Resize") {
        SegmentedControl(
            options = listOf(TextAutoResize.WidthAndHeight, TextAutoResize.Height, TextAutoResize.None),
            selected = kind.autoResize,
            label = { it.autoResizeLabel() },
            onSelect = { state.dispatch(DesignEditorIntent.SetTextAutoResize(nodeId, it)) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// --- Constraints -------------------------------------------------------------

@Composable
private fun ConstraintsSection(state: MissionEditorStateHolder, node: DesignNode) {
    val nodeId = node.id
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        SelectField(
            value = node.constraints.horizontal.hLabel(),
            options = HorizontalConstraint.entries.map { it.hLabel() },
            onSelect = { label -> HorizontalConstraint.entries.firstOrNull { it.hLabel() == label }?.let { state.dispatch(DesignEditorIntent.UpdateConstraints(nodeId, horizontal = it)) } },
            modifier = Modifier.weight(1f),
        )
        SelectField(
            value = node.constraints.vertical.vLabel(),
            options = VerticalConstraint.entries.map { it.vLabel() },
            onSelect = { label -> VerticalConstraint.entries.firstOrNull { it.vLabel() == label }?.let { state.dispatch(DesignEditorIntent.UpdateConstraints(nodeId, vertical = it)) } },
            modifier = Modifier.weight(1f),
        )
    }
    Spacer(Modifier.height(8.dp))
    CheckRow("Fix position when scrolling", node.scroll.sticky) { state.dispatch(DesignEditorIntent.SetSticky(nodeId, it)) }
}

// --- Small shared bits -------------------------------------------------------

@Composable
private fun SectionHeaderAdd(label: String, onAdd: () -> Unit) {
    val colors = LocalEditorColors.current
    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = colors.mutedInk)
        TinyButton("+", onAdd)
    }
}

@Composable
private fun MutedNote(text: String) {
    val colors = LocalEditorColors.current
    Text(text, color = colors.mutedInk, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun LayerToggle(visible: Boolean, onClick: () -> Unit) {
    val colors = LocalEditorColors.current
    Box(Modifier.size(20.dp).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(if (visible) "O" else "-", color = colors.controlInk, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun RemoveButton(onClick: () -> Unit) {
    val colors = LocalEditorColors.current
    Box(Modifier.size(22.dp).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text("x", color = colors.statusDanger, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TinyButton(label: String, onClick: () -> Unit) {
    val colors = LocalEditorColors.current
    Surface(
        modifier = Modifier.height(24.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(5.dp),
        color = colors.raisedSurface,
        border = BorderStroke(1.dp, colors.softStroke),
    ) {
        Box(Modifier.padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = colors.ink)
        }
    }
}

@Composable
private fun SmallSelect(value: String, options: List<String>, modifier: Modifier = Modifier, onSelect: (String) -> Unit) {
    SelectField(value = value, options = options, onSelect = onSelect, modifier = modifier.width(96.dp))
}

// --- Color fields (wrap the shared color picker into the inspector's edit model) -----

/** Opaque copy of a color with its alpha byte replaced by a 0..1 fraction. */
private fun DesignColor.withAlphaFraction(fraction: Float): DesignColor {
    val a = (fraction.coerceIn(0f, 1f) * 255f).roundToInt().toLong()
    return DesignColor((a shl 24) or (argb and 0xFFFFFF))
}

/**
 * Color picker bound to a paint whose color and opacity are separate ops (fill / stroke).
 * The whole picker session coalesces into one undo entry and records the committed color
 * as a recent swatch.
 */
@Composable
private fun InspectorColorField(
    state: MissionEditorStateHolder,
    color: DesignColor,
    opacity: Double,
    label: String,
    enabled: Boolean,
    onColor: (DesignColor) -> Unit,
    onOpacity: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    var lastColor by remember { mutableStateOf(color) }
    ColorPickerField(
        rgb = color,
        alpha = opacity.toFloat(),
        label = label,
        recent = state.workspace.recentColors,
        enabled = enabled,
        onEditStart = { state.dispatch(DesignEditorIntent.BeginInteraction) },
        onEditEnd = {
            state.dispatch(DesignEditorIntent.EndInteraction)
            state.addRecentColor(lastColor)
        },
        onChange = { rgb, a ->
            lastColor = rgb
            onColor(rgb)
            onOpacity(a.toDouble())
        },
        modifier = modifier,
    )
}

/**
 * Color picker for a single ARGB color with no separate opacity op (gradient stops): the
 * alpha slider folds into the emitted color's alpha byte.
 */
@Composable
private fun InspectorArgbColorField(
    state: MissionEditorStateHolder,
    color: DesignColor,
    label: String,
    enabled: Boolean,
    onArgb: (DesignColor) -> Unit,
    modifier: Modifier = Modifier,
) {
    var lastOpaque by remember { mutableStateOf(color.withAlphaFraction(1f)) }
    ColorPickerField(
        rgb = color,
        alpha = color.alpha / 255f,
        label = label,
        recent = state.workspace.recentColors,
        enabled = enabled,
        onEditStart = { state.dispatch(DesignEditorIntent.BeginInteraction) },
        onEditEnd = {
            state.dispatch(DesignEditorIntent.EndInteraction)
            state.addRecentColor(lastOpaque)
        },
        onChange = { rgb, a ->
            lastOpaque = rgb
            onArgb(rgb.withAlphaFraction(a))
        },
        modifier = modifier,
    )
}

private fun findParentBox(root: LayoutBox, sourceId: String): LayoutBox? {
    if (root.children.any { it.node.sourceId == sourceId }) return root
    return root.children.firstNotNullOfOrNull { findParentBox(it, sourceId) }
}

private fun DesignPaint.displayColor(): DesignColor? = when (this) {
    is DesignPaint.Solid -> color.literalOrNull()
    is DesignPaint.Gradient -> stops.firstOrNull()?.color?.literalOrNull()
    else -> null
}

private fun DesignPaint.fillKind(): FillKind = when (this) {
    is DesignPaint.Solid -> FillKind.Solid
    is DesignPaint.Gradient -> if (gradientType == io.aequicor.visualization.engine.ir.model.GradientKind.Radial) FillKind.RadialGradient else FillKind.LinearGradient
    is DesignPaint.Image -> FillKind.Image
    else -> FillKind.Solid
}

private fun DesignEffect.effectLabel(): String = when (this) {
    is DesignEffect.DropShadow -> EffectType.DropShadow.displayName
    is DesignEffect.InnerShadow -> EffectType.InnerShadow.displayName
    is DesignEffect.LayerBlur -> EffectType.LayerBlur.displayName
    is DesignEffect.BackgroundBlur -> EffectType.BackgroundBlur.displayName
    is DesignEffect.Unknown -> "Effect"
}

private val BlendModes = listOf("normal", "multiply", "screen", "overlay", "darken", "lighten", "color-dodge", "color-burn", "difference")

private fun AlignItems.alignItemsLabel() = when (this) {
    AlignItems.Start -> "Start"; AlignItems.Center -> "Center"; AlignItems.End -> "End"; AlignItems.Baseline -> "Base"; AlignItems.Stretch -> "Fill"
}
private fun JustifyContent.justifyLabel() = when (this) {
    JustifyContent.Start -> "Start"; JustifyContent.Center -> "Center"; JustifyContent.End -> "End"; JustifyContent.SpaceBetween -> "Between"
}
private fun SizingMode.sizingLabel() = when (this) { SizingMode.Fixed -> "Fixed"; SizingMode.Hug -> "Hug"; SizingMode.Fill -> "Fill" }
private fun sizingFromLabel(label: String) = when (label) { "Hug" -> SizingMode.Hug; "Fill" -> SizingMode.Fill; else -> SizingMode.Fixed }
private fun StrokeAlign.strokeLabel() = when (this) { StrokeAlign.Inside -> "Inside"; StrokeAlign.Center -> "Center"; StrokeAlign.Outside -> "Outside" }
private fun TextAlignHorizontal.alignLabel() = when (this) { TextAlignHorizontal.Left -> "L"; TextAlignHorizontal.Center -> "C"; TextAlignHorizontal.Right -> "R"; TextAlignHorizontal.Justified -> "J" }
private fun TextAutoResize.autoResizeLabel() = when (this) { TextAutoResize.WidthAndHeight -> "Auto W"; TextAutoResize.Height -> "Auto H"; TextAutoResize.None -> "Fixed" }
private fun HorizontalConstraint.hLabel() = when (this) { HorizontalConstraint.Left -> "Left"; HorizontalConstraint.Right -> "Right"; HorizontalConstraint.Center -> "Center"; HorizontalConstraint.LeftRight -> "Left & Right"; HorizontalConstraint.Scale -> "Scale" }
private fun VerticalConstraint.vLabel() = when (this) { VerticalConstraint.Top -> "Top"; VerticalConstraint.Bottom -> "Bottom"; VerticalConstraint.Center -> "Center"; VerticalConstraint.TopBottom -> "Top & Bottom"; VerticalConstraint.Scale -> "Scale" }
