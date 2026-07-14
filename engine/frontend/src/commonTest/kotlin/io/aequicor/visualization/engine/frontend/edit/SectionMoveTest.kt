package io.aequicor.visualization.engine.frontend.edit

import io.aequicor.visualization.engine.ir.model.DesignSeverity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * [MoveSection]: relocating a heading section (with its subtree) under a new parent re-levels every
 * heading in the moved footprint by the depth delta, is byte-lossless outside the moved/target
 * range, and recompiles to the new parent/child topology with every id preserved. A move whose
 * re-leveled depth would exceed ATX 6 is refused (the caller falls back in-memory).
 */
class SectionMoveTest {
    private val doc = """
        ---
        screen: s
        sourceLocale: en-US
        ---

        # Screen id root name «Screen» column auto-layout

        ## Frame: Panel id panel

        ### Shape: Chip id chip

        ## Frame: Sidebar id sidebar
    """.trimIndent() + "\n"

    @Test
    fun movesSubtreeUnderSiblingRelevelingHeadings() {
        val compiled = compileForEdit(doc)
        // Move Panel (with its Chip child) to become a child of the empty Sidebar frame.
        val result = applySlmEdit(doc, MoveSection("panel", "sidebar"), compiled)
        val new = result.requireNewSource()
        assertLosslessOutside(doc, new, assertNotNull(result.appliedRange))

        // The whole subtree is re-leveled one deeper: Panel `## `->`### `, Chip `### `->`#### `,
        // and the old level-2 Panel / level-3 Chip heading lines are gone (line-anchored so a
        // deeper heading's `#`-run does not spuriously match the shallower one).
        assertTrue("\n### Frame: Panel id panel\n" in new, new)
        assertTrue("\n#### Shape: Chip id chip\n" in new, new)
        assertFalse("\n## Frame: Panel id panel\n" in new, "old level-2 Panel heading still present:\n$new")
        assertFalse("\n### Shape: Chip id chip\n" in new, "old level-3 Chip heading still present:\n$new")
        // Panel landed after Sidebar's heading (it is now Sidebar's child).
        assertTrue(new.indexOf("\n### Frame: Panel id panel\n") > new.indexOf("\n## Frame: Sidebar id sidebar\n"), new)

        val recompiled = compileForEdit(new)
        assertNoErrors(recompiled)
        val document = recompiled.requireDocument()
        // root now owns only Sidebar; Sidebar owns Panel; Panel owns Chip. Every id preserved.
        assertEquals(listOf("sidebar"), document.requireNode("root").children.map { it.id })
        assertEquals(listOf("panel"), document.requireNode("sidebar").children.map { it.id })
        assertEquals(listOf("chip"), document.requireNode("panel").children.map { it.id })
    }

    @Test
    fun movesChildOutToRootAfterNamedSiblingDelevelingHeading() {
        val compiled = compileForEdit(doc)
        // Pull Chip out of Panel up to a top-level child of root, positioned right after Panel.
        val result = applySlmEdit(doc, MoveSection("chip", "root", afterSiblingId = "panel"), compiled)
        val new = result.requireNewSource()
        assertLosslessOutside(doc, new, assertNotNull(result.appliedRange))

        // Chip is de-leveled `### `->`## ` and sits between Panel and Sidebar.
        assertTrue("\n## Shape: Chip id chip\n" in new, new)
        assertFalse("\n### Shape: Chip id chip\n" in new, "old level-3 Chip heading still present:\n$new")
        val chipAt = new.indexOf("\n## Shape: Chip id chip\n")
        assertTrue(chipAt in (new.indexOf("\n## Frame: Panel id panel\n") + 1) until new.indexOf("\n## Frame: Sidebar id sidebar\n"), new)

        val recompiled = compileForEdit(new)
        assertNoErrors(recompiled)
        val document = recompiled.requireDocument()
        assertEquals(listOf("panel", "chip", "sidebar"), document.requireNode("root").children.map { it.id })
        assertTrue(document.requireNode("panel").children.isEmpty(), "Chip left Panel")
    }

    @Test
    fun refusesMoveThatExceedsHeadingDepth() {
        // Panel (##, with Chip ###) moved under a level-5 parent would produce a level-7 heading.
        val deep = """
            ---
            screen: s
            sourceLocale: en-US
            ---

            # Screen id root name «Screen»

            ## Frame: A id a

            ### Frame: B id b

            #### Frame: C id c

            ##### Frame: D id d

            ## Frame: Panel id panel

            ### Shape: Chip id chip
        """.trimIndent() + "\n"
        val compiled = compileForEdit(deep)
        val result = applySlmEdit(deep, MoveSection("panel", "d"), compiled)
        assertFalse(result.isApplied)
        val message = result.diagnostics.single { it.severity == DesignSeverity.Error }.message
        assertTrue("maximum heading depth of 6" in message, message)
    }

    @Test
    fun refusesMovingTheScreenRoot() {
        val compiled = compileForEdit(doc)
        // "root" is anchored to the whole document, not a heading line -> not movable.
        val result = applySlmEdit(doc, MoveSection("root", "sidebar"), compiled)
        assertFalse(result.isApplied)
        assertTrue(result.diagnostics.isNotEmpty())
    }

    private fun assertNoErrors(result: io.aequicor.visualization.engine.frontend.SlmCompileResult) {
        val errors = result.diagnostics.filter { it.severity == DesignSeverity.Error }
        assertTrue(errors.isEmpty(), "unexpected errors: ${errors.joinToString { it.message }}")
    }
}
