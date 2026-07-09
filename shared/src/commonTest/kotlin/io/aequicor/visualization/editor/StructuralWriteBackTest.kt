package io.aequicor.visualization.editor

import io.aequicor.visualization.editor.domain.MissionDocumentSource
import io.aequicor.visualization.editor.domain.compileMissionDocuments
import io.aequicor.visualization.editor.presentation.DesignEditorIntent
import io.aequicor.visualization.editor.presentation.DesignEditorState
import io.aequicor.visualization.editor.presentation.NewObjectKind
import io.aequicor.visualization.editor.presentation.ZOrderMove
import io.aequicor.visualization.editor.presentation.createDesignEditorState
import io.aequicor.visualization.editor.presentation.reduceDesignEditor
import io.aequicor.visualization.engine.ir.model.DesignDocument
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.engine.ir.model.DesignSeverity
import io.aequicor.visualization.engine.ir.model.ShapeType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Structural delete write-back through the reducer: [DesignEditorIntent.DeleteNodes] drops the
 * owning heading section from the SLM source (surviving ids intact) while the in-memory document
 * stays the authority. Cross-page selections and any drift-inducing delete fall back to an
 * in-memory-only delete with every source byte-identical — the id-preservation net.
 */
class StructuralWriteBackTest {

    private val owningFile = "mission-overview.layout.md"

    private fun freshState(): DesignEditorState =
        createDesignEditorState(legacyMissionDocuments())

    private fun DesignEditorState.sourceOf(fileName: String): String =
        assertNotNull(sources.firstOrNull { it.fileName == fileName }, "missing source $fileName").content

    /** The freshly-recompiled IR for [fileName]'s owning source (post write-back). */
    private fun DesignEditorState.compiledDocumentOf(fileName: String): DesignDocument? {
        val index = sources.indexOfFirst { it.fileName == fileName }
        return if (index < 0) null else compiledResults[index].document
    }

    /** Owning source rewritten, all other sources byte-identical, no error diagnostics. */
    private fun DesignEditorState.assertWroteBack(before: DesignEditorState) {
        assertNotEquals(before.sourceOf(owningFile), sourceOf(owningFile), "owning source rewritten")
        before.sources.filterNot { it.fileName == owningFile }.forEach { source ->
            assertEquals(source.content, sourceOf(source.fileName), "${source.fileName} stays byte-identical")
        }
        assertTrue(
            diagnostics.none { it.severity == DesignSeverity.Error },
            "write-back errors: ${diagnostics.filter { it.severity == DesignSeverity.Error }}",
        )
    }

    @Test
    fun deleteWritesBackAndSurvivorsIntact() {
        val before = reduceDesignEditor(freshState(), DesignEditorIntent.SelectNode("overview_wide"))

        val next = reduceDesignEditor(before, DesignEditorIntent.DeleteNodes(setOf("overview_wide")))

        // Document mirrors the delete; every sibling id survives the recompile.
        assertNull(next.document?.nodeById("overview_wide"), "deleted node gone from document")
        listOf("frame_overview", "overview_hero", "overview_tiles", "tile_1", "overview_cards", "card_1").forEach { id ->
            assertNotNull(next.document?.nodeById(id), "survivor $id kept its id")
        }

        // The owning source dropped the whole `## Shape: Wide block` section.
        val source = next.sourceOf(owningFile)
        assertTrue("overview_wide" !in source, "wide-block section removed from source")
        assertTrue("Wide block" !in source, "wide-block heading removed from source")
        next.assertWroteBack(before)
        assertEquals(listOf(before.sources), next.previousSources, "source undo captured the pre-edit sources")
    }

    @Test
    fun deleteSectionWithDescendantsRemovesSubtreeFromSource() {
        val before = freshState()

        val next = reduceDesignEditor(before, DesignEditorIntent.DeleteNodes(setOf("overview_tiles")))

        // The container and every descendant instance leave both the document and the source.
        listOf("overview_tiles", "tile_1", "tile_2", "tile_3").forEach { id ->
            assertNull(next.document?.nodeById(id), "$id removed with its section")
            assertTrue(id !in next.sourceOf(owningFile), "$id removed from source")
        }
        // Untouched siblings survive.
        listOf("overview_hero", "overview_wide", "overview_cards").forEach { id ->
            assertNotNull(next.document?.nodeById(id), "sibling $id survives")
        }
        next.assertWroteBack(before)
    }

