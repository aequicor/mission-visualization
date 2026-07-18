package io.aequicor.visualization.subsystems.diagrams.ops

import io.aequicor.visualization.subsystems.diagrams.model.DiagramEdge
import io.aequicor.visualization.subsystems.diagrams.model.DiagramEdgeId
import io.aequicor.visualization.subsystems.diagrams.model.DiagramEndpoint
import io.aequicor.visualization.subsystems.diagrams.model.DiagramGraph
import io.aequicor.visualization.subsystems.diagrams.model.DiagramLabel
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNode
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodeId
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodePayload
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodeSide
import io.aequicor.visualization.subsystems.diagrams.model.DiagramPort
import io.aequicor.visualization.subsystems.diagrams.model.DiagramPortId
import io.aequicor.visualization.subsystems.diagrams.model.DiagramRelation
import io.aequicor.visualization.subsystems.diagrams.model.DiagramRoutingStyle
import io.aequicor.visualization.subsystems.diagrams.model.DiagramStyle
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
import io.aequicor.visualization.subsystems.diagrams.model.attachedNodeId
import io.aequicor.visualization.subsystems.diagrams.model.updateNode
import io.aequicor.visualization.subsystems.diagrams.model.withEdge
import io.aequicor.visualization.subsystems.diagrams.model.withNode
import io.aequicor.visualization.subsystems.diagrams.path.DiagramPoint
import io.aequicor.visualization.subsystems.diagrams.path.DiagramRect

/**
 * Pure editing operations over [DiagramGraph]. Every op returns a new graph and is a
 * no-op (returns the receiver) when the target is missing or the input is invalid, so
 * they compose safely inside reducers.
 */

/**
 * Moves the node **and its whole container subtree** by (`dx`, `dy`). Waypoints of edges
 * whose both ends are attached inside the moved subtree move along.
 */
public fun DiagramGraph.moveNode(id: DiagramNodeId, dx: Double, dy: Double): DiagramGraph {
    if (nodeById(id) == null || (dx == 0.0 && dy == 0.0)) return this
    val moved = subtreeIds(id)
    return copy(
        nodes = nodes.map { node ->
            if (node.id in moved) node.copy(x = node.x + dx, y = node.y + dy) else node
        },
        edges = edges.map { edge ->
            val sourceInside = edge.source.attachedNodeId?.let { it in moved } ?: false
            val targetInside = edge.target.attachedNodeId?.let { it in moved } ?: false
            if (sourceInside && targetInside && edge.waypoints.isNotEmpty()) {
                edge.copy(waypoints = edge.waypoints.map { DiagramPoint(it.x + dx, it.y + dy) })
            } else {
                edge
            }
        },
    )
}

/**
 * Sets the node bounds. With [resizeChildren] the container's whole subtree is scaled
 * proportionally into the new bounds (positions and sizes); otherwise children keep their
 * absolute geometry.
 */
public fun DiagramGraph.resizeNode(
    id: DiagramNodeId,
    bounds: DiagramRect,
    resizeChildren: Boolean = false,
): DiagramGraph {
    val node = nodeById(id) ?: return this
    if (bounds.width < 0.0 || bounds.height < 0.0) return this
    val old = node.bounds
    val resized = updateNode(id) {
        it.copy(x = bounds.x, y = bounds.y, width = bounds.width, height = bounds.height)
    }
    if (!resizeChildren) return resized
    val descendants = subtreeIds(id) - id
    if (descendants.isEmpty()) return resized
    val scaleX = if (old.width > 0.0) bounds.width / old.width else 1.0
    val scaleY = if (old.height > 0.0) bounds.height / old.height else 1.0
    return resized.copy(
        nodes = resized.nodes.map { child ->
            if (child.id in descendants) {
                child.copy(
                    x = bounds.x + (child.x - old.x) * scaleX,
                    y = bounds.y + (child.y - old.y) * scaleY,
                    width = child.width * scaleX,
                    height = child.height * scaleY,
                )
            } else {
                child
            }
        },
    )
}

