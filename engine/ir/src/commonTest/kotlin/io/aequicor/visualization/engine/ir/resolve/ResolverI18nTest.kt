package io.aequicor.visualization.engine.ir.resolve

import io.aequicor.visualization.engine.ir.model.DesignColor
import io.aequicor.visualization.engine.ir.model.DesignSeverity
import io.aequicor.visualization.engine.ir.serialization.DesignParseResult
import io.aequicor.visualization.engine.ir.serialization.parseDesignDocument
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Stage 5.2 resolver: locale/direction from [ResolveContext] and the i18n text path. */
class ResolverI18nTest {

    private val documentJson = """
        {
          "i18n": {
            "sourceLocale": "en-US",
            "targetLocales": ["en-US", "ru-RU"],
            "resources": {
              "en-US": {
                "title": "Missions",
                "count": "{count, plural, one {# mission} other {# missions}}"
              },
              "ru-RU": {
                "title": "Миссии",
                "count": "{count, plural, one {# миссия} few {# миссии} many {# миссий} other {# миссии}}"
              }
            }
          },
          "pages": [ { "id": "p", "children": [
            { "id": "label", "type": "text",
              "characters": "Raw fallback",
              "content": { "key": "title", "defaultText": "Default title" } }
          ] } ]
        }
    """.trimIndent()

    private fun parse(json: String) =
        assertIs<DesignParseResult.Success>(parseDesignDocument(json)).document

    private fun resolvedCharacters(
        json: String,
        context: ResolveContext = ResolveContext(),
        sourceId: String = "label",
    ): Pair<String, DesignResolver> {
        val document = parse(json)
        val resolver = DesignResolver(document, context)
        val roots = resolver.resolvePage(document.pages.first())
        val text = assertNotNull(roots.firstNotNullOfOrNull { it.findText(sourceId) }, "no text for '$sourceId'")
        return text to resolver
    }

    private fun ResolvedNode.findText(targetSourceId: String): String? {
        if (sourceId == targetSourceId && text != null) return text?.characters
        return children.firstNotNullOfOrNull { it.findText(targetSourceId) }
    }

    @Test
    fun textContentResolvesAgainstTargetLocaleResources() {
        val (russian, resolver) = resolvedCharacters(documentJson, ResolveContext(locale = "ru-RU"))
        assertEquals("Миссии", russian)
        assertEquals(0, resolver.diagnostics.size, "expected no diagnostics: ${resolver.diagnostics}")
    }

    @Test
    fun defaultContextUsesDocumentSourceLocale() {
        val (english, resolver) = resolvedCharacters(documentJson)
        assertEquals("en-US", resolver.activeLocale)
        assertEquals("Missions", english)
    }

    @Test
    fun contextResourcesOverrideDocumentBundles() {
        val context = ResolveContext(
            locale = "ru-RU",
            resources = mapOf("ru-RU" to mapOf("title" to "Задачи")),
        )
        val (overridden, _) = resolvedCharacters(documentJson, context)
        assertEquals("Задачи", overridden)
    }

