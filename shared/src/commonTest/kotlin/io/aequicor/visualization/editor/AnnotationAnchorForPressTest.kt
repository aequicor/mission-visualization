package io.aequicor.visualization.editor

import io.aequicor.visualization.editor.presentation.annotationAnchorForPress
import io.aequicor.visualization.subsystems.annotations.AnnotationAnchor
import io.aequicor.visualization.subsystems.annotations.AnnotationRect
import io.aequicor.visualization.subsystems.annotations.annotationBadgePosition
import kotlin.test.Test
import kotlin.test.assertEquals

/** Canvas-press → annotation-anchor routing (node hit vs free point vs unknown bounds). */
class AnnotationAnchorForPressTest {

    @Test
    fun pressOnNodeAnchorsWithOffsetFromTopCenter() {
        val bounds = AnnotationRect.fromSize(100.0, 50.0, 40.0, 20.0) // top-center = (120, 50)
        val anchor = annotationAnchorForPress(docX = 130.0, docY = 58.0, hitNodeId = "node-a", nodeBounds = bounds)

        assertEquals(AnnotationAnchor.NodeAnchor("node-a", offsetX = 10.0, offsetY = 8.0), anchor)
        // Placement round-trip: the badge lands exactly where the user clicked.
        assertEquals(130.0, annotationBadgePosition(anchor, bounds).x)
        assertEquals(58.0, annotationBadgePosition(anchor, bounds).y)
    }

    @Test
    fun pressOnNodeWithoutBoundsPinsAtTopCenter() {
        val anchor = annotationAnchorForPress(docX = 5.0, docY = 6.0, hitNodeId = "node-b", nodeBounds = null)
        assertEquals(AnnotationAnchor.NodeAnchor("node-b"), anchor)
    }

    @Test
    fun pressOnEmptyCanvasBecomesFreePoint() {
        val anchor = annotationAnchorForPress(docX = 12.5, docY = -3.0, hitNodeId = "", nodeBounds = null)
        assertEquals(AnnotationAnchor.FreePoint(12.5, -3.0), anchor)
    }

    @Test
    fun boundsAreIgnoredWhenNothingWasHit() {
        val bounds = AnnotationRect.fromSize(0.0, 0.0, 10.0, 10.0)
        val anchor = annotationAnchorForPress(docX = 1.0, docY = 2.0, hitNodeId = "", nodeBounds = bounds)
        assertEquals(AnnotationAnchor.FreePoint(1.0, 2.0), anchor)
    }
}
