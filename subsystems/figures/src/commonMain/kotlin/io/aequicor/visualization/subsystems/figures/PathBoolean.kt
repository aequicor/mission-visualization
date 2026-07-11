package io.aequicor.visualization.subsystems.figures

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToLong

/** The four Figma boolean operations. */
enum class PathBooleanOp { Union, Subtract, Intersect, Exclude }

/**
 * Pure-Kotlin polygon boolean. Both operands are flattened to polygon rings (curves become
 * polylines — the output is polyline geometry, a deliberate v1 tradeoff), then combined by an
 * edge-classification arrangement:
 *
 * 1. every edge of both operands is split at all pairwise intersections (proper + collinear);
 * 2. each resulting sub-edge is kept iff the boolean predicate differs on its two sides (sampled
 *    just off the midpoint), oriented so the result interior lies on its left;
 * 3. coincident boundary edges are cancelled/merged, then the kept edges chain into closed loops.
 *
 * The result is a [PathFillRule.NonZero] geometry. This classification approach is robust to the
 * degeneracies real design content produces — shared edges, full containment, self-intersection —
 * because it decides membership by sampling regions, not by walking fragile intersection chains.
 */
fun pathBoolean(
    a: PathGeometry,
    b: PathGeometry,
    op: PathBooleanOp,
    tolerance: Double = 0.25,
): PathGeometry {
    val ringsA = flattenToPolygons(a, tolerance).map { cleanRing(it) }.filter { it.size >= 3 }
    val ringsB = flattenToPolygons(b, tolerance).map { cleanRing(it) }.filter { it.size >= 3 }
    if (ringsA.isEmpty() && ringsB.isEmpty()) return PathGeometry(emptyList())
    val evenOddA = a.fillRule == PathFillRule.EvenOdd
    val evenOddB = b.fillRule == PathFillRule.EvenOdd

    // Scale-relative epsilons.
    var minX = Double.POSITIVE_INFINITY; var minY = Double.POSITIVE_INFINITY
    var maxX = Double.NEGATIVE_INFINITY; var maxY = Double.NEGATIVE_INFINITY
    (ringsA + ringsB).forEach { ring ->
        ring.forEach {
            if (it.x < minX) minX = it.x; if (it.y < minY) minY = it.y
            if (it.x > maxX) maxX = it.x; if (it.y > maxY) maxY = it.y
        }
    }
    val diag = hypot(maxX - minX, maxY - minY).coerceAtLeast(1.0)
    val snap = 1e-6 * diag
    val sampleDist = 1e-4 * diag
    val paramEps = 1e-9

    // All directed edges of both operands.
    val edges = ArrayList<Seg>()
    fun addRing(ring: List<PointD>) {
        for (i in ring.indices) {
            val p = ring[i]
            val q = ring[(i + 1) % ring.size]
            if (hypot(q.x - p.x, q.y - p.y) > snap) edges += Seg(p.x, p.y, q.x, q.y)
        }
    }
    ringsA.forEach { addRing(it) }
    ringsB.forEach { addRing(it) }
    if (edges.isEmpty()) return PathGeometry(emptyList())

    // Split parameters per edge (0 and 1 always present).
    val params = Array(edges.size) { mutableListOf(0.0, 1.0) }
    for (i in edges.indices) {
        for (j in i + 1 until edges.size) {
            intersectInto(edges[i], edges[j], params[i], params[j])
        }
    }

    fun pred(inA: Boolean, inB: Boolean): Boolean = when (op) {
        PathBooleanOp.Union -> inA || inB
        PathBooleanOp.Subtract -> inA && !inB
        PathBooleanOp.Intersect -> inA && inB
        PathBooleanOp.Exclude -> inA != inB
    }

    val kept = ArrayList<Seg>()
    for (i in edges.indices) {
        val e = edges[i]
        val ps = params[i].distinct().sorted()
        val dx = e.x1 - e.x0
        val dy = e.y1 - e.y0
        val len = hypot(dx, dy)
        if (len < snap) continue
        val nx = -dy / len * sampleDist
        val ny = dx / len * sampleDist
        for (k in 0 until ps.size - 1) {
            val ta = ps[k]
            val tb = ps[k + 1]
            if (tb - ta < paramEps) continue
            val tm = (ta + tb) / 2.0
            val mx = e.x0 + dx * tm
            val my = e.y0 + dy * tm
            val resL = pred(inside(ringsA, mx + nx, my + ny, evenOddA), inside(ringsB, mx + nx, my + ny, evenOddB))
            val resR = pred(inside(ringsA, mx - nx, my - ny, evenOddA), inside(ringsB, mx - nx, my - ny, evenOddB))
            if (resL == resR) continue
            val ax = e.x0 + dx * ta; val ay = e.y0 + dy * ta
            val bx = e.x0 + dx * tb; val by = e.y0 + dy * tb
            kept += if (resL) Seg(ax, ay, bx, by) else Seg(bx, by, ax, ay)
        }
    }

    val cleaned = cancelCoincident(kept, snap)
    if (cleaned.isEmpty()) return PathGeometry(emptyList())
    val loops = chainLoops(cleaned, snap)
    val commands = ArrayList<PathCommand>()
    for (loop in loops) {
        if (loop.size < 3) continue
        commands += PathCommand.MoveTo(loop[0].x, loop[0].y)
        for (i in 1 until loop.size) commands += PathCommand.LineTo(loop[i].x, loop[i].y)
        commands += PathCommand.Close
    }
    if (commands.isEmpty()) return PathGeometry(emptyList())
    return PathGeometry(commands, PathFillRule.NonZero)
}

