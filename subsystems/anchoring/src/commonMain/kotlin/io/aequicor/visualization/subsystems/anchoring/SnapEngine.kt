package io.aequicor.visualization.subsystems.anchoring

import kotlin.math.abs

// --- Move-drag smart snapping (design-book §18 "snap ... to parent center, parent edges,
//     sibling edges and sibling centers") -----------------------------------------------

/** Per-axis snap correction (doc units) to add to the raw drag, plus the guide lines to draw. */
data class SnapResult(
    val dx: Double,
    val dy: Double,
    val guides: List<SnapLine>,
)

/** One axis's winning alignment: how far to nudge, the line it aligned to, and which target owns it. */
private data class AxisSnap(val delta: Double, val aligned: Double, val target: SnapBox)

/**
 * Best alignment on one axis: compares each of [sourceLines] (a dragged box's left/center/right
 * or top/center/bottom, tagged with whether it is the *center*) against every target's
 * corresponding lines ([targetLinesOf]); keeps the pair with the smallest `|delta|` within
 * [threshold]. Exact ties prefer a center-involving alignment (rank 0 center↔center, 1 mixed,
 * 2 edge↔edge); remaining ties keep the earlier one (fixed iteration order → deterministic).
 */
private fun snapAxis(
    sourceLines: List<Pair<Double, Boolean>>,
    targets: List<SnapBox>,
    targetLinesOf: (SnapBox) -> List<Pair<Double, Boolean>>,
    threshold: Double,
): AxisSnap? {
    var best: AxisSnap? = null
    var bestDistance = Double.MAX_VALUE
    var bestRank = Int.MAX_VALUE
    targets.forEach { target ->
        val targetLines = targetLinesOf(target)
        sourceLines.forEach { (src, srcCenter) ->
            targetLines.forEach { (dst, dstCenter) ->
                val delta = dst - src
                val distance = abs(delta)
                if (distance <= threshold) {
                    val rank = if (srcCenter && dstCenter) 0 else if (srcCenter || dstCenter) 1 else 2
                    val better = distance < bestDistance || (distance == bestDistance && rank < bestRank)
                    if (better) {
                        bestDistance = distance
                        bestRank = rank
                        best = AxisSnap(delta = delta, aligned = dst, target = target)
                    }
                }
            }
        }
    }
    return best
}

/**
 * Backward-compatible alignment-only snapping: [targets] are treated as a flat list of
 * boxes to align edges/centers against, with no container-relative "beautiful" anchors.
 * Retained for its regression suite and as the edge/center-only entry point; delegates to
 * [computeAnchors] (the single source of truth) with no containers.
 */
fun computeSnap(moved: SnapBox, targets: List<SnapBox>, threshold: Double): SnapResult {
    val result = computeAnchors(moved, containers = emptyList(), siblings = targets, threshold = threshold)
    return SnapResult(result.dx, result.dy, result.guides.map { it.line })
}

// --- Beautiful-anchor snapping (design-book §18 + "beautiful positions") ----------------
//
// Extends the edge/center alignment above with the positions a designer reads as
// "beautiful" relative to the container(s) and the sibling components:
//   • center / edges           — Figma's alignment guides (via [snapAxis], highest priority);
//   • equal spacing            — the moving box centered between two flanking siblings so the
//                                gap on each side is equal (Figma's distribution bars);
//   • equal margin             — centered between a sibling and the container wall on the other side;
//   • match gap                — the gap to a neighbour duplicates an existing gap between two
//                                other siblings (Figma's "keep the rhythm" snapping);
//   • golden ratio             — a box line landing on a container's 0.382 / 0.618 section line;
//   • simple proportions       — thirds (1/3, 2/3) and quarters (1/4, 3/4) of a container.
//
// Pure and framework-free. Resolves each axis independently: it picks the single nearest
// candidate within [threshold], breaking ties by a fixed priority (alignment > equal spacing >
// match gap > equal margin > golden ratio > proportion), and returns the per-axis snap delta
// plus the overlay guides and spacing bars to draw. `halves` is intentionally omitted from
// proportions: a box centered on a container is already the container-center *alignment* candidate.

