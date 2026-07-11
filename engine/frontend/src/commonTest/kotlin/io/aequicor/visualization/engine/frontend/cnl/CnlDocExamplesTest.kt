package io.aequicor.visualization.engine.frontend.cnl

import io.aequicor.visualization.engine.frontend.compileSlm
import io.aequicor.visualization.engine.ir.model.DesignSeverity
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Doc-examples guard: the CNL examples published in
 * `design-book/semantic-layout-markdown-i18n.md` must compile without error
 * diagnostics. Keep this file in sync when editing the spec's examples.
 */
class CnlDocExamplesTest {

    private fun assertCompilesClean(body: String) {
        val source = """
            ---
            screen: docExamples
            ---

            # Doc Examples

        """.trimIndent() + "\n" + body + "\n"
        val result = compileSlm(source)
        assertNotNull(result.document, "document must compile: ${result.diagnostics}")
        assertTrue(
            result.diagnostics.none { it.severity == DesignSeverity.Error },
            "doc example must compile without errors:\n$body\n-> ${result.diagnostics}",
        )
    }

    @Test
    fun layoutSchemaExamples() {
        assertCompilesClean(
            """
            ## Frame: Mission Detail Panel column padding (20 24) gap (row 16 column 8) align (inline stretch) width (fill min 320 max 520) height (hug) clip overflow (x hidden y auto) scroll (direction vertical fixedChildren (missionPanelHeader))

            Ellipse 8 by 8 absolute anchor (inlineEnd 4 blockStart 4) constraints (horizontal right vertical top)
            """.trimIndent(),
        )
    }

    @Test
    fun responsiveAndGridOverlayExamples() {
        assertCompilesClean(
            """
            ## Frame: Card row gap 16 when (breakpoint mobile) column padding (16) gap 12 radius 0 when (breakpoint desktop density compact) row gap 8
            """.trimIndent(),
        )
        assertCompilesClean(
            """
            ## Frame: Canvas guides (vertical 72) grids (columns count 12 gutter 24 margin 72 alignment stretch)
            """.trimIndent(),
        )
    }

    @Test
    fun componentAndInstanceExamples() {
        assertCompilesClean(
            """
            ## Panel column gap 12

            Instance of ds/Button library ds variant (size md state default type primary) props (label «Create mission» iconLeading (swap ds/Icon/Plus) loading false) onClick navigate (missions/new)
            Instance of ds/Card override header/title (color #111111 bold) slot actions (Button «Open» color #2563EB) nested statusBadge (variant (tone warning))
            """.trimIndent(),
        )
        assertCompilesClean(
            """
            # Component: Mission Card component-name ds/MissionCard axis status (nominal warning critical) axis density (compact comfortable) prop title (text default «Mission name») prop showBadge (boolean default true) prop icon (instanceSwap preferred (ds/Icon/Rocket ds/Icon/Alert)) prop actions (slot min 0 max 3)
            """.trimIndent(),
        )
    }

    @Test
    fun variablesStylesAndBindingExamples() {
        assertCompilesClean(
            """
            # Collection theme (modes light dark default light)

            Color color.surface light #FFFFFF dark #101114
            Number radius.card light 8 dark 8

            # Collection density (modes compact comfortable default compact)

            Number space.4 compact 12 comfortable 16

            ## Panel column

            Frame styles (fill color.surface.default text typography.heading.lg effect shadow.card grid grid.desktop.12)
            Frame color ${'$'}color.surface radius ${'$'}radius.card gap ${'$'}space.4
            """.trimIndent(),
        )
    }

    @Test
    fun textAndRichTextExamples() {
        assertCompilesClean(
            """
            ## Header column gap 8

            Text «Mission Control» key missionDashboard.title size 24 bold font «Inter» line-height 32 text-align left text-valign center autosize both truncate 1 text-style ${'$'}typography.heading.lg
            Text «Read the mission brief» key header.brief size 14 span (range (5 12) style typography.link) link (range (5 12) url «https://example.com/brief»)
            """.trimIndent(),
        )
    }

    @Test
    fun visualStyleShapesAndMediaExamples() {
        assertCompilesClean(
            """
            ## Frame: Card color ${'$'}color.surface gradient (linear stops (${'$'}color.accent.start at 0) (${'$'}color.accent.end at 1) opacity 0.12) stroke ${'$'}color.border.subtle 2 effect (dropShadow color #0F172A offset (0 8) blur 24 spread 0) radius 12 smoothing 0.6 opacity 0.96

            Ellipse 10 by 10 color ${'$'}color.status.success
            Star 24 by 24 points 5 inner 0.5 color #F59E0B
            Ellipse 64 by 64 arc (-90 270) inner 0.5
            Icon icon ds/Icon/Alert svg assets/icons/alert.svg viewbox (0 0 24 24) color ${'$'}color.icon.warning
            Icon path «M12 2L22 20H2L12 2Z»
            Icon path «M0 0 L24 0 L24 24 Z» evenodd
            Rectangle 96 by 96 mask alpha clips (avatarImage)
            Image media (asset assets/mission-map.png crop focus (0.48 0.42) alt «Active missions map» replaceable)
            Image media (asset assets/launch-preview.mp4 video fit poster assets/launch-preview-poster.jpg autoplay loop)
            """.trimIndent(),
        )
    }

    @Test
    fun interactionsMotionAndPrototypeVariableExamples() {
        assertCompilesClean(
            """
            # Prototype Variables

            String selectedMissionId default «»
            Boolean isCreateDialogOpen default false

            ## Actions column gap 8

            Button «Create» onClick openOverlay (createMissionDialog) overlay (position center closeOnOutside true background #00000052) animate (type smartAnimate easing easeOut duration 220)
            Button «Select» onClick setVariable (selectedMissionId) to ({{mission.id}})
            Button «Highlight» onClick changeToVariant (missionCard) variant (state selected)
            Rectangle 24 by 24 color #2563EB motion (duration 900 loop frames (at 0 opacity 0.4) (at 0.5 opacity 1) (at 1 opacity 0.4))
            """.trimIndent(),
        )
    }

    @Test
    fun handoffAndExportExamples() {
        assertCompilesClean(
            """
            ## Panel column gap 8 export (png at 2 «@2x») (svg at 1 «»)

            Rectangle 320 by 4 color #E2E8F0 radius 2
            """.trimIndent(),
        )
    }
}
