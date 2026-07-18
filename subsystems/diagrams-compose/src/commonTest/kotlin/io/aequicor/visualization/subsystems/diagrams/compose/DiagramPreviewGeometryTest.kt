package io.aequicor.visualization.subsystems.diagrams.compose

import io.aequicor.visualization.subsystems.diagrams.model.DiagramNode
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodeId
import io.aequicor.visualization.subsystems.diagrams.model.UmlNoteNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DiagramPreviewGeometryTest {

    @Test
    fun zeroCanvasClampsToEmptyBox() {
        // The release-blocker crash: a 0x0 first-frame canvas made width = 0 - 2*2.5 = -5.0.
        val box = diagramPreviewContentBox(canvasWidth = 0.0, canvasHeight = 0.0, inset = 2.5, verticalTrim = 1.0)
        assertEquals(0.0, box.width)
        assertEquals(0.0, box.height)
        assertTrue(box.isDegenerate)
    }

    @Test
    fun tinyCanvasesNeverGoNegative() {
        val sizes = listOf(0.0 to 0.0, 1.0 to 1.0, 4.0 to 4.0, 0.0 to 18.0, 18.0 to 0.0, 6.9 to 6.9)
        for ((w, h) in sizes) {
            val box = diagramPreviewContentBox(canvasWidth = w, canvasHeight = h, inset = 2.5, verticalTrim = 1.0)
            assertTrue(box.width >= 0.0, "width for ${w}x$h: ${box.width}")
            assertTrue(box.height >= 0.0, "height for ${w}x$h: ${box.height}")
        }
    }

    @Test
    fun canvasJustBelowInsetsIsDegenerate() {
        // inset*2 - 1 = 4: one pixel short of the horizontal insets alone.
        val box = diagramPreviewContentBox(canvasWidth = 4.0, canvasHeight = 4.0, inset = 2.5, verticalTrim = 1.0)
        assertEquals(0.0, box.width)
        assertEquals(0.0, box.height)
        assertTrue(box.isDegenerate)
    }

    @Test
    fun degenerateBoxStillBuildsAValidSyntheticNode() {
        // Pins the crash itself: DiagramNode.init requires dimensions >= 0.
        val box = diagramPreviewContentBox(canvasWidth = 0.0, canvasHeight = 0.0, inset = 2.5, verticalTrim = 1.0)
        val node = DiagramNode(
            id = DiagramNodeId("preview"),
            x = box.x,
            y = box.y,
            width = box.width,
            height = box.height,
            payload = UmlNoteNode(text = ""),
        )
        assertEquals(0.0, node.width)
        assertEquals(0.0, node.height)
    }

    @Test
    fun nominalDropdownCanvasKeepsLegacyBox() {
        // 18x18 is the default preview size; the box must match the pre-fix geometry.
        val box = diagramPreviewContentBox(canvasWidth = 18.0, canvasHeight = 18.0, inset = 2.5, verticalTrim = 1.0)
        assertEquals(2.5, box.x)
        assertEquals(3.5, box.y)
        assertEquals(13.0, box.width)
        assertEquals(11.0, box.height)
        assertFalse(box.isDegenerate)
    }
}
