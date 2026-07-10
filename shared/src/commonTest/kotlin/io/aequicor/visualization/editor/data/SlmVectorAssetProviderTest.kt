package io.aequicor.visualization.editor.data

import io.aequicor.visualization.engine.ir.model.DesignAsset
import io.aequicor.visualization.engine.ir.model.DesignDocument
import io.aequicor.visualization.subsystems.figures.VectorRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SlmVectorAssetProviderTest {

    private val rectSvg = """<svg viewBox="0 0 24 24"><path d="M2 2 H22 V22 H2 Z"/></svg>"""

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
    fun ignoresBase64DataPayloads() {
        val doc = DesignDocument(
            assets = mapOf("b" to DesignAsset(type = "svg", url = "data:image/svg+xml;base64,PHN2Zz48L3N2Zz4=")),
        )
        assertNull(SlmVectorAssetProvider(document = doc).resolve(VectorRef.Svg("b")))
    }
}
