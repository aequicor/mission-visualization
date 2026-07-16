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
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.runtime.CompositionLocalProvider
import io.aequicor.visualization.editor.data.DefaultDesignDocumentRepository
import io.aequicor.visualization.editor.data.DefaultDraftRepository
import io.aequicor.visualization.editor.data.DefaultRecentProjectsRepository
import io.aequicor.visualization.editor.data.FolderPendingStorageKey
import io.aequicor.visualization.editor.data.LanguagePreference
import io.aequicor.visualization.editor.data.ProjectSnapshot
import io.aequicor.visualization.editor.data.WorkspaceStateRepository
import io.aequicor.visualization.editor.data.createKeyValueStore
import io.aequicor.visualization.editor.data.decodeProjectSnapshot
import io.aequicor.visualization.editor.data.encodeProjectSourcesJson
import io.aequicor.visualization.editor.domain.AppLanguage
import io.aequicor.visualization.editor.domain.RecentProject
import io.aequicor.visualization.editor.domain.RecentProjectKind
import io.aequicor.visualization.editor.domain.RecentProjectsRepository
import io.aequicor.visualization.editor.domain.WelcomeProjectId
import io.aequicor.visualization.editor.domain.ClearDraftUseCase
import io.aequicor.visualization.editor.domain.LoadDesignDocumentUseCase
import io.aequicor.visualization.editor.domain.ExportIssuesPromptUseCase
import io.aequicor.visualization.editor.domain.MissionDocumentSource
import io.aequicor.visualization.editor.domain.MissionDocuments
import io.aequicor.visualization.editor.domain.RestoreDraftSourcesUseCase
import io.aequicor.visualization.editor.domain.SaveDraftUseCase
import io.aequicor.visualization.editor.domain.compileMissionDocuments
import io.aequicor.visualization.editor.presentation.DesignEditorIntent
import io.aequicor.visualization.editor.presentation.DesignEditorState
import io.aequicor.visualization.editor.presentation.DraftController
import io.aequicor.visualization.editor.presentation.EditorWorkspaceState
import io.aequicor.visualization.editor.presentation.FocusMode
import io.aequicor.visualization.editor.presentation.InspectorSection
import io.aequicor.visualization.editor.presentation.PersistedWorkspaceState
import io.aequicor.visualization.editor.presentation.WorkspaceLimits
import io.aequicor.visualization.editor.presentation.createDesignEditorState
import io.aequicor.visualization.editor.presentation.projectDisplayName
import io.aequicor.visualization.editor.presentation.persistedLayout
import io.aequicor.visualization.editor.presentation.persistedProjectWorkspace
import io.aequicor.visualization.editor.presentation.reduceAnnotationWorkspace
import io.aequicor.visualization.editor.presentation.reduceDesignEditor
import io.aequicor.visualization.editor.presentation.restoreProjectWorkspace
import io.aequicor.visualization.editor.presentation.restoreWorkspace
import io.aequicor.visualization.editor.presentation.screenFileNamesByPageId
import io.aequicor.visualization.subsystems.annotations.ExportScope
import io.aequicor.visualization.editor.platform.CanvasExportBounds
import io.aequicor.visualization.editor.platform.FolderFileWrite
import io.aequicor.visualization.editor.platform.folderSyncRevision
import io.aequicor.visualization.editor.platform.folderSyncError
import io.aequicor.visualization.editor.platform.folderSyncSnapshotJson
import io.aequicor.visualization.editor.platform.folderSyncStatus
import io.aequicor.visualization.editor.platform.ProjectLandingMode
import io.aequicor.visualization.editor.platform.ProjectStorageMode
import io.aequicor.visualization.editor.platform.platformActiveFolderId
import io.aequicor.visualization.editor.platform.platformConnectFolderById
import io.aequicor.visualization.editor.platform.platformConnectFolderLive
import io.aequicor.visualization.editor.platform.platformCreateFolderProject
import io.aequicor.visualization.editor.platform.platformDisconnectFolder
import io.aequicor.visualization.editor.platform.platformEpochMillis
import io.aequicor.visualization.editor.platform.platformForgetFolder
import io.aequicor.visualization.editor.platform.platformInitFolderSync
import io.aequicor.visualization.editor.platform.platformInstallLanding
import io.aequicor.visualization.editor.platform.platformLandingPendingActionJson
import io.aequicor.visualization.editor.platform.platformProjectLandingMode
import io.aequicor.visualization.editor.platform.platformProjectStorageMode
import io.aequicor.visualization.editor.platform.platformRefreshFolder
import io.aequicor.visualization.editor.platform.platformReconnectSavedFolder
import io.aequicor.visualization.editor.platform.platformSavedFolderName
import io.aequicor.visualization.editor.platform.platformSetActiveProjectId
import io.aequicor.visualization.editor.platform.platformSupportsFolderSync
import io.aequicor.visualization.editor.platform.platformSupportsLanding
import io.aequicor.visualization.editor.platform.platformWriteFolderFiles
import io.aequicor.visualization.editor.ui.EditorBrowserSaveBanner
import io.aequicor.visualization.editor.ui.EditorCanvasPane
import io.aequicor.visualization.editor.ui.buildLandingConfigJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import io.aequicor.visualization.editor.ui.EditorIcon
import io.aequicor.visualization.editor.ui.EditorInspectorPane
import io.aequicor.visualization.editor.ui.ProjectLandingScreen
import io.aequicor.visualization.editor.ui.EditorSourcePane
import io.aequicor.visualization.editor.ui.EditorSvgIcon
import io.aequicor.visualization.editor.ui.horizontalResizeCursor
import io.aequicor.visualization.editor.ui.strings.LocalStrings
import io.aequicor.visualization.editor.ui.strings.appStringsFor
import io.aequicor.visualization.editor.ui.theme.EditorTheme
import io.aequicor.visualization.editor.ui.theme.LocalEditorColors
import io.aequicor.visualization.engine.ir.layout.LayoutBox
import io.aequicor.visualization.engine.ir.model.DesignColor
import io.aequicor.visualization.engine.ir.model.DesignDocument
import io.aequicor.visualization.mcp.McpServerController
import io.aequicor.visualization.mcp.NoMcpServerController

