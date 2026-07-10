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

    private fun ellipse(fields: String): String =
        """{ "pages": [ { "id": "p", "children": [ { "id": "e", "type": "ellipse"$fields } ] } ] }"""

    @Test
    fun arcSweepOutOfRangeIsWarning() {
        validate(ellipse(""","arcSweepDeg": 720.0""")).assertHas("IR-ASSET-013", DesignSeverity.Warning)
    }

    @Test
    fun innerRadiusOutOfRangeIsWarning() {
        validate(ellipse(""","innerRadius": 2.0""")).assertHas("IR-ASSET-014", DesignSeverity.Warning)
    }

    @Test
    fun validArcAndRatioHaveNoWarning() {
        val diagnostics = validate(ellipse(""","arcStartDeg": -90.0, "arcSweepDeg": 270.0, "innerRadius": 0.5"""))
        diagnostics.assertNone("IR-ASSET-013")
        diagnostics.assertNone("IR-ASSET-014")
    }

    @Test
    fun negativeVertexRadiusIsWarning() {
        validate(
            doc(
                """
                { "vertices": [ {"x":0,"y":0,"cornerRadius":-5.0}, {"x":10,"y":0}, {"x":0,"y":10} ],
                  "segments": [ {"from":0,"to":1}, {"from":1,"to":2}, {"from":2,"to":0} ] }
                """.trimIndent(),
            ),
        ).assertHas("IR-ASSET-015", DesignSeverity.Warning)
    }

    @Test
    fun regionFillOutOfRangeIsWarning() {
        validate(
            doc(
                """
                { "vertices": [ {"x":0,"y":0}, {"x":10,"y":0}, {"x":0,"y":10} ],
                  "segments": [ {"from":0,"to":1}, {"from":1,"to":2}, {"from":2,"to":0} ],
                  "regions": [ { "loops": [[0,1,2]] } ] }
                """.trimIndent(),
                extra = ""","regionFills": { "5": [ { "type": "solid", "color": "#FF0000" } ] }""",
            ),
        ).assertHas("IR-ASSET-016", DesignSeverity.Warning)
    }
}
