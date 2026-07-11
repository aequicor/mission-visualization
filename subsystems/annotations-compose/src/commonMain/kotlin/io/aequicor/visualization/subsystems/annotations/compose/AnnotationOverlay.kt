package io.aequicor.visualization.subsystems.annotations.compose

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.aequicor.visualization.subsystems.annotations.Annotation
import io.aequicor.visualization.subsystems.annotations.AnnotationAnchor
import io.aequicor.visualization.subsystems.annotations.AnnotationLayer
import io.aequicor.visualization.subsystems.annotations.AnnotationPoint
import io.aequicor.visualization.subsystems.annotations.AnnotationRect
import kotlin.math.roundToInt

private val CardGap = 6.dp

/**
 * Review layer over the artboard: draws every annotation of [layer] as a collapsed
 * droplet badge (plus the expanded card for ids in [expandedIds]), positioned via the
 * core's `annotationBadgePosition` and the caller's pan/zoom [transform]. Stateless —
 * expansion/selection live in the editor's workspace state; [nodeBounds] resolves node
 * anchors from the resolved layout (null = dangling: the core falls back
 * deterministically and the badge renders in the muted dashed dangling style).
 *
 * Badge click selects + toggles expansion; card click selects. When [onMoveBy] is given,
 * badges are draggable and report document-space deltas (offset for node anchors,
 * absolute movement for free points — the reducer decides). The caller keeps the
 * transient drag as view state and feeds it back through [dragOffsets] (doc-space
 * displacement per annotation id, applied on top of the anchor position), committing
 * once from [onMoveEnd] — so a drag never mutates the document per frame; [onMoveCancel]
 * reports an aborted gesture to drop the transient offset.
 */
@Composable
fun AnnotationOverlay(
    layer: AnnotationLayer,
    expandedIds: Set<String>,
    selectedId: String?,
    nodeBounds: (String) -> AnnotationRect?,
    transform: AnnotationViewTransform,
    colors: AnnotationOverlayColors,
    onToggleExpand: (String) -> Unit,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    dragOffsets: Map<String, AnnotationPoint> = emptyMap(),
    onMoveBy: ((id: String, dx: Double, dy: Double) -> Unit)? = null,
    onMoveEnd: ((id: String) -> Unit)? = null,
    onMoveCancel: ((id: String) -> Unit)? = null,
) {
    val density = LocalDensity.current
    val badgeSize = with(density) { Size(AnnotationBadgeWidth.toPx(), AnnotationBadgeHeight.toPx()) }
    val cardGap = with(density) { CardGap.toPx() }
    Box(modifier) {
        for (annotation in layer.annotations) {
            key(annotation.id) {
                val nodeAnchor = annotation.anchor as? AnnotationAnchor.NodeAnchor
                val dangling = nodeAnchor != null && nodeBounds(nodeAnchor.nodeId) == null
                val anchor = annotationScreenAnchor(annotation, nodeBounds, transform)
                    .plus(dragOffsets[annotation.id]?.let(transform::toScreenDelta) ?: Offset.Zero)
                val selected = annotation.id == selectedId
                val badgeTopLeft = annotationBadgeTopLeft(anchor, badgeSize)
                AnnotationBadge(
                    kind = annotation.kind,
                    colors = colors,
                    modifier = Modifier
                        .placeAt(badgeTopLeft.x, badgeTopLeft.y)
                        .then(badgeDragModifier(annotation, transform, onMoveBy, onMoveEnd, onMoveCancel)),
                    selected = selected,
                    dangling = dangling,
                    onToggleExpand = {
                        onSelect(annotation.id)
                        onToggleExpand(annotation.id)
                    },
                )
                if (annotation.id in expandedIds) {
                    val cardTopLeft = annotationCardTopLeft(anchor, badgeSize, cardGap)
                    AnnotationCard(
                        annotation = annotation,
                        colors = colors,
                        modifier = Modifier.placeAt(cardTopLeft.x, cardTopLeft.y),
                        selected = selected,
                        onSelect = { onSelect(annotation.id) },
                    )
                }
            }
        }
    }
}

/** Places the element at an absolute pixel position inside the overlay box. */
private fun Modifier.placeAt(x: Float, y: Float): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    layout(0, 0) { placeable.place(IntOffset(x.roundToInt(), y.roundToInt())) }
}

/** Drag-to-move support for a badge; identity when the overlay is not editable. */
private fun badgeDragModifier(
    annotation: Annotation,
    transform: AnnotationViewTransform,
    onMoveBy: ((id: String, dx: Double, dy: Double) -> Unit)?,
    onMoveEnd: ((id: String) -> Unit)?,
    onMoveCancel: ((id: String) -> Unit)?,
): Modifier =
    if (onMoveBy == null) {
        Modifier
    } else {
        Modifier.pointerInput(annotation.id, transform) {
            detectDragGestures(
                onDragEnd = { onMoveEnd?.invoke(annotation.id) },
                onDragCancel = { onMoveCancel?.invoke(annotation.id) },
            ) { change, dragAmount ->
                change.consume()
                val delta = transform.toDocDelta(dragAmount)
                onMoveBy(annotation.id, delta.x, delta.y)
            }
        }
    }
