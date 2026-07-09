package io.aequicor.visualization.engine.ir.projection

import io.aequicor.visualization.engine.ir.layout.ApproximateTextMeasurer
import io.aequicor.visualization.engine.ir.layout.DesignLayoutEngine
import io.aequicor.visualization.engine.ir.model.DesignDocument
import io.aequicor.visualization.engine.ir.resolve.DesignResolver
import io.aequicor.visualization.engine.ir.serialization.DesignParseResult
import io.aequicor.visualization.engine.ir.serialization.parseDesignDocument
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CanvasProjectionTest {

    private val document: DesignDocument = run {
        val result = parseDesignDocument(
            """
            {
              "pages": [
                { "id": "screen1", "children": [
                  { "id": "frame_1", "type": "frame", "name": "Screen One",
                    "size": { "width": 400, "height": 300 },
                    "sizing": { "horizontal": "fixed", "vertical": "fixed" },
                    "children": [
                      { "id": "cta", "type": "frame", "name": "CTA",
                        "position": { "x": 20, "y": 20 },
                        "size": { "width": 120, "height": 44 },
                        "sizing": { "horizontal": "fixed", "vertical": "fixed" },
                        "interactions": [ { "trigger": "onClick",
                          "actions": [ { "type": "navigate", "to": "screen2" } ] } ] }
                    ] }
                ] },
                { "id": "screen2", "children": [
                  { "id": "frame_2", "type": "frame", "name": "Screen Two",
                    "size": { "width": 400, "height": 300 },
                    "sizing": { "horizontal": "fixed", "vertical": "fixed" } }
                ] }
              ]
            }
            """.trimIndent(),
        )
        assertIs<DesignParseResult.Success>(result).document
    }

    private fun composer() = ScreenComposer(document, DesignLayoutEngine(ApproximateTextMeasurer()))

    @Test
    fun composeMatchesCanonicalResolveLayoutPath() {
        // Golden: ScreenComposer must produce byte-identical geometry to the DesignArtboard
        // path (DesignResolver(document).resolveNodeTree(root) -> engine.layout(...)).
        val engine = DesignLayoutEngine(ApproximateTextMeasurer())
        val root = document.pageById("screen1")!!.children.first()
        val expected = engine.layout(DesignResolver(document).resolveNodeTree(root)!!)

        val composed = assertNotNull(ScreenComposer(document, engine).compose("screen1"))
        assertEquals("screen1", composed.screenId)
        assertEquals("frame_1", composed.rootSourceId)
        assertEquals(expected, composed.root, "composer output must equal the canonical resolve->layout tree")
    }

    @Test
    fun screenIndexResolvesPageIdScreenMetaAndNodeId() {
        val index = ScreenIndex(document)
        assertEquals("frame_1", index.rootFrameFor("screen1")?.id)
        assertEquals("frame_2", index.rootFrameFor("screen2")?.id)
        // A node-id target resolves to that node directly.
        assertEquals("cta", index.rootFrameFor("cta")?.id)
        assertNull(index.rootFrameFor("does_not_exist"))
        assertNull(index.rootFrameFor(""))
        assertEquals(listOf("screen1", "screen2"), index.screenIds)
        // screenIdFor collapses a node-id target to its owning page id.
        assertEquals("screen1", index.screenIdFor("cta"))
        assertEquals("screen2", index.screenIdFor("screen2"))
    }

    @Test
    fun canvasProjectionCarriesEditorStateAndNeverExecutesInteractions() {
        val input = CanvasProjectionInput(
            screenId = "screen1",
            selectedSourceIds = setOf("cta"),
            hoveredSourceId = "frame_1",
        )
        val model = assertNotNull(CanvasProjection.project(input, composer()))
        assertEquals("screen1", model.screenId)
        assertEquals(setOf("cta"), model.selectedSourceIds)
        assertEquals("frame_1", model.hoveredSourceId)
        // The clickable node is present in the model but the projection carries no execution:
        // the model is just a laid-out tree, so Canvas-mode click can only select.
        assertNotNull(model.root.findBySourceId("cta"))
        assertTrue(model.root.node.sourceId == "frame_1")
    }

    @Test
    fun composeReturnsNullForUnknownScreen() {
        assertNull(composer().compose("nope"))
    }
}
