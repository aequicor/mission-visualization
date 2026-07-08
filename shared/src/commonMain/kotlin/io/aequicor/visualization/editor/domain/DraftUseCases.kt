package io.aequicor.visualization.editor.domain

/** Persists the current per-page SLM sources as the local draft. */
class SaveDraftUseCase(private val repository: DraftRepository) {
    suspend operator fun invoke(sources: List<MissionDocumentSource>) {
        repository.save(WorkspaceDraft(DraftSchemaVersion, sources))
    }
}

/** Discards any locally-persisted draft (used by the editor's Reset action). */
class ClearDraftUseCase(private val repository: DraftRepository) {
    suspend operator fun invoke() {
        repository.clear()
    }
}

/**
 * Resolves the SLM sources the editor should boot with: the persisted draft's sources
 * when a compatible, non-empty draft exists, or null to fall back to the bundled
 * defaults (no draft stored, empty, or an incompatible [WorkspaceDraft.schemaVersion]).
 */
class RestoreDraftSourcesUseCase(private val repository: DraftRepository) {
    suspend operator fun invoke(): List<MissionDocumentSource>? {
        val draft = repository.load() ?: return null
        if (draft.schemaVersion != DraftSchemaVersion) return null
        return draft.files.ifEmpty { null }
    }
}
