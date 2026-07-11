package io.aequicor.visualization

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.aequicor.visualization.editor.data.DefaultDesignDocumentRepository
import io.aequicor.visualization.editor.data.DefaultDraftRepository
import io.aequicor.visualization.editor.data.createKeyValueStore
import io.aequicor.visualization.editor.domain.ClearDraftUseCase
import io.aequicor.visualization.editor.domain.LoadDesignDocumentUseCase
import io.aequicor.visualization.editor.domain.ExportIssuesPromptUseCase
import io.aequicor.visualization.editor.domain.RestoreDraftSourcesUseCase
import io.aequicor.visualization.editor.domain.SaveDraftUseCase
import io.aequicor.visualization.editor.domain.compileMissionDocuments
import io.aequicor.visualization.editor.presentation.DesignEditorIntent
import io.aequicor.visualization.editor.presentation.DraftController
import io.aequicor.visualization.editor.presentation.EditorWorkspaceState
import io.aequicor.visualization.editor.presentation.FocusMode
import io.aequicor.visualization.editor.presentation.InspectorSection
import io.aequicor.visualization.editor.presentation.WorkspaceLimits
import io.aequicor.visualization.editor.presentation.createDesignEditorState
import io.aequicor.visualization.editor.presentation.projectDisplayName
import io.aequicor.visualization.editor.presentation.reduceAnnotationWorkspace
import io.aequicor.visualization.editor.presentation.reduceDesignEditor
import io.aequicor.visualization.editor.presentation.screenFileNamesByPageId
import io.aequicor.visualization.subsystems.annotations.ExportScope
import io.aequicor.visualization.editor.platform.CanvasExportBounds
import io.aequicor.visualization.editor.ui.EditorCanvasPane
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import io.aequicor.visualization.editor.ui.EditorIcon
import io.aequicor.visualization.editor.ui.EditorInspectorPane
import io.aequicor.visualization.editor.ui.EditorSourcePane
import io.aequicor.visualization.editor.ui.EditorSvgIcon
import io.aequicor.visualization.editor.ui.horizontalResizeCursor
import io.aequicor.visualization.editor.ui.theme.EditorTheme
import io.aequicor.visualization.editor.ui.theme.LocalEditorColors
import io.aequicor.visualization.engine.ir.layout.LayoutBox
import io.aequicor.visualization.engine.ir.model.DesignColor

/**
 * Presentation holder for the Mission Editor: keeps the design-document state
 * ([designState]) separate from the ephemeral workspace/view state ([workspace]) per
 * the design book. Document actions flow through [dispatch] into the pure reducer;
 * workspace changes go through [updateWorkspace] and never touch the document.
 */
/** Debounce before autosaving a source change, so a burst of edits coalesces into one write. */
private const val AutosaveDebounceMs: Long = 600L