/**
 * The kind of "beautiful" relationship a winning anchor expresses; drives overlay styling.
 * [EqualSpacing], [EqualMargin] and [MatchGap] are the "equal distances" family — all drawn as
 * green spacing bars rather than a guide line.
 */
enum class AnchorKind {
    Alignment,      // edge/center aligned to a container or sibling
    EqualSpacing,   // centered between two flanking siblings (equal gap each side)
    EqualMargin,    // equal gap to a sibling and to the container wall on the opposite side
    MatchGap,       // gap to a neighbour matches an existing gap between two other siblings
    GoldenRatio,    // a box line on a container's 0.382 / 0.618 section line
    Proportion,     // a box line on a container's 1/3, 2/3, 1/4 or 3/4 line
}

/** A guide line to draw for a winning anchor, tagged with its [kind] and an optional [label] ("φ", "1/3", …). */
data class AnchorGuide(val line: SnapLine, val kind: AnchorKind, val label: String? = null)

/** An equal-spacing measurement bar: the gap [segment] in document space and its [gap] length in doc units. */
data class SpacingBar(val segment: SnapLine, val gap: Double)

/** Result of [computeAnchors]: per-axis snap correction plus the overlay guides / spacing bars to draw. */
data class AnchorResult(
    val dx: Double,
    val dy: Double,
    val guides: List<AnchorGuide>,
    val spacing: List<SpacingBar> = emptyList(),
)

/** Golden-section fractions of a container interior: 0.382 = 1/φ², 0.618 = 1/φ (they sum to 1). */
private val GoldenFractions = listOf(0.3819660112501051, 0.6180339887498949)

/**
 * Figma-style magnetic anchoring for a free-move drag. [moved] is the dragged selection's
 * proposed union bounds (already displaced by the raw drag) in document coords. [containers]
 * are the boxes whose interior yields center/edge *and* golden/proportion anchors — the
 * immediate parent frame plus any ancestors up to the root, so a deeply nested node can still
 * anchor to an outer container. [siblings] are the co-resident components: they contribute
 * edge/center alignment and are the only boxes considered for equal-spacing distribution.
 * [threshold] is the snap radius in DOCUMENT units (the caller converts a fixed screen-px
 * radius via `/ zoom`, so the feel is constant on screen).
 *
 * Snaps X and Y independently to the single nearest anchor within [threshold]; returns the
 * delta to add to [moved] plus the guides / spacing bars. Pure and deterministic. Non-positive
 * [threshold] or nothing in range → a zero result.
 */
fun computeAnchors(
    moved: SnapBox,
    containers: List<SnapBox>,
    siblings: List<SnapBox>,
    threshold: Double,
): AnchorResult {
    if (threshold <= 0.0) return AnchorResult(0.0, 0.0, emptyList())
    val xWinner = resolveAxis(AxisX, moved, containers, siblings, threshold)
    val yWinner = resolveAxis(AxisY, moved, containers, siblings, threshold)

    val dx = xWinner?.delta ?: 0.0
    val dy = yWinner?.delta ?: 0.0
    val snapped = moved.translate(dx, dy)

    val guides = ArrayList<AnchorGuide>()
    val spacing = ArrayList<SpacingBar>()
    xWinner?.let { buildAxisOverlay(it, alongX = true, snapped, guides, spacing) }
    yWinner?.let { buildAxisOverlay(it, alongX = false, snapped, guides, spacing) }
    return AnchorResult(dx, dy, guides, spacing)
}

/** Alignment (edge/center) winner on one [axis] against [alignBoxes] — the axis-generic form of [snapAxis]. */
private fun alignAxis(axis: BoxAxis, moved: SnapBox, alignBoxes: List<SnapBox>, threshold: Double): AxisSnap? =
    snapAxis(
        sourceLines = listOf(axis.lo(moved) to false, axis.center(moved) to true, axis.hi(moved) to false),
        targets = alignBoxes,
        targetLinesOf = { listOf(axis.lo(it) to false, axis.center(it) to true, axis.hi(it) to false) },
        threshold = threshold,
    )