/**
 * The node's primary caption — the one string the canvas draws for it — regardless of which
 * store holds it: typed payloads keep it in the payload (`name` / `text` / `title`), untyped
 * shapes in [DiagramNode.labels]. `null` means the payload has no single caption.
 *
 * Editors MUST read text through this accessor rather than [DiagramNode.labels] directly:
 * reading the wrong store is what made the inline editor open empty over a visible caption.
 * Pair with [setNodeText].
 */
public fun DiagramNode.primaryText(): String? = when (val payload = payload) {
    // Untyped shapes: the caption lives on the node (see DiagramNodePayload.BasicShape's KDoc).
    is DiagramNodePayload.BasicShape,
    is DiagramNodePayload.FlowchartNode,
    is DiagramNodePayload.BpmnNode,
    -> labels.firstOrNull()?.text

    is DiagramNodePayload.ContainerNode -> payload.title?.text
    is DiagramNodePayload.SwimlaneNode -> payload.title?.text
    is DiagramNodePayload.ErEntityNode -> payload.name

    // A table has no node-level caption; every string lives in a cell (edited via TableCell ops).
    is TableNode -> null

    is UmlClassNode -> payload.name
    is UmlLifelineNode -> payload.name
    is UmlStateNode -> payload.name
    is UmlActivityNode -> payload.name
    is UmlActorNode -> payload.name
    is UmlUseCaseNode -> payload.name
    is UmlComponentNode -> payload.name
    is UmlDeploymentNode -> payload.name
    is UmlNoteNode -> payload.text
    is UmlPackageNode -> payload.name
}

/**
 * Writes the node's primary caption into whichever store that payload actually renders from
 * (the inverse of [primaryText]); `null` clears it. No-op for payloads without a single
 * caption ([TableNode]) or when the node is missing.
 *
 * The `when` is deliberately exhaustive with no `else`: a new payload kind must fail to compile
 * here rather than silently drop the user's text.
 */
public fun DiagramGraph.setNodeText(id: DiagramNodeId, text: String?): DiagramGraph {
    val node = nodeById(id) ?: return this
    return when (val payload = node.payload) {
        is DiagramNodePayload.BasicShape,
        is DiagramNodePayload.FlowchartNode,
        is DiagramNodePayload.BpmnNode,
        -> setNodeLabel(id, text)

        is DiagramNodePayload.ContainerNode ->
            updateNode(id) { it.copy(payload = payload.copy(title = text?.let(::DiagramLabel))) }

        is DiagramNodePayload.SwimlaneNode ->
            updateNode(id) { it.copy(payload = payload.copy(title = text?.let(::DiagramLabel))) }

        is DiagramNodePayload.ErEntityNode ->
            updateNode(id) { it.copy(payload = payload.copy(name = text.orEmpty())) }

        is TableNode -> this

        is UmlClassNode -> updateNode(id) { it.copy(payload = payload.copy(name = text.orEmpty())) }
        is UmlLifelineNode -> updateNode(id) { it.copy(payload = payload.copy(name = text.orEmpty())) }
        is UmlStateNode -> updateNode(id) { it.copy(payload = payload.copy(name = text.orEmpty())) }
        is UmlActivityNode -> updateNode(id) { it.copy(payload = payload.copy(name = text.orEmpty())) }
        is UmlActorNode -> updateNode(id) { it.copy(payload = payload.copy(name = text.orEmpty())) }
        is UmlUseCaseNode -> updateNode(id) { it.copy(payload = payload.copy(name = text.orEmpty())) }
        is UmlComponentNode -> updateNode(id) { it.copy(payload = payload.copy(name = text.orEmpty())) }
        is UmlDeploymentNode -> updateNode(id) { it.copy(payload = payload.copy(name = text.orEmpty())) }
        is UmlNoteNode -> updateNode(id) { it.copy(payload = payload.copy(text = text.orEmpty())) }
        is UmlPackageNode -> updateNode(id) { it.copy(payload = payload.copy(name = text.orEmpty())) }
    }
}

/** Replaces the node's primary label with [text]; `null` clears all node labels. */
public fun DiagramGraph.setNodeLabel(id: DiagramNodeId, text: String?): DiagramGraph =
    updateNode(id) { node ->
        node.copy(
            labels = when {
                text == null -> emptyList()
                node.labels.isEmpty() -> listOf(DiagramLabel(text))
                else -> listOf(node.labels.first().copy(text = text)) + node.labels.drop(1)
            },
        )
    }

