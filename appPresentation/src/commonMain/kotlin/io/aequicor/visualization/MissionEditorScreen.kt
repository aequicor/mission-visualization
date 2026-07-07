package io.aequicor.visualization

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.aequicor.visualization.ui_engine.compose_render_engine.DefaultUiRenderEngine
import io.aequicor.visualization.ui_engine.runtime_state.UiCommand
import io.aequicor.visualization.ui_engine.runtime_state.UiVisualizationState
import io.aequicor.visualization.ui_engine.runtime_state.createUiVisualizationState
import io.aequicor.visualization.ui_engine.runtime_state.reduceUiVisualization
import io.aequicor.visualization.ui_engine.ui_document_ir.UiDocument
import io.aequicor.visualization.ui_engine.ui_document_ir.title
import kotlin.math.roundToInt

private val AccentBlue = Color(0xFF1E88FF)
private val AppChrome = Color(0xFFEAF5FF)
private val PanelStroke = Color(0xFFD6E3EF)
private val SoftStroke = Color(0xFFE3EAF2)
private val Ink = Color(0xFF111827)
private val MutedInk = Color(0xFF5E6B7A)
private val CodeInk = Color(0xFF263449)
private val Green = Color(0xFF17C46B)
private val Amber = Color(0xFFFFB800)
private val Red = Color(0xFFFF1D1D)

enum class SourceTab(val title: String) {
    Markdown("Markdown"),
    Resources("Resources"),
    Layers("Layers"),
}

enum class InspectorTab(val title: String) {
    Design("Design"),
    Comments("Comments"),
}

enum class DeviceMode(val title: String) {
    Pc("PC"),
    Mob("MOB"),
    Tab("TAB"),
}

enum class ToolMode(val label: String) {
    Select("P"),
    Grid("#"),
    Frame("[]"),
    Draw("/"),
    Text("T"),
    Comment("C"),
    List("::"),
    Link("@"),
    Code("</>"),
}

enum class InspectorSection(val title: String) {
    Position("Position"),
    Size("Size"),
    Appearance("Appearance"),
    Constraints("Constraints"),
}

data class InspectorDraft(
    val x: String = "72",
    val y: String = "72",
    val width: String = "1440",
    val height: String = "1024",
    val widthMode: String = "Fixed",
    val heightMode: String = "Fixed",
    val clipContent: Boolean = true,
    val opacity: Float = 100f,
    val fill: String = "#FFFFFF",
    val fillOpacity: String = "100%",
    val stroke: String = "#1E88FF",
    val strokeWidth: String = "1",
    val radius: String = "8",
    val shadow: String = "None",
    val horizontalConstraint: String = "Left",
    val verticalConstraint: String = "Top",
    val fixedWhenScrolling: Boolean = false,
)

data class MissionEditorViewState(
    val sourceTab: SourceTab = SourceTab.Markdown,
    val inspectorTab: InspectorTab = InspectorTab.Design,
    val deviceMode: DeviceMode = DeviceMode.Pc,
    val toolMode: ToolMode = ToolMode.Select,
    val selectedScreenId: String = "",
    val selectedNodeId: String = "",
    val expandedSections: Set<InspectorSection> = InspectorSection.entries.toSet(),
    val inspectorDraft: InspectorDraft = InspectorDraft(),
)

@Stable
class MissionEditorStateHolder {
    var runtimeState by mutableStateOf(createUiVisualizationState())
        private set

    var viewState by mutableStateOf(
        MissionEditorViewState(
            selectedScreenId = runtimeState.selectedScreenId,
            selectedNodeId = runtimeState.selectedNodeId,
        ),
    )
        private set

    fun dispatchDomain(command: UiCommand) {
        runtimeState = reduceUiVisualization(runtimeState, command)
        viewState = viewState.copy(
            selectedScreenId = runtimeState.selectedScreenId,
            selectedNodeId = runtimeState.selectedNodeId,
        )
    }

    fun selectScreen(screenId: String) {
        if (runtimeState.documentOrNull?.screenById(screenId) != null) {
            dispatchDomain(UiCommand.SelectScreen(screenId))
        } else {
            viewState = viewState.copy(selectedScreenId = screenId, selectedNodeId = "")
        }
    }

    fun selectSourceTab(tab: SourceTab) {
        viewState = viewState.copy(sourceTab = tab)
    }

