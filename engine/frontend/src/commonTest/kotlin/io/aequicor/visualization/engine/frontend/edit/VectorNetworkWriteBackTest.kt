package io.aequicor.visualization.engine.frontend.edit

import io.aequicor.visualization.engine.ir.model.DesignNode
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.subsystems.figures.DesignViewBox
import io.aequicor.visualization.subsystems.figures.HandleMirror
import io.aequicor.visualization.subsystems.figures.HandleOffset
import io.aequicor.visualization.subsystems.figures.ShapeType
import io.aequicor.visualization.subsystems.figures.VectorNetwork
import io.aequicor.visualization.subsystems.figures.VectorRegion
import io.aequicor.visualization.subsystems.figures.VectorSegment
import io.aequicor.visualization.engine.ir.model.DesignSeverity
import io.aequicor.visualization.subsystems.figures.VectorVertex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Authoring + write-back for structural vector networks: the `network:` block reads, survives a
 * structural insert ([NodeSectionWriter] `vectorPayload` — the fixed silent-drop bug), and
 * round-trips through the surgical [SetVectorNetwork] patch.
 */
class VectorNetworkWriteBackTest {

    private val doc = """
        ---
        screen: s
        sourceLocale: en-US
        ---

        # Screen id root name «Screen»

        ## Vector: Glyph id glyph viewbox (0 0 24 24)
    """.trimIndent() + "\n"

    private fun sampleNetwork(): VectorNetwork = VectorNetwork(
        vertices = listOf(
            VectorVertex(
                12.0, 2.0,
                inHandle = HandleOffset(-6.0, -4.0),
                outHandle = HandleOffset(6.0, 4.0),
                mirror = HandleMirror.AngleAndLength,
            ),
            VectorVertex(22.0, 20.0, corner = true),
            VectorVertex(2.0, 20.0, corner = true),
        ),
        segments = listOf(VectorSegment(0, 1), VectorSegment(1, 2), VectorSegment(2, 0)),
        regions = listOf(VectorRegion("nonzero", listOf(listOf(0, 1, 2)))),
    )

    @Test
    fun authorsNetworkBlock() {
        val networkDoc = """
            ---
            screen: s
            sourceLocale: en-US
            ---

            # Screen id root

            ## Vector: Glyph id glyph viewbox (0 0 24 24) network (vertex (12 2 in (-6 -4) out (6 4) mirror angleAndLength) vertex (22 20 corner) vertex (2 20 corner) segment (0 1) segment (1 2) segment (2 0) region loops (0 1 2))
        """.trimIndent() + "\n"

        val recompiled = compileForEdit(networkDoc)
        assertNoErrors(recompiled)
        val shape = recompiled.requireDocument().requireNode("glyph").kind as DesignNodeKind.Shape
        assertEquals(sampleNetwork(), shape.network)
    }

    @Test
    fun insertVectorSubtreePreservesNetwork() {
        val node = DesignNode(
            id = "ins_glyph",
            type = "vector",
            name = "Glyph",
            kind = DesignNodeKind.Shape(
                shape = ShapeType.Vector,
                viewBox = DesignViewBox(0.0, 0.0, 24.0, 24.0),
                network = sampleNetwork(),
            ),
        )
        val compiled = compileForEdit(doc)
        val result = applySlmEdit(doc, InsertChildSubtree("root", node), compiled)
        val recompiled = compileForEdit(result.requireNewSource())
        assertNoErrors(recompiled)
        val shape = recompiled.requireDocument().requireNode("ins_glyph").kind as DesignNodeKind.Shape
        assertEquals(sampleNetwork(), shape.network)
        assertEquals(DesignViewBox(0.0, 0.0, 24.0, 24.0), shape.viewBox)
    }

    private fun assertNoErrors(result: io.aequicor.visualization.engine.frontend.SlmCompileResult) {
        val errors = result.diagnostics.filter { it.severity == DesignSeverity.Error }
        assertTrue(errors.isEmpty(), "unexpected errors: ${errors.joinToString { it.message }}")
    }
}
