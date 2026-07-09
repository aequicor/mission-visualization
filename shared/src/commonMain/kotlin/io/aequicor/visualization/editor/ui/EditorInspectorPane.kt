package io.aequicor.visualization.editor.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.aequicor.visualization.MissionEditorStateHolder
import io.aequicor.visualization.editor.presentation.CompactLabel
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
import io.aequicor.visualization.editor.presentation.normalizeAngleDegrees
import io.aequicor.visualization.editor.presentation.StrokeOp
import io.aequicor.visualization.editor.presentation.TypographyPatch
import io.aequicor.visualization.editor.ui.theme.LocalEditorColors
import io.aequicor.visualization.engine.ir.layout.LayoutBox
import io.aequicor.visualization.engine.ir.model.AlignItems
import io.aequicor.visualization.engine.ir.model.Bindable
import io.aequicor.visualization.engine.ir.model.BooleanOperationKind
import io.aequicor.visualization.engine.ir.model.DesignColor
import io.aequicor.visualization.engine.ir.model.DesignDocument
import io.aequicor.visualization.engine.ir.model.DesignEffect
import io.aequicor.visualization.engine.ir.model.DesignNode
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.engine.ir.model.DesignPaint
import io.aequicor.visualization.engine.ir.model.DesignViewBox
import io.aequicor.visualization.engine.ir.model.HorizontalConstraint
import io.aequicor.visualization.engine.ir.model.JustifyContent
import io.aequicor.visualization.engine.ir.model.LayoutMode
import io.aequicor.visualization.engine.ir.model.ShapeType
import io.aequicor.visualization.engine.ir.model.SizingMode
import io.aequicor.visualization.engine.ir.model.StrokeAlign
import io.aequicor.visualization.engine.ir.model.TextAlignHorizontal
import io.aequicor.visualization.engine.ir.model.TextAutoResize
import io.aequicor.visualization.engine.ir.model.VariableValue
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
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(8.dp),
            color = Color.White,
            shadowElevation = 0.dp,
        ) {
            Column(Modifier.fillMaxSize()) {
                TabStrip(
                    tabs = InspectorTab.entries,
                    selected = state.workspace.inspectorTab,
                    title = { it.label },
                    icon = ::inspectorTabIcon,
                    onSelect = { tab -> state.updateWorkspace { it.copy(inspectorTab = tab) } },
                )
                when (state.workspace.inspectorTab) {
                    InspectorTab.Design -> InspectorDesign(state)
                    InspectorTab.Prototype -> InspectorPrototype(state)
                    InspectorTab.Comments -> EmptyInspector("No comments yet.")
                }
            }
        }
    }
}

