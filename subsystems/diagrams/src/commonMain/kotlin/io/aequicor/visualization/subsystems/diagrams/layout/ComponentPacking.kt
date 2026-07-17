package io.aequicor.visualization.subsystems.diagrams.layout

import io.aequicor.visualization.subsystems.diagrams.path.DiagramPoint
import kotlin.math.sqrt

/**
 * One laid-out connected component ready for packing: local positions (top-left corners
 * starting at `(0,0)`) plus the block extents along the flow (main) and cross axes.
 */
internal class ComponentBlock<K>(
    val positions: Map<K, DiagramPoint>,
    val mainExtent: Double,
    val crossExtent: Double,
)

/**
 * Shelf-packing of component blocks: blocks flow across the cross axis and wrap into a
 * new band when a row would outgrow the largest block (or a near-square target), so a
 * main flow plus strays reads as one tidy block instead of a single long strip.
 * Blocks are placed in list order — pass them largest first for stable, compact rows.
 */
internal fun <K> packComponentBlocks(
    blocks: List<ComponentBlock<K>>,
    config: DiagramLayoutConfig,
): Map<K, DiagramPoint> {
    val horizontalFlow = config.direction == LayoutDirection.LEFT_RIGHT
    val gap = config.nodeGap * 2.0
    val totalArea = blocks.sumOf { (it.mainExtent + gap) * (it.crossExtent + gap) }
    val rowLimit = maxOf(blocks.maxOf { it.crossExtent }, sqrt(totalArea) * 1.5)
    val result = mutableMapOf<K, DiagramPoint>()
    var mainOffset = 0.0
    var rowCross = 0.0
    var rowMainMax = 0.0
    for (block in blocks) {
        if (rowCross > 0.0 && rowCross + block.crossExtent > rowLimit) {
            mainOffset += rowMainMax + config.layerGap
            rowCross = 0.0
            rowMainMax = 0.0
        }
        for ((id, point) in block.positions) {
            result[id] = if (horizontalFlow) {
                DiagramPoint(x = mainOffset + point.x, y = rowCross + point.y)
            } else {
                DiagramPoint(x = rowCross + point.x, y = mainOffset + point.y)
            }
        }
        rowCross += block.crossExtent + gap
        rowMainMax = maxOf(rowMainMax, block.mainExtent)
    }
    return result
}
