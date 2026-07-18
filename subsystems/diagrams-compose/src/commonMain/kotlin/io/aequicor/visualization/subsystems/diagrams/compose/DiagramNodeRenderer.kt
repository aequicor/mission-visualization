package io.aequicor.visualization.subsystems.diagrams.compose

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import io.aequicor.visualization.subsystems.diagrams.geometry.labelBox
import io.aequicor.visualization.subsystems.diagrams.geometry.labelPadding
import io.aequicor.visualization.subsystems.diagrams.geometry.outlinePath
import io.aequicor.visualization.subsystems.diagrams.model.BpmnNodeKind
import io.aequicor.visualization.subsystems.diagrams.model.DiagramLabel
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNode
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodePayload
import io.aequicor.visualization.subsystems.diagrams.model.DiagramOrientation
import io.aequicor.visualization.subsystems.diagrams.model.DiagramShapeKind
import io.aequicor.visualization.subsystems.diagrams.model.DiagramStyle
import io.aequicor.visualization.subsystems.diagrams.model.TableNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlActivityKind
import io.aequicor.visualization.subsystems.diagrams.model.UmlActivityNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlActorNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlClassNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlComponentNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlDeploymentNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlLifelineNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlMember
import io.aequicor.visualization.subsystems.diagrams.model.lifelineTop
import io.aequicor.visualization.subsystems.diagrams.model.umlLifelineHeadHeight
import io.aequicor.visualization.subsystems.diagrams.model.UmlNoteNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlPackageNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlStateKind
import io.aequicor.visualization.subsystems.diagrams.model.UmlStateNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlUseCaseNode
import io.aequicor.visualization.subsystems.diagrams.path.DiagramPath
import io.aequicor.visualization.subsystems.diagrams.path.DiagramRect
import io.aequicor.visualization.subsystems.diagrams.path.diagramPath
import io.aequicor.visualization.subsystems.typography.AlignHorizontal
import io.aequicor.visualization.subsystems.typography.compose.ComposeTypographyMeasurer
import kotlin.math.min

/** Height of container / swimlane / package title bands, document px. */
internal const val TITLE_BAND_HEIGHT = 26.0

/** Fold size of the UML note corner (matches the core outline). */
internal const val NOTE_FOLD = 12.0

/**
 * Draws one node: payload-specific body + decorations + labels. Coordinates are document
 * px; the caller has already ordered nodes by layer/z and culled invisible ones.
 */
internal fun DrawScope.drawDiagramNode(
    node: DiagramNode,
    colors: DiagramCanvasColors,
    measurer: ComposeTypographyMeasurer,
) {
    if (!node.visible || node.width <= 0.0 || node.height <= 0.0) return
    if (node.rotation != 0.0) {
        rotate(
            degrees = node.rotation.toFloat(),
            pivot = Offset(node.bounds.centerX.toFloat(), node.bounds.centerY.toFloat()),
        ) {
            drawDiagramNodeContent(node, colors, measurer)
        }
    } else {
        drawDiagramNodeContent(node, colors, measurer)
    }
}

