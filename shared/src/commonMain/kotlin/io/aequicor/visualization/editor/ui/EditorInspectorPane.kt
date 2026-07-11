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
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.aequicor.visualization.MissionEditorStateHolder
import io.aequicor.visualization.editor.presentation.CompactLabel
import io.aequicor.visualization.editor.presentation.DesignEditorIntent
import io.aequicor.visualization.editor.presentation.DiagramEditorIntent
import io.aequicor.visualization.editor.presentation.DiagramSelection
import io.aequicor.visualization.editor.presentation.DiagramTextFormat
import io.aequicor.visualization.editor.presentation.DiagramTool
import io.aequicor.visualization.editor.presentation.EditorLayoutMode
import io.aequicor.visualization.editor.presentation.EffectOp
import io.aequicor.visualization.editor.presentation.EffectType
import io.aequicor.visualization.editor.presentation.FillKind
import io.aequicor.visualization.editor.presentation.FillOp
import io.aequicor.visualization.editor.presentation.InspectorSection
import io.aequicor.visualization.editor.presentation.InspectorTab
import io.aequicor.visualization.editor.presentation.LineHeightPatch
import io.aequicor.visualization.editor.presentation.PaddingSide
import io.aequicor.visualization.editor.presentation.isCoordinatePositioned
import io.aequicor.visualization.editor.presentation.isNodeLocked
import io.aequicor.visualization.editor.presentation.normalizeAngleDegrees
import io.aequicor.visualization.editor.presentation.StrokeOp
import io.aequicor.visualization.editor.presentation.TextRangeEditing
import io.aequicor.visualization.editor.presentation.TypographyPatch
import io.aequicor.visualization.editor.ui.theme.LocalEditorColors
import io.aequicor.visualization.engine.ir.layout.LayoutBox
import io.aequicor.visualization.engine.ir.model.AlignItems
import io.aequicor.visualization.engine.ir.model.Bindable
import io.aequicor.visualization.subsystems.diagrams.arrows.arrowheadPath
import io.aequicor.visualization.subsystems.diagrams.compose.DiagramNodePreview
import io.aequicor.visualization.subsystems.diagrams.compose.DiagramRelationPreview
import io.aequicor.visualization.subsystems.diagrams.compose.toComposePath
import io.aequicor.visualization.subsystems.diagrams.model.DiagramArrowhead
import io.aequicor.visualization.subsystems.diagrams.model.DiagramArrowheadKind
import io.aequicor.visualization.subsystems.diagrams.model.DiagramColor
import io.aequicor.visualization.subsystems.diagrams.model.DiagramCornerStyle
import io.aequicor.visualization.subsystems.diagrams.model.DiagramEdge
import io.aequicor.visualization.subsystems.diagrams.model.DiagramEdgeId
import io.aequicor.visualization.subsystems.diagrams.model.DiagramEdgeLabelPosition
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodeId
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodePayload
import io.aequicor.visualization.subsystems.diagrams.model.DiagramRelation
import io.aequicor.visualization.subsystems.diagrams.model.DiagramRoutingStyle
import io.aequicor.visualization.subsystems.diagrams.model.DiagramStrokePattern
import io.aequicor.visualization.subsystems.diagrams.model.DiagramStyle
import io.aequicor.visualization.subsystems.diagrams.model.FlowchartNodeKind
import io.aequicor.visualization.subsystems.diagrams.model.LineJumpStyle
import io.aequicor.visualization.subsystems.diagrams.model.TableNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlActivityKind
import io.aequicor.visualization.subsystems.diagrams.model.UmlActivityNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlClassNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlComponentNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlDeploymentNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlLifelineNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlMember
import io.aequicor.visualization.subsystems.diagrams.model.UmlMessageKind
import io.aequicor.visualization.subsystems.diagrams.model.UmlStateNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlUseCaseNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlVisibility
import io.aequicor.visualization.subsystems.diagrams.ops.UmlClassMemberKind
import io.aequicor.visualization.subsystems.diagrams.path.DiagramPoint
import io.aequicor.visualization.subsystems.diagrams.templates.diagramTemplates
import io.aequicor.visualization.subsystems.figures.BooleanOperationKind
import io.aequicor.visualization.subsystems.figures.compose.FigureBooleanPreview
import io.aequicor.visualization.subsystems.figures.compose.FigurePreviewStyle
import io.aequicor.visualization.subsystems.figures.compose.FigureShapePreview
import io.aequicor.visualization.engine.ir.model.DesignColor
import io.aequicor.visualization.engine.ir.model.bindable
import io.aequicor.visualization.engine.ir.model.DesignDocument
import io.aequicor.visualization.engine.ir.model.DesignEffect
import io.aequicor.visualization.engine.ir.model.DesignNode
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.engine.ir.model.DesignPaint
import io.aequicor.visualization.subsystems.figures.DesignViewBox
import io.aequicor.visualization.subsystems.figures.HandleMirror
import io.aequicor.visualization.subsystems.figures.ShapeType
import io.aequicor.visualization.engine.ir.model.DesignStyle
import io.aequicor.visualization.engine.ir.model.DesignTextStyle
import io.aequicor.visualization.engine.ir.model.HorizontalConstraint
import io.aequicor.visualization.engine.ir.model.JustifyContent
import io.aequicor.visualization.engine.ir.model.LayoutMode
import io.aequicor.visualization.engine.ir.model.LeadingTrim
import io.aequicor.visualization.engine.ir.model.SizingMode
import io.aequicor.visualization.engine.ir.model.StrokeAlign
import io.aequicor.visualization.engine.ir.model.TextAlignHorizontal
import io.aequicor.visualization.engine.ir.model.TextAlignVertical
import io.aequicor.visualization.engine.ir.model.TextAutoResize
import io.aequicor.visualization.engine.ir.model.TextCase
import io.aequicor.visualization.engine.ir.model.TextDecorationKind
import io.aequicor.visualization.engine.ir.model.TextDecorationStyle
import io.aequicor.visualization.engine.ir.model.TextListType
import io.aequicor.visualization.engine.ir.model.TextScriptPosition
import io.aequicor.visualization.engine.ir.model.TextTruncate
import io.aequicor.visualization.engine.ir.model.VariableValue
import io.aequicor.visualization.engine.ir.model.VerticalConstraint
import io.aequicor.visualization.engine.ir.model.literalOrNull
import io.aequicor.visualization.subsystems.typography.FontStyles
import io.aequicor.visualization.subsystems.typography.compose.FontFamilyInfo
import io.aequicor.visualization.subsystems.typography.compose.rememberBundledFontProvider
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
        // The Diagram section mirrors the Shape section's standalone chrome (same ownership note).
        DiagramSection(state, node, visible = node.kind is DesignNodeKind.Diagram)
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
            leadingContent = { SizingModePreview(node.sizing?.horizontal ?: SizingMode.Fixed) },
            optionLeadingContent = { label -> SizingModePreview(sizingFromLabel(label)) },
        )
        CompactSelectField(
            value = (node.sizing?.vertical ?: SizingMode.Fixed).sizingLabel(),
            options = SizingMode.entries.map { it.sizingLabel() },
            onSelect = { state.dispatch(DesignEditorIntent.UpdateSizingMode(nodeId, vertical = sizingFromLabel(it))) },
            modifier = Modifier.weight(1f),
            leadingContent = { SizingModePreview(node.sizing?.vertical ?: SizingMode.Fixed) },
            optionLeadingContent = { label -> SizingModePreview(sizingFromLabel(label)) },
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
        leadingContent = { BlendModePreview(node.blendMode.ifBlank { "normal" }) },
        optionLeadingContent = { BlendModePreview(it) },
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
        FillRow(state, nodeId, index, paint, enabled) { state.dispatch(DesignEditorIntent.FillCommand(nodeId, it)) }
        Spacer(Modifier.height(6.dp))
    }
}

/**
 * One paint row of a fill list. Reused for node fills and vector-network region fills; the owning
 * list decides how a [FillOp] is committed via [onOp] (so this stays list-agnostic). [keyPrefix]
 * namespaces text-field remember keys so multiple lists on screen don't collide.
 */
