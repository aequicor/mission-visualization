package io.aequicor.visualization.subsystems.figures

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Re-fits flattened polyline geometry back into cubic Béziers using Schneider's fit-curve
 * algorithm ("An Algorithm for Automatically Fitting Digitized Curves", Graphics Gems, 1990):
 * a least-squares cubic fit with Newton–Raphson reparameterization and recursive splitting at the
 * point of maximum error. Straight (near-collinear) runs stay [PathCommand.LineTo]; curved runs
 * become [PathCommand.CubicTo].
 *
 * This is **opt-in**: the boolean ([pathBoolean]) and stroke-outline ([strokeOutline]) engines emit
 * flattened polylines; callers that want smooth curves back (Flatten / Outline Stroke) pipe their
 * result through this before serializing. QuadTo/CubicTo inputs are flattened first (via
 * [flattenSubpaths]) then refit, so the function is total over any geometry.
 *
 * @param tolerance maximum allowed distance (document units) between the fitted curve and the input
 *   polyline; larger values yield fewer, looser curves.
 */
fun PathGeometry.refitCurves(tolerance: Double = 0.8): PathGeometry {
    if (commands.isEmpty()) return this
    // Flatten any residual quads/cubics to a dense polyline before refitting.
    val subs = flattenSubpaths(this, tolerance = 0.1)
    if (subs.isEmpty()) return this
    val tol = if (tolerance > 1e-6) tolerance else 1e-6
    val out = ArrayList<PathCommand>()
    for (sub in subs) {
        val pts = sub.points
        if (pts.isEmpty()) continue
        if (pts.size < 3) {
            // Degenerate run: pass through unchanged as a polyline.
            out += PathCommand.MoveTo(pts[0].x, pts[0].y)
            for (i in 1 until pts.size) out += PathCommand.LineTo(pts[i].x, pts[i].y)
            if (sub.closed) out += PathCommand.Close
            continue
        }
        // For a closed subpath, re-append the start so the fit spans the full loop.
        val fitPts = if (sub.closed) pts + pts.first() else pts
        val deduped = dropRunDuplicates(fitPts)
        if (deduped.size < 3) {
            out += PathCommand.MoveTo(pts[0].x, pts[0].y)
            for (i in 1 until pts.size) out += PathCommand.LineTo(pts[i].x, pts[i].y)
            if (sub.closed) out += PathCommand.Close
            continue
        }
        out += PathCommand.MoveTo(deduped[0].x, deduped[0].y)
        val tHat1 = leftTangent(deduped)
        val tHat2 = rightTangent(deduped)
        fitCubic(deduped, tHat1, tHat2, tol, depth = 0, out = out)
        if (sub.closed) out += PathCommand.Close
    }
    return PathGeometry(out, fillRule = fillRule, sourceViewBox = sourceViewBox)
}

/** Removes consecutive duplicate points that would break tangent/parameterization math. */
private fun dropRunDuplicates(pts: List<PointD>): List<PointD> {
    val out = ArrayList<PointD>(pts.size)
    for (p in pts) {
        val last = out.lastOrNull()
        if (last == null || dist(last, p) > 1e-9) out += p
    }
    return out
}

/**
 * Fits a sequence of on-curve points to one or more cubics, appending [PathCommand.CubicTo] /
 * [PathCommand.LineTo] to [out]. The starting [PathCommand.MoveTo] is emitted by the caller.
 */
private fun fitCubic(
    d: List<PointD>,
    tHat1: PointD,
    tHat2: PointD,
    error: Double,
    depth: Int,
    out: MutableList<PathCommand>,
) {
    val last = d.last()
    // Straight-run short-circuit: a near-collinear run stays a single LineTo.
    if (isStraight(d, error)) {
        out += PathCommand.LineTo(last.x, last.y)
        return
    }
    if (depth >= 32) {
        // Bail out to a polyline for pathological inputs rather than recursing forever.
        for (i in 1 until d.size) out += PathCommand.LineTo(d[i].x, d[i].y)
        return
    }

    val nPts = d.size
    if (nPts == 2) {
        out += PathCommand.LineTo(last.x, last.y)
        return
    }

    var u = chordLengthParameterize(d)
    var bezier = generateBezier(d, u, tHat1, tHat2)
    var (maxError, splitPoint) = computeMaxError(d, bezier, u)
    val errSq = error * error
    if (maxError < errSq) {
        emitCubic(bezier, out)
        return
    }

    // Reparameterize and try again a couple of times if we are reasonably close.
    if (maxError < errSq * 4.0) {
        repeat(4) {
            val uPrime = reparameterize(d, u, bezier)
            bezier = generateBezier(d, uPrime, tHat1, tHat2)
            val r = computeMaxError(d, bezier, uPrime)
            maxError = r.first
            splitPoint = r.second
            if (maxError < errSq) {
                emitCubic(bezier, out)
                return
            }
            u = uPrime
        }
    }

    // Split at the point of maximum error and fit each half recursively.
    if (splitPoint <= 0 || splitPoint >= nPts - 1) {
        // No usable interior split; emit the best fit we have.
        emitCubic(bezier, out)
        return
    }
    val tHatCenter = centerTangent(d, splitPoint)
    fitCubic(d.subList(0, splitPoint + 1), tHat1, tHatCenter, error, depth + 1, out)
    fitCubic(d.subList(splitPoint, nPts), negate(tHatCenter), tHat2, error, depth + 1, out)
}

