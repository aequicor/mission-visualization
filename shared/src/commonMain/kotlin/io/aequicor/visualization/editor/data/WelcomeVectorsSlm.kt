package io.aequicor.visualization.editor.data

/**
 * Welcome screen 2: a mission night-launch scene composed entirely from the figures
 * subsystem — parametric stars, a gradient planet with a donut-ellipse ring, a crescent
 * moon as a boolean subtract, a rocket group (SVG paths + ellipse window) with a
 * flickering flame, a vector-network asteroid, an arc-pie ground dish with signal
 * arrows, and a comet on a keyframe track. Looping motion clips animate the scene;
 * `afterDelay` / nav buttons continue the welcome tour. Pure CNL.
 */
val WelcomeVectorsSlm: String = missionSlm(
    """
    ---
    screen: welcomeVectors
    page: Vectors & Objects
    sourceLocale: en-US
    theme: light
    frame:
      width: 1440
      height: 1024
    ---

    # Vectors & Objects id frame_vectors name «Vectors & Objects» 1440 by 1024 position 72 72 gradient (linear to (0 1) stops (#0B1220 at 0) (#16233F at 1)) radius 16 clip afterDelay (9000) navigate (welcomeUml) animate (type dissolve easing easeInOut duration 600)

    ## Text: id vec_title characters «Vectors & Objects» name «Title» width hug height hug position 72 60 color #E2E8F0 size 30 key welcomeVectors.heading bold font «Inter» autosize both

    ## Text: id vec_subtitle characters «A mission scene drawn one sentence per shape — stars, networks, arcs and booleans» name «Subtitle» width hug height hug position 72 104 color #94A3B8 size 15 key welcomeVectors.subtitle weight 400 font «Inter» autosize both

    ## Star: id star_1 name «Star 1» 26 by 26 position 190 220 color #F8FAFC points 4 inner 0.35 motion duration 1800 loop frames (0 opacity 0.25) (0.5 opacity 1) (1 opacity 0.25)

    ## Star: id star_2 name «Star 2» 18 by 18 position 430 168 color #E0F2FE points 4 inner 0.35 motion duration 2400 loop frames (0 opacity 1) (0.5 opacity 0.3) (1 opacity 1)

    ## Star: id star_3 name «Star 3» 22 by 22 position 964 150 color #F8FAFC points 4 inner 0.35 motion duration 2100 loop frames (0 opacity 0.3) (0.5 opacity 1) (1 opacity 0.3)

    ## Star: id star_4 name «Star 4» 16 by 16 position 1250 300 color #E0F2FE points 4 inner 0.35 motion duration 1500 loop frames (0 opacity 1) (0.5 opacity 0.25) (1 opacity 1)

    ## Star: id star_5 name «Star 5» 14 by 14 position 710 260 color #F8FAFC points 4 inner 0.35 motion duration 2700 loop frames (0 opacity 0.4) (0.5 opacity 1) (1 opacity 0.4)

    ## Ellipse: id star_dust_1 name «Dust 1» 4 by 4 position 320 340 color #64748B

    ## Ellipse: id star_dust_2 name «Dust 2» 4 by 4 position 560 180 color #64748B

    ## Ellipse: id star_dust_3 name «Dust 3» 5 by 5 position 1120 220 color #64748B

    ## Ellipse: id star_dust_4 name «Dust 4» 4 by 4 position 860 380 color #64748B

    ## Vector: id comet name «Comet» 76 by 14 position 950 210 color #BAE6FD viewbox (0 0 76 14) path «M12 1 A6 6 0 1 0 12 13 A6 6 0 1 0 12 1 Z M18 2 L76 7 L18 12 Z» motion duration 7000 loop frames (0 x 0 y 0 opacity 0) (0.15 opacity 0.9) (0.85 opacity 0.9) (1 x -460 y 150 opacity 0)

    ## Ellipse: id planet name «Planet» 320 by 320 position 120 620 gradient (radial stops (#3B82F6 at 0) (#1E3A8A at 1))

    ## Ellipse: id planet_ring name «Planet Ring» 470 by 130 position 45 715 color #93C5FD59 inner 0.86 rotation -16

    ## Frame: id satellite name «Satellite» 90 by 44 position 240 520 motion duration 5200 loop frames (0 x 0 y 0) (0.25 x 100 y 44) (0.5 x 0 y 88) (0.75 x -100 y 44) (1 x 0 y 0)

    ### Rectangle: id sat_body name «Sat Body» 26 by 18 position 32 13 color #CBD5F5 radius 4

    ### Rectangle: id sat_panel_l name «Sat Panel L» 24 by 14 position 2 15 color #2563EB radius 2

    ### Rectangle: id sat_panel_r name «Sat Panel R» 24 by 14 position 64 15 color #2563EB radius 2

    ### Line: id sat_antenna name «Sat Antenna» 2 by 12 position 44 0 stroke (color #94A3B8 weight 2 cap round)

    ## Vector: id moon name «Crescent Moon» 120 by 120 position 1150 110 color #E5E7EB boolean subtract motion duration 6400 loop frames (0 y 0) (0.5 y 8) (1 y 0)

    Ellipse id moon_base name «Moon Base» 100 by 100 position 4 12 absolute

    Ellipse id moon_bite name «Moon Bite» 92 by 92 position 34 0 absolute

    ## Vector: id asteroid name «Network Asteroid» 90 by 90 position 400 560 color #64748B viewbox (0 0 24 24) network (vertex (12 2 in (-7 -3) out (7 3) mirror angleAndLength) vertex (22 20 corner) vertex (2 20 corner) segment (0 1) segment (1 2) segment (2 0) region loops (0 1 2)) motion duration 9000 loop frames (0 rotation 0) (1 rotation 360)

    ## Frame: id rocket name «Rocket» 200 by 320 position 620 360 motion duration 3200 loop frames (0 y 0 rotation 0) (0.5 y -16 rotation 2) (1 y 0 rotation 0)

    ### Vector: id rocket_body name «Rocket Body» 100 by 140 position 50 30 viewbox (0 0 100 160) path «M50 0 C78 34 82 78 82 120 L18 120 C18 78 22 34 50 0 Z» color #E2E8F0

    ### Ellipse: id rocket_window name «Rocket Window» 28 by 28 position 86 88 color #38BDF8 stroke (color #0EA5E9 weight 3)

    ### Vector: id rocket_fin_l name «Fin Left» 40 by 70 position 24 130 viewbox (0 0 40 70) path «M40 0 L40 70 L0 70 Z» color #F87171

    ### Vector: id rocket_fin_r name «Fin Right» 40 by 70 position 136 130 viewbox (0 0 40 70) path «M0 0 L0 70 L40 70 Z» color #F87171

    ### Vector: id rocket_flame name «Flame» 44 by 72 position 78 204 viewbox (0 0 44 72) path «M22 72 C8 46 10 26 22 0 C34 26 36 46 22 72 Z» color #FB923C motion duration 700 loop frames (0 scale 1 opacity 0.9) (0.5 scale 1.25 opacity 0.55) (1 scale 1 opacity 0.9)

    ## Polygon: id peak_1 name «Peak 1» 420 by 220 position 560 830 color #101B33 points 3

    ## Polygon: id peak_2 name «Peak 2» 520 by 260 position 880 800 color #0B1424 points 3

    ## Line: id horizon name «Horizon» 1296 by 4 position 72 952 stroke (color #1E293B weight 2 cap round)

    ## Ellipse: id dish name «Ground Dish» 130 by 130 position 1080 810 color #94A3B8 arc (205 130)

    ## Rectangle: id dish_mast name «Dish Mast» 6 by 56 position 1140 900 color #64748B radius 3

    ## Arrow: id sig_1 name «Signal 1» 130 by 44 position 930 700 stroke (color #38BDF8 weight 4 cap round) rotation -150 motion duration 1800 loop frames (0 opacity 0) (0.35 opacity 1) (1 opacity 0)

    ## Arrow: id sig_2 name «Signal 2» 110 by 38 position 985 770 stroke (color #38BDF866 weight 3 cap round) rotation -150 motion duration 1800 loop frames (0 opacity 0.6) (0.5 opacity 0) (0.85 opacity 1) (1 opacity 0.6)

    ## Frame: id vec_nav_prev name «Prev Button» 170 by 44 position 72 940 color #1E293B stroke #334155 radius 22 onClick navigate (welcomeEditor) animate (type push easing easeInOut duration 500 direction right)

    ### Text: id vec_nav_prev_label characters «← Welcome» name «Prev Label» 170 by 44 position 0 0 color #E2E8F0 size 14 key welcomeVectors.nav.prev semibold font «Inter» text-align center text-valign center

    ## Frame: id vec_nav_next name «Next Button» 200 by 44 position 1168 940 color #1E88FF radius 22 onClick navigate (welcomeUml) animate (type dissolve easing easeInOut duration 500) motion duration 2400 loop frames (0 scale 1) (0.5 scale 1.05) (1 scale 1)

    ### Text: id vec_nav_next_label characters «Architecture →» name «Next Label» 200 by 44 position 0 0 color #FFFFFF size 14 key welcomeVectors.nav.next semibold font «Inter» text-align center text-valign center
    """,
)
