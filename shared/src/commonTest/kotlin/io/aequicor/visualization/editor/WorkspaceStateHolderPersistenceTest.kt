package io.aequicor.visualization.editor

import io.aequicor.visualization.MissionEditorStateHolder
import io.aequicor.visualization.editor.data.DefaultDesignDocumentRepository
import io.aequicor.visualization.editor.data.KeyValueStore
import io.aequicor.visualization.editor.data.WorkspaceStateRepository
import io.aequicor.visualization.editor.domain.LoadDesignDocumentUseCase
import io.aequicor.visualization.editor.presentation.DesignEditorIntent
import io.aequicor.visualization.editor.presentation.DeviceMode
import io.aequicor.visualization.editor.presentation.EditorMode
import io.aequicor.visualization.editor.presentation.EditorTool
import io.aequicor.visualization.editor.presentation.SourceTab
import io.aequicor.visualization.editor.presentation.screenFileNamesByPageId
import kotlin.test.Test
import kotlin.test.assertEquals

private class HolderWorkspaceMemoryStore : KeyValueStore {
    private val values = mutableMapOf<String, String>()

    override fun getString(key: String): String? = values[key]

    override fun putString(key: String, value: String) {
        values[key] = value
    }

    override fun remove(key: String) {
        values.remove(key)
    }
}

class WorkspaceStateHolderPersistenceTest {
    @Test
    fun freshHolderRestoresWorkbenchFileAndComponent() {
        val store = HolderWorkspaceMemoryStore()
        val repository = WorkspaceStateRepository(store)
        val first = stateHolder(repository)
        first.openWelcomeProject()
        val targetPage = first.designState.document!!.pages.last()
        val targetNode = targetPage.children.last()
        val targetFile = first.designState.screenFileNamesByPageId().getValue(targetPage.id)

        first.dispatch(DesignEditorIntent.SelectPage(targetPage.id))
        first.dispatch(DesignEditorIntent.SelectNode(targetNode.id))
        first.updateWorkspace {
            it.copy(
                sourceWidthDp = 520f,
                inspectorWidthDp = 300f,
                mode = EditorMode.Scene,
                deviceMode = DeviceMode.Tab,
                sourceTab = SourceTab.Markdown,
                tool = EditorTool.Pen,
            )
        }

        val restored = stateHolder(repository)

        assertEquals(520f, restored.workspace.sourceWidthDp)
        assertEquals(300f, restored.workspace.inspectorWidthDp)
        assertEquals(EditorMode.Scene, restored.workspace.mode)
        assertEquals(DeviceMode.Tab, restored.workspace.deviceMode)
        assertEquals(SourceTab.Markdown, restored.workspace.sourceTab)
        assertEquals(EditorTool.Pen, restored.workspace.tool)
        assertEquals(targetPage.id, restored.designState.selectedPageId)
        assertEquals(targetNode.id, restored.designState.selectedNodeId)
        assertEquals(targetFile, restored.designState.screenFileNamesByPageId()[restored.designState.selectedPageId])
    }

    private fun stateHolder(repository: WorkspaceStateRepository) = MissionEditorStateHolder(
        loadDesignDocument = LoadDesignDocumentUseCase(DefaultDesignDocumentRepository()),
        workspaceStateRepository = repository,
    )
}
