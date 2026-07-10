package io.aequicor.visualization.editor.data

/**
 * Temporary pre-CNL snapshot for the Shapes Showcase migration test. It preserves the
 * former YAML-authored source so the shipped CNL version can be compared at the IR level.
 */
internal val ShapesShowcaseYamlSnapshot: String = missionSlm(
    """
    ---
    screen: shapesShowcase
    page: Shapes Showcase
    sourceLocale: en-US
    targetLocales:
      - en-US
      - ru-RU
    theme: light
    frame:
      width: 1440
      height: 1024
    ---

    # Shapes Showcase

    node:
      id: frame_showcase
      name: Shapes Showcase
      position:
        x: 72
        y: 72
      constraints:
        horizontal: left
        vertical: top
    layout:
      mode: column
      gap: §space
      padding:
        block: §padV
        inline: §padH
      align:
        inline: start
      clipContent: true
    style:
      radius: §radius
      fills:
        - token: color.surface
      strokes:
        - token: color.stroke
          weight: 1
          position: inside

    variables:
      collections:
        - id: theme
          name: Theme
          modes: [light, dark]
          defaultMode: light
          variables:
            color.surface:
              type: color
              values:
                light: "#FFFFFF"
                dark: "#111827"
            color.text:
              type: color
              values:
                light: "#172033"
                dark: "#F9FAFB"
            color.muted:
              type: color
              values:
                light: "#5E6B7A"
                dark: "#9CA3AF"
            color.accent:
              type: color
              values:
                light: "#1E88FF"
                dark: "#60A5FA"
            color.placeholder:
              type: color
              values:
                light: "#E9EEF4"
                dark: "#1F2937"
            color.placeholderDeep:
              type: color
              values:
                light: "#D8DFE8"
                dark: "#374151"
            color.line:
              type: color
              values:
                light: "#DCE3EC"
                dark: "#334155"
            color.stroke:
              type: color
              values:
                light: §color.accent
                dark: §color.accent
            radius:
              type: number
              values:
                light: 8
                dark: 8
            space:
              type: number
              values:
                light: 40
                dark: 40
            padH:
              type: number
              values:
                light: 56
                dark: 56
            padV:
              type: number
              values:
                light: 88
                dark: 88

    ## Text: Title

    node:
      type: text
      id: showcase_title
      name: Title
    text:
      key: shapesShowcase.title.heading
      defaultText: Shapes & Vector Components
      typography:
        fontFamily: Inter
        fontSize: 28
        fontWeight: 700
      resizing:
        width: hug
        height: hug
    style:
      fills:
        - token: color.text

    ## Frame: Parametrics

    node:
      type: frame
      id: showcase_parametrics
      name: Parametrics
    layout:
      mode: row
      gap: §space
      align:
        block: start
      sizing:
        width:
          type: hug
        height:
          type: hug

    ### Frame: Card Star

    node:
      type: frame
      id: card_star
      name: Card Star
    layout:
      mode: column
      gap: 12
      align:
        inline: center
      sizing:
        width:
          type: hug
        height:
          type: hug

    #### Text: Star Label

    node:
      type: text
      id: label_star
      name: Star Label
    text:
      key: shapesShowcase.label.star
      defaultText: Star
      typography:
        fontFamily: Inter
        fontSize: 13
        fontWeight: 600
      resizing:
        width: hug
        height: hug
    style:
      fills:
        - token: color.muted

    #### Shape: Star

    node:
      type: shape
      id: showcase_star
      name: Star
    shape:
      kind: star
      pointCount: 6
      innerRadius: 0.45
    layout:
      sizing:
        width:
          type: fixed
          value: 160
        height:
          type: fixed
          value: 160
    style:
      fills:
        - color: "#4C6EF5"

    ### Frame: Card Polygon

    node:
      type: frame
      id: card_polygon
      name: Card Polygon
    layout:
      mode: column
      gap: 12
      align:
        inline: center
      sizing:
        width:
          type: hug
        height:
          type: hug

    #### Text: Polygon Label

    node:
      type: text
      id: label_polygon
      name: Polygon Label
    text:
      key: shapesShowcase.label.polygon
      defaultText: Polygon
      typography:
        fontFamily: Inter
        fontSize: 13
        fontWeight: 600
      resizing:
        width: hug
        height: hug
    style:
      fills:
        - token: color.muted

    #### Shape: Polygon

    node:
      type: shape
      id: showcase_polygon
      name: Polygon
    shape:
      kind: polygon
      pointCount: 5
    layout:
      sizing:
        width:
          type: fixed
          value: 160
        height:
          type: fixed
          value: 160
    style:
      fills:
        - color: "#F76707"

    ### Frame: Card Ellipse

    node:
      type: frame
      id: card_ellipse
      name: Card Ellipse
    layout:
      mode: column
      gap: 12
      align:
        inline: center
      sizing:
        width:
          type: hug
        height:
          type: hug

    #### Text: Ellipse Label

    node:
      type: text
      id: label_ellipse
      name: Ellipse Label
    text:
      key: shapesShowcase.label.ellipse
      defaultText: Ellipse
      typography:
        fontFamily: Inter
        fontSize: 13
        fontWeight: 600
      resizing:
        width: hug
        height: hug
    style:
      fills:
        - token: color.muted

    #### Shape: Ellipse

    node:
      type: shape
      id: showcase_ellipse
      name: Ellipse
    shape:
      kind: ellipse
    layout:
      sizing:
        width:
          type: fixed
          value: 200
        height:
          type: fixed
          value: 160
    style:
      fills:
        - color: "#12B886"

    ## Frame: Strokes

    node:
      type: frame
      id: showcase_strokes
      name: Strokes
    layout:
      mode: row
      gap: §space
      align:
        block: start
      sizing:
        width:
          type: hug
        height:
          type: hug

    ### Frame: Card Arrow

    node:
      type: frame
      id: card_arrow
      name: Card Arrow
    layout:
      mode: column
      gap: 12
      align:
        inline: center
      sizing:
        width:
          type: hug
        height:
          type: hug

    #### Text: Arrow Label

    node:
      type: text
      id: label_arrow
      name: Arrow Label
    text:
      key: shapesShowcase.label.arrow
      defaultText: Arrow
      typography:
        fontFamily: Inter
        fontSize: 13
        fontWeight: 600
      resizing:
        width: hug
        height: hug
    style:
      fills:
        - token: color.muted

    #### Shape: Arrow

    node:
      type: shape
      id: showcase_arrow
      name: Arrow
    shape:
      kind: arrow
    layout:
      sizing:
        width:
          type: fixed
          value: 260
        height:
          type: fixed
          value: 56
    style:
      strokes:
        - color: "#E8590C"
          weight: 6
          caps: round
          joins: round

    ### Frame: Card Line

    node:
      type: frame
      id: card_line
      name: Card Line
    layout:
      mode: column
      gap: 12
      align:
        inline: center
      sizing:
        width:
          type: hug
        height:
          type: hug

    #### Text: Line Label

    node:
      type: text
      id: label_line
      name: Line Label
    text:
      key: shapesShowcase.label.line
      defaultText: Line (round caps)
      typography:
        fontFamily: Inter
        fontSize: 13
        fontWeight: 600
      resizing:
        width: hug
        height: hug
    style:
      fills:
        - token: color.muted

    #### Shape: Line

    node:
      type: shape
      id: showcase_line
      name: Line
    shape:
      kind: line
    layout:
      sizing:
        width:
          type: fixed
          value: 260
        height:
          type: fixed
          value: 24
    style:
      strokes:
        - color: "#7048E8"
          weight: 10
          caps: round

    ## Frame: Vectors

    node:
      type: frame
      id: showcase_vectors
      name: Vectors
    layout:
      mode: row
      gap: §space
      align:
        block: start
      sizing:
        width:
          type: hug
        height:
          type: hug

    ### Frame: Card Network

    node:
      type: frame
      id: card_network
      name: Card Network
    layout:
      mode: column
      gap: 12
      align:
        inline: center
      sizing:
        width:
          type: hug
        height:
          type: hug

    #### Text: Network Label

    node:
      type: text
      id: label_network
      name: Network Label
    text:
      key: shapesShowcase.label.network
      defaultText: Vector network (editable)
      typography:
        fontFamily: Inter
        fontSize: 13
        fontWeight: 600
      resizing:
        width: hug
        height: hug
    style:
      fills:
        - token: color.muted

    #### Vector: Network

    node:
      type: vector
      id: showcase_network
      name: Network
    vector:
      viewBox: [0, 0, 24, 24]
      network:
        vertices:
          - x: 12
            y: 2
            out: [7, 3]
            in: [-7, -3]
            mirror: angleAndLength
          - x: 22
            y: 20
            corner: true
          - x: 2
            y: 20
            corner: true
        segments:
          - [0, 1]
          - [1, 2]
          - [2, 0]
        regions:
          - windingRule: nonzero
            loops:
              - [0, 1, 2]
    layout:
      sizing:
        width:
          type: fixed
          value: 160
        height:
          type: fixed
          value: 160
    style:
      fills:
        - color: "#2F9E44"

    ### Frame: Card Donut

    node:
      type: frame
      id: card_donut
      name: Card Donut
    layout:
      mode: column
      gap: 12
      align:
        inline: center
      sizing:
        width:
          type: hug
        height:
          type: hug

    #### Text: Donut Label

    node:
      type: text
      id: label_donut
      name: Donut Label
    text:
      key: shapesShowcase.label.donut
      defaultText: Even-odd hole
      typography:
        fontFamily: Inter
        fontSize: 13
        fontWeight: 600
      resizing:
        width: hug
        height: hug
    style:
      fills:
        - token: color.muted

    #### Vector: Donut

    node:
      type: vector
      id: showcase_donut
      name: Donut
    vector:
      viewBox: [0, 0, 100, 100]
      paths:
        - windingRule: evenodd
          d: "M50 6 A44 44 0 1 0 50 94 A44 44 0 1 0 50 6 Z M50 30 A20 20 0 1 0 50 70 A20 20 0 1 0 50 30 Z"
    layout:
      sizing:
        width:
          type: fixed
          value: 160
        height:
          type: fixed
          value: 160
    style:
      fills:
        - color: "#1098AD"

    ### Frame: Card Boolean

    node:
      type: frame
      id: card_boolean
      name: Card Boolean
    layout:
      mode: column
      gap: 12
      align:
        inline: center
      sizing:
        width:
          type: hug
        height:
          type: hug

    #### Text: Boolean Label

    node:
      type: text
      id: label_boolean
      name: Boolean Label
    text:
      key: shapesShowcase.label.boolean
      defaultText: Boolean union
      typography:
        fontFamily: Inter
        fontSize: 13
        fontWeight: 600
      resizing:
        width: hug
        height: hug
    style:
      fills:
        - token: color.muted

    #### Boolean: Union

    node:
      type: vector
      id: showcase_union
      name: Union
    vector:
      boolean:
        op: union
        children:
          - union_a
          - union_b
    layout:
      sizing:
        width:
          type: fixed
          value: 160
        height:
          type: fixed
          value: 160
    style:
      fills:
        - color: "#E64980"

    ##### Shape: Union A

    node:
      type: shape
      id: union_a
      name: Union A
      position:
        mode: absolute
        x: 12
        y: 40
    shape:
      kind: ellipse
    layout:
      sizing:
        width:
          type: fixed
          value: 90
        height:
          type: fixed
          value: 90

    ##### Shape: Union B

    node:
      type: shape
      id: union_b
      name: Union B
      position:
        mode: absolute
        x: 58
        y: 40
    shape:
      kind: ellipse
    layout:
      sizing:
        width:
          type: fixed
          value: 90
        height:
          type: fixed
          value: 90
    """,
)
