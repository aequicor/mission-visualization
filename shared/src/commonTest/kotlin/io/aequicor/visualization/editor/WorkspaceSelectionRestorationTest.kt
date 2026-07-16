package io.aequicor.visualization.editor

import io.aequicor.visualization.editor.presentation.PersistedProjectWorkspace
import io.aequicor.visualization.editor.presentation.createDesignEditorState
import io.aequicor.visualization.editor.presentation.restoreProjectWorkspace
import io.aequicor.visualization.editor.presentation.screenFileNamesByPageId
import kotlin.test.Test
import kotlin.test.assertEquals

class WorkspaceSelectionRestorationTest {
    @Test
    fun fileNameRestoresPageAndItsLastComponent() {
        val initial = createDesignEditorState(missionDemoDocuments())
        val targetPage = initial.document!!.pages.last()
        val targetNode = targetPage.children.last()
        val fileName = initial.screenFileNamesByPageId().getValue(targetPage.id)

        val restored = initial.restoreProjectWorkspace(
            PersistedProjectWorkspace(
                projectId = "mission-demo",
                activeFileName = fileName,
                selectedPageId = "stale-page-id",
                selectedNodeId = targetNode.id,
            ),
        )

        assertEquals(targetPage.id, restored.selectedPageId)
        assertEquals(targetNode.id, restored.selectedNodeId)
        assertEquals(setOf(targetNode.id), restored.selectedNodeIds)
    }

    @Test
    fun staleComponentFallsBackToFirstNodeOfRestoredFile() {
        val initial = createDesignEditorState(missionDemoDocuments())
        val targetPage = initial.document!!.pages.last()
        val fileName = initial.screenFileNamesByPageId().getValue(targetPage.id)

        val restored = initial.restoreProjectWorkspace(
            PersistedProjectWorkspace(
                projectId = "mission-demo",
                activeFileName = fileName,
                selectedNodeId = "deleted-component",
            ),
        )

        assertEquals(targetPage.id, restored.selectedPageId)
        assertEquals(targetPage.children.first().id, restored.selectedNodeId)
    }
}
