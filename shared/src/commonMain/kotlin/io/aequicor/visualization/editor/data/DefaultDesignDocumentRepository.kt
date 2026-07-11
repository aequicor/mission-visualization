package io.aequicor.visualization.editor.data

import io.aequicor.visualization.editor.domain.DesignDocumentRepository
import io.aequicor.visualization.editor.domain.MissionDocumentSource

/** Serves the bundled mission design SLM documents, one per page. */
class DefaultDesignDocumentRepository : DesignDocumentRepository {
    override fun missionDocumentSources(): List<MissionDocumentSource> = listOf(
        MissionDocumentSource("mission-overview.layout.md", MissionOverviewSlm),
        MissionDocumentSource("mission-telemetry.layout.md", MissionTelemetrySlm),
        MissionDocumentSource("mission-event-log.layout.md", MissionEventLogSlm),
        MissionDocumentSource("shapes-showcase.layout.md", ShapesShowcaseSlm),
        MissionDocumentSource("diagrams.layout.md", MissionDiagramsSlm),
        MissionDocumentSource("cnl-showcase.layout.md", CnlShowcaseSlm),
    )
}
