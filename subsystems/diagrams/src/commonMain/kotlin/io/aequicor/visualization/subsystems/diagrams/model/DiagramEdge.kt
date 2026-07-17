package io.aequicor.visualization.subsystems.diagrams.model

import io.aequicor.visualization.subsystems.diagrams.path.DiagramPoint

/**
 * Where an edge end attaches. Floating ends slide along the node perimeter (the router
 * picks the exit point); fixed ends stay glued to a specific [DiagramPort]; free ends
 * float in empty canvas space.
 */
sealed interface DiagramEndpoint {

    /** Floating attachment: connected to the node, exit point chosen by the router. */
    data class FloatingAnchor(
        val nodeId: DiagramNodeId,
    ) : DiagramEndpoint

    /** Fixed attachment to a specific port of a node. */
    data class FixedPort(
        val nodeId: DiagramNodeId,
        val portId: DiagramPortId,
    ) : DiagramEndpoint

    /** Unattached end at an absolute document point. */
    data class FreePoint(
        val x: Double,
        val y: Double,
    ) : DiagramEndpoint
}

/** The node this endpoint is attached to, or `null` for [DiagramEndpoint.FreePoint]. */
val DiagramEndpoint.attachedNodeId: DiagramNodeId?
    get() = when (this) {
        is DiagramEndpoint.FloatingAnchor -> nodeId
        is DiagramEndpoint.FixedPort -> nodeId
        is DiagramEndpoint.FreePoint -> null
    }

/** UML sequence-message kinds. */
enum class UmlMessageKind { SYNC, ASYNC, RETURN, CREATE, DESTROY }

/** Cardinality of one end of an ER relationship. */
enum class ErCardinality { ONE, ZERO_OR_ONE, MANY, ONE_OR_MANY, ZERO_OR_MANY }

/**
 * Semantic kind of a connection. Determines the default UML/ER notation (arrowheads,
 * dashing) via [notationArrowheads] / [isDashedNotation]; explicit edge style/arrowheads
 * override the notation.
 */
sealed interface DiagramRelation {

    /** No specific semantics: a plain connector. */
    data object Plain : DiagramRelation

    /** UML association; [directed] draws an open arrow at the target end. */
    data class Association(
        val directed: Boolean = false,
    ) : DiagramRelation

    /** UML aggregation: hollow diamond at the source (aggregate) end. */
    data object Aggregation : DiagramRelation

    /** UML composition: filled diamond at the source (whole) end. */
    data object Composition : DiagramRelation

    /** UML generalization (inheritance): hollow triangle at the target (parent) end. */
    data object Generalization : DiagramRelation

    /** UML dependency: dashed line with an open arrow at the target end. */
    data object Dependency : DiagramRelation

    /** UML realization (interface implementation): dashed line, hollow triangle at target. */
    data object Realization : DiagramRelation

    /** Sequence-diagram message. */
    data class Message(
        val kind: UmlMessageKind,
    ) : DiagramRelation

    /** State/activity transition: open arrow at the target end. */
    data object Transition : DiagramRelation

    /** Use-case «include»: dashed, open arrow at the included use case. */
    data object Include : DiagramRelation

    /** Use-case «extend»: dashed, open arrow at the extended use case. */
    data object Extend : DiagramRelation

    /** ER relationship with crow's-foot cardinalities at each end. */
    data class EntityRelation(
        val sourceCardinality: ErCardinality = ErCardinality.ONE,
        val targetCardinality: ErCardinality = ErCardinality.MANY,
    ) : DiagramRelation
}

/** Default arrowhead pair implied by a relation's notation. */
data class DiagramNotationArrowheads(
    val source: DiagramArrowhead,
    val target: DiagramArrowhead,
)

/** UML/ER notation arrowheads for this relation (renderer default when the edge doesn't override). */
fun DiagramRelation.notationArrowheads(): DiagramNotationArrowheads {
    fun head(kind: DiagramArrowheadKind): DiagramArrowhead = DiagramArrowhead(kind)
    val none = DiagramArrowhead.None
    return when (this) {
        DiagramRelation.Plain -> DiagramNotationArrowheads(none, none)
        is DiagramRelation.Association -> DiagramNotationArrowheads(
            source = none,
            target = if (directed) head(DiagramArrowheadKind.OPEN) else none,
        )
        DiagramRelation.Aggregation ->
            DiagramNotationArrowheads(head(DiagramArrowheadKind.DIAMOND), none)
        DiagramRelation.Composition ->
            DiagramNotationArrowheads(head(DiagramArrowheadKind.DIAMOND_FILLED), none)
        DiagramRelation.Generalization ->
            DiagramNotationArrowheads(none, head(DiagramArrowheadKind.TRIANGLE))
        DiagramRelation.Dependency ->
            DiagramNotationArrowheads(none, head(DiagramArrowheadKind.OPEN))
        DiagramRelation.Realization ->
            DiagramNotationArrowheads(none, head(DiagramArrowheadKind.TRIANGLE))
        is DiagramRelation.Message -> DiagramNotationArrowheads(
            source = none,
            target = when (kind) {
                UmlMessageKind.SYNC -> head(DiagramArrowheadKind.TRIANGLE_FILLED)
                UmlMessageKind.ASYNC -> head(DiagramArrowheadKind.OPEN)
                UmlMessageKind.RETURN -> head(DiagramArrowheadKind.OPEN)
                UmlMessageKind.CREATE -> head(DiagramArrowheadKind.OPEN)
                UmlMessageKind.DESTROY -> head(DiagramArrowheadKind.CROSS)
            },
        )
        DiagramRelation.Transition ->
            DiagramNotationArrowheads(none, head(DiagramArrowheadKind.OPEN))
        DiagramRelation.Include ->
            DiagramNotationArrowheads(none, head(DiagramArrowheadKind.OPEN))
        DiagramRelation.Extend ->
            DiagramNotationArrowheads(none, head(DiagramArrowheadKind.OPEN))
        is DiagramRelation.EntityRelation -> DiagramNotationArrowheads(
            source = head(sourceCardinality.arrowheadKind()),
            target = head(targetCardinality.arrowheadKind()),
        )
    }
}

