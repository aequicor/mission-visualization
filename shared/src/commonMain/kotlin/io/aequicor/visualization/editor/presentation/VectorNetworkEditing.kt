package io.aequicor.visualization.editor.presentation

import io.aequicor.visualization.engine.ir.geometry.PathCommand
import io.aequicor.visualization.engine.ir.geometry.PathGeometry
import io.aequicor.visualization.engine.ir.geometry.RectD
import io.aequicor.visualization.engine.ir.geometry.arrowGeometry
import io.aequicor.visualization.engine.ir.geometry.ellipseGeometry
import io.aequicor.visualization.engine.ir.geometry.lineGeometry
import io.aequicor.visualization.engine.ir.geometry.regularPolygonGeometry
import io.aequicor.visualization.engine.ir.geometry.roundedRectGeometry
import io.aequicor.visualization.engine.ir.geometry.starGeometry
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.engine.ir.model.DesignSize
import io.aequicor.visualization.engine.ir.model.HandleMirror
import io.aequicor.visualization.engine.ir.model.HandleOffset
import io.aequicor.visualization.engine.ir.model.ShapeType
import io.aequicor.visualization.engine.ir.model.VectorNetwork
import io.aequicor.visualization.engine.ir.model.VectorRegion
import io.aequicor.visualization.engine.ir.model.VectorSegment
import io.aequicor.visualization.engine.ir.model.VectorVertex
import kotlin.math.abs
import kotlin.math.sqrt

/** Which of a vertex's two bezier handles an edit targets. */
enum class HandleSide { In, Out }

/**
 * Pure, Compose-free operations over a structural [VectorNetwork] — the editing model behind
 * the canvas point/handle tooling. Every op returns a new network (handles ride along with
 * their vertex because they are stored as offsets), so they compose with the immutable editor
 * state and are trivially unit-testable. Replaces the MVP `d`-string [translateSvgPoint] path.
 */

/** Moves vertex [index] by ([dx], [dy]); its handle offsets are unchanged (they follow the vertex). */
fun VectorNetwork.moveVertex(index: Int, dx: Double, dy: Double): VectorNetwork {
    if (index !in vertices.indices) return this
    return copy(
        vertices = vertices.mapIndexed { i, vertex ->
            if (i == index) vertex.copy(x = vertex.x + dx, y = vertex.y + dy) else vertex
        },
    )
}

/** Moves one bezier handle of vertex [index] by ([dx], [dy]), applying its mirror constraint. */
fun VectorNetwork.moveHandle(index: Int, side: HandleSide, dx: Double, dy: Double): VectorNetwork {
    if (index !in vertices.indices) return this
    val vertex = vertices[index]
    val base = (if (side == HandleSide.Out) vertex.outHandle else vertex.inHandle) ?: HandleOffset(0.0, 0.0)
    val moved = HandleOffset(base.dx + dx, base.dy + dy)
    val withMoved = if (side == HandleSide.Out) vertex.copy(outHandle = moved) else vertex.copy(inHandle = moved)
    return replaceVertex(index, applyMirror(withMoved, side))
}

/** Sets vertex [index]'s handle-mirror mode, normalizing the opposite handle to match. */
fun VectorNetwork.setMirror(index: Int, mirror: HandleMirror): VectorNetwork {
    if (index !in vertices.indices) return this
    val vertex = vertices[index].copy(mirror = mirror)
    // Normalize using the out handle as primary when present, else the in handle.
    val primary = if (vertex.outHandle != null) HandleSide.Out else HandleSide.In
    return replaceVertex(index, applyMirror(vertex, primary))
}

/** Toggles the sharp-corner flag of vertex [index]; a corner drops the mirror constraint. */
fun VectorNetwork.toggleCorner(index: Int): VectorNetwork {
    if (index !in vertices.indices) return this
    val vertex = vertices[index]
    val corner = !vertex.corner
    return replaceVertex(index, vertex.copy(corner = corner, mirror = if (corner) HandleMirror.None else vertex.mirror))
}

