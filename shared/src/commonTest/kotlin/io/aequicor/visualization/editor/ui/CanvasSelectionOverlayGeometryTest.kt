package io.aequicor.visualization.editor.ui

import io.aequicor.visualization.engine.ir.layout.LayoutBox
import io.aequicor.visualization.engine.ir.resolve.ResolvedNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class CanvasSelectionOverlayGeometryTest {

    private fun node(id: String, rotation: Double = 0.0): ResolvedNode =
        ResolvedNode(id = id, sourceId = id, type = "frame", name = id, rotation = rotation)

    @Test
    fun multiSelectionProducesOneUnionFrame() {
        val layout = LayoutBox(
            node = node("root"),
            x = 0.0,
            y = 0.0,
            width = 400.0,
            height = 300.0,
            children = listOf(
                LayoutBox(node("left"), x = 20.0, y = 30.0, width = 80.0, height = 50.0),
                LayoutBox(node("right", rotation = 90.0), x = 180.0, y = 40.0, width = 60.0, height = 20.0),
            ),
        )

        val bounds = assertNotNull(selectionHandleBounds(layout, setOf("left", "right")))

        assertEquals(20.0, bounds.x)
        assertEquals(20.0, bounds.y)
        assertEquals(200.0, bounds.width)
        assertEquals(60.0, bounds.height)
    }

    @Test
    fun groupBoundsStillWrapAllIndividualOutlines() {
        val layout = LayoutBox(
            node = node("root"),
            x = 0.0,
            y = 0.0,
            width = 400.0,
            height = 300.0,
            children = listOf(
                LayoutBox(node("left"), x = 20.0, y = 30.0, width = 80.0, height = 50.0),
                LayoutBox(node("right"), x = 180.0, y = 40.0, width = 60.0, height = 20.0),
            ),
        )

        val bounds = assertNotNull(selectionHandleBounds(layout, setOf("left", "right")))

        assertEquals(20.0, bounds.x)
        assertEquals(30.0, bounds.y)
        assertEquals(220.0, bounds.width)
        assertEquals(50.0, bounds.height)
    }

    @Test
    fun commonFrameClaimsTransparentSpaceBetweenSelectedNodes() {
        val group = io.aequicor.visualization.editor.presentation.BoundsBox(
            x = 20.0,
            y = 30.0,
            width = 220.0,
            height = 80.0,
        )

        assertEquals(true, groupFrameContains(group, docX = 140.0, docY = 70.0))
        assertEquals(false, groupFrameContains(group, docX = 10.0, docY = 70.0))
    }

    @Test
    fun parentSizedFirstChildIsTreatedAsMarqueeBackdrop() {
        val parent = LayoutBox(
            node = node("video_preview"),
            x = 24.0,
            y = 88.0,
            width = 1064.0,
            height = 598.0,
        )
        val background = LayoutBox(
            node = node("preview_background"),
            x = 24.0,
            y = 88.0,
            width = 1064.0,
            height = 598.0,
        )
        val foreground = LayoutBox(
            node = node("preview_desktop"),
            x = 144.0,
            y = 146.0,
            width = 824.0,
            height = 464.0,
        )

        assertEquals(true, isParentSizedMarqueeBackdrop(parent, background, index = 0))
        assertEquals(false, isParentSizedMarqueeBackdrop(parent, foreground, index = 1))
    }
}
