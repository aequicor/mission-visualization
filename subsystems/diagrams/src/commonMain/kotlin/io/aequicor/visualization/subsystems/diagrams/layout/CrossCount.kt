package io.aequicor.visualization.subsystems.diagrams.layout

/**
 * Exact edge-crossing count of the whole layered graph under its current layer orders:
 * the sum of bilayer counts over every adjacent layer pair. After dummy insertion every
 * down link connects adjacent layers, so this sees long edges too.
 */
internal fun totalCrossings(layered: LayeredGraph): Long {
    var total = 0L
    for (layerIndex in 0 until layered.layers.size - 1) {
        val lower = layered.layers[layerIndex + 1]
        val lowerIndex = HashMap<String, Int>(lower.size * 2)
        lower.forEachIndexed { index, slot -> lowerIndex[slot.key] = index }
        val pairs = mutableListOf<Pair<Int, Int>>()
        layered.layers[layerIndex].forEachIndexed { upperPosition, slot ->
            for (neighbor in layered.down[slot.key].orEmpty()) {
                pairs += upperPosition to lowerIndex.getValue(neighbor)
            }
        }
        total += countBilayerCrossings(pairs, lower.size)
    }
    return total
}

/**
 * Crossings among edges of one layer pair, each edge given as
 * `(upper position, lower position)`: two edges cross iff their endpoint orders invert
 * (shared endpoints never cross). Barth–Mutzel–Jünger: sort by upper (then lower)
 * position and count inversions of the lower-position sequence with a binary indexed
 * tree accumulator — O(E log V) instead of the naive O(E²).
 */
internal fun countBilayerCrossings(pairs: List<Pair<Int, Int>>, lowerSize: Int): Long {
    if (pairs.size < 2 || lowerSize == 0) return 0
    val sorted = pairs.sortedWith(compareBy({ it.first }, { it.second }))
    val tree = IntArray(lowerSize + 1)
    fun add(index: Int) {
        var i = index + 1
        while (i <= lowerSize) {
            tree[i]++
            i += i and (-i)
        }
    }
    fun countAtMost(index: Int): Int {
        var i = index + 1
        var sum = 0
        while (i > 0) {
            sum += tree[i]
            i -= i and (-i)
        }
        return sum
    }
    var crossings = 0L
    var inserted = 0
    for ((_, lowerPosition) in sorted) {
        // Every already-inserted edge with a strictly larger lower position came from a
        // not-larger upper position (sort order), i.e. crosses this edge. Same-upper
        // edges arrive in ascending lower order and are never counted.
        crossings += inserted - countAtMost(lowerPosition)
        add(lowerPosition)
        inserted++
    }
    return crossings
}
