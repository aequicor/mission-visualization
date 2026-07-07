package io.aequicor.visualization.engine.backend.compose

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.Density
import io.aequicor.visualization.engine.ir.layout.LayoutBox
import io.aequicor.visualization.engine.ir.model.GradientKind
import io.aequicor.visualization.engine.ir.model.ShapeType
import io.aequicor.visualization.engine.ir.model.StrokeAlign
import io.aequicor.visualization.engine.ir.model.TextAlignVertical
import io.aequicor.visualization.engine.ir.resolve.ResolvedCornerRadius
import io.aequicor.visualization.engine.ir.resolve.ResolvedEffect
import io.aequicor.visualization.engine.ir.resolve.ResolvedNode
import io.aequicor.visualization.engine.ir.resolve.ResolvedPaint
import io.aequicor.visualization.engine.ir.resolve.ResolvedStrokes
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

internal class DesignDrawContext(
    val textMeasurer: TextMeasurer,
    val density: Density,
)

private val PlaceholderImageColor = Color(0xFFD9E1EA)
private val UnknownPaintColor = Color(0xFFC9CFD6)

/** Draws a laid-out node tree in document coordinates (the caller applies zoom). */
internal fun DrawScope.drawDesignBox(box: LayoutBox, context: DesignDrawContext) {
    val node = box.node
    if (node.opacity <= 0.0) return

    if (node.opacity < 1.0) {
        // Group opacity must not clip overflowing or rotated descendants: use huge
        // layer bounds; Skia intersects them with the current clip.
        val bounds = Rect(-1_000_000f, -1_000_000f, 1_000_000f, 1_000_000f)
        val layerPaint = Paint().apply { alpha = node.opacity.toFloat() }
        drawContext.canvas.saveLayer(bounds, layerPaint)
        drawWithRotation(box, context)
        drawContext.canvas.restore()
    } else {
        drawWithRotation(box, context)
    }
}

private fun DrawScope.drawWithRotation(box: LayoutBox, context: DesignDrawContext) {
    val rotation = box.node.rotation
    if (rotation != 0.0) {
        rotate(
            degrees = rotation.toFloat(),
            pivot = Offset((box.x + box.width / 2).toFloat(), (box.y + box.height / 2).toFloat()),
        ) {
            drawDesignBoxContent(box, context)
        }
    } else {
        drawDesignBoxContent(box, context)
    }
}

private fun DrawScope.drawDesignBoxContent(box: LayoutBox, context: DesignDrawContext) {
    val node = box.node
    val outline = outlinePath(box)

    node.effects.filterIsInstance<ResolvedEffect.DropShadow>().forEach { shadow ->
        translate(shadow.offset.x.toFloat(), shadow.offset.y.toFloat()) {
            val alpha = (shadow.color.alpha / 255f) * 0.9f
            drawPath(outline, shadow.color.toComposeColor().copy(alpha = alpha))
        }
    }

    // A text node's fills paint the glyphs, not the node box.
    if (node.text == null) {
        node.fills.forEach { fill -> drawResolvedPaint(fill, outline, box) }
        node.effects.filterIsInstance<ResolvedEffect.InnerShadow>().forEach { shadow ->
            drawInnerShadow(shadow, outline)
        }
    }

    node.text?.let { text ->
        val color = node.fills.firstSolidColor() ?: Color.Black
        val layout = measureResolvedText(
            measurer = context.textMeasurer,
            density = context.density,
            text = text,
            color = color,
            maxWidth = box.width,
            exactWidth = true,
        )
        val yOffset = when (text.style.textAlignVertical) {
            TextAlignVertical.Top -> 0.0
            TextAlignVertical.Center -> (box.height - layout.size.height) / 2.0
            TextAlignVertical.Bottom -> box.height - layout.size.height
        }
        drawText(layout, topLeft = Offset(box.x.toFloat(), (box.y + yOffset).toFloat()))
    }

    if (box.children.isNotEmpty()) {
        if (node.layout.clipsContent) {
            clipPath(outline) {
                box.children.forEach { child -> drawDesignBox(child, context) }
            }
        } else {
            box.children.forEach { child -> drawDesignBox(child, context) }
        }
    }

    node.strokes?.let { strokes -> drawStrokes(strokes, box) }
}

