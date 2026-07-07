package io.aequicor.visualization.engine.ir.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class DesignNodePatchTest {

    private val node = DesignNode(
        id = "panel",
        type = "frame",
        kind = DesignNodeKind.Frame,
        opacity = 0.8.bindable(),
        size = DesignSize(width = 320.0, height = 200.0),
        layout = DesignAutoLayout(mode = LayoutMode.Horizontal),
        fills = listOf(DesignPaint.Solid(DesignColor.Black.bindable())),
    )

    @Test
    fun patchAppliesOnlyNonNullGroups() {
        val patch = DesignNodePatch(
            layout = DesignAutoLayout(mode = LayoutMode.Vertical),
            visible = false.bindable(),
        )

        val patched = patch.appliedTo(node)

        assertEquals(LayoutMode.Vertical, patched.layout.mode)
        assertEquals(false, (patched.visible as Bindable.Value<Boolean>).value)
        // Untouched groups keep the node's values.
        assertEquals(0.8, (patched.opacity as Bindable.Value<Double>).value)
        assertEquals(DesignSize(width = 320.0, height = 200.0), patched.size)
        assertEquals(node.fills, patched.fills)
        assertNull(patched.strokes)
    }

    @Test
    fun emptyPatchLeavesNodeUnchanged() {
        assertEquals(node, DesignNodePatch().appliedTo(node))
    }

    @Test
    fun layoutPatchReplacesTheWholeBlock() {
        val patch = DesignNodePatch(layout = DesignAutoLayout(mode = LayoutMode.Grid))

        val patched = patch.appliedTo(node.copy(layout = node.layout.copy(wrap = true)))

        assertEquals(LayoutMode.Grid, patched.layout.mode)
        // Whole-block replacement: the authored wrap flag is not merged in.
        assertEquals(false, patched.layout.wrap)
    }

    @Test
    fun textStylePatchMergesIntoTextNodeStyle() {
        val textNode = node.copy(
            kind = DesignNodeKind.Text(
                characters = "Hello".bindable(),
                textStyle = DesignTextStyle(fontSize = 14.0.bindable()),
            ),
        )
        val patch = DesignNodePatch(textStyle = DesignTextStyle(fontWeight = 700.0.bindable()))

        val patched = patch.appliedTo(textNode)

        val text = assertIs<DesignNodeKind.Text>(patched.kind)
        assertEquals(14.0, (text.textStyle?.fontSize as Bindable.Value<Double>).value)
        assertEquals(700.0, (text.textStyle?.fontWeight as Bindable.Value<Double>).value)
    }
}
