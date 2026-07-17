package io.aequicor.visualization.subsystems.diagrams.layout

import kotlin.math.abs

/**
 * Deterministic pairwise box-overlap removal (the VPSC x/y projection of "Fast Node
 * Overlap Removal"): boxes are pushed apart along one axis at a time until every pair
 * is separated by at least [gap] on some axis.
 *
 * Two stages, both with fixed operation order (index-sorted ties), so identical input
 * always produces identical output:
 * 1. Up to [MAX_ROUNDS] rounds of Gauss–Seidel relaxation. Each round separates the
 *    pairs whose **cheaper** axis is x along x, then the pairs whose cheaper axis is y
 *    along y (the smaller push), splitting the correction between both boxes — overlaps
 *    dissolve with minimal drift from the desired layout, and a pair whose x-overlap is
 *    the shallow one is never scattered vertically.
 * 2. A final monotone sweep along y: boxes sorted by center, every pair still
 *    overlapping across x gets the later box pushed down to clearance. Pushes only
 *    ever increase later coordinates, so previously fixed pairs stay fixed — after the
 *    sweep **zero padded overlaps remain** (any residual overlap would overlap across
 *    x, which the sweep has just separated vertically).
 *
 * Centers are mutated in place; extents are read-only.
 */
internal class OverlapBoxes(
    val centersX: DoubleArray,
    val centersY: DoubleArray,
    val halfWidths: DoubleArray,
    val halfHeights: DoubleArray,
) {
    val size: Int get() = centersX.size

    init {
        require(centersY.size == size && halfWidths.size == size && halfHeights.size == size) {
            "all box arrays must have the same size"
        }
    }
}

private const val MAX_ROUNDS = 4
private const val RELAXATION_PASSES = 16
private const val EPSILON = 1e-9

/** Padded overlap depth along x for the pair; positive means the padded boxes overlap. */
private fun OverlapBoxes.overlapX(first: Int, second: Int, gap: Double): Double =
    halfWidths[first] + halfWidths[second] + gap - abs(centersX[first] - centersX[second])

private fun OverlapBoxes.overlapY(first: Int, second: Int, gap: Double): Double =
    halfHeights[first] + halfHeights[second] + gap - abs(centersY[first] - centersY[second])

/**
 * Pushes boxes apart until every pair is separated by at least [gap] along x or y.
 * See the class KDoc for the algorithm and the zero-overlap guarantee.
 */
internal fun removeBoxOverlaps(boxes: OverlapBoxes, gap: Double) {
    if (boxes.size < 2) return
    repeat(MAX_ROUNDS) {
        val pairs = overlappingPairs(boxes, gap)
        if (pairs.isEmpty()) return
        // Separate x-cheaper pairs along x, then y-cheaper pairs along y. A pair is only
        // ever pushed along its shallower axis, so a barely-overlapping-in-x pair is not
        // scattered vertically by the y sub-pass.
        relaxAxis(boxes, gap, pairs.filter { (first, second) ->
            boxes.overlapX(first, second, gap) <= boxes.overlapY(first, second, gap)
        }, horizontal = true)
        relaxAxis(boxes, gap, overlappingPairs(boxes, gap).filter { (first, second) ->
            boxes.overlapY(first, second, gap) < boxes.overlapX(first, second, gap)
        }, horizontal = false)
    }
    sweepApartVertically(boxes, gap)
}

/** All padded-overlapping index pairs, first < second, in index order. */
private fun overlappingPairs(boxes: OverlapBoxes, gap: Double): List<Pair<Int, Int>> = buildList {
    for (first in 0 until boxes.size) {
        for (second in first + 1 until boxes.size) {
            if (boxes.overlapX(first, second, gap) > EPSILON &&
                boxes.overlapY(first, second, gap) > EPSILON
            ) {
                add(first to second)
            }
        }
    }
}

/**
 * Gauss–Seidel passes over [pairs] along one axis: each violated pair is separated to
 * exact clearance, the correction split evenly, lower-coordinate box (ties by index)
 * moving toward negative.
 */
private fun relaxAxis(
    boxes: OverlapBoxes,
    gap: Double,
    pairs: List<Pair<Int, Int>>,
    horizontal: Boolean,
) {
    if (pairs.isEmpty()) return
    val centers = if (horizontal) boxes.centersX else boxes.centersY
    val halves = if (horizontal) boxes.halfWidths else boxes.halfHeights
    val crossOverlap = { first: Int, second: Int ->
        if (horizontal) boxes.overlapY(first, second, gap) else boxes.overlapX(first, second, gap)
    }
    repeat(RELAXATION_PASSES) {
        var moved = false
        for ((first, second) in pairs) {
            if (crossOverlap(first, second) <= EPSILON) continue
            val required = halves[first] + halves[second] + gap
            val deficit = required - abs(centers[first] - centers[second])
            if (deficit <= EPSILON) continue
            val (low, high) = if (
                centers[first] < centers[second] ||
                (centers[first] == centers[second] && first < second)
            ) {
                first to second
            } else {
                second to first
            }
            centers[low] -= deficit / 2.0
            centers[high] += deficit / 2.0
            moved = true
        }
        if (!moved) return
    }
}

/**
 * Monotone vertical sweep guaranteeing feasibility: boxes in (centerY, index) order;
 * every pair overlapping across x (padded) becomes a separation constraint from the
 * earlier to the later box, applied as `y[later] = max(y[later], y[earlier] + clearance)`.
 * Earlier boxes are final when a later box is processed, so all constraints hold at exit.
 */
private fun sweepApartVertically(boxes: OverlapBoxes, gap: Double) {
    val order = (0 until boxes.size).sortedWith(
        compareBy({ boxes.centersY[it] }, { it }),
    )
    for (position in 1 until order.size) {
        val later = order[position]
        var minCenter = boxes.centersY[later]
        for (earlierPosition in 0 until position) {
            val earlier = order[earlierPosition]
            if (boxes.overlapX(earlier, later, gap) <= EPSILON) continue
            val clearance = boxes.halfHeights[earlier] + boxes.halfHeights[later] + gap
            minCenter = maxOf(minCenter, boxes.centersY[earlier] + clearance)
        }
        boxes.centersY[later] = minCenter
    }
}
