package io.aequicor.visualization.subsystems.diagrams.export

import io.aequicor.visualization.subsystems.diagrams.arrows.ArrowheadGeometry
import io.aequicor.visualization.subsystems.diagrams.arrows.arrowheadPath
import io.aequicor.visualization.subsystems.diagrams.arrows.arrowheadsForRelation
import io.aequicor.visualization.subsystems.diagrams.geometry.outlinePath
import io.aequicor.visualization.subsystems.diagrams.hittest.edgeLabelAnchorPoint
import io.aequicor.visualization.subsystems.diagrams.hittest.edgeLabelObstacleRoutes
import io.aequicor.visualization.subsystems.diagrams.model.DiagramArrowhead
import io.aequicor.visualization.subsystems.diagrams.model.DiagramArrowheadKind
import io.aequicor.visualization.subsystems.diagrams.model.DiagramColor
import io.aequicor.visualization.subsystems.diagrams.model.DiagramEdge
import io.aequicor.visualization.subsystems.diagrams.model.DiagramGraph
import io.aequicor.visualization.subsystems.diagrams.model.DiagramLayerId
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNode
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodePayload
import io.aequicor.visualization.subsystems.diagrams.model.DiagramShapeKind
import io.aequicor.visualization.subsystems.diagrams.model.DiagramStrokePattern
import io.aequicor.visualization.subsystems.diagrams.model.DiagramStyle
import io.aequicor.visualization.subsystems.diagrams.model.ErAttribute
import io.aequicor.visualization.subsystems.diagrams.model.TableNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlActorNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlActivityNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlClassNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlComponentNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlDeploymentNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlLifelineNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlMember
import io.aequicor.visualization.subsystems.diagrams.model.UmlNoteNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlPackageNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlStateNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlUseCaseNode
import io.aequicor.visualization.subsystems.diagrams.path.DiagramPath
import io.aequicor.visualization.subsystems.diagrams.path.DiagramPoint
import io.aequicor.visualization.subsystems.diagrams.path.DiagramRect
import io.aequicor.visualization.subsystems.diagrams.path.diagramPath
import io.aequicor.visualization.subsystems.diagrams.path.toSvgNumber
import io.aequicor.visualization.subsystems.diagrams.path.toSvgPathData
import io.aequicor.visualization.subsystems.diagrams.routing.RoutedEdge
import io.aequicor.visualization.subsystems.diagrams.routing.RoutingOptions
import io.aequicor.visualization.subsystems.diagrams.routing.routeAllEdges
import io.aequicor.visualization.subsystems.diagrams.routing.routedEdgeToPath
import kotlin.math.sqrt

/** Palette and metrics for [diagramToSvg]. */
data class SvgExportOptions(
    val padding: Double = 24.0,
    /** Document background; `null` = transparent (no background rect). */
    val background: DiagramColor? = DiagramColor.White,
    val defaultFill: DiagramColor = DiagramColor.White,
    val defaultStroke: DiagramColor = DiagramColor.fromRgb(0x37, 0x41, 0x51),
    val textColor: DiagramColor = DiagramColor.fromRgb(0x1F, 0x29, 0x37),
    val fontFamily: String = "Helvetica, Arial, sans-serif",
    val fontSize: Double = 12.0,
    val routing: RoutingOptions = RoutingOptions.Default,
) {
    init {
        require(padding >= 0.0) { "padding must be >= 0, got $padding" }
        require(fontSize > 0.0) { "fontSize must be > 0, got $fontSize" }
    }

    companion object {
        val Default: SvgExportOptions = SvgExportOptions()
    }
}

/**
 * Renders [graph] into a complete standalone SVG document: node outlines
 * ([outlinePath] per payload), payload decorations (UML class compartments, ER/table
 * rows, lifelines), routed edges with notation arrowheads, and `<text>` labels.
 *
 * @param routed pre-routed edges to reuse (e.g. after interactive editing); `null`
 *   routes all edges with [routeAllEdges].
 */
