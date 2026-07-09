package io.aequicor.visualization.subsystems.anchoring

// --- Move-drag center anchor lines ---------------------------------------------

/** Center anchor lines through [box]'s center, extended to [parent]'s edges (design-book §18). */
data class CenterAnchorLines(val horizontal: SnapLine, val vertical: SnapLine)

fun centerAnchorLines(box: SnapBox, parent: SnapBox): CenterAnchorLines =
    CenterAnchorLines(
        horizontal = SnapLine(parent.x, box.centerY, parent.right, box.centerY),
        vertical = SnapLine(box.centerX, parent.y, box.centerX, parent.bottom),
    )
