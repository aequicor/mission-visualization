package io.aequicor.visualization.subsystems.diagrams.routing

import io.aequicor.visualization.subsystems.diagrams.model.DiagramEdgeId
import io.aequicor.visualization.subsystems.diagrams.model.DiagramEndpoint
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodeSide
import io.aequicor.visualization.subsystems.diagrams.model.DiagramPort
import io.aequicor.visualization.subsystems.diagrams.model.DiagramPortAnchor
import io.aequicor.visualization.subsystems.diagrams.model.DiagramPortId
import io.aequicor.visualization.subsystems.diagrams.model.DiagramRoutingStyle
import io.aequicor.visualization.subsystems.diagrams.model.diagramGraph
import io.aequicor.visualization.subsystems.diagrams.path.DiagramPoint
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Covers separation of edges that used to lie on top of each other: fan-out of edges
 * sharing one fixed port, authored via corners surviving far from the port axis, and
 * nudging of router-chosen legs of waypointed edges.
 */
class EdgeOverlapSeparationTest {

    private fun port(id: String, side: DiagramNodeSide, offset: Double): DiagramPort =
        DiagramPort(DiagramPortId(id), DiagramPortAnchor.SideOffset(side, offset))

    private fun assertOnRoute(route: RoutedEdge, point: DiagramPoint) {
        val onRoute = route.points.zipWithNext().any { (a, b) ->
            val cross = (b.x - a.x) * (point.y - a.y) - (b.y - a.y) * (point.x - a.x)
            abs(cross) < 1e-6 &&
                point.x >= minOf(a.x, b.x) - 1e-6 && point.x <= maxOf(a.x, b.x) + 1e-6 &&
                point.y >= minOf(a.y, b.y) - 1e-6 && point.y <= maxOf(a.y, b.y) + 1e-6
        }
        assertTrue(onRoute, "route ${route.points} does not pass through $point")
    }

    // --- same-port fan-out --------------------------------------------------------------

    @Test
    fun edgesSharingAFixedPortFanOutAlongTheSide() {
        val graph = diagramGraph {
            val parent = node(
                "parent",
                x = 0.0,
                y = 0.0,
                width = 200.0,
                height = 120.0,
                ports = listOf(port("id", DiagramNodeSide.RIGHT, 0.5)),
            )
            val top = node("top", x = 400.0, y = -160.0, width = 160.0, height = 80.0)
            val bottom = node("bottom", x = 400.0, y = 200.0, width = 160.0, height = 80.0)
            edge(
                "to_top",
                source = DiagramEndpoint.FixedPort(parent, DiagramPortId("id")),
                target = DiagramEndpoint.FloatingAnchor(top),
                routing = DiagramRoutingStyle.ENTITY_RELATION,
            )
            edge(
                "to_bottom",
                source = DiagramEndpoint.FixedPort(parent, DiagramPortId("id")),
                target = DiagramEndpoint.FloatingAnchor(bottom),
                routing = DiagramRoutingStyle.ENTITY_RELATION,
            )
        }
        val routes = routeAllEdges(graph).associateBy { it.edgeId.value }
        val toTop = routes.getValue("to_top").sourcePoint
        val toBottom = routes.getValue("to_bottom").sourcePoint

        // Both stay on the right side, spread by the fan separation, centered on the port.
        assertTrue(abs(toTop.x - 200.0) < 1e-6, "fan must stay on the port's side, got $toTop")
        assertTrue(abs(toBottom.x - 200.0) < 1e-6, "fan must stay on the port's side, got $toBottom")
        val separation = RoutingOptions.Default.portFanSeparation
        assertTrue(
            abs(toTop.y - toBottom.y) >= separation - 1e-6,
            "same-port anchors must fan out, got $toTop / $toBottom",
        )
        assertEquals(60.0, (toTop.y + toBottom.y) / 2.0, 1e-6)
        // The edge heading up takes the upper slot so the fan does not cross itself.
        assertTrue(toTop.y < toBottom.y, "fan order must follow route headings")
    }

    @Test
    fun aLonePortEdgeKeepsTheExactPortPoint() {
        val graph = diagramGraph {
            val parent = node(
                "parent",
                x = 0.0,
                y = 0.0,
                width = 200.0,
                height = 120.0,
                ports = listOf(port("id", DiagramNodeSide.RIGHT, 0.5)),
            )
            val other = node("other", x = 400.0, y = 0.0, width = 160.0, height = 80.0)
            edge(
                "only",
                source = DiagramEndpoint.FixedPort(parent, DiagramPortId("id")),
                target = DiagramEndpoint.FloatingAnchor(other),
                routing = DiagramRoutingStyle.ENTITY_RELATION,
            )
        }
        val route = routeAllEdges(graph).single()
        assertEquals(DiagramPoint(200.0, 60.0), route.sourcePoint)
    }

    // --- facing-port lane coordination ---------------------------------------------------