    fun selectInspectorTab(tab: InspectorTab) {
        viewState = viewState.copy(inspectorTab = tab)
    }

    fun selectDeviceMode(mode: DeviceMode) {
        viewState = viewState.copy(deviceMode = mode)
    }

    fun selectToolMode(mode: ToolMode) {
        viewState = viewState.copy(toolMode = mode)
    }

    fun toggleSection(section: InspectorSection) {
        val expanded = viewState.expandedSections
        viewState = viewState.copy(
            expandedSections = if (section in expanded) expanded - section else expanded + section,
        )
    }

    fun updateDraft(update: (InspectorDraft) -> InspectorDraft) {
        viewState = viewState.copy(inspectorDraft = update(viewState.inspectorDraft))
    }
}

@Composable
fun MissionEditorApp() {
    MissionEditorTheme {
        val state = remember { MissionEditorStateHolder() }
        MissionEditorScreen(state)
    }
}

@Composable
private fun MissionEditorTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = AccentBlue,
            onPrimary = Color.White,
            primaryContainer = Color(0xFFDCEEFF),
            onPrimaryContainer = Color(0xFF0E4F9D),
            surface = Color.White,
            onSurface = Ink,
            surfaceVariant = Color(0xFFF4F8FC),
            onSurfaceVariant = MutedInk,
            background = AppChrome,
            onBackground = Ink,
            outline = PanelStroke,
        ),
        content = content,
    )
}

@Composable
private fun MissionEditorScreen(state: MissionEditorStateHolder) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(Color(0xFFF3FAFF), Color(0xFFEAF5FF), Color(0xFFF9FDFF))))
            .padding(12.dp),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(18.dp),
            color = Color.White.copy(alpha = 0.72f),
            border = BorderStroke(1.dp, Color(0xFFCEE1F2)),
            shadowElevation = 8.dp,
        ) {
            if (maxWidth < 1100.dp) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    SourcePane(state, Modifier.fillMaxWidth().height(720.dp))
                    PreviewPane(state, Modifier.fillMaxWidth().height(760.dp))
                    InspectorPane(state, Modifier.fillMaxWidth().height(760.dp))
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(18.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    SourcePane(state, Modifier.width(440.dp).fillMaxHeight())
                    VerticalDivider()
                    PreviewPane(state, Modifier.weight(1f).fillMaxHeight())
                    VerticalDivider()
                    InspectorPane(state, Modifier.width(420.dp).fillMaxHeight())
                }
            }
        }
    }
}

@Composable
private fun SourcePane(
    state: MissionEditorStateHolder,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            MenuButton()
            Text("Source", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }

        Surface(
            modifier = Modifier.fillMaxWidth().weight(1f),
            shape = RoundedCornerShape(8.dp),
            color = Color.White,
            border = BorderStroke(1.dp, PanelStroke),
        ) {
            Column(Modifier.fillMaxSize()) {
                TabStrip(
                    tabs = SourceTab.entries,
                    selected = state.viewState.sourceTab,
                    title = { it.title },
                    onSelect = state::selectSourceTab,
                )
                when (state.viewState.sourceTab) {
                    SourceTab.Markdown -> SourceEditor()
                    SourceTab.Resources -> EmptySourceTab("Resources")
                    SourceTab.Layers -> EmptySourceTab("Layers")
                }
            }
        }

        ScreensPanel(state, Modifier.fillMaxWidth().height(390.dp))
    }
}

