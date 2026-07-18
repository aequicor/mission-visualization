package io.aequicor.visualization.subsystems.figures.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import io.aequicor.visualization.subsystems.figures.BooleanOperationKind
import io.aequicor.visualization.subsystems.figures.ShapeType

/**
 * Theme tokens the figure previews need, passed in by the caller so the subsystem stays
 * framework/theme-agnostic (mirrors the anchoring-compose `GuideStyle` bridge).
 */
data class FigurePreviewStyle(
    val ink: Color,
    val fill: Color,
    val accent: Color,
    val surface: Color,
)

/** A small glyph of a [ShapeType], used as the left visual of shape dropdowns and tool rows. */
@Composable
fun FigureShapePreview(
    shape: ShapeType,
    style: FigurePreviewStyle,
    modifier: Modifier = Modifier.size(18.dp),
) {
    Canvas(modifier) {
        val stroke = Stroke(1.5.dp.toPx())
        val ink = style.ink
        val fill = style.fill
        when (shape) {
            ShapeType.Rectangle -> {
                // A degenerate canvas (first layout frame, cramped dropdown rows) makes
                // the inset subtraction negative — clamp instead of drawing garbage.
                val rect = Size((size.width - 5f).coerceAtLeast(0f), (size.height - 7f).coerceAtLeast(0f))
                drawRoundRect(fill, topLeft = Offset(2.5f, 3.5f), size = rect, cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()))
                drawRoundRect(ink, topLeft = Offset(2.5f, 3.5f), size = rect, cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()), style = stroke)
            }
            ShapeType.Ellipse -> {
                val oval = Size((size.width - 5f).coerceAtLeast(0f), (size.height - 6f).coerceAtLeast(0f))
                drawOval(fill, topLeft = Offset(2.5f, 3f), size = oval)
                drawOval(ink, topLeft = Offset(2.5f, 3f), size = oval, style = stroke)
            }
            ShapeType.Polygon -> {
                val path = Path().apply {
                    moveTo(size.width / 2f, 2.5f)
                    lineTo(size.width - 2.5f, size.height - 3f)
                    lineTo(2.5f, size.height - 3f)
                    close()
                }
                drawPath(path, fill)
                drawPath(path, ink, style = stroke)
            }
            ShapeType.Star -> {
                val path = Path().apply {
                    moveTo(size.width / 2f, 2f)
                    lineTo(size.width * 0.62f, size.height * 0.38f)
                    lineTo(size.width - 2f, size.height * 0.38f)
                    lineTo(size.width * 0.70f, size.height * 0.58f)
                    lineTo(size.width * 0.82f, size.height - 2f)
                    lineTo(size.width / 2f, size.height * 0.74f)
                    lineTo(size.width * 0.18f, size.height - 2f)
                    lineTo(size.width * 0.30f, size.height * 0.58f)
                    lineTo(2f, size.height * 0.38f)
                    lineTo(size.width * 0.38f, size.height * 0.38f)
                    close()
                }
                drawPath(path, fill)
                drawPath(path, ink, style = stroke)
            }
            ShapeType.Line -> {
                drawLine(ink, Offset(3f, size.height - 4f), Offset(size.width - 3f, 4f), strokeWidth = 2.dp.toPx())
            }
            ShapeType.Arrow -> {
                val start = Offset(3f, size.height - 4f)
                val end = Offset(size.width - 3f, 4f)
                drawLine(ink, start, end, strokeWidth = 2.dp.toPx())
                drawLine(ink, end, Offset(end.x - 6f, end.y + 1f), strokeWidth = 2.dp.toPx())
                drawLine(ink, end, Offset(end.x - 1f, end.y + 6f), strokeWidth = 2.dp.toPx())
            }
            ShapeType.Vector -> {
                val path = Path().apply {
                    moveTo(2.5f, size.height - 4f)
                    cubicTo(size.width * 0.35f, 1f, size.width * 0.65f, size.height + 1f, size.width - 2.5f, 4f)
                }
                drawPath(path, ink, style = Stroke(1.8.dp.toPx()))
            }
        }
    }
}

/** A small glyph of a [BooleanOperationKind], used as the left visual of boolean-op dropdowns. */
@Composable
fun FigureBooleanPreview(
    operation: BooleanOperationKind,
    style: FigurePreviewStyle,
    modifier: Modifier = Modifier.size(18.dp),
) {
    Canvas(modifier) {
        val first = style.ink.copy(alpha = 0.75f)
        val second = style.accent.copy(alpha = 0.42f)
        val surface = style.surface
        val left = Offset(2f, 6f)
        val right = Offset(6f, 2f)
        val box = Size(10f, 10f)
        val radius = CornerRadius(2f, 2f)
        when (operation) {
            BooleanOperationKind.Union -> {
                drawRoundRect(first, left, box, radius)
                drawRoundRect(second, right, box, radius)
            }
            BooleanOperationKind.Subtract -> {
                drawRoundRect(first, left, box, radius)
                drawRoundRect(surface, right, box, radius)
                drawRoundRect(style.ink, right, box, radius, style = Stroke(1.dp.toPx()))
            }
            BooleanOperationKind.Intersect -> {
                drawRoundRect(first.copy(alpha = 0.18f), left, box, radius, style = Stroke(1.dp.toPx()))
                drawRoundRect(second.copy(alpha = 0.18f), right, box, radius, style = Stroke(1.dp.toPx()))
                drawRoundRect(style.accent, Offset(6f, 6f), Size(6f, 6f), radius)
            }
            BooleanOperationKind.Exclude -> {
                drawRoundRect(first, left, box, radius)
                drawRoundRect(second, right, box, radius)
                drawRoundRect(surface, Offset(6f, 6f), Size(6f, 6f), radius)
            }
        }
    }
}
