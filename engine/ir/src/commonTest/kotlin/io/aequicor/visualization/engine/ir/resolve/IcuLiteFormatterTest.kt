package io.aequicor.visualization.engine.ir.resolve

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IcuLiteFormatterTest {

    private val ruPlural =
        "{count, plural, one {# миссия} few {# миссии} many {# миссий} other {# миссии}}"

    private fun format(
        message: String,
        params: Map<String, String>,
        locale: String,
        diagnostics: MutableList<String> = mutableListOf(),
    ): String = IcuLiteFormatter.format(message, params, locale) { diagnostics += it }

    @Test
    fun russianPluralCategories() {
        val expectations = mapOf(
            "1" to "1 миссия",
            "2" to "2 миссии",
            "5" to "5 миссий",
            "21" to "21 миссия",
            "12" to "12 миссий",
            "104" to "104 миссии",
            "111" to "111 миссий",
        )
        expectations.forEach { (count, expected) ->
            assertEquals(expected, format(ruPlural, mapOf("count" to count), "ru-RU"), "count=$count")
        }
    }

    @Test
    fun russianFractionUsesOtherCategory() {
        assertEquals("1.5 миссии", format(ruPlural, mapOf("count" to "1.5"), "ru-RU"))
    }

    @Test
    fun englishPluralCategories() {
        val message = "{count, plural, one {# mission} other {# missions}}"
        assertEquals("1 mission", format(message, mapOf("count" to "1"), "en-US"))
        assertEquals("5 missions", format(message, mapOf("count" to "5"), "en-US"))
        assertEquals("0 missions", format(message, mapOf("count" to "0"), "en-US"))
    }

    @Test
    fun exactMatchWinsOverCategory() {
        val message = "{count, plural, =0 {none} one {# item} other {# items}}"
        assertEquals("none", format(message, mapOf("count" to "0"), "en-US"))
        assertEquals("1 item", format(message, mapOf("count" to "1"), "en-US"))
    }

    @Test
    fun substitutesParams() {
        assertEquals(
            "Привет, Мир! Добро пожаловать, Мир.",
            format("Привет, {name}! Добро пожаловать, {name}.", mapOf("name" to "Мир"), "ru-RU"),
        )
    }

    @Test
    fun missingParamKeepsPlaceholderVerbatim() {
        val diagnostics = mutableListOf<String>()
        assertEquals("Hello, {name}!", format("Hello, {name}!", emptyMap(), "en-US", diagnostics))
        assertTrue(diagnostics.isEmpty(), "simple substitution never reports: $diagnostics")
    }

    @Test
    fun selectPicksBranchAndFallsBackToOther() {
        val message = "{gender, select, male {He} female {She} other {They}}"
        assertEquals("She", format(message, mapOf("gender" to "female"), "en-US"))
        assertEquals("They", format(message, mapOf("gender" to "robot"), "en-US"))
    }

    @Test
    fun nestedArgumentsInsideBranches() {
        val message = "{count, plural, one {{name} has # task} other {{name} has # tasks}}"
        assertEquals("Ada has 2 tasks", format(message, mapOf("count" to "2", "name" to "Ada"), "en-US"))
        assertEquals("Ada has 1 task", format(message, mapOf("count" to "1", "name" to "Ada"), "en-US"))
    }

    @Test
    fun wholeNumbersFormatWithoutDecimalPoint() {
        val message = "{count, plural, other {value #}}"
        assertEquals("value 4", format(message, mapOf("count" to "4.0"), "en-US"))
        assertEquals("value 2.5", format(message, mapOf("count" to "2.5"), "en-US"))
    }

    @Test
    fun malformedMessageReturnsRawWithDiagnostic() {
        val diagnostics = mutableListOf<String>()
        val raw = "{count, plural, one {"
        assertEquals(raw, format(raw, mapOf("count" to "1"), "en-US", diagnostics))
        assertTrue(diagnostics.any { "Malformed ICU message" in it }, "expected a diagnostic: $diagnostics")
    }

    @Test
    fun unsupportedArgumentTypeReturnsRawWithDiagnostic() {
        val diagnostics = mutableListOf<String>()
        val raw = "{when, date, short}"
        assertEquals(raw, format(raw, mapOf("when" to "now"), "en-US", diagnostics))
        assertTrue(diagnostics.any { "unsupported argument type" in it }, "expected a diagnostic: $diagnostics")
    }

    @Test
    fun missingPluralParamReportsAndUsesOther() {
        val diagnostics = mutableListOf<String>()
        val message = "{count, plural, one {# mission} other {# missions}}"
        assertEquals("# missions", format(message, emptyMap(), "en-US", diagnostics))
        assertTrue(diagnostics.any { "count" in it }, "expected a diagnostic: $diagnostics")
    }
}
