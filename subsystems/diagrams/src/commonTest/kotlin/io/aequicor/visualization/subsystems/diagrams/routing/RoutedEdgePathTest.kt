package io.aequicor.visualization.subsystems.diagrams.routing

import io.aequicor.visualization.subsystems.diagrams.model.DiagramCornerStyle
import io.aequicor.visualization.subsystems.diagrams.model.DiagramEdgeId
import io.aequicor.visualization.subsystems.diagrams.model.DiagramRoutingStyle
import io.aequicor.visualization.subsystems.diagrams.model.DiagramStyle
import io.aequicor.visualization.subsystems.diagrams.model.LineJumpStyle
import io.aequicor.visualization.subsystems.diagrams.path.DiagramPathSegment
import io.aequicor.visualization.subsystems.diagrams.path.DiagramPoint
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RoutedEdgePathTest {

    private fun polyline(vararg points: Pair<Double, Double>): RoutedEdge = RoutedEdge(
        edgeId = DiagramEdgeId("edge"),
        routing = DiagramRoutingStyle.ORTHOGONAL,
        points = points.map { DiagramPoint(it.first, it.second) },
    )

    private fun lastPoint(segment: DiagramPathSegment): DiagramPoint? = when (segment) {
        is DiagramPathSegment.MoveTo -> segment.point
        is DiagramPathSegment.LineTo -> segment.point
        is DiagramPathSegment.QuadTo -> segment.end
        is DiagramPathSegment.CubicTo -> segment.end
        is DiagramPathSegment.ArcTo -> segment.end
        DiagramPathSegment.Close -> null
    }

    @Test
    fun sharpCornersProduceLinesOnly() {
        val path = routedEdgeToPath(
            polyline(0.0 to 0.0, 100.0 to 0.0, 100.0 to 100.0),
            style = DiagramStyle(cornerStyle = DiagramCornerStyle.SHARP),
        )
        assertTrue(path.segments.first() is DiagramPathSegment.MoveTo)
        assertTrue(path.segments.drop(1).all { it is DiagramPathSegment.LineTo })
        assertEquals(DiagramPoint(100.0, 100.0), lastPoint(path.segments.last()))
    }

    @Test
    fun roundedCornersKeepEndpoints() {
        val path = routedEdgeToPath(
            polyline(0.0 to 0.0, 100.0 to 0.0, 100.0 to 100.0),
            style = DiagramStyle(cornerStyle = DiagramCornerStyle.ROUNDED),
        )
        val move = path.segments.first() as DiagramPathSegment.MoveTo
        assertEquals(DiagramPoint(0.0, 0.0), move.point)
        assertEquals(DiagramPoint(100.0, 100.0), lastPoint(path.segments.last()))
        assertTrue(path.segments.any { it is DiagramPathSegment.QuadTo })
        // The corner is cut at radius 8 on both sides of the bend.
        val beforeCorner = path.segments[1] as DiagramPathSegment.LineTo
        assertEquals(DiagramPoint(92.0, 0.0), beforeCorner.point)
        val corner = path.segments[2] as DiagramPathSegment.QuadTo
        assertEquals(DiagramPoint(100.0, 0.0), corner.control)
        assertEquals(DiagramPoint(100.0, 8.0), corner.end)
    }

    @Test
    fun curvedCornersUseLargerRadius() {
        val path = routedEdgeToPath(
            polyline(0.0 to 0.0, 100.0 to 0.0, 100.0 to 100.0),
            style = DiagramStyle(cornerStyle = DiagramCornerStyle.CURVED),
        )
        val beforeCorner = path.segments[1] as DiagramPathSegment.LineTo
        assertEquals(DiagramPoint(84.0, 0.0), beforeCorner.point)
        assertEquals(DiagramPoint(100.0, 100.0), lastPoint(path.segments.last()))
    }

    @Test
    fun cornerRadiusClampsToShortSegments() {
        val path = routedEdgeToPath(
            polyline(0.0 to 0.0, 6.0 to 0.0, 6.0 to 100.0),
            style = DiagramStyle(cornerStyle = DiagramCornerStyle.ROUNDED),
        )
        // Radius clamps to half the 6-long segment; endpoints stay exact.
        val move = path.segments.first() as DiagramPathSegment.MoveTo
        assertEquals(DiagramPoint(0.0, 0.0), move.point)
        assertEquals(DiagramPoint(6.0, 100.0), lastPoint(path.segments.last()))
        val corner = path.segments.filterIsInstance<DiagramPathSegment.QuadTo>().single()
        assertTrue(abs(corner.end.y - 3.0) < 1e-9)
    }

    @Test
    fun gapJumpBreaksTheLine() {
        val crossing = polyline(0.0 to 50.0, 100.0 to 50.0)
        val other = RoutedEdge(
            edgeId = DiagramEdgeId("other"),
            routing = DiagramRoutingStyle.ORTHOGONAL,
            points = listOf(DiagramPoint(50.0, 0.0), DiagramPoint(50.0, 100.0)),
        )
        val path = routedEdgeToPath(
            crossing,
            lineJumps = LineJumpStyle.GAP,
            otherEdges = listOf(other),
        )
        val moves = path.segments.filterIsInstance<DiagramPathSegment.MoveTo>()
        assertEquals(2, moves.size)
        assertEquals(DiagramPoint(56.0, 50.0), moves[1].point)
        assertEquals(DiagramPoint(100.0, 50.0), lastPoint(path.segments.last()))
    }

    @Test
    fun arcJumpHopsOverTheCrossing() {
        val crossing = polyline(0.0 to 50.0, 100.0 to 50.0)
        val other = RoutedEdge(
            edgeId = DiagramEdgeId("other"),
            routing = DiagramRoutingStyle.ORTHOGONAL,
            points = listOf(DiagramPoint(50.0, 0.0), DiagramPoint(50.0, 100.0)),
        )
        val path = routedEdgeToPath(
            crossing,
            lineJumps = LineJumpStyle.ARC,
            otherEdges = listOf(other),
        )
        val arc = path.segments.filterIsInstance<DiagramPathSegment.ArcTo>().single()
        assertEquals(DiagramPoint(56.0, 50.0), arc.end)
        assertEquals(DiagramPoint(100.0, 50.0), lastPoint(path.segments.last()))
    }

    @Test
    fun sharpJumpAddsAPeak() {
        val crossing = polyline(0.0 to 50.0, 100.0 to 50.0)
        val other = RoutedEdge(
            edgeId = DiagramEdgeId("other"),
            routing = DiagramRoutingStyle.ORTHOGONAL,
            points = listOf(DiagramPoint(50.0, 0.0), DiagramPoint(50.0, 100.0)),
        )
        val path = routedEdgeToPath(
            crossing,
            lineJumps = LineJumpStyle.SHARP,
            otherEdges = listOf(other),
        )
        // The apex sits above the line (deterministic side).
        assertTrue(
            path.segments.filterIsInstance<DiagramPathSegment.LineTo>()
                .any { abs(it.point.y - 44.0) < 1e-9 },
        )
        assertEquals(DiagramPoint(100.0, 50.0), lastPoint(path.segments.last()))
    }

    @Test
    fun noJumpWithoutCrossings() {
        val path = routedEdgeToPath(
            polyline(0.0 to 50.0, 100.0 to 50.0),
            lineJumps = LineJumpStyle.GAP,
            otherEdges = emptyList(),
        )
        assertEquals(1, path.segments.filterIsInstance<DiagramPathSegment.MoveTo>().size)
    }

    @Test
    fun ownSegmentsNeverJumpThemselves() {
        val selfCrossing = polyline(0.0 to 50.0, 100.0 to 50.0)
        val path = routedEdgeToPath(
            selfCrossing,
            lineJumps = LineJumpStyle.GAP,
            otherEdges = listOf(selfCrossing),
        )
        assertEquals(1, path.segments.filterIsInstance<DiagramPathSegment.MoveTo>().size)
    }

    @Test
    fun curveRouteBuildsSmoothSpline() {
        val routed = RoutedEdge(
            edgeId = DiagramEdgeId("edge"),
            routing = DiagramRoutingStyle.CURVED,
            points = listOf(
                DiagramPoint(0.0, 0.0),
                DiagramPoint(100.0, 100.0),
                DiagramPoint(200.0, 0.0),
            ),
        )
        val path = routedEdgeToPath(routed)
        val move = path.segments.first() as DiagramPathSegment.MoveTo
        assertEquals(DiagramPoint(0.0, 0.0), move.point)
        assertTrue(path.segments.count { it is DiagramPathSegment.CubicTo } == 2)
        assertEquals(DiagramPoint(200.0, 0.0), lastPoint(path.segments.last()))
    }
}
