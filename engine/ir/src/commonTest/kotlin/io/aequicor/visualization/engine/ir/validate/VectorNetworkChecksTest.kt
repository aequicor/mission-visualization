package io.aequicor.visualization.engine.ir.validate

import io.aequicor.visualization.engine.ir.model.DesignSeverity
import kotlin.test.Test

class VectorNetworkChecksTest {

    private fun doc(networkJson: String, extra: String = ""): String =
        """
        { "pages": [ { "id": "p", "children": [
          { "id": "glyph", "type": "vector"$extra,
            "network": $networkJson }
        ] } ] }
        """.trimIndent()

    @Test
    fun cleanClosedTriangleHasNoNetworkDiagnostics() {
        val diagnostics = validate(
            doc(
                """
                { "vertices": [ {"x":0,"y":0}, {"x":10,"y":0}, {"x":0,"y":10} ],
                  "segments": [ {"from":0,"to":1}, {"from":1,"to":2}, {"from":2,"to":0} ],
                  "regions": [ { "loops": [[0,1,2]] } ] }
                """.trimIndent(),
            ),
        )
        diagnostics.assertNone("IR-ASSET-008")
        diagnostics.assertNone("IR-ASSET-009")
        diagnostics.assertNone("IR-ASSET-010")
        diagnostics.assertNone("IR-ASSET-011")
        diagnostics.assertNone("IR-ASSET-012")
    }

    @Test
    fun outOfRangeSegmentIndexIsError() {
        validate(
            doc(
                """
                { "vertices": [ {"x":0,"y":0}, {"x":10,"y":0} ],
                  "segments": [ {"from":0,"to":5} ] }
                """.trimIndent(),
            ),
        ).assertHas("IR-ASSET-008", DesignSeverity.Error)
    }

    @Test
    fun unclosedRegionLoopIsError() {
        validate(
            doc(
                """
                { "vertices": [ {"x":0,"y":0}, {"x":10,"y":0}, {"x":0,"y":10} ],
                  "segments": [ {"from":0,"to":1}, {"from":1,"to":2} ],
                  "regions": [ { "loops": [[0,1]] } ] }
                """.trimIndent(),
            ),
        ).assertHas("IR-ASSET-009", DesignSeverity.Error)
    }

    @Test
    fun degenerateNetworkIsWarning() {
        validate(
            doc(
                """
                { "vertices": [ {"x":0,"y":0} ], "segments": [] }
                """.trimIndent(),
            ),
        ).assertHas("IR-ASSET-010", DesignSeverity.Warning)
    }

    @Test
    fun mirrorMismatchIsWarning() {
        validate(
            doc(
                """
                { "vertices": [
                    {"x":0,"y":0,"in":{"dx":6,"dy":4},"out":{"dx":6,"dy":4},"mirror":"angleAndLength"},
                    {"x":10,"y":0} ],
                  "segments": [ {"from":0,"to":1} ] }
                """.trimIndent(),
            ),
        ).assertHas("IR-ASSET-011", DesignSeverity.Warning)
    }

    @Test
    fun networkAndPathsCoexistenceIsWarning() {
        validate(
            doc(
                """
                { "vertices": [ {"x":0,"y":0}, {"x":10,"y":0} ],
                  "segments": [ {"from":0,"to":1} ] }
                """.trimIndent(),
                extra = ""","paths":[{"d":"M0 0 L10 10"}]""",
            ),
        ).assertHas("IR-ASSET-012", DesignSeverity.Warning)
    }
}
