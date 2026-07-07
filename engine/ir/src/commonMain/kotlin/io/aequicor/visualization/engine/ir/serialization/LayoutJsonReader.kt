package io.aequicor.visualization.engine.ir.serialization

import io.aequicor.visualization.engine.ir.model.AlignItems
import io.aequicor.visualization.engine.ir.model.BaselineAlign
import io.aequicor.visualization.engine.ir.model.DesignAnchors
import io.aequicor.visualization.engine.ir.model.DesignAutoLayout
import io.aequicor.visualization.engine.ir.model.DesignConstraints
import io.aequicor.visualization.engine.ir.model.DesignGap
import io.aequicor.visualization.engine.ir.model.DesignInsets
import io.aequicor.visualization.engine.ir.model.DesignLayoutChild
import io.aequicor.visualization.engine.ir.model.DesignLogicalInsets
import io.aequicor.visualization.engine.ir.model.DesignScroll
import io.aequicor.visualization.engine.ir.model.DesignSizing
import io.aequicor.visualization.engine.ir.model.GridPlacement
import io.aequicor.visualization.engine.ir.model.GridTrack
import io.aequicor.visualization.engine.ir.model.HorizontalConstraint
import io.aequicor.visualization.engine.ir.model.JustifyContent
import io.aequicor.visualization.engine.ir.model.LayoutMode
import io.aequicor.visualization.engine.ir.model.OverflowMode
import io.aequicor.visualization.engine.ir.model.ScrollOverflow
import io.aequicor.visualization.engine.ir.model.SizingMode
import io.aequicor.visualization.engine.ir.model.VerticalConstraint
import io.aequicor.visualization.engine.ir.model.bindable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull

/** Auto-layout, grid tracks/placement, layout child, scroll, constraints, sizing, insets. */

private val AlignItemsValues = mapOf(
    "start" to AlignItems.Start,
    "center" to AlignItems.Center,
    "end" to AlignItems.End,
    "baseline" to AlignItems.Baseline,
    "stretch" to AlignItems.Stretch,
)

internal fun DesignDocumentReader.readAutoLayout(obj: JsonObject?, pointer: String): DesignAutoLayout {
    if (obj == null) return DesignAutoLayout()
    val gapElement = obj["gap"]
    val gap = when {
        gapElement is JsonPrimitive && gapElement.isString && gapElement.content == "auto" -> DesignGap.Auto
        gapElement == null || gapElement is JsonObject && !gapElement.isBindingRef() -> DesignGap.Fixed(0.0.bindable())
        else -> DesignGap.Fixed(readBindableDouble(gapElement, 0.0))
    }
    val gapObject = gapElement as? JsonObject
    val rowsElement = obj["rows"]
    val implicitRowsObject = rowsElement as? JsonObject
    return DesignAutoLayout(
        mode = readEnum(
            obj["mode"], "$pointer/mode", LayoutMode.None,
            mapOf(
                "none" to LayoutMode.None,
                "horizontal" to LayoutMode.Horizontal,
                "vertical" to LayoutMode.Vertical,
                "grid" to LayoutMode.Grid,
            ),
        ),
        gap = gap,
        crossGap = obj["crossGap"]?.let { readBindableDouble(it, 0.0) },
        wrap = obj.booleanOrDefault("wrap", false),
        padding = (obj["padding"])?.let { readInsetsOrShorthand(it) } ?: DesignInsets(),
        paddingLogical = readLogicalInsets(obj["paddingLogical"]),
        alignItems = readEnum(obj["alignItems"], "$pointer/alignItems", AlignItems.Start, AlignItemsValues),
        justifyContent = readEnum(
            obj["justifyContent"], "$pointer/justifyContent", JustifyContent.Start,
            mapOf(
                "start" to JustifyContent.Start,
                "center" to JustifyContent.Center,
                "end" to JustifyContent.End,
                "spaceBetween" to JustifyContent.SpaceBetween,
            ),
        ),
        baseline = readEnum(
            obj["baseline"], "$pointer/baseline", BaselineAlign.First,
            mapOf("first" to BaselineAlign.First, "last" to BaselineAlign.Last),
        ),
        clipsContent = obj.booleanOrDefault("clipsContent", false),
        columns = obj["columns"].asArray("$pointer/columns").mapNotNull { readGridTrack(it) },
        rows = (rowsElement as? JsonArray)?.mapNotNull { readGridTrack(it) } ?: emptyList(),
        columnGap = gapObject?.get("column")?.let { readBindableDouble(it, 0.0) },
        rowGap = gapObject?.get("row")?.let { readBindableDouble(it, 0.0) },
        implicitRows = implicitRowsObject?.let { readImplicitRows(it["auto"]) },
        implicitRowMin = (implicitRowsObject?.get("min") as? JsonPrimitive)?.doubleOrNull,
    )
}

/** `rows: {auto: true}` uses a hug template; `rows: {auto: {track}}` an explicit one. */
private fun DesignDocumentReader.readImplicitRows(element: JsonElement?): GridTrack? =
    when (element) {
        null -> null
        is JsonPrimitive -> if (element.booleanOrNull == true) GridTrack.Hug else null
        is JsonObject -> readGridTrack(element)
        else -> null
    }

internal fun DesignDocumentReader.readGridTrack(element: JsonElement): GridTrack? {
    val obj = element as? JsonObject ?: return null
    return when (obj.stringOrDefault("type", "hug")) {
        "fixed" -> GridTrack.Fixed(obj.doubleOrDefault("value", 0.0))
        "flex" -> GridTrack.Flex(obj.doubleOrDefault("value", 1.0))
        else -> GridTrack.Hug
    }
}