private fun DrawScope.drawDiagramNodeContent(
    node: DiagramNode,
    colors: DiagramCanvasColors,
    measurer: ComposeTypographyMeasurer,
) {
    val bounds = node.bounds
    val style = node.style
    val seed = sketchSeed(node.id.value)
    val ink = style.stroke?.toComposeColor() ?: colors.nodeStroke
    val labelInk = colors.labelInk.applyOpacity(style.opacity)

    when (val payload = node.payload) {
        is DiagramNodePayload.BasicShape -> {
            if (payload.shape != DiagramShapeKind.TEXT) {
                drawStyledPath(node.outlinePath(), style, colors, seed)
                if (payload.shape == DiagramShapeKind.CYLINDER) {
                    // Front rim of the top ellipse.
                    val ry = min(bounds.height * 0.15, bounds.height / 2.0)
                    val rim = diagramPath {
                        moveTo(bounds.left, bounds.top + ry)
                        arcTo(
                            radiusX = bounds.width / 2.0,
                            radiusY = ry,
                            sweep = false,
                            endX = bounds.right,
                            endY = bounds.top + ry,
                        )
                    }
                    strokeDiagramPath(rim, style, ink, seed)
                }
            }
            drawFirstLabel(node, node.labelBox(node.labelPadding()), labelInk, measurer)
        }

        is DiagramNodePayload.FlowchartNode -> {
            drawStyledPath(node.outlinePath(), style, colors, seed)
            drawFirstLabel(node, node.labelBox(node.labelPadding()), labelInk, measurer)
        }

        is DiagramNodePayload.BpmnNode -> {
            drawStyledPath(node.outlinePath(), style, colors, seed)
            if (payload.kind == BpmnNodeKind.GATEWAY) {
                // Exclusive-gateway X marker.
                val r = min(bounds.width, bounds.height) * 0.16
                val cx = bounds.centerX
                val cy = bounds.centerY
                drawLine(ink, Offset((cx - r).toFloat(), (cy - r).toFloat()), Offset((cx + r).toFloat(), (cy + r).toFloat()), strokeWidth = style.strokeWidth.toFloat() * 1.6f)
                drawLine(ink, Offset((cx - r).toFloat(), (cy + r).toFloat()), Offset((cx + r).toFloat(), (cy - r).toFloat()), strokeWidth = style.strokeWidth.toFloat() * 1.6f)
            }
            val labelBox = if (payload.kind == BpmnNodeKind.TASK) bounds.inset(6.0) else bounds
            if (payload.kind == BpmnNodeKind.TASK) {
                drawFirstLabel(node, labelBox, labelInk, measurer)
            } else {
                node.labels.firstOrNull()?.let { label ->
                    drawDiagramLabel(
                        measurer, label,
                        DiagramRect(bounds.left - 40.0, bounds.bottom + 2.0, bounds.width + 80.0, 30.0),
                        labelInk,
                        verticalAlign = LabelVerticalAlign.TOP,
                    )
                }
            }
        }

        is DiagramNodePayload.ContainerNode -> {
            drawStyledPath(node.outlinePath(), style, colors, seed)
            val band = DiagramRect(bounds.left, bounds.top, bounds.width, min(TITLE_BAND_HEIGHT, bounds.height))
            drawRect(
                colors.headerFill.applyOpacity(style.opacity),
                topLeft = Offset(band.left.toFloat(), band.top.toFloat()),
                size = Size(band.width.toFloat(), band.height.toFloat()),
            )
            drawLine(
                ink,
                Offset(band.left.toFloat(), band.bottom.toFloat()),
                Offset(band.right.toFloat(), band.bottom.toFloat()),
                strokeWidth = style.strokeWidth.toFloat(),
            )
            strokeDiagramPath(node.outlinePath(), style, ink, seed)
            payload.title?.let { title ->
                drawDiagramLabel(measurer, title, band.inset(6.0), labelInk, fontWeight = 600, align = AlignHorizontal.Left)
            }
        }

        is DiagramNodePayload.SwimlaneNode -> drawSwimlane(node, payload, colors, measurer, seed)

        is DiagramNodePayload.ErEntityNode -> drawErEntity(node, payload, colors, measurer, seed)

        is TableNode -> drawTable(node, payload, colors, measurer, seed)

        is UmlClassNode -> drawUmlClass(node, payload, colors, measurer, seed)

        is UmlLifelineNode -> drawUmlLifeline(node, payload, colors, measurer, seed)

        is UmlStateNode -> drawUmlState(node, payload, colors, measurer, seed)

        is UmlActivityNode -> drawUmlActivity(node, payload, colors, measurer, seed)

        is UmlActorNode -> drawUmlActor(node, payload, colors, measurer)

        is UmlUseCaseNode -> {
            drawStyledPath(node.outlinePath(), style, colors, seed)
            drawDiagramLabel(measurer, DiagramLabel(payload.name), node.labelBox(node.labelPadding()), labelInk)
        }

        is UmlComponentNode -> drawUmlComponent(node, payload, colors, measurer, seed)

        is UmlDeploymentNode -> drawUmlDeployment(node, payload, colors, measurer, seed)

        is UmlNoteNode -> {
            drawStyledPath(node.outlinePath(), style, colors, seed, fillOverride = style.fill?.toComposeColor() ?: colors.noteFill)
            // Fold triangle.
            val fold = min(NOTE_FOLD, min(bounds.width / 2.0, bounds.height / 2.0))
            val foldPath = diagramPath {
                moveTo(bounds.right - fold, bounds.top)
                lineTo(bounds.right - fold, bounds.top + fold)
                lineTo(bounds.right, bounds.top + fold)
            }
            strokeDiagramPath(foldPath, style, ink, seed)
            drawDiagramLabel(
                measurer,
                DiagramLabel(payload.text),
                bounds.inset(6.0),
                labelInk,
                fontSize = DIAGRAM_DETAIL_FONT_SIZE,
                align = AlignHorizontal.Left,
                verticalAlign = LabelVerticalAlign.TOP,
            )
        }

        is UmlPackageNode -> {
            val tabWidth = min(bounds.width * 0.4, 90.0)
            val tabHeight = min(16.0, bounds.height / 3.0)
            val tab = DiagramRect(bounds.left, bounds.top, tabWidth, tabHeight)
            val body = DiagramRect(bounds.left, bounds.top + tabHeight, bounds.width, bounds.height - tabHeight)
            drawStyledPath(rectPath(tab), style, colors, seed)
            drawStyledPath(rectPath(body), style, colors, seed + 1)
            drawDiagramLabel(measurer, DiagramLabel(payload.name), body.inset(6.0), labelInk, fontWeight = 600)
        }
    }
}

