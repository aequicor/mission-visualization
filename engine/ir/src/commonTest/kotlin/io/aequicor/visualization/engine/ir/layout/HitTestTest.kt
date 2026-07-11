package io.aequicor.visualization.engine.ir.layout

import io.aequicor.visualization.subsystems.figures.PathCommand
import io.aequicor.visualization.subsystems.figures.PathGeometry
import io.aequicor.visualization.subsystems.figures.RectD
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.subsystems.figures.ShapeType
import io.aequicor.visualization.engine.ir.resolve.ResolvedNode
import io.aequicor.visualization.engine.ir.resolve.ResolvedAutoLayout
import io.aequicor.visualization.engine.ir.resolve.ResolvedStrokes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HitTestTest {

    private fun shapeBox(
        shape: DesignNodeKind.Shape,
        width: Double = 100.0,
        height: Double = 100.0,
        rotation: Double = 0.0,
        strokes: ResolvedStrokes? = null,
        geometry: PathGeometry? = null,
    ): LayoutBox = LayoutBox(
        node = ResolvedNode(
            id = "s", sourceId = "s", type = "shape", name = "s",
            rotation = rotation, shape = shape, strokes = strokes, geometry = geometry,
        ),
        x = 0.0, y = 0.0, width = width, height = height,
    )

    @Test
    fun starCenterHitsButNotchMisses() {
        val box = shapeBox(DesignNodeKind.Shape(ShapeType.Star, pointCount = 5, innerRadius = 0.4))
        assertEquals(box, box.hitTest(50.0, 50.0))
        assertNull(box.hitTest(2.0, 50.0)) // notch between two points
        assertNull(box.hitTest(1.0, 1.0)) // bbox corner, outside the star
    }

    @Test
    fun ellipseCornerOfBoxMisses() {
        val box = shapeBox(DesignNodeKind.Shape(ShapeType.Ellipse))
        assertEquals(box, box.hitTest(50.0, 50.0))
        assertNull(box.hitTest(3.0, 3.0)) // outside the inscribed ellipse
    }

    @Test
    fun plainRectangleKeepsBoundingBoxHit() {
        val box = shapeBox(DesignNodeKind.Shape(ShapeType.Rectangle))
        assertEquals(box, box.hitTest(1.0, 1.0)) // AABB corner still hits a plain rect
    }

    @Test
    fun rotatedStarNotchStillMisses() {
        val box = shapeBox(DesignNodeKind.Shape(ShapeType.Star, pointCount = 5, innerRadius = 0.4), rotation = 33.0)
        assertEquals(box, box.hitTest(50.0, 50.0)) // center is rotation-invariant
    }

    @Test
    fun loweredVectorGeometryMapsThroughViewBoxFit() {
        // lower-left triangle in a 10x10 viewBox, fit into a 100x100 box
        val geometry = PathGeometry(
            commands = listOf(
                PathCommand.MoveTo(0.0, 0.0),
                PathCommand.LineTo(10.0, 0.0),
                PathCommand.LineTo(0.0, 10.0),
                PathCommand.Close,
            ),
            sourceViewBox = RectD(0.0, 0.0, 10.0, 10.0),
        )
        val box = shapeBox(DesignNodeKind.Shape(ShapeType.Vector), geometry = geometry)
        assertEquals(box, box.hitTest(30.0, 30.0)) // maps to (3,3): inside x+y<10
        assertNull(box.hitTest(80.0, 80.0)) // maps to (8,8): outside x+y<10
    }

    @Test
    fun openLineHitByStrokeDistance() {
        val box = shapeBox(
            DesignNodeKind.Shape(ShapeType.Line),
            height = 20.0,
            strokes = ResolvedStrokes(weight = 4.0),
        )
        assertEquals(box, box.hitTest(50.0, 10.0)) // on the centerline
        assertNull(box.hitTest(50.0, 19.0)) // far from the stroke
    }

    @Test
    fun overflowingChildCanBeHitOutsideUnclippedParent() {
        val child = shapeBox(DesignNodeKind.Shape(ShapeType.Rectangle), width = 20.0, height = 20.0)
            .copy(x = 120.0, y = 10.0)
        val parent = LayoutBox(
            node = ResolvedNode(
                id = "parent",
                sourceId = "parent",
                type = "frame",
                name = "parent",
                layout = ResolvedAutoLayout(clipsContent = false),
            ),
            x = 0.0,
            y = 0.0,
            width = 100.0,
            height = 100.0,
            children = listOf(child),
        )

        assertEquals(child, parent.hitTest(125.0, 15.0))
    }

    @Test
    fun clippedParentBlocksOverflowingChildHit() {
        val child = shapeBox(DesignNodeKind.Shape(ShapeType.Rectangle), width = 20.0, height = 20.0)
            .copy(x = 120.0, y = 10.0)
        val parent = LayoutBox(
            node = ResolvedNode(
                id = "parent",
                sourceId = "parent",
                type = "frame",
                name = "parent",
                layout = ResolvedAutoLayout(clipsContent = true),
            ),
            x = 0.0,
            y = 0.0,
            width = 100.0,
            height = 100.0,
            children = listOf(child),
        )

        assertNull(parent.hitTest(125.0, 15.0))
    }

    @Test
    fun clippedAncestorBlocksOverflowThroughUnclippedIntermediate() {
        val child = shapeBox(DesignNodeKind.Shape(ShapeType.Rectangle), width = 20.0, height = 20.0)
            .copy(x = 120.0, y = 10.0)
        val intermediate = LayoutBox(
            node = ResolvedNode(
                id = "intermediate",
                sourceId = "intermediate",
                type = "frame",
                name = "intermediate",
            ),
            x = 0.0,
            y = 0.0,
            width = 80.0,
            height = 80.0,
            children = listOf(child),
        )
        val root = LayoutBox(
            node = ResolvedNode(
                id = "root",
                sourceId = "root",
                type = "frame",
                name = "root",
                layout = ResolvedAutoLayout(clipsContent = true),
            ),
            x = 0.0,
            y = 0.0,
            width = 100.0,
            height = 100.0,
            children = listOf(intermediate),
        )

        assertNull(root.hitTest(125.0, 15.0))
    }
}
