package io.aequicor.visualization.engine.frontend.blocks.readers

import io.aequicor.visualization.engine.frontend.blocks.LayoutPatch
import io.aequicor.visualization.engine.frontend.blocks.ResponsivePatch
import io.aequicor.visualization.engine.frontend.blocks.ResponsiveVariantPatch
import io.aequicor.visualization.engine.frontend.blocks.StylePatch
import io.aequicor.visualization.engine.ir.model.Bindable
import io.aequicor.visualization.engine.ir.model.DesignCornerRadius
import io.aequicor.visualization.engine.ir.model.DesignGap
import io.aequicor.visualization.engine.ir.model.LayoutMode
import io.aequicor.visualization.engine.ir.model.ResponsiveDimension
import io.aequicor.visualization.engine.ir.model.bindable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ResponsiveBlockReaderTest {
    /** Spec responsive example (~line 534-552). */
    @Test
    fun readsSpecResponsiveExample() {
        val (patch, collector) = readSingle(
            """
            responsive:
              variants:
                - when:
                    breakpoint: mobile
                  layout:
                    mode: column
                    padding:
                      inline: §space.4
                    gap: §space.3
                  style:
                    radius: 0
                - when:
                    breakpoint: desktop
                    density: compact
                  layout:
                    mode: row
                    gap: §space.2
            """,
        )
        assertTrue(collector.diagnostics.isEmpty(), collector.diagnostics.joinToString { it.message })
        assertEquals(
            ResponsivePatch(
                variants = listOf(
                    ResponsiveVariantPatch(
                        selectors = mapOf(ResponsiveDimension.Breakpoint to "mobile"),
                        layout = LayoutPatch(
                            mode = LayoutMode.Vertical,
                            paddingInlineEnd = Bindable.VarRef("space.4"),
                            paddingInlineStart = Bindable.VarRef("space.4"),
                            gap = DesignGap.Fixed(Bindable.VarRef("space.3")),
                        ),
                        style = StylePatch(radius = DesignCornerRadius.all(0.0.bindable())),
                    ),
                    ResponsiveVariantPatch(
                        selectors = mapOf(
                            ResponsiveDimension.Breakpoint to "desktop",
                            ResponsiveDimension.Density to "compact",
                        ),
                        layout = LayoutPatch(
                            mode = LayoutMode.Horizontal,
                            gap = DesignGap.Fixed(Bindable.VarRef("space.2")),
                        ),
                    ),
                ),
            ),
            patch,
        )
    }

    @Test
    fun unknownDimensionIsDroppedWithWarning() {
        val (patch, collector) = readSingle(
            """
            responsive:
              variants:
                - when:
                    mood: gloomy
                  layout:
                    mode: row
            """,
        )
        assertEquals(ResponsivePatch(variants = emptyList()), patch)
        assertTrue(collector.diagnostics.any { "mood" in it.message })
    }
}
