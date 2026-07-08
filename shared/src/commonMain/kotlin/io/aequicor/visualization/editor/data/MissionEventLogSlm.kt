package io.aequicor.visualization.editor.data

/**
 * Event Log page as Semantic Layout Markdown: a header placeholder, six log-row
 * instances (two with id-path dot-fill overrides), and a footer that flips the
 * whole theme collection to `dark` for its subtree.
 *
 * `ir` fences cover the IR features typed blocks cannot spell yet:
 * - `characters: {"$prop": ...}` bindings inside the Log Row component,
 * - instance overrides that patch fills at an id path (`row_3`, `row_5`),
 * - `variableModes` on the footer frame.
 * The old JSON's bottom-only row border (`strokes.weightPerSide`) is reproduced
 * as an absolutely anchored 1px divider rectangle inside the component: the SLM
 * style block has no per-side stroke weights.
 */
val MissionEventLogSlm: String = missionSlm(
    """
    ---
    screen: missionEventLog
    page: Event Log
    sourceLocale: en-US
    targetLocales:
      - en-US
      - ru-RU
    theme: light
    frame:
      width: 1440
      height: 1024
    ---

    # Event Log

    node:
      id: frame_eventlog
      name: Event Log
      position:
        x: 72
        y: 72
      constraints:
        horizontal: left
        vertical: top
    layout:
      mode: column
      gap: 32
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

    ```ir
    { "id": "eventlog_footer", "order": 30, "type": "frame", "name": "Footer",
      "variableModes": { "theme": "dark" },
      "sizing": { "horizontal": "fill", "vertical": "hug" },
      "layout": { "mode": "horizontal", "gap": "auto", "alignItems": "center",
                  "padding": { "top": 16, "right": 20, "bottom": 16, "left": 20 } },
      "fills": [ { "type": "solid", "color": { "§var": "color.surface" } } ],
      "cornerRadius": { "§var": "radius" },
      "children": [
        { "id": "footer_label", "type": "text", "name": "Summary",
          "characters": "6 events captured in the last orbit",
          "content": { "defaultLocale": "en-US", "defaultText": "6 events captured in the last orbit" },
          "textStyle": { "fontFamily": "Inter", "fontSize": 14, "fontWeight": 500,
                         "lineHeight": { "unit": "percent", "value": 143 } },
          "fills": [ { "type": "solid", "color": { "§var": "color.text" } } ],
          "sizing": { "horizontal": "hug", "vertical": "hug" },
          "autoResize": "widthAndHeight" },
        { "id": "footer_time", "type": "text", "name": "Updated",
          "characters": "updated 12:04",
          "content": { "defaultLocale": "en-US", "defaultText": "updated 12:04" },
          "textStyle": { "fontFamily": "Inter", "fontSize": 12, "fontWeight": 400,
                         "lineHeight": { "unit": "percent", "value": 133 } },
          "fills": [ { "type": "solid", "color": { "§var": "color.muted" } } ],
          "sizing": { "horizontal": "hug", "vertical": "hug" },
          "autoResize": "widthAndHeight" }
      ] }
    ```

    ## Shape: Header

    node:
      type: shape
      id: eventlog_header
      name: Header
      order: 10
    shape:
      kind: rectangle
    layout:
      sizing:
        width:
          type: fill
        height:
          type: fixed
          value: 120
    style:
      radius: §radius
      fills:
        - token: color.placeholder

    ## Frame: Rows

    node:
      type: frame
      id: eventlog_list
      name: Rows
      order: 20
    layout:
      mode: column
      gap: 0
      align:
        inline: stretch
      clipContent: true
      sizing:
        width:
          type: fill
        height:
          type: hug
    style:
      radius: §radius
      strokes:
        - token: color.line
          weight: 1
          position: inside

    ```ir
    { "id": "row_3", "order": 30, "type": "instance", "componentId": "cmp_log_row",
      "props": { "label": "Thruster calibration drift", "time": "11:12" },
      "overrides": [
        { "target": ["log_dot"],
          "set": { "fills": [ { "type": "solid", "color": "#FFB800" } ] } }
      ] }
    ```

    ```ir
    { "id": "row_5", "order": 50, "type": "instance", "componentId": "cmp_log_row",
      "props": { "label": "Power bus anomaly detected", "time": "10:31" },
      "overrides": [
        { "target": ["log_dot"],
          "set": { "fills": [ { "type": "solid", "color": "#FF1D1D" } ] } }
      ] }
    ```

    ### Instance: Row 1

    node:
      type: instance
      id: row_1
      name: Row 1
      order: 10
    component:
      ref: cmp_log_row
    props:
      label: Telemetry sync completed
      time: "12:04"

    ### Instance: Row 2

    node:
      type: instance
      id: row_2
      name: Row 2
      order: 20
    component:
      ref: cmp_log_row
    props:
      label: Trajectory checkpoint passed
      time: "11:47"

    ### Instance: Row 4

    node:
      type: instance
      id: row_4
      name: Row 4
      order: 40
    component:
      ref: cmp_log_row
    props:
      label: Signal lock re-established
      time: "10:58"

    ### Instance: Row 6

    node:
      type: instance
      id: row_6
      name: Row 6
      order: 60
    component:
      ref: cmp_log_row
    props:
      label: Uplink window opened
      time: "09:52"

    ## Component: Log Row

    node:
      type: component
      id: cmp_log_row
      name: Log Row
    component:
      name: Log Row
      properties:
        label:
          type: text
          default: Event
        time:
          type: text
          default: "00:00"
    layout:
      mode: row
      gap: auto
      align:
        block: center
      padding:
        blockStart: 0
        inlineEnd: 20
        blockEnd: 0
        inlineStart: 20
      sizing:
        width:
          type: fill
          value: 1328
        height:
          type: fixed
          value: 56
    style:
      fills:
        - token: color.surface

    ```ir
    { "id": "log_time", "order": 20, "type": "text", "name": "Time",
      "characters": { "§prop": "time" },
      "textStyle": { "fontFamily": "Inter", "fontSize": 12, "fontWeight": 400,
                     "lineHeight": { "unit": "percent", "value": 133 } },
      "fills": [ { "type": "solid", "color": { "§var": "color.muted" } } ],
      "sizing": { "horizontal": "hug", "vertical": "hug" },
      "autoResize": "widthAndHeight" }
    ```

    ```ir
    { "id": "log_row_divider", "order": 30, "type": "rectangle", "name": "Divider",
      "layoutChild": { "absolute": true },
      "anchors": { "inlineStart": 0, "inlineEnd": 0, "blockEnd": 0 },
      "sizing": { "horizontal": "fixed", "vertical": "fixed" },
      "size": { "width": 1328, "height": 1 },
      "fills": [ { "type": "solid", "color": { "§var": "color.line" } } ] }
    ```

    ### Frame: Left

    node:
      type: frame
      id: log_left
      name: Left
      order: 10
    layout:
      mode: row
      gap: 12
      align:
        block: center
      sizing:
        width:
          type: hug
        height:
          type: hug

    ```ir
    { "id": "log_label", "order": 20, "type": "text", "name": "Label",
      "characters": { "§prop": "label" },
      "textStyle": { "fontFamily": "Inter", "fontSize": 14, "fontWeight": 500,
                     "lineHeight": { "unit": "percent", "value": 143 } },
      "fills": [ { "type": "solid", "color": { "§var": "color.text" } } ],
      "sizing": { "horizontal": "hug", "vertical": "hug" },
      "autoResize": "widthAndHeight" }
    ```

    #### Shape: Status

    node:
      type: shape
      id: log_dot
      name: Status
      order: 10
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
    """,
)
