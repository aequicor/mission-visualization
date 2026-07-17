package io.aequicor.visualization.subsystems.diagrams.layout

/**
 * Cross-axis coordinate assignment — Brandes–Köpf (2001) with variable slot extents:
 *
 * 1. mark type-1 conflicts (non-inner segments crossing inner dummy–dummy segments,
 *    which get priority to stay straight);
 * 2. four alignment runs (up/down × left/right, realized by mirroring the layer view
 *    and reusing one canonical down-left pass): each slot aligns with a median
 *    neighbor into vertical blocks;
 * 3. per run, horizontal block compaction with exact separations
 *    `(extent(a) + extent(b))/2 + gap` between adjacent centers;
 * 4. balance: shift every run onto the narrowest one (left-biased runs by min edge,
 *    right-biased by max edge) and take the mean of the two median candidates per
 *    slot — a convex combination of feasible solutions, so separations survive;
 * 5. a per-layer [solveChainSeparation] snap guarantees exact feasibility against
 *    floating-point drift (normally the identity).
 *
 * Returns center coordinates per slot key. Deterministic: fixed run order, stable
 * sorts, no randomness. Aligned chains — a single parent over its child, and the
 * dummy chain of a long edge — share one coordinate, which is what turns layered
 * output from stairsteps into straight publication-style lines.
 */
internal fun assignCrossCenters(
    layered: LayeredGraph,
    gap: Double,
    crossExtentOf: (LayerSlot) -> Double,
): Map<String, Double> {
    val keyLayers = layered.layers.map { layer -> layer.map { it.key } }
    val extents = layered.slots.associate { it.key to crossExtentOf(it) }
    val dummies = layered.slots.filter { it.isDummy }.map { it.key }.toSet()

    // Candidate runs in fixed order: down-left, down-right, up-left, up-right.
    val candidates = mutableListOf<Map<String, Double>>()
    val rightBiased = mutableListOf<Boolean>()
    for (verticalFlip in listOf(false, true)) {
        for (horizontalMirror in listOf(false, true)) {
            val layersView = (if (verticalFlip) keyLayers.reversed() else keyLayers)
                .map { if (horizontalMirror) it.reversed() else it }
            val view = BkView(
                layers = layersView,
                ups = if (verticalFlip) layered.down else layered.up,
                extents = extents,
                dummies = dummies,
                gap = gap,
            )
            val run = runCanonicalDownLeft(view)
            candidates += if (horizontalMirror) run.mapValues { -it.value } else run
            rightBiased += horizontalMirror
        }
    }

    // Align every run onto the narrowest one, then take the mean of the two medians.
    fun minEdge(xs: Map<String, Double>) = xs.entries.minOf { it.value - extents.getValue(it.key) / 2.0 }
    fun maxEdge(xs: Map<String, Double>) = xs.entries.maxOf { it.value + extents.getValue(it.key) / 2.0 }
    val narrowest = candidates.minByOrNull { maxEdge(it) - minEdge(it) } ?: return emptyMap()
    val targetMin = minEdge(narrowest)
    val targetMax = maxEdge(narrowest)
    val shifted = candidates.mapIndexed { index, xs ->
        val offset = if (rightBiased[index]) targetMax - maxEdge(xs) else targetMin - minEdge(xs)
        xs.mapValues { it.value + offset }
    }
    val balanced = layered.slots.associate { slot ->
        val values = shifted.map { it.getValue(slot.key) }.sorted()
        slot.key to (values[1] + values[2]) / 2.0
    }

    // Exact per-layer feasibility snap (guards floating-point drift of the averaging).
    val result = mutableMapOf<String, Double>()
    for (layer in keyLayers) {
        val desired = DoubleArray(layer.size) { balanced.getValue(layer[it]) }
        val gaps = DoubleArray(maxOf(layer.size - 1, 0)) { index ->
            (extents.getValue(layer[index]) + extents.getValue(layer[index + 1])) / 2.0 + gap
        }
        val solved = solveChainSeparation(desired, gaps)
        layer.forEachIndexed { index, key -> result[key] = solved[index] }
    }
    return result
}

/** One mirrored view of the layered graph: the canonical pass always runs down-left. */
private class BkView(
    val layers: List<List<String>>,
    val ups: Map<String, List<String>>,
    val extents: Map<String, Double>,
    val dummies: Set<String>,
    val gap: Double,
) {
    val position: Map<String, Int> = buildMap {
        for (layer in layers) layer.forEachIndexed { index, key -> put(key, index) }
    }
    val layerIndex: Map<String, Int> = buildMap {
        layers.forEachIndexed { index, layer -> layer.forEach { put(it, index) } }
    }

    fun separation(left: String, right: String): Double =
        (extents.getValue(left) + extents.getValue(right)) / 2.0 + gap
}

