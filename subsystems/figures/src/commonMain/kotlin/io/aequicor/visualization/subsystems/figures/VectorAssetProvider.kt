package io.aequicor.visualization.subsystems.figures

/**
 * The device-independent shape data an app resolves for an external SVG asset (`pathRef`)
 * or a design-system icon (`iconRef`): the [VectorPath]s plus an optional [DesignViewBox].
 * The engine holds no SVG bytes — a `DesignAsset` carries only hash/url — so the app parses
 * the SVG XML and hands the engine only the geometry it consumes.
 */
data class VectorGraphic(
    val paths: List<VectorPath>,
    val viewBox: DesignViewBox?,
)

/** A reference the renderer asks the app to dereference into a [VectorGraphic]. */
sealed interface VectorRef {
    /** External SVG asset addressed by its asset id (a shape's `pathRef`). */
    data class Svg(val assetId: String) : VectorRef

    /** Design-system icon addressed by its icon ref (a shape's `iconRef`). */
    data class Icon(val iconRef: String) : VectorRef
}

/**
 * Injected by the app to dereference a [VectorRef] into drawable geometry. Returns null when
 * the reference is unknown or unavailable, leaving the renderer's box-rect fallback in place.
 */
fun interface VectorAssetProvider {
    fun resolve(ref: VectorRef): VectorGraphic?
}

/** No-op provider: resolves nothing. Keeps callers that supply no assets rendering unchanged. */
val NoVectorAssets: VectorAssetProvider = VectorAssetProvider { null }

/**
 * Builds the geometry IR from a provider-supplied [VectorGraphic], mirroring the engine's inline
 * path lowering: each [VectorPath] `d` parsed via [parseSvgPathToGeometry] and concatenated, fill
 * rule from the first path's winding rule, and the source view box taken from the graphic (else the
 * parsed path bounds). Returns null when the graphic carries no drawable geometry.
 */
fun graphicGeometry(graphic: VectorGraphic): PathGeometry? {
    val firstPath = graphic.paths.firstOrNull() ?: return null
    val fillRule = if (firstPath.windingRule == "evenodd") PathFillRule.EvenOdd else PathFillRule.NonZero
    val commands = graphic.paths.flatMap { parseSvgPathToGeometry(it.d).commands }
    if (commands.isEmpty()) return null
    val geometry = PathGeometry(commands, fillRule)
    return geometry.copy(sourceViewBox = graphic.viewBox.toRectD() ?: geometry.bounds())
}