/**
 * The single winning anchor on one [axis] within [threshold] (alignment vs beautiful anchors), or
 * null. The per-axis entry point shared by [computeAnchors] and the sticky [solveMoveSnap] /
 * resize/rotate solvers, so all snapping resolves an axis the same way.
 */
internal fun resolveAxis(
    axis: BoxAxis,
    moved: SnapBox,
    containers: List<SnapBox>,
    siblings: List<SnapBox>,
    threshold: Double,
): AxisWinner? {
    if (threshold <= 0.0) return null
    val align = alignAxis(axis, moved, containers + siblings, threshold)
    val beauty = bestBeautyAnchor(axis, moved, containers, siblings, threshold)
    return chooseAxisWinner(axis, align, beauty)
}

/** One axis of a [SnapBox] as accessors, so the beautiful-anchor math is written once and applied to X and Y. */
internal class BoxAxis(
    val lo: (SnapBox) -> Double,
    val hi: (SnapBox) -> Double,
    val center: (SnapBox) -> Double,
    val crossLo: (SnapBox) -> Double,
    val crossHi: (SnapBox) -> Double,
)

internal val AxisX = BoxAxis({ it.x }, { it.right }, { it.centerX }, { it.y }, { it.bottom })
internal val AxisY = BoxAxis({ it.y }, { it.bottom }, { it.centerY }, { it.x }, { it.right })

/**
 * How to draw the green bars for an "equal distances" anchor. [lowBoundary] / [highBoundary]
 * are edges the moved box's own edges are measured against (drawn relative to the box's final
 * position). [refLo] / [refHi] is an optional fixed reference gap (the existing sibling gap a
 * [AnchorKind.MatchGap] snap duplicates), drawn where it actually is, not tied to the box.
 */
internal data class SpacingPlan(
    val lowBoundary: Double? = null,
    val highBoundary: Double? = null,
    val refLo: Double? = null,
    val refHi: Double? = null,
)

/** A candidate golden/proportion/equal-distance anchor on one axis, before comparing against alignment. */
private data class BeautyAnchor(
    val delta: Double,
    val target: Double,
    val kind: AnchorKind,
    val rank: Int,
    val label: String? = null,
    val crossLo: Double? = null,   // container cross extent for the guide line; null for the equal-distance family (bars instead)
    val crossHi: Double? = null,
    val plan: SpacingPlan? = null, // the spacing bars to draw (equal-distance family only)
)

/** The single axis winner across both alignment (via [snapAxis]) and the beautiful anchors, normalized for overlay building. */
internal data class AxisWinner(
    val delta: Double,
    val target: Double,
    val kind: AnchorKind,
    val label: String?,
    val crossLo: Double?,
    val crossHi: Double?,
    val plan: SpacingPlan? = null,
)

private fun rangesOverlap(aLo: Double, aHi: Double, bLo: Double, bHi: Double): Boolean = aLo < bHi && bLo < aHi

/**
 * Best golden/proportion/equal-distance anchor on [axis] within [threshold], or null. Golden and
 * proportion lines come from every [containers] interior and match whichever of the moved box's
 * lines (near edge / center / far edge) is closest. The equal-distance family uses [siblings]
 * that overlap the moved box on the perpendicular axis: centering it between two of them (equal
 * spacing) or between one and the container wall (equal margin), or duplicating an existing gap
 * between two other siblings (match gap). Ties break by [BeautyAnchor.rank] (equal spacing 3 <
 * match gap 4 < equal margin 5 < golden 6 < proportion 7).
 */