    @Test
    fun facingPortFansKeepTheirLanesApart() {
        // Two fans on facing sides at the same height: without coordination all four
        // endpoint whiskers depart on two coincident lanes and co-run in the corridor.
        val graph = diagramGraph {
            val a = node(
                "a",
                x = 0.0,
                y = 0.0,
                width = 200.0,
                height = 120.0,
                ports = listOf(port("p", DiagramNodeSide.RIGHT, 0.5)),
            )
            val b = node(
                "b",
                x = 400.0,
                y = 0.0,
                width = 200.0,
                height = 120.0,
                ports = listOf(port("p", DiagramNodeSide.LEFT, 0.5)),
            )
            val topRight = node("top_right", x = 800.0, y = -260.0, width = 160.0, height = 80.0)
            val bottomRight = node("bottom_right", x = 800.0, y = 300.0, width = 160.0, height = 80.0)
            val topLeft = node("top_left", x = -400.0, y = -260.0, width = 160.0, height = 80.0)
            val bottomLeft = node("bottom_left", x = -400.0, y = 300.0, width = 160.0, height = 80.0)
            edge(
                "a_up",
                source = DiagramEndpoint.FixedPort(a, DiagramPortId("p")),
                target = DiagramEndpoint.FloatingAnchor(topRight),
                routing = DiagramRoutingStyle.ENTITY_RELATION,
            )
            edge(
                "a_down",
                source = DiagramEndpoint.FixedPort(a, DiagramPortId("p")),
                target = DiagramEndpoint.FloatingAnchor(bottomRight),
                routing = DiagramRoutingStyle.ENTITY_RELATION,
            )
            edge(
                "b_up",
                source = DiagramEndpoint.FixedPort(b, DiagramPortId("p")),
                target = DiagramEndpoint.FloatingAnchor(topLeft),
                routing = DiagramRoutingStyle.ENTITY_RELATION,
            )
            edge(
                "b_down",
                source = DiagramEndpoint.FixedPort(b, DiagramPortId("p")),
                target = DiagramEndpoint.FloatingAnchor(bottomLeft),
                routing = DiagramRoutingStyle.ENTITY_RELATION,
            )
        }
        val routes = routeAllEdges(graph).associateBy { it.edgeId.value }
        val aLanes = listOf(routes.getValue("a_up").sourcePoint.y, routes.getValue("a_down").sourcePoint.y)
        val bLanes = listOf(routes.getValue("b_up").sourcePoint.y, routes.getValue("b_down").sourcePoint.y)
        // The smaller-key fan holds its ground, centered on the authored port.
        assertEquals(60.0, aLanes.average(), 1e-6)
        for (aLane in aLanes) {
            for (bLane in bLanes) {
                assertTrue(
                    abs(aLane - bLane) >= 8.0 - 1e-6,
                    "facing fans must not share a lane, got a=$aLanes b=$bLanes",
                )
            }
        }
    }

    @Test
    fun facingLonePortsKeepTheirAuthoredPoints() {
        // Lone ports are exact user intent: even coincident lanes are left alone.
        val graph = diagramGraph {
            val a = node(
                "a",
                x = 0.0,
                y = 0.0,
                width = 200.0,
                height = 120.0,
                ports = listOf(port("p", DiagramNodeSide.RIGHT, 0.5)),
            )
            val b = node(
                "b",
                x = 400.0,
                y = 0.0,
                width = 200.0,
                height = 120.0,
                ports = listOf(port("p", DiagramNodeSide.LEFT, 0.5)),
            )
            val right = node("far_right", x = 800.0, y = -260.0, width = 160.0, height = 80.0)
            val left = node("far_left", x = -400.0, y = 300.0, width = 160.0, height = 80.0)
            edge(
                "from_a",
                source = DiagramEndpoint.FixedPort(a, DiagramPortId("p")),
                target = DiagramEndpoint.FloatingAnchor(right),
                routing = DiagramRoutingStyle.ENTITY_RELATION,
            )
            edge(
                "from_b",
                source = DiagramEndpoint.FixedPort(b, DiagramPortId("p")),
                target = DiagramEndpoint.FloatingAnchor(left),
                routing = DiagramRoutingStyle.ENTITY_RELATION,
            )
        }
        val routes = routeAllEdges(graph).associateBy { it.edgeId.value }
        assertEquals(DiagramPoint(200.0, 60.0), routes.getValue("from_a").sourcePoint)
        assertEquals(DiagramPoint(400.0, 60.0), routes.getValue("from_b").sourcePoint)
    }

    @Test
    fun anEdgeBetweenFacingLonePortsStaysStraight() {
        val graph = diagramGraph {
            val a = node(
                "a",
                x = 0.0,
                y = 0.0,
                width = 200.0,
                height = 120.0,
                ports = listOf(port("p", DiagramNodeSide.RIGHT, 0.5)),
            )
            val b = node(
                "b",
                x = 400.0,
                y = 0.0,
                width = 200.0,
                height = 120.0,
                ports = listOf(port("p", DiagramNodeSide.LEFT, 0.5)),
            )
            edge(
                "link",
                source = DiagramEndpoint.FixedPort(a, DiagramPortId("p")),
                target = DiagramEndpoint.FixedPort(b, DiagramPortId("p")),
                routing = DiagramRoutingStyle.ENTITY_RELATION,
            )
        }
        val route = routeAllEdges(graph).single()
        assertEquals(
            listOf(DiagramPoint(200.0, 60.0), DiagramPoint(400.0, 60.0)),
            route.points,
        )
    }

