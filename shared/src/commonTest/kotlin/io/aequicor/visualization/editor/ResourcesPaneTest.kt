package io.aequicor.visualization.editor

import io.aequicor.visualization.editor.ui.collectResourceImageRefs
import io.aequicor.visualization.editor.ui.firstNodeUsingRef
import io.aequicor.visualization.editor.ui.resourceUsageByRef
import io.aequicor.visualization.engine.ir.model.DesignDocument
import io.aequicor.visualization.engine.ir.model.DesignMedia
import io.aequicor.visualization.engine.ir.model.DesignNode
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.engine.ir.model.DesignPage
import io.aequicor.visualization.engine.ir.model.DesignPaint
import io.aequicor.visualization.engine.ir.model.ImageScaleMode
import io.aequicor.visualization.engine.ir.model.bindable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Data logic behind the Resources tab: which `res/…` images the document uses, how often, and where. */
class ResourcesPaneTest {

    private fun mediaNode(id: String, ref: String) = DesignNode(
        id = id,
        type = "media",
        kind = DesignNodeKind.Media(DesignMedia(assetId = ref.bindable(), fillMode = ImageScaleMode.Fill)),
    )

    private fun fillNode(id: String, ref: String) = DesignNode(
        id = id,
        type = "frame",
        kind = DesignNodeKind.Frame,
        fills = listOf(DesignPaint.Image(assetId = ref)),
    )

    private fun document(vararg nodes: DesignNode): DesignDocument {
        val frame = DesignNode(id = "frame", type = "frame", kind = DesignNodeKind.Frame, children = nodes.toList())
        return DesignDocument(pages = listOf(DesignPage(id = "p1", children = listOf(frame))))
    }

    @Test
    fun collectsMediaAndImageFillRefs() {
        val doc = document(
            mediaNode("m1", "res/logo.png"),
            fillNode("r1", "res/logo.png"), // same ref again
            fillNode("r2", "res/bg.png"),
            DesignNode(id = "plain", type = "frame", kind = DesignNodeKind.Frame), // no resource
        )
        assertEquals(setOf("res/logo.png", "res/bg.png"), collectResourceImageRefs(doc))
    }

    @Test
    fun ignoresNonResourceAssetIds() {
        val doc = document(
            mediaNode("m1", "https://example.com/a.png"),
            fillNode("r1", "data:image/png;base64,AAAA"),
        )
        assertTrue(collectResourceImageRefs(doc).isEmpty(), "only res/ refs are project resources")
    }

    @Test
    fun countsUsagesPerRef() {
        val doc = document(
            mediaNode("m1", "res/logo.png"),
            fillNode("r1", "res/logo.png"),
            fillNode("r2", "res/bg.png"),
        )
        assertEquals(mapOf("res/logo.png" to 2, "res/bg.png" to 1), resourceUsageByRef(doc))
    }

    @Test
    fun findsFirstNodeUsingRefInDocumentOrder() {
        val doc = document(
            mediaNode("m1", "res/logo.png"),
            fillNode("r1", "res/logo.png"),
            fillNode("r2", "res/bg.png"),
        )
        assertEquals("m1", firstNodeUsingRef(doc, "res/logo.png"))
        assertEquals("r2", firstNodeUsingRef(doc, "res/bg.png"))
        assertNull(firstNodeUsingRef(doc, "res/missing.png"))
        assertNull(firstNodeUsingRef(null, "res/logo.png"))
    }
}