/** Sets the node style. */
public fun DiagramGraph.setNodeStyle(id: DiagramNodeId, style: DiagramStyle): DiagramGraph =
    updateNode(id) { it.copy(style = style) }

/** Adds a custom connection point; no-op if a port with the same id already exists. */
public fun DiagramGraph.addCustomPort(id: DiagramNodeId, port: DiagramPort): DiagramGraph {
    val node = nodeById(id) ?: return this
    if (node.portById(port.id) != null) return this
    return updateNode(id) { it.copy(ports = it.ports + port) }
}

/**
 * Removes a port. Edges fixed to it stay connected to the node as floating anchors
 * (draw.io behavior: the arrow does not fall off).
 */
public fun DiagramGraph.removePort(id: DiagramNodeId, portId: DiagramPortId): DiagramGraph {
    val node = nodeById(id) ?: return this
    if (node.portById(portId) == null) return this
    val withoutPort = updateNode(id) { it.copy(ports = it.ports.filter { port -> port.id != portId }) }
    fun DiagramEndpoint.detached(): DiagramEndpoint =
        if (this is DiagramEndpoint.FixedPort && nodeId == id && this.portId == portId) {
            DiagramEndpoint.FloatingAnchor(id)
        } else {
            this
        }
    return withoutPort.copy(
        edges = withoutPort.edges.map { edge ->
            edge.copy(source = edge.source.detached(), target = edge.target.detached())
        },
    )
}

/**
 * Collision-aware offset for a directional-arrow clone of [sourceId] toward [side]: one
 * node-extent-plus-[gap] step, center-aligned on the perpendicular axis, then advanced by
 * further steps while the clone's rect would overlap another visible node — so repeated
 * clicks lay clones out in a row instead of stacking. Returns `null` when the source is
 * missing. Feed the result straight into [cloneNodeAndConnect]'s `offsetX`/`offsetY`.
 */
public fun DiagramGraph.directionalCloneOffset(
    sourceId: DiagramNodeId,
    side: DiagramNodeSide,
    gap: Double = 40.0,
): Pair<Double, Double>? {
    val source = nodeById(sourceId) ?: return null
    val (stepX, stepY) = when (side) {
        DiagramNodeSide.TOP -> 0.0 to -(source.height + gap)
        DiagramNodeSide.BOTTOM -> 0.0 to (source.height + gap)
        DiagramNodeSide.LEFT -> -(source.width + gap) to 0.0
        DiagramNodeSide.RIGHT -> (source.width + gap) to 0.0
    }
    var offsetX = stepX
    var offsetY = stepY
    repeat(32) {
        val rect = DiagramRect(source.x + offsetX, source.y + offsetY, source.width, source.height)
        val overlaps = nodes.any { it.id != sourceId && it.visible && it.bounds.intersects(rect) }
        if (!overlaps) return offsetX to offsetY
        offsetX += stepX
        offsetY += stepY
    }
    return offsetX to offsetY
}

/**
 * Clone-and-connect gesture: copies the node (geometry offset by [offsetX]/[offsetY],
 * children are not cloned) and connects the original to the clone with a floating edge.
 * No-op if the source is missing or [cloneId]/[edgeId] are already taken.
 */
public fun DiagramGraph.cloneNodeAndConnect(
    sourceId: DiagramNodeId,
    cloneId: DiagramNodeId,
    edgeId: DiagramEdgeId,
    offsetX: Double = 40.0,
    offsetY: Double = 40.0,
    relation: DiagramRelation = DiagramRelation.Plain,
    routing: DiagramRoutingStyle = DiagramRoutingStyle.ORTHOGONAL,
): DiagramGraph {
    val source = nodeById(sourceId) ?: return this
    if (nodeById(cloneId) != null || edgeById(edgeId) != null) return this
    val clone = source.copy(id = cloneId, x = source.x + offsetX, y = source.y + offsetY)
    val edge = DiagramEdge(
        id = edgeId,
        source = DiagramEndpoint.FloatingAnchor(sourceId),
        target = DiagramEndpoint.FloatingAnchor(cloneId),
        relation = relation,
        routing = routing,
        layerId = source.layerId,
    )
    return withNode(clone).withEdge(edge)
}
