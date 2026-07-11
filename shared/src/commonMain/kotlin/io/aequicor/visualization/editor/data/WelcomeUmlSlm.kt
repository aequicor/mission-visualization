package io.aequicor.visualization.editor.data

/**
 * Welcome screen 3: the project's own technical structure as UML — a component diagram of
 * the Gradle module graph (apps → shared → engine / subsystem pairs → ir → figures), a
 * flowchart of the SLM pipeline (CNL source → frontend → typed IR → validate → render, with
 * the SlmPatcher write-back loop), and a small class diagram of the editor's MVI core.
 * Diagrams are `## Diagram:` CNL containers in the canonical DiagramCnlWriter form.
 * `afterDelay` / nav buttons close the welcome tour loop. Pure CNL.
 */
val WelcomeUmlSlm: String = missionSlm(
    """
    ---
    screen: welcomeUml
    page: Architecture
    sourceLocale: en-US
    theme: light
    frame:
      width: 1440
      height: 1024
    ---

    # Architecture id frame_uml name «Architecture» 1440 by 1024 position 72 72 color #FFFFFF stroke #D8DEE9 radius 16 clip afterDelay (9000) navigate (welcomeEditor) animate (type moveIn easing easeOut duration 600 direction right)

    ## Text: id uml_title characters «Architecture» name «Title» width hug height hug position 48 40 color #172033 size 28 key welcomeUml.heading bold font «Inter» autosize both

    ## Text: id uml_subtitle characters «The engine behind this editor — module graph, SLM pipeline and the MVI core» name «Subtitle» width hug height hug position 48 84 color #5E6B7A size 15 key welcomeUml.subtitle weight 400 font «Inter» autosize both

    ## Diagram: id module_map name «Module Map» 640 by 700 position 48 130

    Node component app_android «androidApp» stereotype «app» 140 by 48 position 10 20
    Node component app_desktop «desktopApp» stereotype «app» 140 by 48 position 170 20
    Node component app_web «webApp» stereotype «app» 140 by 48 position 330 20
    Node component app_ios «iosApp» stereotype «app» 140 by 48 position 490 20
    Node component mod_shared «shared» stereotype «app shell» 200 by 56 position 220 140
    Node component mod_frontend «frontend» stereotype «SLM compiler» 170 by 56 position 20 270
    Node component mod_backend «backend-compose» stereotype «renderer» 180 by 56 position 230 270
    Node component mod_scene «scene» stereotype «runtime» 150 by 56 position 450 270
    Node component mod_ir «ir» stereotype «slm-ir/1.0» 200 by 64 position 220 410
    Node component mod_figures «figures» stereotype «geometry» 170 by 56 position 235 560
    Node component mod_typography «typography» stereotype «core + compose» 180 by 56 position 20 560
    Node component mod_diagrams «diagrams» stereotype «core + compose» 180 by 56 position 440 560
    Edge e_app_android from app_android to mod_shared relation dependency
    Edge e_app_desktop from app_desktop to mod_shared relation dependency
    Edge e_app_web from app_web to mod_shared relation dependency
    Edge e_app_ios from app_ios to mod_shared relation dependency
    Edge e_shared_frontend from mod_shared to mod_frontend relation dependency
    Edge e_shared_backend from mod_shared to mod_backend relation dependency
    Edge e_shared_scene from mod_shared to mod_scene relation dependency
    Edge e_frontend_ir from mod_frontend to mod_ir relation dependency
    Edge e_backend_ir from mod_backend to mod_ir relation dependency
    Edge e_scene_ir from mod_scene to mod_ir relation dependency
    Edge e_ir_figures from mod_ir to mod_figures relation dependency label «api»
    Edge e_shared_typography from mod_shared to mod_typography relation dependency
    Edge e_shared_diagrams from mod_shared to mod_diagrams relation dependency

    ## Text: id uml_modules_note characters «Dependencies point inward: apps → shared → engine renderers → pure cores → ir» name «Modules Note» width hug height hug position 48 846 color #5E6B7A size 13 key welcomeUml.modules.note weight 400 font «Inter» autosize both

    ## Diagram: id slm_pipeline name «SLM Pipeline» 600 by 480 position 760 130

    Node flowchart fc_source terminator 220 by 52 position 190 10 label «*.layout.md — CNL source»
    Node flowchart fc_compile process 200 by 52 position 200 100 label «frontend · compile»
    Node flowchart fc_ir process 230 by 52 position 185 190 label «slm-ir/1.0 · resolve + layout»
    Node flowchart fc_valid decision 140 by 84 position 230 280 label «valid?»
    Node flowchart fc_diag input-output 180 by 52 position 10 296 label «diagnostics IR-*»
    Node flowchart fc_render process 230 by 52 position 185 400 label «backend-compose · render»
    Edge fe_author from fc_source to fc_compile relation transition
    Edge fe_compile from fc_compile to fc_ir relation transition
    Edge fe_resolve from fc_ir to fc_valid relation transition
    Edge fe_invalid from fc_valid to fc_diag relation transition label «no»
    Edge fe_valid from fc_valid to fc_render relation transition label «yes»
    Edge fe_writeback from fc_render to fc_source relation transition label («SlmPatcher write-back» at source dx 30 dy 26) routing orthogonal

    ## Diagram: id editor_mvi name «Editor MVI» 600 by 220 position 760 650

    Node class cls_state «DesignEditorState» 190 by 96 position 10 110 field (+ «document: DesignDocument») field (+ «sources: List<Source>»)
    Node class cls_intent «DesignEditorIntent» abstract 190 by 72 position 400 110
    Node class cls_reducer «reduceDesignEditor» 210 by 72 position 195 10 method (+ «invoke(state, intent): State»)
    Edge me_consumes from cls_reducer to cls_intent relation dependency label «consumes»
    Edge me_returns from cls_reducer to cls_state relation dependency label «returns»

    ## Text: id uml_mvi_note characters «One immutable state, sealed intents, a pure reducer — edits write back into the CNL source» name «MVI Note» width hug height hug position 760 890 color #5E6B7A size 13 key welcomeUml.mvi.note weight 400 font «Inter» autosize both

    ## Frame: id uml_nav_prev name «Prev Button» 170 by 44 position 48 940 color #F6FAFE stroke #E3EAF2 radius 22 onClick navigate (welcomeVectors) animate (type push easing easeInOut duration 500 direction right)

    ### Text: id uml_nav_prev_label characters «← Vectors» name «Prev Label» 170 by 44 position 0 0 color #31516E size 14 key welcomeUml.nav.prev semibold font «Inter» text-align center text-valign center

    ## Frame: id uml_nav_next name «Next Button» 190 by 44 position 1202 940 color #1E88FF radius 22 onClick navigate (welcomeEditor) animate (type moveIn easing easeOut duration 500 direction right) motion duration 2400 loop frames (0 scale 1) (0.5 scale 1.05) (1 scale 1)

    ### Text: id uml_nav_next_label characters «Welcome →» name «Next Label» 190 by 44 position 0 0 color #FFFFFF size 14 key welcomeUml.nav.next semibold font «Inter» text-align center text-valign center
    """,
)