private fun emitCubic(bezier: Array<PointD>, out: MutableList<PathCommand>) {
    out += PathCommand.CubicTo(
        bezier[1].x, bezier[1].y,
        bezier[2].x, bezier[2].y,
        bezier[3].x, bezier[3].y,
    )
}

/** True if every interior point of [d] lies within [tolerance] of the chord from first to last. */
private fun isStraight(d: List<PointD>, tolerance: Double): Boolean {
    val a = d.first()
    val b = d.last()
    if (dist(a, b) < 1e-9) return false // degenerate chord (closed loop): not straight
    for (i in 1 until d.size - 1) {
        if (pointLineDistance(d[i], a, b) > tolerance) return false
    }
    return true
}

private fun generateBezier(d: List<PointD>, uPrime: DoubleArray, tHat1: PointD, tHat2: PointD): Array<PointD> {
    val n = d.size
    val first = d.first()
    val lastP = d.last()
    // A[i] = [tHat1 * B1(u), tHat2 * B2(u)]
    var c00 = 0.0
    var c01 = 0.0
    var c11 = 0.0
    var x0 = 0.0
    var x1 = 0.0
    for (i in 0 until n) {
        val u = uPrime[i]
        val b0 = bern0(u)
        val b1 = bern1(u)
        val b2 = bern2(u)
        val b3 = bern3(u)
        val a0 = scale(tHat1, b1)
        val a1 = scale(tHat2, b2)
        c00 += dot(a0, a0)
        c01 += dot(a0, a1)
        c11 += dot(a1, a1)
        // tmp = d[i] - (first*(b0+b1) + last*(b2+b3))
        val tmpx = d[i].x - (first.x * (b0 + b1) + lastP.x * (b2 + b3))
        val tmpy = d[i].y - (first.y * (b0 + b1) + lastP.y * (b2 + b3))
        val tmp = PointD(tmpx, tmpy)
        x0 += dot(a0, tmp)
        x1 += dot(a1, tmp)
    }
    val detC0C1 = c00 * c11 - c01 * c01
    val detC0X = c00 * x1 - c01 * x0
    val detXC1 = x0 * c11 - x1 * c01
    var alphaL = if (abs(detC0C1) < 1e-12) 0.0 else detXC1 / detC0C1
    var alphaR = if (abs(detC0C1) < 1e-12) 0.0 else detC0X / detC0C1

    val segLength = dist(first, lastP)
    val epsilon = 1e-6 * segLength
    if (alphaL < epsilon || alphaR < epsilon) {
        // Fall back to a heuristic: place control points 1/3 along the chord.
        val third = segLength / 3.0
        alphaL = third
        alphaR = third
    }
    return arrayOf(
        first,
        add(first, scale(tHat1, alphaL)),
        add(lastP, scale(tHat2, alphaR)),
        lastP,
    )
}

private fun reparameterize(d: List<PointD>, u: DoubleArray, bezier: Array<PointD>): DoubleArray {
    val out = DoubleArray(d.size)
    for (i in d.indices) out[i] = newtonRaphson(bezier, d[i], u[i])
    return out
}

