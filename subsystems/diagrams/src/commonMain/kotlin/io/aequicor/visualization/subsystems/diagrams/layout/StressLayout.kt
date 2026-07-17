package io.aequicor.visualization.subsystems.diagrams.layout

import io.aequicor.visualization.subsystems.diagrams.model.DiagramGraph
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNode
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodeId
import io.aequicor.visualization.subsystems.diagrams.path.DiagramPoint
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Stress-majorization (SMACOF) auto-layout — the FORCE engine for dense/cyclic ER and
 * undirected many-to-many graphs, where a layered layout would invent a false hierarchy.
 *
 * Pipeline per connected component: BFS graph-theoretic distances scaled to an ideal
 * edge length → classical-MDS initialization (double-centered distance matrix, top-2
 * eigenvectors via index-seeded power iteration — no randomness) → SMACOF majorization
 * with a fixed iteration cap → PCA alignment of the principal axis to horizontal (ER
 * reads left-to-right; wide beats tall on screens) → pairwise overlap removal
 * ([removeBoxOverlaps], [DiagramLayoutConfig.nodeGap] clearance) → axis-union snap
 * (near-collinear rows/columns collapse onto shared axes) → integer grid snap (keeps
 * written CNL coordinates bit-stable across JVM/wasm FP differences). Components are
 * then shelf-packed like the layered engine.
 *
 * Within a single component [DiagramLayoutConfig.direction] has no effect — an
 * undirected layout has no layer flow, and the PCA step always lands the widest spread
 * horizontally. It only steers how **multiple** components shelf-pack (the main axis they
 * flow along), matching the other engines.
 *
 * Only node positions change; edges are untouched (the router re-routes them).
 * Deterministic: identical input always yields the identical output.
 */
fun stressLayout(
    graph: DiagramGraph,
    config: DiagramLayoutConfig = DiagramLayoutConfig.Default,
): DiagramGraph = layoutHierarchically(graph, config) { _, nodesById, adjacency ->
    stressPositions(nodesById, adjacency, config)
}

/** Scope-local stress positions: top-left corners in local coordinates starting at (0,0). */
internal fun stressPositions(
    nodesById: Map<DiagramNodeId, DiagramNode>,
    adjacency: ScopeAdjacency,
    config: DiagramLayoutConfig,
): Map<DiagramNodeId, DiagramPoint> {
    if (adjacency.members.isEmpty()) return emptyMap()
    val components = adjacency.connectedComponents()
    if (components.size == 1) {
        return stressComponentPositions(nodesById, components.single(), config)
    }
    val blocks = components.map { component ->
        val positions = stressComponentPositions(nodesById, component, config)
        var right = 0.0
        var bottom = 0.0
        for (id in component.members) {
            val node = nodesById.getValue(id)
            val point = positions.getValue(id)
            right = maxOf(right, point.x + node.width)
            bottom = maxOf(bottom, point.y + node.height)
        }
        val horizontalFlow = config.direction == LayoutDirection.LEFT_RIGHT
        ComponentBlock(
            positions = positions,
            mainExtent = if (horizontalFlow) right else bottom,
            crossExtent = if (horizontalFlow) bottom else right,
        )
    }
    return packComponentBlocks(blocks, config)
}

private const val MDS_POWER_ITERATIONS = 64
private const val SMACOF_MAX_ITERATIONS = 128
private const val SMACOF_RELATIVE_TOLERANCE = 1e-7
private const val DISTANCE_EPSILON = 1e-9

