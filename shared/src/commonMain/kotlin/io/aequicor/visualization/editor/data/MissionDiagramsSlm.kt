package io.aequicor.visualization.editor.data

/**
 * Diagrams screen: two diagram-canvas nodes demonstrating the `:subsystems:diagrams`
 * pipeline through SLM — a UML class diagram (generalization / composition / association)
 * and a state machine (initial / simple / final states with transitions). The `diagram:`
 * blocks are authored in the canonical `DiagramYamlWriter` form, so the first editor
 * write-back re-emits them byte-identically outside the edited entry.
 */
val MissionDiagramsSlm: String = missionSlm(
    """
    ---
    screen: diagrams
    page: Diagrams
    sourceLocale: en-US
    theme: light
    frame:
      width: 1440
      height: 1024
    ---

    # Diagrams

    node:
      id: frame_diagrams
      name: Diagrams
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

    ## Class Diagram

    node:
      id: class_diagram
      name: Class Diagram
      position:
        x: 48
        y: 48
    layout:
      sizing:
        width:
          type: fixed
          value: 560
        height:
          type: fixed
          value: 400
    diagram:
      nodes:
        - id: shape
          type: class
          x: 190
          y: 24
          w: 180
          h: 120
          name: Shape
          abstract: true
          fields:
            - "+ origin: Point"
          methods:
            - text: "area(): Double"
              abstract: true
        - id: circle
          type: class
          x: 60
          y: 220
          w: 180
          h: 100
          name: Circle
          fields:
            - "- radius: Double"
          methods:
            - "+ area(): Double"
        - id: drawing
          type: class
          x: 320
          y: 220
          w: 200
          h: 100
          name: Drawing
          methods:
            - "+ render(): Unit"
      edges:
        - id: e_extends
          from: circle
          to: shape
          relation: generalization
        - id: e_owns
          from: drawing
          to: circle
          relation: composition
        - id: e_draws
          from: drawing
          to: shape
          relation: association
          label: draws

    ## State Machine

    node:
      id: state_machine
      name: State Machine
      position:
        x: 660
        y: 48
    layout:
      sizing:
        width:
          type: fixed
          value: 540
        height:
          type: fixed
          value: 400
    diagram:
      nodes:
        - id: start
          type: state
          x: 40
          y: 40
          w: 28
          h: 28
          kind: initial
        - id: idle
          type: state
          x: 140
          y: 32
          w: 150
          h: 56
          name: Idle
        - id: active
          type: state
          x: 140
          y: 180
          w: 150
          h: 56
          name: Active
        - id: done
          type: state
          x: 380
          y: 194
          w: 28
          h: 28
          kind: final
      edges:
        - id: t_init
          from: start
          to: idle
          relation: transition
        - id: t_activate
          from: idle
          to: active
          relation: transition
          label: activate
        - id: t_finish
          from: active
          to: done
          relation: transition
          label: finish
    """,
)
