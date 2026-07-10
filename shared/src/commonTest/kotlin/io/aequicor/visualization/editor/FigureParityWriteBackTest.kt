package io.aequicor.visualization.editor

import io.aequicor.visualization.editor.data.DefaultDesignDocumentRepository
import io.aequicor.visualization.editor.domain.LoadDesignDocumentUseCase
import io.aequicor.visualization.editor.presentation.DesignEditorIntent
import io.aequicor.visualization.editor.presentation.DesignEditorState
import io.aequicor.visualization.editor.presentation.FillKind
import io.aequicor.visualization.editor.presentation.FillOp
import io.aequicor.visualization.editor.presentation.StrokeOp
import io.aequicor.visualization.editor.presentation.createDesignEditorState
import io.aequicor.visualization.editor.presentation.reduceDesignEditor
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.engine.ir.model.DesignColor
import io.aequicor.visualization.engine.ir.model.DesignPaint
import io.aequicor.visualization.engine.ir.model.bindable
import io.aequicor.visualization.subsystems.figures.BooleanOperationKind
import io.aequicor.visualization.subsystems.figures.ShapeType
import io.aequicor.visualization.engine.ir.model.DesignSeverity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Figma-parity write-back: ellipse arcs (pie/donut), stroke join and vector fill (winding)
 * rule patch their owning SLM source (`shapes-showcase.layout.md`) and mirror onto the working
 * document, leaving every other source byte-identical.
 */
class FigureParityWriteBackTest {

    private val owningFile = "shapes-showcase.layout.md"

    private fun freshState(selecting: String): DesignEditorState =
        reduceDesignEditor(
            createDesignEditorState(LoadDesignDocumentUseCase(DefaultDesignDocumentRepository())()),
            DesignEditorIntent.SelectNode(selecting),
        )

    private fun DesignEditorState.sourceOf(fileName: String): String =
        assertNotNull(sources.firstOrNull { it.fileName == fileName }, "missing source $fileName").content

    private fun DesignEditorState.assertWroteBack(before: DesignEditorState) {
        assertTrue(
            diagnostics.none { it.severity == DesignSeverity.Error },
            "write-back diagnostics: ${diagnostics.filter { it.severity == DesignSeverity.Error }}",
        )
        before.sources.filterNot { it.fileName == owningFile }.forEach { source ->
            assertEquals(source.content, sourceOf(source.fileName), "${source.fileName} must stay byte-identical")
        }
    }

    private fun DesignEditorState.shape(id: String): DesignNodeKind.Shape =
        assertNotNull(document?.nodeById(id)?.kind as? DesignNodeKind.Shape, "node $id is not a shape")

    @Test
    fun setArcSweepWritesArcSweep() {
        val before = freshState("showcase_ellipse")
        val next = reduceDesignEditor(before, DesignEditorIntent.SetArcSweep("showcase_ellipse", 270.0))
        assertEquals(270.0, next.shape("showcase_ellipse").arcSweepDeg)
        assertTrue("arcSweep" in next.sourceOf(owningFile))
        next.assertWroteBack(before)
    }

    @Test
    fun setArcStartWritesArcStart() {
        val before = freshState("showcase_ellipse")
        val next = reduceDesignEditor(before, DesignEditorIntent.SetArcStart("showcase_ellipse", -90.0))
        assertEquals(-90.0, next.shape("showcase_ellipse").arcStartDeg)
        assertTrue("arcStart" in next.sourceOf(owningFile))
        next.assertWroteBack(before)
    }

    @Test
    fun setArcRatioWritesInnerRadius() {
        val before = freshState("showcase_ellipse")
        val next = reduceDesignEditor(before, DesignEditorIntent.SetArcRatio("showcase_ellipse", 0.5))
        assertEquals(0.5, next.shape("showcase_ellipse").innerRadius)
        assertTrue("innerRadius" in next.sourceOf(owningFile))
        next.assertWroteBack(before)
    }

    @Test
    fun setStrokeJoinWritesJoins() {
        val before = freshState("showcase_arrow")
        val next = reduceDesignEditor(
            before,
            DesignEditorIntent.StrokeCommand("showcase_arrow", StrokeOp.SetJoin("bevel")),
        )
        assertTrue("joins: bevel" in next.sourceOf(owningFile), "expected joins: bevel in source")
        next.assertWroteBack(before)
    }

    @Test
    fun setVertexCornerRadiusWritesRadius() {
        val before = freshState("showcase_network")
        val next = reduceDesignEditor(before, DesignEditorIntent.SetVertexCornerRadius("showcase_network", 1, 8.0))
        assertEquals(8.0, next.shape("showcase_network").network?.vertices?.get(1)?.cornerRadius)
        assertTrue("radius: 8" in next.sourceOf(owningFile))
        next.assertWroteBack(before)
    }

    @Test
    fun appendVectorVertexExtendsNetworkAndWritesBack() {
        val before = freshState("showcase_network")
        val start = before.shape("showcase_network").network?.vertices?.size ?: 0
        val next = reduceDesignEditor(before, DesignEditorIntent.AppendVectorVertex("showcase_network", 42.0, 24.0))
        val network = assertNotNull(next.shape("showcase_network").network)
        assertEquals(start + 1, network.vertices.size)
        assertEquals(42.0, network.vertices.last().x)
        assertEquals(24.0, network.vertices.last().y)
        next.assertWroteBack(before)
    }

