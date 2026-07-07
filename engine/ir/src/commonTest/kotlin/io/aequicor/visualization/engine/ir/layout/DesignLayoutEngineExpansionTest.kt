package io.aequicor.visualization.engine.ir.layout

import io.aequicor.visualization.engine.ir.resolve.DesignResolver
import io.aequicor.visualization.engine.ir.resolve.ResolvedNode
import io.aequicor.visualization.engine.ir.resolve.ResolvedText
import io.aequicor.visualization.engine.ir.serialization.DesignParseResult
import io.aequicor.visualization.engine.ir.serialization.parseDesignDocument
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Stage 5.4 layout deltas: baseline, content extent, fixed children, implicit rows, media, annotations. */
class DesignLayoutEngineExpansionTest {

    /** Deterministic baseline injected through the measurer contract. */
    private class FixedBaselineMeasurer(
        private val baseline: Double,
        private val delegate: ApproximateTextMeasurer = ApproximateTextMeasurer(),
    ) : DesignTextMeasurer {
        override fun measure(text: ResolvedText, maxWidth: Double?): MeasuredText =
            delegate.measure(text, maxWidth)

        override fun firstBaseline(text: ResolvedText, maxWidth: Double?): Double = baseline
    }

    private fun resolveFirstFrame(json: String): ResolvedNode {
        val result = parseDesignDocument(json)
        val success = assertIs<DesignParseResult.Success>(result)
        val resolver = DesignResolver(success.document)
        return assertNotNull(resolver.resolvePage(success.document.pages.first()).firstOrNull())
    }

    private fun layoutFirstFrame(
        json: String,
        engine: DesignLayoutEngine = DesignLayoutEngine(),
    ): LayoutBox = engine.layout(resolveFirstFrame(json))

    private fun LayoutBox.box(sourceId: String): LayoutBox =
        assertNotNull(findBySourceId(sourceId), "missing box '$sourceId'")

    @Test
    fun baselineAlignmentUsesMeasurerFirstBaseline() {
        val root = layoutFirstFrame(
            """
            { "pages": [ { "id": "p", "children": [
              { "id": "root", "type": "frame",
                "size": { "width": 300, "height": 60 },
                "layout": { "mode": "horizontal", "gap": 10, "alignItems": "baseline" },
                "children": [
                  { "id": "label", "type": "text", "autoResize": "widthAndHeight",
                    "characters": "Label", "textStyle": { "fontSize": 20 } },
                  { "id": "chip", "type": "rectangle", "size": { "width": 40, "height": 10 } }
                ] }
            ] } ] }
            """.trimIndent(),
            engine = DesignLayoutEngine(FixedBaselineMeasurer(baseline = 16.0)),
        )
        assertEquals(0.0, root.box("label").y, "text child anchors the shared baseline")
        assertEquals(6.0, root.box("chip").y, "non-text child aligns its bottom edge to the baseline: 16 - 10")
    }

    @Test
    fun defaultFirstBaselineIsEightyPercentOfFontSize() {
        val root = layoutFirstFrame(
            """
            { "pages": [ { "id": "p", "children": [
              { "id": "root", "type": "frame",
                "size": { "width": 300, "height": 60 },
                "layout": { "mode": "horizontal", "gap": 10, "alignItems": "baseline" },
                "children": [
                  { "id": "big", "type": "text", "autoResize": "widthAndHeight",
                    "characters": "Big", "textStyle": { "fontSize": 30 } },
                  { "id": "small", "type": "text", "autoResize": "widthAndHeight",
                    "characters": "Small", "textStyle": { "fontSize": 10 } }
                ] }
            ] } ] }
            """.trimIndent(),
        )
        assertEquals(0.0, root.box("big").y)
        assertEquals(16.0, root.box("small").y, "0.8*30 - 0.8*10")
    }

    @Test
    fun contentExtentReportsChildrenBeyondHiddenOverflow() {
        val root = layoutFirstFrame(
            """
            { "pages": [ { "id": "p", "children": [
              { "id": "root", "type": "frame",
                "size": { "width": 100, "height": 50 },
                "layout": { "mode": "vertical", "gap": 0, "clipsContent": true },
                "scroll": { "overflowY": "hidden" },
                "children": [
                  { "id": "a", "type": "rectangle", "size": { "width": 80, "height": 40 } },
                  { "id": "b", "type": "rectangle", "size": { "width": 80, "height": 80 } }
                ] }
            ] } ] }
            """.trimIndent(),
        )
        assertEquals(50.0, root.height, "hidden overflow never affects measurement")
        assertEquals(100.0, root.width)
        assertEquals(120.0, root.contentHeight, "content extent includes the overflowing child")
        assertEquals(100.0, root.contentWidth, "children narrower than the box do not shrink the extent")
        assertEquals(40.0, root.box("a").contentHeight, "a leaf box's content equals its size")
    }