/**
 * Presentation holder for the Mission Editor: keeps the design-document state
 * ([designState]) separate from the user-local workspace/view state ([workspace]) per
 * the design book. Document actions flow through [dispatch] into the pure reducer;
 * workspace changes go through [updateWorkspace] and never touch the document.
 */
/** Debounce before autosaving a source change, so a burst of edits coalesces into one write. */
private const val AutosaveDebounceMs: Long = 600L

/** Poll cadence for the live local-folder revision counter (cheap synchronous read on wasmJs). */
private const val FolderSyncPollMs: Long = 400L

/** Poll cadence for the startup landing's user-action queue (cheap synchronous read on wasmJs). */
private const val LandingPollMs: Long = 150L

/** A user action dequeued from the startup project screen (`window.__mvLanding`). */
@Serializable
private data class LandingAction(val type: String, val id: String? = null)

private val LandingActionJson: Json = Json { ignoreUnknownKeys = true }

/**
 * UI-facing state of the live local-folder connection ("browser IDE" mode), mapped from the JS
 * layer's coarse status string by [MissionEditorStateHolder.runFolderSync].
 */
enum class FolderSyncPresence { Idle, Connecting, ReconnectNeeded, Watching }

@Stable
class MissionEditorStateHolder(
    private val loadDesignDocument: LoadDesignDocumentUseCase,
    private val draft: DraftController? = null,
    private val folderPending: DraftController? = null,
    private val languagePreference: LanguagePreference? = null,
    private val recentProjects: RecentProjectsRepository? = null,
    private val workspaceStateRepository: WorkspaceStateRepository? = null,
    val mcpServer: McpServerController = NoMcpServerController,
) {
    private val defaultDocuments = loadDesignDocument()

    private val restoredWorkspaceState = workspaceStateRepository?.load() ?: PersistedWorkspaceState()

    private var persistedWorkspaceState = restoredWorkspaceState

    private var activeProjectId: String = WelcomeProjectId

    /** Prevents the bundled placeholder document from overwriting a not-yet-restored last project. */
    private var workspacePersistenceReady: Boolean =
        restoredWorkspaceState.lastProject?.projectId.let { it == null || it == WelcomeProjectId }

    var designState by mutableStateOf(
        createDesignEditorState(defaultDocuments).let { initial ->
            restoredWorkspaceState.lastProject
                ?.takeIf { it.projectId == WelcomeProjectId }
                ?.let(initial::restoreProjectWorkspace)
                ?: initial
        },
    )
        private set

    /** Active UI language; restored from the local store and persisted on every change. */
    var language by mutableStateOf(languagePreference?.load() ?: AppLanguage.Default)
        private set

    fun selectLanguage(next: AppLanguage) {
        if (language == next) return
        language = next
        languagePreference?.save(next)
    }

    var projectName by mutableStateOf("")
        private set

    /**
     * Whether the current working set is the bundled, unsaved Welcome project. Folder and
     * browser projects keep their identity even when their persistence backend is temporarily
     * unavailable; backend status alone must never turn them back into a Welcome copy.
     */
    private var isWelcomeProject = true

    /** Native Compose project screen; the web owns its equivalent DOM overlay. */
    var projectLandingVisible by mutableStateOf(
        platformProjectLandingMode == ProjectLandingMode.Compose &&
            restoredWorkspaceState.lastProject?.projectId != WelcomeProjectId,
    )
        private set

    var projectLandingError by mutableStateOf<String?>(null)
        private set

    var recentProjectItems by mutableStateOf(recentProjects?.list() ?: emptyList())
        private set

    val usesEmbeddedDraftProjects: Boolean
        get() = platformProjectStorageMode == ProjectStorageMode.EmbeddedDraft

    /** Stable deep-link id of the active browser-only project; null for Welcome/folder projects. */
    private var browserProjectId: String? = null

    private fun ensureBrowserProjectId(): String = browserProjectId ?: "browser-${platformEpochMillis()}".also {
        browserProjectId = it
    }

    /**
     * True once the working set is a persistent (browser-saved) project: a restored draft,
     * an explicit save, or the first write-back edit of the bundled in-memory Welcome (which
     * also raises [projectCreationPromptVisible]). Until this flips, autosave stays off, so a
     * reload (or «Открыть → Welcome») restores the pristine screens.
     */
    var persistenceEnabled by mutableStateOf(false)
        private set

    /**
     * True once the in-memory Welcome project has been turned into a browser-saved project by
     * a user edit (or a recovered draft was restored) and the user has not yet dismissed or
     * acted on the "save to disk" nudge. Drives the top banner; cleared on reseed
     * ([openWelcomeProject], [resetToDefaults], live-folder adopt) and by
     * [dismissProjectCreationPrompt].
     */
    var projectCreationPromptVisible by mutableStateOf(false)
        private set

    fun dismissProjectCreationPrompt() {
        projectCreationPromptVisible = false
    }

    /** Turns the edited in-memory Welcome copy into a browser-only project. */
    fun createBrowserProject() {
        if (!usesEmbeddedDraftProjects) return
        val draft = draft ?: return
        val projectId = ensureBrowserProjectId()
        isWelcomeProject = false
        persistenceEnabled = true
        projectCreationPromptVisible = false
        platformSetActiveProjectId(projectId)
        activateProject(projectId)
        draft.saveNow(designState.sources, displayProjectName, projectId = projectId)
    }

    /** Creates and connects a disk-backed project from the edited Welcome sources. */
    fun createFolderProject() {
        if (!platformSupportsFolderSync) return
        hasAdoptedLiveFolder = false
        platformCreateFolderProject(encodeProjectSourcesJson(displayProjectName, designState.sources))
    }

    /** Creates a fresh disk project seeded from the pristine Welcome tour. */
    fun createWelcomeFolderProject() {
        if (!platformSupportsFolderSync) return
        prepareForProjectSwitch()
        hasAdoptedLiveFolder = false
        projectLandingError = null
        platformCreateFolderProject(
            encodeProjectSourcesJson(
                projectName = projectDisplayName("", defaultDocuments.document?.name, defaultDocuments.sources),
                sources = defaultDocuments.sources,
            ),
        )
    }

    /**
     * Sources of the freshly (re)loaded in-memory Welcome project — the reference [runPersistence]
     * compares against to tell a genuine user edit apart from a reseed assigning the same baseline.
     */
    private var inMemoryBaselineSources: List<MissionDocumentSource> = designState.sources

    // --- Live local-folder sync ("browser IDE" mode) ------------------------------------------

    /** Whether this platform can live-sync a local folder (Chromium web only). */
    val supportsFolderSync: Boolean get() = platformSupportsFolderSync

    /** UI-facing live-folder connection state, refreshed each tick by [runFolderSync]. */
    var folderSync by mutableStateOf(FolderSyncPresence.Idle)
        private set

    /** Name of the connected/remembered folder (status chip + reconnect prompt), or null. */
    var folderName by mutableStateOf<String?>(null)
        private set

    /** True when the latest external snapshot failed to compile — the canvas holds its last good state. */
    var folderExternalError by mutableStateOf(false)
        private set

    /**
     * Monotonic counter bumped on every external-error occurrence, even while [folderExternalError]
     * stays continuously true. Lets the error snackbar re-show after a manual dismiss when a
     * subsequent broken snapshot arrives without the flag ever flipping back to false.
     */
    var folderExternalErrorNonce by mutableStateOf(0)
        private set

    /**
     * The in-editor sources an external change replaced while they were unsaved, or null when
     * there is nothing to recover. Surfaced as a one-click "restore my edit" affordance so a
     * concurrent edit is never silently lost.
     */
    var folderConflictBackup by mutableStateOf<List<MissionDocumentSource>?>(null)
        private set

    /** True once the live folder's first snapshot has been adopted in the current session. */
    private var hasAdoptedLiveFolder = false

    /** Sources as of the last inbound-adopt or outbound-write: the base for concurrency detection. */
    private var lastSyncedSources: List<MissionDocumentSource> = emptyList()

    /** Full document represented by [lastSyncedSources], used only for conflict detection. */
    private var lastSyncedDocument: DesignDocument? = null

    /** Folder id last written to the recent-projects list this session, to record each connection once. */
    private var lastRecordedFolderId: String? = null

    private val liveFolderConnected: Boolean get() = folderSync == FolderSyncPresence.Watching

    val displayProjectName: String
        get() = projectDisplayName(projectName, designState.document?.name, designState.sources)

    var workspace by mutableStateOf(restoredWorkspaceState.layout.restoreWorkspace())
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
        val previousSources = designState.sources
        designState = reduceDesignEditor(designState, intent)
        // Reducer previews keep sources checkpointed, so this fires once on EndInteraction rather
        // than once per pointer frame. Committed inspector/command edits reach disk synchronously;
        // app shutdown can no longer cancel the debounce before the authoritative SLM is written.
        val sourceCommitted = if (
            liveFolderConnected && !designState.interacting && designState.sources != previousSources
        ) {
            writeSourcesToFolder(designState.sources)
        } else {
            false
        }
        if (sourceCommitted) lastSyncedDocument = designState.document
        // Annotation view intents (expand/select/tool) live in workspace state, and a
        // deleted annotation prunes its view entries; every other intent is a no-op here.
        updateWorkspace { reduceAnnotationWorkspace(it, intent) }
        persistWorkspaceState()
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
        val embeddedDraft = draft.takeIf { usesEmbeddedDraftProjects }
        val restoredDraft = embeddedDraft?.restore()?.takeIf { it.folderId == null }
        restoredDraft?.let { restored ->
            val projectId = restored.projectId ?: ensureBrowserProjectId()
            browserProjectId = projectId
            isWelcomeProject = false
            persistenceEnabled = true
            projectName = restored.projectName
            designState = createDesignEditorState(compileMissionDocuments(restored.files))
            platformSetActiveProjectId(projectId)
            activateProject(projectId)
            if (restored.projectId == null) {
                embeddedDraft.saveNow(restored.files, restored.projectName, projectId = projectId)
            }
        }
        if (restoredDraft == null && platformProjectLandingMode == ProjectLandingMode.None) {
            activateProject(WelcomeProjectId)
        }
        // Source write-back is tracked separately from the full desktop document snapshot.
        // drop(1) skips the just-restored value so restore does not immediately re-save. The gate sits inside collect
        // (checked at fire time) so the in-memory Welcome project never autosaves and a
        // stale debounced write cannot land after «Открыть → Welcome» clears the draft.
        coroutineScope {
            // Folder projects get an immediate browser safety copy before the debounced disk write.
            // The copy is tagged with the folder id, so it never appears as a separate project.
            launch {
                snapshotFlow { designState.sources }
                    .drop(1)
                    .distinctUntilChanged()
                    .collect { sources ->
                        if (usesEmbeddedDraftProjects && liveFolderConnected && sources != lastSyncedSources) {
                            platformActiveFolderId()?.let { folderId ->
                                folderPending?.save(sources, displayProjectName, folderId)
                            }
                        }
                    }
            }
            launch {
                snapshotFlow { designState.sources }
                    .drop(1)
                    .distinctUntilChanged()
                    .debounce(AutosaveDebounceMs)
                    .collect { sources ->
                        when {
                            liveFolderConnected -> writeSourcesToFolder(sources)
                            persistenceEnabled && embeddedDraft != null -> embeddedDraft.save(
                                sources,
                                displayProjectName,
                                projectId = ensureBrowserProjectId(),
                            )
                            isWelcomeProject && sources != inMemoryBaselineSources -> {
                                // First write-back edit of Welcome asks where the new project
                                // should live. Until the user chooses, it remains in memory only.
                                projectCreationPromptVisible = true
                            }
                        }
                    }
            }
        }
    }

    /**
     * Drives live local-folder sync: installs the JS layer, probes for a previously connected
     * folder, then polls its revision counter and applies external changes (compile-gated) to the
     * canvas. Runs until cancelled; call once from a `LaunchedEffect`. A no-op where unsupported.
     */
    suspend fun runFolderSync() {
        if (!platformSupportsFolderSync) return
        platformInitFolderSync()
        if (platformProjectLandingMode == ProjectLandingMode.Compose && platformActiveFolderId() == null) {
            persistedWorkspaceState.lastProject
                ?.projectId
                ?.takeIf { it != WelcomeProjectId }
                ?.let(::platformConnectFolderById)
        }
        var lastRevision = -1
        while (true) {
            val rawStatus = folderSyncStatus() ?: "idle"
            val presence = folderPresenceOf(rawStatus)
            if (presence != folderSync) folderSync = presence
            if (rawStatus == "error") projectLandingError = folderSyncError()
            platformSavedFolderName().let { if (it != folderName) folderName = it }
            if (presence != FolderSyncPresence.Watching) {
                hasAdoptedLiveFolder = false
            } else if (platformActiveFolderId() == null) {
                // A gesture-free probe() resumed a previously-connected folder. With the startup
                // landing as the single entry point, a folder goes live only when the user
                // explicitly opens it (connect / connectById / reconnect all set an id). So drop an
                // auto-resumed folder rather than silently re-syncing it — otherwise the in-memory
                // Welcome, or a recovered browser draft, would be written over its on-disk files.
                disconnectFolder()
            } else {
                mcpServer.useProjectFolder(platformActiveFolderId())
                recordConnectedFolderAsRecent()
                val revision = folderSyncRevision()
                if (revision != lastRevision) {
                    lastRevision = revision
                    // Keep the two reads adjacent (no suspension between) so revision/snapshot pair up.
                    folderSyncSnapshotJson()?.let { json ->
                        decodeProjectSnapshot(json)?.let { onFolderSnapshot(it) }
                    }
                }
            }
            delay(FolderSyncPollMs)
        }
    }

    private fun folderPresenceOf(status: String): FolderSyncPresence = when (status) {
        "watching" -> FolderSyncPresence.Watching
        "connecting" -> FolderSyncPresence.Connecting
        "reconnect-needed" -> FolderSyncPresence.ReconnectNeeded
        else -> FolderSyncPresence.Idle
    }

    /**
     * Applies a snapshot pulled from the connected folder. The first snapshot of a live session
     * is adopted wholesale; later ones are compile-gated external changes that preserve the
     * viewport/selection and back up any unsaved local edit they replace.
     */
    private suspend fun onFolderSnapshot(snapshot: ProjectSnapshot) {
        val pending = if (usesEmbeddedDraftProjects && !hasAdoptedLiveFolder) {
            val activeFolderId = platformActiveFolderId()
            folderPending?.restore()?.takeIf { it.folderId != null && it.folderId == activeFolderId }
        } else {
            null
        }
        val incomingSources = pending?.files ?: snapshot.sources
        val docs = compileMissionDocuments(incomingSources)
        val incomingDocument = docs.document?.takeUnless { docs.hasErrors }
        val isEmptyProject = incomingSources.isEmpty()
        // Compile-gate: a torn or broken non-empty external file yields no document — hold the
        // last good canvas. An empty folder is a valid empty project and must not reveal Welcome.
        if (!isEmptyProject && incomingDocument == null) {
            folderExternalError = true
            folderExternalErrorNonce++
            return
        }
        folderExternalError = !isEmptyProject && (docs.document == null || docs.hasErrors)
        if (!hasAdoptedLiveFolder) {
            isWelcomeProject = false
            persistenceEnabled = false
            projectCreationPromptVisible = false
            browserProjectId = null
            projectName = snapshot.projectName
            designState = editorStateFrom(docs, incomingDocument)
            platformActiveFolderId()?.let(::activateProject)
            lastSyncedSources = snapshot.sources
            lastSyncedDocument = designState.document
            folderConflictBackup = null
            hasAdoptedLiveFolder = true
            projectLandingVisible = false
            projectLandingError = null
            if (pending != null) {
                if (pending.files == snapshot.sources) {
                    folderPending?.clear()
                } else {
                    writeSourcesToFolder(pending.files)
                }
            }
            return
        }
        applyExternalSources(docs, incomingDocument)
    }

    /** Reseeds the editor from an external change, preserving selection/viewport and backing up a clobbered local edit. */
    private fun applyExternalSources(docs: MissionDocuments, incomingDocument: DesignDocument?) {
        val incoming = docs.sources
        val current = designState.sources
        val currentDocument = designState.document
        if (incoming == current && incomingDocument == currentDocument) {
            lastSyncedSources = incoming
            lastSyncedDocument = incomingDocument
            return
        }
        val userEditedLocally = current != lastSyncedSources || currentDocument != lastSyncedDocument
        val next = editorStateFrom(docs, incomingDocument)
        val doc = next.document
        val keepPage = if (doc?.pages?.any { it.id == designState.selectedPageId } == true) {
            designState.selectedPageId
        } else {
            next.selectedPageId
        }
        val keepNode = if (designState.selectedNodeId.isNotBlank() && doc?.nodeById(designState.selectedNodeId) != null) {
            designState.selectedNodeId
        } else {
            next.selectedNodeId
        }
        val keepSet = designState.selectedNodeIds.filter { doc?.nodeById(it) != null }.toSet()
            .ifEmpty { if (keepNode.isBlank()) emptySet() else setOf(keepNode) }
        designState = next.copy(
            selectedPageId = keepPage,
            selectedNodeId = keepNode,
            selectedNodeIds = keepSet,
        )
        // workspace (viewport/zoom) is a separate mutableStateOf — left untouched by design.
        if (userEditedLocally) folderConflictBackup = current
        lastSyncedSources = incoming
        lastSyncedDocument = incomingDocument
        persistWorkspaceState()
    }

    private fun editorStateFrom(docs: MissionDocuments, document: DesignDocument?): DesignEditorState {
        val base = createDesignEditorState(docs)
        if (document == null || document == base.document) return base
        val firstPage = document.pages.firstOrNull()
        val firstNode = firstPage?.children?.firstOrNull()?.id.orEmpty()
        return base.copy(
            document = document,
            selectedPageId = firstPage?.id.orEmpty(),
            selectedNodeId = firstNode,
            selectedNodeIds = if (firstNode.isBlank()) emptySet() else setOf(firstNode),
        )
    }

    /**
     * Live outbound compare-and-swap. Every changed/new/deleted file is submitted together; the
     * platform first verifies the complete [lastSyncedSources] base and only then publishes the
     * batch. A conflict or I/O failure leaves the sync base untouched so a later disk snapshot can
     * be presented as an external conflict instead of silently overwriting either side.
     */
    private fun writeSourcesToFolder(sources: List<MissionDocumentSource>): Boolean {
        val base = lastSyncedSources.associate { it.fileName to it.content }
        val next = sources.associate { it.fileName to it.content }
        val writes = base.keys.union(next.keys)
            .filter { base[it] != next[it] }
            .map { fileName -> FolderFileWrite(fileName, base[fileName], next[fileName]) }
        if (writes.isEmpty()) return true
        val committed = platformWriteFolderFiles(writes)
        if (committed) lastSyncedSources = sources
        return committed
    }

    /** Menu action: pick a local folder and start live two-way sync. */
    fun connectFolder() {
        if (!platformSupportsFolderSync) return
        prepareForProjectSwitch()
        hasAdoptedLiveFolder = false
        projectLandingError = null
        platformConnectFolderLive()
    }

    /** Re-grant access to a previously connected folder (must run inside the menu-click gesture). */
    fun reconnectFolder() {
        if (!platformSupportsFolderSync) return
        hasAdoptedLiveFolder = false
        platformReconnectSavedFolder()
    }

    /** Stop live sync and forget the folder; the current document stays in place (in-memory). */
    fun disconnectFolder() {
        platformDisconnectFolder()
        folderSync = FolderSyncPresence.Idle
        folderName = null
        folderExternalError = false
        folderConflictBackup = null
        hasAdoptedLiveFolder = false
        lastRecordedFolderId = null
        lastSyncedDocument = null
    }

    /** Pulls a fresh full snapshot from disk when live folder sync is connected. */
    fun refreshScreensFromDisk() {
        if (liveFolderConnected) platformRefreshFolder()
    }

    /** Restores the in-editor edit that an external change replaced (the conflict backup). */
    fun restoreFolderConflictBackup() {
        val backup = folderConflictBackup ?: return
        val docs = compileMissionDocuments(backup)
        if (docs.document != null) designState = createDesignEditorState(docs)
        lastSyncedSources = backup
        lastSyncedDocument = designState.document
        folderConflictBackup = null
        persistWorkspaceState()
    }

    fun dismissFolderConflictBackup() { folderConflictBackup = null }

    /**
     * Records the currently-watched folder in the recent-projects list (once per connection), so it
     * appears on the next startup landing. Runs on every `watching` tick but no-ops until the active
     * folder id changes. A probe-resumed folder without an id (legacy "root" pointer) is skipped —
     * it was already recorded when first connected.
     */
    private fun recordConnectedFolderAsRecent() {
        val repo = recentProjects ?: return
        val id = platformActiveFolderId() ?: return
        if (id == lastRecordedFolderId) return
        repo.upsert(
            RecentProject(
                id = id,
                displayName = folderName ?: id,
                kind = RecentProjectKind.LocalFolder,
                lastOpenedAtEpochMs = platformEpochMillis(),
                folderKey = id,
            ),
        )
        recentProjectItems = repo.list()
        lastRecordedFolderId = id
    }

    // --- Startup landing ("recent projects + Welcome") ----------------------------------------

    /** The recent projects shown on the startup landing, newest-first (folders only). */
    fun recentProjectsList(): List<RecentProject> = recentProjectItems

    fun showProjectLanding() {
        if (platformProjectLandingMode != ProjectLandingMode.Compose) return
        leaveProjectForLanding()
        recentProjectItems = recentProjects?.list() ?: emptyList()
        projectLandingError = null
        projectLandingVisible = true
    }

    /** Flushes the active project before the platform-specific project landing is shown. */
    fun leaveProjectForLanding() {
        prepareForProjectSwitch()
    }

    fun openRecentProject(id: String) {
        prepareForProjectSwitch()
        hasAdoptedLiveFolder = false
        projectLandingError = null
        platformConnectFolderById(id)
    }

    fun removeRecentProject(id: String) {
        recentProjects?.remove(id)
        platformForgetFolder(id)
        if (lastRecordedFolderId == id) lastRecordedFolderId = null
        recentProjectItems = recentProjects?.list() ?: emptyList()
    }

    /** Flushes a connected project before changing its folder, then tears down the watcher. */
    private fun prepareForProjectSwitch() {
        persistWorkspaceState()
        if (liveFolderConnected) {
            if (writeSourcesToFolder(designState.sources)) lastSyncedDocument = designState.document
        }
        if (folderSync != FolderSyncPresence.Idle) disconnectFolder()
    }

    /** True when a browser-only project exists; folder-bound safety copies stay hidden. */
    suspend fun hasRecoveryDraft(): Boolean {
        if (!usesEmbeddedDraftProjects) return false
        val stored = draft?.restore() ?: return false
        return stored.folderId == null
    }

    /** Stable deep-link id of the stored browser-only project, if it has one. */
    suspend fun storedBrowserProjectId(): String? =
        if (usesEmbeddedDraftProjects) draft?.restore()?.takeIf { it.folderId == null }?.projectId else null

    /**
     * Polls the landing screen's action queue and drives the editor from the user's explicit choice:
     * open Welcome, recover the browser draft, create a browser project, or forget a recent. Folder opens are
     * handled by the folder-sync layer directly (the screen reconnects inside the click gesture),
     * so they never surface here. Runs until cancelled; a no-op where the landing is unsupported.
     */
    suspend fun runLanding() {
        if (!platformSupportsLanding) return
        while (true) {
            platformLandingPendingActionJson()?.let { json ->
                runCatching { LandingActionJson.decodeFromString(LandingAction.serializer(), json) }
                    .getOrNull()?.let { applyLandingAction(it) }
            }
            delay(LandingPollMs)
        }
    }

    private suspend fun applyLandingAction(action: LandingAction) {
        when (action.type) {
            "openWelcome" -> openWelcomeProject()
            "createBrowserProject" -> createEmptyBrowserProject()
            "openRecovery" -> restoreRecoveryDraft()
            "clearRecovery" -> draft?.reset { }
            // The landing's own language switcher: also update the editor + persist the choice.
            "setLanguage" -> action.id?.let { selectLanguage(AppLanguage.fromCode(it)) }
            "remove" -> action.id?.let { id ->
                removeRecentProject(id)
            }
        }
    }

    /** Starts a blank project whose source set is persisted only in this browser. */
    private fun createEmptyBrowserProject() {
        if (folderSync != FolderSyncPresence.Idle) disconnectFolder()
        browserProjectId = null
        val projectId = ensureBrowserProjectId()
        isWelcomeProject = false
        persistenceEnabled = true
        projectCreationPromptVisible = false
        projectName = ""
        designState = createDesignEditorState(compileMissionDocuments(emptyList()))
        inMemoryBaselineSources = emptyList()
        platformSetActiveProjectId(projectId)
        activateProject(projectId)
        draft?.saveNow(emptyList(), displayProjectName, projectId = projectId)
    }

    /** Opens the browser-only project from the landing. */
    private suspend fun restoreRecoveryDraft() {
        val draft = draft ?: return
        // Never let the recovered draft flow onto a live folder's files: drop any folder connection
        // first (as openWelcomeProject does), so autosave routes the draft to the browser store.
        if (folderSync != FolderSyncPresence.Idle) disconnectFolder()
        draft.restore()?.takeIf { it.folderId == null }?.let { restored ->
            val projectId = restored.projectId ?: ensureBrowserProjectId()
            browserProjectId = projectId
            isWelcomeProject = false
            persistenceEnabled = true
            projectCreationPromptVisible = false
            projectName = restored.projectName
            designState = createDesignEditorState(compileMissionDocuments(restored.files))
            platformSetActiveProjectId(projectId)
            activateProject(projectId)
            if (restored.projectId == null) {
                draft.saveNow(restored.files, restored.projectName, projectId = projectId)
            }
        }
    }

    /**
     * Explicit Save: force-flush the current SLM sources to the draft now. This is the
     * opt-in that turns the in-memory Welcome project into a persistent one.
     */
    fun saveDraftNow() {
        if (!usesEmbeddedDraftProjects) return
        val draft = draft ?: return
        val projectId = ensureBrowserProjectId()
        isWelcomeProject = false
        persistenceEnabled = true
        platformSetActiveProjectId(projectId)
        activateProject(projectId)
        draft.saveNow(designState.sources, displayProjectName, projectId = projectId)
    }

    /** Desktop Save: flush to the connected folder or choose one for the current in-memory sources. */
    fun saveProjectNow() {
        if (liveFolderConnected) {
            if (writeSourcesToFolder(designState.sources)) lastSyncedDocument = designState.document
        } else {
            createFolderProject()
        }
    }

    /**
     * «Открыть → Welcome»: reseed the editor with the bundled in-memory Welcome project.
     * Non-destructive — autosave switches off but a saved draft stays untouched, so a
     * reload returns to the saved project; the Welcome copy persists only if the user
     * explicitly saves it.
     */
    fun openWelcomeProject() {
        prepareForProjectSwitch()
        isWelcomeProject = true
        persistenceEnabled = false
        projectCreationPromptVisible = false
        browserProjectId = null
        projectName = ""
        designState = createDesignEditorState(loadDesignDocument())
        inMemoryBaselineSources = designState.sources
        projectLandingVisible = false
        projectLandingError = null
        platformSetActiveProjectId(WelcomeProjectId)
        activateProject(WelcomeProjectId)
    }

    /**
     * Reset: discard the draft and reseed the editor from the bundled Welcome sources.
     * Destructive counterpart of [openWelcomeProject]; currently has no UI entry point.
     */
    fun resetToDefaults() {
        isWelcomeProject = true
        persistenceEnabled = false
        projectCreationPromptVisible = false
        val draft = draft
        if (draft == null) {
            projectName = ""
            designState = createDesignEditorState(loadDesignDocument())
            inMemoryBaselineSources = designState.sources
            browserProjectId = null
            platformSetActiveProjectId(WelcomeProjectId)
            activateProject(WelcomeProjectId)
            return
        }
        draft.reset {
            designState = createDesignEditorState(loadDesignDocument())
            inMemoryBaselineSources = designState.sources
            browserProjectId = null
            platformSetActiveProjectId(WelcomeProjectId)
            activateProject(WelcomeProjectId)
        }
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

    fun updateWorkspace(
        persist: Boolean = true,
        block: (EditorWorkspaceState) -> EditorWorkspaceState,
    ) {
        val next = block(workspace)
        if (next != workspace) {
            workspace = next
            if (persist) persistWorkspaceState()
        }
    }

    fun saveWorkspaceNow() = persistWorkspaceState()

    private fun activateProject(projectId: String) {
        if (projectId.isBlank()) return
        activeProjectId = projectId
        persistedWorkspaceState.lastProject
            ?.takeIf { it.projectId == projectId }
            ?.let { designState = designState.restoreProjectWorkspace(it) }
        workspacePersistenceReady = true
        persistWorkspaceState()
    }

    private fun persistWorkspaceState() {
        if (!workspacePersistenceReady) return
        val next = PersistedWorkspaceState(
            layout = workspace.persistedLayout(),
            lastProject = designState.persistedProjectWorkspace(activeProjectId),
        )
        if (next == persistedWorkspaceState) return
        workspaceStateRepository?.save(next)
        persistedWorkspaceState = next
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
fun MissionEditorApp(mcpServer: McpServerController = NoMcpServerController) {
    EditorTheme {
        val scope = rememberCoroutineScope()
        val state = remember {
            // Composition root: wire the persistence slice. The dispatcher is injected
            // here (the boundary), not taken from Dispatchers.* inside the repository.
            val keyValueStore = createKeyValueStore()
            val draftRepository = DefaultDraftRepository(keyValueStore, Dispatchers.Default)
            val folderPendingRepository = DefaultDraftRepository(
                keyValueStore,
                Dispatchers.Default,
                key = FolderPendingStorageKey,
            )
            MissionEditorStateHolder(
                loadDesignDocument = LoadDesignDocumentUseCase(DefaultDesignDocumentRepository()),
                draft = DraftController(
                    saveDraft = SaveDraftUseCase(draftRepository),
                    clearDraft = ClearDraftUseCase(draftRepository),
                    restoreDraftSources = RestoreDraftSourcesUseCase(draftRepository),
                    scope = scope,
                ),
                folderPending = DraftController(
                    saveDraft = SaveDraftUseCase(folderPendingRepository),
                    clearDraft = ClearDraftUseCase(folderPendingRepository),
                    restoreDraftSources = RestoreDraftSourcesUseCase(folderPendingRepository),
                    scope = scope,
                ),
                languagePreference = LanguagePreference(keyValueStore),
                recentProjects = DefaultRecentProjectsRepository(keyValueStore),
                workspaceStateRepository = WorkspaceStateRepository(keyValueStore),
                mcpServer = mcpServer,
            )
        }
        LaunchedEffect(state) { state.runPersistence() }
        LaunchedEffect(state) { state.runFolderSync() }
        LaunchedEffect(state) { state.runLanding() }
        // Show the startup landing (recent projects + Welcome) once, seeded with the localized
        // catalog copy and theme tokens so the DOM screen carries no strings/palette of its own.
        val landingColors = LocalEditorColors.current
        LaunchedEffect(state) {
            if (platformSupportsLanding) {
                platformInstallLanding(
                    buildLandingConfigJson(
                        colors = landingColors,
                        recents = state.recentProjectsList(),
                        supportsFolders = state.supportsFolderSync,
                        hasRecovery = state.hasRecoveryDraft(),
                        browserProjectId = state.storedBrowserProjectId(),
                        language = state.language,
                    ),
                )
            }
        }
        CompositionLocalProvider(LocalStrings provides appStringsFor(state.language)) {
            if (state.projectLandingVisible) ProjectLandingScreen(state) else MissionEditorScreen(state)
        }
    }
}

@Composable
private fun MissionEditorScreen(state: MissionEditorStateHolder) {
    val strings = LocalStrings.current
    val colors = LocalEditorColors.current
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.folderExternalError, state.folderExternalErrorNonce, strings.menu.folderExternalError) {
        if (state.folderExternalError) {
            snackbarHostState.showSnackbar(
                message = strings.menu.folderExternalError,
                withDismissAction = true,
                duration = SnackbarDuration.Indefinite,
            )
        } else {
            snackbarHostState.currentSnackbarData?.dismiss()
        }
    }

    // The editor fills the window edge-to-edge: no chrome frame, no shell margin, no
    // rounded shell corners — the working area gets the whole viewport.
    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Color.White)) {
        val isNarrow = maxWidth < 1100.dp
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(0.dp),
            color = Color.White,
            shadowElevation = 0.dp,
        ) {
            Column(Modifier.fillMaxSize()) {
                EditorBrowserSaveBanner(state)
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    when {
                        state.workspace.isMainOnly -> MainOnlyLayout(state)
                        isNarrow -> StackedLayout(state)
                        else -> WorkbenchLayout(state)
                    }
                }
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp),
        ) { data ->
            Snackbar(
                snackbarData = data,
                containerColor = colors.statusDanger,
                contentColor = Color.White,
                actionColor = Color.White,
                dismissActionContentColor = Color.White,
            )
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
            CollapsedRail(label = LocalStrings.current.labels.sourceRail, icon = EditorIcon.Source, onExpand = { state.updateWorkspace { it.copy(sourceCollapsed = false) } })
        } else {
            EditorSourcePane(state, Modifier.width(sourceWidthDp.dp).fillMaxHeight())
            VerticalSplitter(
                onDeltaDp = { d ->
                    state.updateWorkspace(persist = false) {
                        it.copy(sourceWidthDp = (it.sourceWidthDp + d).coerceIn(WorkspaceLimits.MinSourceDp, WorkspaceLimits.MaxSourceDp))
                    }
                },
                onDragFinished = state::saveWorkspaceNow,
                onReset = { state.updateWorkspace { it.copy(sourceWidthDp = WorkspaceLimits.DefaultSourceDp) } },
            )
        }

        // Center: canvas (flexes).
        EditorCanvasPane(state, Modifier.weight(1f).fillMaxHeight())

        // Right: Inspector (or collapsed rail).
        if (ws.inspectorCollapsed) {
            CollapsedRail(label = LocalStrings.current.labels.inspectorRail, icon = EditorIcon.Inspector, onExpand = { state.updateWorkspace { it.copy(inspectorCollapsed = false) } })
        } else {
            VerticalSplitter(
                onDeltaDp = { d ->
                    state.updateWorkspace(persist = false) {
                        // Splitter is left of the inspector, so dragging right shrinks it.
                        it.copy(inspectorWidthDp = (it.inspectorWidthDp - d).coerceIn(WorkspaceLimits.MinInspectorDp, WorkspaceLimits.MaxInspectorDp))
                    }
                },
                onDragFinished = state::saveWorkspaceNow,
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
                LocalStrings.current.labels.exitFocus,
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
private fun VerticalSplitter(
    onDeltaDp: (Float) -> Unit,
    onDragFinished: () -> Unit,
    onReset: () -> Unit,
) {
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
                    onDragEnd = {
                        active = false
                        onDragFinished()
                    },
                    onDragCancel = {
                        active = false
                        onDragFinished()
                    },
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

