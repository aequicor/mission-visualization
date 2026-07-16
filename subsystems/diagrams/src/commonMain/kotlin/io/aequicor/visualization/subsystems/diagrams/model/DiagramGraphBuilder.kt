package io.aequicor.visualization.subsystems.diagrams.model

import io.aequicor.visualization.subsystems.diagrams.path.DiagramPoint

/**
 * Builder DSL for tests and generators:
 *
 * ```
 * val graph = diagramGraph {
 *     val a = node("a", x = 0.0, y = 0.0, label = "A")
 *     val b = node("b", x = 200.0, y = 0.0, label = "B")
 *     edge("a-b", a, b, relation = DiagramRelation.Generalization)
 * }
 * ```
 */
fun diagramGraph(build: DiagramGraphBuilder.() -> Unit): DiagramGraph =
    DiagramGraphBuilder().apply(build).build()

class DiagramGraphBuilder internal constructor() {
    private val nodes = mutableListOf<DiagramNode>()
    private val edges = mutableListOf<DiagramEdge>()
    private val layers = mutableListOf<DiagramLayer>()
    private val groups = mutableListOf<DiagramGroup>()

    fun layer(
        id: String,
        name: String = id,
        visible: Boolean = true,
        locked: Boolean = false,
    ): DiagramLayerId {
        val layerId = DiagramLayerId(id)
        layers += DiagramLayer(id = layerId, name = name, visible = visible, locked = locked)
        return layerId
    }

    fun node(
        id: String,
        x: Double = 0.0,
        y: Double = 0.0,
        width: Double = 120.0,
        height: Double = 60.0,
        payload: DiagramNodePayload = DiagramNodePayload.BasicShape(),
        label: String? = null,
        rotation: Double = 0.0,
        ports: List<DiagramPort> = emptyList(),
        style: DiagramStyle = DiagramStyle.Default,
        labels: List<DiagramLabel> = emptyList(),
        parentId: DiagramNodeId? = null,
        layerId: DiagramLayerId? = null,
        locked: Boolean = false,
        visible: Boolean = true,
    ): DiagramNodeId {
        val nodeId = DiagramNodeId(id)
        nodes += DiagramNode(
            id = nodeId,
            x = x,
            y = y,
            width = width,
            height = height,
            rotation = rotation,
            payload = payload,
            ports = ports,
            style = style,
            labels = if (label != null) labels + DiagramLabel(label) else labels,
            parentId = parentId,
            layerId = layerId,
            locked = locked,
            visible = visible,
        )
        return nodeId
    }

    fun edge(
        id: String,
        source: DiagramEndpoint,
        target: DiagramEndpoint,
        relation: DiagramRelation = DiagramRelation.Plain,
        routing: DiagramRoutingStyle = DiagramRoutingStyle.ORTHOGONAL,
        label: String? = null,
        waypoints: List<DiagramPoint> = emptyList(),
        style: DiagramStyle = DiagramStyle.Default,
        labels: List<DiagramEdgeLabel> = emptyList(),
        sourceArrowhead: DiagramArrowhead = DiagramArrowhead.None,
        targetArrowhead: DiagramArrowhead = DiagramArrowhead.None,
        lineJumps: LineJumpStyle = LineJumpStyle.ARC,
        connectionMode: DiagramConnectionMode = DiagramConnectionMode.LINE,
        flowAnimation: Boolean = false,
        layerId: DiagramLayerId? = null,
    ): DiagramEdgeId {
        val edgeId = DiagramEdgeId(id)
        edges += DiagramEdge(
            id = edgeId,
            source = source,
            target = target,
            relation = relation,
            routing = routing,
            waypoints = waypoints,
            style = style,
            labels = if (label != null) {
                labels + DiagramEdgeLabel(DiagramLabel(label))
            } else {
                labels
            },
            sourceArrowhead = sourceArrowhead,
            targetArrowhead = targetArrowhead,
            lineJumps = lineJumps,
            connectionMode = connectionMode,
            flowAnimation = flowAnimation,
            layerId = layerId,
        )
        return edgeId
    }

    /** Convenience: floating-to-floating edge between two nodes. */
    fun edge(
        id: String,
        from: DiagramNodeId,
        to: DiagramNodeId,
        relation: DiagramRelation = DiagramRelation.Plain,
        routing: DiagramRoutingStyle = DiagramRoutingStyle.ORTHOGONAL,
        label: String? = null,
    ): DiagramEdgeId = edge(
        id = id,
        source = DiagramEndpoint.FloatingAnchor(from),
        target = DiagramEndpoint.FloatingAnchor(to),
        relation = relation,
        routing = routing,
        label = label,
    )

    fun group(
        id: String,
        members: List<DiagramNodeId>,
        name: String? = null,
    ): DiagramGroupId {
        val groupId = DiagramGroupId(id)
        groups += DiagramGroup(id = groupId, memberIds = members, name = name)
        return groupId
    }

    internal fun build(): DiagramGraph = DiagramGraph(
        nodes = nodes.toList(),
        edges = edges.toList(),
        layers = layers.toList(),
        groups = groups.toList(),
    )
}
