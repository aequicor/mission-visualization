package io.aequicor.visualization.subsystems.diagrams.layout

/** Rounds of (median down → transpose → median up → transpose), keep-best across them. */
private const val MAX_MEDIAN_ROUNDS = 8

/** Defensive cap on full transpose passes; every pass must strictly reduce crossings. */
private const val MAX_TRANSPOSE_PASSES = 50

/**
 * Crossing minimization over a seeded [LayeredGraph] (GKNV/Sugiyama): weighted-median
 * sweeps reorder each layer toward the median position of its neighbors, an
 * adjacent-exchange transpose then swaps neighbors while the exact crossing count
 * strictly falls, and the best order across all rounds wins ([totalCrossings], with a
 * lexicographic tie-break so equal-quality rounds pick one canonical order).
 *
 * Deterministic: stable sorts, strict-improvement swaps only, no randomness. Dummies
 * participate exactly like real slots.
 */
internal fun minimizeCrossings(layered: LayeredGraph) {
    if (layered.layers.size <= 1) return
    val indexOf = mutableMapOf<String, Int>()
    fun reindex(layerIndex: Int) {
        layered.layers[layerIndex].forEachIndexed { index, slot -> indexOf[slot.key] = index }
    }
    for (layerIndex in layered.layers.indices) reindex(layerIndex)

    // The seed is transposed before it becomes the baseline candidate: keep-best may
    // restore any candidate, and every restorable order must already be locally optimal
    // under adjacent swaps (round candidates are captured post-transpose anyway).
    transpose(layered, indexOf)
    var best = snapshot(layered)
    var bestCrossings = totalCrossings(layered)
    var previousRound = best

    for (round in 0 until MAX_MEDIAN_ROUNDS) {
        if (bestCrossings == 0L) break
        medianSweep(layered, indexOf, layered.up, 1 until layered.layers.size, ::reindex)
        transpose(layered, indexOf)
        medianSweep(layered, indexOf, layered.down, layered.layers.size - 2 downTo 0, ::reindex)
        transpose(layered, indexOf)

        val order = snapshot(layered)
        val crossings = totalCrossings(layered)
        if (crossings < bestCrossings ||
            (crossings == bestCrossings && lexicographicallyBefore(order, best))
        ) {
            best = order
            bestCrossings = crossings
        }
        if (order == previousRound) break
        previousRound = order
    }

    restore(layered, best)
}

private fun snapshot(layered: LayeredGraph): List<List<String>> =
    layered.layers.map { layer -> layer.map { it.key } }

private fun restore(layered: LayeredGraph, saved: List<List<String>>) {
    layered.layers.forEachIndexed { layerIndex, layer ->
        val rank = saved[layerIndex].withIndex().associate { (index, key) -> key to index }
        layer.sortBy { rank.getValue(it.key) }
    }
}

private fun lexicographicallyBefore(left: List<List<String>>, right: List<List<String>>): Boolean {
    for (layerIndex in left.indices) {
        val a = left[layerIndex]
        val b = right[layerIndex]
        for (position in a.indices) {
            val comparison = a[position].compareTo(b[position])
            if (comparison != 0) return comparison < 0
        }
    }
    return false
}

/**
 * One median sweep: every layer in [layerOrder] reorders by the GKNV weighted median of
 * its neighbor positions on the [neighborsOf] side; slots without neighbors keep their
 * position (classic median rule). Stable sort keeps ties deterministic.
 */
private fun medianSweep(
    layered: LayeredGraph,
    indexOf: MutableMap<String, Int>,
    neighborsOf: Map<String, List<String>>,
    layerOrder: IntProgression,
    reindex: (Int) -> Unit,
) {
    for (layerIndex in layerOrder) {
        val layer = layered.layers[layerIndex]
        val medians = layer.map { slot ->
            medianOf(neighborsOf[slot.key].orEmpty().map { indexOf.getValue(it) })
        }
        val movable = layer.indices.filter { medians[it] >= 0.0 }.sortedBy { medians[it] }
        val result = arrayOfNulls<LayerSlot>(layer.size)
        for (position in layer.indices) {
            if (medians[position] < 0.0) result[position] = layer[position]
        }
        var nextMovable = 0
        for (position in layer.indices) {
            if (result[position] == null) {
                result[position] = layer[movable[nextMovable]]
                nextMovable++
            }
        }
        layer.clear()
        result.forEach { layer += it!! }
        reindex(layerIndex)
    }
}

/**
 * GKNV weighted median of sorted neighbor [positions]: plain median for odd counts, the
 * mean for pairs, and for larger even counts the value interpolated toward the side
 * where neighbors pack more densely. `-1` marks "no neighbors — keep the slot put".
 */
private fun medianOf(positions: List<Int>): Double {
    if (positions.isEmpty()) return -1.0
    val sorted = positions.sorted()
    val middle = sorted.size / 2
    return when {
        sorted.size % 2 == 1 -> sorted[middle].toDouble()
        sorted.size == 2 -> (sorted[0] + sorted[1]) / 2.0
        else -> {
            val left = (sorted[middle - 1] - sorted[0]).toDouble()
            val right = (sorted[sorted.size - 1] - sorted[middle]).toDouble()
            if (left + right == 0.0) {
                (sorted[middle - 1] + sorted[middle]) / 2.0
            } else {
                (sorted[middle - 1] * right + sorted[middle] * left) / (left + right)
            }
        }
    }
}

/**
 * Adjacent-exchange transpose: swaps neighboring slots while the exact local crossing
 * count (against both adjacent layers) strictly falls. Strictness guarantees
 * termination — the global crossing count decreases with every swap.
 */
private fun transpose(layered: LayeredGraph, indexOf: MutableMap<String, Int>) {
    var improved = true
    var passes = 0
    while (improved && passes < MAX_TRANSPOSE_PASSES) {
        improved = false
        passes++
        for (layer in layered.layers) {
            for (position in 0 until layer.size - 1) {
                val left = layer[position]
                val right = layer[position + 1]
                val current = pairCrossings(layered, indexOf, left, right)
                val swapped = pairCrossings(layered, indexOf, right, left)
                if (swapped < current) {
                    layer[position] = right
                    layer[position + 1] = left
                    indexOf[right.key] = position
                    indexOf[left.key] = position + 1
                    improved = true
                }
            }
        }
    }
}

/** Crossings among the two slots' own edges when [left] sits immediately left of [right]. */
private fun pairCrossings(
    layered: LayeredGraph,
    indexOf: Map<String, Int>,
    left: LayerSlot,
    right: LayerSlot,
): Int {
    fun count(leftNeighbors: List<String>, rightNeighbors: List<String>): Int {
        var crossings = 0
        for (a in leftNeighbors) {
            val leftPosition = indexOf.getValue(a)
            for (b in rightNeighbors) {
                if (leftPosition > indexOf.getValue(b)) crossings++
            }
        }
        return crossings
    }
    return count(layered.up[left.key].orEmpty(), layered.up[right.key].orEmpty()) +
        count(layered.down[left.key].orEmpty(), layered.down[right.key].orEmpty())
}