@Composable
private fun SourceEditor() {
    val lines = remember {
        listOf(
            "# Mission Overview",
            "",
            "## Summary",
            "",
            "This screen provides a high-level",
            "overview of mission progress, key",
            "metrics, and recent activity.",
            "",
            "- Mission status",
            "- Key metrics",
            "- Recent events",
            "",
            "![overview](overview.png)",
        )
    }
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color(0xFFFBFDFF))
                .verticalScroll(rememberScrollState()),
        ) {
            Column(
                modifier = Modifier
                    .width(46.dp)
                    .fillMaxHeight()
                    .background(Color(0xFFF3F7FB))
                    .padding(top = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                lines.forEachIndexed { index, _ ->
                    Text(
                        (index + 1).toString(),
                        modifier = Modifier.height(26.dp),
                        color = Color(0xFF64748B),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 14.dp, start = 12.dp, end = 12.dp),
            ) {
                lines.forEachIndexed { index, line ->
                    val lineColor = if (line.startsWith("![")) AccentBlue else CodeInk
                    Text(
                        line,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(26.dp)
                            .background(if (index == 0) Color(0xFFF8FAFC) else Color.Transparent),
                        color = lineColor,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .background(Color(0xFFF7FAFE))
                .border(BorderStroke(1.dp, SoftStroke)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("file", modifier = Modifier.padding(start = 16.dp), color = MutedInk, style = MaterialTheme.typography.bodySmall)
            Text("mission-overview.md", modifier = Modifier.padding(start = 8.dp).weight(1f), color = Color(0xFF334155), style = MaterialTheme.typography.bodySmall)
            Text("Ln 1, Col 1", modifier = Modifier.padding(end = 22.dp), color = Color(0xFF334155), style = MaterialTheme.typography.bodySmall)
            Text("124 words", modifier = Modifier.padding(end = 16.dp), color = Color(0xFF334155), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun EmptySourceTab(title: String) {
    Box(Modifier.fillMaxSize().background(Color(0xFFFBFDFF)), contentAlignment = Alignment.Center) {
        Text(title, color = MutedInk, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ScreensPanel(
    state: MissionEditorStateHolder,
    modifier: Modifier = Modifier,
) {
    val screenRows = remember(state.runtimeState.documentOrNull) {
        listOf(
            ScreenRowModel("mission-overview", "Mission Overview", "/screens/mission-overview.md", Green),
            ScreenRowModel("telemetry", "Telemetry", "/screens/telemetry.md", Amber),
            ScreenRowModel("event-log", "Event Log", "/screens/event-log.md", Red),
        )
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = Color.White,
        border = BorderStroke(1.dp, PanelStroke),
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Screens", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                SmallSquareButton("+", onClick = {})
                Spacer(Modifier.width(8.dp))
                SmallSquareButton("v", onClick = {})
            }
            val selectedScreenId = screenRows
                .firstOrNull { it.id == state.viewState.selectedScreenId }
                ?.id
                ?: screenRows.firstOrNull()?.id.orEmpty()
            Column {
                screenRows.forEach { screen ->
                    ScreenListRow(
                        model = screen,
                        selected = screen.id == selectedScreenId,
                        onClick = { state.selectScreen(screen.id) },
                    )
                }
            }
        }
    }
}

private data class ScreenRowModel(
    val id: String,
    val title: String,
    val path: String,
    val status: Color,
)

@Composable
private fun ScreenListRow(
    model: ScreenRowModel,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(82.dp)
            .background(if (selected) Color(0xFFEAF4FF) else Color.White)
            .border(BorderStroke(if (selected) 1.dp else 0.dp, if (selected) Color(0xFFE2F0FF) else Color.Transparent))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MiniThumbnail(selected)
        Column(Modifier.weight(1f)) {
            Text(model.title, style = MaterialTheme.typography.bodyMedium, color = Ink, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(model.path, style = MaterialTheme.typography.bodySmall, color = Color(0xFF41617E), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.size(12.dp).background(model.status, CircleShape))
        Text(":", style = MaterialTheme.typography.titleMedium, color = Color.Black)
    }
}

@Composable
private fun PreviewPane(
    state: MissionEditorStateHolder,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Preview: Mission Overview",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HeaderIconButton("eye", onClick = {})
                HeaderIconButton("grid", onClick = {})
                HeaderIconButton("mob", onClick = {})
                HeaderIconButton("max", onClick = {})
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth().weight(1f),
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFFFBFDFF),
            border = BorderStroke(1.dp, PanelStroke),
        ) {
            ArtboardPreview(state)
        }

        Box(Modifier.fillMaxWidth().height(58.dp)) {
            DeviceControl(
                selected = state.viewState.deviceMode,
                onSelect = state::selectDeviceMode,
                modifier = Modifier.align(Alignment.CenterStart),
            )
            FloatingToolbar(
                selected = state.viewState.toolMode,
                onSelect = state::selectToolMode,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

@Composable
private fun ArtboardPreview(state: MissionEditorStateHolder) {
    BoxWithConstraints(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
        val maxArtboardWidth = (maxWidth - 104.dp).coerceAtLeast(360.dp)
        val artboardWidth = minOf(maxArtboardWidth, 760.dp)
        val artboardHeight = (artboardWidth.value * 1024f / 1440f).dp
        val document = state.runtimeState.documentOrNull

        Canvas(Modifier.matchParentSize()) {
            val step = 12.dp.toPx()
            var x = 0f
            while (x < size.width) {
                var y = 0f
                while (y < size.height) {
                    drawCircle(Color(0xFFE4ECF5), radius = 0.65.dp.toPx(), center = Offset(x, y))
                    y += step
                }
                x += step
            }
        }

        Box(
            modifier = Modifier
                .width(artboardWidth + 72.dp)
                .height(artboardHeight + 58.dp),
            contentAlignment = Alignment.Center,
        ) {
            GuideLine(Modifier.align(Alignment.TopCenter).padding(top = 14.dp), horizontal = true)
            GuideLine(Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp), horizontal = true)
            GuideLine(Modifier.align(Alignment.CenterStart).padding(start = 18.dp), horizontal = false)
            GuideLine(Modifier.align(Alignment.CenterEnd).padding(end = 18.dp), horizontal = false)
            DimensionBadge("1440", Modifier.align(Alignment.TopCenter))
            DimensionBadge("1024", Modifier.align(Alignment.CenterStart).graphicsLayer { rotationZ = -90f })
            Text("72", modifier = Modifier.align(Alignment.TopStart).padding(start = 42.dp, top = 4.dp), color = AccentBlue, style = MaterialTheme.typography.labelSmall)
            Text("72", modifier = Modifier.align(Alignment.TopEnd).padding(end = 42.dp, top = 4.dp), color = AccentBlue, style = MaterialTheme.typography.labelSmall)

            Box(
                modifier = Modifier
                    .size(artboardWidth, artboardHeight)
                    .background(Color.White.copy(alpha = 0.98f))
                    .border(2.dp, AccentBlue),
            ) {
                DottedArtboardBackground()
                if (document != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp)
                            .alpha(0.34f),
                    ) {
                        DefaultUiRenderEngine.Render(document, state.runtimeState, state::dispatchDomain)
                    }
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No preview", color = MutedInk)
                    }
                }
                SelectionHandle(Alignment.TopStart)
                SelectionHandle(Alignment.TopCenter)
                SelectionHandle(Alignment.TopEnd)
                SelectionHandle(Alignment.CenterStart)
                SelectionHandle(Alignment.CenterEnd)
                SelectionHandle(Alignment.BottomStart)
                SelectionHandle(Alignment.BottomCenter)
                SelectionHandle(Alignment.BottomEnd)
            }
        }
    }
}

@Composable
private fun InspectorPane(
    state: MissionEditorStateHolder,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(
            "Inspector",
            modifier = Modifier.padding(start = 12.dp, bottom = 16.dp),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )

        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(8.dp),
            color = Color.White,
            border = BorderStroke(1.dp, PanelStroke),
        ) {
            Column(Modifier.fillMaxSize()) {
                TabStrip(
                    tabs = InspectorTab.entries,
                    selected = state.viewState.inspectorTab,
                    title = { it.title },
                    onSelect = state::selectInspectorTab,
                )
                when (state.viewState.inspectorTab) {
                    InspectorTab.Design -> InspectorDesign(state)
                    InspectorTab.Comments -> InspectorComments(state.runtimeState.documentOrNull, state.runtimeState)
                }
            }
        }
    }
}

@Composable
private fun InspectorDesign(state: MissionEditorStateHolder) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        InspectorSectionBlock(state, InspectorSection.Position) {
            PositionControls(state)
        }
        InspectorSectionBlock(state, InspectorSection.Size) {
            SizeControls(state)
        }
        InspectorSectionBlock(state, InspectorSection.Appearance) {
            AppearanceControls(state)
        }
        InspectorSectionBlock(state, InspectorSection.Constraints) {
            ConstraintControls(state)
        }
    }
}

@Composable
private fun InspectorSectionBlock(
    state: MissionEditorStateHolder,
    section: InspectorSection,
    content: @Composable () -> Unit,
) {
    val expanded = section in state.viewState.expandedSections
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(BorderStroke(0.5.dp, SoftStroke))
            .padding(horizontal = 22.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { state.toggleSection(section) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(section.title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(if (expanded) "^" else "v", color = Color(0xFF31516E), fontWeight = FontWeight.Bold)
        }
        if (expanded) {
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
private fun PositionControls(state: MissionEditorStateHolder) {
    val draft = state.viewState.inspectorDraft
    Row(horizontalArrangement = Arrangement.spacedBy(22.dp), verticalAlignment = Alignment.CenterVertically) {
        AnchorGrid()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            InspectorInput("X", draft.x, "px") { value -> state.updateDraft { current -> current.copy(x = value.sanitizeInput()) } }
            InspectorInput("Y", draft.y, "px") { value -> state.updateDraft { current -> current.copy(y = value.sanitizeInput()) } }
        }
    }
}

@Composable
private fun SizeControls(state: MissionEditorStateHolder) {
    val draft = state.viewState.inspectorDraft
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        InspectorInput("W", draft.width, "px", Modifier.weight(1f)) { value -> state.updateDraft { current -> current.copy(width = value.sanitizeInput()) } }
        InspectorInput("H", draft.height, "px", Modifier.weight(1f)) { value -> state.updateDraft { current -> current.copy(height = value.sanitizeInput()) } }
    }
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        SelectLike(draft.widthMode, Modifier.weight(1f))
        SelectLike(draft.heightMode, Modifier.weight(1f))
    }
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(draft.clipContent, onCheckedChange = { checked -> state.updateDraft { it.copy(clipContent = checked) } })
        Text("Clip content", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun AppearanceControls(state: MissionEditorStateHolder) {
    val draft = state.viewState.inspectorDraft
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("eye", style = MaterialTheme.typography.bodySmall, color = Color(0xFF31516E))
        Text("${draft.opacity.roundToInt()}%", style = MaterialTheme.typography.bodySmall)
        Slider(
            value = draft.opacity,
            onValueChange = { value -> state.updateDraft { it.copy(opacity = value) } },
            valueRange = 0f..100f,
            modifier = Modifier.weight(1f),
        )
        SmallSquareButton("eye", onClick = {})
    }
    Spacer(Modifier.height(10.dp))
    LabeledField("Fill") {
        SwatchField(Color.White, draft.fill, draft.fillOpacity)
    }
    Spacer(Modifier.height(10.dp))
    LabeledField("Stroke") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            SwatchField(AccentBlue, draft.stroke, draft.strokeWidth)
            SelectLike("list", Modifier.width(70.dp))
        }
    }
    Spacer(Modifier.height(10.dp))
    InspectorInput("Radius", draft.radius, "px") { value -> state.updateDraft { current -> current.copy(radius = value.sanitizeInput()) } }
    Spacer(Modifier.height(10.dp))
    LabeledField("Shadow") {
        SelectLike(draft.shadow, Modifier.fillMaxWidth())
    }
}

@Composable
private fun ConstraintControls(state: MissionEditorStateHolder) {
    val draft = state.viewState.inspectorDraft
    Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
        ConstraintGlyph()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SelectLike(draft.horizontalConstraint, Modifier.fillMaxWidth())
            SelectLike(draft.verticalConstraint, Modifier.fillMaxWidth())
        }
    }
    Spacer(Modifier.height(10.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(draft.fixedWhenScrolling, onCheckedChange = { checked -> state.updateDraft { it.copy(fixedWhenScrolling = checked) } })
        Text("Fix position when scrolling", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun InspectorComments(document: UiDocument?, runtimeState: UiVisualizationState) {
    val comments = document?.comments.orEmpty()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (comments.isEmpty()) {
            Text("No comments yet.", color = MutedInk, style = MaterialTheme.typography.bodySmall)
        } else {
            comments.forEach { comment ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = if (comment.targetId == runtimeState.selectedNodeId) Color(0xFFEAF4FF) else Color(0xFFFFFBF7),
                    border = BorderStroke(1.dp, SoftStroke),
                ) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(comment.author, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
                        Text(comment.body, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun <T> TabStrip(
    tabs: List<T>,
    selected: T,
    title: (T) -> String,
    onSelect: (T) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .background(Color(0xFFFDFEFF))
            .border(BorderStroke(1.dp, SoftStroke)),
    ) {
        tabs.forEach { tab ->
            val isSelected = tab == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable { onSelect(tab) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    title(tab),
                    color = if (isSelected) AccentBlue else Color.Black,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                )
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth(0.7f)
                            .height(3.dp)
                            .background(AccentBlue),
                    )
                }
            }
        }
    }
}

@Composable
private fun InspectorInput(
    label: String,
    value: String,
    suffix: String,
    modifier: Modifier = Modifier,
    onChange: (String) -> Unit,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, modifier = Modifier.width(26.dp), style = MaterialTheme.typography.bodySmall, color = Color.Black)
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall,
            trailingIcon = { Text(suffix, style = MaterialTheme.typography.bodySmall, color = Color.Black) },
        )
    }
}

@Composable
private fun LabeledField(
    label: String,
    content: @Composable () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(label, modifier = Modifier.width(52.dp), style = MaterialTheme.typography.bodySmall)
        Box(Modifier.weight(1f)) { content() }
    }
}

@Composable
private fun SwatchField(
    color: Color,
    value: String,
    rightValue: String,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().height(38.dp),
        shape = RoundedCornerShape(6.dp),
        color = Color.White,
        border = BorderStroke(1.dp, SoftStroke),
    ) {
        Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(22.dp).background(color, RoundedCornerShape(4.dp)).border(1.dp, SoftStroke, RoundedCornerShape(4.dp)))
            Text(value, modifier = Modifier.padding(start = 6.dp).weight(1f), style = MaterialTheme.typography.bodySmall)
            Text(rightValue, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SelectLike(value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.height(38.dp),
        shape = RoundedCornerShape(6.dp),
        color = Color.White,
        border = BorderStroke(1.dp, SoftStroke),
    ) {
        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(value, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, maxLines = 1)
            Text("v", style = MaterialTheme.typography.bodySmall, color = Color(0xFF31516E))
        }
    }
}

