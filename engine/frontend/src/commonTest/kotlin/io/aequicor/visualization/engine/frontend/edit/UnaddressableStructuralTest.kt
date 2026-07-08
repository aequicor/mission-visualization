package io.aequicor.visualization.engine.frontend.edit

import io.aequicor.visualization.engine.frontend.semantics.SpecRuDocument
import io.aequicor.visualization.engine.ir.model.DesignNode
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Structural edits degrade cleanly (no source mutation) for nodes that have no addressable heading
 * anchor — prose segments, ```ir splices, the screen root — so the caller can fall back to an
 * in-memory edit. Mirrors [UnaddressableNodeTest] for the attribute path.
 */
class UnaddressableStructuralTest {
    private val leaf = DesignNode(id = "new_frame", type = "frame", kind = DesignNodeKind.Frame, name = "New frame")

    @Test
    fun insertUnderProseSegmentParentIsNotApplied() {
        val compiled = compileForEdit(SpecRuDocument)
        // "createMission" is synthesized from a paragraph segment: no heading anchor.
        val result = applySlmEdit(SpecRuDocument, InsertChildSubtree("createMission", leaf), compiled)
        assertFalse(result.isApplied)
        val message = result.diagnostics.single().message
        assertTrue("no addressable source anchor" in message, message)
        assertTrue("promote" in message, message)
    }

    @Test
    fun deleteIrSplicedNodeRedirectsToTheEmbeddedJson() {
        val doc = """
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
        """.trimIndent() + "\n"
        val compiled = compileForEdit(doc)
        assertTrue("customAlertIcon" in compiled.editIndex.irSpliceNodes)
        val result = applySlmEdit(doc, DeleteSection("customAlertIcon"), compiled)
        assertFalse(result.isApplied)
        val message = result.diagnostics.single().message
        assertTrue("```ir" in message, message)
        assertTrue("edit the embedded JSON directly" in message, message)
    }

    @Test
    fun deleteScreenRootIsRefused() {
        val compiled = compileForEdit(SpecRuDocument)
        // The screen root's anchor is the whole document, not a heading line.
        val result = applySlmEdit(SpecRuDocument, DeleteSection("missionDashboard"), compiled)
        assertFalse(result.isApplied)
        assertTrue(result.diagnostics.isNotEmpty())
    }

    @Test
    fun insertAfterUnaddressableSiblingIsNotApplied() {
        val doc = """
            ---
            screen: s
            sourceLocale: en-US
            ---

            # Screen

            node:
              id: root
              name: Screen

            ## Frame: Panel

            node:
              type: frame
              id: panel
              name: Panel
        """.trimIndent() + "\n"
        val compiled = compileForEdit(doc)
        val result = applySlmEdit(doc, InsertChildSubtree("root", leaf, afterSiblingId = "ghost"), compiled)
        assertFalse(result.isApplied)
        val message = result.diagnostics.single().message
        assertTrue("no addressable source anchor" in message, message)
    }
}
