package io.aequicor.visualization.agent

import io.aequicor.visualization.editor.presentation.ScreenPreset
import io.aequicor.visualization.engine.ir.model.DesignSeverity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AgentSessionTest {

    @Test
    fun samplesCompileAndListAllScreens() {
        val session = AgentSession.fromSamples()
        assertNotNull(session.document, "bundled samples must compile")
        val screens = session.screens()
        assertEquals(3, screens.size)
        val welcome = screens.first { it.id == "welcomeEditor" }
        assertEquals(1440.0, welcome.width)
        assertEquals(1024.0, welcome.height)
        assertTrue(welcome.nodeCount > 0)
        assertEquals("welcome-editor.layout.md", welcome.sourceFile)
    }

    @Test
    fun inspectReturnsLaidOutTreeAndFindsNodesByAuthoredId() {
        val session = AgentSession.fromSamples()
        val root = assertNotNull(session.inspect("welcomeVectors"))
        assertEquals(1440.0, root.width)
        assertEquals(1024.0, root.height)
        assertTrue(root.children.isNotEmpty(), "screen root must have laid-out children")

        val rocket = assertNotNull(session.inspect("welcomeVectors", "rocket"))
        assertEquals("rocket", rocket.node.sourceId)
        assertTrue(rocket.width > 0.0)

        assertNull(session.inspect("welcomeVectors", "no_such_node"))
        assertNull(session.inspect("no_such_screen"))
    }

    @Test
    fun validateFiltersOtherScreensFileAnchoredDiagnostics() {
        val session = AgentSession.fromSamples()
        val filtered = session.validate("welcomeEditor")
        assertTrue(filtered.isNotEmpty())
        filtered.forEach { diagnostic ->
            val file = diagnostic.location?.file.orEmpty()
            assertTrue(
                file.isBlank() || file == "welcome-editor.layout.md",
                "unexpected foreign-file diagnostic: $diagnostic",
            )
            if (file.isBlank()) {
                assertEquals(DesignSeverity.Error, diagnostic.severity, "file-less warnings must be filtered out")
            }
        }
    }

    @Test
    fun createScreenAppendsNewSourceWithMatchingFileName() {
        val session = AgentSession.fromSamples()
        val sourcesBefore = session.sources().size
        val pageId = session.createScreen(ScreenPreset.Mobile, "Agent Screen")
        assertTrue(pageId.isNotBlank())
        assertEquals(sourcesBefore + 1, session.sources().size)
        assertEquals("$pageId.layout.md", session.sources().last().fileName)
        val created = session.screens().first { it.id == pageId }
        assertEquals(ScreenPreset.Mobile.width, created.width)
        assertEquals(ScreenPreset.Mobile.height, created.height)
    }
}
