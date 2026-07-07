package io.aequicor.visualization.engine.ir.validate

import io.aequicor.visualization.engine.ir.model.DesignSeverity
import kotlin.test.Test
import kotlin.test.assertTrue

class ResolvedChecksTest {

    private val documentJson = """
        {
          "i18n": { "sourceLocale": "en", "targetLocales": ["de"] },
          "resources": {
            "en": { "cta": "OK" },
            "de": { "cta": "Außerordentlich lange deutsche Beschriftung, die niemals passt" }
          },
          "pages": [ { "id": "p", "children": [
            { "id": "card", "type": "frame",
              "size": { "width": 200, "height": 40 },
              "layout": { "mode": "vertical", "clipsContent": true },
              "children": [
                { "id": "cta_label", "type": "text",
                  "size": { "width": 90, "height": 30 },
                  "content": { "key": "cta", "defaultText": "OK" } }
              ] }
          ] } ]
        }
    """.trimIndent()

    @Test
    fun resolvedChecksAreOptIn() {
        val diagnostics = validate(documentJson, options = ValidationOptions(locales = emptyList()))
        // Target locales exist, so per-locale resolution runs, but no layout probe:
        diagnostics.assertNone("IR-RESOLVE-002")
    }

    @Test
    fun longLocaleOverflowingFixedFrameIsReportedByLayoutProbe() {
        val diagnostics = validate(
            documentJson,
            options = ValidationOptions(locales = listOf("de"), layoutProbe = true),
        )
        diagnostics.assertHas(
            "IR-RESOLVE-002",
            DesignSeverity.Warning,
            messagePart = "[de]",
        )
        assertTrue(
            diagnostics.any { it.code == "IR-RESOLVE-002" && "cta_label" in it.message },
            "expected the overflowing text node to be reported: $diagnostics",
        )
    }

    @Test
    fun shortSourceLocaleDoesNotOverflow() {
        validate(
            documentJson,
            options = ValidationOptions(locales = listOf("en"), layoutProbe = true),
        ).assertNone("IR-RESOLVE-002")
    }

    @Test
    fun resolverDiagnosticsAreWrappedWithTheirLocale() {
        val json = """
            {
              "i18n": { "sourceLocale": "en", "targetLocales": ["de"] },
              "resources": { "en": { "cta": "OK" } },
              "pages": [ { "id": "p", "children": [
                { "id": "t", "type": "text", "content": { "key": "cta", "defaultText": "OK" } }
              ] } ]
            }
        """.trimIndent()
        validate(json).assertHas("IR-RESOLVE-001", messagePart = "[de]")
    }
}
