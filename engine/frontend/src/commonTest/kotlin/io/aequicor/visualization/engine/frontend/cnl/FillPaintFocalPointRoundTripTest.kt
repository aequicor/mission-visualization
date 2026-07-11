package io.aequicor.visualization.engine.frontend.cnl

import io.aequicor.visualization.engine.frontend.blocks.readers.slm
import io.aequicor.visualization.engine.frontend.compileSlm
import io.aequicor.visualization.engine.ir.model.Bindable
import io.aequicor.visualization.engine.ir.model.DesignExpression
import io.aequicor.visualization.engine.ir.model.DesignNode
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.engine.ir.model.DesignPaint
import io.aequicor.visualization.engine.ir.model.DesignPoint
import io.aequicor.visualization.engine.ir.model.DesignSeverity
import io.aequicor.visualization.engine.ir.serialization.DesignNodeParseResult
import io.aequicor.visualization.engine.ir.serialization.parseDesignNode
import io.aequicor.visualization.engine.ir.serialization.toJsonString
import io.aequicor.visualization.engine.ir.serialization.writeDesignNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * S28d: a `DesignPaint.Image`/`Video` `focalPoint` (a fill paint) must carry the same per-axis
 * `$var`/`{{expr}}` refs as the media-node `focalPoint`, and survive every emit/parse leg —
 * CNL authoring, CNL emit + recompile, and JSON serde. Before the [CnlGrammar] fix the CNL
 * emitter rendered the focal via `.orZero`, silently collapsing a ref axis to `0` on the way out.
 */
class FillPaintFocalPointRoundTripTest {
    /** x authored as a `$var`, y as a `{{expr}}` — the mixed shape that a literal-only emit drops. */
    private val focal = DesignPoint(
        x = Bindable.VarRef("crop.x"),
        y = Bindable.DataRef(DesignExpression("data.fy")),
    )

    /** Leg 1 — CNL authoring preserves per-axis refs on a fill-paint focalPoint. */
    @Test
    fun cnlAuthoringPreservesFillPaintFocalRefs() {
        val fill = assertIs<DesignPaint.Image>(assertNotNull(imageFillNode().fills).single())
        assertEquals(focal, fill.focalPoint)
    }

    /** Leg 2 — CNL emit then recompile preserves the refs (this is the leg the fix repairs). */
    @Test
    fun cnlEmitRecompilePreservesFillPaintFocalRefs() {
        val node = imageFillNode()
        val cnl = CnlEmitter.emitSentence(node)
        assertTrue(
            "\$crop.x" in cnl && "{{data.fy}}" in cnl,
            "emitted CNL must carry the focal refs, not literal zeros: $cnl",
        )
        assertEquals(node.fills, compileLeaf(cnl).fills)
    }

    /** Leg 3 — JSON serde preserves the refs. */
    @Test
    fun jsonSerdePreservesFillPaintFocalRefs() {
        val node = imageFillNode()
        val reparsed = assertIs<DesignNodeParseResult.Success>(
            parseDesignNode(writeDesignNode(node).toJsonString()),
        ).node
        assertEquals(node.fills, reparsed.fills)
    }

    /** All legs chained end-to-end: CNL author → IR → CNL → IR → JSON → IR. */
    @Test
    fun fillPaintFocalRefsSurviveCnlJsonRoundTrip() {
        val fromCnl = imageFillNode()
        val cnlFill = assertIs<DesignPaint.Image>(assertNotNull(fromCnl.fills).single())
        assertEquals(focal, cnlFill.focalPoint, "CNL → IR must preserve the focal refs")

        val afterCnl = compileLeaf(CnlEmitter.emitSentence(fromCnl))
        assertEquals(fromCnl.fills, afterCnl.fills, "IR → CNL → IR must preserve the focal refs")

        val afterJson = assertIs<DesignNodeParseResult.Success>(
            parseDesignNode(writeDesignNode(afterCnl).toJsonString()),
        ).node
        assertEquals(fromCnl.fills, afterJson.fills, "IR → JSON → IR must preserve the focal refs")
    }

    private fun imageFillNode(): DesignNode =
        compileLeaf("Rectangle 300 by 200 image (asset «hero.jpg» crop focus (\$crop.x {{data.fy}}))")

    /** Compile a single-sentence body under a minimal screen header and return the lone leaf node. */
    private fun compileLeaf(sentence: String): DesignNode {
        val header = slm(
            """
            ---
            screen: demo
            sourceLocale: en-US
            targetLocales: [en-US]
            frame: { preset: desktop-1440, width: 1440, height: 1024 }
            ---

            # Demo
            """,
        )
        val src = header + "\n\n" + sentence.trimIndent() + "\n"
        val result = compileSlm(src)
        assertTrue(
            result.diagnostics.none { it.severity == DesignSeverity.Error },
            result.diagnostics.filter { it.severity == DesignSeverity.Error }.joinToString { it.message },
        )
        return assertNotNull(result.document).pages.single().children.single()
            .allDescendants().first { it.kind is DesignNodeKind.Shape }
    }
}
