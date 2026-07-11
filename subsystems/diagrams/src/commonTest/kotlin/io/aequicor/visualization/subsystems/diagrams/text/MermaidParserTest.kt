package io.aequicor.visualization.subsystems.diagrams.text

import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodeId
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodePayload
import io.aequicor.visualization.subsystems.diagrams.model.DiagramRelation
import io.aequicor.visualization.subsystems.diagrams.model.DiagramShapeKind
import io.aequicor.visualization.subsystems.diagrams.model.DiagramStrokePattern
import io.aequicor.visualization.subsystems.diagrams.model.FlowchartNodeKind
import io.aequicor.visualization.subsystems.diagrams.model.UmlClassNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlLifelineNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlMessageKind
import io.aequicor.visualization.subsystems.diagrams.model.UmlStateKind
import io.aequicor.visualization.subsystems.diagrams.model.UmlStateNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlVisibility
import io.aequicor.visualization.subsystems.diagrams.model.attachedNodeId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MermaidParserTest {

    private fun success(source: String): TextDiagramResult.Success {
        val result = mermaidToDiagram(source)
        assertIs<TextDiagramResult.Success>(result, "expected Success, got $result")
        return result
    }

    // --- flowchart ---

    @Test
    fun flowchartParsesNodesEdgesAndShapes() {
        val result = success(
            """
            flowchart TD
            A[Start] --> B{Check}
            B -->|yes| C(Handle)
            B -->|no| D[Stop]
            C -.-> D
            """.trimIndent(),
        )
        val graph = result.graph
        assertEquals(4, graph.nodes.size)
        assertEquals(4, graph.edges.size)

        val a = assertNotNull(graph.nodeById(DiagramNodeId("A")))
        assertEquals(DiagramNodePayload.FlowchartNode(FlowchartNodeKind.PROCESS), a.payload)
        assertEquals("Start", a.labels.first().text)

        val b = assertNotNull(graph.nodeById(DiagramNodeId("B")))
        assertEquals(DiagramNodePayload.FlowchartNode(FlowchartNodeKind.DECISION), b.payload)

        val c = assertNotNull(graph.nodeById(DiagramNodeId("C")))
        assertEquals(DiagramNodePayload.BasicShape(DiagramShapeKind.ROUNDED_RECTANGLE), c.payload)
    }

    @Test
    fun flowchartEdgeKindsAndLabels() {
        val result = success(
            """
            graph LR
            A --> B
            B --- C
            C -.-> D
            D ==> A
            A -->|label text| C
            """.trimIndent(),
        )
        val edges = result.graph.edges
        assertEquals(5, edges.size)
        assertEquals(DiagramRelation.Association(directed = true), edges[0].relation)
        assertEquals(DiagramRelation.Plain, edges[1].relation)
        assertEquals(DiagramStrokePattern.DASHED, edges[2].style.pattern)
        assertEquals(2.5, edges[3].style.strokeWidth)
        assertEquals("label text", edges[4].labels.single().label.text)
    }

    @Test
    fun flowchartChainedStatementCreatesTwoEdges() {
        val result = success(
            """
            flowchart TD
            A --> B --> C
            """.trimIndent(),
        )
        assertEquals(3, result.graph.nodes.size)
        assertEquals(2, result.graph.edges.size)
    }

    @Test
    fun flowchartIsLaidOutTopDown() {
        val result = success(
            """
            flowchart TD
            A[Top] --> B[Bottom]
            """.trimIndent(),
        )
        val a = assertNotNull(result.graph.nodeById(DiagramNodeId("A")))
        val b = assertNotNull(result.graph.nodeById(DiagramNodeId("B")))
        assertTrue(a.y < b.y, "TD layout should stack A above B, got a.y=${a.y}, b.y=${b.y}")
    }

    @Test
    fun flowchartLeftRightLayout() {
        val result = success(
            """
            flowchart LR
            A[Left] --> B[Right]
            """.trimIndent(),
        )
        val a = assertNotNull(result.graph.nodeById(DiagramNodeId("A")))
        val b = assertNotNull(result.graph.nodeById(DiagramNodeId("B")))
        assertTrue(a.x < b.x, "LR layout should place A left of B")
    }

    // --- classDiagram ---

    @Test
    fun classDiagramParsesMembersAndRelations() {
        val result = success(
            """
            classDiagram
            class Animal {
              +String name
              -age
              +speak()
            }
            class Dog
            Animal <|-- Dog
            Dog --> Owner : belongs
            Animal *-- Organ
            Animal o-- Toy
            Animal ..> Food
            """.trimIndent(),
        )
        val graph = result.graph
        assertEquals(6, graph.nodes.size)
        assertEquals(5, graph.edges.size)

        val animal = assertNotNull(graph.nodeById(DiagramNodeId("Animal")))
        val payload = assertIs<UmlClassNode>(animal.payload)
        assertEquals(2, payload.attributes.size)
        assertEquals(1, payload.operations.size)
        assertEquals(UmlVisibility.PUBLIC, payload.attributes[0].visibility)
        assertEquals(UmlVisibility.PRIVATE, payload.attributes[1].visibility)
        assertEquals("speak()", payload.operations[0].text)

        // Mermaid `Animal <|-- Dog` = Dog inherits Animal: source Dog, target Animal.
        val generalization = graph.edges.single { it.relation == DiagramRelation.Generalization }
        assertEquals(DiagramNodeId("Dog"), generalization.source.attachedNodeId)
        assertEquals(DiagramNodeId("Animal"), generalization.target.attachedNodeId)

        val composition = graph.edges.single { it.relation == DiagramRelation.Composition }
        assertEquals(DiagramNodeId("Animal"), composition.source.attachedNodeId)

        assertEquals(1, graph.edges.count { it.relation == DiagramRelation.Aggregation })
        assertEquals(1, graph.edges.count { it.relation == DiagramRelation.Dependency })

        val association = graph.edges.single { it.relation == DiagramRelation.Association(directed = true) }
        assertEquals("belongs", association.labels.single().label.text)
    }

    // --- stateDiagram ---

    @Test
    fun stateDiagramParsesInitialFinalAndTransitions() {
        val result = success(
            """
            stateDiagram-v2
            [*] --> Idle
            Idle --> Running : start
            Running --> [*]
            """.trimIndent(),
        )
        val graph = result.graph
        assertEquals(4, graph.nodes.size)
        assertEquals(3, graph.edges.size)
        assertTrue(graph.edges.all { it.relation == DiagramRelation.Transition })

        val kinds = graph.nodes.map { (it.payload as UmlStateNode).kind }
        assertEquals(1, kinds.count { it == UmlStateKind.INITIAL })
        assertEquals(1, kinds.count { it == UmlStateKind.FINAL })

        val labeled = graph.edges.single { it.labels.isNotEmpty() }
        assertEquals("start", labeled.labels.single().label.text)
    }

    // --- sequenceDiagram ---

    @Test
    fun sequenceDiagramParsesParticipantsAndMessages() {
        val result = success(
            """
            sequenceDiagram
            participant A as Alice
            actor B
            A->>B: hello
            B-->>A: hi
            A-->B: done
            """.trimIndent(),
        )
        val graph = result.graph
        assertEquals(2, graph.nodes.size)

        val alice = assertIs<UmlLifelineNode>(assertNotNull(graph.nodeById(DiagramNodeId("A"))).payload)
        assertEquals("Alice", alice.name)
        assertEquals(false, alice.actor)
        val bob = assertIs<UmlLifelineNode>(assertNotNull(graph.nodeById(DiagramNodeId("B"))).payload)
        assertTrue(bob.actor)

        assertEquals(3, graph.edges.size)
        assertEquals(DiagramRelation.Message(UmlMessageKind.SYNC), graph.edges[0].relation)
        assertEquals(DiagramRelation.Message(UmlMessageKind.ASYNC), graph.edges[1].relation)
        assertEquals(DiagramRelation.Message(UmlMessageKind.RETURN), graph.edges[2].relation)
        assertEquals("hello", graph.edges[0].labels.single().label.text)

        // Lifelines are placed side by side.
        val xs = graph.nodes.map { it.x }
        assertTrue(xs[0] < xs[1])
    }

    // --- failures ---

    @Test
    fun unsupportedDiagramTypeFails() {
        val result = mermaidToDiagram("pie\n\"a\": 1")
        val failure = assertIs<TextDiagramResult.Failure>(result)
        assertTrue(failure.diagnostics.single().message.contains("unsupported"))
    }

    @Test
    fun malformedFlowchartLineFailsWithLineNumber() {
        val result = mermaidToDiagram(
            """
            flowchart TD
            A[ok] --> B
            ???garbage???
            """.trimIndent(),
        )
        val failure = assertIs<TextDiagramResult.Failure>(result)
        assertEquals(3, failure.diagnostics.single().line)
    }

    @Test
    fun emptySourceFails() {
        assertIs<TextDiagramResult.Failure>(mermaidToDiagram("   \n  "))
    }
}
