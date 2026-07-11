package io.aequicor.visualization.subsystems.diagrams.routing

import io.aequicor.visualization.subsystems.diagrams.model.DiagramGraph
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodeSide
import io.aequicor.visualization.subsystems.diagrams.model.diagramGraph
import io.aequicor.visualization.subsystems.diagrams.path.DiagramPoint
import io.aequicor.visualization.subsystems.diagrams.path.DiagramRect
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Regression suite over the Welcome «Module Map» component diagram (the layered
 * apps → shared → engine modules → ir → subsystem cores graph). The original router
 * produced corner anchors, edges hugging foreign node borders, two edges leaving the
 * same point, and hook-shaped entries that read as loops.
 */
class ModuleMapRoutingTest {

    private val nodeBounds = mapOf(
        "app_android" to DiagramRect(10.0, 20.0, 140.0, 48.0),
        "app_desktop" to DiagramRect(170.0, 20.0, 140.0, 48.0),
        "app_web" to DiagramRect(330.0, 20.0, 140.0, 48.0),
        "app_ios" to DiagramRect(490.0, 20.0, 140.0, 48.0),
        "mod_shared" to DiagramRect(220.0, 140.0, 200.0, 56.0),
        "mod_frontend" to DiagramRect(20.0, 270.0, 170.0, 56.0),
        "mod_backend" to DiagramRect(230.0, 270.0, 180.0, 56.0),
        "mod_scene" to DiagramRect(450.0, 270.0, 150.0, 56.0),
        "mod_ir" to DiagramRect(220.0, 410.0, 200.0, 64.0),
        "mod_figures" to DiagramRect(235.0, 560.0, 170.0, 56.0),
        "mod_typography" to DiagramRect(20.0, 560.0, 180.0, 56.0),
        "mod_diagrams" to DiagramRect(440.0, 560.0, 180.0, 56.0),
    )

    private val edgeEnds = listOf(
        Triple("e_app_android", "app_android", "mod_shared"),
        Triple("e_app_desktop", "app_desktop", "mod_shared"),
        Triple("e_app_web", "app_web", "mod_shared"),
        Triple("e_app_ios", "app_ios", "mod_shared"),
        Triple("e_shared_frontend", "mod_shared", "mod_frontend"),
        Triple("e_shared_backend", "mod_shared", "mod_backend"),
        Triple("e_shared_scene", "mod_shared", "mod_scene"),
        Triple("e_frontend_ir", "mod_frontend", "mod_ir"),
        Triple("e_backend_ir", "mod_backend", "mod_ir"),
        Triple("e_scene_ir", "mod_scene", "mod_ir"),
        Triple("e_ir_figures", "mod_ir", "mod_figures"),
        Triple("e_shared_typography", "mod_shared", "mod_typography"),
        Triple("e_shared_diagrams", "mod_shared", "mod_diagrams"),
    )

    private fun moduleMapGraph(): DiagramGraph = diagramGraph {
        val ids = nodeBounds.mapValues { (id, bounds) ->
            node(id, x = bounds.x, y = bounds.y, width = bounds.width, height = bounds.height)
        }
        for ((edgeId, from, to) in edgeEnds) {
            edge(edgeId, from = ids.getValue(from), to = ids.getValue(to))
        }
    }

    private fun routedByEdgeId(): Map<String, RoutedEdge> =
        routeAllEdges(moduleMapGraph()).associateBy { it.edgeId.value }

    @Test
    fun layeredEdgesConnectFacingSidesVertically() {
        val routed = routedByEdgeId()
        routed.values.forEach { edge ->
            assertEquals(DiagramNodeSide.BOTTOM, edge.sourceSide, "source side of ${edge.edgeId.value}")
            assertEquals(DiagramNodeSide.TOP, edge.targetSide, "target side of ${edge.edgeId.value}")
        }
    }

    @Test
    fun overlappingStacksConnectWithSingleStraightSegment() {
        val routed = routedByEdgeId()
        for (edgeId in listOf("e_app_desktop", "e_app_web", "e_shared_backend", "e_backend_ir", "e_ir_figures")) {
            val points = routed.getValue(edgeId).points
            assertEquals(2, points.size, "$edgeId should be a straight vertical, got $points")
            assertTrue(abs(points[0].x - points[1].x) < 1e-6, "$edgeId is not vertical: $points")
        }
    }

