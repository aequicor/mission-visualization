package io.aequicor.visualization.engine.frontend.semantics

import io.aequicor.visualization.engine.frontend.SlmCompileResult
import io.aequicor.visualization.engine.frontend.compileSlm
import io.aequicor.visualization.engine.ir.model.AlignItems
import io.aequicor.visualization.engine.ir.model.Bindable
import io.aequicor.visualization.engine.ir.model.DesignAction
import io.aequicor.visualization.engine.ir.model.DesignNode
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.engine.ir.model.DesignSeverity
import io.aequicor.visualization.engine.ir.model.JustifyContent
import io.aequicor.visualization.engine.ir.model.LayoutMode
import io.aequicor.visualization.engine.ir.model.PropValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

internal fun compileBody(body: String, sourceLocale: String = "ru-RU"): SlmCompileResult =
    compileSlm(
        "---\nscreen: testScreen\nsourceLocale: $sourceLocale\n---\n\n# Screen\n\n" +
            body.trimIndent().replace('§', '$') + "\n",
    )

internal fun screenRoot(result: SlmCompileResult): DesignNode =
    assertNotNull(result.document).pages.single().children.single()

class SemanticExtractorRuTest {
    @Test
    fun topbarRuleSetsRoleAndRowLayout() {
        val result = compileBody("Верхняя панель: заголовок Mission Control.")
        val topbar = screenRoot(result).children.single()
        assertEquals("topbar", topbar.role)
        assertEquals("topbar", topbar.id)
        assertEquals(LayoutMode.Horizontal, topbar.layout.mode)
        assertEquals(AlignItems.Center, topbar.layout.alignItems)
    }

    @Test
    fun titleRuleCreatesTitleTextChild() {
        val result = compileBody("Верхняя панель: заголовок Mission Control.")
        val title = screenRoot(result).children.single().children.single()
        assertEquals("title", title.role)
        assertEquals("title", title.id)
        val kind = assertIs<DesignNodeKind.Text>(title.kind)
        assertEquals("Mission Control", kind.content?.defaultText)
    }

    @Test
    fun trailingEndRuleSetsSpaceBetweenOnBase() {
        val result = compileBody(
            "Верхняя панель: заголовок X, справа основная кнопка [Создать миссию](/missions/new).",
        )
        val topbar = screenRoot(result).children.single()
        assertEquals(JustifyContent.SpaceBetween, topbar.layout.justifyContent)
    }

    @Test
    fun primaryButtonRuleSynthesizesDsButtonInstance() {
        val result = compileBody(
            "Верхняя панель: справа основная кнопка [Создать миссию](/missions/new).",
        )
        val button = screenRoot(result).children.single().children.single()
        assertEquals("primaryAction", button.role)
        assertEquals("createMission", button.id)
        val kind = assertIs<DesignNodeKind.Instance>(button.kind)
        assertEquals(Bindable.Value("ds/Button"), kind.componentId)
        assertEquals(mapOf("type" to "primary"), kind.variant)
        val label = assertIs<PropValue.Content>(kind.props.getValue("label"))
        assertEquals("Создать миссию", label.content.defaultText)
        assertEquals("testScreen.actions.createMission", label.content.key)
        val navigate = assertIs<DesignAction.Navigate>(button.interactions.single().actions.single())
        assertEquals("/missions/new", navigate.to)
    }

    @Test
    fun secondaryButtonRuleSynthesizesSecondaryVariant() {
        val result = compileBody("Верхняя панель: вторичная кнопка [Отмена](/back).")
        val button = screenRoot(result).children.single().children.single()
        val kind = assertIs<DesignNodeKind.Instance>(button.kind)
        assertEquals(Bindable.Value("ds/Button"), kind.componentId)
        assertEquals(mapOf("type" to "secondary"), kind.variant)
        // No lexicon action slug -> route slug.
        assertEquals("testScreen.actions.back", (kind.props.getValue("label") as PropValue.Content).content.key)
    }

    @Test
    fun emptyStateRuleMarksBlockquote() {
        val result = compileBody(
            "> Пустое состояние: миссий пока нет. Основное действие [Создать миссию](/missions/new).",
        )
        val empty = screenRoot(result).children.single()
        assertEquals("emptyState", empty.role)
        assertEquals("emptyState", empty.id)
        val title = empty.children.first()
        assertEquals("title", title.role)
        assertEquals("миссий пока нет", assertIs<DesignNodeKind.Text>(title.kind).content?.defaultText)
        assertEquals(
            "testScreen.empty.title",
            assertIs<DesignNodeKind.Text>(title.kind).content?.key,
        )
        val action = empty.children[1]
        assertEquals("primaryAction", action.role)
        assertIs<DesignNodeKind.Instance>(action.kind)
    }

