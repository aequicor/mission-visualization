package io.aequicor.visualization.editor.domain

/**
 * Draft-format schema version. Bump to invalidate incompatible persisted drafts (a
 * stored draft whose version differs is ignored on restore, so a shipped change to the
 * bundled SLM or draft shape can never restore an inconsistent document).
 */
const val DraftSchemaVersion: Int = 1

/**
 * A locally-persisted snapshot of the user's work: the per-page SLM [files] as last
 * edited. Only the SLM text is stored — it is the single source of truth and is
 * recompiled on restore. [schemaVersion] guards the draft format.
 */
data class WorkspaceDraft(
    val schemaVersion: Int,
    val files: List<MissionDocumentSource>,
    val projectName: String = "",
)

/**
 * Local persistence of the editing [WorkspaceDraft]; implementations live in the data
 * layer over a platform key/value store (browser localStorage, a file, NSUserDefaults).
 */
interface DraftRepository {
    /** The saved draft, or null when none is stored or it cannot be read. */
    suspend fun load(): WorkspaceDraft?

    /** Persists [draft], replacing any previously stored draft. */
    suspend fun save(draft: WorkspaceDraft)

    /** Discards any stored draft. */
    suspend fun clear()
}
