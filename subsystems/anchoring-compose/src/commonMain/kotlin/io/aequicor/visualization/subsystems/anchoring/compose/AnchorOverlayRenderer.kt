package io.aequicor.visualization.subsystems.anchoring.compose

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import io.aequicor.visualization.subsystems.anchoring.AnchorGuide
import io.aequicor.visualization.subsystems.anchoring.AnchorKind
import io.aequicor.visualization.subsystems.anchoring.CenterAnchorLines
import io.aequicor.visualization.subsystems.anchoring.SnapLine
import io.aequicor.visualization.subsystems.anchoring.SpacingBar

/**
 * Compose overlay renderer for the anchoring subsystem. `DrawScope` extensions that paint the
 * engine's guide/spacing/center-line models. Kept independent of the app: the caller supplies a
 * [DocToScreen] projection (closing over its viewport), a [GuideStyle] (its theme tokens) and a
 * `TextMeasurer` — this module never sees the editor's viewport or color theme.
 */

/** Maps a document-space point to a screen-space [Offset]. */
typealias DocToScreen = (Double, Double) -> Offset

/** The theme colors the anchoring overlays draw with; the editor builds this from its tokens. */
data class GuideStyle(
    val accent: Color,        // edge/center alignment guides + solid center lines
    val accentSoft: Color,    // dashed center lines (accent at reduced alpha)
    val positive: Color,      // equal-spacing distribution bars
    val warning: Color,       // golden-ratio / proportion lines
    val badgeSurface: Color,  // outlined px-badge fill
)

/**
 * Draws one beautiful-anchor [guide], styled by kind: [GuideStyle.accent] for edge/center
 * alignment, a solid [GuideStyle.warning] line with a "φ" badge for a golden-ratio line, and a
 * dashed warning line with a fraction badge for a simple proportion. The equal-distance family
 * (EqualSpacing/EqualMargin/MatchGap) draws as [SpacingBar]s, so those kinds are a fallback here.
 */
fun DrawScope.drawAnchorGuide(guide: AnchorGuide, project: DocToScreen, style: GuideStyle, textMeasurer: TextMeasurer) {
    val start = project(guide.line.x1, guide.line.y1)
    val end = project(guide.line.x2, guide.line.y2)
    when (guide.kind) {
        AnchorKind.Alignment, AnchorKind.EqualSpacing, AnchorKind.EqualMargin, AnchorKind.MatchGap ->
            drawLine(style.accent, start, end, strokeWidth = 1f)
        AnchorKind.GoldenRatio -> {
            drawLine(style.warning, start, end, strokeWidth = 1f)
            guide.label?.let { drawFilledBadge(it, midpoint(start, end), style.warning, textMeasurer) }
        }
        AnchorKind.Proportion -> {
            val dash = PathEffect.dashPathEffect(floatArrayOf(5f, 4f))
            drawLine(style.warning.copy(alpha = 0.75f), start, end, strokeWidth = 1f, pathEffect = dash)
            guide.label?.let { drawFilledBadge(it, midpoint(start, end), style.warning.copy(alpha = 0.9f), textMeasurer) }
        }
    }
}

/** Draws an equal-spacing distribution [bar] as a green measurement line with end caps and a px gap badge. */
fun DrawScope.drawSpacingBar(bar: SpacingBar, project: DocToScreen, style: GuideStyle, textMeasurer: TextMeasurer) {
    val start = project(bar.segment.x1, bar.segment.y1)
    val end = project(bar.segment.x2, bar.segment.y2)
    drawMeasurementLine(start, end, style.positive, dashed = false)
    drawSpacingCap(start, end, style.positive)
    drawSpacingCap(end, start, style.positive)
    drawOutlinedBadge(formatMeasurement(bar.gap), midpoint(start, end), style.positive, style.badgeSurface, textMeasurer)
}

/**
 * Draws the selection's center anchor cross-hairs (design-book §18). Each axis renders **dashed**
 * while a free-move drag is searching, and turns **solid** the moment that axis magnetically
 * catches an anchor: [verticalSolid] = the X/centerX line locked, [horizontalSolid] = the Y/centerY
 * line locked. A crossing marker appears once either axis is locked.
 */
fun DrawScope.drawCenterAnchorLines(
    lines: CenterAnchorLines,
    verticalSolid: Boolean,
    horizontalSolid: Boolean,
    project: DocToScreen,
    style: GuideStyle,
) {
    drawCenterLine(lines.horizontal, horizontalSolid, project, style)
    drawCenterLine(lines.vertical, verticalSolid, project, style)
    if (verticalSolid || horizontalSolid) {
        // The crossing point (component center) gets its own marker when an axis is locked.
        val cross = project(lines.vertical.x1, lines.horizontal.y1)
        drawCircle(style.accent, radius = 3f, center = cross)
    }
}

private fun DrawScope.drawCenterLine(line: SnapLine, solid: Boolean, project: DocToScreen, style: GuideStyle) {
    val start = project(line.x1, line.y1)
    val end = project(line.x2, line.y2)
    if (solid) {
        drawLine(style.accent, start, end, strokeWidth = 1.5f)
        drawCircle(style.accent, radius = 2.5f, center = start)
        drawCircle(style.accent, radius = 2.5f, center = end)
    } else {
        val dash = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))
        drawLine(style.accentSoft, start, end, strokeWidth = 1f, pathEffect = dash)
    }
}