/** Splits segment [segmentIndex] at ([x], [y]), inserting a new vertex and reindexing regions. */
fun VectorNetwork.insertVertexOnSegment(segmentIndex: Int, x: Double, y: Double): VectorNetwork {
    val segment = segments.getOrNull(segmentIndex) ?: return this
    val newVertexIndex = vertices.size
    val firstHalf = VectorSegment(segment.from, newVertexIndex)
    val secondHalf = VectorSegment(newVertexIndex, segment.to)
    val newSegments = segments.toMutableList().apply {
        set(segmentIndex, firstHalf)
        add(segmentIndex + 1, secondHalf)
    }
    // A segment index at or after the split shifts by one; the split index expands to a pair.
    val newRegions = regions.map { region ->
        region.copy(
            loops = region.loops.map { loop ->
                loop.flatMap { s ->
                    when {
                        s < segmentIndex -> listOf(s)
                        s == segmentIndex -> listOf(segmentIndex, segmentIndex + 1)
                        else -> listOf(s + 1)
                    }
                }
            },
        )
    }
    return copy(vertices = vertices + VectorVertex(x, y), segments = newSegments, regions = newRegions)
}

/** Removes vertex [index], stitching its two incident segments and reindexing everything. */
fun VectorNetwork.removeVertex(index: Int): VectorNetwork {
    if (index !in vertices.indices || vertices.size <= 2) return this
    val incoming = segments.indexOfFirst { it.to == index }
    val outgoing = segments.indexOfFirst { it.from == index }

    val stitched = mutableListOf<VectorSegment>()
    var bridgeInserted = false
    segments.forEachIndexed { i, segment ->
        when {
            i == incoming && outgoing >= 0 -> {
                // Replace the incoming segment with a bridge to the outgoing segment's target.
                stitched += VectorSegment(segment.from, segments[outgoing].to)
                bridgeInserted = true
            }
            i == outgoing -> Unit // dropped; folded into the bridge
            segment.from == index || segment.to == index -> Unit // other incident segments dropped
            else -> stitched += segment
        }
    }
    if (!bridgeInserted && incoming < 0) {
        // Open-path tail/head: just drop segments touching the vertex.
    }

    val remap = { v: Int -> if (v > index) v - 1 else v }
    val reindexedVertices = vertices.filterIndexed { i, _ -> i != index }
    val reindexedSegments = stitched.map { VectorSegment(remap(it.from), remap(it.to)) }
    // Regions reference segment indices which shifted; drop loops that referenced removed segments,
    // otherwise rebuild against the simplified segment list by identity is unsafe — clear regions to
    // the implicit single-region form, which the resolver reconstructs.
    return VectorNetwork(reindexedVertices, reindexedSegments, regions = emptyList())
}

/** Adds a closing segment from the last vertex back to the first when the path is open. */
fun VectorNetwork.closePath(): VectorNetwork {
    if (vertices.size < 3) return this
    val last = vertices.lastIndex
    val alreadyClosed = segments.any { it.from == last && it.to == 0 }
    if (alreadyClosed) return this
    return copy(segments = segments + VectorSegment(last, 0))
}

private fun VectorNetwork.replaceVertex(index: Int, vertex: VectorVertex): VectorNetwork =
    copy(vertices = vertices.mapIndexed { i, existing -> if (i == index) vertex else existing })

private fun applyMirror(vertex: VectorVertex, movedSide: HandleSide): VectorVertex {
    val moved = (if (movedSide == HandleSide.Out) vertex.outHandle else vertex.inHandle) ?: return vertex
    return when (vertex.mirror) {
        HandleMirror.None -> vertex
        HandleMirror.AngleAndLength -> {
            val opposite = HandleOffset(-moved.dx, -moved.dy)
            if (movedSide == HandleSide.Out) vertex.copy(inHandle = opposite) else vertex.copy(outHandle = opposite)
        }
        HandleMirror.Angle -> {
            val otherLength = length(if (movedSide == HandleSide.Out) vertex.inHandle else vertex.outHandle)
                ?: length(moved) ?: 0.0
            val movedLength = length(moved) ?: 0.0
            val opposite = if (movedLength == 0.0) {
                HandleOffset(0.0, 0.0)
            } else {
                HandleOffset(-moved.dx / movedLength * otherLength, -moved.dy / movedLength * otherLength)
            }
            if (movedSide == HandleSide.Out) vertex.copy(inHandle = opposite) else vertex.copy(outHandle = opposite)
        }
    }
}

