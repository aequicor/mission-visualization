package io.aequicor.visualization.engine.frontend.edit

import io.aequicor.visualization.engine.ir.model.DesignSeverity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [DeleteSection]: removing a heading section (with descendants) leaves every surviving sibling id
 * and the child order intact, drops the whole subtree, and is byte-lossless outside the footprint —
 * including a trailing section whose footprint runs to end-of-source.
 */
class SectionDeleteTest {
    private val doc = """
        ---
        screen: s
        sourceLocale: en-US
        ---

        # Screen id root name «Screen» column auto-layout

        ## Frame: Topbar id topbar

        ## Frame: Filters id filters

        ### Shape: Filter chip id filter_chip

        ## Frame: Empty state id emptyState

        ## Frame: Missions id missions
    """.trimIndent() + "\n"

    @Test
    fun deletesMidDocumentSectionWithDescendants() {
        val compiled = compileForEdit(doc)
        val result = applySlmEdit(doc, DeleteSection("filters"), compiled)
        val new = result.requireNewSource()

        // Golden: exactly the Filters footprint (heading -> just before the next `## `) is gone.
        val expected = doc.removeRange(doc.indexOf("## Frame: Filters"), doc.indexOf("## Frame: Empty state"))
        assertEquals(expected, new)
        assertLosslessOutside(doc, new, assertNotNull(result.appliedRange))

        val recompiled = compileForEdit(new)
        assertNoErrors(recompiled)
        val root = recompiled.requireDocument().requireNode("root")
        assertEquals(listOf("topbar", "emptyState", "missions"), root.children.map { it.id })
        assertNull(recompiled.requireDocument().nodeById("filters"), "deleted section still present")
        assertNull(recompiled.requireDocument().nodeById("filter_chip"), "deleted descendant still present")
    }

    @Test
    fun deletesTrailingSectionToEndOfSource() {
        val compiled = compileForEdit(doc)
        val result = applySlmEdit(doc, DeleteSection("missions"), compiled)
        val new = result.requireNewSource()

        val expected = doc.removeRange(doc.indexOf("## Frame: Missions"), doc.length)
        assertEquals(expected, new)
        assertLosslessOutside(doc, new, assertNotNull(result.appliedRange))

        val recompiled = compileForEdit(new)
        assertNoErrors(recompiled)
        val root = recompiled.requireDocument().requireNode("root")
        assertEquals(listOf("topbar", "filters", "emptyState"), root.children.map { it.id })
        assertNull(recompiled.requireDocument().nodeById("missions"))
        // A surviving nested descendant of an untouched sibling is unharmed.
        assertNotNull(recompiled.requireDocument().nodeById("filter_chip"))
    }

    private fun assertNoErrors(result: io.aequicor.visualization.engine.frontend.SlmCompileResult) {
        val errors = result.diagnostics.filter { it.severity == DesignSeverity.Error }
        assertTrue(errors.isEmpty(), "unexpected errors: ${errors.joinToString { it.message }}")
    }
}
