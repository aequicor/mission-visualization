package io.aequicor.visualization.editor.domain

/**
 * Kind of entry shown on the startup landing. Projects live on disk as folders
 * ([LocalFolder]); the bundled [Welcome] tour is always present as a static default card
 * and is never persisted. The browser holds no projects of its own — only an optional
 * crash-recovery draft, which is surfaced separately from this list.
 */
enum class RecentProjectKind(val code: String) {
    Welcome("welcome"),
    LocalFolder("localFolder"),
    ;

    companion object {
        /** Resolves a persisted [code] back to a kind, defaulting to [LocalFolder]. */
        fun fromCode(code: String?): RecentProjectKind =
            entries.firstOrNull { it.code == code } ?: LocalFolder
    }
}

/**
 * A recently-opened project, listed on the startup landing, newest-first.
 *
 * @property id stable unique identifier — also the deep-link token (`…/#<id>`) and, for a
 *   [RecentProjectKind.LocalFolder], the IndexedDB key its directory handle is stored under.
 * @property displayName human-facing name (the folder name).
 * @property lastOpenedAtEpochMs recency key; the most recently opened entry sorts first.
 * @property folderKey IndexedDB handle key for a local folder (defaults to [id]); null for Welcome.
 */
data class RecentProject(
    val id: String,
    val displayName: String,
    val kind: RecentProjectKind,
    val lastOpenedAtEpochMs: Long,
    val folderKey: String? = null,
)

/** Fixed id of the always-present Welcome card / deep-link token (`…/#welcome`). */
const val WelcomeProjectId: String = "welcome"

/** Maximum number of recent projects retained; older entries fall off the list. */
const val RecentProjectsCap: Int = 8

/**
 * Persistence of the recent-projects list shown on the startup landing: newest-first,
 * capped at [RecentProjectsCap], upsert-by-[RecentProject.id]. Only real on-disk projects
 * ([RecentProjectKind.LocalFolder]) are stored — the static Welcome card is prepended by
 * the landing, never persisted. Implementation lives in the data layer over the platform
 * key/value store.
 */
interface RecentProjectsRepository {
    /** The stored projects, newest-first. */
    fun list(): List<RecentProject>

    /** Adds or refreshes [project] (moving it to the front); Welcome entries are ignored. */
    fun upsert(project: RecentProject)

    /** Forgets the project with [id], if present. */
    fun remove(id: String)
}