    @Test
    fun fanShiftKeepsAHealedViaWithinItsAlignmentLimit() {
        // Mirrors the reference-file regression: the straight edge connects the two
        // facing ports (so its lanes never conflict), and the via edge owns the fan's
        // far slot with its authored via row healed onto it. The only clearance-feasible
        // shift (+14) would push that slot beyond the healing limit of its via — the
        // edge's long leg would snap back to the authored row, right onto the facing
        // lane. The shift must be rejected: the fan settles in place at reduced
        // clearance instead.
        val graph = diagramGraph {
            val a = node(
                "a",
                x = 0.0,
                y = 0.0,
                width = 200.0,
                height = 120.0,
                ports = listOf(port("p", DiagramNodeSide.RIGHT, 0.5)),
            )
            val b = node(
                "b",
                x = 400.0,
                y = 0.0,
                width = 200.0,
                height = 120.0,
                // The authored lane sits just above the fan's base (y = 59): the only
                // shift that fully clears it (+13) is exactly the one healing forbids.
                ports = listOf(port("q", DiagramNodeSide.LEFT, 59.0 / 120.0)),
            )
            val mid1 = node("mid1", x = 800.0, y = 30.0, width = 160.0, height = 80.0)
            val mid2 = node("mid2", x = 2000.0, y = 100.0, width = 160.0, height = 80.0)
            val viaTarget = node("via_target", x = 800.0, y = 400.0, width = 160.0, height = 80.0)
            edge(
                "e_link",
                source = DiagramEndpoint.FixedPort(a, DiagramPortId("p")),
                target = DiagramEndpoint.FixedPort(b, DiagramPortId("q")),
                routing = DiagramRoutingStyle.ENTITY_RELATION,
            )
            edge(
                "e_mid1",
                source = DiagramEndpoint.FixedPort(a, DiagramPortId("p")),
                target = DiagramEndpoint.FloatingAnchor(mid1),
                routing = DiagramRoutingStyle.ENTITY_RELATION,
            )
            edge(
                "e_mid2",
                source = DiagramEndpoint.FixedPort(a, DiagramPortId("p")),
                target = DiagramEndpoint.FloatingAnchor(mid2),
                routing = DiagramRoutingStyle.ENTITY_RELATION,
            )
            edge(
                "e_via",
                source = DiagramEndpoint.FixedPort(a, DiagramPortId("p")),
                target = DiagramEndpoint.FloatingAnchor(viaTarget),
                routing = DiagramRoutingStyle.ENTITY_RELATION,
                // Two waypoints: the first is the healable port-leg corner (the healing
                // only runs for >= 2 waypoints; a lone one is an exact pass-through).
                waypoints = listOf(DiagramPoint(330.0, 66.0), DiagramPoint(330.0, 400.0)),
            )
        }
        val routes = routeAllEdges(graph).associateBy { it.edgeId.value }
        // The facing lone port holds its authored point.
        assertEquals(400.0, routes.getValue("e_link").targetPoint.x, 1e-6)
        assertEquals(59.0, routes.getValue("e_link").targetPoint.y, 1e-6)
        // The fan stays in place: slots keep their spread positions around the port.
        assertEquals(42.0, routes.getValue("e_link").sourcePoint.y, 1e-6)
        assertEquals(54.0, routes.getValue("e_mid1").sourcePoint.y, 1e-6)
        assertEquals(66.0, routes.getValue("e_mid2").sourcePoint.y, 1e-6)
        assertEquals(78.0, routes.getValue("e_via").sourcePoint.y, 1e-6)
    }

    @Test
    fun aFanClearsANeighborPortOnItsOwnFace() {
        // A shifted (or unshifted) fan must not land on the lane of another port on the
        // same node face: those whiskers leave through the same face and would co-run.
        val graph = diagramGraph {
            val a = node(
                "a",
                x = 0.0,
                y = 0.0,
                width = 200.0,
                height = 120.0,
                ports = listOf(
                    port("p1", DiagramNodeSide.RIGHT, 0.45),
                    port("p2", DiagramNodeSide.RIGHT, 0.5),
                ),
            )
            val right = node("far_right", x = 800.0, y = -40.0, width = 160.0, height = 80.0)
            val topRight = node("top_right", x = 800.0, y = -260.0, width = 160.0, height = 80.0)
            val bottomRight = node("bottom_right", x = 800.0, y = 300.0, width = 160.0, height = 80.0)
            edge(
                "lone",
                source = DiagramEndpoint.FixedPort(a, DiagramPortId("p1")),
                target = DiagramEndpoint.FloatingAnchor(right),
                routing = DiagramRoutingStyle.ENTITY_RELATION,
            )
            edge(
                "fan_up",
                source = DiagramEndpoint.FixedPort(a, DiagramPortId("p2")),
                target = DiagramEndpoint.FloatingAnchor(topRight),
                routing = DiagramRoutingStyle.ENTITY_RELATION,
            )
            edge(
                "fan_down",
                source = DiagramEndpoint.FixedPort(a, DiagramPortId("p2")),
                target = DiagramEndpoint.FloatingAnchor(bottomRight),
                routing = DiagramRoutingStyle.ENTITY_RELATION,
            )
        }
        val routes = routeAllEdges(graph).associateBy { it.edgeId.value }
        // The lone neighbor port keeps its authored point (y = 0.45 * 120).
        assertEquals(DiagramPoint(200.0, 54.0), routes.getValue("lone").sourcePoint)
        for (id in listOf("fan_up", "fan_down")) {
            assertTrue(
                abs(routes.getValue(id).sourcePoint.y - 54.0) >= 8.0 - 1e-6,
                "fan lanes must clear the same-face neighbor, got ${routes.getValue(id).sourcePoint}",
            )
        }
    }