    @Test
    fun anchorsStayOffNodeCorners() {
        val routed = routedByEdgeId()
        for ((edgeId, fromId, toId) in edgeEnds) {
            val edge = routed.getValue(edgeId)
            assertAnchorInsideSide(edgeId, edge.sourcePoint, nodeBounds.getValue(fromId))
            assertAnchorInsideSide(edgeId, edge.targetPoint, nodeBounds.getValue(toId))
        }
    }

    private fun assertAnchorInsideSide(edgeId: String, anchor: DiagramPoint, bounds: DiagramRect) {
        val onHorizontalSide =
            abs(anchor.y - bounds.top) < 1e-6 || abs(anchor.y - bounds.bottom) < 1e-6
        val distanceToCorner = if (onHorizontalSide) {
            minOf(anchor.x - bounds.left, bounds.right - anchor.x)
        } else {
            minOf(anchor.y - bounds.top, bounds.bottom - anchor.y)
        }
        assertTrue(
            distanceToCorner >= 12.0 - 1e-6,
            "$edgeId anchor $anchor is ${distanceToCorner}px from a corner of $bounds",
        )
    }

    @Test
    fun anchorsOnOneSideAreSeparated() {
        val routed = routedByEdgeId()
        val anchors = mutableListOf<Triple<String, String, DiagramPoint>>()
        for ((edgeId, fromId, toId) in edgeEnds) {
            val edge = routed.getValue(edgeId)
            anchors += Triple(fromId, "bottom", edge.sourcePoint)
            anchors += Triple(toId, "top", edge.targetPoint)
        }
        anchors.groupBy { it.first to it.second }.forEach { (side, group) ->
            val sorted = group.map { it.third.x }.sorted()
            sorted.zipWithNext().forEach { (a, b) ->
                assertTrue(b - a >= 24.0 - 1e-6, "anchors on $side too close: $sorted")
            }
        }
    }

    @Test
    fun routesKeepClearOfForeignNodes() {
        val graph = moduleMapGraph()
        val routed = routedByEdgeId()
        for ((edgeId, fromId, toId) in edgeEnds) {
            val edge = routed.getValue(edgeId)
            for ((a, b) in edge.points.zipWithNext()) {
                for (node in graph.nodes) {
                    if (node.id.value == fromId || node.id.value == toId) continue
                    val clearance = segmentToRectDistance(a, b, node.bounds)
                    assertTrue(
                        clearance >= 2.0,
                        "$edgeId segment $a -> $b passes ${clearance}px from ${node.id.value}",
                    )
                }
            }
        }
    }

    @Test
    fun noTwoEdgeSegmentsRunOnTopOfEachOther() {
        val routed = routedByEdgeId().values.toList()
        for (i in routed.indices) {
            for (j in i + 1 until routed.size) {
                for ((a1, b1) in routed[i].points.zipWithNext()) {
                    for ((a2, b2) in routed[j].points.zipWithNext()) {
                        assertTrue(
                            !collinearOverlap(a1, b1, a2, b2),
                            "${routed[i].edgeId.value} and ${routed[j].edgeId.value} share " +
                                "a collinear stretch: $a1->$b1 vs $a2->$b2",
                        )
                    }
                }
            }
        }
    }

    private fun collinearOverlap(
        a1: DiagramPoint,
        b1: DiagramPoint,
        a2: DiagramPoint,
        b2: DiagramPoint,
    ): Boolean {
        val h1 = abs(a1.y - b1.y) < 1e-6
        val h2 = abs(a2.y - b2.y) < 1e-6
        if (h1 != h2) return false
        return if (h1) {
            abs(a1.y - a2.y) < 1e-6 &&
                minOf(maxOf(a1.x, b1.x), maxOf(a2.x, b2.x)) -
                maxOf(minOf(a1.x, b1.x), minOf(a2.x, b2.x)) > 1e-6
        } else {
            abs(a1.x - a2.x) < 1e-6 &&
                minOf(maxOf(a1.y, b1.y), maxOf(a2.y, b2.y)) -
                maxOf(minOf(a1.y, b1.y), minOf(a2.y, b2.y)) > 1e-6
        }
    }

    /** Distance between an axis-aligned segment and a rectangle (0 when touching/crossing). */
    private fun segmentToRectDistance(a: DiagramPoint, b: DiagramPoint, rect: DiagramRect): Double {
        val minX = minOf(a.x, b.x)
        val maxX = maxOf(a.x, b.x)
        val minY = minOf(a.y, b.y)
        val maxY = maxOf(a.y, b.y)
        val dx = maxOf(rect.left - maxX, minX - rect.right, 0.0)
        val dy = maxOf(rect.top - maxY, minY - rect.bottom, 0.0)
        return maxOf(dx, dy)
    }
}
