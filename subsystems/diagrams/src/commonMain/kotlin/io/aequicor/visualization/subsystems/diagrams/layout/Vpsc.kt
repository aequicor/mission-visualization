package io.aequicor.visualization.subsystems.diagrams.layout

/**
 * Exact 1-D separation solver for a chain of variables — the VPSC special case every
 * layered coordinate phase needs (nodes of one layer form a total order):
 *
 * minimize `Σ weights[i] · (x[i] − desired[i])²` subject to `x[i+1] − x[i] ≥ gaps[i]`.
 *
 * Shifting each variable by its cumulative gap turns the separations into plain
 * monotonicity, which weighted isotonic regression solves exactly via
 * pool-adjacent-violators: single left-to-right pass merging adjacent blocks whose
 * weighted means violate the order. O(n), no iteration limits, and bit-stable —
 * identical input always produces the identical output (fixed operation order).
 */
internal fun solveChainSeparation(
    desired: DoubleArray,
    gaps: DoubleArray,
    weights: DoubleArray? = null,
): DoubleArray {
    val count = desired.size
    require(gaps.size == maxOf(count - 1, 0)) {
        "gaps must have ${maxOf(count - 1, 0)} entries for $count variables, got ${gaps.size}"
    }
    require(weights == null || weights.size == count) {
        "weights must have $count entries, got ${weights?.size}"
    }
    require(weights == null || weights.all { it > 0.0 }) { "weights must be positive" }
    if (count <= 1) return desired.copyOf()

    // x[i] = y[i] + offset[i] with offset[i] = Σ gaps[0..i): constraints become y[i] ≤ y[i+1].
    val offsets = DoubleArray(count)
    for (index in 1 until count) offsets[index] = offsets[index - 1] + gaps[index - 1]

    // Pool-adjacent-violators over the shifted targets: a stack of blocks, each holding
    // the weighted mean of the variables merged into it.
    val blockMeans = DoubleArray(count)
    val blockWeights = DoubleArray(count)
    val blockSizes = IntArray(count)
    var top = -1
    for (index in 0 until count) {
        var mean = desired[index] - offsets[index]
        var weight = weights?.get(index) ?: 1.0
        var size = 1
        while (top >= 0 && blockMeans[top] > mean) {
            mean = (blockMeans[top] * blockWeights[top] + mean * weight) / (blockWeights[top] + weight)
            weight += blockWeights[top]
            size += blockSizes[top]
            top--
        }
        top++
        blockMeans[top] = mean
        blockWeights[top] = weight
        blockSizes[top] = size
    }

    val result = DoubleArray(count)
    var variable = 0
    for (block in 0..top) {
        repeat(blockSizes[block]) {
            result[variable] = blockMeans[block] + offsets[variable]
            variable++
        }
    }
    return result
}