/** First node label centered in [box] (payloads whose name lives in the payload skip this). */
private fun DrawScope.drawFirstLabel(
    node: DiagramNode,
    box: DiagramRect,
    color: Color,
    measurer: ComposeTypographyMeasurer,
) {
    node.labels.firstOrNull()?.let { label -> drawDiagramLabel(measurer, label, box, color) }
}

// --- Composite payload bodies -------------------------------------------------------------

private fun DrawScope.drawSwimlane(
    node: DiagramNode,
    payload: DiagramNodePayload.SwimlaneNode,
    colors: DiagramCanvasColors,
    measurer: ComposeTypographyMeasurer,
    seed: Int,
) {
    val bounds = node.bounds
    val style = node.style
    val ink = style.stroke?.toComposeColor() ?: colors.nodeStroke
    val labelInk = colors.labelInk.applyOpacity(style.opacity)
    drawStyledPath(node.outlinePath(), style, colors, seed)

    var titleOffset = 0.0
    payload.title?.let { title ->
        titleOffset = min(TITLE_BAND_HEIGHT, bounds.height)
        val band = DiagramRect(bounds.left, bounds.top, bounds.width, titleOffset)
        drawRect(
            colors.headerFill.applyOpacity(style.opacity),
            topLeft = Offset(band.left.toFloat(), band.top.toFloat()),
            size = Size(band.width.toFloat(), band.height.toFloat()),
        )
        drawLine(
            ink,
            Offset(band.left.toFloat(), band.bottom.toFloat()),
            Offset(band.right.toFloat(), band.bottom.toFloat()),
            strokeWidth = style.strokeWidth.toFloat(),
        )
        drawDiagramLabel(measurer, title, band.inset(6.0), labelInk, fontWeight = 600)
    }

    // Lanes: HORIZONTAL pool = lanes stacked top->bottom, VERTICAL = left->right
    // (mirrors the core hit-test semantics).
    var offset = 0.0
    payload.lanes.forEachIndexed { index, lane ->
        val laneStart = offset
        offset += lane.size
        if (index > 0) {
            when (payload.orientation) {
                DiagramOrientation.HORIZONTAL -> {
                    val y = bounds.top + titleOffset + laneStart
                    if (y < bounds.bottom) {
                        drawLine(
                            ink,
                            Offset(bounds.left.toFloat(), y.toFloat()),
                            Offset(bounds.right.toFloat(), y.toFloat()),
                            strokeWidth = style.strokeWidth.toFloat(),
                        )
                    }
                }

                DiagramOrientation.VERTICAL -> {
                    val x = bounds.left + laneStart
                    if (x < bounds.right) {
                        drawLine(
                            ink,
                            Offset(x.toFloat(), (bounds.top + titleOffset).toFloat()),
                            Offset(x.toFloat(), bounds.bottom.toFloat()),
                            strokeWidth = style.strokeWidth.toFloat(),
                        )
                    }
                }
            }
        }
        lane.title?.let { title ->
            val box = when (payload.orientation) {
                DiagramOrientation.HORIZONTAL -> DiagramRect(
                    bounds.left + 6.0,
                    bounds.top + titleOffset + laneStart + 4.0,
                    bounds.width - 12.0,
                    18.0,
                )

                DiagramOrientation.VERTICAL -> DiagramRect(
                    bounds.left + laneStart + 4.0,
                    bounds.top + titleOffset + 4.0,
                    lane.size - 8.0,
                    18.0,
                )
            }
            drawDiagramLabel(
                measurer, title, box, labelInk,
                fontSize = DIAGRAM_DETAIL_FONT_SIZE,
                align = AlignHorizontal.Left,
                verticalAlign = LabelVerticalAlign.TOP,
            )
        }
    }
}

private fun DrawScope.drawErEntity(
    node: DiagramNode,
    payload: DiagramNodePayload.ErEntityNode,
    colors: DiagramCanvasColors,
    measurer: ComposeTypographyMeasurer,
    seed: Int,
) {
    val bounds = node.bounds
    val style = node.style
    val ink = style.stroke?.toComposeColor() ?: colors.nodeStroke
    val labelInk = colors.labelInk.applyOpacity(style.opacity)
    drawStyledPath(node.outlinePath(), style, colors, seed)

    val headerHeight = min(TITLE_BAND_HEIGHT, bounds.height)
    drawRect(
        colors.headerFill.applyOpacity(style.opacity),
        topLeft = Offset(bounds.left.toFloat(), bounds.top.toFloat()),
        size = Size(bounds.width.toFloat(), headerHeight.toFloat()),
    )
    drawLine(
        ink,
        Offset(bounds.left.toFloat(), (bounds.top + headerHeight).toFloat()),
        Offset(bounds.right.toFloat(), (bounds.top + headerHeight).toFloat()),
        strokeWidth = style.strokeWidth.toFloat(),
    )
    strokeDiagramPath(node.outlinePath(), style, ink, seed)
    drawDiagramLabel(
        measurer,
        DiagramLabel(payload.name),
        DiagramRect(bounds.left, bounds.top, bounds.width, headerHeight).inset(4.0),
        labelInk,
        fontWeight = 700,
    )

    val rowHeight = 18.0
    payload.attributes.forEachIndexed { index, attribute ->
        val y = bounds.top + headerHeight + index * rowHeight
        if (y + rowHeight > bounds.bottom + 0.5) return@forEachIndexed
        val markers = buildString {
            if (attribute.primaryKey) append("PK ")
            if (attribute.foreignKey) append("FK ")
        }
        val text = markers + attribute.name + (attribute.type?.let { ": $it" } ?: "")
        drawDiagramLabel(
            measurer,
            DiagramLabel(text),
            DiagramRect(bounds.left + 8.0, y, bounds.width - 16.0, rowHeight),
            labelInk,
            fontSize = DIAGRAM_DETAIL_FONT_SIZE,
            align = AlignHorizontal.Left,
            fontWeight = if (attribute.primaryKey) 600 else null,
        )
    }
}

