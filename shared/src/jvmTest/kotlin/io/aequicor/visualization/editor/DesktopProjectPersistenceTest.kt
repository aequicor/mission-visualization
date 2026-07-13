package io.aequicor.visualization.editor

import io.aequicor.visualization.MissionEditorStateHolder
import io.aequicor.visualization.editor.data.DefaultDesignDocumentRepository
import io.aequicor.visualization.editor.domain.ClearDraftUseCase
import io.aequicor.visualization.editor.domain.DraftRepository
import io.aequicor.visualization.editor.domain.LoadDesignDocumentUseCase
import io.aequicor.visualization.editor.domain.RestoreDraftSourcesUseCase
import io.aequicor.visualization.editor.domain.SaveDraftUseCase
import io.aequicor.visualization.editor.domain.WorkspaceDraft
import io.aequicor.visualization.editor.presentation.DraftController
import io.aequicor.visualization.editor.presentation.DesignEditorIntent
import io.aequicor.visualization.editor.platform.platformConnectFolderForTest
import io.aequicor.visualization.editor.platform.platformResetFolderSyncForTest
import io.aequicor.visualization.engine.ir.model.literalOrNull
import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopProjectPersistenceTest {
    @Test
    fun desktopNeverLoadsOrWritesEmbeddedProjectDrafts() = runBlocking {
        val repository = RecordingDraftRepository()
        val controller = DraftController(
            saveDraft = SaveDraftUseCase(repository),
            clearDraft = ClearDraftUseCase(repository),
            restoreDraftSources = RestoreDraftSourcesUseCase(repository),
            scope = CoroutineScope(Dispatchers.Unconfined),
        )
        val state = MissionEditorStateHolder(
            loadDesignDocument = LoadDesignDocumentUseCase(DefaultDesignDocumentRepository()),
            draft = controller,
        )

        assertTrue(state.projectLandingVisible)
        assertFalse(state.usesEmbeddedDraftProjects)
        assertFalse(state.hasRecoveryDraft())
        state.saveDraftNow()
        state.createBrowserProject()

        assertEquals(0, repository.loadCount)
        assertEquals(0, repository.saveCount)
    }

    @Test
    fun committedVisualEditWritesLayoutImmediatelyAndReopensWithoutSidecar() = runBlocking {
        val root = createTempDirectory("desktop-project-roundtrip")
        missionDemoDocuments().sources.forEach { source ->
            val target = root.resolve(source.fileName)
            target.parent?.createDirectories()
            target.writeText(source.content)
        }
        platformConnectFolderForTest(root)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val state = MissionEditorStateHolder(
            loadDesignDocument = LoadDesignDocumentUseCase(DefaultDesignDocumentRepository()),
        )
        scope.launch { state.runFolderSync() }
        scope.launch { state.runPersistence() }

        try {
            waitUntil { !state.projectLandingVisible && state.designState.document?.nodeById("frame_overview") != null }
            state.dispatch(DesignEditorIntent.UpdateOpacity("win_bg", 0.73))
            assertEquals(0.73, state.designState.document?.nodeById("win_bg")?.opacity?.literalOrNull())

            val overviewPath = root.resolve("mission-overview.layout.md")
            val authoritative = state.designState.sources.single { it.fileName == "mission-overview.layout.md" }.content
            assertEquals(authoritative, Files.readString(overviewPath), "dispatch commits SLM before returning")

            val beforeDrag = Files.readString(overviewPath)
            state.dispatch(DesignEditorIntent.BeginInteraction)
            state.dispatch(DesignEditorIntent.UpdatePosition("win_bg", x = 210.0, y = 220.0))
            state.dispatch(DesignEditorIntent.UpdatePosition("win_bg", x = 230.0, y = 240.0))
            assertEquals(beforeDrag, Files.readString(overviewPath), "pointer previews never write the folder")
            state.dispatch(DesignEditorIntent.EndInteraction)
            val afterDrag = state.designState.sources.single { it.fileName == "mission-overview.layout.md" }.content
            assertEquals(afterDrag, Files.readString(overviewPath), "gesture commits once at EndInteraction")

            waitUntil { Files.isRegularFile(root.resolve(".mission-visualization/editor-state.json")) }
            state.showProjectLanding()
            Files.deleteIfExists(root.resolve(".mission-visualization/editor-state.json"))
            state.openRecentProject(root.toString())
            waitUntil { !state.projectLandingVisible }

            assertEquals(0.73, state.designState.document?.nodeById("win_bg")?.opacity?.literalOrNull())
        } finally {
            scope.cancel()
            platformResetFolderSyncForTest()
        }
    }

    @Test
    fun invalidExternalSourceKeepsLastGoodCanvasAndSurfacesError() = runBlocking {
        val root = createTempDirectory("desktop-external-error")
        missionDemoDocuments().sources.forEach { source ->
            val target = root.resolve(source.fileName)
            target.parent?.createDirectories()
            target.writeText(source.content)
        }
        platformConnectFolderForTest(root)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val state = MissionEditorStateHolder(
            loadDesignDocument = LoadDesignDocumentUseCase(DefaultDesignDocumentRepository()),
        )
        scope.launch { state.runFolderSync() }

        try {
            waitUntil { !state.projectLandingVisible && state.designState.document?.nodeById("win_bg") != null }
            val lastGood = state.designState.document
            root.resolve("mission-overview.layout.md").writeText("---\nscreen: [\n---\n# broken\n")

            waitUntil { state.folderExternalError }
            assertEquals(lastGood, state.designState.document)
        } finally {
            scope.cancel()
            platformResetFolderSyncForTest()
        }
    }

    private suspend fun waitUntil(timeoutMillis: Long = 5_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (!condition()) {
            if (System.currentTimeMillis() >= deadline) error("Timed out waiting for desktop project state")
            delay(25)
        }
    }
}

private class RecordingDraftRepository : DraftRepository {
    var loadCount = 0
    var saveCount = 0

    override suspend fun load(): WorkspaceDraft? {
        loadCount += 1
        return null
    }

    override suspend fun save(draft: WorkspaceDraft) {
        saveCount += 1
    }

    override suspend fun clear() = Unit
}
