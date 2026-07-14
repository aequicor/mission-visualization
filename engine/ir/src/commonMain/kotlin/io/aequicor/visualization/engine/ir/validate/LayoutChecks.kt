package io.aequicor.visualization.engine.ir.validate

import io.aequicor.visualization.engine.ir.model.DesignDiagnostic
import io.aequicor.visualization.engine.ir.model.ContainerKind
import io.aequicor.visualization.engine.ir.model.DesignAutoLayout
import io.aequicor.visualization.engine.ir.model.DesignConstraints
import io.aequicor.visualization.engine.ir.model.DesignGap
import io.aequicor.visualization.engine.ir.model.DesignNode
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.engine.ir.model.DesignScroll
import io.aequicor.visualization.engine.ir.model.HorizontalConstraint
import io.aequicor.visualization.engine.ir.model.LayoutMode
import io.aequicor.visualization.engine.ir.model.OverflowMode
import io.aequicor.visualization.engine.ir.model.ScrollOverflow
import io.aequicor.visualization.engine.ir.model.SizingMode
import io.aequicor.visualization.engine.ir.model.VerticalConstraint
import io.aequicor.visualization.engine.ir.model.literalOrNull

/**
 * IR-LAYOUT — auto-layout parameters, grid placement, sizing conflicts.
 *
 * - IR-LAYOUT-001 (error): negative literal gap/padding.
 * - IR-LAYOUT-002 (warning): `wrap` on a non-horizontal layout (engine ignores it).
 * - IR-LAYOUT-003 (error): grid placement/span outside the explicit tracks.
 * - IR-LAYOUT-004 (warning): Fill child of a free (`LayoutMode.None`) parent without
 *   stretching constraints — it never adapts when the parent resizes.
 * - IR-LAYOUT-005 (warning): Hug parent whose flow children all Fill the same axis
 *   (circular sizing; the engine collapses toward content minimums).
 * - IR-LAYOUT-006 (warning): absolute child with neither position nor anchors.
 * - IR-LAYOUT-007 (warning): scrollable container that neither clips nor hides
 *   overflow (adaptation of the overflow/clipsContent contradiction check: the
 *   authored-default `overflow: visible` is indistinguishable from an omitted field,
 *   so the noisy inverse combination is not flagged).
 * - IR-LAYOUT-008 (error): `scroll.fixedChildren` id that is not a direct child.
 * - IR-LAYOUT-009/010 (error): containerKind and flow properties disagree.
 * - IR-LAYOUT-011 (warning): a non-default resize constraint has no authored
 *   position/anchors to preserve, so it cannot place the node. In-flow Auto Layout
 *   children that do carry a position/anchors are left alone (Figma keeps the
 *   constraint for when the child is later detached).
 */
internal object LayoutChecks {

    fun check(ctx: ValidationContext): List<DesignDiagnostic> = buildList {
        ctx.entries.forEach { entry ->
            val node = entry.node
            checkContainerKind(this, ctx, node)
            checkNegativeScalars(this, ctx, node)
            checkWrap(this, ctx, node)
            checkGridPlacement(this, ctx, node)
            checkSizingConflicts(this, ctx, node)
            checkConstraintPlacement(this, ctx, entry)
            checkAbsoluteChildren(this, ctx, node)
            checkScroll(this, ctx, node)
        }
    }

    private fun checkContainerKind(
        sink: MutableList<DesignDiagnostic>,
        ctx: ValidationContext,
        node: DesignNode,
    ) {
        if (node.kind !is DesignNodeKind.Frame) return
        val flowSettings = node.layout.copy(mode = LayoutMode.None, clipsContent = false)
        when (node.containerKind) {
            ContainerKind.Frame -> if (node.layout.mode != LayoutMode.None || flowSettings != DesignAutoLayout()) {
                sink += validationError(
                    "IR-LAYOUT-009",
                    "Frame '${node.id}' cannot use Auto Layout properties; use containerKind autoLayout",
                    ctx.location(node),
                )
            }
            ContainerKind.AutoLayout -> if (node.layout.mode == LayoutMode.None) {
                sink += validationError(
                    "IR-LAYOUT-010",
                    "AutoLayout '${node.id}' requires row, column, or grid",
                    ctx.location(node),
                )
            }
        }
    }

    private fun checkNegativeScalars(sink: MutableList<DesignDiagnostic>, ctx: ValidationContext, node: DesignNode) {
        fun flag(name: String, value: Double?) {
            if (value != null && value < 0.0) {
                sink += validationError(
                    "IR-LAYOUT-001",
                    "Negative $name ($value) on '${node.id}'",
                    ctx.location(node),
                )
            }
        }

        val layout = node.layout
        flag("gap", (layout.gap as? DesignGap.Fixed)?.value?.literalOrNull())
        flag("crossGap", layout.crossGap?.literalOrNull())
        flag("columnGap", layout.columnGap?.literalOrNull())
        flag("rowGap", layout.rowGap?.literalOrNull())
        flag("padding.top", layout.padding.top.literalOrNull())
        flag("padding.right", layout.padding.right.literalOrNull())
        flag("padding.bottom", layout.padding.bottom.literalOrNull())
        flag("padding.left", layout.padding.left.literalOrNull())
    }

    private fun checkWrap(sink: MutableList<DesignDiagnostic>, ctx: ValidationContext, node: DesignNode) {
        if (node.layout.wrap && node.layout.mode != LayoutMode.Horizontal) {
            sink += validationWarning(
                "IR-LAYOUT-002",
                "'${node.id}' sets wrap on a ${node.layout.mode} layout; only horizontal layouts wrap",
                ctx.location(node),
            )
        }
    }

