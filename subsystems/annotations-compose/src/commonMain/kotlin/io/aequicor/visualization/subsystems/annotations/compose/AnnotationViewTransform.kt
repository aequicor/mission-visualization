package io.aequicor.visualization.subsystems.annotations.compose

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import io.aequicor.visualization.subsystems.annotations.Annotation
import io.aequicor.visualization.subsystems.annotations.AnnotationAnchor
import io.aequicor.visualization.subsystems.annotations.AnnotationPoint
import io.aequicor.visualization.subsystems.annotations.AnnotationRect
import io.aequicor.visualization.subsystems.annotations.annotationBadgePosition

/**
 * Document→screen transform the overlay positions with: `screen = doc * zoom + pan`.
 * Mirrors the artboard's `CanvasViewport` math without depending on :engine:* — the
 * editor builds this from its viewport, same bridge style as anchoring's `DocToScreen`.
 */
data class AnnotationViewTransform(
    val zoom: Float = 1f,
    val panX: Float = 0f,
    val panY: Float = 0f,
) {
    fun toScreen(x: Double, y: Double): Offset =
        Offset((x * zoom + panX).toFloat(), (y * zoom + panY).toFloat())

    fun toScreen(point: AnnotationPoint): Offset = toScreen(point.x, point.y)

    /** Screen-space drag delta → document-space delta (for move callbacks). */
    fun toDocDelta(screenDelta: Offset): AnnotationPoint =
        AnnotationPoint((screenDelta.x / zoom).toDouble(), (screenDelta.y / zoom).toDouble())

    /** Document-space displacement → screen-space displacement (pan-free, for transient drag offsets). */
    fun toScreenDelta(docDelta: AnnotationPoint): Offset =
        Offset((docDelta.x * zoom).toFloat(), (docDelta.y * zoom).toFloat())
}

/**
 * Screen-space anchor point of [annotation]: the core's [annotationBadgePosition]
 * (node top-center + offset, dangling fallback, or free point) run through [transform].
 */
fun annotationScreenAnchor(
    annotation: Annotation,
    nodeBounds: (String) -> AnnotationRect?,
    transform: AnnotationViewTransform,
): Offset {
    val bounds = (annotation.anchor as? AnnotationAnchor.NodeAnchor)?.let { nodeBounds(it.nodeId) }
    return transform.toScreen(annotationBadgePosition(annotation.anchor, bounds))
}

/** Top-left of the droplet badge so its tail tip lands exactly on the [anchor] point. */
fun annotationBadgeTopLeft(anchor: Offset, badgeSize: Size): Offset =
    Offset(anchor.x - badgeSize.width / 2f, anchor.y - badgeSize.height)

/** Top-left of the expanded card: to the right of the badge, top-aligned with it. */
fun annotationCardTopLeft(anchor: Offset, badgeSize: Size, gap: Float): Offset =
    Offset(anchor.x + badgeSize.width / 2f + gap, anchor.y - badgeSize.height)
