package io.aequicor.visualization.engine.ir.resolve

import io.aequicor.visualization.engine.ir.model.DesignDocument
import io.aequicor.visualization.engine.ir.model.orZero
import io.aequicor.visualization.engine.ir.model.HorizontalConstraint
import io.aequicor.visualization.engine.ir.model.JustifyContent
import io.aequicor.visualization.engine.ir.model.VerticalConstraint
import io.aequicor.visualization.engine.ir.serialization.DesignParseResult
import io.aequicor.visualization.engine.ir.serialization.parseDesignDocument
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Stage 5.3 resolver: logical-to-physical lowering (padding, anchors, RTL rows). */
class ResolverRtlTest {

    private fun parse(json: String): DesignDocument =
        assertIs<DesignParseResult.Success>(parseDesignDocument(json)).document

    private fun resolveFirst(json: String, context: ResolveContext = ResolveContext()): ResolvedNode {
        val document = parse(json)
        val resolver = DesignResolver(document, context)
        return assertNotNull(resolver.resolvePage(document.pages.first()).firstOrNull())
    }

    private val paddingJson = """
        {
          "pages": [ { "id": "p", "children": [
            { "id": "root", "type": "frame",
              "layout": {
                "mode": "vertical",
                "padding": { "top": 2, "left": 99 },
                "paddingLogical": { "inlineStart": 10, "inlineEnd": 4 }
              } }
          ] } ]
        }
    """.trimIndent()

    @Test
    fun logicalPaddingMapsToPhysicalByDirection() {
        val ltr = resolveFirst(paddingJson)
        assertEquals(10.0, ltr.layout.paddingLeft, "LTR: inlineStart -> left, winning over physical padding")
        assertEquals(4.0, ltr.layout.paddingRight)
        assertEquals(2.0, ltr.layout.paddingTop, "unset logical sides fall back to physical padding")

        val rtl = resolveFirst(paddingJson, ResolveContext(locale = "ar"))
        assertEquals(4.0, rtl.layout.paddingLeft, "RTL: inlineEnd -> left")
        assertEquals(10.0, rtl.layout.paddingRight, "RTL: inlineStart -> right")
    }

    private val rowJson = """
        {
          "pages": [ { "id": "p", "children": [
            { "id": "root", "type": "frame",
              "layout": { "mode": "horizontal", "gap": 8, "justifyContent": "start" },
              "children": [
                { "id": "a", "type": "rectangle", "size": { "width": 10, "height": 10 } },
                { "id": "b", "type": "rectangle", "size": { "width": 10, "height": 10 } }
              ] }
          ] } ]
        }
    """.trimIndent()

    @Test
    fun rtlReversesHorizontalChildrenAndFlipsJustify() {
        val ltr = resolveFirst(rowJson)
        assertEquals(listOf("a", "b"), ltr.children.map { it.sourceId })
        assertEquals(JustifyContent.Start, ltr.layout.justifyContent)

        val rtl = resolveFirst(rowJson, ResolveContext(locale = "he-IL"))
        assertEquals(listOf("b", "a"), rtl.children.map { it.sourceId }, "row children reverse in RTL")
        assertEquals(JustifyContent.End, rtl.layout.justifyContent, "Start flips to End in RTL rows")
    }

    @Test
    fun rtlDoesNotReverseVerticalStacks() {
        val json = rowJson.replace(""""mode": "horizontal"""", """"mode": "vertical"""")
        val rtl = resolveFirst(json, ResolveContext(locale = "ar"))
        assertEquals(listOf("a", "b"), rtl.children.map { it.sourceId })
        assertEquals(JustifyContent.Start, rtl.layout.justifyContent)
    }

    private val anchorsJson = """
        {
          "pages": [ { "id": "p", "children": [
            { "id": "root", "type": "frame", "size": { "width": 200, "height": 100 },
              "children": [
                { "id": "pin", "type": "rectangle", "size": { "width": 40, "height": 20 },
                  "anchors": { "inlineEnd": 4, "blockStart": 10 } }
              ] }
          ] } ]
        }
    """.trimIndent()

    @Test
    fun anchorsMapToConstraintsAndOffsetsByDirection() {
        val ltrPin = assertNotNull(resolveFirst(anchorsJson).children.firstOrNull())
        assertEquals(HorizontalConstraint.Right, ltrPin.constraints.horizontal, "LTR: inlineEnd -> right")
        assertEquals(156.0, assertNotNull(ltrPin.position).x.orZero, "x measured from the parent's right edge")
        assertEquals(VerticalConstraint.Top, ltrPin.constraints.vertical)
        assertEquals(10.0, assertNotNull(ltrPin.position).y.orZero)
        assertTrue(ltrPin.layoutChild.absolute, "anchored children go out of flow")

        val rtlPin = assertNotNull(resolveFirst(anchorsJson, ResolveContext(locale = "ar")).children.firstOrNull())
        assertEquals(HorizontalConstraint.Left, rtlPin.constraints.horizontal, "RTL: inlineEnd -> left")
        assertEquals(4.0, assertNotNull(rtlPin.position).x.orZero)
    }

    @Test
    fun bothInlineAnchorsStretchBetweenEdges() {
        val json = anchorsJson.replace(
            """"anchors": { "inlineEnd": 4, "blockStart": 10 }""",
            """"anchors": { "inlineStart": 10, "inlineEnd": 30 }""",
        )
        val pin = assertNotNull(resolveFirst(json).children.firstOrNull())
        assertEquals(HorizontalConstraint.LeftRight, pin.constraints.horizontal)
        assertEquals(10.0, assertNotNull(pin.position).x.orZero)
        assertEquals(160.0, pin.size.width, "width = parent width - both inline offsets")
    }
}
