package io.aequicor.visualization.editor

import io.aequicor.visualization.editor.presentation.DesignEditorIntent
import io.aequicor.visualization.editor.presentation.DesignEditorState
import io.aequicor.visualization.editor.presentation.TypographyPatch
import io.aequicor.visualization.editor.presentation.createDesignEditorState
import io.aequicor.visualization.editor.presentation.reduceDesignEditor
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.engine.ir.model.DesignSeverity
import io.aequicor.visualization.engine.ir.model.DesignTextStyle
import io.aequicor.visualization.engine.ir.model.DesignUnit
import io.aequicor.visualization.engine.ir.model.TextAlignHorizontal
import io.aequicor.visualization.engine.ir.model.UnitValue
import io.aequicor.visualization.engine.ir.model.literalOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Typography write-back through the reducer: [DesignEditorIntent.UpdateTypography] merges
 * the patch into the node's CNL typography phrases in the owning SLM source and mirrors
 * onto the working document, leaving every other source byte-identical. `badge_text` is a
 * heading-anchored text node in mission-telemetry.layout.md authored with CNL typography
 * phrases (`font`, `size`, `bold`), so every field below is addressable there.
 */
class TypographyWriteBackReducerTest {

    private val nodeId = "badge_text"
    private val owningFile = "mission-telemetry.layout.md"

    private fun freshState(): DesignEditorState =
        reduceDesignEditor(
            createDesignEditorState(missionDemoDocuments()),
            DesignEditorIntent.SelectNode(nodeId),
        )

    private fun DesignEditorState.sourceOf(fileName: String): String =
        assertNotNull(sources.firstOrNull { it.fileName == fileName }, "missing source $fileName").content

    private fun DesignEditorState.textStyle(): DesignTextStyle =
        assertNotNull((document?.nodeById(nodeId)?.kind as? DesignNodeKind.Text)?.textStyle, "resolved text style")

    private fun DesignEditorState.assertWroteBack(before: DesignEditorState) {
        assertNotEquals(before.sourceOf(owningFile), sourceOf(owningFile), "owning source rewritten")
        before.sources.filterNot { it.fileName == owningFile }.forEach { source ->
            assertEquals(source.content, sourceOf(source.fileName), "${source.fileName} must stay byte-identical")
        }
        assertTrue(
            diagnostics.none { it.severity == DesignSeverity.Error },
            "write-back diagnostics: ${diagnostics.filter { it.severity == DesignSeverity.Error }}",
        )
        assertEquals(listOf(before.sources), previousSources, "source undo captured the pre-edit sources")
    }

    @Test
    fun fontSizeReplacesScalarInOwningSource() {
        val before = freshState()
        val next = reduceDesignEditor(before, DesignEditorIntent.UpdateTypography(nodeId, TypographyPatch(fontSize = 20.0)))
        assertEquals(20.0, next.textStyle().fontSize?.literalOrNull())
        assertTrue("size 20" in next.sourceOf(owningFile), "font size written as CNL")
        next.assertWroteBack(before)
    }

    @Test
    fun lineHeightPercentWritesUnitMap() {
        val before = freshState()
        val next = reduceDesignEditor(before, DesignEditorIntent.UpdateTypography(nodeId, TypographyPatch(lineHeightPercent = 140.0)))
        assertEquals(UnitValue(DesignUnit.Percent, 140.0), next.textStyle().lineHeight)
        val source = next.sourceOf(owningFile)
        assertTrue("line-height 140%" in source, "percent line height written as CNL: $source")
        next.assertWroteBack(before)
    }

    @Test
    fun letterSpacingWritesBareNumberAndAlignToken() {
        val before = freshState()
        val next = reduceDesignEditor(
            before,
            DesignEditorIntent.UpdateTypography(
                nodeId,
                TypographyPatch(letterSpacing = 1.5, alignHorizontal = TextAlignHorizontal.Center),
            ),
        )
        val style = next.textStyle()
        assertEquals(UnitValue(DesignUnit.Px, 1.5), style.letterSpacing)
        assertEquals(TextAlignHorizontal.Center, style.textAlignHorizontal)
        val source = next.sourceOf(owningFile)
        assertTrue("tracking 1.5" in source, "px letter spacing written as CNL: $source")
        assertTrue("text-align center" in source)
        next.assertWroteBack(before)
    }

    @Test
    fun preservesAuthoredFontFamilyWhenOnlySizeChanges() {
        val before = freshState()
        val next = reduceDesignEditor(before, DesignEditorIntent.UpdateTypography(nodeId, TypographyPatch(fontSize = 15.0)))
        // Field-by-field merge: the authored fontFamily/fontWeight the editor never touched survive.
        assertEquals("Inter", next.textStyle().fontFamily)
        assertEquals(700.0, next.textStyle().fontWeight?.literalOrNull())
        assertTrue("font «Inter»" in next.sourceOf(owningFile))
        next.assertWroteBack(before)
    }

    @Test
    fun openTypeFeatureWritesGroupAndPreservesAuthoredTypography() {
        val before = freshState()
        val next = reduceDesignEditor(
            before,
            DesignEditorIntent.UpdateTypography(nodeId, TypographyPatch(fontFeatures = mapOf("liga" to true))),
        )
        assertEquals(true, next.textStyle().fontFeatures["liga"])
        // Features have no surgical value span, so this goes through CnlWriter's tier-3
        // whole-sentence re-emit; the `features (…)` group lands and every authored phrase survives.
        val source = next.sourceOf(owningFile)
        assertTrue("features (liga on)" in source, "OpenType feature written as CNL: $source")
        assertTrue("font «Inter»" in source, source)
        assertTrue("size 12" in source, source)
        assertTrue("key missionTelemetry.badge.live" in source, source)
        assertEquals("Inter", next.textStyle().fontFamily)
        next.assertWroteBack(before)
    }

    @Test
    fun variableAxisWritesGroupAndRoundTrips() {
        val before = freshState()
        val next = reduceDesignEditor(
            before,
            DesignEditorIntent.UpdateTypography(nodeId, TypographyPatch(variableAxes = mapOf("wght" to 620.0))),
        )
        assertEquals(620.0, next.textStyle().variableAxes["wght"])
        val source = next.sourceOf(owningFile)
        assertTrue("axes (wght 620)" in source, "variable axis written as CNL: $source")
        next.assertWroteBack(before)
    }

    @Test
    fun nonTextNodeIsNoOp() {
        val before = freshState()
        // A frame/shape carries no textStyle; UpdateTypography must not touch any source.
        val next = reduceDesignEditor(before, DesignEditorIntent.UpdateTypography("frame_telemetry", TypographyPatch(fontSize = 20.0)))
        before.sources.forEach { source ->
            assertEquals(source.content, next.sourceOf(source.fileName), "${source.fileName} untouched by a non-text typography edit")
        }
    }
}
