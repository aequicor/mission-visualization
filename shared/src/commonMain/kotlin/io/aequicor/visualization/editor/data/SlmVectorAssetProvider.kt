package io.aequicor.visualization.editor.data

import io.aequicor.visualization.engine.ir.model.DesignDocument
import io.aequicor.visualization.subsystems.figures.VectorAssetProvider
import io.aequicor.visualization.subsystems.figures.VectorGraphic
import io.aequicor.visualization.subsystems.figures.VectorRef
import io.aequicor.visualization.subsystems.figures.parseSvgDocument

/**
 * A registry-backed [VectorAssetProvider]: resolves a shape's `pathRef` ([VectorRef.Svg]) or
 * `iconRef` ([VectorRef.Icon]) to real [VectorGraphic] geometry by looking the SVG source up in
 * an injected registry, then parsing it with the pure figures parser ([parseSvgDocument]).
 *
 * The document model carries no bundled SVG bytes — a `DesignAsset` holds only `type/hash/url`
 * (see `engine.ir.model.DesignAsset`) — so resolution draws on two sources:
 *  - [svgSources]: an app-supplied map from ref key to raw SVG XML. The intended wiring keys it by
 *    the bundled **editor-icons** names (`rectangle`, `ellipse`, `star`, …), so an `iconRef` whose
 *    last path segment normalises to a bundled name resolves to that icon's outline.
 *  - a `DesignAsset.url` that inlines the SVG as a `data:image/svg+xml` URI (plain/`;utf8,` form).
 *
 * When nothing matches, [resolve] returns null and the renderer's box-rect fallback stays in place.
 * Parsed graphics are memoised per [VectorRef]. Pure Kotlin — no Compose, no platform XML.
 *
 * Limitation (v1): there is no bundled per-document SVG asset store yet, so `pathRef` (e.g.
 * `assets/icons/alert.svg`) resolves only when its asset carries an inline `data:` SVG url;
 * otherwise it falls back. `iconRef` resolves against the editor-icons bundle by normalised name.
 * Base64-encoded `data:` payloads and element `transform`s are not decoded (see [parseSvgDocument]).
 */
public class SlmVectorAssetProvider(
    private val svgSources: Map<String, String> = emptyMap(),
    private val document: DesignDocument? = null,
) : VectorAssetProvider {

    private val cache = HashMap<VectorRef, VectorGraphic?>()

    override fun resolve(ref: VectorRef): VectorGraphic? {
        if (cache.containsKey(ref)) return cache[ref]
        val graphic = svgSourceFor(ref)?.let(::parseSvgDocument)
        cache[ref] = graphic
        return graphic
    }

    private fun svgSourceFor(ref: VectorRef): String? = when (ref) {
        is VectorRef.Svg -> svgSources[ref.assetId] ?: inlineSvgFromAsset(ref.assetId)
        is VectorRef.Icon -> svgSources[ref.iconRef] ?: svgSources[normalizeIconKey(ref.iconRef)]
    }

    /** Reads an inline `data:image/svg+xml` payload from the asset's url, else null. */
    private fun inlineSvgFromAsset(assetId: String): String? {
        val url = document?.assets?.get(assetId)?.url?.trim() ?: return null
        return when {
            url.startsWith("<svg", ignoreCase = true) -> url
            url.startsWith("data:image/svg+xml", ignoreCase = true) -> {
                val comma = url.indexOf(',')
                val header = if (comma >= 0) url.substring(0, comma) else ""
                val payload = if (comma >= 0) url.substring(comma + 1) else return null
                // Base64 payloads are out of scope for v1; only plain / ;utf8, text is decoded.
                if (header.contains("base64", ignoreCase = true)) null else decodePercent(payload)
            }
            else -> null
        }
    }

    private companion object {
        /**
         * Best-effort mapping of a logical `iconRef` (`ds/Icon/Alert`, `assets/icons/Star`) to a
         * bundled editor-icon name: last path segment, camel/Pascal boundaries to `_`, lowercased.
         */
        fun normalizeIconKey(iconRef: String): String =
            iconRef.substringAfterLast('/')
                .replace(Regex("([a-z0-9])([A-Z])"), "$1_$2")
                .replace('-', '_')
                .replace(' ', '_')
                .lowercase()

        fun decodePercent(value: String): String =
            if ('%' !in value) value else runCatching { percentDecode(value) }.getOrDefault(value)

        fun percentDecode(value: String): String = buildString {
            var i = 0
            while (i < value.length) {
                val c = value[i]
                if (c == '%' && i + 2 < value.length) {
                    val code = value.substring(i + 1, i + 3).toInt(16)
                    append(code.toChar())
                    i += 3
                } else {
                    append(c)
                    i++
                }
            }
        }
    }
}