@Composable
internal fun EmptyInspector(text: String) {
    val colors = LocalEditorColors.current
    Box(Modifier.fillMaxSize().padding(16.dp)) {
        Text(text, color = colors.mutedInk, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun InspectorDesign(state: MissionEditorStateHolder) {
    val design = state.designState
    val node = design.selectedNode
    if (node == null) {
        EmptyInspector("Nothing selected — select an object on the canvas or in Layers.")
        return
    }
    val box = state.artboardLayout?.findBySourceId(design.selectedNodeId)
    val isText = node.kind is DesignNodeKind.Text
    val isFrame = node.kind is DesignNodeKind.Frame
    val isShapeLike = node.kind is DesignNodeKind.Shape || node.kind is DesignNodeKind.BooleanOperation
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SelectionHeader(state, node)
        Section(state, InspectorSection.Position) { PositionSection(state, node, box) }
        Section(state, InspectorSection.Layout) { LayoutSection(state, node, box, isFrame) }
        Section(state, InspectorSection.Appearance) { AppearanceSection(state, node, box) }
        Section(state, InspectorSection.Fill) { FillSection(state, node) }
        Section(state, InspectorSection.Stroke) { StrokeSection(state, node) }
        // The Shape/Vector section owns its own collapsible chrome because InspectorSection
        // (source of truth for persisted expand state) lives in editor.presentation, which is
        // outside this file's ownership; it uses local visual expand state instead.
        ShapeSection(state, node, visible = isShapeLike)
        Section(state, InspectorSection.Effects) { EffectsSection(state, node) }
        Section(state, InspectorSection.Typography, visible = isText) { TypographySection(state, node) }
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
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
        if (!design.hasMultiSelection && node.locked) {
            Text("Locked", style = MaterialTheme.typography.labelSmall, color = colors.statusWarning, fontWeight = FontWeight.SemiBold)
        }
        SmallIconButton(EditorIcon.Duplicate, contentDescription = "Duplicate selection", onClick = { state.dispatch(DesignEditorIntent.DuplicateNodes(design.selectedNodeIds)) })
        SmallIconButton(EditorIcon.Trash, contentDescription = "Delete selection", onClick = { state.dispatch(DesignEditorIntent.DeleteNodes(design.selectedNodeIds)) })
    }
}

@Composable
internal fun Section(
    state: MissionEditorStateHolder,
    section: InspectorSection,
    visible: Boolean = true,
    content: @Composable () -> Unit,
) {
    if (!visible) return
    val colors = LocalEditorColors.current
    val expanded = section in state.workspace.expandedSections
    val headerInteraction = remember { MutableInteractionSource() }
    Column(Modifier.fillMaxWidth().border(BorderStroke(0.5.dp, colors.softStroke)).padding(horizontal = 18.dp, vertical = 12.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = headerInteraction,
                    indication = null,
                ) { state.toggleSection(section) },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            EditorSvgIcon(
                icon = inspectorSectionIcon(section),
                contentDescription = section.title,
                modifier = Modifier.size(18.dp),
                tint = colors.mutedInk,
            )
            CompactText(
                label = section.label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            EditorSvgIcon(
                icon = if (expanded) EditorIcon.ChevronUp else EditorIcon.ChevronDown,
                contentDescription = if (expanded) "Collapse section" else "Expand section",
                modifier = Modifier.size(14.dp),
                tint = colors.controlInk,
            )
        }
        if (expanded) {
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

// --- Position / layout geometry ---------------------------------------------

@Composable
private fun PositionSection(state: MissionEditorStateHolder, node: DesignNode, box: LayoutBox?) {
    if (state.designState.hasMultiSelection) {
        MultiPositionSection(state)
        return
    }
    val design = state.designState
    val nodeId = node.id
    val document = design.document
    val positioned = document?.isCoordinatePositioned(nodeId) ?: false
    val locked = design.isNodeLocked(nodeId)
    val parentBox = state.artboardLayout?.let { root -> findParentBox(root, nodeId) }
    val x = if (positioned) node.position?.x ?: 0.0 else (box?.x ?: 0.0) - (parentBox?.x ?: 0.0)
    val y = if (positioned) node.position?.y ?: 0.0 else (box?.y ?: 0.0) - (parentBox?.y ?: 0.0)

    InspectorSubLabel("Alignment")
    AlignmentControls(state, enabled = positioned && !locked && state.artboardLayout != null)
    Spacer(Modifier.height(8.dp))

    InspectorSubLabel("Position")
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val fieldWidth = inspectorPairFieldWidth(maxWidth)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CompactNumberField("X", x.formatPx(), "x-$nodeId", Modifier.width(fieldWidth), enabled = positioned && !locked) {
                state.dispatch(DesignEditorIntent.PositionNode(nodeId, x = it, y = y))
            }
            CompactNumberField("Y", y.formatPx(), "y-$nodeId", Modifier.width(fieldWidth), enabled = positioned && !locked) {
                state.dispatch(DesignEditorIntent.PositionNode(nodeId, x = x, y = it))
            }
        }
    }

    if (!positioned) {
        Spacer(Modifier.height(8.dp))
        MutedNote("Auto layout child — position follows the flow. Drag on canvas to reorder.")
        Spacer(Modifier.height(6.dp))
        TinyButton("Absolute position inside frame", enabled = !locked) {
            state.dispatch(DesignEditorIntent.SetAbsolutePosition(nodeId, x = x, y = y))
        }
    }

    if (positioned) {
        Spacer(Modifier.height(8.dp))
        InspectorSubLabel("Constraints")
        ConstraintsControls(
            horizontal = node.constraints.horizontal,
            vertical = node.constraints.vertical,
            enabled = !locked,
            onHorizontal = { state.dispatch(DesignEditorIntent.UpdateConstraints(nodeId, horizontal = it)) },
            onVertical = { state.dispatch(DesignEditorIntent.UpdateConstraints(nodeId, vertical = it)) },
        )
    }

    Spacer(Modifier.height(8.dp))
    InspectorSubLabel("Rotation")
    RotationControls(state, value = node.rotation.formatPx(), resetKey = "rot-$nodeId", enabled = !locked)
}

@Composable
private fun MultiPositionSection(state: MissionEditorStateHolder) {
    val design = state.designState
    val document = design.document
    val ids = selectedIds(state)
    val nodes = design.selectedNodes
    val key = ids.sorted().joinToString(",")
    val canPosition = document != null && ids.any { id -> !design.isNodeLocked(id) && document.isCoordinatePositioned(id) }

    fun sharedDouble(selector: (DesignNode) -> Double?): Double? {
        val values = nodes.map(selector)
        val first = values.firstOrNull() ?: return null
        return if (values.all { it == first }) first else null
    }

    fun sharedHorizontal(): HorizontalConstraint? {
        val values = nodes.map { it.constraints.horizontal }
        val first = values.firstOrNull() ?: return null
        return if (values.all { it == first }) first else null
    }

    fun sharedVertical(): VerticalConstraint? {
        val values = nodes.map { it.constraints.vertical }
        val first = values.firstOrNull() ?: return null
        return if (values.all { it == first }) first else null
    }

    InspectorSubLabel("Alignment")
    AlignmentControls(state, enabled = state.artboardLayout != null && ids.isNotEmpty())
    Spacer(Modifier.height(8.dp))

    val x = sharedDouble { it.position?.x }
    val y = sharedDouble { it.position?.y }
    InspectorSubLabel("Position")
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val fieldWidth = inspectorPairFieldWidth(maxWidth)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CompactNumberField("X", x?.formatPx() ?: "", "mx-$key", Modifier.width(fieldWidth), enabled = canPosition, placeholder = "Mixed") { v ->
                bulkPosition(state, x = v)
            }
            CompactNumberField("Y", y?.formatPx() ?: "", "my-$key", Modifier.width(fieldWidth), enabled = canPosition, placeholder = "Mixed") { v ->
                bulkPosition(state, y = v)
            }
        }
    }

    if (document != null && ids.any { document.isCoordinatePositioned(it) }) {
        Spacer(Modifier.height(8.dp))
        InspectorSubLabel("Constraints")
        ConstraintsControls(
            horizontal = sharedHorizontal(),
            vertical = sharedVertical(),
            enabled = canPosition,
            onHorizontal = { applyConstraintsToSelection(state, horizontal = it) },
            onVertical = { applyConstraintsToSelection(state, vertical = it) },
        )
    }

    val rotation = sharedDouble { it.rotation }
    Spacer(Modifier.height(8.dp))
    InspectorSubLabel("Rotation")
    RotationControls(
        state = state,
        value = rotation?.formatPx() ?: "",
        resetKey = "mrot-$key",
        enabled = ids.any { !design.isNodeLocked(it) },
        placeholder = "Mixed",
    )
    Spacer(Modifier.height(8.dp))
    MutedNote("${ids.size} layers selected; locked layers are skipped.")
}

// --- Layout ------------------------------------------------------------------

@Composable
private fun LayoutSection(state: MissionEditorStateHolder, node: DesignNode, box: LayoutBox?, isFrame: Boolean) {
    DimensionsBlock(state, node, box)
    if (state.designState.hasMultiSelection) return

    Spacer(Modifier.height(10.dp))
    SizingControls(state, node)
    if (!isFrame) {
        // A component instance can't expose Auto layout (the resolver ignores an instance's own
        // layout). Detaching bakes it into an editable Frame, after which these controls apply.
        if (node.kind is DesignNodeKind.Instance) {
            Spacer(Modifier.height(12.dp))
            MutedNote("Component instance — detach to edit its layout.")
            Spacer(Modifier.height(6.dp))
            TinyButton("Detach instance", enabled = !state.designState.isNodeLocked(node.id)) {
                state.dispatch(DesignEditorIntent.DetachInstance(node.id))
            }
        }
        return
    }

    Spacer(Modifier.height(12.dp))
    val nodeId = node.id
    val current = when (node.layout.mode) {
        LayoutMode.Horizontal -> EditorLayoutMode.Horizontal
        LayoutMode.Vertical -> EditorLayoutMode.Vertical
        LayoutMode.Grid -> EditorLayoutMode.Grid
        LayoutMode.None -> EditorLayoutMode.Free
    }
    InspectorSubLabel("Auto layout")
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
        CompactLabeledNumberField("Gap", gap.formatPx(), "gap-$nodeId") {
            state.dispatch(DesignEditorIntent.SetLayoutGap(nodeId, it))
        }
        Spacer(Modifier.height(8.dp))
        InspectorSubLabel("Padding")
        val pad = node.layout.padding
        PaddingControls(
            top = (pad.top.literalOrNull() ?: 0.0).formatPx(),
            right = (pad.right.literalOrNull() ?: 0.0).formatPx(),
            bottom = (pad.bottom.literalOrNull() ?: 0.0).formatPx(),
            left = (pad.left.literalOrNull() ?: 0.0).formatPx(),
            resetKey = nodeId,
            onChange = { side, value -> state.dispatch(DesignEditorIntent.SetLayoutPadding(nodeId, side, value)) },
        )
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
    Spacer(Modifier.height(8.dp))
    CheckRow("Clip content", node.layout.clipsContent) { state.dispatch(DesignEditorIntent.SetClipsContent(nodeId, it)) }
}

@Composable
private fun DimensionsBlock(state: MissionEditorStateHolder, node: DesignNode, box: LayoutBox?) {
    if (state.designState.hasMultiSelection) {
        MultiDimensionsBlock(state)
        return
    }
    val nodeId = node.id
    val locked = state.designState.isNodeLocked(nodeId)
    val ws = state.workspace
    val width = box?.width ?: node.size.width ?: 0.0
    val height = box?.height ?: node.size.height ?: 0.0

    InspectorSubLabel("Dimensions")
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val fieldWidth = inspectorPairFieldWidth(maxWidth, reserved = 50.dp, minWidth = 58.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            CompactNumberField("W", width.formatPx(), "w-$nodeId", Modifier.width(fieldWidth), enabled = !locked) { value ->
                val nextHeight = if (ws.lockAspectRatio && width > 0.0) value * height / width else null
                state.dispatch(DesignEditorIntent.ResizeNode(nodeId, width = value, height = nextHeight))
            }
            CompactNumberField("H", height.formatPx(), "h-$nodeId", Modifier.width(fieldWidth), enabled = !locked) { value ->
                val nextWidth = if (ws.lockAspectRatio && height > 0.0) value * width / height else null
                state.dispatch(DesignEditorIntent.ResizeNode(nodeId, width = nextWidth, height = value))
            }
            SmallIconButton(
                icon = EditorIcon.AspectRatio,
                contentDescription = "Lock aspect ratio",
                active = ws.lockAspectRatio,
                onClick = { state.updateWorkspace { it.copy(lockAspectRatio = !it.lockAspectRatio) } },
            )
        }
    }
}

