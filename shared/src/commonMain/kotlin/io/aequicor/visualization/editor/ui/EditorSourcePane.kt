package io.aequicor.visualization.editor.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.aequicor.visualization.MissionEditorStateHolder
import io.aequicor.visualization.editor.presentation.DesignEditorIntent
import io.aequicor.visualization.editor.presentation.ScreenPreset
import io.aequicor.visualization.editor.presentation.SourceTab
import io.aequicor.visualization.editor.presentation.ZOrderMove
import io.aequicor.visualization.editor.presentation.isSelfOrAncestor
import io.aequicor.visualization.editor.presentation.parentNodeOf
import io.aequicor.visualization.editor.presentation.siblingsOf
import io.aequicor.visualization.editor.presentation.topLevelOwnerPage
import io.aequicor.visualization.editor.ui.theme.LocalEditorColors
import io.aequicor.visualization.engine.ir.model.DesignNode
import io.aequicor.visualization.engine.ir.model.DesignPage
import io.aequicor.visualization.engine.ir.model.literalOrNull
import kotlin.math.roundToInt

/** Left column: Source / Resources / Layers tabs plus the Screens list. */
@Composable
fun EditorSourcePane(state: MissionEditorStateHolder, modifier: Modifier = Modifier) {
    val colors = LocalEditorColors.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            io.aequicor.visualization.MenuButton()
            Text("Source", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        Surface(
            modifier = Modifier.fillMaxWidth().weight(1f),
            shape = RoundedCornerShape(8.dp),
            color = Color.White,
            border = BorderStroke(1.dp, colors.panelStroke),
        ) {
            Column(Modifier.fillMaxSize()) {
                TabStrip(
                    tabs = SourceTab.entries,
                    selected = state.workspace.sourceTab,
                    title = { it.title },
                    onSelect = { tab -> state.updateWorkspace { it.copy(sourceTab = tab) } },
                )
                when (state.workspace.sourceTab) {
                    SourceTab.Markdown -> SourceMarkdown(state)
                    SourceTab.Resources -> EmptyTab("Resources")
                    SourceTab.Layers -> LayersTree(state)
                }
            }
        }
        ScreensPanel(state, Modifier.fillMaxWidth().height(300.dp))
    }
}

@Composable
private fun EmptyTab(title: String) {
    val colors = LocalEditorColors.current
    Box(Modifier.fillMaxSize().background(colors.paneSurface), contentAlignment = Alignment.Center) {
        Text(title, color = colors.mutedInk, style = MaterialTheme.typography.bodyMedium)
    }
}

// --- Markdown source viewer --------------------------------------------------

