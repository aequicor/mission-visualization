package io.aequicor.visualization.subsystems.diagrams.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import io.aequicor.visualization.subsystems.diagrams.arrows.arrowheadPath
import io.aequicor.visualization.subsystems.diagrams.arrows.arrowheadsForRelation
import io.aequicor.visualization.subsystems.diagrams.geometry.outlinePath
import io.aequicor.visualization.subsystems.diagrams.model.DiagramArrowhead
import io.aequicor.visualization.subsystems.diagrams.model.DiagramArrowheadKind
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNode
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodeId
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodePayload
import io.aequicor.visualization.subsystems.diagrams.model.DiagramRelation
import io.aequicor.visualization.subsystems.diagrams.model.DiagramStrokePattern
import io.aequicor.visualization.subsystems.diagrams.model.TableNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlActivityKind
import io.aequicor.visualization.subsystems.diagrams.model.UmlActivityNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlActorNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlClassNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlComponentNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlDeploymentNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlLifelineNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlNoteNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlPackageNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlStateKind
import io.aequicor.visualization.subsystems.diagrams.model.UmlStateNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlUseCaseNode
import io.aequicor.visualization.subsystems.diagrams.path.DiagramPathSegment
import io.aequicor.visualization.subsystems.diagrams.path.DiagramPoint
import io.aequicor.visualization.subsystems.diagrams.path.DiagramRect

/**
 * Theme tokens for the small dropdown previews, passed in by the caller so the
 * subsystem stays theme-agnostic (same convention as `FigurePreviewStyle`).
 */
@Immutable
data class DiagramPreviewStyle(
    val ink: Color,
    val fill: Color,
    val accent: Color,
    val surface: Color,
)

/**
 * A small glyph of a node payload kind — the left visual of node-type dropdown rows
 * (project convention: every menu row carries a visual). Pass a representative
 * [payload] instance (e.g. `UmlClassNode(name = "")`).
 */