@Stable
class MissionEditorStateHolder(
    private val loadDesignDocument: LoadDesignDocumentUseCase,
    private val draft: DraftController? = null,
) {
    private val defaultDocuments = loadDesignDocument()

    var designState by mutableStateOf(createDesignEditorState(defaultDocuments))
        private set

    var projectName by mutableStateOf("")
        private set

    /**
     * True once the working set is a persistent project: a restored draft / opened
     * project, or after the user explicitly saves. The bundled Welcome project is
     * in-memory — autosave stays off until this flips, so a reload (or «Открыть →
     * Welcome») restores the pristine screens.
     */
    var persistenceEnabled by mutableStateOf(false)
        private set

    val displayProjectName: String
        get() = projectDisplayName(projectName, designState.document?.name, designState.sources)

    var workspace by mutableStateOf(EditorWorkspaceState())
        private set

    /** Node currently hovered on the canvas or in Layers; separated from workspace to avoid full workbench recomposition on pointer moves. */
    var hoveredNodeId by mutableStateOf("")
        private set

    /** Last computed layout of the previewed frame, in document coordinates. */
    var artboardLayout by mutableStateOf<LayoutBox?>(null)
        private set

    /** Last known browser-window bounds of the editable canvas surface, used for export crops. */
    var canvasExportBounds by mutableStateOf<CanvasExportBounds?>(null)
        private set

    /** True while a canvas move/resize drag is live; Escape then cancels it. */
    var activeDrag by mutableStateOf(false)
        private set

    private var cancelDragRequested = false

    /** Marks the start/end of a live canvas drag (see [requestCancelDrag]). */
    fun beginDrag() {
        activeDrag = true
        cancelDragRequested = false
    }

    fun endDrag() {
        activeDrag = false
        cancelDragRequested = false
    }

    /** Escape while dragging: request the in-progress gesture abort itself. */
    fun requestCancelDrag() {
        if (activeDrag) cancelDragRequested = true
    }

    /** The gesture loop polls this; true once means "abort this drag". */
    fun consumeCancelDrag(): Boolean {
        val requested = cancelDragRequested
        cancelDragRequested = false
        return requested
    }

    fun dispatch(intent: DesignEditorIntent) {
        designState = reduceDesignEditor(designState, intent)
        // Annotation view intents (expand/select/tool) live in workspace state, and a
        // deleted annotation prunes its view entries; every other intent is a no-op here.
        updateWorkspace { reduceAnnotationWorkspace(it, intent) }
    }

    private val exportIssuesPromptUseCase = ExportIssuesPromptUseCase()

    /**
     * Builds the "fix these design issues" AI prompt from the issue annotations in
     * [scope] with resolved node context; the toolbar copies it to the clipboard.
     */
    fun exportIssuesPrompt(scope: ExportScope): String = exportIssuesPromptUseCase(
        layers = designState.annotationLayers.values.toList(),
        scope = scope,
        document = designState.document,
        screenFileNameByPageId = designState.screenFileNamesByPageId(),
    )

    /**
     * Restores a persisted draft (if any) into the editor, then autosaves subsequent
     * SLM-source changes (debounced). Runs until cancelled; call once from a
     * `LaunchedEffect`. A null [draft] disables persistence.
     */
    @OptIn(kotlinx.coroutines.FlowPreview::class)
    suspend fun runPersistence() {
        val draft = draft ?: return
        draft.restore()?.let { restored ->
            persistenceEnabled = true
            projectName = restored.projectName
            designState = createDesignEditorState(compileMissionDocuments(restored.files))
        }
        // Only the SLM `sources` are persisted; edits that do not write back leave them
        // unchanged, so snapshotFlow never emits for them. drop(1) skips the just-restored
        // value so restore does not immediately re-save. The gate sits inside collect
        // (checked at fire time) so the in-memory Welcome project never autosaves and a
        // stale debounced write cannot land after «Открыть → Welcome» clears the draft.
        snapshotFlow { designState.sources }
            .drop(1)
            .distinctUntilChanged()
            .debounce(AutosaveDebounceMs)
            .collect { sources -> if (persistenceEnabled) draft.save(sources, displayProjectName) }
    }

    /**
     * Explicit Save: force-flush the current SLM sources to the draft now. This is the
     * opt-in that turns the in-memory Welcome project into a persistent one.
     */
    fun saveDraftNow() {
        val draft = draft ?: return
        persistenceEnabled = true
        draft.saveNow(designState.sources, displayProjectName)
    }

    /**
     * «Открыть → Welcome»: reseed the editor with the bundled in-memory Welcome project.
     * Non-destructive — autosave switches off but a saved draft stays untouched, so a
     * reload returns to the saved project; the Welcome copy persists only if the user
     * explicitly saves it.
     */
    fun openWelcomeProject() {
        persistenceEnabled = false
        projectName = ""
        designState = createDesignEditorState(loadDesignDocument())
    }

    /**
     * Reset: discard the draft and reseed the editor from the bundled Welcome sources.
     * Destructive counterpart of [openWelcomeProject]; currently has no UI entry point.
     */
    fun resetToDefaults() {
        persistenceEnabled = false
        val draft = draft
        if (draft == null) {
            projectName = ""
            designState = createDesignEditorState(loadDesignDocument())
            return
        }
        draft.reset { designState = createDesignEditorState(loadDesignDocument()) }
        projectName = ""
    }

    fun onArtboardLayout(layout: LayoutBox?) {
        if (artboardLayout == layout) return
        artboardLayout = layout
    }

    fun onCanvasExportBounds(bounds: CanvasExportBounds) {
        if (canvasExportBounds == bounds) return
        canvasExportBounds = bounds
    }

    fun updateHoveredNode(nodeId: String) {
        if (hoveredNodeId == nodeId) return
        hoveredNodeId = nodeId
    }

    fun updateWorkspace(block: (EditorWorkspaceState) -> EditorWorkspaceState) {
        val next = block(workspace)
        if (next != workspace) workspace = next
    }

    fun toggleSection(section: InspectorSection) {
        updateWorkspace { ws ->
            val expanded = ws.expandedSections
            ws.copy(expandedSections = if (section in expanded) expanded - section else expanded + section)
        }
    }

    /** Records a committed color as most-recent (deduped, capped) for the color picker. */
    fun addRecentColor(color: DesignColor) {
        updateWorkspace { ws ->
            ws.copy(recentColors = (listOf(color) + ws.recentColors.filter { it != color }).take(12))
        }
    }
}

