package io.aequicor.visualization.editor.data

import io.aequicor.visualization.editor.domain.RecentProject
import io.aequicor.visualization.editor.domain.RecentProjectKind
import io.aequicor.visualization.editor.domain.RecentProjectsCap
import io.aequicor.visualization.editor.domain.RecentProjectsRepository
import kotlinx.serialization.json.Json

/** Storage key for the recent-projects list; shared with the landing JS layer. */
internal const val RecentProjectsStorageKey: String = "mv.recent.projects.v1"

private val RecentProjectsJson: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

/**
 * Recent-projects persistence over a [KeyValueStore]: one JSON [RecentProjectsEnvelopeDto]
 * under [RecentProjectsStorageKey]. Newest-first, capped at [RecentProjectsCap], upsert by id.
 * A corrupt payload or mismatched schema reads back as an empty list rather than throwing.
 *
 * Only on-disk [RecentProjectKind.LocalFolder] projects are stored — the static Welcome card
 * is prepended by the landing and never persisted. On the web the landing's JS layer reads
 * and (on "remove") rewrites the same key; the schema is kept in lockstep in [RecentProjectsDto].
 */
class DefaultRecentProjectsRepository(
    private val store: KeyValueStore,
    private val key: String = RecentProjectsStorageKey,
    private val json: Json = RecentProjectsJson,
) : RecentProjectsRepository {

    override fun list(): List<RecentProject> = read()

    override fun upsert(project: RecentProject) {
        if (project.kind == RecentProjectKind.Welcome) return
        val next = (listOf(project) + read().filter { it.id != project.id })
            .sortedByDescending { it.lastOpenedAtEpochMs }
            .take(RecentProjectsCap)
        write(next)
    }

    override fun remove(id: String) {
        val current = read()
        val next = current.filter { it.id != id }
        if (next.size != current.size) write(next)
    }

    /** Reads the stored list newest-first, or an empty list on absence / corruption / bad schema. */
    private fun read(): List<RecentProject> {
        val raw = store.getString(key) ?: return emptyList()
        val envelope = runCatching {
            json.decodeFromString(RecentProjectsEnvelopeDto.serializer(), raw)
        }.getOrNull() ?: return emptyList()
        if (envelope.schemaVersion != RecentProjectsSchemaVersion) return emptyList()
        return envelope.projects.map { it.toDomain() }.sortedByDescending { it.lastOpenedAtEpochMs }
    }

    private fun write(projects: List<RecentProject>) {
        val envelope = RecentProjectsEnvelopeDto(RecentProjectsSchemaVersion, projects.map { it.toDto() })
        store.putString(key, json.encodeToString(RecentProjectsEnvelopeDto.serializer(), envelope))
    }
}