    private fun checkGridPlacement(sink: MutableList<DesignDiagnostic>, ctx: ValidationContext, node: DesignNode) {
        if (node.layout.mode != LayoutMode.Grid) return
        val columnCount = node.layout.columns.size
        val rowCount = node.layout.rows.size
        node.children.forEach { child ->
            val placement = child.gridPlacement ?: return@forEach
            if (columnCount > 0 && placement.column >= 1 &&
                placement.column - 1 + placement.columnSpan.coerceAtLeast(1) > columnCount
            ) {
                sink += validationError(
                    "IR-LAYOUT-003",
                    "Grid child '${child.id}' spans columns ${placement.column}.." +
                        "${placement.column + placement.columnSpan.coerceAtLeast(1) - 1} " +
                        "but '${node.id}' declares $columnCount column(s)",
                    ctx.location(child),
                )
            }
            if (rowCount > 0 && placement.row >= 1 &&
                placement.row - 1 + placement.rowSpan.coerceAtLeast(1) > rowCount
            ) {
                sink += validationError(
                    "IR-LAYOUT-003",
                    "Grid child '${child.id}' spans rows ${placement.row}.." +
                        "${placement.row + placement.rowSpan.coerceAtLeast(1) - 1} " +
                        "but '${node.id}' declares $rowCount row(s)",
                    ctx.location(child),
                )
            }
        }
    }

    private fun checkSizingConflicts(sink: MutableList<DesignDiagnostic>, ctx: ValidationContext, node: DesignNode) {
        if (node.layout.mode == LayoutMode.None) {
            node.children.forEach { child ->
                val sizing = child.sizing ?: return@forEach
                if (sizing.horizontal == SizingMode.Fill &&
                    child.constraints.horizontal !in setOf(HorizontalConstraint.LeftRight, HorizontalConstraint.Scale)
                ) {
                    sink += validationWarning(
                        "IR-LAYOUT-004",
                        "'${child.id}' fills horizontally inside free-layout '${node.id}' " +
                            "without leftRight/scale constraints; it will not adapt to resizes",
                        ctx.location(child),
                    )
                }
                if (sizing.vertical == SizingMode.Fill &&
                    child.constraints.vertical !in setOf(VerticalConstraint.TopBottom, VerticalConstraint.Scale)
                ) {
                    sink += validationWarning(
                        "IR-LAYOUT-004",
                        "'${child.id}' fills vertically inside free-layout '${node.id}' " +
                            "without topBottom/scale constraints; it will not adapt to resizes",
                        ctx.location(child),
                    )
                }
            }
            return
        }
        val flow = node.children.filterNot { it.layoutChild.absolute }
        if (flow.isEmpty()) return
        val sizing = node.sizing ?: return
        if (sizing.horizontal == SizingMode.Hug &&
            flow.all { it.sizing?.horizontal == SizingMode.Fill }
        ) {
            sink += validationWarning(
                "IR-LAYOUT-005",
                "Hug-width '${node.id}' only has fill-width children; sizes are circular",
                ctx.location(node),
            )
        }
        if (sizing.vertical == SizingMode.Hug &&
            flow.all { it.sizing?.vertical == SizingMode.Fill }
        ) {
            sink += validationWarning(
                "IR-LAYOUT-005",
                "Hug-height '${node.id}' only has fill-height children; sizes are circular",
                ctx.location(node),
            )
        }
    }

    /** Constraints preserve authored geometry during resize; they never create that geometry. */
    private fun checkConstraintPlacement(
        sink: MutableList<DesignDiagnostic>,
        ctx: ValidationContext,
        entry: NodeEntry,
    ) {
        val node = entry.node
        if (node.constraints == DesignConstraints()) return
        // In-flow Auto Layout children legitimately carry resize constraints in Figma: the
        // constraint takes effect only if the child is later detached/absolutized. Flagging
        // every such child was noise, so we only warn when the constraint truly cannot place
        // the node — i.e. there is no authored position or anchors to preserve.
        if (node.position == null && node.anchors == null) {
            sink += validationWarning(
                "IR-LAYOUT-011",
                "Resize constraints on '${node.id}' preserve an authored position but do not " +
                    "create one; add position/anchors before using align center|bottom|right",
                ctx.location(node),
            )
        }
    }

    private fun checkAbsoluteChildren(sink: MutableList<DesignDiagnostic>, ctx: ValidationContext, node: DesignNode) {
        if (node.layout.mode == LayoutMode.None) return
        node.children.forEach { child ->
            if (child.layoutChild.absolute && child.position == null && child.anchors == null) {
                sink += validationWarning(
                    "IR-LAYOUT-006",
                    "Absolute child '${child.id}' of '${node.id}' has neither position nor anchors",
                    ctx.location(child),
                )
            }
        }
    }

    private fun checkScroll(sink: MutableList<DesignDiagnostic>, ctx: ValidationContext, node: DesignNode) {
        val scroll = node.scroll
        if (scroll == DesignScroll()) return
        if (scroll.overflow != ScrollOverflow.None &&
            scroll.overflowX == OverflowMode.Visible &&
            scroll.overflowY == OverflowMode.Visible &&
            !node.layout.clipsContent
        ) {
            sink += validationWarning(
                "IR-LAYOUT-007",
                "'${node.id}' scrolls (${scroll.overflow}) but neither clips content nor hides overflow",
                ctx.location(node),
            )
        }
        val childIds = node.children.map { it.id }.toSet()
        scroll.fixedChildren.forEach { fixedId ->
            if (fixedId !in childIds) {
                sink += validationError(
                    "IR-LAYOUT-008",
                    "scroll.fixedChildren of '${node.id}' references '$fixedId', which is not a direct child",
                    ctx.location(node),
                )
            }
        }
    }
}
