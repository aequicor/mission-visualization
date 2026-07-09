package io.aequicor.visualization.subsystems.anchoring.compose

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Self-contained overlay primitives for the anchoring renderer: short measurement lines, spacing
 * caps and px badges. These deliberately duplicate the editor's own badge/line helpers (`:shared`
 * keeps its copies for the Alt-measurement overlay) so this renderer module stays independent of
 * the app layer.
 */

internal const val BadgePaddingH = 6f
internal const val BadgePaddingV = 3f

/** Rounds to a whole number when close, otherwise one decimal (matches the editor's `formatPx`). */
internal fun formatMeasurement(value: Double): String {
    val rounded = value.roundToInt()
    return if (abs(value - rounded) < 0.05) rounded.toString() else ((value * 10).roundToInt() / 10.0).toString()
}

internal fun midpoint(a: Offset, b: Offset): Offset = Offset((a.x + b.x) / 2f, (a.y + b.y) / 2f)

internal fun DrawScope.drawMeasurementLine(start: Offset, end: Offset, color: Color, dashed: Boolean) {
    val effect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(3f, 3f)) else null
    drawLine(color, start, end, strokeWidth = 1.5f, pathEffect = effect)
}

/** A short perpendicular tick at [at], oriented across the bar running from [other] to [at]. */
internal fun DrawScope.drawSpacingCap(at: Offset, other: Offset, color: Color) {
    val dx = at.x - other.x
    val dy = at.y - other.y
    val length = hypot(dx, dy)
    if (length == 0f) return
    val px = -dy / length
    val py = dx / length
    val half = 3f
    drawLine(color, Offset(at.x - px * half, at.y - py * half), Offset(at.x + px * half, at.y + py * half), strokeWidth = 1f)
}

private fun badgeRect(textSize: IntSize, center: Offset): Rect {
    val size = Size(textSize.width + BadgePaddingH * 2, textSize.height + BadgePaddingV * 2)
    return Rect(Offset(center.x - size.width / 2f, center.y - size.height / 2f), size)
}

/** A solid-fill badge with white text (the golden-ratio "φ" / size badges). */
internal fun DrawScope.drawFilledBadge(text: String, center: Offset, background: Color, textMeasurer: TextMeasurer) {
    val layout = textMeasurer.measure(
        text = AnnotatedString(text),
        style = TextStyle(color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Medium),
    )
    val rect = badgeRect(layout.size, center)
    drawRoundRect(color = background, topLeft = rect.topLeft, size = rect.size, cornerRadius = CornerRadius(4f, 4f))
    drawText(layout, topLeft = Offset(rect.topLeft.x + BadgePaddingH, rect.topLeft.y + BadgePaddingV))
}

/** A neutral-surface badge with a colored border/text (the equal-spacing px badges). */
internal fun DrawScope.drawOutlinedBadge(text: String, center: Offset, accent: Color, surface: Color, textMeasurer: TextMeasurer) {
    val layout = textMeasurer.measure(
        text = AnnotatedString(text),
        style = TextStyle(color = accent, fontSize = 10.sp, fontWeight = FontWeight.Medium),
    )
    val rect = badgeRect(layout.size, center)
    val corner = CornerRadius(4f, 4f)
    drawRoundRect(color = surface, topLeft = rect.topLeft, size = rect.size, cornerRadius = corner)
    drawRoundRect(color = accent, topLeft = rect.topLeft, size = rect.size, cornerRadius = corner, style = Stroke(width = 1f))
    drawText(layout, topLeft = Offset(rect.topLeft.x + BadgePaddingH, rect.topLeft.y + BadgePaddingV))
}