private fun DrawScope.drawResolvedPaint(
    paint: ResolvedPaint,
    outline: Path,
    box: LayoutBox,
    style: androidx.compose.ui.graphics.drawscope.DrawStyle = androidx.compose.ui.graphics.drawscope.Fill,
) {
    when (paint) {
        is ResolvedPaint.Solid -> {
            val color = paint.color.toComposeColor()
            drawPath(outline, color, alpha = paint.opacity.toFloat(), style = style)
        }
        is ResolvedPaint.Gradient -> when {
            // Degenerate gradients must fall back, never crash the platform brush.
            paint.stops.isEmpty() ->
                drawPath(outline, UnknownPaintColor, alpha = paint.opacity.toFloat(), style = style)
            paint.stops.size == 1 ->
                drawPath(
                    outline,
                    paint.stops.first().color.toComposeColor(),
                    alpha = paint.opacity.toFloat(),
                    style = style,
                )
            else ->
                drawPath(outline, paint.toBrush(box), alpha = paint.opacity.toFloat(), style = style)
        }
        is ResolvedPaint.Image -> {
            drawPath(outline, PlaceholderImageColor, alpha = paint.opacity.toFloat(), style = style)
        }
        is ResolvedPaint.Unknown -> {
            drawPath(outline, UnknownPaintColor, alpha = paint.opacity.toFloat(), style = style)
        }
    }
}

private fun DrawScope.drawInnerShadow(shadow: ResolvedEffect.InnerShadow, outline: Path) {
    val rim = ((shadow.blur + shadow.spread).coerceAtLeast(1.0) * 2).toFloat()
    val alpha = (shadow.color.alpha / 255f) * 0.9f
    clipPath(outline) {
        translate(shadow.offset.x.toFloat(), shadow.offset.y.toFloat()) {
            drawPath(
                outline,
                shadow.color.toComposeColor().copy(alpha = alpha),
                style = Stroke(width = rim),
            )
        }
    }
}

private fun ResolvedPaint.Gradient.toBrush(box: LayoutBox): Brush {
    val start = Offset(
        (box.x + from.x * box.width).toFloat(),
        (box.y + from.y * box.height).toFloat(),
    )
    val end = Offset(
        (box.x + to.x * box.width).toFloat(),
        (box.y + to.y * box.height).toFloat(),
    )
    val colorStops = stops
        .sortedBy { it.position }
        .map { it.position.toFloat() to it.color.toComposeColor() }
        .toTypedArray()
    return when (gradientType) {
        GradientKind.Linear, GradientKind.Diamond -> Brush.linearGradient(
            colorStops = colorStops,
            start = start,
            end = end,
        )
        GradientKind.Radial -> {
            val distance = (end - start).getDistance()
            Brush.radialGradient(
                colorStops = colorStops,
                center = start,
                radius = if (distance > 0.01f) {
                    distance
                } else {
                    maxOf(0.5f * min(box.width, box.height).toFloat(), 1f)
                },
            )
        }
        GradientKind.Angular -> Brush.sweepGradient(
            colorStops = colorStops,
            center = start,
        )
    }
}

private fun DrawScope.drawStrokes(strokes: ResolvedStrokes, box: LayoutBox) {
    if (strokes.paints.isEmpty()) return
    val dash = strokes.dashPattern.takeIf { it.size >= 2 }?.let { pattern ->
        PathEffect.dashPathEffect(pattern.map { it.toFloat() }.toFloatArray())
    }

    val hasPerSide = strokes.weightTop != null || strokes.weightRight != null ||
        strokes.weightBottom != null || strokes.weightLeft != null
    if (hasPerSide) {
        drawPerSideStroke(strokes, box, strokes.paints.strokeBrushColor(), dash)
        return
    }

    val weight = strokes.weight.toFloat()
    if (weight <= 0f) return
    val inset = when (strokes.align) {
        StrokeAlign.Inside -> weight / 2.0
        StrokeAlign.Center -> 0.0
        StrokeAlign.Outside -> -weight / 2.0
    }
    val path = outlinePath(box, inset)
    val style = Stroke(width = weight, pathEffect = dash)
    strokes.paints.forEach { paint -> drawResolvedPaint(paint, path, box, style) }
}

/** Per-side strokes draw straight lines, so multiple paint layers collapse to one color. */
private fun List<ResolvedPaint>.strokeBrushColor(): Color =
    firstNotNullOfOrNull { paint ->
        when (paint) {
            is ResolvedPaint.Solid -> paint.color.toComposeColor().copy(
                alpha = (paint.color.alpha / 255f) * paint.opacity.toFloat(),
            )
            is ResolvedPaint.Gradient -> paint.stops.firstOrNull()?.color?.toComposeColor()
            else -> null
        }
    } ?: UnknownPaintColor

