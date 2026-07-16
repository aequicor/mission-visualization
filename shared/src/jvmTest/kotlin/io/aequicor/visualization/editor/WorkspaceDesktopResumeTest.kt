package io.aequicor.visualization.editor

import io.aequicor.visualization.MissionEditorStateHolder
import io.aequicor.visualization.editor.data.DefaultDesignDocumentRepository
import io.aequicor.visualization.editor.data.KeyValueStore
import io.aequicor.visualization.editor.data.WorkspaceStateRepository
import io.aequicor.visualization.editor.domain.LoadDesignDocumentUseCase
import io.aequicor.visualization.editor.platform.platformResetFolderSyncForTest
import io.aequicor.visualization.editor.presentation.PersistedProjectWorkspace
import io.aequicor.visualization.editor.presentation.PersistedWorkspaceState
import io.aequicor.visualization.editor.presentation.createDesignEditorState
import io.aequicor.visualization.editor.presentation.screenFileNamesByPageId
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private class DesktopWorkspaceMemoryStore : KeyValueStore {
    private val values = mutableMapOf<String, String>()

    override fun getString(key: String): String? = values[key]

    override fun putString(key: String, value: String) {
        values[key] = value
    }

    override fun remove(key: String) {
        values.remove(key)
    }
}

class WorkspaceDesktopResumeTest {
    @Test
    fun desktopAutomaticallyReopensLastFolderAtSavedFileAndComponent() = runBlocking {
        platformResetFolderSyncForTest()
        val documents = missionDemoDocuments()
        val root = createTempDirectory("desktop-workspace-resume")
        documents.sources.forEach { source ->
            val target = root.resolve(source.fileName)
            target.parent?.createDirectories()
            target.writeText(source.content)
        }
        val initial = createDesignEditorState(documents)
        val targetPage = initial.document!!.pages.last()
        val targetNode = targetPage.children.last()
        val targetFile = initial.screenFileNamesByPageId().getValue(targetPage.id)
        val repository = WorkspaceStateRepository(DesktopWorkspaceMemoryStore())
        repository.save(
            PersistedWorkspaceState(
                lastProject = PersistedProjectWorkspace(
                    projectId = root.toString(),
                    activeFileName = targetFile,
                    selectedPageId = targetPage.id,
                    selectedNodeId = targetNode.id,
                ),
            ),
        )
        val state = MissionEditorStateHolder(
            loadDesignDocument = LoadDesignDocumentUseCase(DefaultDesignDocumentRepository()),
            workspaceStateRepository = repository,
        )
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope.launch { state.runFolderSync() }

        try {
            waitUntil { !state.projectLandingVisible && state.designState.selectedNodeId == targetNode.id }

            assertFalse(state.projectLandingVisible)
            assertEquals(targetPage.id, state.designState.selectedPageId)
            assertEquals(targetNode.id, state.designState.selectedNodeId)
            assertEquals(targetFile, state.designState.screenFileNamesByPageId()[state.designState.selectedPageId])
        } finally {
            scope.cancel()
            platformResetFolderSyncForTest()
        }
    }

    private suspend fun waitUntil(timeoutMillis: Long = 5_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (!condition()) {
            if (System.currentTimeMillis() >= deadline) error("Timed out waiting for workspace resume")
            delay(25)
        }
    }
}
