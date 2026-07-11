package io.aequicor.visualization.engine.ir.serialization

import io.aequicor.visualization.engine.ir.model.DesignNode
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.subsystems.figures.HandleMirror
import io.aequicor.visualization.subsystems.figures.HandleOffset
import io.aequicor.visualization.subsystems.figures.ShapeType
import io.aequicor.visualization.subsystems.figures.VectorNetwork
import io.aequicor.visualization.subsystems.figures.VectorRegion
import io.aequicor.visualization.subsystems.figures.VectorSegment
import io.aequicor.visualization.subsystems.figures.VectorVertex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

class VectorNetworkSerializationTest {

    private val networkJson = """
        {
          "id": "glyph", "type": "vector",
          "viewBox": { "width": 24, "height": 24 },
          "network": {
            "vertices": [
              { "x": 12, "y": 2, "out": {"dx":6,"dy":4}, "in": {"dx":-6,"dy":-4}, "mirror": "angleAndLength" },
              { "x": 22, "y": 20, "corner": true },
              { "x": 2, "y": 20, "corner": true }
            ],
            "segments": [ {"from":0,"to":1}, {"from":1,"to":2}, {"from":2,"to":0} ],
            "regions": [ { "windingRule": "evenodd", "loops": [[0,1,2]] } ]
          }
        }
    """.trimIndent()

    private fun parseShape(json: String): DesignNodeKind.Shape {
        val result = assertIs<DesignNodeParseResult.Success>(parseDesignNode(json))
        return assertIs<DesignNodeKind.Shape>(result.node.kind)
    }

    @Test
    fun readsNetworkFieldByField() {
        val shape = parseShape(networkJson)
        val network = assertIs<VectorNetwork>(shape.network)
        assertEquals(
            VectorNetwork(
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
                regions = listOf(VectorRegion("evenodd", listOf(listOf(0, 1, 2)))),
            ),
            network,
        )
    }

    @Test
    fun networkRoundTripsThroughWriter() {
        val original = parseShape(networkJson)
        val rewritten = parseShape(writeDesignNode(designNode(original)).toJsonString())
        assertEquals(original.network, rewritten.network)
    }

    @Test
    fun emptyNetworkIsOmittedFromOutput() {
        val node = designNode(DesignNodeKind.Shape(shape = ShapeType.Vector))
        val json = writeDesignNode(node).toJsonString()
        assertFalse(json.contains("network"), "empty network must be omitted: $json")
    }

    private fun designNode(kind: DesignNodeKind.Shape): DesignNode =
        DesignNode(id = "glyph", type = "vector", kind = kind)
}
