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
    fun missionCardRu() = assertClean(
        """
        ---
        screen: missionCard
        sourceLocale: ru-RU
        targetLocales: [ru-RU, en-US]
        frame: { preset: desktop-1440, width: 1440, height: 1024 }
        ---

        # Mission Card

        ## Карточка миссии колонка отступ 12 паддинги 16 цвет #FFFFFF радиус 12

        Текст «Активные миссии» размер 20 жирный цвет #0F172A
        Текст «12 в работе» размер 14 цвет #64748B
        Прямоугольник 320 на 4 цвет #2563EB радиус 2
        Кнопка «Открыть» цвет #2563EB
        """,
    )

    @Test
    fun statusChipsEn() = assertClean(
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
