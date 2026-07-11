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
        assertEquals(6, screens.size)
        val overview = screens.first { it.id == "missionOverview" }
        assertEquals(1440.0, overview.width)
        assertEquals(1024.0, overview.height)
        assertTrue(overview.nodeCount > 0)
        assertEquals("mission-overview.layout.md", overview.sourceFile)
    }

    @Test
    fun inspectReturnsLaidOutTreeAndFindsNodesByAuthoredId() {
        val session = AgentSession.fromSamples()
        val root = assertNotNull(session.inspect("missionTelemetry"))
        assertEquals(1440.0, root.width)
        assertEquals(1024.0, root.height)
        assertTrue(root.children.isNotEmpty(), "screen root must have laid-out children")

        val header = assertNotNull(session.inspect("missionTelemetry", "telemetry_header"))
        assertEquals("telemetry_header", header.node.sourceId)
        assertTrue(header.width > 0.0)

        assertNull(session.inspect("missionTelemetry", "no_such_node"))
        assertNull(session.inspect("no_such_screen"))
    }

    @Test
    fun validateFiltersOtherScreensFileAnchoredDiagnostics() {
        val session = AgentSession.fromSamples()
        val filtered = session.validate("missionTelemetry")
        assertTrue(filtered.isNotEmpty())
        filtered.forEach { diagnostic ->
            val file = diagnostic.location?.file.orEmpty()
            assertTrue(
                file.isBlank() || file == "mission-telemetry.layout.md",
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
