package io.aequicor.visualization.engine.frontend.edit

import io.aequicor.visualization.engine.ir.model.Bindable
import io.aequicor.visualization.engine.ir.model.DesignColor
import io.aequicor.visualization.engine.ir.model.DesignNode
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.engine.ir.model.DesignPaint
import io.aequicor.visualization.engine.ir.model.DesignSize
import io.aequicor.visualization.engine.ir.model.DesignSizing
import io.aequicor.visualization.subsystems.figures.ShapeType
import io.aequicor.visualization.engine.ir.model.SizingMode
import io.aequicor.visualization.engine.ir.model.bindable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * [InsertChildSubtree]: synthesizing a fresh heading section under a heading parent and under the
 * H1 root — golden inserted block, lossless outside, and a faithful recompile of the minted id,
 * kind, size and fills with no spurious `SectionTitle` i18n text.
 */
class SectionInsertTest {
    private val doc = """
        ---
        screen: s
        sourceLocale: en-US
        ---

        # Screen id root name «Screen»

        ## AutoLayout: Panel id panel column width (fill) height (hug)

        ## Frame: Sidebar id sidebar
    """.trimIndent() + "\n"

    /** A rectangle carrying a fixed size and two fills (a literal hex and a design token). */
    private fun rectangle(id: String, name: String, children: List<DesignNode> = emptyList()): DesignNode =
        DesignNode(
            id = id,
            type = "shape",
            kind = DesignNodeKind.Shape(shape = ShapeType.Rectangle),
            name = name,
            sizing = DesignSizing(horizontal = SizingMode.Fixed, vertical = SizingMode.Fixed),
            size = DesignSize(width = 120.0, height = 80.0),
            fills = listOf(
                DesignPaint.Solid(color = DesignColor(0xFFFF0000L).bindable()),
                DesignPaint.Solid(color = Bindable.VarRef("color.accent")),
            ),
            children = children,
        )


    @Test
    fun insertsUnderHeadingBeforeFollowingSibling() {
        val compiled = compileForEdit(doc)
        val result = applySlmEdit(doc, InsertChildSubtree("panel", rectangle("new_shape", "New Shape")), compiled)
        val new = result.requireNewSource()

        // A `### `-level CNL heading lands between Panel's footprint and the Sidebar heading;
        // the insert only adds bytes, everything else stays lossless.
        assertTrue("### Rectangle:" in new && "New Shape" in new, new)
        assertTrue(new.indexOf("New Shape") < new.indexOf("## Frame: Sidebar"), "inserted before Sidebar\n$new")
        assertLosslessOutside(doc, new, assertNotNull(result.appliedRange))

        val recompiled = compileForEdit(new)
        assertNoErrors(recompiled)
        val node = recompiled.requireDocument().requireNode("new_shape")
        assertEquals("shape", node.type)
        assertEquals(ShapeType.Rectangle, (node.kind as DesignNodeKind.Shape).shape)
        assertEquals(120.0, node.size.width)
        assertEquals(80.0, node.size.height)
        assertFills(node)

        // The new section is a child of the parent, not a sibling.
        assertTrue(recompiled.requireDocument().requireNode("panel").children.any { it.id == "new_shape" })
        assertNoSpuriousTitle(compiled, recompiled, "New Shape")
    }

    @Test
    fun insertsUnderRootAppendsAtEnd() {
        val compiled = compileForEdit(doc)
        val result = applySlmEdit(doc, InsertChildSubtree("root", rectangle("new_shape", "New Shape")), compiled)
        val new = result.requireNewSource()

        // Root child is one level under the H1 (`## `), appended as a CNL heading after the last section.
        assertTrue("## Rectangle:" in new && "New Shape" in new, new)
        assertTrue(new.indexOf("New Shape") > new.indexOf("## Frame: Sidebar"), "appended after last section\n$new")
        assertLosslessOutside(doc, new, assertNotNull(result.appliedRange))

        val recompiled = compileForEdit(new)
        assertNoErrors(recompiled)
        val node = recompiled.requireDocument().requireNode("new_shape")
        assertEquals(ShapeType.Rectangle, (node.kind as DesignNodeKind.Shape).shape)
        assertEquals(120.0, node.size.width)
        assertTrue(recompiled.requireDocument().requireNode("root").children.any { it.id == "new_shape" })
        assertNoSpuriousTitle(compiled, recompiled, "New Shape")
    }

    @Test
    fun insertsSubtreeWithChildRecursingHeadingLevels() {
        val compiled = compileForEdit(doc)
        val subtree = rectangle("outer", "Outer", children = listOf(rectangle("inner", "Inner")))
        val result = applySlmEdit(doc, InsertChildSubtree("panel", subtree), compiled)
        val new = result.requireNewSource()
        assertLosslessOutside(doc, new, assertNotNull(result.appliedRange))

        // The child renders one heading level deeper (`#### ` under a `### ` parent). Stable
        // headings carry the name in a `name «…»` phrase, so match on the level + id.
        assertTrue("### Rectangle: id outer" in new, new)
        assertTrue("#### Rectangle: id inner" in new, new)

        val recompiled = compileForEdit(new)
        assertNoErrors(recompiled)
        val outer = recompiled.requireDocument().requireNode("outer")
        assertEquals("outer", outer.id)
        assertTrue(outer.children.any { it.id == "inner" }, "inner should be a child of outer")
        assertTrue(recompiled.requireDocument().requireNode("panel").children.any { it.id == "outer" })
        assertNoSpuriousTitle(compiled, recompiled, "Outer")
    }

    // --- helpers ---

    private fun assertFills(node: DesignNode) {
        val fills = assertNotNull(node.fills)
        assertEquals(2, fills.size)
        val hex = fills[0] as DesignPaint.Solid
        assertEquals(DesignColor(0xFFFF0000L), (hex.color as Bindable.Value).value)
        val token = fills[1] as DesignPaint.Solid
        assertEquals("color.accent", (token.color as Bindable.VarRef).id)
    }

    private fun assertNoErrors(result: io.aequicor.visualization.engine.frontend.SlmCompileResult) {
        val errors = result.diagnostics.filter {
            it.severity == io.aequicor.visualization.engine.ir.model.DesignSeverity.Error
        }
        assertTrue(errors.isEmpty(), "unexpected errors: ${errors.joinToString { it.message }}")
    }

    /** No new i18n text keys appear, and none carries the inserted heading's name as a title. */
    private fun assertNoSpuriousTitle(
        before: io.aequicor.visualization.engine.frontend.SlmCompileResult,
        after: io.aequicor.visualization.engine.frontend.SlmCompileResult,
        headingName: String,
    ) {
        val bundle = after.requireDocument().i18n.resources["en-US"].orEmpty()
        val beforeBundle = before.requireDocument().i18n.resources["en-US"].orEmpty()
        assertEquals(beforeBundle.size, bundle.size, "a new i18n text entry appeared")
        assertTrue(bundle.values.none { headingName in it }, "spurious SectionTitle for \"$headingName\": $bundle")
    }
}
