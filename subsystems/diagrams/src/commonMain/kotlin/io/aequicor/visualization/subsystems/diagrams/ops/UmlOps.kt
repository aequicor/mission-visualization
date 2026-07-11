package io.aequicor.visualization.subsystems.diagrams.ops

import io.aequicor.visualization.subsystems.diagrams.model.DiagramGraph
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodeId
import io.aequicor.visualization.subsystems.diagrams.model.UmlClassNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlMember
import io.aequicor.visualization.subsystems.diagrams.model.UmlVisibility
import io.aequicor.visualization.subsystems.diagrams.model.updateNode

/** Which member compartment of a [UmlClassNode] an operation targets. */
enum class UmlClassMemberKind { ATTRIBUTE, OPERATION }

/**
 * UML class node ops. All are no-ops when the node is missing or its payload is not a
 * [UmlClassNode].
 */

/** Inserts an attribute row at [index] (coerced; default appends). */
public fun DiagramGraph.addClassField(
    id: DiagramNodeId,
    member: UmlMember,
    index: Int = Int.MAX_VALUE,
): DiagramGraph = updateClass(id) { payload ->
    val at = index.coerceIn(0, payload.attributes.size)
    payload.copy(attributes = payload.attributes.take(at) + member + payload.attributes.drop(at))
}

/** Inserts an operation row at [index] (coerced; default appends). */
public fun DiagramGraph.addClassMethod(
    id: DiagramNodeId,
    member: UmlMember,
    index: Int = Int.MAX_VALUE,
): DiagramGraph = updateClass(id) { payload ->
    val at = index.coerceIn(0, payload.operations.size)
    payload.copy(operations = payload.operations.take(at) + member + payload.operations.drop(at))
}

/** Removes the member at [index] of the given compartment; no-op if out of bounds. */
public fun DiagramGraph.removeClassMember(
    id: DiagramNodeId,
    kind: UmlClassMemberKind,
    index: Int,
): DiagramGraph = updateClass(id) { payload ->
    when (kind) {
        UmlClassMemberKind.ATTRIBUTE ->
            if (index in payload.attributes.indices) {
                payload.copy(attributes = payload.attributes.filterIndexed { i, _ -> i != index })
            } else {
                payload
            }

        UmlClassMemberKind.OPERATION ->
            if (index in payload.operations.indices) {
                payload.copy(operations = payload.operations.filterIndexed { i, _ -> i != index })
            } else {
                payload
            }
    }
}

/** Sets the visibility of the member at [index]; no-op if out of bounds. */
public fun DiagramGraph.setClassMemberVisibility(
    id: DiagramNodeId,
    kind: UmlClassMemberKind,
    index: Int,
    visibility: UmlVisibility,
): DiagramGraph = updateClass(id) { payload ->
    fun List<UmlMember>.withVisibility(): List<UmlMember> = mapIndexed { i, member ->
        if (i == index) member.copy(visibility = visibility) else member
    }
    when (kind) {
        UmlClassMemberKind.ATTRIBUTE -> payload.copy(attributes = payload.attributes.withVisibility())
        UmlClassMemberKind.OPERATION -> payload.copy(operations = payload.operations.withVisibility())
    }
}

private fun DiagramGraph.updateClass(
    id: DiagramNodeId,
    transform: (UmlClassNode) -> UmlClassNode,
): DiagramGraph {
    val node = nodeById(id) ?: return this
    if (node.payload !is UmlClassNode) return this
    return updateNode(id) { it.copy(payload = transform(it.payload as UmlClassNode)) }
}
