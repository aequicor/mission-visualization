package io.aequicor.visualization.editor

import io.aequicor.visualization.editor.domain.MissionDocumentSource
import io.aequicor.visualization.editor.domain.compileMissionDocuments
import io.aequicor.visualization.editor.presentation.DesignEditorIntent
import io.aequicor.visualization.editor.presentation.createDesignEditorState
import io.aequicor.visualization.editor.presentation.reduceDesignEditor
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.engine.ir.model.DesignSeverity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Characterizes the structural write-back for a legitimately-expressible node whose free-form
 * `type` is "component" (kind = Frame, a normal in-tree node). The section writer used to emit a
 * `### Component:` heading — a component *definition* marker — which recompiled as a component def
 * and dropped the node from the page tree. NodeSectionWriter now falls back to a neutral `Node:`
 * prefix for component-family types (the kind still comes from `node: type:`), so duplicating such
 * a node round-trips faithfully into the SLM source instead of corrupting or silently dropping it.
 */
class ComponentTypeWriteBackTest {

    private val source = """
        ---
        screen: s
        sourceLocale: en-US
        ---

        # Screen

        node:
          id: root
          name: Screen

        ## Frame: Host

        node:
          type: frame
          id: host
          name: Host
        layout:
          mode: column

        ### Frame: Widget

        node:
          type: component
          id: widget
          name: Widget
    """.trimIndent() + "\n"

    private fun state() = createDesignEditorState(
        compileMissionDocuments(listOf(MissionDocumentSource("comp.layout.md", source))),
    )

    @Test
    fun componentTypedNodeIsANormalInTreeFrame() {
        val before = state()
        val widget = assertNotNull(before.document?.nodeById("widget"), "widget compiles into the page tree")
        assertEquals("component", widget.type, "authored free-form type survives")
        assertTrue(widget.kind is DesignNodeKind.Frame, "component-typed node is a Frame kind (in-tree, expressible)")
        assertTrue(
            before.document?.nodeById("host")?.children?.any { it.id == "widget" } == true,
            "widget is a child of host",
        )
    }

    @Test
    fun duplicatingComponentTypedNodePersistsFaithfully() {
        val before = state()
        val next = reduceDesignEditor(before, DesignEditorIntent.DuplicateNodes(setOf("widget")))

        // The in-memory duplicate is the canvas authority: original + a fresh clone under host.
        assertNotNull(next.document?.nodeById("widget"), "original survives in-memory")
        val cloneId = assertNotNull(next.selectedNodeIds.firstOrNull(), "the clone is selected")
        assertTrue(cloneId != "widget", "clone has a fresh id")

        // The write-back now persists a faithful clone: the owning source was rewritten (no more
        // silent drop), it carries no error diagnostics, and it recompiles with BOTH ids present as
        // in-tree component-typed Frames under host.
        assertTrue(before.sources.single().content != next.sources.single().content, "owning source rewritten")
        assertEquals(1, next.previousSources.size, "one source-undo entry captured")
        assertTrue(
            next.diagnostics.none { it.severity == DesignSeverity.Error },
            "no write-back errors: ${next.diagnostics.filter { it.severity == DesignSeverity.Error }}",
        )

        val recompiled = compileMissionDocuments(next.sources).document ?: error("recompile produced no document")
        listOf("widget", cloneId).forEach { id ->
            val node = assertNotNull(recompiled.nodeById(id), "$id present after recompile")
            assertEquals("component", node.type, "$id keeps the authored component type")
            assertTrue(node.kind is DesignNodeKind.Frame, "$id stays a Frame (not lifted to a component def)")
            assertTrue(
                recompiled.nodeById("host")?.children?.any { it.id == id } == true,
                "$id is a child of host",
            )
        }
    }
}