private fun DrawScope.drawTable(
    node: DiagramNode,
    payload: TableNode,
    colors: DiagramCanvasColors,
    measurer: ComposeTypographyMeasurer,
    seed: Int,
) {
    val bounds = node.bounds
    val style = node.style
    val ink = style.stroke?.toComposeColor() ?: colors.nodeStroke
    val labelInk = colors.labelInk.applyOpacity(style.opacity)
    if (payload.rowCount == 0 || payload.columnCount == 0) {
        drawStyledPath(node.outlinePath(), style, colors, seed)
        return
    }

    // Track offsets, proportionally scaled into the node box (matches the core hit-test).
    val rowTotal = payload.rows.sumOf { it.height }.takeIf { it > 0.0 } ?: 1.0
    val columnTotal = payload.columns.sumOf { it.width }.takeIf { it > 0.0 } ?: 1.0
    val rowScale = bounds.height / rowTotal
    val columnScale = bounds.width / columnTotal
    val rowEdges = DoubleArray(payload.rowCount + 1)
    payload.rows.forEachIndexed { index, row -> rowEdges[index + 1] = rowEdges[index] + row.height * rowScale }
    val columnEdges = DoubleArray(payload.columnCount + 1)
    payload.columns.forEachIndexed { index, column -> columnEdges[index + 1] = columnEdges[index] + column.width * columnScale }

    drawStyledPath(node.outlinePath(), style, colors, seed)

    // Header shading.
    payload.rows.forEachIndexed { index, row ->
        if (row.header) {
            drawRect(
                colors.headerFill.applyOpacity(style.opacity),
                topLeft = Offset(bounds.left.toFloat(), (bounds.top + rowEdges[index]).toFloat()),
                size = Size(bounds.width.toFloat(), (rowEdges[index + 1] - rowEdges[index]).toFloat()),
            )
        }
    }
    payload.columns.forEachIndexed { index, column ->
        if (column.header) {
            drawRect(
                colors.headerFill.applyOpacity(style.opacity),
                topLeft = Offset((bounds.left + columnEdges[index]).toFloat(), bounds.top.toFloat()),
                size = Size((columnEdges[index + 1] - columnEdges[index]).toFloat(), bounds.height.toFloat()),
            )
        }
    }

    // Explicit per-cell fills.
    payload.cells.forEach { cell ->
        val fill = cell.style?.fill?.toComposeColor() ?: return@forEach
        val right = columnEdges[(cell.column + cell.colSpan).coerceAtMost(payload.columnCount)]
        val bottom = rowEdges[(cell.row + cell.rowSpan).coerceAtMost(payload.rowCount)]
        drawRect(
            fill.applyOpacity(style.opacity),
            topLeft = Offset((bounds.left + columnEdges[cell.column]).toFloat(), (bounds.top + rowEdges[cell.row]).toFloat()),
            size = Size((right - columnEdges[cell.column]).toFloat(), (bottom - rowEdges[cell.row]).toFloat()),
        )
    }

    // Grid lines, skipping spans of merged cells.
    val strokeWidth = style.strokeWidth.toFloat().coerceAtLeast(0.5f)
    for (rowLine in 1 until payload.rowCount) {
        var column = 0
        while (column < payload.columnCount) {
            val above = payload.cellAt(rowLine - 1, column)
            // A horizontal border segment is hidden when one merged cell covers both sides.
            val hidden = above != null && above.row + above.rowSpan > rowLine &&
                payload.cellAt(rowLine, column) == above
            val segmentEnd = above?.let { (it.column + it.colSpan).coerceAtMost(payload.columnCount) } ?: (column + 1)
            if (!hidden) {
                drawLine(
                    ink,
                    Offset((bounds.left + columnEdges[column]).toFloat(), (bounds.top + rowEdges[rowLine]).toFloat()),
                    Offset((bounds.left + columnEdges[segmentEnd]).toFloat(), (bounds.top + rowEdges[rowLine]).toFloat()),
                    strokeWidth = strokeWidth,
                )
            }
            column = segmentEnd
        }
    }
    for (columnLine in 1 until payload.columnCount) {
        var row = 0
        while (row < payload.rowCount) {
            val leftCell = payload.cellAt(row, columnLine - 1)
            val hidden = leftCell != null && leftCell.column + leftCell.colSpan > columnLine &&
                payload.cellAt(row, columnLine) == leftCell
            val segmentEnd = leftCell?.let { (it.row + it.rowSpan).coerceAtMost(payload.rowCount) } ?: (row + 1)
            if (!hidden) {
                drawLine(
                    ink,
                    Offset((bounds.left + columnEdges[columnLine]).toFloat(), (bounds.top + rowEdges[row]).toFloat()),
                    Offset((bounds.left + columnEdges[columnLine]).toFloat(), (bounds.top + rowEdges[segmentEnd]).toFloat()),
                    strokeWidth = strokeWidth,
                )
            }
            row = segmentEnd
        }
    }

    // Cell labels (merged cells span their full coverage).
    payload.cells.forEach { cell ->
        val label = cell.label ?: return@forEach
        val right = columnEdges[(cell.column + cell.colSpan).coerceAtMost(payload.columnCount)]
        val bottom = rowEdges[(cell.row + cell.rowSpan).coerceAtMost(payload.rowCount)]
        val box = DiagramRect(
            bounds.left + columnEdges[cell.column],
            bounds.top + rowEdges[cell.row],
            right - columnEdges[cell.column],
            bottom - rowEdges[cell.row],
        ).inset(3.0)
        val header = payload.rows.getOrNull(cell.row)?.header == true ||
            payload.columns.getOrNull(cell.column)?.header == true
        drawDiagramLabel(
            measurer, label, box, labelInk,
            fontSize = DIAGRAM_DETAIL_FONT_SIZE,
            fontWeight = if (header) 600 else null,
        )
    }
}

