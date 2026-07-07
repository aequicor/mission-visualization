package io.aequicor.visualization.engine.frontend.blocks.readers

import io.aequicor.visualization.engine.frontend.blocks.ActionPatch
import io.aequicor.visualization.engine.frontend.blocks.InteractionPatch
import io.aequicor.visualization.engine.frontend.blocks.MotionPatch
import io.aequicor.visualization.engine.frontend.yaml.YamlMap
import io.aequicor.visualization.engine.frontend.yaml.YamlScalar
import io.aequicor.visualization.engine.frontend.yaml.YamlValue
import io.aequicor.visualization.engine.ir.model.DesignAction
import io.aequicor.visualization.engine.ir.model.DesignEasing
import io.aequicor.visualization.engine.ir.model.DesignMotion
import io.aequicor.visualization.engine.ir.model.DesignTransition
import io.aequicor.visualization.engine.ir.model.EasingKind
import io.aequicor.visualization.engine.ir.model.MotionFrame
import io.aequicor.visualization.engine.ir.model.MotionKeyframes
import io.aequicor.visualization.engine.ir.model.OverlaySettings
import io.aequicor.visualization.engine.ir.model.TransitionDirection
import io.aequicor.visualization.engine.ir.model.TransitionType
import io.aequicor.visualization.engine.ir.model.bindable

/** `action:` block — shorthand for a single onClick interaction. */
internal fun readActionBlock(value: YamlValue, reading: BlockReading): ActionPatch? {
    val map = value as? YamlMap ?: run {
        reading.error("`action` must be a map with a `type`", value)
        return null
    }
    val type = map.string("type", reading) ?: run {
        reading.error("`action` needs a `type`", map)
        return null
    }
    val action = buildAction(type, map, reading) ?: return null
    return ActionPatch(action)
}

/** `interaction:` block — trigger plus one action (flat form) or an `actions` list. */
internal fun readInteractionBlock(value: YamlValue, reading: BlockReading): InteractionPatch? {
    val map = value as? YamlMap ?: run {
        reading.error("`interaction` must be a map", value)
        return null
    }
    val actions = mutableListOf<DesignAction>()
    map.string("action", reading)?.let { type ->
        buildAction(type, map, reading)?.let { actions += it }
    }
    map.listValue("actions", reading)?.items?.forEach { item ->
        val actionMap = item as? YamlMap ?: run {
            reading.warning("`actions` items must be maps", item)
            return@forEach
        }
        val type = actionMap.string("type", reading) ?: run {
            reading.warning("Action entry needs a `type`", actionMap)
            return@forEach
        }
        buildAction(type, actionMap, reading)?.let { actions += it }
    }
    if (actions.isEmpty()) {
        reading.warning("Interaction has no action", map)
    }
    return InteractionPatch(
        trigger = map.enum("trigger", ReaderEnums.trigger, reading),
        key = map.string("key", reading),
        delayMs = map.double("delayMs", reading),
        variable = map.string("variable", reading),
        actions = actions,
    )
}

/**
 * One prototyping action. [fields] is either the action list entry or, for the flat
 * `action: openOverlay` form, the interaction map itself (destination/overlay/animation
 * are siblings there).
 */
private fun buildAction(type: String, fields: YamlMap, reading: BlockReading): DesignAction? {
    val transition = readTransition(fields.value("animation"), reading)
    return when (type) {
        "navigate" -> DesignAction.Navigate(
            to = fields.string("to", reading) ?: fields.string("destination", reading).orEmpty(),
            transition = transition,
        )
        "openOverlay" -> DesignAction.OpenOverlay(
            destination = fields.string("destination", reading).orEmpty(),
            overlay = readOverlay(fields.value("overlay"), reading),
            transition = transition,
        )
        "swapOverlay" -> DesignAction.SwapOverlay(
            destination = fields.string("destination", reading).orEmpty(),
            transition = transition,
        )
        "closeOverlay" -> DesignAction.CloseOverlay(transition)
        "back" -> DesignAction.Back
        "openLink" -> DesignAction.OpenLink(
            url = fields.string("url", reading) ?: fields.string("href", reading).orEmpty(),
        )
        "setVariable" -> DesignAction.SetVariable(
            variable = fields.string("variable", reading).orEmpty(),
            value = fields.value("value")
                ?.let { bindableString(it, "value", reading) }
                ?: "".bindable(),
        )
        "changeToVariant" -> DesignAction.ChangeToVariant(
            target = fields.string("target", reading).orEmpty(),
            variant = fields.mapValue("variant", reading)?.stringEntries(reading) ?: emptyMap(),
        )
        "scrollTo" -> DesignAction.ScrollTo(
            target = fields.string("target", reading).orEmpty(),
            animated = fields.boolean("animated", reading) ?: true,
        )
        "runActionSet" -> DesignAction.RunActionSet(
            actionSetId = fields.string("actionSet", reading)
                ?: fields.string("actionSetId", reading).orEmpty(),
        )
        else -> {
            reading.warning("Unknown action type \"$type\"", fields)
            DesignAction.Unknown(type)
        }
    }
}

