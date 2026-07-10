package io.aequicor.visualization.subsystems.figures

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Lowers a structural [VectorNetwork] into device-independent [PathGeometry] in view-box
 * coordinate space. Loops to draw: the network's authored regions when present, otherwise one
 * implicit loop over every segment in declaration order. Vertices with a positive
 * [VectorVertex.cornerRadius] joining two straight segments are rounded. Returns null when nothing
 * is drawable.
 *
 * Pure geometry — the IR resolver calls this to populate a shape's lowered geometry.
 */
fun networkToGeometry(network: VectorNetwork, viewBox: DesignViewBox? = null): PathGeometry? {
    val vertices = network.vertices
    if (vertices.isEmpty()) return null
    val fillRule = fillRuleOf(network.regions.firstOrNull()?.windingRule)

    val loops: List<List<Int>> = if (network.regions.isNotEmpty()) {
        network.regions.flatMap { it.loops }
    } else {
        listOf(network.segments.indices.toList())
    }

    val rounded = vertices.any { it.cornerRadius > 0.0 }
    val commands = ArrayList<PathCommand>()
    for (loop in loops) emitLoop(network, loop, rounded, commands)
    if (commands.isEmpty()) return null
    val geometry = PathGeometry(commands, fillRule)
    return geometry.copy(sourceViewBox = viewBox.toRectD() ?: geometry.bounds())
}

/**
 * Lowers a single region of a [VectorNetwork] into its own [PathGeometry] (for per-region fills).
 * Uses the region's own winding rule. Returns null when the index is out of range or empty.
 */
fun networkRegionGeometry(network: VectorNetwork, regionIndex: Int, viewBox: DesignViewBox? = null): PathGeometry? {
    val region = network.regions.getOrNull(regionIndex) ?: return null
    val rounded = network.vertices.any { it.cornerRadius > 0.0 }
    val commands = ArrayList<PathCommand>()
    for (loop in region.loops) emitLoop(network, loop, rounded, commands)
    if (commands.isEmpty()) return null
    val geometry = PathGeometry(commands, fillRuleOf(region.windingRule))
    return geometry.copy(sourceViewBox = viewBox.toRectD() ?: geometry.bounds())
}

private fun emitLoop(network: VectorNetwork, loop: List<Int>, rounded: Boolean, commands: MutableList<PathCommand>) {
    val segments = loop.mapNotNull { network.segments.getOrNull(it) }
    if (segments.isEmpty()) return
    if (rounded) {
        emitRoundedLoop(network, segments, commands)
    } else {
        val start = network.vertices.getOrNull(segments.first().from) ?: return
        commands += PathCommand.MoveTo(start.x, start.y)
        for (segment in segments) {
            val from = network.vertices.getOrNull(segment.from) ?: continue
            val to = network.vertices.getOrNull(segment.to) ?: continue
            commands += segmentToCommand(from, to)
        }
        if (segments.last().to == segments.first().from) commands += PathCommand.Close
    }
}

/** A straight segment (no handles) collapses to a line; otherwise a cubic from the offset handles. */
private fun segmentToCommand(from: VectorVertex, to: VectorVertex): PathCommand {
    val out = from.outHandle
    val incoming = to.inHandle
    if (out == null && incoming == null) return PathCommand.LineTo(to.x, to.y)
    return PathCommand.CubicTo(
        c1x = from.x + (out?.dx ?: 0.0),
        c1y = from.y + (out?.dy ?: 0.0),
        c2x = to.x + (incoming?.dx ?: 0.0),
        c2y = to.y + (incoming?.dy ?: 0.0),
        x = to.x,
        y = to.y,
    )
}

private class Pt(val x: Double, val y: Double)

/**
 * Emits one loop with per-vertex corner rounding. A vertex is rounded only when it joins two
 * straight segments and its [VectorVertex.cornerRadius] is positive; the trim distance is clamped
 * to half of the shorter adjacent segment so two neighbouring rounds never overlap.
 */
