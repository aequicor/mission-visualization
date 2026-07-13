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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