@Composable
private fun MultiDimensionsBlock(state: MissionEditorStateHolder) {
    val design = state.designState
    val ids = selectedIds(state)
    val nodes = design.selectedNodes
    val key = ids.sorted().joinToString(",")
    val ws = state.workspace
    val canResize = ids.any { !design.isNodeLocked(it) }

    fun shared(selector: (DesignNode) -> Double?): Double? {
        val values = nodes.map(selector)
        val first = values.firstOrNull() ?: return null
        return if (values.all { it == first }) first else null
    }

    InspectorSubLabel("Dimensions")
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val fieldWidth = inspectorPairFieldWidth(maxWidth, reserved = 50.dp, minWidth = 58.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            CompactNumberField("W", shared { it.size.width }?.formatPx() ?: "", "mw-$key", Modifier.width(fieldWidth), enabled = canResize, placeholder = "Mixed") { value ->
                bulkResize(state, width = value, keepRatio = ws.lockAspectRatio)
            }
            CompactNumberField("H", shared { it.size.height }?.formatPx() ?: "", "mh-$key", Modifier.width(fieldWidth), enabled = canResize, placeholder = "Mixed") { value ->
                bulkResize(state, height = value, keepRatio = ws.lockAspectRatio)
            }
            SmallIconButton(
                icon = EditorIcon.AspectRatio,
                contentDescription = "Lock aspect ratio",
                active = ws.lockAspectRatio,
                onClick = { state.updateWorkspace { it.copy(lockAspectRatio = !it.lockAspectRatio) } },
            )
        }
    }
}

