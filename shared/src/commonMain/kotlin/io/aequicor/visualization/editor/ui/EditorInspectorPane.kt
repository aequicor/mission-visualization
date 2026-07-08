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
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import io.aequicor.visualization.editor.presentation.StrokeOp
import io.aequicor.visualization.editor.presentation.TypographyPatch
import io.aequicor.visualization.editor.ui.theme.LocalEditorColors
import io.aequicor.visualization.engine.ir.layout.LayoutBox
import io.aequicor.visualization.engine.ir.model.DesignColor
import io.aequicor.visualization.engine.ir.model.DesignEffect
import io.aequicor.visualization.engine.ir.model.DesignNode
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.engine.ir.model.DesignPaint
import io.aequicor.visualization.engine.ir.model.HorizontalConstraint
import io.aequicor.visualization.engine.ir.model.LayoutMode
import io.aequicor.visualization.engine.ir.model.SizingMode
import io.aequicor.visualization.engine.ir.model.StrokeAlign
import io.aequicor.visualization.engine.ir.model.TextAlignHorizontal
import io.aequicor.visualization.engine.ir.model.VerticalConstraint
import io.aequicor.visualization.engine.ir.model.literalOrNull
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
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SelectionHeader(state, node)
        Section(state, InspectorSection.Position) { PositionSection(state, node, box) }
        Section(state, InspectorSection.Layout, visible = isFrame) { LayoutSection(state, node) }
        Section(state, InspectorSection.Appearance) { AppearanceSection(state, node, box) }
        Section(state, InspectorSection.Fill) { FillSection(state, node) }
        Section(state, InspectorSection.Stroke) { StrokeSection(state, node) }
        Section(state, InspectorSection.Effects) { EffectsSection(state, node) }
        Section(state, InspectorSection.Typography, visible = isText) { TypographySection(state, node) }
        Section(state, InspectorSection.Constraints) { ConstraintsSection(state, node) }
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
    val design = state.designState
    val ws = state.workspace
    val nodeId = node.id
    val document = design.document
    val positioned = document?.isCoordinatePositioned(nodeId) ?: false
    val parentBox = state.artboardLayout?.let { root -> findParentBox(root, nodeId) }
    val x = if (positioned) node.position?.x ?: 0.0 else (box?.x ?: 0.0) - (parentBox?.x ?: 0.0)
    val y = if (positioned) node.position?.y ?: 0.0 else (box?.y ?: 0.0) - (parentBox?.y ?: 0.0)
    val w = box?.width ?: node.size.width ?: 0.0
    val h = box?.height ?: node.size.height ?: 0.0

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        InspectorNumberField("X", x.formatPx(), "", nodeId, Modifier.weight(1f), enabled = positioned) {
            state.dispatch(DesignEditorIntent.UpdatePosition(nodeId, x = it))
        }
        InspectorNumberField("Y", y.formatPx(), "", nodeId, Modifier.weight(1f), enabled = positioned) {
            state.dispatch(DesignEditorIntent.UpdatePosition(nodeId, y = it))
        }
    }
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        InspectorCommitNumberField("W", w.formatPx(), "", nodeId, Modifier.weight(1f)) { value ->
            state.dispatch(DesignEditorIntent.ResizeNode(nodeId, width = value))
            if (ws.lockAspectRatio && h > 0) state.dispatch(DesignEditorIntent.ResizeNode(nodeId, height = value * h / w))
        }
        InspectorCommitNumberField("H", h.formatPx(), "", nodeId, Modifier.weight(1f)) { value ->
            state.dispatch(DesignEditorIntent.ResizeNode(nodeId, height = value))
            if (ws.lockAspectRatio && h > 0) state.dispatch(DesignEditorIntent.ResizeNode(nodeId, width = value * w / h))
        }
        SmallSquareButton(if (ws.lockAspectRatio) "L*" else "L", active = ws.lockAspectRatio, onClick = {
            state.updateWorkspace { it.copy(lockAspectRatio = !it.lockAspectRatio) }
        })
    }
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        InspectorNumberField("Rot", node.rotation.formatPx(), "°", nodeId, Modifier.weight(1f)) {
            state.dispatch(DesignEditorIntent.SetRotation(nodeId, it))
        }
        SmallSquareButton("|<>|", onClick = { state.dispatch(DesignEditorIntent.FlipHorizontal(design.selectedNodeIds)) })
        SmallSquareButton("flipV", onClick = { state.dispatch(DesignEditorIntent.FlipVertical(design.selectedNodeIds)) })
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
    }
}

