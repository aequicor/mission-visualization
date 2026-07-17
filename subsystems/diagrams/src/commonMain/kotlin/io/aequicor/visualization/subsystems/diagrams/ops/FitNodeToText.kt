package io.aequicor.visualization.subsystems.diagrams.ops

import io.aequicor.visualization.subsystems.diagrams.geometry.boundsForLabel
import io.aequicor.visualization.subsystems.diagrams.geometry.labelPadding
import io.aequicor.visualization.subsystems.diagrams.geometry.perimeterKind
import io.aequicor.visualization.subsystems.diagrams.model.DiagramGraph
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNode
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodeId
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodePayload
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodeSizing
import io.aequicor.visualization.subsystems.diagrams.model.TableNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlActorNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlClassNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlLifelineNode
import io.aequicor.visualization.subsystems.diagrams.model.updateNode
import io.aequicor.visualization.subsystems.diagrams.text.DiagramTextMeasurer
import io.aequicor.visualization.subsystems.diagrams.text.DiagramTextStyle

/**
 * Resizes [id] to fit its caption, draw.io's `getPreferredSizeForCell` semantics:
 *
 * 1. measure the caption **unwrapped**, so the shape grows sideways instead of wrapping;
 * 2. invert the label box for the node's perimeter family ([boundsForLabel]) — an ellipse
 *    needs `√2` more than the text it must contain;
 * 3. recompute x/y **from the alignment anchor**, so a centered shape stays centered rather
 *    than growing rightward off its neighbours (`mxGraph.js:5528`);
 * 4. never shrink below the kind's minimum ([DiagramNodeDefaults]).
 *
 * No-op unless [force] is set or the node opts in with [DiagramNodeSizing.Hug], and always a
 * no-op for payloads that size by content rather than by caption (class/table/lifeline/actor).
 *
 * 🔴 Do NOT reimplement this on top of the renderer's label layout. `drawDiagramLabel` lays out
 * with `exactWidth = true` (min == max constraints), so the width it reports IS the box width:
 * feeding that back here would compute a fixed point on garbage, not a fit. The natural width
 * only ever comes from a `maxWidth = null` measurement, which is what this does.
 */
public fun DiagramGraph.fitNodeToText(
    id: DiagramNodeId,
    measurer: DiagramTextMeasurer,
    fontSize: Double = DiagramTextStyle().fontSize,
    force: Boolean = false,
): DiagramGraph {
    val node = nodeById(id) ?: return this
    if (!force && node.sizing != DiagramNodeSizing.Hug) return this
    if (!node.hugsItsCaption()) return this
    val text = node.primaryText()?.takeIf { it.isNotBlank() } ?: return this

    val measured = measurer.measure(text, DiagramTextStyle(fontSize = fontSize), maxWidth = null)
    val padding = node.labelPadding()
    val fitted = boundsForLabel(node.perimeterKind(), measured.width, measured.height, padding)
    val minimum = DiagramNodeDefaults.minimumSizeFor(node.payload)
    val width = maxOf(fitted.width, minimum.width)
    val height = maxOf(fitted.height, minimum.height)

    // Grow from the center: the anchor for every centered caption this op applies to.
    val centerX = node.x + node.width / 2.0
    val centerY = node.y + node.height / 2.0
    return updateNode(id) {
        it.copy(
            x = centerX - width / 2.0,
            y = centerY - height / 2.0,
            width = width,
            height = height,
        )
    }
}

/**
 * Whether this node's size is driven by a single caption. Payloads sized by their rows
 * (class, ER, table) or by the diagram's own layout (lifeline, actor) are excluded: their
 * height comes from content and their width from the compartment metrics.
 */
private fun DiagramNode.hugsItsCaption(): Boolean = when (payload) {
    is UmlClassNode,
    is DiagramNodePayload.ErEntityNode,
    is TableNode,
    is UmlLifelineNode,
    is UmlActorNode,
    is DiagramNodePayload.ContainerNode,
    is DiagramNodePayload.SwimlaneNode,
    -> false

    else -> true
}
