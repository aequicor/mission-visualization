package io.aequicor.visualization.editor

import io.aequicor.visualization.editor.presentation.DesignEditorIntent
import io.aequicor.visualization.editor.presentation.annotationMoveCommitTarget
import io.aequicor.visualization.editor.presentation.annotationNodeVisualBounds
import io.aequicor.visualization.editor.presentation.createDesignEditorState
import io.aequicor.visualization.editor.presentation.reduceDesignEditor
import io.aequicor.visualization.engine.ir.layout.LayoutBox
import io.aequicor.visualization.engine.ir.resolve.ResolvedNode
import io.aequicor.visualization.subsystems.annotations.AnnotationAnchor
import io.aequicor.visualization.subsystems.annotations.AnnotationKind
import io.aequicor.visualization.subsystems.annotations.AnnotationPoint
import io.aequicor.visualization.subsystems.annotations.AnnotationRect
import io.aequicor.visualization.subsystems.annotations.slm.AnnotationLayoutComments
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Pure geometry of the annotation canvas layer: badge drag-to-move commit targets and
 * rotation-aware node bounds (annotation anchors must live in the same visual space the
 * renderer and the selection overlay use — raw layout boxes are pre-rotation and detach
 * badges as soon as an ancestor rotates).
 */
class AnnotationCanvasGeometryTest {

    // --- Drag commit targets ------------------------------------------------

    @Test
    fun nodeAnchorDragCommitsTheAccumulatedOffset() {
        val anchor = AnnotationAnchor.NodeAnchor("tile_1", offsetX = 5.0, offsetY = 6.0)
        assertEquals(AnnotationPoint(7.0, 3.0), annotationMoveCommitTarget(anchor, dx = 2.0, dy = -3.0))
    }

    @Test
    fun freePointDragCommitsTheDisplacedAbsolutePoint() {
        val anchor = AnnotationAnchor.FreePoint(10.0, 20.0)
        assertEquals(AnnotationPoint(11.5, 22.0), annotationMoveCommitTarget(anchor, dx = 1.5, dy = 2.0))
    }

    @Test
    fun dragEndCommitsExactlyOneMoveWithOneSidecarPatchAndOneHistoryEntry() {
        val screenFile = "mission-overview.layout.md"
        var state = createDesignEditorState(annotationFixtureDocuments())
        state = reduceDesignEditor(
            state,
            DesignEditorIntent.AddAnnotation(
                screenFile,
                AnnotationAnchor.NodeAnchor("tile_1", 5.0, 6.0),
                AnnotationKind.Note,
            ),
        )
        val historyBefore = state.previousSources.size

        // The transient drag never dispatches intents; release commits one MoveAnnotation
        // with the helper's target — replay exactly what the canvas drag-end does.
        val target = annotationMoveCommitTarget(
            assertNotNull(state.annotationLayers[screenFile]).annotations.single().anchor,
            dx = 2.0,
            dy = -3.0,
        )
        val moved = reduceDesignEditor(
            state,
            DesignEditorIntent.MoveAnnotation(screenFile, "ann-1", target.x, target.y),
        )

        assertEquals(historyBefore + 1, moved.previousSources.size, "one commit = one history entry")
        assertEquals(
            AnnotationAnchor.NodeAnchor("tile_1", 7.0, 3.0),
            assertNotNull(moved.annotationLayers[screenFile]).annotations.single().anchor,
        )
        val layoutSource = assertNotNull(moved.sources.firstOrNull { it.fileName == screenFile })
        val persisted = AnnotationLayoutComments.parse(screenFile, layoutSource.content).layer.annotations.single()
        assertEquals(AnnotationAnchor.NodeAnchor("tile_1", 7.0, 3.0), persisted.anchor)
    }

    // --- Rotation-aware node bounds ------------------------------------------

    private fun node(id: String, rotation: Double = 0.0): ResolvedNode =
        ResolvedNode(id = id, sourceId = id, type = "frame", name = id, rotation = rotation)

    private fun layoutTree(parentRotation: Double): LayoutBox =
        LayoutBox(
            node = node("root"),
            x = 0.0, y = 0.0, width = 200.0, height = 200.0,
            children = listOf(
                LayoutBox(
                    node = node("parent", rotation = parentRotation),
                    x = 50.0, y = 50.0, width = 100.0, height = 50.0,
                    children = listOf(
                        LayoutBox(node = node("child"), x = 60.0, y = 60.0, width = 20.0, height = 10.0),
                    ),
                ),
            ),
        )

    @Test
    fun unrotatedNodeBoundsEqualTheRawLayoutBox() {
        val bounds = annotationNodeVisualBounds(layoutTree(parentRotation = 0.0), "child")
        assertEquals(AnnotationRect(60.0, 60.0, 80.0, 70.0), bounds)
    }

    @Test
    fun rotatedAncestorCarriesTheChildBoundsToItsVisualPosition() {
        // Parent center (100, 75), rotated 90°: the child's center (70, 65) maps to
        // (110, 45); the 20x10 box turns 90° so its AABB is 10x20 around that center.
        val bounds = annotationNodeVisualBounds(layoutTree(parentRotation = 90.0), "child")!!
        assertEquals(105.0, bounds.left, absoluteTolerance = 1e-9)
        assertEquals(35.0, bounds.top, absoluteTolerance = 1e-9)
        assertEquals(115.0, bounds.right, absoluteTolerance = 1e-9)
        assertEquals(55.0, bounds.bottom, absoluteTolerance = 1e-9)
    }

    @Test
    fun ownRotationExpandsTheBoundsToTheVisualAabb() {
        val layout = LayoutBox(
            node = node("root"),
            x = 0.0, y = 0.0, width = 100.0, height = 100.0,
            children = listOf(
                LayoutBox(node = node("spun", rotation = 90.0), x = 10.0, y = 10.0, width = 40.0, height = 20.0),
            ),
        )
        // 40x20 box rotated 90° about its center (30, 20) -> AABB 20x40 around the same center.
        val bounds = annotationNodeVisualBounds(layout, "spun")!!
        assertEquals(20.0, bounds.left, absoluteTolerance = 1e-9)
        assertEquals(0.0, bounds.top, absoluteTolerance = 1e-9)
        assertEquals(40.0, bounds.right, absoluteTolerance = 1e-9)
        assertEquals(40.0, bounds.bottom, absoluteTolerance = 1e-9)
    }

    @Test
    fun unresolvedNodeAndBlankIdYieldNull() {
        assertNull(annotationNodeVisualBounds(layoutTree(0.0), "missing"))
        assertNull(annotationNodeVisualBounds(layoutTree(0.0), ""))
        assertNull(annotationNodeVisualBounds(null, "child"))
    }
}
