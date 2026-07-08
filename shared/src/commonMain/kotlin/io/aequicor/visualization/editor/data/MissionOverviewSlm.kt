package io.aequicor.visualization.editor.data

/**
 * Mission Overview page as Semantic Layout Markdown: the hero + wide placeholder
 * rectangles, three wire-tile instances sharing a row, and three wire-card
 * instances (one with an id-path fill override, one with `showTail: false`).
 *
 * The `variables:` collection (theme, light/dark modes) is duplicated verbatim in
 * all three mission documents because each document compiles standalone; the
 * merge in `mergeMissionDocuments` collapses identical collections.
 *
 * `ir` fences are the SLM escape hatch for IR features the typed blocks cannot
 * spell yet (id-path instance overrides with fills, `$prop` bindings,
 * per-subtree `variableModes`); see the compiled documents' comments.
 */
val MissionOverviewSlm: String = missionSlm(
    """
    ---
    screen: missionOverview
    page: Mission Overview
    sourceLocale: en-US
    targetLocales:
      - en-US
      - ru-RU
    theme: light
    frame:
      width: 1440
      height: 1024
    ---

    # Mission Overview

    node:
      id: frame_overview
      name: Mission Overview
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
        inline: stretch
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

    ## Shape: Hero

    node:
      type: shape
      id: overview_hero
      name: Hero
    shape:
      kind: rectangle
    layout:
      sizing:
        width:
          type: fill
        height:
          type: fixed
          value: 140
    style:
      radius: §radius
      fills:
        - token: color.placeholder

    ## Frame: Tiles

    node:
      type: frame
      id: overview_tiles
      name: Tiles
    layout:
      mode: row
      gap: §space
      align:
        block: start
      sizing:
        width:
          type: fill
        height:
          type: hug

    ### Instance: Tile 1

    node:
      type: instance
      id: tile_1
      name: Tile 1
    component:
      ref: cmp_wire_tile_default

    ### Instance: Tile 2

    node:
      type: instance
      id: tile_2
      name: Tile 2
    component:
      ref: cmp_wire_tile_default

    ### Instance: Tile 3

    node:
      type: instance
      id: tile_3
      name: Tile 3
    component:
      ref: cmp_wire_tile_default

    ## Shape: Wide block

    node:
      type: shape
      id: overview_wide
      name: Wide block
    shape:
      kind: rectangle
    layout:
      sizing:
        width:
          type: fill
        height:
          type: fixed
          value: 140
    style:
      radius: §radius
      fills:
        - token: color.placeholder

    ## Frame: Cards

    node:
      type: frame
      id: overview_cards
      name: Cards
    layout:
      mode: row
      gap: §space
      align:
        block: start
      sizing:
        width:
          type: fill
        height:
          type: hug

    ```ir
    { "id": "card_2", "order": 20, "type": "instance", "componentId": "cmp_wire_card",
      "overrides": [
        { "target": ["card_avatar"],
          "set": { "fills": [ { "type": "solid", "color": "#BFD7F5" } ] } }
      ] }
    ```

    ### Instance: Card 1

    node:
      type: instance
      id: card_1
      name: Card 1
      order: 10
    component:
      ref: cmp_wire_card

    ### Instance: Card 3

    node:
      type: instance
      id: card_3
      name: Card 3
      order: 30
    component:
      ref: cmp_wire_card
    props:
      showTail: false

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

    ## Component: Wire Card

    node:
      type: component
      id: cmp_wire_card
      name: Card
    component:
      name: Wire Card
      properties:
        showTail:
          type: boolean
          default: true
    layout:
      mode: column
      gap: 14
      padding: 24
      align:
        inline: stretch
      sizing:
        width:
          type: fill
          value: 416
        height:
          type: hug
          value: 158
    style:
      radius: §radius
      fills:
        - token: color.placeholder

    ```ir
    { "id": "card_tail", "order": 40, "type": "rectangle", "name": "Tail",
      "visible": { "§prop": "showTail" },
      "sizing": { "horizontal": "fixed", "vertical": "fixed" },
      "size": { "width": 160, "height": 12 },
      "cornerRadius": 6,
      "fills": [ { "type": "solid", "color": { "§var": "color.placeholderDeep" } } ] }
    ```

    ### Frame: Header

    node:
      type: frame
      id: card_header
      name: Header
      order: 10
    layout:
      mode: row
      gap: 12
      align:
        block: center
      sizing:
        width:
          type: fill
        height:
          type: hug

    #### Shape: Avatar

    node:
      type: shape
      id: card_avatar
      name: Avatar
    shape:
      kind: ellipse
    layout:
      sizing:
        width:
          type: fixed
          value: 32
        height:
          type: fixed
          value: 32
    style:
      fills:
        - token: color.placeholderDeep

    #### Shape: Header line

    node:
      type: shape
      id: card_header_line
      name: Header line
    shape:
      kind: rectangle
    layout:
      sizing:
        width:
          type: fill
        height:
          type: fixed
          value: 12
    style:
      radius: 6
      fills:
        - token: color.placeholderDeep

    ### Shape: Line 1

    node:
      type: shape
      id: card_line_1
      name: Line 1
      order: 20
    shape:
      kind: rectangle
    layout:
      sizing:
        width:
          type: fill
        height:
          type: fixed
          value: 12
    style:
      radius: 6
      fills:
        - token: color.placeholderDeep

    ### Shape: Line 2

    node:
      type: shape
      id: card_line_2
      name: Line 2
      order: 30
    shape:
      kind: rectangle
    layout:
      sizing:
        width:
          type: fill
        height:
          type: fixed
          value: 12
    style:
      radius: 6
      fills:
        - token: color.placeholderDeep
    """,
)