fun diagramToSvg(
    graph: DiagramGraph,
    routed: List<RoutedEdge>? = null,
    options: SvgExportOptions = SvgExportOptions.Default,
): String {
    val visibleLayerIds = graph.layers.filter { it.visible }.map { it.id }.toSet()
    fun layerVisible(layerId: DiagramLayerId?): Boolean = layerId == null || layerId in visibleLayerIds

    val nodes = zOrderedNodes(graph).filter { it.visible && layerVisible(it.layerId) }
    val allRouted = routed ?: routeAllEdges(graph, options.routing)
    val routedById = allRouted.associateBy { it.edgeId }
    // Layer-grouped draw order (implicit default layer first, then explicit layers
    // bottom->top) — must match the canvas so line jumps land on the same edge of a
    // crossing pair in both surfaces.
    val edges = zOrderedEdges(graph).filter { layerVisible(it.layerId) && routedById.containsKey(it.id) }

    val bounds = contentBounds(nodes, edges.mapNotNull { routedById[it.id] }, options.padding)
    val svg = StringBuilder()
    svg.append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"")
        .append(fmt(bounds.x)).append(' ').append(fmt(bounds.y)).append(' ')
        .append(fmt(bounds.width)).append(' ').append(fmt(bounds.height))
        .append("\" width=\"").append(fmt(bounds.width))
        .append("\" height=\"").append(fmt(bounds.height))
        .append("\" font-family=\"").append(escapeXml(options.fontFamily))
        .append("\" font-size=\"").append(fmt(options.fontSize))
        .append("\">\n")
    options.background?.let { background ->
        svg.append("<rect x=\"").append(fmt(bounds.x)).append("\" y=\"").append(fmt(bounds.y))
            .append("\" width=\"").append(fmt(bounds.width))
            .append("\" height=\"").append(fmt(bounds.height))
            .append("\" fill=\"").append(background.toSvgHex()).append("\"/>\n")
    }
    for (node in nodes) svg.appendNode(node, options)
    // Edges accumulate in draw order so each edge line-jumps only over lines below it
    // (matches the on-canvas renderer).
    val routePoints = routedById.mapValues { it.value.points }
    val drawnRoutes = mutableListOf<RoutedEdge>()
    for (edge in edges) {
        val route = routedById.getValue(edge.id)
        svg.appendEdge(
            edge,
            route,
            options,
            jumpOverRoutes = drawnRoutes.toList(),
            labelObstacleRoutes = if (edge.labels.isEmpty()) {
                emptyList()
            } else {
                edgeLabelObstacleRoutes(graph, routePoints, edge.id)
            },
        )
        drawnRoutes += route
    }
    svg.append("</svg>")
    return svg.toString()
}

// --- z-order ------------------------------------------------------------------------------

private fun zOrderedNodes(graph: DiagramGraph): List<DiagramNode> {
    val defaultLayerNodes = graph.nodes.filter { it.layerId == null }
    val layeredNodes = graph.layers.flatMap { layer ->
        graph.nodes.filter { it.layerId == layer.id }
    }
    return defaultLayerNodes + layeredNodes
}

private fun zOrderedEdges(graph: DiagramGraph): List<DiagramEdge> {
    val knownLayers = graph.layers.map { it.id }.toSet()
    val defaultLayerEdges = graph.edges.filter { it.layerId == null || it.layerId !in knownLayers }
    val layeredEdges = graph.layers.flatMap { layer ->
        graph.edges.filter { it.layerId == layer.id }
    }
    return defaultLayerEdges + layeredEdges
}

// --- bounds -------------------------------------------------------------------------------

private fun contentBounds(
    nodes: List<DiagramNode>,
    routedEdges: List<RoutedEdge>,
    padding: Double,
): DiagramRect {
    var minX = Double.POSITIVE_INFINITY
    var minY = Double.POSITIVE_INFINITY
    var maxX = Double.NEGATIVE_INFINITY
    var maxY = Double.NEGATIVE_INFINITY
    fun include(x: Double, y: Double) {
        if (x < minX) minX = x
        if (y < minY) minY = y
        if (x > maxX) maxX = x
        if (y > maxY) maxY = y
    }
    for (node in nodes) {
        val b = node.bounds
        include(b.left, b.top)
        include(b.right, b.bottom)
    }
    for (routedEdge in routedEdges) {
        for (point in routedEdge.points) include(point.x, point.y)
    }
    if (minX > maxX || minY > maxY) return DiagramRect(0.0, 0.0, 2 * padding + 1.0, 2 * padding + 1.0)
    return DiagramRect(
        x = minX - padding,
        y = minY - padding,
        width = (maxX - minX) + 2 * padding,
        height = (maxY - minY) + 2 * padding,
    )
}