/** One connected component: local top-left positions normalized to (0,0), integer grid. */
private fun stressComponentPositions(
    nodesById: Map<DiagramNodeId, DiagramNode>,
    component: ScopeAdjacency,
    config: DiagramLayoutConfig,
): Map<DiagramNodeId, DiagramPoint> {
    val members = component.members
    val count = members.size
    if (count == 1) return mapOf(members.single() to DiagramPoint.Zero)

    val indexOf = members.withIndex().associate { (index, id) -> id to index }
    val neighbors = Array(count) { mutableListOf<Int>() }
    for (link in component.links) {
        val from = indexOf.getValue(link.from)
        val to = indexOf.getValue(link.to)
        if (to !in neighbors[from]) neighbors[from] += to
        if (from !in neighbors[to]) neighbors[to] += from
    }

    // Graph-theoretic target distances: BFS hops scaled by an ideal edge length derived
    // from the average node size, so neighbors sit roughly one node-plus-gap apart.
    val idealEdge = config.layerGap +
        members.sumOf { (nodesById.getValue(it).width + nodesById.getValue(it).height) / 2.0 } / count
    val targets = Array(count) { source -> DoubleArray(count) }
    for (source in 0 until count) {
        val hops = IntArray(count) { -1 }
        hops[source] = 0
        val queue = ArrayDeque<Int>()
        queue += source
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            for (next in neighbors[current]) {
                if (hops[next] == -1) {
                    hops[next] = hops[current] + 1
                    queue += next
                }
            }
        }
        for (other in 0 until count) targets[source][other] = hops[other] * idealEdge
    }

    val (xs, ys) = classicalMdsInit(targets)
    runSmacof(xs, ys, targets)
    alignPrincipalAxisHorizontally(xs, ys)

    val boxes = OverlapBoxes(
        centersX = xs,
        centersY = ys,
        halfWidths = DoubleArray(count) { nodesById.getValue(members[it]).width / 2.0 },
        halfHeights = DoubleArray(count) { nodesById.getValue(members[it]).height / 2.0 },
    )
    removeBoxOverlaps(boxes, config.nodeGap)
    snapAxisUnions(xs, tolerance = config.nodeGap / 2.0, maxSpread = config.nodeGap)
    snapAxisUnions(ys, tolerance = config.nodeGap / 2.0, maxSpread = config.nodeGap)
    removeBoxOverlaps(boxes, config.nodeGap)

    var minX = Double.MAX_VALUE
    var minY = Double.MAX_VALUE
    for (index in 0 until count) {
        minX = minOf(minX, xs[index] - boxes.halfWidths[index])
        minY = minOf(minY, ys[index] - boxes.halfHeights[index])
    }
    return members.withIndex().associate { (index, id) ->
        id to DiagramPoint(
            x = round(xs[index] - boxes.halfWidths[index] - minX),
            y = round(ys[index] - boxes.halfHeights[index] - minY),
        )
    }
}

/**
 * Deterministic pseudo-random in `[-0.5, 0.5)` from an integer seed (Knuth multiplicative
 * hash over exact integer doubles — bit-identical on every platform, unlike transcendental
 * functions seeded differently).
 */
private fun seededNoise(seed: Int): Double {
    val hashed = (seed.toLong() * 2654435761L + 1013904223L) and 0xFFFFFFFFL
    return hashed.toDouble() / 4294967296.0 - 0.5
}

/**
 * Classical MDS: double-center the squared target distances and take the top two
 * eigenvectors by power iteration (deflating the first before the second). The start
 * vectors are index-seeded, so the whole initialization is deterministic.
 */
private fun classicalMdsInit(targets: Array<DoubleArray>): Pair<DoubleArray, DoubleArray> {
    val count = targets.size
    val squared = Array(count) { row -> DoubleArray(count) { column -> targets[row][column] * targets[row][column] } }
    val rowMeans = DoubleArray(count) { row -> squared[row].sum() / count }
    val grandMean = rowMeans.sum() / count
    val centered = Array(count) { row ->
        DoubleArray(count) { column ->
            -0.5 * (squared[row][column] - rowMeans[row] - rowMeans[column] + grandMean)
        }
    }

    fun multiply(vector: DoubleArray): DoubleArray = DoubleArray(count) { row ->
        var sum = 0.0
        for (column in 0 until count) sum += centered[row][column] * vector[column]
        sum
    }

    fun normalize(vector: DoubleArray): Boolean {
        val norm = sqrt(vector.sumOf { it * it })
        if (norm < DISTANCE_EPSILON) return false
        for (index in vector.indices) vector[index] /= norm
        return true
    }

    fun powerIterate(seedOffset: Int, orthogonalTo: DoubleArray?): Pair<DoubleArray, Double> {
        var vector = DoubleArray(count) { seededNoise(it + seedOffset) }
        if (!normalize(vector)) vector = DoubleArray(count) { if (it == 0) 1.0 else 0.0 }
        repeat(MDS_POWER_ITERATIONS) {
            val next = multiply(vector)
            if (orthogonalTo != null) {
                var dot = 0.0
                for (index in next.indices) dot += next[index] * orthogonalTo[index]
                for (index in next.indices) next[index] -= dot * orthogonalTo[index]
            }
            if (!normalize(next)) return vector to 0.0
            vector = next
        }
        val image = multiply(vector)
        var eigenvalue = 0.0
        for (index in vector.indices) eigenvalue += vector[index] * image[index]
        return vector to eigenvalue
    }

    val (first, firstValue) = powerIterate(seedOffset = 1, orthogonalTo = null)
    val (second, secondValue) = powerIterate(seedOffset = 7919, orthogonalTo = first)
    val firstScale = sqrt(maxOf(firstValue, 0.0))
    val secondScale = sqrt(maxOf(secondValue, 0.0))
    return Pair(
        DoubleArray(count) { first[it] * firstScale },
        DoubleArray(count) { second[it] * secondScale },
    )
}

