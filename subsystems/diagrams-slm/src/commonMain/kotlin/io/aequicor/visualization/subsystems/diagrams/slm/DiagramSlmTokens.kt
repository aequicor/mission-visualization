package io.aequicor.visualization.subsystems.diagrams.slm

import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodePayload
import io.aequicor.visualization.subsystems.diagrams.model.TableNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlActivityNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlActorNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlClassNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlComponentNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlDeploymentNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlLifelineNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlNoteNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlPackageNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlStateNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlUseCaseNode

// Shared token vocabulary of the diagram CNL grammar (reader and writer sides).

/** Canonical SLM token of an enum constant: lowercase snake of the Kotlin name. */
internal fun Enum<*>.slmToken(): String = name.lowercase()

/** Parses [token] case-insensitively, accepting `-` for `_`; null on unknown values. */
internal inline fun <reified E : Enum<E>> enumFromToken(token: String): E? {
    val normalized = token.trim().lowercase().replace('-', '_')
    return enumValues<E>().firstOrNull { it.name.lowercase() == normalized }
}

/** Canonical snake-case type token of a node payload (`use_case`, `class`, …). */
internal fun payloadTypeToken(payload: DiagramNodePayload): String = when (payload) {
    is DiagramNodePayload.BasicShape -> payload.shape.slmToken()
    is DiagramNodePayload.ContainerNode -> "container"
    is DiagramNodePayload.SwimlaneNode -> "swimlane"
    is DiagramNodePayload.FlowchartNode -> "flowchart"
    is DiagramNodePayload.ErEntityNode -> "entity"
    is DiagramNodePayload.BpmnNode -> "bpmn"
    is TableNode -> "table"
    is UmlClassNode -> "class"
    is UmlLifelineNode -> "lifeline"
    is UmlStateNode -> "state"
    is UmlActivityNode -> "activity"
    is UmlActorNode -> "actor"
    is UmlUseCaseNode -> "use_case"
    is UmlComponentNode -> "component"
    is UmlDeploymentNode -> "deployment"
    is UmlNoteNode -> "note"
    is UmlPackageNode -> "package"
}