    @Test
    fun fixedScrollChildrenAreFlagged() {
        val root = layoutFirstFrame(
            """
            { "pages": [ { "id": "p", "children": [
              { "id": "root", "type": "frame",
                "size": { "width": 200, "height": 100 },
                "layout": { "mode": "vertical", "gap": 0, "clipsContent": true },
                "scroll": { "overflow": "vertical", "fixedChildren": ["header"] },
                "children": [
                  { "id": "header", "type": "rectangle", "size": { "width": 200, "height": 20 } },
                  { "id": "row", "type": "rectangle", "size": { "width": 200, "height": 40 } },
                  { "id": "sticky", "type": "rectangle", "size": { "width": 200, "height": 20 },
                    "scroll": { "sticky": true } }
                ] }
            ] } ] }
            """.trimIndent(),
        )
        assertTrue(root.box("header").fixed, "listed in the parent's fixedChildren")
        assertFalse(root.box("row").fixed)
        assertTrue(root.box("sticky").fixed, "legacy sticky flag pins the node")
        assertFalse(root.fixed, "the scroll container itself is not fixed")
    }

    @Test
    fun gridGeneratesImplicitRowsClampedToMin() {
        val root = layoutFirstFrame(
            """
            { "pages": [ { "id": "p", "children": [
              { "id": "root", "type": "frame",
                "sizing": { "horizontal": "hug", "vertical": "hug" },
                "layout": { "mode": "grid",
                            "columns": [ { "type": "fixed", "value": 100 }, { "type": "fixed", "value": 100 } ],
                            "rows": { "auto": { "type": "fixed", "value": 40 }, "min": 50 },
                            "gap": { "column": 0, "row": 0 } },
                "children": [
                  { "id": "a", "type": "rectangle", "size": { "width": 90, "height": 30 } },
                  { "id": "b", "type": "rectangle", "size": { "width": 90, "height": 30 } },
                  { "id": "c", "type": "rectangle", "size": { "width": 90, "height": 30 } }
                ] }
            ] } ] }
            """.trimIndent(),
        )
        assertEquals(0.0, root.box("a").y)
        assertEquals(50.0, root.box("c").y, "second implicit row starts after the clamped 50px row")
        assertEquals(100.0, root.height, "two implicit rows, each Fixed(40) clamped to min 50")
    }

    @Test
    fun mediaHugsAssetIntrinsicSize() {
        val root = layoutFirstFrame(
            """
            {
              "assets": { "img1": { "type": "image", "url": "https://cdn/a.png", "width": 320, "height": 240 } },
              "pages": [ { "id": "p", "children": [
                { "id": "hero", "type": "media",
                  "sizing": { "horizontal": "hug", "vertical": "hug" },
                  "media": { "assetId": "img1" } }
              ] } ]
            }
            """.trimIndent(),
        )
        assertEquals(320.0, root.width, "hug width = asset intrinsic width")
        assertEquals(240.0, root.height, "hug height = asset intrinsic height")
    }

    @Test
    fun mediaWithoutIntrinsicSizeKeepsCurrentBehavior() {
        val root = layoutFirstFrame(
            """
            {
              "assets": { "img1": { "type": "image", "url": "https://cdn/a.png" } },
              "pages": [ { "id": "p", "children": [
                { "id": "hero", "type": "media",
                  "sizing": { "horizontal": "hug", "vertical": "hug" },
                  "media": { "assetId": "img1" } }
              ] } ]
            }
            """.trimIndent(),
        )
        assertEquals(0.0, root.width, "no intrinsic size falls back to the empty-node behavior")
        assertEquals(0.0, root.height)
    }

    @Test
    fun annotationsAndOverlayMetadataHaveNoGeometryEffect() {
        val json = """
            { "pages": [ { "id": "p", "children": [
              { "id": "root", "type": "frame",
                "size": { "width": 200 },
                "sizing": { "horizontal": "fixed", "vertical": "hug" },
                "layout": { "mode": "vertical", "gap": 10 },
                "guides": [ { "orientation": "vertical", "position": 100 } ],
                "layoutGrids": [ { "type": "columns", "count": 4 } ],
                "children": [
                  { "id": "a", "type": "rectangle", "size": { "width": 50, "height": 30 },
                    "interactions": [ { "trigger": "onClick", "action": { "type": "back" } } ],
                    "motion": { "ref": "spin" },
                    "exportSettings": [ { "format": "png", "scale": 2 } ] },
                  { "id": "note", "type": "annotation",
                    "annotation": { "target": "a", "text": "dev note" } },
                  { "id": "b", "type": "rectangle", "size": { "width": 50, "height": 30 } }
                ] }
            ] } ] }
        """.trimIndent()
        val root = layoutFirstFrame(json)
        assertEquals(40.0, root.box("b").y, "annotation marker leaves the flow: 30 + 10")
        assertEquals(70.0, root.height, "hug height ignores the annotation marker")
        val note = root.box("note")
        assertEquals(0.0, note.width, "annotation markers occupy zero geometry")
        assertEquals(0.0, note.height)

        val bare = layoutFirstFrame(
            """
            { "pages": [ { "id": "p", "children": [
              { "id": "root", "type": "frame",
                "size": { "width": 200 },
                "sizing": { "horizontal": "fixed", "vertical": "hug" },
                "layout": { "mode": "vertical", "gap": 10 },
                "children": [
                  { "id": "a", "type": "rectangle", "size": { "width": 50, "height": 30 } },
                  { "id": "b", "type": "rectangle", "size": { "width": 50, "height": 30 } }
                ] }
            ] } ] }
            """.trimIndent(),
        )
        assertEquals(bare.height, root.height, "guides/grids/interactions/motion/export change nothing")
        assertEquals(bare.box("b").y, root.box("b").y)
    }
}