@Composable
fun MissionEditorApp() {
    EditorTheme {
        val scope = rememberCoroutineScope()
        val state = remember {
            // Composition root: wire the persistence slice. The dispatcher is injected
            // here (the boundary), not taken from Dispatchers.* inside the repository.
            val draftRepository = DefaultDraftRepository(createKeyValueStore(), Dispatchers.Default)
            MissionEditorStateHolder(
                loadDesignDocument = LoadDesignDocumentUseCase(DefaultDesignDocumentRepository()),
                draft = DraftController(
                    saveDraft = SaveDraftUseCase(draftRepository),
                    clearDraft = ClearDraftUseCase(draftRepository),
                    restoreDraftSources = RestoreDraftSourcesUseCase(draftRepository),
                    scope = scope,
                ),
            )
        }
        LaunchedEffect(state) { state.runPersistence() }
        MissionEditorScreen(state)
    }
}

@Composable
private fun MissionEditorScreen(state: MissionEditorStateHolder) {
    // The editor fills the window edge-to-edge: no chrome frame, no shell margin, no
    // rounded shell corners — the working area gets the whole viewport.
    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Color.White)) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(0.dp),
            color = Color.White,
            shadowElevation = 0.dp,
        ) {
            when {
                state.workspace.isMainOnly -> MainOnlyLayout(state)
                maxWidth < 1100.dp -> StackedLayout(state)
                else -> WorkbenchLayout(state)
            }
        }
    }
}

// --- Full three-pane workbench ----------------------------------------------

@Composable
private fun WorkbenchLayout(state: MissionEditorStateHolder) {
    val ws = state.workspace
    val sourceWidthDp = ws.sourceWidthDp.coerceIn(WorkspaceLimits.MinSourceDp, WorkspaceLimits.MaxSourceDp)
    val inspectorWidthDp = ws.inspectorWidthDp.coerceIn(WorkspaceLimits.MinInspectorDp, WorkspaceLimits.MaxInspectorDp)
    Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(0.dp)) {
        // Left: Source + Screens (or collapsed rail).
        if (ws.sourceCollapsed) {
            CollapsedRail(label = "Source", icon = EditorIcon.Source, onExpand = { state.updateWorkspace { it.copy(sourceCollapsed = false) } })
        } else {
            EditorSourcePane(state, Modifier.width(sourceWidthDp.dp).fillMaxHeight())
            VerticalSplitter(
                onDeltaDp = { d ->
                    state.updateWorkspace {
                        it.copy(sourceWidthDp = (it.sourceWidthDp + d).coerceIn(WorkspaceLimits.MinSourceDp, WorkspaceLimits.MaxSourceDp))
                    }
                },
                onReset = { state.updateWorkspace { it.copy(sourceWidthDp = WorkspaceLimits.DefaultSourceDp) } },
            )
        }

        // Center: canvas (flexes).
        EditorCanvasPane(state, Modifier.weight(1f).fillMaxHeight())

        // Right: Inspector (or collapsed rail).
        if (ws.inspectorCollapsed) {
            CollapsedRail(label = "Inspector", icon = EditorIcon.Inspector, onExpand = { state.updateWorkspace { it.copy(inspectorCollapsed = false) } })
        } else {
            VerticalSplitter(
                onDeltaDp = { d ->
                    state.updateWorkspace {
                        // Splitter is left of the inspector, so dragging right shrinks it.
                        it.copy(inspectorWidthDp = (it.inspectorWidthDp - d).coerceIn(WorkspaceLimits.MinInspectorDp, WorkspaceLimits.MaxInspectorDp))
                    }
                },
                onReset = { state.updateWorkspace { it.copy(inspectorWidthDp = WorkspaceLimits.DefaultInspectorDp) } },
            )
            EditorInspectorPane(state, Modifier.width(inspectorWidthDp.dp).fillMaxHeight())
        }
    }
}

