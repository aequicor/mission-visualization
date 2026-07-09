package io.aequicor.visualization.editor.presentation

import io.aequicor.visualization.subsystems.anchoring.MovingEdges
import io.aequicor.visualization.subsystems.anchoring.SnapBox
import io.aequicor.visualization.subsystems.anchoring.SnapLine

/**
 * Bridges the editor's presentation geometry ([BoundsBox]/[LineSegment]) onto the anchoring
 * subsystem's own value types. These are the only cross-module conversions — the engine's
 * result types ([io.aequicor.visualization.subsystems.anchoring.AnchorResult] etc.) flow back
 * through the UI unchanged.
 */
fun BoundsBox.toSnapBox(): SnapBox = SnapBox(x = x, y = y, width = width, height = height)

fun SnapBox.toBoundsBox(): BoundsBox = BoundsBox(x = x, y = y, width = width, height = height)

fun SnapLine.toLineSegment(): LineSegment = LineSegment(x1, y1, x2, y2)

/** Which edges a [ResizeHandle] drags, for the resize-snap engine. */
fun ResizeHandle.toMovingEdges(): MovingEdges =
    MovingEdges(left = movesLeft, right = movesRight, top = movesTop, bottom = movesBottom)