    @Test
    fun fanSlotsFollowTheTrueApproachBehindRowLevelVias() {
        // Both corridors are authored INTO the port row (the adjacent vias carry no
        // ordering information); the point behind them tells one comes from above and
        // one from below. The northern arrival must take the upper slot — ids are
        // chosen so the alphabetical tie-break would order them the wrong way round.
        val graph = diagramGraph {
            val a = node(
                "a",
                x = 0.0,
                y = 0.0,
                width = 200.0,
                height = 120.0,
                ports = listOf(port("p", DiagramNodeSide.RIGHT, 0.5)),
            )
            val below = node("below", x = 800.0, y = 400.0, width = 160.0, height = 80.0)
            val above = node("above", x = 800.0, y = -360.0, width = 160.0, height = 80.0)
            edge(
                "a_south",
                source = DiagramEndpoint.FixedPort(a, DiagramPortId("p")),
                target = DiagramEndpoint.FloatingAnchor(below),
                routing = DiagramRoutingStyle.ENTITY_RELATION,
                waypoints = listOf(DiagramPoint(330.0, 60.0), DiagramPoint(330.0, 400.0)),
            )
            edge(
                "b_north",
                source = DiagramEndpoint.FixedPort(a, DiagramPortId("p")),
                target = DiagramEndpoint.FloatingAnchor(above),
                routing = DiagramRoutingStyle.ENTITY_RELATION,
                waypoints = listOf(DiagramPoint(360.0, 60.0), DiagramPoint(360.0, -300.0)),
            )
        }
        val routes = routeAllEdges(graph).associateBy { it.edgeId.value }
        assertTrue(
            routes.getValue("b_north").sourcePoint.y < routes.getValue("a_south").sourcePoint.y,
            "the northern corridor must take the upper slot, got " +
                "north=${routes.getValue("b_north").sourcePoint} south=${routes.getValue("a_south").sourcePoint}",
        )
    }

    @Test
    fun aLoneExactViaKeepsItsSlotOrder() {
        // A lone waypoint is an exact pass-through the healing never moves: the route
        // really bends there, so it must stay the ordering reference even inside the
        // healing band — ordering by the far target would put the edge on the wrong
        // side of the fan and its whisker would hairpin across the other lane.
        val graph = diagramGraph {
            val a = node(
                "a",
                x = 0.0,
                y = 0.0,
                width = 200.0,
                height = 120.0,
                ports = listOf(port("p", DiagramNodeSide.RIGHT, 0.5)),
            )
            val below = node("below", x = 800.0, y = 400.0, width = 160.0, height = 80.0)
            val mid = node("mid", x = 800.0, y = 120.0, width = 160.0, height = 80.0)
            edge(
                "e_via",
                source = DiagramEndpoint.FixedPort(a, DiagramPortId("p")),
                target = DiagramEndpoint.FloatingAnchor(below),
                routing = DiagramRoutingStyle.ENTITY_RELATION,
                waypoints = listOf(DiagramPoint(330.0, 40.0)),
            )
            edge(
                "e_plain",
                source = DiagramEndpoint.FixedPort(a, DiagramPortId("p")),
                target = DiagramEndpoint.FloatingAnchor(mid),
                routing = DiagramRoutingStyle.ENTITY_RELATION,
            )
        }
        val routes = routeAllEdges(graph).associateBy { it.edgeId.value }
        assertTrue(
            routes.getValue("e_via").sourcePoint.y < routes.getValue("e_plain").sourcePoint.y,
            "the lone via above the row must keep the upper slot, got " +
                "via=${routes.getValue("e_via").sourcePoint} plain=${routes.getValue("e_plain").sourcePoint}",
        )
    }

    @Test
    fun oppositeViaDriftFallsBackToViaOrder() {
        // The corridors approach from the side OPPOSITE their in-band via drift: the
        // true-approach order would hand each via a slot its healing window cannot
        // reach (one demands shifting up, the other down — no common shift). The fan
        // must fall back to the via-based order, which heals both by construction.
        val graph = diagramGraph {
            val a = node(
                "a",
                x = 0.0,
                y = 0.0,
                width = 200.0,
                height = 120.0,
                ports = listOf(port("p", DiagramNodeSide.RIGHT, 0.5)),
            )
            val above = node("above", x = 800.0, y = -360.0, width = 160.0, height = 80.0)
            val below = node("below", x = 800.0, y = 400.0, width = 160.0, height = 80.0)
            edge(
                "cross_up",
                source = DiagramEndpoint.FixedPort(a, DiagramPortId("p")),
                target = DiagramEndpoint.FloatingAnchor(above),
                routing = DiagramRoutingStyle.ENTITY_RELATION,
                waypoints = listOf(DiagramPoint(330.0, 80.0), DiagramPoint(330.0, -300.0)),
            )
            edge(
                "cross_down",
                source = DiagramEndpoint.FixedPort(a, DiagramPortId("p")),
                target = DiagramEndpoint.FloatingAnchor(below),
                routing = DiagramRoutingStyle.ENTITY_RELATION,
                waypoints = listOf(DiagramPoint(360.0, 40.0), DiagramPoint(360.0, 400.0)),
            )
        }
        val routes = routeAllEdges(graph).associateBy { it.edgeId.value }
        val up = routes.getValue("cross_up").sourcePoint.y
        val down = routes.getValue("cross_down").sourcePoint.y
        assertTrue(down < up, "via order must win when heal windows conflict, got up=$up down=$down")
        assertTrue(
            abs(80.0 - up) <= 24.0 + 1e-6 && abs(40.0 - down) <= 24.0 + 1e-6,
            "both vias must stay healable, got up=$up down=$down",
        )
    }

