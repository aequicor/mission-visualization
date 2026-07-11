package io.aequicor.visualization.editor

import io.aequicor.visualization.editor.domain.annotationSidecarFileName
import io.aequicor.visualization.editor.presentation.DesignEditorIntent
import io.aequicor.visualization.editor.presentation.DesignEditorState
import io.aequicor.visualization.editor.presentation.annotationNodeVisualBounds
import io.aequicor.visualization.editor.presentation.createDesignEditorState
import io.aequicor.visualization.editor.presentation.reduceDesignEditor
import io.aequicor.visualization.editor.presentation.screenFileNamesByPageId
import io.aequicor.visualization.engine.ir.layout.DesignLayoutEngine
import io.aequicor.visualization.engine.ir.resolve.DesignResolver
import io.aequicor.visualization.subsystems.annotations.AnnotationAnchor
import io.aequicor.visualization.subsystems.annotations.AnnotationKind
import io.aequicor.visualization.subsystems.annotations.annotationBadgePosition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Deleting nodes freezes the annotations anchored to them (or to their descendants) as
 * free points at their PRE-delete badge positions — instead of leaving dangling node
 * anchors that fall back near the document origin. The freeze is a normal sidecar
 * write-back, so it persists and round-trips like every annotation edit.
 */
class AnnotationDetachOnDeleteTest {

    private val screenFile = "mission-overview.layout.md"
    private val sidecarFile = annotationSidecarFileName(screenFile)

    private fun freshState(): DesignEditorState = createDesignEditorState(legacyMissionDocuments())

    /** Pre-delete badge position of [anchor], resolved exactly like the reducer resolves it. */
    private fun DesignEditorState.expectedFrozenPoint(anchor: AnnotationAnchor.NodeAnchor): io.aequicor.visualization.subsystems.annotations.AnnotationPoint {
        val document = assertNotNull(document)
        val pageId = screenFileNamesByPageId().entries.first { it.value == screenFile }.key
        val page = document.pages.first { it.id == pageId }
        val layout = DesignLayoutEngine().layout(assertNotNull(DesignResolver(document).resolvePage(page).firstOrNull()))
        val bounds = assertNotNull(annotationNodeVisualBounds(layout, anchor.nodeId), "pre-delete bounds resolve")
        return annotationBadgePosition(anchor, bounds)
    }

    @Test
    fun deletingTheAnchorNodeFreezesTheBadgeAtItsPreDeletePosition() {
        val anchor = AnnotationAnchor.NodeAnchor("tile_1", offsetX = 4.0, offsetY = 6.0)
        val annotated = reduceDesignEditor(
            freshState(),
            DesignEditorIntent.AddAnnotation(screenFile, anchor, AnnotationKind.Issue),
        )
        val expected = annotated.expectedFrozenPoint(anchor)

        val deleted = reduceDesignEditor(annotated, DesignEditorIntent.DeleteNodes(setOf("tile_1")))

        assertNull(assertNotNull(deleted.document).nodeById("tile_1"), "the node actually left the tree")
        val annotation = assertNotNull(deleted.annotationLayers[screenFile]).annotations.single()
        val frozen = assertIs<AnnotationAnchor.FreePoint>(annotation.anchor, "the anchor froze into a free point")
        assertEquals(expected.x, frozen.x, absoluteTolerance = 1e-9)
        assertEquals(expected.y, frozen.y, absoluteTolerance = 1e-9)
        val sidecar = assertNotNull(deleted.sources.firstOrNull { it.fileName == sidecarFile }).content
        assertTrue("@tile_1" !in sidecar, "the sidecar no longer references the deleted node anchor")
        assertTrue("@(" in sidecar, "the sidecar carries the frozen free point")
    }

    @Test
    fun deletingAnAncestorFreezesBadgesAnchoredToItsDescendants() {
        val anchor = AnnotationAnchor.NodeAnchor("tile_1")
        val annotated = reduceDesignEditor(
            freshState(),
            DesignEditorIntent.AddAnnotation(screenFile, anchor, AnnotationKind.Note),
        )
        val expected = annotated.expectedFrozenPoint(anchor)

        // overview_tiles is tile_1's parent container; deleting it deletes the subtree.
        val deleted = reduceDesignEditor(annotated, DesignEditorIntent.DeleteNodes(setOf("overview_tiles")))

        assertNull(assertNotNull(deleted.document).nodeById("tile_1"))
        val annotation = assertNotNull(deleted.annotationLayers[screenFile]).annotations.single()
        val frozen = assertIs<AnnotationAnchor.FreePoint>(annotation.anchor)
        assertEquals(expected.x, frozen.x, absoluteTolerance = 1e-9)
        assertEquals(expected.y, frozen.y, absoluteTolerance = 1e-9)
    }

    @Test
    fun annotationsOnSurvivingNodesAndFreePointsAreUntouched() {
        var state = freshState()
        state = reduceDesignEditor(
            state,
            DesignEditorIntent.AddAnnotation(screenFile, AnnotationAnchor.NodeAnchor("overview_hero"), AnnotationKind.Note),
        )
        state = reduceDesignEditor(
            state,
            DesignEditorIntent.AddAnnotation(screenFile, AnnotationAnchor.FreePoint(7.0, 9.0), AnnotationKind.Issue),
        )

        val deleted = reduceDesignEditor(state, DesignEditorIntent.DeleteNodes(setOf("tile_1")))

        val layer = assertNotNull(deleted.annotationLayers[screenFile])
        assertEquals(
            listOf(AnnotationAnchor.NodeAnchor("overview_hero"), AnnotationAnchor.FreePoint(7.0, 9.0)),
            layer.annotations.map { it.anchor },
            "unrelated anchors survive the delete unchanged",
        )
    }
}
