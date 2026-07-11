package io.aequicor.visualization.engine.backend.compose

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import io.aequicor.visualization.subsystems.figures.PathGeometry
import io.aequicor.visualization.subsystems.figures.RectD
import io.aequicor.visualization.subsystems.figures.arrowGeometry
import io.aequicor.visualization.subsystems.figures.ellipseArcGeometry
import io.aequicor.visualization.subsystems.figures.compose.pathOperationOf
import io.aequicor.visualization.subsystems.figures.compose.strokeCapOf
import io.aequicor.visualization.subsystems.figures.compose.strokeJoinOf
import io.aequicor.visualization.subsystems.figures.compose.toComposePath
import io.aequicor.visualization.subsystems.figures.graphicGeometry
import io.aequicor.visualization.subsystems.figures.NoVectorAssets
import io.aequicor.visualization.subsystems.figures.VectorAssetProvider
import io.aequicor.visualization.subsystems.figures.VectorRef
import io.aequicor.visualization.subsystems.figures.lineGeometry
import io.aequicor.visualization.subsystems.figures.regularPolygonGeometry
import io.aequicor.visualization.subsystems.figures.starGeometry
import io.aequicor.visualization.engine.ir.layout.LayoutBox
import io.aequicor.visualization.subsystems.figures.BooleanOperationKind
import io.aequicor.visualization.engine.ir.model.GradientKind
import io.aequicor.visualization.engine.ir.model.ImageScaleMode
import io.aequicor.visualization.engine.ir.model.MediaKind
import io.aequicor.visualization.subsystems.figures.ShapeType
import io.aequicor.visualization.engine.ir.model.StrokeAlign
import io.aequicor.visualization.engine.ir.model.TextAlignVertical
import io.aequicor.visualization.engine.ir.resolve.ResolvedCornerRadius
import io.aequicor.visualization.engine.ir.resolve.ResolvedEffect
import io.aequicor.visualization.engine.ir.resolve.ResolvedMedia
import io.aequicor.visualization.engine.ir.resolve.ResolvedNode
import io.aequicor.visualization.engine.ir.resolve.ResolvedPaint
import io.aequicor.visualization.engine.ir.resolve.ResolvedStrokes
import kotlin.math.abs
import io.aequicor.visualization.subsystems.diagrams.compose.DiagramCanvasColors
import io.aequicor.visualization.subsystems.typography.compose.ComposeTypographyMeasurer
import io.aequicor.visualization.subsystems.typography.compose.drawRichText
import kotlin.math.ceil
import kotlin.math.min

internal class DesignDrawContext(
    val textMeasurer: TextMeasurer,
    val density: Density,
    val vectorAssets: VectorAssetProvider = NoVectorAssets,
    /** Shared rich-text measurer/cache for the text draw path. */
    val typography: ComposeTypographyMeasurer =
        ComposeTypographyMeasurer(textMeasurer, density),
    /** Theme defaults for embedded diagram nodes; the app bridges its tokens in. */
    val diagramColors: DiagramCanvasColors = DiagramCanvasColors(),
) {
    /** Per-graph edge-route cache for embedded diagram nodes (see [DiagramRouteCache]). */
    val diagramRoutes: DiagramRouteCache = DiagramRouteCache()
}

/** The laid-out box as a figures [RectD] (the coordinate seam the geometry adapter consumes). */
internal fun LayoutBox.rectD(): RectD = RectD(x, y, right, bottom)

private val PlaceholderImageColor = Color(0xFFD9E1EA)
private val UnknownPaintColor = Color(0xFFC9CFD6)

/** Explicit "missing asset" fill: an image paint with no bound asset id or url. */
private val MissingAssetColor = Color(0xFFF3D6DE)
private val MissingAssetHatch = Color(0x66B0455F)

/**
 * Draws a laid-out node tree in document coordinates (the caller applies zoom).
 *
 * [drawChild], when non-null, replaces the default recursive draw of each child so a caller can
 * wrap children in extra transforms (e.g. the Scene renderer's per-node motion overrides) while
 * still inheriting this node's group opacity, rotation, clip and sibling-mask wrapping.
 */