private fun emitRoundedLoop(network: VectorNetwork, segments: List<VectorSegment>, out: MutableList<PathCommand>) {
    val vs = network.vertices
    val closed = segments.last().to == segments.first().from

    // Ordered ring of distinct vertex indices around the loop.
    val order = ArrayList<Int>()
    order += segments.first().from
    for (s in segments) order += s.to
    val ring = if (closed && order.size > 1 && order.first() == order.last()) order.dropLast(1) else order
    val n = ring.size
    if (n == 0) return

    fun vertex(pos: Int): VectorVertex = vs[ring[pos]]
    fun hasPrev(pos: Int): Boolean = closed || pos > 0
    fun hasNext(pos: Int): Boolean = closed || pos < n - 1
    fun prevPos(pos: Int): Int = if (pos == 0) n - 1 else pos - 1
    fun nextPos(pos: Int): Int = if (pos == n - 1) 0 else pos + 1

    val entry = arrayOfNulls<Pt>(n)
    val exit = arrayOfNulls<Pt>(n)
    val eligible = BooleanArray(n)

    for (i in 0 until n) {
        val v = vertex(i)
        if (v.cornerRadius <= 0.0 || v.inHandle != null || v.outHandle != null || !hasPrev(i) || !hasNext(i)) continue
        val p = vertex(prevPos(i))
        val q = vertex(nextPos(i))
        if (p.outHandle != null || q.inHandle != null) continue // adjacent segments must be straight

        val d1x = p.x - v.x
        val d1y = p.y - v.y
        val d2x = q.x - v.x
        val d2y = q.y - v.y
        val len1 = sqrt(d1x * d1x + d1y * d1y)
        val len2 = sqrt(d2x * d2x + d2y * d2y)
        if (len1 < 1e-9 || len2 < 1e-9) continue
        val u1x = d1x / len1
        val u1y = d1y / len1
        val u2x = d2x / len2
        val u2y = d2y / len2
        val cos = (u1x * u2x + u1y * u2y).coerceIn(-1.0, 1.0)
        val theta = acos(cos)
        if (theta < 1e-4 || theta > PI - 1e-4) continue // degenerate: spike or straight

        val t = min(min(v.cornerRadius / tan(theta / 2.0), len1 / 2.0), len2 / 2.0)
        if (t < 1e-6) continue
        entry[i] = Pt(v.x + u1x * t, v.y + u1y * t)
        exit[i] = Pt(v.x + u2x * t, v.y + u2y * t)
        eligible[i] = true
    }

    // Start point: after vertex 0's corner if it is rounded, else the vertex itself.
    val v0 = vertex(0)
    val startPt = if (eligible[0]) exit[0]!! else Pt(v0.x, v0.y)
    out += PathCommand.MoveTo(startPt.x, startPt.y)

    for (i in segments.indices) {
        val toPos = if (closed) nextPos(i) else i + 1
        val fromV = vs[segments[i].from]
        val toV = vs[segments[i].to]
        val endPt = if (eligible[toPos]) entry[toPos]!! else Pt(toV.x, toV.y)
        if (fromV.outHandle == null && toV.inHandle == null) {
            out += PathCommand.LineTo(endPt.x, endPt.y)
        } else {
            // Curved segment; its endpoints are never rounded, so draw the plain cubic.
            out += segmentToCommand(fromV, toV)
        }
        if (eligible[toPos]) out += cornerArc(entry[toPos]!!, exit[toPos]!!, Pt(toV.x, toV.y))
    }
    if (closed) out += PathCommand.Close
}

/** A single cubic approximating the circular corner arc from [from] to [to] around apex [apex]. */
private fun cornerArc(from: Pt, to: Pt, apex: Pt): PathCommand {
    // Interior angle theta at the apex between the two trimmed edges.
    val a1x = from.x - apex.x
    val a1y = from.y - apex.y
    val a2x = to.x - apex.x
    val a2y = to.y - apex.y
    val l1 = sqrt(a1x * a1x + a1y * a1y)
    val l2 = sqrt(a2x * a2x + a2y * a2y)
    val cos = if (l1 < 1e-9 || l2 < 1e-9) 0.0 else ((a1x * a2x + a1y * a2y) / (l1 * l2)).coerceIn(-1.0, 1.0)
    val theta = acos(cos)
    // Handle fraction along (apex - endpoint): exact for the circular-arc approximation.
    val frac = if (abs(PI - theta) < 1e-6) 0.0 else tan(theta / 2.0) * (4.0 / 3.0) * tan((PI - theta) / 4.0)
    return PathCommand.CubicTo(
        c1x = from.x + (apex.x - from.x) * frac,
        c1y = from.y + (apex.y - from.y) * frac,
        c2x = to.x + (apex.x - to.x) * frac,
        c2y = to.y + (apex.y - to.y) * frac,
        x = to.x,
        y = to.y,
    )
}

internal fun fillRuleOf(windingRule: String?): PathFillRule =
    if (windingRule == "evenodd") PathFillRule.EvenOdd else PathFillRule.NonZero

internal fun DesignViewBox?.toRectD(): RectD? =
    if (this != null && width > 0.0 && height > 0.0) RectD(x, y, x + width, y + height) else null
