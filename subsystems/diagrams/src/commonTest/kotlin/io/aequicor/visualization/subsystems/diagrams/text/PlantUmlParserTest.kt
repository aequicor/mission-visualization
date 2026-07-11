package io.aequicor.visualization.subsystems.diagrams.text

import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodeId
import io.aequicor.visualization.subsystems.diagrams.model.DiagramRelation
import io.aequicor.visualization.subsystems.diagrams.model.UmlClassNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlLifelineNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlMessageKind
import io.aequicor.visualization.subsystems.diagrams.model.UmlVisibility
import io.aequicor.visualization.subsystems.diagrams.model.attachedNodeId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PlantUmlParserTest {

    private fun success(source: String): TextDiagramResult.Success {
        val result = plantUmlToDiagram(source)
        assertIs<TextDiagramResult.Success>(result, "expected Success, got $result")
        return result
    }

    @Test
    fun classDiagramParsesDeclarationsMembersAndRelations() {
        val result = success(
            """
            @startuml
            ' a comment line
            abstract class Vehicle {
              #speed: Int
              +drive()
              +{static} count()
            }
            class Car
            interface Movable
            Vehicle <|-- Car
            Vehicle ..|> Movable
            Car --> Engine : has
            @enduml
            """.trimIndent(),
        )
        val graph = result.graph
        assertEquals(4, graph.nodes.size)
        assertEquals(3, graph.edges.size)

        val vehicle = assertIs<UmlClassNode>(assertNotNull(graph.nodeById(DiagramNodeId("Vehicle"))).payload)
        assertTrue(vehicle.abstract)
        assertEquals(1, vehicle.attributes.size)
        assertEquals(UmlVisibility.PROTECTED, vehicle.attributes[0].visibility)
        assertEquals(2, vehicle.operations.size)
        assertTrue(vehicle.operations.single { it.text == "count()" }.static)

        val movable = assertIs<UmlClassNode>(assertNotNull(graph.nodeById(DiagramNodeId("Movable"))).payload)
        assertEquals("interface", movable.stereotype)

        val generalization = graph.edges.single { it.relation == DiagramRelation.Generalization }
        assertEquals(DiagramNodeId("Car"), generalization.source.attachedNodeId)
        assertEquals(DiagramNodeId("Vehicle"), generalization.target.attachedNodeId)

        val realization = graph.edges.single { it.relation == DiagramRelation.Realization }
        assertEquals(DiagramNodeId("Movable"), realization.target.attachedNodeId)

        val association = graph.edges.single { it.relation == DiagramRelation.Association(directed = true) }
        assertEquals("has", association.labels.single().label.text)
    }

    @Test
    fun sequenceDiagramParsesParticipantsAndMessages() {
        val result = success(
            """
            @startuml
            participant "Web App" as app
            actor User
            User -> app : open page
            app --> User : rendered
            User ->> app : async ping
            @enduml
            """.trimIndent(),
        )
        val graph = result.graph
        assertEquals(2, graph.nodes.size)

        val app = assertIs<UmlLifelineNode>(assertNotNull(graph.nodeById(DiagramNodeId("app"))).payload)
        assertEquals("Web App", app.name)
        val user = assertIs<UmlLifelineNode>(assertNotNull(graph.nodeById(DiagramNodeId("User"))).payload)
        assertTrue(user.actor)

        assertEquals(3, graph.edges.size)
        assertEquals(DiagramRelation.Message(UmlMessageKind.SYNC), graph.edges[0].relation)
        assertEquals(DiagramRelation.Message(UmlMessageKind.RETURN), graph.edges[1].relation)
        assertEquals(DiagramRelation.Message(UmlMessageKind.ASYNC), graph.edges[2].relation)
        assertEquals("open page", graph.edges[0].labels.single().label.text)
    }

    @Test
    fun undetectableDiagramFails() {
        val result = plantUmlToDiagram("@startuml\nsomething unrelated\n@enduml")
        assertIs<TextDiagramResult.Failure>(result)
    }
}