internal fun DrawScope.drawDesignBox(
    box: LayoutBox,
    context: DesignDrawContext,
    drawChild: (DrawScope.(LayoutBox) -> Unit)? = null,
) {
    val node = box.node
    if (node.opacity <= 0.0) return

    if (node.opacity < 1.0) {
        // Group opacity must not clip overflowing or rotated descendants: use huge
        // layer bounds; Skia intersects them with the current clip.
        val bounds = Rect(-1_000_000f, -1_000_000f, 1_000_000f, 1_000_000f)
        val layerPaint = Paint().apply { alpha = node.opacity.toFloat() }
        drawContext.canvas.saveLayer(bounds, layerPaint)
        drawWithRotation(box, context, drawChild)
        drawContext.canvas.restore()
    } else {
        drawWithRotation(box, context, drawChild)
    }
}

private fun DrawScope.drawWithRotation(
    box: LayoutBox,
    context: DesignDrawContext,
    drawChild: (DrawScope.(LayoutBox) -> Unit)?,
) {
    val rotation = box.node.rotation
    if (rotation != 0.0) {
        rotate(
            degrees = rotation.toFloat(),
            pivot = Offset((box.x + box.width / 2).toFloat(), (box.y + box.height / 2).toFloat()),
        ) {
            drawDesignBoxContent(box, context, drawChild)
        }
    } else {
        drawDesignBoxContent(box, context, drawChild)
    }
}

private fun DrawScope.drawDesignBoxContent(
    box: LayoutBox,
    context: DesignDrawContext,
    drawChild: (DrawScope.(LayoutBox) -> Unit)? = null,
) {
    val node = box.node
    node.booleanOp?.let { op ->
        drawBooleanOperation(box, op, context.vectorAssets)
        return
    }
    val outline = outlinePath(box, provider = context.vectorAssets)

    node.effects.filterIsInstance<ResolvedEffect.DropShadow>().forEach { shadow ->
        translate(shadow.offset.x.toFloat(), shadow.offset.y.toFloat()) {
            val alpha = (shadow.color.alpha / 255f) * 0.9f
            drawPath(outline, shadow.color.toComposeColor().copy(alpha = alpha))
        }
    }

    // A text node's fills paint the glyphs, not the node box.
    if (node.text == null) {
        node.fills.forEach { fill -> drawResolvedPaint(fill, outline, box) }
        // Region paint: each explicitly-filled region overpaints its own area (Figma region fills).
        node.regionPaints.forEach { region ->
            val regionPath = region.geometry.toComposePath(box.rectD())
            region.paints.forEach { paint -> drawResolvedPaint(paint, regionPath, box) }
        }
        node.effects.filterIsInstance<ResolvedEffect.InnerShadow>().forEach { shadow ->
            drawInnerShadow(shadow, outline)
        }
    }

    node.media?.let { media -> drawMediaPlaceholder(box, media, outline, context) }

    // Embedded diagram canvas: the graph paints above the wrapper fills and below any
    // children/strokes, clipped to the node outline (see DiagramNodeDrawing.kt).
    node.diagram?.let { graph -> drawDiagramNodeContent(box, graph, outline, context) }

    node.text?.let { text ->
        val laidOut = context.typography.layout(
            rich = text.toRichText(),
            maxWidth = box.width,
            fill = node.fills.toRichTextFill(),
            exactWidth = true,
        )
        val yOffset = when (text.style.textAlignVertical) {
            TextAlignVertical.Top -> 0.0
            TextAlignVertical.Center -> (box.height - laidOut.measured.height) / 2.0
            TextAlignVertical.Bottom -> box.height - laidOut.measured.height
        }
        drawRichText(laidOut, topLeft = Offset(box.x.toFloat(), (box.y + yOffset).toFloat()))
    }

    if (box.children.isNotEmpty()) {
        if (node.layout.clipsContent) {
            clipPath(outline) {
                drawChildBoxes(box, context, drawChild)
            }
        } else {
            drawChildBoxes(box, context, drawChild)
        }
    }

    if (node.type == "table") drawTableDecorations(box)

    node.strokes?.let { strokes -> drawStrokes(strokes, box, context.vectorAssets) }
}

