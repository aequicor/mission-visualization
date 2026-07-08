package io.aequicor.visualization.editor.presentation

/**
 * Workspace-owned document viewport. Values are stored in Compose-independent units:
 * [zoom] is logical scale, pan offsets are dp so they survive density changes.
 */
data class EditorViewport(
    val zoom: Float = 1f,
    val panOffsetXDp: Float = 0f,
    val panOffsetYDp: Float = 0f,
) {
    fun zoomPx(density: Float): Float =
        (zoom * density).coerceAtLeast(0.0001f)

    fun panXPx(density: Float): Float = panOffsetXDp * density

    fun panYPx(density: Float): Float = panOffsetYDp * density

    fun toDocumentX(screenX: Float, density: Float): Double =
        ((screenX - panXPx(density)) / zoomPx(density)).toDouble()

    fun toDocumentY(screenY: Float, density: Float): Double =
        ((screenY - panYPx(density)) / zoomPx(density)).toDouble()

    fun toScreenX(documentX: Double, density: Float): Float =
        (documentX * zoomPx(density) + panXPx(density)).toFloat()

    fun toScreenY(documentY: Double, density: Float): Float =
        (documentY * zoomPx(density) + panYPx(density)).toFloat()

    fun panByScreenDelta(deltaXpx: Float, deltaYpx: Float, density: Float): EditorViewport =
        copy(
            panOffsetXDp = panOffsetXDp + deltaXpx / density,
            panOffsetYDp = panOffsetYDp + deltaYpx / density,
        )

    /**
     * Zoom around a screen-space focus point while keeping the document coordinate under
     * the cursor fixed. Pan changes here are viewport-only and never alter document geometry.
     */
    fun zoomAround(
        focusXpx: Float,
        focusYpx: Float,
        factor: Float,
        density: Float,
        minZoom: Float = WorkspaceLimits.MinZoom,
        maxZoom: Float = WorkspaceLimits.MaxZoom,
    ): EditorViewport {
        val nextZoom = (zoom * factor).coerceIn(minZoom, maxZoom)
        if (nextZoom == zoom) return this
        val oldZoomPx = zoomPx(density)
        val newZoomPx = (nextZoom * density).coerceAtLeast(0.0001f)
        val docX = (focusXpx - panXPx(density)) / oldZoomPx
        val docY = (focusYpx - panYPx(density)) / oldZoomPx
        val nextPanXpx = focusXpx - docX * newZoomPx
        val nextPanYpx = focusYpx - docY * newZoomPx
        return copy(
            zoom = nextZoom,
            panOffsetXDp = nextPanXpx / density,
            panOffsetYDp = nextPanYpx / density,
        )
    }
}

/** Convenience wrapper used where pointer coordinates must be converted as a pair. */
data class PointerDocumentTransform(
    val viewport: EditorViewport,
    val density: Float,
) {
    fun toDocumentX(screenX: Float): Double = viewport.toDocumentX(screenX, density)

    fun toDocumentY(screenY: Float): Double = viewport.toDocumentY(screenY, density)

    fun screenDeltaToDocument(deltaPx: Float): Double =
        (deltaPx / viewport.zoomPx(density)).toDouble()
}

/** Explicit canvas operation chosen on pointer-down and captured until pointer-up/cancel. */
sealed interface CanvasOperation {
    data object Pan : CanvasOperation
    data object Marquee : CanvasOperation
    data object Move : CanvasOperation
    data class Resize(val handle: ResizeHandle) : CanvasOperation
    data class Create(val kind: NewObjectKind) : CanvasOperation
}
