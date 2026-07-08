package io.aequicor.visualization.engine.frontend.edit

import io.aequicor.visualization.engine.ir.model.Bindable
import io.aequicor.visualization.engine.ir.model.DesignColor
import io.aequicor.visualization.engine.ir.model.DesignEffect
import io.aequicor.visualization.engine.ir.model.DesignPaint
import io.aequicor.visualization.engine.ir.model.DesignPoint
import io.aequicor.visualization.engine.ir.model.DesignStrokes
import io.aequicor.visualization.engine.ir.model.GradientKind
import io.aequicor.visualization.engine.ir.model.GradientStop
import io.aequicor.visualization.engine.ir.model.StrokeAlign
import io.aequicor.visualization.engine.ir.model.bindable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Whole-list style write-back (Tier-2): [SetFills] / [SetStrokes] / [SetEffects] rewrite
 * the `style:` list from the IR and round-trip faithfully — the recompiled node carries
 * exactly the paints/strokes/effects that were written, with token colors preserved as
 * `token:` refs and sibling style keys (radius) left intact.
 */
class StyleListWriteBackTest {

    private val doc = """
        ---
        screen: s
        sourceLocale: en-US
        ---

        # Screen

        ## Panel
        style:
          radius: 8
          fills:
            - color: "#101010"
    """.trimIndent() + "\n"

    private fun hex(h: String): DesignColor = DesignColor.fromHex(h)!!

    private fun DesignPaint.solidColor(): Bindable<DesignColor> = (this as DesignPaint.Solid).color

    private fun applyAndRecompileNode(edit: SlmEdit): io.aequicor.visualization.engine.ir.model.DesignNode {
        val compiled = compileForEdit(doc)
        val newSource = applySlmEdit(doc, edit, compiled).requireNewSource()
        val recompiled = compileForEdit(newSource)
        assertTrue(recompiled.isSuccess, recompiled.diagnostics.joinToString { it.message })
        assertTrue("radius: 8" in newSource, "sibling style key survives: $newSource")
        return recompiled.requireDocument().requireNode("panel")
    }

    @Test
    fun replacesFillsWithLiteralAndTokenSolids() {
        val fills = listOf(
            DesignPaint.Solid(hex("#ff0000").bindable()),
            DesignPaint.Solid(Bindable.VarRef("color.accent")),
        )
        val node = applyAndRecompileNode(SetFills("panel", fills))
        assertEquals(2, node.fills?.size)
        assertEquals(hex("#ff0000").bindable(), node.fills!![0].solidColor())
        assertEquals(Bindable.VarRef("color.accent"), node.fills!![1].solidColor())
    }

    @Test
    fun replacesFillsWithGradient() {
        val gradient = DesignPaint.Gradient(
            gradientType = GradientKind.Linear,
            from = DesignPoint(0.0, 0.0),
            to = DesignPoint(0.0, 1.0),
            stops = listOf(
                GradientStop(0.0, hex("#ff0000").bindable()),
                GradientStop(1.0, hex("#0000ff").bindable()),
            ),
        )
        val node = applyAndRecompileNode(SetFills("panel", listOf(gradient)))
        val fill = node.fills?.single() as DesignPaint.Gradient
        assertEquals(GradientKind.Linear, fill.gradientType)
        assertEquals(2, fill.stops.size)
        assertEquals(0.0, fill.stops[0].position)
        assertEquals(hex("#ff0000").bindable(), fill.stops[0].color)
        assertEquals(1.0, fill.stops[1].position)
        assertEquals(hex("#0000ff").bindable(), fill.stops[1].color)
    }

    @Test
    fun writesStrokesWithAttributes() {
        val strokes = DesignStrokes(
            paints = listOf(DesignPaint.Solid(hex("#334455").bindable())),
            weight = 2.0.bindable(),
            align = StrokeAlign.Outside,
            dashPattern = listOf(6.0, 4.0),
            cap = "round",
            join = "round",
        )
        val node = applyAndRecompileNode(SetStrokes("panel", strokes))
        val result = node.strokes!!
        assertEquals(2.0.bindable(), result.weight)
        assertEquals(StrokeAlign.Outside, result.align)
        assertEquals(listOf(6.0, 4.0), result.dashPattern)
        assertEquals("round", result.cap)
        assertEquals("round", result.join)
        assertEquals(hex("#334455").bindable(), (result.paints.single() as DesignPaint.Solid).color)
    }

    @Test
    fun writesDropShadowEffect() {
        val effects = listOf(
            DesignEffect.DropShadow(
                color = DesignColor(0x40000000).bindable(),
                offset = DesignPoint(0.0, 4.0),
                blur = 12.0,
                spread = 0.0,
            ),
        )
        val node = applyAndRecompileNode(SetEffects("panel", effects))
        val shadow = node.effects.single() as DesignEffect.DropShadow
        assertEquals(DesignColor(0x40000000).bindable(), shadow.color)
        assertEquals(DesignPoint(0.0, 4.0), shadow.offset)
        assertEquals(12.0, shadow.blur)
    }
}
