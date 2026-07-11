package io.aequicor.visualization.subsystems.diagrams.slm

import io.aequicor.visualization.engine.frontend.blocks.SlmExtensionRegistry
import io.aequicor.visualization.engine.frontend.blocks.TypedBlockExtension
import io.aequicor.visualization.engine.frontend.blocks.readers.BlockReading
import io.aequicor.visualization.engine.frontend.yaml.YamlValue
import io.aequicor.visualization.engine.ir.model.DesignNode
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.subsystems.diagrams.model.DiagramEndpoint
import io.aequicor.visualization.subsystems.diagrams.model.DiagramGraph

/**
 * The `diagram:` typed-block extension: SLM YAML <-> [DiagramGraph] <-> IR diagram node.
 *
 * The composition root registers it via `SlmCompileOptions(extensions = [registry])`. The
 * block grammar (all keys optional, defaults omitted on write; canonical order `layers`,
 * `nodes`, `edges`, `groups`):
 *
 * ```
 * diagram:
 *   layers:
 *     - id: back              # name (defaults to id), visible: false, locked: true
 *   nodes:
 *     - id: user
 *       type: class           # shape kinds (rectangle/ellipse/...), container, swimlane,
 *                             # flowchart, entity, bpmn, table, class, lifeline, state,
 *                             # activity, actor, use_case, component, deployment, note, package
 *       x: 40                 # geometry in diagram-local coordinates
 *       y: 40
 *       w: 180
 *       h: 120
 *       name: User            # payload-specific keys follow `type`
 *       fields:               # uml members: "+ text" shorthand or { text, visibility, static, abstract }
 *         - "+ id: UUID"
 *       methods:
 *         - "+ login(): Boolean"
 *       ports:
 *         - id: out           # { id, side, offset } or { id, at: [x, y] }
 *           side: right
 *       style:                # fill/stroke "#AARRGGBB", strokeWidth, pattern, opacity,
 *         fill: "#FF336699"   # corners, sketch, shadow
 *       label: "User"         # or labels: [...]
 *   edges:
 *     - id: e1
 *       from: user            # nodeId | nodeId.portId | [x, y] | { node, port } | { x, y }
 *       to: base
 *       relation: generalization  # association/aggregation/composition/... or
 *                                 # { type: message, kind: sync } / { type: er, source: one, target: many }
 *       routing: straight     # straight/orthogonal/simple/isometric/curved/entity_relation
 *       waypoints:
 *         - [120, 80]
 *       label: "extends"      # or labels: [{ text, position, dx, dy }] (max 3, one per position)
 *       arrowheads:
 *         target: open        # kind token or { kind, size, inset }
 *   groups:
 *     - id: g1
 *       members: [user, base]
 * ```
 */
public object DiagramSlmExtension : TypedBlockExtension<DiagramGraph> {

    override val kind: String = "diagram"

    override fun read(value: YamlValue, reading: BlockReading): DiagramGraph? =
        DiagramYamlReader.read(value, reading)

    override fun validate(payload: DiagramGraph, reading: BlockReading) {
        validateDiagramReferences(payload, reading)
    }

    override fun applyToNode(node: DesignNode, payload: DiagramGraph): DesignNode =
        node.copy(type = "diagram", kind = DesignNodeKind.Diagram(payload))

    override fun write(payload: DiagramGraph): String = DiagramYamlWriter.blockText(payload)

    /** A registry containing just this extension, ready for `SlmCompileOptions.extensions`. */
    public fun registry(): SlmExtensionRegistry = SlmExtensionRegistry.of(DiagramSlmExtension)
}

/**
 * Cross-reference checks over a parsed graph: broken edge endpoints and port references
 * are errors, broken layer/parent references are errors, broken group members warnings.
 * Mirrors the IR-level `IR-DIAGRAM` validation but fires at SLM parse time with the
 * block's source location.
 */
internal fun validateDiagramReferences(graph: DiagramGraph, reading: BlockReading) {
    val nodeIds = graph.nodes.map { it.id }.toSet()
    val layerIds = graph.layers.map { it.id }.toSet()

    graph.edges.forEach { edge ->
        listOf("from" to edge.source, "to" to edge.target).forEach { (key, endpoint) ->
            when (endpoint) {
                is DiagramEndpoint.FloatingAnchor ->
                    if (endpoint.nodeId !in nodeIds) {
                        reading.diagnostics.error(
                            "diagram edge '${edge.id.value}' `$key` references missing node " +
                                "'${endpoint.nodeId.value}'",
                            blockPath = reading.blockPath,
                        )
                    }
                is DiagramEndpoint.FixedPort -> {
                    val node = graph.nodeById(endpoint.nodeId)
                    if (node == null) {
                        reading.diagnostics.error(
                            "diagram edge '${edge.id.value}' `$key` references missing node " +
                                "'${endpoint.nodeId.value}'",
                            blockPath = reading.blockPath,
                        )
                    } else if (node.portById(endpoint.portId) == null) {
                        reading.diagnostics.error(
                            "diagram edge '${edge.id.value}' `$key` references port " +
                                "'${endpoint.portId.value}' that node '${endpoint.nodeId.value}' " +
                                "does not declare",
                            blockPath = reading.blockPath,
                        )
                    }
                }
                is DiagramEndpoint.FreePoint -> Unit
            }
        }
        edge.layerId?.let { layerId ->
            if (layerId !in layerIds) {
                reading.diagnostics.error(
                    "diagram edge '${edge.id.value}' references missing layer '${layerId.value}'",
                    blockPath = reading.blockPath,
                )
            }
        }
    }

    graph.nodes.forEach { node ->
        node.parentId?.let { parentId ->
            if (parentId !in nodeIds) {
                reading.diagnostics.error(
                    "diagram node '${node.id.value}' references missing parent '${parentId.value}'",
                    blockPath = reading.blockPath,
                )
            }
        }
        node.layerId?.let { layerId ->
            if (layerId !in layerIds) {
                reading.diagnostics.error(
                    "diagram node '${node.id.value}' references missing layer '${layerId.value}'",
                    blockPath = reading.blockPath,
                )
            }
        }
    }

    graph.groups.forEach { group ->
        group.memberIds.forEach { memberId ->
            if (memberId !in nodeIds) {
                reading.diagnostics.warning(
                    "diagram group '${group.id.value}' references missing node '${memberId.value}'",
                    blockPath = reading.blockPath,
                )
            }
        }
    }
}