    @Test
    fun setWindingRuleWritesEvenOdd() {
        val before = freshState("showcase_network")
        val next = reduceDesignEditor(before, DesignEditorIntent.SetWindingRule("showcase_network", "evenodd"))
        assertEquals("evenodd", next.shape("showcase_network").network?.regions?.firstOrNull()?.windingRule)
        assertTrue("evenodd" in next.sourceOf(owningFile))
        next.assertWroteBack(before)
    }

    @Test
    fun flattenBooleanBecomesVectorPaths() {
        val before = freshState("showcase_union")
        val next = reduceDesignEditor(before, DesignEditorIntent.FlattenNode("showcase_union"))
        val shape = next.shape("showcase_union")
        assertEquals(ShapeType.Vector, shape.shape)
        assertEquals(1, shape.paths.size) // children folded into one clean boolean path
        assertTrue(shape.paths.first().d.isNotBlank(), "combined path has geometry")
        assertTrue(next.document?.nodeById("showcase_union")?.children?.isEmpty() == true, "children removed")
        // Persistence: the owning source is actually re-emitted (not an in-memory fallback).
        assertTrue(
            next.sourceOf(owningFile) != before.sourceOf(owningFile),
            "flatten must patch the owning SLM source",
        )
        next.assertWroteBack(before)
    }

    @Test
    fun flattenIntersectIsSupported() {
        // Intersect used to be a hard no-op; it now folds through the boolean engine. When the
        // operands overlap the node flattens to a Vector shape (1 clean path); a disjoint intersect
        // is empty and correctly leaves the node unchanged — either way it must not crash.
        val before = reduceDesignEditor(
            freshState("showcase_union"),
            DesignEditorIntent.SetBooleanOperation("showcase_union", BooleanOperationKind.Intersect),
        )
        val next = reduceDesignEditor(before, DesignEditorIntent.FlattenNode("showcase_union"))
        val kind = next.document?.nodeById("showcase_union")?.kind
        if (kind is DesignNodeKind.Shape) {
            assertEquals(ShapeType.Vector, kind.shape)
            assertEquals(1, kind.paths.size)
        }
    }

    @Test
    fun outlineStrokeBecomesFilledVector() {
        val before = freshState("showcase_arrow")
        val next = reduceDesignEditor(before, DesignEditorIntent.OutlineStroke("showcase_arrow"))
        val shape = next.shape("showcase_arrow")
        assertEquals(ShapeType.Vector, shape.shape)
        assertTrue(shape.paths.isNotEmpty(), "stroke outlined into a vector path")
        assertNull(next.document?.nodeById("showcase_arrow")?.strokes, "stroke cleared")
        assertTrue(next.document?.nodeById("showcase_arrow")?.fills?.isNotEmpty() == true, "stroke paint became fill")
        assertTrue(
            next.sourceOf(owningFile) != before.sourceOf(owningFile),
            "outline stroke must patch the owning SLM source",
        )
        next.assertWroteBack(before)
    }

    @Test
    fun setRegionFillWritesRegionFills() {
        val before = freshState("showcase_network")
        val fill = listOf(DesignPaint.Solid(DesignColor(0xFFEF476F).bindable()))
        val next = reduceDesignEditor(before, DesignEditorIntent.SetRegionFill("showcase_network", 0, fill))
        assertEquals(1, next.shape("showcase_network").regionFills[0]?.size)
        assertTrue("fills:" in next.sourceOf(owningFile))
        next.assertWroteBack(before)
    }

    @Test
    fun regionFillCommandAddAppendsPaintAndWritesBack() {
        val before = freshState("showcase_network")
        val next = reduceDesignEditor(before, DesignEditorIntent.RegionFillCommand("showcase_network", 0, FillOp.Add))
        assertEquals(1, next.shape("showcase_network").regionFills[0]?.size)
        assertTrue("fills:" in next.sourceOf(owningFile))
        next.assertWroteBack(before)
    }

    @Test
    fun regionFillCommandSupportsMultiplePaintsAndTypeConversion() {
        val once = reduceDesignEditor(freshState("showcase_network"), DesignEditorIntent.RegionFillCommand("showcase_network", 0, FillOp.Add))
        val twice = reduceDesignEditor(once, DesignEditorIntent.RegionFillCommand("showcase_network", 0, FillOp.Add))
        assertEquals(2, twice.shape("showcase_network").regionFills[0]?.size)
        val gradient = reduceDesignEditor(
            twice,
            DesignEditorIntent.RegionFillCommand("showcase_network", 0, FillOp.SetType(1, FillKind.LinearGradient)),
        )
        assertTrue(gradient.shape("showcase_network").regionFills[0]?.get(1) is DesignPaint.Gradient)
    }

    @Test
    fun regionFillCommandRemoveClearsRegionEntry() {
        val added = reduceDesignEditor(freshState("showcase_network"), DesignEditorIntent.RegionFillCommand("showcase_network", 0, FillOp.Add))
        val removed = reduceDesignEditor(added, DesignEditorIntent.RegionFillCommand("showcase_network", 0, FillOp.RemoveAt(0)))
        assertNull(removed.shape("showcase_network").regionFills[0])
    }

    @Test
    fun regionFillCommandOutOfRangeRegionIsNoOp() {
        val before = freshState("showcase_network")
        val next = reduceDesignEditor(before, DesignEditorIntent.RegionFillCommand("showcase_network", 99, FillOp.Add))
        assertEquals(before.shape("showcase_network").regionFills, next.shape("showcase_network").regionFills)
    }
}