    @Test
    fun deleteMultiPageSelectionFallsBack() {
        val before = freshState()
        // Two heading-anchored nodes on different pages: a single-source patch can't express the
        // two-file transaction, so the delete stays in-memory with every source untouched.
        val ids = setOf("overview_hero", "telemetry_header")
        assertNotNull(before.document?.nodeById("telemetry_header"), "telemetry_header present")

        val next = reduceDesignEditor(before, DesignEditorIntent.DeleteNodes(ids))

        // Document still reflects BOTH deletes.
        ids.forEach { id -> assertNull(next.document?.nodeById(id), "$id deleted in-memory") }
        // But no source was rewritten.
        before.sources.forEach { source ->
            assertEquals(source.content, next.sourceOf(source.fileName), "${source.fileName} untouched by multi-page delete")
        }
        assertTrue(next.previousSources.isEmpty(), "no source undo entry for the in-memory fallback")
    }

    @Test
    fun idPreservationVetoDiscardsPatchedSource() {
        // Two same-named frames with no explicit id compile to "panel" and "panel-2". Deleting the
        // first would let the survivor recompile back to "panel" — a drift the veto must catch.
        val drift = """
            ---
            screen: drift
            sourceLocale: en-US
            ---

            # Screen

            node:
              id: root
              name: Screen

            ## Frame: Panel

            node:
              type: frame
              name: Panel

            ## Frame: Panel

            node:
              type: frame
              name: Panel
        """.trimIndent() + "\n"
        val before = createDesignEditorState(
            compileMissionDocuments(listOf(MissionDocumentSource("drift.layout.md", drift))),
        )
        // Sanity: the generated ids are exactly the drift-prone pair.
        assertNotNull(before.document?.nodeById("panel"), "first Panel -> panel")
        assertNotNull(before.document?.nodeById("panel-2"), "second Panel -> panel-2")

        val next = reduceDesignEditor(before, DesignEditorIntent.DeleteNodes(setOf("panel")))

        // The in-memory delete stands: "panel" gone, "panel-2" survives.
        assertNull(next.document?.nodeById("panel"), "selected node deleted in-memory")
        assertNotNull(next.document?.nodeById("panel-2"), "sibling survives in-memory")
        // But the veto discarded the patched source — the source is byte-identical, no undo entry.
        assertEquals(before.sources, next.sources, "drift-inducing patch discarded, source intact")
        assertTrue(next.previousSources.isEmpty(), "vetoed write-back records no source undo entry")
    }

    // --- Create ------------------------------------------------------------------

    @Test
    fun createObjectWritesSectionWithMintedId() {
        val before = freshState()
        val rootFrame = "frame_overview"
        assertNotNull(before.document?.nodeById(rootFrame), "root frame present")

        val next = reduceDesignEditor(
            before,
            DesignEditorIntent.CreateObject(NewObjectKind.Rectangle, parentId = rootFrame, x = 24.0, y = 24.0, width = 120.0, height = 80.0),
        )

        // A fresh explicit id was minted and the working document mirrors the create.
        val mintedId = next.selectedNodeId
        assertTrue(mintedId.startsWith("rect_"), "minted a fresh rect id: $mintedId")
        val created = assertNotNull(next.document?.nodeById(mintedId), "created node in document")
        assertTrue(created.kind is DesignNodeKind.Shape, "created a shape")
        assertTrue(
            next.document?.nodeById(rootFrame)?.children?.any { it.id == mintedId } == true,
            "created node is a child of the root frame",
        )

        // The owning source gained a heading section carrying that explicit id, and the recompiled
        // IR faithfully round-trips the new node's kind and size.
        val source = next.sourceOf(owningFile)
        assertTrue(mintedId in source, "minted id written to source")
        assertTrue("Shape:" in source, "shape heading section written to source")
        val recompiled = assertNotNull(next.compiledDocumentOf(owningFile), "owning source recompiled")
        val recompiledNode = assertNotNull(recompiled.nodeById(mintedId), "minted node present after recompile")
        assertEquals(ShapeType.Rectangle, (recompiledNode.kind as DesignNodeKind.Shape).shape)
        assertEquals(120.0, recompiledNode.size.width)
        assertEquals(80.0, recompiledNode.size.height)

        next.assertWroteBack(before)
        assertEquals(listOf(before.sources), next.previousSources, "source undo captured the pre-edit sources")
    }

