package io.aequicor.visualization.subsystems.diagrams.ops

import io.aequicor.visualization.subsystems.diagrams.geometry.DiagramSize
import io.aequicor.visualization.subsystems.diagrams.model.BpmnNodeKind
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodePayload
import io.aequicor.visualization.subsystems.diagrams.model.DiagramShapeKind
import io.aequicor.visualization.subsystems.diagrams.model.TableNode
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

/**
 * Starting and minimum sizes per payload kind — the single source for the editor palette,
 * the templates and [fitNodeToText]'s floor, which previously disagreed (a use case started
 * at 150x70 from the palette but 160x70 from a template).
 *
 * Sizes follow draw.io's stock shapes where it has one (use case 140x70, class 160x90,
 * actor 30x60 — widened here to 60x90, which the actor renderer's stick figure needs).
 */
public object DiagramNodeDefaults {

    /** The size a freshly created node of this kind starts at. */
    public fun defaultSizeFor(payload: DiagramNodePayload): DiagramSize = when (payload) {
        is DiagramNodePayload.BasicShape -> when (payload.shape) {
            DiagramShapeKind.RHOMBUS -> DiagramSize(120.0, 80.0)
            DiagramShapeKind.TRIANGLE -> DiagramSize(100.0, 80.0)
            DiagramShapeKind.HEXAGON -> DiagramSize(130.0, 70.0)
            DiagramShapeKind.PARALLELOGRAM, DiagramShapeKind.TRAPEZOID -> DiagramSize(140.0, 60.0)
            DiagramShapeKind.CYLINDER -> DiagramSize(100.0, 80.0)
            DiagramShapeKind.CLOUD -> DiagramSize(140.0, 80.0)
            else -> DiagramSize(120.0, 60.0)
        }

        is DiagramNodePayload.ContainerNode -> DiagramSize(240.0, 160.0)
        is DiagramNodePayload.SwimlaneNode -> DiagramSize(360.0, 240.0)
        is DiagramNodePayload.FlowchartNode -> DiagramSize(120.0, 60.0)
        is DiagramNodePayload.ErEntityNode -> DiagramSize(180.0, 96.0)
        is DiagramNodePayload.BpmnNode -> when (payload.kind) {
            BpmnNodeKind.TASK -> DiagramSize(120.0, 60.0)
            BpmnNodeKind.EVENT -> DiagramSize(40.0, 40.0)
            BpmnNodeKind.GATEWAY -> DiagramSize(50.0, 50.0)
        }

        is TableNode -> DiagramSize(300.0, 120.0)
        is UmlClassNode -> DiagramSize(160.0, 90.0)
        is UmlLifelineNode -> DiagramSize(120.0, 200.0)
        is UmlStateNode -> when (payload.kind) {
            UmlStateKind.INITIAL, UmlStateKind.FINAL -> DiagramSize(24.0, 24.0)
            else -> DiagramSize(140.0, 56.0)
        }

        is UmlActivityNode -> when (payload.kind) {
            UmlActivityKind.START, UmlActivityKind.END -> DiagramSize(24.0, 24.0)
            UmlActivityKind.DECISION -> DiagramSize(60.0, 60.0)
            UmlActivityKind.FORK, UmlActivityKind.JOIN -> DiagramSize(120.0, 8.0)
            UmlActivityKind.ACTION -> DiagramSize(140.0, 56.0)
        }

        is UmlActorNode -> DiagramSize(60.0, 90.0)
        is UmlUseCaseNode -> DiagramSize(140.0, 70.0)
        is UmlComponentNode -> DiagramSize(180.0, 60.0)
        is UmlDeploymentNode -> DiagramSize(180.0, 90.0)
        is UmlNoteNode -> DiagramSize(160.0, 80.0)
        is UmlPackageNode -> DiagramSize(200.0, 120.0)
    }

    /**
     * The smallest size [fitNodeToText] may shrink a node to, so a one-word caption still
     * reads as its shape rather than collapsing onto the text.
     */
    public fun minimumSizeFor(payload: DiagramNodePayload): DiagramSize = when (payload) {
        is UmlUseCaseNode -> DiagramSize(80.0, 40.0)
        is UmlStateNode, is UmlActivityNode -> DiagramSize(60.0, 32.0)
        is UmlNoteNode -> DiagramSize(60.0, 40.0)
        is UmlPackageNode -> DiagramSize(80.0, 60.0)
        else -> DiagramSize(40.0, 24.0)
    }
}
