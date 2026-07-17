package io.aequicor.visualization.subsystems.diagrams.ops

import io.aequicor.visualization.subsystems.diagrams.model.BpmnNodeKind
import io.aequicor.visualization.subsystems.diagrams.model.DiagramGraph
import io.aequicor.visualization.subsystems.diagrams.model.DiagramLabel
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNode
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodeId
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodePayload
import io.aequicor.visualization.subsystems.diagrams.model.DiagramShapeKind
import io.aequicor.visualization.subsystems.diagrams.model.FlowchartNodeKind
import io.aequicor.visualization.subsystems.diagrams.model.TableColumn
import io.aequicor.visualization.subsystems.diagrams.model.TableNode
import io.aequicor.visualization.subsystems.diagrams.model.TableRow
import io.aequicor.visualization.subsystems.diagrams.model.UmlActivityKind
import io.aequicor.visualization.subsystems.diagrams.model.UmlActivityNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlActorNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlClassNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlComponentNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlDeploymentNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlLifelineNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlNoteNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlPackageNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlStateKind
import io.aequicor.visualization.subsystems.diagrams.model.UmlStateNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlUseCaseNode
import io.aequicor.visualization.subsystems.diagrams.model.withNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [primaryText] / [setNodeText] over every payload kind: the canvas draws exactly one caption
 * per node, and editors must read and write it through this pair rather than guessing a store.
 */
class NodeTextOpsTest {

    private val id = DiagramNodeId("n1")

    private fun graphOf(payload: DiagramNodePayload, labels: List<DiagramLabel> = emptyList()): DiagramGraph =
        DiagramGraph().withNode(
            DiagramNode(id = id, x = 0.0, y = 0.0, width = 120.0, height = 60.0, payload = payload, labels = labels),
        )

    private fun DiagramGraph.node(): DiagramNode = requireNotNull(nodeById(id))

    /** Every payload kind, with the caption store it renders from. */
    private val payloadsCarryingTextInThePayload: List<DiagramNodePayload> = listOf(
        DiagramNodePayload.ContainerNode(title = DiagramLabel("seed")),
        DiagramNodePayload.SwimlaneNode(title = DiagramLabel("seed")),
        DiagramNodePayload.ErEntityNode(name = "seed"),
        UmlClassNode(name = "seed"),
        UmlLifelineNode(name = "seed"),
        UmlStateNode(name = "seed", kind = UmlStateKind.SIMPLE),
        UmlActivityNode(kind = UmlActivityKind.ACTION, name = "seed"),
        UmlActorNode(name = "seed"),
        UmlUseCaseNode(name = "seed"),
        UmlComponentNode(name = "seed"),
        UmlDeploymentNode(name = "seed"),
        UmlNoteNode(text = "seed"),
        UmlPackageNode(name = "seed"),
    )

    private val payloadsCarryingTextInNodeLabels: List<DiagramNodePayload> = listOf(
        DiagramNodePayload.BasicShape(DiagramShapeKind.RECTANGLE),
        DiagramNodePayload.FlowchartNode(FlowchartNodeKind.PROCESS),
        DiagramNodePayload.BpmnNode(BpmnNodeKind.TASK),
    )

    private val tablePayload = TableNode(
        rows = listOf(TableRow(32.0)),
        columns = listOf(TableColumn(120.0)),
    )

    @Test
    fun everyPayloadKindIsCovered() {
        // Guards the exhaustive `when` in setNodeText: a new payload kind must be added here too.
        val covered = payloadsCarryingTextInThePayload.size + payloadsCarryingTextInNodeLabels.size + 1
        assertEquals(17, covered, "all 17 DiagramNodePayload kinds must be exercised")
    }

    @Test
    fun primaryTextReadsWhicheverStoreThePayloadRendersFrom() {
        payloadsCarryingTextInThePayload.forEach { payload ->
            assertEquals("seed", graphOf(payload).node().primaryText(), "payload ${payload::class.simpleName}")
        }
        payloadsCarryingTextInNodeLabels.forEach { payload ->
            assertEquals(
                "seed",
                graphOf(payload, labels = listOf(DiagramLabel("seed"))).node().primaryText(),
                "payload ${payload::class.simpleName}",
            )
        }
    }

    @Test
    fun setNodeTextRoundTripsThroughPrimaryTextForEveryKind() {
        (payloadsCarryingTextInThePayload + payloadsCarryingTextInNodeLabels).forEach { payload ->
            val updated = graphOf(payload).setNodeText(id, "написано")
            assertEquals(
                "написано",
                updated.node().primaryText(),
                "setNodeText/primaryText must round-trip for ${payload::class.simpleName}",
            )
        }
    }

    @Test
    fun typedPayloadTextGoesToThePayloadAndNeverOrphansANodeLabel() {
        val updated = graphOf(UmlUseCaseNode(name = "before")).setNodeText(id, "after")

        assertEquals("after", (updated.node().payload as UmlUseCaseNode).name)
        assertTrue(updated.node().labels.isEmpty(), "typed payloads must not grow an orphaned node label")
    }

    @Test
    fun basicShapeTextGoesToNodeLabelsAndNotThePayload() {
        val updated = graphOf(DiagramNodePayload.BasicShape(DiagramShapeKind.RECTANGLE)).setNodeText(id, "after")

        assertEquals("after", updated.node().labels.firstOrNull()?.text)
        assertEquals(DiagramNodePayload.BasicShape(DiagramShapeKind.RECTANGLE), updated.node().payload)
    }

    @Test
    fun nullClearsTheCaptionInEitherStore() {
        val clearedTyped = graphOf(UmlUseCaseNode(name = "before")).setNodeText(id, null)
        assertEquals("", (clearedTyped.node().payload as UmlUseCaseNode).name)

        val clearedContainer = graphOf(DiagramNodePayload.ContainerNode(title = DiagramLabel("t")))
            .setNodeText(id, null)
        assertNull((clearedContainer.node().payload as DiagramNodePayload.ContainerNode).title)

        val clearedShape = graphOf(
            DiagramNodePayload.BasicShape(DiagramShapeKind.RECTANGLE),
            labels = listOf(DiagramLabel("t")),
        ).setNodeText(id, null)
        assertTrue(clearedShape.node().labels.isEmpty())
    }

    @Test
    fun tableHasNoNodeLevelCaptionAndIsLeftAlone() {
        val graph = graphOf(tablePayload)

        assertNull(graph.node().primaryText(), "table text lives per-cell, not on the node")
        assertEquals(graph, graph.setNodeText(id, "ignored"), "setNodeText must not touch a table")
    }

    @Test
    fun missingNodeIsANoOp() {
        val graph = graphOf(UmlUseCaseNode(name = "a"))
        assertEquals(graph, graph.setNodeText(DiagramNodeId("nope"), "b"))
    }
}
