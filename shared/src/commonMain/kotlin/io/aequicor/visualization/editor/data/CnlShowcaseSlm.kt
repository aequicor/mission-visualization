package io.aequicor.visualization.editor.data

/**
 * Controlled-natural-language (CNL) showcase page: every element is authored as one
 * natural-language sentence (`noun keyword value…`), not YAML. The screen frame carries its
 * layout/background as heading properties on the H1; the elements below stack in its column.
 * Demonstrates the `engine/frontend/.../cnl` authoring surface end-to-end (English keywords;
 * the RU vocabulary is identical — see `SLM-SKILL.md`).
 */
val CnlShowcaseSlm: String = missionSlm(
    """
    ---
    screen: cnlShowcase
    page: CNL Showcase
    sourceLocale: en-US
    targetLocales: [en-US, ru-RU]
    frame: { preset: desktop-1440, width: 1440, height: 1024 }
    ---

    # CNL Showcase column gap 24 padding 48 color #0B1220

    Text «Natural-language SLM» size 32 bold color #E2E8F0
    Text «One sentence per element» size 16 color #94A3B8

    ## Card column gap 12 padding 24 color #111827 radius 16 stroke #1F2937 1 inside

    Text «Active missions» size 20 bold color #F8FAFC
    Rectangle 520 by 8 color #22C55E radius 4
    Rectangle 360 by 8 color #334155 radius 4
    Button «Create mission» size 16 bold color #22C55E

    ## Status row gap 12

    Rectangle 96 by 32 color #14532D radius 16
    Rectangle 96 by 32 color #7F1D1D radius 16
    Ellipse 40 by 40 color #2563EB
    """,
)
