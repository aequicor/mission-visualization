package io.aequicor.visualization.editor.domain

import io.aequicor.visualization.engine.frontend.SlmCompileOptions
import io.aequicor.visualization.engine.frontend.compileSlm

/**
 * Loads the bundled mission SLM sources, compiles each standalone document and
 * merges them into one multi-page design document.
 */
class LoadDesignDocumentUseCase(
    private val repository: DesignDocumentRepository,
) {
    operator fun invoke(): MissionDocuments {
        val sources = repository.missionDocumentSources()
        val compiled = sources.map { source ->
            compileSlm(source.content, SlmCompileOptions(fileName = source.fileName))
        }
        return mergeMissionDocuments(sources, compiled)
    }
}