/**
 * Draws children in document order with the mask approximation: a sibling
 * carrying a [io.aequicor.visualization.engine.ir.resolve.ResolvedMask] is not
 * painted itself — its geometry clips the siblings it applies to (explicit
 * `appliesTo` ids, else every following sibling). Alpha and luminance masks
 * both reduce to this shape clip; no alpha sampling happens.
 */
private fun DrawScope.drawChildBoxes(
    box: LayoutBox,
    context: DesignDrawContext,
    drawChild: (DrawScope.(LayoutBox) -> Unit)? = null,
) {
    fun DrawScope.paint(child: LayoutBox) {
        if (drawChild != null) drawChild(child) else drawDesignBox(child, context)
    }
    val children = box.children
    val masks = children.withIndex().filter { (_, child) -> child.node.mask != null }
    if (masks.isEmpty()) {
        children.forEach { child -> paint(child) }
        return
    }
    children.forEachIndexed { index, child ->
        if (child.node.mask != null) return@forEachIndexed // clip geometry only, never painted
        val clips = masks
            .filter { (maskIndex, mask) -> maskAppliesTo(mask, maskIndex, child, index) }
            .map { (_, mask) -> maskClipPath(mask, context.vectorAssets) }
        drawClippedBy(clips, 0) { paint(child) }
    }
}

private fun DrawScope.drawClippedBy(clips: List<Path>, index: Int, block: DrawScope.() -> Unit) {
    if (index >= clips.size) {
        block()
        return
    }
    clipPath(clips[index]) { drawClippedBy(clips, index + 1, block) }
}

/** Clip geometry of a mask node; the shape choice lives in [maskShapeFor]. */
private fun maskClipPath(mask: LayoutBox, provider: VectorAssetProvider = NoVectorAssets): Path {
    val rect = Rect(
        mask.x.toFloat(),
        mask.y.toFloat(),
        mask.right.toFloat(),
        mask.bottom.toFloat(),
    )
    return when (maskShapeFor(mask.node)) {
        MaskShape.RoundedRect -> roundedRectPath(rect, mask.node.cornerRadius)
        MaskShape.Ellipse -> Path().apply { addOval(rect) }
        MaskShape.VectorPath -> vectorPath(mask, rect, provider)
        MaskShape.BoundingBox -> Path().apply { addRect(rect) }
    }
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
            val missing = paint.assetId.isBlank() && paint.url.isBlank()
            if (missing) {
                // Explicit missing-asset state: a warning tint + a hatch so it never reads
                // as a valid, deliberately-flat placeholder.
                drawPath(outline, MissingAssetColor, alpha = paint.opacity.toFloat(), style = style)
                if (style == androidx.compose.ui.graphics.drawscope.Fill) {
                    clipPath(outline) { drawMissingAssetHatch(box) }
                }
            } else {
                drawPath(outline, PlaceholderImageColor, alpha = paint.opacity.toFloat(), style = style)
            }
        }
        is ResolvedPaint.Unknown -> {
            drawPath(outline, UnknownPaintColor, alpha = paint.opacity.toFloat(), style = style)
        }
    }
}

