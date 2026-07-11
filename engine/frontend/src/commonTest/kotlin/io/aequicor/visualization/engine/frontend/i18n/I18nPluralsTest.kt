package io.aequicor.visualization.engine.frontend.i18n

import io.aequicor.visualization.engine.frontend.SlmLocale
import io.aequicor.visualization.engine.frontend.compileSlm
import io.aequicor.visualization.engine.ir.model.Bindable
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class I18nPluralsTest {
    @Test
    fun icuPluralMessagesPassThroughVerbatim() {
        val icu = "{count, plural, one {# миссия} few {# миссии} many {# миссий} other {# миссии}}"
        val result = compileSlm(
            """
            ---
            screen: s
            sourceLocale: ru-RU
            ---

            # Экран

            ## Счётчик

            Text «$icu» key missions.count
            """.trimIndent() + "\n",
        )
        val bundle = assertNotNull(result.resources[SlmLocale("ru-RU")])
        assertEquals(icu, bundle["missions.count"])
        assertTrue(result.diagnostics.none { "ICU" in it.message || "braces" in it.message })
    }

    @Test
    fun inlineBindingsBecomeParams() {
        val result = compileSlm(
            """
            ---
            screen: s
            sourceLocale: ru-RU
            ---

            # Экран

            Всего {{missions.length}} миссий
            """.trimIndent() + "\n",
        )
        val bundle = assertNotNull(result.resources[SlmLocale("ru-RU")])
        assertEquals("Всего {missionsLength} миссий", bundle["s.text"])

        val text = assertNotNull(result.document).pages.single().children.single().children.single()
        val kind = assertIs<DesignNodeKind.Text>(text.kind)
        assertEquals("s.text", kind.content?.key)
        val param = assertIs<Bindable.DataRef>(kind.content?.params?.get("missionsLength"))
        assertEquals("missions.length", param.expression.raw)
    }

    @Test
    fun missingRuPluralCategoriesWarn() {
        val result = compileSlm(
            """
            ---
            screen: s
            sourceLocale: ru-RU
            ---

            # Экран

            ## Счётчик

            Text «{count, plural, one {# миссия} other {# миссии}}» key missions.count
            """.trimIndent() + "\n",
        )
        val warning = result.diagnostics.single { "ICU plural" in it.message }
        assertTrue("few" in warning.message && "many" in warning.message)
    }

    @Test
    fun enPluralRequiresOnlyOneAndOther() {
        val result = compileSlm(
            """
            ---
            screen: s
            sourceLocale: en-US
            ---

            # Screen

            ## Counter

            Text «{count, plural, one {# mission} other {# missions}}» key missions.count
            """.trimIndent() + "\n",
        )
        assertTrue(result.diagnostics.none { "ICU plural" in it.message })
    }

    @Test
    fun unbalancedBracesWarn() {
        val result = compileSlm(
            """
            ---
            screen: s
            sourceLocale: en-US
            ---

            # Screen

            ## Counter

            Text «{count, plural, one {# mission other {# missions}}» key broken.count
            """.trimIndent() + "\n",
        )
        assertTrue(result.diagnostics.any { "Unbalanced braces" in it.message && "broken.count" in it.message })
    }
}
