package io.aequicor.visualization.editor

import io.aequicor.visualization.editor.data.MissionEventLogSlm
import io.aequicor.visualization.editor.data.MissionOverviewSlm
import io.aequicor.visualization.editor.data.MissionTelemetrySlm
import io.aequicor.visualization.editor.data.ShapesShowcaseSlm
import io.aequicor.visualization.editor.domain.MissionDocumentSource
import io.aequicor.visualization.editor.domain.MissionDocuments
import io.aequicor.visualization.editor.domain.compileMissionDocuments

/**
 * The shipped CNL mission demos assembled as the multi-page environment the reducer /
 * SlmPatcher / scene behavioural tests drive: the Overview wireframe (owning file
 * `mission-overview.layout.md`) plus the Telemetry page (component definitions, instances and
 * the status-dot pulse motion), the Event Log page (instances + `override` fills) and the
 * Shapes page. All authored in CNL — the former YAML + `ir`-fenced legacy Overview fixture was
 * retired when CNL became the sole authoring format, so these tests now exercise the exact
 * sources the app ships.
 */
internal fun missionDemoDocuments(): MissionDocuments = compileMissionDocuments(
    listOf(
        MissionDocumentSource("mission-overview.layout.md", MissionOverviewSlm),
        MissionDocumentSource("mission-telemetry.layout.md", MissionTelemetrySlm),
        MissionDocumentSource("mission-event-log.layout.md", MissionEventLogSlm),
        MissionDocumentSource("shapes-showcase.layout.md", ShapesShowcaseSlm),
    ),
)