/**
 * SMACOF majorization with weights `1/d²`: each iteration applies the Guttman transform;
 * stops after [SMACOF_MAX_ITERATIONS] or when the stress improvement falls below
 * [SMACOF_RELATIVE_TOLERANCE]. Coincident points separate along an index-seeded
 * deterministic direction.
 */
private fun runSmacof(xs: DoubleArray, ys: DoubleArray, targets: Array<DoubleArray>) {
    val count = xs.size

    fun stress(): Double {
        var sum = 0.0
        for (row in 0 until count) {
            for (column in row + 1 until count) {
                val target = targets[row][column]
                if (target < DISTANCE_EPSILON) continue
                val dx = xs[row] - xs[column]
                val dy = ys[row] - ys[column]
                val distance = sqrt(dx * dx + dy * dy)
                val residual = distance - target
                sum += (residual * residual) / (target * target)
            }
        }
        return sum
    }

    var previousStress = stress()
    val nextXs = DoubleArray(count)
    val nextYs = DoubleArray(count)
    repeat(SMACOF_MAX_ITERATIONS) {
        for (row in 0 until count) {
            var weightSum = 0.0
            var sumX = 0.0
            var sumY = 0.0
            for (column in 0 until count) {
                if (column == row) continue
                val target = targets[row][column]
                if (target < DISTANCE_EPSILON) continue
                val weight = 1.0 / (target * target)
                val dx = xs[row] - xs[column]
                val dy = ys[row] - ys[column]
                val distance = sqrt(dx * dx + dy * dy)
                val (unitX, unitY) = if (distance < DISTANCE_EPSILON) {
                    val noiseX = seededNoise(row * 31 + column)
                    val noiseY = seededNoise(row * 57 + column + 3)
                    val norm = sqrt(noiseX * noiseX + noiseY * noiseY)
                    if (norm < DISTANCE_EPSILON) 1.0 to 0.0 else noiseX / norm to noiseY / norm
                } else {
                    dx / distance to dy / distance
                }
                weightSum += weight
                sumX += weight * (xs[column] + target * unitX)
                sumY += weight * (ys[column] + target * unitY)
            }
            if (weightSum > 0.0) {
                nextXs[row] = sumX / weightSum
                nextYs[row] = sumY / weightSum
            } else {
                nextXs[row] = xs[row]
                nextYs[row] = ys[row]
            }
        }
        nextXs.copyInto(xs)
        nextYs.copyInto(ys)
        val currentStress = stress()
        if (previousStress - currentStress < SMACOF_RELATIVE_TOLERANCE * maxOf(previousStress, 1.0)) {
            return
        }
        previousStress = currentStress
    }
}

/**
 * Rotates the point cloud so its principal (widest-spread) axis is horizontal, then
 * flips axes into a canonical orientation: the first point (lowest member id) lands in
 * the left/top half. Rotation-invariant stress keeps its value; readers get a wide layout.
 */
private fun alignPrincipalAxisHorizontally(xs: DoubleArray, ys: DoubleArray) {
    val count = xs.size
    val meanX = xs.sum() / count
    val meanY = ys.sum() / count
    var covXx = 0.0
    var covYy = 0.0
    var covXy = 0.0
    for (index in 0 until count) {
        val dx = xs[index] - meanX
        val dy = ys[index] - meanY
        covXx += dx * dx
        covYy += dy * dy
        covXy += dx * dy
    }
    val angle = 0.5 * atan2(2.0 * covXy, covXx - covYy)
    val cosAngle = cos(angle)
    val sinAngle = sin(angle)
    for (index in 0 until count) {
        val dx = xs[index] - meanX
        val dy = ys[index] - meanY
        xs[index] = dx * cosAngle + dy * sinAngle
        ys[index] = -dx * sinAngle + dy * cosAngle
    }
    if (xs[0] > 0.0) for (index in 0 until count) xs[index] = -xs[index]
    if (ys[0] > 0.0) for (index in 0 until count) ys[index] = -ys[index]
}

/**
 * Axis-union snap: sorts the coordinates and collapses near-collinear runs (consecutive
 * gap within [tolerance], total run spread within [maxSpread]) onto their mean, so
 * almost-aligned rows/columns become exactly aligned shared axes.
 */
internal fun snapAxisUnions(values: DoubleArray, tolerance: Double, maxSpread: Double) {
    if (values.size < 2) return
    val order = values.indices.sortedWith(compareBy({ values[it] }, { it }))
    var start = 0
    while (start < order.size) {
        var end = start
        while (end + 1 < order.size &&
            values[order[end + 1]] - values[order[end]] <= tolerance &&
            values[order[end + 1]] - values[order[start]] <= maxSpread
        ) {
            end++
        }
        if (end > start) {
            var sum = 0.0
            for (position in start..end) sum += values[order[position]]
            val mean = sum / (end - start + 1)
            for (position in start..end) values[order[position]] = mean
        }
        start = end + 1
    }
}
