package io.aequicor.visualization.engine.frontend.frontmatter

import io.aequicor.visualization.engine.frontend.SlmLocale

/**
 * Screen-level metadata from the SLM frontmatter. Frontmatter contributes only
 * screen defaults; per-node properties live in typed blocks next to their anchors.
 */
data class SlmFrontmatter(
    val screen: String = "",
    val page: String = "",
    val sourceLocale: SlmLocale? = null,
    val targetLocales: List<SlmLocale> = emptyList(),
    /** Mode defaults keyed by dimension: `density`, `platform`, `theme`. */
    val modes: Map<String, String> = emptyMap(),
    val frame: SlmFrame? = null,
    val canvas: SlmCanvas? = null,
    val flow: SlmFlow? = null,
    val breakpoints: List<SlmBreakpoint> = emptyList(),
    val libraries: List<SlmLibrary> = emptyList(),
)

data class SlmFrame(
    val preset: String = "",
    val width: Double? = null,
    val height: Double? = null,
)

data class SlmCanvas(
    val section: String = "",
    val x: Double? = null,
    val y: Double? = null,
)

data class SlmFlow(
    val id: String = "",
    val node: String = "",
    val next: List<String> = emptyList(),
)

data class SlmBreakpoint(
    val id: String,
    val minWidth: Double? = null,
    val maxWidth: Double? = null,
)

data class SlmLibrary(
    val id: String,
    val source: String,
)
