package io.aequicor.visualization.subsystems.figures

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VertexCornerRadiusTest {

    /** A closed square with one rounded corner. */
    private fun square(radius: Double): VectorNetwork = VectorNetwork(
        vertices = listOf(
            VectorVertex(0.0, 0.0, cornerRadius = radius),
            VectorVertex(100.0, 0.0),
            VectorVertex(100.0, 100.0),
            VectorVertex(0.0, 100.0),
        ),
        segments = listOf(
            VectorSegment(0, 1),
            VectorSegment(1, 2),
            VectorSegment(2, 3),
            VectorSegment(3, 0),
        ),
        regions = listOf(VectorRegion("nonzero", listOf(listOf(0, 1, 2, 3)))),
    )

    @Test
    fun zeroRadiusEmitsSharpCorners() {
        val g = networkToGeometry(square(0.0))!!
        // 4 lines + close, no cubics.
        assertTrue(g.commands.none { it is PathCommand.CubicTo }, "no rounding at radius 0")
    }

    @Test
    fun positiveRadiusRoundsWithACubic() {
        val g = networkToGeometry(square(20.0))!!
        assertTrue(g.commands.any { it is PathCommand.CubicTo }, "a rounded corner produces a cubic")
        // The rounded corner's tangent points sit 20 units along each edge from vertex (0,0):
        // start moves to the exit point on the top edge (20, 0).
        val move = g.commands.first() as PathCommand.MoveTo
        assertTrue(kotlin.math.abs(move.x - 20.0) < 1e-6 && kotlin.math.abs(move.y) < 1e-6, "start=(${move.x},${move.y})")
    }

    @Test
    fun radiusClampsToHalfTheShorterEdge() {
        // Radius 999 on a 100-unit square corner clamps the trim to 50 (half the edge).
        val g = networkToGeometry(square(999.0))!!
        val move = g.commands.first() as PathCommand.MoveTo
        assertEquals(50.0, move.x, 1e-6)
    }

    @Test
    fun roundingSkippedForCurvedAdjacentVertex() {
        // Vertex 0 rounded, but its incoming segment (3->0) is curved via vertex 3's outHandle.
        val net = square(20.0).let {
            it.copy(vertices = it.vertices.mapIndexed { i, v -> if (i == 3) v.copy(outHandle = HandleOffset(5.0, 0.0)) else v })
        }
        val g = networkToGeometry(net)!!
        val move = g.commands.first() as PathCommand.MoveTo
        // Not rounded: starts exactly at the vertex (0,0), not trimmed.
        assertTrue(kotlin.math.abs(move.x) < 1e-6 && kotlin.math.abs(move.y) < 1e-6, "start=(${move.x},${move.y})")
    }

    @Test
    fun setVertexCornerRadiusClampsNonNegative() {
        val net = square(0.0).setVertexCornerRadius(1, -5.0)
        assertEquals(0.0, net.vertices[1].cornerRadius)
        assertEquals(12.0, net.setVertexCornerRadius(1, 12.0).vertices[1].cornerRadius)
    }
}