@Composable
private fun DeviceControl(
    selected: DeviceMode,
    onSelect: (DeviceMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(8.dp),
        color = Color.White,
        border = BorderStroke(1.dp, PanelStroke),
        shadowElevation = 2.dp,
    ) {
        Row {
            DeviceMode.entries.forEach { mode ->
                val active = mode == selected
                Box(
                    modifier = Modifier
                        .width(92.dp)
                        .fillMaxHeight()
                        .background(if (active) Color(0xFFEAF4FF) else Color.White)
                        .clickable { onSelect(mode) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        mode.title,
                        color = if (active) AccentBlue else Color.Black,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (active) Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp).background(AccentBlue))
                }
            }
        }
    }
}

@Composable
private fun FloatingToolbar(
    selected: ToolMode,
    onSelect: (ToolMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.height(54.dp).widthIn(min = 500.dp, max = 560.dp),
        shape = RoundedCornerShape(8.dp),
        color = Color.White,
        border = BorderStroke(1.dp, PanelStroke),
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxSize().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ToolMode.entries.forEach { tool ->
                val active = tool == selected
                Box(
                    modifier = Modifier
                        .size(if (tool == ToolMode.Code) 50.dp else 36.dp)
                        .background(if (active) AccentBlue else Color.White, RoundedCornerShape(8.dp))
                        .clickable { onSelect(tool) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        tool.label,
                        color = if (active) Color.White else Ink,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun MenuButton() {
    Box(
        modifier = Modifier
            .size(48.dp)
            .background(AccentBlue, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            repeat(3) {
                Box(Modifier.width(20.dp).height(2.dp).background(Color.White, RoundedCornerShape(1.dp)))
            }
        }
    }
}

@Composable
private fun SmallSquareButton(label: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.size(38.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(7.dp),
        color = Color.White,
        border = BorderStroke(1.dp, PanelStroke),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.titleMedium, color = Ink)
        }
    }
}

@Composable
private fun HeaderIconButton(label: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.size(48.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFFFDFEFF),
        border = BorderStroke(1.dp, PanelStroke),
        shadowElevation = 2.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = Ink, maxLines = 1)
        }
    }
}

