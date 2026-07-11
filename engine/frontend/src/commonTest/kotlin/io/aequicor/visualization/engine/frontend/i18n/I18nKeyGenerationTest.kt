package io.aequicor.visualization.engine.frontend.i18n

import io.aequicor.visualization.engine.frontend.SlmCompileResult
import io.aequicor.visualization.engine.frontend.SlmLocale
import io.aequicor.visualization.engine.frontend.compileSlm
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.engine.ir.model.DesignSeverity
import io.aequicor.visualization.engine.ir.model.PropValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class I18nKeyGenerationTest {
    private fun compile(source: String): SlmCompileResult = compileSlm(source.trimIndent() + "\n")

    private fun bundle(result: SlmCompileResult, locale: String = "ru-RU"): Map<String, String> =
        assertNotNull(result.resources[SlmLocale(locale)])

    @Test
    fun everyKeySchemeRowGeneratesItsPattern() {
        val result = compile(
            """
            ---
            screen: shop
            sourceLocale: ru-RU
            ---

            # Экран магазина

            ## Раздел

            Первый абзац.

            Второй абзац.

            [Открыть](/orders)

            ![Схема потока](assets/flow.png)

            | Название | Статус |
            | --- | --- |
            | Ячейка | Данные |

            Миссии:
            - Карточка для каждой {{mission in missions}}:
              - Название: {{mission.name}}

            ## Component: Card component-name ds/Card prop title (text default «Заголовок карточки»)
            """,
        )
        assertTrue(
            result.diagnostics.none { it.severity == DesignSeverity.Error },
            result.diagnostics.joinToString { it.message },
        )
        val bundle = bundle(result)
        // {screen}.title
        assertEquals("Экран магазина", bundle["shop.title"])
        // {screen}.sections.{slugPath}.title
        assertEquals("Раздел", bundle["shop.sections.razdel.title"])
        // {screen}.sections.{slugPath}.text{N} — N only when >1
        assertEquals("Первый абзац.", bundle["shop.sections.razdel.text1"])
        assertEquals("Второй абзац.", bundle["shop.sections.razdel.text2"])
        // {screen}.actions.{actionSlug}
        assertEquals("Открыть", bundle["shop.actions.open"])
        // {screen}.{nodeSlug}.alt
        assertEquals("Схема потока", bundle["shop.shemaPotoka.alt"])
        // {screen}.{tableSlug}.columns.{colSlug} — noun slugs win over transliteration
        assertEquals("Название", bundle["shop.table1.columns.name"])
        assertEquals("Статус", bundle["shop.table1.columns.status"])
        // {screen}.{collectionSlug}.card.{fieldSlug}
        assertEquals("Название: {missionName}", bundle["shop.missions.card.name"])
        // components.{componentSlug}.{propName}
        assertEquals("Заголовок карточки", bundle["components.card.title"])
    }

    @Test
    fun singleSectionTextGetsNoNumber() {
        val result = compile(
            """
            ---
            screen: s
            sourceLocale: ru-RU
            ---

            # Экран

            ## Раздел

            Единственный абзац.
            """,
        )
        val bundle = bundle(result)
        assertEquals("Единственный абзац.", bundle["s.sections.razdel.text"])
        assertTrue("s.sections.razdel.text1" !in bundle)
    }

    @Test
    fun allThreeExplicitOverrideFormsWin() {
        val result = compile(
            """
            ---
            screen: s
            sourceLocale: ru-RU
            ---

            # Экран

            Основная кнопка [Создать миссию](/missions/new) <!-- i18n:key=custom.actions.create -->.

            ## Раздел

            Text «Явный текст» key custom.section.title

            [Открыть](/orders) <!-- i18n:key=custom.actions.open -->
            """,
        )
        val bundle = bundle(result)
        // trailing i18n:key comment on the synthesized instance's link
        assertEquals("Создать миссию", bundle["custom.actions.create"])
        // key phrase on a CNL text node
        assertEquals("Явный текст", bundle["custom.section.title"])
        // trailing <!-- i18n:key=... --> comment on a link
        assertEquals("Открыть", bundle["custom.actions.open"])
        assertTrue(bundle.keys.none { it == "s.actions.createMission" || it == "s.actions.open" })

        // The keys are wired back into the node contents.
        val root = assertNotNull(result.document).pages.single().children.single()
        val button = root.children.first { it.role == "primaryAction" }
        val label = assertIs<PropValue.Content>(assertIs<DesignNodeKind.Instance>(button.kind).props.getValue("label"))
        assertEquals("custom.actions.create", label.content.key)
    }

    @Test
    fun actionSlugPrecedenceChain() {
        val result = compile(
            """
            ---
            screen: s
            sourceLocale: ru-RU
            ---

            # Экран

            - [Открыть](/missions/new)
            - [Отменить](/settings/cancel)
            - [Удалить всё](https://external.example)
            """,
        )
        val bundle = bundle(result)
        // Lexicon slug beats the route slug (/missions/new -> missionsNew is skipped).
        assertEquals("Открыть", bundle["s.actions.open"])
        assertTrue("s.actions.missionsNew" !in bundle)
        // Route slug: camelCased path.
        assertEquals("Отменить", bundle["s.actions.settingsCancel"])
        // Transliterated fallback + info diagnostic recommending an explicit key.
        assertEquals("Удалить всё", bundle["s.actions.udalitVsyo"] ?: bundle["s.actions.udalitVse"])
        assertTrue(result.diagnostics.any { "transliterated" in it.message })
    }

    @Test
    fun identicalKeyAndTextPairsDedupe() {
        val result = compile(
            """
            ---
            screen: s
            sourceLocale: ru-RU
            ---

            # Экран

            [Открыть](/a)

            [Открыть](/b)
            """,
        )
        val bundle = bundle(result)
        assertEquals("Открыть", bundle["s.actions.open"])
        assertEquals(1, bundle.keys.count { it.startsWith("s.actions.") })
    }

    @Test
    fun sameKeyDifferentTextGetsSuffixAndWarning() {
        val result = compile(
            """
            ---
            screen: s
            sourceLocale: ru-RU
            ---

            # Экран

            Верхняя панель: заголовок Альфа.

            Верхняя панель: заголовок Бета.
            """,
        )
        val bundle = bundle(result)
        assertEquals("Альфа", bundle["s.sections.topbar.title"])
        assertEquals("Бета", bundle["s.sections.topbar.title.2"])
        assertTrue(result.diagnostics.any { "already used with different text" in it.message })
    }

    @Test
    fun duplicateExplicitKeysWithDifferentTextAreAnError() {
        val result = compile(
            """
            ---
            screen: s
            sourceLocale: ru-RU
            ---

            # Экран

            ## A

            Text «Один» key dup.key

            ## B

            Text «Два» key dup.key
            """,
        )
        assertTrue(
            result.diagnostics.any {
                it.severity == DesignSeverity.Error && "Duplicate explicit i18n key" in it.message
            },
        )
    }

    @Test
    fun explicitKeyBeatsCollidingGeneratedKey() {
        val result = compile(
            """
            ---
            screen: conf
            sourceLocale: ru-RU
            ---

            # Заголовок экрана

            ## Секция

            Text «Другой текст» key conf.title
            """,
        )
        val bundle = bundle(result)
        assertEquals("Другой текст", bundle["conf.title"])
        assertEquals("Заголовок экрана", bundle["conf.title.2"])
        assertTrue(result.diagnostics.any { "already used with different text" in it.message })
    }
}