internal fun DesignDocumentReader.readGridPlacement(obj: JsonObject): GridPlacement =
    GridPlacement(
        column = obj.intOrDefault("column", 0),
        row = obj.intOrDefault("row", 0),
        columnSpan = obj.intOrDefault("columnSpan", 1),
        rowSpan = obj.intOrDefault("rowSpan", 1),
    )

internal fun DesignDocumentReader.readLayoutChild(element: JsonElement?, pointer: String): DesignLayoutChild {
    val obj = element as? JsonObject ?: return DesignLayoutChild()
    return DesignLayoutChild(
        alignSelf = obj["alignSelf"]?.let {
            readEnum(it, "$pointer/alignSelf", AlignItems.Start, AlignItemsValues)
        },
        absolute = obj.booleanOrDefault("absolute", false),
    )
}

internal fun DesignDocumentReader.readScroll(element: JsonElement?, pointer: String): DesignScroll {
    val obj = element as? JsonObject ?: return DesignScroll()
    val overflowModes = mapOf(
        "visible" to OverflowMode.Visible,
        "hidden" to OverflowMode.Hidden,
        "auto" to OverflowMode.Auto,
    )
    return DesignScroll(
        overflow = readEnum(
            obj["overflow"], "$pointer/overflow", ScrollOverflow.None,
            mapOf(
                "none" to ScrollOverflow.None,
                "horizontal" to ScrollOverflow.Horizontal,
                "vertical" to ScrollOverflow.Vertical,
                "both" to ScrollOverflow.Both,
            ),
        ),
        sticky = obj.booleanOrDefault("sticky", false),
        overflowX = readEnum(obj["overflowX"], "$pointer/overflowX", OverflowMode.Visible, overflowModes),
        overflowY = readEnum(obj["overflowY"], "$pointer/overflowY", OverflowMode.Visible, overflowModes),
        fixedChildren = obj["fixedChildren"].asArray("$pointer/fixedChildren").map { it.asStringOrEmpty() },
    )
}

internal fun DesignDocumentReader.readConstraints(element: JsonElement?, pointer: String): DesignConstraints {
    val obj = element as? JsonObject ?: return DesignConstraints()
    return DesignConstraints(
        horizontal = readEnum(
            obj["horizontal"], "$pointer/horizontal", HorizontalConstraint.Left,
            mapOf(
                "left" to HorizontalConstraint.Left,
                "right" to HorizontalConstraint.Right,
                "center" to HorizontalConstraint.Center,
                "leftRight" to HorizontalConstraint.LeftRight,
                "scale" to HorizontalConstraint.Scale,
            ),
        ),
        vertical = readEnum(
            obj["vertical"], "$pointer/vertical", VerticalConstraint.Top,
            mapOf(
                "top" to VerticalConstraint.Top,
                "bottom" to VerticalConstraint.Bottom,
                "center" to VerticalConstraint.Center,
                "topBottom" to VerticalConstraint.TopBottom,
                "scale" to VerticalConstraint.Scale,
            ),
        ),
    )
}

internal fun DesignDocumentReader.readSizing(element: JsonElement?, pointer: String): DesignSizing? {
    val obj = element as? JsonObject ?: return null
    val modes = mapOf(
        "fixed" to SizingMode.Fixed,
        "hug" to SizingMode.Hug,
        "fill" to SizingMode.Fill,
    )
    return DesignSizing(
        horizontal = readEnum(obj["horizontal"], "$pointer/horizontal", SizingMode.Fixed, modes),
        vertical = readEnum(obj["vertical"], "$pointer/vertical", SizingMode.Fixed, modes),
    )
}

internal fun DesignDocumentReader.readInsetsOrShorthand(element: JsonElement): DesignInsets =
    when (element) {
        is JsonObject -> readInsets(element)
        is JsonPrimitive -> {
            val all = readBindableDouble(element, 0.0)
            DesignInsets(all, all, all, all)
        }
        else -> DesignInsets()
    }

internal fun DesignDocumentReader.readInsets(obj: JsonObject): DesignInsets =
    DesignInsets(
        top = readBindableDouble(obj["top"], 0.0),
        right = readBindableDouble(obj["right"], 0.0),
        bottom = readBindableDouble(obj["bottom"], 0.0),
        left = readBindableDouble(obj["left"], 0.0),
    )

internal fun DesignDocumentReader.readLogicalInsets(element: JsonElement?): DesignLogicalInsets? {
    val obj = element as? JsonObject ?: return null
    return DesignLogicalInsets(
        blockStart = obj["blockStart"]?.let { readBindableDouble(it, 0.0) },
        inlineEnd = obj["inlineEnd"]?.let { readBindableDouble(it, 0.0) },
        blockEnd = obj["blockEnd"]?.let { readBindableDouble(it, 0.0) },
        inlineStart = obj["inlineStart"]?.let { readBindableDouble(it, 0.0) },
    )
}

internal fun DesignDocumentReader.readAnchors(element: JsonElement?): DesignAnchors? {
    val obj = element as? JsonObject ?: return null
    return DesignAnchors(
        inlineStart = obj["inlineStart"]?.let { readBindableDouble(it, 0.0) },
        inlineEnd = obj["inlineEnd"]?.let { readBindableDouble(it, 0.0) },
        blockStart = obj["blockStart"]?.let { readBindableDouble(it, 0.0) },
        blockEnd = obj["blockEnd"]?.let { readBindableDouble(it, 0.0) },
    )
}