private fun DrawScope.drawUmlClass(
    node: DiagramNode,
    payload: UmlClassNode,
    colors: DiagramCanvasColors,
    measurer: ComposeTypographyMeasurer,
    seed: Int,
) {
    val bounds = node.bounds
    val style = node.style
    val ink = style.stroke?.toComposeColor() ?: colors.nodeStroke
    val labelInk = colors.labelInk.applyOpacity(style.opacity)
    drawStyledPath(node.outlinePath(), style, colors, seed)

    // Equal-height rows: 1 name row + max(attrs, 1) + max(ops, 1) (matches the core hit-test).
    val attributeRows = maxOf(payload.attributes.size, 1)
    val operationRows = maxOf(payload.operations.size, 1)
    val totalRows = 1 + attributeRows + operationRows
    val rowHeight = bounds.height / totalRows

    val nameBottom = bounds.top + rowHeight
    val attributesBottom = nameBottom + attributeRows * rowHeight
    drawLine(
        ink,
        Offset(bounds.left.toFloat(), nameBottom.toFloat()),
        Offset(bounds.right.toFloat(), nameBottom.toFloat()),
        strokeWidth = style.strokeWidth.toFloat(),
    )
    drawLine(
        ink,
        Offset(bounds.left.toFloat(), attributesBottom.toFloat()),
        Offset(bounds.right.toFloat(), attributesBottom.toFloat()),
        strokeWidth = style.strokeWidth.toFloat(),
    )

    val nameBox = DiagramRect(bounds.left, bounds.top, bounds.width, rowHeight).inset(2.0)
    if (payload.stereotype != null) {
        drawDiagramLabel(
            measurer,
            DiagramLabel("«${payload.stereotype}»"),
            DiagramRect(nameBox.x, nameBox.y, nameBox.width, nameBox.height / 2.0),
            labelInk,
            fontSize = 10.0,
        )
        drawDiagramLabel(
            measurer,
            DiagramLabel(payload.name),
            DiagramRect(nameBox.x, nameBox.y + nameBox.height / 2.0, nameBox.width, nameBox.height / 2.0),
            labelInk,
            fontWeight = 700,
            italic = payload.abstract,
        )
    } else {
        drawDiagramLabel(measurer, DiagramLabel(payload.name), nameBox, labelInk, fontWeight = 700, italic = payload.abstract)
    }

    fun drawMembers(members: List<UmlMember>, top: Double) {
        members.forEachIndexed { index, member ->
            val box = DiagramRect(bounds.left + 8.0, top + index * rowHeight, bounds.width - 16.0, rowHeight)
            drawDiagramLabel(
                measurer,
                DiagramLabel("${member.visibility.symbol} ${member.text}"),
                box,
                labelInk,
                fontSize = DIAGRAM_DETAIL_FONT_SIZE,
                align = AlignHorizontal.Left,
                italic = if (member.abstract) true else null,
                fontWeight = if (member.static) 600 else null,
            )
        }
    }
    drawMembers(payload.attributes, nameBottom)
    drawMembers(payload.operations, attributesBottom)
}