    @Test
    fun missingKeyFallsBackToDefaultTextWithWarning() {
        val json = documentJson.replace(""""key": "title"""", """"key": "missing.key"""")
        val (fallback, resolver) = resolvedCharacters(json, ResolveContext(locale = "ru-RU"))
        assertEquals("Default title", fallback)
        assertTrue(
            resolver.diagnostics.any {
                it.severity == DesignSeverity.Warning && "missing.key" in it.message && "defaultText" in it.message
            },
            "expected a fallback warning: ${resolver.diagnostics}",
        )
    }

    @Test
    fun missingKeyAndDefaultTextFallsBackToCharactersWithWarning() {
        val json = documentJson
            .replace(""""key": "title", "defaultText": "Default title"""", """"key": "missing.key"""")
        val (fallback, resolver) = resolvedCharacters(json, ResolveContext(locale = "ru-RU"))
        assertEquals("Raw fallback", fallback)
        assertTrue(
            resolver.diagnostics.any {
                it.severity == DesignSeverity.Warning && "missing.key" in it.message && "characters" in it.message
            },
            "expected a fallback warning: ${resolver.diagnostics}",
        )
    }

    @Test
    fun icuPluralFormatsCountParamPerLocale() {
        val json = documentJson.replace(
            """"content": { "key": "title", "defaultText": "Default title" }""",
            """"content": { "key": "count", "params": { "count": "5" } }""",
        )
        val (russian, _) = resolvedCharacters(json, ResolveContext(locale = "ru-RU"))
        assertEquals("5 миссий", russian)
        val (english, _) = resolvedCharacters(json, ResolveContext(locale = "en-US"))
        assertEquals("5 missions", english)
        val (singular, _) = resolvedCharacters(
            json.replace(""""count": "5"""", """"count": "21""""),
            ResolveContext(locale = "ru-RU"),
        )
        assertEquals("21 миссия", singular)
    }

    @Test
    fun dataBoundParamIsSkippedWithWarningAndPlaceholderStays() {
        val json = documentJson.replace(
            """"content": { "key": "title", "defaultText": "Default title" }""",
            """"content": { "key": "greet", "defaultText": "Hello, {name}!", "params": { "name": "{{user.name}}" } }""",
        )
        val (greeting, resolver) = resolvedCharacters(json, ResolveContext(locale = "en-US"))
        assertEquals("Hello, {name}!", greeting)
        assertTrue(
            resolver.diagnostics.any { "user.name" in it.message },
            "expected a data-binding warning: ${resolver.diagnostics}",
        )
    }

    @Test
    fun propValueContentResolvesThroughInstanceExpansion() {
        val json = """
            {
              "i18n": {
                "sourceLocale": "en-US",
                "resources": {
                  "en-US": { "card.title": "Missions" },
                  "ru-RU": { "card.title": "Миссии" }
                }
              },
              "components": {
                "cmp_card": {
                  "name": "Card",
                  "properties": { "title": { "type": "text", "default": "Card" } },
                  "root": { "id": "card_root", "type": "frame", "children": [
                    { "id": "card_title", "type": "text", "characters": { "${'$'}prop": "title" } }
                  ] }
                }
              },
              "pages": [ { "id": "p", "children": [
                { "id": "inst", "type": "instance", "componentId": "cmp_card",
                  "props": { "title": { "content": { "key": "card.title", "defaultText": "Card" } } } }
              ] } ]
            }
        """.trimIndent()
        val (russian, resolver) = resolvedCharacters(json, ResolveContext(locale = "ru-RU"), sourceId = "card_title")
        assertEquals("Миссии", russian)
        assertEquals(0, resolver.diagnostics.size, "expected no diagnostics: ${resolver.diagnostics}")
    }

    @Test
    fun directionDerivesFromLocaleUnlessExplicit() {
        val document = parse(documentJson)
        assertEquals(LayoutDirection.Ltr, DesignResolver(document).direction)
        assertEquals(LayoutDirection.Rtl, DesignResolver(document, ResolveContext(locale = "ar")).direction)
        assertEquals(LayoutDirection.Rtl, DesignResolver(document, ResolveContext(locale = "he-IL")).direction)
        assertEquals(
            LayoutDirection.Ltr,
            DesignResolver(document, ResolveContext(locale = "ar", direction = LayoutDirection.Ltr)).direction,
        )
    }

    @Test
    fun contextModeSelectionsSeedTheRootScope() {
        val json = """
            {
              "variables": { "collections": { "col": {
                "modes": ["light", "dark"], "defaultMode": "light",
                "vars": { "var_brand": { "type": "color", "values": { "light": "#112233", "dark": "#445566" } } }
              } } },
              "pages": [ { "id": "p", "children": [
                { "id": "root", "type": "frame",
                  "fills": [ { "type": "solid", "color": { "${'$'}var": "var_brand" } } ] }
              ] } ]
            }
        """.trimIndent()
        val document = parse(json)
        val resolver = DesignResolver(document, ResolveContext(modeSelections = mapOf("col" to "dark")))
        val root = assertNotNull(resolver.resolvePage(document.pages.first()).firstOrNull())
        val fill = assertIs<ResolvedPaint.Solid>(root.fills.first())
        assertEquals(DesignColor.fromHex("#445566"), fill.color)
    }
}
