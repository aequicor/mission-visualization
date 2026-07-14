package io.aequicor.visualization.engine.backend.compose

import io.aequicor.visualization.engine.ir.layout.LayoutBox
import io.aequicor.visualization.engine.ir.model.TextAlignVertical
import io.aequicor.visualization.subsystems.typography.compose.ComposeTypographyMeasurer
import io.aequicor.visualization.subsystems.typography.compose.LaidOutRichText

/**
 * The laid-out text of a node plus the vertical offset applied by its vertical alignment.
 *
 * Offsets inside [laid] (caret rects, selection geometry, line metrics) are in node-local
 * text-box space: add [LayoutBox.x] and ([LayoutBox.y] + [yOffset]) to reach document coords.
 */
data class TextEditGeometry(
    val laid: LaidOutRichText,
    val yOffset: Double,
)

/**
 * Lays out [box]'s text with the same measurer and vertical-align offset as the draw and
 * hit-test paths, so caret/selection overlays share one geometry seam and cannot drift.
 * Returns null when the node has no text ([LayoutBox.node].text == null).
 */
fun textEditGeometry(
    box: LayoutBox,
    typography: ComposeTypographyMeasurer,
): TextEditGeometry? {
    val text = box.node.text ?: return null
    val laid = typography.layout(
        rich = text.toRichText(),
        maxWidth = box.width,
        fill = box.node.fills.toRichTextFill(),
        exactWidth = true,
    )
    val yOffset = when (text.style.textAlignVertical) {
        TextAlignVertical.Top -> 0.0
        TextAlignVertical.Center -> (box.height - laid.measured.height) / 2.0
        TextAlignVertical.Bottom -> box.height - laid.measured.height
    }
    return TextEditGeometry(laid, yOffset)
}
