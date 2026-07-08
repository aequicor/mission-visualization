package io.aequicor.visualization.editor

import io.aequicor.visualization.editor.domain.MissionDocumentSource
import io.aequicor.visualization.editor.domain.compileMissionDocuments
import io.aequicor.visualization.editor.presentation.DesignEditorIntent
import io.aequicor.visualization.editor.presentation.DesignEditorState
import io.aequicor.visualization.editor.presentation.ZOrderMove
import io.aequicor.visualization.editor.presentation.createDesignEditorState
import io.aequicor.visualization.editor.presentation.reduceDesignEditor
import io.aequicor.visualization.engine.ir.model.DesignDocument
import io.aequicor.visualization.engine.ir.model.DesignSeverity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Adversarial id-stability probes: after a structural write-back, the freshly recompiled owning
 * source MUST agree with the in-memory working document node-for-node — same id-to-property
 * mapping and same tree topology on every page. The reducer's veto only compares id *sets*
 * (plus, for reparent, parent-of(moved)); these tests hunt for cases where the set survives but
 * the id->node *identity* silently diverges (a swap), which corrupts the round-trip.
 */
class IdStabilityAdversarialTest {

    private fun state(fileName: String, content: String): DesignEditorState =
        createDesignEditorState(compileMissionDocuments(listOf(MissionDocumentSource(fileName, content))))

    private fun DesignEditorState.recompiledOf(fileName: String): DesignDocument {
        val i = sources.indexOfFirst { it.fileName == fileName }
        assertTrue(i >= 0, "missing $fileName")
        return assertNotNull(compiledResults[i].document, "no recompiled doc for $fileName")
    }

    private fun DesignEditorState.sourceText(fileName: String): String =
        assertNotNull(sources.firstOrNull { it.fileName == fileName }, "missing $fileName").content

    /** id -> (name, width) fingerprint of every page node — the identity map the round-trip must preserve. */
    private fun DesignDocument.fingerprint(): Map<String, Pair<String, Double?>> =
        pages.flatMap { it.allNodes() }.associate { it.id to (it.name to it.size.width) }

    /**
     * Core invariant: whenever a structural write-back actually rewrites the source (i.e. it did
     * NOT fall back to in-memory), the recompiled owning document must equal the in-memory working
     * document node-for-node (id -> name+width and full id set). A mismatch is a corrupting divergence.
     */
    private fun assertNoDivergence(before: DesignEditorState, after: DesignEditorState, fileName: String) {
        val sourceRewritten = before.sourceText(fileName) != after.sourceText(fileName)
        if (!sourceRewritten) return // in-memory fallback: nothing was persisted, no divergence possible
        val working = assertNotNull(after.document, "working document present")
        val recompiled = after.recompiledOf(fileName)
        assertTrue(
            after.diagnostics.none { it.severity == DesignSeverity.Error },
            "write-back errors: ${after.diagnostics.filter { it.severity == DesignSeverity.Error }}",
        )
        assertEquals(
            working.fingerprint(),
            recompiled.fingerprint(),
            "id->node identity map diverged between the working document and the recompiled source",
        )
    }

    // --- REPARENT: swap of two id-less same-slug nodes across parents --------------------------

    private val swapDoc = """
        ---
        screen: swap
        sourceLocale: en-US
        ---

        # Screen

        node:
          id: root
          name: Screen

        ## Frame: Home

        node:
          type: frame
          id: home
          name: Home

        ### Frame: Panel

        node:
          type: frame
          name: Panel
        layout:
          sizing:
            width:
              type: fixed
              value: 100
            height:
              type: fixed
              value: 100

        ## Frame: Tray

        node:
          type: frame
          id: tray
          name: Tray

        ### Frame: Panel

        node:
          type: frame
          name: Panel
        layout:
          sizing:
            width:
              type: fixed
              value: 200
            height:
              type: fixed
              value: 200
    """.trimIndent() + "\n"

