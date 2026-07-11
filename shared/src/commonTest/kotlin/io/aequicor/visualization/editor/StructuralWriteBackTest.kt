package io.aequicor.visualization.editor

import io.aequicor.visualization.editor.domain.MissionDocumentSource
import io.aequicor.visualization.editor.domain.compileMissionDocuments
import io.aequicor.visualization.editor.presentation.DesignEditorIntent
import io.aequicor.visualization.editor.presentation.DesignEditorState
import io.aequicor.visualization.editor.presentation.NewObjectKind
import io.aequicor.visualization.editor.presentation.ZOrderMove
import io.aequicor.visualization.editor.presentation.createDesignEditorState
import io.aequicor.visualization.editor.presentation.parentNodeOf
import io.aequicor.visualization.editor.presentation.reduceDesignEditor
import io.aequicor.visualization.engine.ir.model.DesignDocument
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.engine.ir.model.DesignPoint
import io.aequicor.visualization.engine.ir.model.DesignSeverity
import io.aequicor.visualization.subsystems.diagrams.model.UmlComponentNode
import io.aequicor.visualization.subsystems.figures.ShapeType
import io.aequicor.visualization.engine.ir.model.DesignSize
import io.aequicor.visualization.engine.ir.model.HorizontalConstraint
import io.aequicor.visualization.engine.ir.model.SizingMode
import io.aequicor.visualization.engine.ir.model.VerticalConstraint
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
 * in-memory-only delete with every source byte-identical — the id-preservation net. Drives the
 * shipped CNL demos (`mission-overview.layout.md` is a Free-layout wireframe: `win_bg` window,
 * `src_panel` / `cv_panel` / `in_panel` panels).
 */
class StructuralWriteBackTest {

    private val owningFile = "mission-overview.layout.md"

    private fun freshState(): DesignEditorState =
        createDesignEditorState(missionDemoDocuments())

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
        val before = reduceDesignEditor(freshState(), DesignEditorIntent.SelectNode("win_bg"))

        val next = reduceDesignEditor(before, DesignEditorIntent.DeleteNodes(setOf("win_bg")))

        // Document mirrors the delete; every sibling id survives the recompile.
        assertNull(next.document?.nodeById("win_bg"), "deleted node gone from document")
        listOf("frame_overview", "src_panel", "cv_panel", "in_panel", "src_menu").forEach { id ->
            assertNotNull(next.document?.nodeById(id), "survivor $id kept its id")
        }

