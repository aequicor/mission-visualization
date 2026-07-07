package io.aequicor.visualization.engine.backend.compose

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import io.aequicor.visualization.engine.ir.layout.LayoutBox
import io.aequicor.visualization.engine.ir.model.GuideOrientation
import io.aequicor.visualization.engine.ir.model.LayoutGridType

private val GuideColor = Color(0xFF00B5FF)
private val LayoutGridDefaultColor = Color(0xFFFF0000)
private val AnnotationPinColor = Color(0xFFE97155)
private const val LayoutGridFillAlpha = 0.08f
private const val LayoutGridLineAlpha = 0.25f
private const val AnnotationPinRadius = 8f

/**
 * Draws the enabled editor overlays over the finished artboard content, in
 * document coordinates ([hairline] = one screen pixel under the current zoom).
 * Guides and layout grids come from the nodes that carry them; annotations are
 * numbered in tree order.
 */
internal fun DrawScope.drawDesignOverlays(
    root: LayoutBox,
    options: DesignOverlayOptions,
    context: DesignDrawContext,
    hairline: Float,
) {
    val boxes = root.allBoxes()
    if (options.showLayoutGrids) {
        boxes.forEach { box -> drawLayoutGrids(box, hairline) }
    }
    if (options.showGuides) {
        boxes.forEach { box -> drawGuides(box, root, hairline) }
    }
    if (options.showAnnotations) {
        boxes.filter { it.node.annotation != null }.forEachIndexed { index, box ->
            drawAnnotationPin(box, index + 1, context)
        }
    }
}

private fun DrawScope.drawGuides(box: LayoutBox, root: LayoutBox, hairline: Float) {
    box.node.guides.forEach { guide ->
        when (guide.orientation) {
            GuideOrientation.Vertical -> drawLine(
                color = GuideColor,
                start = Offset((box.x + guide.position).toFloat(), root.y.toFloat()),
                end = Offset((box.x + guide.position).toFloat(), root.bottom.toFloat()),
                strokeWidth = hairline,
            )
            GuideOrientation.Horizontal -> drawLine(
                color = GuideColor,
                start = Offset(root.x.toFloat(), (box.y + guide.position).toFloat()),
                end = Offset(root.right.toFloat(), (box.y + guide.position).toFloat()),
                strokeWidth = hairline,
            )
        }
    }
}

private fun DrawScope.drawLayoutGrids(box: LayoutBox, hairline: Float) {
    box.node.layoutGrids.filter { it.visible }.forEach { definition ->
        val color = definition.color?.toComposeColor() ?: LayoutGridDefaultColor
        when (definition.type) {
            LayoutGridType.Columns -> layoutGridSlices(
                offset = box.x,
                extent = box.width,
                count = definition.count,
                size = definition.size,
                gutter = definition.gutter,
                margin = definition.margin,
                alignment = definition.alignment,
            ).forEach { slice ->
                drawRect(
                    color = color,
                    topLeft = Offset(slice.start.toFloat(), box.y.toFloat()),
                    size = Size(slice.size.toFloat(), box.height.toFloat()),
                    alpha = LayoutGridFillAlpha,
                )
            }
            LayoutGridType.Rows -> layoutGridSlices(
                offset = box.y,
                extent = box.height,
                count = definition.count,
                size = definition.size,
                gutter = definition.gutter,
                margin = definition.margin,
                alignment = definition.alignment,
            ).forEach { slice ->
                drawRect(
                    color = color,
                    topLeft = Offset(box.x.toFloat(), slice.start.toFloat()),
                    size = Size(box.width.toFloat(), slice.size.toFloat()),
                    alpha = LayoutGridFillAlpha,
                )
            }
            LayoutGridType.Grid -> {
                val step = definition.size ?: return@forEach
                gridLinePositions(box.x, box.width, step).forEach { x ->
                    drawLine(
                        color = color,
                        start = Offset(x.toFloat(), box.y.toFloat()),
                        end = Offset(x.toFloat(), box.bottom.toFloat()),
                        strokeWidth = hairline,
                        alpha = LayoutGridLineAlpha,
                    )
                }
                gridLinePositions(box.y, box.height, step).forEach { y ->
                    drawLine(
                        color = color,
                        start = Offset(box.x.toFloat(), y.toFloat()),
                        end = Offset(box.right.toFloat(), y.toFloat()),
                        strokeWidth = hairline,
                        alpha = LayoutGridLineAlpha,
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawAnnotationPin(box: LayoutBox, number: Int, context: DesignDrawContext) {
    val center = Offset(box.x.toFloat(), box.y.toFloat())
    drawCircle(color = AnnotationPinColor, radius = AnnotationPinRadius, center = center)
    drawCircle(
        color = Color.White,
        radius = AnnotationPinRadius,
        center = center,
        style = Stroke(width = 1.5f),
    )
    val layout = context.textMeasurer.measure(
        text = AnnotatedString(number.toString()),
        style = TextStyle(
            color = Color.White,
            fontSize = docPxToSp(10.0, context.density),
            fontWeight = FontWeight.Bold,
        ),
    )
    drawText(
        textLayoutResult = layout,
        topLeft = Offset(
            center.x - layout.size.width / 2f,
            center.y - layout.size.height / 2f,
        ),
    )
}

/** Document px map 1:1 to draw px, so label sizes convert through the density. */
internal fun docPxToSp(value: Double, density: Density): TextUnit =
    with(density) { value.toFloat().toSp() }