/** Diagonal hatch marking a missing image asset (caller clips to the node outline). */
private fun DrawScope.drawMissingAssetHatch(box: LayoutBox) {
    val top = box.y.toFloat()
    val bottom = box.bottom.toFloat()
    val right = box.right.toFloat()
    val span = bottom - top
    val step = 10f
    var x = box.x.toFloat() - span
    while (x < right) {
        drawLine(
            color = MissingAssetHatch,
            start = Offset(x, bottom),
            end = Offset(x + span, top),
            strokeWidth = 1.5f,
        )
        x += step
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

// --- Media placeholder -------------------------------------------------------

private val MediaAdornColor = Color(0xFF5B6B7F)
private val MediaCheckerColor = Color(0xFFC7D2DE)
private val MediaGlyphInk = Color(0xCC172033)
private const val MediaCheckerCell = 8.0
private const val MediaLabelFontSize = 9.0

/**
 * Adornments over the flat media placeholder fill: the notional crop window per
 * `fillMode` + `focalPoint` (no decoding — [mediaContentRect] only projects the
 * intrinsic size), a subtle focal crosshair, the asset id label, a checker
 * pattern for `Tile`, and a play glyph (+ poster label) for video.
 */
private fun DrawScope.drawMediaPlaceholder(
    box: LayoutBox,
    media: ResolvedMedia,
    outline: Path,
    context: DesignDrawContext,
) {
    val bounds = RenderRect(box.x, box.y, box.width, box.height)
    val focalX = media.focalPoint?.x ?: 0.5
    val focalY = media.focalPoint?.y ?: 0.5
    clipPath(outline) {
        when (media.fillMode) {
            ImageScaleMode.Tile -> checkerDarkCells(bounds, MediaCheckerCell).forEach { cell ->
                drawRect(
                    color = MediaCheckerColor,
                    topLeft = Offset(cell.x.toFloat(), cell.y.toFloat()),
                    size = Size(cell.width.toFloat(), cell.height.toFloat()),
                )
            }
            ImageScaleMode.Fill,
            ImageScaleMode.Crop,
            ImageScaleMode.Fit,
            ImageScaleMode.Stretch,
            -> {
                val content = mediaContentRect(
                    box = bounds,
                    intrinsicWidth = media.intrinsicWidth,
                    intrinsicHeight = media.intrinsicHeight,
                    fillMode = media.fillMode,
                    focalX = focalX,
                    focalY = focalY,
                )
                if (content != bounds) drawCropWindow(content)
                val (markerX, markerY) = focalMarker(content, focalX, focalY)
                drawFocalCrosshair(markerX, markerY)
            }
        }
        if (media.kind == MediaKind.Video) drawPlayGlyph(box)
        drawMediaLabel(box, media, context)
    }
}

private fun DrawScope.drawCropWindow(content: RenderRect) {
    drawRect(
        color = MediaAdornColor,
        topLeft = Offset(content.x.toFloat(), content.y.toFloat()),
        size = Size(content.width.toFloat(), content.height.toFloat()),
        alpha = 0.4f,
        style = Stroke(
            width = 1f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 3f)),
        ),
    )
}

private fun DrawScope.drawFocalCrosshair(x: Double, y: Double) {
    val center = Offset(x.toFloat(), y.toFloat())
    val arm = 5f
    drawLine(
        color = MediaAdornColor,
        start = Offset(center.x - arm, center.y),
        end = Offset(center.x + arm, center.y),
        strokeWidth = 1f,
        alpha = 0.6f,
    )
    drawLine(
        color = MediaAdornColor,
        start = Offset(center.x, center.y - arm),
        end = Offset(center.x, center.y + arm),
        strokeWidth = 1f,
        alpha = 0.6f,
    )
    drawCircle(
        color = MediaAdornColor,
        radius = 2.5f,
        center = center,
        alpha = 0.6f,
        style = Stroke(width = 1f),
    )
}

private fun DrawScope.drawPlayGlyph(box: LayoutBox) {
    val radius = (min(box.width, box.height) * 0.18).coerceIn(6.0, 28.0).toFloat()
    val center = Offset(
        (box.x + box.width / 2).toFloat(),
        (box.y + box.height / 2).toFloat(),
    )
    drawCircle(color = MediaGlyphInk, radius = radius, center = center)
    val triangle = Path().apply {
        moveTo(center.x - radius * 0.35f, center.y - radius * 0.5f)
        lineTo(center.x - radius * 0.35f, center.y + radius * 0.5f)
        lineTo(center.x + radius * 0.55f, center.y)
        close()
    }
    drawPath(triangle, Color.White)
}