private fun DrawScope.drawUmlLifeline(
    node: DiagramNode,
    payload: UmlLifelineNode,
    colors: DiagramCanvasColors,
    measurer: ComposeTypographyMeasurer,
    seed: Int,
) {
    val bounds = node.bounds
    val style = node.style
    val ink = style.stroke?.toComposeColor() ?: colors.nodeStroke
    val labelInk = colors.labelInk.applyOpacity(style.opacity)
    val headHeight = umlLifelineHeadHeight(bounds.height)
    val centerX = bounds.centerX

    if (payload.actor) {
        drawStickFigure(
            DiagramRect(centerX - headHeight * 0.4, bounds.top, headHeight * 0.8, headHeight),
            ink,
            style.strokeWidth.toFloat().coerceAtLeast(1.2f),
        )
        drawDiagramLabel(
            measurer,
            DiagramLabel(payload.name),
            DiagramRect(bounds.left, bounds.top + headHeight, bounds.width, 16.0),
            labelInk,
            fontSize = DIAGRAM_DETAIL_FONT_SIZE,
        )
    } else {
        val head = DiagramRect(bounds.left, bounds.top, bounds.width, headHeight)
        drawStyledPath(rectPath(head), style, colors, seed)
        drawDiagramLabel(measurer, DiagramLabel(payload.name), head.inset(3.0), labelInk)
    }

    // Dashed vertical lifeline.
    val lineTop = payload.lifelineTop(bounds.top, bounds.height)
    drawLine(
        ink,
        Offset(centerX.toFloat(), lineTop.toFloat()),
        Offset(centerX.toFloat(), bounds.bottom.toFloat()),
        strokeWidth = style.strokeWidth.toFloat(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f)),
    )

    // Activation bars, normalized 0..1 along the line below the head.
    val lineLength = (bounds.bottom - lineTop).coerceAtLeast(0.0)
    val barWidth = 10.0
    payload.activations.forEach { activation ->
        val top = lineTop + activation.start * lineLength
        val height = (activation.end - activation.start) * lineLength
        drawRect(
            style.fill?.toComposeColor() ?: colors.nodeFill,
            topLeft = Offset((centerX - barWidth / 2.0).toFloat(), top.toFloat()),
            size = Size(barWidth.toFloat(), height.toFloat()),
        )
        drawRect(
            ink,
            topLeft = Offset((centerX - barWidth / 2.0).toFloat(), top.toFloat()),
            size = Size(barWidth.toFloat(), height.toFloat()),
            style = Stroke(style.strokeWidth.toFloat()),
        )
    }
}

private fun DrawScope.drawUmlState(
    node: DiagramNode,
    payload: UmlStateNode,
    colors: DiagramCanvasColors,
    measurer: ComposeTypographyMeasurer,
    seed: Int,
) {
    val bounds = node.bounds
    val style = node.style
    val ink = style.stroke?.toComposeColor() ?: colors.nodeStroke
    val labelInk = colors.labelInk.applyOpacity(style.opacity)
    when (payload.kind) {
        UmlStateKind.INITIAL -> drawStyledPath(node.outlinePath(), style, colors, seed, fillOverride = ink)

        UmlStateKind.FINAL -> {
            drawStyledPath(node.outlinePath(), style, colors, seed)
            val inset = min(bounds.width, bounds.height) * 0.22
            drawOval(
                ink,
                topLeft = Offset((bounds.left + inset).toFloat(), (bounds.top + inset).toFloat()),
                size = Size((bounds.width - 2 * inset).toFloat(), (bounds.height - 2 * inset).toFloat()),
            )
        }

        UmlStateKind.SIMPLE -> {
            drawStyledPath(node.outlinePath(), style, colors, seed)
            drawDiagramLabel(measurer, DiagramLabel(payload.name), bounds.inset(6.0), labelInk)
        }

        UmlStateKind.COMPOSITE -> {
            drawStyledPath(node.outlinePath(), style, colors, seed)
            val band = min(TITLE_BAND_HEIGHT, bounds.height)
            drawLine(
                ink,
                Offset(bounds.left.toFloat(), (bounds.top + band).toFloat()),
                Offset(bounds.right.toFloat(), (bounds.top + band).toFloat()),
                strokeWidth = style.strokeWidth.toFloat(),
            )
            drawDiagramLabel(
                measurer,
                DiagramLabel(payload.name),
                DiagramRect(bounds.left, bounds.top, bounds.width, band).inset(3.0),
                labelInk,
                fontWeight = 600,
            )
        }
    }
}