// --- Appearance --------------------------------------------------------------

@Composable
private fun AppearanceSection(state: MissionEditorStateHolder, node: DesignNode, box: LayoutBox?) {
    val colors = LocalEditorColors.current
    val nodeId = node.id
    val opacity = (node.opacity.literalOrNull() ?: 1.0)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Opacity", style = MaterialTheme.typography.bodySmall, color = colors.controlInk)
        Text("${(opacity * 100).roundToInt()}%", style = MaterialTheme.typography.bodySmall)
        Slider(
            value = (opacity * 100).toFloat(),
            onValueChange = { state.dispatch(DesignEditorIntent.UpdateOpacity(nodeId, it / 100.0)) },
            valueRange = 0f..100f,
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
    val fills = node.fills.orEmpty()
    SectionHeaderAdd("Fills") { state.dispatch(DesignEditorIntent.FillCommand(nodeId, FillOp.Add)) }
    if (fills.isEmpty()) {
        MutedNote("No fills. Add one with +.")
        return
    }
    fills.forEachIndexed { index, paint ->
        FillRow(state, nodeId, index, paint)
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun FillRow(state: MissionEditorStateHolder, nodeId: String, index: Int, paint: DesignPaint) {
    val kind = paint.fillKind()
    val visible = paint.visible.literalOrNull() ?: true
    val color = paint.displayColor()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        LayerToggle(visible) { state.dispatch(DesignEditorIntent.FillCommand(nodeId, FillOp.ToggleAt(index))) }
        Box(Modifier.weight(1f)) {
            SwatchField(
                color = color?.toComposeColor() ?: Color.Gray,
                value = color?.toHex() ?: kind.displayName,
                rightValue = "${((paint.opacity.literalOrNull() ?: 1.0) * 100).roundToInt()}%",
                resetKey = "$nodeId-fill-$index",
                onCommitHex = { hex -> DesignColor.fromHex(hex)?.let { state.dispatch(DesignEditorIntent.FillCommand(nodeId, FillOp.SetColor(index, it))) } },
            )
        }
        SmallSelect(kind.displayName, FillKind.entries.map { it.displayName }) { label ->
            FillKind.entries.firstOrNull { it.displayName == label }?.let { state.dispatch(DesignEditorIntent.FillCommand(nodeId, FillOp.SetType(index, it))) }
        }
        RemoveButton { state.dispatch(DesignEditorIntent.FillCommand(nodeId, FillOp.RemoveAt(index))) }
    }
    if (paint is DesignPaint.Gradient) {
        GradientStops(state, nodeId, index, paint)
    }
}

@Composable
private fun GradientStops(state: MissionEditorStateHolder, nodeId: String, index: Int, gradient: DesignPaint.Gradient) {
    val colors = LocalEditorColors.current
    Column(Modifier.padding(start = 26.dp, top = 4.dp)) {
        gradient.stops.forEachIndexed { stopIndex, stop ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.weight(1f)) {
                    SwatchField(
                        color = stop.color.literalOrNull()?.toComposeColor() ?: Color.Gray,
                        value = stop.color.literalOrNull()?.toHex() ?: "#000000",
                        rightValue = "${(stop.position * 100).roundToInt()}%",
                        resetKey = "$nodeId-fill-$index-stop-$stopIndex",
                        onCommitHex = { hex -> DesignColor.fromHex(hex)?.let { state.dispatch(DesignEditorIntent.FillCommand(nodeId, FillOp.SetGradientStopColor(index, stopIndex, it))) } },
                    )
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

// --- Stroke ------------------------------------------------------------------

@Composable
private fun StrokeSection(state: MissionEditorStateHolder, node: DesignNode) {
    val nodeId = node.id
    val strokes = node.strokes
    SectionHeaderAdd("Stroke") {
        if (strokes == null) state.dispatch(DesignEditorIntent.StrokeCommand(nodeId, StrokeOp.Add))
    }
    if (strokes == null) {
        MutedNote("No stroke. Add one with +.")
        return
    }
    val paint = strokes.paints.firstOrNull()
    val color = paint?.displayColor()
    val visible = paint?.visible?.literalOrNull() ?: true
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        LayerToggle(visible) { state.dispatch(DesignEditorIntent.StrokeCommand(nodeId, StrokeOp.SetVisible(!visible))) }
        Box(Modifier.weight(1f)) {
            SwatchField(
                color = color?.toComposeColor() ?: Color.Gray,
                value = color?.toHex() ?: "#1E88FF",
                rightValue = "${((paint?.opacity?.literalOrNull() ?: 1.0) * 100).roundToInt()}%",
                resetKey = "$nodeId-stroke",
                onCommitHex = { hex -> DesignColor.fromHex(hex)?.let { state.dispatch(DesignEditorIntent.StrokeCommand(nodeId, StrokeOp.SetColor(it))) } },
            )
        }
        RemoveButton { state.dispatch(DesignEditorIntent.StrokeCommand(nodeId, StrokeOp.Remove)) }
    }
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        InspectorNumberField("W", (strokes.weight.literalOrNull() ?: 1.0).formatPx(), "", nodeId, Modifier.weight(1f)) {
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
        InspectorNumberField("Ltr", letter.formatPx(), "", nodeId, Modifier.weight(1f)) { state.dispatch(DesignEditorIntent.UpdateTypography(nodeId, TypographyPatch(letterSpacing = it))) }
    }
    Spacer(Modifier.height(8.dp))
    SegmentedControl(
        options = listOf(TextAlignHorizontal.Left, TextAlignHorizontal.Center, TextAlignHorizontal.Right, TextAlignHorizontal.Justified),
        selected = style?.textAlignHorizontal ?: TextAlignHorizontal.Left,
        label = { it.alignLabel() },
        onSelect = { state.dispatch(DesignEditorIntent.UpdateTypography(nodeId, TypographyPatch(alignHorizontal = it))) },
        modifier = Modifier.fillMaxWidth(),
    )
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

private fun SizingMode.sizingLabel() = when (this) { SizingMode.Fixed -> "Fixed"; SizingMode.Hug -> "Hug"; SizingMode.Fill -> "Fill" }
private fun sizingFromLabel(label: String) = when (label) { "Hug" -> SizingMode.Hug; "Fill" -> SizingMode.Fill; else -> SizingMode.Fixed }
private fun StrokeAlign.strokeLabel() = when (this) { StrokeAlign.Inside -> "Inside"; StrokeAlign.Center -> "Center"; StrokeAlign.Outside -> "Outside" }
private fun TextAlignHorizontal.alignLabel() = when (this) { TextAlignHorizontal.Left -> "L"; TextAlignHorizontal.Center -> "C"; TextAlignHorizontal.Right -> "R"; TextAlignHorizontal.Justified -> "J" }
private fun HorizontalConstraint.hLabel() = when (this) { HorizontalConstraint.Left -> "Left"; HorizontalConstraint.Right -> "Right"; HorizontalConstraint.Center -> "Center"; HorizontalConstraint.LeftRight -> "Left & Right"; HorizontalConstraint.Scale -> "Scale" }
private fun VerticalConstraint.vLabel() = when (this) { VerticalConstraint.Top -> "Top"; VerticalConstraint.Bottom -> "Bottom"; VerticalConstraint.Center -> "Center"; VerticalConstraint.TopBottom -> "Top & Bottom"; VerticalConstraint.Scale -> "Scale" }
