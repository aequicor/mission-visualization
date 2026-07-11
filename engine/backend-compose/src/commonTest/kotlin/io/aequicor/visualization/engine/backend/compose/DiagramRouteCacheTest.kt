package io.aequicor.visualization.engine.backend.compose

import io.aequicor.visualization.subsystems.diagrams.model.DiagramEdgeId
import io.aequicor.visualization.subsystems.diagrams.model.DiagramEndpoint
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodeId
import io.aequicor.visualization.subsystems.diagrams.model.diagramGraph
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DiagramRouteCacheTest {

    private fun sampleGraph() = diagramGraph {
        val a = node("a", x = 0.0, y = 0.0, width = 100.0, height = 50.0)
        val b = node("b", x = 200.0, y = 150.0, width = 100.0, height = 50.0)
        edge("ab", from = a, to = b)
    }

    @Test
    fun routesEveryEdgeOfTheGraph() {
        val cache = DiagramRouteCache()
        val routes = cache.routesFor(sampleGraph())
        assertEquals(setOf(DiagramEdgeId("ab")), routes.keys)
        assertTrue(routes.getValue(DiagramEdgeId("ab")).points.size >= 2)
    }

    @Test
    fun returnsTheCachedInstanceForTheSameGraph() {
        val cache = DiagramRouteCache()
        val graph = sampleGraph()
        assertSame(cache.routesFor(graph), cache.routesFor(graph))
    }

    @Test
    fun skipsEdgesWithBrokenEndpointsInsteadOfFailing() {
        val graph = diagramGraph {
            val a = node("a", x = 0.0, y = 0.0, width = 100.0, height = 50.0)
            val b = node("b", x = 200.0, y = 0.0, width = 100.0, height = 50.0)
            edge("ok", from = a, to = b)
            edge(
                "broken",
                source = DiagramEndpoint.FloatingAnchor(DiagramNodeId("missing")),
                target = DiagramEndpoint.FloatingAnchor(a),
            )
        }
        val routes = DiagramRouteCache().routesFor(graph)
        assertEquals(setOf(DiagramEdgeId("ok")), routes.keys)
    }
}