private fun DrawScope.drawUmlActivity(
    node: DiagramNode,
    payload: UmlActivityNode,
    colors: DiagramCanvasColors,
    measurer: ComposeTypographyMeasurer,
    seed: Int,
) {
    val bounds = node.bounds
    val style = node.style
    val ink = style.stroke?.toComposeColor() ?: colors.nodeStroke
    val labelInk = colors.labelInk.applyOpacity(style.opacity)
    when (payload.kind) {
        UmlActivityKind.START -> drawStyledPath(node.outlinePath(), style, colors, seed, fillOverride = ink)

        UmlActivityKind.END -> {
            drawStyledPath(node.outlinePath(), style, colors, seed)
            val inset = min(bounds.width, bounds.height) * 0.25
            drawOval(
                ink,
                topLeft = Offset((bounds.left + inset).toFloat(), (bounds.top + inset).toFloat()),
                size = Size((bounds.width - 2 * inset).toFloat(), (bounds.height - 2 * inset).toFloat()),
            )
        }

        UmlActivityKind.FORK, UmlActivityKind.JOIN ->
            drawStyledPath(node.outlinePath(), style, colors, seed, fillOverride = ink)

        UmlActivityKind.ACTION, UmlActivityKind.DECISION -> {
            drawStyledPath(node.outlinePath(), style, colors, seed)
            drawDiagramLabel(measurer, DiagramLabel(payload.name), bounds.inset(6.0), labelInk)
        }
    }
}

private fun DrawScope.drawUmlActor(
    node: DiagramNode,
    payload: UmlActorNode,
    colors: DiagramCanvasColors,
    measurer: ComposeTypographyMeasurer,
) {
    val bounds = node.bounds
    val style = node.style
    val ink = style.stroke?.toComposeColor() ?: colors.nodeStroke
    val labelInk = colors.labelInk.applyOpacity(style.opacity)
    val nameHeight = 16.0
    val figure = DiagramRect(bounds.left, bounds.top, bounds.width, (bounds.height - nameHeight).coerceAtLeast(4.0))
    drawStickFigure(figure, ink, style.strokeWidth.toFloat().coerceAtLeast(1.4f))
    drawDiagramLabel(
        measurer,
        DiagramLabel(payload.name),
        DiagramRect(bounds.left - 20.0, bounds.bottom - nameHeight, bounds.width + 40.0, nameHeight),
        labelInk,
        fontSize = DIAGRAM_DETAIL_FONT_SIZE,
    )
}

private fun DrawScope.drawUmlComponent(
    node: DiagramNode,
    payload: UmlComponentNode,
    colors: DiagramCanvasColors,
    measurer: ComposeTypographyMeasurer,
    seed: Int,
) {
    val bounds = node.bounds
    val style = node.style
    val labelInk = colors.labelInk.applyOpacity(style.opacity)
    val tabWidth = min(14.0, bounds.width * 0.2)
    val body = DiagramRect(bounds.left + tabWidth / 2.0, bounds.top, bounds.width - tabWidth / 2.0, bounds.height)
    drawStyledPath(rectPath(body), style, colors, seed)

    // The two classic provider tabs sticking out on the left.
    val tabHeight = min(9.0, bounds.height * 0.15)
    listOf(0.28, 0.55).forEach { fraction ->
        val tab = DiagramRect(bounds.left, bounds.top + bounds.height * fraction, tabWidth, tabHeight)
        drawStyledPath(rectPath(tab), style, colors, seed)
    }

        drawStereotypedName(measurer, payload.stereotype, payload.name, body.inset(6.0), labelInk)
}

/**
 * `«stereotype»` band on top, name centered in the remaining area below — the two never
 * share a box, so a wrapped name cannot collide with the stereotype (mirrors the
 * class-node name-row split).
 */
private fun DrawScope.drawStereotypedName(
    measurer: ComposeTypographyMeasurer,
    stereotype: String?,
    name: String,
    content: DiagramRect,
    labelInk: Color,
) {
    if (stereotype == null) {
        drawDiagramLabel(measurer, DiagramLabel(name), content, labelInk, fontWeight = 600)
        return
    }
    val band = min(14.0, content.height / 2.0)
    drawDiagramLabel(
        measurer,
        DiagramLabel("«$stereotype»"),
        DiagramRect(content.x, content.y, content.width, band),
        labelInk,
        fontSize = 10.0,
    )
    drawDiagramLabel(
        measurer,
        DiagramLabel(name),
        DiagramRect(content.x, content.y + band, content.width, (content.height - band).coerceAtLeast(0.0)),
        labelInk,
        fontWeight = 600,
    )
}