@Composable
private fun MiniThumbnail(selected: Boolean) {
    Canvas(
        modifier = Modifier
            .width(66.dp)
            .height(52.dp)
            .background(Color.White, RoundedCornerShape(4.dp))
            .border(1.dp, if (selected) Color(0xFFB9D9FF) else PanelStroke, RoundedCornerShape(4.dp))
            .padding(5.dp),
    ) {
        val blue = AccentBlue.copy(alpha = 0.85f)
        drawRect(Color(0xFFEAF2FA), topLeft = Offset(5f, 5f), size = androidx.compose.ui.geometry.Size(18f, 13f))
        drawRect(blue, topLeft = Offset(5f, 21f), size = androidx.compose.ui.geometry.Size(18f, 16f))
        drawRect(Color(0xFFD9E5F1), topLeft = Offset(28f, 6f), size = androidx.compose.ui.geometry.Size(24f, 4f))
        drawRect(Color(0xFFD9E5F1), topLeft = Offset(28f, 16f), size = androidx.compose.ui.geometry.Size(24f, 4f))
        drawRect(Color(0xFFD9E5F1), topLeft = Offset(28f, 28f), size = androidx.compose.ui.geometry.Size(20f, 4f))
    }
}

@Composable
private fun AnchorGrid() {
    Surface(
        modifier = Modifier.size(82.dp),
        shape = RoundedCornerShape(7.dp),
        color = Color.White,
        border = BorderStroke(1.dp, PanelStroke),
    ) {
        Canvas(Modifier.fillMaxSize().padding(12.dp)) {
            val xs = listOf(size.width * 0.1f, size.width * 0.5f, size.width * 0.9f)
            val ys = listOf(size.height * 0.1f, size.height * 0.5f, size.height * 0.9f)
            xs.forEach { x ->
                ys.forEach { y ->
                    drawCircle(
                        color = if (x == xs[1] && y == ys[1]) AccentBlue else Color(0xFFC8D3DF),
                        radius = if (x == xs[1] && y == ys[1]) 4.5.dp.toPx() else 3.5.dp.toPx(),
                        center = Offset(x, y),
                        style = Stroke(width = if (x == xs[1] && y == ys[1]) 2.dp.toPx() else 1.2.dp.toPx()),
                    )
                }
            }
        }
    }
}

