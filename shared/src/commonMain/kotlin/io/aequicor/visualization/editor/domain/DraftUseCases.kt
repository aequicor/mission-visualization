package io.aequicor.visualization.editor.domain

/** Persists the current per-page SLM sources as the local draft. */
class SaveDraftUseCase(private val repository: DraftRepository) {
    suspend operator fun invoke(
        sources: List<MissionDocumentSource>,
        projectName: String = "",
        folderId: String? = null,
        projectId: String? = null,
    ) {
        repository.save(WorkspaceDraft(DraftSchemaVersion, sources, projectName, folderId, projectId))
    }
}

/** Discards any locally-persisted draft (used by the editor's Reset action). */
class ClearDraftUseCase(private val repository: DraftRepository) {
    suspend operator fun invoke() {
        repository.clear()
    }
}

/**
 * Resolves the draft the editor should boot with: the persisted SLM sources and
 * project metadata when a compatible draft exists, or null to fall back to
 * the bundled defaults (no draft stored or an incompatible
 * [WorkspaceDraft.schemaVersion]).
 */
class RestoreDraftSourcesUseCase(private val repository: DraftRepository) {
    suspend operator fun invoke(): WorkspaceDraft? {
        val draft = repository.load() ?: return null
        if (draft.schemaVersion != DraftSchemaVersion) return null
        return draft
    }
}