private fun DrawScope.drawUmlDeployment(
    node: DiagramNode,
    payload: UmlDeploymentNode,
    colors: DiagramCanvasColors,
    measurer: ComposeTypographyMeasurer,
    seed: Int,
) {
    val bounds = node.bounds
    val style = node.style
    val labelInk = colors.labelInk.applyOpacity(style.opacity)
    val depth = min(12.0, min(bounds.width, bounds.height) * 0.18)
    val front = DiagramRect(bounds.left, bounds.top + depth, bounds.width - depth, bounds.height - depth)

    // Top and side faces of the 3D box.
    val topFace = diagramPath {
        moveTo(front.left, front.top)
        lineTo(front.left + depth, bounds.top)
        lineTo(bounds.right, bounds.top)
        lineTo(front.right, front.top)
        close()
    }
    val sideFace = diagramPath {
        moveTo(front.right, front.top)
        lineTo(bounds.right, bounds.top)
        lineTo(bounds.right, bounds.bottom - depth)
        lineTo(front.right, front.bottom)
        close()
    }
    drawStyledPath(topFace, style, colors, seed, fillOverride = (style.fill?.toComposeColor() ?: colors.nodeFill).darken(0.06f))
    drawStyledPath(sideFace, style, colors, seed + 1, fillOverride = (style.fill?.toComposeColor() ?: colors.nodeFill).darken(0.12f))
    drawStyledPath(rectPath(front), style, colors, seed + 2)

    drawStereotypedName(measurer, payload.stereotype, payload.name, front.inset(6.0), labelInk)
}

// --- Shared drawing helpers ---------------------------------------------------------------

/**
 * Fills and strokes a closed body path with the node's [DiagramStyle]: sketch jitter,
 * drop shadow, fill (explicit or theme default), patterned stroke.
 */
internal fun DrawScope.drawStyledPath(
    outline: DiagramPath,
    style: DiagramStyle,
    colors: DiagramCanvasColors,
    seed: Int,
    fillOverride: Color? = null,
) {
    val shaped = if (style.sketch) outline.sketched(seed) else outline
    val path = shaped.toComposePath()
    if (style.shadow) {
        translate(2.5f, 2.5f) { drawPath(path, colors.shadow) }
    }
    val fill = (fillOverride ?: style.fill?.toComposeColor() ?: colors.nodeFill).applyOpacity(style.opacity)
    drawPath(path, fill)
    if (style.strokeWidth > 0.0) {
        val ink = (style.stroke?.toComposeColor() ?: colors.nodeStroke).applyOpacity(style.opacity)
        drawPath(
            path,
            ink,
            style = Stroke(
                width = style.strokeWidth.toFloat(),
                pathEffect = strokePatternEffect(style.pattern, style.strokeWidth.toFloat()),
            ),
        )
    }
}

/** Strokes a decoration path (no fill) honoring sketch mode and the stroke pattern. */
internal fun DrawScope.strokeDiagramPath(
    path: DiagramPath,
    style: DiagramStyle,
    color: Color,
    seed: Int,
) {
    if (style.strokeWidth <= 0.0) return
    val shaped = if (style.sketch) path.sketched(seed) else path
    drawPath(
        shaped.toComposePath(),
        color.applyOpacity(style.opacity),
        style = Stroke(
            width = style.strokeWidth.toFloat(),
            pathEffect = strokePatternEffect(style.pattern, style.strokeWidth.toFloat()),
        ),
    )
}

/** UML actor glyph: head, torso, arms, legs, proportioned into [box]. */
internal fun DrawScope.drawStickFigure(box: DiagramRect, ink: Color, strokeWidth: Float) {
    val cx = box.centerX.toFloat()
    val headRadius = (min(box.width, box.height) * 0.18).toFloat()
    val headCenterY = (box.top + headRadius + 1.0).toFloat()
    val neckY = headCenterY + headRadius
    val hipY = (box.top + box.height * 0.62).toFloat()
    val armY = neckY + (hipY - neckY) * 0.3f
    val armSpan = (box.width * 0.42).toFloat()
    val legSpan = (box.width * 0.32).toFloat()
    val footY = box.bottom.toFloat()

    drawCircle(ink, headRadius, Offset(cx, headCenterY), style = Stroke(strokeWidth))
    drawLine(ink, Offset(cx, neckY), Offset(cx, hipY), strokeWidth)
    drawLine(ink, Offset(cx - armSpan, armY), Offset(cx + armSpan, armY), strokeWidth)
    drawLine(ink, Offset(cx, hipY), Offset(cx - legSpan, footY), strokeWidth)
    drawLine(ink, Offset(cx, hipY), Offset(cx + legSpan, footY), strokeWidth)
}

internal fun rectPath(rect: DiagramRect): DiagramPath = diagramPath {
    moveTo(rect.left, rect.top)
    lineTo(rect.right, rect.top)
    lineTo(rect.right, rect.bottom)
    lineTo(rect.left, rect.bottom)
    close()
}

internal fun DiagramRect.inset(amount: Double): DiagramRect = DiagramRect(
    x + amount,
    y + amount,
    (width - 2 * amount).coerceAtLeast(0.0),
    (height - 2 * amount).coerceAtLeast(0.0),
)

internal fun Color.applyOpacity(opacity: Double): Color =
    if (opacity >= 1.0) this else copy(alpha = (alpha * opacity.toFloat()).coerceIn(0f, 1f))

internal fun Color.darken(amount: Float): Color = copy(
    red = (red - amount).coerceIn(0f, 1f),
    green = (green - amount).coerceIn(0f, 1f),
    blue = (blue - amount).coerceIn(0f, 1f),
)
