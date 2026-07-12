package io.aequicor.visualization.editor.data

import io.aequicor.visualization.editor.domain.RecentProject
import io.aequicor.visualization.editor.domain.RecentProjectKind
import kotlinx.serialization.Serializable

/** Draft-format version for the recent-projects list; a differing version restores as empty. */
internal const val RecentProjectsSchemaVersion: Int = 1

/**
 * Serialization form of the recent-projects list. This exact JSON shape is also read (and
 * mutated on "remove") by the startup landing's JS layer (`window.__mvLanding`) under the
 * same [RecentProjectsStorageKey], so the two must agree field-for-field.
 */
@Serializable
internal data class RecentProjectsEnvelopeDto(
    val schemaVersion: Int = RecentProjectsSchemaVersion,
    val projects: List<RecentProjectDto> = emptyList(),
)

@Serializable
internal data class RecentProjectDto(
    val id: String,
    val displayName: String = "",
    val kind: String = RecentProjectKind.LocalFolder.code,
    val lastOpenedAtEpochMs: Long = 0,
    val folderKey: String? = null,
)

internal fun RecentProject.toDto(): RecentProjectDto =
    RecentProjectDto(id, displayName, kind.code, lastOpenedAtEpochMs, folderKey)

internal fun RecentProjectDto.toDomain(): RecentProject =
    RecentProject(id, displayName, RecentProjectKind.fromCode(kind), lastOpenedAtEpochMs, folderKey)
