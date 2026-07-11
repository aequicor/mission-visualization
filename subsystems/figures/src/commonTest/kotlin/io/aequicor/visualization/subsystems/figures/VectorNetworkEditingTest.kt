package io.aequicor.visualization.subsystems.figures

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VectorNetworkEditingTest {

    private fun triangle(): VectorNetwork = VectorNetwork(
        vertices = listOf(VectorVertex(0.0, 0.0), VectorVertex(10.0, 0.0), VectorVertex(0.0, 10.0)),
        segments = listOf(VectorSegment(0, 1), VectorSegment(1, 2), VectorSegment(2, 0)),
    )

    @Test
    fun moveVertexShiftsOnlyThatVertex() {
        val moved = triangle().moveVertex(1, 5.0, 3.0)
        assertEquals(15.0, moved.vertices[1].x)
        assertEquals(3.0, moved.vertices[1].y)
        assertEquals(0.0, moved.vertices[0].x)
    }

    @Test
    fun appendVertexOnEmptySeedsFirstVertexWithoutSegment() {
        val net = VectorNetwork().appendVertex(3.0, 4.0)
        assertEquals(1, net.vertices.size)
        assertEquals(VectorVertex(3.0, 4.0), net.vertices[0])
        assertTrue(net.segments.isEmpty())
    }

    @Test
    fun appendVertexExtendsOpenPathFromLastVertex() {
        val start = VectorNetwork(vertices = listOf(VectorVertex(0.0, 0.0)), segments = emptyList())
        val one = start.appendVertex(10.0, 0.0)
        assertEquals(2, one.vertices.size)
        assertEquals(listOf(VectorSegment(0, 1)), one.segments)
        assertEquals(HandleMirror.AngleAndLength, one.vertices[1].mirror)
        val two = one.appendVertex(10.0, 10.0)
        assertEquals(3, two.vertices.size)
        assertEquals(listOf(VectorSegment(0, 1), VectorSegment(1, 2)), two.segments)
    }

    @Test
    fun moveHandleMirrorsOppositeForAngleAndLength() {
        val net = VectorNetwork(
            vertices = listOf(VectorVertex(10.0, 10.0, mirror = HandleMirror.AngleAndLength)),
            segments = emptyList(),
        )
        val moved = net.moveHandle(0, HandleSide.Out, 4.0, 2.0)
        assertEquals(HandleOffset(4.0, 2.0), moved.vertices[0].outHandle)
        assertEquals(HandleOffset(-4.0, -2.0), moved.vertices[0].inHandle)
    }

    @Test
    fun moveHandleAngleKeepsOppositeLength() {
        val net = VectorNetwork(
            vertices = listOf(
                VectorVertex(0.0, 0.0, inHandle = HandleOffset(-6.0, 0.0), mirror = HandleMirror.Angle),
            ),
            segments = emptyList(),
        )
        val moved = net.moveHandle(0, HandleSide.Out, 0.0, 3.0) // out becomes (0,3), length 3
        val inHandle = assertNotNull(moved.vertices[0].inHandle)
        // opposite direction of (0,3) is (0,-1); scaled to the in-handle's length (6) => (0,-6)
        assertTrue(kotlin.math.abs(inHandle.dx) < 1e-9, "expected 0 got ${inHandle.dx}")
        assertTrue(kotlin.math.abs(inHandle.dy + 6.0) < 1e-9, "expected -6 got ${inHandle.dy}")
    }

    @Test
    fun toggleCornerFlipsFlagAndDropsMirror() {
        val net = VectorNetwork(
            vertices = listOf(VectorVertex(0.0, 0.0, mirror = HandleMirror.AngleAndLength)),
            segments = emptyList(),
        )
        val toggled = net.toggleCorner(0)
        assertTrue(toggled.vertices[0].corner)
        assertEquals(HandleMirror.None, toggled.vertices[0].mirror)
    }

    @Test
    fun insertVertexSplitsSegmentAndReindexesRegion() {
        val net = triangle().copy(
            regions = listOf(VectorRegion("nonzero", listOf(listOf(0, 1, 2)))),
        )
        val split = net.insertVertexOnSegment(0, 5.0, 0.0)
        assertEquals(4, split.vertices.size)
        assertEquals(4, split.segments.size)
        // the new vertex is the split point
        assertEquals(5.0, split.vertices[3].x)
        // region loop expands segment 0 into [0, 1], shifting the rest
        assertEquals(listOf(listOf(0, 1, 2, 3)), split.regions.first().loops)
    }

    @Test
    fun removeVertexStitchesNeighbors() {
        val removed = triangle().removeVertex(1)
        assertEquals(2, removed.vertices.size)
        // a bridge from old vertex 0 to old vertex 2 (now index 1) survives
        assertTrue(removed.segments.any { it.from == 0 && it.to == 1 })
    }

    @Test
    fun closePathAddsClosingSegment() {
        val open = VectorNetwork(
            vertices = listOf(VectorVertex(0.0, 0.0), VectorVertex(10.0, 0.0), VectorVertex(0.0, 10.0)),
            segments = listOf(VectorSegment(0, 1), VectorSegment(1, 2)),
        )
        val closed = open.closePath()
        assertTrue(closed.segments.any { it.from == 2 && it.to == 0 })
        // idempotent
        assertEquals(closed.segments.size, closed.closePath().segments.size)
    }

    @Test
    fun moveVertexOutOfRangeIsNoOp() {
        assertEquals(triangle(), triangle().moveVertex(9, 1.0, 1.0))
        assertNull(triangle().vertices.getOrNull(9))
    }
}
