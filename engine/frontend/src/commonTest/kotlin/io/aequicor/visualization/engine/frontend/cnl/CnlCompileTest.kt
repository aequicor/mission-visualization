package io.aequicor.visualization.engine.frontend.cnl

import io.aequicor.visualization.engine.frontend.blocks.readers.slm
import io.aequicor.visualization.engine.frontend.compileSlm
import io.aequicor.visualization.engine.ir.model.DesignColor
import io.aequicor.visualization.engine.ir.model.DesignGap
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.engine.ir.model.DesignPaint
import io.aequicor.visualization.engine.ir.model.DesignSeverity
import io.aequicor.visualization.engine.ir.model.LayoutMode
import io.aequicor.visualization.engine.ir.model.ShapeType
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
        sourceLocale: ru-RU
        targetLocales:
          - ru-RU
        frame:
          preset: desktop-1440
          width: 1440
          height: 1024
        ---

        # Demo

        Прямоугольник 120 на 15 цвет #00B843 радиус 15 паддинги 10 отступ 16
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
            sourceLocale: ru-RU
            targetLocales:
              - ru-RU
            ---

            # Demo

            ## Панель миссий колонка отступ 16 паддинги 24

            Прямоугольник 120 на 15 цвет #00B843
            """,
        ) + "\n"
        val result = compileSlm(screen)
        assertTrue(
            result.diagnostics.none { it.severity == DesignSeverity.Error },
            result.diagnostics.joinToString { it.message },
        )
        val root = assertNotNull(result.document).pages.single().children.single()
        val panel = root.children.first()
        assertEquals("Панель миссий", panel.name)
        assertEquals(LayoutMode.Vertical, panel.layout.mode)
        assertEquals(DesignGap.Fixed(16.0.bindable()), panel.layout.gap)
        assertEquals(24.0.bindable(), panel.layout.paddingLogical?.blockStart)
        // The rectangle sentence nests inside the container heading.
        assertTrue(panel.allDescendants().any { it.kind is DesignNodeKind.Shape })
    }
}
