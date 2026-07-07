package io.aequicor.visualization.engine.frontend.escape

import io.aequicor.visualization.engine.frontend.SlmCompileOptions
import io.aequicor.visualization.engine.frontend.compileSlm
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.engine.ir.model.DesignSeverity
import io.aequicor.visualization.engine.ir.model.ShapeType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class IrEscapeHatchTest {
    @Test
    fun specVectorExampleSplicesWithSourceMap() {
        val result = compileSlm(
            """
            ---
            screen: s
            sourceLocale: en-US
            ---

            # Screen

            ## Alerts

            ```ir
            {
              "type": "vector",
              "id": "customAlertIcon",
              "name": "Custom alert icon",
              "pathRef": "assets/icons/custom-alert.svg"
            }
            ```
            """.trimIndent() + "\n",
            SlmCompileOptions(fileName = "mission-dashboard.layout.md"),
        )
        assertTrue(
            result.diagnostics.none { it.severity == DesignSeverity.Error },
            result.diagnostics.joinToString { it.message },
        )
        val section = assertNotNull(result.document).pages.single().children.single().children.single()
        val vector = section.children.single()
        assertEquals("customAlertIcon", vector.id)
        assertEquals("vector", vector.type)
        assertEquals("Custom alert icon", vector.name)
        val kind = assertIs<DesignNodeKind.Shape>(vector.kind)
        assertEquals(ShapeType.Vector, kind.shape)
        assertEquals("assets/icons/custom-alert.svg", kind.pathRef)
        // Source map points at the fence content inside the SLM file.
        assertEquals("mission-dashboard.layout.md", vector.sourceMap?.file)
        assertEquals(11, vector.sourceMap?.line)
        // The spliced node is recorded for the patcher.
        assertEquals(setOf("customAlertIcon"), result.editIndex.irSpliceNodes)
    }

    @Test
    fun missingIdIsGenerated() {
        val result = compileSlm(
            """
            ---
            screen: s
            sourceLocale: en-US
            ---

            # Screen

            ```ir
            {"type": "vector", "name": "Custom alert icon"}
            ```
            """.trimIndent() + "\n",
        )
        val root = assertNotNull(result.document).pages.single().children.single()
        val vector = root.children.single()
        assertEquals("customAlertIcon", vector.id)
        assertTrue("customAlertIcon" in result.editIndex.irSpliceNodes)
    }

    @Test
    fun malformedJsonReportsErrorAndSkipsOnlyTheSplice() {
        val result = compileSlm(
            """
            ---
            screen: s
            sourceLocale: en-US
            ---

            # Screen

            ## Panel

            A visible sibling paragraph.

            ```ir
            { this is not json
            ```
            """.trimIndent() + "\n",
        )
        // The compile continues with an error diagnostic at the fence.
        val error = result.diagnostics.single { it.severity == DesignSeverity.Error }
        assertTrue("Malformed JSON" in error.message)
        assertEquals(13, error.location?.line)
        val panel = assertNotNull(result.document).pages.single().children.single().children.single()
        // The paragraph sibling survives; the broken splice is skipped.
        assertEquals(1, panel.children.size)
        assertIs<DesignNodeKind.Text>(panel.children.single().kind)
        assertTrue(result.editIndex.irSpliceNodes.isEmpty())
    }

    @Test
    fun duplicateExplicitSpliceIdGetsSuffixAndDiagnostic() {
        val result = compileSlm(
            """
            ---
            screen: s
            sourceLocale: en-US
            ---

            # Screen

            ## Panel
            node:
              id: customIcon

            ```ir
            {"type": "vector", "id": "customIcon"}
            ```
            """.trimIndent() + "\n",
        )
        assertTrue(result.diagnostics.any { it.severity == DesignSeverity.Error && "customIcon" in it.message })
        val root = assertNotNull(result.document).pages.single().children.single()
        val panel = root.children.single()
        val spliced = panel.children.single()
        assertEquals("customIcon-2", spliced.id)
        assertTrue("customIcon-2" in result.editIndex.irSpliceNodes)
    }
}