    @Test
    fun reparentIdLessSameSlugNodesDoesNotSwapIdentity() {
        val before = state("swap.layout.md", swapDoc)
        // Two id-less "Panel" frames: the one under Home slugs to "panel" (parsed first, width 100);
        // the one under Tray to "panel-2" (width 200).
        val doc = assertNotNull(before.document)
        assertEquals(100.0, doc.nodeById("panel")?.size?.width, "panel = Home's 100-wide Panel")
        assertEquals(200.0, doc.nodeById("panel-2")?.size?.width, "panel-2 = Tray's 200-wide Panel")

        // Move the Home panel ("panel", width 100) to become a child of Tray (which already holds
        // panel-2). In-memory the moved node keeps id "panel" (width 100) under Tray.
        val after = reduceDesignEditor(before, DesignEditorIntent.ReparentNode("panel", "tray"))

        val working = assertNotNull(after.document)
        assertEquals(
            200.0,
            working.nodeById("panel-2")?.size?.width,
            "sanity: in-memory panel-2 is still Tray's 200-wide panel",
        )
        assertEquals(
            100.0,
            working.nodeById("panel")?.size?.width,
            "sanity: in-memory 'panel' is still the moved 100-wide Home panel",
        )

        // If the source was rewritten, the recompiled identity map MUST match the working document.
        assertNoDivergence(before, after, "swap.layout.md")
    }

    // --- REPARENT: three-node ordinal drift under one destination ------------------------------

    private val tripleDoc = """
        ---
        screen: triple
        sourceLocale: en-US
        ---

        # Screen

        node:
          id: root
          name: Screen

        ## Frame: Bin

        node:
          type: frame
          id: bin
          name: Bin

        ### Frame: Item

        node:
          type: frame
          name: Item
        layout:
          sizing:
            width:
              type: fixed
              value: 11
            height:
              type: fixed
              value: 11

        ### Frame: Item

        node:
          type: frame
          name: Item
        layout:
          sizing:
            width:
              type: fixed
              value: 22
            height:
              type: fixed
              value: 22

        ## Frame: Dock

        node:
          type: frame
          id: dock
          name: Dock

        ### Frame: Item

        node:
          type: frame
          name: Item
        layout:
          sizing:
            width:
              type: fixed
              value: 33
            height:
              type: fixed
              value: 33
    """.trimIndent() + "\n"

    @Test
    fun reparentIntoRunOfIdLessSlugsKeepsIdentity() {
        val before = state("triple.layout.md", tripleDoc)
        val doc = assertNotNull(before.document)
        // item(11) & item-2(22) under Bin, item-3(33) under Dock.
        assertEquals(11.0, doc.nodeById("item")?.size?.width)
        assertEquals(22.0, doc.nodeById("item-2")?.size?.width)
        assertEquals(33.0, doc.nodeById("item-3")?.size?.width)

        // Move item-3 (Dock's 33-wide) under Bin. In-memory: Bin = [item(11), item-2(22), item-3(33)].
        val after = reduceDesignEditor(before, DesignEditorIntent.ReparentNode("item-3", "bin"))
        assertNoDivergence(before, after, "triple.layout.md")
    }

    // --- DELETE: identity of survivors --------------------------------------------------------

    @Test
    fun deleteIdLessSiblingKeepsSurvivorIdentity() {
        val before = state("swap.layout.md", swapDoc)
        // Delete Home's panel (width 100). Survivor Tray panel is width 200. If the veto let a
        // patched source through, the recompiled survivor must still map its id to width 200.
        val after = reduceDesignEditor(before, DesignEditorIntent.DeleteNodes(setOf("panel")))
        assertNoDivergence(before, after, "swap.layout.md")
    }

    // --- REORDER: id-less same-slug run --------------------------------------------------------

    @Test
    fun reorderIdLessRunKeepsIdentity() {
        val before = state("triple.layout.md", tripleDoc)
        // Reorder within Bin's two id-less "Item" siblings.
        val after = reduceDesignEditor(before, DesignEditorIntent.ReorderNode("item", ZOrderMove.ToFront))
        assertNoDivergence(before, after, "triple.layout.md")
    }

    // --- DUPLICATE: id-less original beside its clone ------------------------------------------

    @Test
    fun duplicateBesideIdLessSiblingKeepsIdentity() {
        val before = state("swap.layout.md", swapDoc)
        // Duplicate Home's id-less panel; the clone gets an explicit minted id. The original id-less
        // panel-2 (Tray, width 200) must not have its identity stolen by the inserted section.
        val after = reduceDesignEditor(before, DesignEditorIntent.DuplicateNodes(setOf("panel")))
        assertNoDivergence(before, after, "swap.layout.md")
    }
}
