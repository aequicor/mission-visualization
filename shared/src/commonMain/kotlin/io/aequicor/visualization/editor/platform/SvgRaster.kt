package io.aequicor.visualization.editor.platform

/**
 * Rasterizes an SVG document ([svgBytes]) to PNG bytes for display, or null when the platform
 * cannot rasterize SVG (v1 ships web only; other platforms render a placeholder instead). The
 * caller decodes the returned PNG with the common `decodeToImageBitmap`. The web backend draws the
 * SVG onto an off-screen `<canvas>` at its intrinsic size — no Skia SVG dependency.
 */
internal expect suspend fun rasterizeSvgToPng(svgBytes: ByteArray): ByteArray?
