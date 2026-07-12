package io.aequicor.visualization.editor.platform

// SVG rasterization ships web-only in v1; desktop renders a placeholder for SVG media.
internal actual suspend fun rasterizeSvgToPng(svgBytes: ByteArray): ByteArray? = null
