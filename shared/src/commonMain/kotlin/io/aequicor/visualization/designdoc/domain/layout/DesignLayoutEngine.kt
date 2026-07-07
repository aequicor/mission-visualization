package io.aequicor.visualization.designdoc.domain.layout

import io.aequicor.visualization.designdoc.domain.model.AlignItems
import io.aequicor.visualization.designdoc.domain.model.GridTrack
import io.aequicor.visualization.designdoc.domain.model.HorizontalConstraint
import io.aequicor.visualization.designdoc.domain.model.JustifyContent
import io.aequicor.visualization.designdoc.domain.model.LayoutMode
import io.aequicor.visualization.designdoc.domain.model.SizingMode
import io.aequicor.visualization.designdoc.domain.model.TextAutoResize
import io.aequicor.visualization.designdoc.domain.model.VerticalConstraint
import io.aequicor.visualization.designdoc.domain.resolve.ResolvedNode
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * A laid-out node: absolute rect in root-frame coordinates plus laid-out children.
 */
data class LayoutBox(
    val node: ResolvedNode,
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,
    val children: List<LayoutBox> = emptyList(),
) {
    val right: Double get() = x + width
    val bottom: Double get() = y + height

    fun findById(id: String): LayoutBox? {
        if (node.id == id) return this
        return children.firstNotNullOfOrNull { it.findById(id) }
    }

    fun findBySourceId(sourceId: String): LayoutBox? {
        if (node.sourceId == sourceId) return this
        return children.firstNotNullOfOrNull { it.findBySourceId(sourceId) }
    }

    /**
     * Point-in-node test in the parent's coordinate space. Rotated nodes are drawn
     * rotated about their center, so the point is inverse-rotated before testing.
     */
    fun hitTest(px: Double, py: Double): LayoutBox? {
        val rotation = node.rotation
        val (lx, ly) = if (rotation != 0.0) {
            val radians = -rotation * PI / 180.0
            val cx = x + width / 2.0
            val cy = y + height / 2.0
            val dx = px - cx
            val dy = py - cy
            (cx + dx * cos(radians) - dy * sin(radians)) to (cy + dx * sin(radians) + dy * cos(radians))
        } else {
            px to py
        }
        if (lx < x || ly < y || lx > right || ly > bottom) return null
        for (child in children.asReversed()) {
            child.hitTest(lx, ly)?.let { return it }
        }
        return this
    }

    fun allBoxes(): List<LayoutBox> = listOf(this) + children.flatMap { it.allBoxes() }
}

/**
 * Pure-Kotlin layout solver with Figma semantics: `fixed | hug | fill` sizing with
 * min/max clamps, auto-layout rows/columns (fixed and `auto` gaps, padding, align,
 * justify, wrap), grid tracks with explicit placement, absolute positioning with
 * constraints, and children pulled out of flow via `layoutChild.absolute`.
 */
