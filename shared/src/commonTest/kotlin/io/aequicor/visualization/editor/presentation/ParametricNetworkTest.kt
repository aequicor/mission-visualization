package io.aequicor.visualization.editor.presentation

import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.engine.ir.model.DesignSize
import io.aequicor.visualization.subsystems.figures.ShapeType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Baking of IR parametric shapes into editable vector networks ([parametricToNetwork]). */
class ParametricNetworkTest {

    @Test
    fun parametricPolygonBakesToNetwork() {
        val net = parametricToNetwork(
            DesignNodeKind.Shape(ShapeType.Polygon, pointCount = 3),
            DesignSize(100.0, 100.0),
        )
        assertEquals(3, net.vertices.size)
        assertEquals(3, net.segments.size) // triangle closes
    }

    @Test
    fun parametricEllipseBakesToBezierNetworkWithoutDuplicateStart() {
        val net = parametricToNetwork(DesignNodeKind.Shape(ShapeType.Ellipse), DesignSize(100.0, 50.0))
        // 4 cubic arcs closing back to start => 4 vertices, 4 segments, all with handles
        assertEquals(4, net.vertices.size)
        assertEquals(4, net.segments.size)
        assertTrue(net.vertices.all { it.inHandle != null || it.outHandle != null })
    }
}
