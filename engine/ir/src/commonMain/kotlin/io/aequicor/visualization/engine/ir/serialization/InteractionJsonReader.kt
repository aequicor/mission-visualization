package io.aequicor.visualization.engine.ir.serialization

import io.aequicor.visualization.engine.ir.model.DesignAction
import io.aequicor.visualization.engine.ir.model.DesignEasing
import io.aequicor.visualization.engine.ir.model.DesignInteraction
import io.aequicor.visualization.engine.ir.model.DesignMotion
import io.aequicor.visualization.engine.ir.model.DesignTransition
import io.aequicor.visualization.engine.ir.model.EasingKind
import io.aequicor.visualization.engine.ir.model.InteractionTrigger
import io.aequicor.visualization.engine.ir.model.MotionFrame
import io.aequicor.visualization.engine.ir.model.MotionKeyframes
import io.aequicor.visualization.engine.ir.model.OverlayPosition
import io.aequicor.visualization.engine.ir.model.OverlaySettings
import io.aequicor.visualization.engine.ir.model.PrototypeVariable
import io.aequicor.visualization.engine.ir.model.TransitionDirection
import io.aequicor.visualization.engine.ir.model.TransitionType
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

/** Interactions, actions, transitions, easing, prototype variables, action sets, motion. */

private val TriggerValues = mapOf(
    "onClick" to InteractionTrigger.OnClick,
    "onHover" to InteractionTrigger.OnHover,
    "onPress" to InteractionTrigger.OnPress,
    "onDrag" to InteractionTrigger.OnDrag,
    "onKey" to InteractionTrigger.OnKey,
    "afterDelay" to InteractionTrigger.AfterDelay,
    "whileHovering" to InteractionTrigger.WhileHovering,
    "whilePressed" to InteractionTrigger.WhilePressed,
    "onVariableChange" to InteractionTrigger.OnVariableChange,
)

private val NamedEasingValues = mapOf(
    "linear" to EasingKind.Linear,
    "easeIn" to EasingKind.EaseIn,
    "easeOut" to EasingKind.EaseOut,
    "easeInOut" to EasingKind.EaseInOut,
    "easeInBack" to EasingKind.EaseInBack,
    "easeOutBack" to EasingKind.EaseOutBack,
)

internal fun DesignDocumentReader.readInteractions(
    element: JsonElement?,
    pointer: String,
): List<DesignInteraction> =
    element.asArray(pointer).mapIndexedNotNull { index, interaction ->
        readInteraction(interaction, "$pointer/$index")
    }

private fun DesignDocumentReader.readInteraction(element: JsonElement, pointer: String): DesignInteraction? {
    val obj = element as? JsonObject ?: return null
    val triggerRaw = obj.stringOrDefault("trigger")
    val trigger = TriggerValues[triggerRaw] ?: run {
        warn("$pointer/trigger", "Unknown interaction trigger '$triggerRaw'; interaction dropped")
        return null
    }
    // "action" shorthand = single-element action list.
    val actions = listOfNotNull(obj["action"]?.let { readAction(it, "$pointer/action") }) +
        obj["actions"].asArray("$pointer/actions").mapIndexedNotNull { index, action ->
            readAction(action, "$pointer/actions/$index")
        }
    return DesignInteraction(
        trigger = trigger,
        key = obj.stringOrDefault("key"),
        delayMs = (obj["delayMs"] as? JsonPrimitive)?.doubleOrNull,
        variable = obj.stringOrDefault("variable"),
        actions = actions,
        sourceMap = readSourceLocation(obj["sourceMap"], pointer),
    )
}

internal fun DesignDocumentReader.readAction(element: JsonElement, pointer: String): DesignAction? {
    val obj = element as? JsonObject ?: return null
    val transition = (obj["transition"] as? JsonObject)?.let { readTransition(it, "$pointer/transition") }
    return when (val type = obj.stringOrDefault("type")) {
        "navigate" -> DesignAction.Navigate(
            to = obj.stringOrDefault("to"),
            transition = transition,
        )
        "openOverlay" -> DesignAction.OpenOverlay(
            destination = obj.stringOrDefault("destination"),
            overlay = readOverlaySettings(obj["overlay"], "$pointer/overlay"),
            transition = transition,
        )
        "swapOverlay" -> DesignAction.SwapOverlay(
            destination = obj.stringOrDefault("destination"),
            transition = transition,
        )
        "closeOverlay" -> DesignAction.CloseOverlay(transition)
        "back" -> DesignAction.Back
        "openLink" -> DesignAction.OpenLink(obj.stringOrDefault("url"))
        "setVariable" -> DesignAction.SetVariable(
            variable = obj.stringOrDefault("variable"),
            value = readBindableString(obj["value"], ""),
        )
        "changeToVariant" -> DesignAction.ChangeToVariant(
            target = obj.stringOrDefault("target"),
            variant = obj["variant"].asObject().mapValues { (_, value) -> value.asStringOrEmpty() },
        )
        "scrollTo" -> DesignAction.ScrollTo(
            target = obj.stringOrDefault("target"),
            animated = obj.booleanOrDefault("animated", true),
        )
        "runActionSet" -> DesignAction.RunActionSet(obj.stringOrDefault("actionSetId"))
        else -> {
            warn(pointer, "Unknown action type '$type' kept as unknown")
            DesignAction.Unknown(type)
        }
    }
}

