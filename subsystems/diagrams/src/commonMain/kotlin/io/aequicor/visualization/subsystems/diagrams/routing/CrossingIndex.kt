package io.aequicor.visualization.subsystems.diagrams.routing

import io.aequicor.visualization.subsystems.diagrams.geometry.GEOMETRY_EPSILON
import io.aequicor.visualization.subsystems.diagrams.path.DiagramPoint

/**
 * Axis-aligned segments of already-routed edges, indexed for fast "how many lines would
 * this step cross" queries during the grid A* ([orthogonalGridRoute]). Only proper
 * perpendicular crossings count: a T-junction (an endpoint touching another line) is an
 * arrival tap, and collinear co-runs are the nudge pass's domain — neither is a crossing.
 */
internal class RouteCrossingIndex private constructor(
    private val horizontalY: DoubleArray,
    private val horizontalX1: DoubleArray,
    private val horizontalX2: DoubleArray,
    private val verticalX: DoubleArray,
    private val verticalY1: DoubleArray,
    private val verticalY2: DoubleArray,
) {

    /** Crossings a horizontal run at [y] over `[x1, x2]` would make with indexed vertical lines. */
    fun crossingsOfHorizontal(y: Double, x1: Double, x2: Double): Int =
        countInRange(verticalX, x1, x2) { index ->
            y > verticalY1[index] + GEOMETRY_EPSILON && y < verticalY2[index] - GEOMETRY_EPSILON
        }

    /** Crossings a vertical run at [x] over `[y1, y2]` would make with indexed horizontal lines. */
    fun crossingsOfVertical(x: Double, y1: Double, y2: Double): Int =
        countInRange(horizontalY, y1, y2) { index ->
            x > horizontalX1[index] + GEOMETRY_EPSILON && x < horizontalX2[index] - GEOMETRY_EPSILON
        }

    /** Entries of sorted [keys] strictly inside `(low, high)` that also pass [accepts]. */
    private inline fun countInRange(
        keys: DoubleArray,
        low: Double,
        high: Double,
        accepts: (Int) -> Boolean,
    ): Int {
        var index = lowerBound(keys, low + GEOMETRY_EPSILON)
        var count = 0
        while (index < keys.size && keys[index] < high - GEOMETRY_EPSILON) {
            if (accepts(index)) count++
            index++
        }
        return count
    }

    /** First index whose key is >= [value] (binary search over the sorted keys). */
    private fun lowerBound(keys: DoubleArray, value: Double): Int {
        var low = 0
        var high = keys.size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (keys[mid] < value) low = mid + 1 else high = mid
        }
        return low
    }

    companion object {
        fun of(routes: List<List<DiagramPoint>>): RouteCrossingIndex {
            data class Segment(val at: Double, val from: Double, val to: Double)

            val horizontals = mutableListOf<Segment>()
            val verticals = mutableListOf<Segment>()
            for (route in routes) {
                for ((a, b) in route.zipWithNext()) {
                    when {
                        kotlin.math.abs(a.y - b.y) <= GEOMETRY_EPSILON &&
                            kotlin.math.abs(a.x - b.x) > GEOMETRY_EPSILON ->
                            horizontals += Segment(a.y, minOf(a.x, b.x), maxOf(a.x, b.x))
                        kotlin.math.abs(a.x - b.x) <= GEOMETRY_EPSILON &&
                            kotlin.math.abs(a.y - b.y) > GEOMETRY_EPSILON ->
                            verticals += Segment(a.x, minOf(a.y, b.y), maxOf(a.y, b.y))
                    }
                }
            }
            horizontals.sortBy { it.at }
            verticals.sortBy { it.at }
            return RouteCrossingIndex(
                horizontalY = DoubleArray(horizontals.size) { horizontals[it].at },
                horizontalX1 = DoubleArray(horizontals.size) { horizontals[it].from },
                horizontalX2 = DoubleArray(horizontals.size) { horizontals[it].to },
                verticalX = DoubleArray(verticals.size) { verticals[it].at },
                verticalY1 = DoubleArray(verticals.size) { verticals[it].from },
                verticalY2 = DoubleArray(verticals.size) { verticals[it].to },
            )
        }
    }
}