// --- Main-only / focus mode --------------------------------------------------

@Composable
private fun MainOnlyLayout(state: MissionEditorStateHolder) {
    val colors = LocalEditorColors.current
    Box(Modifier.fillMaxSize()) {
        EditorCanvasPane(state, Modifier.fillMaxSize())
        Surface(
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp).clickable {
                state.updateWorkspace { it.copy(focusMode = FocusMode.Normal) }
            },
            shape = RoundedCornerShape(8.dp),
            color = colors.raisedSurface,
            border = BorderStroke(1.dp, colors.panelStroke),
            shadowElevation = 4.dp,
        ) {
            Text(
                "Exit focus  (Esc)",
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                style = MaterialTheme.typography.labelMedium,
                color = colors.ink,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

// --- Narrow / responsive stacked layout -------------------------------------

@Composable
private fun StackedLayout(state: MissionEditorStateHolder) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (!state.workspace.sourceCollapsed) {
            EditorSourcePane(state, Modifier.fillMaxWidth().height(560.dp))
        }
        EditorCanvasPane(state, Modifier.fillMaxWidth().height(640.dp))
        if (!state.workspace.inspectorCollapsed) {
            Box(Modifier.fillMaxWidth()) {
                EditorInspectorPane(
                    state,
                    Modifier
                        .align(Alignment.CenterEnd)
                        .widthIn(max = WorkspaceLimits.MaxInspectorDp.dp)
                        .fillMaxWidth()
                        .height(640.dp),
                )
            }
        }
    }
}

// --- Splitter & rails --------------------------------------------------------

/**
 * Draggable vertical splitter between panels. Hovering shows a horizontal-resize
 * cursor; dragging reports the drag in dp. Double-click resets to the default width.
 * Text is not wrapped in a `SelectionContainer`, so dragging never selects text.
 */
@Composable
private fun VerticalSplitter(onDeltaDp: (Float) -> Unit, onReset: () -> Unit) {
    val colors = LocalEditorColors.current
    val density = LocalDensity.current.density
    var active by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(10.dp)
            .pointerHoverIcon(horizontalResizeCursor())
            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { onReset() }) }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { active = true },
                    onDragEnd = { active = false },
                    onDragCancel = { active = false },
                ) { change, dragAmount ->
                    change.consume()
                    onDeltaDp(dragAmount.x / density)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .width(if (active) 2.dp else 1.dp)
                .fillMaxHeight()
                .background(Color.Transparent),
        )
    }
}

/** Thin rail shown in place of a collapsed panel; click re-expands it. */
@Composable
private fun CollapsedRail(label: String, icon: EditorIcon, onExpand: () -> Unit) {
    val colors = LocalEditorColors.current
    Surface(
        modifier = Modifier.fillMaxHeight().width(36.dp).clickable(onClick = onExpand),
        shape = RoundedCornerShape(8.dp),
        color = colors.raisedSurface,
        border = BorderStroke(1.dp, colors.panelStroke),
    ) {
        Column(
            Modifier.fillMaxSize().padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            EditorSvgIcon(icon = icon, contentDescription = label, modifier = Modifier.size(20.dp), tint = colors.accent)
            // Vertical label, one glyph per line, so the rail stays narrow.
            label.take(9).forEach { ch ->
                Text(ch.toString(), style = MaterialTheme.typography.labelSmall, color = colors.mutedInk)
            }
        }
    }
}

