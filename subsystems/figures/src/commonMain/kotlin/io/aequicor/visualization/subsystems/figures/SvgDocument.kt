package io.aequicor.visualization.subsystems.figures

/**
 * Minimal, pure-Kotlin SVG-XML reader that lifts an `<svg>` document into a [VectorGraphic]
 * (its `<path d>` geometry plus the `viewBox`/`width`/`height`). No platform XML dependency —
 * a small hand parser over the tags the figures pipeline consumes.
 *
 * Scope (v1): `<path d="...">` (multiple paths concatenated, each an independent [VectorPath]),
 * a per-path `fill-rule` mapped to the winding rule, and the document `viewBox` (else `width`/
 * `height`). Presentation attributes (fill colours, strokes) and element transforms are ignored;
 * the geometry is what the renderer lowers. Returns null when the document carries no `<path d>`.
 *
 * Mirrors the Material-Symbols parsing in `shared/.../ui/EditorIcon.kt`, but yields the engine's
 * device-independent [VectorGraphic] rather than a Compose `ImageVector`.
 */
public fun parseSvgDocument(xml: String): VectorGraphic? {
    val svgTag = SvgTagRegex.find(xml)?.value.orEmpty()
    val svgAttributes = parseSvgAttributes(svgTag)
    val viewBox = parseSvgViewBox(svgAttributes)

    val paths = PathTagRegex.findAll(xml).mapNotNull { match ->
        val attributes = parseSvgAttributes(match.value)
        val d = attributes["d"]?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val windingRule = when (attributes["fill-rule"] ?: attributes["clip-rule"]) {
            "evenodd" -> "evenodd"
            else -> "nonzero"
        }
        VectorPath(windingRule = windingRule, d = d)
    }.toList()

    if (paths.isEmpty()) return null
    return VectorGraphic(paths = paths, viewBox = viewBox)
}

private fun parseSvgViewBox(attributes: Map<String, String>): DesignViewBox? {
    val viewBox = attributes["viewBox"]
        ?.trim()
        ?.split(SvgNumberSplitRegex)
        ?.mapNotNull { it.toDoubleOrNull() }
    if (viewBox != null && viewBox.size == 4) {
        return DesignViewBox(x = viewBox[0], y = viewBox[1], width = viewBox[2], height = viewBox[3])
    }
    val width = parseDoublePrefix(attributes["width"])
    val height = parseDoublePrefix(attributes["height"])
    if (width != null && height != null) {
        return DesignViewBox(x = 0.0, y = 0.0, width = width, height = height)
    }
    return null
}

private fun parseSvgAttributes(tag: String): Map<String, String> =
    SvgAttributeRegex.findAll(tag).associate { match ->
        match.groupValues[1] to unescapeSvgXml(match.groupValues[2])
    }

private fun unescapeSvgXml(value: String): String =
    value
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&")

private fun parseDoublePrefix(value: String?): Double? =
    value?.let { DoublePrefixRegex.find(it)?.value?.toDoubleOrNull() }

private val SvgTagRegex = Regex("""<svg\b[^>]*>""")
private val PathTagRegex = Regex("""<path\b[^>]*>""")
private val SvgAttributeRegex = Regex("""([A-Za-z_:][A-Za-z0-9_:.-]*)\s*=\s*"([^"]*)"""")
private val SvgNumberSplitRegex = Regex("""[\s,]+""")
private val DoublePrefixRegex = Regex("""[-+]?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?""")