    // --- Duplicate ---------------------------------------------------------------

    @Test
    fun duplicateWritesCloneWithFreshExplicitIds() {
        val before = freshState()
        // overview_hero is a pure Shape (faithfully expressible), so its duplicate writes back.
        assertNotNull(before.document?.nodeById("overview_hero"), "original present")

        val next = reduceDesignEditor(before, DesignEditorIntent.DuplicateNodes(setOf("overview_hero")))

        // A single clone with a fresh id is selected; the original keeps its id (id stability).
        assertEquals(1, next.selectedNodeIds.size)
        val cloneId = next.selectedNodeIds.first()
        assertNotEquals("overview_hero", cloneId, "clone got a fresh id")
        assertNotNull(next.document?.nodeById("overview_hero"), "original id preserved")
        val clone = assertNotNull(next.document?.nodeById(cloneId), "clone in document")
        assertTrue(clone.kind is DesignNodeKind.Shape)
        // The clone is a sibling of the original under the same parent.
        assertTrue(
            next.document?.nodeById("frame_overview")?.children?.map { it.id }?.containsAll(listOf("overview_hero", cloneId)) == true,
            "clone sits beside the original",
        )

        // The owning source carries the clone's explicit id, and it round-trips on recompile.
        assertTrue(cloneId in next.sourceOf(owningFile), "clone id written to source")
        val recompiled = assertNotNull(next.compiledDocumentOf(owningFile), "owning source recompiled")
        assertNotNull(recompiled.nodeById(cloneId), "clone present after recompile")
        assertNotNull(recompiled.nodeById("overview_hero"), "original survives recompile")
        next.assertWroteBack(before)
    }

    @Test
    fun duplicateSubtreeRecursesFreshIdsAndPreservesOriginals() {
        // A frame with a shape child: duplicating the frame must re-id the whole subtree and emit
        // both as nested heading sections, leaving the originals untouched.
        val nested = """
            ---
            screen: nested
            sourceLocale: en-US
            ---

            # Screen

            node:
              id: root
              name: Screen

            ## Frame: Card

            node:
              type: frame
              id: card
              name: Card

            ### Shape: Body

            node:
              type: shape
              id: body
              name: Body
            shape:
              kind: rectangle
            layout:
              sizing:
                width:
                  type: fixed
                  value: 40
                height:
                  type: fixed
                  value: 40
        """.trimIndent() + "\n"
        val before = createDesignEditorState(
            compileMissionDocuments(listOf(MissionDocumentSource("nested.layout.md", nested))),
        )

        val next = reduceDesignEditor(before, DesignEditorIntent.DuplicateNodes(setOf("card")))

        // Clone root + its descendant both got fresh ids; both originals survive.
        val cloneId = next.selectedNodeIds.first()
        assertNotEquals("card", cloneId, "clone frame got a fresh id")
        val clone = assertNotNull(next.document?.nodeById(cloneId), "clone frame present")
        val childCloneId = assertNotNull(clone.children.firstOrNull()?.id, "clone kept a child")
        assertNotEquals("body", childCloneId, "descendant re-id'd")
        listOf("card", "body").forEach { id ->
            assertNotNull(next.document?.nodeById(id), "original $id preserved")
        }

        // Both fresh ids landed in the source and round-trip on recompile as a nested subtree.
        val source = next.sourceOf("nested.layout.md")
        assertTrue(cloneId in source && childCloneId in source, "both clone ids written to source")
        val recompiled = assertNotNull(next.compiledDocumentOf("nested.layout.md"), "recompiled")
        val recompiledClone = assertNotNull(recompiled.nodeById(cloneId), "clone frame after recompile")
        assertTrue(recompiledClone.children.any { it.id == childCloneId }, "child clone nested under the clone frame")
        assertNotNull(recompiled.nodeById("card"), "original card survives recompile")
        assertTrue(
            next.diagnostics.none { it.severity == DesignSeverity.Error },
            "no write-back errors: ${next.diagnostics.filter { it.severity == DesignSeverity.Error }}",
        )
    }

    // --- Reorder -----------------------------------------------------------------

