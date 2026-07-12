package io.aequicor.visualization.editor.platform

// Legacy JS target renders a placeholder for SVG media (wasmJs is the shipped web surface).
internal actual suspend fun rasterizeSvgToPng(svgBytes: ByteArray): ByteArray? = null
