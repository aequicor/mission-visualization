package io.aequicor.visualization.engine.frontend

import io.aequicor.visualization.engine.frontend.semantics.SpecRuDocument
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

/** End-to-end compile of the spec's full RU source document. */
class SlmCompileGoldenTest {
    private val result = compileSlm(
        SpecRuDocument,
        SlmCompileOptions(fileName = "mission-dashboard.layout.md"),
    )

    private val root get() = assertNotNull(result.document).pages.single().children.single()

    @Test
    fun compilesWithZeroErrors() {
        assertTrue(result.isSuccess)
        assertTrue(
            result.diagnostics.none { it.severity == DesignSeverity.Error },
            result.diagnostics.joinToString { it.message },
        )
    }

    @Test
    fun screenMetadataComesFromFrontmatter() {
        val document = assertNotNull(result.document)
        assertEquals("missionDashboard", document.id)
        assertEquals("Панель миссий", document.name)
        assertEquals("compact", document.screen?.modes?.get("density"))
        assertEquals("web", document.screen?.modes?.get("platform"))
        assertEquals(1440.0, root.size.width)
        assertEquals(listOf("topbar", "filters", "emptyState", "missions"), root.children.map { it.id })
    }

    @Test
    fun topbarFrameMatchesSpecExtractionExample() {
        val topbar = root.children.first()
        assertEquals("topbar", topbar.role)
        assertEquals("frame", topbar.type)
        assertEquals(LayoutMode.Horizontal, topbar.layout.mode)
        assertEquals(AlignItems.Center, topbar.layout.alignItems)
        assertEquals(JustifyContent.SpaceBetween, topbar.layout.justifyContent)

        val title = topbar.children[0]
        assertEquals("title", title.role)
        val titleKind = assertIs<DesignNodeKind.Text>(title.kind)
        assertEquals("Mission Control", titleKind.content?.defaultText)
        assertEquals("ru-RU", titleKind.content?.defaultLocale)

        val button = topbar.children[1]
        assertEquals("primaryAction", button.role)
        assertEquals("createMission", button.id)
        val buttonKind = assertIs<DesignNodeKind.Instance>(button.kind)
        assertEquals(Bindable.Value("ds/Button"), buttonKind.componentId)
        assertEquals(mapOf("type" to "primary"), buttonKind.variant)
        val label = assertIs<PropValue.Content>(buttonKind.props.getValue("label"))
        assertEquals("missionDashboard.actions.createMission", label.content.key)
        assertEquals("Создать миссию", label.content.defaultText)
        val navigate = assertIs<DesignAction.Navigate>(button.interactions.single().actions.single())
        assertEquals("/missions/new", navigate.to)
        assertEquals("mission-dashboard.layout.md", button.sourceMap?.file)
    }

    @Test
    fun filtersGroupContainsSearchInputAndStatusText() {
        val filters = root.children[1]
        assertEquals("filters", filters.id)
        assertEquals(listOf("search", "status"), filters.children.map { it.id })

        val search = filters.children[0]
        val searchKind = assertIs<DesignNodeKind.Instance>(search.kind)
        assertEquals(Bindable.Value("ds/Input"), searchKind.componentId)
        assertEquals("query.search", assertIs<PropValue.Data>(searchKind.props.getValue("value")).expression.raw)

        val status = filters.children[1]
        val statusKind = assertIs<DesignNodeKind.Text>(status.kind)
        assertEquals("Статус из {queryStatus}", statusKind.content?.defaultText)
        assertEquals("query.status", (statusKind.content?.params?.get("queryStatus") as? Bindable.DataRef)?.expression?.raw)
    }

    @Test
    fun emptyStateCalloutCarriesConditionAndPrimaryAction() {
        val empty = root.children[2]
        assertEquals("emptyState", empty.role)
        assertEquals("missions.length == 0", empty.condition?.expression?.raw)

        val title = empty.children[0]
        assertEquals("title", title.role)
        val titleKind = assertIs<DesignNodeKind.Text>(title.kind)
        assertEquals("missionDashboard.empty.title", titleKind.content?.key)
        assertEquals("миссий пока нет", titleKind.content?.defaultText)

        val action = empty.children[1]
        assertEquals("primaryAction", action.role)
        val actionKind = assertIs<DesignNodeKind.Instance>(action.kind)
        assertEquals(Bindable.Value("ds/Button"), actionKind.componentId)
        // Same key as the topbar action: identical (key, text) pairs dedupe.
        assertEquals(
            "missionDashboard.actions.createMission",
            assertIs<PropValue.Content>(actionKind.props.getValue("label")).content.key,
        )
    }

    @Test
    fun missionsRepeatCarriesCardFields() {
        val missions = root.children[3]
        assertEquals("missions", missions.id)
        val card = missions.children.single()
        assertEquals("card", card.role)
        assertEquals("mission", card.repeat?.itemName)
        assertEquals("missions", card.repeat?.collection?.raw)

        val (name, status, action) = card.children
        assertEquals("name", name.id)
        assertEquals(
            "missionDashboard.missions.card.name",
            assertIs<DesignNodeKind.Text>(name.kind).content?.key,
        )
        // "status" is claimed by the filters item; ids stay document-unique.
        assertEquals("status-2", status.id)
        val statusKind = assertIs<DesignNodeKind.Instance>(status.kind)
        assertEquals(Bindable.Value("ds/Badge"), statusKind.componentId)
        assertEquals("mission.status", assertIs<PropValue.Data>(statusKind.props.getValue("content")).expression.raw)
        assertEquals("open", action.id)
        val actionKind = assertIs<DesignNodeKind.Text>(action.kind)
        assertEquals("missionDashboard.actions.open", actionKind.content?.key)
        val navigate = assertIs<DesignAction.Navigate>(action.interactions.single().actions.single())
        assertEquals("/missions/{{mission.id}}", navigate.to)
    }

    @Test
    fun ruBundleCarriesEveryDefaultText() {
        val bundle = assertNotNull(result.resources[SlmLocale("ru-RU")])
        assertEquals("Панель миссий", bundle["missionDashboard.title"])
        assertEquals("Mission Control", bundle["missionDashboard.sections.topbar.title"])
        assertEquals("Создать миссию", bundle["missionDashboard.actions.createMission"])
        assertEquals("Открыть", bundle["missionDashboard.actions.open"])
        assertEquals("Фильтры", bundle["missionDashboard.sections.filters.title"])
        assertEquals("Статус из {queryStatus}", bundle["missionDashboard.sections.filters.text"])
        assertEquals("миссий пока нет", bundle["missionDashboard.empty.title"])
        assertEquals("Миссии", bundle["missionDashboard.sections.missions.title"])
        assertEquals("Название: {missionName}", bundle["missionDashboard.missions.card.name"])
        assertEquals("Статус", bundle["missionDashboard.missions.card.status"])
        assertEquals(10, bundle.size)
        // Bundles are mirrored into the document with string locale keys.
        assertEquals(bundle, assertNotNull(result.document).i18n.resources["ru-RU"])
        // The declared target locale ships empty with a summary warning.
        assertEquals(emptyMap(), result.resources[SlmLocale("en-US")])
        assertEquals(1, result.diagnostics.count { "untranslated" in it.message })
    }

    @Test
    fun editIndexAddressesAnchorNodes() {
        assertNotNull(result.editIndex)
        assertTrue(result.editIndex.irSpliceNodes.isEmpty())
    }
}