    @Test
    fun theFanShiftsToKeepABoundarySlotHealable() {
        // The via row (92) sits just outside the healing window of its assigned slot
        // (66): without the rescue shift the healing breaks and the edge's long leg
        // runs down the authored row. The whole fan pulls up by the missing 2 units.
        val graph = diagramGraph {
            val a = node(
                "a",
                x = 0.0,
                y = 0.0,
                width = 200.0,
                height = 120.0,
                ports = listOf(port("p", DiagramNodeSide.RIGHT, 0.5)),
            )
            val up = node("up", x = 800.0, y = -260.0, width = 160.0, height = 80.0)
            val down = node("down", x = 800.0, y = 400.0, width = 160.0, height = 80.0)
            edge(
                "e_plain",
                source = DiagramEndpoint.FixedPort(a, DiagramPortId("p")),
                target = DiagramEndpoint.FloatingAnchor(up),
                routing = DiagramRoutingStyle.ENTITY_RELATION,
            )
            edge(
                "e_via",
                source = DiagramEndpoint.FixedPort(a, DiagramPortId("p")),
                target = DiagramEndpoint.FloatingAnchor(down),
                routing = DiagramRoutingStyle.ENTITY_RELATION,
                waypoints = listOf(DiagramPoint(330.0, 92.0), DiagramPoint(330.0, 400.0)),
            )
        }
        val routes = routeAllEdges(graph).associateBy { it.edgeId.value }
        assertEquals(56.0, routes.getValue("e_plain").sourcePoint.y, 1e-6)
        assertEquals(68.0, routes.getValue("e_via").sourcePoint.y, 1e-6)
    }

    @Test
    fun aDistantViaDoesNotDragTheFanOffItsPort() {
        // The via row (260) is a deliberate corner far from the port; the healing
        // window must not participate, or the whole fan would chase it away from the
        // authored port.
        val graph = diagramGraph {
            val a = node(
                "a",
                x = 0.0,
                y = 0.0,
                width = 200.0,
                height = 320.0,
                ports = listOf(port("p", DiagramNodeSide.RIGHT, 0.1875)),
            )
            val up = node("up", x = 800.0, y = -260.0, width = 160.0, height = 80.0)
            val down = node("down", x = 800.0, y = 500.0, width = 160.0, height = 80.0)
            edge(
                "e_plain",
                source = DiagramEndpoint.FixedPort(a, DiagramPortId("p")),
                target = DiagramEndpoint.FloatingAnchor(up),
                routing = DiagramRoutingStyle.ENTITY_RELATION,
            )
            edge(
                "e_far_via",
                source = DiagramEndpoint.FixedPort(a, DiagramPortId("p")),
                target = DiagramEndpoint.FloatingAnchor(down),
                routing = DiagramRoutingStyle.ENTITY_RELATION,
                waypoints = listOf(DiagramPoint(330.0, 260.0), DiagramPoint(330.0, 500.0)),
            )
        }
        val routes = routeAllEdges(graph).associateBy { it.edgeId.value }
        // Port sits at y = 0.1875 * 320 = 60; the fan stays centered on it.
        assertEquals(54.0, routes.getValue("e_plain").sourcePoint.y, 1e-6)
        assertEquals(66.0, routes.getValue("e_far_via").sourcePoint.y, 1e-6)
    }

