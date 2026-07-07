package io.aequicor.visualization.editor.data

/**
 * Bundled mission design document in the Figma-like JSON format: three pages
 * (Mission Overview / Telemetry / Event Log), auto-layout + grid, variables with a
 * dark mode, components with props, a variant set, and id-path instance overrides.
 */
val MissionDesignJson: String = """
{
  "schemaVersion": "1.0.0",
  "id": "doc_mission",
  "name": "Mission Visualization",

  "variables": {
    "collections": {
      "col_theme": {
        "name": "Theme",
        "modes": ["light", "dark"],
        "defaultMode": "light",
        "vars": {
          "var_surface":          { "type": "color",  "values": { "light": "#FFFFFF", "dark": "#111827" } },
          "var_text":             { "type": "color",  "values": { "light": "#172033", "dark": "#F9FAFB" } },
          "var_muted":            { "type": "color",  "values": { "light": "#5E6B7A", "dark": "#9CA3AF" } },
          "var_accent":           { "type": "color",  "values": { "light": "#1E88FF", "dark": "#60A5FA" } },
          "var_placeholder":      { "type": "color",  "values": { "light": "#E9EEF4", "dark": "#1F2937" } },
          "var_placeholder_deep": { "type": "color",  "values": { "light": "#D8DFE8", "dark": "#374151" } },
          "var_line":             { "type": "color",  "values": { "light": "#DCE3EC", "dark": "#334155" } },
          "var_stroke":           { "type": "color",  "values": { "light": { "${'$'}var": "var_accent" }, "dark": { "${'$'}var": "var_accent" } } },
          "var_radius":           { "type": "number", "values": { "light": 8, "dark": 8 } },
          "var_space":            { "type": "number", "values": { "light": 40, "dark": 40 } },
          "var_pad_h":            { "type": "number", "values": { "light": 56, "dark": 56 } },
          "var_pad_v":            { "type": "number", "values": { "light": 88, "dark": 88 } }
        }
      }
    }
  },

  "styles": {
    "sty_row":  { "type": "text", "value": { "fontFamily": "Inter", "fontSize": 14, "fontWeight": 500, "lineHeight": { "unit": "percent", "value": 143 } } },
    "sty_time": { "type": "text", "value": { "fontFamily": "Inter", "fontSize": 12, "fontWeight": 400, "lineHeight": { "unit": "percent", "value": 133 } } },
    "sty_fill_placeholder": { "type": "paint", "value": [ { "type": "solid", "color": { "${'$'}var": "var_placeholder" } } ] }
  },

  "components": {
    "cmp_wire_tile_default": {
      "name": "Wire Tile / Default",
      "properties": {},
      "root": {
        "id": "wire_tile_root", "type": "frame", "name": "Tile",
        "sizing": { "horizontal": "fill", "vertical": "fixed" },
        "size": { "width": 416, "height": 150 },
        "cornerRadius": { "${'$'}var": "var_radius" },
        "fillStyleId": "sty_fill_placeholder",
        "layout": { "mode": "none", "clipsContent": true }
      }
    },
    "cmp_wire_tile_highlight": {
      "name": "Wire Tile / Highlight",
      "properties": {},
      "root": {
        "id": "wire_tile_hl_root", "type": "frame", "name": "Tile Highlight",
        "sizing": { "horizontal": "fill", "vertical": "fixed" },
        "size": { "width": 416, "height": 150 },
        "cornerRadius": { "${'$'}var": "var_radius" },
        "fills": [
          { "type": "gradientLinear",
            "from": { "x": 0, "y": 0 }, "to": { "x": 1, "y": 1 },
            "stops": [
              { "position": 0, "color": "#DCEBFD" },
              { "position": 1, "color": "#E9EEF4" }
            ] }
        ],
        "layout": { "mode": "none", "clipsContent": true }
      }
    },
    "cmp_wire_card": {
      "name": "Wire Card",
      "properties": {
        "showTail": { "type": "boolean", "default": true }
      },
      "root": {
        "id": "wire_card_root", "type": "frame", "name": "Card",
        "sizing": { "horizontal": "fill", "vertical": "hug" },
        "size": { "width": 416, "height": 158 },
        "cornerRadius": { "${'$'}var": "var_radius" },
        "fills": [ { "type": "solid", "color": { "${'$'}var": "var_placeholder" } } ],
        "layout": { "mode": "vertical", "gap": 14,
                    "padding": { "top": 24, "right": 24, "bottom": 24, "left": 24 },
                    "alignItems": "stretch" },
        "children": [
          { "id": "card_header", "type": "frame", "name": "Header",
            "sizing": { "horizontal": "fill", "vertical": "hug" },
            "layout": { "mode": "horizontal", "gap": 12, "alignItems": "center" },
            "children": [
              { "id": "card_avatar", "type": "ellipse", "name": "Avatar",
                "sizing": { "horizontal": "fixed", "vertical": "fixed" },
                "size": { "width": 32, "height": 32 },
                "fills": [ { "type": "solid", "color": { "${'$'}var": "var_placeholder_deep" } } ] },
              { "id": "card_header_line", "type": "rectangle", "name": "Header line",
                "sizing": { "horizontal": "fill", "vertical": "fixed" },
                "size": { "height": 12 },
                "cornerRadius": 6,
                "fills": [ { "type": "solid", "color": { "${'$'}var": "var_placeholder_deep" } } ] }
            ] },
          { "id": "card_line_1", "type": "rectangle", "name": "Line 1",
            "sizing": { "horizontal": "fill", "vertical": "fixed" },
            "size": { "height": 12 },
            "cornerRadius": 6,
            "fills": [ { "type": "solid", "color": { "${'$'}var": "var_placeholder_deep" } } ] },
          { "id": "card_line_2", "type": "rectangle", "name": "Line 2",
            "sizing": { "horizontal": "fill", "vertical": "fixed" },
            "size": { "height": 12 },
            "cornerRadius": 6,
            "fills": [ { "type": "solid", "color": { "${'$'}var": "var_placeholder_deep" } } ] },
          { "id": "card_tail", "type": "rectangle", "name": "Tail",
            "visible": { "${'$'}prop": "showTail" },
            "sizing": { "horizontal": "fixed", "vertical": "fixed" },
            "size": { "width": 160, "height": 12 },
            "cornerRadius": 6,
            "fills": [ { "type": "solid", "color": { "${'$'}var": "var_placeholder_deep" } } ] }
        ]
      }
    },
    "cmp_log_row": {
      "name": "Log Row",
      "properties": {
        "label": { "type": "text", "default": "Event" },
        "time":  { "type": "text", "default": "00:00" }
      },
      "root": {
        "id": "log_row_root", "type": "frame", "name": "Log Row",
        "sizing": { "horizontal": "fill", "vertical": "fixed" },
        "size": { "width": 1328, "height": 56 },
        "layout": { "mode": "horizontal", "gap": "auto", "alignItems": "center",
                    "padding": { "top": 0, "right": 20, "bottom": 0, "left": 20 } },
        "fills": [ { "type": "solid", "color": { "${'$'}var": "var_surface" } } ],
        "strokes": {
          "paints": [ { "type": "solid", "color": { "${'$'}var": "var_line" } } ],
          "weight": 1,
          "weightPerSide": { "top": 0, "right": 0, "bottom": 1, "left": 0 },
          "align": "inside"
        },
        "children": [
          { "id": "log_left", "type": "frame", "name": "Left",
            "sizing": { "horizontal": "hug", "vertical": "hug" },
            "layout": { "mode": "horizontal", "gap": 12, "alignItems": "center" },
            "children": [
              { "id": "log_dot", "type": "ellipse", "name": "Status",
                "sizing": { "horizontal": "fixed", "vertical": "fixed" },
                "size": { "width": 12, "height": 12 },
                "fills": [ { "type": "solid", "color": "#17C46B" } ] },
              { "id": "log_label", "type": "text", "name": "Label",
                "characters": { "${'$'}prop": "label" },
                "textStyleId": "sty_row",
                "fills": [ { "type": "solid", "color": { "${'$'}var": "var_text" } } ],
                "sizing": { "horizontal": "hug", "vertical": "hug" },
                "autoResize": "widthAndHeight" }
            ] },
          { "id": "log_time", "type": "text", "name": "Time",
            "characters": { "${'$'}prop": "time" },
            "textStyleId": "sty_time",
            "fills": [ { "type": "solid", "color": { "${'$'}var": "var_muted" } } ],
            "sizing": { "horizontal": "hug", "vertical": "hug" },
            "autoResize": "widthAndHeight" }
        ]
      }
    }
  },

  "componentSets": {
    "set_wire_tile": {
      "name": "Wire Tile",
      "axes": { "kind": ["default", "highlight"] },
      "variants": {
        "kind=default":   "cmp_wire_tile_default",
        "kind=highlight": "cmp_wire_tile_highlight"
      }
    }
  },

  "pages": [
    {
      "id": "page_overview",
      "name": "Mission Overview",
      "children": [
        {
          "id": "frame_overview", "type": "frame", "name": "Mission Overview",
          "position": { "x": 72, "y": 72 },
          "constraints": { "horizontal": "left", "vertical": "top" },
          "size": { "width": 1440, "height": 1024 },
          "sizing": { "horizontal": "fixed", "vertical": "fixed" },
          "layout": { "mode": "vertical", "gap": { "${'$'}var": "var_space" },
                      "padding": { "top": { "${'$'}var": "var_pad_v" }, "right": { "${'$'}var": "var_pad_h" },
                                   "bottom": { "${'$'}var": "var_pad_v" }, "left": { "${'$'}var": "var_pad_h" } },
                      "alignItems": "stretch", "clipsContent": true },
          "fills": [ { "type": "solid", "color": { "${'$'}var": "var_surface" } } ],
          "strokes": { "paints": [ { "type": "solid", "color": { "${'$'}var": "var_stroke" } } ], "weight": 1, "align": "inside" },
          "cornerRadius": { "${'$'}var": "var_radius" },
          "children": [
            { "id": "overview_hero", "type": "rectangle", "name": "Hero",
              "sizing": { "horizontal": "fill", "vertical": "fixed" },
              "size": { "height": 140 },
              "cornerRadius": { "${'$'}var": "var_radius" },
              "fills": [ { "type": "solid", "color": { "${'$'}var": "var_placeholder" } } ] },

            { "id": "overview_tiles", "type": "frame", "name": "Tiles",
              "sizing": { "horizontal": "fill", "vertical": "hug" },
              "layout": { "mode": "horizontal", "gap": { "${'$'}var": "var_space" }, "alignItems": "start" },
              "children": [
                { "id": "tile_1", "type": "instance", "componentId": "set_wire_tile",
                  "variant": { "kind": "default" } },
                { "id": "tile_2", "type": "instance", "componentId": "set_wire_tile",
                  "variant": { "kind": "default" } },
                { "id": "tile_3", "type": "instance", "componentId": "set_wire_tile",
                  "variant": { "kind": "default" } }
              ] },

            { "id": "overview_wide", "type": "rectangle", "name": "Wide block",
              "sizing": { "horizontal": "fill", "vertical": "fixed" },
              "size": { "height": 140 },
              "cornerRadius": { "${'$'}var": "var_radius" },
              "fills": [ { "type": "solid", "color": { "${'$'}var": "var_placeholder" } } ] },

            { "id": "overview_cards", "type": "frame", "name": "Cards",
              "sizing": { "horizontal": "fill", "vertical": "hug" },
              "layout": { "mode": "horizontal", "gap": { "${'$'}var": "var_space" }, "alignItems": "start" },
              "children": [
                { "id": "card_1", "type": "instance", "componentId": "cmp_wire_card" },
                { "id": "card_2", "type": "instance", "componentId": "cmp_wire_card",
                  "overrides": [
                    { "target": ["card_avatar"],
                      "set": { "fills": [ { "type": "solid", "color": "#BFD7F5" } ] } }
                  ] },
                { "id": "card_3", "type": "instance", "componentId": "cmp_wire_card",
                  "props": { "showTail": false } }
              ] }
          ]
        }
      ]
    },
    {
      "id": "page_telemetry",
      "name": "Telemetry",
      "children": [
        {
          "id": "frame_telemetry", "type": "frame", "name": "Telemetry",
          "position": { "x": 72, "y": 72 },
          "constraints": { "horizontal": "left", "vertical": "top" },
          "size": { "width": 1440, "height": 1024 },
          "sizing": { "horizontal": "fixed", "vertical": "fixed" },
          "layout": { "mode": "grid",
                      "columns": [ { "type": "flex", "value": 1 }, { "type": "flex", "value": 1 }, { "type": "flex", "value": 1 } ],
                      "rows": [ { "type": "fixed", "value": 140 }, { "type": "hug" }, { "type": "hug" } ],
                      "gap": { "column": 40, "row": 40 },
                      "padding": { "top": { "${'$'}var": "var_pad_v" }, "right": { "${'$'}var": "var_pad_h" },
                                   "bottom": { "${'$'}var": "var_pad_v" }, "left": { "${'$'}var": "var_pad_h" } },
                      "clipsContent": true },
          "fills": [ { "type": "solid", "color": { "${'$'}var": "var_surface" } } ],
          "strokes": { "paints": [ { "type": "solid", "color": { "${'$'}var": "var_stroke" } } ], "weight": 1, "align": "inside" },
          "cornerRadius": { "${'$'}var": "var_radius" },
          "children": [
            { "id": "telemetry_header", "type": "rectangle", "name": "Header",
              "gridPlacement": { "column": 1, "row": 1, "columnSpan": 3 },
              "sizing": { "horizontal": "fill", "vertical": "fill" },
              "cornerRadius": { "${'$'}var": "var_radius" },
              "fills": [
                { "type": "gradientLinear",
                  "from": { "x": 0, "y": 0 }, "to": { "x": 1, "y": 0 },
                  "stops": [
                    { "position": 0, "color": "#DCEBFD" },
                    { "position": 1, "color": "#E9EEF4" }
                  ] }
              ] },
            { "id": "t_tile_1", "type": "instance", "componentId": "set_wire_tile", "variant": { "kind": "highlight" } },
            { "id": "t_tile_2", "type": "instance", "componentId": "set_wire_tile", "variant": { "kind": "default" } },
            { "id": "t_tile_3", "type": "instance", "componentId": "set_wire_tile", "variant": { "kind": "default" } },
            { "id": "t_tile_4", "type": "instance", "componentId": "set_wire_tile", "variant": { "kind": "default" } },
            { "id": "t_tile_5", "type": "instance", "componentId": "set_wire_tile", "variant": { "kind": "highlight" } },
            { "id": "t_tile_6", "type": "instance", "componentId": "set_wire_tile", "variant": { "kind": "default" } },

            { "id": "telemetry_badge", "type": "frame", "name": "Live badge",
              "layoutChild": { "absolute": true },
              "position": { "x": 1296, "y": 36 },
              "constraints": { "horizontal": "right", "vertical": "top" },
              "sizing": { "horizontal": "hug", "vertical": "hug" },
              "size": { "width": 88, "height": 28 },
              "layout": { "mode": "horizontal", "gap": 6, "alignItems": "center",
                          "padding": { "top": 5, "right": 12, "bottom": 5, "left": 12 } },
              "fills": [ { "type": "solid", "color": { "${'$'}var": "var_accent" } } ],
              "cornerRadius": 999,
              "children": [
                { "id": "badge_dot", "type": "ellipse", "name": "Dot",
                  "sizing": { "horizontal": "fixed", "vertical": "fixed" },
                  "size": { "width": 8, "height": 8 },
                  "fills": [ { "type": "solid", "color": "#FFFFFF" } ] },
                { "id": "badge_text", "type": "text", "name": "LIVE",
                  "characters": "LIVE",
                  "textStyle": { "fontFamily": "Inter", "fontSize": 12, "fontWeight": 700 },
                  "fills": [ { "type": "solid", "color": "#FFFFFF" } ],
                  "sizing": { "horizontal": "hug", "vertical": "hug" },
                  "autoResize": "widthAndHeight" }
              ] }
          ]
        }
      ]
    },
    {
      "id": "page_eventlog",
      "name": "Event Log",
      "children": [
        {
          "id": "frame_eventlog", "type": "frame", "name": "Event Log",
          "position": { "x": 72, "y": 72 },
          "constraints": { "horizontal": "left", "vertical": "top" },
          "size": { "width": 1440, "height": 1024 },
          "sizing": { "horizontal": "fixed", "vertical": "fixed" },
          "layout": { "mode": "vertical", "gap": 32,
                      "padding": { "top": { "${'$'}var": "var_pad_v" }, "right": { "${'$'}var": "var_pad_h" },
                                   "bottom": { "${'$'}var": "var_pad_v" }, "left": { "${'$'}var": "var_pad_h" } },
                      "alignItems": "stretch", "clipsContent": true },
          "fills": [ { "type": "solid", "color": { "${'$'}var": "var_surface" } } ],
          "strokes": { "paints": [ { "type": "solid", "color": { "${'$'}var": "var_stroke" } } ], "weight": 1, "align": "inside" },
          "cornerRadius": { "${'$'}var": "var_radius" },
          "children": [
            { "id": "eventlog_header", "type": "rectangle", "name": "Header",
              "sizing": { "horizontal": "fill", "vertical": "fixed" },
              "size": { "height": 120 },
              "cornerRadius": { "${'$'}var": "var_radius" },
              "fills": [ { "type": "solid", "color": { "${'$'}var": "var_placeholder" } } ] },

            { "id": "eventlog_list", "type": "frame", "name": "Rows",
              "sizing": { "horizontal": "fill", "vertical": "hug" },
              "layout": { "mode": "vertical", "gap": 0, "alignItems": "stretch", "clipsContent": true },
              "cornerRadius": { "${'$'}var": "var_radius" },
              "strokes": { "paints": [ { "type": "solid", "color": { "${'$'}var": "var_line" } } ], "weight": 1, "align": "inside" },
              "children": [
                { "id": "row_1", "type": "instance", "componentId": "cmp_log_row",
                  "props": { "label": "Telemetry sync completed", "time": "12:04" } },
                { "id": "row_2", "type": "instance", "componentId": "cmp_log_row",
                  "props": { "label": "Trajectory checkpoint passed", "time": "11:47" } },
                { "id": "row_3", "type": "instance", "componentId": "cmp_log_row",
                  "props": { "label": "Thruster calibration drift", "time": "11:12" },
                  "overrides": [
                    { "target": ["log_dot"],
                      "set": { "fills": [ { "type": "solid", "color": "#FFB800" } ] } }
                  ] },
                { "id": "row_4", "type": "instance", "componentId": "cmp_log_row",
                  "props": { "label": "Signal lock re-established", "time": "10:58" } },
                { "id": "row_5", "type": "instance", "componentId": "cmp_log_row",
                  "props": { "label": "Power bus anomaly detected", "time": "10:31" },
                  "overrides": [
                    { "target": ["log_dot"],
                      "set": { "fills": [ { "type": "solid", "color": "#FF1D1D" } ] } }
                  ] },
                { "id": "row_6", "type": "instance", "componentId": "cmp_log_row",
                  "props": { "label": "Uplink window opened", "time": "09:52" } }
              ] },

            { "id": "eventlog_footer", "type": "frame", "name": "Footer",
              "variableModes": { "col_theme": "dark" },
              "sizing": { "horizontal": "fill", "vertical": "hug" },
              "layout": { "mode": "horizontal", "gap": "auto", "alignItems": "center",
                          "padding": { "top": 16, "right": 20, "bottom": 16, "left": 20 } },
              "fills": [ { "type": "solid", "color": { "${'$'}var": "var_surface" } } ],
              "cornerRadius": { "${'$'}var": "var_radius" },
              "children": [
                { "id": "footer_label", "type": "text", "name": "Summary",
                  "characters": "6 events captured in the last orbit",
                  "textStyleId": "sty_row",
                  "fills": [ { "type": "solid", "color": { "${'$'}var": "var_text" } } ],
                  "sizing": { "horizontal": "hug", "vertical": "hug" },
                  "autoResize": "widthAndHeight" },
                { "id": "footer_time", "type": "text", "name": "Updated",
                  "characters": "updated 12:04",
                  "textStyleId": "sty_time",
                  "fills": [ { "type": "solid", "color": { "${'$'}var": "var_muted" } } ],
                  "sizing": { "horizontal": "hug", "vertical": "hug" },
                  "autoResize": "widthAndHeight" }
              ] }
          ]
        }
      ]
    }
  ],

  "assets": {}
}
"""
