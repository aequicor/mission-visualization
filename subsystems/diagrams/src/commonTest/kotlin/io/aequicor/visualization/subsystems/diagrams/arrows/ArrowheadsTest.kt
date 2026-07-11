package io.aequicor.visualization.subsystems.diagrams.arrows

import io.aequicor.visualization.subsystems.diagrams.model.DiagramArrowhead
import io.aequicor.visualization.subsystems.diagrams.model.DiagramArrowheadKind
import io.aequicor.visualization.subsystems.diagrams.model.DiagramRelation
import io.aequicor.visualization.subsystems.diagrams.model.DiagramStrokePattern
import io.aequicor.visualization.subsystems.diagrams.model.ErCardinality
import io.aequicor.visualization.subsystems.diagrams.path.DiagramPath
import io.aequicor.visualization.subsystems.diagrams.path.DiagramPathSegment
import io.aequicor.visualization.subsystems.diagrams.path.DiagramPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArrowheadsTest {

    private val nonNoneKinds = DiagramArrowheadKind.entries.filter { it != DiagramArrowheadKind.NONE }

    private fun DiagramPath.allPoints(): List<DiagramPoint> = segments.flatMap { segment ->
        when (segment) {
            is DiagramPathSegment.MoveTo -> listOf(segment.point)
            is DiagramPathSegment.LineTo -> listOf(segment.point)
            is DiagramPathSegment.QuadTo -> listOf(segment.control, segment.end)
            is DiagramPathSegment.CubicTo -> listOf(segment.control1, segment.control2, segment.end)
            is DiagramPathSegment.ArcTo -> listOf(segment.end)
            DiagramPathSegment.Close -> emptyList()
        }
    }

    @Test
    fun noneKindProducesEmptyPathAndZeroShorten() {
        val geometry = arrowheadPath(DiagramArrowhead.None, DiagramPoint(10.0, 10.0), DiagramPoint(1.0, 0.0))
        assertTrue(geometry.path.isEmpty)
        assertFalse(geometry.filled)
        assertEquals(0.0, geometry.lineShorten)
    }

    @Test
    fun everyNonNoneKindProducesGeometry() {
        nonNoneKinds.forEach { kind ->
            val geometry = arrowheadPath(
                DiagramArrowhead(kind),
                tip = DiagramPoint(0.0, 0.0),
                direction = DiagramPoint(1.0, 0.0),
            )
            assertFalse(geometry.path.isEmpty, "kind $kind produced an empty path")
        }
    }

    @Test
    fun markerExtendsBackwardAlongHorizontalDirection() {
        val tip = DiagramPoint(100.0, 50.0)
        nonNoneKinds.forEach { kind ->
            val geometry = arrowheadPath(DiagramArrowhead(kind), tip, DiagramPoint(1.0, 0.0))
            val points = geometry.path.allPoints()
            assertTrue(points.isNotEmpty(), "kind $kind has no points")
            points.forEach { point ->
                assertTrue(point.x <= tip.x + 1e-6, "kind $kind point $point pokes past the tip")
            }
            assertTrue(points.any { it.x < tip.x - 1e-6 }, "kind $kind does not extend backward")
        }
    }

    @Test
    fun markerFollowsVerticalDirection() {
        val tip = DiagramPoint(50.0, 100.0)
        nonNoneKinds.forEach { kind ->
            val geometry = arrowheadPath(DiagramArrowhead(kind), tip, DiagramPoint(0.0, 1.0))
            geometry.path.allPoints().forEach { point ->
                assertTrue(point.y <= tip.y + 1e-6, "kind $kind point $point pokes past the tip")
            }
        }
    }

    @Test
    fun nonUnitDirectionIsNormalized() {
        val tip = DiagramPoint(0.0, 0.0)
        val unit = arrowheadPath(DiagramArrowhead.Open, tip, DiagramPoint(1.0, 0.0))
        val scaled = arrowheadPath(DiagramArrowhead.Open, tip, DiagramPoint(25.0, 0.0))
        assertEquals(unit.path, scaled.path)
    }

    @Test
    fun filledFlagMatchesKind() {
        val filledKinds = setOf(
            DiagramArrowheadKind.BLOCK_FILLED,
            DiagramArrowheadKind.TRIANGLE_FILLED,
            DiagramArrowheadKind.DIAMOND_FILLED,
            DiagramArrowheadKind.OVAL_FILLED,
        )
        nonNoneKinds.forEach { kind ->
            val geometry = arrowheadPath(
                DiagramArrowhead(kind),
                tip = DiagramPoint(0.0, 0.0),
                direction = DiagramPoint(1.0, 0.0),
            )
            assertEquals(kind in filledKinds, geometry.filled, "kind $kind filled flag")
        }
    }

    @Test
    fun openMarkerDoesNotShortenTheLine() {
        val geometry = arrowheadPath(
            DiagramArrowhead(DiagramArrowheadKind.OPEN, size = 8.0),
            tip = DiagramPoint(0.0, 0.0),
            direction = DiagramPoint(1.0, 0.0),
        )
        assertEquals(0.0, geometry.lineShorten)
    }

    @Test
    fun blockMarkerShortensLineBySize() {
        val geometry = arrowheadPath(
            DiagramArrowhead(DiagramArrowheadKind.BLOCK, size = 8.0),
            tip = DiagramPoint(0.0, 0.0),
            direction = DiagramPoint(1.0, 0.0),
        )
        assertEquals(8.0, geometry.lineShorten)
    }

    @Test
    fun insetPullsMarkerAndShortenBack() {
        val tip = DiagramPoint(100.0, 0.0)
        val direction = DiagramPoint(1.0, 0.0)
        val plain = arrowheadPath(DiagramArrowhead(DiagramArrowheadKind.BLOCK, size = 8.0), tip, direction)
        val inset = arrowheadPath(
            DiagramArrowhead(DiagramArrowheadKind.BLOCK, size = 8.0, inset = 3.0),
            tip,
            direction,
        )
        assertEquals(plain.lineShorten + 3.0, inset.lineShorten)
        val plainMaxX = plain.path.allPoints().maxOf { it.x }
        val insetMaxX = inset.path.allPoints().maxOf { it.x }
        assertEquals(plainMaxX - 3.0, insetMaxX, 1e-9)
    }

    @Test
    fun relationNotationDependencyIsOpenAndDashed() {
        val notation = arrowheadsForRelation(DiagramRelation.Dependency)
        assertEquals(DiagramArrowheadKind.NONE, notation.source.kind)
        assertEquals(DiagramArrowheadKind.OPEN, notation.target.kind)
        assertEquals(DiagramStrokePattern.DASHED, notation.pattern)
    }

    @Test
    fun relationNotationGeneralizationIsHollowTriangleSolid() {
        val notation = arrowheadsForRelation(DiagramRelation.Generalization)
        assertEquals(DiagramArrowheadKind.TRIANGLE, notation.target.kind)
        assertEquals(DiagramStrokePattern.SOLID, notation.pattern)
    }

    @Test
    fun relationNotationRealizationIsHollowTriangleDashed() {
        val notation = arrowheadsForRelation(DiagramRelation.Realization)
        assertEquals(DiagramArrowheadKind.TRIANGLE, notation.target.kind)
        assertEquals(DiagramStrokePattern.DASHED, notation.pattern)
    }

    @Test
    fun relationNotationDiamondsSitAtTheSourceEnd() {
        val aggregation = arrowheadsForRelation(DiagramRelation.Aggregation)
        assertEquals(DiagramArrowheadKind.DIAMOND, aggregation.source.kind)
        assertEquals(DiagramArrowheadKind.NONE, aggregation.target.kind)
        val composition = arrowheadsForRelation(DiagramRelation.Composition)
        assertEquals(DiagramArrowheadKind.DIAMOND_FILLED, composition.source.kind)
        assertEquals(DiagramStrokePattern.SOLID, composition.pattern)
    }

    @Test
    fun relationNotationAssociation() {
        val plain = arrowheadsForRelation(DiagramRelation.Association(directed = false))
        assertEquals(DiagramArrowheadKind.NONE, plain.target.kind)
        val directed = arrowheadsForRelation(DiagramRelation.Association(directed = true))
        assertEquals(DiagramArrowheadKind.OPEN, directed.target.kind)
        assertEquals(DiagramStrokePattern.SOLID, directed.pattern)
    }

    @Test
    fun relationNotationEntityRelationUsesCrowFeet() {
        val notation = arrowheadsForRelation(
            DiagramRelation.EntityRelation(
                sourceCardinality = ErCardinality.ONE,
                targetCardinality = ErCardinality.ZERO_OR_MANY,
            ),
        )
        assertEquals(DiagramArrowheadKind.ER_ONE, notation.source.kind)
        assertEquals(DiagramArrowheadKind.ER_ZERO_OR_MANY, notation.target.kind)
    }
}
