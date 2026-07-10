package io.aequicor.visualization.subsystems.annotations.compose

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import io.aequicor.visualization.subsystems.annotations.Annotation
import io.aequicor.visualization.subsystems.annotations.AnnotationAnchor
import io.aequicor.visualization.subsystems.annotations.AnnotationKind
import io.aequicor.visualization.subsystems.annotations.AnnotationRect
import kotlin.test.Test
import kotlin.test.assertEquals

class AnnotationOverlayGeometryTest {

    private val badgeSize = Size(20f, 26f)

    private fun annotation(anchor: AnnotationAnchor): Annotation =
        Annotation(id = "a1", kind = AnnotationKind.Note, anchor = anchor)

    // -- AnnotationViewTransform --

    @Test
    fun toScreenAppliesZoomThenPan() {
        val transform = AnnotationViewTransform(zoom = 2f, panX = 10f, panY = -5f)
        assertEquals(Offset(210f, 75f), transform.toScreen(100.0, 40.0))
    }

    @Test
    fun identityTransformIsPassThrough() {
        assertEquals(Offset(7f, 9f), AnnotationViewTransform().toScreen(7.0, 9.0))
    }

    @Test
    fun toDocDeltaDividesByZoom() {
        val transform = AnnotationViewTransform(zoom = 2f, panX = 100f, panY = 100f)
        val delta = transform.toDocDelta(Offset(8f, -4f))
        assertEquals(4.0, delta.x)
        assertEquals(-2.0, delta.y)
    }

    // -- annotationScreenAnchor --

    @Test
    fun nodeAnchorProjectsNodeTopCenterPlusOffset() {
        val bounds = AnnotationRect.fromSize(100.0, 200.0, 40.0, 30.0) // top-center (120, 200)
        val annotation = annotation(AnnotationAnchor.NodeAnchor("node-1", offsetX = 5.0, offsetY = -10.0))
        val transform = AnnotationViewTransform(zoom = 2f, panX = 1f, panY = 2f)

        val anchor = annotationScreenAnchor(annotation, { id -> bounds.takeIf { id == "node-1" } }, transform)

        assertEquals(Offset(125f * 2f + 1f, 190f * 2f + 2f), anchor)
    }

    @Test
    fun danglingNodeAnchorFallsBackToOffsetAsAbsolutePoint() {
        val annotation = annotation(AnnotationAnchor.NodeAnchor("gone", offsetX = 30.0, offsetY = 40.0))

        val anchor = annotationScreenAnchor(annotation, { null }, AnnotationViewTransform())

        assertEquals(Offset(30f, 40f), anchor)
    }

    @Test
    fun freePointProjectsVerbatimAndSkipsBoundsLookup() {
        val annotation = annotation(AnnotationAnchor.FreePoint(50.0, 60.0))

        val anchor = annotationScreenAnchor(
            annotation,
            { error("free points must not resolve node bounds") },
            AnnotationViewTransform(zoom = 3f),
        )

        assertEquals(Offset(150f, 180f), anchor)
    }

    // -- badge / card placement --

    @Test
    fun badgeTopLeftPutsTailTipOnAnchorPoint() {
        val topLeft = annotationBadgeTopLeft(Offset(100f, 80f), badgeSize)
        assertEquals(Offset(90f, 54f), topLeft)
    }

    @Test
    fun cardTopLeftSitsRightOfBadgeTopAligned() {
        val topLeft = annotationCardTopLeft(Offset(100f, 80f), badgeSize, gap = 6f)
        assertEquals(Offset(116f, 54f), topLeft)
    }
}
