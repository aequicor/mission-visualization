package io.aequicor.visualization.subsystems.annotations

import kotlin.test.Test
import kotlin.test.assertEquals

class AnnotationPlacementTest {

    @Test
    fun nodeAnchorPlacesBadgeAtTopCenterPlusOffset() {
        val bounds = AnnotationRect.fromSize(100.0, 50.0, 200.0, 80.0)
        val position = annotationBadgePosition(
            AnnotationAnchor.NodeAnchor("node-1", offsetX = 10.0, offsetY = -12.0),
            bounds,
        )
        assertEquals(AnnotationPoint(210.0, 38.0), position)
    }

    @Test
    fun nodeAnchorWithZeroOffsetSitsExactlyAtTopCenter() {
        val bounds = AnnotationRect(0.0, 0.0, 40.0, 40.0)
        val position = annotationBadgePosition(AnnotationAnchor.NodeAnchor("node-1"), bounds)
        assertEquals(AnnotationPoint(20.0, 0.0), position)
    }

    @Test
    fun freePointPlacesBadgeAtThePointItself() {
        val position = annotationBadgePosition(AnnotationAnchor.FreePoint(33.0, 44.0), nodeBounds = null)
        assertEquals(AnnotationPoint(33.0, 44.0), position)
    }

    @Test
    fun danglingNodeAnchorFallsBackToDeterministicOffsetPosition() {
        val anchor = AnnotationAnchor.NodeAnchor("deleted-node", offsetX = 7.0, offsetY = 9.0)
        val first = annotationBadgePosition(anchor, nodeBounds = null)
        val second = annotationBadgePosition(anchor, nodeBounds = null)
        assertEquals(AnnotationPoint(7.0, 9.0), first)
        assertEquals(first, second)
    }
}
