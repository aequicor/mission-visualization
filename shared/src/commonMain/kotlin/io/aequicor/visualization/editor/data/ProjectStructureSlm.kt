package io.aequicor.visualization.editor.data

/**
 * Project Structure screen: a UML component diagram of the Gradle module graph
 * (apps -> :shared -> engine/subsystem renderers -> pure cores -> :engine:ir -> :subsystems:figures),
 * authored in pure CNL — the host frame as heading property suffixes and the diagram as a
 * `## Diagram:` container with one sentence per element (`DiagramCnlWriter` canonical form,
 * so the first editor write-back re-emits the body byte-identically).
 * Mirrors the root `project-structure.layout.md`; dependency edges point inward.
 */
val ProjectStructureSlm: String = missionSlm(
    """
    ---
    screen: projectStructure
    page: Project Structure
    sourceLocale: en-US
    theme: light
    frame:
      width: 1440
      height: 1024
    ---

    # Project Structure id frame_project_structure name «Project Structure» 1440 by 1024 position 0 0 color #FFFFFF stroke #D8DEE9 radius 16

    ## Diagram: id module_graph name «Module Dependency Diagram» 1340 by 920 position 48 64

    Node component android_app «androidApp» stereotype «app» 150 by 56 position 100 20
    Node component desktop_app «desktopApp» stereotype «app» 150 by 56 position 380 20
    Node component web_app «webApp» stereotype «app» 150 by 56 position 660 20
    Node component ios_app «iosApp» stereotype «app» 150 by 56 position 940 20
    Node component shared «shared» stereotype «app shell» 210 by 60 position 490 140
    Node component backend_compose «backend-compose» stereotype «engine» 170 by 56 position 20 290
    Node component scene «scene» stereotype «engine» 140 by 56 position 210 290
    Node component figures_compose «figures-compose» stereotype «renderer» 170 by 56 position 370 290
    Node component typography_compose «typography-compose» stereotype «renderer» 190 by 56 position 560 290
    Node component anchoring_compose «anchoring-compose» stereotype «renderer» 180 by 56 position 770 290
    Node component annotations_compose «annotations-compose» stereotype «renderer» 190 by 56 position 970 290
    Node component diagrams_compose «diagrams-compose» stereotype «renderer» 180 by 56 position 1180 290
    Node component annotations_slm «annotations-slm» stereotype «sidecar» 190 by 52 position 970 410
    Node component diagrams_slm «diagrams-slm» stereotype «sidecar» 180 by 52 position 1180 410
    Node component frontend «frontend» stereotype «engine» 170 by 56 position 20 560
    Node component typography «typography» stereotype «core» 190 by 56 position 560 560
    Node component anchoring «anchoring» stereotype «core» 180 by 56 position 770 560
    Node component annotations «annotations» stereotype «core» 190 by 56 position 970 560
    Node component diagrams «diagrams» stereotype «core» 180 by 56 position 1180 560
    Node component ir «ir» stereotype «IR core» 200 by 64 position 210 700
    Node component figures «figures» stereotype «geometry» 190 by 56 position 215 840
    Edge e_android from android_app to shared relation dependency
    Edge e_desktop from desktop_app to shared relation dependency
    Edge e_web from web_app to shared relation dependency
    Edge e_ios from ios_app to shared relation dependency
    Edge e_shared_backend from shared to backend_compose relation dependency
    Edge e_shared_frontend from shared to frontend relation dependency
    Edge e_shared_scene from shared to scene relation dependency
    Edge e_shared_typo from shared to typography_compose relation dependency
    Edge e_shared_anchor from shared to anchoring_compose relation dependency
    Edge e_shared_figures from shared to figures_compose relation dependency
    Edge e_shared_anno_c from shared to annotations_compose relation dependency
    Edge e_shared_anno_slm from shared to annotations_slm relation dependency
    Edge e_shared_diag_c from shared to diagrams_compose relation dependency
    Edge e_shared_diag_slm from shared to diagrams_slm relation dependency
    Edge e_frontend_ir from frontend to ir relation dependency
    Edge e_backend_ir from backend_compose to ir relation dependency
    Edge e_scene_ir from scene to ir relation dependency
    Edge e_backend_figc from backend_compose to figures_compose relation dependency
    Edge e_backend_typc from backend_compose to typography_compose relation dependency
    Edge e_ir_figures from ir to figures relation dependency label «api»
    Edge e_figc_fig from figures_compose to figures relation dependency
    Edge e_typc_typ from typography_compose to typography relation dependency
    Edge e_anchc_anch from anchoring_compose to anchoring relation dependency
    Edge e_annoc_anno from annotations_compose to annotations relation dependency
    Edge e_annoslm_anno from annotations_slm to annotations relation dependency
    Edge e_diagc_diag from diagrams_compose to diagrams relation dependency
    Edge e_diagslm_diag from diagrams_slm to diagrams relation dependency
    """,
)
