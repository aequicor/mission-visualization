package io.aequicor.visualization.editor.domain

/**
 * Loads the bundled mission SLM sources, compiles each standalone document and
 * merges them into one multi-page design document.
 */
class LoadDesignDocumentUseCase(
    private val repository: DesignDocumentRepository,
) {
    operator fun invoke(): MissionDocuments =
        compileMissionDocuments(repository.missionDocumentSources())
}
