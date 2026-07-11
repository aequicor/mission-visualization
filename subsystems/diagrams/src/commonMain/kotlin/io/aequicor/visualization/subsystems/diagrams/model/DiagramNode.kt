package io.aequicor.visualization.subsystems.diagrams.model

import io.aequicor.visualization.subsystems.diagrams.path.DiagramPoint
import io.aequicor.visualization.subsystems.diagrams.path.DiagramRect

/** A side of a node's bounding box. */
enum class DiagramNodeSide { TOP, RIGHT, BOTTOM, LEFT }

/**
 * Where a port sits on its node. Both variants are node-relative, so the port
 * (and any fixed edge attached to it) follows the node when it moves or resizes.
 */
sealed interface DiagramPortAnchor {

    /** On a [side] of the bounding box at a fractional [offset] `0..1` along that side. */
    data class SideOffset(
        val side: DiagramNodeSide,
        val offset: Double = 0.5,
    ) : DiagramPortAnchor {
        init {
            require(offset in 0.0..1.0) { "offset must be in 0..1, got $offset" }
        }
    }

    /**
     * An arbitrary point in normalized node coordinates: `(0,0)` is the top-left of the
     * bounding box, `(1,1)` the bottom-right. Values outside `0..1` place the port
     * outside the box (still following the node).
     */
    data class RelativePoint(
        val x: Double,
        val y: Double,
    ) : DiagramPortAnchor
}

/** A connection point on a node that edges can attach to via [DiagramEndpoint.FixedPort]. */
data class DiagramPort(
    val id: DiagramPortId,
    val anchor: DiagramPortAnchor,
) {
    companion object {
        /** A predefined mid-side port with a conventional id (`top`/`right`/`bottom`/`left`). */
        fun side(side: DiagramNodeSide, offset: Double = 0.5): DiagramPort = DiagramPort(
            id = DiagramPortId(side.name.lowercase()),
            anchor = DiagramPortAnchor.SideOffset(side, offset),
        )

        /** The four predefined mid-side ports (draw.io-style connection points). */
        fun standardPorts(): List<DiagramPort> = DiagramNodeSide.entries.map { side(it) }
    }
}

/**
 * A diagram node: geometry + typed [payload] + connection [ports].
 *
 * @param x left edge in document coordinates (relative to the diagram origin, not the parent).
 * @param rotation clockwise degrees around the node center; `0.0` means unrotated.
 * @param parentId containing node (container/swimlane/group-container semantics).
 * @param layerId owning layer; `null` means the graph's implicit default layer.
 */
data class DiagramNode(
    val id: DiagramNodeId,
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,
    val rotation: Double = 0.0,
    val payload: DiagramNodePayload = DiagramNodePayload.BasicShape(),
    val ports: List<DiagramPort> = emptyList(),
    val style: DiagramStyle = DiagramStyle.Default,
    val labels: List<DiagramLabel> = emptyList(),
    val parentId: DiagramNodeId? = null,
    val layerId: DiagramLayerId? = null,
    val locked: Boolean = false,
    val visible: Boolean = true,
) {
    init {
        require(width >= 0.0) { "width must be >= 0, got $width" }
        require(height >= 0.0) { "height must be >= 0, got $height" }
        require(ports.map { it.id }.toSet().size == ports.size) {
            "duplicate port ids on node ${id.value}"
        }
    }

    /** Axis-aligned bounding box (ignores [rotation]). */
    val bounds: DiagramRect get() = DiagramRect(x, y, width, height)

    fun portById(portId: DiagramPortId): DiagramPort? = ports.firstOrNull { it.id == portId }

    /** Resolves a port anchor to absolute document coordinates (ignores [rotation]). */
    fun portPosition(port: DiagramPort): DiagramPoint = when (val anchor = port.anchor) {
        is DiagramPortAnchor.SideOffset -> when (anchor.side) {
            DiagramNodeSide.TOP -> DiagramPoint(x + width * anchor.offset, y)
            DiagramNodeSide.RIGHT -> DiagramPoint(x + width, y + height * anchor.offset)
            DiagramNodeSide.BOTTOM -> DiagramPoint(x + width * anchor.offset, y + height)
            DiagramNodeSide.LEFT -> DiagramPoint(x, y + height * anchor.offset)
        }

        is DiagramPortAnchor.RelativePoint ->
            DiagramPoint(x + width * anchor.x, y + height * anchor.y)
    }
}