private fun newtonRaphson(q: Array<PointD>, point: PointD, u: Double): Double {
    // Q(u)
    val qu = bezierEval(q, u)
    // Q'(u): control points 3*(Q[i+1]-Q[i])
    val q1 = arrayOf(
        scale(sub(q[1], q[0]), 3.0),
        scale(sub(q[2], q[1]), 3.0),
        scale(sub(q[3], q[2]), 3.0),
    )
    // Q''(u): control points 2*(Q1[i+1]-Q1[i])
    val q2 = arrayOf(
        scale(sub(q1[1], q1[0]), 2.0),
        scale(sub(q1[2], q1[1]), 2.0),
    )
    val q1u = bezierEval(q1, u)
    val q2u = bezierEval(q2, u)
    val numerator = (qu.x - point.x) * q1u.x + (qu.y - point.y) * q1u.y
    val denominator = q1u.x * q1u.x + q1u.y * q1u.y +
        (qu.x - point.x) * q2u.x + (qu.y - point.y) * q2u.y
    if (abs(denominator) < 1e-12) return u
    return u - numerator / denominator
}

/** Squared max error + split index of the worst point. */
private fun computeMaxError(d: List<PointD>, bezier: Array<PointD>, u: DoubleArray): Pair<Double, Int> {
    var maxDist = 0.0
    var splitPoint = d.size / 2
    for (i in 1 until d.size - 1) {
        val p = bezierEval(bezier, u[i])
        val dx = p.x - d[i].x
        val dy = p.y - d[i].y
        val distSq = dx * dx + dy * dy
        if (distSq >= maxDist) {
            maxDist = distSq
            splitPoint = i
        }
    }
    return maxDist to splitPoint
}

private fun chordLengthParameterize(d: List<PointD>): DoubleArray {
    val u = DoubleArray(d.size)
    u[0] = 0.0
    for (i in 1 until d.size) u[i] = u[i - 1] + dist(d[i - 1], d[i])
    val total = u.last()
    if (total < 1e-12) {
        for (i in d.indices) u[i] = i.toDouble() / (d.size - 1)
    } else {
        for (i in d.indices) u[i] = u[i] / total
    }
    return u
}

private fun leftTangent(d: List<PointD>): PointD = normalize(sub(d[1], d[0]))

private fun rightTangent(d: List<PointD>): PointD = normalize(sub(d[d.size - 2], d[d.size - 1]))

private fun centerTangent(d: List<PointD>, center: Int): PointD {
    val v1 = sub(d[center - 1], d[center])
    val v2 = sub(d[center], d[center + 1])
    val mid = PointD((v1.x + v2.x) / 2.0, (v1.y + v2.y) / 2.0)
    val n = normalize(mid)
    return if (n.x == 0.0 && n.y == 0.0) normalize(v1) else n
}

/** de Casteljau evaluation of a Bézier of any degree. */
private fun bezierEval(ctrl: Array<PointD>, t: Double): PointD {
    val tmp = ctrl.copyOf()
    val n = tmp.size
    for (k in 1 until n) {
        for (i in 0 until n - k) {
            tmp[i] = PointD(
                tmp[i].x + (tmp[i + 1].x - tmp[i].x) * t,
                tmp[i].y + (tmp[i + 1].y - tmp[i].y) * t,
            )
        }
    }
    return tmp[0]
}

private fun bern0(u: Double): Double { val t = 1.0 - u; return t * t * t }
private fun bern1(u: Double): Double { val t = 1.0 - u; return 3.0 * u * t * t }
private fun bern2(u: Double): Double { val t = 1.0 - u; return 3.0 * u * u * t }
private fun bern3(u: Double): Double = u * u * u

private fun sub(a: PointD, b: PointD): PointD = PointD(a.x - b.x, a.y - b.y)
private fun add(a: PointD, b: PointD): PointD = PointD(a.x + b.x, a.y + b.y)
private fun scale(a: PointD, s: Double): PointD = PointD(a.x * s, a.y * s)
private fun negate(a: PointD): PointD = PointD(-a.x, -a.y)
private fun dot(a: PointD, b: PointD): Double = a.x * b.x + a.y * b.y
private fun dist(a: PointD, b: PointD): Double = sqrt((a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y))

private fun normalize(a: PointD): PointD {
    val len = sqrt(a.x * a.x + a.y * a.y)
    return if (len < 1e-12) PointD(0.0, 0.0) else PointD(a.x / len, a.y / len)
}

private fun pointLineDistance(p: PointD, a: PointD, b: PointD): Double {
    val dx = b.x - a.x
    val dy = b.y - a.y
    val len = sqrt(dx * dx + dy * dy)
    if (len < 1e-12) return dist(p, a)
    return abs(dx * (a.y - p.y) - dy * (a.x - p.x)) / len
}
