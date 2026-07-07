package io.aequicor.visualization.engine.ir.resolve

import io.aequicor.visualization.engine.ir.model.DesignDocument
import io.aequicor.visualization.engine.ir.model.DesignPoint
import io.aequicor.visualization.engine.ir.model.GridPlacement
import io.aequicor.visualization.engine.ir.model.GridTrack
import io.aequicor.visualization.engine.ir.model.ImageScaleMode
import io.aequicor.visualization.engine.ir.model.LayoutMode
import io.aequicor.visualization.engine.ir.model.MaskType
import io.aequicor.visualization.engine.ir.model.MediaKind
import io.aequicor.visualization.engine.ir.serialization.DesignParseResult
import io.aequicor.visualization.engine.ir.serialization.parseDesignDocument
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** Stage 5.3 resolver: media/table lowering, order, masks, carried metadata. */
class ResolverLoweringTest {

    private fun parse(json: String): DesignDocument =
        assertIs<DesignParseResult.Success>(parseDesignDocument(json)).document

    private fun resolveFirst(json: String, context: ResolveContext = ResolveContext()): Pair<ResolvedNode, DesignResolver> {
        val document = parse(json)
        val resolver = DesignResolver(document, context)
        val root = assertNotNull(resolver.resolvePage(document.pages.first()).firstOrNull())
        return root to resolver
    }

    @Test
    fun mediaLowersToPlaceholderFillWithResolvedMedia() {
        val json = """
            {
              "assets": {
                "img1": { "type": "image", "url": "https://cdn/img.png", "width": 320, "height": 240 }
              },
              "pages": [ { "id": "p", "children": [
                { "id": "hero", "type": "media",
                  "media": {
                    "assetId": "img1", "fillMode": "fit",
                    "focalPoint": { "x": 0.3, "y": 0.7 },
                    "alt": { "defaultText": "Poster" },
                    "opacity": 0.8
                  } }
              ] } ]
            }
        """.trimIndent()
        val (hero, resolver) = resolveFirst(json)
        val fill = assertIs<ResolvedPaint.Image>(hero.fills.single())
        assertEquals("img1", fill.assetId)
        assertEquals("https://cdn/img.png", fill.url)
        assertEquals(ImageScaleMode.Fit, fill.scaleMode)
        assertEquals(0.8, fill.opacity)
        val media = assertNotNull(hero.media)
        assertEquals(MediaKind.Image, media.kind)
        assertEquals(DesignPoint(0.3, 0.7), media.focalPoint)
        assertEquals("Poster", media.altText, "alt resolves through the i18n path")
        assertEquals(320.0, media.intrinsicWidth)
        assertEquals(240.0, media.intrinsicHeight)
        assertEquals(0, resolver.diagnostics.size, "expected no diagnostics: ${resolver.diagnostics}")
    }

    @Test
    fun videoMediaCarriesPlaybackFlags() {
        val json = """
            {
              "assets": { "vid1": { "type": "video", "url": "https://cdn/clip.mp4" } },
              "pages": [ { "id": "p", "children": [
                { "id": "clip", "type": "media",
                  "media": { "assetId": "vid1", "kind": "video",
                    "posterAssetId": "img_poster", "autoplay": true, "loop": true, "muted": false } }
              ] } ]
            }
        """.trimIndent()
        val (clip, _) = resolveFirst(json)
        assertIs<ResolvedPaint.Image>(clip.fills.single(), "video draws as the image placeholder for now")
        val media = assertNotNull(clip.media)
        assertEquals(MediaKind.Video, media.kind)
        assertEquals("img_poster", media.posterAssetId)
        assertEquals(true, media.autoplay)
        assertEquals(true, media.loop)
        assertEquals(false, media.muted)
    }

