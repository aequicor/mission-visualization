package io.aequicor.visualization.engine.ir.resolve

import io.aequicor.visualization.subsystems.figures.PathCommand
import io.aequicor.visualization.subsystems.figures.PathFillRule
import io.aequicor.visualization.subsystems.figures.RectD
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.subsystems.figures.DesignViewBox
import io.aequicor.visualization.subsystems.figures.HandleOffset
import io.aequicor.visualization.subsystems.figures.ShapeType
import io.aequicor.visualization.subsystems.figures.VectorNetwork
import io.aequicor.visualization.subsystems.figures.VectorPath
import io.aequicor.visualization.subsystems.figures.VectorSegment
import io.aequicor.visualization.subsystems.figures.VectorVertex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShapeLoweringTest {

    @Test
    fun cubicSegmentLowersToCubicWithOffsetControls() {
        val shape = DesignNodeKind.Shape(
            shape = ShapeType.Vector,
            viewBox = DesignViewBox(0.0, 0.0, 100.0, 100.0),
            network = VectorNetwork(
                vertices = listOf(
                    VectorVertex(0.0, 0.0, outHandle = HandleOffset(10.0, 0.0)),
                    VectorVertex(100.0, 0.0, inHandle = HandleOffset(-10.0, 0.0)),
                ),
                segments = listOf(VectorSegment(0, 1)),
            ),
        )
        val geometry = lowerShapeGeometry(shape)!!
        assertEquals(
            listOf(
                PathCommand.MoveTo(0.0, 0.0),
                PathCommand.CubicTo(10.0, 0.0, 90.0, 0.0, 100.0, 0.0),
            ),
            geometry.commands,
        )
        assertEquals(RectD(0.0, 0.0, 100.0, 100.0), geometry.sourceViewBox)
    }

    @Test
    fun straightSegmentCollapsesToLine() {
        val shape = DesignNodeKind.Shape(
            shape = ShapeType.Vector,
            network = VectorNetwork(
                vertices = listOf(VectorVertex(0.0, 0.0), VectorVertex(10.0, 0.0)),
                segments = listOf(VectorSegment(0, 1)),
            ),
        )
        val geometry = lowerShapeGeometry(shape)!!
        assertEquals(
            listOf(PathCommand.MoveTo(0.0, 0.0), PathCommand.LineTo(10.0, 0.0)),
            geometry.commands,
        )
    }

    @Test
    fun closedLoopEmitsClose() {
        val shape = DesignNodeKind.Shape(
            shape = ShapeType.Vector,
            network = VectorNetwork(
                vertices = listOf(VectorVertex(0.0, 0.0), VectorVertex(10.0, 0.0), VectorVertex(0.0, 10.0)),
                segments = listOf(VectorSegment(0, 1), VectorSegment(1, 2), VectorSegment(2, 0)),
            ),
        )
        val geometry = lowerShapeGeometry(shape)!!
        assertTrue(geometry.isClosed)
    }

    @Test
    fun regionWindingRuleDrivesFillRule() {
        val shape = DesignNodeKind.Shape(
            shape = ShapeType.Vector,
            network = VectorNetwork(
                vertices = listOf(VectorVertex(0.0, 0.0), VectorVertex(10.0, 0.0)),
                segments = listOf(VectorSegment(0, 1)),
                regions = listOf(
                    io.aequicor.visualization.subsystems.figures.VectorRegion("evenodd", listOf(listOf(0))),
                ),
            ),
        )
        assertEquals(PathFillRule.EvenOdd, lowerShapeGeometry(shape)!!.fillRule)
    }

    @Test
    fun networkWinsOverInlinePaths() {
        val shape = DesignNodeKind.Shape(
            shape = ShapeType.Vector,
            paths = listOf(VectorPath(d = "M0 0 L50 50")),
            network = VectorNetwork(
                vertices = listOf(VectorVertex(1.0, 1.0), VectorVertex(2.0, 2.0)),
                segments = listOf(VectorSegment(0, 1)),
            ),
        )
        val geometry = lowerShapeGeometry(shape)!!
        assertEquals(PathCommand.MoveTo(1.0, 1.0), geometry.commands.first())
    }

    @Test
    fun inlinePathsLowerWithViewBoxFallingBackToBounds() {
        val withViewBox = DesignNodeKind.Shape(
            shape = ShapeType.Vector,
            paths = listOf(VectorPath(d = "M0 0 L10 0 L10 10 Z")),
            viewBox = DesignViewBox(0.0, 0.0, 24.0, 24.0),
        )
        assertEquals(RectD(0.0, 0.0, 24.0, 24.0), lowerShapeGeometry(withViewBox)!!.sourceViewBox)

        val noViewBox = DesignNodeKind.Shape(
            shape = ShapeType.Vector,
            paths = listOf(VectorPath(d = "M0 0 L10 0 L10 10 Z")),
        )
        assertEquals(RectD(0.0, 0.0, 10.0, 10.0), lowerShapeGeometry(noViewBox)!!.sourceViewBox)
    }

    @Test
    fun parametricPrimitivesLowerToNull() {
        assertNull(lowerShapeGeometry(DesignNodeKind.Shape(shape = ShapeType.Rectangle)))
        assertNull(lowerShapeGeometry(DesignNodeKind.Shape(shape = ShapeType.Star, pointCount = 5)))
    }
}
