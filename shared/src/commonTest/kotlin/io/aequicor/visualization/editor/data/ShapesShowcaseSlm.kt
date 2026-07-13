package io.aequicor.visualization.editor.data

/**
 * Frozen test fixture (former bundled default; figure tests anchor to its node ids).
 * Shapes Showcase screen: authored in CNL, covering parametric
 * primitives, stroked open paths, an editable vector network, even-odd paths, and a
 * boolean union of two ellipses.
 */
val ShapesShowcaseSlm: String = missionSlm(
    """
    ---
    screen: shapesShowcase
    page: Shapes Showcase
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

    # Shapes Showcase id frame_showcase name «Shapes Showcase» 1440 by 1024 position 72 72 column gap §space padding §padV §padH color §color.surface stroke §color.stroke radius §radius clip auto-layout

    Text id showcase_title «Shapes & Vector Components» name «Title» color §color.text size 28 key shapesShowcase.title.heading bold font «Inter» autosize both

    ## AutoLayout: Parametrics id showcase_parametrics width hug height hug row gap §space

    ### AutoLayout: Card Star id card_star width hug height hug column gap 12 align (inline center)

    Text id label_star «Star» name «Star Label» color §color.muted size 13 key shapesShowcase.label.star semibold font «Inter» autosize both

    Star id showcase_star name «Star» 160 by 160 color #4C6EF5 points 6 inner 0.45

    ### AutoLayout: Card Polygon id card_polygon width hug height hug column gap 12 align (inline center)

    Text id label_polygon «Polygon» name «Polygon Label» color §color.muted size 13 key shapesShowcase.label.polygon semibold font «Inter» autosize both

    Polygon id showcase_polygon name «Polygon» 160 by 160 color #F76707 points 5

    ### AutoLayout: Card Ellipse id card_ellipse width hug height hug column gap 12 align (inline center)

    Text id label_ellipse «Ellipse» name «Ellipse Label» color §color.muted size 13 key shapesShowcase.label.ellipse semibold font «Inter» autosize both

    Ellipse id showcase_ellipse name «Ellipse» 200 by 160 color #12B886

    ## AutoLayout: Strokes id showcase_strokes width hug height hug row gap §space

    ### AutoLayout: Card Arrow id card_arrow width hug height hug column gap 12 align (inline center)

    Text id label_arrow «Arrow» name «Arrow Label» color §color.muted size 13 key shapesShowcase.label.arrow semibold font «Inter» autosize both

    Arrow id showcase_arrow name «Arrow» 260 by 56 stroke (color #E8590C weight 6 cap round join round)

    ### AutoLayout: Card Line id card_line width hug height hug column gap 12 align (inline center)

    Text id label_line «Line (round caps)» name «Line Label» color §color.muted size 13 key shapesShowcase.label.line semibold font «Inter» autosize both

    Line id showcase_line name «Line» 260 by 24 stroke (color #7048E8 weight 10 cap round)

    ## AutoLayout: Vectors id showcase_vectors width hug height hug row gap §space

    ### AutoLayout: Card Network id card_network width hug height hug column gap 12 align (inline center)

    Text id label_network «Vector network (editable)» name «Network Label» color §color.muted size 13 key shapesShowcase.label.network semibold font «Inter» autosize both

    Vector id showcase_network name «Network» 160 by 160 color #2F9E44 viewbox (0 0 24 24) network (vertex (12 2 in (-7 -3) out (7 3) mirror angleAndLength) vertex (22 20 corner) vertex (2 20 corner) segment (0 1) segment (1 2) segment (2 0) region loops (0 1 2))

    ### AutoLayout: Card Donut id card_donut width hug height hug column gap 12 align (inline center)

    Text id label_donut «Even-odd hole» name «Donut Label» color §color.muted size 13 key shapesShowcase.label.donut semibold font «Inter» autosize both

    Vector id showcase_donut name «Donut» 160 by 160 color #1098AD viewbox (0 0 100 100) path «M50 6 A44 44 0 1 0 50 94 A44 44 0 1 0 50 6 Z M50 30 A20 20 0 1 0 50 70 A20 20 0 1 0 50 30 Z» evenodd

    ### AutoLayout: Card Boolean id card_boolean width hug height hug column gap 12 align (inline center)

    Text id label_boolean «Boolean union» name «Boolean Label» color §color.muted size 13 key shapesShowcase.label.boolean semibold font «Inter» autosize both

    #### Vector: Union id showcase_union 160 by 160 color #E64980 boolean union

    Ellipse id union_a name «Union A» 90 by 90 position 12 40 absolute

    Ellipse id union_b name «Union B» 90 by 90 position 58 40 absolute
    """,
)
