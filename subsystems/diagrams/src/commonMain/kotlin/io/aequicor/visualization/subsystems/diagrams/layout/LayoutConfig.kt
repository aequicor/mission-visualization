package io.aequicor.visualization.subsystems.diagrams.layout

/**
 * Locked spacing presets for [autoLayout] — a couple of tuned combinations instead of
 * exposing every knob (untested combinations stay impossible).
 */
enum class DiagramLayoutPreset {
    /** The stock spacing ([DiagramLayoutConfig.Default]). */
    DEFAULT,

    /** Publication air: wider layer/node gaps and edge corridors — paper-figure spacing. */
    PUBLICATION,

    /** Dense overview: tightened gaps for large graphs on small screens. */
    COMPACT,
}

/** The tuned [DiagramLayoutConfig] behind a preset, flowing along [direction]. */
fun DiagramLayoutPreset.toLayoutConfig(
    direction: LayoutDirection = LayoutDirection.TOP_DOWN,
): DiagramLayoutConfig = when (this) {
    DiagramLayoutPreset.DEFAULT -> DiagramLayoutConfig(direction = direction)
    DiagramLayoutPreset.PUBLICATION -> DiagramLayoutConfig(
        direction = direction,
        layerGap = 112.0,
        nodeGap = 56.0,
        dummySize = 16.0,
    )
    DiagramLayoutPreset.COMPACT -> DiagramLayoutConfig(
        direction = direction,
        layerGap = 56.0,
        nodeGap = 24.0,
        dummySize = 10.0,
    )
}

/** Main flow direction of an auto-layout. */
enum class LayoutDirection {
    /** Layers/levels stack top → bottom; siblings spread horizontally. */
    TOP_DOWN,

    /** Layers/levels stack left → right; siblings spread vertically. */
    LEFT_RIGHT,
}

/**
 * Spacing knobs shared by the auto-layout algorithms.
 *
 * @param layerGap gap between consecutive layers/levels along the flow direction.
 * @param nodeGap gap between neighboring nodes within a layer/level.
 * @param containerPadding inset from a container's top-left corner to its laid-out children.
 * @param dummySize cross-axis extent reserved by each dummy vertex of a long edge
 *   (an edge spanning two or more layers) — the corridor the edge runs through.
 */
data class DiagramLayoutConfig(
    val direction: LayoutDirection = LayoutDirection.TOP_DOWN,
    val layerGap: Double = 80.0,
    val nodeGap: Double = 40.0,
    val containerPadding: Double = 24.0,
    val dummySize: Double = 12.0,
) {
    init {
        require(layerGap >= 0.0) { "layerGap must be >= 0, got $layerGap" }
        require(nodeGap >= 0.0) { "nodeGap must be >= 0, got $nodeGap" }
        require(containerPadding >= 0.0) {
            "containerPadding must be >= 0, got $containerPadding"
        }
        require(dummySize >= 0.0) { "dummySize must be >= 0, got $dummySize" }
    }

    companion object {
        val Default: DiagramLayoutConfig = DiagramLayoutConfig()
    }
}
