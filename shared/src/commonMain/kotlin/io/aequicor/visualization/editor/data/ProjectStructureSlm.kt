package io.aequicor.visualization.editor.data

/**
 * Project Structure screen: a UML component diagram of the Gradle module graph
 * (apps -> :shared -> engine/subsystem renderers -> pure cores -> :engine:ir -> :subsystems:figures).
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
    
    # Project Structure
    
    node:
      id: frame_project_structure
      name: Project Structure
      position:
        x: 0
        y: 0
      constraints:
        horizontal: left
        vertical: top
    layout:
      mode: none
    style:
      radius: 16
      fills:
        - color: "#FFFFFF"
      strokes:
        - color: "#D8DEE9"
          weight: 1
          position: inside
    
    ## Module Dependency Diagram
    
    node:
      id: module_graph
      name: Module Dependency Diagram
      position:
        x: 48
        y: 64
    layout:
      sizing:
        width:
          type: fixed
          value: 1340
        height:
          type: fixed
          value: 920
    diagram:
      nodes:
        - id: android_app
          type: component
          x: 100
          y: 20
          w: 150
          h: 56
          name: androidApp
          stereotype: app
        - id: desktop_app
          type: component
          x: 380
          y: 20
          w: 150
          h: 56
          name: desktopApp
          stereotype: app
        - id: web_app
          type: component
          x: 660
          y: 20
          w: 150
          h: 56
          name: webApp
          stereotype: app
        - id: ios_app
          type: component
          x: 940
          y: 20
          w: 150
          h: 56
          name: iosApp
          stereotype: app
        - id: shared
          type: component
          x: 490
          y: 140
          w: 210
          h: 60
          name: shared
          stereotype: app shell
        - id: backend_compose
          type: component
          x: 20
          y: 290
          w: 170
          h: 56
          name: backend-compose
          stereotype: engine
        - id: scene
          type: component
          x: 210
          y: 290
          w: 140
          h: 56
          name: scene
          stereotype: engine
        - id: figures_compose
          type: component
          x: 370
          y: 290
          w: 170
          h: 56
          name: figures-compose
          stereotype: renderer
        - id: typography_compose
          type: component
          x: 560
          y: 290
          w: 190
          h: 56
          name: typography-compose
          stereotype: renderer
        - id: anchoring_compose
          type: component
          x: 770
          y: 290
          w: 180
          h: 56
          name: anchoring-compose
          stereotype: renderer
        - id: annotations_compose
          type: component
          x: 970
          y: 290
          w: 190
          h: 56
          name: annotations-compose
          stereotype: renderer
        - id: diagrams_compose
          type: component
          x: 1180
          y: 290
          w: 180
          h: 56
          name: diagrams-compose
          stereotype: renderer
        - id: annotations_slm
          type: component
          x: 970
          y: 410
          w: 190
          h: 52
          name: annotations-slm
          stereotype: sidecar
        - id: diagrams_slm
          type: component
          x: 1180
          y: 410
          w: 180
          h: 52
          name: diagrams-slm
          stereotype: sidecar
        - id: frontend
          type: component
          x: 20
          y: 560
          w: 170
          h: 56
          name: frontend
          stereotype: engine
        - id: typography
          type: component
          x: 560
          y: 560
          w: 190
          h: 56
          name: typography
          stereotype: core
        - id: anchoring
          type: component
          x: 770
          y: 560
          w: 180
          h: 56
          name: anchoring
          stereotype: core
        - id: annotations
          type: component
          x: 970
          y: 560
          w: 190
          h: 56
          name: annotations
          stereotype: core
        - id: diagrams
          type: component
          x: 1180
          y: 560
          w: 180
          h: 56
          name: diagrams
          stereotype: core
        - id: ir
          type: component
          x: 210
          y: 700
          w: 200
          h: 64
          name: ir
          stereotype: IR core
        - id: figures
          type: component
          x: 215
          y: 840
          w: 190
          h: 56
          name: figures
          stereotype: geometry
      edges:
        - id: e_android
          from: android_app
          to: shared
          relation: dependency
        - id: e_desktop
          from: desktop_app
          to: shared
          relation: dependency
        - id: e_web
          from: web_app
          to: shared
          relation: dependency
        - id: e_ios
          from: ios_app
          to: shared
          relation: dependency
        - id: e_shared_backend
          from: shared
          to: backend_compose
          relation: dependency
        - id: e_shared_frontend
          from: shared
          to: frontend
          relation: dependency
        - id: e_shared_scene
          from: shared
          to: scene
          relation: dependency
        - id: e_shared_typo
          from: shared
          to: typography_compose
          relation: dependency
        - id: e_shared_anchor
          from: shared
          to: anchoring_compose
          relation: dependency
        - id: e_shared_figures
          from: shared
          to: figures_compose
          relation: dependency
        - id: e_shared_anno_c
          from: shared
          to: annotations_compose
          relation: dependency
        - id: e_shared_anno_slm
          from: shared
          to: annotations_slm
          relation: dependency
        - id: e_shared_diag_c
          from: shared
          to: diagrams_compose
          relation: dependency
        - id: e_shared_diag_slm
          from: shared
          to: diagrams_slm
          relation: dependency
        - id: e_frontend_ir
          from: frontend
          to: ir
          relation: dependency
        - id: e_backend_ir
          from: backend_compose
          to: ir
          relation: dependency
        - id: e_scene_ir
          from: scene
          to: ir
          relation: dependency
        - id: e_backend_figc
          from: backend_compose
          to: figures_compose
          relation: dependency
        - id: e_backend_typc
          from: backend_compose
          to: typography_compose
          relation: dependency
        - id: e_ir_figures
          from: ir
          to: figures
          relation: dependency
          label: api
        - id: e_figc_fig
          from: figures_compose
          to: figures
          relation: dependency
        - id: e_typc_typ
          from: typography_compose
          to: typography
          relation: dependency
        - id: e_anchc_anch
          from: anchoring_compose
          to: anchoring
          relation: dependency
        - id: e_annoc_anno
          from: annotations_compose
          to: annotations
          relation: dependency
        - id: e_annoslm_anno
          from: annotations_slm
          to: annotations
          relation: dependency
        - id: e_diagc_diag
          from: diagrams_compose
          to: diagrams
          relation: dependency
        - id: e_diagslm_diag
          from: diagrams_slm
          to: diagrams
          relation: dependency
    """,
)
