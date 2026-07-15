package io.aequicor.visualization.subsystems.diagrams.path

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DiagramPathTest {

    @Test
    fun toSvgPathDataCoversAllSegmentKinds() {
        val path = diagramPath {
            moveTo(0.0, 0.0)
            lineTo(10.0, 0.0)
            quadTo(15.0, 5.0, 10.0, 10.0)
            cubicTo(8.0, 12.0, 2.0, 12.0, 0.0, 10.0)
            arcTo(radiusX = 5.0, radiusY = 5.0, largeArc = true, sweep = false, endX = 0.0, endY = 0.0)
            close()
        }

        assertEquals(
            "M 0 0 L 10 0 Q 15 5 10 10 C 8 12 2 12 0 10 A 5 5 0 1 0 0 0 Z",
            path.toSvgPathData(),
        )
    }

    @Test
    fun svgNumbersAreTrimmedAndNeverScientific(): Unit {
        assertEquals("1.5", 1.5.toSvgNumber())
        assertEquals("-0.0001", (-0.0001).toSvgNumber())
        assertEquals("0", 0.00001.toSvgNumber())
        assertEquals("12345", 12345.0.toSvgNumber())
        assertEquals("2.3333", (7.0 / 3.0).toSvgNumber())
    }

    @Test
    fun emptyPathSerializesToEmptyString() {
        assertTrue(DiagramPath.Empty.isEmpty)
        assertEquals("", DiagramPath.Empty.toSvgPathData())
    }

    @Test
    fun rectContainsAndIntersects() {
        val rect = DiagramRect(0.0, 0.0, 100.0, 50.0)
        assertTrue(rect.contains(DiagramPoint(50.0, 25.0)))
        assertTrue(!rect.contains(DiagramPoint(150.0, 25.0)))
        assertTrue(rect.intersects(DiagramRect(90.0, 40.0, 50.0, 50.0)))
        assertTrue(!rect.intersects(DiagramRect(200.0, 0.0, 10.0, 10.0)))
        assertEquals(DiagramPoint(50.0, 25.0), rect.center)
    }

    @Test
    fun closedPathContainsItsAreaAndBoundary() {
        val diamond = diagramPath {
            moveTo(50.0, 0.0)
            lineTo(100.0, 50.0)
            lineTo(50.0, 100.0)
            lineTo(0.0, 50.0)
            close()
        }

        assertTrue(diamond.contains(DiagramPoint(50.0, 50.0)))
        assertTrue(diamond.contains(DiagramPoint(25.0, 25.0)), "the contour belongs to the hit area")
        assertFalse(diamond.contains(DiagramPoint(5.0, 5.0)), "a bounding-box corner is outside the diamond")
    }

    @Test
    fun arcPathUsesCurvedAreaInsteadOfItsBounds() {
        val ellipse = diagramPath {
            moveTo(0.0, 50.0)
            arcTo(radiusX = 50.0, radiusY = 50.0, sweep = true, endX = 100.0, endY = 50.0)
            arcTo(radiusX = 50.0, radiusY = 50.0, sweep = true, endX = 0.0, endY = 50.0)
            close()
        }

        assertTrue(ellipse.contains(DiagramPoint(50.0, 50.0)))
        assertFalse(ellipse.contains(DiagramPoint(5.0, 5.0)))
        assertTrue(ellipse.contains(DiagramPoint(50.0, -2.0), outlineTolerance = 2.1))
    }

    @Test
    fun pathRectangleIntersectionFollowsTheFilledContour() {
        val diamond = diagramPath {
            moveTo(50.0, 0.0)
            lineTo(100.0, 50.0)
            lineTo(50.0, 100.0)
            lineTo(0.0, 50.0)
            close()
        }

        assertFalse(diamond.intersects(DiagramRect(0.0, 0.0, 5.0, 5.0)))
        assertTrue(diamond.intersects(DiagramRect(45.0, 45.0, 10.0, 10.0)))
        assertTrue(diamond.intersects(DiagramRect(48.0, -5.0, 4.0, 10.0)))
    }

    @Test
    fun rayIntersectionFindsTheVisibleBoundary() {
        val diamond = diagramPath {
            moveTo(50.0, 0.0)
            lineTo(100.0, 50.0)
            lineTo(50.0, 100.0)
            lineTo(0.0, 50.0)
            close()
        }

        assertEquals(
            DiagramPoint(75.0, 25.0),
            diamond.rayIntersection(DiagramPoint(50.0, 50.0), DiagramPoint(100.0, 0.0)),
        )
        assertEquals(null, diamond.rayIntersection(DiagramPoint(50.0, 50.0), DiagramPoint(50.0, 50.0)))
    }
}