// --- nodes ---------------------------------------------------------------------------------

private fun StringBuilder.appendNode(node: DiagramNode, options: SvgExportOptions) {
    val payload = node.payload
    val bounds = node.bounds
    val borderless = payload is DiagramNodePayload.BasicShape && payload.shape == DiagramShapeKind.TEXT
    when {
        payload is UmlLifelineNode -> appendLifeline(node, payload, options)
        payload is UmlActorNode -> appendActor(node, payload, options)
        else -> {
            if (!borderless) appendStyledPath(node.outlinePath(), node.style, options, closedShape = true)
            when (payload) {
                is UmlClassNode -> appendClassCompartments(bounds, payload, options)
                is DiagramNodePayload.ErEntityNode ->
                    appendEntityRows(bounds, payload.name, payload.attributes, options)

                is TableNode -> appendTable(bounds, payload, options)
                else -> nodeCenterText(node)?.let { text ->
                    svgText(bounds.centerX, bounds.centerY, text, options)
                }
            }
        }
    }
}

private fun nodeCenterText(node: DiagramNode): String? =
    node.labels.firstOrNull()?.text ?: when (val payload = node.payload) {
        is UmlStateNode -> payload.name.ifEmpty { null }
        is UmlActivityNode -> payload.name.ifEmpty { null }
        is UmlUseCaseNode -> payload.name
        is UmlComponentNode -> payload.name
        is UmlDeploymentNode -> payload.name
        is UmlNoteNode -> payload.text
        is UmlPackageNode -> payload.name
        is DiagramNodePayload.ContainerNode -> payload.title?.text
        is DiagramNodePayload.SwimlaneNode -> payload.title?.text
        else -> null
    }

private fun StringBuilder.appendClassCompartments(
    bounds: DiagramRect,
    payload: UmlClassNode,
    options: SvgExportOptions,
) {
    val headerHeight = 32.0
    val rowHeight = 18.0
    val headerBottom = bounds.top + headerHeight
    if (payload.stereotype != null) {
        svgText(bounds.centerX, bounds.top + 11.0, "«${payload.stereotype}»", options)
        svgText(bounds.centerX, bounds.top + 24.0, payload.name, options, bold = true, italic = payload.abstract)
    } else {
        svgText(bounds.centerX, bounds.top + headerHeight / 2.0, payload.name, options, bold = true, italic = payload.abstract)
    }
    appendLine(bounds.left, headerBottom, bounds.right, headerBottom, options)
    var y = headerBottom
    for (member in payload.attributes) {
        svgText(bounds.left + 8.0, y + rowHeight / 2.0, memberText(member), options, anchor = "start")
        y += rowHeight
    }
    if (payload.attributes.isNotEmpty() && payload.operations.isNotEmpty()) {
        appendLine(bounds.left, y, bounds.right, y, options)
    }
    for (member in payload.operations) {
        svgText(
            bounds.left + 8.0, y + rowHeight / 2.0, memberText(member), options,
            anchor = "start", italic = member.abstract,
        )
        y += rowHeight
    }
}

private fun memberText(member: UmlMember): String = "${member.visibility.symbol} ${member.text}"

private fun StringBuilder.appendEntityRows(
    bounds: DiagramRect,
    name: String,
    attributes: List<ErAttribute>,
    options: SvgExportOptions,
) {
    val headerHeight = 28.0
    val rowHeight = 18.0
    svgText(bounds.centerX, bounds.top + headerHeight / 2.0, name, options, bold = true)
    appendLine(bounds.left, bounds.top + headerHeight, bounds.right, bounds.top + headerHeight, options)
    attributes.forEachIndexed { index, attribute ->
        val prefix = when {
            attribute.primaryKey -> "PK "
            attribute.foreignKey -> "FK "
            else -> ""
        }
        val typeSuffix = attribute.type?.let { ": $it" } ?: ""
        svgText(
            bounds.left + 8.0,
            bounds.top + headerHeight + rowHeight * index + rowHeight / 2.0,
            "$prefix${attribute.name}$typeSuffix",
            options,
            anchor = "start",
        )
    }
}

