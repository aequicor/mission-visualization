package io.aequicor.visualization.subsystems.diagrams.geometry

import io.aequicor.visualization.subsystems.diagrams.model.DiagramNode
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodeId
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodePayload
import io.aequicor.visualization.subsystems.diagrams.model.DiagramShapeKind
import io.aequicor.visualization.subsystems.diagrams.model.UmlUseCaseNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** [labelBox] / [boundsForLabel] over the perimeter families. */
class NodeLabelBoxTest {

    private fun node(payload: DiagramNodePayload, w: Double, h: Double): DiagramNode =
        DiagramNode(id = DiagramNodeId("n"), x = 0.0, y = 0.0, width = w, height = h, payload = payload)

    @Test
    fun ellipseLabelBoxIsTheInscribedRectangleNotTheBoundingBox() {
        // The reported repro: a 930x260 use-case ellipse. bounds.inset(8) would claim 914x244,
        // overstating the usable width by 39% and the height by 33%.
        val node = node(UmlUseCaseNode("Вести контакты собственников и совета дома"), 930.0, 260.0)

        // Unpadded, the inscribed rectangle is 930/√2 x 260/√2.
        val bare = node.labelBox(0.0)
        assertEquals(657.6, bare.width, 0.1)
        assertEquals(183.8, bare.height, 0.1)

        // Padding then insets that rectangle, so 8px of padding costs 16px per axis.
        val box = node.labelBox(8.0)
        assertEquals(641.6, box.width, 0.1)
        assertEquals(167.8, box.height, 0.1)
        assertTrue(box.width < 914.0, "must not claim the bounding box's 914px")

        assertEquals(465.0, box.x + box.width / 2.0, 0.1, "inscribed box stays centered")
        assertEquals(130.0, box.y + box.height / 2.0, 0.1)
    }

    @Test
    fun rectangleLabelBoxIsJustTheInset() {
        val box = node(DiagramNodePayload.BasicShape(DiagramShapeKind.RECTANGLE), 200.0, 100.0).labelBox(8.0)

        assertEquals(184.0, box.width, 1e-9)
        assertEquals(84.0, box.height, 1e-9)
    }

    @Test
    fun rhombusLabelBoxIsTheInscribedHalfRectangle() {
        val box = node(DiagramNodePayload.BasicShape(DiagramShapeKind.RHOMBUS), 200.0, 100.0).labelBox(4.0)

        assertEquals(92.0, box.width, 1e-9)
        assertEquals(42.0, box.height, 1e-9)
    }

    @Test
    fun boundsForLabelInvertsLabelBoxForEveryFamily() {
        val cases = listOf(
            DiagramPerimeterKind.RECTANGLE to node(DiagramNodePayload.BasicShape(DiagramShapeKind.RECTANGLE), 200.0, 100.0),
            DiagramPerimeterKind.ELLIPSE to node(UmlUseCaseNode("x"), 930.0, 260.0),
            DiagramPerimeterKind.RHOMBUS to node(DiagramNodePayload.BasicShape(DiagramShapeKind.RHOMBUS), 160.0, 90.0),
        )
        listOf(0.0, 4.0, 8.0).forEach { padding ->
            cases.forEach { (kind, node) ->
                val box = node.labelBox(padding)
                val size = boundsForLabel(kind, box.width, box.height, padding)
                assertEquals(node.width, size.width, 1e-9, "$kind width @ padding $padding")
                assertEquals(node.height, size.height, 1e-9, "$kind height @ padding $padding")
            }
        }
    }

    @Test
    fun labelBoxNeverInvertsOnShapesSmallerThanTheirPadding() {
        val box = node(UmlUseCaseNode("x"), 6.0, 6.0).labelBox(8.0)

        assertTrue(box.width >= 0.0 && box.height >= 0.0, "degenerate box must clamp, not invert: $box")
    }

    @Test
    fun ellipseLabelBoxCornersLieInsideTheOutline() {
        val node = node(UmlUseCaseNode("x"), 300.0, 160.0)
        val box = node.labelBox(0.0)
        val cx = node.width / 2.0
        val cy = node.height / 2.0

        listOf(
            box.x to box.y,
            box.x + box.width to box.y,
            box.x to box.y + box.height,
            box.x + box.width to box.y + box.height,
        ).forEach { (px, py) ->
            val nx = (px - cx) / cx
            val ny = (py - cy) / cy
            assertTrue(nx * nx + ny * ny <= 1.0 + 1e-9, "corner ($px, $py) escapes the ellipse")
        }
    }
}