private fun DesignDocumentReader.readTransition(obj: JsonObject, pointer: String): DesignTransition =
    DesignTransition(
        type = readEnum(
            obj["type"], "$pointer/type", TransitionType.Instant,
            mapOf(
                "instant" to TransitionType.Instant,
                "dissolve" to TransitionType.Dissolve,
                "smartAnimate" to TransitionType.SmartAnimate,
                "moveIn" to TransitionType.MoveIn,
                "moveOut" to TransitionType.MoveOut,
                "push" to TransitionType.Push,
                "slideIn" to TransitionType.SlideIn,
                "slideOut" to TransitionType.SlideOut,
            ),
        ),
        direction = readEnum(
            obj["direction"], "$pointer/direction", TransitionDirection.Left,
            mapOf(
                "left" to TransitionDirection.Left,
                "right" to TransitionDirection.Right,
                "top" to TransitionDirection.Top,
                "bottom" to TransitionDirection.Bottom,
            ),
        ),
        easing = readEasing(obj["easing"], "$pointer/easing"),
        durationMs = obj.doubleOrDefault("durationMs", 300.0),
    )

/** A name string, or `{"type": "cubicBezier"|"spring"|<name>, ...}`. */
private fun DesignDocumentReader.readEasing(element: JsonElement?, pointer: String): DesignEasing =
    when (element) {
        null -> DesignEasing.Named(EasingKind.EaseOut)
        is JsonPrimitive -> DesignEasing.Named(
            readEnum(element, pointer, EasingKind.EaseOut, NamedEasingValues),
        )
        is JsonObject -> when (val type = element.stringOrDefault("type")) {
            "cubicBezier" -> DesignEasing.CubicBezier(
                x1 = element.doubleOrDefault("x1", 0.0),
                y1 = element.doubleOrDefault("y1", 0.0),
                x2 = element.doubleOrDefault("x2", 1.0),
                y2 = element.doubleOrDefault("y2", 1.0),
            )
            "spring" -> DesignEasing.Spring(
                mass = element.doubleOrDefault("mass", 1.0),
                stiffness = element.doubleOrDefault("stiffness", 100.0),
                damping = element.doubleOrDefault("damping", 15.0),
            )
            else -> NamedEasingValues[type]?.let { DesignEasing.Named(it) } ?: run {
                warn("$pointer/type", "Unknown easing '$type', using fallback")
                DesignEasing.Named(EasingKind.EaseOut)
            }
        }
        else -> DesignEasing.Named(EasingKind.EaseOut)
    }

private fun DesignDocumentReader.readOverlaySettings(element: JsonElement?, pointer: String): OverlaySettings {
    val obj = element as? JsonObject ?: return OverlaySettings()
    return OverlaySettings(
        position = readEnum(
            obj["position"], "$pointer/position", OverlayPosition.Center,
            mapOf(
                "center" to OverlayPosition.Center,
                "topLeft" to OverlayPosition.TopLeft,
                "topCenter" to OverlayPosition.TopCenter,
                "topRight" to OverlayPosition.TopRight,
                "bottomLeft" to OverlayPosition.BottomLeft,
                "bottomCenter" to OverlayPosition.BottomCenter,
                "bottomRight" to OverlayPosition.BottomRight,
                "manual" to OverlayPosition.Manual,
            ),
        ),
        offset = (obj["offset"] as? JsonObject)?.let { readPoint(it, "$pointer/offset") },
        closeOnOutsideClick = obj.booleanOrDefault("closeOnOutsideClick", true),
        background = obj["background"]?.let { readBindableColor(it, "$pointer/background") },
    )
}

internal fun DesignDocumentReader.readPrototypeVariables(
    element: JsonElement?,
    pointer: String,
): Map<String, PrototypeVariable> =
    element.asObject().mapNotNull { (name, value) ->
        val obj = value as? JsonObject ?: return@mapNotNull null
        val type = readVariableType(obj["type"], "$pointer/$name/type")
        val default = obj["default"]?.let { readVariableValue(it, type, "$pointer/$name/default") }
        name to PrototypeVariable(type = type, default = default)
    }.toMap()

internal fun DesignDocumentReader.readActionSets(
    element: JsonElement?,
    pointer: String,
): Map<String, List<DesignAction>> =
    element.asObject().mapValues { (id, actions) ->
        actions.asArray("$pointer/$id").mapIndexedNotNull { index, action ->
            readAction(action, "$pointer/$id/$index")
        }
    }

internal fun DesignDocumentReader.readMotion(obj: JsonObject, pointer: String): DesignMotion =
    DesignMotion(
        ref = obj.stringOrDefault("ref"),
        fallback = (obj["fallback"] as? JsonObject)?.let { fallback ->
            MotionKeyframes(
                durationMs = fallback.doubleOrDefault("durationMs", 0.0),
                loop = fallback.booleanOrDefault("loop", false),
                frames = fallback["frames"].asArray("$pointer/fallback/frames").mapNotNull { frame ->
                    val frameObj = frame as? JsonObject ?: return@mapNotNull null
                    MotionFrame(
                        at = frameObj.doubleOrDefault("at", 0.0),
                        properties = (frameObj["properties"] as? JsonObject ?: frameObj)
                            .mapNotNull { (property, value) ->
                                if (property == "at") return@mapNotNull null
                                (value as? JsonPrimitive)?.doubleOrNull?.let { property to it }
                            }
                            .toMap(),
                    )
                },
            )
        },
    )
