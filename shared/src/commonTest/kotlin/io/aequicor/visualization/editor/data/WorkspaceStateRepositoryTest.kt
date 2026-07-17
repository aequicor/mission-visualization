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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private class WorkspaceMemoryStore : KeyValueStore {
    val values = mutableMapOf<String, String>()

    override fun getString(key: String): String? = values[key]

    override fun putString(key: String, value: String) {
        values[key] = value
    }

    override fun remove(key: String) {
        values.remove(key)
    }
}

class WorkspaceStateRepositoryTest {
    @Test
    fun emptyAndCorruptStorageReturnDefaults() {
        val store = WorkspaceMemoryStore()
        val repository = WorkspaceStateRepository(store)
        assertEquals(PersistedWorkspaceState(), repository.load())

        store.putString(WorkspaceStateStorageKey, "{broken")
        assertEquals(PersistedWorkspaceState(), repository.load())

        store.putString(WorkspaceStateStorageKey, """{"schemaVersion":99}""")
        assertEquals(PersistedWorkspaceState(), repository.load())
    }

    @Test
    fun roundTripsLayoutModesToolAndLastProjectLocation() {
        val store = WorkspaceMemoryStore()
        val repository = WorkspaceStateRepository(store)
        val expected = PersistedWorkspaceState(
            layout = PersistedWorkspaceLayout(
                sourceWidthDp = 512f,
                inspectorWidthDp = 318f,
                sourceCollapsed = true,
                inspectorCollapsed = true,
                focusMode = FocusMode.MainOnly,
                mode = EditorMode.Scene,
                tool = EditorTool.Pen,
                annotationTool = AnnotationTool.Issue,
                lastShapeTool = EditorTool.Star,
                lastContainerTool = EditorTool.AutoLayoutGrid,
                deviceMode = DeviceMode.Mob,
                sourceTab = SourceTab.Markdown,
                inspectorTab = InspectorTab.Prototype,
            ),
            lastProject = PersistedProjectWorkspace(
                projectId = "project-42",
                activeFileName = "dashboard.layout.md",
                selectedPageId = "dashboard",
                selectedNodeId = "revenue-card",
            ),
        )

        repository.save(expected)

        assertEquals(expected, repository.load())
    }

    @Test
    fun unknownEnumsAndBlankProjectFieldsAreSafelyNormalized() {
        val store = WorkspaceMemoryStore()
        store.putString(
            WorkspaceStateStorageKey,
            """
            {
              "schemaVersion": 1,
              "layout": {
                "focusMode": "future-focus",
                "mode": "future-mode",
                "tool": "future-tool",
                "annotationTool": "future-annotation",
                "lastShapeTool": "future-shape",
                "lastContainerTool": "future-container",
                "deviceMode": "future-device",
                "sourceTab": "future-source",
                "inspectorTab": "future-inspector"
              },
              "lastProject": { "projectId": "", "activeFileName": "" }
            }
            """.trimIndent(),
        )

        val restored = WorkspaceStateRepository(store).load()

        assertEquals(PersistedWorkspaceLayout(), restored.layout)
        assertNull(restored.lastProject)
    }
}
