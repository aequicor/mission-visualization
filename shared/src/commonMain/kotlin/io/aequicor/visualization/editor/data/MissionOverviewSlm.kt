package io.aequicor.visualization.editor.data

/**
 * Mission Overview page as Semantic Layout Markdown — a self-referential wireframe of
 * the editor application itself ("вёрстка текущего приложения"): the light chrome page,
 * the rounded window, and the three working panels — Source (menu, Save/Reset, tabs,
 * layer tree, screen list), Canvas (title, toolbar, board, PC/MOB/TAB + Canvas/Scene +
 * zoom), and the Inspector (Design/Prototype/Comments tabs, Position, Layout with the
 * **Free** auto-layout segment active).
 *
 * Everything is authored in **Free layout** (`layout.mode: none`, which maps to
 * `LayoutMode.None`): each node carries an absolute `position:` and a fixed size, so the
 * document is a flat, hand-placed mock rather than an auto-layout flow.
 *
 * The `variables:` collection (theme, light/dark) is duplicated verbatim across all
 * mission documents because each compiles standalone; `mergeMissionDocuments` collapses
 * the identical collections. App-specific chrome colors that are not part of that shared
 * token set are inlined as literal hex fills.
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
      mode: none
      clipContent: true
    style:
      fills:
        - color: "#EAF5FF"

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

    ## Shape: Window

    node:
      type: shape
      id: win_bg
      name: Window
      position:
        x: 16
        y: 16
    shape:
      kind: rectangle
    layout:
      sizing:
        width:
          type: fixed
          value: 1408
        height:
          type: fixed
          value: 992
    style:
      radius: 24
      fills:
        - color: "#FFFFFF"
      strokes:
        - color: "#CEE1F2"
          weight: 1
          position: inside

    ## Frame: Source

    node:
      type: frame
      id: src_panel
      name: Source
      position:
        x: 40
        y: 40
    layout:
      mode: none
      clipContent: true
      sizing:
        width:
          type: fixed
          value: 424
        height:
          type: fixed
          value: 944
    style:
      radius: 16
      fills:
        - color: "#FBFDFF"
      strokes:
        - color: "#E3EAF2"
          weight: 1
          position: inside

    ### Frame: Menu Button

    node:
      type: frame
      id: src_menu
      name: Menu Button
      position:
        x: 20
        y: 20
    layout:
      mode: none
      sizing:
        width:
          type: fixed
          value: 44
        height:
          type: fixed
          value: 44
    style:
      radius: 12
      fills:
        - token: color.accent

    #### Shape: Menu Line 1

    node:
      type: shape
      id: src_menu_l1
      name: Menu Line 1
      position:
        x: 12
        y: 15
    shape:
      kind: rectangle
    layout:
      sizing:
        width:
          type: fixed
          value: 20
        height:
          type: fixed
          value: 3
    style:
      radius: 2
      fills:
        - color: "#FFFFFF"

    #### Shape: Menu Line 2

    node:
      type: shape
      id: src_menu_l2
      name: Menu Line 2
      position:
        x: 12
        y: 21
    shape:
      kind: rectangle
    layout:
      sizing:
        width:
          type: fixed
          value: 20
        height:
          type: fixed
          value: 3
    style:
      radius: 2
      fills:
        - color: "#FFFFFF"

    #### Shape: Menu Line 3

    node:
      type: shape
      id: src_menu_l3
      name: Menu Line 3
      position:
        x: 12
        y: 27
    shape:
      kind: rectangle
    layout:
      sizing:
        width:
          type: fixed
          value: 20
        height:
          type: fixed
          value: 3
    style:
      radius: 2
      fills:
        - color: "#FFFFFF"

    ### Text: Source Title

    node:
      type: text
      id: src_title
      name: Source Title
      position:
        x: 80
        y: 28
    text:
      key: app.source.title
      defaultText: Source
      typography:
        fontFamily: Inter
        fontSize: 24
        fontWeight: 700
      resizing:
        width: hug
        height: hug
    style:
      fills:
        - token: color.text

    ### Frame: Save Button

    node:
      type: frame
      id: src_save
      name: Save Button
      position:
        x: 250
        y: 28
    layout:
      mode: none
      sizing:
        width:
          type: fixed
          value: 74
        height:
          type: fixed
          value: 40
    style:
      radius: 10
      fills:
        - color: "#F6FAFE"
      strokes:
        - color: "#E3EAF2"
          weight: 1
          position: inside

    #### Text: Save Label

    node:
      type: text
      id: src_save_label
      name: Save Label
      position:
        x: 0
        y: 0
    text:
      key: app.source.save
      defaultText: Save
      typography:
        fontFamily: Inter
        fontSize: 14
        fontWeight: 600
        horizontalAlign: center
        verticalAlign: center
      resizing:
        width: fixed
        height: fixed
    layout:
      sizing:
        width:
          type: fixed
          value: 74
        height:
          type: fixed
          value: 40
    style:
      fills:
        - color: "#31516E"

    ### Frame: Reset Button

    node:
      type: frame
      id: src_reset
      name: Reset Button
      position:
        x: 330
        y: 28
    layout:
      mode: none
      sizing:
        width:
          type: fixed
          value: 74
        height:
          type: fixed
          value: 40
    style:
      radius: 10
      fills:
        - color: "#F6FAFE"
      strokes:
        - color: "#E3EAF2"
          weight: 1
          position: inside

    #### Text: Reset Label

    node:
      type: text
      id: src_reset_label
      name: Reset Label
      position:
        x: 0
        y: 0
    text:
      key: app.source.reset
      defaultText: Reset
      typography:
        fontFamily: Inter
        fontSize: 14
        fontWeight: 600
        horizontalAlign: center
        verticalAlign: center
      resizing:
        width: fixed
        height: fixed
    layout:
      sizing:
        width:
          type: fixed
          value: 74
        height:
          type: fixed
          value: 40
    style:
      fills:
        - color: "#31516E"

    ### Frame: Tabs

    node:
      type: frame
      id: src_tabs
      name: Tabs
      position:
        x: 20
        y: 84
    layout:
      mode: none
      sizing:
        width:
          type: fixed
          value: 384
        height:
          type: fixed
          value: 44
    style:
      radius: 12
      fills:
        - color: "#F4F8FC"
      strokes:
        - color: "#E3EAF2"
          weight: 1
          position: inside

    #### Text: Tab Markdown

    node:
      type: text
      id: src_tab_md
      name: Tab Markdown
      position:
        x: 20
        y: 15
    text:
      key: app.source.tab.markdown
      defaultText: Markdown
      typography:
        fontFamily: Inter
        fontSize: 14
        fontWeight: 500
      resizing:
        width: hug
        height: hug
    style:
      fills:
        - token: color.muted

    #### Text: Tab Resources

    node:
      type: text
      id: src_tab_res
      name: Tab Resources
      position:
        x: 140
        y: 15
    text:
      key: app.source.tab.resources
      defaultText: Resources
      typography:
        fontFamily: Inter
        fontSize: 14
        fontWeight: 500
      resizing:
        width: hug
        height: hug
    style:
      fills:
        - token: color.muted

    #### Text: Tab Layers

    node:
      type: text
      id: src_tab_layers
      name: Tab Layers
      position:
        x: 285
        y: 15
    text:
      key: app.source.tab.layers
      defaultText: Layers
      typography:
        fontFamily: Inter
        fontSize: 14
        fontWeight: 700
      resizing:
        width: hug
        height: hug
    style:
      fills:
        - token: color.accent

    #### Shape: Tab Underline

    node:
      type: shape
      id: src_tab_underline
      name: Tab Underline
      position:
        x: 283
        y: 36
    shape:
      kind: rectangle
    layout:
      sizing:
        width:
          type: fixed
          value: 58
        height:
          type: fixed
          value: 3
    style:
      radius: 2
      fills:
        - token: color.accent

    ### Shape: Row Highlight

    node:
      type: shape
      id: src_row_hl
      name: Row Highlight
      position:
        x: 16
        y: 145
    shape:
      kind: rectangle
    layout:
      sizing:
        width:
          type: fixed
          value: 392
        height:
          type: fixed
          value: 28
    style:
      radius: 8
      fills:
        - color: "#EAF4FF"

    ### Shape: Layer Icon 0

    node:
      type: shape
      id: src_ic0
      name: Layer Icon 0
      position:
        x: 26
        y: 149
    shape:
      kind: rectangle
    layout:
      sizing:
        width:
          type: fixed
          value: 14
        height:
          type: fixed
          value: 14
    style:
      radius: 4
      fills:
        - color: "#B9D9FF"

    ### Text: Layer Label 0

    node:
      type: text
      id: src_lb0
      name: Layer Label 0
      position:
        x: 50
        y: 149
    text:
      key: app.layer.overview
      defaultText: Mission Overview
      typography:
        fontFamily: Inter
        fontSize: 13
        fontWeight: 600
      resizing:
        width: hug
        height: hug
    style:
      fills:
        - token: color.accent

    ### Shape: Layer Icon 1

    node:
      type: shape
      id: src_ic1
      name: Layer Icon 1
      position:
        x: 46
        y: 179
    shape:
      kind: rectangle
    layout:
      sizing:
        width:
          type: fixed
          value: 14
        height:
          type: fixed
          value: 14
    style:
      radius: 4
      fills:
        - color: "#D9E5F1"

    ### Text: Layer Label 1

    node:
      type: text
      id: src_lb1
      name: Layer Label 1
      position:
        x: 70
        y: 179
    text:
      key: app.layer.cards
      defaultText: Cards
      typography:
        fontFamily: Inter
        fontSize: 13
        fontWeight: 600
      resizing:
        width: hug
        height: hug
    style:
      fills:
        - token: color.text

    ### Shape: Layer Icon 2

    node:
      type: shape
      id: src_ic2
      name: Layer Icon 2
      position:
        x: 66
        y: 209
    shape:
      kind: rectangle
    layout:
      sizing:
        width:
          type: fixed
          value: 14
        height:
          type: fixed
          value: 14
    style:
      radius: 4
      fills:
        - color: "#D9E5F1"

    ### Text: Layer Label 2

    node:
      type: text
      id: src_lb2
      name: Layer Label 2
      position:
        x: 90
        y: 209
    text:
      key: app.layer.card3
      defaultText: Card 3
      typography:
        fontFamily: Inter
        fontSize: 13
        fontWeight: 400
      resizing:
        width: hug
        height: hug
    style:
      fills:
        - token: color.text

    ### Shape: Layer Icon 3

    node:
      type: shape
      id: src_ic3
      name: Layer Icon 3
      position:
        x: 66
        y: 239
    shape:
      kind: rectangle
    layout:
      sizing:
        width:
          type: fixed
          value: 14
        height:
          type: fixed
          value: 14
    style:
      radius: 4
      fills:
        - color: "#D9E5F1"

    ### Text: Layer Label 3

    node:
      type: text
      id: src_lb3
      name: Layer Label 3
      position:
        x: 90
        y: 239
    text:
      key: app.layer.card2
      defaultText: card_2
      typography:
        fontFamily: Inter
        fontSize: 13
        fontWeight: 400
      resizing:
        width: hug
        height: hug
    style:
      fills:
        - token: color.text

    ### Shape: Layer Icon 4

    node:
      type: shape
      id: src_ic4
      name: Layer Icon 4
      position:
        x: 66
        y: 269
    shape:
      kind: rectangle
    layout:
      sizing:
        width:
          type: fixed
          value: 14
        height:
          type: fixed
          value: 14
    style:
      radius: 4
      fills:
        - color: "#D9E5F1"

    ### Text: Layer Label 4

    node:
      type: text
      id: src_lb4
      name: Layer Label 4
      position:
        x: 90
        y: 269
    text:
      key: app.layer.card1
      defaultText: Card 1
      typography:
        fontFamily: Inter
        fontSize: 13
        fontWeight: 400
      resizing:
        width: hug
        height: hug
    style:
      fills:
        - token: color.text

    ### Shape: Layer Icon 5

    node:
      type: shape
      id: src_ic5
      name: Layer Icon 5
      position:
        x: 66
        y: 299
    shape:
      kind: rectangle
    layout:
      sizing:
        width:
          type: fixed
          value: 14
        height:
          type: fixed
          value: 14
    style:
      radius: 3
      fills:
        - color: "#D8DFE8"

    ### Text: Layer Label 5

    node:
      type: text
      id: src_lb5
      name: Layer Label 5
      position:
        x: 90
        y: 299
    text:
      key: app.layer.wideblock
      defaultText: Wide block
      typography:
        fontFamily: Inter
        fontSize: 13
        fontWeight: 400
      resizing:
        width: hug
        height: hug
    style:
      fills:
        - token: color.text

    ### Shape: Layer Icon 6

    node:
      type: shape
      id: src_ic6
      name: Layer Icon 6
      position:
        x: 46
        y: 329
    shape:
      kind: rectangle
    layout:
      sizing:
        width:
          type: fixed
          value: 14
        height:
          type: fixed
          value: 14
    style:
      radius: 4
      fills:
        - color: "#D9E5F1"

    ### Text: Layer Label 6

    node:
      type: text
      id: src_lb6
      name: Layer Label 6
      position:
        x: 70
        y: 329
    text:
      key: app.layer.tiles
      defaultText: Tiles
      typography:
        fontFamily: Inter
        fontSize: 13
        fontWeight: 600
      resizing:
        width: hug
        height: hug
    style:
      fills:
        - token: color.text

    ### Shape: Layer Icon 7

    node:
      type: shape
      id: src_ic7
      name: Layer Icon 7
      position:
        x: 66
        y: 359
    shape:
      kind: rectangle
    layout:
      sizing:
        width:
          type: fixed
          value: 14
        height:
          type: fixed
          value: 14
    style:
      radius: 4
      fills:
        - color: "#D9E5F1"

    ### Text: Layer Label 7

    node:
      type: text
      id: src_lb7
      name: Layer Label 7
      position:
        x: 90
        y: 359
    text:
      key: app.layer.tile3
      defaultText: Tile 3
      typography:
        fontFamily: Inter
        fontSize: 13
        fontWeight: 400
      resizing:
        width: hug
        height: hug
    style:
      fills:
        - token: color.text

    ### Shape: Layer Icon 8

    node:
      type: shape
      id: src_ic8
      name: Layer Icon 8
      position:
        x: 66
        y: 389
    shape:
      kind: rectangle
    layout:
      sizing:
        width:
          type: fixed
          value: 14
        height:
          type: fixed
          value: 14
    style:
      radius: 4
      fills:
        - color: "#D9E5F1"

    ### Text: Layer Label 8

    node:
      type: text
      id: src_lb8
      name: Layer Label 8
      position:
        x: 90
        y: 389
    text:
      key: app.layer.tile2
      defaultText: Tile 2
      typography:
        fontFamily: Inter
        fontSize: 13
        fontWeight: 400
      resizing:
        width: hug
        height: hug
    style:
      fills:
        - token: color.text

    ### Shape: Layer Icon 9

    node:
      type: shape
      id: src_ic9
      name: Layer Icon 9
      position:
        x: 66
        y: 419
    shape:
      kind: rectangle
    layout:
      sizing:
        width:
          type: fixed
          value: 14
        height:
          type: fixed
          value: 14
    style:
      radius: 4
      fills:
        - color: "#D9E5F1"

    ### Text: Layer Label 9

    node:
      type: text
      id: src_lb9
      name: Layer Label 9
      position:
        x: 90
        y: 419
    text:
      key: app.layer.tile1
      defaultText: Tile 1
      typography:
        fontFamily: Inter
        fontSize: 13
        fontWeight: 400
      resizing:
        width: hug
        height: hug
    style:
      fills:
        - token: color.text

    ### Text: Screens Heading

    node:
      type: text
      id: src_screens_h
      name: Screens Heading
      position:
        x: 24
        y: 462
    text:
      key: app.source.screens
      defaultText: Screens
      typography:
        fontFamily: Inter
        fontSize: 17
        fontWeight: 700
      resizing:
        width: hug
        height: hug
    style:
      fills:
        - token: color.text

    ### Frame: Add Screen

    node:
      type: frame
      id: src_add
      name: Add Screen
      position:
        x: 368
        y: 458
    layout:
      mode: none
      sizing:
        width:
          type: fixed
          value: 34
        height:
          type: fixed
          value: 34
    style:
      radius: 10
      fills:
        - color: "#F6FAFE"
      strokes:
        - color: "#E3EAF2"
          weight: 1
          position: inside

    #### Text: Add Plus

    node:
      type: text
      id: src_add_plus
      name: Add Plus
      position:
        x: 0
        y: -1
    text:
      key: app.source.add
      defaultText: +
      typography:
        fontFamily: Inter
        fontSize: 20
        fontWeight: 500
        horizontalAlign: center
        verticalAlign: center
      resizing:
        width: fixed
        height: fixed
    layout:
      sizing:
        width:
          type: fixed
          value: 34
        height:
          type: fixed
          value: 34
    style:
      fills:
        - token: color.muted

    ### Frame: Screen Card 1

    node:
      type: frame
      id: src_card1
      name: Screen Card 1
      position:
        x: 16
        y: 500
    layout:
      mode: none
      sizing:
        width:
          type: fixed
          value: 384
        height:
          type: fixed
          value: 78
    style:
      radius: 14
      fills:
        - color: "#EAF4FF"
      strokes:
        - color: "#1E88FF"
          weight: 1
          position: inside

    #### Shape: Card Thumb 1

    node:
      type: shape
      id: src_card1_thumb
      name: Card Thumb 1
      position:
        x: 18
        y: 18
    shape:
      kind: rectangle
    layout:
      sizing:
        width:
          type: fixed
          value: 58
        height:
          type: fixed
          value: 42
    style:
      radius: 8
      fills:
        - color: "#EAF2FA"
      strokes:
        - color: "#E3EAF2"
          weight: 1
          position: inside

    #### Text: Card Name 1

    node:
      type: text
      id: src_card1_name
      name: Card Name 1
      position:
        x: 92
        y: 17
    text:
      key: app.screen.overview.name
      defaultText: Mission Overview
      typography:
        fontFamily: Inter
        fontSize: 15
        fontWeight: 600
      resizing:
        width: hug
        height: hug
    style:
      fills:
        - token: color.text

    #### Text: Card Dims 1

    node:
      type: text
      id: src_card1_dims
      name: Card Dims 1
      position:
        x: 92
        y: 44
    text:
      key: app.screen.overview.dims
      defaultText: 1440 x 1024
      typography:
        fontFamily: Inter
        fontSize: 12
        fontWeight: 400
      resizing:
        width: hug
        height: hug
    style:
      fills:
        - token: color.muted

    #### Shape: Card Status 1

    node:
      type: shape
      id: src_card1_dot
      name: Card Status 1
      position:
        x: 350
        y: 33
    shape:
      kind: ellipse
    layout:
      sizing:
        width:
          type: fixed
          value: 12
        height:
          type: fixed
          value: 12
    style:
      fills:
        - color: "#17C46B"

    ### Frame: Screen Card 2

    node:
      type: frame
      id: src_card2
      name: Screen Card 2
      position:
        x: 16
        y: 588
    layout:
      mode: none
      sizing:
        width:
          type: fixed
          value: 384
        height:
          type: fixed
          value: 78
    style:
      radius: 14
      fills:
        - color: "#FDFEFF"
      strokes:
        - color: "#E3EAF2"
          weight: 1
          position: inside

    #### Shape: Card Thumb 2

    node:
      type: shape
      id: src_card2_thumb
      name: Card Thumb 2
      position:
        x: 18
        y: 18
    shape:
      kind: rectangle
    layout:
      sizing:
        width:
          type: fixed
          value: 58
        height:
          type: fixed
          value: 42
    style:
      radius: 8
      fills:
        - color: "#EAF2FA"
      strokes:
        - color: "#E3EAF2"
          weight: 1
          position: inside

    #### Text: Card Name 2

    node:
      type: text
      id: src_card2_name
      name: Card Name 2
      position:
        x: 92
        y: 17
    text:
      key: app.screen.telemetry.name
      defaultText: Telemetry
      typography:
        fontFamily: Inter
        fontSize: 15
        fontWeight: 600
      resizing:
        width: hug
        height: hug
    style:
      fills:
        - token: color.text

    #### Text: Card Dims 2

    node:
      type: text
      id: src_card2_dims
      name: Card Dims 2
      position:
        x: 92
        y: 44
    text:
      key: app.screen.telemetry.dims
      defaultText: 1440 x 1024
      typography:
        fontFamily: Inter
        fontSize: 12
        fontWeight: 400
      resizing:
        width: hug
        height: hug
    style:
      fills:
        - token: color.muted

    #### Shape: Card Status 2

    node:
      type: shape
      id: src_card2_dot
      name: Card Status 2
      position:
        x: 350
        y: 33
    shape:
      kind: ellipse
    layout:
      sizing:
        width:
          type: fixed
          value: 12
        height:
          type: fixed
          value: 12
    style:
      fills:
        - color: "#FFB800"

    ### Frame: Screen Card 3

    node:
      type: frame
      id: src_card3
      name: Screen Card 3
      position:
        x: 16
        y: 676
    layout:
      mode: none
      sizing:
        width:
          type: fixed
          value: 384
        height:
          type: fixed
          value: 78
    style:
      radius: 14
      fills:
        - color: "#FDFEFF"
      strokes:
        - color: "#E3EAF2"
          weight: 1
          position: inside

    #### Shape: Card Thumb 3

    node:
      type: shape
      id: src_card3_thumb
      name: Card Thumb 3
      position:
        x: 18
        y: 18
    shape:
      kind: rectangle
    layout:
      sizing:
        width:
          type: fixed
          value: 58
        height:
          type: fixed
          value: 42
    style:
      radius: 8
      fills:
        - color: "#EAF2FA"
      strokes:
        - color: "#E3EAF2"
          weight: 1
          position: inside

    #### Text: Card Name 3

    node:
      type: text
      id: src_card3_name
      name: Card Name 3
      position:
        x: 92
        y: 17
    text:
      key: app.screen.eventlog.name
      defaultText: Event Log
      typography:
        fontFamily: Inter
        fontSize: 15
        fontWeight: 600
      resizing:
        width: hug
        height: hug
    style:
      fills:
        - token: color.text

    #### Text: Card Dims 3

    node:
      type: text
      id: src_card3_dims
      name: Card Dims 3
      position:
        x: 92
        y: 44
    text:
      key: app.screen.eventlog.dims
      defaultText: 1440 x 1024
      typography:
        fontFamily: Inter
        fontSize: 12
        fontWeight: 400
      resizing:
        width: hug
        height: hug
    style:
      fills:
        - token: color.muted

    #### Shape: Card Status 3

    node:
      type: shape
      id: src_card3_dot
      name: Card Status 3
      position:
        x: 350
        y: 33
    shape:
      kind: ellipse
    layout:
      sizing:
        width:
          type: fixed
          value: 12
        height:
          type: fixed
          value: 12
    style:
      fills:
        - color: "#FF1D1D"

    ### Frame: Screen Card 4

    node:
      type: frame
      id: src_card4
      name: Screen Card 4
      position:
        x: 16
        y: 764
    layout:
      mode: none
      sizing:
        width:
          type: fixed
          value: 384
        height:
          type: fixed
          value: 78
    style:
      radius: 14
      fills:
        - color: "#FDFEFF"
      strokes:
        - color: "#E3EAF2"
          weight: 1
          position: inside

    #### Shape: Card Thumb 4

    node:
      type: shape
      id: src_card4_thumb
      name: Card Thumb 4
      position:
        x: 18
        y: 18
    shape:
      kind: rectangle
    layout:
      sizing:
        width:
          type: fixed
          value: 58
        height:
          type: fixed
          value: 42
    style:
      radius: 8
      fills:
        - color: "#EAF2FA"
      strokes:
        - color: "#E3EAF2"
          weight: 1
          position: inside

    #### Text: Card Name 4

    node:
      type: text
      id: src_card4_name
      name: Card Name 4
      position:
        x: 92
        y: 17
    text:
      key: app.screen.shapes.name
      defaultText: Shapes Showcase
      typography:
        fontFamily: Inter
        fontSize: 15
        fontWeight: 600
      resizing:
        width: hug
        height: hug
    style:
      fills:
        - token: color.text

    #### Text: Card Dims 4

    node:
      type: text
      id: src_card4_dims
      name: Card Dims 4
      position:
        x: 92
        y: 44
    text:
      key: app.screen.shapes.dims
      defaultText: 1440 x 1024
      typography:
        fontFamily: Inter
        fontSize: 12
        fontWeight: 400
      resizing:
        width: hug
        height: hug
    style:
      fills:
        - token: color.muted

    #### Shape: Card Status 4

    node:
      type: shape
      id: src_card4_dot
      name: Card Status 4
      position:
        x: 350
        y: 33
    shape:
      kind: ellipse
    layout:
      sizing:
        width:
          type: fixed
          value: 12
        height:
          type: fixed
          value: 12
    style:
      fills:
        - color: "#17C46B"

    ## Frame: Canvas

    node:
      type: frame
      id: cv_panel
      name: Canvas
      position:
        x: 480
        y: 40
    layout:
      mode: none
      clipContent: true
      sizing:
        width:
          type: fixed
          value: 596
        height:
          type: fixed
          value: 944
    style:
      radius: 16
      fills:
        - color: "#FBFDFF"
      strokes:
        - color: "#E3EAF2"
          weight: 1
          position: inside

    ### Text: Canvas Title

    node:
      type: text
      id: cv_title
      name: Canvas Title
      position:
        x: 24
        y: 26
    text:
      key: app.canvas.title
      defaultText: Canvas — Mission Overview
      typography:
        fontFamily: Inter
        fontSize: 20
        fontWeight: 700
      resizing:
        width: hug
        height: hug
    style:
      fills:
        - token: color.text

    ### Frame: Tool 1

    node:
      type: frame
      id: cv_tool1
      name: Tool 1
      position:
        x: 452
        y: 20
    layout:
      mode: none
      sizing:
        width:
          type: fixed
          value: 40
        height:
          type: fixed
          value: 40
    style:
      radius: 10
      fills:
        - color: "#F6FAFE"
      strokes:
        - color: "#E3EAF2"
          weight: 1
          position: inside

    #### Text: Tool Glyph 1

    node:
      type: text
      id: cv_tool1_g
      name: Tool Glyph 1
      position:
        x: 0
        y: 0
    text:
      key: app.canvas.tool.code
      defaultText: "</>"
      typography:
        fontFamily: Inter
        fontSize: 15
        fontWeight: 600
        horizontalAlign: center
        verticalAlign: center
      resizing:
        width: fixed
        height: fixed
    layout:
      sizing:
        width:
          type: fixed
          value: 40
        height:
          type: fixed
          value: 40
    style:
      fills:
        - token: color.muted

    ### Frame: Tool 2

    node:
      type: frame
      id: cv_tool2
      name: Tool 2
      position:
        x: 500
        y: 20
    layout:
      mode: none
      sizing:
        width:
          type: fixed
          value: 40
        height:
          type: fixed
          value: 40
    style:
      radius: 10
      fills:
        - color: "#F6FAFE"
      strokes:
        - color: "#E3EAF2"
          weight: 1
          position: inside

    #### Text: Tool Glyph 2

    node:
      type: text
      id: cv_tool2_g
      name: Tool Glyph 2
      position:
        x: 0
        y: 0
    text:
      key: app.canvas.tool.tune
      defaultText: "≡"
      typography:
        fontFamily: Inter
        fontSize: 17
        fontWeight: 600
        horizontalAlign: center
        verticalAlign: center
      resizing:
        width: fixed
        height: fixed
    layout:
      sizing:
        width:
          type: fixed
          value: 40
        height:
          type: fixed
          value: 40
    style:
      fills:
        - token: color.muted

    ### Frame: Tool 3

    node:
      type: frame
      id: cv_tool3
      name: Tool 3
      position:
        x: 548
        y: 20
    layout:
      mode: none
      sizing:
        width:
          type: fixed
          value: 40
        height:
          type: fixed
          value: 40
    style:
      radius: 10
      fills:
        - color: "#F6FAFE"
      strokes:
        - color: "#E3EAF2"
          weight: 1
          position: inside

    #### Text: Tool Glyph 3

    node:
      type: text
      id: cv_tool3_g
      name: Tool Glyph 3
      position:
        x: 0
        y: 0
    text:
      key: app.canvas.tool.fit
      defaultText: "⤢"
      typography:
        fontFamily: Inter
        fontSize: 16
        fontWeight: 600
        horizontalAlign: center
        verticalAlign: center
      resizing:
        width: fixed
        height: fixed
    layout:
      sizing:
        width:
          type: fixed
          value: 40
        height:
          type: fixed
          value: 40
    style:
      fills:
        - token: color.muted

    ### Shape: Canvas Surface

    node:
      type: shape
      id: cv_surface
      name: Canvas Surface
      position:
        x: 20
        y: 76
    shape:
      kind: rectangle
    layout:
      sizing:
        width:
          type: fixed
          value: 556
        height:
          type: fixed
          value: 764
    style:
      radius: 16
      fills:
        - color: "#F4F8FC"
      strokes:
        - color: "#E3EAF2"
          weight: 1
          position: inside

    ### Frame: Device Toggle

    node:
      type: frame
      id: cv_dev
      name: Device Toggle
      position:
        x: 20
        y: 872
    layout:
      mode: none
      sizing:
        width:
          type: fixed
          value: 210
        height:
          type: fixed
          value: 52
    style:
      radius: 14
      fills:
        - color: "#F6FAFE"
      strokes:
        - color: "#E3EAF2"
          weight: 1
          position: inside

    #### Shape: PC Highlight

    node:
      type: shape
      id: cv_dev_hl
      name: PC Highlight
      position:
        x: 8
        y: 8
    shape:
      kind: rectangle
    layout:
      sizing:
        width:
          type: fixed
          value: 60
        height:
          type: fixed
          value: 36
    style:
      radius: 10
      fills:
        - color: "#DCEEFF"

    #### Text: Seg PC

    node:
      type: text
      id: cv_dev_pc
      name: Seg PC
      position:
        x: 6
        y: 0
    text:
      key: app.canvas.device.pc
      defaultText: PC
      typography:
        fontFamily: Inter
        fontSize: 14
        fontWeight: 700
        horizontalAlign: center
        verticalAlign: center
      resizing:
        width: fixed
        height: fixed
    layout:
      sizing:
        width:
          type: fixed
          value: 64
        height:
          type: fixed
          value: 52
    style:
      fills:
        - token: color.accent

    #### Text: Seg MOB

    node:
      type: text
      id: cv_dev_mob
      name: Seg MOB
      position:
        x: 74
        y: 0
    text:
      key: app.canvas.device.mob
      defaultText: MOB
      typography:
        fontFamily: Inter
        fontSize: 13
        fontWeight: 500
        horizontalAlign: center
        verticalAlign: center
      resizing:
        width: fixed
        height: fixed
    layout:
      sizing:
        width:
          type: fixed
          value: 64
        height:
          type: fixed
          value: 52
    style:
      fills:
        - token: color.muted

    #### Text: Seg TAB

    node:
      type: text
      id: cv_dev_tab
      name: Seg TAB
      position:
        x: 142
        y: 0
    text:
      key: app.canvas.device.tab
      defaultText: TAB
      typography:
        fontFamily: Inter
        fontSize: 13
        fontWeight: 500
        horizontalAlign: center
        verticalAlign: center
      resizing:
        width: fixed
        height: fixed
    layout:
      sizing:
        width:
          type: fixed
          value: 64
        height:
          type: fixed
          value: 52
    style:
      fills:
        - token: color.muted

    ### Frame: Mode Toggle

    node:
      type: frame
      id: cv_mode
      name: Mode Toggle
      position:
        x: 242
        y: 872
    layout:
      mode: none
      sizing:
        width:
          type: fixed
          value: 150
        height:
          type: fixed
          value: 52
    style:
      radius: 14
      fills:
        - color: "#F6FAFE"
      strokes:
        - color: "#E3EAF2"
          weight: 1
          position: inside

    #### Shape: Canvas Highlight

    node:
      type: shape
      id: cv_mode_hl
      name: Canvas Highlight
      position:
        x: 6
        y: 6
    shape:
      kind: rectangle
    layout:
      sizing:
        width:
          type: fixed
          value: 78
        height:
          type: fixed
          value: 40
    style:
      radius: 10
      fills:
        - color: "#1E88FF"

    #### Text: Seg Canvas

    node:
      type: text
      id: cv_mode_canvas
      name: Seg Canvas
      position:
        x: 6
        y: 0
    text:
      key: app.canvas.mode.canvas
      defaultText: Canvas
      typography:
        fontFamily: Inter
        fontSize: 14
        fontWeight: 600
        horizontalAlign: center
        verticalAlign: center
      resizing:
        width: fixed
        height: fixed
    layout:
      sizing:
        width:
          type: fixed
          value: 78
        height:
          type: fixed
          value: 52
    style:
      fills:
        - color: "#FFFFFF"

    #### Text: Seg Scene

    node:
      type: text
      id: cv_mode_scene
      name: Seg Scene
      position:
        x: 86
        y: 0
    text:
      key: app.canvas.mode.scene
      defaultText: Scene
      typography:
        fontFamily: Inter
        fontSize: 14
        fontWeight: 500
        horizontalAlign: center
        verticalAlign: center
      resizing:
        width: fixed
        height: fixed
    layout:
      sizing:
        width:
          type: fixed
          value: 60
        height:
          type: fixed
          value: 52
    style:
      fills:
        - token: color.muted

    ### Frame: Zoom Control

    node:
      type: frame
      id: cv_zoom
      name: Zoom Control
      position:
        x: 404
        y: 872
    layout:
      mode: none
      sizing:
        width:
          type: fixed
          value: 172
        height:
          type: fixed
          value: 52
    style:
      radius: 14
      fills:
        - color: "#F6FAFE"
      strokes:
        - color: "#E3EAF2"
          weight: 1
          position: inside

    #### Text: Zoom Minus

    node:
      type: text
      id: cv_zoom_minus
      name: Zoom Minus
      position:
        x: 4
        y: 0
    text:
      key: app.canvas.zoom.minus
      defaultText: −
      typography:
        fontFamily: Inter
        fontSize: 20
        fontWeight: 600
        horizontalAlign: center
        verticalAlign: center
      resizing:
        width: fixed
        height: fixed
    layout:
      sizing:
        width:
          type: fixed
          value: 44
        height:
          type: fixed
          value: 52
    style:
      fills:
        - token: color.text

    #### Text: Zoom Value

    node:
      type: text
      id: cv_zoom_val
      name: Zoom Value
      position:
        x: 50
        y: 0
    text:
      key: app.canvas.zoom.value
      defaultText: 37%
      typography:
        fontFamily: Inter
        fontSize: 14
        fontWeight: 500
        horizontalAlign: center
        verticalAlign: center
      resizing:
        width: fixed
        height: fixed
    layout:
      sizing:
        width:
          type: fixed
          value: 74
        height:
          type: fixed
          value: 52
    style:
      fills:
        - token: color.text

    #### Text: Zoom Plus

    node:
      type: text
      id: cv_zoom_plus
      name: Zoom Plus
      position:
        x: 124
        y: 0
    text:
      key: app.canvas.zoom.plus
      defaultText: +
      typography:
        fontFamily: Inter
        fontSize: 20
        fontWeight: 600
        horizontalAlign: center
        verticalAlign: center
      resizing:
        width: fixed
        height: fixed
    layout:
      sizing:
        width:
          type: fixed
          value: 44
        height:
          type: fixed
          value: 52
    style:
      fills:
        - token: color.text

    ## Frame: Inspector

    node:
      type: frame
      id: in_panel
      name: Inspector
      position:
        x: 1092
        y: 40
    layout:
      mode: none
      clipContent: true
      sizing:
        width:
          type: fixed
          value: 300
        height:
          type: fixed
          value: 944
    style:
      radius: 16
      fills:
        - color: "#FBFDFF"
      strokes:
        - color: "#E3EAF2"
          weight: 1
          position: inside

    ### Text: Inspector Title

    node:
      type: text
      id: in_title
      name: Inspector Title
      position:
        x: 20
        y: 24
    text:
      key: app.inspector.title
      defaultText: Inspector
      typography:
        fontFamily: Inter
        fontSize: 22
        fontWeight: 700
      resizing:
        width: hug
        height: hug
    style:
      fills:
        - token: color.text

    ### Text: Tab Design

    node:
      type: text
      id: in_tab_design
      name: Tab Design
      position:
        x: 20
        y: 68
    text:
      key: app.inspector.tab.design
      defaultText: Design
      typography:
        fontFamily: Inter
        fontSize: 14
        fontWeight: 700
      resizing:
        width: hug
        height: hug
    style:
      fills:
        - token: color.accent

    ### Text: Tab Prototype

    node:
      type: text
      id: in_tab_proto
      name: Tab Prototype
      position:
        x: 96
        y: 68
    text:
      key: app.inspector.tab.prototype
      defaultText: Prototype
      typography:
        fontFamily: Inter
        fontSize: 14
        fontWeight: 500
      resizing:
        width: hug
        height: hug
    style:
      fills:
        - token: color.muted

    ### Text: Tab Comments

    node:
      type: text
      id: in_tab_comments
      name: Tab Comments
      position:
        x: 192
        y: 68
    text:
      key: app.inspector.tab.comments
      defaultText: Comments
      typography:
        fontFamily: Inter
        fontSize: 14
        fontWeight: 500
      resizing:
        width: hug
        height: hug
    style:
      fills:
        - token: color.muted

    ### Shape: Inspector Tab Underline

    node:
      type: shape
      id: in_tab_ul
      name: Inspector Tab Underline
      position:
        x: 18
        y: 92
    shape:
      kind: rectangle
    layout:
      sizing:
        width:
          type: fixed
          value: 54
        height:
          type: fixed
          value: 3
    style:
      radius: 2
      fills:
        - token: color.accent

    ### Shape: Header Divider

    node:
      type: shape
      id: in_div1
      name: Header Divider
      position:
        x: 18
        y: 106
    shape:
      kind: rectangle
    layout:
      sizing:
        width:
          type: fixed
          value: 262
        height:
          type: fixed
          value: 1
    style:
      fills:
        - token: color.line

    ### Text: Node Name

    node:
      type: text
      id: in_node
      name: Node Name
      position:
        x: 20
        y: 122
    text:
      key: app.inspector.node
      defaultText: Mission Overview
      typography:
        fontFamily: Inter
        fontSize: 16
        fontWeight: 700
      resizing:
        width: hug
        height: hug
    style:
      fills:
        - token: color.text

    ### Frame: Dup Button

    node:
      type: frame
      id: in_dup
      name: Dup Button
      position:
        x: 214
        y: 116
    layout:
      mode: none
      sizing:
        width:
          type: fixed
          value: 30
        height:
          type: fixed
          value: 30
    style:
      radius: 8
      fills:
        - color: "#F6FAFE"
      strokes:
        - color: "#E3EAF2"
          weight: 1
          position: inside

    #### Shape: Dup Icon

    node:
      type: shape
      id: in_dup_icon
      name: Dup Icon
      position:
        x: 9
        y: 9
    shape:
      kind: rectangle
    layout:
      sizing:
        width:
          type: fixed
          value: 12
        height:
          type: fixed
          value: 12
    style:
      radius: 3
      strokes:
        - token: color.muted
          weight: 1
          position: inside

    ### Frame: Del Button

    node:
      type: frame
      id: in_del
      name: Del Button
      position:
        x: 250
        y: 116
    layout:
      mode: none
      sizing:
        width:
          type: fixed
          value: 30
        height:
          type: fixed
          value: 30
    style:
      radius: 8
      fills:
        - color: "#F6FAFE"
      strokes:
        - color: "#E3EAF2"
          weight: 1
          position: inside

    #### Shape: Del Icon

    node:
      type: shape
      id: in_del_icon
      name: Del Icon
      position:
        x: 9
        y: 9
    shape:
      kind: rectangle
    layout:
      sizing:
        width:
          type: fixed
          value: 12
        height:
          type: fixed
          value: 12
    style:
      radius: 3
      strokes:
        - token: color.muted
          weight: 1
          position: inside

    ### Text: Position Heading

    node:
      type: text
      id: in_pos_h
      name: Position Heading
      position:
        x: 20
        y: 168
    text:
      key: app.inspector.position
      defaultText: Position
      typography:
        fontFamily: Inter
        fontSize: 15
        fontWeight: 700
      resizing:
        width: hug
        height: hug
    style:
      fills:
        - token: color.text

    ### Text: Position Label

    node:
      type: text
      id: in_pos_lbl
      name: Position Label
      position:
        x: 20
        y: 200
    text:
      key: app.inspector.position.label
      defaultText: Position
      typography:
        fontFamily: Inter
        fontSize: 12
        fontWeight: 500
      resizing:
        width: hug
        height: hug
    style:
      fills:
        - token: color.muted

    ### Frame: X Field

    node:
      type: frame
      id: in_x
      name: X Field
      position:
        x: 20
        y: 220
    layout:
      mode: none
      sizing:
        width:
          type: fixed
          value: 126
        height:
          type: fixed
          value: 36
    style:
      radius: 8
      fills:
        - color: "#F6FAFE"
      strokes:
        - color: "#E3EAF2"
          weight: 1
          position: inside

    #### Text: X Value

    node:
      type: text
      id: in_x_val
      name: X Value
      position:
        x: 12
        y: 10
    text:
      key: app.inspector.x
      defaultText: "X      72"
      typography:
        fontFamily: Inter
        fontSize: 13
        fontWeight: 500
      resizing:
        width: hug
        height: hug
    style:
      fills:
        - token: color.text

    ### Frame: Y Field

    node:
      type: frame
      id: in_y
      name: Y Field
      position:
        x: 154
        y: 220
    layout:
      mode: none
      sizing:
        width:
          type: fixed
          value: 126
        height:
          type: fixed
          value: 36
    style:
      radius: 8
      fills:
        - color: "#F6FAFE"
      strokes:
        - color: "#E3EAF2"
          weight: 1
          position: inside

    #### Text: Y Value

    node:
      type: text
      id: in_y_val
      name: Y Value
      position:
        x: 12
        y: 10
    text:
      key: app.inspector.y
      defaultText: "Y      72"
      typography:
        fontFamily: Inter
        fontSize: 13
        fontWeight: 500
      resizing:
        width: hug
        height: hug
    style:
      fills:
        - token: color.text

    ### Text: Constraints Label

    node:
      type: text
      id: in_con_lbl
      name: Constraints Label
      position:
        x: 20
        y: 268
    text:
      key: app.inspector.constraints
      defaultText: Constraints
      typography:
        fontFamily: Inter
        fontSize: 12
        fontWeight: 500
      resizing:
        width: hug
        height: hug
    style:
      fills:
        - token: color.muted

    ### Frame: Constraint H

    node:
      type: frame
      id: in_conh
      name: Constraint H
      position:
        x: 20
        y: 288
    layout:
      mode: none
      sizing:
        width:
          type: fixed
          value: 160
        height:
          type: fixed
          value: 36
    style:
      radius: 8
      fills:
        - color: "#F6FAFE"
      strokes:
        - color: "#E3EAF2"
          weight: 1
          position: inside

    #### Text: Constraint H Value

    node:
      type: text
      id: in_conh_val
      name: Constraint H Value
      position:
        x: 12
        y: 10
    text:
      key: app.inspector.constraint.h
      defaultText: "↔   Left"
      typography:
        fontFamily: Inter
        fontSize: 13
        fontWeight: 500
      resizing:
        width: hug
        height: hug
    style:
      fills:
        - token: color.text

    ### Frame: Constraint V

    node:
      type: frame
      id: in_conv
      name: Constraint V
      position:
        x: 20
        y: 330
    layout:
      mode: none
      sizing:
        width:
          type: fixed
          value: 160
        height:
          type: fixed
          value: 36
    style:
      radius: 8
      fills:
        - color: "#F6FAFE"
      strokes:
        - color: "#E3EAF2"
          weight: 1
          position: inside

    #### Text: Constraint V Value

    node:
      type: text
      id: in_conv_val
      name: Constraint V Value
      position:
        x: 12
        y: 10
    text:
      key: app.inspector.constraint.v
      defaultText: "↕   Top"
      typography:
        fontFamily: Inter
        fontSize: 13
        fontWeight: 500
      resizing:
        width: hug
        height: hug
    style:
      fills:
        - token: color.text

    ### Frame: Constraint Diagram

    node:
      type: frame
      id: in_condiag
      name: Constraint Diagram
      position:
        x: 190
        y: 288
    layout:
      mode: none
      sizing:
        width:
          type: fixed
          value: 90
        height:
          type: fixed
          value: 78
    style:
      radius: 10
      fills:
        - color: "#F6FAFE"
      strokes:
        - color: "#E3EAF2"
          weight: 1
          position: inside

    #### Shape: Constraint Bar V

    node:
      type: shape
      id: in_condiag_v
      name: Constraint Bar V
      position:
        x: 44
        y: 14
    shape:
      kind: rectangle
    layout:
      sizing:
        width:
          type: fixed
          value: 2
        height:
          type: fixed
          value: 50
    style:
      fills:
        - color: "#D8DFE8"

    #### Shape: Constraint Bar H

    node:
      type: shape
      id: in_condiag_h
      name: Constraint Bar H
      position:
        x: 18
        y: 38
    shape:
      kind: rectangle
    layout:
      sizing:
        width:
          type: fixed
          value: 54
        height:
          type: fixed
          value: 2
    style:
      fills:
        - token: color.accent

    ### Shape: Section Divider

    node:
      type: shape
      id: in_div2
      name: Section Divider
      position:
        x: 18
        y: 400
    shape:
      kind: rectangle
    layout:
      sizing:
        width:
          type: fixed
          value: 262
        height:
          type: fixed
          value: 1
    style:
      fills:
        - token: color.line

    ### Text: Layout Heading

    node:
      type: text
      id: in_lay_h
      name: Layout Heading
      position:
        x: 20
        y: 418
    text:
      key: app.inspector.layout
      defaultText: Layout
      typography:
        fontFamily: Inter
        fontSize: 15
        fontWeight: 700
      resizing:
        width: hug
        height: hug
    style:
      fills:
        - token: color.text

    ### Text: Dimensions Label

    node:
      type: text
      id: in_dim_lbl
      name: Dimensions Label
      position:
        x: 20
        y: 450
    text:
      key: app.inspector.dimensions
      defaultText: Dimensions
      typography:
        fontFamily: Inter
        fontSize: 12
        fontWeight: 500
      resizing:
        width: hug
        height: hug
    style:
      fills:
        - token: color.muted

    ### Frame: W Field

    node:
      type: frame
      id: in_w
      name: W Field
      position:
        x: 20
        y: 470
    layout:
      mode: none
      sizing:
        width:
          type: fixed
          value: 126
        height:
          type: fixed
          value: 36
    style:
      radius: 8
      fills:
        - color: "#F6FAFE"
      strokes:
        - color: "#E3EAF2"
          weight: 1
          position: inside

    #### Text: W Value

    node:
      type: text
      id: in_w_val
      name: W Value
      position:
        x: 12
        y: 10
    text:
      key: app.inspector.w
      defaultText: "W   1440"
      typography:
        fontFamily: Inter
        fontSize: 13
        fontWeight: 500
      resizing:
        width: hug
        height: hug
    style:
      fills:
        - token: color.text

    ### Frame: H Field

    node:
      type: frame
      id: in_h
      name: H Field
      position:
        x: 154
        y: 470
    layout:
      mode: none
      sizing:
        width:
          type: fixed
          value: 126
        height:
          type: fixed
          value: 36
    style:
      radius: 8
      fills:
        - color: "#F6FAFE"
      strokes:
        - color: "#E3EAF2"
          weight: 1
          position: inside

    #### Text: H Value

    node:
      type: text
      id: in_h_val
      name: H Value
      position:
        x: 12
        y: 10
    text:
      key: app.inspector.h
      defaultText: "H   1024"
      typography:
        fontFamily: Inter
        fontSize: 13
        fontWeight: 500
      resizing:
        width: hug
        height: hug
    style:
      fills:
        - token: color.text

    ### Text: Resizing Label

    node:
      type: text
      id: in_res_lbl
      name: Resizing Label
      position:
        x: 20
        y: 518
    text:
      key: app.inspector.resizing
      defaultText: Resizing
      typography:
        fontFamily: Inter
        fontSize: 12
        fontWeight: 500
      resizing:
        width: hug
        height: hug
    style:
      fills:
        - token: color.muted

    ### Frame: Resize W

    node:
      type: frame
      id: in_rw
      name: Resize W
      position:
        x: 20
        y: 538
    layout:
      mode: none
      sizing:
        width:
          type: fixed
          value: 126
        height:
          type: fixed
          value: 36
    style:
      radius: 8
      fills:
        - color: "#F6FAFE"
      strokes:
        - color: "#E3EAF2"
          weight: 1
          position: inside

    #### Text: Resize W Value

    node:
      type: text
      id: in_rw_val
      name: Resize W Value
      position:
        x: 12
        y: 10
    text:
      key: app.inspector.resize.w
      defaultText: "Fixed        ⌄"
      typography:
        fontFamily: Inter
        fontSize: 13
        fontWeight: 500
      resizing:
        width: hug
        height: hug
    style:
      fills:
        - token: color.text

    ### Frame: Resize H

    node:
      type: frame
      id: in_rh
      name: Resize H
      position:
        x: 154
        y: 538
    layout:
      mode: none
      sizing:
        width:
          type: fixed
          value: 126
        height:
          type: fixed
          value: 36
    style:
      radius: 8
      fills:
        - color: "#F6FAFE"
      strokes:
        - color: "#E3EAF2"
          weight: 1
          position: inside

    #### Text: Resize H Value

    node:
      type: text
      id: in_rh_val
      name: Resize H Value
      position:
        x: 12
        y: 10
    text:
      key: app.inspector.resize.h
      defaultText: "Fixed        ⌄"
      typography:
        fontFamily: Inter
        fontSize: 13
        fontWeight: 500
      resizing:
        width: hug
        height: hug
    style:
      fills:
        - token: color.text

    ### Text: Auto Layout Label

    node:
      type: text
      id: in_al_lbl
      name: Auto Layout Label
      position:
        x: 20
        y: 586
    text:
      key: app.inspector.autolayout
      defaultText: Auto layout
      typography:
        fontFamily: Inter
        fontSize: 12
        fontWeight: 500
      resizing:
        width: hug
        height: hug
    style:
      fills:
        - token: color.muted

    ### Frame: Auto Layout Toggle

    node:
      type: frame
      id: in_al
      name: Auto Layout Toggle
      position:
        x: 20
        y: 606
    layout:
      mode: none
      sizing:
        width:
          type: fixed
          value: 260
        height:
          type: fixed
          value: 40
    style:
      radius: 10
      fills:
        - color: "#F4F8FC"
      strokes:
        - color: "#E3EAF2"
          weight: 1
          position: inside

    #### Shape: Free Highlight

    node:
      type: shape
      id: in_al_hl
      name: Free Highlight
      position:
        x: 4
        y: 4
    shape:
      kind: rectangle
    layout:
      sizing:
        width:
          type: fixed
          value: 61
        height:
          type: fixed
          value: 32
    style:
      radius: 8
      fills:
        - color: "#FFFFFF"
      strokes:
        - color: "#B9D9FF"
          weight: 1
          position: inside

    #### Text: Seg Free

    node:
      type: text
      id: in_al_free
      name: Seg Free
      position:
        x: 4
        y: 0
    text:
      key: app.inspector.autolayout.free
      defaultText: Free
      typography:
        fontFamily: Inter
        fontSize: 13
        fontWeight: 700
        horizontalAlign: center
        verticalAlign: center
      resizing:
        width: fixed
        height: fixed
    layout:
      sizing:
        width:
          type: fixed
          value: 61
        height:
          type: fixed
          value: 40
    style:
      fills:
        - token: color.accent

    #### Text: Seg Vert

    node:
      type: text
      id: in_al_vert
      name: Seg Vert
      position:
        x: 67
        y: 0
    text:
      key: app.inspector.autolayout.vert
      defaultText: Vert
      typography:
        fontFamily: Inter
        fontSize: 13
        fontWeight: 500
        horizontalAlign: center
        verticalAlign: center
      resizing:
        width: fixed
        height: fixed
    layout:
      sizing:
        width:
          type: fixed
          value: 61
        height:
          type: fixed
          value: 40
    style:
      fills:
        - token: color.muted

    #### Text: Seg Hori

    node:
      type: text
      id: in_al_hori
      name: Seg Hori
      position:
        x: 130
        y: 0
    text:
      key: app.inspector.autolayout.hori
      defaultText: Hori
      typography:
        fontFamily: Inter
        fontSize: 13
        fontWeight: 500
        horizontalAlign: center
        verticalAlign: center
      resizing:
        width: fixed
        height: fixed
    layout:
      sizing:
        width:
          type: fixed
          value: 61
        height:
          type: fixed
          value: 40
    style:
      fills:
        - token: color.muted

    #### Text: Seg Grid

    node:
      type: text
      id: in_al_grid
      name: Seg Grid
      position:
        x: 193
        y: 0
    text:
      key: app.inspector.autolayout.grid
      defaultText: Grid
      typography:
        fontFamily: Inter
        fontSize: 13
        fontWeight: 500
        horizontalAlign: center
        verticalAlign: center
      resizing:
        width: fixed
        height: fixed
    layout:
      sizing:
        width:
          type: fixed
          value: 61
        height:
          type: fixed
          value: 40
    style:
      fills:
        - token: color.muted

    ### Text: Gap Label

    node:
      type: text
      id: in_gap_lbl
      name: Gap Label
      position:
        x: 20
        y: 668
    text:
      key: app.inspector.gap
      defaultText: Gap
      typography:
        fontFamily: Inter
        fontSize: 12
        fontWeight: 500
      resizing:
        width: hug
        height: hug
    style:
      fills:
        - token: color.muted

    ### Frame: Gap Field

    node:
      type: frame
      id: in_gap
      name: Gap Field
      position:
        x: 70
        y: 662
    layout:
      mode: none
      sizing:
        width:
          type: fixed
          value: 210
        height:
          type: fixed
          value: 36
    style:
      radius: 8
      fills:
        - color: "#F6FAFE"
      strokes:
        - color: "#E3EAF2"
          weight: 1
          position: inside

    #### Text: Gap Value

    node:
      type: text
      id: in_gap_val
      name: Gap Value
      position:
        x: 12
        y: 10
    text:
      key: app.inspector.gap.value
      defaultText: "0"
      typography:
        fontFamily: Inter
        fontSize: 13
        fontWeight: 500
      resizing:
        width: hug
        height: hug
    style:
      fills:
        - token: color.text

    ### Text: Padding Label

    node:
      type: text
      id: in_pad_lbl
      name: Padding Label
      position:
        x: 20
        y: 716
    text:
      key: app.inspector.padding
      defaultText: Padding
      typography:
        fontFamily: Inter
        fontSize: 12
        fontWeight: 500
      resizing:
        width: hug
        height: hug
    style:
      fills:
        - token: color.muted

    ### Frame: Padding Field

    node:
      type: frame
      id: in_pad
      name: Padding Field
      position:
        x: 20
        y: 736
    layout:
      mode: none
      sizing:
        width:
          type: fixed
          value: 260
        height:
          type: fixed
          value: 36
    style:
      radius: 8
      fills:
        - color: "#F6FAFE"
      strokes:
        - color: "#E3EAF2"
          weight: 1
          position: inside

    #### Text: Padding Value

    node:
      type: text
      id: in_pad_val
      name: Padding Value
      position:
        x: 12
        y: 10
    text:
      key: app.inspector.padding.value
      defaultText: "0          0          0          0"
      typography:
        fontFamily: Inter
        fontSize: 13
        fontWeight: 500
      resizing:
        width: hug
        height: hug
    style:
      fills:
        - token: color.muted
    """,
)
