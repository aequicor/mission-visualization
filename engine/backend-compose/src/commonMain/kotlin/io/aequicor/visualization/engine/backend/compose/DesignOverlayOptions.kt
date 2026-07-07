package io.aequicor.visualization.engine.backend.compose

/**
 * Editor overlays drawn on top of the artboard content; everything is off by
 * default so the plain render stays clean.
 *
 * - [showGuides] — ruler guides as full-length hairlines at their positions.
 * - [showLayoutGrids] — layout grid definitions (columns/rows/grid) as ~8%-alpha
 *   strips or lines; they never affect layout.
 * - [showAnnotations] — node-level handoff annotations as small numbered pins.
 */
data class DesignOverlayOptions(
    val showGuides: Boolean = false,
    val showLayoutGrids: Boolean = false,
    val showAnnotations: Boolean = false,
) {
    val anyEnabled: Boolean
        get() = showGuides || showLayoutGrids || showAnnotations
}
