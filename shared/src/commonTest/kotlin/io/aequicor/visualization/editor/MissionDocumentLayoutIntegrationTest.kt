package io.aequicor.visualization.editor

import io.aequicor.visualization.editor.data.DefaultDesignDocumentRepository
import io.aequicor.visualization.editor.domain.LoadDesignDocumentUseCase
import io.aequicor.visualization.engine.ir.layout.DesignLayoutEngine
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.engine.ir.model.DesignSeverity
import io.aequicor.visualization.engine.ir.resolve.DesignResolver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Compiles the three bundled SLM documents, merges, resolves and lays them out. */
class MissionDocumentLayoutIntegrationTest {

    private val engine = DesignLayoutEngine()
    private val documents = LoadDesignDocumentUseCase(DefaultDesignDocumentRepository())()

    @Test
    fun missionDocumentsCompileAndMergeWithoutErrors() {
        assertEquals(6, documents.compiled.size)
        documents.compiled.forEachIndexed { index, compiled ->
            assertTrue(compiled.isSuccess, "${documents.sources[index].fileName} failed to compile")
        }
        assertTrue(
            documents.diagnostics.none { it.severity == DesignSeverity.Error },
            "mission doc compile errors: " +
                documents.diagnostics.filter { it.severity == DesignSeverity.Error },
        )

        val document = assertNotNull(documents.document)
        assertEquals(
            listOf("Mission Overview", "Telemetry", "Event Log", "Shapes Showcase", "Diagrams", "CNL Showcase"),
            document.pages.map { it.name },
        )
        assertEquals(
            listOf("missionOverview", "missionTelemetry", "missionEventLog", "shapesShowcase", "diagrams", "cnlShowcase"),
            document.pages.map { it.id },
        )
        // Duplicated definitions collapse: Telemetry ships the wire tiles, Event Log the
        // log row, and every document ships the identical theme variables collection.
        // (Mission Overview is now an app-UI wireframe and contributes no components.)
        assertTrue("cmp_wire_tile_default" in document.components)
        assertTrue("cmp_wire_tile_highlight" in document.components)
        assertTrue("cmp_log_row" in document.components)
        assertEquals(setOf("theme"), document.variables.collections.keys)
    }

    @Test
    fun missionDocumentLaysOutCleanlyAtAuthoredAndDeviceWidths() {
        val document = assertNotNull(documents.document)
        val resolver = DesignResolver(document)

        document.pages.forEach { page ->
            val resolved = assertNotNull(resolver.resolvePage(page).firstOrNull(), "page ${page.id} has a root frame")
            val box = engine.layout(resolved)
            assertEquals(1440.0, box.width, "page ${page.id} authored width")
            assertEquals(1024.0, box.height, "page ${page.id} authored height")

            val mobile = engine.layout(resolved, 375.0, 812.0)
            assertEquals(375.0, mobile.width)
            mobile.allBoxes().forEach { child ->
                assertTrue(child.width >= 0.0 && child.height >= 0.0, "negative size in ${child.node.id}")
            }
        }
        assertTrue(
            resolver.diagnostics.isEmpty(),
            "mission doc resolve diagnostics: ${resolver.diagnostics}",
        )

        // Mission Overview is a Free-layout (`mode: none`) wireframe of the editor app:
        // its three working panels are absolutely positioned side by side.
        val overview = document.pages.first()
        val overviewBox = engine.layout(assertNotNull(resolver.resolvePage(overview).firstOrNull()))
        val panels = listOf("src_panel", "cv_panel", "in_panel").map { id ->
            assertNotNull(overviewBox.findBySourceId(id), "missing $id")
        }
        assertTrue(panels[0].x < panels[1].x, "Source sits left of Canvas")
        assertTrue(panels[1].x < panels[2].x, "Canvas sits left of Inspector")
        assertEquals(944.0, panels[0].height, "panel height is fixed by the wireframe")
    }

    @Test
    fun sourceLocaleBundleCarriesAuthoredTexts() {
        val document = assertNotNull(documents.document)
        val bundle = assertNotNull(document.i18n.resources["en-US"], "en-US bundle present")
        assertEquals("Mission Overview", bundle["missionOverview.title"])
        assertEquals("Telemetry", bundle["missionTelemetry.title"])
        assertEquals("Event Log", bundle["missionEventLog.title"])
        assertEquals("LIVE", bundle["missionTelemetry.badge.live"])
        // Log Row's text property defaults become component resources.
        assertEquals("Event", bundle["components.logRow.label"])
        assertEquals("00:00", bundle["components.logRow.time"])

        // The footer keeps i18n-shaped content even though its subtree comes from
        // an `ir` splice (which bypasses resource generation).
        val footerLabel = assertNotNull(document.nodeById("footer_label"))
        val footerKind = assertIs<DesignNodeKind.Text>(footerLabel.kind)
        assertEquals("6 events captured in the last orbit", footerKind.content?.defaultText)
    }
}
