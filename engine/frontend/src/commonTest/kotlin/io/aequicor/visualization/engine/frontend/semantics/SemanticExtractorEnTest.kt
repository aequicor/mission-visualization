package io.aequicor.visualization.engine.frontend.semantics

import io.aequicor.visualization.engine.ir.model.AlignItems
import io.aequicor.visualization.engine.ir.model.Bindable
import io.aequicor.visualization.engine.ir.model.DesignAction
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

class SemanticExtractorEnTest {
    @Test
    fun topbarRuleSetsRoleAndRowLayout() {
        val result = compileBody("Top bar: title Mission Control.", sourceLocale = "en-US")
        val topbar = screenRoot(result).children.single()
        assertEquals("topbar", topbar.role)
        assertEquals("topbar", topbar.id)
        assertEquals(LayoutMode.Horizontal, topbar.layout.mode)
        assertEquals(AlignItems.Center, topbar.layout.alignItems)
    }

    @Test
    fun titleRuleCreatesTitleTextChild() {
        val result = compileBody("Top bar: title Mission Control.", sourceLocale = "en-US")
        val title = screenRoot(result).children.single().children.single()
        assertEquals("title", title.role)
        assertEquals("title", title.id)
        assertEquals("Mission Control", assertIs<DesignNodeKind.Text>(title.kind).content?.defaultText)
    }

    @Test
    fun trailingEndRuleSetsSpaceBetweenOnBase() {
        val result = compileBody(
            "Top bar: title X, on the right primary button [Create mission](/missions/new).",
            sourceLocale = "en-US",
        )
        val topbar = screenRoot(result).children.single()
        assertEquals(JustifyContent.SpaceBetween, topbar.layout.justifyContent)
    }

    @Test
    fun primaryButtonRuleSynthesizesDsButtonInstance() {
        val result = compileBody(
            "Top bar: on the right primary button [Create mission](/missions/new).",
            sourceLocale = "en-US",
        )
        val button = screenRoot(result).children.single().children.single()
        assertEquals("primaryAction", button.role)
        assertEquals("createMission", button.id)
        val kind = assertIs<DesignNodeKind.Instance>(button.kind)
        assertEquals(Bindable.Value("ds/Button"), kind.componentId)
        assertEquals(mapOf("type" to "primary"), kind.variant)
        val label = assertIs<PropValue.Content>(kind.props.getValue("label"))
        assertEquals("testScreen.actions.createMission", label.content.key)
        val navigate = assertIs<DesignAction.Navigate>(button.interactions.single().actions.single())
        assertEquals("/missions/new", navigate.to)
    }

    @Test
    fun secondaryButtonRuleSynthesizesSecondaryVariant() {
        val result = compileBody("Top bar: secondary button [Cancel](/back).", sourceLocale = "en-US")
        val button = screenRoot(result).children.single().children.single()
        val kind = assertIs<DesignNodeKind.Instance>(button.kind)
        assertEquals(mapOf("type" to "secondary"), kind.variant)
        assertEquals("testScreen.actions.back", (kind.props.getValue("label") as PropValue.Content).content.key)
    }

    @Test
    fun emptyStateRuleMarksBlockquote() {
        val result = compileBody(
            "> Empty state: no missions yet. Primary action [Create mission](/missions/new).",
            sourceLocale = "en-US",
        )
        val empty = screenRoot(result).children.single()
        assertEquals("emptyState", empty.role)
        val title = empty.children.first()
        val kind = assertIs<DesignNodeKind.Text>(title.kind)
        assertEquals("no missions yet", kind.content?.defaultText)
        assertEquals("testScreen.empty.title", kind.content?.key)
        assertEquals("primaryAction", empty.children[1].role)
    }

    @Test
    fun badgeRuleSynthesizesBadgeWithContentBinding() {
        val result = compileBody(
            """
            Missions:
            - Card for each {{mission in missions}}:
              - Status: {{mission.status}} as badge
            """,
            sourceLocale = "en-US",
        )
        val badge = screenRoot(result).children.single().children.single().children.single()
        assertEquals("status", badge.id)
        val kind = assertIs<DesignNodeKind.Instance>(badge.kind)
        assertEquals(Bindable.Value("ds/Badge"), kind.componentId)
        assertEquals("mission.status", assertIs<PropValue.Data>(kind.props.getValue("content")).expression.raw)
        assertEquals(
            "testScreen.missions.card.status",
            assertIs<PropValue.Content>(kind.props.getValue("label")).content.key,
        )
    }

    @Test
    fun cardRepeatRuleMarksRepeatAsCard() {
        val result = compileBody(
            """
            Missions:
            - Card for each {{mission in missions}}:
              - Name: {{mission.name}}
            """,
            sourceLocale = "en-US",
        )
        val missions = screenRoot(result).children.single()
        assertEquals("missions", missions.id)
        val card = missions.children.single()
        assertEquals("card", card.role)
        val field = card.children.single()
        assertEquals("name", field.id)
        assertEquals(
            "testScreen.missions.card.name",
            assertIs<DesignNodeKind.Text>(field.kind).content?.key,
        )
    }

    @Test
    fun densityCompactRuleSetsScreenMode() {
        val result = compileBody("Compact.", sourceLocale = "en-US")
        val document = assertNotNull(result.document)
        assertEquals("compact", document.screen?.modes?.get("density"))
        assertTrue(screenRoot(result).children.isEmpty())
    }

    @Test
    fun searchExtensionSynthesizesInput() {
        val result = compileBody(
            """
            Filters:
            - Search by {{query.search}}
            """,
            sourceLocale = "en-US",
        )
        val filters = screenRoot(result).children.single()
        assertEquals("filters", filters.id)
        val input = filters.children.single()
        assertEquals("search", input.id)
        val kind = assertIs<DesignNodeKind.Instance>(input.kind)
        assertEquals(Bindable.Value("ds/Input"), kind.componentId)
        assertEquals("query.search", assertIs<PropValue.Data>(kind.props.getValue("value")).expression.raw)
    }

    @Test
    fun conditionParagraphBindsToNextInstructionBlock() {
        val result = compileBody(
            """
            If {{missions.length == 0}}:
            > Empty state: no missions yet.
            """,
            sourceLocale = "en-US",
        )
        val empty = screenRoot(result).children.single()
        assertEquals("emptyState", empty.role)
        assertEquals("missions.length == 0", empty.condition?.expression?.raw)
    }

    @Test
    fun unmatchedInstructionReportsAmbiguityWithSuggestions() {
        val result = compileBody("Control block: {{a.b}} and [Open](/x).", sourceLocale = "en-US")
        val ambiguity = result.diagnostics.single { "Ambiguous semantic instruction" in it.message }
        assertEquals(DesignSeverity.Warning, ambiguity.severity)
        assertTrue("Control block" in ambiguity.message)
        assertTrue("Suggested fixes" in ambiguity.message)
    }
}
