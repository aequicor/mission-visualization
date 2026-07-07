package io.aequicor.visualization.engine.ir.serialization

import io.aequicor.visualization.engine.ir.model.DesignNodePatch
import io.aequicor.visualization.engine.ir.model.ResponsiveDimension
import io.aequicor.visualization.engine.ir.model.ResponsiveVariant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** Responsive variants: "when" dimension selectors plus a partial node patch. */

private val DimensionValues = mapOf(
    "breakpoint" to ResponsiveDimension.Breakpoint,
    "devicePreset" to ResponsiveDimension.DevicePreset,
    "platform" to ResponsiveDimension.Platform,
    "theme" to ResponsiveDimension.Theme,
    "density" to ResponsiveDimension.Density,
    "locale" to ResponsiveDimension.Locale,
    "direction" to ResponsiveDimension.Direction,
    "brand" to ResponsiveDimension.Brand,
    "state" to ResponsiveDimension.State,
)

internal fun DesignDocumentReader.readResponsiveVariants(
    element: JsonElement?,
    pointer: String,
): List<ResponsiveVariant> =
    element.asArray(pointer).mapIndexedNotNull { index, variant ->
        readResponsiveVariant(variant, "$pointer/$index")
    }

private fun DesignDocumentReader.readResponsiveVariant(
    element: JsonElement,
    pointer: String,
): ResponsiveVariant? {
    val obj = element as? JsonObject ?: return null
    val selectors = obj["when"].asObject().mapNotNull { (dimension, value) ->
        val known = DimensionValues[dimension] ?: run {
            warn("$pointer/when/$dimension", "Unknown responsive dimension '$dimension' ignored")
            return@mapNotNull null
        }
        known to value.asStringOrEmpty()
    }.toMap()
    if (selectors.isEmpty()) {
        warn(pointer, "Responsive variant without selectors is ignored")
        return null
    }
    val patchObj = (obj["set"] ?: obj["patch"]) as? JsonObject ?: JsonObject(emptyMap())
    return ResponsiveVariant(
        selectors = selectors,
        patch = readNodePatch(patchObj, "$pointer/set"),
        sourceMap = readSourceLocation(obj["sourceMap"], pointer),
    )
}

internal fun DesignDocumentReader.readNodePatch(obj: JsonObject, pointer: String): DesignNodePatch =
    DesignNodePatch(
        visible = obj["visible"]?.let { readBindableBoolean(it, true) },
        opacity = obj["opacity"]?.let { readBindableDouble(it, 1.0) },
        layout = (obj["layout"] as? JsonObject)?.let { readAutoLayout(it, "$pointer/layout") },
        layoutChild = (obj["layoutChild"] as? JsonObject)?.let { readLayoutChild(it, "$pointer/layoutChild") },
        gridPlacement = (obj["gridPlacement"] as? JsonObject)?.let { readGridPlacement(it) },
        sizing = readSizing(obj["sizing"], "$pointer/sizing"),
        size = (obj["size"] as? JsonObject)?.let { readSize(it, "$pointer/size") },
        minSize = (obj["minSize"] as? JsonObject)?.let { readSize(it, "$pointer/minSize") },
        maxSize = (obj["maxSize"] as? JsonObject)?.let { readSize(it, "$pointer/maxSize") },
        fills = (obj["fills"] as? JsonArray)?.mapIndexedNotNull { index, paint ->
            readPaint(paint, "$pointer/fills/$index")
        },
        strokes = (obj["strokes"] as? JsonObject)?.let { readStrokes(it, "$pointer/strokes") },
        effects = (obj["effects"] as? JsonArray)?.mapIndexedNotNull { index, effect ->
            readEffect(effect, "$pointer/effects/$index")
        },
        cornerRadius = obj["cornerRadius"]?.let { readCornerRadius(it, 0.0) },
        textStyle = (obj["textStyle"] as? JsonObject)?.let { readTextStyle(it, "$pointer/textStyle") },
        scroll = (obj["scroll"] as? JsonObject)?.let { readScroll(it, "$pointer/scroll") },
    )