private fun DrawScope.drawMediaLabel(
    box: LayoutBox,
    media: ResolvedMedia,
    context: DesignDrawContext,
) {
    if (box.width < 32.0 || box.height < 16.0) return
    val label = buildString {
        append(media.assetId)
        if (media.kind == MediaKind.Video && media.posterAssetId.isNotEmpty()) {
            append(" · poster: ").append(media.posterAssetId)
        }
    }
    if (label.isBlank()) return
    val maxWidth = ceil(box.width - 8.0).toInt().coerceAtLeast(1)
    val layout = context.textMeasurer.measure(
        text = AnnotatedString(label),
        style = TextStyle(
            color = MediaAdornColor,
            fontSize = docPxToSp(MediaLabelFontSize, context.density),
        ),
        overflow = TextOverflow.Ellipsis,
        maxLines = 1,
        constraints = Constraints(maxWidth = maxWidth),
    )
    drawText(
        textLayoutResult = layout,
        topLeft = Offset(
            (box.x + 4.0).toFloat(),
            (box.bottom - layout.size.height - 3.0).toFloat(),
        ),
    )
}

// --- Table hairlines -----------------------------------------------------------

private val TableHairlineColor = Color(0x33172033)
private val TableHeaderTint = Color(0x141F5FA8)

/**
 * Minimal table dressing for nodes lowered from `table`: 1px hairlines on the
 * grid track boundaries (derived from laid-out cell edges, lines centered in
 * the gutters) plus a translucent tint over the first grid row as the header
 * band. No per-cell alignment logic.
 */
