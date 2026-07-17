package io.aequicor.visualization.subsystems.diagrams.layout

import io.aequicor.visualization.subsystems.diagrams.model.DiagramGraph
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNode
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodeId
import io.aequicor.visualization.subsystems.diagrams.path.DiagramPoint
import kotlin.math.round

/**
 * Tidy/Align — topology-preserving cleanup of a manual arrangement, the light-touch
 * alternative to a full [autoLayout]: node positions stay where the author put them
 * (container children included — see `keepScopeOrigin`), but almost-aligned rows and
 * columns snap onto exactly shared axes and colliding nodes separate:
 * - Snapping runs on the **top-left edges** ([snapAxisUnions] within half a
 *   [DiagramLayoutConfig.nodeGap]), so a row of unequal-height nodes the author top-
 *   aligned stays top-aligned (center snapping would misalign their tops).
 * - Overlaps are removed with a quarter-gap clearance ([removeBoxOverlaps]) — tidy
 *   respects tight manual spacing and only pushes true overlaps apart, in place.
 * - Coordinates land on the integer grid.
 *
 * Idempotent: an already-tidy diagram is returned unchanged (a second Tidy is a no-op).
 * Only node positions change; edges are untouched (the router re-routes them).
 * Deterministic: identical input always yields the identical output.
 */
fun tidyAlign(
    graph: DiagramGraph,
    config: DiagramLayoutConfig = DiagramLayoutConfig.Default,
): DiagramGraph =
    layoutHierarchically(graph, config, keepScopeOrigin = true) { _, nodesById, adjacency ->
        tidyPositions(nodesById, adjacency, config)
    }

/** Scope-local tidied positions: top-left corners in local coordinates starting at (0,0). */
internal fun tidyPositions(
    nodesById: Map<DiagramNodeId, DiagramNode>,
    adjacency: ScopeAdjacency,
    config: DiagramLayoutConfig,
): Map<DiagramNodeId, DiagramPoint> {
    val members = adjacency.members
    if (members.isEmpty()) return emptyMap()
    val count = members.size

    // Snap the top-left edges (columns share a left x, rows share a top y), which
    // preserves whatever edge the author aligned regardless of node size.
    val lefts = DoubleArray(count) { nodesById.getValue(members[it]).x }
    val tops = DoubleArray(count) { nodesById.getValue(members[it]).y }
    snapAxisUnions(lefts, tolerance = config.nodeGap / 2.0, maxSpread = config.nodeGap)
    snapAxisUnions(tops, tolerance = config.nodeGap / 2.0, maxSpread = config.nodeGap)

    val halfWidths = DoubleArray(count) { nodesById.getValue(members[it]).width / 2.0 }
    val halfHeights = DoubleArray(count) { nodesById.getValue(members[it]).height / 2.0 }
    val boxes = OverlapBoxes(
        centersX = DoubleArray(count) { lefts[it] + halfWidths[it] },
        centersY = DoubleArray(count) { tops[it] + halfHeights[it] },
        halfWidths = halfWidths,
        halfHeights = halfHeights,
    )
    removeBoxOverlaps(boxes, gap = config.nodeGap / 4.0)

    var minX = Double.MAX_VALUE
    var minY = Double.MAX_VALUE
    for (index in 0 until count) {
        minX = minOf(minX, boxes.centersX[index] - boxes.halfWidths[index])
        minY = minOf(minY, boxes.centersY[index] - boxes.halfHeights[index])
    }
    return members.withIndex().associate { (index, id) ->
        id to DiagramPoint(
            x = round(boxes.centersX[index] - boxes.halfWidths[index] - minX),
            y = round(boxes.centersY[index] - boxes.halfHeights[index] - minY),
        )
    }
}
