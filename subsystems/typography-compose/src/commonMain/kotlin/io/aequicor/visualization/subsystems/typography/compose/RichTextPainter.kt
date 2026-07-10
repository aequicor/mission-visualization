package io.aequicor.visualization.subsystems.typography.compose

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import io.aequicor.visualization.subsystems.typography.DecorationKind
import io.aequicor.visualization.subsystems.typography.DecorationStyle
import io.aequicor.visualization.subsystems.typography.resolvePx

/**
 * Paints a [LaidOutRichText]: paragraph text, list markers and custom decorations.
 *
 * Stateless like the anchoring renderer: the caller owns canvas transforms (zoom/pan)
 * and colors; this module never sees the editor viewport or theme.
 */
fun DrawScope.drawRichText(laidOut: LaidOutRichText, topLeft: Offset = Offset.Zero) {
    laidOut.paragraphs.forEach { paragraph ->
        val origin = Offset(topLeft.x + paragraph.x.toFloat(), topLeft.y + paragraph.y.toFloat())

        // Underlines sit behind glyphs (descenders overlap them, as native rendering does).
        paragraph.spec.customDecorations
            .filter { it.spec.kind == DecorationKind.Underline }
            .forEach { drawDecoration(paragraph.result, it, origin) }

        drawText(paragraph.result, topLeft = origin)

        paragraph.markerLayout?.let { marker ->
            val gap = paragraph.spec.indent * 0.25
            val markerX = topLeft.x + paragraph.spec.indent - marker.size.width - gap
            val baselineAlign = if (paragraph.result.lineCount > 0 && marker.lineCount > 0) {
                paragraph.result.getLineBaseline(0) - marker.getLineBaseline(0)
            } else 0f
            drawText(
                marker,
                topLeft = Offset(markerX.toFloat().coerceAtLeast(topLeft.x), origin.y + baselineAlign),
            )
        }

        // Strikethrough crosses over the glyphs.
        paragraph.spec.customDecorations
            .filter { it.spec.kind == DecorationKind.Strikethrough }
            .forEach { drawDecoration(paragraph.result, it, origin) }
    }
}

/** Selection highlight; draw before [drawRichText] so glyphs stay on top. */
fun DrawScope.drawRichTextSelection(
    laidOut: LaidOutRichText,
    start: Int,
    end: Int,
    color: Color,
    topLeft: Offset = Offset.Zero,
) {
    if (end <= start) return
    translate(topLeft.x, topLeft.y) {
        drawPath(laidOut.selectionPath(start, end), color)
    }
}

fun DrawScope.drawRichTextCaret(
    laidOut: LaidOutRichText,
    offset: Int,
    color: Color,
    topLeft: Offset = Offset.Zero,
    width: Float = 1.5f,
) {
    val rect = laidOut.caretRect(offset)
    drawLine(
        color = color,
        start = Offset(topLeft.x + rect.left.toFloat(), topLeft.y + rect.top.toFloat()),
        end = Offset(topLeft.x + rect.left.toFloat(), topLeft.y + rect.bottom.toFloat()),
        strokeWidth = width,
    )
}

private fun DrawScope.drawDecoration(
    result: TextLayoutResult,
    decoration: CustomDecoration,
    origin: Offset,
) {
    val thickness = decoration.spec.thickness.resolvePx(decoration.fontSize).toFloat()
    val from = decoration.start.coerceIn(0, result.layoutInput.text.length)
    val to = decoration.end.coerceIn(from, result.layoutInput.text.length)
    if (to <= from) return

    val firstLine = result.getLineForOffset(from)
    val lastLine = result.getLineForOffset((to - 1).coerceAtLeast(from))
    for (line in firstLine..lastLine) {
        val segStart = maxOf(from, result.getLineStart(line))
        val segEnd = minOf(to, result.getLineEnd(line, visibleEnd = true))
        if (segEnd <= segStart) continue
        val a = result.getHorizontalPosition(segStart, usePrimaryDirection = true)
        val b = result.getHorizontalPosition(segEnd, usePrimaryDirection = true)
        val left = minOf(a, b)
        val right = maxOf(a, b)
        val baseline = result.getLineBaseline(line)
        val y = when (decoration.spec.kind) {
            DecorationKind.Underline -> baseline + (decoration.fontSize * 0.12f).toFloat() + thickness / 2
            DecorationKind.Strikethrough -> baseline - (decoration.fontSize * 0.28f).toFloat()
            DecorationKind.None -> continue
        }

        val segments = if (decoration.spec.kind == DecorationKind.Underline && decoration.spec.skipInk) {
            skipInkSegments(result, segStart, segEnd, left, right, pad = thickness * 1.5f)
        } else {
            listOf(left to right)
        }
        segments.forEach { (x0, x1) ->
            if (x1 <= x0) return@forEach
            drawDecorationLine(
                style = decoration.spec.style,
                color = decoration.color,
                start = Offset(origin.x + x0, origin.y + y),
                end = Offset(origin.x + x1, origin.y + y),
                thickness = thickness,
            )
        }
    }
}