@Composable
private fun SizingControls(state: MissionEditorStateHolder, node: DesignNode) {
    val nodeId = node.id
    InspectorSubLabel("Resizing")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CompactSelectField(
            value = (node.sizing?.horizontal ?: SizingMode.Fixed).sizingLabel(),
            options = SizingMode.entries.map { it.sizingLabel() },
            onSelect = { state.dispatch(DesignEditorIntent.UpdateSizingMode(nodeId, horizontal = sizingFromLabel(it))) },
            modifier = Modifier.weight(1f),
        )
        CompactSelectField(
            value = (node.sizing?.vertical ?: SizingMode.Fixed).sizingLabel(),
            options = SizingMode.entries.map { it.sizingLabel() },
            onSelect = { state.dispatch(DesignEditorIntent.UpdateSizingMode(nodeId, vertical = sizingFromLabel(it))) },
            modifier = Modifier.weight(1f),
        )
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
    CompactLabeledSelectField(
        label = "Blend",
        value = node.blendMode.ifBlank { "normal" },
        options = BlendModes,
        maxFieldWidth = 220.dp,
    ) {
        state.dispatch(DesignEditorIntent.SetBlendMode(nodeId, it))
    }
    Spacer(Modifier.height(8.dp))
    val radius = node.cornerRadius?.topLeft?.literalOrNull() ?: 0.0
    CompactLabeledNumberField("Radius", radius.formatPx(), "radius-$nodeId") {
        state.dispatch(DesignEditorIntent.UpdateCornerRadius(nodeId, it))
    }
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
                    val color = paint.color.resolveDisplayColor(state.designState.document) ?: DesignColor.Black
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
                is DesignPaint.Gradient -> GradientPreview(state, paint)
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
private fun GradientPreview(state: MissionEditorStateHolder, gradient: DesignPaint.Gradient) {
    val colors = LocalEditorColors.current
    val document = state.designState.document
    val stops = gradient.stops.sortedBy { it.position }.mapNotNull { it.color.resolveDisplayColor(document)?.toComposeColor() }
    val brush = when {
        stops.size >= 2 -> Brush.horizontalGradient(stops)
        stops.size == 1 -> Brush.horizontalGradient(listOf(stops.first(), stops.first()))
        else -> Brush.horizontalGradient(listOf(Color.Gray, Color.Gray))
    }
    Surface(
        modifier = Modifier.fillMaxWidth().height(36.dp),
        shape = RoundedCornerShape(6.dp),
        color = colors.controlSurface,
        border = BorderStroke(1.dp, colors.controlStroke),
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
        color = colors.controlSurface,
        border = BorderStroke(1.dp, colors.controlStroke),
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
            val stopColor = stop.color.resolveDisplayColor(state.designState.document) ?: DesignColor.Black
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
    val color = paint?.displayColor(state.designState.document) ?: DesignColor.fromHex("#1E88FF") ?: DesignColor.Black
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

// --- Shape / vector ----------------------------------------------------------

private val ShapeSectionLabel = CompactLabel("Shape", "Shape", "Shp")

/**
 * Geometry controls for parametric shapes, editable vectors and boolean groups. Owns its own
 * collapsible chrome (see [StandaloneSection]) because [InspectorSection] — which persists
 * expand state — is out of this file's edit scope. Every control is disabled while the node is
 * locked and dispatches the fixed Vector intents; point/handle drags are the canvas' job.
 */
@Composable
private fun ShapeSection(state: MissionEditorStateHolder, node: DesignNode, visible: Boolean) {
    if (!visible) return
    StandaloneSection(icon = EditorIcon.Pen, label = ShapeSectionLabel) {
        val nodeId = node.id
        val locked = state.designState.isNodeLocked(nodeId)
        when (val kind = node.kind) {
            is DesignNodeKind.Shape -> ShapeControls(state, nodeId, kind, locked)
            is DesignNodeKind.BooleanOperation -> BooleanOpControls(state, nodeId, kind, locked)
            else -> Unit
        }
    }
}

@Composable
private fun ShapeControls(state: MissionEditorStateHolder, nodeId: String, shape: DesignNodeKind.Shape, locked: Boolean) {
    LabeledField("Type") {
        SelectField(
            value = shape.shape.shapeTypeLabel(),
            options = ShapeType.entries.map { it.shapeTypeLabel() },
            onSelect = { label ->
                if (!locked) {
                    ShapeType.entries.firstOrNull { it.shapeTypeLabel() == label }
                        ?.let { state.dispatch(DesignEditorIntent.SetShapeType(nodeId, it)) }
                }
            },
        )
    }
    if (shape.shape == ShapeType.Polygon || shape.shape == ShapeType.Star) {
        Spacer(Modifier.height(8.dp))
        val sides = (shape.pointCount ?: 3).coerceAtLeast(3)
        CompactLabeledNumberField("Sides", sides.toDouble().formatPx(), "sides-$nodeId", enabled = !locked) {
            state.dispatch(DesignEditorIntent.SetPointCount(nodeId, it.roundToInt().coerceAtLeast(3)))
        }
    }
    if (shape.shape == ShapeType.Star) {
        Spacer(Modifier.height(8.dp))
        StarInnerRadiusControl(state, nodeId, shape.innerRadius, locked)
    }
    if (shape.shape == ShapeType.Vector) {
        Spacer(Modifier.height(8.dp))
        VectorControls(state, nodeId, shape, locked)
    }
}

@Composable
private fun StarInnerRadiusControl(state: MissionEditorStateHolder, nodeId: String, innerRadius: Double?, locked: Boolean) {
    val colors = LocalEditorColors.current
    val ratio = (innerRadius ?: 0.5).coerceIn(0.0, 1.0)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Inner", style = MaterialTheme.typography.bodySmall, color = colors.controlInk)
        Text("${(ratio * 100).roundToInt()}%", style = MaterialTheme.typography.bodySmall)
        // Whole drag coalesces into one undo entry, matching the opacity slider idiom.
        UndoableSlider(
            value = (ratio * 100).toFloat(),
            valueRange = 0f..100f,
            enabled = !locked,
            onBegin = { state.dispatch(DesignEditorIntent.BeginInteraction) },
            onChange = { v -> state.dispatch(DesignEditorIntent.SetStarInnerRadius(nodeId, v / 100.0)) },
            onEnd = { state.dispatch(DesignEditorIntent.EndInteraction) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun VectorControls(state: MissionEditorStateHolder, nodeId: String, shape: DesignNodeKind.Shape, locked: Boolean) {
    CompactLabeledTextField("Icon", shape.iconRef, "icon-$nodeId", enabled = !locked, placeholder = "ds/Icon/…") {
        state.dispatch(DesignEditorIntent.SetIconRef(nodeId, it))
    }
    Spacer(Modifier.height(8.dp))
    CompactLabeledTextField("Path", shape.pathRef, "path-$nodeId", enabled = !locked, placeholder = "asset id") {
        state.dispatch(DesignEditorIntent.SetPathRef(nodeId, it))
    }
    Spacer(Modifier.height(8.dp))
    InspectorSubLabel("View box")
    val vb = shape.viewBox ?: DesignViewBox()
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val fieldWidth = inspectorPairFieldWidth(maxWidth)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CompactNumberField("X", vb.x.formatPx(), "vbx-$nodeId", Modifier.width(fieldWidth), enabled = !locked) {
                    state.dispatch(DesignEditorIntent.SetVectorViewBox(nodeId, vb.copy(x = it)))
                }
                CompactNumberField("Y", vb.y.formatPx(), "vby-$nodeId", Modifier.width(fieldWidth), enabled = !locked) {
                    state.dispatch(DesignEditorIntent.SetVectorViewBox(nodeId, vb.copy(y = it)))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CompactNumberField("W", vb.width.formatPx(), "vbw-$nodeId", Modifier.width(fieldWidth), enabled = !locked) {
                    state.dispatch(DesignEditorIntent.SetVectorViewBox(nodeId, vb.copy(width = it)))
                }
                CompactNumberField("H", vb.height.formatPx(), "vbh-$nodeId", Modifier.width(fieldWidth), enabled = !locked) {
                    state.dispatch(DesignEditorIntent.SetVectorViewBox(nodeId, vb.copy(height = it)))
                }
            }
        }
    }
    if (shape.network?.isNotEmpty() != true) {
        Spacer(Modifier.height(8.dp))
        MutedNote("No editable geometry yet — convert to edit points on the canvas.")
        Spacer(Modifier.height(6.dp))
        TinyButton("Convert to editable", enabled = !locked) {
            state.dispatch(DesignEditorIntent.ConvertToEditableVector(nodeId))
        }
    }
}

@Composable
private fun BooleanOpControls(state: MissionEditorStateHolder, nodeId: String, kind: DesignNodeKind.BooleanOperation, locked: Boolean) {
    LabeledField("Operation") {
        SelectField(
            value = kind.operation.booleanOpLabel(),
            options = BooleanOperationKind.entries.map { it.booleanOpLabel() },
            onSelect = { label ->
                if (!locked) {
                    BooleanOperationKind.entries.firstOrNull { it.booleanOpLabel() == label }
                        ?.let { state.dispatch(DesignEditorIntent.SetBooleanOperation(nodeId, it)) }
                }
            },
        )
    }
}

/** Collapsible section shell mirroring [Section] but with local visual expand state. */
@Composable
private fun StandaloneSection(icon: EditorIcon, label: CompactLabel, content: @Composable () -> Unit) {
    val colors = LocalEditorColors.current
    var expanded by remember { mutableStateOf(true) }
    val headerInteraction = remember { MutableInteractionSource() }
    Column(Modifier.fillMaxWidth().border(BorderStroke(0.5.dp, colors.softStroke)).padding(horizontal = 18.dp, vertical = 12.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = headerInteraction,
                    indication = null,
                ) { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            EditorSvgIcon(
                icon = icon,
                contentDescription = label.full,
                modifier = Modifier.size(18.dp),
                tint = colors.mutedInk,
            )
            CompactText(
                label = label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            EditorSvgIcon(
                icon = if (expanded) EditorIcon.ChevronUp else EditorIcon.ChevronDown,
                contentDescription = if (expanded) "Collapse section" else "Expand section",
                modifier = Modifier.size(14.dp),
                tint = colors.controlInk,
            )
        }
        if (expanded) {
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

private fun ShapeType.shapeTypeLabel() = when (this) {
    ShapeType.Rectangle -> "Rectangle"
    ShapeType.Ellipse -> "Ellipse"
    ShapeType.Polygon -> "Polygon"
    ShapeType.Star -> "Star"
    ShapeType.Line -> "Line"
    ShapeType.Arrow -> "Arrow"
    ShapeType.Vector -> "Vector"
}

private fun BooleanOperationKind.booleanOpLabel() = when (this) {
    BooleanOperationKind.Union -> "Union"
    BooleanOperationKind.Subtract -> "Subtract"
    BooleanOperationKind.Intersect -> "Intersect"
    BooleanOperationKind.Exclude -> "Exclude"
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
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val fieldWidth = inspectorPairFieldWidth(maxWidth, minWidth = 88.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CompactNumberField("Size", size.formatPx(), "type-size-$nodeId", Modifier.width(fieldWidth)) {
                state.dispatch(DesignEditorIntent.UpdateTypography(nodeId, TypographyPatch(fontSize = it)))
            }
            CompactNumberField("Wt", weight.formatPx(), "type-weight-$nodeId", Modifier.width(fieldWidth)) {
                state.dispatch(DesignEditorIntent.UpdateTypography(nodeId, TypographyPatch(fontWeight = it)))
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val fieldWidth = inspectorPairFieldWidth(maxWidth, minWidth = 88.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CompactNumberField("Line", lineHeight.formatPx(), "type-line-$nodeId", Modifier.width(fieldWidth), suffix = "%") {
                state.dispatch(DesignEditorIntent.UpdateTypography(nodeId, TypographyPatch(lineHeightPercent = it)))
            }
            CompactNumberField("Ltr", letter.formatPx(), "type-letter-$nodeId", Modifier.width(fieldWidth), suffix = "px") {
                state.dispatch(DesignEditorIntent.UpdateTypography(nodeId, TypographyPatch(letterSpacing = it)))
            }
        }
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

// --- Figma-like inspector controls ------------------------------------------

@Composable
private fun InspectorSubLabel(text: String) {
    val colors = LocalEditorColors.current
    Text(
        text,
        modifier = Modifier.padding(bottom = 4.dp),
        style = MaterialTheme.typography.labelSmall,
        color = colors.mutedInk,
    )
}

private val InspectorCompactFieldMaxWidth = 132.dp
private val InspectorCompactSelectMaxWidth = 220.dp
private val InspectorPaddingTwoColumnWidth = 280.dp

private fun inspectorPairFieldWidth(maxWidth: Dp, reserved: Dp = 0.dp, minWidth: Dp = 72.dp): Dp {
    val candidate = (maxWidth - reserved - 8.dp) / 2f
    return when {
        candidate < minWidth -> minWidth
        candidate > InspectorCompactFieldMaxWidth -> InspectorCompactFieldMaxWidth
        else -> candidate
    }
}

@Composable
private fun CompactLabeledNumberField(
    label: String,
    value: String,
    resetKey: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    suffix: String = "",
    placeholder: String = "",
    maxFieldWidth: Dp = InspectorCompactFieldMaxWidth,
    onCommit: (Double) -> Unit,
) {
    LabeledField(label) {
        CompactNumberField(
            label = "",
            value = value,
            resetKey = resetKey,
            modifier = modifier.widthIn(max = maxFieldWidth).fillMaxWidth(),
            enabled = enabled,
            suffix = suffix,
            placeholder = placeholder,
            onCommit = onCommit,
        )
    }
}

@Composable
private fun CompactLabeledSelectField(
    label: String,
    value: String,
    options: List<String>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    maxFieldWidth: Dp = InspectorCompactSelectMaxWidth,
    onSelect: (String) -> Unit,
) {
    LabeledField(label) {
        CompactSelectField(
            value = value,
            options = options,
            onSelect = onSelect,
            modifier = modifier.widthIn(max = maxFieldWidth).fillMaxWidth(),
            enabled = enabled,
        )
    }
}

@Composable
private fun CompactLabeledTextField(
    label: String,
    value: String,
    resetKey: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    placeholder: String = "",
    maxFieldWidth: Dp = InspectorCompactSelectMaxWidth,
    onCommit: (String) -> Unit,
) {
    LabeledField(label) {
        CompactTextField(
            value = value,
            resetKey = resetKey,
            modifier = modifier.widthIn(max = maxFieldWidth).fillMaxWidth(),
            enabled = enabled,
            placeholder = placeholder,
            onCommit = onCommit,
        )
    }
}

/** Single-line string field mirroring [CompactNumberField]; commits on Enter or focus loss. */
@Composable
private fun CompactTextField(
    value: String,
    resetKey: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    placeholder: String = "",
    onCommit: (String) -> Unit,
) {
    val colors = LocalEditorColors.current
    var draft by remember(resetKey, value) { mutableStateOf(value) }
    var hadFocus by remember(resetKey) { mutableStateOf(false) }
    var focused by remember(resetKey) { mutableStateOf(false) }
    fun commitDraft() {
        if (draft != value) onCommit(draft)
    }
    Surface(
        modifier = modifier.height(26.dp),
        shape = RoundedCornerShape(5.dp),
        color = if (enabled) colors.controlSurface else colors.controlDisabledSurface,
        border = BorderStroke(1.dp, if (focused) colors.accent else if (enabled) colors.controlStroke else colors.controlDisabledStroke),
    ) {
        Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (draft.isEmpty() && placeholder.isNotEmpty()) {
                    Text(placeholder, style = MaterialTheme.typography.labelMedium, color = colors.mutedInk, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                }
                BasicTextField(
                    value = draft,
                    onValueChange = { input -> draft = input },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focus ->
                            focused = focus.isFocused
                            if (focus.isFocused) {
                                hadFocus = true
                            } else if (hadFocus) {
                                hadFocus = false
                                commitDraft()
                            }
                        }
                        .onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown && (event.key == Key.Enter || event.key == Key.NumPadEnter)) {
                                commitDraft()
                                true
                            } else {
                                false
                            }
                        },
                    enabled = enabled,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.labelMedium.copy(color = if (enabled) colors.ink else colors.mutedInk),
                )
            }
        }
    }
}

private data class PaddingControlSpec(
    val label: String,
    val side: PaddingSide,
    val value: String,
)

@Composable
private fun PaddingControls(
    top: String,
    right: String,
    bottom: String,
    left: String,
    resetKey: String,
    onChange: (PaddingSide, Double) -> Unit,
) {
    val fields = listOf(
        PaddingControlSpec("T", PaddingSide.Top, top),
        PaddingControlSpec("R", PaddingSide.Right, right),
        PaddingControlSpec("B", PaddingSide.Bottom, bottom),
        PaddingControlSpec("L", PaddingSide.Left, left),
    )
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < InspectorPaddingTwoColumnWidth) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                fields.chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { field ->
                            PaddingNumberField(field, resetKey, Modifier.weight(1f), onChange)
                        }
                    }
                }
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                fields.forEach { field ->
                    PaddingNumberField(field, resetKey, Modifier.weight(1f), onChange)
                }
            }
        }
    }
}

@Composable
private fun PaddingNumberField(
    field: PaddingControlSpec,
    resetKey: String,
    modifier: Modifier,
    onChange: (PaddingSide, Double) -> Unit,
) {
    CompactNumberField(
        label = field.label,
        value = field.value,
        resetKey = "padding-$resetKey-${field.label}",
        modifier = modifier,
    ) { value ->
        onChange(field.side, value)
    }
}

@Composable
private fun CompactNumberField(
    label: String,
    value: String,
    resetKey: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    suffix: String = "",
    leadingIcon: EditorIcon? = null,
    placeholder: String = "",
    onCommit: (Double) -> Unit,
) {
    val colors = LocalEditorColors.current
    var draft by remember(resetKey, value) { mutableStateOf(value) }
    var hadFocus by remember(resetKey) { mutableStateOf(false) }
    var focused by remember(resetKey) { mutableStateOf(false) }
    fun commitDraft() {
        val parsed = draft.toDoubleOrNull() ?: return
        if (parsed != value.toDoubleOrNull()) onCommit(parsed)
    }
    Surface(
        modifier = modifier.height(26.dp),
        shape = RoundedCornerShape(5.dp),
        color = if (enabled) colors.controlSurface else colors.controlDisabledSurface,
        border = BorderStroke(1.dp, if (focused) colors.accent else if (enabled) colors.controlStroke else colors.controlDisabledStroke),
    ) {
        Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            leadingIcon?.let {
                EditorSvgIcon(it, contentDescription = null, modifier = Modifier.size(13.dp), tint = if (enabled) colors.ink else colors.mutedInk)
                Spacer(Modifier.width(5.dp))
            }
            if (label.isNotEmpty()) {
                val labelWidth = when {
                    label.length <= 1 -> 16.dp
                    label.length <= 3 -> 24.dp
                    else -> 34.dp
                }
                Text(
                    label,
                    modifier = Modifier.width(labelWidth),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (enabled) colors.ink else colors.mutedInk,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (draft.isEmpty() && placeholder.isNotEmpty()) {
                    Text(placeholder, style = MaterialTheme.typography.labelMedium, color = colors.mutedInk)
                }
                BasicTextField(
                    value = draft,
                    onValueChange = { input -> draft = input.filter { it.isDigit() || it == '.' || it == '-' } },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focus ->
                            focused = focus.isFocused
                            if (focus.isFocused) {
                                hadFocus = true
                            } else if (hadFocus) {
                                hadFocus = false
                                commitDraft()
                            }
                        }
                        .onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown && (event.key == Key.Enter || event.key == Key.NumPadEnter)) {
                                commitDraft()
                                true
                            } else {
                                false
                            }
                        },
                    enabled = enabled,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.labelMedium.copy(color = if (enabled) colors.ink else colors.mutedInk),
                )
            }
            if (suffix.isNotEmpty()) {
                Text(suffix, style = MaterialTheme.typography.labelMedium, color = if (enabled) colors.ink else colors.mutedInk)
            }
        }
    }
}

