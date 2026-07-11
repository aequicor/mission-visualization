package io.aequicor.visualization.subsystems.diagrams.model

/**
 * Typed semantic payload of a [DiagramNode]. The payload decides which shape family the
 * node renders as and which structured content (table cells, UML members, lanes, ...)
 * it carries. Geometry (x/y/w/h) stays on the node itself.
 *
 * Subclasses live in this file plus [TableNode] (`TableNode.kt`) and the UML set
 * (`UmlNodes.kt`) — all in the same package, as required for a sealed hierarchy.
 */
sealed interface DiagramNodePayload {

    /** Primitive geometric shape; the node's [DiagramNode.labels] carry its text. */
    data class BasicShape(
        val shape: DiagramShapeKind = DiagramShapeKind.RECTANGLE,
    ) : DiagramNodePayload

    /**
     * A container that owns children (nodes whose [DiagramNode.parentId] points here).
     * Resizing/moving semantics (drag-in/out, resize pulls content) are editor concerns.
     */
    data class ContainerNode(
        val title: DiagramLabel? = null,
        val collapsed: Boolean = false,
    ) : DiagramNodePayload

    /** A pool of parallel [lanes]; children are assigned to lanes by geometry. */
    data class SwimlaneNode(
        val orientation: DiagramOrientation = DiagramOrientation.HORIZONTAL,
        val lanes: List<SwimlaneLane> = emptyList(),
        val title: DiagramLabel? = null,
    ) : DiagramNodePayload

    /** Classic flowchart primitive. */
    data class FlowchartNode(
        val kind: FlowchartNodeKind,
    ) : DiagramNodePayload

    /** Entity-relationship entity with typed attribute rows. */
    data class ErEntityNode(
        val name: String,
        val attributes: List<ErAttribute> = emptyList(),
    ) : DiagramNodePayload

    /** BPMN primitive (v1 granularity: task / event / gateway). */
    data class BpmnNode(
        val kind: BpmnNodeKind,
    ) : DiagramNodePayload
}

/** Shape vocabulary for [DiagramNodePayload.BasicShape]. */
enum class DiagramShapeKind {
    RECTANGLE,
    ROUNDED_RECTANGLE,
    ELLIPSE,

    /** Borderless text-only node. */
    TEXT,
    RHOMBUS,
    TRIANGLE,
    HEXAGON,
    PARALLELOGRAM,
    TRAPEZOID,
    CYLINDER,
    CLOUD,
}

/** Orientation of swimlane pools. */
enum class DiagramOrientation { HORIZONTAL, VERTICAL }

/**
 * One lane inside a [DiagramNodePayload.SwimlaneNode].
 *
 * @param size lane thickness in document units, along the axis perpendicular
 *   to the pool's orientation.
 */
data class SwimlaneLane(
    val title: DiagramLabel? = null,
    val size: Double = 120.0,
) {
    init {
        require(size >= 0.0) { "lane size must be >= 0, got $size" }
    }
}

/** Flowchart primitive kinds. */
enum class FlowchartNodeKind { PROCESS, DECISION, INPUT_OUTPUT, TERMINATOR }

/** An attribute row of an [DiagramNodePayload.ErEntityNode]. */
data class ErAttribute(
    val name: String,
    val type: String? = null,
    val primaryKey: Boolean = false,
    val foreignKey: Boolean = false,
)

/** BPMN primitive kinds. */
enum class BpmnNodeKind { TASK, EVENT, GATEWAY }
