package io.aequicor.visualization.subsystems.diagrams.templates

import io.aequicor.visualization.subsystems.diagrams.model.DiagramEndpoint
import io.aequicor.visualization.subsystems.diagrams.model.UmlClassNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlLifelineNode
import io.aequicor.visualization.subsystems.diagrams.model.attachedNodeId
import io.aequicor.visualization.subsystems.diagrams.routing.routeAllEdges
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DiagramTemplatesTest {

    @Test
    fun providesAllNineTemplates() {
        val templates = diagramTemplates()
        assertEquals(
            listOf(
                "uml-class", "sequence", "state-machine", "activity", "use-case",
                "component", "deployment", "flowchart", "er",
            ),
            templates.map { it.id },
        )
        assertEquals(templates.size, templates.map { it.id }.distinct().size)
        assertTrue(templates.all { it.name.isNotBlank() })
    }

    @Test
    fun everyTemplateGraphIsValid() {
        for (template in diagramTemplates()) {
            val graph = template.graph
            assertTrue(graph.nodes.isNotEmpty(), "template ${template.id} has no nodes")
            assertTrue(graph.edges.isNotEmpty(), "template ${template.id} has no edges")
            for (edge in graph.edges) {
                for (endpoint in listOf(edge.source, edge.target)) {
                    val nodeId = endpoint.attachedNodeId
                    assertNotNull(nodeId, "template ${template.id}: edge ${edge.id} has a free endpoint")
                    val node = assertNotNull(
                        graph.nodeById(nodeId),
                        "template ${template.id}: edge ${edge.id} references missing node ${nodeId.value}",
                    )
                    if (endpoint is DiagramEndpoint.FixedPort) {
                        assertNotNull(
                            node.portById(endpoint.portId),
                            "template ${template.id}: edge ${edge.id} references missing port",
                        )
                    }
                }
            }
        }
    }

    @Test
    fun everyTemplateIsRoutable() {
        for (template in diagramTemplates()) {
            val routed = routeAllEdges(template.graph)
            assertEquals(template.graph.edges.size, routed.size)
        }
    }

    @Test
    fun templatesAreLaidOutWithoutNodeOverlap() {
        for (template in diagramTemplates()) {
            val nodes = template.graph.nodes
            for (i in nodes.indices) {
                for (j in i + 1 until nodes.size) {
                    assertTrue(
                        !nodes[i].bounds.intersects(nodes[j].bounds),
                        "template ${template.id}: nodes ${nodes[i].id.value} and ${nodes[j].id.value} overlap",
                    )
                }
            }
        }
    }

    @Test
    fun umlClassTemplateHasThreeClassesWithGeneralizations() {
        val graph = diagramTemplates().single { it.id == "uml-class" }.graph
        assertEquals(3, graph.nodes.count { it.payload is UmlClassNode })
        assertEquals(2, graph.edges.size)
    }

    @Test
    fun sequenceTemplateHasTwoLifelinesSideBySide() {
        val graph = diagramTemplates().single { it.id == "sequence" }.graph
        val lifelines = graph.nodes.filter { it.payload is UmlLifelineNode }
        assertEquals(2, lifelines.size)
        assertTrue(lifelines[0].x < lifelines[1].x)
        assertEquals(2, graph.edges.size)
    }

    @Test
    fun templatesAreDeterministic() {
        assertEquals(diagramTemplates(), diagramTemplates())
    }
}