@Composable
private fun CompactSelectField(
    value: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: EditorIcon? = null,
) {
    val colors = LocalEditorColors.current
    var expanded by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(5.dp)
    Box(modifier) {
        Surface(
            modifier = Modifier.fillMaxWidth().height(26.dp).clip(shape).clickable(enabled = enabled) { expanded = true },
            shape = shape,
            color = if (enabled) colors.controlSurface else colors.controlDisabledSurface,
            border = BorderStroke(1.dp, if (expanded) colors.accent else if (enabled) colors.controlStroke else colors.controlDisabledStroke),
        ) {
            Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                leadingIcon?.let {
                    EditorSvgIcon(it, contentDescription = null, modifier = Modifier.size(13.dp), tint = if (enabled) colors.ink else colors.mutedInk)
                    Spacer(Modifier.width(6.dp))
                }
                Text(value, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = if (enabled) colors.ink else colors.mutedInk, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                EditorSvgIcon(EditorIcon.ChevronDown, contentDescription = "Open options", modifier = Modifier.size(11.dp), tint = colors.controlInk)
            }
        }
        EditorDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                EditorDropdownMenuItem(
                    text = option,
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}

private data class IconStripItem(
    val icon: EditorIcon,
    val contentDescription: String,
    val enabled: Boolean = true,
    val active: Boolean = false,
    val onClick: () -> Unit,
)

@Composable
private fun IconButtonStrip(items: List<IconStripItem>, modifier: Modifier = Modifier) {
    val colors = LocalEditorColors.current
    val shape = RoundedCornerShape(5.dp)
    Surface(
        modifier = modifier.height(26.dp).clip(shape),
        shape = shape,
        color = colors.controlSurface,
        border = BorderStroke(1.dp, colors.controlStroke),
    ) {
        Row(Modifier.fillMaxSize()) {
            items.forEachIndexed { index, item ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(if (item.active) colors.accent else Color.Transparent)
                        .clickable(enabled = item.enabled) { item.onClick() },
                    contentAlignment = Alignment.Center,
                ) {
                    EditorSvgIcon(
                        icon = item.icon,
                        contentDescription = item.contentDescription,
                        modifier = Modifier.size(15.dp),
                        tint = when {
                            !item.enabled -> colors.mutedInk
                            item.active -> Color.White
                            else -> colors.ink
                        },
                    )
                    if (index > 0) {
                        Box(Modifier.align(Alignment.CenterStart).width(1.dp).fillMaxHeight().background(colors.controlStroke))
                    }
                }
            }
        }
    }
}

@Composable
private fun AlignmentControls(state: MissionEditorStateHolder, enabled: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        IconButtonStrip(
            items = listOf(
                IconStripItem(EditorIcon.AlignHorizontalLeft, "Align left", enabled) { alignSelection(state, InspectorAlignment.Left) },
                IconStripItem(EditorIcon.AlignHorizontalCenter, "Align horizontal center", enabled) { alignSelection(state, InspectorAlignment.HCenter) },
                IconStripItem(EditorIcon.AlignHorizontalRight, "Align right", enabled) { alignSelection(state, InspectorAlignment.Right) },
            ),
            modifier = Modifier.weight(1f),
        )
        IconButtonStrip(
            items = listOf(
                IconStripItem(EditorIcon.AlignVerticalTop, "Align top", enabled) { alignSelection(state, InspectorAlignment.Top) },
                IconStripItem(EditorIcon.AlignVerticalCenter, "Align vertical center", enabled) { alignSelection(state, InspectorAlignment.VCenter) },
                IconStripItem(EditorIcon.AlignVerticalBottom, "Align bottom", enabled) { alignSelection(state, InspectorAlignment.Bottom) },
            ),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ConstraintsControls(
    horizontal: HorizontalConstraint?,
    vertical: VerticalConstraint?,
    enabled: Boolean,
    onHorizontal: (HorizontalConstraint) -> Unit,
    onVertical: (VerticalConstraint) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            CompactSelectField(
                value = horizontal?.hLabel() ?: "Mixed",
                options = HorizontalConstraint.entries.map { it.hLabel() },
                onSelect = { label -> HorizontalConstraint.entries.firstOrNull { it.hLabel() == label }?.let(onHorizontal) },
                enabled = enabled,
                leadingIcon = EditorIcon.ConstraintHorizontal,
            )
            CompactSelectField(
                value = vertical?.vLabel() ?: "Mixed",
                options = VerticalConstraint.entries.map { it.vLabel() },
                onSelect = { label -> VerticalConstraint.entries.firstOrNull { it.vLabel() == label }?.let(onVertical) },
                enabled = enabled,
                leadingIcon = EditorIcon.ConstraintVertical,
            )
        }
        ConstraintWidget(horizontal = horizontal, vertical = vertical, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ConstraintWidget(
    horizontal: HorizontalConstraint?,
    vertical: VerticalConstraint?,
    modifier: Modifier = Modifier,
) {
    val colors = LocalEditorColors.current
    val line = colors.mutedInk
    val active = colors.accent
    Surface(
        modifier = modifier.height(58.dp),
        shape = RoundedCornerShape(5.dp),
        color = colors.controlSurface,
        border = BorderStroke(1.dp, colors.controlStroke),
    ) {
        Canvas(Modifier.fillMaxSize().padding(7.dp)) {
            val stroke = Stroke(1.dp.toPx())
            val activeStrokeWidth = 2.dp.toPx()
            val objectWidth = size.width * 0.58f
            val objectHeight = 22.dp.toPx().coerceAtMost(size.height - 10.dp.toPx())
            val left = (size.width - objectWidth) / 2f
            val top = (size.height - objectHeight) / 2f
            val right = left + objectWidth
            val bottom = top + objectHeight
            val centerX = size.width / 2f
            val centerY = size.height / 2f

            drawRoundRect(
                color = Color.White,
                topLeft = Offset(left, top),
                size = Size(objectWidth, objectHeight),
                cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx()),
            )
            drawRoundRect(
                color = colors.controlStroke,
                topLeft = Offset(left, top),
                size = Size(objectWidth, objectHeight),
                cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx()),
                style = stroke,
            )

            drawLine(line, Offset(0f, centerY), Offset(left - 8.dp.toPx(), centerY), strokeWidth = 1.dp.toPx())
            drawLine(line, Offset(right + 8.dp.toPx(), centerY), Offset(size.width, centerY), strokeWidth = 1.dp.toPx())
            drawLine(line, Offset(centerX, 0f), Offset(centerX, top - 6.dp.toPx()), strokeWidth = 1.dp.toPx())
            drawLine(line, Offset(centerX, bottom + 6.dp.toPx()), Offset(centerX, size.height), strokeWidth = 1.dp.toPx())

            if (horizontal in setOf(HorizontalConstraint.Left, HorizontalConstraint.LeftRight, HorizontalConstraint.Scale)) {
                drawLine(active, Offset(0f, centerY), Offset(left, centerY), strokeWidth = activeStrokeWidth)
            }
            if (horizontal in setOf(HorizontalConstraint.Right, HorizontalConstraint.LeftRight, HorizontalConstraint.Scale)) {
                drawLine(active, Offset(right, centerY), Offset(size.width, centerY), strokeWidth = activeStrokeWidth)
            }
            if (horizontal == HorizontalConstraint.Center) {
                drawLine(active, Offset(centerX, top - 9.dp.toPx()), Offset(centerX, bottom + 9.dp.toPx()), strokeWidth = activeStrokeWidth)
            }
            if (vertical in setOf(VerticalConstraint.Top, VerticalConstraint.TopBottom, VerticalConstraint.Scale)) {
                drawLine(active, Offset(centerX, 0f), Offset(centerX, top), strokeWidth = activeStrokeWidth)
            }
            if (vertical in setOf(VerticalConstraint.Bottom, VerticalConstraint.TopBottom, VerticalConstraint.Scale)) {
                drawLine(active, Offset(centerX, bottom), Offset(centerX, size.height), strokeWidth = activeStrokeWidth)
            }
            if (vertical == VerticalConstraint.Center) {
                drawLine(active, Offset(left - 10.dp.toPx(), centerY), Offset(right + 10.dp.toPx(), centerY), strokeWidth = activeStrokeWidth)
            }
        }
    }
}

@Composable
private fun RotationControls(
    state: MissionEditorStateHolder,
    value: String,
    resetKey: String,
    enabled: Boolean,
    placeholder: String = "",
) {
    val ids = selectedIds(state)
    val canFlip = ids.size >= 2
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        CompactNumberField(
            label = "",
            value = value,
            resetKey = resetKey,
            modifier = Modifier.weight(1f),
            enabled = enabled,
            suffix = "°",
            leadingIcon = EditorIcon.Rotate,
            placeholder = placeholder,
        ) { setRotationForSelection(state, it) }
        IconButtonStrip(
            items = listOf(
                IconStripItem(EditorIcon.Rotate, "Rotate 90 degrees", enabled) { rotateSelectionBy(state, 90.0) },
                IconStripItem(EditorIcon.FlipHorizontal, "Flip horizontal", enabled && canFlip) { state.dispatch(DesignEditorIntent.FlipHorizontal(ids)) },
                IconStripItem(EditorIcon.FlipVertical, "Flip vertical", enabled && canFlip) { state.dispatch(DesignEditorIntent.FlipVertical(ids)) },
            ),
            modifier = Modifier.weight(1f),
        )
    }
}

// --- Small shared bits -------------------------------------------------------

@Composable
internal fun SectionHeaderAdd(label: String, onAdd: () -> Unit) {
    val colors = LocalEditorColors.current
    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = colors.mutedInk, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
        TinyIconButton(EditorIcon.Plus, contentDescription = "Add $label", onClick = onAdd)
    }
}