@Composable
private fun ConstraintGlyph() {
    Surface(
        modifier = Modifier.size(82.dp),
        shape = RoundedCornerShape(7.dp),
        color = Color.White,
        border = BorderStroke(1.dp, PanelStroke),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text("^\n< o >\nv", color = AccentBlue, textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun DottedArtboardBackground() {
    Canvas(Modifier.fillMaxSize()) {
        drawRect(Color.White)
        val step = 14.dp.toPx()
        var x = step
        while (x < size.width) {
            var y = step
            while (y < size.height) {
                drawCircle(Color(0xFFEAF0F6), radius = 0.55.dp.toPx(), center = Offset(x, y))
                y += step
            }
            x += step
        }
    }
}

@Composable
private fun SelectionHandle(alignment: Alignment) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = alignment,
    ) {
        Box(
            Modifier
                .size(10.dp)
                .background(Color.White)
                .border(2.dp, AccentBlue),
        )
    }
}

@Composable
private fun DimensionBadge(
    text: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFFF8FBFF),
        border = BorderStroke(1.dp, Color(0xFFBFD8F5)),
    ) {
        Text(text, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), color = AccentBlue, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun GuideLine(modifier: Modifier = Modifier, horizontal: Boolean) {
    Canvas(modifier.then(if (horizontal) Modifier.fillMaxWidth().height(1.dp) else Modifier.width(1.dp).fillMaxHeight())) {
        val dash = 6.dp.toPx()
        val gap = 5.dp.toPx()
        if (horizontal) {
            var x = 0f
            while (x < size.width) {
                drawLine(AccentBlue.copy(alpha = 0.45f), Offset(x, 0f), Offset((x + dash).coerceAtMost(size.width), 0f), strokeWidth = 1.dp.toPx())
                x += dash + gap
            }
        } else {
            var y = 0f
            while (y < size.height) {
                drawLine(AccentBlue.copy(alpha = 0.45f), Offset(0f, y), Offset(0f, (y + dash).coerceAtMost(size.height)), strokeWidth = 1.dp.toPx())
                y += dash + gap
            }
        }
    }
}

@Composable
private fun VerticalDivider() {
    Box(Modifier.fillMaxHeight().width(1.dp).background(Color(0xFFCFE0EF)))
}

private fun String.sanitizeInput(): String =
    filter { it.isDigit() || it == '.' }.ifBlank { this }
