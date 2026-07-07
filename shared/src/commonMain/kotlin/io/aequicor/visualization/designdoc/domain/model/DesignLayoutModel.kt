package io.aequicor.visualization.designdoc.domain.model

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
    val alignItems: AlignItems = AlignItems.Start,
    val justifyContent: JustifyContent = JustifyContent.Start,
    val clipsContent: Boolean = false,
    val columns: List<GridTrack> = emptyList(),
    val rows: List<GridTrack> = emptyList(),
    val columnGap: Bindable<Double>? = null,
    val rowGap: Bindable<Double>? = null,
)

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

data class DesignScroll(
    val overflow: ScrollOverflow = ScrollOverflow.None,
    val sticky: Boolean = false,
)
