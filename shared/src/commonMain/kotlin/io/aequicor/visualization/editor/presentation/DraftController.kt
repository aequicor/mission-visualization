package io.aequicor.visualization.editor.presentation

import io.aequicor.visualization.editor.domain.ClearDraftUseCase
import io.aequicor.visualization.editor.domain.MissionDocumentSource
import io.aequicor.visualization.editor.domain.RestoreDraftSourcesUseCase
import io.aequicor.visualization.editor.domain.SaveDraftUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Presentation-side coordinator for local draft persistence. Wraps the draft use cases
 * and owns the [scope] their fire-and-forget launches (explicit Save / Reset) run in;
 * the debounced autosave is driven by [MissionEditorStateHolder] via [save]. A null
 * controller (previews, tests) disables persistence entirely.
 */
class DraftController(
    private val saveDraft: SaveDraftUseCase,
    private val clearDraft: ClearDraftUseCase,
    private val restoreDraftSources: RestoreDraftSourcesUseCase,
    private val scope: CoroutineScope,
) {
    /** Draft sources to seed the editor with on boot, or null to keep the defaults. */
    suspend fun restore(): List<MissionDocumentSource>? = restoreDraftSources()

    /** Persists [sources] now, suspending until written (used by autosave). */
    suspend fun save(sources: List<MissionDocumentSource>) = saveDraft(sources)

    /** Fire-and-forget explicit save (the Save button). */
    fun saveNow(sources: List<MissionDocumentSource>) {
        scope.launch { saveDraft(sources) }
    }

    /** Clears the draft, then runs [onCleared] to reseed the editor (the Reset button). */
    fun reset(onCleared: () -> Unit) {
        scope.launch {
            clearDraft()
            onCleared()
        }
    }
}
