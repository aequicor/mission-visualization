package io.aequicor.visualization.engine.ir.serialization

import io.aequicor.visualization.engine.ir.model.ComponentPropertyDefinition
import io.aequicor.visualization.engine.ir.model.ComponentPropertyType
import io.aequicor.visualization.engine.ir.model.DesignComponent
import io.aequicor.visualization.engine.ir.model.DesignComponentSet
import io.aequicor.visualization.engine.ir.model.DesignExpression
import io.aequicor.visualization.engine.ir.model.InstanceOverride
import io.aequicor.visualization.engine.ir.model.PropValue
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

/** Components, component sets, property definitions, prop values, instance overrides. */

internal fun DesignDocumentReader.readComponent(element: JsonElement, pointer: String): DesignComponent? {
    val obj = element as? JsonObject ?: return null
    val rootElement = obj["root"] ?: run {
        error(pointer, "Component is missing 'root' node")
        return null
    }
    val root = readNode(rootElement, "$pointer/root") ?: return null
    return DesignComponent(
        name = obj.stringOrDefault("name"),
        libraryId = obj.stringOrDefault("libraryId"),
        properties = obj["properties"].asObject().mapNotNull { (name, def) ->
            readPropertyDefinition(def, "$pointer/properties/$name")?.let { name to it }
        }.toMap(),
        exposedInstances = obj["exposedInstances"].asArray("$pointer/exposedInstances").map { path ->
            path.asArray("$pointer/exposedInstances").map { it.asStringOrEmpty() }
        },
        root = root,
    )
}

private fun DesignDocumentReader.readPropertyDefinition(
    element: JsonElement,
    pointer: String,
): ComponentPropertyDefinition? {
    val obj = element as? JsonObject ?: return null
    return ComponentPropertyDefinition(
        type = readEnum(
            obj["type"], "$pointer/type", ComponentPropertyType.Text,
            mapOf(
                "text" to ComponentPropertyType.Text,
                "boolean" to ComponentPropertyType.Boolean,
                "instanceSwap" to ComponentPropertyType.InstanceSwap,
                "variant" to ComponentPropertyType.Variant,
                "slot" to ComponentPropertyType.Slot,
                "number" to ComponentPropertyType.Number,
                "rawString" to ComponentPropertyType.RawString,
                "dataBinding" to ComponentPropertyType.DataBinding,
            ),
        ),
        default = obj["default"]?.let { readPropValue(it, "$pointer/default") },
        preferredValues = obj["preferredValues"].asArray("$pointer/preferredValues").map {
            it.asStringOrEmpty()
        },
        minItems = (obj["minItems"] as? JsonPrimitive)?.intOrNull,
        maxItems = (obj["maxItems"] as? JsonPrimitive)?.intOrNull,
        allowedContent = obj["allowedContent"].asArray("$pointer/allowedContent").map {
            it.asStringOrEmpty()
        },
    )
}

internal fun DesignDocumentReader.readComponentSet(element: JsonElement, pointer: String): DesignComponentSet {
    val obj = element.asObject()
    return DesignComponentSet(
        name = obj.stringOrDefault("name"),
        axes = obj["axes"].asObject().mapValues { (_, values) ->
            values.asArray("$pointer/axes").map { it.asStringOrEmpty() }
        },
        variants = obj["variants"].asObject().mapValues { (_, componentId) ->
            componentId.asStringOrEmpty()
        },
    )
}

/**
 * Prop value forms: primitive (text/bool/number), `"{{expr}}"` or `{"$data": expr}`
 * data binding, `{"content": {...}}` i18n text, node array or `{"nodes": [...]}` slot fill.
 */
internal fun DesignDocumentReader.readPropValue(element: JsonElement, pointer: String): PropValue? =
    when (element) {
        is JsonPrimitive -> when {
            element.isString && element.content.isMustache() ->
                PropValue.Data(DesignExpression(element.content.stripMustache()))
            element.isString -> PropValue.Text(element.content)
            element.booleanOrNull != null -> PropValue.Bool(element.booleanOrNull ?: false)
            element.doubleOrNull != null -> PropValue.Number(element.doubleOrNull ?: 0.0)
            else -> null
        }
        is JsonArray -> PropValue.SlotContent(
            element.mapIndexedNotNull { index, node -> readNode(node, "$pointer/$index") },
        )
        is JsonObject -> {
            val dataRef = (element["\$data"] as? JsonPrimitive)?.takeIf { it.isString }?.content
            val contentObj = element["content"] as? JsonObject
            val nodesArray = element["nodes"] as? JsonArray
            when {
                dataRef != null -> PropValue.Data(DesignExpression(dataRef.stripMustache()))
                contentObj != null -> PropValue.Content(readTextContent(contentObj, "$pointer/content"))
                nodesArray != null -> PropValue.SlotContent(
                    nodesArray.mapIndexedNotNull { index, node -> readNode(node, "$pointer/nodes/$index") },
                )
                else -> {
                    warn(pointer, "Unsupported prop value object ignored")
                    null
                }
            }
        }
    }

internal fun DesignDocumentReader.readOverride(element: JsonElement, pointer: String): InstanceOverride? {
    val obj = element as? JsonObject ?: return null
    val target = obj["target"].asArray("$pointer/target").map { it.asStringOrEmpty() }
    if (target.isEmpty()) {
        warn(pointer, "Override without target path is ignored")
        return null
    }
    val set = obj["set"] as? JsonObject ?: JsonObject(emptyMap())
    return InstanceOverride(
        target = target,
        fills = (set["fills"] as? JsonArray)?.mapIndexedNotNull { index, paint ->
            readPaint(paint, "$pointer/set/fills/$index")
        },
        strokes = (set["strokes"] as? JsonObject)?.let { readStrokes(it, "$pointer/set/strokes") },
        opacity = set["opacity"]?.let { readBindableDouble(it, 1.0) },
        visible = set["visible"]?.let { readBindableBoolean(it, true) },
        characters = set["characters"]?.let { readBindableString(it, "") },
        textStyle = (set["textStyle"] as? JsonObject)?.let { readTextStyle(it, "$pointer/set/textStyle") },
        cornerRadius = set["cornerRadius"]?.let { readCornerRadius(it, 0.0) },
        variant = (set["variant"] as? JsonObject)?.mapValues { (_, value) -> value.asStringOrEmpty() },
        props = (set["props"] as? JsonObject)?.mapNotNull { (name, value) ->
            readPropValue(value, "$pointer/set/props/$name")?.let { name to it }
        }?.toMap(),
        slotContent = (set["slotContent"] as? JsonArray)?.mapIndexedNotNull { index, node ->
            readNode(node, "$pointer/set/slotContent/$index")
        },
    )
}