/**
 * Best-effort skip-ink: carve the descender glyphs' bounding boxes out of the line.
 * True glyph-outline intercepts are not reachable from common Compose; boxes of the
 * usual descender characters are a close visual approximation.
 */
private fun skipInkSegments(
    result: TextLayoutResult,
    segStart: Int,
    segEnd: Int,
    left: Float,
    right: Float,
    pad: Float,
): List<Pair<Float, Float>> {
    val text = result.layoutInput.text.text
    val holes = mutableListOf<Pair<Float, Float>>()
    for (i in segStart until segEnd) {
        val ch = text.getOrNull(i) ?: continue
        if (ch !in DESCENDERS) continue
        val box = result.getBoundingBox(i)
        holes += (box.left - pad) to (box.right + pad)
    }
    return carveSegments(left, right, holes)
}

/**
 * Subtracts the [holes] (x-intervals) from `[left, right]`, returning the surviving
 * segments left-to-right. Pure so skip-ink carving is unit-testable without a layout.
 */
internal fun carveSegments(left: Float, right: Float, holes: List<Pair<Float, Float>>): List<Pair<Float, Float>> {
    if (holes.isEmpty()) return listOf(left to right)
    val sorted = holes.sortedBy { it.first }
    val segments = mutableListOf<Pair<Float, Float>>()
    var cursor = left
    sorted.forEach { (h0, h1) ->
        if (h0 > cursor) segments += cursor to minOf(h0, right)
        cursor = maxOf(cursor, h1)
    }
    if (cursor < right) segments += cursor to right
    return segments
}

private const val DESCENDERS = "gjpqyçĝğġģįĵŋņŗşţųय؟"

/** One quadratic segment of a [DecorationStyle.Wavy] stroke: control point plus end point. */
internal data class WaveSegment(val controlX: Float, val controlY: Float, val endX: Float, val endY: Float)

/**
 * Pure geometry of a [DecorationStyle.Wavy] decoration: quadratic segments whose control
 * points alternate above/below [baselineY] (first above), advancing by [wavelength] until
 * [right]. Extracted so the wave path is unit-testable without a canvas.
 */
internal fun wavePoints(
    left: Float,
    right: Float,
    baselineY: Float,
    amplitude: Float,
    wavelength: Float,
): List<WaveSegment> {
    if (left >= right) return emptyList()
    val step = if (wavelength <= 0f) 0.01f else wavelength
    val segments = mutableListOf<WaveSegment>()
    var x = left
    var up = true
    while (x < right) {
        val nextX = minOf(x + step, right)
        val controlY = if (up) baselineY - amplitude * 2 else baselineY + amplitude * 2
        segments += WaveSegment((x + nextX) / 2, controlY, nextX, baselineY)
        up = !up
        x = nextX
    }
    return segments
}

private fun DrawScope.drawDecorationLine(
    style: DecorationStyle,
    color: Color,
    start: Offset,
    end: Offset,
    thickness: Float,
) {
    when (style) {
        DecorationStyle.Solid -> drawLine(color, start, end, strokeWidth = thickness)
        DecorationStyle.Dashed -> drawLine(
            color, start, end,
            strokeWidth = thickness,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(thickness * 3, thickness * 2)),
        )
        DecorationStyle.Dotted -> drawLine(
            color, start, end,
            strokeWidth = thickness,
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.01f, thickness * 2)),
        )
        DecorationStyle.Wavy -> {
            val amplitude = thickness * 1.2f
            val wavelength = (thickness * 6f).coerceAtLeast(4f)
            val path = Path()
            path.moveTo(start.x, start.y)
            wavePoints(start.x, end.x, start.y, amplitude, wavelength).forEach { segment ->
                path.quadraticTo(segment.controlX, segment.controlY, segment.endX, segment.endY)
            }
            drawPath(path, color, style = Stroke(width = thickness))
        }
    }
}