@Composable
private fun FillRow(
    state: MissionEditorStateHolder,
    keyPrefix: String,
    index: Int,
    paint: DesignPaint,
    enabled: Boolean,
    onOp: (FillOp) -> Unit,
) {
    val kind = paint.fillKind()
    val visible = paint.visible.literalOrNull() ?: true
    val opacity = paint.opacity.literalOrNull() ?: 1.0
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        LayerToggle(visible) { onOp(FillOp.ToggleAt(index)) }
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
                        onColor = { onOp(FillOp.SetColor(index, it)) },
                        onOpacity = { onOp(FillOp.SetOpacity(index, it)) },
                    )
                }
                is DesignPaint.Gradient -> GradientPreview(state, paint)
                else -> FillTypeChip(kind.displayName)
            }
        }
        SmallSelect(
            value = kind.displayName,
            options = FillKind.entries.map { it.displayName },
            leadingContent = { FillKindPreview(kind) },
            optionLeadingContent = { label -> FillKind.entries.firstOrNull { it.displayName == label }?.let { FillKindPreview(it) } },
        ) { label ->
            FillKind.entries.firstOrNull { it.displayName == label }?.let { onOp(FillOp.SetType(index, it)) }
        }
        RemoveButton { onOp(FillOp.RemoveAt(index)) }
    }
    if (paint is DesignPaint.Gradient) {
        GradientStops(state, keyPrefix, index, paint, enabled, onOp)
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
private fun GradientStops(
    state: MissionEditorStateHolder,
    keyPrefix: String,
    index: Int,
    gradient: DesignPaint.Gradient,
    enabled: Boolean,
    onOp: (FillOp) -> Unit,
) {
    Column(Modifier.padding(start = 26.dp, top = 4.dp)) {
        // Direction angle (0° = left→right, 90° = top→bottom).
        InspectorNumberField("Angle", gradientAngleDegrees(gradient).formatPx(), "°", "$keyPrefix-fill-$index-angle", enabled = enabled) {
            onOp(FillOp.SetGradientAngle(index, it))
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
                        onArgb = { onOp(FillOp.SetGradientStopColor(index, stopIndex, it)) },
                    )
                }
                Box(Modifier.width(78.dp)) {
                    InspectorNumberField("", (stop.position * 100).formatPx(), "%", "$keyPrefix-fill-$index-stop-$stopIndex", enabled = enabled) {
                        onOp(FillOp.SetGradientStopPosition(index, stopIndex, it / 100.0))
                    }
                }
                RemoveButton { onOp(FillOp.RemoveGradientStop(index, stopIndex)) }
            }
            Spacer(Modifier.height(4.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TinyButton("+ stop") { onOp(FillOp.AddGradientStop(index)) }
            TinyButton("reverse") { onOp(FillOp.ReverseGradient(index)) }
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
        SmallSelect(
            value = strokes.align.strokeLabel(),
            options = StrokeAlign.entries.map { it.strokeLabel() },
            leadingContent = { StrokeAlignPreview(strokes.align) },
            optionLeadingContent = { label -> StrokeAlign.entries.firstOrNull { it.strokeLabel() == label }?.let { StrokeAlignPreview(it) } },
        ) { label ->
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
        if (shape == ShapeType.Line || shape == ShapeType.Arrow) {
            Spacer(Modifier.height(6.dp))
            LabeledField("Ends") {
                SelectField(
                    value = strokes.cap,
                    options = listOf("butt", "round", "square", "arrow"),
                    onSelect = { state.dispatch(DesignEditorIntent.StrokeCommand(nodeId, StrokeOp.SetCap(it))) },
                    leadingContent = { StrokeCapPreview(strokes.cap) },
                    optionLeadingContent = { StrokeCapPreview(it) },
                )
            }
        } else {
            // Corner join applies to shapes with corners (rect/polygon/star/vector), not 2-point lines.
            Spacer(Modifier.height(6.dp))
            LabeledField("Join") {
                SelectField(
                    value = strokes.join,
                    options = listOf("miter", "round", "bevel"),
                    onSelect = { state.dispatch(DesignEditorIntent.StrokeCommand(nodeId, StrokeOp.SetJoin(it))) },
                    leadingContent = { StrokeJoinPreview(strokes.join) },
                    optionLeadingContent = { StrokeJoinPreview(it) },
                )
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
        ShapeActions(state, node, locked)
    }
}

/** Flatten / Outline-stroke actions available on shapes and boolean groups. */
@Composable
private fun ShapeActions(state: MissionEditorStateHolder, node: DesignNode, locked: Boolean) {
    val nodeId = node.id
    val canOutline = node.kind is DesignNodeKind.Shape && node.strokes != null
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        TinyButton("Flatten", enabled = !locked) {
            state.dispatch(DesignEditorIntent.FlattenNode(nodeId))
        }
        if (canOutline) {
            TinyButton("Outline stroke", enabled = !locked) {
                state.dispatch(DesignEditorIntent.OutlineStroke(nodeId))
            }
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
            leadingContent = { ShapeTypePreview(shape.shape) },
            optionLeadingContent = { label -> ShapeType.entries.firstOrNull { it.shapeTypeLabel() == label }?.let { ShapeTypePreview(it) } },
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
    if (shape.shape == ShapeType.Ellipse) {
        Spacer(Modifier.height(8.dp))
        ArcControls(state, nodeId, shape, locked)
    }
    if (shape.shape == ShapeType.Vector) {
        Spacer(Modifier.height(8.dp))
        VectorControls(state, nodeId, shape, locked)
        VertexControls(state, nodeId, shape, locked)
    }
}

/** Per-vertex controls shown while editing a vector network and a vertex is selected. */
@Composable
private fun VertexControls(state: MissionEditorStateHolder, nodeId: String, shape: DesignNodeKind.Shape, locked: Boolean) {
    val ws = state.workspace
    if (ws.vectorEditNodeId != nodeId) return
    val ref = ws.vectorSelectedVertex ?: return
    val vertex = shape.network?.vertices?.getOrNull(ref.vertexIndex) ?: return
    Spacer(Modifier.height(10.dp))
    InspectorSubLabel("Point")
    Spacer(Modifier.height(6.dp))
    LabeledField("Mirror") {
        SelectField(
            value = mirrorLabel(vertex.mirror),
            options = HandleMirror.entries.map { mirrorLabel(it) },
            onSelect = { label ->
                if (!locked) {
                    HandleMirror.entries.firstOrNull { mirrorLabel(it) == label }
                        ?.let { state.dispatch(DesignEditorIntent.SetVertexMirror(nodeId, ref.vertexIndex, it)) }
                }
            },
            leadingContent = { MirrorPreview(vertex.mirror) },
            optionLeadingContent = { label -> HandleMirror.entries.firstOrNull { mirrorLabel(it) == label }?.let { MirrorPreview(it) } },
        )
    }
    Spacer(Modifier.height(6.dp))
    CheckRow("Sharp corner", vertex.corner) {
        if (!locked) state.dispatch(DesignEditorIntent.ToggleVertexCorner(nodeId, ref.vertexIndex))
    }
    Spacer(Modifier.height(6.dp))
    CompactLabeledNumberField("Radius", vertex.cornerRadius.formatPx(), "vradius-$nodeId-${ref.vertexIndex}", enabled = !locked) {
        state.dispatch(DesignEditorIntent.SetVertexCornerRadius(nodeId, ref.vertexIndex, it.coerceAtLeast(0.0)))
    }
}

private fun mirrorLabel(mirror: HandleMirror): String = when (mirror) {
    HandleMirror.None -> "No mirror"
    HandleMirror.Angle -> "Mirror angle"
    HandleMirror.AngleAndLength -> "Mirror angle & length"
}

@Composable
private fun MirrorPreview(mirror: HandleMirror, modifier: Modifier = Modifier.size(18.dp)) {
    val colors = LocalEditorColors.current
    Canvas(modifier) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val ink = colors.controlInk
        val accent = colors.accent
        when (mirror) {
            HandleMirror.None -> {
                // Bent tangents (independent handles).
                drawLine(accent, c, Offset(c.x - 6f, c.y - 2f), strokeWidth = 1.5.dp.toPx())
                drawLine(accent, c, Offset(c.x + 5f, c.y + 5f), strokeWidth = 1.5.dp.toPx())
            }
            HandleMirror.Angle -> {
                // Colinear, unequal length.
                drawLine(accent, Offset(c.x - 6f, c.y - 4f), c, strokeWidth = 1.5.dp.toPx())
                drawLine(accent, c, Offset(c.x + 4f, c.y + 2.7f), strokeWidth = 1.5.dp.toPx())
            }
            HandleMirror.AngleAndLength -> {
                // Colinear, equal length.
                drawLine(accent, Offset(c.x - 6f, c.y - 4f), Offset(c.x + 6f, c.y + 4f), strokeWidth = 1.5.dp.toPx())
            }
        }
        drawCircle(ink, radius = 2.5f, center = c)
    }
}

/** Ellipse arc (pie/donut) controls: start angle, sweep %, donut ratio %. */
@Composable
private fun ArcControls(state: MissionEditorStateHolder, nodeId: String, shape: DesignNodeKind.Shape, locked: Boolean) {
    val colors = LocalEditorColors.current
    CompactLabeledNumberField("Start", (shape.arcStartDeg ?: 0.0).formatPx(), "arc-start-$nodeId", enabled = !locked) {
        state.dispatch(DesignEditorIntent.SetArcStart(nodeId, it))
    }
    Spacer(Modifier.height(8.dp))
    val sweep = (shape.arcSweepDeg ?: 360.0).coerceIn(0.0, 360.0)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Sweep", style = MaterialTheme.typography.bodySmall, color = colors.controlInk)
        Text("${(sweep / 360.0 * 100).roundToInt()}%", style = MaterialTheme.typography.bodySmall)
        UndoableSlider(
            value = (sweep / 360.0 * 100).toFloat(),
            valueRange = 0f..100f,
            enabled = !locked,
            onBegin = { state.dispatch(DesignEditorIntent.BeginInteraction) },
            onChange = { v -> state.dispatch(DesignEditorIntent.SetArcSweep(nodeId, v / 100.0 * 360.0)) },
            onEnd = { state.dispatch(DesignEditorIntent.EndInteraction) },
            modifier = Modifier.weight(1f),
        )
    }
    Spacer(Modifier.height(8.dp))
    val ratio = (shape.innerRadius ?: 0.0).coerceIn(0.0, 1.0)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Ratio", style = MaterialTheme.typography.bodySmall, color = colors.controlInk)
        Text("${(ratio * 100).roundToInt()}%", style = MaterialTheme.typography.bodySmall)
        UndoableSlider(
            value = (ratio * 100).toFloat(),
            valueRange = 0f..100f,
            enabled = !locked,
            onBegin = { state.dispatch(DesignEditorIntent.BeginInteraction) },
            onChange = { v -> state.dispatch(DesignEditorIntent.SetArcRatio(nodeId, v / 100.0)) },
            onEnd = { state.dispatch(DesignEditorIntent.EndInteraction) },
            modifier = Modifier.weight(1f),
        )
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
    if (shape.network?.isNotEmpty() == true) {
        Spacer(Modifier.height(8.dp))
        val rule = shape.network?.regions?.firstOrNull()?.windingRule ?: "nonzero"
        LabeledField("Fill rule") {
            SelectField(
                value = fillRuleLabel(rule),
                options = listOf(fillRuleLabel("nonzero"), fillRuleLabel("evenodd")),
                onSelect = { label ->
                    if (!locked) state.dispatch(DesignEditorIntent.SetWindingRule(nodeId, fillRuleValue(label)))
                },
                leadingContent = { FillRulePreview(rule) },
                optionLeadingContent = { FillRulePreview(fillRuleValue(it)) },
            )
        }
        val regions = shape.network?.regions ?: emptyList()
        if (regions.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            InspectorSubLabel("Region fills")
            PaintBucketToggle(state, nodeId, locked)
            regions.indices.forEach { index ->
                RegionFillGroup(state, nodeId, index, regions.size, shape.regionFills[index].orEmpty(), locked)
            }
        }
    } else {
        Spacer(Modifier.height(8.dp))
        MutedNote("No editable geometry yet — convert to edit points on the canvas.")
        Spacer(Modifier.height(6.dp))
        TinyButton("Convert to editable", enabled = !locked) {
            state.dispatch(DesignEditorIntent.ConvertToEditableVector(nodeId))
        }
    }
}

private fun fillRuleLabel(rule: String): String = if (rule == "evenodd") "Even-odd" else "Nonzero"

private fun fillRuleValue(label: String): String = if (label == "Even-odd") "evenodd" else "nonzero"

/**
 * Toggles the canvas paint-bucket sub-mode of vector edit: enabling it enters vector-edit for this
 * node so a canvas press fills the clicked region with [swatch]; a color field picks that fill.
 */
@Composable
private fun PaintBucketToggle(state: MissionEditorStateHolder, nodeId: String, locked: Boolean) {
    val colors = LocalEditorColors.current
    val active = state.workspace.vectorPaintBucket && state.workspace.vectorEditNodeId == nodeId
    val bucketColor = state.workspace.vectorPaintBucketColor
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        TinyButton(if (active) "Paint bucket: on" else "Paint bucket: off", enabled = !locked) {
            state.updateWorkspace {
                if (active) {
                    it.copy(vectorPaintBucket = false)
                } else {
                    it.copy(vectorPaintBucket = true, vectorEditNodeId = nodeId)
                }
            }
        }
        Box(Modifier.weight(1f)) {
            InspectorColorField(
                state = state,
                color = bucketColor,
                opacity = 1.0,
                label = bucketColor.toHex(),
                enabled = !locked,
                onColor = { picked -> state.updateWorkspace { it.copy(vectorPaintBucketColor = picked) } },
                onOpacity = { },
            )
        }
    }
    if (active) {
        Text(
            "Click a region on the canvas to fill it.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.mutedInk,
        )
    }
    Spacer(Modifier.height(8.dp))
}

/**
 * One vector-network region's full paint list: a "Region N" header with an add-fill affordance and
 * a [FillRow] per paint (solid + gradient, opacity, swatch), each committing through
 * [DesignEditorIntent.RegionFillCommand]. Rendered even for single-region networks so any closed
 * shape can carry an explicit region fill.
 */
@Composable
private fun RegionFillGroup(
    state: MissionEditorStateHolder,
    nodeId: String,
    index: Int,
    regionCount: Int,
    fills: List<DesignPaint>,
    locked: Boolean,
) {
    val label = if (regionCount > 1) "Region ${index + 1}" else "Region fill"
    val onOp: (FillOp) -> Unit = { if (!locked) state.dispatch(DesignEditorIntent.RegionFillCommand(nodeId, index, it)) }
    SectionHeaderAdd(label) { onOp(FillOp.Add) }
    if (fills.isEmpty()) {
        MutedNote("No fill. Add one with +.")
    } else {
        fills.forEachIndexed { paintIndex, paint ->
            FillRow(state, "region-$nodeId-$index", paintIndex, paint, !locked, onOp)
            Spacer(Modifier.height(6.dp))
        }
    }
    Spacer(Modifier.height(8.dp))
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
            leadingContent = { BooleanOperationPreview(kind.operation) },
            optionLeadingContent = { label ->
                BooleanOperationKind.entries.firstOrNull { it.booleanOpLabel() == label }?.let { BooleanOperationPreview(it) }
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

/** Theme bridge for the figures-compose figure previews (shape/boolean glyphs). */
@Composable
private fun figurePreviewStyle(): FigurePreviewStyle {
    val colors = LocalEditorColors.current
    return FigurePreviewStyle(
        ink = colors.controlInk,
        fill = colors.selectionFill,
        accent = colors.accent,
        surface = colors.raisedSurface,
    )
}

@Composable
private fun ShapeTypePreview(shape: ShapeType, modifier: Modifier = Modifier.size(18.dp)) =
    FigureShapePreview(shape, figurePreviewStyle(), modifier)

@Composable
private fun BooleanOperationPreview(operation: BooleanOperationKind, modifier: Modifier = Modifier.size(18.dp)) =
    FigureBooleanPreview(operation, figurePreviewStyle(), modifier)

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
            val type = effect.effectType()
            SmallSelect(
                value = effect.effectLabel(),
                options = EffectType.entries.map { it.displayName },
                modifier = Modifier.weight(1f),
                leadingContent = { EffectTypeIcon(type) },
                optionLeadingContent = { label -> EffectType.entries.firstOrNull { it.displayName == label }?.let { EffectTypeIcon(it) } },
            ) { label ->
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
    val fontProvider = rememberBundledFontProvider()
    val families = fontProvider.families

    // Per-range vs node-level editing: a live non-collapsed text selection edits that range;
    // otherwise the whole node's style is edited (design-book "mixed" text styling).
    val sel = state.workspace.textSelection
    val rangeActive = sel != null && sel.nodeId == nodeId && !sel.isCollapsed
    val nodeStyle = kind.textStyle ?: DesignTextStyle()
    val editableLength = (
        kind.content?.defaultText?.takeIf { it.isNotEmpty() } ?: kind.characters.literalOrNull() ?: ""
    ).length
    val runs = if (sel != null && rangeActive) {
        TextRangeEditing.runsInRange(nodeStyle, kind.styleRanges, editableLength, sel.min, sel.max)
    } else {
        listOf(nodeStyle to null)
    }
    val styles = runs.map { it.first }
    val cur = styles.first()
    fun mixed(selector: (DesignTextStyle) -> Any?): Boolean = styles.map(selector).distinct().size > 1
    val resetKey = "type-$nodeId-${if (sel != null && rangeActive) "${sel.min}-${sel.max}" else "node"}"

    fun applyPatch(patch: TypographyPatch) {
        if (sel != null && rangeActive) {
            state.dispatch(DesignEditorIntent.UpdateTypographyRange(nodeId, sel.min, sel.max, patch))
        } else {
            state.dispatch(DesignEditorIntent.UpdateTypography(nodeId, patch))
        }
    }

    // Row 1 — font family, full width, each row rendered in its own typeface.
    val family = cur.fontFamily ?: ""
    val familyPreview = families.firstOrNull { it.family == family }?.preview
    FontFamilyField(
        families = families,
        selected = family,
        mixed = mixed { it.fontFamily },
        modifier = Modifier.fillMaxWidth(),
        onSelect = { applyPatch(TypographyPatch(fontFamily = it)) },
    )
    Spacer(Modifier.height(8.dp))

    // Shared document text styles — apply one to the active range (or the whole node).
    val textStyleIds = state.designState.document?.styles.orEmpty()
        .filterValues { it is DesignStyle.Text }
        .keys.toList()
    if (textStyleIds.isNotEmpty()) {
        val styleColors = LocalEditorColors.current
        CompactLabeledSelectField(
            label = "Text style",
            value = "Apply style",
            options = textStyleIds,
            modifier = Modifier.fillMaxWidth(),
            leadingContent = { DropdownMenuIcon(EditorIcon.Typography, modifier = Modifier.size(13.dp), tint = styleColors.ink) },
            optionLeadingContent = { DropdownMenuIcon(EditorIcon.Typography, modifier = Modifier.size(16.dp)) },
            onSelect = { styleId ->
                if (sel != null && rangeActive) {
                    state.dispatch(DesignEditorIntent.SetTextRangeStyleRef(nodeId, sel.min, sel.max, styleId))
                } else {
                    state.dispatch(DesignEditorIntent.SetTextRangeStyleRef(nodeId, 0, editableLength, styleId))
                }
            },
        )
        Spacer(Modifier.height(8.dp))
    }

    // Row 2 — named style (weight + italic) · italic quick-toggle · size with presets.
    val weight = cur.fontWeight?.literalOrNull() ?: 400.0
    val italic = cur.italic ?: false
    val mixedStyle = mixed { it.fontWeight?.literalOrNull() } || mixed { it.italic }
    val size = cur.fontSize?.literalOrNull() ?: 16.0
    val mixedSize = mixed { it.fontSize?.literalOrNull() }
    val styleOptions = (
        families.firstOrNull { it.family == family }?.styles?.takeIf { it.isNotEmpty() } ?: FontStyles.STANDARD
    ).map { it.name }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompactSelectField(
            value = if (mixedStyle) "Mixed" else FontStyles.nameFor(weight.roundToInt(), italic),
            options = styleOptions,
            onSelect = { name ->
                val parsed = FontStyles.parse(name)
                applyPatch(TypographyPatch(fontWeight = parsed.weight.toDouble(), italic = parsed.italic))
            },
            modifier = Modifier.weight(1f),
            leadingContent = { FontStyleGlyph(weight.roundToInt(), italic, familyPreview) },
            optionLeadingContent = { name ->
                val parsed = FontStyles.parse(name)
                FontStyleGlyph(parsed.weight, parsed.italic, familyPreview)
            },
        )
        IconButtonStrip(
            items = listOf(
                IconStripItem(EditorIcon.FormatItalic, "Italic", active = !mixedStyle && italic) {
                    applyPatch(TypographyPatch(italic = !italic))
                },
            ),
            modifier = Modifier.width(30.dp),
        )
        FontSizeField(
            value = if (mixedSize) "" else size.formatPx(),
            placeholder = if (mixedSize) "Mixed" else "",
            resetKey = "size-$resetKey",
            modifier = Modifier.width(96.dp),
            onCommit = { applyPatch(TypographyPatch(fontSize = it)) },
        )
    }
    Spacer(Modifier.height(8.dp))

    // Row 3 — line height (Auto | %) · letter spacing (%).
    val lineHeightUnit = cur.lineHeight
    val isAuto = lineHeightUnit == null
    val mixedLine = mixed { it.lineHeight }
    val letter = cur.letterSpacing?.value ?: 0.0
    val mixedLetter = mixed { it.letterSpacing?.value }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val fieldWidth = inspectorPairFieldWidth(maxWidth, minWidth = 96.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LineHeightField(
                isAuto = isAuto && !mixedLine,
                value = if (mixedLine || isAuto) "" else lineHeightUnit.value.formatPx(),
                placeholder = if (mixedLine) "Mixed" else if (isAuto) "Auto" else "",
                resetKey = "line-$resetKey",
                modifier = Modifier.width(fieldWidth),
                onToggleAuto = { toAuto ->
                    applyPatch(
                        TypographyPatch(
                            lineHeight = if (toAuto) LineHeightPatch(auto = true) else LineHeightPatch(percent = 120.0),
                        ),
                    )
                },
                onValue = { applyPatch(TypographyPatch(lineHeight = LineHeightPatch(percent = it))) },
            )
            CompactNumberField(
                label = "",
                value = if (mixedLetter) "" else letter.formatPx(),
                resetKey = "letter-$resetKey",
                modifier = Modifier.width(fieldWidth),
                suffix = "%",
                placeholder = if (mixedLetter) "Mixed" else "",
                onCommit = { applyPatch(TypographyPatch(letterSpacingPercent = it)) },
            )
        }
    }
    Spacer(Modifier.height(8.dp))

    // Row 4 — horizontal align · vertical align · advanced-settings toggle.
    val alignH = cur.textAlignHorizontal ?: TextAlignHorizontal.Left
    val alignV = cur.textAlignVertical ?: TextAlignVertical.Top
    var advanced by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        IconButtonStrip(
            items = listOf(
                IconStripItem(EditorIcon.AlignHorizontalLeft, "Align left", active = alignH == TextAlignHorizontal.Left) { applyPatch(TypographyPatch(alignHorizontal = TextAlignHorizontal.Left)) },
                IconStripItem(EditorIcon.AlignHorizontalCenter, "Align center", active = alignH == TextAlignHorizontal.Center) { applyPatch(TypographyPatch(alignHorizontal = TextAlignHorizontal.Center)) },
                IconStripItem(EditorIcon.AlignHorizontalRight, "Align right", active = alignH == TextAlignHorizontal.Right) { applyPatch(TypographyPatch(alignHorizontal = TextAlignHorizontal.Right)) },
                IconStripItem(EditorIcon.FormatAlignJustify, "Justify", active = alignH == TextAlignHorizontal.Justified) { applyPatch(TypographyPatch(alignHorizontal = TextAlignHorizontal.Justified)) },
            ),
            modifier = Modifier.weight(4f),
        )
        IconButtonStrip(
            items = listOf(
                IconStripItem(EditorIcon.VerticalAlignTop, "Align top", active = alignV == TextAlignVertical.Top) { applyPatch(TypographyPatch(alignVertical = TextAlignVertical.Top)) },
                IconStripItem(EditorIcon.VerticalAlignCenter, "Align middle", active = alignV == TextAlignVertical.Center) { applyPatch(TypographyPatch(alignVertical = TextAlignVertical.Center)) },
                IconStripItem(EditorIcon.VerticalAlignBottom, "Align bottom", active = alignV == TextAlignVertical.Bottom) { applyPatch(TypographyPatch(alignVertical = TextAlignVertical.Bottom)) },
            ),
            modifier = Modifier.weight(3f),
        )
        IconButtonStrip(
            items = listOf(
                IconStripItem(EditorIcon.Tune, "Type settings", active = advanced) { advanced = !advanced },
            ),
            modifier = Modifier.weight(1f),
        )
    }

    if (advanced) {
        Spacer(Modifier.height(12.dp))
        TypographyAdvanced(
            state = state,
            nodeId = nodeId,
            kind = kind,
            styles = styles,
            resetKey = resetKey,
            onPatch = { applyPatch(it) },
        )
    }
}

/** The collapsible advanced text controls revealed by the "tune" toggle in [TypographySection]. */
@Composable
private fun TypographyAdvanced(
    state: MissionEditorStateHolder,
    nodeId: String,
    kind: DesignNodeKind.Text,
    styles: List<DesignTextStyle>,
    resetKey: String,
    onPatch: (TypographyPatch) -> Unit,
) {
    fun mixed(selector: (DesignTextStyle) -> Any?): Boolean = styles.map(selector).distinct().size > 1
    val s = styles.first()
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Resize (node-level).
        LabeledField("Resize") {
            SegmentedControl(
                options = listOf(TextAutoResize.WidthAndHeight, TextAutoResize.Height, TextAutoResize.None),
                selected = kind.autoResize,
                label = { it.autoResizeLabel() },
                onSelect = { state.dispatch(DesignEditorIntent.SetTextAutoResize(nodeId, it)) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        // Truncate (node-level): toggle + max-lines.
        val truncate = kind.truncate
        LabeledField("Truncate") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                TypoToggleChip(label = "...", active = truncate != null, modifier = Modifier.width(38.dp)) {
                    state.dispatch(
                        DesignEditorIntent.SetTextTruncate(
                            nodeId,
                            if (truncate != null) null else TextTruncate(maxLines = 1),
                        ),
                    )
                }
                CompactNumberField(
                    label = "Lines",
                    value = (truncate?.maxLines ?: 1).toDouble().formatPx(),
                    resetKey = "trunc-$resetKey",
                    modifier = Modifier.weight(1f),
                    enabled = truncate != null,
                    onCommit = { state.dispatch(DesignEditorIntent.SetTextTruncate(nodeId, TextTruncate(maxLines = it.roundToInt().coerceAtLeast(1)))) },
                )
            }
        }
        // Decoration + style + skip-ink.
        val decoration = s.textDecoration ?: TextDecorationKind.None
        val mixedDecoration = mixed { it.textDecoration }
        LabeledField("Decor") {
            IconButtonStrip(
                items = listOf(
                    IconStripItem(EditorIcon.Text, "No decoration", active = !mixedDecoration && decoration == TextDecorationKind.None) { onPatch(TypographyPatch(textDecoration = TextDecorationKind.None)) },
                    IconStripItem(EditorIcon.FormatUnderlined, "Underline", active = !mixedDecoration && decoration == TextDecorationKind.Underline) { onPatch(TypographyPatch(textDecoration = TextDecorationKind.Underline)) },
                    IconStripItem(EditorIcon.FormatStrikethrough, "Strikethrough", active = !mixedDecoration && decoration == TextDecorationKind.Strikethrough) { onPatch(TypographyPatch(textDecoration = TextDecorationKind.Strikethrough)) },
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (decoration != TextDecorationKind.None) {
            LabeledField("Line") {
                SegmentedControl(
                    options = listOf(TextDecorationStyle.Solid, TextDecorationStyle.Dashed, TextDecorationStyle.Dotted, TextDecorationStyle.Wavy),
                    selected = s.decorationStyle ?: TextDecorationStyle.Solid,
                    label = { it.decorationStyleLabel() },
                    onSelect = { onPatch(TypographyPatch(decorationStyle = it)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            CheckRow("Skip ink", s.decorationSkipInk == true) { onPatch(TypographyPatch(decorationSkipInk = it)) }
        }
        // Case.
        LabeledField("Case") {
            SegmentedControl(
                options = listOf(TextCase.None, TextCase.Upper, TextCase.Lower, TextCase.Title, TextCase.SmallCaps),
                selected = s.textCase ?: TextCase.None,
                label = { it.caseLabel() },
                onSelect = { onPatch(TypographyPatch(textCase = it)) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        // Position (super / sub).
        val position = s.textPosition ?: TextScriptPosition.None
        LabeledField("Position") {
            IconButtonStrip(
                items = listOf(
                    IconStripItem(EditorIcon.Text, "Baseline", active = position == TextScriptPosition.None) { onPatch(TypographyPatch(textPosition = TextScriptPosition.None)) },
                    IconStripItem(EditorIcon.Superscript, "Superscript", active = position == TextScriptPosition.Superscript) { onPatch(TypographyPatch(textPosition = TextScriptPosition.Superscript)) },
                    IconStripItem(EditorIcon.Subscript, "Subscript", active = position == TextScriptPosition.Subscript) { onPatch(TypographyPatch(textPosition = TextScriptPosition.Subscript)) },
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        // List (node-level) + indent.
        val list = kind.list
        LabeledField("List") {
            IconButtonStrip(
                items = listOf(
                    IconStripItem(EditorIcon.Text, "No list", active = list.type == TextListType.None) { state.dispatch(DesignEditorIntent.SetTextList(nodeId, list.copy(type = TextListType.None))) },
                    IconStripItem(EditorIcon.FormatListBulleted, "Bulleted", active = list.type == TextListType.Bullet) { state.dispatch(DesignEditorIntent.SetTextList(nodeId, list.copy(type = TextListType.Bullet))) },
                    IconStripItem(EditorIcon.FormatListNumbered, "Numbered", active = list.type == TextListType.Ordered) { state.dispatch(DesignEditorIntent.SetTextList(nodeId, list.copy(type = TextListType.Ordered))) },
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (list.type != TextListType.None) {
            LabeledField("Indent") {
                CompactNumberField(
                    label = "",
                    value = list.indent.toDouble().formatPx(),
                    resetKey = "list-indent-$resetKey",
                    modifier = Modifier.width(96.dp),
                    onCommit = { state.dispatch(DesignEditorIntent.SetTextList(nodeId, list.copy(indent = it.roundToInt().coerceAtLeast(0)))) },
                )
            }
        }
        // Paragraph spacing + first-line indent.
        LabeledField("Para space") {
            CompactNumberField(
                label = "",
                value = if (mixed { it.paragraphSpacing }) "" else (s.paragraphSpacing ?: 0.0).formatPx(),
                resetKey = "para-space-$resetKey",
                modifier = Modifier.width(120.dp),
                suffix = "px",
                placeholder = if (mixed { it.paragraphSpacing }) "Mixed" else "",
                onCommit = { onPatch(TypographyPatch(paragraphSpacing = it)) },
            )
        }
        LabeledField("Para indent") {
            CompactNumberField(
                label = "",
                value = if (mixed { it.paragraphIndent }) "" else (s.paragraphIndent ?: 0.0).formatPx(),
                resetKey = "para-indent-$resetKey",
                modifier = Modifier.width(120.dp),
                suffix = "px",
                placeholder = if (mixed { it.paragraphIndent }) "Mixed" else "",
                onCommit = { onPatch(TypographyPatch(paragraphIndent = it)) },
            )
        }
        // Leading trim.
        LabeledField("Trim") {
            SegmentedControl(
                options = listOf(LeadingTrim.None, LeadingTrim.CapHeight),
                selected = s.leadingTrim ?: LeadingTrim.None,
                label = { it.trimLabel() },
                onSelect = { onPatch(TypographyPatch(leadingTrim = it)) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        CheckRow("Hanging punctuation", s.hangingPunctuation == true) { onPatch(TypographyPatch(hangingPunctuation = it)) }
        // Common OpenType feature toggles.
        InspectorSubLabel("OpenType")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("liga", "tnum", "frac", "smcp").forEach { feature ->
                val on = s.fontFeatures[feature] == true
                TypoToggleChip(label = feature, active = on, modifier = Modifier.weight(1f)) {
                    onPatch(TypographyPatch(fontFeatures = mapOf(feature to !on)))
                }
            }
        }
        // Variable-font axes: a number field per registered axis; empty leaves it unset.
        InspectorSubLabel("Variable axes")
        VariableAxes(kind.textStyle?.variableAxes.orEmpty(), resetKey) { axis, value ->
            onPatch(TypographyPatch(variableAxes = mapOf(axis to value)))
        }
    }
}

private val VariableAxisRanges = listOf(
    Triple("wght", "Weight", 100.0..900.0),
    Triple("opsz", "Optical", 8.0..144.0),
    Triple("wdth", "Width", 25.0..200.0),
    Triple("slnt", "Slant", -15.0..0.0),
)

@Composable
private fun VariableAxes(axes: Map<String, Double>, resetKey: String, onAxis: (String, Double) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        VariableAxisRanges.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { (tag, label, range) ->
                    CompactNumberField(
                        label = label,
                        value = axes[tag]?.formatPx() ?: "",
                        resetKey = "axis-$tag-$resetKey",
                        modifier = Modifier.weight(1f),
                        placeholder = "—",
                    ) { onAxis(tag, it.coerceIn(range.start, range.endInclusive)) }
                }
            }
        }
    }
}

/** Full-width font-family picker; the field and every row render in that family's own typeface. */
@Composable
private fun FontFamilyField(
    families: List<FontFamilyInfo>,
    selected: String,
    mixed: Boolean,
    modifier: Modifier = Modifier,
    onSelect: (String) -> Unit,
) {
    val colors = LocalEditorColors.current
    var expanded by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(5.dp)
    val currentPreview = families.firstOrNull { it.family == selected }?.preview
    Box(modifier) {
        Surface(
            modifier = Modifier.fillMaxWidth().height(26.dp).clip(shape).clickable { expanded = true },
            shape = shape,
            color = colors.controlSurface,
            border = BorderStroke(1.dp, if (expanded) colors.accent else colors.controlStroke),
        ) {
            Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                DropdownLeadingBox(size = 16.dp) {
                    FontFamilyGlyph(if (mixed) null else currentPreview, colors.ink)
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    if (mixed) "Mixed" else selected.ifBlank { "Default" },
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (mixed) colors.mutedInk else colors.ink,
                    fontFamily = if (mixed) null else currentPreview,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
                EditorSvgIcon(EditorIcon.ChevronDown, contentDescription = "Choose font", modifier = Modifier.size(11.dp), tint = colors.controlInk)
            }
        }
        EditorDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (families.isEmpty()) {
                EditorDropdownMenuItem(
                    text = "No fonts available",
                    leadingContent = { DropdownMenuIcon(EditorIcon.Typography, modifier = Modifier.size(16.dp)) },
                    onClick = { expanded = false },
                )
            }
            families.forEach { info ->
                DropdownMenuItem(
                    text = {
                        Text(
                            info.family,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.ink,
                            fontFamily = info.preview,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    onClick = { expanded = false; onSelect(info.family) },
                    leadingIcon = {
                        DropdownLeadingBox(size = 18.dp) {
                            FontFamilyGlyph(info.preview, colors.ink)
                        }
                    },
                )
            }
        }
    }
}

/** "Ag" preview rendered in [preview]'s typeface (custom dropdown visual for a font family). */
@Composable
private fun FontFamilyGlyph(preview: FontFamily?, tint: Color, modifier: Modifier = Modifier) {
    Text(
        "Ag",
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall,
        color = tint,
        fontFamily = preview,
        maxLines = 1,
        softWrap = false,
    )
}

/** "A" preview at a given weight/italic — the leading visual of the named-style dropdown. */
@Composable
private fun FontStyleGlyph(weight: Int, italic: Boolean, preview: FontFamily?, modifier: Modifier = Modifier) {
    Text(
        "A",
        modifier = modifier,
        style = MaterialTheme.typography.labelMedium,
        color = LocalEditorColors.current.ink,
        fontFamily = preview,
        fontWeight = FontWeight(weight.coerceIn(1, 1000)),
        fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
        maxLines = 1,
        softWrap = false,
    )
}

/** Numeric font-size field with a chevron that opens the standard size presets. */
@Composable
private fun FontSizeField(
    value: String,
    placeholder: String,
    resetKey: String,
    modifier: Modifier = Modifier,
    onCommit: (Double) -> Unit,
) {
    val colors = LocalEditorColors.current
    var expanded by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(5.dp)
    Box(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            CompactNumberField(
                label = "",
                value = value,
                resetKey = resetKey,
                modifier = Modifier.weight(1f),
                placeholder = placeholder,
                onCommit = onCommit,
            )
            Surface(
                modifier = Modifier.size(26.dp).clip(shape).clickable { expanded = true },
                shape = shape,
                color = colors.controlSurface,
                border = BorderStroke(1.dp, colors.controlStroke),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    EditorSvgIcon(EditorIcon.ChevronDown, contentDescription = "Size presets", modifier = Modifier.size(11.dp), tint = colors.controlInk)
                }
            }
        }
        EditorDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            listOf(12, 14, 16, 24, 32, 48, 64).forEach { preset ->
                EditorDropdownMenuItem(
                    text = "$preset",
                    leadingContent = { DropdownMenuIcon(EditorIcon.Typography, modifier = Modifier.size(16.dp)) },
                    onClick = { expanded = false; onCommit(preset.toDouble()) },
                )
            }
        }
    }
}

/** Line-height control: an "Auto" toggle segment followed by the % value entry. */
@Composable
private fun LineHeightField(
    isAuto: Boolean,
    value: String,
    placeholder: String,
    resetKey: String,
    modifier: Modifier = Modifier,
    onToggleAuto: (Boolean) -> Unit,
    onValue: (Double) -> Unit,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        IconButtonStrip(
            items = listOf(
                IconStripItem(EditorIcon.FormatLineSpacing, "Auto line height", active = isAuto) { onToggleAuto(!isAuto) },
            ),
            modifier = Modifier.width(30.dp),
        )
        CompactNumberField(
            label = "",
            value = value,
            resetKey = resetKey,
            modifier = Modifier.weight(1f),
            enabled = !isAuto,
            suffix = "%",
            placeholder = placeholder,
            onCommit = onValue,
        )
    }
}

/** Small pill toggle for boolean text options (skip-ink, OpenType features, truncation). */
@Composable
private fun TypoToggleChip(label: String, active: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = LocalEditorColors.current
    val shape = RoundedCornerShape(5.dp)
    Surface(
        modifier = modifier.height(26.dp).clip(shape).clickable(onClick = onClick),
        shape = shape,
        color = if (active) colors.selectionFill else colors.controlSurface,
        border = BorderStroke(1.dp, if (active) colors.accent else colors.controlStroke),
    ) {
        Box(Modifier.padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = if (active) colors.accent else colors.ink,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun TextDecorationStyle.decorationStyleLabel() = when (this) {
    TextDecorationStyle.Solid -> "Solid"
    TextDecorationStyle.Dashed -> "Dash"
    TextDecorationStyle.Dotted -> "Dot"
    TextDecorationStyle.Wavy -> "Wave"
}

private fun TextCase.caseLabel() = when (this) {
    TextCase.None -> "—"
    TextCase.Upper -> "AG"
    TextCase.Lower -> "ag"
    TextCase.Title -> "Ag"
    TextCase.SmallCaps -> "SC"
    TextCase.SmallCapsForced -> "SC!"
}

private fun LeadingTrim.trimLabel() = when (this) {
    LeadingTrim.None -> "None"
    LeadingTrim.CapHeight -> "Cap"
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
    leadingContent: (@Composable () -> Unit)? = null,
    optionLeadingContent: (@Composable (String) -> Unit)? = null,
    onSelect: (String) -> Unit,
) {
    LabeledField(label) {
        CompactSelectField(
            value = value,
            options = options,
            onSelect = onSelect,
            modifier = modifier.widthIn(max = maxFieldWidth).fillMaxWidth(),
            enabled = enabled,
            leadingContent = leadingContent,
            optionLeadingContent = optionLeadingContent,
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
    leadingContent: (@Composable () -> Unit)? = null,
    optionLeadingContent: (@Composable (String) -> Unit)? = null,
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
                DropdownLeadingBox(size = 14.dp) {
                    when {
                        leadingContent != null -> leadingContent()
                        leadingIcon != null -> DropdownMenuIcon(leadingIcon, modifier = Modifier.size(13.dp), tint = if (enabled) colors.ink else colors.mutedInk)
                        else -> DefaultDropdownLeadingContent(value, modifier = Modifier.size(13.dp), tint = if (enabled) colors.ink else colors.mutedInk)
                    }
                }
                Spacer(Modifier.width(6.dp))
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
                    leadingContent = {
                        when {
                            optionLeadingContent != null -> optionLeadingContent(option)
                            leadingIcon != null -> DropdownMenuIcon(leadingIcon, modifier = Modifier.size(16.dp))
                            else -> DefaultDropdownLeadingContent(option, modifier = Modifier.size(16.dp))
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun SizingModePreview(mode: SizingMode, modifier: Modifier = Modifier.size(14.dp)) {
    val colors = LocalEditorColors.current
    Canvas(modifier) {
        val stroke = Stroke(1.3.dp.toPx())
        val ink = colors.controlInk
        val fill = colors.selectionFill
        when (mode) {
            SizingMode.Fixed -> {
                drawRoundRect(fill, Offset(2f, 3f), Size(size.width - 4f, size.height - 6f), CornerRadius(2f, 2f))
                drawRoundRect(ink, Offset(2f, 3f), Size(size.width - 4f, size.height - 6f), CornerRadius(2f, 2f), style = stroke)
            }
            SizingMode.Hug -> {
                drawLine(ink, Offset(3f, 3f), Offset(3f, size.height - 3f), strokeWidth = 1.5.dp.toPx())
                drawLine(ink, Offset(size.width - 3f, 3f), Offset(size.width - 3f, size.height - 3f), strokeWidth = 1.5.dp.toPx())
                drawRoundRect(fill, Offset(5f, 5f), Size(size.width - 10f, size.height - 10f), CornerRadius(2f, 2f))
            }
            SizingMode.Fill -> {
                drawLine(ink, Offset(2f, size.height / 2f), Offset(size.width - 2f, size.height / 2f), strokeWidth = 1.5.dp.toPx())
                drawLine(ink, Offset(2f, size.height / 2f), Offset(5f, size.height / 2f - 3f), strokeWidth = 1.5.dp.toPx())
                drawLine(ink, Offset(2f, size.height / 2f), Offset(5f, size.height / 2f + 3f), strokeWidth = 1.5.dp.toPx())
                drawLine(ink, Offset(size.width - 2f, size.height / 2f), Offset(size.width - 5f, size.height / 2f - 3f), strokeWidth = 1.5.dp.toPx())
                drawLine(ink, Offset(size.width - 2f, size.height / 2f), Offset(size.width - 5f, size.height / 2f + 3f), strokeWidth = 1.5.dp.toPx())
            }
        }
    }
}

@Composable
private fun BlendModePreview(label: String, modifier: Modifier = Modifier.size(16.dp)) {
    val colors = LocalEditorColors.current
    Canvas(modifier) {
        val leftColor = colors.accent.copy(alpha = if (label == "normal") 0.55f else 0.72f)
        val rightColor = when (label) {
            "multiply", "color-burn", "darken" -> Color(0xFFFFB300).copy(alpha = 0.82f)
            "screen", "lighten", "color-dodge" -> Color(0xFF55D6BE).copy(alpha = 0.68f)
            "difference" -> Color(0xFFE95F7A).copy(alpha = 0.76f)
            else -> Color(0xFFFFB300).copy(alpha = 0.62f)
        }
        drawCircle(leftColor, radius = size.minDimension * 0.34f, center = Offset(size.width * 0.40f, size.height * 0.52f))
        drawCircle(rightColor, radius = size.minDimension * 0.34f, center = Offset(size.width * 0.62f, size.height * 0.46f))
        drawCircle(colors.controlInk, radius = size.minDimension * 0.34f, center = Offset(size.width * 0.40f, size.height * 0.52f), style = Stroke(1.dp.toPx()))
        drawCircle(colors.controlInk, radius = size.minDimension * 0.34f, center = Offset(size.width * 0.62f, size.height * 0.46f), style = Stroke(1.dp.toPx()))
    }
}

@Composable
private fun FillKindPreview(kind: FillKind, modifier: Modifier = Modifier.size(16.dp)) {
    val colors = LocalEditorColors.current
    when (kind) {
        FillKind.Solid -> DropdownMenuIcon(EditorIcon.Fill, modifier = modifier, tint = colors.controlInk)
        FillKind.Image -> DropdownMenuIcon(EditorIcon.Image, modifier = modifier, tint = colors.controlInk)
        FillKind.LinearGradient,
        FillKind.RadialGradient -> Canvas(modifier) {
            val brush = if (kind == FillKind.LinearGradient) {
                Brush.horizontalGradient(listOf(colors.accent, Color(0xFFFFB300)))
            } else {
                Brush.radialGradient(listOf(colors.accent, Color(0xFFFFB300)), radius = size.minDimension * 0.65f)
            }
            drawRoundRect(brush, Offset(1.5f, 2.5f), Size(size.width - 3f, size.height - 5f), CornerRadius(3f, 3f))
            drawRoundRect(colors.controlInk, Offset(1.5f, 2.5f), Size(size.width - 3f, size.height - 5f), CornerRadius(3f, 3f), style = Stroke(1.dp.toPx()))
        }
    }
}

@Composable
private fun StrokeAlignPreview(align: StrokeAlign, modifier: Modifier = Modifier.size(16.dp)) {
    val colors = LocalEditorColors.current
    Canvas(modifier) {
        val rectTop = 4f
        val rectHeight = size.height - 8f
        drawRoundRect(colors.selectionFill, Offset(3f, rectTop), Size(size.width - 6f, rectHeight), CornerRadius(2f, 2f))
        drawRoundRect(colors.controlInk, Offset(3f, rectTop), Size(size.width - 6f, rectHeight), CornerRadius(2f, 2f), style = Stroke(1.dp.toPx()))
        val y = when (align) {
            StrokeAlign.Inside -> rectTop + 2f
            StrokeAlign.Center -> rectTop + rectHeight / 2f
            StrokeAlign.Outside -> rectTop - 1f
        }
        drawLine(colors.accent, Offset(2f, y), Offset(size.width - 2f, y), strokeWidth = 2.dp.toPx())
    }
}

@Composable
private fun StrokeCapPreview(cap: String, modifier: Modifier = Modifier.size(18.dp)) {
    val colors = LocalEditorColors.current
    Canvas(modifier) {
        val start = Offset(3f, size.height / 2f)
        val end = Offset(size.width - 4f, size.height / 2f)
        drawLine(colors.controlInk, start, end, strokeWidth = 2.dp.toPx())
        when (cap) {
            "round" -> drawCircle(colors.accent, radius = 3f, center = end)
            "square" -> drawRect(colors.accent, topLeft = Offset(end.x - 2.5f, end.y - 2.5f), size = Size(5f, 5f))
            "arrow" -> {
                drawLine(colors.accent, end, Offset(end.x - 5f, end.y - 4f), strokeWidth = 2.dp.toPx())
                drawLine(colors.accent, end, Offset(end.x - 5f, end.y + 4f), strokeWidth = 2.dp.toPx())
            }
            else -> drawLine(colors.accent, Offset(end.x, end.y - 4f), Offset(end.x, end.y + 4f), strokeWidth = 2.dp.toPx())
        }
    }
}

@Composable
private fun StrokeJoinPreview(join: String, modifier: Modifier = Modifier.size(18.dp)) {
    val colors = LocalEditorColors.current
    Canvas(modifier) {
        val ink = colors.controlInk
        val accent = colors.accent
        val w = 2.5.dp.toPx()
        // An "L" corner; the joint style is drawn in the accent colour at the elbow.
        val corner = Offset(size.width * 0.35f, size.height * 0.3f)
        val down = Offset(size.width * 0.35f, size.height - 3f)
        val right = Offset(size.width - 3f, size.height * 0.3f)
        drawLine(ink, corner, down, strokeWidth = w)
        drawLine(ink, corner, right, strokeWidth = w)
        when (join) {
            "round" -> drawArc(
                color = accent,
                startAngle = 180f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(corner.x - 4f, corner.y - 4f),
                size = Size(8f, 8f),
                style = Stroke(w),
            )
            "bevel" -> drawLine(accent, Offset(corner.x, corner.y + 4f), Offset(corner.x + 4f, corner.y), strokeWidth = w)
            else -> { // miter: a sharp spike at the elbow
                drawLine(accent, corner, Offset(corner.x - 3f, corner.y - 3f), strokeWidth = w)
            }
        }
    }
}

@Composable
private fun FillRulePreview(rule: String, modifier: Modifier = Modifier.size(18.dp)) {
    val colors = LocalEditorColors.current
    Canvas(modifier) {
        val ink = colors.controlInk
        val fill = colors.accent.copy(alpha = 0.5f)
        val r = size.minDimension * 0.28f
        val a = Offset(size.width * 0.4f, size.height * 0.42f)
        val b = Offset(size.width * 0.6f, size.height * 0.58f)
        if (rule == "evenodd") {
            // Overlap punched out (hole).
            drawCircle(fill, radius = r, center = a, style = Stroke(1.5f))
            drawCircle(fill, radius = r, center = b, style = Stroke(1.5f))
        } else {
            // Overlap filled (nonzero).
            drawCircle(fill, radius = r, center = a)
            drawCircle(fill, radius = r, center = b)
        }
        drawCircle(ink, radius = r, center = a, style = Stroke(1f))
        drawCircle(ink, radius = r, center = b, style = Stroke(1f))
    }
}

@Composable
private fun EffectTypeIcon(type: EffectType?, modifier: Modifier = Modifier.size(16.dp)) {
    val icon = when (type) {
        EffectType.DropShadow -> EditorIcon.Visibility
        EffectType.InnerShadow -> EditorIcon.Visibility
        EffectType.LayerBlur -> EditorIcon.BlurOn
        EffectType.BackgroundBlur -> EditorIcon.BlurOn
        null -> EditorIcon.Design
    }
    DropdownMenuIcon(icon, modifier = modifier)
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
internal fun SmallSelect(
    value: String,
    options: List<String>,
    modifier: Modifier = Modifier,
    leadingContent: (@Composable () -> Unit)? = null,
    optionLeadingContent: (@Composable (String) -> Unit)? = null,
    onSelect: (String) -> Unit,
) {
    SelectField(
        value = value,
        options = options,
        onSelect = onSelect,
        modifier = modifier.width(108.dp),
        leadingContent = leadingContent,
        optionLeadingContent = optionLeadingContent,
    )
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

private fun DesignEffect.effectType(): EffectType? = when (this) {
    is DesignEffect.DropShadow -> EffectType.DropShadow
    is DesignEffect.InnerShadow -> EffectType.InnerShadow
    is DesignEffect.LayerBlur -> EffectType.LayerBlur
    is DesignEffect.BackgroundBlur -> EffectType.BackgroundBlur
    is DesignEffect.Unknown -> null
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
private fun TextAutoResize.autoResizeLabel() = when (this) { TextAutoResize.WidthAndHeight -> "Auto W"; TextAutoResize.Height -> "Auto H"; TextAutoResize.None -> "Fixed" }
private fun HorizontalConstraint.hLabel() = when (this) { HorizontalConstraint.Left -> "Left"; HorizontalConstraint.Right -> "Right"; HorizontalConstraint.Center -> "Center"; HorizontalConstraint.LeftRight -> "Left & Right"; HorizontalConstraint.Scale -> "Scale" }
private fun VerticalConstraint.vLabel() = when (this) { VerticalConstraint.Top -> "Top"; VerticalConstraint.Bottom -> "Bottom"; VerticalConstraint.Center -> "Center"; VerticalConstraint.TopBottom -> "Top & Bottom"; VerticalConstraint.Scale -> "Scale" }

// --- Diagram -------------------------------------------------------------------

private val DiagramSectionLabel = CompactLabel("Diagram", "Diagram", "Dgm")

/**
 * Diagram controls: node type/label/style (+ table and UML class structure) for a selected
 * diagram element, relation/routing/arrowheads/pattern/jumps/labels for a selected edge,
 * plus diagram-level actions (auto-layout, starter templates, Mermaid/PlantUML import).
 * Owns its own collapsible chrome like [ShapeSection]. Element selection lives in the
 * workspace ([DiagramSelection]); everything dispatches [DiagramEditorIntent]s.
 */
@Composable
private fun DiagramSection(state: MissionEditorStateHolder, node: DesignNode, visible: Boolean) {
    if (!visible) return
    val kind = node.kind as? DesignNodeKind.Diagram ?: return
    StandaloneSection(icon = EditorIcon.Diagram, label = DiagramSectionLabel) {
        val nodeId = node.id
        val locked = state.designState.isNodeLocked(nodeId)
        val graph = kind.graph
        val ws = state.workspace
        val editing = ws.diagramEditNodeId == nodeId
        val selection = if (editing) ws.diagramSelection else DiagramSelection.Empty

        if (!editing) {
            TinyButton("Edit diagram", enabled = !locked) {
                state.updateWorkspace {
                    it.copy(diagramEditNodeId = nodeId, diagramTool = DiagramTool.Select, diagramSelection = DiagramSelection.Empty)
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        val selectedElement = selection.elementIds.singleOrNull()?.let { graph.nodeById(DiagramNodeId(it)) }
        val selectedEdge = selection.edgeIds.singleOrNull()?.let { graph.edgeById(DiagramEdgeId(it)) }
        when {
            selectedElement != null -> {
                DiagramNodeControls(state, nodeId, selectedElement, locked)
                Spacer(Modifier.height(12.dp))
            }
            selectedEdge != null -> {
                DiagramEdgeControls(state, nodeId, selectedEdge, locked)
                Spacer(Modifier.height(12.dp))
            }
            editing -> {
                MutedNote("Select a node or an edge on the canvas.")
                Spacer(Modifier.height(12.dp))
            }
        }
        DiagramCanvasActions(state, nodeId, locked)
    }
}

/** Type / label / style controls for a selected diagram node (+ table / UML structure). */
@Composable
private fun DiagramNodeControls(
    state: MissionEditorStateHolder,
    nodeId: String,
    element: io.aequicor.visualization.subsystems.diagrams.model.DiagramNode,
    locked: Boolean,
) {
    val elementId = element.id.value
    val previewStyle = editorDiagramPreviewStyle()
    LabeledField("Type") {
        SelectField(
            value = diagramPayloadTypeLabel(element.payload),
            options = DiagramNodePalette.map { it.label },
            onSelect = { label ->
                if (!locked && label != diagramPayloadTypeLabel(element.payload)) {
                    DiagramNodePalette.firstOrNull { it.label == label }?.let { entry ->
                        state.dispatch(DiagramEditorIntent.SetDiagramNodePayload(nodeId, elementId, entry.payload))
                    }
                }
            },
            leadingContent = { DiagramNodePreview(element.payload, previewStyle) },
            optionLeadingContent = { label ->
                DiagramNodePalette.firstOrNull { it.label == label }
                    ?.let { DiagramNodePreview(it.payload, previewStyle) }
            },
        )
    }
    Spacer(Modifier.height(8.dp))
    CompactLabeledTextField(
        "Label",
        element.labels.firstOrNull()?.text.orEmpty(),
        "diagram-label-$elementId",
        enabled = !locked,
    ) { text ->
        state.dispatch(DiagramEditorIntent.SetDiagramNodeLabel(nodeId, elementId, text.takeIf { it.isNotBlank() }))
    }
    Spacer(Modifier.height(10.dp))
    DiagramStyleControls(state, element.style, locked, resetKey = "diagram-node-$elementId") { style ->
        state.dispatch(DiagramEditorIntent.SetDiagramNodeStyle(nodeId, elementId, style))
    }
    (element.payload as? TableNode)?.let { table ->
        Spacer(Modifier.height(10.dp))
        DiagramTableControls(state, nodeId, elementId, table, locked)
    }
    (element.payload as? UmlClassNode)?.let { uml ->
        Spacer(Modifier.height(10.dp))
        DiagramClassControls(state, nodeId, elementId, uml, locked)
    }
}

/** Shared node/edge style block: fill/stroke colors, width, pattern, corners, sketch/shadow, opacity. */
@Composable
private fun DiagramStyleControls(
    state: MissionEditorStateHolder,
    style: DiagramStyle,
    locked: Boolean,
    resetKey: String,
    showFill: Boolean = true,
    onStyle: (DiagramStyle) -> Unit,
) {
    val colors = LocalEditorColors.current
    InspectorSubLabel("Style")
    Spacer(Modifier.height(6.dp))
    if (showFill) {
        LabeledField("Fill") {
            InspectorColorField(
                state = state,
                color = (style.fill ?: DiagramColor.White).toDesignColor(),
                opacity = 1.0,
                label = style.fill?.toDesignColor()?.toHex() ?: "Default",
                enabled = !locked,
                onColor = { picked -> onStyle(style.copy(fill = picked.toDiagramColor())) },
                onOpacity = { },
            )
        }
        Spacer(Modifier.height(6.dp))
    }
    LabeledField("Stroke") {
        InspectorColorField(
            state = state,
            color = (style.stroke ?: DiagramColor.Black).toDesignColor(),
            opacity = 1.0,
            label = style.stroke?.toDesignColor()?.toHex() ?: "Default",
            enabled = !locked,
            onColor = { picked -> onStyle(style.copy(stroke = picked.toDiagramColor())) },
            onOpacity = { },
        )
    }
    Spacer(Modifier.height(6.dp))
    CompactLabeledNumberField("Width", style.strokeWidth.formatPx(), "$resetKey-stroke-width", enabled = !locked) {
        onStyle(style.copy(strokeWidth = it.coerceAtLeast(0.0)))
    }
    Spacer(Modifier.height(8.dp))
    CompactLabeledSelectField(
        "Pattern",
        diagramPatternLabel(style.pattern),
        DiagramStrokePattern.entries.map { diagramPatternLabel(it) },
        enabled = !locked,
        leadingContent = { DiagramPatternPreview(style.pattern) },
        optionLeadingContent = { label ->
            DiagramStrokePattern.entries.firstOrNull { diagramPatternLabel(it) == label }
                ?.let { DiagramPatternPreview(it) }
        },
    ) { label ->
        DiagramStrokePattern.entries.firstOrNull { diagramPatternLabel(it) == label }
            ?.let { onStyle(style.copy(pattern = it)) }
    }
    Spacer(Modifier.height(8.dp))
    CompactLabeledSelectField(
        "Corners",
        diagramCornerLabel(style.cornerStyle),
        DiagramCornerStyle.entries.map { diagramCornerLabel(it) },
        enabled = !locked,
        leadingContent = { DiagramCornerPreview(style.cornerStyle) },
        optionLeadingContent = { label ->
            DiagramCornerStyle.entries.firstOrNull { diagramCornerLabel(it) == label }
                ?.let { DiagramCornerPreview(it) }
        },
    ) { label ->
        DiagramCornerStyle.entries.firstOrNull { diagramCornerLabel(it) == label }
            ?.let { onStyle(style.copy(cornerStyle = it)) }
    }
    Spacer(Modifier.height(6.dp))
    CheckRow("Sketch", style.sketch) { if (!locked) onStyle(style.copy(sketch = !style.sketch)) }
    CheckRow("Shadow", style.shadow) { if (!locked) onStyle(style.copy(shadow = !style.shadow)) }
    Spacer(Modifier.height(6.dp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Opacity", style = MaterialTheme.typography.bodySmall, color = colors.controlInk)
        Text("${(style.opacity * 100).roundToInt()}%", style = MaterialTheme.typography.bodySmall)
        UndoableSlider(
            value = (style.opacity * 100).toFloat(),
            valueRange = 0f..100f,
            enabled = !locked,
            onBegin = { state.dispatch(DesignEditorIntent.BeginInteraction) },
            onChange = { value -> onStyle(style.copy(opacity = (value / 100.0).coerceIn(0.0, 1.0))) },
            onEnd = { state.dispatch(DesignEditorIntent.EndInteraction) },
            modifier = Modifier.weight(1f),
        )
    }
}

/** Table structure controls: add/remove rows and columns, merge/split a cell range. */
@Composable
private fun DiagramTableControls(
    state: MissionEditorStateHolder,
    nodeId: String,
    elementId: String,
    table: TableNode,
    locked: Boolean,
) {
    InspectorSubLabel("Table (${table.rowCount} x ${table.columnCount})")
    Spacer(Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        TinyButton("+ Row", enabled = !locked) {
            state.dispatch(DiagramEditorIntent.AddDiagramTableRow(nodeId, elementId))
        }
        TinyButton("+ Column", enabled = !locked) {
            state.dispatch(DiagramEditorIntent.AddDiagramTableColumn(nodeId, elementId))
        }
    }
    Spacer(Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        TinyButton("- Row", enabled = !locked && table.rowCount > 1) {
            state.dispatch(DiagramEditorIntent.RemoveDiagramTableRow(nodeId, elementId, table.rowCount - 1))
        }
        TinyButton("- Column", enabled = !locked && table.columnCount > 1) {
            state.dispatch(DiagramEditorIntent.RemoveDiagramTableColumn(nodeId, elementId, table.columnCount - 1))
        }
    }
    Spacer(Modifier.height(8.dp))
    InspectorSubLabel("Merge cells")
    var rowStart by remember(elementId) { mutableStateOf(0) }
    var rowEnd by remember(elementId) { mutableStateOf(0) }
    var columnStart by remember(elementId) { mutableStateOf(0) }
    var columnEnd by remember(elementId) { mutableStateOf(1) }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val fieldWidth = inspectorPairFieldWidth(maxWidth)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CompactNumberField("R1", rowStart.toDouble().formatPx(), "merge-r1-$elementId", Modifier.width(fieldWidth), enabled = !locked) {
                    rowStart = it.roundToInt().coerceIn(0, table.rowCount - 1)
                }
                CompactNumberField("R2", rowEnd.toDouble().formatPx(), "merge-r2-$elementId", Modifier.width(fieldWidth), enabled = !locked) {
                    rowEnd = it.roundToInt().coerceIn(0, table.rowCount - 1)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CompactNumberField("C1", columnStart.toDouble().formatPx(), "merge-c1-$elementId", Modifier.width(fieldWidth), enabled = !locked) {
                    columnStart = it.roundToInt().coerceIn(0, table.columnCount - 1)
                }
                CompactNumberField("C2", columnEnd.toDouble().formatPx(), "merge-c2-$elementId", Modifier.width(fieldWidth), enabled = !locked) {
                    columnEnd = it.roundToInt().coerceIn(0, table.columnCount - 1)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TinyButton("Merge", enabled = !locked) {
                    state.dispatch(
                        DiagramEditorIntent.MergeDiagramTableCells(
                            nodeId, elementId,
                            rowStart = minOf(rowStart, rowEnd),
                            rowEnd = maxOf(rowStart, rowEnd),
                            columnStart = minOf(columnStart, columnEnd),
                            columnEnd = maxOf(columnStart, columnEnd),
                        ),
                    )
                }
                TinyButton("Split", enabled = !locked) {
                    state.dispatch(DiagramEditorIntent.SplitDiagramTableCell(nodeId, elementId, rowStart, columnStart))
                }
            }
        }
    }
}

/** UML class compartments: attribute/operation rows with visibility selects, add/remove. */
@Composable
private fun DiagramClassControls(
    state: MissionEditorStateHolder,
    nodeId: String,
    elementId: String,
    uml: UmlClassNode,
    locked: Boolean,
) {
    DiagramMemberList(state, nodeId, elementId, "Attributes", UmlClassMemberKind.ATTRIBUTE, uml.attributes, locked)
    Spacer(Modifier.height(8.dp))
    DiagramMemberList(state, nodeId, elementId, "Operations", UmlClassMemberKind.OPERATION, uml.operations, locked)
}

@Composable
private fun DiagramMemberList(
    state: MissionEditorStateHolder,
    nodeId: String,
    elementId: String,
    title: String,
    kind: UmlClassMemberKind,
    members: List<UmlMember>,
    locked: Boolean,
) {
    val colors = LocalEditorColors.current
    InspectorSubLabel(title)
    if (members.isEmpty()) {
        MutedNote("None yet.")
    }
    members.forEachIndexed { index, member ->
        Row(
            Modifier.fillMaxWidth().padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            CompactSelectField(
                value = umlVisibilityLabel(member.visibility),
                options = UmlVisibility.entries.map { umlVisibilityLabel(it) },
                onSelect = { label ->
                    if (!locked) {
                        UmlVisibility.entries.firstOrNull { umlVisibilityLabel(it) == label }?.let {
                            state.dispatch(
                                DiagramEditorIntent.SetDiagramClassMemberVisibility(nodeId, elementId, kind, index, it),
                            )
                        }
                    }
                },
                modifier = Modifier.width(96.dp),
                enabled = !locked,
                leadingContent = { UmlVisibilityPreview(member.visibility) },
                optionLeadingContent = { label ->
                    UmlVisibility.entries.firstOrNull { umlVisibilityLabel(it) == label }
                        ?.let { UmlVisibilityPreview(it) }
                },
            )
            Text(
                member.text,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = colors.ink,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
            TinyButton("x", enabled = !locked) {
                state.dispatch(DiagramEditorIntent.RemoveDiagramClassMember(nodeId, elementId, kind, index))
            }
        }
    }
    Spacer(Modifier.height(4.dp))
    var draft by remember(elementId, kind) { mutableStateOf("") }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.weight(1f)) {
            CompactTextField(
                value = draft,
                resetKey = "member-$elementId-$kind-${members.size}",
                enabled = !locked,
                placeholder = if (kind == UmlClassMemberKind.ATTRIBUTE) "name: Type" else "name(args): Type",
            ) { draft = it }
        }
        TinyButton("+ Add", enabled = !locked) {
            val text = draft.trim().ifBlank { if (kind == UmlClassMemberKind.ATTRIBUTE) "attribute" else "operation()" }
            state.dispatch(DiagramEditorIntent.AddDiagramClassMember(nodeId, elementId, kind, UmlMember(text)))
            draft = ""
        }
    }
}

/** Relation / routing / arrowheads / pattern / jumps / labels / reverse for a selected edge. */
@Composable
private fun DiagramEdgeControls(
    state: MissionEditorStateHolder,
    nodeId: String,
    edge: DiagramEdge,
    locked: Boolean,
) {
    val edgeId = edge.id.value
    val previewStyle = editorDiagramPreviewStyle()
    LabeledField("Relation") {
        SelectField(
            value = diagramRelationLabel(edge.relation),
            options = DiagramRelationOptions.map { it.first },
            onSelect = { label ->
                if (!locked) {
                    DiagramRelationOptions.firstOrNull { it.first == label }?.let { (_, relation) ->
                        state.dispatch(DiagramEditorIntent.SetDiagramEdgeRelation(nodeId, edgeId, relation))
                    }
                }
            },
            leadingContent = { DiagramRelationPreview(edge.relation, previewStyle) },
            optionLeadingContent = { label ->
                DiagramRelationOptions.firstOrNull { it.first == label }
                    ?.let { DiagramRelationPreview(it.second, previewStyle) }
            },
        )
    }
    Spacer(Modifier.height(8.dp))
    CompactLabeledSelectField(
        "Routing",
        diagramRoutingLabel(edge.routing),
        DiagramRoutingStyle.entries.map { diagramRoutingLabel(it) },
        enabled = !locked,
        leadingContent = { DiagramRoutingPreview(edge.routing) },
        optionLeadingContent = { label ->
            DiagramRoutingStyle.entries.firstOrNull { diagramRoutingLabel(it) == label }
                ?.let { DiagramRoutingPreview(it) }
        },
    ) { label ->
        DiagramRoutingStyle.entries.firstOrNull { diagramRoutingLabel(it) == label }
            ?.let { state.dispatch(DiagramEditorIntent.SetDiagramEdgeRouting(nodeId, edgeId, it)) }
    }
    Spacer(Modifier.height(8.dp))
    CompactLabeledSelectField(
        "Start head",
        diagramArrowheadLabel(edge.sourceArrowhead.kind),
        DiagramArrowheadKind.entries.map { diagramArrowheadLabel(it) },
        enabled = !locked,
        leadingContent = { DiagramArrowheadPreview(edge.sourceArrowhead.kind) },
        optionLeadingContent = { label ->
            DiagramArrowheadKind.entries.firstOrNull { diagramArrowheadLabel(it) == label }
                ?.let { DiagramArrowheadPreview(it) }
        },
    ) { label ->
        DiagramArrowheadKind.entries.firstOrNull { diagramArrowheadLabel(it) == label }?.let {
            state.dispatch(DiagramEditorIntent.SetDiagramEdgeArrowheads(nodeId, edgeId, source = DiagramArrowhead(it)))
        }
    }
    Spacer(Modifier.height(8.dp))
    CompactLabeledSelectField(
        "End head",
        diagramArrowheadLabel(edge.targetArrowhead.kind),
        DiagramArrowheadKind.entries.map { diagramArrowheadLabel(it) },
        enabled = !locked,
        leadingContent = { DiagramArrowheadPreview(edge.targetArrowhead.kind) },
        optionLeadingContent = { label ->
            DiagramArrowheadKind.entries.firstOrNull { diagramArrowheadLabel(it) == label }
                ?.let { DiagramArrowheadPreview(it) }
        },
    ) { label ->
        DiagramArrowheadKind.entries.firstOrNull { diagramArrowheadLabel(it) == label }?.let {
            state.dispatch(DiagramEditorIntent.SetDiagramEdgeArrowheads(nodeId, edgeId, target = DiagramArrowhead(it)))
        }
    }
    Spacer(Modifier.height(8.dp))
    CompactLabeledSelectField(
        "Pattern",
        diagramPatternLabel(edge.style.pattern),
        DiagramStrokePattern.entries.map { diagramPatternLabel(it) },
        enabled = !locked,
        leadingContent = { DiagramPatternPreview(edge.style.pattern) },
        optionLeadingContent = { label ->
            DiagramStrokePattern.entries.firstOrNull { diagramPatternLabel(it) == label }
                ?.let { DiagramPatternPreview(it) }
        },
    ) { label ->
        DiagramStrokePattern.entries.firstOrNull { diagramPatternLabel(it) == label }
            ?.let { state.dispatch(DiagramEditorIntent.SetDiagramEdgePattern(nodeId, edgeId, it)) }
    }
    Spacer(Modifier.height(8.dp))
    CompactLabeledSelectField(
        "Line jumps",
        diagramLineJumpLabel(edge.lineJumps),
        LineJumpStyle.entries.map { diagramLineJumpLabel(it) },
        enabled = !locked,
        leadingContent = { DiagramLineJumpPreview(edge.lineJumps) },
        optionLeadingContent = { label ->
            LineJumpStyle.entries.firstOrNull { diagramLineJumpLabel(it) == label }
                ?.let { DiagramLineJumpPreview(it) }
        },
    ) { label ->
        LineJumpStyle.entries.firstOrNull { diagramLineJumpLabel(it) == label }
            ?.let { state.dispatch(DiagramEditorIntent.SetDiagramEdgeLineJumps(nodeId, edgeId, it)) }
    }
    Spacer(Modifier.height(10.dp))
    InspectorSubLabel("Labels")
    DiagramEdgeLabelPosition.entries.forEach { position ->
        val current = edge.labels.firstOrNull { it.position == position }?.label?.text.orEmpty()
        CompactLabeledTextField(
            diagramEdgeLabelPositionTitle(position),
            current,
            "edge-label-$edgeId-$position",
            enabled = !locked,
        ) { text ->
            state.dispatch(
                DiagramEditorIntent.SetDiagramEdgeLabel(nodeId, edgeId, position, text.takeIf { it.isNotBlank() }),
            )
        }
        Spacer(Modifier.height(6.dp))
    }
    Spacer(Modifier.height(4.dp))
    TinyButton("Reverse direction", enabled = !locked) {
        state.dispatch(DiagramEditorIntent.ReverseDiagramEdge(nodeId, edgeId))
    }
}

/** Diagram-level actions: auto-layout, starter template insertion, text import. */
@Composable
private fun DiagramCanvasActions(state: MissionEditorStateHolder, nodeId: String, locked: Boolean) {
    val previewStyle = editorDiagramPreviewStyle()
    InspectorSubLabel("Diagram")
    Spacer(Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        TinyButton("Auto-layout", enabled = !locked) {
            state.dispatch(DiagramEditorIntent.ApplyDiagramAutoLayout(nodeId))
        }
        Box {
            var templatesExpanded by remember(nodeId) { mutableStateOf(false) }
            TinyButton("Insert template", enabled = !locked) { templatesExpanded = true }
            EditorDropdownMenu(expanded = templatesExpanded, onDismissRequest = { templatesExpanded = false }) {
                diagramTemplates().forEach { template ->
                    EditorDropdownMenuItem(
                        text = template.name,
                        leadingContent = {
                            DiagramNodePreview(diagramTemplatePreviewPayload(template.id), previewStyle)
                        },
                        onClick = {
                            templatesExpanded = false
                            state.dispatch(DiagramEditorIntent.InsertDiagramTemplate(nodeId, template.id))
                        },
                    )
                }
            }
        }
    }
    Spacer(Modifier.height(10.dp))
    InspectorSubLabel("Import text")
    Spacer(Modifier.height(6.dp))
    var format by remember(nodeId) { mutableStateOf(DiagramTextFormat.Mermaid) }
    SegmentedControl(
        options = DiagramTextFormat.entries.toList(),
        selected = format,
        label = { if (it == DiagramTextFormat.Mermaid) "Mermaid" else "PlantUML" },
        onSelect = { format = it },
    )
    Spacer(Modifier.height(6.dp))
    var importDraft by remember(nodeId) { mutableStateOf("") }
    DiagramImportTextArea(
        value = importDraft,
        enabled = !locked,
        placeholder = if (format == DiagramTextFormat.Mermaid) "graph TD; A-->B" else "@startuml ...",
        onChange = { importDraft = it },
    )
    Spacer(Modifier.height(6.dp))
    TinyButton(if (format == DiagramTextFormat.Mermaid) "Import Mermaid" else "Import PlantUML", enabled = !locked) {
        if (importDraft.isNotBlank()) {
            state.dispatch(DiagramEditorIntent.ImportDiagramText(nodeId, importDraft, format))
            importDraft = ""
        }
    }
}

/** Multi-line source field for the text-to-diagram import. */
@Composable
private fun DiagramImportTextArea(
    value: String,
    enabled: Boolean,
    placeholder: String,
    onChange: (String) -> Unit,
) {
    val colors = LocalEditorColors.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(5.dp),
        color = if (enabled) colors.controlSurface else colors.controlDisabledSurface,
        border = BorderStroke(1.dp, if (enabled) colors.controlStroke else colors.controlDisabledStroke),
    ) {
        Box(Modifier.padding(8.dp)) {
            if (value.isEmpty()) {
                Text(placeholder, style = MaterialTheme.typography.bodySmall, color = colors.mutedInk)
            }
            BasicTextField(
                value = value,
                onValueChange = onChange,
                modifier = Modifier.fillMaxWidth().height(72.dp),
                enabled = enabled,
                textStyle = MaterialTheme.typography.bodySmall.copy(color = colors.ink, fontFamily = FontFamily.Monospace),
            )
        }
    }
}

// --- Diagram labels + previews ----------------------------------------------------

private val DiagramRelationOptions: List<Pair<String, DiagramRelation>> = listOf(
    "Plain" to DiagramRelation.Plain,
    "Association" to DiagramRelation.Association(directed = false),
    "Directed association" to DiagramRelation.Association(directed = true),
    "Aggregation" to DiagramRelation.Aggregation,
    "Composition" to DiagramRelation.Composition,
    "Generalization" to DiagramRelation.Generalization,
    "Dependency" to DiagramRelation.Dependency,
    "Realization" to DiagramRelation.Realization,
    "Transition" to DiagramRelation.Transition,
    "Include" to DiagramRelation.Include,
    "Extend" to DiagramRelation.Extend,
    "Message (sync)" to DiagramRelation.Message(UmlMessageKind.SYNC),
    "Message (async)" to DiagramRelation.Message(UmlMessageKind.ASYNC),
    "Message (return)" to DiagramRelation.Message(UmlMessageKind.RETURN),
    "Message (create)" to DiagramRelation.Message(UmlMessageKind.CREATE),
    "Message (destroy)" to DiagramRelation.Message(UmlMessageKind.DESTROY),
    "Entity relation" to DiagramRelation.EntityRelation(),
)

private fun diagramRelationLabel(relation: DiagramRelation): String = when (relation) {
    DiagramRelation.Plain -> "Plain"
    is DiagramRelation.Association -> if (relation.directed) "Directed association" else "Association"
    DiagramRelation.Aggregation -> "Aggregation"
    DiagramRelation.Composition -> "Composition"
    DiagramRelation.Generalization -> "Generalization"
    DiagramRelation.Dependency -> "Dependency"
    DiagramRelation.Realization -> "Realization"
    is DiagramRelation.Message -> when (relation.kind) {
        UmlMessageKind.SYNC -> "Message (sync)"
        UmlMessageKind.ASYNC -> "Message (async)"
        UmlMessageKind.RETURN -> "Message (return)"
        UmlMessageKind.CREATE -> "Message (create)"
        UmlMessageKind.DESTROY -> "Message (destroy)"
    }
    DiagramRelation.Transition -> "Transition"
    DiagramRelation.Include -> "Include"
    DiagramRelation.Extend -> "Extend"
    is DiagramRelation.EntityRelation -> "Entity relation"
}

private fun diagramRoutingLabel(style: DiagramRoutingStyle): String = when (style) {
    DiagramRoutingStyle.STRAIGHT -> "Straight"
    DiagramRoutingStyle.ORTHOGONAL -> "Orthogonal"
    DiagramRoutingStyle.SIMPLE -> "Simple"
    DiagramRoutingStyle.ISOMETRIC -> "Isometric"
    DiagramRoutingStyle.CURVED -> "Curved"
    DiagramRoutingStyle.ENTITY_RELATION -> "Entity relation"
}

private fun diagramPatternLabel(pattern: DiagramStrokePattern): String = when (pattern) {
    DiagramStrokePattern.SOLID -> "Solid"
    DiagramStrokePattern.DASHED -> "Dashed"
    DiagramStrokePattern.DOTTED -> "Dotted"
}

private fun diagramCornerLabel(style: DiagramCornerStyle): String = when (style) {
    DiagramCornerStyle.SHARP -> "Sharp"
    DiagramCornerStyle.ROUNDED -> "Rounded"
    DiagramCornerStyle.CURVED -> "Curved"
}

private fun diagramLineJumpLabel(style: LineJumpStyle): String = when (style) {
    LineJumpStyle.NONE -> "None"
    LineJumpStyle.ARC -> "Arc"
    LineJumpStyle.GAP -> "Gap"
    LineJumpStyle.SHARP -> "Sharp"
}

private fun diagramArrowheadLabel(kind: DiagramArrowheadKind): String = when (kind) {
    DiagramArrowheadKind.NONE -> "None"
    DiagramArrowheadKind.OPEN -> "Open"
    DiagramArrowheadKind.BLOCK -> "Block"
    DiagramArrowheadKind.BLOCK_FILLED -> "Block filled"
    DiagramArrowheadKind.DIAMOND -> "Diamond"
    DiagramArrowheadKind.DIAMOND_FILLED -> "Diamond filled"
    DiagramArrowheadKind.TRIANGLE -> "Triangle"
    DiagramArrowheadKind.TRIANGLE_FILLED -> "Triangle filled"
    DiagramArrowheadKind.OVAL -> "Oval"
    DiagramArrowheadKind.OVAL_FILLED -> "Oval filled"
    DiagramArrowheadKind.CROSS -> "Cross"
    DiagramArrowheadKind.DASH -> "Dash"
    DiagramArrowheadKind.ER_ONE -> "ER one"
    DiagramArrowheadKind.ER_MANY -> "ER many"
    DiagramArrowheadKind.ER_ONE_OR_MANY -> "ER one or many"
    DiagramArrowheadKind.ER_ZERO_OR_ONE -> "ER zero or one"
    DiagramArrowheadKind.ER_ZERO_OR_MANY -> "ER zero or many"
}

private fun umlVisibilityLabel(visibility: UmlVisibility): String = when (visibility) {
    UmlVisibility.PUBLIC -> "Public"
    UmlVisibility.PRIVATE -> "Private"
    UmlVisibility.PROTECTED -> "Protected"
    UmlVisibility.PACKAGE -> "Package"
}

private fun diagramEdgeLabelPositionTitle(position: DiagramEdgeLabelPosition): String = when (position) {
    DiagramEdgeLabelPosition.SOURCE -> "Start"
    DiagramEdgeLabelPosition.MIDDLE -> "Middle"
    DiagramEdgeLabelPosition.TARGET -> "End"
}

/** Representative payload glyph for a starter template's dropdown row. */
private fun diagramTemplatePreviewPayload(templateId: String): DiagramNodePayload = when (templateId) {
    "uml-class" -> UmlClassNode(name = "")
    "sequence" -> UmlLifelineNode(name = "")
    "state-machine" -> UmlStateNode(name = "")
    "activity" -> UmlActivityNode(UmlActivityKind.ACTION)
    "use-case" -> UmlUseCaseNode(name = "")
    "component" -> UmlComponentNode(name = "")
    "deployment" -> UmlDeploymentNode(name = "")
    "flowchart" -> DiagramNodePayload.FlowchartNode(FlowchartNodeKind.DECISION)
    "er" -> DiagramNodePayload.ErEntityNode(name = "")
    else -> DiagramNodePayload.BasicShape()
}

private fun DiagramColor.toDesignColor(): DesignColor = DesignColor(argb.toLong() and 0xFFFFFFFFL)

private fun DesignColor.toDiagramColor(): DiagramColor = DiagramColor(argb.toULong() and 0xFFFFFFFFu)

@Composable
private fun UmlVisibilityPreview(visibility: UmlVisibility, modifier: Modifier = Modifier.size(18.dp)) {
    val colors = LocalEditorColors.current
    Box(modifier, contentAlignment = Alignment.Center) {
        Text(
            visibility.symbol.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = colors.accent,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun DiagramPatternPreview(pattern: DiagramStrokePattern, modifier: Modifier = Modifier.size(18.dp)) {
    val colors = LocalEditorColors.current
    Canvas(modifier) {
        val y = size.height / 2f
        val effect = when (pattern) {
            DiagramStrokePattern.SOLID -> null
            DiagramStrokePattern.DASHED -> androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(4f, 3f))
            DiagramStrokePattern.DOTTED -> androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(1.6f, 2.6f))
        }
        drawLine(
            colors.controlInk,
            Offset(1.5f, y),
            Offset(size.width - 1.5f, y),
            strokeWidth = 1.6f,
            cap = androidx.compose.ui.graphics.StrokeCap.Round,
            pathEffect = effect,
        )
    }
}

@Composable
private fun DiagramCornerPreview(style: DiagramCornerStyle, modifier: Modifier = Modifier.size(18.dp)) {
    val colors = LocalEditorColors.current
    Canvas(modifier) {
        val start = Offset(3f, size.height - 3f)
        val corner = Offset(3f, 5f)
        val end = Offset(size.width - 3f, 5f)
        val path = Path().apply {
            moveTo(start.x, start.y)
            when (style) {
                DiagramCornerStyle.SHARP -> {
                    lineTo(corner.x, corner.y)
                    lineTo(end.x, end.y)
                }
                DiagramCornerStyle.ROUNDED -> {
                    lineTo(corner.x, corner.y + 4f)
                    quadraticTo(corner.x, corner.y, corner.x + 4f, corner.y)
                    lineTo(end.x, end.y)
                }
                DiagramCornerStyle.CURVED -> {
                    quadraticTo(corner.x, corner.y, end.x, end.y)
                }
            }
        }
        drawPath(path, colors.controlInk, style = Stroke(1.6f))
    }
}

@Composable
private fun DiagramRoutingPreview(style: DiagramRoutingStyle, modifier: Modifier = Modifier.size(18.dp)) {
    val colors = LocalEditorColors.current
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            when (style) {
                DiagramRoutingStyle.STRAIGHT -> {
                    moveTo(2f, h - 3f)
                    lineTo(w - 2f, 3f)
                }
                DiagramRoutingStyle.ORTHOGONAL -> {
                    moveTo(2f, h - 3f)
                    lineTo(w / 2f, h - 3f)
                    lineTo(w / 2f, 3f)
                    lineTo(w - 2f, 3f)
                }
                DiagramRoutingStyle.SIMPLE -> {
                    moveTo(2f, h - 3f)
                    lineTo(2f, 3f)
                    lineTo(w - 2f, 3f)
                }
                DiagramRoutingStyle.ISOMETRIC -> {
                    moveTo(2f, h - 4f)
                    lineTo(w / 2f, h / 2f)
                    lineTo(w / 2f + 3f, h / 2f + 2f)
                    lineTo(w - 2f, 4f)
                }
                DiagramRoutingStyle.CURVED -> {
                    moveTo(2f, h - 3f)
                    cubicTo(w / 2f, h - 3f, w / 2f, 3f, w - 2f, 3f)
                }
                DiagramRoutingStyle.ENTITY_RELATION -> {
                    moveTo(2f, h - 4f)
                    lineTo(6f, h - 4f)
                    lineTo(w - 6f, 4f)
                    lineTo(w - 2f, 4f)
                }
            }
        }
        drawPath(path, colors.controlInk, style = Stroke(1.5f))
    }
}

@Composable
private fun DiagramLineJumpPreview(style: LineJumpStyle, modifier: Modifier = Modifier.size(18.dp)) {
    val colors = LocalEditorColors.current
    Canvas(modifier) {
        val y = size.height / 2f
        val cx = size.width / 2f
        // The crossed (vertical) edge.
        drawLine(colors.mutedInk, Offset(cx, 2f), Offset(cx, size.height - 2f), strokeWidth = 1.2f)
        val path = Path().apply {
            moveTo(1.5f, y)
            when (style) {
                LineJumpStyle.NONE -> lineTo(size.width - 1.5f, y)
                LineJumpStyle.ARC -> {
                    lineTo(cx - 3.5f, y)
                    quadraticTo(cx, y - 6f, cx + 3.5f, y)
                    lineTo(size.width - 1.5f, y)
                }
                LineJumpStyle.GAP -> {
                    lineTo(cx - 3.5f, y)
                    moveTo(cx + 3.5f, y)
                    lineTo(size.width - 1.5f, y)
                }
                LineJumpStyle.SHARP -> {
                    lineTo(cx - 3.5f, y)
                    lineTo(cx, y - 5f)
                    lineTo(cx + 3.5f, y)
                    lineTo(size.width - 1.5f, y)
                }
            }
        }
        drawPath(path, colors.controlInk, style = Stroke(1.5f))
    }
}

@Composable
private fun DiagramArrowheadPreview(kind: DiagramArrowheadKind, modifier: Modifier = Modifier.size(18.dp)) {
    val colors = LocalEditorColors.current
    Canvas(modifier) {
        val y = (size.height / 2f).toDouble()
        val tip = DiagramPoint((size.width - 2f).toDouble(), y)
        val geometry = arrowheadPath(
            DiagramArrowhead(kind, size = size.minDimension * 0.45),
            tip = tip,
            direction = DiagramPoint(1.0, 0.0),
        )
        drawLine(
            colors.controlInk,
            Offset(2f, y.toFloat()),
            Offset((tip.x - geometry.lineShorten).toFloat(), y.toFloat()),
            strokeWidth = 1.4f,
            cap = androidx.compose.ui.graphics.StrokeCap.Round,
        )
        val path = geometry.path.toComposePath()
        if (geometry.filled) {
            drawPath(path, colors.controlInk)
        } else {
            drawPath(path, colors.controlInk, style = Stroke(1.3f))
        }
    }
}