private fun bestBeautyAnchor(
    axis: BoxAxis,
    moved: SnapBox,
    containers: List<SnapBox>,
    siblings: List<SnapBox>,
    threshold: Double,
): BeautyAnchor? {
    val mLo = axis.lo(moved)
    val mCenter = axis.center(moved)
    val mHi = axis.hi(moved)
    val mSize = mHi - mLo
    val sources = listOf(mLo, mCenter, mHi)
    val candidates = ArrayList<BeautyAnchor>()

    containers.forEach { container ->
        val cLo = axis.lo(container)
        val span = axis.hi(container) - cLo
        if (span <= 0.0) return@forEach
        val crossLo = axis.crossLo(container)
        val crossHi = axis.crossHi(container)
        fun consider(fraction: Double, kind: AnchorKind, rank: Int, label: String) {
            val target = cLo + span * fraction
            val nearest = sources.map { target - it }.minByOrNull { abs(it) } ?: return
            if (abs(nearest) <= threshold) {
                candidates += BeautyAnchor(nearest, target, kind, rank, label, crossLo, crossHi)
            }
        }
        GoldenFractions.forEach { consider(it, AnchorKind.GoldenRatio, 6, "φ") }
        consider(1.0 / 3.0, AnchorKind.Proportion, 7, "1/3")
        consider(2.0 / 3.0, AnchorKind.Proportion, 7, "2/3")
        consider(0.25, AnchorKind.Proportion, 7, "1/4")
        consider(0.75, AnchorKind.Proportion, 7, "3/4")
    }

    // Equal-distance family: only siblings that overlap the moved box on the perpendicular
    // axis are "in the same row/column" and can form a real gap to balance or match.
    val flankable = siblings.filter { rangesOverlap(axis.crossLo(moved), axis.crossHi(moved), axis.crossLo(it), axis.crossHi(it)) }
    val lowSibling = flankable.filter { axis.center(it) < mCenter }.maxByOrNull { axis.center(it) }
    val highSibling = flankable.filter { axis.center(it) > mCenter }.minByOrNull { axis.center(it) }
    val wall = containers.firstOrNull() // the immediate container provides the "wall" for equal margins

    // (A) Center the box between the nearest boundary on each side, where a boundary is a
    //     flanking sibling's inner edge or the container wall. Two siblings → equal spacing;
    //     one sibling + a wall → equal margin. (Two walls would just be the container center,
    //     which is already an alignment candidate — so require at least one sibling.)
    run {
        val lowEdge = lowSibling?.let { axis.hi(it) } ?: wall?.let { axis.lo(it) }
        val highEdge = highSibling?.let { axis.lo(it) } ?: wall?.let { axis.hi(it) }
        val hasSibling = lowSibling != null || highSibling != null
        if (lowEdge != null && highEdge != null && hasSibling && highEdge - lowEdge > mSize) {
            val target = (lowEdge + highEdge) / 2.0
            val delta = target - mCenter
            if (abs(delta) <= threshold) {
                val bothSiblings = lowSibling != null && highSibling != null
                val kind = if (bothSiblings) AnchorKind.EqualSpacing else AnchorKind.EqualMargin
                val rank = if (bothSiblings) 3 else 5
                candidates += BeautyAnchor(delta, target, kind, rank, plan = SpacingPlan(lowBoundary = lowEdge, highBoundary = highEdge))
            }
        }
    }

    // (B) Match an existing sibling-to-sibling gap on either side of the box (duplication
    //     snapping): drag the box a distance G from a neighbour where G already occurs between
    //     two other siblings. Snaps the box's leading edge (low side) or trailing edge (high side).
    val existingGaps = existingGaps(axis, flankable)
    existingGaps.forEach { (gap, refLo, refHi) ->
        lowSibling?.let { neighbour ->
            val edge = axis.hi(neighbour)
            val delta = (edge + gap) - mLo
            if (abs(delta) <= threshold) {
                candidates += BeautyAnchor(delta, edge + gap, AnchorKind.MatchGap, 4, plan = SpacingPlan(lowBoundary = edge, refLo = refLo, refHi = refHi))
            }
        }
        highSibling?.let { neighbour ->
            val edge = axis.lo(neighbour)
            val delta = (edge - gap) - mHi
            if (abs(delta) <= threshold) {
                candidates += BeautyAnchor(delta, edge - gap, AnchorKind.MatchGap, 4, plan = SpacingPlan(highBoundary = edge, refLo = refLo, refHi = refHi))
            }
        }
    }

    return candidates.minWithOrNull(compareBy({ abs(it.delta) }, { it.rank }))
}

