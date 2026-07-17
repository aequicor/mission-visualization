package io.aequicor.visualization.subsystems.diagrams.layout

import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodeId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CrossingMinimizationTest {

    private fun link(from: String, to: String) = ScopeLink(DiagramNodeId(from), DiagramNodeId(to))

    private fun layeredGraphOf(members: List<String>, links: List<ScopeLink>): LayeredGraph {
        val ids = members.map(::DiagramNodeId)
        val layers = assignLongestPathLayers(ids, links.groupBy({ it.to }, { it.from }))
        return buildLayeredGraph(ids, links, layers)
    }

    // --- exact counter ---

    @Test
    fun bilayerCounterMatchesBruteForce() {
        val fixtures = listOf(
            listOf(0 to 0, 1 to 1) to 2,
            listOf(0 to 1, 1 to 0) to 2,
            listOf(0 to 0, 0 to 1, 1 to 0) to 2,
            listOf(0 to 2, 1 to 0, 1 to 1, 2 to 2) to 3,
            listOf(0 to 0, 0 to 1, 1 to 0, 1 to 1) to 2,
            listOf(2 to 0, 1 to 1, 0 to 2, 0 to 0, 2 to 2, 1 to 0) to 3,
        )
        for ((pairs, lowerSize) in fixtures) {
            var brute = 0L
            for (first in pairs.indices) {
                for (second in first + 1 until pairs.size) {
                    val (a, b) = pairs[first]
                    val (c, d) = pairs[second]
                    if ((a - c) * (b - d) < 0) brute++
                }
            }
            assertEquals(
                brute,
                countBilayerCrossings(pairs, lowerSize),
                "accumulator disagrees with brute force on $pairs",
            )
        }
    }

    // --- minimization ---

    @Test
    fun medianUncrossesASkewedFan() {
        // u1 -> {w1, w2}, u2 -> w1: the id-sorted seed [w1, w2] has one crossing,
        // the median sweep resolves it to zero.
        val layered = layeredGraphOf(
            members = listOf("u1", "u2", "w1", "w2"),
            links = listOf(link("u1", "w1"), link("u1", "w2"), link("u2", "w1")),
        )
        assertEquals(1L, totalCrossings(layered), "seed order must start crossed")
        minimizeCrossings(layered)
        assertEquals(0L, totalCrossings(layered))
    }

    @Test
    fun completeBipartitePairKeepsItsSingleUnavoidableCrossing() {
        // K2,2 has exactly one crossing in every order; the strict-improvement
        // transpose must not thrash and the count must stay at the optimum.
        val layered = layeredGraphOf(
            members = listOf("u1", "u2", "w1", "w2"),
            links = listOf(link("u1", "w1"), link("u1", "w2"), link("u2", "w1"), link("u2", "w2")),
        )
        minimizeCrossings(layered)
        assertEquals(1L, totalCrossings(layered))
    }

    private fun denseFixture(): LayeredGraph = layeredGraphOf(
        members = listOf("a1", "a2", "a3", "b1", "b2", "b3", "b4", "c1", "c2", "c3"),
        links = listOf(
            link("a1", "b3"), link("a1", "b4"),
            link("a2", "b1"), link("a2", "b3"),
            link("a3", "b1"), link("a3", "b2"),
            link("b1", "c3"), link("b2", "c2"), link("b2", "c3"),
            link("b3", "c1"), link("b4", "c1"), link("b4", "c2"),
            link("a1", "c3"), link("a3", "c1"),
        ),
    )

    @Test
    fun minimizationNeverEndsWorseThanTheSeedOrder() {
        val layered = denseFixture()
        val seedCrossings = totalCrossings(layered)
        minimizeCrossings(layered)
        assertTrue(
            totalCrossings(layered) <= seedCrossings,
            "keep-best must never lose to the seed (${totalCrossings(layered)} > $seedCrossings)",
        )
    }

    @Test
    fun resultIsLocallyOptimalUnderAdjacentSwaps() {
        val layered = denseFixture()
        minimizeCrossings(layered)
        val base = totalCrossings(layered)
        for (layer in layered.layers) {
            for (position in 0 until layer.size - 1) {
                val left = layer[position]
                layer[position] = layer[position + 1]
                layer[position + 1] = left
                val swapped = totalCrossings(layered)
                layer[position + 1] = layer[position]
                layer[position] = left
                assertTrue(
                    swapped >= base,
                    "swap at $position improves crossings ($swapped < $base) — transpose missed it",
                )
            }
        }
    }

    @Test
    fun minimizationIsDeterministic() {
        val first = denseFixture().also(::minimizeCrossings)
        val second = denseFixture().also(::minimizeCrossings)
        assertEquals(
            first.layers.map { layer -> layer.map { it.key } },
            second.layers.map { layer -> layer.map { it.key } },
        )
    }
}