@Composable
internal fun MutedNote(text: String) {
    val colors = LocalEditorColors.current
    Text(text, color = colors.mutedInk, style = MaterialTheme.typography.bodySmall)
}

@Composable
internal fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label, style = MaterialTheme.typography.bodySmall, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun LayerToggle(visible: Boolean, onClick: () -> Unit) {
    val colors = LocalEditorColors.current
    Box(Modifier.size(20.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        EditorSvgIcon(
            icon = if (visible) EditorIcon.Visibility else EditorIcon.VisibilityOff,
            contentDescription = if (visible) "Hide" else "Show",
            modifier = Modifier.size(16.dp),
            tint = if (visible) colors.controlInk else colors.mutedInk,
        )
    }
}

private fun inspectorTabIcon(tab: InspectorTab): EditorIcon = when (tab) {
    InspectorTab.Design -> EditorIcon.Design
    InspectorTab.Prototype -> EditorIcon.Link
    InspectorTab.Comments -> EditorIcon.Comments
}

internal fun inspectorSectionIcon(section: InspectorSection): EditorIcon = when (section) {
    InspectorSection.Position -> EditorIcon.Position
    InspectorSection.Layout -> EditorIcon.Layout
    InspectorSection.Appearance -> EditorIcon.Design
    InspectorSection.Fill -> EditorIcon.Fill
    InspectorSection.Stroke -> EditorIcon.Stroke
    InspectorSection.Effects -> EditorIcon.Gradient
    InspectorSection.Typography -> EditorIcon.Typography
    InspectorSection.Constraints -> EditorIcon.Position
    InspectorSection.Interactions -> EditorIcon.Link
    InspectorSection.Motion -> EditorIcon.Rotate
}

@Composable
internal fun RemoveButton(onClick: () -> Unit) {
    val colors = LocalEditorColors.current
    Box(Modifier.size(22.dp).clip(RoundedCornerShape(11.dp)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        EditorSvgIcon(EditorIcon.Close, contentDescription = "Remove", modifier = Modifier.size(14.dp), tint = colors.statusDanger)
    }
}

@Composable
private fun TinyIconButton(icon: EditorIcon, contentDescription: String, onClick: () -> Unit) {
    val colors = LocalEditorColors.current
    val shape = RoundedCornerShape(5.dp)
    Surface(
        modifier = Modifier.size(24.dp).clip(shape).clickable(onClick = onClick),
        shape = shape,
        color = colors.controlSurface,
        border = BorderStroke(1.dp, colors.controlStroke),
    ) {
        Box(contentAlignment = Alignment.Center) {
            EditorSvgIcon(icon, contentDescription = contentDescription, modifier = Modifier.size(14.dp), tint = colors.ink)
        }
    }
}

@Composable
internal fun TinyButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    val colors = LocalEditorColors.current
    val shape = RoundedCornerShape(5.dp)
    Surface(
        modifier = Modifier.height(24.dp).clip(shape).clickable(enabled = enabled, onClick = onClick),
        shape = shape,
        color = colors.controlSurface,
        border = BorderStroke(1.dp, colors.controlStroke),
    ) {
        Box(Modifier.padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = if (enabled) colors.ink else colors.mutedInk)
        }
    }
}

