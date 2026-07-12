package io.aequicor.visualization.subsystems.diagrams.model

/** UML member visibility with its notation symbol. */
enum class UmlVisibility(val symbol: Char) {
    PUBLIC('+'),
    PRIVATE('-'),
    PROTECTED('#'),
    PACKAGE('~'),
}

/**
 * One attribute or operation row of a [UmlClassNode].
 *
 * @param text the signature as authored, e.g. `name: String` or `move(dx: Int, dy: Int)`.
 */
data class UmlMember(
    val text: String,
    val visibility: UmlVisibility = UmlVisibility.PUBLIC,
    val static: Boolean = false,
    val abstract: Boolean = false,
)

/** UML class/interface box with attribute and operation compartments. */
data class UmlClassNode(
    val name: String,
    val stereotype: String? = null,
    val abstract: Boolean = false,
    val attributes: List<UmlMember> = emptyList(),
    val operations: List<UmlMember> = emptyList(),
) : DiagramNodePayload

/**
 * Sequence-diagram lifeline (head + dashed life line).
 *
 * @param actor render the head as a stick figure instead of a box.
 * @param activations execution occurrences along the line.
 */
data class UmlLifelineNode(
    val name: String,
    val actor: Boolean = false,
    val activations: List<UmlActivation> = emptyList(),
) : DiagramNodePayload

/**
 * Head-box height of a lifeline whose bounds are [height] tall. Shared by the sequence
 * layout ([io.aequicor.visualization.subsystems.diagrams.routing]) and the compose
 * renderer so message rows start exactly below the head.
 */
fun umlLifelineHeadHeight(height: Double): Double = minOf(36.0, height * 0.25)

/**
 * Top y of a lifeline's dashed life line (where messages may start), given the head box
 * top [top] and the lifeline [height]. Actor heads reserve an extra label row below the
 * stick figure, matching the renderer.
 */
fun UmlLifelineNode.lifelineTop(top: Double, height: Double): Double =
    top + umlLifelineHeadHeight(height) + if (actor) 16.0 else 0.0

/**
 * An activation bar on a lifeline; [start] and [end] are normalized `0..1`
 * positions along the lifeline (0 = head, 1 = bottom).
 */
data class UmlActivation(
    val start: Double,
    val end: Double,
) {
    init {
        require(start in 0.0..1.0 && end in 0.0..1.0 && start <= end) {
            "activation must satisfy 0 <= start <= end <= 1, got $start..$end"
        }
    }
}

/** Kinds of state-diagram nodes. */
enum class UmlStateKind { SIMPLE, INITIAL, FINAL, COMPOSITE }

/** State-machine state; [UmlStateKind.COMPOSITE] states contain children via `parentId`. */
data class UmlStateNode(
    val name: String = "",
    val kind: UmlStateKind = UmlStateKind.SIMPLE,
) : DiagramNodePayload

/** Kinds of activity-diagram nodes. */
enum class UmlActivityKind { ACTION, DECISION, FORK, JOIN, START, END }

/** Activity-diagram node. */
data class UmlActivityNode(
    val kind: UmlActivityKind,
    val name: String = "",
) : DiagramNodePayload

/** Use-case actor (stick figure). */
data class UmlActorNode(
    val name: String,
) : DiagramNodePayload

/** Use-case ellipse. */
data class UmlUseCaseNode(
    val name: String,
) : DiagramNodePayload

/** Component-diagram component box. */
data class UmlComponentNode(
    val name: String,
    val stereotype: String? = null,
) : DiagramNodePayload

/** Deployment-diagram node (3D box); may contain children via `parentId`. */
data class UmlDeploymentNode(
    val name: String,
    val stereotype: String? = null,
) : DiagramNodePayload

/** UML note (dog-eared rectangle). */
data class UmlNoteNode(
    val text: String,
) : DiagramNodePayload

/** UML package (tabbed folder); may contain children via `parentId`. */
data class UmlPackageNode(
    val name: String,
) : DiagramNodePayload