    @Test
    fun aFanDodgeAlsoClearsPlannedFloatingLanes() {
        // The facing node holds a lone fixed port AND a floating edge whose planned
        // whisker rides the corridor too. A dodge that only knew about the fixed lane
        // would land the fan right next to the invisible floating lane — both anchored
        // whiskers, un-nudgeable. The dodge must clear both.
        val graph = diagramGraph {
            val a = node(
                "a",
                x = 0.0,
                y = 0.0,
                width = 200.0,
                height = 120.0,
                ports = listOf(port("p", DiagramNodeSide.RIGHT, 0.5)),
            )
            val b = node(
                "b",
                x = 400.0,
                y = 0.0,
                width = 200.0,
                height = 120.0,
                ports = listOf(port("q", DiagramNodeSide.LEFT, 0.55)),
            )
            val topRight = node("top_right", x = 800.0, y = -260.0, width = 160.0, height = 80.0)
            val bottomRight = node("bottom_right", x = 800.0, y = 300.0, width = 160.0, height = 80.0)
            val farLeft1 = node("far_left1", x = -400.0, y = -200.0, width = 160.0, height = 80.0)
            val farLeft2 = node("far_left2", x = -400.0, y = 20.0, width = 160.0, height = 80.0)
            edge(
                "a_up",
                source = DiagramEndpoint.FixedPort(a, DiagramPortId("p")),
                target = DiagramEndpoint.FloatingAnchor(topRight),
                routing = DiagramRoutingStyle.ENTITY_RELATION,
            )
            edge(
                "a_down",
                source = DiagramEndpoint.FixedPort(a, DiagramPortId("p")),
                target = DiagramEndpoint.FloatingAnchor(bottomRight),
                routing = DiagramRoutingStyle.ENTITY_RELATION,
            )
            edge(
                "b_lone",
                source = DiagramEndpoint.FixedPort(b, DiagramPortId("q")),
                target = DiagramEndpoint.FloatingAnchor(farLeft1),
                routing = DiagramRoutingStyle.ENTITY_RELATION,
            )
            edge(
                "b_float",
                source = DiagramEndpoint.FloatingAnchor(b),
                target = DiagramEndpoint.FloatingAnchor(farLeft2),
                routing = DiagramRoutingStyle.ENTITY_RELATION,
            )
        }
        val routes = routeAllEdges(graph).associateBy { it.edgeId.value }
        // Setup self-check: the lone port keeps 66, the planner puts the float at 60.
        assertEquals(DiagramPoint(400.0, 66.0), routes.getValue("b_lone").sourcePoint)
        assertEquals(DiagramPoint(400.0, 60.0), routes.getValue("b_float").sourcePoint)
        for (id in listOf("a_up", "a_down")) {
            val lane = routes.getValue(id).sourcePoint.y
            assertTrue(
                abs(lane - 66.0) >= 8.0 - 1e-6 && abs(lane - 60.0) >= 8.0 - 1e-6,
                "fan lanes must clear both the fixed and the planned floating lane, " +
                    "got ${routes.getValue(id).sourcePoint}",
            )
        }
    }

    @Test
    fun facingVerticalPortFansKeepTheirLanesApart() {
        val graph = diagramGraph {
            val a = node(
                "a",
                x = 0.0,
                y = 0.0,
                width = 200.0,
                height = 100.0,
                ports = listOf(port("p", DiagramNodeSide.BOTTOM, 0.5)),
            )
            val b = node(
                "b",
                x = 0.0,
                y = 300.0,
                width = 200.0,
                height = 100.0,
                ports = listOf(port("q", DiagramNodeSide.TOP, 0.5)),
            )
            val belowLeft = node("below_left", x = -400.0, y = 700.0, width = 160.0, height = 80.0)
            val belowRight = node("below_right", x = 600.0, y = 700.0, width = 160.0, height = 80.0)
            val aboveLeft = node("above_left", x = -400.0, y = -300.0, width = 160.0, height = 80.0)
            val aboveRight = node("above_right", x = 600.0, y = -300.0, width = 160.0, height = 80.0)
            edge(
                "a_left",
                source = DiagramEndpoint.FixedPort(a, DiagramPortId("p")),
                target = DiagramEndpoint.FloatingAnchor(belowLeft),
                routing = DiagramRoutingStyle.ENTITY_RELATION,
            )
            edge(
                "a_right",
                source = DiagramEndpoint.FixedPort(a, DiagramPortId("p")),
                target = DiagramEndpoint.FloatingAnchor(belowRight),
                routing = DiagramRoutingStyle.ENTITY_RELATION,
            )
            edge(
                "b_left",
                source = DiagramEndpoint.FixedPort(b, DiagramPortId("q")),
                target = DiagramEndpoint.FloatingAnchor(aboveLeft),
                routing = DiagramRoutingStyle.ENTITY_RELATION,
            )
            edge(
                "b_right",
                source = DiagramEndpoint.FixedPort(b, DiagramPortId("q")),
                target = DiagramEndpoint.FloatingAnchor(aboveRight),
                routing = DiagramRoutingStyle.ENTITY_RELATION,
            )
        }
        val routes = routeAllEdges(graph).associateBy { it.edgeId.value }
        val aLanes = listOf(routes.getValue("a_left").sourcePoint.x, routes.getValue("a_right").sourcePoint.x)
        val bLanes = listOf(routes.getValue("b_left").sourcePoint.x, routes.getValue("b_right").sourcePoint.x)
        assertEquals(100.0, aLanes.average(), 1e-6)
        for (aLane in aLanes) {
            for (bLane in bLanes) {
                assertTrue(
                    abs(aLane - bLane) >= 8.0 - 1e-6,
                    "vertical facing fans must not share a lane, got a=$aLanes b=$bLanes",
                )
            }
        }
    }

