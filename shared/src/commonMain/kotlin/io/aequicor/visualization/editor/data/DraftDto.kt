package io.aequicor.visualization.editor.data

import io.aequicor.visualization.editor.domain.MissionDocumentSource
import io.aequicor.visualization.editor.domain.WorkspaceDraft
import kotlinx.serialization.Serializable

/** Serialization form of [WorkspaceDraft]; kept separate from the domain model. */
@Serializable
internal data class DraftEnvelopeDto(
    val schemaVersion: Int,
    val files: List<DraftFileDto>,
)

@Serializable
internal data class DraftFileDto(
    val fileName: String,
    val content: String,
)

internal fun WorkspaceDraft.toDto(): DraftEnvelopeDto =
    DraftEnvelopeDto(schemaVersion, files.map { DraftFileDto(it.fileName, it.content) })

internal fun DraftEnvelopeDto.toDomain(): WorkspaceDraft =
    WorkspaceDraft(schemaVersion, files.map { MissionDocumentSource(it.fileName, it.content) })
