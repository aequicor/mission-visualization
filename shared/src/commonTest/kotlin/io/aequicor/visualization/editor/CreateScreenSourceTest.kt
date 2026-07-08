package io.aequicor.visualization.editor

import io.aequicor.visualization.editor.data.DefaultDesignDocumentRepository
import io.aequicor.visualization.editor.domain.LoadDesignDocumentUseCase
import io.aequicor.visualization.editor.domain.compileMissionDocuments
import io.aequicor.visualization.editor.presentation.DesignEditorIntent
import io.aequicor.visualization.editor.presentation.DesignEditorState
import io.aequicor.visualization.editor.presentation.ScreenPreset
import io.aequicor.visualization.editor.presentation.createDesignEditorState
import io.aequicor.visualization.editor.presentation.reduceDesignEditor
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.engine.ir.model.DesignSeverity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * CreateScreen write-back: a new screen has no owning source to patch, so the reducer renders a
 * standalone SLM document ([io.aequicor.visualization.engine.frontend.edit.ScreenSourceWriter])
 * and appends it to the source list. The rendered document must compile back to exactly the
 * minted screen + root-frame ids so the page persists; every pre-existing source stays
 * byte-identical, and a full recompile of the grown source list reproduces the new page.
 */
class CreateScreenSourceTest {

    private fun freshState(): DesignEditorState =
        createDesignEditorState(LoadDesignDocumentUseCase(DefaultDesignDocumentRepository())())

    @Test
    fun createScreenAppendsSourceThatRoundTrips() {
        val before = freshState()
        val sourcesBefore = before.sources.size
        val pagesBefore = before.document?.pages?.size ?: 0

        val next = reduceDesignEditor(before, DesignEditorIntent.CreateScreen(ScreenPreset.Mobile, "New Screen"))

        // A new page is the working document authority, and a new source was appended (not edited).
        assertEquals(pagesBefore + 1, next.document?.pages?.size, "page added to working document")
        assertEquals(sourcesBefore + 1, next.sources.size, "a new source was appended")
        val pageId = next.selectedPageId
        val rootFrameId = next.selectedNodeId
        val newSource = assertNotNull(next.sources.lastOrNull(), "appended source present")
        assertEquals("$pageId.layout.md", newSource.fileName, "source file named after the screen id")

        // Every pre-existing source is byte-identical; the source undo captured the pre-edit list.
        before.sources.forEach { source ->
            val kept = assertNotNull(next.sources.firstOrNull { it.fileName == source.fileName }, "kept ${source.fileName}")
            assertEquals(source.content, kept.content, "${source.fileName} stays byte-identical")
        }
        assertEquals(listOf(before.sources), next.previousSources, "source undo captured the pre-edit sources")

        // The appended source's own compile: page id == screen id, and the root frame minted id
        // is the only node id (no drift, no spurious node).
        val compiled = assertNotNull(next.compiledResults.lastOrNull(), "appended compile result present")
        assertTrue(compiled.diagnostics.none { it.severity == DesignSeverity.Error }, "new source compiles cleanly")
        assertEquals(pageId, compiled.document?.screen?.id, "screen id == page id")
        assertEquals(
            setOf(rootFrameId),
            compiled.document?.pages?.flatMap { page -> page.allNodes().map { it.id } }?.toSet(),
            "root frame minted id is the sole node",
        )

        // Full recompile of the grown source list reproduces the new page + root frame faithfully.
        val remerged = compileMissionDocuments(next.sources)
        assertTrue(remerged.diagnostics.none { it.severity == DesignSeverity.Error }, "merged recompile clean")
        val recompiledPage = assertNotNull(remerged.document?.pages?.firstOrNull { it.id == pageId }, "new page after remerge")
        val recompiledRoot = assertNotNull(recompiledPage.children.firstOrNull(), "root frame after remerge")
        assertEquals(rootFrameId, recompiledRoot.id, "root frame id round-trips")
        assertTrue(recompiledRoot.kind is DesignNodeKind.Frame, "root is a frame")
        assertEquals(375.0, recompiledRoot.size.width, "mobile preset width round-trips")
        assertEquals(812.0, recompiledRoot.size.height, "mobile preset height round-trips")
    }

    @Test
    fun createScreenIdsAreUniqueAgainstExistingPages() {
        val before = freshState()

        val next = reduceDesignEditor(before, DesignEditorIntent.CreateScreen(ScreenPreset.Desktop, "Overview"))

        // The minted screen id must not collide with the three bundled pages even though the title
        // ("Overview") echoes an existing screen — ids come from EditorNodeFactory.uniqueId.
        val pageId = next.selectedPageId
        assertTrue(before.document?.pages?.none { it.id == pageId } == true, "minted a fresh page id: $pageId")
        assertEquals("$pageId.layout.md", next.sources.last().fileName)
        // The desktop preset size round-trips through the appended source.
        val remerged = compileMissionDocuments(next.sources)
        val root = assertNotNull(remerged.document?.pages?.firstOrNull { it.id == pageId }?.children?.firstOrNull())
        assertEquals(1440.0, root.size.width)
        assertEquals(1024.0, root.size.height)
    }
}