@Composable
private fun SourceMarkdown(state: MissionEditorStateHolder) {
    val colors = LocalEditorColors.current
    val design = state.designState
    val sourceText = remember(design.selectedPageId, design.sources) {
        sourceForSelectedPage(state)
    }
    if (sourceText == null) {
        Box(Modifier.fillMaxSize().background(colors.paneSurface), contentAlignment = Alignment.Center) {
            Text(
                "This screen was created in the editor and has no SLM source yet.",
                color = colors.mutedInk,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(24.dp),
            )
        }
        return
    }
    val lines = remember(sourceText) { sourceText.lines() }
    Row(
        modifier = Modifier.fillMaxSize().background(colors.paneSurface)
            .verticalScroll(rememberScrollState())
            .horizontalScroll(rememberScrollState()),
    ) {
        Column(
            modifier = Modifier.width(46.dp).background(colors.gutterSurface).padding(top = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            lines.forEachIndexed { index, _ ->
                Text((index + 1).toString(), modifier = Modifier.height(20.dp), color = colors.gutterInk, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            }
        }
        Column(Modifier.padding(top = 12.dp, start = 12.dp, end = 12.dp)) {
            lines.forEach { line ->
                Text(
                    line.ifEmpty { " " },
                    modifier = Modifier.height(20.dp),
                    color = colors.codeInk,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                )
            }
        }
    }
}

/** SLM source of the page currently selected, or null for an in-memory screen. */
private fun sourceForSelectedPage(state: MissionEditorStateHolder): String? {
    val design = state.designState
    val pageId = design.selectedPageId
    design.compiledResults.forEachIndexed { index, result ->
        val doc = result.document ?: return@forEachIndexed
        val screenId = doc.screen?.id.orEmpty()
        val matches = doc.pages.any { page -> screenId.ifBlank { page.id } == pageId }
        if (matches) return design.sources.getOrNull(index)?.content
    }
    return null
}

// --- Layers tree -------------------------------------------------------------

private data class LayerRow(val node: DesignNode, val depth: Int)

/**
 * Layers tree: paint order shown front-first (top of the list = front), expand/collapse
 * of frames, hover + selection sync, visibility/lock toggles and per-row reorder.
 */
@Composable
private fun LayersTree(state: MissionEditorStateHolder) {
    val colors = LocalEditorColors.current
    val design = state.designState
    val ws = state.workspace
    val page = design.document?.pageById(design.selectedPageId)
    val rows = remember(page, ws.collapsedLayers) {
        page?.let { flattenLayers(it, ws.collapsedLayers) } ?: emptyList()
    }
    val rowHeightPx = with(LocalDensity.current) { LayerRowHeight.toPx() }
    // Drag-to-reorder / reparent state, local to the tree.
    var dragId by remember { mutableStateOf("") }
    var dragFrom by remember { mutableStateOf(-1) }
    var dragAccumY by remember { mutableStateOf(0f) }
    var dragOver by remember { mutableStateOf(-1) }
    Column(
        modifier = Modifier.fillMaxSize().background(colors.paneSurface)
            .verticalScroll(rememberScrollState()).padding(vertical = 6.dp),
    ) {
        rows.forEachIndexed { index, row ->
            LayerRowView(
                state = state,
                row = row,
                isDropTarget = dragId.isNotEmpty() && dragOver == index && row.node.id != dragId,
                onDragStart = { dragId = row.node.id; dragFrom = index; dragAccumY = 0f; dragOver = index },
                onDrag = { dy ->
                    if (dragFrom >= 0) {
                        dragAccumY += dy
                        dragOver = (dragFrom + (dragAccumY / rowHeightPx).roundToInt()).coerceIn(0, rows.size - 1)
                    }
                },
                onDrop = {
                    if (dragId.isNotEmpty() && dragOver in rows.indices) applyLayerDrop(state, rows, dragId, dragOver)
                    dragId = ""; dragFrom = -1; dragOver = -1
                },
                onDragCancel = { dragId = ""; dragFrom = -1; dragOver = -1 },
            )
        }
        if (rows.isEmpty()) {
            Text("Empty screen", color = colors.mutedInk, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp))
        }
    }
}

private val LayerRowHeight = 30.dp

/**
 * Resolves a layers drag drop: dropping onto a frame/container reparents into it,
 * otherwise the node reorders next to the target within the target's parent. Invalid
 * drops (self / own descendant) are rejected here and again by the reducer.
 */
private fun applyLayerDrop(state: MissionEditorStateHolder, rows: List<LayerRow>, dragId: String, overIndex: Int) {
    val doc = state.designState.document ?: return
    val overNode = rows.getOrNull(overIndex)?.node ?: return
    if (overNode.id == dragId) return
    if (doc.isSelfOrAncestor(overNode.id, dragId)) return
    val dragParent = doc.parentNodeOf(dragId)?.id ?: doc.topLevelOwnerPage(dragId)?.id
    val overIsContainer = overNode.kind is io.aequicor.visualization.engine.ir.model.DesignNodeKind.Frame || overNode.children.isNotEmpty()
    if (overIsContainer && overNode.id != dragParent) {
        state.dispatch(DesignEditorIntent.ReparentNode(dragId, overNode.id))
        return
    }
    val overParent = doc.parentNodeOf(overNode.id)?.id ?: doc.topLevelOwnerPage(overNode.id)?.id ?: return
    val targetIndex = doc.siblingsOf(overNode.id).indexOfFirst { it.id == overNode.id }
    state.dispatch(DesignEditorIntent.ReparentNode(dragId, overParent, targetIndex))
}

@Composable
private fun LayerRowView(
    state: MissionEditorStateHolder,
    row: LayerRow,
    isDropTarget: Boolean,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDrop: () -> Unit,
    onDragCancel: () -> Unit,
) {
    val colors = LocalEditorColors.current
    val design = state.designState
    val ws = state.workspace
    val node = row.node
    val selected = node.id in design.selectedNodeIds
    val hovered = node.id == ws.hoveredNodeId
    val hasChildren = node.children.isNotEmpty()
    val collapsed = node.id in ws.collapsedLayers
    val visible = node.visible.literalOrNull() ?: true
    Row(
        modifier = Modifier.fillMaxWidth().height(LayerRowHeight)
            .background(
                when {
                    selected -> colors.selectionFill
                    hovered -> colors.raisedSurface
                    else -> Color.Transparent
                },
            )
            .then(if (isDropTarget) Modifier.border(BorderStroke(1.5.dp, colors.accent)) else Modifier)
            .pointerInput(node.id) {
                awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent()
                        if (e.type == PointerEventType.Enter) state.updateWorkspace { it.copy(hoveredNodeId = node.id) }
                        if (e.type == PointerEventType.Exit && ws.hoveredNodeId == node.id) state.updateWorkspace { it.copy(hoveredNodeId = "") }
                    }
                }
            }
            .pointerInput(node.id) {
                detectDragGestures(
                    onDragStart = { onDragStart() },
                    onDragEnd = { onDrop() },
                    onDragCancel = { onDragCancel() },
                ) { change, dragAmount -> change.consume(); onDrag(dragAmount.y) }
            }
            .clickable { state.dispatch(DesignEditorIntent.SelectNode(node.id)) }
            .padding(start = (8 + row.depth * 16).dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Expand / collapse chevron.
        Box(Modifier.size(16.dp), contentAlignment = Alignment.Center) {
            if (hasChildren) {
                Text(
                    if (collapsed) ">" else "v",
                    color = colors.mutedInk,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.clickable {
                        state.updateWorkspace {
                            it.copy(collapsedLayers = if (collapsed) it.collapsedLayers - node.id else it.collapsedLayers + node.id)
                        }
                    },
                )
            }
        }
        Text(layerGlyph(node.type), color = if (selected) colors.accent else colors.mutedInk, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
        Text(
            node.name.ifBlank { node.id },
            modifier = Modifier.weight(1f),
            color = if (selected) colors.accent else if (visible) colors.ink else colors.mutedInk,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        // Row actions appear on hover/selection to avoid clutter.
        if (selected || hovered) {
            LayerAction("^", enabled = true) { state.dispatch(DesignEditorIntent.ReorderNode(node.id, ZOrderMove.Forward)) }
            LayerAction("v", enabled = true) { state.dispatch(DesignEditorIntent.ReorderNode(node.id, ZOrderMove.Backward)) }
            LayerAction(if (node.locked) "L*" else "L", enabled = true) { state.dispatch(DesignEditorIntent.SetLocked(node.id, !node.locked)) }
        }
        LayerAction(if (visible) "O" else "-", enabled = true) { state.dispatch(DesignEditorIntent.SetVisible(node.id, !visible)) }
    }
}

@Composable
private fun LayerAction(glyph: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = LocalEditorColors.current
    Box(
        Modifier.size(18.dp).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, color = colors.controlInk, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
    }
}

private fun flattenLayers(page: DesignPage, collapsed: Set<String>): List<LayerRow> {
    val rows = mutableListOf<LayerRow>()
    fun visit(node: DesignNode, depth: Int) {
        rows += LayerRow(node, depth)
        if (node.id !in collapsed) {
            // Front-first: last paint-order child shown at the top of its group.
            node.children.asReversed().forEach { visit(it, depth + 1) }
        }
    }
    page.children.asReversed().forEach { visit(it, 0) }
    return rows
}

private fun layerGlyph(type: String): String = when (type) {
    "frame", "group", "section", "screen" -> "[]"
    "text" -> "T"
    "instance" -> "<>"
    "shape" -> "◇"
    else -> "--"
}

// --- Screens panel -----------------------------------------------------------

@Composable
private fun ScreensPanel(state: MissionEditorStateHolder, modifier: Modifier = Modifier) {
    val colors = LocalEditorColors.current
    val design = state.designState
    val document = design.document
    val statusColors = listOf(colors.statusPositive, colors.statusWarning, colors.statusDanger)
    var presetMenu by remember { mutableStateOf(false) }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = Color.White,
        border = BorderStroke(1.dp, colors.panelStroke),
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Screens", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Box {
                    SmallSquareButton("+", onClick = { presetMenu = true })
                    DropdownMenu(expanded = presetMenu, onDismissRequest = { presetMenu = false }) {
                        ScreenPreset.entries.forEach { preset ->
                            DropdownMenuItem(
                                text = { Text("${preset.displayName}  ${preset.width.toInt()}x${preset.height.toInt()}", style = MaterialTheme.typography.bodySmall) },
                                onClick = {
                                    presetMenu = false
                                    val count = (document?.pages?.size ?: 0) + 1
                                    state.dispatch(DesignEditorIntent.CreateScreen(preset, "Screen $count"))
                                },
                            )
                        }
                    }
                }
            }
            Column(Modifier.verticalScroll(rememberScrollState())) {
                document?.pages?.forEachIndexed { index, page ->
                    ScreenRow(
                        title = page.name.ifBlank { page.id },
                        subtitle = "${page.children.firstOrNull()?.size?.width?.toInt() ?: 0} x ${page.children.firstOrNull()?.size?.height?.toInt() ?: 0}",
                        status = statusColors[index % statusColors.size],
                        selected = page.id == design.selectedPageId,
                        onClick = { state.dispatch(DesignEditorIntent.SelectPage(page.id)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ScreenRow(title: String, subtitle: String, status: Color, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalEditorColors.current
    Row(
        modifier = Modifier.fillMaxWidth().height(64.dp)
            .background(if (selected) colors.selectionFill else Color.White)
            .border(BorderStroke(if (selected) 1.dp else 0.dp, if (selected) colors.selectionStroke else Color.Transparent))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(44.dp, 34.dp).background(Color.White, RoundedCornerShape(4.dp)).border(1.dp, if (selected) colors.thumbnailSelectedStroke else colors.panelStroke, RoundedCornerShape(4.dp)))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = colors.ink, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.subtleInk, maxLines = 1)
        }
        Box(Modifier.size(12.dp).background(status, CircleShape))
    }
}
