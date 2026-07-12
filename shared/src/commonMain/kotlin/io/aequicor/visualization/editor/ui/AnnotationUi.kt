package io.aequicor.visualization.editor.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import io.aequicor.visualization.editor.ui.theme.LocalEditorColors
import io.aequicor.visualization.subsystems.annotations.AnnotationKind
import io.aequicor.visualization.subsystems.annotations.compose.AnnotationOverlayColors
import io.aequicor.visualization.subsystems.annotations.compose.badgeFill
import io.aequicor.visualization.subsystems.annotations.compose.badgeStroke
import kotlin.math.min

/**
 * Theme bridge for the annotations-compose renderer: maps the editor's `EditorColors`
 * tokens into the [AnnotationOverlayColors] contract (same pattern as anchoring's
 * `GuideStyle` and figures' `FigurePreviewStyle`). Issue-yellow is the `statusWarning`
 * token per the palette rules; notes take the neutral chrome/ink tokens.
 */
@Composable
internal fun annotationOverlayColors(): AnnotationOverlayColors {
    val colors = LocalEditorColors.current
    return AnnotationOverlayColors(
        noteFill = colors.chrome,
        noteStroke = colors.controlInk,
        issueFill = colors.statusWarning,
        issueStroke = colors.statusWarning,
        ink = colors.ink,
        mutedInk = colors.mutedInk,
        cardSurface = colors.raisedSurface,
        cardStroke = colors.panelStroke,
        selectionStroke = colors.accent,
        // Dangling badge (anchor node gone): muted, dashed — visible but clearly inert.
        danglingFill = colors.raisedSurface,
        danglingStroke = colors.mutedInk,
    )
}

/**
 * Mini droplet preview of an annotation kind for dropdown rows and list entries — the
 * dropdown rules ask for a distinct visual per row, so note/issue show their real badge
 * tint (issue = `statusWarning`) instead of a generic icon.
 */
@Composable
internal fun AnnotationKindPreview(kind: AnnotationKind, modifier: Modifier = Modifier.size(16.dp)) {
    val colors = annotationOverlayColors()
    val outline = LocalEditorColors.current.controlInk
    Canvas(modifier) {
        // Same silhouette as AnnotationBadge's dropletPath, scaled into this box.
        val d = min(size.width, size.height * (20f / 26f))
        val left = (size.width - d) / 2f
        val droplet = Path().apply {
            moveTo(left + d / 2f, size.height)
            arcTo(Rect(left, 0f, left + d, d), startAngleDegrees = 135f, sweepAngleDegrees = 270f, forceMoveTo = false)
            close()
        }
        drawPath(droplet, colors.badgeFill(kind))
        // Constant outline so the preview stays readable at menu size on any row state.
        drawPath(droplet, outline, style = Stroke(1.dp.toPx()))
        drawCircle(colors.badgeStroke(kind), radius = d * 0.14f, center = Offset(left + d / 2f, d / 2f))
    }
}
