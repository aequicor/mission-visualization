package io.aequicor.visualization.subsystems.diagrams.path

import kotlin.test.Test
import kotlin.test.assertEquals
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
}