    @Test
    fun aPreviewEdgeAbsentFromTheGraphKeepsTheExactPortPoint() {
        val graph = diagramGraph {
            val a = node(
                "a",
                x = 0.0,
                y = 0.0,
                width = 200.0,
                height = 120.0,
                ports = listOf(port("p", DiagramNodeSide.RIGHT, 0.5)),
            )
            val top = node("top", x = 400.0, y = -160.0, width = 160.0, height = 80.0)
            val bottom = node("bottom", x = 400.0, y = 200.0, width = 160.0, height = 80.0)
            edge(
                "to_top",
                source = DiagramEndpoint.FixedPort(a, DiagramPortId("p")),
                target = DiagramEndpoint.FloatingAnchor(top),
                routing = DiagramRoutingStyle.ENTITY_RELATION,
            )
            edge(
                "to_bottom",
                source = DiagramEndpoint.FixedPort(a, DiagramPortId("p")),
                target = DiagramEndpoint.FloatingAnchor(bottom),
                routing = DiagramRoutingStyle.ENTITY_RELATION,
            )
        }
        val preview = io.aequicor.visualization.subsystems.diagrams.model.DiagramEdge(
            id = DiagramEdgeId("preview"),
            source = DiagramEndpoint.FixedPort(
                io.aequicor.visualization.subsystems.diagrams.model.DiagramNodeId("a"),
                DiagramPortId("p"),
            ),
            target = DiagramEndpoint.FreePoint(600.0, 300.0),
            routing = DiagramRoutingStyle.ENTITY_RELATION,
        )
        val route = routeEdge(graph, preview)
        assertEquals(DiagramPoint(200.0, 60.0), route.sourcePoint)
    }

    @Test
    fun aFanIgnoresTheFacingLaneOfItsOwnEdge() {
        // Edge "link" runs a.p -> b.p; its own lane at the facing port is not a conflict,
        // but the unrelated edge of the fan must still clear the facing lone port's lane.
        val graph = diagramGraph {
            val a = node(
                "a",
                x = 0.0,
                y = 0.0,
                width = 200.0,
                height = 120.0,
                ports = listOf(port("p", DiagramNodeSide.RIGHT, 0.5)),
            )
            val b = node(
                "b",
                x = 400.0,
                y = 0.0,
                width = 200.0,
                height = 120.0,
                ports = listOf(port("p", DiagramNodeSide.LEFT, 0.5)),
            )
            val topRight = node("top_right", x = 800.0, y = -260.0, width = 160.0, height = 80.0)
            edge(
                "link",
                source = DiagramEndpoint.FixedPort(a, DiagramPortId("p")),
                target = DiagramEndpoint.FixedPort(b, DiagramPortId("p")),
                routing = DiagramRoutingStyle.ENTITY_RELATION,
            )
            edge(
                "unrelated",
                source = DiagramEndpoint.FixedPort(a, DiagramPortId("p")),
                target = DiagramEndpoint.FloatingAnchor(topRight),
                routing = DiagramRoutingStyle.ENTITY_RELATION,
            )
        }
        val routes = routeAllEdges(graph).associateBy { it.edgeId.value }
        // The authored ports share row 60; the fan moves link's source onto a lane, and
        // the facing lone port follows it (facing-pair snap), keeping the link straight.
        val link = routes.getValue("link")
        assertEquals(link.sourcePoint.y, link.targetPoint.y, "link must stay straight: ${link.points}")
        // The fan's unrelated lane stays clear of the facing link lane.
        assertTrue(
            abs(routes.getValue("unrelated").sourcePoint.y - link.targetPoint.y) >= 8.0 - 1e-6,
            "unrelated lane must clear the facing lane, got ${routes.getValue("unrelated").sourcePoint}",
        )
    }

    // --- authored vias far from the port axis -------------------------------------------

    @Test
    fun farViaKeepsItsAuthoredCorner() {
        val graph = diagramGraph {
            val a = node(
                "a",
                x = 0.0,
                y = 0.0,
                width = 100.0,
                height = 60.0,
                ports = listOf(port("out", DiagramNodeSide.RIGHT, 0.5)),
            )
            val b = node(
                "b",
                x = 400.0,
                y = 300.0,
                width = 100.0,
                height = 60.0,
                ports = listOf(port("in", DiagramNodeSide.LEFT, 0.5)),
            )
            edge(
                "e",
                source = DiagramEndpoint.FixedPort(a, DiagramPortId("out")),
                target = DiagramEndpoint.FixedPort(b, DiagramPortId("in")),
                waypoints = listOf(DiagramPoint(150.0, 200.0), DiagramPoint(300.0, 200.0)),
            )
        }
        val route = routeAllEdges(graph).single()
        // Both vias sit far from their port axes (y=30 / y=330) — they are deliberate
        // corners and must stay exact mandatory pass-through points.
        assertOnRoute(route, DiagramPoint(150.0, 200.0))
        assertOnRoute(route, DiagramPoint(300.0, 200.0))
    }