    @Test
    fun badgeRuleSynthesizesBadgeWithContentBinding() {
        val result = compileBody(
            """
            Миссии:
            - Карточка для каждой {{mission in missions}}:
              - Статус: {{mission.status}} как badge
            """,
        )
        val missions = screenRoot(result).children.single()
        val card = missions.children.single()
        val badge = card.children.single()
        assertEquals("status", badge.id)
        val kind = assertIs<DesignNodeKind.Instance>(badge.kind)
        assertEquals(Bindable.Value("ds/Badge"), kind.componentId)
        val content = assertIs<PropValue.Data>(kind.props.getValue("content"))
        assertEquals("mission.status", content.expression.raw)
        val label = assertIs<PropValue.Content>(kind.props.getValue("label"))
        assertEquals("Статус", label.content.defaultText)
        assertEquals("testScreen.missions.card.status", label.content.key)
    }

    @Test
    fun cardRepeatRuleMarksRepeatAsCard() {
        val result = compileBody(
            """
            Миссии:
            - Карточка для каждой {{mission in missions}}:
              - Название: {{mission.name}}
            """,
        )
        val missions = screenRoot(result).children.single()
        assertEquals("missions", missions.id)
        val card = missions.children.single()
        assertEquals("card", card.role)
        assertEquals("mission", card.repeat?.itemName)
        assertEquals("missions", card.repeat?.collection?.raw)
        val field = card.children.single()
        assertEquals("name", field.id)
        val kind = assertIs<DesignNodeKind.Text>(field.kind)
        assertEquals("testScreen.missions.card.name", kind.content?.key)
        assertEquals("Название: {missionName}", kind.content?.defaultText)
    }

    @Test
    fun densityCompactRuleSetsScreenMode() {
        val result = compileBody("Компактно.")
        val document = assertNotNull(result.document)
        assertEquals("compact", document.screen?.modes?.get("density"))
        // A pure mode instruction leaves no visible node behind.
        assertTrue(screenRoot(result).children.isEmpty())
    }

    @Test
    fun searchExtensionSynthesizesInput() {
        val result = compileBody(
            """
            Фильтры:
            - Поиск по {{query.search}}
            """,
        )
        val filters = screenRoot(result).children.single()
        assertEquals("filters", filters.id)
        val input = filters.children.single()
        assertEquals("search", input.id)
        val kind = assertIs<DesignNodeKind.Instance>(input.kind)
        assertEquals(Bindable.Value("ds/Input"), kind.componentId)
        val value = assertIs<PropValue.Data>(kind.props.getValue("value"))
        assertEquals("query.search", value.expression.raw)
    }

    @Test
    fun conditionParagraphBindsToNextInstructionBlock() {
        val result = compileBody(
            """
            Если {{missions.length == 0}}:
            > Пустое состояние: миссий пока нет.
            """,
        )
        val empty = screenRoot(result).children.single()
        assertEquals("emptyState", empty.role)
        assertEquals("missions.length == 0", empty.condition?.expression?.raw)
    }

    @Test
    fun unmatchedInstructionReportsAmbiguityWithSuggestions() {
        val result = compileBody("Блок управления: {{a.b}} и [Открыть](/x).")
        val ambiguity = result.diagnostics.single { "Ambiguous semantic instruction" in it.message }
        assertEquals(DesignSeverity.Warning, ambiguity.severity)
        assertTrue("Блок управления" in ambiguity.message)
        assertTrue("Suggested fixes" in ambiguity.message)
        // Up to three nearest-rule rewrites are offered.
        assertTrue(RuLexicon.rules.count { it.phrases.first() in ambiguity.message } in 1..3)
        // The paragraph still compiles as a plain text node.
        assertTrue(screenRoot(result).children.isNotEmpty())
    }

    @Test
    fun plainProseStaysPlainWithoutDiagnostics() {
        val result = compileBody("Обычный описательный абзац без инструкций.")
        assertTrue(result.diagnostics.none { "Ambiguous" in it.message })
        val text = screenRoot(result).children.single()
        assertIs<DesignNodeKind.Text>(text.kind)
    }
}
