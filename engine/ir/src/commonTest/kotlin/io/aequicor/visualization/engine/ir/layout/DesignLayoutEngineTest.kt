package io.aequicor.visualization.engine.ir.layout

import io.aequicor.visualization.engine.ir.serialization.DesignParseResult
import io.aequicor.visualization.engine.ir.serialization.parseDesignDocument
import io.aequicor.visualization.engine.ir.resolve.DesignResolver
import io.aequicor.visualization.engine.ir.resolve.ResolvedNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DesignLayoutEngineTest {

    private val engine = DesignLayoutEngine()

    private fun layoutFirstFrame(json: String, width: Double? = null, height: Double? = null): LayoutBox {
        val result = parseDesignDocument(json)
        val success = assertIs<DesignParseResult.Success>(result)
        val resolver = DesignResolver(success.document)
        val root: ResolvedNode = assertNotNull(resolver.resolvePage(success.document.pages.first()).firstOrNull())
        return engine.layout(root, width, height)
    }

    private fun LayoutBox.box(sourceId: String): LayoutBox =
        assertNotNull(findBySourceId(sourceId), "missing box '$sourceId'")

    @Test
    fun verticalAutoLayoutStacksWithGapAndPadding() {
        val root = layoutFirstFrame(
            """
            { "pages": [ { "id": "p", "children": [
              { "id": "root", "type": "frame",
                "size": { "width": 200, "height": 400 },
                "layout": { "mode": "vertical", "gap": 10,
                            "padding": { "top": 20, "right": 16, "bottom": 20, "left": 16 },
                            "alignItems": "stretch" },
                "children": [
                  { "id": "a", "type": "rectangle", "sizing": { "horizontal": "fill", "vertical": "fixed" }, "size": { "height": 50 } },
                  { "id": "b", "type": "rectangle", "sizing": { "horizontal": "fill", "vertical": "fixed" }, "size": { "height": 30 } }
                ] }
            ] } ] }
            """.trimIndent(),
        )
        val a = root.box("a")
        val b = root.box("b")
        assertEquals(16.0, a.x)
        assertEquals(20.0, a.y)
        assertEquals(168.0, a.width, "fill width = 200 - 16 - 16")
        assertEquals(80.0, b.y, "b starts after a + gap")
    }

    @Test
    fun horizontalFillChildrenShareRemainingSpaceEqually() {
        val root = layoutFirstFrame(
            """
            { "pages": [ { "id": "p", "children": [
              { "id": "root", "type": "frame",
                "size": { "width": 340, "height": 100 },
                "layout": { "mode": "horizontal", "gap": 20 },
                "children": [
                  { "id": "fixed", "type": "rectangle", "size": { "width": 100, "height": 40 } },
                  { "id": "f1", "type": "rectangle", "sizing": { "horizontal": "fill" }, "size": { "height": 40 } },
                  { "id": "f2", "type": "rectangle", "sizing": { "horizontal": "fill" }, "size": { "height": 40 } }
                ] }
            ] } ] }
            """.trimIndent(),
        )
        assertEquals(100.0, root.box("f1").width, "(340 - 100 - 2*20) / 2")
        assertEquals(100.0, root.box("f2").width)
        assertEquals(240.0, root.box("f2").x)
    }

    @Test
    fun hugContainerWrapsContentHeight() {
        val root = layoutFirstFrame(
            """
            { "pages": [ { "id": "p", "children": [
              { "id": "root", "type": "frame",
                "size": { "width": 200 },
                "sizing": { "horizontal": "fixed", "vertical": "hug" },
                "layout": { "mode": "vertical", "gap": 10, "padding": { "top": 5, "bottom": 5 } },
                "children": [
                  { "id": "a", "type": "rectangle", "size": { "width": 50, "height": 40 } },
                  { "id": "b", "type": "rectangle", "size": { "width": 50, "height": 60 } }
                ] }
            ] } ] }
            """.trimIndent(),
        )
        assertEquals(120.0, root.height, "5 + 40 + 10 + 60 + 5")
    }

    @Test
    fun autoGapDistributesSpaceBetweenItems() {
        val root = layoutFirstFrame(
            """
            { "pages": [ { "id": "p", "children": [
              { "id": "root", "type": "frame",
                "size": { "width": 300, "height": 60 },
                "layout": { "mode": "horizontal", "gap": "auto", "alignItems": "center" },
                "children": [
                  { "id": "left", "type": "rectangle", "size": { "width": 60, "height": 20 } },
                  { "id": "right", "type": "rectangle", "size": { "width": 40, "height": 20 } }
                ] }
            ] } ] }
            """.trimIndent(),
        )
        assertEquals(0.0, root.box("left").x)
        assertEquals(260.0, root.box("right").x, "right item pushed to the far edge")
        assertEquals(20.0, root.box("left").y, "centered on cross axis: (60 - 20) / 2")
    }

    @Test
    fun constraintsRemapWhenParentResizes() {
        val json = """
            { "pages": [ { "id": "p", "children": [
              { "id": "root", "type": "frame",
                "size": { "width": 400, "height": 200 },
                "layout": { "mode": "none" },
                "children": [
                  { "id": "pinRight", "type": "rectangle",
                    "position": { "x": 340, "y": 10 }, "size": { "width": 40, "height": 20 },
                    "constraints": { "horizontal": "right", "vertical": "top" } },
                  { "id": "stretch", "type": "rectangle",
                    "position": { "x": 20, "y": 50 }, "size": { "width": 360, "height": 20 },
                    "constraints": { "horizontal": "leftRight", "vertical": "top" } },
                  { "id": "centered", "type": "rectangle",
                    "position": { "x": 180, "y": 90 }, "size": { "width": 40, "height": 20 },
                    "constraints": { "horizontal": "center", "vertical": "top" } },
                  { "id": "scaled", "type": "rectangle",
                    "position": { "x": 100, "y": 130 }, "size": { "width": 200, "height": 20 },
                    "constraints": { "horizontal": "scale", "vertical": "top" } }
                ] }
            ] } ] }
        """.trimIndent()

        val resized = layoutFirstFrame(json, width = 600.0, height = 200.0)
        assertEquals(540.0, resized.box("pinRight").x, "right-pinned keeps distance to right edge")
        assertEquals(560.0, resized.box("stretch").width, "leftRight stretches by the delta")
        assertEquals(280.0, resized.box("centered").x, "center keeps offset from parent center")
        assertEquals(150.0, resized.box("scaled").x, "scale multiplies position")
        assertEquals(300.0, resized.box("scaled").width, "scale multiplies size")
    }

    @Test
    fun absoluteChildLeavesSiblingFlowUntouched() {
        val root = layoutFirstFrame(
            """
            { "pages": [ { "id": "p", "children": [
              { "id": "root", "type": "frame",
                "size": { "width": 300, "height": 100 },
                "layout": { "mode": "vertical", "gap": 10 },
                "children": [
                  { "id": "flow1", "type": "rectangle", "size": { "width": 50, "height": 30 } },
                  { "id": "badge", "type": "rectangle",
                    "layoutChild": { "absolute": true },
                    "position": { "x": 260, "y": 8 }, "size": { "width": 30, "height": 16 },
                    "constraints": { "horizontal": "right", "vertical": "top" } },
                  { "id": "flow2", "type": "rectangle", "size": { "width": 50, "height": 30 } }
                ] }
            ] } ] }
            """.trimIndent(),
        )
        assertEquals(0.0, root.box("flow1").y)
        assertEquals(40.0, root.box("flow2").y, "flow ignores the absolute badge")
        assertEquals(260.0, root.box("badge").x)
        assertEquals(8.0, root.box("badge").y)
    }

    @Test
    fun gridResolvesFixedFlexTracksAndSpans() {
        val root = layoutFirstFrame(
            """
            { "pages": [ { "id": "p", "children": [
              { "id": "root", "type": "frame",
                "size": { "width": 460, "height": 300 },
                "layout": { "mode": "grid",
                            "columns": [ { "type": "fixed", "value": 100 }, { "type": "flex", "value": 1 }, { "type": "flex", "value": 1 } ],
                            "rows": [ { "type": "fixed", "value": 60 }, { "type": "hug" } ],
                            "gap": { "column": 20, "row": 10 } },
                "children": [
                  { "id": "header", "type": "rectangle",
                    "gridPlacement": { "column": 1, "row": 1, "columnSpan": 3 },
                    "sizing": { "horizontal": "fill", "vertical": "fill" } },
                  { "id": "auto1", "type": "rectangle", "size": { "width": 90, "height": 40 } },
                  { "id": "auto2", "type": "rectangle", "size": { "width": 90, "height": 40 } }
                ] }
            ] } ] }
            """.trimIndent(),
        )
        val header = root.box("header")
        assertEquals(460.0, header.width, "span of all three tracks + gaps")
        assertEquals(60.0, header.height)
        val auto1 = root.box("auto1")
        val auto2 = root.box("auto2")
        assertEquals(0.0, auto1.x, "auto-placement starts in the first free cell of row 2")
        assertEquals(70.0, auto1.y, "row 1 height + row gap")
        assertEquals(120.0, auto2.x, "second column offset = 100 + 20")
    }

    @Test
    fun minMaxClampComputedSizesAndRedistributeFreedSpace() {
        val root = layoutFirstFrame(
            """
            { "pages": [ { "id": "p", "children": [
              { "id": "root", "type": "frame",
                "size": { "width": 500, "height": 100 },
                "layout": { "mode": "horizontal", "gap": 0 },
                "children": [
                  { "id": "capped", "type": "rectangle",
                    "sizing": { "horizontal": "fill" }, "size": { "height": 40 },
                    "maxSize": { "width": 120 } },
                  { "id": "floored", "type": "rectangle",
                    "sizing": { "horizontal": "fill" }, "size": { "height": 40 },
                    "minSize": { "width": 300 } }
                ] }
            ] } ] }
            """.trimIndent(),
        )
        assertEquals(120.0, root.box("capped").width)
        assertEquals(380.0, root.box("floored").width, "space freed by the max clamp goes to the other fill")
    }

    @Test
    fun hugHeightHorizontalStretchKeepsChildrenNaturalHeights() {
        val root = layoutFirstFrame(
            """
            { "pages": [ { "id": "p", "children": [
              { "id": "root", "type": "frame",
                "size": { "width": 200 },
                "sizing": { "horizontal": "fixed", "vertical": "hug" },
                "layout": { "mode": "horizontal", "gap": 10, "alignItems": "stretch",
                            "padding": { "top": 5, "bottom": 5 } },
                "children": [
                  { "id": "a", "type": "rectangle", "size": { "width": 50, "height": 40 } },
                  { "id": "b", "type": "rectangle", "size": { "width": 50, "height": 24 } }
                ] }
            ] } ] }
            """.trimIndent(),
        )
        assertEquals(50.0, root.height, "hug height = tallest child + padding")
        assertEquals(40.0, root.box("a").height)
        assertEquals(40.0, root.box("b").height, "stretch child grows to the line height")
    }

    @Test
    fun hugWidthGridMeasuresHugTracksByContent() {
        val root = layoutFirstFrame(
            """
            { "pages": [ { "id": "p", "children": [
              { "id": "root", "type": "frame",
                "sizing": { "horizontal": "hug", "vertical": "hug" },
                "layout": { "mode": "grid",
                            "columns": [ { "type": "hug" }, { "type": "hug" } ],
                            "gap": { "column": 20, "row": 0 } },
                "children": [
                  { "id": "a", "type": "rectangle", "size": { "width": 90, "height": 30 } },
                  { "id": "b", "type": "rectangle", "size": { "width": 90, "height": 30 } }
                ] }
            ] } ] }
            """.trimIndent(),
        )
        assertEquals(200.0, root.width, "hug width = 90 + 20 + 90")
        assertEquals(110.0, root.box("b").x)
    }

    @Test
    fun textHugsMeasuredSizeAndWrapsAtFillWidth() {
        val root = layoutFirstFrame(
            """
            { "pages": [ { "id": "p", "children": [
              { "id": "root", "type": "frame",
                "size": { "width": 100 },
                "sizing": { "horizontal": "fixed", "vertical": "hug" },
                "layout": { "mode": "vertical", "gap": 0, "alignItems": "stretch" },
                "children": [
                  { "id": "long", "type": "text", "autoResize": "height",
                    "sizing": { "horizontal": "fill", "vertical": "hug" },
                    "characters": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                    "textStyle": { "fontSize": 10 } }
                ] }
            ] } ] }
            """.trimIndent(),
        )
        val text = root.box("long")
        assertEquals(100.0, text.width)
        assertTrue(text.height > 12.0, "40 chars at ~6px cannot fit one 100px line: ${text.height}")
        assertEquals(root.height, text.height, "hug root follows wrapped text height")
    }

}
