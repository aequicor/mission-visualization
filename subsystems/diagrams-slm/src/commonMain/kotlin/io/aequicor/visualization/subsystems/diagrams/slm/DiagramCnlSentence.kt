package io.aequicor.visualization.subsystems.diagrams.slm

import io.aequicor.visualization.subsystems.diagrams.model.DiagramArrowhead
import io.aequicor.visualization.subsystems.diagrams.model.DiagramConnectionMode
import io.aequicor.visualization.subsystems.diagrams.model.DiagramEdgeLabel
import io.aequicor.visualization.subsystems.diagrams.model.DiagramGroup
import io.aequicor.visualization.subsystems.diagrams.model.DiagramLayer
import io.aequicor.visualization.subsystems.diagrams.model.DiagramLayerId
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNode
import io.aequicor.visualization.subsystems.diagrams.model.DiagramRelation
import io.aequicor.visualization.subsystems.diagrams.model.DiagramRoutingStyle
import io.aequicor.visualization.subsystems.diagrams.model.DiagramStyle
import io.aequicor.visualization.subsystems.diagrams.model.LineJumpStyle
import io.aequicor.visualization.subsystems.diagrams.path.DiagramPoint

/**
 * One parsed diagram CNL sentence (`Node …` / `Edge …` / `Layer …` / `Group …`) with its
 * 1-based source [line]. Edge endpoints stay **unresolved** here: a bare dotted token
 * `a.b` can only be classified (fixed port vs dotted node id) once every `Node` sentence
 * of the container body is known, so resolution happens during aggregation.
 */
public sealed interface DiagramCnlSentence {
    public val line: Int

    public data class LayerSentence(
        val layer: DiagramLayer,
        override val line: Int,
    ) : DiagramCnlSentence

    public data class NodeSentence(
        val node: DiagramNode,
        override val line: Int,
    ) : DiagramCnlSentence

    public data class EdgeSentence(
        val edge: DiagramCnlEdge,
        override val line: Int,
    ) : DiagramCnlSentence

    public data class GroupSentence(
        val group: DiagramGroup,
        override val line: Int,
    ) : DiagramCnlSentence
}

/** An edge as authored, before endpoint resolution against the collected node set. */
public data class DiagramCnlEdge(
    val id: String,
    val source: DiagramCnlEndpoint,
    val target: DiagramCnlEndpoint,
    val relation: DiagramRelation = DiagramRelation.Plain,
    val routing: DiagramRoutingStyle = DiagramRoutingStyle.ORTHOGONAL,
    val waypoints: List<DiagramPoint> = emptyList(),
    val style: DiagramStyle = DiagramStyle.Default,
    val labels: List<DiagramEdgeLabel> = emptyList(),
    val sourceArrowhead: DiagramArrowhead = DiagramArrowhead.None,
    val targetArrowhead: DiagramArrowhead = DiagramArrowhead.None,
    val lineJumps: LineJumpStyle = LineJumpStyle.NONE,
    val connectionMode: DiagramConnectionMode = DiagramConnectionMode.LINE,
    val flowAnimation: Boolean = false,
    val layerId: DiagramLayerId? = null,
)

/** An authored edge endpoint; [Bare] tokens resolve per the §5 rules during aggregation. */
public sealed interface DiagramCnlEndpoint {

    /** A bare id token — a node id, or `node.port` once resolution proves the port reading. */
    public data class Bare(val token: String) : DiagramCnlEndpoint

    /** A free `(x y)` point. */
    public data class Free(val x: Double, val y: Double) : DiagramCnlEndpoint

    /** The dot-safe `(node id [port id])` group form. */
    public data class Explicit(val nodeId: String, val portId: String?) : DiagramCnlEndpoint
}
