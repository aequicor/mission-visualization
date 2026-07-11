package io.aequicor.visualization.agent

import io.aequicor.visualization.editor.data.DefaultDesignDocumentRepository
import io.aequicor.visualization.editor.domain.MissionDocumentSource
import java.io.File

/**
 * A design project as the automation surface sees it: a directory of `*.layout.md`
 * screen sources (the CNL authoring format). The AI agent edits those files with its
 * own tooling; this class only translates directory ↔ [MissionDocumentSource] lists
 * for the pure compile pipeline.
 */
object AgentProject {

    const val LAYOUT_EXTENSION: String = ".layout.md"

    /** Loads every `*.layout.md` in [directory] (sorted by file name for determinism). */
    fun load(directory: File): List<MissionDocumentSource> {
        require(directory.isDirectory) { "Not a directory: ${directory.absolutePath}" }
        val files = directory.listFiles { file -> file.isFile && file.name.endsWith(LAYOUT_EXTENSION) }
            ?.sortedBy { it.name }
            .orEmpty()
        require(files.isNotEmpty()) {
            "No $LAYOUT_EXTENSION sources found in ${directory.absolutePath}"
        }
        return files.map { MissionDocumentSource(fileName = it.name, content = it.readText()) }
    }

    /** The 6 bundled demo screens — an instant corpus that needs no files on disk. */
    fun samples(): List<MissionDocumentSource> =
        DefaultDesignDocumentRepository().missionDocumentSources()

    /**
     * Writes [sources] into [directory] (created if missing). Only files whose content
     * actually changed are rewritten, so agent-side file watchers stay quiet.
     */
    fun write(directory: File, sources: List<MissionDocumentSource>): List<File> {
        require(directory.isDirectory || directory.mkdirs()) {
            "Cannot create directory: ${directory.absolutePath}"
        }
        return sources.map { source ->
            val target = File(directory, source.fileName)
            if (!target.isFile || target.readText() != source.content) {
                target.writeText(source.content)
            }
            target
        }
    }
}