private fun StringBuilder.appendTable(bounds: DiagramRect, payload: TableNode, options: SvgExportOptions) {
    val totalWidth = payload.columns.sumOf { it.width }.takeIf { it > 0.0 } ?: 1.0
    val totalHeight = payload.rows.sumOf { it.height }.takeIf { it > 0.0 } ?: 1.0
    val scaleX = bounds.width / totalWidth
    val scaleY = bounds.height / totalHeight
    val columnOffsets = buildList {
        var x = 0.0
        add(0.0)
        for (column in payload.columns) {
            x += column.width
            add(x)
        }
    }
    val rowOffsets = buildList {
        var y = 0.0
        add(0.0)
        for (row in payload.rows) {
            y += row.height
            add(y)
        }
    }
    for (index in 1 until payload.columnCount) {
        val x = bounds.left + columnOffsets[index] * scaleX
        appendLine(x, bounds.top, x, bounds.bottom, options)
    }
    for (index in 1 until payload.rowCount) {
        val y = bounds.top + rowOffsets[index] * scaleY
        appendLine(bounds.left, y, bounds.right, y, options)
    }
    for (cell in payload.cells) {
        val label = cell.label ?: continue
        val left = bounds.left + columnOffsets[cell.column] * scaleX
        val right = bounds.left + columnOffsets[(cell.column + cell.colSpan).coerceAtMost(payload.columnCount)] * scaleX
        val top = bounds.top + rowOffsets[cell.row] * scaleY
        val bottom = bounds.top + rowOffsets[(cell.row + cell.rowSpan).coerceAtMost(payload.rowCount)] * scaleY
        svgText((left + right) / 2.0, (top + bottom) / 2.0, label.text, options)
    }
}

private fun StringBuilder.appendLifeline(node: DiagramNode, payload: UmlLifelineNode, options: SvgExportOptions) {
    val bounds = node.bounds
    val headHeight = minOf(40.0, bounds.height)
    val headBottom = bounds.top + headHeight
    val head = diagramPath {
        moveTo(bounds.left, bounds.top)
        lineTo(bounds.right, bounds.top)
        lineTo(bounds.right, headBottom)
        lineTo(bounds.left, headBottom)
        close()
    }
    appendStyledPath(head, node.style, options, closedShape = true)
    // Dashed life line down the center.
    val stroke = node.style.stroke ?: options.defaultStroke
    append("<path d=\"M ").append(fmt(bounds.centerX)).append(' ').append(fmt(headBottom))
        .append(" L ").append(fmt(bounds.centerX)).append(' ').append(fmt(bounds.bottom))
        .append("\" fill=\"none\" stroke=\"").append(stroke.toSvgHex())
        .append("\" stroke-dasharray=\"6 4\"/>\n")
    svgText(bounds.centerX, bounds.top + headHeight / 2.0, payload.name, options)
    val lineSpan = bounds.height - headHeight
    for (activation in payload.activations) {
        val top = headBottom + lineSpan * activation.start
        val height = lineSpan * (activation.end - activation.start)
        append("<rect x=\"").append(fmt(bounds.centerX - 5.0)).append("\" y=\"").append(fmt(top))
            .append("\" width=\"10\" height=\"").append(fmt(height))
            .append("\" fill=\"").append((node.style.fill ?: options.defaultFill).toSvgHex())
            .append("\" stroke=\"").append(stroke.toSvgHex()).append("\"/>\n")
    }
}

private fun StringBuilder.appendActor(node: DiagramNode, payload: UmlActorNode, options: SvgExportOptions) {
    val bounds = node.bounds
    val headRadius = minOf(bounds.width, bounds.height) * 0.15
    val cx = bounds.centerX
    val headCenterY = bounds.top + headRadius
    val neckY = bounds.top + headRadius * 2.0
    val hipY = bounds.top + bounds.height * 0.62
    val armY = bounds.top + bounds.height * 0.34
    val armSpan = bounds.width * 0.4
    val figure = diagramPath {
        moveTo(cx - headRadius, headCenterY)
        arcTo(radiusX = headRadius, radiusY = headRadius, sweep = true, endX = cx + headRadius, endY = headCenterY)
        arcTo(radiusX = headRadius, radiusY = headRadius, sweep = true, endX = cx - headRadius, endY = headCenterY)
        moveTo(cx, neckY)
        lineTo(cx, hipY)
        moveTo(cx - armSpan, armY)
        lineTo(cx + armSpan, armY)
        moveTo(cx, hipY)
        lineTo(cx - armSpan, bounds.bottom - options.fontSize - 6.0)
        moveTo(cx, hipY)
        lineTo(cx + armSpan, bounds.bottom - options.fontSize - 6.0)
    }
    appendStyledPath(figure, node.style, options, closedShape = false)
    svgText(cx, bounds.bottom - options.fontSize / 2.0, payload.name, options)
}

