package io.aequicor.visualization.engine.frontend

import io.aequicor.visualization.engine.frontend.blocks.readers.slm
import io.aequicor.visualization.engine.ir.model.Bindable
import io.aequicor.visualization.engine.ir.model.DesignGap
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.engine.ir.model.DesignSeverity
import io.aequicor.visualization.engine.ir.model.LayoutMode
import io.aequicor.visualization.engine.ir.model.SizingMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SlmCompileSmokeTest {
    private val source = slm(
        """
        ---
        screen: missionDashboard
        page: Operations
        sourceLocale: ru-RU
        targetLocales:
          - ru-RU
          - en-US
        frame:
          preset: desktop-1440
          width: 1440
          height: 1024
        libraries:
          - id: ds
            source: "@company/design-system"
        ---

        # Панель миссий

        ## Панель деталей id missionPanel column gap ${'$'}space.4 width (fill) height (hug) radius 8 color ${'$'}color.surface

        Text id missionTitle «Mission Control» key missionDashboard.title
        Instance id cardInstance of ds/MissionCard variant (status nominal)

        ## Component: Mission Card id componentMissionCard component-name ds/MissionCard axis status (nominal warning) prop title (text default «Mission name»)

        Text id cardTitle «Mission name» key components.missionCard.title
        """,
    ) + "\n"

    @Test
    fun compilesWithZeroErrors() {
        val result = compileSlm(source, SlmCompileOptions(fileName = "mission.layout.md"))
        assertTrue(result.isSuccess)
        assertTrue(
            result.diagnostics.none { it.severity == DesignSeverity.Error },
            result.diagnostics.joinToString { it.message },
        )
    }

    @Test
    fun buildsExpectedDocumentTree() {
        val result = compileSlm(source)
        val document = assertNotNull(result.document)

        assertEquals("missionDashboard", document.id)
        assertEquals("Панель миссий", document.name)
        assertEquals("ru-RU", document.i18n.sourceLocale)
        assertEquals(listOf("ru-RU", "en-US"), document.i18n.targetLocales)
        assertEquals(listOf("ds"), document.libraries.map { it.id })

        val page = document.pages.single()
        assertEquals("Operations", page.name)
        val root = page.children.single()
        assertEquals("missionDashboard", root.id)
        assertEquals("screen", root.type)
        assertEquals(1440.0, root.size.width)
        assertEquals(1024.0, root.size.height)

        // The component definition subtree is lifted out of the visible tree.
        assertEquals(listOf("missionPanel"), root.children.map { it.id })
        val component = document.components.getValue("componentMissionCard")
        assertEquals("ds/MissionCard", component.name)
        assertEquals("cardTitle", component.root.children.single().id)
        assertTrue("componentMissionCardSet" in document.componentSets)
        assertEquals(
            mapOf("status" to listOf("nominal", "warning")),
            document.componentSets.getValue("componentMissionCardSet").axes,
        )

        val panel = root.children.single()
        assertEquals(LayoutMode.Vertical, panel.layout.mode)
        assertEquals(DesignGap.Fixed(Bindable.VarRef("space.4")), panel.layout.gap)
        assertEquals(SizingMode.Fill, panel.sizing?.horizontal)
        assertEquals(SizingMode.Hug, panel.sizing?.vertical)
        assertEquals(8.0, (panel.cornerRadius?.topLeft as? Bindable.Value)?.value)
        assertEquals(
            Bindable.VarRef("color.surface"),
            (panel.fills?.single() as io.aequicor.visualization.engine.ir.model.DesignPaint.Solid).color,
        )

        assertEquals(listOf("missionTitle", "cardInstance"), panel.children.map { it.id })
        assertEquals(listOf(10, 20), panel.children.map { it.order })

        val title = panel.children.first()
        val titleKind = assertIs<DesignNodeKind.Text>(title.kind)
        assertEquals("missionDashboard.title", titleKind.content?.key)
        assertEquals("Mission Control", titleKind.content?.defaultText)
        assertEquals("ru-RU", titleKind.content?.defaultLocale)

        val instance = panel.children.last()
        val instanceKind = assertIs<DesignNodeKind.Instance>(instance.kind)
        // Local ref by component name resolves to the lifted definition id.
        assertEquals(Bindable.Value("componentMissionCard"), instanceKind.componentId)
        assertEquals(mapOf("status" to "nominal"), instanceKind.variant)
    }

    @Test
    fun collectsResourcesForSourceLocale() {
        val result = compileSlm(source)
        val bundle = assertNotNull(result.resources[SlmLocale("ru-RU")])
        // Explicit text.key wins the key; the generated screen title moves aside.
        assertEquals("Mission Control", bundle["missionDashboard.title"])
        assertEquals("Панель миссий", bundle["missionDashboard.title.2"])
        assertEquals("Mission name", bundle["components.missionCard.title"])
        // The target locale ships an empty bundle plus one summary warning.
        assertEquals(emptyMap(), result.resources[SlmLocale("en-US")])
        assertTrue(result.diagnostics.any { "en-US" in it.message && "untranslated" in it.message })
    }

    @Test
    fun buildsEditIndexForAnchorOwningNodes() {
        val result = compileSlm(source)
        assertNotNull(result.editIndex.anchorOwners["missionPanel"])
        assertNotNull(result.editIndex.anchorOwners["missionTitle"])
        assertNotNull(result.editIndex.anchorOwners["cardInstance"])
    }

    @Test
    fun fingerprintIsStableAcrossCallsAndSensitiveToSource() {
        val first = compileSlm(source)
        val second = compileSlm(source)
        assertEquals(first.sourceFingerprint, second.sourceFingerprint)
        assertEquals(first.document, second.document)
        val changed = compileSlm(source + "\n")
        assertTrue(changed.sourceFingerprint != first.sourceFingerprint)
    }

    @Test
    fun fatalFailuresReturnNullDocument() {
        val unclosed = compileSlm("---\nscreen: x\n# no closing fence")
        assertEquals(null, unclosed.document)
        assertTrue(unclosed.diagnostics.any { it.severity == DesignSeverity.Error })

        val empty = compileSlm("")
        assertEquals(null, empty.document)
        assertTrue(empty.diagnostics.any { it.severity == DesignSeverity.Error })
    }
}