private fun length(handle: HandleOffset?): Double? =
    handle?.let { sqrt(it.dx * it.dx + it.dy * it.dy) }

/**
 * Bakes a parametric shape (its box `0..width × 0..height`) into an editable [VectorNetwork] for
 * "convert to editable vector". Curved primitives (ellipse, rounded rect) become bezier vertices.
 */
fun parametricToNetwork(shape: DesignNodeKind.Shape, size: DesignSize): VectorNetwork {
    val width = size.width ?: 100.0
    val height = size.height ?: 100.0
    val rect = RectD(0.0, 0.0, width, height)
    val geometry = when (shape.shape) {
        ShapeType.Ellipse -> ellipseGeometry(rect)
        ShapeType.Polygon -> regularPolygonGeometry(rect, shape.pointCount ?: 3)
        ShapeType.Star -> starGeometry(rect, shape.pointCount ?: 5, shape.innerRadius ?: 0.4)
        ShapeType.Line -> lineGeometry(rect)
        ShapeType.Arrow -> arrowGeometry(rect, 2.0)
        ShapeType.Rectangle, ShapeType.Vector -> roundedRectGeometry(rect, 0.0, 0.0, 0.0, 0.0)
    }
    return geometry.toNetwork()
}

/** Converts a resolved outline into an editable network (cubic control points become handles). */
fun PathGeometry.toNetwork(): VectorNetwork {
    val vertices = mutableListOf<VectorVertex>()
    val segments = mutableListOf<VectorSegment>()
    var startIndex = -1
    var currentIndex = -1

    fun closesToStart(x: Double, y: Double): Boolean {
        if (startIndex < 0) return false
        val start = vertices[startIndex]
        return abs(x - start.x) < 1e-6 && abs(y - start.y) < 1e-6
    }

    for (command in commands) {
        when (command) {
            is PathCommand.MoveTo -> {
                vertices += VectorVertex(command.x, command.y)
                startIndex = vertices.lastIndex
                currentIndex = vertices.lastIndex
            }
            is PathCommand.LineTo -> {
                if (currentIndex < 0) continue
                if (closesToStart(command.x, command.y)) {
                    segments += VectorSegment(currentIndex, startIndex)
                    currentIndex = startIndex
                } else {
                    vertices += VectorVertex(command.x, command.y)
                    segments += VectorSegment(currentIndex, vertices.lastIndex)
                    currentIndex = vertices.lastIndex
                }
            }
            is PathCommand.QuadTo -> {
                if (currentIndex < 0) continue
                vertices += VectorVertex(command.x, command.y)
                segments += VectorSegment(currentIndex, vertices.lastIndex)
                currentIndex = vertices.lastIndex
            }
            is PathCommand.CubicTo -> {
                if (currentIndex < 0) continue
                val from = vertices[currentIndex]
                vertices[currentIndex] = from.copy(outHandle = HandleOffset(command.c1x - from.x, command.c1y - from.y))
                if (closesToStart(command.x, command.y)) {
                    val start = vertices[startIndex]
                    vertices[startIndex] =
                        start.copy(inHandle = HandleOffset(command.c2x - start.x, command.c2y - start.y))
                    segments += VectorSegment(currentIndex, startIndex)
                    currentIndex = startIndex
                } else {
                    vertices += VectorVertex(
                        command.x,
                        command.y,
                        inHandle = HandleOffset(command.c2x - command.x, command.c2y - command.y),
                    )
                    segments += VectorSegment(currentIndex, vertices.lastIndex)
                    currentIndex = vertices.lastIndex
                }
            }
            PathCommand.Close -> {
                if (currentIndex >= 0 && startIndex >= 0 && currentIndex != startIndex) {
                    segments += VectorSegment(currentIndex, startIndex)
                }
                currentIndex = startIndex
            }
        }
    }
    val region = VectorRegion("nonzero", listOf(segments.indices.toList()))
    return VectorNetwork(vertices, segments, if (segments.isEmpty()) emptyList() else listOf(region))
}
