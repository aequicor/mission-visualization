package io.aequicor.visualization.editor.presentation

import kotlin.math.exp

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

    fun panToDocumentStartX(documentX: Double): EditorViewport =
        copy(panOffsetXDp = -(documentX * zoom).toFloat())

    fun panToDocumentStartY(documentY: Double): EditorViewport =
        copy(panOffsetYDp = -(documentY * zoom).toFloat())

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

/**
 * Smooth, magnitude-aware zoom factor for a wheel/trackpad scroll amount. Unlike a fixed
 * per-notch step, the factor grows exponentially with the (clamped) scroll magnitude, so a
 * gentle trackpad glide yields many tiny multiplicative steps (buttery) while a firm
 * mouse-wheel notch still lands a consistent step. `signedScroll > 0` zooms in. The
 * exponential mapping keeps zooming perceptually uniform across the whole range (1→2 feels
 * like 2→4); the clamp tames outlier pixel-mode deltas some platforms emit.
 */
fun zoomFactorForScroll(
    signedScroll: Float,
    sensitivity: Float = WorkspaceLimits.ZoomWheelSensitivity,
    maxStep: Float = WorkspaceLimits.MaxZoomScrollStep,
): Float = exp(signedScroll.coerceIn(-maxStep, maxStep) * sensitivity)

data class CanvasScrollbarAxisMetrics(
    val visible: Boolean,
    val trackLengthPx: Float = 0f,
    val thumbOffsetPx: Float = 0f,
    val thumbLengthPx: Float = 0f,
    val minDocumentStart: Double = 0.0,
    val maxDocumentStart: Double = 0.0,
) {
    val maxThumbOffsetPx: Float get() = (trackLengthPx - thumbLengthPx).coerceAtLeast(0f)

    fun documentStartForThumbOffset(offsetPx: Float): Double {
        if (!visible || maxThumbOffsetPx <= 0f) return minDocumentStart
        val fraction = (offsetPx / maxThumbOffsetPx).coerceIn(0f, 1f).toDouble()
        return minDocumentStart + (maxDocumentStart - minDocumentStart) * fraction
    }
}

data class CanvasScrollbarsMetrics(
    val horizontal: CanvasScrollbarAxisMetrics = CanvasScrollbarAxisMetrics(visible = false),
    val vertical: CanvasScrollbarAxisMetrics = CanvasScrollbarAxisMetrics(visible = false),
)

