package io.aequicor.visualization.engine.ir.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class TextContentTest {

    private val resources = mapOf(
        "ru-RU" to mapOf("missionDashboard.title" to "Панель миссий"),
        "en-US" to mapOf("missionDashboard.title" to "Mission Dashboard"),
    )

    @Test
    fun fallbackTextPrefersTheLocaleBundle() {
        val content = TextContent(
            key = "missionDashboard.title",
            defaultLocale = "ru-RU",
            defaultText = "Панель миссий",
        )

        assertEquals("Mission Dashboard", content.fallbackText(resources, "en-US"))
        assertEquals("Панель миссий", content.fallbackText(resources, "ru-RU"))
    }

    @Test
    fun fallbackTextUsesDefaultTextWhenKeyOrLocaleIsMissing() {
        val missingKey = TextContent(key = "missionDashboard.subtitle", defaultText = "Обзор")
        assertEquals("Обзор", missingKey.fallbackText(resources, "en-US"))

        val missingLocale = TextContent(key = "missionDashboard.title", defaultText = "Панель миссий")
        assertEquals("Панель миссий", missingLocale.fallbackText(resources, "de-DE"))
    }

    @Test
    fun paramsCarryDataBindings() {
        val content = TextContent(
            key = "missions.count",
            params = mapOf(
                "count" to Bindable.DataRef(DesignExpression("missions.length")),
                "unit" to "шт".bindable(),
            ),
        )

        val count = assertIs<Bindable.DataRef>(content.params.getValue("count"))
        assertEquals("missions.length", count.expression.raw)
        assertEquals("шт", (content.params.getValue("unit") as Bindable.Value<String>).value)
    }
}