@Composable
internal fun SmallSelect(value: String, options: List<String>, modifier: Modifier = Modifier, onSelect: (String) -> Unit) {
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

private enum class InspectorAlignment { Left, HCenter, Right, Top, VCenter, Bottom }

private data class InspectorBounds(
    val left: Double,
    val top: Double,
    val right: Double,
    val bottom: Double,
) {
    val centerX: Double get() = (left + right) / 2.0
    val centerY: Double get() = (top + bottom) / 2.0
}

private data class AlignTarget(
    val nodeId: String,
    val box: LayoutBox,
    val parent: LayoutBox?,
    val currentX: Double,
    val currentY: Double,
)

private fun selectedIds(state: MissionEditorStateHolder): Set<String> =
    state.designState.selectedNodeIds.ifEmpty {
        state.designState.selectedNodeId.takeIf { it.isNotBlank() }?.let { setOf(it) } ?: emptySet()
    }

private fun LayoutBox.bounds(): InspectorBounds = InspectorBounds(x, y, right, bottom)

private fun boundsOf(boxes: List<LayoutBox>): InspectorBounds? {
    if (boxes.isEmpty()) return null
    return InspectorBounds(
        left = boxes.minOf { it.x },
        top = boxes.minOf { it.y },
        right = boxes.maxOf { it.right },
        bottom = boxes.maxOf { it.bottom },
    )
}

private fun alignSelection(state: MissionEditorStateHolder, alignment: InspectorAlignment) {
    val document = state.designState.document ?: return
    val layout = state.artboardLayout ?: return
    val targets = selectedIds(state).mapNotNull { id ->
        val node = document.nodeById(id) ?: return@mapNotNull null
        if (node.locked || !document.isCoordinatePositioned(id)) return@mapNotNull null
        val box = layout.findBySourceId(id) ?: return@mapNotNull null
        val parent = findParentBox(layout, id)
        AlignTarget(
            nodeId = id,
            box = box,
            parent = parent,
            currentX = node.position?.x ?: (box.x - (parent?.x ?: 0.0)),
            currentY = node.position?.y ?: (box.y - (parent?.y ?: 0.0)),
        )
    }
    if (targets.isEmpty()) return
    val reference = if (targets.size > 1) {
        boundsOf(targets.map { it.box }) ?: return
    } else {
        targets.first().parent?.bounds() ?: layout.bounds()
    }
    state.dispatch(DesignEditorIntent.BeginInteraction)
    targets.forEach { target ->
        val parentLeft = target.parent?.x ?: 0.0
        val parentTop = target.parent?.y ?: 0.0
        val nextAbsoluteX = when (alignment) {
            InspectorAlignment.Left -> reference.left
            InspectorAlignment.HCenter -> reference.centerX - target.box.width / 2.0
            InspectorAlignment.Right -> reference.right - target.box.width
            InspectorAlignment.Top, InspectorAlignment.VCenter, InspectorAlignment.Bottom -> null
        }
        val nextAbsoluteY = when (alignment) {
            InspectorAlignment.Top -> reference.top
            InspectorAlignment.VCenter -> reference.centerY - target.box.height / 2.0
            InspectorAlignment.Bottom -> reference.bottom - target.box.height
            InspectorAlignment.Left, InspectorAlignment.HCenter, InspectorAlignment.Right -> null
        }
        state.dispatch(
            DesignEditorIntent.PositionNode(
                nodeId = target.nodeId,
                x = nextAbsoluteX?.minus(parentLeft) ?: target.currentX,
                y = nextAbsoluteY?.minus(parentTop) ?: target.currentY,
            ),
        )
    }
    state.dispatch(DesignEditorIntent.EndInteraction)
}

private fun bulkPosition(state: MissionEditorStateHolder, x: Double? = null, y: Double? = null) {
    val document = state.designState.document ?: return
    state.dispatch(DesignEditorIntent.BeginInteraction)
    selectedIds(state).forEach { id ->
        val node = document.nodeById(id) ?: return@forEach
        if (node.locked || !document.isCoordinatePositioned(id)) return@forEach
        val currentX = node.position?.x ?: 0.0
        val currentY = node.position?.y ?: 0.0
        state.dispatch(DesignEditorIntent.PositionNode(id, x = x ?: currentX, y = y ?: currentY))
    }
    state.dispatch(DesignEditorIntent.EndInteraction)
}

private fun bulkResize(state: MissionEditorStateHolder, width: Double? = null, height: Double? = null, keepRatio: Boolean) {
    val document = state.designState.document ?: return
    state.dispatch(DesignEditorIntent.BeginInteraction)
    selectedIds(state).forEach { id ->
        val node = document.nodeById(id) ?: return@forEach
        if (node.locked) return@forEach
        val currentWidth = node.size.width ?: 0.0
        val currentHeight = node.size.height ?: 0.0
        val nextWidth = when {
            width != null -> width
            keepRatio && height != null && currentHeight > 0.0 -> height * currentWidth / currentHeight
            else -> null
        }
        val nextHeight = when {
            height != null -> height
            keepRatio && width != null && currentWidth > 0.0 -> width * currentHeight / currentWidth
            else -> null
        }
        state.dispatch(DesignEditorIntent.ResizeNode(id, width = nextWidth, height = nextHeight))
    }
    state.dispatch(DesignEditorIntent.EndInteraction)
}

private fun applyConstraintsToSelection(
    state: MissionEditorStateHolder,
    horizontal: HorizontalConstraint? = null,
    vertical: VerticalConstraint? = null,
) {
    val document = state.designState.document ?: return
    state.dispatch(DesignEditorIntent.BeginInteraction)
    selectedIds(state).forEach { id ->
        val node = document.nodeById(id) ?: return@forEach
        if (node.locked || !document.isCoordinatePositioned(id)) return@forEach
        state.dispatch(DesignEditorIntent.UpdateConstraints(id, horizontal = horizontal, vertical = vertical))
    }
    state.dispatch(DesignEditorIntent.EndInteraction)
}

private fun setRotationForSelection(state: MissionEditorStateHolder, degrees: Double) {
    val document = state.designState.document ?: return
    state.dispatch(DesignEditorIntent.BeginInteraction)
    selectedIds(state).forEach { id ->
        val node = document.nodeById(id) ?: return@forEach
        if (!node.locked) state.dispatch(DesignEditorIntent.SetRotation(id, normalizeAngleDegrees(degrees)))
    }
    state.dispatch(DesignEditorIntent.EndInteraction)
}

private fun rotateSelectionBy(state: MissionEditorStateHolder, delta: Double) {
    val document = state.designState.document ?: return
    state.dispatch(DesignEditorIntent.BeginInteraction)
    selectedIds(state).forEach { id ->
        val node = document.nodeById(id) ?: return@forEach
        if (!node.locked) state.dispatch(DesignEditorIntent.SetRotation(id, normalizeAngleDegrees(node.rotation + delta)))
    }
    state.dispatch(DesignEditorIntent.EndInteraction)
}

private fun DesignPaint.displayColor(document: DesignDocument?): DesignColor? = when (this) {
    is DesignPaint.Solid -> color.resolveDisplayColor(document)
    is DesignPaint.Gradient -> stops.firstOrNull()?.color?.resolveDisplayColor(document)
    else -> null
}

/**
 * The best available preview color for a possibly variable-bound color: the literal value
 * when authored directly, else the design token's default-mode value (aliases followed).
 * Prop/data bindings have no static value to preview and fall back to null. Without this,
 * every token-bound fill/stroke (the common case — most fills in the bundled samples are
 * `{"§var": "..."}` references) showed as a flat black/blue swatch that didn't match what
 * the canvas actually rendered (the canvas resolves the same variable via `DesignResolver`).
 */
private fun Bindable<DesignColor>.resolveDisplayColor(document: DesignDocument?): DesignColor? {
    literalOrNull()?.let { return it }
    val varId = (this as? Bindable.VarRef)?.id ?: return null
    return document?.let { resolveVariableColor(it, varId) }
}

/** Resolves a color variable's default-mode value, following alias chains (cycle-safe). */
private fun resolveVariableColor(document: DesignDocument, varId: String, seen: Set<String> = emptySet()): DesignColor? {
    if (varId in seen) return null
    val (collectionId, variable) = document.variables.findVariable(varId) ?: return null
    val collection = document.variables.collections[collectionId] ?: return null
    val mode = collection.defaultMode.ifBlank { collection.modes.firstOrNull() } ?: return null
    return when (val value = variable.values[mode]) {
        is VariableValue.ColorValue -> value.value
        is VariableValue.Alias -> resolveVariableColor(document, value.varId, seen + varId)
        else -> null
    }
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
