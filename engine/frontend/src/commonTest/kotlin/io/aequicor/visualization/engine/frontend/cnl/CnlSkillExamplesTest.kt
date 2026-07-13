package io.aequicor.visualization.engine.frontend.cnl

import io.aequicor.visualization.engine.frontend.compileSlm
import io.aequicor.visualization.engine.ir.model.DesignSeverity
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Compilation guard for representative source blocks published by the canonical
 * `SKILLS/SLM.md`, `SLM-vector-graphics.md`, and `SLM-typography.md` skills.
 * Diagram and annotation skill examples have their own subsystem round-trip suites.
 */
class CnlSkillExamplesTest {

    private fun assertCompilesClean(source: String) {
        val result = compileSlm(source.trimIndent() + "\n")
        assertNotNull(result.document, "skill example must produce a document: ${result.diagnostics}")
        assertTrue(
            result.diagnostics.none { it.severity == DesignSeverity.Error },
            "skill example must compile without errors:\n$source\n-> ${result.diagnostics}",
        )
        assertTrue(
            result.diagnostics.none { "[CNL:" in it.message },
            "skill example must not rely on dropped/unknown CNL:\n$source\n-> ${result.diagnostics}",
        )
    }

    @Test
    fun canonicalMinimalScreenCompiles() {
        assertCompilesClean(
            """
            ---
            screen: sampleScreen
            page: Product
            sourceLocale: en-US
            targetLocales: [en-US]
            theme: light
            density: compact
            platform: web
            frame: { preset: desktop-1440, width: 1440, height: 1024 }
            ---

            # Sample Screen

            ## AutoLayout: App Shell id app_shell column width (fill) height (fill) padding 24 gap 16 clip overflow (y auto) color #FFFFFF

            Text id page_title «Sample Screen» key sample.title font «Inter» bold size 24 line-height 32 width (fill) height (hug) maxLines 1
            """,
        )
    }

    @Test
    fun vectorNetworkAndBooleanExamplesCompile() {
        assertCompilesClean(
            """
            ---
            screen: vectorSample
            ---

            # Vector Sample

            ## AutoLayout: Figures id figures column gap 16

            Vector id editable_glyph 160 by 160 color #2F9E44 viewbox (0 0 24 24) network (vertex (12 2 in (-7 -3) out (7 3) mirror angleAndLength) vertex (22 20 corner radius 2) vertex (2 20 corner radius 2) segment (0 1) segment (1 2) segment (2 0) region loops (0 1 2) fill #2F9E44)

            ### Vector: Combined Mark id combined_mark 160 by 160 color #E64980 boolean union

            Ellipse id union_left 90 by 90 position 12 40 absolute
            Ellipse id union_right 90 by 90 position 58 40 absolute
            """,
        )
    }

    @Test
    fun typographyAndRichTextExampleCompiles() {
        assertCompilesClean(
            """
            ---
            screen: typographySample
            sourceLocale: en-US
            targetLocales: [en-US]
            frame: { width: 720, height: 480 }
            ---

            # Styles

            TextStyle typography.heading font «Inter» size 24 weight 700 line-height 32
            TextStyle typography.body font «Inter» size 14 weight 400 line-height 20
            TextStyle typography.emphasis font «Inter» size 14 weight 600 line-height 20

            # Typography Sample

            ## AutoLayout: Content id content column width (fill) height (hug) padding 24 gap 12

            Text id title «Mission Control» key sample.title text-style ${'$'}typography.heading width (fill) height (hug) maxLines 1
            Text id body «Read the mission brief» key sample.body text-style ${'$'}typography.body span (range (5 12) style typography.emphasis) link (range (5 12) to brief_screen) width (fill) height (hug) maxLines 2
            Text id status characters {{mission.status}} text-style ${'$'}typography.body width (fill) height (hug) maxLines 1
            Frame id brief_screen visible no
            """,
        )
    }
}