private fun DrawScope.drawTableDecorations(box: LayoutBox) {
    val cells = box.children.filter { !it.node.layoutChild.absolute }
    if (cells.isEmpty()) return
    val layout = box.node.layout

    val firstRow = cells.mapNotNull { it.node.gridPlacement?.row }.minOrNull()
    val headerBottom = firstRow?.let { row ->
        cells.filter { it.node.gridPlacement?.row == row }.maxOfOrNull { it.bottom }
    }
    if (headerBottom != null && headerBottom > box.y) {
        drawRect(
            color = TableHeaderTint,
            topLeft = Offset(box.x.toFloat(), box.y.toFloat()),
            size = Size(box.width.toFloat(), (headerBottom - box.y).toFloat()),
        )
    }

    interiorTrackBoundaries(cells.map { it.x }, box.x + layout.paddingLeft, layout.columnGap)
        .forEach { x ->
            drawLine(
                color = TableHairlineColor,
                start = Offset(x.toFloat(), box.y.toFloat()),
                end = Offset(x.toFloat(), box.bottom.toFloat()),
                strokeWidth = 1f,
            )
        }
    interiorTrackBoundaries(cells.map { it.y }, box.y + layout.paddingTop, layout.rowGap)
        .forEach { y ->
            drawLine(
                color = TableHairlineColor,
                start = Offset(box.x.toFloat(), y.toFloat()),
                end = Offset(box.right.toFloat(), y.toFloat()),
                strokeWidth = 1f,
            )
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

private fun DrawScope.drawStrokes(
    strokes: ResolvedStrokes,
    box: LayoutBox,
    provider: VectorAssetProvider = NoVectorAssets,
) {
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
    val path = outlinePath(box, inset, provider)
    val style = Stroke(
        width = weight,
        pathEffect = dash,
        cap = strokeCapOf(strokes.cap),
        join = strokeJoinOf(strokes.join),
    )
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
        drawLine(color, start, end, weight.toFloat(), cap = strokeCapOf(strokes.cap), pathEffect = dash)
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
private fun outlinePath(
    box: LayoutBox,
    inset: Double = 0.0,
    provider: VectorAssetProvider = NoVectorAssets,
): Path {
    val node = box.node
    val rect = Rect(
        (box.x + inset).toFloat(),
        (box.y + inset).toFloat(),
        (box.right - inset).toFloat(),
        (box.bottom - inset).toFloat(),
    )
    val insetRect = RectD(box.x + inset, box.y + inset, box.right - inset, box.bottom - inset)
    return when (node.shape?.shape) {
        // Ellipse/rounded-rect stay native for pixel-exact ovals and inset-aware corner clamping.
        ShapeType.Ellipse -> {
            val shape = node.shape!!
            val sweep = shape.arcSweepDeg
            val inner = shape.innerRadius ?: 0.0
            val isArc = (sweep != null && abs(sweep) < 360.0) || inner > 0.0
            if (isArc) {
                ellipseArcGeometry(insetRect, shape.arcStartDeg ?: 0.0, sweep ?: 360.0, inner).toComposePath(insetRect)
            } else {
                Path().apply { addOval(rect) }
            }
        }
        ShapeType.Polygon -> regularPolygonGeometry(insetRect, node.shape?.pointCount ?: 3).toComposePath(insetRect)
        ShapeType.Star ->
            starGeometry(insetRect, node.shape?.pointCount ?: 5, node.shape?.innerRadius ?: 0.4).toComposePath(insetRect)
        ShapeType.Line -> lineGeometry(insetRect).toComposePath(insetRect)
        ShapeType.Arrow -> arrowGeometry(insetRect, node.strokes?.weight ?: 1.0).toComposePath(insetRect)
        ShapeType.Vector -> vectorPath(box, rect, provider)
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

/**
 * Vector geometry precedence:
 * 1. inline lowering ([ResolvedNode.geometry]: inline `d` or a structural network in view-box
 *    space) — always wins when present;
 * 2. an external SVG asset (`pathRef`) then a design-system icon (`iconRef`), dereferenced via
 *    the injected [VectorAssetProvider];
 * 3. the box rectangle, when nothing is lowered and nothing dereferences.
 *
 * A dereferenced graphic is built into the same [PathGeometry] IR (parse each `d`, concatenate,
 * fill rule from the first path, view-box from the graphic or the path bounds) so the adapter
 * applies the identical meet-fit into the node box as an inline vector.
 */
private fun vectorPath(box: LayoutBox, rect: Rect, provider: VectorAssetProvider = NoVectorAssets): Path {
    box.node.geometry?.let { return it.toComposePath(box.rectD()) }

    val shape = box.node.shape
    val ref = when {
        shape == null -> null
        shape.pathRef.isNotBlank() -> VectorRef.Svg(shape.pathRef)
        shape.iconRef.isNotBlank() -> VectorRef.Icon(shape.iconRef)
        else -> null
    }
    ref?.let { provider.resolve(it) }
        ?.let { graphicGeometry(it) }
        ?.let { return it.toComposePath(box.rectD()) }

    return Path().apply { addRect(rect) }
}

/**
 * Combines the operand children of a boolean node into one path via `Path.op` and paints the
 * boolean node's own fills/strokes on it; the children are not drawn as ordinary siblings.
 */
private fun DrawScope.drawBooleanOperation(
    box: LayoutBox,
    op: BooleanOperationKind,
    provider: VectorAssetProvider = NoVectorAssets,
) {
    val node = box.node
    val merged = booleanOutline(box, provider) ?: return

    node.effects.filterIsInstance<ResolvedEffect.DropShadow>().forEach { shadow ->
        translate(shadow.offset.x.toFloat(), shadow.offset.y.toFloat()) {
            val alpha = (shadow.color.alpha / 255f) * 0.9f
            drawPath(merged, shadow.color.toComposeColor().copy(alpha = alpha))
        }
    }
    node.fills.forEach { fill -> drawResolvedPaint(fill, merged, box) }
    node.strokes?.let { strokes ->
        if (strokes.paints.isNotEmpty() && strokes.weight > 0.0) {
            val style = Stroke(
                width = strokes.weight.toFloat(),
                cap = strokeCapOf(strokes.cap),
                join = strokeJoinOf(strokes.join),
            )
            strokes.paints.forEach { paint -> drawResolvedPaint(paint, merged, box, style) }
        }
    }
}

/** Folds a boolean node's children left-to-right; leaves (and nested booleans) use their outline. */
private fun booleanOutline(box: LayoutBox, provider: VectorAssetProvider = NoVectorAssets): Path? {
    val op = box.node.booleanOp ?: return outlinePath(box, provider = provider)
    val childPaths = box.children.mapNotNull { booleanOutline(it, provider) }
    if (childPaths.isEmpty()) return null
    val operation = pathOperationOf(op)
    var merged = childPaths.first()
    for (index in 1 until childPaths.size) {
        val next = childPaths[index]
        merged = Path().apply { op(merged, next, operation) }
    }
    return merged
}
