package io.aequicor.visualization.subsystems.diagrams.layout

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OverlapRemovalTest {

    private fun boxes(vararg specs: DoubleArray): OverlapBoxes = OverlapBoxes(
        centersX = DoubleArray(specs.size) { specs[it][0] },
        centersY = DoubleArray(specs.size) { specs[it][1] },
        halfWidths = DoubleArray(specs.size) { specs[it][2] },
        halfHeights = DoubleArray(specs.size) { specs[it][3] },
    )

    private fun assertSeparated(boxes: OverlapBoxes, gap: Double) {
        for (first in 0 until boxes.size) {
            for (second in first + 1 until boxes.size) {
                val overlapX = boxes.halfWidths[first] + boxes.halfWidths[second] + gap -
                    abs(boxes.centersX[first] - boxes.centersX[second])
                val overlapY = boxes.halfHeights[first] + boxes.halfHeights[second] + gap -
                    abs(boxes.centersY[first] - boxes.centersY[second])
                assertTrue(
                    overlapX <= 1e-6 || overlapY <= 1e-6,
                    "boxes $first and $second still overlap (x=$overlapX, y=$overlapY)",
                )
            }
        }
    }

    @Test
    fun separatedBoxesStayPut() {
        val input = boxes(
            doubleArrayOf(0.0, 0.0, 10.0, 10.0),
            doubleArrayOf(100.0, 0.0, 10.0, 10.0),
        )
        removeBoxOverlaps(input, gap = 8.0)
        assertEquals(0.0, input.centersX[0])
        assertEquals(100.0, input.centersX[1])
        assertEquals(0.0, input.centersY[0])
        assertEquals(0.0, input.centersY[1])
    }

    @Test
    fun overlappingPairSplitsAlongCheaperAxis() {
        // Deep y-overlap, shallow x-overlap → resolves along x, splitting evenly.
        val input = boxes(
            doubleArrayOf(0.0, 0.0, 20.0, 20.0),
            doubleArrayOf(35.0, 2.0, 20.0, 20.0),
        )
        removeBoxOverlaps(input, gap = 4.0)
        assertSeparated(input, gap = 4.0 - 1e-6)
        assertTrue(input.centersX[0] < input.centersX[1], "pair keeps its x order")
        assertEquals(2.0, input.centersY[1] - input.centersY[0], "y untouched for an x-resolved pair")
    }

    @Test
    fun coincidentStackSeparatesDeterministically() {
        val stack = Array(4) { doubleArrayOf(50.0, 50.0, 15.0, 10.0) }
        val first = boxes(*stack)
        val second = boxes(*stack)
        removeBoxOverlaps(first, gap = 6.0)
        removeBoxOverlaps(second, gap = 6.0)
        assertSeparated(first, gap = 6.0 - 1e-6)
        assertEquals(first.centersX.toList(), second.centersX.toList())
        assertEquals(first.centersY.toList(), second.centersY.toList())
    }

    @Test
    fun denseClusterAlwaysEndsSeparated() {
        // A tight 4x4 grid of large boxes: relaxation alone cannot finish; the final
        // vertical sweep must still guarantee zero overlaps.
        val specs = buildList {
            for (row in 0 until 4) {
                for (column in 0 until 4) {
                    add(doubleArrayOf(column * 10.0, row * 8.0, 25.0, 18.0))
                }
            }
        }
        val input = boxes(*specs.toTypedArray())
        removeBoxOverlaps(input, gap = 5.0)
        assertSeparated(input, gap = 5.0 - 1e-6)
    }
}
