package io.aequicor.visualization.editor

import io.aequicor.visualization.editor.data.DefaultDesignDocumentRepository
import io.aequicor.visualization.editor.domain.LoadDesignDocumentUseCase
import io.aequicor.visualization.engine.ir.layout.DesignLayoutEngine
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.engine.ir.model.DesignAction
import io.aequicor.visualization.engine.ir.model.DesignSeverity
import io.aequicor.visualization.engine.ir.model.InteractionTrigger
import io.aequicor.visualization.engine.ir.resolve.DesignResolver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Compiles the bundled Welcome SLM documents, merges, resolves and lays them out. */
class MissionDocumentLayoutIntegrationTest {

    private val engine = DesignLayoutEngine()
    private val documents = LoadDesignDocumentUseCase(DefaultDesignDocumentRepository())()

    @Test
    fun missionDocumentsCompileAndMergeWithoutErrors() {
        assertEquals(3, documents.compiled.size)
        documents.compiled.forEachIndexed { index, compiled ->
            assertTrue(compiled.isSuccess, "${documents.sources[index].fileName} failed to compile")
        }
        assertTrue(
            documents.diagnostics.none { it.severity == DesignSeverity.Error },
            "welcome doc compile errors: " +
                documents.diagnostics.filter { it.severity == DesignSeverity.Error },
        )

        val document = assertNotNull(documents.document)
        assertEquals(
            listOf("Welcome", "Vectors & Objects", "Architecture"),
            document.pages.map { it.name },
        )
        assertEquals(
            listOf("welcomeEditor", "welcomeVectors", "welcomeUml"),
            document.pages.map { it.id },
        )
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
            "welcome doc resolve diagnostics: ${resolver.diagnostics}",
        )

        // The Welcome screen is a Free-layout (`mode: none`) wireframe of the editor app:
        // its three working panels are absolutely positioned side by side.
        val welcome = document.pages.first()
        val welcomeBox = engine.layout(assertNotNull(resolver.resolvePage(welcome).firstOrNull()))
        val panels = listOf("src_panel", "cv_panel", "in_panel").map { id ->
            assertNotNull(welcomeBox.findBySourceId(id), "missing $id")
        }
        assertTrue(panels[0].x < panels[1].x, "Source sits left of Canvas")
        assertTrue(panels[1].x < panels[2].x, "Canvas sits left of Inspector")
        assertEquals(944.0, panels[0].height, "panel height is fixed by the wireframe")
    }

    @Test
    fun sourceLocaleBundleCarriesAuthoredTexts() {
        val document = assertNotNull(documents.document)
        val bundle = assertNotNull(document.i18n.resources["en-US"], "en-US bundle present")
        assertEquals("Source", bundle["welcome.source.title"])
        assertEquals("SCENE TOUR", bundle["welcome.canvas.badge"])
        assertEquals("Vectors & Objects", bundle["welcomeVectors.heading"])
        assertEquals("Architecture", bundle["welcomeUml.heading"])

        val nextLabel = assertNotNull(document.nodeById("wel_nav_next_label"))
        val nextKind = assertIs<DesignNodeKind.Text>(nextLabel.kind)
        assertEquals("Next · Vectors →", nextKind.content?.defaultText)
    }

    @Test
    fun welcomeUmlShipsCompiledDiagramGraphs() {
        // Live-bundle pin: the shipped Architecture screen's `## Diagram:` containers must
        // compile into non-empty graphs (the fixture-based diagram tests no longer cover
        // what actually ships).
        val document = assertNotNull(documents.document)
        val expected = mapOf(
            "module_map" to (12 to 13),
            "slm_pipeline" to (6 to 6),
            "editor_mvi" to (3 to 2),
        )
        expected.forEach { (id, counts) ->
            val diagram = assertIs<DesignNodeKind.Diagram>(assertNotNull(document.nodeById(id), "diagram $id").kind)
            assertEquals(counts.first, diagram.graph.nodes.size, "$id node count")
            assertEquals(counts.second, diagram.graph.edges.size, "$id edge count")
        }
    }

    @Test
    fun welcomeTourWiresSceneNavigationAcrossAllScreens() {
        // Every Welcome screen participates in the auto-tour: its root frame carries an
        // afterDelay → navigate interaction, and the loop closes back on the first screen.
        val document = assertNotNull(documents.document)
        val tour = document.pages.associate { page ->
            val root = assertNotNull(page.children.firstOrNull(), "page ${page.id} root frame")
            val timer = assertNotNull(
                root.interactions.firstOrNull { it.trigger == InteractionTrigger.AfterDelay },
                "page ${page.id} root carries an afterDelay interaction",
            )
            val navigate = assertNotNull(
                timer.actions.filterIsInstance<DesignAction.Navigate>().firstOrNull(),
                "page ${page.id} afterDelay navigates",
            )
            page.id to navigate.to
        }
        assertEquals("welcomeVectors", tour["welcomeEditor"])
        assertEquals("welcomeUml", tour["welcomeVectors"])
        assertEquals("welcomeEditor", tour["welcomeUml"])
    }
}
