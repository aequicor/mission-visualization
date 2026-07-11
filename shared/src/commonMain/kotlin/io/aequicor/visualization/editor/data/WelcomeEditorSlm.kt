package io.aequicor.visualization.editor.data

/**
 * Welcome screen 1: a Free-layout wireframe mirroring the editor's own chrome
 * (Source / Canvas / Inspector) with a running scene — looping motion clips on the canvas
 * mock (floating card, spinning star, roaming collaborator cursor, pulsing status dots)
 * and an auto-tour: `afterDelay` navigates to the Vectors screen with an animated push,
 * and the floating Next button navigates on click. Pure CNL; frontmatter is the only YAML.
 */
val WelcomeEditorSlm: String = missionSlm(
    """
    ---
    screen: welcomeEditor
    page: Welcome
    sourceLocale: en-US
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
    Color color.line light #DCE3EC dark #334155

    # Welcome id frame_welcome name «Welcome» 1440 by 1024 position 72 72 color #EAF5FF clip afterDelay (9000) navigate (welcomeVectors) animate (type push easing easeInOut duration 600 direction left)

    ## Rectangle: id win_bg name «Window» 1408 by 992 position 16 16 color #FFFFFF stroke #CEE1F2 radius 24

    ## Frame: id src_panel name «Source» 424 by 944 position 40 40 color #FBFDFF stroke #E3EAF2 radius 16 clip

    ### Frame: id src_menu name «Menu Button» 44 by 44 position 20 20 color §color.accent radius 12 motion duration 2600 loop frames (0 scale 1) (0.5 scale 1.08) (1 scale 1)

    #### Rectangle: id src_menu_l1 name «Menu Line 1» 20 by 3 position 12 15 color #FFFFFF radius 2

    #### Rectangle: id src_menu_l2 name «Menu Line 2» 20 by 3 position 12 21 color #FFFFFF radius 2

    #### Rectangle: id src_menu_l3 name «Menu Line 3» 20 by 3 position 12 27 color #FFFFFF radius 2

    ### Text: id src_title characters «Source» name «Source Title» width hug height hug position 80 28 color §color.text size 24 key welcome.source.title bold font «Inter» autosize both

    ### Frame: id src_save name «Save Button» 74 by 40 position 250 28 color #F6FAFE stroke #E3EAF2 radius 10

    #### Text: id src_save_label characters «Save» name «Save Label» 74 by 40 position 0 0 color #31516E size 14 key welcome.source.save semibold font «Inter» text-align center text-valign center

    ### Frame: id src_reset name «Reset Button» 74 by 40 position 330 28 color #F6FAFE stroke #E3EAF2 radius 10

    #### Text: id src_reset_label characters «Reset» name «Reset Label» 74 by 40 position 0 0 color #31516E size 14 key welcome.source.reset semibold font «Inter» text-align center text-valign center

    ### Frame: id src_tabs name «Tabs» 384 by 44 position 20 84 color #F4F8FC stroke #E3EAF2 radius 12

    #### Text: id src_tab_md characters «Markdown» name «Tab Markdown» width hug height hug position 20 15 color §color.muted size 14 key welcome.source.tab.markdown weight 500 font «Inter» autosize both

    #### Text: id src_tab_res characters «Resources» name «Tab Resources» width hug height hug position 140 15 color §color.muted size 14 key welcome.source.tab.resources weight 500 font «Inter» autosize both

    #### Text: id src_tab_layers characters «Layers» name «Tab Layers» width hug height hug position 285 15 color §color.accent size 14 key welcome.source.tab.layers bold font «Inter» autosize both

    #### Rectangle: id src_tab_underline name «Tab Underline» 58 by 3 position 283 36 color §color.accent radius 2

    ### Rectangle: id src_row_hl name «Row Highlight» 392 by 28 position 16 145 color #EAF4FF radius 8

    ### Rectangle: id src_ic0 name «Layer Icon 0» 14 by 14 position 26 149 color #B9D9FF radius 4

    ### Text: id src_lb0 characters «Welcome» name «Layer Label 0» width hug height hug position 50 149 color §color.accent size 13 key welcome.layer.welcome semibold font «Inter» autosize both

    ### Rectangle: id src_ic1 name «Layer Icon 1» 14 by 14 position 46 179 color #D9E5F1 radius 4

    ### Text: id src_lb1 characters «Window» name «Layer Label 1» width hug height hug position 70 179 color §color.text size 13 key welcome.layer.window semibold font «Inter» autosize both

    ### Rectangle: id src_ic2 name «Layer Icon 2» 14 by 14 position 66 209 color #D9E5F1 radius 4

    ### Text: id src_lb2 characters «Source» name «Layer Label 2» width hug height hug position 90 209 color §color.text size 13 key welcome.layer.source weight 400 font «Inter» autosize both

    ### Rectangle: id src_ic3 name «Layer Icon 3» 14 by 14 position 66 239 color #D9E5F1 radius 4

    ### Text: id src_lb3 characters «Canvas» name «Layer Label 3» width hug height hug position 90 239 color §color.text size 13 key welcome.layer.canvas weight 400 font «Inter» autosize both

    ### Rectangle: id src_ic4 name «Layer Icon 4» 14 by 14 position 66 269 color #D9E5F1 radius 4

    ### Text: id src_lb4 characters «Inspector» name «Layer Label 4» width hug height hug position 90 269 color §color.text size 13 key welcome.layer.inspector weight 400 font «Inter» autosize both

    ### Rectangle: id src_ic5 name «Layer Icon 5» 14 by 14 position 66 299 color #D8DFE8 radius 3

    ### Text: id src_lb5 characters «Scene tour» name «Layer Label 5» width hug height hug position 90 299 color §color.text size 13 key welcome.layer.scene weight 400 font «Inter» autosize both

    ### Text: id src_screens_h characters «Screens» name «Screens Heading» width hug height hug position 24 352 color §color.text size 17 key welcome.source.screens bold font «Inter» autosize both

    ### Frame: id src_add name «Add Screen» 34 by 34 position 368 348 color #F6FAFE stroke #E3EAF2 radius 10

    #### Text: id src_add_plus characters «+» name «Add Plus» 34 by 34 position 0 -1 color §color.muted size 20 key welcome.source.add weight 500 font «Inter» text-align center text-valign center

    ### Frame: id src_card1 name «Screen Card 1» 384 by 78 position 16 392 color #EAF4FF stroke #1E88FF radius 14

    #### Rectangle: id src_card1_thumb name «Card Thumb 1» 58 by 42 position 18 18 color #EAF2FA stroke #E3EAF2 radius 8

    #### Text: id src_card1_name characters «Welcome» name «Card Name 1» width hug height hug position 92 17 color §color.text size 15 key welcome.screen.welcome.name semibold font «Inter» autosize both

    #### Text: id src_card1_dims characters «1440 x 1024» name «Card Dims 1» width hug height hug position 92 44 color §color.muted size 12 key welcome.screen.welcome.dims weight 400 font «Inter» autosize both

    #### Ellipse: id src_card1_dot name «Card Status 1» 12 by 12 position 350 33 color #17C46B motion duration 1400 loop frames (0 opacity 0.35) (0.5 opacity 1) (1 opacity 0.35)

    ### Frame: id src_card2 name «Screen Card 2» 384 by 78 position 16 480 color #FDFEFF stroke #E3EAF2 radius 14

    #### Rectangle: id src_card2_thumb name «Card Thumb 2» 58 by 42 position 18 18 color #EAF2FA stroke #E3EAF2 radius 8

    #### Text: id src_card2_name characters «Vectors & Objects» name «Card Name 2» width hug height hug position 92 17 color §color.text size 15 key welcome.screen.vectors.name semibold font «Inter» autosize both

    #### Text: id src_card2_dims characters «1440 x 1024» name «Card Dims 2» width hug height hug position 92 44 color §color.muted size 12 key welcome.screen.vectors.dims weight 400 font «Inter» autosize both

    #### Ellipse: id src_card2_dot name «Card Status 2» 12 by 12 position 350 33 color #FFB800 motion duration 1900 loop frames (0 opacity 1) (0.5 opacity 0.35) (1 opacity 1)

    ### Frame: id src_card3 name «Screen Card 3» 384 by 78 position 16 568 color #FDFEFF stroke #E3EAF2 radius 14

    #### Rectangle: id src_card3_thumb name «Card Thumb 3» 58 by 42 position 18 18 color #EAF2FA stroke #E3EAF2 radius 8

    #### Text: id src_card3_name characters «Architecture» name «Card Name 3» width hug height hug position 92 17 color §color.text size 15 key welcome.screen.uml.name semibold font «Inter» autosize both

    #### Text: id src_card3_dims characters «1440 x 1024» name «Card Dims 3» width hug height hug position 92 44 color §color.muted size 12 key welcome.screen.uml.dims weight 400 font «Inter» autosize both

    #### Ellipse: id src_card3_dot name «Card Status 3» 12 by 12 position 350 33 color #1E88FF motion duration 2400 loop frames (0 opacity 0.35) (0.5 opacity 1) (1 opacity 0.35)

    ### Text: id src_hint characters «These screens live in memory — reload restores them. Save the project to a folder to keep your edits.» name «Persistence Hint» 384 by 60 position 20 680 color §color.muted size 13 key welcome.source.hint weight 400 font «Inter» line-height 150%

    ## Frame: id cv_panel name «Canvas» 596 by 944 position 480 40 color #FBFDFF stroke #E3EAF2 radius 16 clip

    ### Text: id cv_title characters «Canvas — Welcome» name «Canvas Title» width hug height hug position 24 26 color §color.text size 20 key welcome.canvas.title bold font «Inter» autosize both

    ### Frame: id cv_badge name «Tour Badge» width (hug 118) height (hug 28) position 300 26 row gap 6 padding 5 12 color §color.accent radius 999 align (block center)

    #### Ellipse: id cv_badge_dot name «Tour Dot» 8 by 8 color #FFFFFF motion duration 900 loop frames (0 opacity 0.4) (0.5 opacity 1) (1 opacity 0.4)

    #### Text: id cv_badge_text characters «SCENE TOUR» name «Tour Label» width hug height hug color #FFFFFF size 11 key welcome.canvas.badge bold font «Inter» autosize both

    ### Frame: id cv_tool1 name «Tool 1» 40 by 40 position 452 20 color #F6FAFE stroke #E3EAF2 radius 10

    #### Text: id cv_tool1_g characters «</>» name «Tool Glyph 1» 40 by 40 position 0 0 color §color.muted size 15 key welcome.canvas.tool.code semibold font «Inter» text-align center text-valign center

    ### Frame: id cv_tool2 name «Tool 2» 40 by 40 position 500 20 color #F6FAFE stroke #E3EAF2 radius 10

    #### Text: id cv_tool2_g characters «≡» name «Tool Glyph 2» 40 by 40 position 0 0 color §color.muted size 17 key welcome.canvas.tool.tune semibold font «Inter» text-align center text-valign center

    ### Frame: id cv_tool3 name «Tool 3» 40 by 40 position 548 20 color #F6FAFE stroke #E3EAF2 radius 10

    #### Text: id cv_tool3_g characters «⤢» name «Tool Glyph 3» 40 by 40 position 0 0 color §color.muted size 16 key welcome.canvas.tool.fit semibold font «Inter» text-align center text-valign center

    ### Rectangle: id cv_surface name «Canvas Surface» 556 by 764 position 20 76 color #F4F8FC stroke #E3EAF2 radius 16

    ### Frame: id cv_mock_card name «Mock Card» 220 by 140 position 80 170 color #FFFFFF stroke #D8E4F0 radius 14 motion duration 3200 loop frames (0 y 0 rotation 0) (0.5 y -12 rotation 1.5) (1 y 0 rotation 0)

    #### Rectangle: id cv_mock_bar1 name «Mock Bar 1» 150 by 10 position 20 22 color #1E88FF radius 5

    #### Rectangle: id cv_mock_bar2 name «Mock Bar 2» 180 by 8 position 20 48 color #D9E5F1 radius 4

    #### Rectangle: id cv_mock_bar3 name «Mock Bar 3» 120 by 8 position 20 68 color #D9E5F1 radius 4

    #### Frame: id cv_mock_btn name «Mock Button» 92 by 30 position 20 90 color #17C46B radius 8

    ### Star: id cv_mock_star name «Mock Star» 72 by 72 position 380 160 color #FFB800 points 5 inner 0.5 motion duration 6000 loop frames (0 rotation 0) (1 rotation 360)

    ### Frame: id cv_mock_progress name «Mock Progress» 260 by 12 position 80 380 color #E5EEF7 radius 6 clip

    #### Rectangle: id cv_mock_progress_fill name «Progress Fill» 90 by 12 position 0 0 color §color.accent radius 6 motion duration 2200 loop frames (0 x 0) (1 x 260)

    ### Ellipse: id cv_mock_orbit name «Mock Orbit» 150 by 150 position 340 330 color #B9D9FF66 inner 0.94

    ### Ellipse: id cv_mock_moon name «Mock Moon» 18 by 18 position 406 322 color §color.accent motion duration 4200 loop frames (0 x 0 y 0) (0.25 x 66 y 66) (0.5 x 0 y 132) (0.75 x -66 y 66) (1 x 0 y 0)

    ### Frame: id cv_cursor name «Agent Cursor» 120 by 40 position 150 560 motion duration 7000 loop frames (0 x 0 y 0) (0.3 x 220 y -60) (0.55 x 260 y 90) (0.8 x 40 y 130) (1 x 0 y 0)

    #### Vector: id cv_cursor_arrow name «Cursor» 22 by 22 position 0 0 color #7048E8 viewbox (0 0 24 24) path «M2 2 L22 10 L12 13 L9 23 Z»

    #### Frame: id cv_cursor_chip name «Cursor Chip» width (hug 84) height (hug 24) position 24 18 row padding 4 10 color #7048E8 radius 999

    ##### Text: id cv_cursor_label characters «SLM Agent» name «Cursor Label» width hug height hug color #FFFFFF size 11 key welcome.canvas.cursor semibold font «Inter» autosize both

    ### Frame: id wel_nav_next name «Next Button» 190 by 44 position 386 780 color §color.accent radius 22 onClick navigate (welcomeVectors) animate (type push easing easeInOut duration 500 direction left) motion duration 2400 loop frames (0 scale 1) (0.5 scale 1.05) (1 scale 1)

    #### Text: id wel_nav_next_label characters «Next · Vectors →» name «Next Label» 190 by 44 position 0 0 color #FFFFFF size 14 key welcome.nav.next semibold font «Inter» text-align center text-valign center

    ### Frame: id cv_dev name «Device Toggle» 210 by 52 position 20 872 color #F6FAFE stroke #E3EAF2 radius 14

    #### Rectangle: id cv_dev_hl name «PC Highlight» 60 by 36 position 8 8 color #DCEEFF radius 10

    #### Text: id cv_dev_pc characters «PC» name «Seg PC» 64 by 52 position 6 0 color §color.accent size 14 key welcome.canvas.device.pc bold font «Inter» text-align center text-valign center

    #### Text: id cv_dev_mob characters «MOB» name «Seg MOB» 64 by 52 position 74 0 color §color.muted size 13 key welcome.canvas.device.mob weight 500 font «Inter» text-align center text-valign center

    #### Text: id cv_dev_tab characters «TAB» name «Seg TAB» 64 by 52 position 142 0 color §color.muted size 13 key welcome.canvas.device.tab weight 500 font «Inter» text-align center text-valign center

    ### Frame: id cv_mode name «Mode Toggle» 150 by 52 position 242 872 color #F6FAFE stroke #E3EAF2 radius 14

    #### Rectangle: id cv_mode_hl name «Scene Highlight» 60 by 40 position 84 6 color #1E88FF radius 10

    #### Text: id cv_mode_canvas characters «Canvas» name «Seg Canvas» 78 by 52 position 6 0 color §color.muted size 14 key welcome.canvas.mode.canvas weight 500 font «Inter» text-align center text-valign center

    #### Text: id cv_mode_scene characters «Scene» name «Seg Scene» 60 by 52 position 84 0 color #FFFFFF size 14 key welcome.canvas.mode.scene semibold font «Inter» text-align center text-valign center

    ### Frame: id cv_zoom name «Zoom Control» 172 by 52 position 404 872 color #F6FAFE stroke #E3EAF2 radius 14

    #### Text: id cv_zoom_minus characters «−» name «Zoom Minus» 44 by 52 position 4 0 color §color.text size 20 key welcome.canvas.zoom.minus semibold font «Inter» text-align center text-valign center

    #### Text: id cv_zoom_val characters «37%» name «Zoom Value» 74 by 52 position 50 0 color §color.text size 14 key welcome.canvas.zoom.value weight 500 font «Inter» text-align center text-valign center

    #### Text: id cv_zoom_plus characters «+» name «Zoom Plus» 44 by 52 position 124 0 color §color.text size 20 key welcome.canvas.zoom.plus semibold font «Inter» text-align center text-valign center

    ## Frame: id in_panel name «Inspector» 300 by 944 position 1092 40 color #FBFDFF stroke #E3EAF2 radius 16 clip

    ### Text: id in_title characters «Inspector» name «Inspector Title» width hug height hug position 20 24 color §color.text size 22 key welcome.inspector.title bold font «Inter» autosize both

    ### Text: id in_tab_design characters «Design» name «Tab Design» width hug height hug position 20 68 color §color.accent size 14 key welcome.inspector.tab.design bold font «Inter» autosize both

    ### Text: id in_tab_proto characters «Prototype» name «Tab Prototype» width hug height hug position 96 68 color §color.muted size 14 key welcome.inspector.tab.prototype weight 500 font «Inter» autosize both

    ### Text: id in_tab_comments characters «Comments» name «Tab Comments» width hug height hug position 192 68 color §color.muted size 14 key welcome.inspector.tab.comments weight 500 font «Inter» autosize both

    ### Rectangle: id in_tab_ul name «Inspector Tab Underline» 54 by 3 position 18 92 color §color.accent radius 2

    ### Rectangle: id in_div1 name «Header Divider» 262 by 1 position 18 106 color §color.line

    ### Text: id in_node characters «Welcome» name «Node Name» width hug height hug position 20 122 color §color.text size 16 key welcome.inspector.node bold font «Inter» autosize both

    ### Frame: id in_dup name «Dup Button» 30 by 30 position 214 116 color #F6FAFE stroke #E3EAF2 radius 8

    #### Rectangle: id in_dup_icon name «Dup Icon» 12 by 12 position 9 9 stroke §color.muted radius 3

    ### Frame: id in_del name «Del Button» 30 by 30 position 250 116 color #F6FAFE stroke #E3EAF2 radius 8

    #### Rectangle: id in_del_icon name «Del Icon» 12 by 12 position 9 9 stroke §color.muted radius 3

    ### Text: id in_pos_h characters «Position» name «Position Heading» width hug height hug position 20 168 color §color.text size 15 key welcome.inspector.position bold font «Inter» autosize both

    ### Frame: id in_x name «X Field» 126 by 36 position 20 200 color #F6FAFE stroke #E3EAF2 radius 8

    #### Text: id in_x_val characters «X      72» name «X Value» width hug height hug position 12 10 color §color.text size 13 key welcome.inspector.x weight 500 font «Inter» autosize both

    ### Frame: id in_y name «Y Field» 126 by 36 position 154 200 color #F6FAFE stroke #E3EAF2 radius 8

    #### Text: id in_y_val characters «Y      72» name «Y Value» width hug height hug position 12 10 color §color.text size 13 key welcome.inspector.y weight 500 font «Inter» autosize both

    ### Text: id in_dim_lbl characters «Dimensions» name «Dimensions Label» width hug height hug position 20 252 color §color.muted size 12 key welcome.inspector.dimensions weight 500 font «Inter» autosize both

    ### Frame: id in_w name «W Field» 126 by 36 position 20 272 color #F6FAFE stroke #E3EAF2 radius 8

    #### Text: id in_w_val characters «W   1440» name «W Value» width hug height hug position 12 10 color §color.text size 13 key welcome.inspector.w weight 500 font «Inter» autosize both

    ### Frame: id in_h name «H Field» 126 by 36 position 154 272 color #F6FAFE stroke #E3EAF2 radius 8

    #### Text: id in_h_val characters «H   1024» name «H Value» width hug height hug position 12 10 color §color.text size 13 key welcome.inspector.h weight 500 font «Inter» autosize both

    ### Rectangle: id in_div2 name «Section Divider» 262 by 1 position 18 324 color §color.line

    ### Text: id in_proto_h characters «Prototype» name «Prototype Heading» width hug height hug position 20 342 color §color.text size 15 key welcome.inspector.proto bold font «Inter» autosize both

    ### Frame: id in_proto_card name «Interaction Card» 260 by 92 position 20 372 color #F4F8FC stroke #E3EAF2 radius 12

    #### Text: id in_proto_trigger characters «After delay · 9000 ms» name «Trigger Row» width hug height hug position 14 14 color §color.text size 13 key welcome.inspector.proto.trigger semibold font «Inter» autosize both

    #### Text: id in_proto_action characters «Navigate → Vectors & Objects» name «Action Row» width hug height hug position 14 38 color §color.muted size 13 key welcome.inspector.proto.action weight 400 font «Inter» autosize both

    #### Text: id in_proto_anim characters «Push · easeInOut · 600 ms» name «Anim Row» width hug height hug position 14 62 color §color.muted size 13 key welcome.inspector.proto.anim weight 400 font «Inter» autosize both

    ### Frame: id in_motion_card name «Motion Card» 260 by 68 position 20 476 color #F4F8FC stroke #E3EAF2 radius 12

    #### Ellipse: id in_motion_dot name «Motion Dot» 10 by 10 position 16 18 color #17C46B motion duration 1400 loop frames (0 opacity 0.35) (0.5 opacity 1) (1 opacity 0.35)

    #### Text: id in_motion_title characters «Motion · Pulse 1400 ms» name «Motion Title» width hug height hug position 36 14 color §color.text size 13 key welcome.inspector.motion semibold font «Inter» autosize both

    #### Text: id in_motion_sub characters «opacity 0.35 → 1 → 0.35 · loop» name «Motion Sub» width hug height hug position 36 38 color §color.muted size 13 key welcome.inspector.motion.sub weight 400 font «Inter» autosize both

    ### Text: id in_tip characters «Toggle Canvas → Scene under the artboard to watch this screen run its tour.» name «Scene Tip» 260 by 60 position 20 568 color §color.muted size 13 key welcome.inspector.tip weight 400 font «Inter» line-height 150%
    """,
)
