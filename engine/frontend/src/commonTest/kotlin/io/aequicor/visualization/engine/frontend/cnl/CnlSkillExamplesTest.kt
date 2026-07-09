package io.aequicor.visualization.engine.frontend.cnl

import io.aequicor.visualization.engine.frontend.blocks.readers.slm
import io.aequicor.visualization.engine.frontend.compileSlm
import io.aequicor.visualization.engine.ir.model.DesignSeverity
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The golden CNL screens shipped in SLM-SKILL.md must compile with zero error diagnostics. */
class CnlSkillExamplesTest {
    private fun assertClean(source: String) {
        val result = compileSlm(slm(source) + "\n")
        assertNotNull(result.document)
        assertTrue(
            result.diagnostics.none { it.severity == DesignSeverity.Error },
            result.diagnostics.filter { it.severity == DesignSeverity.Error }.joinToString { it.message },
        )
    }

    @Test
    fun missionCard() = assertClean(
        """
        ---
        screen: missionCard
        sourceLocale: en-US
        targetLocales: [en-US]
        frame: { preset: desktop-1440, width: 1440, height: 1024 }
        ---

        # Mission Card

        ## Mission card column gap 12 padding 16 color #FFFFFF radius 12

        Text «Active missions» size 20 bold color #0F172A
        Text «12 in progress» size 14 color #64748B
        Rectangle 320 by 4 color #2563EB radius 2
        Button «Open» color #2563EB
        """,
    )

    @Test
    fun statusChips() = assertClean(
        """
        ---
        screen: statusChips
        sourceLocale: en-US
        targetLocales: [en-US]
        frame: { preset: desktop-1440, width: 1440, height: 1024 }
        ---

        # Status Chips

        ## Chips row gap 8 padding 8

        Rectangle 80 by 24 color #DCFCE7 radius 12
        Rectangle 80 by 24 color #FEE2E2 radius 12
        Text "Nominal" size 12 color #166534
        """,
    )
}
