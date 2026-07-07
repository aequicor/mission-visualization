package io.aequicor.visualization.engine.ir.model

/** `fixed` — from `size`; `hug` — wrap content; `fill` — stretch inside the parent. */
enum class SizingMode { Fixed, Hug, Fill }

data class DesignSizing(
    val horizontal: SizingMode = SizingMode.Fixed,
    val vertical: SizingMode = SizingMode.Fixed,
)

enum class HorizontalConstraint { Left, Right, Center, LeftRight, Scale }

enum class VerticalConstraint { Top, Bottom, Center, TopBottom, Scale }

data class DesignConstraints(
    val horizontal: HorizontalConstraint = HorizontalConstraint.Left,
    val vertical: VerticalConstraint = VerticalConstraint.Top,
)

enum class LayoutMode { None, Horizontal, Vertical, Grid }

/** Main-axis gap: a number, or `"auto"` (space-between). */
sealed interface DesignGap {
    data class Fixed(val value: Bindable<Double>) : DesignGap

    data object Auto : DesignGap
}

enum class AlignItems { Start, Center, End, Baseline, Stretch }

enum class JustifyContent { Start, Center, End, SpaceBetween }

data class DesignAutoLayout(
    val mode: LayoutMode = LayoutMode.None,
    val gap: DesignGap = DesignGap.Fixed(0.0.bindable()),
    val crossGap: Bindable<Double>? = null,
    val wrap: Boolean = false,
    val padding: DesignInsets = DesignInsets(),
    /** Direction-aware padding; wins over [padding], mapped physical by the resolver. */
    val paddingLogical: DesignLogicalInsets? = null,
    val alignItems: AlignItems = AlignItems.Start,
    val justifyContent: JustifyContent = JustifyContent.Start,
    val baseline: BaselineAlign = BaselineAlign.First,
    val clipsContent: Boolean = false,
    val columns: List<GridTrack> = emptyList(),
    val rows: List<GridTrack> = emptyList(),
    val columnGap: Bindable<Double>? = null,
    val rowGap: Bindable<Double>? = null,
    /** Template for implicit rows (`rows.auto: true`) when [rows] is empty. */
    val implicitRows: GridTrack? = null,
    /** Minimum implicit row size (`rows.min`). */
    val implicitRowMin: Double? = null,
)

/** Direction-aware insets: inline = reading direction, block = perpendicular. */
data class DesignLogicalInsets(
    val blockStart: Bindable<Double>? = null,
    val inlineEnd: Bindable<Double>? = null,
    val blockEnd: Bindable<Double>? = null,
    val inlineStart: Bindable<Double>? = null,
)

/** Logical anchors for absolute children; the resolver maps them by direction. */
data class DesignAnchors(
    val inlineStart: Bindable<Double>? = null,
    val inlineEnd: Bindable<Double>? = null,
    val blockStart: Bindable<Double>? = null,
    val blockEnd: Bindable<Double>? = null,
)

enum class BaselineAlign { First, Last }

sealed interface GridTrack {
    data class Fixed(val value: Double) : GridTrack

    data class Flex(val value: Double) : GridTrack

    data object Hug : GridTrack
}

/** Placement of a child inside a grid container; 1-based indices. */
data class GridPlacement(
    val column: Int = 0,
    val row: Int = 0,
    val columnSpan: Int = 1,
    val rowSpan: Int = 1,
)

/**
 * Child behavior inside an auto-layout parent. `absolute` pulls the node out of the
 * flow: it positions via `position` + `constraints` and siblings lay out as if it
 * were not there.
 */
data class DesignLayoutChild(
    val alignSelf: AlignItems? = null,
    val absolute: Boolean = false,
)

enum class ScrollOverflow { None, Horizontal, Vertical, Both }

enum class OverflowMode { Visible, Hidden, Auto }

data class DesignScroll(
    /** Scroll direction (legacy shorthand). */
    val overflow: ScrollOverflow = ScrollOverflow.None,
    val sticky: Boolean = false,
    val overflowX: OverflowMode = OverflowMode.Visible,
    val overflowY: OverflowMode = OverflowMode.Visible,
    /** Child ids pinned in place while this container scrolls. */
    val fixedChildren: List<String> = emptyList(),
)

/** Layout grid overlay definition (frame prop or grid style); no layout effect. */
data class LayoutGridDefinition(
    val type: LayoutGridType,
    val count: Int? = null,
    val size: Double? = null,
    val gutter: Double = 0.0,
    val margin: Double = 0.0,
    val alignment: LayoutGridAlignment = LayoutGridAlignment.Stretch,
    val color: DesignColor? = null,
    val visible: Boolean = true,
)

enum class LayoutGridType { Columns, Rows, Grid }

enum class LayoutGridAlignment { Stretch, Start, Center, End }

/** Canvas ruler guide; overlay + validator only. */
data class GuideLine(
    val orientation: GuideOrientation,
    val position: Double,
)

enum class GuideOrientation { Horizontal, Vertical }
