package io.aequicor.visualization.editor.data

/**
 * Frozen test fixture (former bundled default; instance/override tests anchor to its node ids).
 * Event Log page authored entirely in CNL (controlled natural language): a header
 * placeholder, six Log Row instances (two patch the status dot fill via an `override`
 * at an id path), a footer frame that pins the theme collection to `dark` for its
 * subtree (`modes (theme dark)`), and a Log Row component whose label/time are `$prop`
 * bindings. The bottom-only row border is an absolutely anchored 1px divider rectangle.
 *
 * Migrated from a former YAML/`ir`-fenced source; CNL is now the sole authoring format.
 */
val MissionEventLogSlm: String = missionSlm(
    """
    ---
    screen: missionEventLog
    page: Event Log
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

    # Event Log id frame_eventlog name «Event Log» 1440 by 1024 position 72 72 column gap 32 padding §padV §padH color §color.surface stroke §color.stroke radius §radius clip align (inline stretch) auto-layout

    ## Rectangle: id eventlog_header name «Header» width fill height 120 color §color.placeholder radius §radius

    ## AutoLayout: id eventlog_list name «Rows» width fill height hug column stroke §color.line radius §radius clip align (inline stretch)

    ### Instance: id row_1 of cmp_log_row props (label «Telemetry sync completed» time «12:04») name «Row 1»

    ### Instance: id row_2 of cmp_log_row props (label «Trajectory checkpoint passed» time «11:47») name «Row 2»

    ### Instance: id row_3 of cmp_log_row props (label «Thruster calibration drift» time «11:12») override log_dot (color #FFB800)

    ### Instance: id row_4 of cmp_log_row props (label «Signal lock re-established» time «10:58») name «Row 4»

    ### Instance: id row_5 of cmp_log_row props (label «Power bus anomaly detected» time «10:31») override log_dot (color #FF1D1D)

    ### Instance: id row_6 of cmp_log_row props (label «Uplink window opened» time «09:52») name «Row 6»

    ## AutoLayout: id eventlog_footer name «Footer» width fill height hug row gap auto padding 16 20 color §color.surface radius §radius align (block center) modes (theme dark)

    ### Text: id footer_label characters «6 events captured in the last orbit» name «Summary» width hug height hug color §color.text size 14 weight 500 font «Inter» line-height 143% autosize both

    ### Text: id footer_time characters «updated 12:04» name «Updated» width hug height hug color §color.muted size 12 weight 400 font «Inter» line-height 133% autosize both

    ## Component: Log Row id cmp_log_row component-name «Log Row» prop label (text default «Event») prop time (text default «00:00») width (fill 1328) height 56 row gap auto padding 0 20 color §color.surface align (block center) auto-layout

    ### AutoLayout: id log_left name «Left» width hug height hug row gap 12 align (block center)

    #### Ellipse: id log_dot name «Status» 12 by 12 color #17C46B

    #### Text: id log_label name «Label» width hug height hug color §color.text size 14 weight 500 font «Inter» line-height 143% characters §prop.label autosize both

    ### Text: id log_time name «Time» width hug height hug color §color.muted size 12 weight 400 font «Inter» line-height 133% characters §prop.time autosize both

    ### Rectangle: id log_row_divider name «Divider» 1328 by 1 absolute anchor (inlineStart 0 inlineEnd 0 blockEnd 0) color §color.line
    """,
)
