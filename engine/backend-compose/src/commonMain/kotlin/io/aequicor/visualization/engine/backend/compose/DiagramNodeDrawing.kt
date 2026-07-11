package io.aequicor.visualization.engine.backend.compose

import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import io.aequicor.visualization.engine.ir.layout.LayoutBox
import io.aequicor.visualization.subsystems.diagrams.compose.drawDiagramGraph
import io.aequicor.visualization.subsystems.diagrams.model.DiagramEdgeId
import io.aequicor.visualization.subsystems.diagrams.model.DiagramGraph
import io.aequicor.visualization.subsystems.diagrams.routing.RoutedEdge
import io.aequicor.visualization.subsystems.diagrams.routing.RoutingOptions
import io.aequicor.visualization.subsystems.diagrams.routing.routeEdge

/**
 * Caches edge routes per [DiagramGraph] so the (potentially obstacle-aware A*) router does
 * not re-run on every draw frame. Documents are immutable, so a given resolved diagram node
 * keeps the same graph instance across frames; any edit produces a new graph and therefore a
 * new cache entry. The cache is bounded: it resets wholesale once [MAX_CACHED_DIAGRAM_GRAPHS]
 * distinct graphs accumulate (cheap, and re-routing a handful of visible diagrams is fast).
 *
 * Mirrors `rememberDiagramRoutes` semantics: an edge whose endpoints reference missing
 * nodes/ports fails to route and is silently skipped — it simply does not render.
 */
internal class DiagramRouteCache {
    private val routesByGraph = HashMap<DiagramGraph, Map<DiagramEdgeId, RoutedEdge>>()

    fun routesFor(graph: DiagramGraph): Map<DiagramEdgeId, RoutedEdge> {
        routesByGraph[graph]?.let { return it }
        if (routesByGraph.size >= MAX_CACHED_DIAGRAM_GRAPHS) routesByGraph.clear()
        val routes = buildMap {
            graph.edges.forEach { edge ->
                runCatching { routeEdge(graph, edge, RoutingOptions.Default) }
                    .getOrNull()
                    ?.let { put(edge.id, it) }
            }
        }
        routesByGraph[graph] = routes
        return routes
    }

    private companion object {
        const val MAX_CACHED_DIAGRAM_GRAPHS = 64
    }
}

/**
 * Draws an embedded [DiagramGraph] of a `diagram` node inside its laid-out box.
 *
 * Graph coordinates are diagram-local: the node's box is the diagram canvas and the graph
 * origin (0,0) maps to the box top-left, 1 document px = 1 graph px (the artboard's zoom
 * transform applies uniformly on top, like every other node). Content is clipped to the
 * node [outline] so a graph larger than an authored fixed-size box never bleeds into
 * siblings; wrapper fills/strokes/effects painted by the generic node path stay untouched.
 */
internal fun DrawScope.drawDiagramNodeContent(
    box: LayoutBox,
    graph: DiagramGraph,
    outline: Path,
    context: DesignDrawContext,
) {
    if (graph.nodes.isEmpty() && graph.edges.isEmpty()) return
    val routes = context.diagramRoutes.routesFor(graph)
    clipPath(outline) {
        translate(box.x.toFloat(), box.y.toFloat()) {
            drawDiagramGraph(
                graph = graph,
                routes = routes,
                colors = context.diagramColors,
                measurer = context.typography,
            )
        }
    }
}
