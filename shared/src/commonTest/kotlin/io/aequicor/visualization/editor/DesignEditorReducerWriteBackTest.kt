package io.aequicor.visualization.editor

import io.aequicor.visualization.editor.data.DefaultDesignDocumentRepository
import io.aequicor.visualization.editor.domain.LoadDesignDocumentUseCase
import io.aequicor.visualization.editor.presentation.DesignEditorIntent
import io.aequicor.visualization.editor.presentation.DesignEditorState
import io.aequicor.visualization.editor.presentation.createDesignEditorState
import io.aequicor.visualization.editor.presentation.reduceDesignEditor
import io.aequicor.visualization.engine.ir.model.DesignSeverity
import io.aequicor.visualization.engine.ir.model.SizingMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Resize write-back through the reducer: [DesignEditorIntent.ResizeNode] patches
 * the owning SLM source (fixed sizing), recompiles it and remerges the mission
 * document, leaving the other sources byte-identical.
 */
class DesignEditorReducerWriteBackTest {

    /** `tile_1` is authored with an explicit `node: id:` anchor in mission-overview.layout.md. */
    private val nodeId = "tile_1"
    private val owningFile = "mission-overview.layout.md"

    private fun freshState(): DesignEditorState =
        createDesignEditorState(LoadDesignDocumentUseCase(DefaultDesignDocumentRepository())())

    private fun DesignEditorState.sourceContent(fileName: String): String =
        assertNotNull(sources.firstOrNull { it.fileName == fileName }, "missing source $fileName").content

    @Test
    fun resizeWritesFixedWidthIntoOwningSourceOnly() {
        val state = reduceDesignEditor(freshState(), DesignEditorIntent.SelectNode(nodeId))
        val before = state.sources

        val next = reduceDesignEditor(state, DesignEditorIntent.ResizeNode(nodeId, width = 320.0))

        // Resolve-free inspection of the recompiled raw DesignNode.
        val node = assertNotNull(next.document?.nodeById(nodeId), "$nodeId present after write-back")
        assertEquals(SizingMode.Fixed, node.sizing?.horizontal, "typed width pins the axis to fixed")
        assertEquals(320.0, node.size.width)

        // Only the owning source changed; the other documents are byte-identical.
        assertNotEquals(
            state.sourceContent(owningFile),
            next.sourceContent(owningFile),
            "owning source rewritten",
        )
        before.filterNot { it.fileName == owningFile }.forEach { source ->
            assertEquals(
                source.content,
                next.sourceContent(source.fileName),
                "${source.fileName} must stay byte-identical",
            )
        }

        assertTrue(
            next.diagnostics.none { it.severity == DesignSeverity.Error },
            "write-back diagnostics: ${next.diagnostics.filter { it.severity == DesignSeverity.Error }}",
        )
        assertEquals(listOf(before), next.previousSources, "undo history captured the pre-edit sources")
        assertEquals(nodeId, next.selectedNodeId, "selection survives the recompile")
    }

    @Test
    fun secondResizeAppliesAgainstTheRecompiledSource() {
        val first = reduceDesignEditor(freshState(), DesignEditorIntent.ResizeNode(nodeId, width = 320.0))
        val second = reduceDesignEditor(first, DesignEditorIntent.ResizeNode(nodeId, height = 200.0))

        // The fingerprint chain works: the second patch resolves against the
        // recompiled result, not a stale one.
        val node = assertNotNull(second.document?.nodeById(nodeId))
        assertEquals(SizingMode.Fixed, node.sizing?.vertical)
        assertEquals(200.0, node.size.height)
        assertEquals(320.0, node.size.width, "first edit survives the second")
        assertEquals(SizingMode.Fixed, node.sizing?.horizontal)

        assertNotEquals(
            first.sourceContent(owningFile),
            second.sourceContent(owningFile),
            "second edit rewrote the owning source again",
        )
        assertTrue(second.diagnostics.none { it.severity == DesignSeverity.Error })
        assertEquals(2, second.previousSources.size, "each applied edit pushes one undo entry")
    }

    @Test
    fun resizeUnknownNodeLeavesSourcesUntouchedAndSurfacesDiagnostic() {
        val state = freshState()

        val next = reduceDesignEditor(state, DesignEditorIntent.ResizeNode("no_such_node", width = 100.0))

        assertEquals(state.sources, next.sources, "sources untouched")
        assertEquals(state.compiledResults, next.compiledResults, "compiled results untouched")
        assertEquals(state.document, next.document, "document untouched")
        assertTrue(next.previousSources.isEmpty(), "failed edit records no undo entry")
        assertTrue(
            next.diagnostics.any { it.severity == DesignSeverity.Error && "no_such_node" in it.message },
            "diagnostic names the unknown node: ${next.diagnostics}",
        )
    }
}
