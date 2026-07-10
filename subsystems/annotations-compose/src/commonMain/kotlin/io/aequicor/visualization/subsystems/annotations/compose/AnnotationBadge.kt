package io.aequicor.visualization.subsystems.annotations.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.aequicor.visualization.subsystems.annotations.AnnotationKind

/** Collapsed badge footprint; the tail tip is at bottom-center of this box. */
val AnnotationBadgeWidth: Dp = 20.dp
val AnnotationBadgeHeight: Dp = 26.dp

/**
 * Collapsed annotation marker: a droplet/pin whose tail points at the anchored spot.
 * Issue badges take the warning tokens, notes the neutral ones; a selected badge gets
 * an extra selection outline. Click toggles the expanded card.
 */
@Composable
fun AnnotationBadge(
    kind: AnnotationKind,
    colors: AnnotationOverlayColors,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onToggleExpand: () -> Unit = {},
) {
    Canvas(
        modifier
            .size(AnnotationBadgeWidth, AnnotationBadgeHeight)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onToggleExpand,
            ),
    ) {
        val droplet = dropletPath()
        drawPath(droplet, colors.badgeFill(kind))
        drawPath(droplet, colors.badgeStroke(kind), style = Stroke(1.5.dp.toPx()))
        if (selected) {
            drawPath(droplet, colors.selectionStroke, style = Stroke(3.dp.toPx()))
        }
        // Inner dot so the droplet reads as a comment marker, not a plain pin.
        drawCircle(
            colors.badgeStroke(kind),
            radius = size.width * 0.14f,
            center = Offset(size.width / 2f, size.width / 2f),
        )
    }
}

/**
 * Teardrop outline filling the scope: a circle of the full width up top, tail converging
 * to the bottom-center tip (the anchor point).
 */
private fun DrawScope.dropletPath(): Path = Path().apply {
    val d = size.width
    moveTo(size.width / 2f, size.height) // tail tip = anchor point
    // 135° start (bottom-left of the circle), 270° clockwise sweep to 45° (bottom-right).
    arcTo(Rect(0f, 0f, d, d), startAngleDegrees = 135f, sweepAngleDegrees = 270f, forceMoveTo = false)
    close()
}