private fun readOverlay(value: YamlValue?, reading: BlockReading): OverlaySettings {
    val map = value as? YamlMap ?: return OverlaySettings()
    map.warnUnknownKeys(setOf("position", "offset", "closeOnOutsideClick", "background"), reading)
    return OverlaySettings(
        position = map.enum("position", ReaderEnums.overlayPosition, reading)
            ?: io.aequicor.visualization.engine.ir.model.OverlayPosition.Center,
        offset = readPoint(map.value("offset"), reading),
        closeOnOutsideClick = map.boolean("closeOnOutsideClick", reading) ?: true,
        background = map.bindableColor("background", reading),
    )
}

private val transitionDirections = mapOf(
    "left" to TransitionDirection.Left,
    "right" to TransitionDirection.Right,
    "top" to TransitionDirection.Top,
    "bottom" to TransitionDirection.Bottom,
)

/** `animation: {type, easing, durationMs, direction, mass/stiffness/damping}`. */
private fun readTransition(value: YamlValue?, reading: BlockReading): DesignTransition? {
    val map = value as? YamlMap ?: return null
    map.warnUnknownKeys(
        setOf("type", "easing", "durationMs", "direction", "mass", "stiffness", "damping"),
        reading,
    )
    val easing = when (val name = map.string("easing", reading)) {
        null -> DesignEasing.Named(EasingKind.EaseOut)
        "spring" -> DesignEasing.Spring(
            mass = map.double("mass", reading) ?: 1.0,
            stiffness = map.double("stiffness", reading) ?: 100.0,
            damping = map.double("damping", reading) ?: 15.0,
        )
        else -> {
            val kind = ReaderEnums.easing[name]
            if (kind == null) {
                reading.warning("Unknown `easing` value \"$name\"", map)
            }
            DesignEasing.Named(kind ?: EasingKind.EaseOut)
        }
    }
    return DesignTransition(
        type = map.enum("type", ReaderEnums.transitionType, reading) ?: TransitionType.Instant,
        direction = map.enum("direction", transitionDirections, reading) ?: TransitionDirection.Left,
        easing = easing,
        durationMs = map.double("durationMs", reading) ?: 300.0,
    )
}

/** `motion:` block — external ref plus optional inline keyframe fallback. */
internal fun readMotionBlock(value: YamlValue, reading: BlockReading): MotionPatch? {
    val map = value as? YamlMap ?: run {
        reading.error("`motion` must be a map", value)
        return null
    }
    map.warnUnknownKeys(setOf("ref", "fallback"), reading)
    val fallback = map.mapValue("fallback", reading)?.let { readKeyframes(it, reading) }
    return MotionPatch(
        DesignMotion(
            ref = map.string("ref", reading).orEmpty(),
            fallback = fallback,
        ),
    )
}

private fun readKeyframes(map: YamlMap, reading: BlockReading): MotionKeyframes? {
    map.warnUnknownKeys(setOf("type", "durationMs", "loop", "frames"), reading)
    val type = map.string("type", reading)
    if (type != null && type != "keyframes") {
        reading.warning("Unknown motion fallback type \"$type\"", map)
        return null
    }
    val frames = map.listValue("frames", reading)?.items?.mapNotNull { item ->
        val frame = item as? YamlMap ?: run {
            reading.warning("`frames` items must be maps", item)
            return@mapNotNull null
        }
        val at = frame.double("at", reading) ?: run {
            reading.warning("Keyframe needs `at`", frame)
            return@mapNotNull null
        }
        val properties = frame.entries
            .filterKeys { it != "at" }
            .mapNotNull { (key, propValue) ->
                val number = (propValue as? YamlScalar)?.value as? Double
                if (number == null) {
                    reading.warning("Keyframe property `$key` must be a number", propValue)
                    null
                } else {
                    key to number
                }
            }
            .toMap()
        MotionFrame(at, properties)
    } ?: emptyList()
    return MotionKeyframes(
        durationMs = map.double("durationMs", reading) ?: 0.0,
        loop = map.boolean("loop", reading) ?: false,
        frames = frames,
    )
}
