package io.aequicor.visualization.engine.frontend.edit

import io.aequicor.visualization.engine.frontend.semantics.SpecRuDocument
import io.aequicor.visualization.engine.ir.model.SizingMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Batch semantics: sequential re-resolution with an internal recompile per step. */
class SequentialBatchTest {
    private val topbarParagraph =
        "Верхняя панель: заголовок Mission Control, справа основная кнопка [Создать миссию](/missions/new).\n"

    @Test
    fun secondEditLandsInBlockCreatedByFirst() {
        val compiled = compileForEdit(SpecRuDocument)
        val result = applySlmEdits(
            SpecRuDocument,
            listOf(
                SetSizing("topbar", width = SizingSpec(SizingMode.Fixed, value = 320.0)),
                SetLayoutProperty("topbar", LayoutProp.Gap, YamlScalarValue.Num(12.0)),
            ),
            compiled,
            EditTestOptions,
        )
        val new = result.requireNewSource()
        assertEquals(
            SpecRuDocument.replace(
                topbarParagraph,
                topbarParagraph +
                    "\nlayout:\n  sizing:\n    width:\n      type: fixed\n      value: 320\n  gap: 12\n",
            ),
            new,
        )
        assertLosslessOutside(SpecRuDocument, new, assertNotNull(result.appliedRange))
    }

    @Test
    fun multiNodeBatchAppliesEveryEdit() {
        val compiled = compileForEdit(SpecRuDocument)
        val result = applySlmEdits(
            SpecRuDocument,
            listOf(
                SetSizing("topbar", width = SizingSpec(SizingMode.Fixed, value = 320.0)),
                SetLayoutProperty("filters", LayoutProp.Gap, YamlScalarValue.Num(8.0)),
            ),
            compiled,
            EditTestOptions,
        )
        val new = result.requireNewSource()
        assertEquals(
            SpecRuDocument
                .replace(
                    topbarParagraph,
                    topbarParagraph +
                        "\nlayout:\n  sizing:\n    width:\n      type: fixed\n      value: 320\n",
                )
                .replace(
                    "- Статус из {{query.status}}\n",
                    "- Статус из {{query.status}}\nlayout:\n  gap: 8\n",
                ),
            new,
        )
    }

    @Test
    fun failingMiddleEditAbortsTheWholeBatch() {
        val compiled = compileForEdit(SpecRuDocument)
        val result = applySlmEdits(
            SpecRuDocument,
            listOf(
                SetLayoutProperty("topbar", LayoutProp.Gap, YamlScalarValue.Num(12.0)),
                // Paragraph-segment node: never addressable.
                SetText("createMission", "X"),
                SetLayoutProperty("filters", LayoutProp.Gap, YamlScalarValue.Num(8.0)),
            ),
            compiled,
            EditTestOptions,
        )
        assertFalse(result.isApplied)
        assertNull(result.newSource)
        assertNull(result.appliedRange)
        assertTrue(result.diagnostics.any { "no addressable source anchor" in it.message })
    }
}