        // The owning source dropped the whole `## Rectangle: … win_bg` section.
        val source = next.sourceOf(owningFile)
        assertTrue("win_bg" !in source, "window section removed from source")
        assertTrue("«Window»" !in source, "window heading removed from source")
        next.assertWroteBack(before)
        assertEquals(listOf(before.sources), next.previousSources, "source undo captured the pre-edit sources")
    }

    @Test
    fun deleteSectionWithDescendantsRemovesSubtreeFromSource() {
        val before = freshState()

        val next = reduceDesignEditor(before, DesignEditorIntent.DeleteNodes(setOf("src_menu")))

        // The container and every descendant rectangle leave both the document and the source.
        listOf("src_menu", "src_menu_l1", "src_menu_l2", "src_menu_l3").forEach { id ->
            assertNull(next.document?.nodeById(id), "$id removed with its section")
            assertTrue(id !in next.sourceOf(owningFile), "$id removed from source")
        }
        // Untouched siblings survive.
        listOf("src_title", "src_save", "win_bg", "cv_panel").forEach { id ->
            assertNotNull(next.document?.nodeById(id), "sibling $id survives")
        }
        next.assertWroteBack(before)
    }

    @Test
    fun deleteMultiPageSelectionFallsBack() {
        val before = freshState()
        // Two heading-anchored nodes on different pages: a single-source patch can't express the
        // two-file transaction, so the delete stays in-memory with every source untouched.
        val ids = setOf("win_bg", "telemetry_header")
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
        // Two heading frames with no explicit id take positional ids "section1"/"section2".
        // Deleting the first would let the survivor recompile back to "section1" — a drift the veto
        // must catch (the source patch is discarded, the in-memory delete stands).
        val drift = """
            ---
            screen: drift
            sourceLocale: en-US
            ---

            # Screen id root name «Screen»

            ## Frame: name «Panel»

            ## Frame: name «Panel»
        """.trimIndent() + "\n"
        val before = createDesignEditorState(
            compileMissionDocuments(listOf(MissionDocumentSource("drift.layout.md", drift))),
        )
        // Sanity: the generated ids are exactly the drift-prone positional pair.
        assertNotNull(before.document?.nodeById("section1"), "first Panel -> section1")
        assertNotNull(before.document?.nodeById("section2"), "second Panel -> section2")

        val next = reduceDesignEditor(before, DesignEditorIntent.DeleteNodes(setOf("section1")))

        // The in-memory delete stands: "section1" gone, "section2" survives.
        assertNull(next.document?.nodeById("section1"), "selected node deleted in-memory")
        assertNotNull(next.document?.nodeById("section2"), "sibling survives in-memory")
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
        val recompiled = assertNotNull(next.compiledDocumentOf(owningFile), "owning source recompiled")
        val recompiledNode = assertNotNull(recompiled.nodeById(mintedId), "minted node present after recompile")
        assertEquals(ShapeType.Rectangle, (recompiledNode.kind as DesignNodeKind.Shape).shape)
        assertEquals(120.0, recompiledNode.size.width)
        assertEquals(80.0, recompiledNode.size.height)

        next.assertWroteBack(before)
        assertEquals(listOf(before.sources), next.previousSources, "source undo captured the pre-edit sources")
    }

    @Test
    fun createDiagramObjectWritesSectionWithDiagramBlock() {
        val before = freshState()
        val rootFrame = "frame_overview"

        val next = reduceDesignEditor(
            before,
            DesignEditorIntent.CreateDiagramObject(
                parentId = rootFrame,
                payload = UmlComponentNode(name = "Component"),
                x = 100.0,
                y = 120.0,
                width = 640.0,
                height = 480.0,
                elementWidth = 160.0,
                elementHeight = 80.0,
            ),
        )

        // A fresh diagram node exists in the working document with the seeded element centered.
        val mintedId = next.selectedNodeId
        assertTrue(mintedId.startsWith("diagram_"), "minted a fresh diagram id: $mintedId")
        val created = assertNotNull(next.document?.nodeById(mintedId), "created node in document")
        val createdKind = created.kind
        assertTrue(createdKind is DesignNodeKind.Diagram, "created a diagram node")
        val seed = assertNotNull(createdKind.graph.nodes.singleOrNull(), "one seeded element")
        assertEquals("node-1", seed.id.value)
        assertTrue(seed.payload is UmlComponentNode, "seed keeps the picked payload")
        assertEquals(240.0, seed.x, "seed centered horizontally")
        assertEquals(200.0, seed.y, "seed centered vertically")

        // The owning source gained a CNL `Diagram:` container section (heading + body
        // sentences, no YAML), and the recompiled IR round-trips the whole graph
        // (kind, element, geometry).
        val source = next.sourceOf(owningFile)
        assertTrue(mintedId in source, "minted id written to source")
        assertTrue("Diagram:" in source, "CNL diagram container heading written to source")
        assertTrue("Node component node-1" in source, "CNL body sentence written to source")
        assertTrue("diagram:" !in source, "no YAML diagram block in a CNL source")
        val recompiled = assertNotNull(next.compiledDocumentOf(owningFile), "owning source recompiled")
        val recompiledKind = assertNotNull(
            recompiled.nodeById(mintedId)?.kind as? DesignNodeKind.Diagram,
            "recompiled node keeps the Diagram kind",
        )
        assertEquals(createdKind.graph, recompiledKind.graph, "diagram graph round-trips at parity")

        next.assertWroteBack(before)
    }

    // --- Duplicate ---------------------------------------------------------------

    @Test
    fun duplicateWritesCloneWithFreshExplicitIds() {
        val before = freshState()
        // win_bg is a pure Shape (faithfully expressible), so its duplicate writes back.
        assertNotNull(before.document?.nodeById("win_bg"), "original present")

        val next = reduceDesignEditor(before, DesignEditorIntent.DuplicateNodes(setOf("win_bg")))

        // A single clone with a fresh id is selected; the original keeps its id (id stability).
        assertEquals(1, next.selectedNodeIds.size)
        val cloneId = next.selectedNodeIds.first()
        assertNotEquals("win_bg", cloneId, "clone got a fresh id")
        assertNotNull(next.document?.nodeById("win_bg"), "original id preserved")
        val clone = assertNotNull(next.document?.nodeById(cloneId), "clone in document")
        assertTrue(clone.kind is DesignNodeKind.Shape)
        // The clone is a sibling of the original under the same parent.
        assertTrue(
            next.document?.nodeById("frame_overview")?.children?.map { it.id }?.containsAll(listOf("win_bg", cloneId)) == true,
            "clone sits beside the original",
        )

        // The owning source carries the clone's explicit id, and it round-trips on recompile.
        assertTrue(cloneId in next.sourceOf(owningFile), "clone id written to source")
        val recompiled = assertNotNull(next.compiledDocumentOf(owningFile), "owning source recompiled")
        assertNotNull(recompiled.nodeById(cloneId), "clone present after recompile")
        assertNotNull(recompiled.nodeById("win_bg"), "original survives recompile")
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

            # Screen id root name «Screen»

            ## Frame: id card name «Card»

            ### Rectangle: id body name «Body» 40 by 40
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
    fun reorderRelocatesSectionOverAddressableRun() {
        val before = freshState()
        // frame_overview's four top-level children are all heading-anchored -> the moved node's
        // new after-sibling anchor is addressable, so a reorder persists as a section relocation.
        assertEquals(
            listOf("win_bg", "src_panel", "cv_panel", "in_panel"),
            before.document?.nodeById("frame_overview")?.children?.map { it.id },
            "authored top-level order",
        )

        val next = reduceDesignEditor(before, DesignEditorIntent.ReorderNode("win_bg", ZOrderMove.ToFront))

        val expectedOrder = listOf("src_panel", "cv_panel", "in_panel", "win_bg")
        // The in-memory document is the authority for z-order.
        assertEquals(
            expectedOrder,
            next.document?.nodeById("frame_overview")?.children?.map { it.id },
            "in-memory reorder moved the window to the front (last child paints on top)",
        )
        // The owning source relocated the window's heading section to the back of the run, and the
        // recompiled IR (z-order = document order in CNL) reproduces the in-memory arrangement with
        // zero id drift.
        val source = next.sourceOf(owningFile)
        assertTrue(
            source.indexOf("id win_bg") > source.indexOf("id in_panel"),
            "window's section relocated to the back of the paint order (after in_panel)",
        )
        val recompiled = assertNotNull(next.compiledDocumentOf(owningFile), "owning source recompiled")
        assertEquals(
            expectedOrder,
            recompiled.nodeById("frame_overview")?.children?.map { it.id },
            "recompiled document order matches the in-memory reorder",
        )
        // Every id survives the recompile (the id-preservation net accepted the write).
        listOf("win_bg", "src_panel", "cv_panel", "in_panel").forEach { id ->
            assertNotNull(recompiled.nodeById(id), "survivor $id kept its id")
        }
        next.assertWroteBack(before)
        assertEquals(listOf(before.sources), next.previousSources, "source undo captured the pre-edit sources")
    }

    @Test
    fun reorderWithProseSiblingFallsBack() {
        // `deck` compiles to [<prose text>, first, third]: the bare prose paragraph child is a
        // paragraph-segment node with no heading anchor. Stepping `third` back lands it right after
        // that prose child, so the section move's after-sibling anchor is the unaddressable prose
        // node and the write aborts to an in-memory-only reorder (re-expresses the former
        // `ir`-splice sibling case now that CNL is the sole authoring format).
        val prose = """
            ---
            screen: prose
            sourceLocale: en-US
            ---

            # Screen id root name «Screen»

            ## Frame: id deck name «Deck» column

            A floating annotation.

            ### Rectangle: id first name «First» 40 by 40

            ### Rectangle: id third name «Third» 40 by 40
        """.trimIndent() + "\n"
        val before = createDesignEditorState(
            compileMissionDocuments(listOf(MissionDocumentSource("prose.layout.md", prose))),
        )
        val deckChildren = before.document?.nodeById("deck")?.children?.map { it.id }
        assertEquals(3, deckChildren?.size, "deck has a prose child plus two rectangles: $deckChildren")
        assertEquals(listOf("first", "third"), deckChildren?.drop(1), "the two heading-anchored rects follow the prose child")

        val next = reduceDesignEditor(before, DesignEditorIntent.ReorderNode("third", ZOrderMove.Backward))

        // The canvas reflects the move (document is the authority + fallback)...
        assertEquals(
            "third",
            next.document?.nodeById("deck")?.children?.get(1)?.id,
            "in-memory reorder applied (third stepped back past first, landing after the prose child)",
        )
        // ...but the unaddressable prose sibling forced an in-memory fallback: every source byte-identical.
        before.sources.forEach { source ->
            assertEquals(source.content, next.sourceOf(source.fileName), "${source.fileName} untouched by prose-sibling reorder")
        }
        assertTrue(next.previousSources.isEmpty(), "in-memory fallback records no source undo entry")
    }

    // --- Reparent ----------------------------------------------------------------

    @Test
    fun reparentSamePageWritesBack() {
        val before = freshState()
        // win_bg (a Shape, faithfully expressible) starts as a direct child of frame_overview;
        // src_panel is a sibling frame on the same page. Both are heading-anchored -> the move
        // re-levels the window section under Source and writes back.
        assertTrue(
            before.document?.nodeById("frame_overview")?.children?.any { it.id == "win_bg" } == true,
            "window starts under the root frame",
        )

        val next = reduceDesignEditor(before, DesignEditorIntent.ReparentNode("win_bg", "src_panel"))

        // The in-memory document is the authority: window moved under Source, gone from the root frame.
        assertTrue(
            next.document?.nodeById("src_panel")?.children?.any { it.id == "win_bg" } == true,
            "window reparented under Source in the document",
        )
        assertTrue(
            next.document?.nodeById("frame_overview")?.children?.none { it.id == "win_bg" } == true,
            "window left the root frame's children",
        )

        // The owning source moved and re-leveled the window section; the recompile reproduces the new
        // parent/child topology with every id preserved (the id + parent-of veto both passed).
        assertTrue("win_bg" in next.sourceOf(owningFile), "window id still in source")
        val recompiled = assertNotNull(next.compiledDocumentOf(owningFile), "owning source recompiled")
        assertTrue(
            recompiled.nodeById("src_panel")?.children?.any { it.id == "win_bg" } == true,
            "recompiled tree parents window under Source",
        )
        assertEquals(
            next.document?.nodeById("src_panel")?.children?.map { it.id },
            recompiled.nodeById("src_panel")?.children?.map { it.id },
            "recompiled child order matches the in-memory reorder",
        )
        assertNull(
            recompiled.nodeById("frame_overview")?.children?.firstOrNull { it.id == "win_bg" },
            "recompiled root frame no longer parents window",
        )
        next.assertWroteBack(before)
        assertEquals(listOf(before.sources), next.previousSources, "source undo captured the pre-edit sources")
    }
    // NOTE: a dropped origin/main test (`positionedReparentWritesStructureAndVisualGeometryAtomically`)
    // reparented `overview_hero` — a node that only exists in the legacy Mission Overview fixture and
    // carries an `ir` splice — and asserted the move + geometry persist atomically to the SLM source.
    // Under CNL-only write-back, reparenting a non-heading-anchored / `ir`-splice node lands in-memory
    // only (anti-corruption veto), so atomic CNL source persistence of that case is a tracked gap.

    @Test
    fun reparentCrossPageFallsBack() {
        val before = freshState()
        // win_bg lives on the Overview page; frame_telemetry roots the Telemetry page. A
        // cross-page reparent is a two-source transaction a single-source patch can't express.
        assertNotNull(before.document?.nodeById("frame_telemetry"), "telemetry root present")

        val next = reduceDesignEditor(before, DesignEditorIntent.ReparentNode("win_bg", "frame_telemetry"))

        // The canvas reflects the cross-page move (document is authority + fallback)...
        assertTrue(
            next.document?.nodeById("frame_telemetry")?.children?.any { it.id == "win_bg" } == true,
            "window moved under the telemetry root in-memory",
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

            # Screen id root name «Screen»

            ## Frame: id a name «A»

            ### Frame: id b name «B»

            #### Frame: id c name «C»

            ##### Frame: id d name «D»

            ## Frame: id panel name «Panel»

            ### Rectangle: id chip name «Chip» 40 by 40
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
        // t_tile_1 is a component instance — the section writer can't round-trip its component ref,
        // so the duplicate stays in-memory with every source byte-identical.
        assertTrue(before.document?.nodeById("t_tile_1")?.kind is DesignNodeKind.Instance, "t_tile_1 is an instance")

        val next = reduceDesignEditor(before, DesignEditorIntent.DuplicateNodes(setOf("t_tile_1")))

        // The clone exists in the working document (canvas reflects the duplicate)...
        val cloneId = next.selectedNodeIds.first()
        assertNotEquals("t_tile_1", cloneId)
        assertTrue(next.document?.nodeById(cloneId)?.kind is DesignNodeKind.Instance, "clone is still an instance")
        // ...but no source was rewritten and no source-undo entry was recorded.
        before.sources.forEach { source ->
            assertEquals(source.content, next.sourceOf(source.fileName), "${source.fileName} untouched by instance duplicate")
        }
        assertTrue(next.previousSources.isEmpty(), "in-memory fallback records no source undo entry")
    }
}
