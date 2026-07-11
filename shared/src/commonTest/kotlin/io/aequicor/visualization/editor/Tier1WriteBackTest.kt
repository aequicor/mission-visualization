package io.aequicor.visualization.editor

import io.aequicor.visualization.editor.presentation.DesignEditorIntent
import io.aequicor.visualization.editor.presentation.DesignEditorState
import io.aequicor.visualization.editor.presentation.EditorLayoutMode
import io.aequicor.visualization.editor.presentation.PaddingSide
import io.aequicor.visualization.editor.presentation.createDesignEditorState
import io.aequicor.visualization.editor.presentation.reduceDesignEditor
import io.aequicor.visualization.engine.ir.model.DesignSeverity
import io.aequicor.visualization.engine.ir.model.LayoutMode
import io.aequicor.visualization.engine.ir.model.literalOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tier-1 property write-back through the reducer: rename, visibility, lock, opacity,
 * radius and layout mode/gap/padding patch the owning SLM source and mirror onto the
 * working document, leaving every other source byte-identical.
 *
 * `frame_overview` is the root frame of mission-overview.layout.md, authored with a
 * `node:` anchor plus `layout:` and `style:` blocks, so every property below is
 * addressable there.
 */
class Tier1WriteBackTest {

    private val nodeId = "frame_overview"
    private val owningFile = "mission-overview.layout.md"

    private fun freshState(): DesignEditorState =
        reduceDesignEditor(
            createDesignEditorState(missionDemoDocuments()),
            DesignEditorIntent.SelectNode(nodeId),
        )

    private fun DesignEditorState.sourceOf(fileName: String): String =
        assertNotNull(sources.firstOrNull { it.fileName == fileName }, "missing source $fileName").content

    /** Asserts the edit patched only [owningFile] and produced no error diagnostics. */
    private fun DesignEditorState.assertWroteBack(before: DesignEditorState) {
        assertNotEquals(before.sourceOf(owningFile), sourceOf(owningFile), "owning source rewritten")
        before.sources.filterNot { it.fileName == owningFile }.forEach { source ->
            assertEquals(source.content, sourceOf(source.fileName), "${source.fileName} must stay byte-identical")
        }
        assertTrue(
            diagnostics.none { it.severity == DesignSeverity.Error },
            "write-back diagnostics: ${diagnostics.filter { it.severity == DesignSeverity.Error }}",
        )
        assertEquals(listOf(before.sources), previousSources, "source undo captured the pre-edit sources")
    }

    @Test
    fun renameWritesNodeNameIntoOwningSource() {
        val before = freshState()
        val next = reduceDesignEditor(before, DesignEditorIntent.RenameNode(nodeId, "CommandDeck"))
        assertEquals("CommandDeck", next.document?.nodeById(nodeId)?.name)
        assertTrue("CommandDeck" in next.sourceOf(owningFile))
        next.assertWroteBack(before)
    }

    @Test
    fun setVisibleWritesNodeVisibleFlag() {
        val before = freshState()
        val next = reduceDesignEditor(before, DesignEditorIntent.SetVisible(nodeId, false))
        assertEquals(false, next.document?.nodeById(nodeId)?.visible?.literalOrNull())
        assertTrue("visible no" in next.sourceOf(owningFile))
        next.assertWroteBack(before)
    }

    @Test
    fun setLockedWritesNodeLockedFlag() {
        val before = freshState()
        val next = reduceDesignEditor(before, DesignEditorIntent.SetLocked(nodeId, true))
        assertEquals(true, next.document?.nodeById(nodeId)?.locked)
        assertTrue("locked yes" in next.sourceOf(owningFile))
        next.assertWroteBack(before)
    }

    @Test
    fun opacityWritesStyleOpacity() {
        val before = freshState()
        val next = reduceDesignEditor(before, DesignEditorIntent.UpdateOpacity(nodeId, 0.5))
        assertEquals(0.5, next.document?.nodeById(nodeId)?.opacity?.literalOrNull())
        assertTrue("opacity 0.5" in next.sourceOf(owningFile))
        next.assertWroteBack(before)
    }

    @Test
    fun cornerRadiusWritesStyleRadius() {
        val before = freshState()
        val next = reduceDesignEditor(before, DesignEditorIntent.UpdateCornerRadius(nodeId, 24.0))
        assertNotNull(next.document?.nodeById(nodeId)?.cornerRadius, "radius applied to document")
        assertTrue("radius 24" in next.sourceOf(owningFile))
        next.assertWroteBack(before)
    }

    @Test
    fun layoutModeWritesLayoutMode() {
        val before = freshState()
        val next = reduceDesignEditor(before, DesignEditorIntent.SetLayoutMode(nodeId, EditorLayoutMode.Horizontal))
        assertEquals(LayoutMode.Horizontal, next.document?.nodeById(nodeId)?.layout?.mode)
        assertTrue("row" in next.sourceOf(owningFile))
        next.assertWroteBack(before)
    }

    @Test
    fun layoutGapWritesLayoutGap() {
        val before = freshState()
        val next = reduceDesignEditor(before, DesignEditorIntent.SetLayoutGap(nodeId, 40.0))
        assertTrue("gap 40" in next.sourceOf(owningFile))
        next.assertWroteBack(before)
    }

    @Test
    fun paddingAllWritesEveryLogicalSide() {
        val before = freshState()
        val next = reduceDesignEditor(before, DesignEditorIntent.SetLayoutPadding(nodeId, PaddingSide.All, 32.0))
        val source = next.sourceOf(owningFile)
        assertTrue("padding 32" in source, "uniform padding written")
        next.assertWroteBack(before)
    }
}
