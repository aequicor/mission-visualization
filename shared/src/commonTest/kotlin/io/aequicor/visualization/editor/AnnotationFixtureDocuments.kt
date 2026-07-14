package io.aequicor.visualization.editor

import io.aequicor.visualization.editor.data.MissionEventLogSlm
import io.aequicor.visualization.editor.data.MissionTelemetrySlm
import io.aequicor.visualization.editor.data.ShapesShowcaseSlm
import io.aequicor.visualization.editor.data.missionSlm
import io.aequicor.visualization.editor.domain.MissionDocumentSource
import io.aequicor.visualization.editor.domain.MissionDocuments
import io.aequicor.visualization.editor.domain.compileMissionDocuments

/**
 * Compact pure-CNL Mission Overview used by the annotation / issue-export tests: a stable
 * hero + tiles node set (`overview_hero`, `overview_tiles`, `tile_1..3`) whose ids and
 * display names the sidecar / prompt assertions reference. Replaces the retired raw-YAML
 * `LegacyMissionOverviewFixture` (typed-block YAML authoring was removed in Phase 3).
 */
internal val AnnotationMissionOverviewSlm: String = missionSlm(
    """
    ---
    screen: missionOverview
    page: Mission Overview
    sourceLocale: en-US
    targetLocales: [en-US, ru-RU]
    theme: light
    frame:
      width: 1440
      height: 1024
    ---

    # Mission Overview id frame_overview name «Mission Overview» 1440 by 1024 position 72 72 column gap 40 padding 88 56 color #FFFFFF radius 8 auto-layout

    ## Rectangle: id overview_hero name «Hero» width fill height 140 color #E9EEF4 radius 8

    ## AutoLayout: id overview_tiles name «Tiles» width fill height hug row gap 40

    ### Rectangle: id tile_1 name «Tile 1» width (fill 416) height 150 color #E9EEF4 radius 8

    ### Rectangle: id tile_2 name «Tile 2» width (fill 416) height 150 color #E9EEF4 radius 8

    ### Rectangle: id tile_3 name «Tile 3» width (fill 416) height 150 color #E9EEF4 radius 8
    """,
)

/**
 * The annotation-fixture Mission Overview paired with the shipped Telemetry / Event Log /
 * Shapes seeds — the multi-page environment the annotation write-back and export tests
 * expect (owning file `mission-overview.layout.md`, plus sibling pages).
 */
internal fun annotationFixtureDocuments(): MissionDocuments = compileMissionDocuments(
    listOf(
        MissionDocumentSource("mission-overview.layout.md", AnnotationMissionOverviewSlm),
        MissionDocumentSource("mission-telemetry.layout.md", MissionTelemetrySlm),
        MissionDocumentSource("mission-event-log.layout.md", MissionEventLogSlm),
        MissionDocumentSource("shapes-showcase.layout.md", ShapesShowcaseSlm),
    ),
)
