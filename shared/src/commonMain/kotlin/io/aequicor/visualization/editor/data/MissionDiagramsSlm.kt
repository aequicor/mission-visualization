package io.aequicor.visualization.editor.data

/**
 * Diagrams screen: two diagram-canvas nodes demonstrating the `:subsystems:diagrams`
 * pipeline through SLM — a UML class diagram (generalization / composition / association)
 * and a state machine (initial / simple / final states with transitions). Authored in pure
 * CNL: each canvas is a `## Diagram:` container whose body sentences are the canonical
 * `DiagramCnlWriter` form, so the first editor write-back re-emits them byte-identically.
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

    # Diagrams id frame_diagrams name «Diagrams» 1440 by 1024 position 0 0 color #FFFFFF stroke #D8DEE9 radius 16

    ## Diagram: id class_diagram name «Class Diagram» 560 by 400 position 48 48

    Node class shape «Shape» abstract 180 by 120 position 190 24 field (+ «origin: Point») method (+ abstract «area(): Double»)
    Node class circle «Circle» 180 by 100 position 60 220 field (- «radius: Double») method (+ «area(): Double»)
    Node class drawing «Drawing» 200 by 100 position 320 220 method (+ «render(): Unit»)
    Edge e_extends from circle to shape relation generalization
    Edge e_owns from drawing to circle relation composition
    Edge e_draws from drawing to shape relation association label «draws»

    ## Diagram: id state_machine name «State Machine» 540 by 400 position 660 48

    Node state start initial 28 by 28 position 40 40
    Node state idle «Idle» 150 by 56 position 140 32
    Node state active «Active» 150 by 56 position 140 180
    Node state done final 28 by 28 position 380 194
    Edge t_init from start to idle relation transition
    Edge t_activate from idle to active relation transition label «activate»
    Edge t_finish from active to done relation transition label «finish»
    """,
)
