package io.aequicor.visualization.editor.data

/**
 * Telemetry page authored in CNL. The retained frontmatter is metadata; variables,
 * component definitions, styling, text, motion, and layout are controlled-natural-language source.
 */
val MissionTelemetrySlm: String = missionSlm(
    """
    ---
    screen: missionTelemetry
    page: Telemetry
    sourceLocale: en-US
    targetLocales: [en-US, ru-RU]
    theme: light
    frame:
      width: 1440
      height: 1024
    ---
    
    # Collection theme «Theme» (modes light dark default light)
    
    Color color.surface light #FFFFFF dark #111827
    Color color.text light #172033 dark #F9FAFB
    Color color.muted light #5E6B7A dark #9CA3AF
    Color color.accent light #1E88FF dark #60A5FA
    Color color.placeholder light #E9EEF4 dark #1F2937
    Color color.placeholderDeep light #D8DFE8 dark #374151
    Color color.line light #DCE3EC dark #334155
    Color color.stroke light §color.accent dark §color.accent
    Number radius light 8 dark 8
    Number space light 40 dark 40
    Number padH light 56 dark 56
    Number padV light 88 dark 88
    
    # Telemetry id frame_telemetry name «Telemetry» 1440 by 1024 position 72 72 grid padding §padV §padH gap (row 40 column 40) color §color.surface stroke §color.stroke radius §radius clip columns (count 3 track 1fr) rows (auto track hug)
    
    ## Rectangle: id telemetry_header name «Header» width fill height 140 gradient (linear to (1 0) stops (#DCEBFD at 0) (#E9EEF4 at 1)) radius §radius place (column 1 row 1 columnSpan 3)
    
    ## Instance: id t_tile_1 of cmp_wire_tile_highlight name «Tile 1»
    
    ## Instance: id t_tile_2 of cmp_wire_tile_default name «Tile 2»
    
    ## Instance: id t_tile_3 of cmp_wire_tile_default name «Tile 3»
    
    ## Instance: id t_tile_4 of cmp_wire_tile_default name «Tile 4»
    
    ## Instance: id t_tile_5 of cmp_wire_tile_highlight name «Tile 5»
    
    ## Instance: id t_tile_6 of cmp_wire_tile_default name «Tile 6»
    
    ## Frame: id telemetry_badge name «Live badge» width (hug 88) height (hug 28) position 1296 36 absolute row gap 6 padding 5 12 color §color.accent radius 999 align right align (block center)
    
    ### Ellipse: id badge_dot name «Dot» 8 by 8 color #FFFFFF motion duration 900 loop frames (0 opacity 0.4) (0.5 opacity 1) (1 opacity 0.4)
    
    ### Text: id badge_text characters «LIVE» name «LIVE» width hug height hug color #FFFFFF size 12 key missionTelemetry.badge.live bold font «Inter» autosize both
    
    ## Component: Tile id cmp_wire_tile_default component-name «Wire Tile / Default» width (fill 416) height 150 color §color.placeholder radius §radius clip
    
    ## Component: Tile Highlight id cmp_wire_tile_highlight component-name «Wire Tile / Highlight» width (fill 416) height 150 gradient (linear to (1 1) stops (#DCEBFD at 0) (#E9EEF4 at 1)) radius §radius clip
    """,
)
