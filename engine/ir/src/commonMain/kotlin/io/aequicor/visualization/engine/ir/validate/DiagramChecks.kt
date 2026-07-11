package io.aequicor.visualization.engine.ir.validate

import io.aequicor.visualization.engine.ir.model.DesignDiagnostic
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.engine.ir.model.SourceLocation
import io.aequicor.visualization.subsystems.diagrams.model.DiagramEndpoint
import io.aequicor.visualization.subsystems.diagrams.model.DiagramGraph
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodeId

/**
 * IR-DIAGRAM — referential integrity of embedded diagram graphs
 * ([DesignNodeKind.Diagram] payloads).
 *
 * - IR-DIAGRAM-001 (error): an edge endpoint references a diagram node that does not exist.
 * - IR-DIAGRAM-002 (error): a fixed-port endpoint references a port its node does not declare.
 * - IR-DIAGRAM-003 (error): a diagram node or edge references a missing layer.
 * - IR-DIAGRAM-004 (error): a diagram node's parentId references a missing node.
 * - IR-DIAGRAM-005 (error): the parent chain of a diagram node forms a cycle.
 * - IR-DIAGRAM-006 (warning): a group member references a missing node.
 */
internal object DiagramChecks {

    fun check(ctx: ValidationContext): List<DesignDiagnostic> = buildList {
        ctx.entries.forEach { entry ->
            val kind = entry.node.kind as? DesignNodeKind.Diagram ?: return@forEach
            checkGraph(this, entry.node.id, ctx.location(entry.node), kind.graph)
        }
    }

    private fun checkGraph(
        sink: MutableList<DesignDiagnostic>,
        nodeId: String,
        location: SourceLocation?,
        graph: DiagramGraph,
    ) {
        val diagramNodeIds = graph.nodes.map { it.id }.toSet()
        val layerIds = graph.layers.map { it.id }.toSet()

        graph.edges.forEach { edge ->
            checkEndpoint(sink, nodeId, location, graph, diagramNodeIds, edge.id.value, "source", edge.source)
            checkEndpoint(sink, nodeId, location, graph, diagramNodeIds, edge.id.value, "target", edge.target)
            edge.layerId?.let { layerId ->
                if (layerId !in layerIds) {
                    sink += validationError(
                        "IR-DIAGRAM-003",
                        "Diagram '$nodeId' edge '${edge.id.value}' references missing layer '${layerId.value}'",
                        location,
                    )
                }
            }
        }

        graph.nodes.forEach { node ->
            node.layerId?.let { layerId ->
                if (layerId !in layerIds) {
                    sink += validationError(
                        "IR-DIAGRAM-003",
                        "Diagram '$nodeId' node '${node.id.value}' references missing layer '${layerId.value}'",
                        location,
                    )
                }
            }
            node.parentId?.let { parentId ->
                if (parentId !in diagramNodeIds) {
                    sink += validationError(
                        "IR-DIAGRAM-004",
                        "Diagram '$nodeId' node '${node.id.value}' references missing parent '${parentId.value}'",
                        location,
                    )
                }
            }
        }
        checkParentCycles(sink, nodeId, location, graph)

        graph.groups.forEach { group ->
            group.memberIds.forEach { memberId ->
                if (memberId !in diagramNodeIds) {
                    sink += validationWarning(
                        "IR-DIAGRAM-006",
                        "Diagram '$nodeId' group '${group.id.value}' references missing node '${memberId.value}'",
                        location,
                    )
                }
            }
        }
    }

    private fun checkEndpoint(
        sink: MutableList<DesignDiagnostic>,
        nodeId: String,
        location: SourceLocation?,
        graph: DiagramGraph,
        diagramNodeIds: Set<DiagramNodeId>,
        edgeId: String,
        end: String,
        endpoint: DiagramEndpoint,
    ) {
        when (endpoint) {
            is DiagramEndpoint.FreePoint -> Unit
            is DiagramEndpoint.FloatingAnchor -> {
                if (endpoint.nodeId !in diagramNodeIds) {
                    sink += validationError(
                        "IR-DIAGRAM-001",
                        "Diagram '$nodeId' edge '$edgeId' $end references missing node '${endpoint.nodeId.value}'",
                        location,
                    )
                }
            }
            is DiagramEndpoint.FixedPort -> {
                val target = graph.nodeById(endpoint.nodeId)
                if (target == null) {
                    sink += validationError(
                        "IR-DIAGRAM-001",
                        "Diagram '$nodeId' edge '$edgeId' $end references missing node '${endpoint.nodeId.value}'",
                        location,
                    )
                } else if (target.portById(endpoint.portId) == null) {
                    sink += validationError(
                        "IR-DIAGRAM-002",
                        "Diagram '$nodeId' edge '$edgeId' $end references missing port " +
                            "'${endpoint.portId.value}' on node '${endpoint.nodeId.value}'",
                        location,
                    )
                }
            }
        }
    }

    /** Flags every diagram node whose parent chain loops back onto itself. */
    private fun checkParentCycles(
        sink: MutableList<DesignDiagnostic>,
        nodeId: String,
        location: SourceLocation?,
        graph: DiagramGraph,
    ) {
        val parents = graph.nodes.associate { it.id to it.parentId }
        val reported = mutableSetOf<DiagramNodeId>()
        graph.nodes.forEach { node ->
            if (node.id in reported) return@forEach
            val visited = mutableSetOf<DiagramNodeId>()
            var current: DiagramNodeId? = node.id
            while (current != null && current !in visited) {
                visited += current
                current = parents[current]
            }
            if (current != null && current !in reported) {
                reported += visited
                sink += validationError(
                    "IR-DIAGRAM-005",
                    "Diagram '$nodeId' node '${current.value}' is part of a parent cycle",
                    location,
                )
            }
        }
    }
}
