package io.aequicor.visualization.editor

import io.aequicor.visualization.editor.data.MissionEventLogSlm
import io.aequicor.visualization.editor.data.MissionOverviewSlm
import io.aequicor.visualization.editor.data.MissionTelemetrySlm
import io.aequicor.visualization.editor.data.ShapesShowcaseSlm
import io.aequicor.visualization.editor.domain.MissionDocumentSource
import io.aequicor.visualization.editor.domain.MissionDocuments
import io.aequicor.visualization.editor.domain.compileMissionDocuments

/**
 * The frozen CNL mission-demo fixtures assembled as the multi-page environment the reducer /
 * SlmPatcher / scene behavioural tests drive: the Overview wireframe (owning file
 * `mission-overview.layout.md`) plus the Telemetry page (component definitions, instances and
 * the status-dot pulse motion), the Event Log page (instances + `override` fills) and the
 * Shapes page. These used to be the shipped defaults; when the bundled project became the
 * three Welcome screens they were frozen into commonTest (same package) so the node ids the
 * write-back tests anchor to (`frame_overview`, `badge_text`, `showcase_*`, …) stay stable.
 */
internal fun missionDemoDocuments(): MissionDocuments = compileMissionDocuments(
    listOf(
        MissionDocumentSource("mission-overview.layout.md", MissionOverviewSlm),
        MissionDocumentSource("mission-telemetry.layout.md", MissionTelemetrySlm),
        MissionDocumentSource("mission-event-log.layout.md", MissionEventLogSlm),
        MissionDocumentSource("shapes-showcase.layout.md", ShapesShowcaseSlm),
    ),
)
