package io.aequicor.visualization.editor.data

import io.aequicor.visualization.engine.ir.model.DesignAsset
import io.aequicor.visualization.engine.ir.model.DesignDocument
import io.aequicor.visualization.subsystems.figures.VectorRef
import io.aequicor.visualization.subsystems.figures.graphicGeometry
import io.aequicor.visualization.subsystems.figures.parseSvgDocument
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SlmVectorAssetProviderTest {

    private val rectSvg = """<svg viewBox="0 0 24 24"><path d="M2 2 H22 V22 H2 Z"/></svg>"""

    // Verbatim contents of the bundled editor-icons SVGs (Material Symbols) the loader ships;
    // see shared/.../composeResources/files/editor-icons/{rectangle,star}.svg. Keyed by bare name.
    private val bundledRectangleSvg =
        """<svg xmlns="http://www.w3.org/2000/svg" height="24" viewBox="0 -960 960 960" width="24"><path d="M200-720h560v480H200v-480Z"/></svg>"""
    private val bundledStarSvg =
        """<svg xmlns="http://www.w3.org/2000/svg" height="24" viewBox="0 -960 960 960" width="24"><path d="m354-287 126-76 126 77-33-144 111-96-146-13-58-136-58 135-146 13 111 97-33 143ZM233-120l65-281L80-590l288-25 112-265 112 265 288 25-218 189 65 281-247-149-247 149Zm247-350Z"/></svg>"""

    @Test
    fun resolvesIconRefByNormalizedLastSegment() {
        val provider = SlmVectorAssetProvider(svgSources = mapOf("rectangle" to rectSvg))
        val graphic = assertNotNull(provider.resolve(VectorRef.Icon("ds/Icon/Rectangle")))
        assertEquals(1, graphic.paths.size)
        assertEquals(24.0, graphic.viewBox?.width)
    }

    @Test
    fun normalizesCamelAndKebabIconNames() {
        val provider = SlmVectorAssetProvider(svgSources = mapOf("align_horizontal_left" to rectSvg))
        assertNotNull(provider.resolve(VectorRef.Icon("AlignHorizontalLeft")))
        assertNotNull(provider.resolve(VectorRef.Icon("assets/icons/align-horizontal-left")))
    }

    @Test
    fun resolvesSvgRefByAssetIdKey() {
        val provider = SlmVectorAssetProvider(svgSources = mapOf("assets/icons/box.svg" to rectSvg))
        assertNotNull(provider.resolve(VectorRef.Svg("assets/icons/box.svg")))
    }

    @Test
    fun resolvesSvgRefFromInlineDataUrl() {
        val doc = DesignDocument(
            assets = mapOf(
                "a1" to DesignAsset(type = "svg", url = "data:image/svg+xml;utf8,$rectSvg"),
            ),
        )
        val provider = SlmVectorAssetProvider(document = doc)
        assertNotNull(provider.resolve(VectorRef.Svg("a1")))
    }

    @Test
    fun returnsNullForUnknownRefLeavingBoxFallback() {
        val provider = SlmVectorAssetProvider(svgSources = mapOf("rectangle" to rectSvg))
        assertNull(provider.resolve(VectorRef.Icon("ds/Icon/Unknown")))
        assertNull(provider.resolve(VectorRef.Svg("missing")))
    }

    @Test
    fun bundledIconXmlParsesToGeometryAndResolvesEndToEnd() {
        // 1. parseSvgDocument on the real bundled icon XML yields a graphic with >= 1 path.
        val rectGraphic = assertNotNull(parseSvgDocument(bundledRectangleSvg))
        assertTrue(rectGraphic.paths.isNotEmpty())
        assertEquals(960.0, rectGraphic.viewBox?.width)
        assertNotNull(graphicGeometry(rectGraphic))

        // 2. The provider, keyed by bare bundle name (what the loader produces), resolves an
        //    iconRef to that geometry instead of the box fallback.
        val provider = SlmVectorAssetProvider(
            svgSources = mapOf(
                "rectangle" to bundledRectangleSvg,
                "star" to bundledStarSvg,
            ),
        )
        val resolved = assertNotNull(provider.resolve(VectorRef.Icon("ds/Icon/Rectangle")))
        assertNotNull(graphicGeometry(resolved))
        assertNotNull(graphicGeometry(assertNotNull(provider.resolve(VectorRef.Icon("star")))))
    }

    @Test
    fun ignoresBase64DataPayloads() {
        val doc = DesignDocument(
            assets = mapOf("b" to DesignAsset(type = "svg", url = "data:image/svg+xml;base64,PHN2Zz48L3N2Zz4=")),
        )
        assertNull(SlmVectorAssetProvider(document = doc).resolve(VectorRef.Svg("b")))
    }
}
