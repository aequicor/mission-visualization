package io.aequicor.visualization.engine.frontend.blocks.readers

import io.aequicor.visualization.engine.frontend.blocks.StylePatch
import io.aequicor.visualization.engine.ir.model.Bindable
import io.aequicor.visualization.engine.ir.model.DesignColor
import io.aequicor.visualization.engine.ir.model.DesignCornerRadius
import io.aequicor.visualization.engine.ir.model.DesignEffect
import io.aequicor.visualization.engine.ir.model.DesignPaint
import io.aequicor.visualization.engine.ir.model.DesignPoint
import io.aequicor.visualization.engine.ir.model.GradientKind
import io.aequicor.visualization.engine.ir.model.GradientStop
import io.aequicor.visualization.engine.ir.model.StrokeAlign
import io.aequicor.visualization.engine.ir.model.bindable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class StyleBlockReaderTest {
    /** Spec "Visual Style" example (~line 822-858). */
    @Test
    fun readsSpecVisualStyleExample() {
        val (patch, collector) = readSingle(
            """
            style:
              opacity: 0.96
              blendMode: normal
              radius:
                topLeft: §radius.md
                topRight: §radius.md
                bottomRight: §radius.md
                bottomLeft: §radius.md
              cornerSmoothing: 0.6
              fills:
                - type: solid
                  token: color.surface
                  opacity: 1
                - type: linearGradient
                  stops:
                    - position: 0
                      token: color.accent.start
                    - position: 1
                      token: color.accent.end
                  opacity: 0.12
              strokes:
                - token: color.border.subtle
                  weight: 1
                  position: inside
                  dash: []
                  caps: none
                  joins: round
              effects:
                - type: dropShadow
                  x: 0
                  y: 8
                  blur: 24
                  spread: 0
                  token: shadow.card.color
                  opacity: 0.18
            """,
        )
        val style = assertIs<StylePatch>(patch)
        assertEquals(0.96.bindable(), style.opacity)
        assertEquals("normal", style.blendMode)
        assertEquals(
            DesignCornerRadius(
                topLeft = Bindable.VarRef("radius.md"),
                topRight = Bindable.VarRef("radius.md"),
                bottomRight = Bindable.VarRef("radius.md"),
                bottomLeft = Bindable.VarRef("radius.md"),
                smoothing = 0.6,
            ),
            style.radius,
        )
        assertEquals(
            listOf(
                DesignPaint.Solid(
                    color = Bindable.VarRef("color.surface"),
                    opacity = 1.0.bindable(),
                ),
                DesignPaint.Gradient(
                    gradientType = GradientKind.Linear,
                    stops = listOf(
                        GradientStop(0.0, Bindable.VarRef("color.accent.start")),
                        GradientStop(1.0, Bindable.VarRef("color.accent.end")),
                    ),
                    opacity = 0.12.bindable(),
                ),
            ),
            style.fills,
        )
        val strokes = assertIs<io.aequicor.visualization.engine.ir.model.DesignStrokes>(style.strokes)
        assertEquals(listOf(DesignPaint.Solid(Bindable.VarRef("color.border.subtle"))), strokes.paints)
        assertEquals(1.0.bindable(), strokes.weight)
        assertEquals(StrokeAlign.Inside, strokes.align)
        assertEquals(emptyList(), strokes.dashPattern)
        assertEquals("none", strokes.cap)
        assertEquals("round", strokes.join)
        assertEquals(
            listOf(
                DesignEffect.DropShadow(
                    color = Bindable.VarRef("shadow.card.color"),
                    offset = DesignPoint(0.0, 8.0),
                    blur = 24.0,
                    spread = 0.0,
                ),
            ),
            style.effects,
        )
        // Token-colored shadow cannot carry `opacity` in the IR: one hint expected.
        assertEquals(1, collector.diagnostics.size, collector.diagnostics.joinToString { it.message })
        assertTrue("opacity" in collector.diagnostics.single().message)
    }

    /** Spec style refs example (~line 723-729). */
    @Test
    fun readsSpecStyleRefsExample() {
        val (patch, collector) = readSingle(
            """
            style:
              fillStyle: color.surface.default
              textStyle: typography.heading.lg
              effectStyle: shadow.card
              gridStyle: grid.desktop.12
            """,
        )
        assertTrue(collector.diagnostics.isEmpty())
        assertEquals(
            StylePatch(
                fillStyle = "color.surface.default",
                textStyle = "typography.heading.lg",
                effectStyle = "shadow.card",
                gridStyle = "grid.desktop.12",
            ),
            patch,
        )
    }

    /** Spec variable binding example (~line 733-738). */
    @Test
    fun readsVariableBindings() {
        val (patch, collector) = readSingle(
            """
            style:
              fills:
                - variable: color.surface
              radius:
                variable: radius.card
            """,
        )
        assertTrue(collector.diagnostics.isEmpty())
        assertEquals(
            StylePatch(
                radius = DesignCornerRadius.all(Bindable.VarRef("radius.card")),
                fills = listOf(DesignPaint.Solid(Bindable.VarRef("color.surface"))),
            ),
            patch,
        )
    }

    @Test
    fun hexColorAndUniformRadius() {
        val (patch, collector) = readSingle(
            """
            style:
              radius: 8
              fills:
                - type: solid
                  color: "#1F5FA8"
            """,
        )
        assertTrue(collector.diagnostics.isEmpty())
        assertEquals(
            StylePatch(
                radius = DesignCornerRadius.all(8.0.bindable()),
                fills = listOf(DesignPaint.Solid(DesignColor(0xFF1F5FA8).bindable())),
            ),
            patch,
        )
    }

    @Test
    fun effectStyleRefEntryLandsInEffectStyle() {
        val (patch, collector) = readSingle(
            """
            style:
              effects:
                - style: shadow.card
            """,
        )
        assertTrue(collector.diagnostics.isEmpty())
        assertEquals(StylePatch(effects = emptyList(), effectStyle = "shadow.card"), patch)
    }

    @Test
    fun unknownFillTypeBecomesUnknownPaintWithWarning() {
        val (patch, collector) = readSingle(
            """
            style:
              fills:
                - type: plasma
            """,
        )
        val style = assertIs<StylePatch>(patch)
        assertEquals(listOf<DesignPaint>(DesignPaint.Unknown("plasma")), style.fills)
        assertTrue(collector.diagnostics.any { "plasma" in it.message })
    }
}
