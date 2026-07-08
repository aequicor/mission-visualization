package io.aequicor.visualization.editor.data

/**
 * Telemetry page as Semantic Layout Markdown: a 3-column grid frame with a
 * gradient header spanning all columns, six wire-tile instances (two highlight),
 * and an absolutely positioned LIVE badge pinned to the top-right corner.
 *
 * The wire-tile components are duplicated from the Overview document (documents
 * compile standalone; identical definitions collapse in `mergeMissionDocuments`).
 * The old JSON's `set_wire_tile` component set (kind=default|highlight) is
 * expressed as two components referenced directly: SLM component blocks declare
 * variant axes but cannot map axis values to distinct variant roots.
 *
 * The old grid rows `[fixed 140, hug, hug]` become implicit hug rows plus a fixed
 * 140 header height: SLM `rows:` supports only uniform tracks. Same layout result.
 */
val MissionTelemetrySlm: String = missionSlm(
    """
    ---
    screen: missionTelemetry
    page: Telemetry
    sourceLocale: en-US
    targetLocales:
      - en-US
      - ru-RU
    theme: light
    frame:
      width: 1440
      height: 1024
    ---

    # Telemetry

    node:
      id: frame_telemetry
      name: Telemetry
      position:
        x: 72
        y: 72
      constraints:
        horizontal: left
        vertical: top
    layout:
      mode: grid
      columns:
        count: 3
        track: 1fr
      rows:
        auto: true
        track: hug
      gap:
        row: 40
        column: 40
      padding:
        block: §padV
        inline: §padH
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

    ## Shape: Header

    node:
      type: shape
      id: telemetry_header
      name: Header
    shape:
      kind: rectangle
    layout:
      placement:
        column: 1
        row: 1
        columnSpan: 3
      sizing:
        width:
          type: fill
        height:
          type: fixed
          value: 140
    style:
      radius: §radius
      fills:
        - type: linearGradient
          from:
            x: 0
            y: 0
          to:
            x: 1
            y: 0
          stops:
            - position: 0
              color: "#DCEBFD"
            - position: 1
              color: "#E9EEF4"

    ## Instance: Tile 1

    node:
      type: instance
      id: t_tile_1
      name: Tile 1
    component:
      ref: cmp_wire_tile_highlight

    ## Instance: Tile 2

    node:
      type: instance
      id: t_tile_2
      name: Tile 2
    component:
      ref: cmp_wire_tile_default

    ## Instance: Tile 3

    node:
      type: instance
      id: t_tile_3
      name: Tile 3
    component:
      ref: cmp_wire_tile_default

    ## Instance: Tile 4

    node:
      type: instance
      id: t_tile_4
      name: Tile 4
    component:
      ref: cmp_wire_tile_default

    ## Instance: Tile 5

    node:
      type: instance
      id: t_tile_5
      name: Tile 5
    component:
      ref: cmp_wire_tile_highlight

    ## Instance: Tile 6

    node:
      type: instance
      id: t_tile_6
      name: Tile 6
    component:
      ref: cmp_wire_tile_default

    ## Frame: Live badge

    node:
      type: frame
      id: telemetry_badge
      name: Live badge
      position:
        x: 1296
        y: 36
      constraints:
        horizontal: right
        vertical: top
    layout:
      ignoreAutoLayout: true
      mode: row
      gap: 6
      align:
        block: center
      padding:
        blockStart: 5
        inlineEnd: 12
        blockEnd: 5
        inlineStart: 12
      sizing:
        width:
          type: hug
          value: 88
        height:
          type: hug
          value: 28
    style:
      radius: 999
      fills:
        - token: color.accent

    ### Shape: Dot

    node:
      type: shape
      id: badge_dot
      name: Dot
    shape:
      kind: ellipse
    layout:
      sizing:
        width:
          type: fixed
          value: 8
        height:
          type: fixed
          value: 8
    style:
      fills:
        - color: "#FFFFFF"

    ### Text: Live

    node:
      type: text
      id: badge_text
      name: LIVE
    text:
      key: missionTelemetry.badge.live
      defaultText: LIVE
      typography:
        fontFamily: Inter
        fontSize: 12
        fontWeight: 700
      resizing:
        width: hug
        height: hug
    style:
      fills:
        - color: "#FFFFFF"

    ## Component: Wire Tile Default

    node:
      type: component
      id: cmp_wire_tile_default
      name: Tile
    component:
      name: Wire Tile / Default
    layout:
      mode: none
      clipContent: true
      sizing:
        width:
          type: fill
          value: 416
        height:
          type: fixed
          value: 150
    style:
      radius: §radius
      fills:
        - token: color.placeholder

    ## Component: Wire Tile Highlight

    node:
      type: component
      id: cmp_wire_tile_highlight
      name: Tile Highlight
    component:
      name: Wire Tile / Highlight
    layout:
      mode: none
      clipContent: true
      sizing:
        width:
          type: fill
          value: 416
        height:
          type: fixed
          value: 150
    style:
      radius: §radius
      fills:
        - type: linearGradient
          from:
            x: 0
            y: 0
          to:
            x: 1
            y: 1
          stops:
            - position: 0
              color: "#DCEBFD"
            - position: 1
              color: "#E9EEF4"
    """,
)
