package io.aequicor.visualization.subsystems.diagrams.routing

import io.aequicor.visualization.subsystems.diagrams.model.DiagramEdgeId
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodeSide
import io.aequicor.visualization.subsystems.diagrams.model.DiagramRoutingStyle
import io.aequicor.visualization.subsystems.diagrams.path.DiagramPoint

/** Tuning knobs for [routeEdge]. */
data class RoutingOptions(
    /**
     * Clearance kept around node bounding boxes by the obstacle-aware router; also the
     * length of the perpendicular exit stub at node-attached ends of orthogonal routes.
     */
    val obstacleMargin: Double = 12.0,
    /** Extra path cost per 90° turn in the orthogonal router (higher = straighter routes). */
    val turnPenalty: Double = 4.0,
    /** Length of the horizontal side stubs of [DiagramRoutingStyle.ENTITY_RELATION] routes. */
    val entityRelationStub: Double = 20.0,
) {
    init {
        require(obstacleMargin >= 0.0) { "obstacleMargin must be >= 0, got $obstacleMargin" }
        require(turnPenalty >= 0.0) { "turnPenalty must be >= 0, got $turnPenalty" }
        require(entityRelationStub >= 0.0) {
            "entityRelationStub must be >= 0, got $entityRelationStub"
        }
    }

    companion object {
        val Default: RoutingOptions = RoutingOptions()
    }
}

/**
 * The result of routing one edge: an ordered list of route [points] from the source
 * anchor to the target anchor, plus attachment metadata.
 *
 * For polyline styles (everything except [DiagramRoutingStyle.CURVED]) consecutive
 * points are straight segments. For CURVED the points are interpolation points of a
 * smooth spline; [routedEdgeToPath] builds the actual curve.
 */
data class RoutedEdge(
    val edgeId: DiagramEdgeId,
    val routing: DiagramRoutingStyle,
    val points: List<DiagramPoint>,
    /** Bounding-box side the route leaves the source node through (`null` for free ends). */
    val sourceSide: DiagramNodeSide? = null,
    /** Bounding-box side the route enters the target node through (`null` for free ends). */
    val targetSide: DiagramNodeSide? = null,
) {
    init {
        require(points.size >= 2) {
            "routed edge ${edgeId.value} needs at least 2 points, got ${points.size}"
        }
    }

    /** Where the route touches the source (perimeter point, port, or free point). */
    val sourcePoint: DiagramPoint get() = points.first()

    /** Where the route touches the target. */
    val targetPoint: DiagramPoint get() = points.last()

    /** Whether [points] are spline interpolation points rather than polyline vertices. */
    val isCurve: Boolean get() = routing == DiagramRoutingStyle.CURVED
}
