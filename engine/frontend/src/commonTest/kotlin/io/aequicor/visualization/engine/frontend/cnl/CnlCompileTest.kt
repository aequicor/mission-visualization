package io.aequicor.visualization.engine.frontend.cnl

import io.aequicor.visualization.engine.frontend.blocks.readers.slm
import io.aequicor.visualization.engine.frontend.compileSlm
import io.aequicor.visualization.engine.ir.model.DesignColor
import io.aequicor.visualization.engine.ir.model.DesignGap
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.engine.ir.model.DesignPaint
import io.aequicor.visualization.engine.ir.model.DesignSeverity
import io.aequicor.visualization.engine.ir.model.LayoutMode
import io.aequicor.visualization.subsystems.figures.ShapeType
import io.aequicor.visualization.engine.ir.model.SizingMode
import io.aequicor.visualization.engine.ir.model.bindable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** End-to-end: a CNL element sentence compiles through the full pipeline into the right IR node. */
class CnlCompileTest {
    private val source = slm(
        """
        ---
        screen: demo
        sourceLocale: en-US
        targetLocales:
          - en-US
        frame:
          preset: desktop-1440
          width: 1440
          height: 1024
        ---

        # Demo

        Rectangle 120 by 15 color #00B843 radius 15 padding 10 gap 16
        """,
    ) + "\n"

    @Test
    fun rectangleElementCompilesToShapeNode() {
        val result = compileSlm(source)
        assertTrue(
            result.diagnostics.none { it.severity == DesignSeverity.Error },
            result.diagnostics.joinToString { it.message },
        )
        val document = assertNotNull(result.document)
        val root = document.pages.single().children.single()
        val rect = root.allDescendants().first { it.kind is DesignNodeKind.Shape }

        assertEquals("shape", rect.type)
        assertEquals(ShapeType.Rectangle, assertIs<DesignNodeKind.Shape>(rect.kind).shape)
        assertEquals(SizingMode.Fixed, rect.sizing?.horizontal)
        assertEquals(120.0, rect.size.width)
        assertEquals(15.0, rect.size.height)
        assertEquals(15.0.bindable(), rect.cornerRadius?.topLeft)
        assertEquals(listOf(DesignPaint.Solid(DesignColor(0xFF00B843).bindable())), rect.fills)
        assertEquals(DesignGap.Fixed(16.0.bindable()), rect.layout.gap)
        assertEquals(10.0.bindable(), rect.layout.paddingLogical?.blockStart)
    }

    @Test
    fun containerHeadingCarriesLayoutAndNestsChildren() {
        val screen = slm(
            """
            ---
            screen: demo
            sourceLocale: en-US
            targetLocales:
              - en-US
            ---

            # Demo

            ## Mission Panel column gap 16 padding 24

            Rectangle 120 by 15 color #00B843
            """,
        ) + "\n"
        val result = compileSlm(screen)
        assertTrue(
            result.diagnostics.none { it.severity == DesignSeverity.Error },
            result.diagnostics.joinToString { it.message },
        )
        val root = assertNotNull(result.document).pages.single().children.single()
        val panel = root.children.first()
        assertEquals("Mission Panel", panel.name)
        assertEquals(LayoutMode.Vertical, panel.layout.mode)
        assertEquals(DesignGap.Fixed(16.0.bindable()), panel.layout.gap)
        assertEquals(24.0.bindable(), panel.layout.paddingLogical?.blockStart)
        // The rectangle sentence nests inside the container heading.
        assertTrue(panel.allDescendants().any { it.kind is DesignNodeKind.Shape })
    }
}
