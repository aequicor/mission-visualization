package io.aequicor.visualization.subsystems.diagrams.routing

import io.aequicor.visualization.subsystems.diagrams.geometry.GEOMETRY_EPSILON
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodeSide
import io.aequicor.visualization.subsystems.diagrams.path.DiagramPoint
import io.aequicor.visualization.subsystems.diagrams.path.DiagramRect
import kotlin.math.abs

/** Axis-aligned step direction on the routing grid. */
internal enum class GridDirection(val dx: Int, val dy: Int) {
    RIGHT(1, 0),
    LEFT(-1, 0),
    DOWN(0, 1),
    UP(0, -1),
    ;

    val isHorizontal: Boolean get() = dy == 0

    val opposite: GridDirection
        get() = when (this) {
            RIGHT -> LEFT
            LEFT -> RIGHT
            DOWN -> UP
            UP -> DOWN
        }
}

internal fun DiagramNodeSide.toGridDirection(): GridDirection = when (this) {
    DiagramNodeSide.TOP -> GridDirection.UP
    DiagramNodeSide.RIGHT -> GridDirection.RIGHT
    DiagramNodeSide.BOTTOM -> GridDirection.DOWN
    DiagramNodeSide.LEFT -> GridDirection.LEFT
}

/**
 * Deterministic obstacle-aware Manhattan route between [start] and [goal].
 *
 * Builds a sparse visibility grid from the inflated obstacle rectangle boundaries plus
 * the endpoint coordinates, then runs A* with a per-turn penalty (states carry the
 * incoming direction). Segments may travel **along** inflated obstacle boundaries but
 * never through their strict interior. Returns `null` when no route exists.
 */