/** Distinct positive gaps between consecutive [flankable] siblings (sorted along [axis]), each with the inner edges that bound it. */
private fun existingGaps(axis: BoxAxis, flankable: List<SnapBox>): List<Triple<Double, Double, Double>> {
    if (flankable.size < 2) return emptyList()
    val sorted = flankable.sortedBy { axis.lo(it) }
    val gaps = ArrayList<Triple<Double, Double, Double>>()
    for (i in 0 until sorted.size - 1) {
        val lo = axis.hi(sorted[i])
        val hi = axis.lo(sorted[i + 1])
        if (hi - lo > 0.0) gaps += Triple(hi - lo, lo, hi)
    }
    return gaps.distinctBy { kotlin.math.round(it.first * 100.0) / 100.0 }
}

/** Picks the axis winner: real-geometry [align]ment wins ties over the [beauty] anchors (its higher priority). */
private fun chooseAxisWinner(axis: BoxAxis, align: AxisSnap?, beauty: BeautyAnchor?): AxisWinner? {
    val alignWinner = align?.let {
        AxisWinner(it.delta, it.aligned, AnchorKind.Alignment, null, axis.crossLo(it.target), axis.crossHi(it.target))
    }
    val beautyWinner = beauty?.let {
        AxisWinner(it.delta, it.target, it.kind, it.label, it.crossLo, it.crossHi, it.plan)
    }
    return when {
        alignWinner == null -> beautyWinner
        beautyWinner == null -> alignWinner
        abs(alignWinner.delta) <= abs(beautyWinner.delta) -> alignWinner
        else -> beautyWinner
    }
}

/**
 * Appends the overlay for one axis [winner] to [guides] / [spacing]. [alongX] true means the X
 * axis won, drawing a vertical guide (or horizontal spacing bars); false means the Y axis won.
 * [snapped] is [moved] after both axes' deltas are applied, so cross-axis spans use the final
 * position.
 */
internal fun buildAxisOverlay(
    winner: AxisWinner,
    alongX: Boolean,
    snapped: SnapBox,
    guides: MutableList<AnchorGuide>,
    spacing: MutableList<SpacingBar>,
) {
    val plan = winner.plan
    if (plan != null) {
        if (alongX) {
            val y = snapped.centerY
            plan.lowBoundary?.let { spacing += SpacingBar(SnapLine(it, y, snapped.x, y), snapped.x - it) }
            plan.highBoundary?.let { spacing += SpacingBar(SnapLine(snapped.right, y, it, y), it - snapped.right) }
            if (plan.refLo != null && plan.refHi != null) spacing += SpacingBar(SnapLine(plan.refLo, y, plan.refHi, y), plan.refHi - plan.refLo)
        } else {
            val x = snapped.centerX
            plan.lowBoundary?.let { spacing += SpacingBar(SnapLine(x, it, x, snapped.y), snapped.y - it) }
            plan.highBoundary?.let { spacing += SpacingBar(SnapLine(x, snapped.bottom, x, it), it - snapped.bottom) }
            if (plan.refLo != null && plan.refHi != null) spacing += SpacingBar(SnapLine(x, plan.refLo, x, plan.refHi), plan.refHi - plan.refLo)
        }
        return
    }
    val crossLo = winner.crossLo ?: return
    val crossHi = winner.crossHi ?: return
    val line = if (alongX) {
        SnapLine(winner.target, minOf(snapped.y, crossLo), winner.target, maxOf(snapped.bottom, crossHi))
    } else {
        SnapLine(minOf(snapped.x, crossLo), winner.target, maxOf(snapped.right, crossHi), winner.target)
    }
    guides += AnchorGuide(line, winner.kind, winner.label)
}
