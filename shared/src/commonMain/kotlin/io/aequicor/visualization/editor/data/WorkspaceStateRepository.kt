package io.aequicor.visualization.editor.data

import io.aequicor.visualization.editor.presentation.AnnotationTool
import io.aequicor.visualization.editor.presentation.DeviceMode
import io.aequicor.visualization.editor.presentation.EditorMode
import io.aequicor.visualization.editor.presentation.EditorTool
import io.aequicor.visualization.editor.presentation.FocusMode
import io.aequicor.visualization.editor.presentation.InspectorTab
import io.aequicor.visualization.editor.presentation.PersistedProjectWorkspace
import io.aequicor.visualization.editor.presentation.PersistedWorkspaceLayout
import io.aequicor.visualization.editor.presentation.PersistedWorkspaceState
import io.aequicor.visualization.editor.presentation.SourceTab
import io.aequicor.visualization.editor.presentation.WorkspaceLimits
import kotlin.enums.EnumEntries
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal const val WorkspaceStateStorageKey: String = "mv.editor.workspace.v1"
internal const val WorkspaceStateSchemaVersion: Int = 1

private val WorkspaceStateJson: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

/** User-local persistence for editor layout and the last active project location. */
class WorkspaceStateRepository(
    private val store: KeyValueStore,
    private val key: String = WorkspaceStateStorageKey,
    private val json: Json = WorkspaceStateJson,
) {
    /** Missing, corrupt and incompatible payloads all resolve to fresh workspace defaults. */
    fun load(): PersistedWorkspaceState {
        val raw = store.getString(key) ?: return PersistedWorkspaceState()
        val dto = runCatching {
            json.decodeFromString(WorkspaceStateEnvelopeDto.serializer(), raw)
        }.getOrNull() ?: return PersistedWorkspaceState()
        if (dto.schemaVersion != WorkspaceStateSchemaVersion) return PersistedWorkspaceState()
        return dto.toDomain()
    }

    fun save(state: PersistedWorkspaceState) {
        val dto = state.toDto()
        store.putString(key, json.encodeToString(WorkspaceStateEnvelopeDto.serializer(), dto))
    }
}

@Serializable
private data class WorkspaceStateEnvelopeDto(
    val schemaVersion: Int = WorkspaceStateSchemaVersion,
    val layout: WorkspaceLayoutDto = WorkspaceLayoutDto(),
    val lastProject: ProjectWorkspaceDto? = null,
)

@Serializable
private data class WorkspaceLayoutDto(
    val sourceWidthDp: Float = WorkspaceLimits.DefaultSourceDp,
    val inspectorWidthDp: Float = WorkspaceLimits.DefaultInspectorDp,
    val sourceCollapsed: Boolean = false,
    val inspectorCollapsed: Boolean = false,
    val focusMode: String = FocusMode.Normal.name,
    val mode: String = EditorMode.Canvas.name,
    val tool: String = EditorTool.Select.name,
    val annotationTool: String = AnnotationTool.None.name,
    val lastShapeTool: String = EditorTool.Rectangle.name,
    val lastContainerTool: String = EditorTool.Frame.name,
    val deviceMode: String = DeviceMode.Pc.name,
    val sourceTab: String = SourceTab.Layers.name,
    val inspectorTab: String = InspectorTab.Design.name,
)

@Serializable
private data class ProjectWorkspaceDto(
    val projectId: String,
    val activeFileName: String? = null,
    val selectedPageId: String? = null,
    val selectedNodeId: String? = null,
)

private fun WorkspaceStateEnvelopeDto.toDomain(): PersistedWorkspaceState =
    PersistedWorkspaceState(
        layout = PersistedWorkspaceLayout(
            sourceWidthDp = layout.sourceWidthDp,
            inspectorWidthDp = layout.inspectorWidthDp,
            sourceCollapsed = layout.sourceCollapsed,
            inspectorCollapsed = layout.inspectorCollapsed,
            focusMode = FocusMode.entries.named(layout.focusMode, FocusMode.Normal),
            mode = EditorMode.entries.named(layout.mode, EditorMode.Canvas),
            tool = EditorTool.entries.named(layout.tool, EditorTool.Select),
            annotationTool = AnnotationTool.entries.named(layout.annotationTool, AnnotationTool.None),
            lastShapeTool = EditorTool.entries.named(layout.lastShapeTool, EditorTool.Rectangle),
            lastContainerTool = EditorTool.entries.named(layout.lastContainerTool, EditorTool.Frame),
            deviceMode = DeviceMode.entries.named(layout.deviceMode, DeviceMode.Pc),
            sourceTab = SourceTab.entries.named(layout.sourceTab, SourceTab.Layers),
            inspectorTab = InspectorTab.entries.named(layout.inspectorTab, InspectorTab.Design),
        ),
        lastProject = lastProject
            ?.takeIf { it.projectId.isNotBlank() }
            ?.let {
                PersistedProjectWorkspace(
                    projectId = it.projectId,
                    activeFileName = it.activeFileName?.takeIf(String::isNotBlank),
                    selectedPageId = it.selectedPageId?.takeIf(String::isNotBlank),
                    selectedNodeId = it.selectedNodeId?.takeIf(String::isNotBlank),
                )
            },
    )

private fun PersistedWorkspaceState.toDto(): WorkspaceStateEnvelopeDto =
    WorkspaceStateEnvelopeDto(
        layout = WorkspaceLayoutDto(
            sourceWidthDp = layout.sourceWidthDp,
            inspectorWidthDp = layout.inspectorWidthDp,
            sourceCollapsed = layout.sourceCollapsed,
            inspectorCollapsed = layout.inspectorCollapsed,
            focusMode = layout.focusMode.name,
            mode = layout.mode.name,
            tool = layout.tool.name,
            annotationTool = layout.annotationTool.name,
            lastShapeTool = layout.lastShapeTool.name,
            lastContainerTool = layout.lastContainerTool.name,
            deviceMode = layout.deviceMode.name,
            sourceTab = layout.sourceTab.name,
            inspectorTab = layout.inspectorTab.name,
        ),
        lastProject = lastProject?.let {
            ProjectWorkspaceDto(it.projectId, it.activeFileName, it.selectedPageId, it.selectedNodeId)
        },
    )

private fun <T : Enum<T>> EnumEntries<T>.named(name: String, fallback: T): T =
    firstOrNull { it.name == name } ?: fallback
