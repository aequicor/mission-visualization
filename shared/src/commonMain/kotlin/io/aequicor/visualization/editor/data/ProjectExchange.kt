package io.aequicor.visualization.editor.data

import io.aequicor.visualization.editor.domain.MissionDocumentSource
import io.aequicor.visualization.engine.ir.model.DesignDocument
import io.aequicor.visualization.engine.ir.serialization.documentOrNull
import io.aequicor.visualization.engine.ir.serialization.parseDesignDocument
import io.aequicor.visualization.engine.ir.serialization.writeDesignDocument
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
internal data class ProjectFilesEnvelopeDto(
    val projectName: String = "",
    val files: List<ProjectFileDto>,
    val editorStateJson: String? = null,
)

@Serializable
internal data class ProjectFileDto(
    val fileName: String,
    val content: String,
)

private val ProjectExchangeJson = Json { encodeDefaults = true; ignoreUnknownKeys = true }

internal const val EditorStateRelativePath: String = ".mission-visualization/editor-state.json"

internal fun encodeProjectSourcesJson(
    projectName: String,
    sources: List<MissionDocumentSource>,
    editorStateJson: String? = null,
): String = ProjectExchangeJson.encodeToString(
    ProjectFilesEnvelopeDto(
        projectName = projectName,
        files = sources.map { ProjectFileDto(it.fileName, it.content) },
        editorStateJson = editorStateJson,
    ),
)

@Serializable
private data class EditorStateEnvelopeDto(
    val schemaVersion: Int = 1,
    val sourceFingerprint: String,
    val documentJson: String,
)

/** Full-fidelity desktop fallback for editor changes not yet expressible as SLM patches. */
internal fun encodeEditorStateSnapshot(
    sources: List<MissionDocumentSource>,
    document: DesignDocument,
): String = ProjectExchangeJson.encodeToString(
    EditorStateEnvelopeDto(
        sourceFingerprint = sourceFingerprint(sources),
        documentJson = writeDesignDocument(document).toString(),
    ),
)

/** Returns the saved document only while it still describes the exact on-disk SLM source set. */
internal fun decodeEditorStateSnapshot(
    json: String?,
    sources: List<MissionDocumentSource>,
): DesignDocument? {
    if (json.isNullOrBlank()) return null
    return runCatching {
        val envelope = ProjectExchangeJson.decodeFromString(EditorStateEnvelopeDto.serializer(), json)
        if (envelope.schemaVersion != 1 || envelope.sourceFingerprint != sourceFingerprint(sources)) return null
        parseDesignDocument(envelope.documentJson).documentOrNull()
    }.getOrNull()
}

/** Stable cross-platform FNV-1a fingerprint over sorted names and UTF-16 source content. */
private fun sourceFingerprint(sources: List<MissionDocumentSource>): String {
    var hash = 14695981039346656037uL
    fun mix(value: String) {
        value.forEach { char ->
            hash = (hash xor char.code.toULong()) * 1099511628211uL
        }
        hash = (hash xor 0xFFuL) * 1099511628211uL
    }
    sources.sortedBy { it.fileName }.forEach { source ->
        mix(source.fileName)
        mix(source.content)
    }
    return hash.toString(16)
}

/** Decoded live-folder snapshot: the folder name plus the per-file SLM sources it currently holds. */
internal data class ProjectSnapshot(
    val projectName: String,
    val sources: List<MissionDocumentSource>,
    val editorStateJson: String? = null,
)

/**
 * Parses a `ProjectFilesEnvelope` JSON (as produced by `window.__mvFolderSync`) back into a
 * [ProjectSnapshot]. Returns null on malformed input so a torn read never throws into the
 * watcher loop.
 */
internal fun decodeProjectSnapshot(json: String): ProjectSnapshot? = runCatching {
    val dto = ProjectExchangeJson.decodeFromString(ProjectFilesEnvelopeDto.serializer(), json)
    ProjectSnapshot(
        projectName = dto.projectName,
        sources = dto.files.map { MissionDocumentSource(it.fileName, it.content) },
        editorStateJson = dto.editorStateJson,
    )
}.getOrNull()

