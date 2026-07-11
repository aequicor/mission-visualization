package io.aequicor.visualization.editor.data

import io.aequicor.visualization.editor.domain.DesignDocumentRepository
import io.aequicor.visualization.editor.domain.MissionDocumentSource

/**
 * Serves the bundled Welcome project, one SLM source per page. These screens live
 * in memory: the editor does not autosave them, so a reload restores this pristine
 * set until the user saves the project or opens another one.
 */
class DefaultDesignDocumentRepository : DesignDocumentRepository {
    override fun missionDocumentSources(): List<MissionDocumentSource> = listOf(
        MissionDocumentSource("welcome-editor.layout.md", WelcomeEditorSlm),
        MissionDocumentSource("welcome-vectors.layout.md", WelcomeVectorsSlm),
        MissionDocumentSource("welcome-uml.layout.md", WelcomeUmlSlm),
    )
}
