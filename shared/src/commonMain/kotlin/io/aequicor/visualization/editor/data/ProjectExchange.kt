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

private val ProjectExchangeJson = Json { encodeDefaults = true }

internal fun encodeProjectSourcesJson(projectName: String, sources: List<MissionDocumentSource>): String =
    ProjectExchangeJson.encodeToString(ProjectFilesEnvelopeDto(projectName, sources.map { ProjectFileDto(it.fileName, it.content) }))

