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
    /**
     * Upper bound on the straight run reserved at a node-attached end for its endpoint
     * marker. An end whose marker is longer than [obstacleMargin] (the ER "zero or one"
     * circle, say) gets a stub long enough to hold the marker plus a little breathing
     * room, so the tip reads as a tip instead of merging into the first bend; the node
     * that owns the end is inflated to match, keeping the stub on the boundary the grid
     * A* travels along. Ends whose markers already fit keep [obstacleMargin].
     */
    val endpointClearance: Double = 28.0,
    /**
     * Separation between anchors of edges authored into one fixed port. Such edges would
     * otherwise leave or enter through the same point and read as a single forked line;
     * they fan out this far apart along the port's side instead (see the fan-out in
     * `EdgeRouter`). Kept small enough that a fan of three stays inside a typical ER
     * table row around its authored port.
     */
    val portFanSeparation: Double = 12.0,
    /** Extra path cost per 90° turn in the orthogonal router (higher = straighter routes). */
    val turnPenalty: Double = 4.0,
    /**
     * Minimum distance between anchors of orthogonal floating-to-floating edges attached
     * to the same node side (keeps fan-in/fan-out arrows visually separate).
     */
    val anchorSeparation: Double = 24.0,
) {
    init {
        require(obstacleMargin >= 0.0) { "obstacleMargin must be >= 0, got $obstacleMargin" }
        require(endpointClearance >= 0.0) {
            "endpointClearance must be >= 0, got $endpointClearance"
        }
        require(portFanSeparation >= 0.0) {
            "portFanSeparation must be >= 0, got $portFanSeparation"
        }
        require(turnPenalty >= 0.0) { "turnPenalty must be >= 0, got $turnPenalty" }
        require(anchorSeparation >= 0.0) {
            "anchorSeparation must be >= 0, got $anchorSeparation"
        }
    }

    companion object {
        val Default: RoutingOptions = RoutingOptions()
    }
}

/**
 * Whether this style goes through the orthogonal planning pipeline (anchor planning and
 * spreading, grid A*, nudging). Straight/isometric edges connect points directly and
 * take part in none of it.
 */
internal val DiagramRoutingStyle.routesOrthogonally: Boolean
    get() = this == DiagramRoutingStyle.ORTHOGONAL ||
        this == DiagramRoutingStyle.SIMPLE ||
        this == DiagramRoutingStyle.ENTITY_RELATION ||
        this == DiagramRoutingStyle.CURVED

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
    /**
     * Straight run the router reserved at the source end for its marker, and which
     * [nudgeRoutedEdges] must not shorten. `0.0` for ends with no marker to protect.
     */
    val sourceReach: Double = 0.0,
    /** Straight run reserved at the target end; see [sourceReach]. */
    val targetReach: Double = 0.0,
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