@Composable
fun DiagramNodePreview(
    payload: DiagramNodePayload,
    style: DiagramPreviewStyle,
    modifier: Modifier = Modifier.size(18.dp),
) {
    Canvas(modifier) {
        val stroke = Stroke(1.3f)
        val inset = 2.5f
        val box = DiagramRect(
            inset.toDouble(),
            (inset + 1f).toDouble(),
            (size.width - 2 * inset).toDouble(),
            (size.height - 2 * inset - 2f).toDouble(),
        )
        when (payload) {
            is UmlClassNode -> {
                drawPreviewBody(box, style, stroke)
                val thirds = box.height / 3.0
                listOf(1, 2).forEach { index ->
                    val y = (box.top + thirds * index).toFloat()
                    drawLine(style.ink, Offset(box.left.toFloat(), y), Offset(box.right.toFloat(), y), 1f)
                }
            }

            is UmlLifelineNode -> {
                val head = DiagramRect(box.left, box.top, box.width, box.height * 0.32)
                drawPreviewBody(head, style, stroke)
                val cx = box.centerX.toFloat()
                drawLine(
                    style.ink,
                    Offset(cx, (head.bottom + 1).toFloat()),
                    Offset(cx, box.bottom.toFloat()),
                    1.2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.4f, 2f)),
                )
            }

            is UmlStateNode -> when (payload.kind) {
                UmlStateKind.INITIAL -> drawCircle(style.ink, radius = size.minDimension * 0.24f, center = center)
                UmlStateKind.FINAL -> {
                    drawCircle(style.ink, radius = size.minDimension * 0.3f, center = center, style = stroke)
                    drawCircle(style.ink, radius = size.minDimension * 0.16f, center = center)
                }

                else -> drawPreviewRoundRect(box, style, stroke, radius = size.minDimension * 0.28f)
            }

            is UmlActivityNode -> when (payload.kind) {
                UmlActivityKind.START -> drawCircle(style.ink, radius = size.minDimension * 0.24f, center = center)
                UmlActivityKind.END -> {
                    drawCircle(style.ink, radius = size.minDimension * 0.3f, center = center, style = stroke)
                    drawCircle(style.ink, radius = size.minDimension * 0.15f, center = center)
                }

                UmlActivityKind.FORK, UmlActivityKind.JOIN -> drawRect(
                    style.ink,
                    topLeft = Offset(box.left.toFloat(), (box.centerY - 1.5).toFloat()),
                    size = Size(box.width.toFloat(), 3f),
                )

                UmlActivityKind.DECISION -> drawPreviewOutline(payload, box, style, stroke)
                UmlActivityKind.ACTION -> drawPreviewRoundRect(box, style, stroke, radius = size.minDimension * 0.24f)
            }

            is UmlActorNode -> drawStickFigure(box, style.ink, 1.3f)

            is UmlUseCaseNode -> {
                drawOval(style.fill, Offset(box.left.toFloat(), (box.top + 2).toFloat()), Size(box.width.toFloat(), (box.height - 4).toFloat()))
                drawOval(style.ink, Offset(box.left.toFloat(), (box.top + 2).toFloat()), Size(box.width.toFloat(), (box.height - 4).toFloat()), style = stroke)
            }

            is UmlComponentNode -> {
                val body = DiagramRect(box.left + 3.0, box.top, box.width - 3.0, box.height)
                drawPreviewBody(body, style, stroke)
                listOf(0.3, 0.55).forEach { fraction ->
                    drawRect(
                        style.surface,
                        topLeft = Offset(box.left.toFloat(), (box.top + box.height * fraction).toFloat()),
                        size = Size(5f, 3f),
                    )
                    drawRect(
                        style.ink,
                        topLeft = Offset(box.left.toFloat(), (box.top + box.height * fraction).toFloat()),
                        size = Size(5f, 3f),
                        style = Stroke(1f),
                    )
                }
            }

            is UmlDeploymentNode -> {
                val depth = 3.0
                val front = DiagramRect(box.left, box.top + depth, box.width - depth, box.height - depth)
                // Top and side edges of the 3D box.
                drawLine(style.ink, Offset(front.left.toFloat(), front.top.toFloat()), Offset((front.left + depth).toFloat(), box.top.toFloat()), 1f)
                drawLine(style.ink, Offset((front.left + depth).toFloat(), box.top.toFloat()), Offset(box.right.toFloat(), box.top.toFloat()), 1f)
                drawLine(style.ink, Offset(box.right.toFloat(), box.top.toFloat()), Offset(front.right.toFloat(), front.top.toFloat()), 1f)
                drawLine(style.ink, Offset(box.right.toFloat(), box.top.toFloat()), Offset(box.right.toFloat(), (box.bottom - depth).toFloat()), 1f)
                drawLine(style.ink, Offset(box.right.toFloat(), (box.bottom - depth).toFloat()), Offset(front.right.toFloat(), front.bottom.toFloat()), 1f)
                drawPreviewBody(front, style, stroke)
            }

            is UmlNoteNode -> {
                drawPreviewOutline(payload, box, style, stroke)
                val fold = (box.width * 0.28).toFloat()
                drawLine(style.ink, Offset(box.right.toFloat() - fold, box.top.toFloat()), Offset(box.right.toFloat() - fold, box.top.toFloat() + fold), 1f)
                drawLine(style.ink, Offset(box.right.toFloat() - fold, box.top.toFloat() + fold), Offset(box.right.toFloat(), box.top.toFloat() + fold), 1f)
            }

            is UmlPackageNode -> {
                val tab = DiagramRect(box.left, box.top, box.width * 0.45, box.height * 0.28)
                val body = DiagramRect(box.left, tab.bottom, box.width, box.height - tab.height)
                drawPreviewBody(tab, style, Stroke(1f))
                drawPreviewBody(body, style, stroke)
            }

            is TableNode -> {
                drawPreviewBody(box, style, stroke)
                val columnX = (box.left + box.width / 2.0).toFloat()
                drawLine(style.ink, Offset(columnX, box.top.toFloat()), Offset(columnX, box.bottom.toFloat()), 1f)
                listOf(1, 2).forEach { index ->
                    val y = (box.top + box.height * index / 3.0).toFloat()
                    drawLine(style.ink, Offset(box.left.toFloat(), y), Offset(box.right.toFloat(), y), 1f)
                }
            }

            is DiagramNodePayload.ContainerNode -> {
                drawPreviewBody(box, style, stroke)
                val bandY = (box.top + box.height * 0.3).toFloat()
                drawLine(style.ink, Offset(box.left.toFloat(), bandY), Offset(box.right.toFloat(), bandY), 1f)
            }

            is DiagramNodePayload.SwimlaneNode -> {
                drawPreviewBody(box, style, stroke)
                listOf(1, 2).forEach { index ->
                    val y = (box.top + box.height * index / 3.0).toFloat()
                    drawLine(style.ink, Offset(box.left.toFloat(), y), Offset(box.right.toFloat(), y), 1f)
                }
            }

            is DiagramNodePayload.ErEntityNode -> {
                drawPreviewBody(box, style, stroke)
                val bandY = (box.top + box.height * 0.34).toFloat()
                drawRect(style.accent.copy(alpha = 0.35f), Offset(box.left.toFloat(), box.top.toFloat()), Size(box.width.toFloat(), bandY - box.top.toFloat()))
                drawLine(style.ink, Offset(box.left.toFloat(), bandY), Offset(box.right.toFloat(), bandY), 1f)
            }

            else -> drawPreviewOutline(payload, box, style, stroke)
        }
    }
}