internal fun orthogonalGridRoute(
    start: DiagramPoint,
    startDirection: GridDirection?,
    goal: DiagramPoint,
    obstacles: List<DiagramRect>,
    turnPenalty: Double,
): List<DiagramPoint>? {
    if (start.x == goal.x && start.y == goal.y) return listOf(start)
    val xs = gridCoordinates(
        buildList {
            add(start.x)
            add(goal.x)
            add((start.x + goal.x) / 2.0)
            obstacles.forEach {
                add(it.left)
                add(it.right)
            }
        },
    )
    val ys = gridCoordinates(
        buildList {
            add(start.y)
            add(goal.y)
            add((start.y + goal.y) / 2.0)
            obstacles.forEach {
                add(it.top)
                add(it.bottom)
            }
        },
    )
    val startI = coordinateIndex(xs, start.x)
    val startJ = coordinateIndex(ys, start.y)
    val goalI = coordinateIndex(xs, goal.x)
    val goalJ = coordinateIndex(ys, goal.y)

    val ny = ys.size
    val directions = GridDirection.entries
    val stateCount = xs.size * ny * directions.size
    fun stateId(i: Int, j: Int, dir: GridDirection): Int =
        (i * ny + j) * directions.size + dir.ordinal

    val bestCost = DoubleArray(stateCount) { Double.MAX_VALUE }
    val parent = IntArray(stateCount) { -1 }
    val open = ArrayDeque<Int>()

    val seedDirections = startDirection?.let { listOf(it) } ?: directions
    for (dir in seedDirections) {
        val id = stateId(startI, startJ, dir)
        bestCost[id] = 0.0
        open += id
    }

    fun heuristic(i: Int, j: Int): Double = abs(xs[i] - xs[goalI]) + abs(ys[j] - ys[goalJ])

    fun horizontalBlocked(j: Int, iFrom: Int, iTo: Int): Boolean {
        val y = ys[j]
        val x1 = minOf(xs[iFrom], xs[iTo])
        val x2 = maxOf(xs[iFrom], xs[iTo])
        return obstacles.any { rect ->
            y > rect.top + GEOMETRY_EPSILON && y < rect.bottom - GEOMETRY_EPSILON &&
                x2 > rect.left + GEOMETRY_EPSILON && x1 < rect.right - GEOMETRY_EPSILON
        }
    }

    fun verticalBlocked(i: Int, jFrom: Int, jTo: Int): Boolean {
        val x = xs[i]
        val y1 = minOf(ys[jFrom], ys[jTo])
        val y2 = maxOf(ys[jFrom], ys[jTo])
        return obstacles.any { rect ->
            x > rect.left + GEOMETRY_EPSILON && x < rect.right - GEOMETRY_EPSILON &&
                y2 > rect.top + GEOMETRY_EPSILON && y1 < rect.bottom - GEOMETRY_EPSILON
        }
    }

    while (open.isNotEmpty()) {
        var bestIndex = 0
        var bestF = Double.MAX_VALUE
        var bestG = Double.MAX_VALUE
        var bestId = Int.MAX_VALUE
        for (index in open.indices) {
            val id = open[index]
            val g = bestCost[id]
            val cell = id / directions.size
            val f = g + heuristic(cell / ny, cell % ny)
            val better = f < bestF - GEOMETRY_EPSILON ||
                (abs(f - bestF) <= GEOMETRY_EPSILON && g < bestG - GEOMETRY_EPSILON) ||
                (abs(f - bestF) <= GEOMETRY_EPSILON && abs(g - bestG) <= GEOMETRY_EPSILON && id < bestId)
            if (better) {
                bestIndex = index
                bestF = f
                bestG = g
                bestId = id
            }
        }
        val currentId = open.removeAt(bestIndex)
        val currentG = bestCost[currentId]
        val currentDir = directions[currentId % directions.size]
        val cell = currentId / directions.size
        val i = cell / ny
        val j = cell % ny

        if (i == goalI && j == goalJ) {
            return reconstructRoute(currentId, parent, xs, ys, directions.size, ny)
        }

        for (dir in directions) {
            if (dir == currentDir.opposite) continue
            val ni = i + dir.dx
            val nj = j + dir.dy
            if (ni !in xs.indices || nj !in ys.indices) continue
            val blocked = if (dir.isHorizontal) {
                horizontalBlocked(j, i, ni)
            } else {
                verticalBlocked(i, j, nj)
            }
            if (blocked) continue
            val stepLength = if (dir.isHorizontal) abs(xs[ni] - xs[i]) else abs(ys[nj] - ys[j])
            val turnCost = if (dir != currentDir) turnPenalty else 0.0
            val nextG = currentG + stepLength + turnCost
            val nextId = stateId(ni, nj, dir)
            if (nextG < bestCost[nextId] - GEOMETRY_EPSILON) {
                bestCost[nextId] = nextG
                parent[nextId] = currentId
                if (nextId !in open) open += nextId
            }
        }
    }
    return null
}

private fun reconstructRoute(
    goalState: Int,
    parent: IntArray,
    xs: DoubleArray,
    ys: DoubleArray,
    directionCount: Int,
    ny: Int,
): List<DiagramPoint> {
    val reversed = mutableListOf<DiagramPoint>()
    var state = goalState
    while (state != -1) {
        val cell = state / directionCount
        val point = DiagramPoint(xs[cell / ny], ys[cell % ny])
        if (reversed.isEmpty() || reversed.last() != point) reversed += point
        state = parent[state]
    }
    return reversed.reversed()
}

/** Sorted, epsilon-deduplicated grid coordinates. */
private fun gridCoordinates(raw: List<Double>): DoubleArray {
    val sorted = raw.sorted()
    val result = mutableListOf<Double>()
    for (value in sorted) {
        if (result.isEmpty() || value - result.last() > GEOMETRY_EPSILON) result += value
    }
    return result.toDoubleArray()
}

private fun coordinateIndex(coordinates: DoubleArray, value: Double): Int {
    var best = 0
    var bestDistance = Double.MAX_VALUE
    for (index in coordinates.indices) {
        val distance = abs(coordinates[index] - value)
        if (distance < bestDistance) {
            bestDistance = distance
            best = index
        }
    }
    return best
}