/**
 * Left-folds [operands] with [op]: `Union`/`Intersect`/`Exclude` are associative; `Subtract` yields
 * `o0 - o1 - o2 - ...`. A single operand is normalized (self-union) so a self-intersecting shape
 * comes out clean. Empty operands are ignored.
 */
fun pathBooleanFold(
    operands: List<PathGeometry>,
    op: PathBooleanOp,
    tolerance: Double = 0.25,
): PathGeometry {
    val nonEmpty = operands.filter { it.commands.isNotEmpty() }
    if (nonEmpty.isEmpty()) return PathGeometry(emptyList())
    var acc = pathBoolean(nonEmpty[0], PathGeometry(emptyList()), PathBooleanOp.Union, tolerance)
    for (i in 1 until nonEmpty.size) acc = pathBoolean(acc, nonEmpty[i], op, tolerance)
    return acc
}

private class Seg(val x0: Double, val y0: Double, val x1: Double, val y1: Double)

private fun cleanRing(ring: List<PointD>): List<PointD> {
    if (ring.isEmpty()) return ring
    val out = ArrayList<PointD>(ring.size)
    for (p in ring) {
        val last = out.lastOrNull()
        if (last == null || abs(last.x - p.x) > 1e-12 || abs(last.y - p.y) > 1e-12) out += p
    }
    if (out.size > 1) {
        val f = out.first(); val l = out.last()
        if (abs(f.x - l.x) < 1e-12 && abs(f.y - l.y) < 1e-12) out.removeAt(out.size - 1)
    }
    return out
}

/** Adds split parameters where [e] meets [f]: proper crossing and collinear-overlap endpoints. */
private fun intersectInto(e: Seg, f: Seg, pe: MutableList<Double>, pf: MutableList<Double>) {
    val rx = e.x1 - e.x0; val ry = e.y1 - e.y0
    val sx = f.x1 - f.x0; val sy = f.y1 - f.y0
    val denom = rx * sy - ry * sx
    val qpx = f.x0 - e.x0; val qpy = f.y0 - e.y0
    val eps = 1e-9
    if (abs(denom) > eps) {
        val t = (qpx * sy - qpy * sx) / denom
        val u = (qpx * ry - qpy * rx) / denom
        if (t > -1e-9 && t < 1.0 + 1e-9 && u > -1e-9 && u < 1.0 + 1e-9) {
            pe += t.coerceIn(0.0, 1.0)
            pf += u.coerceIn(0.0, 1.0)
        }
    } else {
        // Parallel; if collinear, split each at the other's endpoints projected inside.
        val cross = qpx * ry - qpy * rx
        if (abs(cross) <= eps * (abs(rx) + abs(ry) + 1.0)) {
            val rr = rx * rx + ry * ry
            val ss = sx * sx + sy * sy
            if (rr > eps) {
                val t0 = ((f.x0 - e.x0) * rx + (f.y0 - e.y0) * ry) / rr
                val t1 = ((f.x1 - e.x0) * rx + (f.y1 - e.y0) * ry) / rr
                if (t0 > 1e-9 && t0 < 1.0 - 1e-9) pe += t0
                if (t1 > 1e-9 && t1 < 1.0 - 1e-9) pe += t1
            }
            if (ss > eps) {
                val u0 = ((e.x0 - f.x0) * sx + (e.y0 - f.y0) * sy) / ss
                val u1 = ((e.x1 - f.x0) * sx + (e.y1 - f.y0) * sy) / ss
                if (u0 > 1e-9 && u0 < 1.0 - 1e-9) pf += u0
                if (u1 > 1e-9 && u1 < 1.0 - 1e-9) pf += u1
            }
        }
    }
}