fun canvasScrollbarsFor(
    viewport: EditorViewport,
    contentBounds: BoundsBox,
    viewportWidthPx: Float,
    viewportHeightPx: Float,
    density: Float,
    scrollbarThicknessPx: Float,
    minThumbLengthPx: Float,
): CanvasScrollbarsMetrics {
    val zoomPx = viewport.zoomPx(density)
    if (zoomPx <= 0f || viewportWidthPx <= 0f || viewportHeightPx <= 0f ||
        contentBounds.width <= 0.0 || contentBounds.height <= 0.0
    ) {
        return CanvasScrollbarsMetrics()
    }
    val visibleWidthDoc = viewportWidthPx / zoomPx
    val visibleHeightDoc = viewportHeightPx / zoomPx
    val visibleLeft = viewport.toDocumentX(0f, density)
    val visibleTop = viewport.toDocumentY(0f, density)
    val horizontalContentOverflows = contentBounds.width * zoomPx > viewportWidthPx + ScrollbarVisibilityEpsilonPx
    val verticalContentOverflows = contentBounds.height * zoomPx > viewportHeightPx + ScrollbarVisibilityEpsilonPx
    val horizontalRange = figmaCanvasScrollRange(
        contentStart = contentBounds.x,
        contentEnd = contentBounds.right,
        visibleStart = visibleLeft,
        visibleLength = visibleWidthDoc.toDouble(),
        addViewportMargin = horizontalContentOverflows,
    )
    val verticalRange = figmaCanvasScrollRange(
        contentStart = contentBounds.y,
        contentEnd = contentBounds.bottom,
        visibleStart = visibleTop,
        visibleLength = visibleHeightDoc.toDouble(),
        addViewportMargin = verticalContentOverflows,
    )
    val horizontalVisible = horizontalRange.length * zoomPx > viewportWidthPx + ScrollbarVisibilityEpsilonPx
    val verticalVisible = verticalRange.length * zoomPx > viewportHeightPx + ScrollbarVisibilityEpsilonPx
    val horizontalTrackPx = (viewportWidthPx - if (verticalVisible) scrollbarThicknessPx else 0f).coerceAtLeast(0f)
    val verticalTrackPx = (viewportHeightPx - if (horizontalVisible) scrollbarThicknessPx else 0f).coerceAtLeast(0f)
    return CanvasScrollbarsMetrics(
        horizontal = scrollbarAxisFor(
            visible = horizontalVisible,
            contentStart = horizontalRange.start,
            contentLength = horizontalRange.length,
            visibleStart = visibleLeft,
            visibleLength = visibleWidthDoc.toDouble(),
            trackLengthPx = horizontalTrackPx,
            minThumbLengthPx = minThumbLengthPx,
        ),
        vertical = scrollbarAxisFor(
            visible = verticalVisible,
            contentStart = verticalRange.start,
            contentLength = verticalRange.length,
            visibleStart = visibleTop,
            visibleLength = visibleHeightDoc.toDouble(),
            trackLengthPx = verticalTrackPx,
            minThumbLengthPx = minThumbLengthPx,
        ),
    )
}

private data class CanvasScrollRange(val start: Double, val end: Double) {
    val length: Double get() = end - start
}

private fun figmaCanvasScrollRange(
    contentStart: Double,
    contentEnd: Double,
    visibleStart: Double,
    visibleLength: Double,
    addViewportMargin: Boolean,
): CanvasScrollRange {
    val margin = if (addViewportMargin) visibleLength * FigmaCanvasScrollbarViewportMarginFraction else 0.0
    val visibleEnd = visibleStart + visibleLength
    return CanvasScrollRange(
        start = minOf(contentStart - margin, visibleStart),
        end = maxOf(contentEnd + margin, visibleEnd),
    )
}

private fun scrollbarAxisFor(
    visible: Boolean,
    contentStart: Double,
    contentLength: Double,
    visibleStart: Double,
    visibleLength: Double,
    trackLengthPx: Float,
    minThumbLengthPx: Float,
): CanvasScrollbarAxisMetrics {
    if (!visible || trackLengthPx <= 0f || contentLength <= 0.0 || visibleLength <= 0.0) {
        return CanvasScrollbarAxisMetrics(visible = false)
    }
    val minStart = contentStart
    val maxStart = contentStart + contentLength - visibleLength
    if (maxStart <= minStart) return CanvasScrollbarAxisMetrics(visible = false)

    val minThumb = minThumbLengthPx.coerceAtMost(trackLengthPx)
    val thumbLength = (trackLengthPx * (visibleLength / contentLength).toFloat())
        .coerceIn(minThumb, trackLengthPx)
    val maxThumbOffset = (trackLengthPx - thumbLength).coerceAtLeast(0f)
    val fraction = ((visibleStart - minStart) / (maxStart - minStart)).coerceIn(0.0, 1.0)
    return CanvasScrollbarAxisMetrics(
        visible = true,
        trackLengthPx = trackLengthPx,
        thumbOffsetPx = maxThumbOffset * fraction.toFloat(),
        thumbLengthPx = thumbLength,
        minDocumentStart = minStart,
        maxDocumentStart = maxStart,
    )
}

private const val FigmaCanvasScrollbarViewportMarginFraction = 0.5

private const val ScrollbarVisibilityEpsilonPx = 0.5f

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
    data object Rotate : CanvasOperation
}