private fun DrawScope.drawPerSideStroke(
    strokes: ResolvedStrokes,
    box: LayoutBox,
    color: Color,
    dash: PathEffect?,
) {
    val left = box.x.toFloat()
    val top = box.y.toFloat()
    val right = box.right.toFloat()
    val bottom = box.bottom.toFloat()
    // Centerline offset from the edge, inward positive: inside w/2, center 0, outside -w/2.
    val alignFactor = when (strokes.align) {
        StrokeAlign.Inside -> 0.5f
        StrokeAlign.Center -> 0f
        StrokeAlign.Outside -> -0.5f
    }

    fun line(start: Offset, end: Offset, weight: Double) {
        drawLine(color, start, end, weight.toFloat(), pathEffect = dash)
    }
    strokes.weightTop?.takeIf { it > 0 }?.let { weight ->
        val y = top + weight.toFloat() * alignFactor
        line(Offset(left, y), Offset(right, y), weight)
    }
    strokes.weightBottom?.takeIf { it > 0 }?.let { weight ->
        val y = bottom - weight.toFloat() * alignFactor
        line(Offset(left, y), Offset(right, y), weight)
    }
    strokes.weightLeft?.takeIf { it > 0 }?.let { weight ->
        val x = left + weight.toFloat() * alignFactor
        line(Offset(x, top), Offset(x, bottom), weight)
    }
    strokes.weightRight?.takeIf { it > 0 }?.let { weight ->
        val x = right - weight.toFloat() * alignFactor
        line(Offset(x, top), Offset(x, bottom), weight)
    }
}

/** Builds the node outline in absolute coordinates, optionally inset (for strokes). */
private fun outlinePath(box: LayoutBox, inset: Double = 0.0): Path {
    val node = box.node
    val rect = Rect(
        (box.x + inset).toFloat(),
        (box.y + inset).toFloat(),
        (box.right - inset).toFloat(),
        (box.bottom - inset).toFloat(),
    )
    val shape = node.shape?.shape
    return when (shape) {
        ShapeType.Ellipse -> Path().apply { addOval(rect) }
        ShapeType.Line -> Path().apply {
            moveTo(rect.left, rect.center.y)
            lineTo(rect.right, rect.center.y)
        }
        ShapeType.Polygon -> regularPolygonPath(rect, node.shape?.pointCount ?: 3)
        ShapeType.Star -> starPath(rect, node.shape?.pointCount ?: 5, node.shape?.innerRadius ?: 0.4)
        ShapeType.Vector -> vectorPath(box, rect)
        else -> roundedRectPath(rect, node.cornerRadius, inset)
    }
}

private fun roundedRectPath(rect: Rect, radius: ResolvedCornerRadius, inset: Double = 0.0): Path {
    val path = Path()
    if (radius.isZero) {
        path.addRect(rect)
        return path
    }
    val limit = min(rect.width, rect.height) / 2f
    // Inset outlines (stroke centerlines) shrink corner radii by the same amount so
    // the stroke arc stays concentric with the fill.
    fun clamp(value: Double): Float = (value - inset).toFloat().coerceIn(0f, limit)
    path.addRoundRect(
        RoundRect(
            rect = rect,
            topLeft = CornerRadius(clamp(radius.topLeft)),
            topRight = CornerRadius(clamp(radius.topRight)),
            bottomRight = CornerRadius(clamp(radius.bottomRight)),
            bottomLeft = CornerRadius(clamp(radius.bottomLeft)),
        ),
    )
    return path
}

private fun regularPolygonPath(rect: Rect, points: Int): Path {
    val path = Path()
    val count = points.coerceAtLeast(3)
    val cx = rect.center.x
    val cy = rect.center.y
    val rx = rect.width / 2f
    val ry = rect.height / 2f
    for (i in 0 until count) {
        val angle = -90.0 + 360.0 * i / count
        val radians = angle * 3.141592653589793 / 180.0
        val x = cx + rx * cos(radians).toFloat()
        val y = cy + ry * sin(radians).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}

private fun starPath(rect: Rect, points: Int, innerRatio: Double): Path {
    val path = Path()
    val count = points.coerceAtLeast(3)
    val cx = rect.center.x
    val cy = rect.center.y
    val rx = rect.width / 2f
    val ry = rect.height / 2f
    val inner = innerRatio.toFloat().coerceIn(0.05f, 1f)
    for (i in 0 until count * 2) {
        val angle = -90.0 + 180.0 * i / count
        val radians = angle * 3.141592653589793 / 180.0
        val scale = if (i % 2 == 0) 1f else inner
        val x = cx + rx * scale * cos(radians).toFloat()
        val y = cy + ry * scale * sin(radians).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}

private fun vectorPath(box: LayoutBox, rect: Rect): Path {
    val node = box.node
    val paths = node.shape?.paths.orEmpty()
    if (paths.isEmpty()) return Path().apply { addRect(rect) }
    val combined = Path()
    paths.forEach { vector -> combined.addPath(parseSvgPath(vector.d)) }
    val authoredWidth = node.size.width ?: box.width
    val authoredHeight = node.size.height ?: box.height
    val scaleX = if (authoredWidth > 0.0) (box.width / authoredWidth).toFloat() else 1f
    val scaleY = if (authoredHeight > 0.0) (box.height / authoredHeight).toFloat() else 1f
    val matrix = Matrix().apply {
        translate(box.x.toFloat(), box.y.toFloat())
        scale(scaleX, scaleY)
    }
    combined.transform(matrix)
    return combined
}
