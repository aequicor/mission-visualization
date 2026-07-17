package io.aequicor.visualization.subsystems.diagrams.layout

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VpscTest {

    @Test
    fun alreadySeparatedInputStaysExactlyWhereItWas() {
        val desired = doubleArrayOf(0.0, 100.0, 250.0)
        val solved = solveChainSeparation(desired, gaps = doubleArrayOf(50.0, 50.0))
        assertContentEquals(desired.toTypedArray(), solved.toTypedArray())
    }

    @Test
    fun overlappingPairSplitsAroundTheirMean() {
        val solved = solveChainSeparation(
            desired = doubleArrayOf(100.0, 100.0),
            gaps = doubleArrayOf(40.0),
        )
        assertEquals(80.0, solved[0], 1e-9)
        assertEquals(120.0, solved[1], 1e-9)
    }

    @Test
    fun heavierVariableMovesLess() {
        val solved = solveChainSeparation(
            desired = doubleArrayOf(100.0, 100.0),
            gaps = doubleArrayOf(40.0),
            weights = doubleArrayOf(3.0, 1.0),
        )
        // Weighted mean of the shifted targets (100, 60) is 90: the heavy first
        // variable ends 10 from its desired spot, the light one 30.
        assertEquals(90.0, solved[0], 1e-9)
        assertEquals(130.0, solved[1], 1e-9)
    }

    @Test
    fun everySeparationHoldsOnAMessyChain() {
        val desired = doubleArrayOf(10.0, 0.0, 5.0, 30.0, 20.0, 21.0)
        val gaps = doubleArrayOf(8.0, 8.0, 8.0, 8.0, 8.0)
        val solved = solveChainSeparation(desired, gaps)
        for (index in gaps.indices) {
            assertTrue(
                solved[index + 1] - solved[index] >= gaps[index] - 1e-9,
                "separation violated at $index: ${solved.toList()}",
            )
        }
    }

    @Test
    fun solveIsBitStable() {
        val desired = doubleArrayOf(3.1, -7.4, 12.9, 12.9, 0.0, 55.5)
        val gaps = doubleArrayOf(6.0, 10.0, 4.0, 24.0, 1.0)
        val weights = doubleArrayOf(1.0, 2.0, 1.0, 5.0, 1.0, 3.0)
        val first = solveChainSeparation(desired, gaps, weights)
        val second = solveChainSeparation(desired, gaps, weights)
        assertContentEquals(first.toTypedArray(), second.toTypedArray())
    }

    @Test
    fun degenerateInputsPassThrough() {
        assertEquals(0, solveChainSeparation(doubleArrayOf(), doubleArrayOf()).size)
        assertContentEquals(
            arrayOf(42.0),
            solveChainSeparation(doubleArrayOf(42.0), doubleArrayOf()).toTypedArray(),
        )
    }
}
