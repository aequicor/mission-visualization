package io.aequicor.visualization.subsystems.diagrams.compose

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import io.aequicor.visualization.subsystems.diagrams.hittest.edgeLabelAvoidRects
import io.aequicor.visualization.subsystems.diagrams.hittest.edgeLabelObstacleRoutes
import io.aequicor.visualization.subsystems.diagrams.model.DiagramEdge
import io.aequicor.visualization.subsystems.diagrams.model.DiagramEdgeId
import io.aequicor.visualization.subsystems.diagrams.model.DiagramGraph
import io.aequicor.visualization.subsystems.diagrams.model.DiagramLayerId
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNode
import io.aequicor.visualization.subsystems.diagrams.routing.RoutedEdge
import io.aequicor.visualization.subsystems.diagrams.routing.RoutingOptions
import io.aequicor.visualization.subsystems.diagrams.routing.routeAllEdgesLenient
import io.aequicor.visualization.subsystems.typography.compose.ComposeTypographyMeasurer
import io.aequicor.visualization.subsystems.typography.compose.FontProvider
import io.aequicor.visualization.subsystems.typography.compose.NoFonts

/**
 * Theme defaults for the diagram renderer, passed in by the caller (the subsystem is
 * theme-agnostic — the editor bridges its `EditorColors` tokens into this, mirroring
 * `FigurePreviewStyle` / anchoring's `GuideStyle`). Explicit [io.aequicor.visualization.subsystems.diagrams.model.DiagramStyle]
 * colors on nodes/edges are data and always win over these defaults.
 */
@Immutable
data class DiagramCanvasColors(
    /** Default node body fill (style.fill == null). */
    val nodeFill: Color = Color(0xFFF8FAFC),
    /** Default node stroke / decoration ink (style.stroke == null). */
    val nodeStroke: Color = Color(0xFF44546A),
    /** Default edge stroke (style.stroke == null). */
    val edgeStroke: Color = Color(0xFF44546A),
    /** Label / member-row text color. */
    val labelInk: Color = Color(0xFF1F2933),
    /** Canvas background: hollow arrowhead plates, LINK gaps, edge-label plates. */
    val surface: Color = Color.White,
    /** Table header rows/columns, container and ER title bands. */
    val headerFill: Color = Color(0xFFE8EDF4),
    /** UML note body fill. */
    val noteFill: Color = Color(0xFFFFF7D6),
    /** Drop shadow color for styles with shadow=true. */
    val shadow: Color = Color(0x30000000),
)

/**
 * Routes every edge of the graph once per graph/options change. Edges with endpoints
 * referencing missing nodes/ports are skipped (they simply do not render).
 */
@Composable
fun rememberDiagramRoutes(
    graph: DiagramGraph,
    options: RoutingOptions = RoutingOptions.Default,
): Map<DiagramEdgeId, RoutedEdge> = remember(graph, options) {
    routeAllEdgesLenient(graph, options)
}

/**
 * Full renderer of a [DiagramGraph] in document coordinates (1 document px = 1 canvas px;
 * the caller applies pan/zoom via a graphics layer on [modifier]).
 *
 * Z-order: implicit default layer (layerId == null) at the bottom, then explicit layers
 * bottom→top; within a layer nodes render in list order and edges render above the layer's
 * nodes. Invisible nodes/layers are skipped.
 *
 * [overlay] draws in the same coordinate space above all content — hook for the editor's
 * selection/ports/waypoint overlays that need the computed [RoutedEdge]s.
 */
@Composable
fun DiagramCanvas(
    graph: DiagramGraph,
    modifier: Modifier = Modifier,
    colors: DiagramCanvasColors = DiagramCanvasColors(),
    routingOptions: RoutingOptions = RoutingOptions.Default,
    fontProvider: FontProvider = NoFonts,
    overlay: (DrawScope.(routes: Map<DiagramEdgeId, RoutedEdge>) -> Unit)? = null,
) {
    val routes = rememberDiagramRoutes(graph, routingOptions)
    val measurer = rememberDiagramTypography(fontProvider)
    val flowPhase = rememberFlowPhase(enabled = graph.edges.any { it.flowAnimation })

    Canvas(modifier) {
        drawDiagramGraph(graph, routes, colors, measurer, flowPhase?.value)
        overlay?.invoke(this, routes)
    }
}

/** Shared rich-text measurer/cache for diagram labels. */
@Composable
fun rememberDiagramTypography(fontProvider: FontProvider = NoFonts): ComposeTypographyMeasurer {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(textMeasurer, density, fontProvider) {
        ComposeTypographyMeasurer(textMeasurer, density, fontProvider)
    }
}

/** Animated 0..1 dash-phase fraction for [DiagramEdge.flowAnimation]; `null` when unused. */
@Composable
private fun rememberFlowPhase(enabled: Boolean): State<Float>? {
    if (!enabled) return null
    val transition = rememberInfiniteTransition(label = "diagramFlow")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "diagramFlowPhase",
    )
}

/**
 * Draws the whole graph into an arbitrary [DrawScope] — the seam for embedding diagrams
 * into a larger scene (e.g. the engine's `DesignArtboard`) without a nested composable.
 */
fun DrawScope.drawDiagramGraph(
    graph: DiagramGraph,
    routes: Map<DiagramEdgeId, RoutedEdge>,
    colors: DiagramCanvasColors = DiagramCanvasColors(),
    measurer: ComposeTypographyMeasurer,
    flowPhase: Float? = null,
) {
    val knownLayers = graph.layers.map { it.id }.toSet()

    fun layerKey(id: DiagramLayerId?): DiagramLayerId? = id?.takeIf { it in knownLayers }

    val nodesByLayer = graph.nodes.groupBy { layerKey(it.layerId) }
    val edgesByLayer = graph.edges.groupBy { layerKey(it.layerId) }
    val routePoints = routes.mapValues { it.value.points }

    // Implicit default layer below all explicit layers, then explicit layers bottom->top.
    // Line jumps see every other route; which side of a crossing hops is decided by
    // orientation inside routedEdgeToPath (horizontal over vertical), not by z-order.
    val allRoutes = routes.values.toList()
    val order: List<DiagramLayerId?> = listOf<DiagramLayerId?>(null) + graph.layers.map { it.id }
    order.forEach { layerId ->
        val layer = layerId?.let { graph.layerById(it) }
        if (layer != null && !layer.visible) return@forEach

        nodesByLayer[layerId].orEmpty().forEach { node: DiagramNode ->
            if (node.visible) drawDiagramNode(node, colors, measurer)
        }
        edgesByLayer[layerId].orEmpty().forEach { edge: DiagramEdge ->
            val routed = routes[edge.id] ?: return@forEach
            drawDiagramEdge(
                edge,
                routed,
                colors,
                measurer,
                flowPhase,
                jumpOverRoutes = allRoutes,
                labelObstacleRoutes = if (edge.labels.isEmpty()) {
                    emptyList()
                } else {
                    edgeLabelObstacleRoutes(graph, routePoints, edge.id)
                },
                labelAvoidRects = if (edge.labels.isEmpty()) {
                    emptyList()
                } else {
                    edgeLabelAvoidRects(graph, edge.id, routePoints)
                },
            )
        }
    }
}