class DesignLayoutEngine(
    private val textMeasurer: DesignTextMeasurer = ApproximateTextMeasurer(),
) {

    /**
     * Lays out one top-level frame. [overrideWidth]/[overrideHeight] replace the
     * authored frame size (e.g. device presets); constraints and fills adapt.
     */
    fun layout(
        root: ResolvedNode,
        overrideWidth: Double? = null,
        overrideHeight: Double? = null,
    ): LayoutBox {
        val width = clampWidth(root, overrideWidth ?: naturalWidth(root))
        val heightBase = overrideHeight
            ?: if (root.sizing.vertical == SizingMode.Hug) {
                naturalHeight(root, width)
            } else {
                root.size.height ?: naturalHeight(root, width)
            }
        return place(root, 0.0, 0.0, width, clampHeight(root, heightBase))
    }

    // --- Natural (hug/fixed) sizes -----------------------------------------

    /** Width the node wants when nothing forces it: fixed size or hugged content. */
    fun naturalWidth(node: ResolvedNode): Double {
        val fixed = node.size.width
        val width = when {
            node.sizing.horizontal == SizingMode.Fixed && fixed != null -> fixed
            node.text != null -> textNaturalWidth(node)
            node.children.isEmpty() -> fixed ?: 0.0
            else -> when (node.layout.mode) {
                LayoutMode.Horizontal -> {
                    val flow = flowChildren(node)
                    node.layout.paddingLeft + node.layout.paddingRight +
                        flow.sumOf { naturalWidth(it) } +
                        fixedGapTotal(node, flow.size)
                }
                LayoutMode.Vertical -> {
                    val flow = flowChildren(node)
                    node.layout.paddingLeft + node.layout.paddingRight +
                        (flow.maxOfOrNull { naturalWidth(it) } ?: 0.0)
                }
                LayoutMode.Grid -> {
                    val columns = gridColumns(node)
                    val cells = assignGridCells(node, columns.size)
                    val columnWidths = resolveTracks(
                        tracks = columns,
                        available = null,
                        gap = node.layout.columnGap,
                    ) { columnIndex -> gridColumnHugWidth(cells, columnIndex) }
                    node.layout.paddingLeft + node.layout.paddingRight + columnWidths.sum() +
                        node.layout.columnGap * (columns.size - 1).coerceAtLeast(0)
                }
                LayoutMode.None -> fixed ?: (node.children.maxOfOrNull { child ->
                    (child.position?.x ?: 0.0) + naturalWidth(child)
                } ?: 0.0)
            }
        }
        return clampWidth(node, width)
    }

    /** Height the node wants given a final [width]. */
    fun naturalHeight(node: ResolvedNode, width: Double): Double {
        val fixed = node.size.height
        val height = when {
            node.sizing.vertical == SizingMode.Fixed && fixed != null -> fixed
            node.text != null -> textNaturalHeight(node, width)
            node.children.isEmpty() -> fixed ?: 0.0
            else -> when (node.layout.mode) {
                LayoutMode.Horizontal, LayoutMode.Vertical, LayoutMode.Grid ->
                    layoutChildren(node, width, null).contentHeight
                LayoutMode.None -> fixed ?: (node.children.maxOfOrNull { child ->
                    (child.position?.y ?: 0.0) + naturalHeight(child, naturalWidth(child))
                } ?: 0.0)
            }
        }
        return clampHeight(node, height)
    }

    private fun textNaturalWidth(node: ResolvedNode): Double {
        val text = requireNotNull(node.text)
        return when (text.autoResize) {
            TextAutoResize.WidthAndHeight -> textMeasurer.measure(text, null).width
            else -> node.size.width ?: textMeasurer.measure(text, null).width
        }
    }

    private fun textNaturalHeight(node: ResolvedNode, width: Double): Double {
        val text = requireNotNull(node.text)
        return when (text.autoResize) {
            TextAutoResize.None -> node.size.height ?: textMeasurer.measure(text, width).height
            else -> textMeasurer.measure(text, width).height
        }
    }

    // --- Placement ----------------------------------------------------------

    private fun place(node: ResolvedNode, x: Double, y: Double, width: Double, height: Double): LayoutBox {
        val children = when {
            node.children.isEmpty() -> emptyList()
            node.layout.mode == LayoutMode.None -> placeConstrained(node, x, y, width, height, node.children)
            else -> {
                val flow = layoutChildren(node, width, height).boxes.map { placed ->
                    place(placed.child, x + placed.x, y + placed.y, placed.width, placed.height)
                }
                val absolute = placeConstrained(
                    node, x, y, width, height,
                    node.children.filter { it.layoutChild.absolute },
                )
                orderBySource(node, flow + absolute)
            }
        }
        return LayoutBox(node, x, y, width, height, children)
    }

    /** Keeps paint order equal to the authored children order. */
    private fun orderBySource(node: ResolvedNode, boxes: List<LayoutBox>): List<LayoutBox> {
        val order = node.children.withIndex().associate { (index, child) -> child.id to index }
        return boxes.sortedBy { order[it.node.id] ?: Int.MAX_VALUE }
    }

    /** Absolute + constraints placement (canvas mode and out-of-flow children). */
    private fun placeConstrained(
        parent: ResolvedNode,
        parentX: Double,
        parentY: Double,
        parentWidth: Double,
        parentHeight: Double,
        children: List<ResolvedNode>,
    ): List<LayoutBox> {
        val authoredWidth = parent.size.width ?: parentWidth
        val authoredHeight = parent.size.height ?: parentHeight
        return children.map { child ->
            val childAuthoredWidth = when (child.sizing.horizontal) {
                SizingMode.Fill -> authoredWidth
                else -> naturalWidth(child)
            }
            val (cx, cw) = remapHorizontal(
                child.constraints.horizontal,
                child.position?.x ?: 0.0,
                childAuthoredWidth,
                authoredWidth,
                parentWidth,
            )
            val childAuthoredHeight = when (child.sizing.vertical) {
                SizingMode.Fill -> authoredHeight
                else -> naturalHeight(child, cw)
            }
            val (cy, ch) = remapVertical(
                child.constraints.vertical,
                child.position?.y ?: 0.0,
                childAuthoredHeight,
                authoredHeight,
                parentHeight,
            )
            place(
                child,
                parentX + cx,
                parentY + cy,
                clampWidth(child, cw),
                clampHeight(child, ch),
            )
        }
    }

    private fun remapHorizontal(
        constraint: HorizontalConstraint,
        x: Double,
        width: Double,
        authoredParent: Double,
        actualParent: Double,
    ): Pair<Double, Double> {
        val delta = actualParent - authoredParent
        return when (constraint) {
            HorizontalConstraint.Left -> x to width
            HorizontalConstraint.Right -> (x + delta) to width
            HorizontalConstraint.Center -> (x + delta / 2.0) to width
            HorizontalConstraint.LeftRight -> x to (width + delta).coerceAtLeast(0.0)
            HorizontalConstraint.Scale ->
                if (authoredParent > 0.0) {
                    val scale = actualParent / authoredParent
                    (x * scale) to (width * scale)
                } else {
                    x to width
                }
        }
    }

    private fun remapVertical(
        constraint: VerticalConstraint,
        y: Double,
        height: Double,
        authoredParent: Double,
        actualParent: Double,
    ): Pair<Double, Double> {
        val delta = actualParent - authoredParent
        return when (constraint) {
            VerticalConstraint.Top -> y to height
            VerticalConstraint.Bottom -> (y + delta) to height
            VerticalConstraint.Center -> (y + delta / 2.0) to height
            VerticalConstraint.TopBottom -> y to (height + delta).coerceAtLeast(0.0)
            VerticalConstraint.Scale ->
                if (authoredParent > 0.0) {
                    val scale = actualParent / authoredParent
                    (y * scale) to (height * scale)
                } else {
                    y to height
                }
        }
    }

    // --- Auto-layout flow -----------------------------------------------------

    private data class PlacedChild(
        val child: ResolvedNode,
        val x: Double,
        val y: Double,
        val width: Double,
        val height: Double,
    )

    private data class FlowResult(
        val boxes: List<PlacedChild>,
        val contentHeight: Double,
    )

    private fun flowChildren(node: ResolvedNode): List<ResolvedNode> =
        node.children.filterNot { it.layoutChild.absolute }

    private fun fixedGapTotal(node: ResolvedNode, childCount: Int): Double {
        if (childCount <= 1) return 0.0
        return if (node.layout.gapAuto) 0.0 else node.layout.gap * (childCount - 1)
    }

    /**
     * Lays out in-flow children inside the content box of [node]. [height] may be
     * null while hugging; [FlowResult.contentHeight] then reports the hugged height.
     */
    private fun layoutChildren(node: ResolvedNode, width: Double, height: Double?): FlowResult =
        when (node.layout.mode) {
            LayoutMode.Horizontal -> horizontalFlow(node, width, height)
            LayoutMode.Vertical -> verticalFlow(node, width, height)
            LayoutMode.Grid -> gridLayout(node, width, height)
            LayoutMode.None -> FlowResult(emptyList(), height ?: 0.0)
        }

    private fun effectiveAlign(alignItems: AlignItems, child: ResolvedNode): AlignItems =
        child.layoutChild.alignSelf ?: alignItems

    private fun alignmentOffset(align: AlignItems, lineCross: Double, childCross: Double): Double =
        when (align) {
            AlignItems.Center, AlignItems.Baseline -> (lineCross - childCross) / 2.0
            AlignItems.End -> lineCross - childCross
            else -> 0.0
        }

    /**
     * Distributes main-axis leftover space. Returns start offset and effective gap.
     * `gap: "auto"` and `justifyContent: spaceBetween` push items apart; fills
     * consume the leftover instead.
     */
    private fun mainDistribution(
        node: ResolvedNode,
        itemCount: Int,
        itemsTotal: Double,
        mainAvail: Double,
        hasFills: Boolean,
    ): Pair<Double, Double> {
        val layout = node.layout
        val fixedGap = if (layout.gapAuto) 0.0 else layout.gap
        val gapCount = (itemCount - 1).coerceAtLeast(0)
        val leftover = (mainAvail - itemsTotal - fixedGap * gapCount).coerceAtLeast(0.0)
        val spaceBetween = layout.gapAuto || layout.justifyContent == JustifyContent.SpaceBetween
        return when {
            hasFills -> 0.0 to fixedGap
            spaceBetween && gapCount > 0 -> 0.0 to (fixedGap + leftover / gapCount)
            layout.justifyContent == JustifyContent.Center -> (leftover / 2.0) to fixedGap
            layout.justifyContent == JustifyContent.End -> leftover to fixedGap
            else -> 0.0 to fixedGap
        }
    }

    private fun horizontalFlow(node: ResolvedNode, width: Double, height: Double?): FlowResult {
        val layout = node.layout
        val children = flowChildren(node)
        if (children.isEmpty()) {
            return FlowResult(emptyList(), layout.paddingTop + layout.paddingBottom)
        }
        val mainAvail = (width - layout.paddingLeft - layout.paddingRight).coerceAtLeast(0.0)

        // 1. Main axis (widths): fixed/hug first, fills share the remainder.
        val widths = MutableList(children.size) { 0.0 }
        val fillIndices = mutableListOf<Int>()
        children.forEachIndexed { index, child ->
            if (child.sizing.horizontal == SizingMode.Fill) {
                fillIndices += index
            } else {
                widths[index] = clampWidth(child, naturalWidth(child))
            }
        }
        if (fillIndices.isNotEmpty()) {
            val used = widths.sum() + fixedGapTotal(node, children.size)
            distributeFillSpace(mainAvail - used, fillIndices, widths) { index, size ->
                clampWidth(children[index], size)
            }
        }

        // 2. Wrap into lines.
        val lines: List<List<Int>> = if (layout.wrap) {
            wrapIntoLines(children.indices.toList(), widths, if (layout.gapAuto) 0.0 else layout.gap, mainAvail)
        } else {
            listOf(children.indices.toList())
        }

        // 3. Cross axis (heights) measured at final widths; stretch/fill follow the
        // line, whose size is driven by every child's natural height (so a hugging
        // container with only stretching children still wraps their content).
        val heights = MutableList(children.size) { 0.0 }
        val lineCross = MutableList(lines.size) { 0.0 }
        val bounded = height != null
        lines.forEachIndexed { lineIndex, line ->
            line.forEach { index ->
                val child = children[index]
                val stretches = child.sizing.vertical == SizingMode.Fill ||
                    effectiveAlign(layout.alignItems, child) == AlignItems.Stretch
                val natural = clampHeight(child, naturalHeight(child, widths[index]))
                if (!stretches) {
                    heights[index] = natural
                    lineCross[lineIndex] = maxOf(lineCross[lineIndex], natural)
                } else if (!bounded || lines.size > 1) {
                    lineCross[lineIndex] = maxOf(lineCross[lineIndex], natural)
                }
            }
        }
        if (lines.size == 1 && height != null) {
            val crossAvail = (height - layout.paddingTop - layout.paddingBottom).coerceAtLeast(0.0)
            lineCross[0] = maxOf(lineCross[0], crossAvail)
        }
        lines.forEachIndexed { lineIndex, line ->
            line.forEach { index ->
                val child = children[index]
                val stretches = child.sizing.vertical == SizingMode.Fill ||
                    effectiveAlign(layout.alignItems, child) == AlignItems.Stretch
                if (stretches) {
                    heights[index] = clampHeight(child, lineCross[lineIndex])
                }
            }
        }

        // 4. Place line by line.
        val placed = mutableListOf<PlacedChild>()
        var lineY = layout.paddingTop
        lines.forEachIndexed { lineIndex, line ->
            val itemsTotal = line.sumOf { widths[it] }
            val (startOffset, gap) = mainDistribution(
                node = node,
                itemCount = line.size,
                itemsTotal = itemsTotal,
                mainAvail = mainAvail,
                hasFills = fillIndices.isNotEmpty(),
            )
            var xCursor = layout.paddingLeft + startOffset
            line.forEach { index ->
                val child = children[index]
                val yOffset = alignmentOffset(effectiveAlign(layout.alignItems, child), lineCross[lineIndex], heights[index])
                placed += PlacedChild(child, xCursor, lineY + yOffset, widths[index], heights[index])
                xCursor += widths[index] + gap
            }
            lineY += lineCross[lineIndex] + layout.crossGap
        }

        val contentHeight = lineY - layout.crossGap + layout.paddingBottom
        return FlowResult(placed, contentHeight)
    }

    private fun verticalFlow(node: ResolvedNode, width: Double, height: Double?): FlowResult {
        val layout = node.layout
        val children = flowChildren(node)
        if (children.isEmpty()) {
            return FlowResult(emptyList(), layout.paddingTop + layout.paddingBottom)
        }
        val crossAvail = (width - layout.paddingLeft - layout.paddingRight).coerceAtLeast(0.0)

        // 1. Cross axis (widths) first: stretch/fill take the content width.
        val widths = MutableList(children.size) { 0.0 }
        children.forEachIndexed { index, child ->
            val stretches = child.sizing.horizontal == SizingMode.Fill ||
                effectiveAlign(layout.alignItems, child) == AlignItems.Stretch
            widths[index] = if (stretches) {
                clampWidth(child, crossAvail)
            } else {
                clampWidth(child, naturalWidth(child))
            }
        }

        // 2. Main axis (heights) at final widths; fills share leftover when bounded.
        val heights = MutableList(children.size) { 0.0 }
        val fillIndices = mutableListOf<Int>()
        children.forEachIndexed { index, child ->
            if (child.sizing.vertical == SizingMode.Fill && height != null) {
                fillIndices += index
            } else {
                heights[index] = clampHeight(child, naturalHeight(child, widths[index]))
            }
        }
        if (fillIndices.isNotEmpty() && height != null) {
            val mainAvail = (height - layout.paddingTop - layout.paddingBottom).coerceAtLeast(0.0)
            val used = heights.sum() + fixedGapTotal(node, children.size)
            distributeFillSpace(mainAvail - used, fillIndices, heights) { index, size ->
                clampHeight(children[index], size)
            }
        }

        // 3. Place top to bottom.
        val itemsTotal = heights.sum()
        val mainAvail = height?.let { (it - layout.paddingTop - layout.paddingBottom).coerceAtLeast(0.0) }
        val (startOffset, gap) = if (mainAvail != null) {
            mainDistribution(
                node = node,
                itemCount = children.size,
                itemsTotal = itemsTotal,
                mainAvail = mainAvail,
                hasFills = fillIndices.isNotEmpty(),
            )
        } else {
            0.0 to if (layout.gapAuto) 0.0 else layout.gap
        }
        val placed = mutableListOf<PlacedChild>()
        var yCursor = layout.paddingTop + startOffset
        children.forEachIndexed { index, child ->
            val xOffset = alignmentOffset(effectiveAlign(layout.alignItems, child), crossAvail, widths[index])
            placed += PlacedChild(child, layout.paddingLeft + xOffset, yCursor, widths[index], heights[index])
            yCursor += heights[index] + gap
        }

        val contentHeight = layout.paddingTop + itemsTotal +
            (if (layout.gapAuto) 0.0 else layout.gap) * (children.size - 1).coerceAtLeast(0) +
            layout.paddingBottom
        return FlowResult(placed, contentHeight)
    }

    /**
     * Splits [available] equally between fill children, re-distributing whatever a
     * min/max clamp binds so the remaining fills consume the freed (or owed) space.
     */
    private fun distributeFillSpace(
        available: Double,
        fillIndices: List<Int>,
        sizes: MutableList<Double>,
        clamp: (Int, Double) -> Double,
    ) {
        var remaining = available.coerceAtLeast(0.0)
        val active = fillIndices.toMutableList()
        repeat(fillIndices.size) {
            if (active.isEmpty()) return
            val share = (remaining / active.size).coerceAtLeast(0.0)
            val violators = active.filter { index -> clamp(index, share) != share }
            if (violators.isEmpty()) {
                active.forEach { index -> sizes[index] = share }
                return
            }
            // Like CSS flexbox: freeze only violators of the dominant direction, so
            // the space they free (or consume) redistributes to the rest.
            val totalViolation = violators.sumOf { index -> clamp(index, share) - share }
            val frozen = when {
                totalViolation > 0.0 -> violators.filter { index -> clamp(index, share) > share }
                totalViolation < 0.0 -> violators.filter { index -> clamp(index, share) < share }
                else -> violators
            }
            frozen.forEach { index ->
                val value = clamp(index, share)
                sizes[index] = value
                remaining = (remaining - value).coerceAtLeast(0.0)
            }
            active.removeAll(frozen)
        }
        active.forEach { index -> sizes[index] = clamp(index, (remaining / active.size).coerceAtLeast(0.0)) }
    }

    private fun wrapIntoLines(
        indices: List<Int>,
        mainSizes: List<Double>,
        gap: Double,
        mainAvail: Double,
    ): List<List<Int>> {
        val lines = mutableListOf<MutableList<Int>>()
        var current = mutableListOf<Int>()
        var used = 0.0
        indices.forEach { index ->
            val childSize = mainSizes[index]
            val needed = if (current.isEmpty()) childSize else used + gap + childSize
            if (current.isNotEmpty() && needed > mainAvail) {
                lines += current
                current = mutableListOf(index)
                used = childSize
            } else {
                current += index
                used = needed
            }
        }
        if (current.isNotEmpty()) lines += current
        return lines
    }

    // --- Grid ------------------------------------------------------------------

    private fun gridColumns(node: ResolvedNode): List<GridTrack> =
        node.layout.columns.ifEmpty { listOf(GridTrack.Flex(1.0)) }

    private data class GridCell(
        val child: ResolvedNode,
        val column: Int,
        val row: Int,
        val columnSpan: Int,
        val rowSpan: Int,
    )

    /** Explicit gridPlacement wins; the rest auto-place row-major into free cells. */
    private fun assignGridCells(node: ResolvedNode, columnCount: Int): List<GridCell> {
        val children = flowChildren(node)
        val cells = mutableListOf<GridCell>()
        val taken = mutableSetOf<Pair<Int, Int>>()
        children.forEach { child ->
            val placement = child.gridPlacement
            if (placement != null && placement.column >= 1 && placement.row >= 1) {
                val cell = GridCell(
                    child,
                    placement.column - 1,
                    placement.row - 1,
                    placement.columnSpan.coerceAtLeast(1),
                    placement.rowSpan.coerceAtLeast(1),
                )
                cells += cell
                for (column in cell.column until cell.column + cell.columnSpan) {
                    for (row in cell.row until cell.row + cell.rowSpan) {
                        taken += column to row
                    }
                }
            }
        }
        var autoColumn = 0
        var autoRow = 0
        children.forEach { child ->
            val placement = child.gridPlacement
            if (placement == null || placement.column < 1 || placement.row < 1) {
                while ((autoColumn to autoRow) in taken) {
                    autoColumn++
                    if (autoColumn >= columnCount) {
                        autoColumn = 0
                        autoRow++
                    }
                }
                cells += GridCell(child, autoColumn, autoRow, 1, 1)
                taken += autoColumn to autoRow
                autoColumn++
                if (autoColumn >= columnCount) {
                    autoColumn = 0
                    autoRow++
                }
            }
        }
        return cells
    }

    private fun gridColumnHugWidth(cells: List<GridCell>, columnIndex: Int): Double =
        cells.filter { it.column == columnIndex && it.columnSpan == 1 }
            .maxOfOrNull { naturalWidth(it.child) } ?: 0.0

    private fun gridLayout(node: ResolvedNode, width: Double, height: Double?): FlowResult {
        val layout = node.layout
        val columns = gridColumns(node)
        val cells = assignGridCells(node, columns.size)

        val rowCount = (cells.maxOfOrNull { it.row + it.rowSpan } ?: 0).coerceAtLeast(layout.rows.size)
        val rows = List(rowCount) { index -> layout.rows.getOrNull(index) ?: GridTrack.Hug }

        // On a hug axis, flex tracks have no free space to share: size them by content
        // so measurement (available == null) and placement agree.
        val contentWidth = (width - layout.paddingLeft - layout.paddingRight).coerceAtLeast(0.0)
        val columnWidths = resolveTracks(
            tracks = columns,
            available = contentWidth.takeUnless { node.sizing.horizontal == SizingMode.Hug },
            gap = layout.columnGap,
        ) { columnIndex -> gridColumnHugWidth(cells, columnIndex) }

        val rowHeights = resolveTracks(
            tracks = rows,
            available = height
                ?.takeUnless { node.sizing.vertical == SizingMode.Hug }
                ?.let { (it - layout.paddingTop - layout.paddingBottom).coerceAtLeast(0.0) },
            gap = layout.rowGap,
        ) { rowIndex ->
            cells.filter { it.row == rowIndex && it.rowSpan == 1 }
                .maxOfOrNull { cell ->
                    val cellWidth = spanSize(columnWidths, cell.column, cell.columnSpan, layout.columnGap)
                    val childWidth = if (cell.child.sizing.horizontal == SizingMode.Fill) {
                        cellWidth
                    } else {
                        naturalWidth(cell.child)
                    }
                    naturalHeight(cell.child, childWidth)
                } ?: 0.0
        }

        val columnOffsets = trackOffsets(columnWidths, layout.columnGap, layout.paddingLeft)
        val rowOffsets = trackOffsets(rowHeights, layout.rowGap, layout.paddingTop)

        val placed = cells.map { cell ->
            val column = cell.column.coerceIn(0, (columnOffsets.size - 1).coerceAtLeast(0))
            val row = cell.row.coerceIn(0, (rowOffsets.size - 1).coerceAtLeast(0))
            val cellX = columnOffsets[column]
            val cellY = rowOffsets[row]
            val cellWidth = spanSize(columnWidths, cell.column, cell.columnSpan, layout.columnGap)
            val cellHeight = spanSize(rowHeights, cell.row, cell.rowSpan, layout.rowGap)
            val childWidth = if (cell.child.sizing.horizontal == SizingMode.Fill) {
                clampWidth(cell.child, cellWidth)
            } else {
                clampWidth(cell.child, naturalWidth(cell.child))
            }
            val childHeight = if (cell.child.sizing.vertical == SizingMode.Fill) {
                clampHeight(cell.child, cellHeight)
            } else {
                clampHeight(cell.child, naturalHeight(cell.child, childWidth))
            }
            val alignY = alignmentOffset(effectiveAlign(layout.alignItems, cell.child), cellHeight, childHeight)
            PlacedChild(cell.child, cellX, cellY + alignY, childWidth, childHeight)
        }

        val contentHeight = layout.paddingTop + rowHeights.sum() +
            layout.rowGap * (rowHeights.size - 1).coerceAtLeast(0) + layout.paddingBottom
        return FlowResult(placed, contentHeight)
    }

    private fun resolveTracks(
        tracks: List<GridTrack>,
        available: Double?,
        gap: Double,
        hugSize: (Int) -> Double,
    ): List<Double> {
        val sizes = MutableList(tracks.size) { 0.0 }
        var flexTotal = 0.0
        tracks.forEachIndexed { index, track ->
            when (track) {
                is GridTrack.Fixed -> sizes[index] = track.value
                GridTrack.Hug -> sizes[index] = hugSize(index)
                is GridTrack.Flex -> flexTotal += track.value
            }
        }
        if (flexTotal > 0.0) {
            val gapTotal = gap * (tracks.size - 1).coerceAtLeast(0)
            val remaining = if (available != null) {
                (available - sizes.sum() - gapTotal).coerceAtLeast(0.0)
            } else {
                0.0
            }
            tracks.forEachIndexed { index, track ->
                if (track is GridTrack.Flex) {
                    sizes[index] = if (available != null) {
                        remaining * track.value / flexTotal
                    } else {
                        hugSize(index)
                    }
                }
            }
        }
        return sizes
    }

    private fun trackOffsets(sizes: List<Double>, gap: Double, paddingStart: Double): List<Double> {
        val offsets = MutableList(sizes.size.coerceAtLeast(1)) { paddingStart }
        var cursor = paddingStart
        sizes.forEachIndexed { index, size ->
            offsets[index] = cursor
            cursor += size + gap
        }
        return offsets
    }

    private fun spanSize(sizes: List<Double>, start: Int, span: Int, gap: Double): Double {
        if (start >= sizes.size || start < 0) return 0.0
        val end = (start + span).coerceAtMost(sizes.size)
        var total = 0.0
        for (index in start until end) total += sizes[index]
        return total + gap * (end - start - 1).coerceAtLeast(0)
    }

    // --- Min/max clamps -----------------------------------------------------

    private fun clampWidth(node: ResolvedNode, width: Double): Double {
        var result = width
        node.minSize?.width?.let { result = result.coerceAtLeast(it) }
        node.maxSize?.width?.let { result = result.coerceAtMost(it) }
        return result.coerceAtLeast(0.0)
    }

    private fun clampHeight(node: ResolvedNode, height: Double): Double {
        var result = height
        node.minSize?.height?.let { result = result.coerceAtLeast(it) }
        node.maxSize?.height?.let { result = result.coerceAtMost(it) }
        return result.coerceAtLeast(0.0)
    }
}