    @Test
    fun reorderWritesOrderScalarsOverRun() {
        val before = freshState()
        // frame_overview's four top-level children are all heading-anchored -> the whole run is
        // addressable, so a reorder persists as explicit `order:` scalars.
        assertEquals(
            listOf("overview_hero", "overview_tiles", "overview_wide", "overview_cards"),
            before.document?.nodeById("frame_overview")?.children?.map { it.id },
            "authored top-level order",
        )

        val next = reduceDesignEditor(before, DesignEditorIntent.ReorderNode("overview_hero", ZOrderMove.ToFront))

        val expectedOrder = listOf("overview_tiles", "overview_wide", "overview_cards", "overview_hero")
        // The in-memory document is the authority for z-order.
        assertEquals(
            expectedOrder,
            next.document?.nodeById("frame_overview")?.children?.map { it.id },
            "in-memory reorder moved hero to the front (last child paints on top)",
        )
        // The owning source gained explicit order scalars across the run, and the recompiled IR's
        // stable order-sort reproduces exactly the in-memory arrangement with zero id drift.
        val source = next.sourceOf(owningFile)
        assertTrue("order: 40" in source, "hero written to the back of the paint order (order 40)")
        val recompiled = assertNotNull(next.compiledDocumentOf(owningFile), "owning source recompiled")
        assertEquals(
            expectedOrder,
            recompiled.nodeById("frame_overview")?.children?.map { it.id },
            "recompiled order-sort matches the in-memory reorder",
        )
        // Every id survives the recompile (the id-preservation net accepted the write).
        listOf("overview_hero", "overview_tiles", "overview_wide", "overview_cards").forEach { id ->
            assertNotNull(recompiled.nodeById(id), "survivor $id kept its id")
        }
        next.assertWroteBack(before)
        assertEquals(listOf(before.sources), next.previousSources, "source undo captured the pre-edit sources")
    }

    @Test
    fun reorderWithIrSpliceSiblingFallsBack() {
        val before = freshState()
        // overview_cards compiles to [card_1, card_2, card_3]; card_2 is defined by an ```ir fence
        // with no heading anchor, so the order-scalar batch can't address it and the whole write aborts.
        assertEquals(
            listOf("card_1", "card_2", "card_3"),
            before.document?.nodeById("overview_cards")?.children?.map { it.id },
            "authored card order",
        )

        val next = reduceDesignEditor(before, DesignEditorIntent.ReorderNode("card_1", ZOrderMove.Forward))

        // The canvas reflects the move (document is the authority + fallback)...
        assertEquals(
            listOf("card_2", "card_1", "card_3"),
            next.document?.nodeById("overview_cards")?.children?.map { it.id },
            "in-memory reorder applied",
        )
        // ...but the unaddressable sibling forced an in-memory fallback: every source byte-identical.
        before.sources.forEach { source ->
            assertEquals(source.content, next.sourceOf(source.fileName), "${source.fileName} untouched by ir-splice reorder")
        }
        assertTrue(next.previousSources.isEmpty(), "in-memory fallback records no source undo entry")
    }

    // --- Reparent ----------------------------------------------------------------

    @Test
    fun reparentSamePageWritesBack() {
        val before = freshState()
        // overview_hero (a Shape, faithfully expressible) starts as a direct child of frame_overview;
        // overview_tiles is a sibling frame on the same page. Both are heading-anchored -> the move
        // re-levels the hero section under Tiles and writes back.
        assertTrue(
            before.document?.nodeById("frame_overview")?.children?.any { it.id == "overview_hero" } == true,
            "hero starts under the root frame",
        )

        val next = reduceDesignEditor(before, DesignEditorIntent.ReparentNode("overview_hero", "overview_tiles"))

        // The in-memory document is the authority: hero moved under Tiles, gone from the root frame.
        assertTrue(
            next.document?.nodeById("overview_tiles")?.children?.any { it.id == "overview_hero" } == true,
            "hero reparented under Tiles in the document",
        )
        assertTrue(
            next.document?.nodeById("frame_overview")?.children?.none { it.id == "overview_hero" } == true,
            "hero left the root frame's children",
        )

        // The owning source moved and re-leveled the hero section; the recompile reproduces the new
        // parent/child topology with every id preserved (the id + parent-of veto both passed).
        assertTrue("overview_hero" in next.sourceOf(owningFile), "hero id still in source")
        val recompiled = assertNotNull(next.compiledDocumentOf(owningFile), "owning source recompiled")
        assertTrue(
            recompiled.nodeById("overview_tiles")?.children?.any { it.id == "overview_hero" } == true,
            "recompiled tree parents hero under Tiles",
        )
        assertEquals(
            next.document?.nodeById("overview_tiles")?.children?.map { it.id },
            recompiled.nodeById("overview_tiles")?.children?.map { it.id },
            "recompiled child order matches the in-memory reorder",
        )
        assertNull(
            recompiled.nodeById("frame_overview")?.children?.firstOrNull { it.id == "overview_hero" },
            "recompiled root frame no longer parents hero",
        )
        next.assertWroteBack(before)
        assertEquals(listOf(before.sources), next.previousSources, "source undo captured the pre-edit sources")
    }