    @Test
    fun staleViaNearThePortAxisIsStillHealed() {
        val graph = diagramGraph {
            val a = node(
                "a",
                x = 0.0,
                y = 0.0,
                width = 100.0,
                height = 60.0,
                ports = listOf(port("out", DiagramNodeSide.RIGHT, 0.5)),
            )
            val b = node(
                "b",
                x = 400.0,
                y = 300.0,
                width = 100.0,
                height = 60.0,
                ports = listOf(port("in", DiagramNodeSide.LEFT, 0.5)),
            )
            edge(
                "e",
                source = DiagramEndpoint.FixedPort(a, DiagramPortId("out")),
                target = DiagramEndpoint.FixedPort(b, DiagramPortId("in")),
                // First via drifted 8 units off the port row after a resize: still healed.
                waypoints = listOf(DiagramPoint(150.0, 38.0), DiagramPoint(150.0, 330.0)),
            )
        }
        val route = routeAllEdges(graph).single()
        assertOnRoute(route, DiagramPoint(150.0, 30.0))
        assertTrue(
            route.points.none { abs(it.y - 38.0) < 1e-6 },
            "stale via row must be healed onto the port row, got ${route.points}",
        )
    }

    // --- nudging of waypointed edges ------------------------------------------------------

    private fun routed(id: String, vararg points: DiagramPoint): RoutedEdge =
        RoutedEdge(DiagramEdgeId(id), DiagramRoutingStyle.ORTHOGONAL, points.toList())

    @Test
    fun routerChosenLegsOfWaypointedEdgesStillSpreadApart() {
        // Two waypointed edges co-run on an interior vertical the router (not the author)
        // picked; their vias sit on other segments, far from the corridor.
        val first = routed(
            "first",
            DiagramPoint(0.0, 0.0),
            DiagramPoint(100.0, 0.0),
            DiagramPoint(100.0, 200.0),
            DiagramPoint(200.0, 200.0),
        )
        val second = routed(
            "second",
            DiagramPoint(0.0, 50.0),
            DiagramPoint(100.0, 50.0),
            DiagramPoint(100.0, 250.0),
            DiagramPoint(200.0, 250.0),
        )
        val nudged = nudgeRoutedEdges(
            routes = listOf(first, second),
            waypointsByEdge = mapOf(
                DiagramEdgeId("first") to listOf(DiagramPoint(170.0, 200.0)),
                DiagramEdgeId("second") to listOf(DiagramPoint(170.0, 250.0)),
            ),
        ).associateBy { it.edgeId.value }
        val firstVertical = nudged.getValue("first").points[1].x
        val secondVertical = nudged.getValue("second").points[1].x
        assertTrue(
            abs(firstVertical - secondVertical) > 1.0,
            "co-running verticals of waypointed edges must spread, got $firstVertical / $secondVertical",
        )
        // The via-carrying last segments still pass through their vias.
        assertOnRoute(nudged.getValue("first"), DiagramPoint(170.0, 200.0))
        assertOnRoute(nudged.getValue("second"), DiagramPoint(170.0, 250.0))
    }

    @Test
    fun viaCarryingSegmentsNeverMove() {
        val pinned = routed(
            "pinned",
            DiagramPoint(0.0, 0.0),
            DiagramPoint(100.0, 0.0),
            DiagramPoint(100.0, 200.0),
            DiagramPoint(200.0, 200.0),
        )
        val free = routed(
            "free",
            DiagramPoint(0.0, 50.0),
            DiagramPoint(100.0, 50.0),
            DiagramPoint(100.0, 250.0),
            DiagramPoint(200.0, 250.0),
        )
        val nudged = nudgeRoutedEdges(
            routes = listOf(pinned, free),
            // The via sits right on the pinned edge's interior vertical.
            waypointsByEdge = mapOf(DiagramEdgeId("pinned") to listOf(DiagramPoint(100.0, 100.0))),
        ).associateBy { it.edgeId.value }
        assertEquals(pinned.points, nudged.getValue("pinned").points)
        assertTrue(
            abs(nudged.getValue("free").points[1].x - 100.0) > 1.0,
            "the movable co-runner must give way instead",
        )
    }

    @Test
    fun coincidentViaCorridorsOfDifferentEdgesSpreadApart() {
        // Two edges AUTHORED onto the same corridor (the reference ER file stacks two
        // relations on one 1000-unit column): with every co-running segment via-pinned
        // the pair used to render as one line. They must spread around the authored lane.
        val first = routed(
            "first",
            DiagramPoint(0.0, 0.0),
            DiagramPoint(100.0, 0.0),
            DiagramPoint(100.0, 400.0),
            DiagramPoint(200.0, 400.0),
        )
        val second = routed(
            "second",
            DiagramPoint(0.0, 60.0),
            DiagramPoint(100.0, 60.0),
            DiagramPoint(100.0, 460.0),
            DiagramPoint(200.0, 460.0),
        )
        val nudged = nudgeRoutedEdges(
            routes = listOf(first, second),
            waypointsByEdge = mapOf(
                DiagramEdgeId("first") to listOf(DiagramPoint(100.0, 200.0)),
                DiagramEdgeId("second") to listOf(DiagramPoint(100.0, 240.0)),
            ),
        ).associateBy { it.edgeId.value }
        val firstX = nudged.getValue("first").points[1].x
        val secondX = nudged.getValue("second").points[1].x
        assertTrue(
            abs(firstX - secondX) > 4.0,
            "coincident via corridors must separate: first=$firstX second=$secondX",
        )
        // The pair spreads AROUND the authored lane rather than both fleeing one side.
        assertTrue(
            (firstX - 100.0) * (secondX - 100.0) < 0.0,
            "spread must straddle the authored corridor: first=$firstX second=$secondX",
        )
    }
}
