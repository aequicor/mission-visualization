package io.aequicor.visualization.editor.data

/**
 * Frozen test fixture (former bundled default; write-back tests anchor to its node ids).
 * Mission Overview page authored in CNL. The retained frontmatter is metadata; all layout,
 * variables, styling, text, and structure below it are controlled-natural-language source.
 */
val MissionOverviewSlm: String = missionSlm(
    """
    ---
    screen: missionOverview
    page: Mission Overview
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
    
    # Mission Overview id frame_overview name «Mission Overview» 1440 by 1024 position 72 72 color #EAF5FF clip
    
    ## Rectangle: id win_bg name «Window» 1408 by 992 position 16 16 color #FFFFFF stroke #CEE1F2 radius 24
    
    ## Frame: id src_panel name «Source» 424 by 944 position 40 40 color #FBFDFF stroke #E3EAF2 radius 16 clip
    
    ### Frame: id src_menu name «Menu Button» 44 by 44 position 20 20 color §color.accent radius 12
    
    #### Rectangle: id src_menu_l1 name «Menu Line 1» 20 by 3 position 12 15 color #FFFFFF radius 2
    
    #### Rectangle: id src_menu_l2 name «Menu Line 2» 20 by 3 position 12 21 color #FFFFFF radius 2
    
    #### Rectangle: id src_menu_l3 name «Menu Line 3» 20 by 3 position 12 27 color #FFFFFF radius 2
    
    ### Text: id src_title characters «Source» name «Source Title» width hug height hug position 80 28 color §color.text size 24 key app.source.title bold font «Inter» autosize both
    
    ### Frame: id src_save name «Save Button» 74 by 40 position 250 28 color #F6FAFE stroke #E3EAF2 radius 10
    
    #### Text: id src_save_label characters «Save» name «Save Label» 74 by 40 position 0 0 color #31516E size 14 key app.source.save semibold font «Inter» text-align center text-valign center
    
    ### Frame: id src_reset name «Reset Button» 74 by 40 position 330 28 color #F6FAFE stroke #E3EAF2 radius 10
    
    #### Text: id src_reset_label characters «Reset» name «Reset Label» 74 by 40 position 0 0 color #31516E size 14 key app.source.reset semibold font «Inter» text-align center text-valign center
    
    ### Frame: id src_tabs name «Tabs» 384 by 44 position 20 84 color #F4F8FC stroke #E3EAF2 radius 12
    
    #### Text: id src_tab_md characters «Markdown» name «Tab Markdown» width hug height hug position 20 15 color §color.muted size 14 key app.source.tab.markdown weight 500 font «Inter» autosize both
    
    #### Text: id src_tab_res characters «Resources» name «Tab Resources» width hug height hug position 140 15 color §color.muted size 14 key app.source.tab.resources weight 500 font «Inter» autosize both
    
    #### Text: id src_tab_layers characters «Layers» name «Tab Layers» width hug height hug position 285 15 color §color.accent size 14 key app.source.tab.layers bold font «Inter» autosize both
    
    #### Rectangle: id src_tab_underline name «Tab Underline» 58 by 3 position 283 36 color §color.accent radius 2
    
    ### Rectangle: id src_row_hl name «Row Highlight» 392 by 28 position 16 145 color #EAF4FF radius 8
    
    ### Rectangle: id src_ic0 name «Layer Icon 0» 14 by 14 position 26 149 color #B9D9FF radius 4
    
    ### Text: id src_lb0 characters «Mission Overview» name «Layer Label 0» width hug height hug position 50 149 color §color.accent size 13 key app.layer.overview semibold font «Inter» autosize both
    
    ### Rectangle: id src_ic1 name «Layer Icon 1» 14 by 14 position 46 179 color #D9E5F1 radius 4
    
    ### Text: id src_lb1 characters «Cards» name «Layer Label 1» width hug height hug position 70 179 color §color.text size 13 key app.layer.cards semibold font «Inter» autosize both
    
    ### Rectangle: id src_ic2 name «Layer Icon 2» 14 by 14 position 66 209 color #D9E5F1 radius 4
    
    ### Text: id src_lb2 characters «Card 3» name «Layer Label 2» width hug height hug position 90 209 color §color.text size 13 key app.layer.card3 weight 400 font «Inter» autosize both
    
    ### Rectangle: id src_ic3 name «Layer Icon 3» 14 by 14 position 66 239 color #D9E5F1 radius 4
    
    ### Text: id src_lb3 characters «card_2» name «Layer Label 3» width hug height hug position 90 239 color §color.text size 13 key app.layer.card2 weight 400 font «Inter» autosize both
    
    ### Rectangle: id src_ic4 name «Layer Icon 4» 14 by 14 position 66 269 color #D9E5F1 radius 4
    
    ### Text: id src_lb4 characters «Card 1» name «Layer Label 4» width hug height hug position 90 269 color §color.text size 13 key app.layer.card1 weight 400 font «Inter» autosize both
    
    ### Rectangle: id src_ic5 name «Layer Icon 5» 14 by 14 position 66 299 color #D8DFE8 radius 3
    
    ### Text: id src_lb5 characters «Wide block» name «Layer Label 5» width hug height hug position 90 299 color §color.text size 13 key app.layer.wideblock weight 400 font «Inter» autosize both
    
    ### Rectangle: id src_ic6 name «Layer Icon 6» 14 by 14 position 46 329 color #D9E5F1 radius 4
    
    ### Text: id src_lb6 characters «Tiles» name «Layer Label 6» width hug height hug position 70 329 color §color.text size 13 key app.layer.tiles semibold font «Inter» autosize both
    
    ### Rectangle: id src_ic7 name «Layer Icon 7» 14 by 14 position 66 359 color #D9E5F1 radius 4
    
    ### Text: id src_lb7 characters «Tile 3» name «Layer Label 7» width hug height hug position 90 359 color §color.text size 13 key app.layer.tile3 weight 400 font «Inter» autosize both
    
    ### Rectangle: id src_ic8 name «Layer Icon 8» 14 by 14 position 66 389 color #D9E5F1 radius 4
    
    ### Text: id src_lb8 characters «Tile 2» name «Layer Label 8» width hug height hug position 90 389 color §color.text size 13 key app.layer.tile2 weight 400 font «Inter» autosize both
    
    ### Rectangle: id src_ic9 name «Layer Icon 9» 14 by 14 position 66 419 color #D9E5F1 radius 4
    
    ### Text: id src_lb9 characters «Tile 1» name «Layer Label 9» width hug height hug position 90 419 color §color.text size 13 key app.layer.tile1 weight 400 font «Inter» autosize both
    
    ### Text: id src_screens_h characters «Screens» name «Screens Heading» width hug height hug position 24 462 color §color.text size 17 key app.source.screens bold font «Inter» autosize both
    
    ### Frame: id src_add name «Add Screen» 34 by 34 position 368 458 color #F6FAFE stroke #E3EAF2 radius 10
    
    #### Text: id src_add_plus characters «+» name «Add Plus» 34 by 34 position 0 -1 color §color.muted size 20 key app.source.add weight 500 font «Inter» text-align center text-valign center
    
    ### Frame: id src_card1 name «Screen Card 1» 384 by 78 position 16 500 color #EAF4FF stroke #1E88FF radius 14
    
    #### Rectangle: id src_card1_thumb name «Card Thumb 1» 58 by 42 position 18 18 color #EAF2FA stroke #E3EAF2 radius 8
    
    #### Text: id src_card1_name characters «Mission Overview» name «Card Name 1» width hug height hug position 92 17 color §color.text size 15 key app.screen.overview.name semibold font «Inter» autosize both
    
    #### Text: id src_card1_dims characters «1440 x 1024» name «Card Dims 1» width hug height hug position 92 44 color §color.muted size 12 key app.screen.overview.dims weight 400 font «Inter» autosize both
    
    #### Ellipse: id src_card1_dot name «Card Status 1» 12 by 12 position 350 33 color #17C46B
    
    ### Frame: id src_card2 name «Screen Card 2» 384 by 78 position 16 588 color #FDFEFF stroke #E3EAF2 radius 14
    
    #### Rectangle: id src_card2_thumb name «Card Thumb 2» 58 by 42 position 18 18 color #EAF2FA stroke #E3EAF2 radius 8
    
    #### Text: id src_card2_name characters «Telemetry» name «Card Name 2» width hug height hug position 92 17 color §color.text size 15 key app.screen.telemetry.name semibold font «Inter» autosize both
    
    #### Text: id src_card2_dims characters «1440 x 1024» name «Card Dims 2» width hug height hug position 92 44 color §color.muted size 12 key app.screen.telemetry.dims weight 400 font «Inter» autosize both
    
    #### Ellipse: id src_card2_dot name «Card Status 2» 12 by 12 position 350 33 color #FFB800
    
    ### Frame: id src_card3 name «Screen Card 3» 384 by 78 position 16 676 color #FDFEFF stroke #E3EAF2 radius 14
    
    #### Rectangle: id src_card3_thumb name «Card Thumb 3» 58 by 42 position 18 18 color #EAF2FA stroke #E3EAF2 radius 8
    
    #### Text: id src_card3_name characters «Event Log» name «Card Name 3» width hug height hug position 92 17 color §color.text size 15 key app.screen.eventlog.name semibold font «Inter» autosize both
    
    #### Text: id src_card3_dims characters «1440 x 1024» name «Card Dims 3» width hug height hug position 92 44 color §color.muted size 12 key app.screen.eventlog.dims weight 400 font «Inter» autosize both
    
    #### Ellipse: id src_card3_dot name «Card Status 3» 12 by 12 position 350 33 color #FF1D1D
    
    ### Frame: id src_card4 name «Screen Card 4» 384 by 78 position 16 764 color #FDFEFF stroke #E3EAF2 radius 14
    
    #### Rectangle: id src_card4_thumb name «Card Thumb 4» 58 by 42 position 18 18 color #EAF2FA stroke #E3EAF2 radius 8
    
    #### Text: id src_card4_name characters «Shapes Showcase» name «Card Name 4» width hug height hug position 92 17 color §color.text size 15 key app.screen.shapes.name semibold font «Inter» autosize both
    
    #### Text: id src_card4_dims characters «1440 x 1024» name «Card Dims 4» width hug height hug position 92 44 color §color.muted size 12 key app.screen.shapes.dims weight 400 font «Inter» autosize both
    
    #### Ellipse: id src_card4_dot name «Card Status 4» 12 by 12 position 350 33 color #17C46B
    
    ## Frame: id cv_panel name «Canvas» 596 by 944 position 480 40 color #FBFDFF stroke #E3EAF2 radius 16 clip
    
    ### Text: id cv_title characters «Canvas — Mission Overview» name «Canvas Title» width hug height hug position 24 26 color §color.text size 20 key app.canvas.title bold font «Inter» autosize both
    
    ### Frame: id cv_tool1 name «Tool 1» 40 by 40 position 452 20 color #F6FAFE stroke #E3EAF2 radius 10
    
    #### Text: id cv_tool1_g characters «</>» name «Tool Glyph 1» 40 by 40 position 0 0 color §color.muted size 15 key app.canvas.tool.code semibold font «Inter» text-align center text-valign center
    
    ### Frame: id cv_tool2 name «Tool 2» 40 by 40 position 500 20 color #F6FAFE stroke #E3EAF2 radius 10
    
    #### Text: id cv_tool2_g characters «≡» name «Tool Glyph 2» 40 by 40 position 0 0 color §color.muted size 17 key app.canvas.tool.tune semibold font «Inter» text-align center text-valign center
    
    ### Frame: id cv_tool3 name «Tool 3» 40 by 40 position 548 20 color #F6FAFE stroke #E3EAF2 radius 10
    
    #### Text: id cv_tool3_g characters «⤢» name «Tool Glyph 3» 40 by 40 position 0 0 color §color.muted size 16 key app.canvas.tool.fit semibold font «Inter» text-align center text-valign center
    
    ### Rectangle: id cv_surface name «Canvas Surface» 556 by 764 position 20 76 color #F4F8FC stroke #E3EAF2 radius 16
    
    ### Frame: id cv_dev name «Device Toggle» 210 by 52 position 20 872 color #F6FAFE stroke #E3EAF2 radius 14
    
    #### Rectangle: id cv_dev_hl name «PC Highlight» 60 by 36 position 8 8 color #DCEEFF radius 10
    
    #### Text: id cv_dev_pc characters «PC» name «Seg PC» 64 by 52 position 6 0 color §color.accent size 14 key app.canvas.device.pc bold font «Inter» text-align center text-valign center
    
    #### Text: id cv_dev_mob characters «MOB» name «Seg MOB» 64 by 52 position 74 0 color §color.muted size 13 key app.canvas.device.mob weight 500 font «Inter» text-align center text-valign center
    
    #### Text: id cv_dev_tab characters «TAB» name «Seg TAB» 64 by 52 position 142 0 color §color.muted size 13 key app.canvas.device.tab weight 500 font «Inter» text-align center text-valign center
    
    ### Frame: id cv_mode name «Mode Toggle» 150 by 52 position 242 872 color #F6FAFE stroke #E3EAF2 radius 14
    
    #### Rectangle: id cv_mode_hl name «Canvas Highlight» 78 by 40 position 6 6 color #1E88FF radius 10
    
    #### Text: id cv_mode_canvas characters «Canvas» name «Seg Canvas» 78 by 52 position 6 0 color #FFFFFF size 14 key app.canvas.mode.canvas semibold font «Inter» text-align center text-valign center
    
    #### Text: id cv_mode_scene characters «Scene» name «Seg Scene» 60 by 52 position 86 0 color §color.muted size 14 key app.canvas.mode.scene weight 500 font «Inter» text-align center text-valign center
    
    ### Frame: id cv_zoom name «Zoom Control» 172 by 52 position 404 872 color #F6FAFE stroke #E3EAF2 radius 14
    
    #### Text: id cv_zoom_minus characters «−» name «Zoom Minus» 44 by 52 position 4 0 color §color.text size 20 key app.canvas.zoom.minus semibold font «Inter» text-align center text-valign center
    
    #### Text: id cv_zoom_val characters «37%» name «Zoom Value» 74 by 52 position 50 0 color §color.text size 14 key app.canvas.zoom.value weight 500 font «Inter» text-align center text-valign center
    
    #### Text: id cv_zoom_plus characters «+» name «Zoom Plus» 44 by 52 position 124 0 color §color.text size 20 key app.canvas.zoom.plus semibold font «Inter» text-align center text-valign center
    
    ## Frame: id in_panel name «Inspector» 300 by 944 position 1092 40 color #FBFDFF stroke #E3EAF2 radius 16 clip
    
    ### Text: id in_title characters «Inspector» name «Inspector Title» width hug height hug position 20 24 color §color.text size 22 key app.inspector.title bold font «Inter» autosize both
    
    ### Text: id in_tab_design characters «Design» name «Tab Design» width hug height hug position 20 68 color §color.accent size 14 key app.inspector.tab.design bold font «Inter» autosize both
    
    ### Text: id in_tab_proto characters «Prototype» name «Tab Prototype» width hug height hug position 96 68 color §color.muted size 14 key app.inspector.tab.prototype weight 500 font «Inter» autosize both
    
    ### Text: id in_tab_comments characters «Comments» name «Tab Comments» width hug height hug position 192 68 color §color.muted size 14 key app.inspector.tab.comments weight 500 font «Inter» autosize both
    
    ### Rectangle: id in_tab_ul name «Inspector Tab Underline» 54 by 3 position 18 92 color §color.accent radius 2
    
    ### Rectangle: id in_div1 name «Header Divider» 262 by 1 position 18 106 color §color.line
    
    ### Text: id in_node characters «Mission Overview» name «Node Name» width hug height hug position 20 122 color §color.text size 16 key app.inspector.node bold font «Inter» autosize both
    
    ### Frame: id in_dup name «Dup Button» 30 by 30 position 214 116 color #F6FAFE stroke #E3EAF2 radius 8
    
    #### Rectangle: id in_dup_icon name «Dup Icon» 12 by 12 position 9 9 stroke §color.muted radius 3
    
    ### Frame: id in_del name «Del Button» 30 by 30 position 250 116 color #F6FAFE stroke #E3EAF2 radius 8
    
    #### Rectangle: id in_del_icon name «Del Icon» 12 by 12 position 9 9 stroke §color.muted radius 3
    
    ### Text: id in_pos_h characters «Position» name «Position Heading» width hug height hug position 20 168 color §color.text size 15 key app.inspector.position bold font «Inter» autosize both
    
    ### Text: id in_pos_lbl characters «Position» name «Position Label» width hug height hug position 20 200 color §color.muted size 12 key app.inspector.position.label weight 500 font «Inter» autosize both
    
    ### Frame: id in_x name «X Field» 126 by 36 position 20 220 color #F6FAFE stroke #E3EAF2 radius 8
    
    #### Text: id in_x_val characters «X      72» name «X Value» width hug height hug position 12 10 color §color.text size 13 key app.inspector.x weight 500 font «Inter» autosize both
    
    ### Frame: id in_y name «Y Field» 126 by 36 position 154 220 color #F6FAFE stroke #E3EAF2 radius 8
    
    #### Text: id in_y_val characters «Y      72» name «Y Value» width hug height hug position 12 10 color §color.text size 13 key app.inspector.y weight 500 font «Inter» autosize both
    
    ### Text: id in_con_lbl characters «Constraints» name «Constraints Label» width hug height hug position 20 268 color §color.muted size 12 key app.inspector.constraints weight 500 font «Inter» autosize both
    
    ### Frame: id in_conh name «Constraint H» 160 by 36 position 20 288 color #F6FAFE stroke #E3EAF2 radius 8
    
    #### Text: id in_conh_val characters «↔   Left» name «Constraint H Value» width hug height hug position 12 10 color §color.text size 13 key app.inspector.constraint.h weight 500 font «Inter» autosize both
    
    ### Frame: id in_conv name «Constraint V» 160 by 36 position 20 330 color #F6FAFE stroke #E3EAF2 radius 8
    
    #### Text: id in_conv_val characters «↕   Top» name «Constraint V Value» width hug height hug position 12 10 color §color.text size 13 key app.inspector.constraint.v weight 500 font «Inter» autosize both
    
    ### Frame: id in_condiag name «Constraint Diagram» 90 by 78 position 190 288 color #F6FAFE stroke #E3EAF2 radius 10
    
    #### Rectangle: id in_condiag_v name «Constraint Bar V» 2 by 50 position 44 14 color #D8DFE8
    
    #### Rectangle: id in_condiag_h name «Constraint Bar H» 54 by 2 position 18 38 color §color.accent
    
    ### Rectangle: id in_div2 name «Section Divider» 262 by 1 position 18 400 color §color.line
    
    ### Text: id in_lay_h characters «Layout» name «Layout Heading» width hug height hug position 20 418 color §color.text size 15 key app.inspector.layout bold font «Inter» autosize both
    
    ### Text: id in_dim_lbl characters «Dimensions» name «Dimensions Label» width hug height hug position 20 450 color §color.muted size 12 key app.inspector.dimensions weight 500 font «Inter» autosize both
    
    ### Frame: id in_w name «W Field» 126 by 36 position 20 470 color #F6FAFE stroke #E3EAF2 radius 8
    
    #### Text: id in_w_val characters «W   1440» name «W Value» width hug height hug position 12 10 color §color.text size 13 key app.inspector.w weight 500 font «Inter» autosize both
    
    ### Frame: id in_h name «H Field» 126 by 36 position 154 470 color #F6FAFE stroke #E3EAF2 radius 8
    
    #### Text: id in_h_val characters «H   1024» name «H Value» width hug height hug position 12 10 color §color.text size 13 key app.inspector.h weight 500 font «Inter» autosize both
    
    ### Text: id in_res_lbl characters «Resizing» name «Resizing Label» width hug height hug position 20 518 color §color.muted size 12 key app.inspector.resizing weight 500 font «Inter» autosize both
    
    ### Frame: id in_rw name «Resize W» 126 by 36 position 20 538 color #F6FAFE stroke #E3EAF2 radius 8
    
    #### Text: id in_rw_val characters «Fixed        ⌄» name «Resize W Value» width hug height hug position 12 10 color §color.text size 13 key app.inspector.resize.w weight 500 font «Inter» autosize both
    
    ### Frame: id in_rh name «Resize H» 126 by 36 position 154 538 color #F6FAFE stroke #E3EAF2 radius 8
    
    #### Text: id in_rh_val characters «Fixed        ⌄» name «Resize H Value» width hug height hug position 12 10 color §color.text size 13 key app.inspector.resize.h weight 500 font «Inter» autosize both
    
    ### Text: id in_al_lbl characters «Auto layout» name «Auto Layout Label» width hug height hug position 20 586 color §color.muted size 12 key app.inspector.autolayout weight 500 font «Inter» autosize both
    
    ### Frame: id in_al name «Auto Layout Toggle» 260 by 40 position 20 606 color #F4F8FC stroke #E3EAF2 radius 10
    
    #### Rectangle: id in_al_hl name «Free Highlight» 61 by 32 position 4 4 color #FFFFFF stroke #B9D9FF radius 8
    
    #### Text: id in_al_free characters «Free» name «Seg Free» 61 by 40 position 4 0 color §color.accent size 13 key app.inspector.autolayout.free bold font «Inter» text-align center text-valign center
    
    #### Text: id in_al_vert characters «Vert» name «Seg Vert» 61 by 40 position 67 0 color §color.muted size 13 key app.inspector.autolayout.vert weight 500 font «Inter» text-align center text-valign center
    
    #### Text: id in_al_hori characters «Hori» name «Seg Hori» 61 by 40 position 130 0 color §color.muted size 13 key app.inspector.autolayout.hori weight 500 font «Inter» text-align center text-valign center
    
    #### Text: id in_al_grid characters «Grid» name «Seg Grid» 61 by 40 position 193 0 color §color.muted size 13 key app.inspector.autolayout.grid weight 500 font «Inter» text-align center text-valign center
    
    ### Text: id in_gap_lbl characters «Gap» name «Gap Label» width hug height hug position 20 668 color §color.muted size 12 key app.inspector.gap weight 500 font «Inter» autosize both
    
    ### Frame: id in_gap name «Gap Field» 210 by 36 position 70 662 color #F6FAFE stroke #E3EAF2 radius 8
    
    #### Text: id in_gap_val characters «0» name «Gap Value» width hug height hug position 12 10 color §color.text size 13 key app.inspector.gap.value weight 500 font «Inter» autosize both
    
    ### Text: id in_pad_lbl characters «Padding» name «Padding Label» width hug height hug position 20 716 color §color.muted size 12 key app.inspector.padding weight 500 font «Inter» autosize both
    
    ### Frame: id in_pad name «Padding Field» 260 by 36 position 20 736 color #F6FAFE stroke #E3EAF2 radius 8
    
    #### Text: id in_pad_val characters «0          0          0          0» name «Padding Value» width hug height hug position 12 10 color §color.muted size 13 key app.inspector.padding.value weight 500 font «Inter» autosize both
    """,
)