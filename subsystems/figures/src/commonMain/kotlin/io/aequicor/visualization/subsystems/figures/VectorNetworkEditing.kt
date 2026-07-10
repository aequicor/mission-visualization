package io.aequicor.visualization.subsystems.figures

import kotlin.math.abs
import kotlin.math.sqrt

/** Which of a vertex's two bezier handles an edit targets. */
enum class HandleSide { In, Out }

/**
 * Pure, Compose-free operations over a structural [VectorNetwork] — the editing model behind
 * the canvas point/handle tooling. Every op returns a new network (handles ride along with
 * their vertex because they are stored as offsets), so they compose with the immutable editor
 * state and are trivially unit-testable. Replaces the MVP `d`-string `translateSvgPoint` path.
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

/** Sets vertex [index]'s corner-rounding radius (clamped non-negative). */
fun VectorNetwork.setVertexCornerRadius(index: Int, radius: Double): VectorNetwork {
    if (index !in vertices.indices) return this
    return replaceVertex(index, vertices[index].copy(cornerRadius = radius.coerceAtLeast(0.0)))
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

/**
 * Appends a vertex at ([x], [y]) to the growing end of an open path (the pen tool's click-to-place
 * op), connecting it from the current last vertex with a fresh segment. The new vertex is seeded
 * [HandleMirror.AngleAndLength] so a follow-up out-handle drag pulls symmetric tangents (a smooth
 * Figma-style point); with no handles it still renders as a straight corner. On an empty network it
 * seeds the first vertex with no segment.
 */
fun VectorNetwork.appendVertex(x: Double, y: Double): VectorNetwork {
    if (vertices.isEmpty()) return copy(vertices = listOf(VectorVertex(x, y)))
    val from = vertices.lastIndex
    val newIndex = vertices.size
    return copy(
        vertices = vertices + VectorVertex(x, y, mirror = HandleMirror.AngleAndLength),
        segments = segments + VectorSegment(from, newIndex),
    )
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

/**
 * Sets the winding ([rule] = "nonzero" | "evenodd") on every region, materializing the implicit
 * single region over all segments when the network has no explicit regions yet.
 */
fun VectorNetwork.withWindingRule(rule: String): VectorNetwork {
    if (regions.isEmpty()) {
        if (segments.isEmpty()) return this
        return copy(regions = listOf(VectorRegion(rule, listOf(segments.indices.toList()))))
    }
    return copy(regions = regions.map { it.copy(windingRule = rule) })
}

/** Adds a closing segment from the last vertex back to the first when the path is open. */
fun VectorNetwork.closePath(): VectorNetwork {
    if (vertices.size < 3) return this
    val last = vertices.lastIndex
    val alreadyClosed = segments.any { it.from == last && it.to == 0 }
    if (alreadyClosed) return this
    return copy(segments = segments + VectorSegment(last, 0))
}

/** Axis-aligned bounds of every vertex and its handle tips, in the network's own space. */
fun VectorNetwork.extent(): RectD? {
    if (vertices.isEmpty()) return null
    var minX = Double.POSITIVE_INFINITY
    var minY = Double.POSITIVE_INFINITY
    var maxX = Double.NEGATIVE_INFINITY
    var maxY = Double.NEGATIVE_INFINITY
    fun include(x: Double, y: Double) {
        if (x < minX) minX = x
        if (y < minY) minY = y
        if (x > maxX) maxX = x
        if (y > maxY) maxY = y
    }
    vertices.forEach { v ->
        include(v.x, v.y)
        v.inHandle?.let { include(v.x + it.dx, v.y + it.dy) }
        v.outHandle?.let { include(v.x + it.dx, v.y + it.dy) }
    }
    if (minX > maxX || minY > maxY) return null
    return RectD(minX, minY, maxX, maxY)
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