// --- edges ---------------------------------------------------------------------------------

private fun StringBuilder.appendEdge(
    edge: DiagramEdge,
    routedEdge: RoutedEdge,
    options: SvgExportOptions,
    jumpOverRoutes: List<RoutedEdge> = emptyList(),
    labelObstacleRoutes: List<List<DiagramPoint>> = emptyList(),
) {
    val notation = arrowheadsForRelation(edge.relation)
    val sourceHead = edge.sourceArrowhead.takeIf { it.kind != DiagramArrowheadKind.NONE } ?: notation.source
    val targetHead = edge.targetArrowhead.takeIf { it.kind != DiagramArrowheadKind.NONE } ?: notation.target
    val pattern = if (edge.style.pattern != DiagramStrokePattern.SOLID) edge.style.pattern else notation.pattern
    val stroke = edge.style.stroke ?: options.defaultStroke

    val points = routedEdge.points
    val sourceGeometry = arrowheadGeometryAt(sourceHead, points, atSource = true)
    val targetGeometry = arrowheadGeometryAt(targetHead, points, atSource = false)
    val trimmedPoints = if (routedEdge.isCurve) {
        points
    } else {
        shortenPolyline(points, sourceGeometry.lineShorten, targetGeometry.lineShorten)
    }
    val linePath = routedEdgeToPath(
        routed = routedEdge.copy(points = trimmedPoints),
        style = edge.style,
        lineJumps = edge.lineJumps,
        otherEdges = jumpOverRoutes,
    )
    append("<path d=\"").append(linePath.toSvgPathData())
        .append("\" fill=\"none\" stroke=\"").append(stroke.toSvgHex())
        .append("\" stroke-width=\"").append(fmt(edge.style.strokeWidth))
    dashArray(pattern, edge.style.strokeWidth)?.let { append("\" stroke-dasharray=\"").append(it) }
    if (edge.style.opacity < 1.0) append("\" opacity=\"").append(fmt(edge.style.opacity))
    append("\"/>\n")

    appendArrowheadMarker(sourceGeometry, stroke, options)
    appendArrowheadMarker(targetGeometry, stroke, options)

    for (edgeLabel in edge.labels) {
        // Shared anchor logic (self-loop caption, perpendicular lift, crossing slide,
        // manual offsets) — the export must match the on-canvas label placement.
        val anchor = edgeLabelAnchorPoint(points, edgeLabel, labelObstacleRoutes)
        svgText(
            x = anchor.x,
            y = anchor.y,
            text = edgeLabel.label.text,
            options = options,
        )
    }
}

private fun arrowheadGeometryAt(
    arrowhead: DiagramArrowhead,
    points: List<DiagramPoint>,
    atSource: Boolean,
) = if (atSource) {
    arrowheadPath(arrowhead, tip = points.first(), direction = directionInto(points[1], points[0]))
} else {
    arrowheadPath(arrowhead, tip = points.last(), direction = directionInto(points[points.size - 2], points.last()))
}

private fun StringBuilder.appendArrowheadMarker(
    geometry: ArrowheadGeometry,
    stroke: DiagramColor,
    options: SvgExportOptions,
) {
    if (geometry.path.isEmpty) return
    val fill = if (geometry.filled) stroke.toSvgHex() else options.defaultFill.toSvgHex()
    append("<path d=\"").append(geometry.path.toSvgPathData())
        .append("\" fill=\"").append(fill)
        .append("\" stroke=\"").append(stroke.toSvgHex())
        .append("\"/>\n")
}

private fun directionInto(from: DiagramPoint, to: DiagramPoint): DiagramPoint {
    val dx = to.x - from.x
    val dy = to.y - from.y
    val length = sqrt(dx * dx + dy * dy)
    if (length < 1e-9) return DiagramPoint(1.0, 0.0)
    return DiagramPoint(dx / length, dy / length)
}

