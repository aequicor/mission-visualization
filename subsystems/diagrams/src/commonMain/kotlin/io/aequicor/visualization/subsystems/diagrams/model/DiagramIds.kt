package io.aequicor.visualization.subsystems.diagrams.model

import kotlin.jvm.JvmInline

/** Stable identity of a diagram node. */
@JvmInline
value class DiagramNodeId(val value: String) {
    override fun toString(): String = value
}

/** Stable identity of a diagram edge (connector). */
@JvmInline
value class DiagramEdgeId(val value: String) {
    override fun toString(): String = value
}

/** Stable identity of a connection port on a node. */
@JvmInline
value class DiagramPortId(val value: String) {
    override fun toString(): String = value
}

/** Stable identity of a layer. */
@JvmInline
value class DiagramLayerId(val value: String) {
    override fun toString(): String = value
}

/** Stable identity of a group. */
@JvmInline
value class DiagramGroupId(val value: String) {
    override fun toString(): String = value
}
