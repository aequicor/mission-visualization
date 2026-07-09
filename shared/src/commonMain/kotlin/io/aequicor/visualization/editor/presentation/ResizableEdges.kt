package io.aequicor.visualization.editor.presentation

import io.aequicor.visualization.engine.ir.model.AlignItems
import io.aequicor.visualization.engine.ir.model.DesignDocument
import io.aequicor.visualization.engine.ir.model.DesignNode
import io.aequicor.visualization.engine.ir.model.DesignSizing
import io.aequicor.visualization.engine.ir.model.LayoutMode
import io.aequicor.visualization.engine.ir.model.SizingMode

/**
 * Which of a node's four edges can actually move under the current layout, mirroring the pure
 * [io.aequicor.visualization.engine.ir.layout] flow rules so a canvas resize handle never grows
 * the box in a direction the layout will refuse.
 *
 * The bug this guards: a child of an auto-layout container is placed by the flow's running
 * cursor, which ignores its authored `position`. Dragging the flow-pinned start edge (the left
 * edge in a `Horizontal` row, the top edge in a `Vertical` column) can only shove the *opposite*
 * edge — so it must be inert, not silently grow the other way.
 */
data class ResizableEdges(
    val left: Boolean,
    val right: Boolean,
    val top: Boolean,
    val bottom: Boolean,
) {
    companion object {
        /** Freely-positioned node: every edge moves. */
        val All = ResizableEdges(left = true, right = true, top = true, bottom = true)
    }
}

/**
 * Edges of [nodeId] that a resize handle may move. Coordinate-positioned nodes (free/absolute/root)
 * move on all four; flow children pin their main-axis start edge and follow their cross-axis
 * alignment/sizing (see [crossEdges]). Purely IR-derived — no resolve layer, no Compose.
 */
internal fun DesignDocument.resizableEdges(nodeId: String): ResizableEdges {
    if (isCoordinatePositioned(nodeId)) return ResizableEdges.All
    val node = nodeById(nodeId) ?: return ResizableEdges.All
    val parent = parentNodeOf(nodeId) ?: return ResizableEdges.All
    return when (parent.layout.mode) {
        LayoutMode.Horizontal -> {
            // Main axis = horizontal: left is flow-pinned, right can grow. Cross axis = vertical.
            val (top, bottom) = crossEdges(node, parent, vertical = true)
            ResizableEdges(left = false, right = true, top = top, bottom = bottom)
        }
        LayoutMode.Vertical -> {
            // Main axis = vertical: top is flow-pinned, bottom can grow. Cross axis = horizontal.
            val (left, right) = crossEdges(node, parent, vertical = false)
            ResizableEdges(left = left, right = right, top = false, bottom = true)
        }
        // Grid cell origin is track/placement-controlled (like a flow start); the span/end can
        // still grow by converting to a fixed size. A precise per-axis grid-stretch rule is a
        // separate task.
        LayoutMode.Grid -> ResizableEdges(left = false, right = true, top = false, bottom = true)
        // A `None` parent means the child is coordinate-positioned; handled above, but kept total.
        LayoutMode.None -> ResizableEdges.All
    }
}

/**
 * Cross-axis movability for a flow child as `startMovable to endMovable` (start = top/left,
 * end = bottom/right). Mirrors the engine: a child whose cross sizing is `Fill` or whose effective
 * align is `Stretch` is sized to the line (both edges pinned); otherwise `alignmentOffset` decides —
 * `Start`/`Baseline` pin the start edge, `End` pins the end edge, `Center` keeps both movable
 * (the box grows symmetrically about its center).
 */
private fun crossEdges(node: DesignNode, parent: DesignNode, vertical: Boolean): Pair<Boolean, Boolean> {
    val sizing = node.sizing ?: DesignSizing()
    val crossSizing = if (vertical) sizing.vertical else sizing.horizontal
    val align = node.layoutChild.alignSelf ?: parent.layout.alignItems
    if (crossSizing == SizingMode.Fill || align == AlignItems.Stretch) return false to false
    return when (align) {
        AlignItems.Center -> true to true
        AlignItems.End -> true to false
        else -> false to true // Start / Baseline
    }
}