/** Whether this relation's notation draws the line dashed. */
val DiagramRelation.isDashedNotation: Boolean
    get() = when (this) {
        DiagramRelation.Dependency,
        DiagramRelation.Realization,
        DiagramRelation.Include,
        DiagramRelation.Extend,
        -> true

        is DiagramRelation.Message -> kind == UmlMessageKind.RETURN || kind == UmlMessageKind.CREATE
        else -> false
    }

/** Crow's-foot arrowhead for an ER cardinality. */
fun ErCardinality.arrowheadKind(): DiagramArrowheadKind = when (this) {
    ErCardinality.ONE -> DiagramArrowheadKind.ER_ONE
    ErCardinality.ZERO_OR_ONE -> DiagramArrowheadKind.ER_ZERO_OR_ONE
    ErCardinality.MANY -> DiagramArrowheadKind.ER_MANY
    ErCardinality.ONE_OR_MANY -> DiagramArrowheadKind.ER_ONE_OR_MANY
    ErCardinality.ZERO_OR_MANY -> DiagramArrowheadKind.ER_ZERO_OR_MANY
}

/** Which end of the edge a label is pinned near. */
enum class DiagramEdgeLabelPosition { SOURCE, MIDDLE, TARGET }

/** A label pinned to an edge at [position], draggable by [offsetX]/[offsetY] document units. */
data class DiagramEdgeLabel(
    val label: DiagramLabel,
    val position: DiagramEdgeLabelPosition = DiagramEdgeLabelPosition.MIDDLE,
    val offsetX: Double = 0.0,
    val offsetY: Double = 0.0,
)

/**
 * A connector between two [DiagramEndpoint]s.
 *
 * @param waypoints manual route points (in document coordinates) the router must pass
 *   through, in source→target order; empty means fully automatic routing.
 * @param sourceArrowhead/targetArrowhead explicit markers; [DiagramArrowheadKind.NONE]
 *   with a non-Plain [relation] means "use the relation's notation"
 *   (see [DiagramRelation.notationArrowheads]).
 * @param labels at most three, one per [DiagramEdgeLabelPosition].
 */
data class DiagramEdge(
    val id: DiagramEdgeId,
    val source: DiagramEndpoint,
    val target: DiagramEndpoint,
    val relation: DiagramRelation = DiagramRelation.Plain,
    val routing: DiagramRoutingStyle = DiagramRoutingStyle.ORTHOGONAL,
    val waypoints: List<DiagramPoint> = emptyList(),
    val style: DiagramStyle = DiagramStyle.Default,
    val labels: List<DiagramEdgeLabel> = emptyList(),
    val sourceArrowhead: DiagramArrowhead = DiagramArrowhead.None,
    val targetArrowhead: DiagramArrowhead = DiagramArrowhead.None,
    val lineJumps: LineJumpStyle = LineJumpStyle.ARC,
    val connectionMode: DiagramConnectionMode = DiagramConnectionMode.LINE,
    val flowAnimation: Boolean = false,
    val layerId: DiagramLayerId? = null,
) {
    init {
        require(labels.size <= 3) { "edge ${id.value} has ${labels.size} labels, max is 3" }
        require(labels.map { it.position }.toSet().size == labels.size) {
            "edge ${id.value} has multiple labels at the same position"
        }
    }
}

/**
 * Reverses the edge direction: swaps endpoints and arrowheads, reverses waypoints,
 * and swaps SOURCE/TARGET label positions (draw.io "reverse" semantics).
 */
fun DiagramEdge.reversed(): DiagramEdge = copy(
    source = target,
    target = source,
    sourceArrowhead = targetArrowhead,
    targetArrowhead = sourceArrowhead,
    waypoints = waypoints.reversed(),
    labels = labels.map { edgeLabel ->
        edgeLabel.copy(
            position = when (edgeLabel.position) {
                DiagramEdgeLabelPosition.SOURCE -> DiagramEdgeLabelPosition.TARGET
                DiagramEdgeLabelPosition.MIDDLE -> DiagramEdgeLabelPosition.MIDDLE
                DiagramEdgeLabelPosition.TARGET -> DiagramEdgeLabelPosition.SOURCE
            },
        )
    },
)
