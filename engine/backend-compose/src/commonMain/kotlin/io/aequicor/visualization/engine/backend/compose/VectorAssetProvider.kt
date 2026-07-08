package io.aequicor.visualization.engine.backend.compose

import io.aequicor.visualization.engine.ir.model.DesignViewBox
import io.aequicor.visualization.engine.ir.model.VectorPath

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