    @Test
    fun reparentCrossPageFallsBack() {
        val before = freshState()
        // overview_hero lives on the Overview page; frame_telemetry roots the Telemetry page. A
        // cross-page reparent is a two-source transaction a single-source patch can't express.
        assertNotNull(before.document?.nodeById("frame_telemetry"), "telemetry root present")

        val next = reduceDesignEditor(before, DesignEditorIntent.ReparentNode("overview_hero", "frame_telemetry"))

        // The canvas reflects the cross-page move (document is authority + fallback)...
        assertTrue(
            next.document?.nodeById("frame_telemetry")?.children?.any { it.id == "overview_hero" } == true,
            "hero moved under the telemetry root in-memory",
        )
        // ...but no source was rewritten and no source-undo entry recorded.
        before.sources.forEach { source ->
            assertEquals(source.content, next.sourceOf(source.fileName), "${source.fileName} untouched by cross-page reparent")
        }
        assertTrue(next.previousSources.isEmpty(), "in-memory fallback records no source undo entry")
    }

    @Test
    fun reparentDepthOverflowFallsBack() {
        // A parent chain root>a>b>c>d already at heading level 5; moving Panel (with a Chip child)
        // under d would emit a level-7 heading, so the reparent stays in-memory.
        val deep = """
            ---
            screen: deep
            sourceLocale: en-US
            ---

            # Screen

            node:
              id: root
              name: Screen

            ## Frame: A

            node:
              type: frame
              id: a
              name: A

            ### Frame: B

            node:
              type: frame
              id: b
              name: B

            #### Frame: C

            node:
              type: frame
              id: c
              name: C

            ##### Frame: D

            node:
              type: frame
              id: d
              name: D

            ## Frame: Panel

            node:
              type: frame
              id: panel
              name: Panel

            ### Shape: Chip

            node:
              type: shape
              id: chip
              name: Chip
            shape:
              kind: rectangle
        """.trimIndent() + "\n"
        val before = createDesignEditorState(
            compileMissionDocuments(listOf(MissionDocumentSource("deep.layout.md", deep))),
        )
        assertNotNull(before.document?.nodeById("d"), "level-5 parent present")

        val next = reduceDesignEditor(before, DesignEditorIntent.ReparentNode("panel", "d"))

        // The document reflects the move, but the depth gate kept the source byte-identical.
        assertTrue(
            next.document?.nodeById("d")?.children?.any { it.id == "panel" } == true,
            "panel reparented under d in-memory",
        )
        assertEquals(before.sources, next.sources, "depth-overflow reparent left the source untouched")
        assertTrue(next.previousSources.isEmpty(), "in-memory fallback records no source undo entry")
    }

    @Test
    fun duplicateInstanceFallsBackInMemory() {
        val before = freshState()
        // tile_1 is a component instance — the section writer can't round-trip its component ref,
        // so the duplicate stays in-memory with every source byte-identical.
        assertTrue(before.document?.nodeById("tile_1")?.kind is DesignNodeKind.Instance, "tile_1 is an instance")

        val next = reduceDesignEditor(before, DesignEditorIntent.DuplicateNodes(setOf("tile_1")))

        // The clone exists in the working document (canvas reflects the duplicate)...
        val cloneId = next.selectedNodeIds.first()
        assertNotEquals("tile_1", cloneId)
        assertTrue(next.document?.nodeById(cloneId)?.kind is DesignNodeKind.Instance, "clone is still an instance")
        // ...but no source was rewritten and no source-undo entry was recorded.
        before.sources.forEach { source ->
            assertEquals(source.content, next.sourceOf(source.fileName), "${source.fileName} untouched by instance duplicate")
        }
        assertTrue(next.previousSources.isEmpty(), "in-memory fallback records no source undo entry")
    }
}
