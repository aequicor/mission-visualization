package io.aequicor.visualization.engine.ir.validate

import io.aequicor.visualization.engine.ir.model.DesignSeverity
import kotlin.test.Test

class TextI18nChecksTest {

    @Test
    fun keyMissingFromTargetLocaleIsAnError() {
        validate(
            """
            {
              "i18n": { "sourceLocale": "en", "targetLocales": ["ru-RU"] },
              "resources": { "en": { "title": "Hello" }, "ru-RU": { "other": "..." } },
              "pages": [ { "id": "p", "children": [
                { "id": "t", "type": "text",
                  "content": { "key": "title", "defaultText": "Hello" } }
              ] } ]
            }
            """.trimIndent(),
        ).assertHas("IR-I18N-001", DesignSeverity.Error, messagePart = "title")
    }

    @Test
    fun coveredKeyIsClean() {
        validate(
            """
            {
              "i18n": { "sourceLocale": "en", "targetLocales": ["ru-RU"] },
              "resources": { "en": { "title": "Hello" }, "ru-RU": { "title": "Привет" } },
              "pages": [ { "id": "p", "children": [
                { "id": "t", "type": "text",
                  "content": { "key": "title", "defaultText": "Hello" } }
              ] } ]
            }
            """.trimIndent(),
        ).assertNone("IR-I18N-001")
    }

    @Test
    fun sameKeyWithDifferentDefaultTextWarns() {
        validate(
            """
            { "pages": [ { "id": "p", "children": [
              { "id": "a", "type": "text", "content": { "key": "cta", "defaultText": "Start" } },
              { "id": "b", "type": "text", "content": { "key": "cta", "defaultText": "Go" } }
            ] } ] }
            """.trimIndent(),
        ).assertHas("IR-I18N-002", DesignSeverity.Warning, messagePart = "cta")
    }

    @Test
    fun malformedIcuMessageIsAnError() {
        validate(
            """
            {
              "i18n": { "sourceLocale": "en", "targetLocales": [] },
              "resources": { "en": { "broken": "{count, plural, one {# item}" } },
              "pages": [ { "id": "p", "children": [
                { "id": "t", "type": "text", "content": { "key": "broken", "defaultText": "x" } }
              ] } ]
            }
            """.trimIndent(),
        ).assertHas("IR-I18N-004", DesignSeverity.Error, messagePart = "broken")
    }

    @Test
    fun missingPluralCategoryWarnsPerLocale() {
        validate(
            """
            {
              "i18n": { "sourceLocale": "en", "targetLocales": ["ru"] },
              "resources": {
                "en": { "n": "{count, plural, one {# item} other {# items}}" },
                "ru": { "n": "{count, plural, one {# элемент} other {# элементов}}" }
              },
              "pages": [ { "id": "p", "children": [
                { "id": "t", "type": "text", "content": { "key": "n", "defaultText": "x" } }
              ] } ]
            }
            """.trimIndent(),
        ).apply {
            assertHas("IR-I18N-005", DesignSeverity.Warning, messagePart = "'ru'")
            assertNone("IR-I18N-004")
        }
    }

    @Test
    fun textSpanOutOfRangeIsAnError() {
        validate(
            """
            { "pages": [ { "id": "p", "children": [
              { "id": "t", "type": "text", "characters": "Hi",
                "styleRanges": [ { "start": 0, "end": 10 } ] }
            ] } ] }
            """.trimIndent(),
        ).assertHas("IR-I18N-006", DesignSeverity.Error, messagePart = "t")
    }

    @Test
    fun boundedTextWithoutTruncationWarns() {
        validate(
            """
            { "pages": [ { "id": "p", "children": [
              { "id": "t", "type": "text", "characters": "Long enough",
                "size": { "width": 100, "height": 20 } }
            ] } ] }
            """.trimIndent(),
        ).assertHas("IR-I18N-008", DesignSeverity.Warning, messagePart = "t")
    }

    @Test
    fun truncatedBoundedTextIsClean() {
        validate(
            """
            { "pages": [ { "id": "p", "children": [
              { "id": "t", "type": "text", "characters": "Long enough",
                "size": { "width": 100, "height": 20 },
                "truncate": { "maxLines": 1 } }
            ] } ] }
            """.trimIndent(),
        ).assertNone("IR-I18N-008")
    }
}