/** Pulls the polyline ends inward so the line does not poke through filled markers. */
private fun shortenPolyline(
    points: List<DiagramPoint>,
    startShorten: Double,
    endShorten: Double,
): List<DiagramPoint> {
    if (points.size < 2 || (startShorten <= 0.0 && endShorten <= 0.0)) return points
    val result = points.toMutableList()
    if (startShorten > 0.0) {
        val direction = directionInto(result[0], result[1])
        val segmentLength = distance(result[0], result[1])
        val amount = minOf(startShorten, segmentLength * 0.9)
        result[0] = DiagramPoint(result[0].x + direction.x * amount, result[0].y + direction.y * amount)
    }
    if (endShorten > 0.0) {
        val lastIndex = result.size - 1
        val direction = directionInto(result[lastIndex], result[lastIndex - 1])
        val segmentLength = distance(result[lastIndex], result[lastIndex - 1])
        val amount = minOf(endShorten, segmentLength * 0.9)
        result[lastIndex] = DiagramPoint(
            result[lastIndex].x + direction.x * amount,
            result[lastIndex].y + direction.y * amount,
        )
    }
    return result
}

private fun distance(a: DiagramPoint, b: DiagramPoint): Double {
    val dx = b.x - a.x
    val dy = b.y - a.y
    return sqrt(dx * dx + dy * dy)
}


// --- primitives ------------------------------------------------------------------------------

private fun StringBuilder.appendStyledPath(
    path: DiagramPath,
    style: DiagramStyle,
    options: SvgExportOptions,
    closedShape: Boolean,
) {
    val fill = if (closedShape) (style.fill ?: options.defaultFill) else null
    val stroke = style.stroke ?: options.defaultStroke
    append("<path d=\"").append(path.toSvgPathData())
        .append("\" fill=\"").append(fill?.toSvgHex() ?: "none")
    if (fill != null && fill.alpha < 0xFF) {
        append("\" fill-opacity=\"").append(fmt(fill.alpha / 255.0))
    }
    append("\" stroke=\"").append(stroke.toSvgHex())
        .append("\" stroke-width=\"").append(fmt(style.strokeWidth))
    dashArray(style.pattern, style.strokeWidth)?.let { append("\" stroke-dasharray=\"").append(it) }
    if (style.opacity < 1.0) append("\" opacity=\"").append(fmt(style.opacity))
    append("\"/>\n")
}

private fun StringBuilder.appendLine(
    x1: Double,
    y1: Double,
    x2: Double,
    y2: Double,
    options: SvgExportOptions,
) {
    append("<path d=\"M ").append(fmt(x1)).append(' ').append(fmt(y1))
        .append(" L ").append(fmt(x2)).append(' ').append(fmt(y2))
        .append("\" fill=\"none\" stroke=\"").append(options.defaultStroke.toSvgHex())
        .append("\"/>\n")
}

private fun StringBuilder.svgText(
    x: Double,
    y: Double,
    text: String,
    options: SvgExportOptions,
    anchor: String = "middle",
    bold: Boolean = false,
    italic: Boolean = false,
) {
    if (text.isEmpty()) return
    append("<text x=\"").append(fmt(x)).append("\" y=\"").append(fmt(y))
        .append("\" text-anchor=\"").append(anchor)
        .append("\" dominant-baseline=\"central\" fill=\"").append(options.textColor.toSvgHex())
    if (bold) append("\" font-weight=\"bold")
    if (italic) append("\" font-style=\"italic")
    append("\">").append(escapeXml(text)).append("</text>\n")
}

private fun dashArray(pattern: DiagramStrokePattern, strokeWidth: Double): String? {
    val unit = strokeWidth.coerceAtLeast(1.0)
    return when (pattern) {
        DiagramStrokePattern.SOLID -> null
        DiagramStrokePattern.DASHED -> "${fmt(unit * 8.0)} ${fmt(unit * 4.0)}"
        DiagramStrokePattern.DOTTED -> "${fmt(unit * 1.5)} ${fmt(unit * 3.0)}"
    }
}

private fun DiagramColor.toSvgHex(): String {
    fun channel(value: Int): String = value.toString(16).padStart(2, '0')
    return "#${channel(red)}${channel(green)}${channel(blue)}"
}

private fun fmt(value: Double): String = value.toSvgNumber()

internal fun escapeXml(text: String): String = buildString(text.length) {
    for (character in text) {
        when (character) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&apos;")
            else -> append(character)
        }
    }
}
