package io.aequicor.visualization.engine.backend.compose

import io.aequicor.visualization.engine.ir.layout.LayoutBox
import io.aequicor.visualization.engine.ir.model.TextLink
import io.aequicor.visualization.subsystems.typography.compose.ComposeTypographyMeasurer

/**
 * The hyperlink under a document point on a text node, or null. Lays the node's text out
 * with the same measurer/vertical-align offset as the draw path, then hit-tests the point
 * (node-local) against the subsystem's link rects.
 */
fun linkAtPoint(
    box: LayoutBox,
    docX: Double,
    docY: Double,
    typography: ComposeTypographyMeasurer,
): TextLink? {
    val text = box.node.text ?: return null
    if (text.links.isEmpty()) return null
    val (laid, yOffset) = textEditGeometry(box, typography) ?: return null
    val hit = laid.linkAt(docX - box.x, docY - box.y - yOffset) ?: return null
    return text.links.firstOrNull { it.start == hit.start && it.end == hit.end }
        ?: TextLink(hit.start, hit.end, hit.url, hit.nodeTarget)
}

/** Finds a link on the deepest text node at (docX, docY) in the laid-out tree. */
fun linkAtInTree(
    root: LayoutBox,
    docX: Double,
    docY: Double,
    typography: ComposeTypographyMeasurer,
): TextLink? {
    val hit = root.hitTest(docX, docY) ?: return null
    return linkAtPoint(hit, docX, docY, typography)
}