    @Test
    fun tableLowersToGridWithAssignedPlacements() {
        val json = """
            {
              "pages": [ { "id": "p", "children": [
                { "id": "tbl", "type": "table",
                  "table": { "rowGap": 4, "columnGap": 8 },
                  "children": [
                    { "id": "r1", "type": "frame", "children": [
                      { "id": "c11", "type": "rectangle", "size": { "width": 10, "height": 10 } },
                      { "id": "c12", "type": "rectangle", "size": { "width": 10, "height": 10 } },
                      { "id": "c13", "type": "rectangle", "size": { "width": 10, "height": 10 } }
                    ] },
                    { "id": "r2", "type": "frame", "children": [
                      { "id": "c21", "type": "rectangle", "size": { "width": 10, "height": 10 } },
                      { "id": "c22", "type": "rectangle", "size": { "width": 10, "height": 10 },
                        "gridPlacement": { "columnSpan": 2 } }
                    ] }
                  ] }
              ] } ]
            }
        """.trimIndent()
        val (table, resolver) = resolveFirst(json)
        assertEquals(LayoutMode.Grid, table.layout.mode)
        assertEquals(List(3) { GridTrack.Flex(1.0) }, table.layout.columns, "one flex track per widest row")
        assertEquals(4.0, table.layout.rowGap)
        assertEquals(8.0, table.layout.columnGap)
        assertEquals(
            listOf("c11", "c12", "c13", "c21", "c22"),
            table.children.map { it.sourceId },
            "rows flatten away; cells become direct grid children",
        )
        assertNull(table.children.map { it.sourceId }.firstOrNull { it.startsWith("r") }, "row frames are gone")
        assertEquals(GridPlacement(column = 1, row = 1), table.children[0].gridPlacement)
        assertEquals(GridPlacement(column = 2, row = 1), table.children[1].gridPlacement)
        assertEquals(GridPlacement(column = 3, row = 1), table.children[2].gridPlacement)
        assertEquals(GridPlacement(column = 1, row = 2), table.children[3].gridPlacement)
        assertEquals(
            GridPlacement(column = 2, row = 2, columnSpan = 2),
            table.children[4].gridPlacement,
            "authored spans are preserved",
        )
        assertEquals(0, resolver.diagnostics.size, "expected no diagnostics: ${resolver.diagnostics}")
    }

    @Test
    fun explicitTableColumnsWinOverWidestRow() {
        val json = """
            {
              "pages": [ { "id": "p", "children": [
                { "id": "tbl", "type": "table",
                  "table": { "columns": [ { "type": "fixed", "value": 120 }, { "type": "flex", "value": 1 } ] },
                  "children": [
                    { "id": "r1", "type": "frame", "children": [
                      { "id": "c11", "type": "rectangle", "size": { "width": 10, "height": 10 } },
                      { "id": "c12", "type": "rectangle", "size": { "width": 10, "height": 10 } }
                    ] }
                  ] }
              ] } ]
            }
        """.trimIndent()
        val (table, _) = resolveFirst(json)
        assertEquals(listOf(GridTrack.Fixed(120.0), GridTrack.Flex(1.0)), table.layout.columns)
    }

    @Test
    fun explicitOrderSortsChildrenStably() {
        val json = """
            {
              "pages": [ { "id": "p", "children": [
                { "id": "root", "type": "frame", "children": [
                  { "id": "a", "type": "frame" },
                  { "id": "b", "type": "frame", "order": -1 },
                  { "id": "c", "type": "frame" },
                  { "id": "d", "type": "frame", "order": -1 }
                ] }
              ] } ]
            }
        """.trimIndent()
        val (root, _) = resolveFirst(json)
        assertEquals(
            listOf("b", "d", "a", "c"),
            root.children.map { it.sourceId },
            "ordered nodes sort by order; equal keys (incl. unordered) keep document position",
        )
    }

    @Test
    fun isMaskNormalizesToAlphaMaskAndMetadataIsCarried() {
        val json = """
            {
              "pages": [ { "id": "p", "children": [
                { "id": "root", "type": "frame", "children": [
                  { "id": "legacy", "type": "ellipse", "isMask": true,
                    "role": "decoration", "blendMode": "multiply" },
                  { "id": "explicit", "type": "rectangle",
                    "mask": { "type": "luminance", "appliesTo": ["photo"] } },
                  { "id": "plain", "type": "rectangle" }
                ] }
              ] } ]
            }
        """.trimIndent()
        val (root, _) = resolveFirst(json)
        val legacy = assertNotNull(root.children.firstOrNull { it.sourceId == "legacy" })
        assertEquals(ResolvedMask(MaskType.Alpha, emptyList()), legacy.mask, "isMask normalizes to an alpha mask")
        assertEquals("decoration", legacy.role)
        assertEquals("multiply", legacy.blendMode)
        val explicit = assertNotNull(root.children.firstOrNull { it.sourceId == "explicit" })
        assertEquals(ResolvedMask(MaskType.Luminance, listOf("photo")), explicit.mask)
        val plain = assertNotNull(root.children.firstOrNull { it.sourceId == "plain" })
        assertNull(plain.mask)
    }

    @Test
    fun annotationNodeCarriesItsPayload() {
        val json = """
            {
              "pages": [ { "id": "p", "children": [
                { "id": "note", "type": "annotation",
                  "annotation": { "target": "hero", "text": "Uses the brand gradient", "audience": "dev" } }
              ] } ]
            }
        """.trimIndent()
        val (note, _) = resolveFirst(json)
        val annotation = assertNotNull(note.annotation)
        assertEquals("hero", annotation.target)
        assertEquals("Uses the brand gradient", annotation.text)
        assertEquals("dev", annotation.audience)
    }
}