/**
 * A small glyph of an edge relation — line with its UML/ER notation heads and dash
 * pattern — the left visual of relation dropdown rows.
 */
@Composable
fun DiagramRelationPreview(
    relation: DiagramRelation,
    style: DiagramPreviewStyle,
    modifier: Modifier = Modifier.size(18.dp),
) {
    Canvas(modifier) {
        val notation = arrowheadsForRelation(relation)
        val headSize = size.minDimension * 0.3
        val y = (size.height / 2f).toDouble()
        val left = DiagramPoint(1.5, y)
        val right = DiagramPoint(size.width - 1.5, y)

        val sourceGeometry = arrowheadPath(notation.source.scaledTo(headSize), tip = left, direction = DiagramPoint(-1.0, 0.0))
        val targetGeometry = arrowheadPath(notation.target.scaledTo(headSize), tip = right, direction = DiagramPoint(1.0, 0.0))

        val lineStart = left.x + sourceGeometry.lineShorten
        val lineEnd = right.x - targetGeometry.lineShorten
        drawLine(
            style.ink,
            Offset(lineStart.toFloat(), y.toFloat()),
            Offset(lineEnd.toFloat(), y.toFloat()),
            strokeWidth = 1.4f,
            cap = StrokeCap.Round,
            pathEffect = if (notation.pattern != DiagramStrokePattern.SOLID) {
                PathEffect.dashPathEffect(floatArrayOf(3f, 2.4f))
            } else null,
        )
        drawArrowheadGeometry(sourceGeometry, style.ink, style.surface, 1.2f)
        drawArrowheadGeometry(targetGeometry, style.ink, style.surface, 1.2f)
    }
}

private fun DiagramArrowhead.scaledTo(size: Double): DiagramArrowhead =
    if (kind == DiagramArrowheadKind.NONE) this else copy(size = size)

/** Body outline of an arbitrary payload, via the core outline of a synthetic node. */
private fun DrawScope.drawPreviewOutline(
    payload: DiagramNodePayload,
    box: DiagramRect,
    style: DiagramPreviewStyle,
    stroke: Stroke,
) {
    val node = DiagramNode(
        id = PreviewNodeId,
        x = box.x,
        y = box.y,
        width = box.width,
        height = box.height,
        payload = payload,
    )
    val path = node.outlinePath().toComposePath()
    val closed = node.outlinePath().segments.any { it is DiagramPathSegment.Close }
    if (closed) drawPath(path, style.fill)
    drawPath(path, style.ink, style = stroke)
}

private fun DrawScope.drawPreviewBody(box: DiagramRect, style: DiagramPreviewStyle, stroke: Stroke) {
    val topLeft = Offset(box.left.toFloat(), box.top.toFloat())
    val boxSize = Size(box.width.toFloat(), box.height.toFloat())
    drawRect(style.fill, topLeft = topLeft, size = boxSize)
    drawRect(style.ink, topLeft = topLeft, size = boxSize, style = stroke)
}

private fun DrawScope.drawPreviewRoundRect(box: DiagramRect, style: DiagramPreviewStyle, stroke: Stroke, radius: Float) {
    val topLeft = Offset(box.left.toFloat(), box.top.toFloat())
    val boxSize = Size(box.width.toFloat(), box.height.toFloat())
    val corner = CornerRadius(radius, radius)
    drawRoundRect(style.fill, topLeft = topLeft, size = boxSize, cornerRadius = corner)
    drawRoundRect(style.ink, topLeft = topLeft, size = boxSize, cornerRadius = corner, style = stroke)
}

private val PreviewNodeId = DiagramNodeId("preview")
