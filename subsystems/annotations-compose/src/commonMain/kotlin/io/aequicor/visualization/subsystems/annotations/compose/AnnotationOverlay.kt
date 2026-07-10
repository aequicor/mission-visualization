package io.aequicor.visualization.subsystems.annotations.compose

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.aequicor.visualization.subsystems.annotations.Annotation
import io.aequicor.visualization.subsystems.annotations.AnnotationLayer
import io.aequicor.visualization.subsystems.annotations.AnnotationRect
import kotlin.math.roundToInt

private val CardGap = 6.dp

/**
 * Review layer over the artboard: draws every annotation of [layer] as a collapsed
 * droplet badge (plus the expanded card for ids in [expandedIds]), positioned via the
 * core's `annotationBadgePosition` and the caller's pan/zoom [transform]. Stateless —
 * expansion/selection live in the editor's workspace state; [nodeBounds] resolves node
 * anchors from the resolved layout (null = dangling, the core falls back deterministically).
 *
 * Badge click selects + toggles expansion; card click selects. When [onMoveBy] is given,
 * badges are draggable and report document-space deltas (offset for node anchors,
 * absolute movement for free points — the reducer decides).
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
    onMoveBy: ((id: String, dx: Double, dy: Double) -> Unit)? = null,
) {
    val density = LocalDensity.current
    val badgeSize = with(density) { Size(AnnotationBadgeWidth.toPx(), AnnotationBadgeHeight.toPx()) }
    val cardGap = with(density) { CardGap.toPx() }
    Box(modifier) {
        for (annotation in layer.annotations) {
            key(annotation.id) {
                val anchor = annotationScreenAnchor(annotation, nodeBounds, transform)
                val selected = annotation.id == selectedId
                val badgeTopLeft = annotationBadgeTopLeft(anchor, badgeSize)
                AnnotationBadge(
                    kind = annotation.kind,
                    colors = colors,
                    modifier = Modifier
                        .placeAt(badgeTopLeft.x, badgeTopLeft.y)
                        .then(badgeDragModifier(annotation, transform, onMoveBy)),
                    selected = selected,
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
): Modifier =
    if (onMoveBy == null) {
        Modifier
    } else {
        Modifier.pointerInput(annotation.id, transform) {
            detectDragGestures { change, dragAmount ->
                change.consume()
                val delta = transform.toDocDelta(dragAmount)
                onMoveBy(annotation.id, delta.x, delta.y)
            }
        }
    }