private fun runCanonicalDownLeft(view: BkView): Map<String, Double> {
    val marked = markType1Conflicts(view)
    val (root, align) = verticalAlignment(view, marked)
    return horizontalCompaction(view, root, align)
}

/**
 * Type-1 conflicts: a non-inner segment crossing an inner (dummy–dummy) segment is
 * marked so the alignment never picks it — long-edge chains keep their priority to
 * run straight. Returns marked segments as (upper, lower) pairs.
 */
private fun markType1Conflicts(view: BkView): Set<Pair<String, String>> {
    val marked = mutableSetOf<Pair<String, String>>()
    for (upperIndex in 0 until view.layers.size - 1) {
        val lower = view.layers[upperIndex + 1]
        var reachableLeft = 0
        var scan = 0
        for (position in lower.indices) {
            val v = lower[position]
            val innerUpper = view.ups[v].orEmpty().singleOrNull()
                ?.takeIf { v in view.dummies && it in view.dummies }
            if (position == lower.lastIndex || innerUpper != null) {
                val reachableRight = innerUpper?.let { view.position.getValue(it) }
                    ?: (view.layers[upperIndex].size - 1)
                while (scan <= position) {
                    val w = lower[scan]
                    for (u in view.ups[w].orEmpty()) {
                        val uPosition = view.position.getValue(u)
                        if (uPosition < reachableLeft || uPosition > reachableRight) marked += u to w
                    }
                    scan++
                }
                reachableLeft = reachableRight
            }
        }
    }
    return marked
}

/** Median-neighbor alignment into vertical blocks (root/align pointers, BK fig. 4). */
private fun verticalAlignment(
    view: BkView,
    marked: Set<Pair<String, String>>,
): Pair<Map<String, String>, Map<String, String>> {
    val root = mutableMapOf<String, String>()
    val align = mutableMapOf<String, String>()
    for (layer in view.layers) {
        for (key in layer) {
            root[key] = key
            align[key] = key
        }
    }
    for (layer in view.layers) {
        var lastAlignedPosition = -1
        for (v in layer) {
            val neighbors = view.ups[v].orEmpty().sortedBy { view.position.getValue(it) }
            if (neighbors.isEmpty()) continue
            val degree = neighbors.size
            for (medianIndex in intArrayOf((degree - 1) / 2, degree / 2)) {
                if (align.getValue(v) != v) break
                val u = neighbors[medianIndex]
                if ((u to v) !in marked && lastAlignedPosition < view.position.getValue(u)) {
                    align[u] = v
                    root[v] = root.getValue(u)
                    align[v] = root.getValue(v)
                    lastAlignedPosition = view.position.getValue(u)
                }
            }
        }
    }
    return root to align
}

/** Block compaction with class shifts (BK fig. 5); results are center coordinates. */
private fun horizontalCompaction(
    view: BkView,
    root: Map<String, String>,
    align: Map<String, String>,
): Map<String, Double> {
    val sink = mutableMapOf<String, String>()
    val shift = mutableMapOf<String, Double>()
    val x = mutableMapOf<String, Double>()
    for (layer in view.layers) {
        for (key in layer) {
            sink[key] = key
            shift[key] = Double.POSITIVE_INFINITY
        }
    }

    fun placeBlock(v: String) {
        if (v in x) return
        x[v] = 0.0
        var w = v
        do {
            val position = view.position.getValue(w)
            if (position > 0) {
                val predecessor = view.layers[view.layerIndex.getValue(w)][position - 1]
                val u = root.getValue(predecessor)
                placeBlock(u)
                if (sink.getValue(v) == v) sink[v] = sink.getValue(u)
                val separation = view.separation(predecessor, w)
                if (sink.getValue(v) != sink.getValue(u)) {
                    shift[sink.getValue(u)] = minOf(
                        shift.getValue(sink.getValue(u)),
                        x.getValue(v) - x.getValue(u) - separation,
                    )
                } else {
                    x[v] = maxOf(x.getValue(v), x.getValue(u) + separation)
                }
            }
            w = align.getValue(w)
        } while (w != v)
    }

    for (layer in view.layers) {
        for (key in layer) {
            if (root.getValue(key) == key) placeBlock(key)
        }
    }
    val result = mutableMapOf<String, Double>()
    for (layer in view.layers) {
        for (key in layer) {
            val blockRoot = root.getValue(key)
            var coordinate = x.getValue(blockRoot)
            val classShift = shift.getValue(sink.getValue(blockRoot))
            if (classShift < Double.POSITIVE_INFINITY) coordinate += classShift
            result[key] = coordinate
        }
    }
    return result
}
