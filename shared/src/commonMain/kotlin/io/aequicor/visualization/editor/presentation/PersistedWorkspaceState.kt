package io.aequicor.visualization.editor.presentation

/**
 * Durable, user-local subset of [EditorWorkspaceState]. Transient gesture, viewport and inline
 * editing fields deliberately stay out of this model: reopening the editor restores the workbench,
 * not an interrupted pointer operation.
 */
data class PersistedWorkspaceLayout(
    val sourceWidthDp: Float = WorkspaceLimits.DefaultSourceDp,
    val inspectorWidthDp: Float = WorkspaceLimits.DefaultInspectorDp,
    val sourceCollapsed: Boolean = false,
    val inspectorCollapsed: Boolean = false,
    val focusMode: FocusMode = FocusMode.Normal,
    val mode: EditorMode = EditorMode.Canvas,
    val tool: EditorTool = EditorTool.Select,
    val annotationTool: AnnotationTool = AnnotationTool.None,
    val lastShapeTool: EditorTool = EditorTool.Rectangle,
    val lastContainerTool: EditorTool = EditorTool.Frame,
    val deviceMode: DeviceMode = DeviceMode.Pc,
    val sourceTab: SourceTab = SourceTab.Layers,
    val inspectorTab: InspectorTab = InspectorTab.Design,
)

/** Last document location within the project that owned the active editor session. */
data class PersistedProjectWorkspace(
    val projectId: String,
    val activeFileName: String? = null,
    val selectedPageId: String? = null,
    val selectedNodeId: String? = null,
)

/** Complete version-independent domain snapshot persisted by the data-layer repository. */
data class PersistedWorkspaceState(
    val layout: PersistedWorkspaceLayout = PersistedWorkspaceLayout(),
    val lastProject: PersistedProjectWorkspace? = null,
)

internal fun EditorWorkspaceState.persistedLayout(): PersistedWorkspaceLayout =
    PersistedWorkspaceLayout(
        sourceWidthDp = sourceWidthDp,
        inspectorWidthDp = inspectorWidthDp,
        sourceCollapsed = sourceCollapsed,
        inspectorCollapsed = inspectorCollapsed,
        focusMode = focusMode,
        mode = mode,
        tool = tool,
        annotationTool = annotationTool,
        lastShapeTool = lastShapeTool,
        lastContainerTool = lastContainerTool,
        deviceMode = deviceMode,
        sourceTab = sourceTab,
        inspectorTab = inspectorTab,
    )

/** Applies only durable preferences over fresh transient workspace defaults. */
internal fun PersistedWorkspaceLayout.restoreWorkspace(): EditorWorkspaceState =
    EditorWorkspaceState(
        sourceWidthDp = sourceWidthDp
            .takeIf(Float::isFinite)
            ?.coerceIn(WorkspaceLimits.MinSourceDp, WorkspaceLimits.MaxSourceDp)
            ?: WorkspaceLimits.DefaultSourceDp,
        inspectorWidthDp = inspectorWidthDp
            .takeIf(Float::isFinite)
            ?.coerceIn(WorkspaceLimits.MinInspectorDp, WorkspaceLimits.MaxInspectorDp)
            ?: WorkspaceLimits.DefaultInspectorDp,
        sourceCollapsed = sourceCollapsed,
        inspectorCollapsed = inspectorCollapsed,
        focusMode = focusMode,
        mode = mode,
        tool = tool,
        annotationTool = annotationTool,
        lastShapeTool = lastShapeTool.takeIf(EditorTool::isShapeTool) ?: EditorTool.Rectangle,
        lastContainerTool = lastContainerTool.takeIf(EditorTool::isContainerTool) ?: EditorTool.Frame,
        deviceMode = deviceMode,
        sourceTab = sourceTab,
        inspectorTab = inspectorTab,
    )

internal fun DesignEditorState.persistedProjectWorkspace(projectId: String): PersistedProjectWorkspace =
    PersistedProjectWorkspace(
        projectId = projectId,
        activeFileName = screenFileNamesByPageId()[selectedPageId],
        selectedPageId = selectedPageId.takeIf(String::isNotBlank),
        selectedNodeId = selectedNodeId.takeIf(String::isNotBlank),
    )

/**
 * Restores the last file/page and component against a freshly compiled project. File name is the
 * primary key because authored screen ids may change. Stale ids fall back to the first valid page
 * and node instead of leaving the editor with an invalid selection.
 */
internal fun DesignEditorState.restoreProjectWorkspace(saved: PersistedProjectWorkspace): DesignEditorState {
    val document = document ?: return this
    val pageByFileName = saved.activeFileName?.let { fileName ->
        val pageId = screenFileNamesByPageId().entries.firstOrNull { it.value == fileName }?.key
        pageId?.let(document::pageById)
    }
    val page = pageByFileName
        ?: saved.selectedPageId?.let(document::pageById)
        ?: document.pageById(selectedPageId)
        ?: document.pages.firstOrNull()
        ?: return copy(selectedPageId = "", selectedNodeId = "", selectedNodeIds = emptySet())
    val savedNodeId = saved.selectedNodeId.orEmpty()
    val nodeId = savedNodeId
        .takeIf { it.isNotBlank() && document.pageOfNode(it)?.id == page.id }
        ?: page.children.firstOrNull()?.id.orEmpty()
    return copy(
        selectedPageId = page.id,
        selectedNodeId = nodeId,
        selectedNodeIds = if (nodeId.isBlank()) emptySet() else setOf(nodeId),
        editingTextNodeId = "",
    )
}
