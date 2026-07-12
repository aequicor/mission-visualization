package io.aequicor.visualization.editor.data

import io.aequicor.visualization.editor.domain.MissionDocumentSource
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
internal data class ProjectFilesEnvelopeDto(
    val projectName: String = "",
    val files: List<ProjectFileDto>,
)

@Serializable
internal data class ProjectFileDto(
    val fileName: String,
    val content: String,
)

private val ProjectExchangeJson = Json { encodeDefaults = true; ignoreUnknownKeys = true }

internal fun encodeProjectSourcesJson(projectName: String, sources: List<MissionDocumentSource>): String =
    ProjectExchangeJson.encodeToString(ProjectFilesEnvelopeDto(projectName, sources.map { ProjectFileDto(it.fileName, it.content) }))

/** Decoded live-folder snapshot: the folder name plus the per-file SLM sources it currently holds. */
internal data class ProjectSnapshot(
    val projectName: String,
    val sources: List<MissionDocumentSource>,
)

/**
 * Parses a `ProjectFilesEnvelope` JSON (as produced by `window.__mvFolderSync`) back into a
 * [ProjectSnapshot]. Returns null on malformed input so a torn read never throws into the
 * watcher loop.
 */
internal fun decodeProjectSnapshot(json: String): ProjectSnapshot? = runCatching {
    val dto = ProjectExchangeJson.decodeFromString(ProjectFilesEnvelopeDto.serializer(), json)
    ProjectSnapshot(dto.projectName, dto.files.map { MissionDocumentSource(it.fileName, it.content) })
}.getOrNull()