/** Even-odd (crossing parity) or nonzero (winding) point-in-region over a set of rings. */
private fun inside(rings: List<List<PointD>>, px: Double, py: Double, evenOdd: Boolean): Boolean {
    if (rings.isEmpty()) return false
    if (evenOdd) {
        var c = false
        for (ring in rings) {
            val n = ring.size
            var j = n - 1
            for (i in 0 until n) {
                val a = ring[i]; val b = ring[j]
                if ((a.y > py) != (b.y > py)) {
                    val xInt = (b.x - a.x) * (py - a.y) / (b.y - a.y) + a.x
                    if (px < xInt) c = !c
                }
                j = i
            }
        }
        return c
    }
    var wn = 0
    for (ring in rings) {
        val n = ring.size
        for (i in 0 until n) {
            val a = ring[i]; val b = ring[(i + 1) % n]
            if (a.y <= py) {
                if (b.y > py && isLeft(a.x, a.y, b.x, b.y, px, py) > 0) wn++
            } else {
                if (b.y <= py && isLeft(a.x, a.y, b.x, b.y, px, py) < 0) wn--
            }
        }
    }
    return wn != 0
}

private fun isLeft(ax: Double, ay: Double, bx: Double, by: Double, px: Double, py: Double): Double =
    (bx - ax) * (py - ay) - (px - ax) * (by - ay)

/** Drops equal-and-opposite coincident edges (interior boundary) and de-dups identical ones. */
private fun cancelCoincident(segs: List<Seg>, snap: Double): List<Seg> {
    fun key(x: Double, y: Double) = "${(x / snap).roundToLong()},${(y / snap).roundToLong()}"
    val counts = HashMap<String, Int>()
    val forward = HashMap<String, Seg>()
    for (s in segs) {
        val a = key(s.x0, s.y0); val b = key(s.x1, s.y1)
        val fwd = "$a>$b"; val rev = "$b>$a"
        val revCount = counts[rev] ?: 0
        if (revCount > 0) {
            counts[rev] = revCount - 1 // cancel with an opposite edge
        } else {
            counts[fwd] = (counts[fwd] ?: 0) + 1
            if (!forward.containsKey(fwd)) forward[fwd] = s
        }
    }
    val out = ArrayList<Seg>()
    for ((k, c) in counts) {
        if (c <= 0) continue
        val s = forward[k] ?: continue
        repeat(c) { out += s }
    }
    return out
}

/** Greedily chains directed edges into closed loops (in/out degree balances at every vertex). */
private fun chainLoops(segs: List<Seg>, snap: Double): List<List<PointD>> {
    fun key(x: Double, y: Double) = "${(x / snap).roundToLong()},${(y / snap).roundToLong()}"
    val adjacency = HashMap<String, ArrayDeque<Int>>()
    for (i in segs.indices) {
        adjacency.getOrPut(key(segs[i].x0, segs[i].y0)) { ArrayDeque() }.add(i)
    }
    val used = BooleanArray(segs.size)
    val loops = ArrayList<List<PointD>>()
    for (start in segs.indices) {
        if (used[start]) continue
        val loop = ArrayList<PointD>()
        var cur = start
        var guard = 0
        while (!used[cur] && guard <= segs.size) {
            used[cur] = true
            val s = segs[cur]
            loop += PointD(s.x0, s.y0)
            val queue = adjacency[key(s.x1, s.y1)]
            var next = -1
            while (queue != null && queue.isNotEmpty()) {
                val cand = queue.removeFirst()
                if (!used[cand]) { next = cand; break }
            }
            if (next < 0) break
            cur = next
            guard++
        }
        if (loop.size >= 3) loops += loop
    }
    return loops
}
