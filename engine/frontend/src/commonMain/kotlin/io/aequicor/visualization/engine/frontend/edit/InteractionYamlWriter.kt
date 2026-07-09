package io.aequicor.visualization.engine.frontend.edit

import io.aequicor.visualization.engine.ir.model.DesignAction
import io.aequicor.visualization.engine.ir.model.DesignEasing
import io.aequicor.visualization.engine.ir.model.DesignInteraction
import io.aequicor.visualization.engine.ir.model.DesignTransition
import io.aequicor.visualization.engine.ir.model.EasingKind
import io.aequicor.visualization.engine.ir.model.InteractionTrigger
import io.aequicor.visualization.engine.ir.model.OverlayPosition
import io.aequicor.visualization.engine.ir.model.OverlaySettings
import io.aequicor.visualization.engine.ir.model.TransitionDirection
import io.aequicor.visualization.engine.ir.model.TransitionType
import io.aequicor.visualization.engine.ir.model.literalOrNull

/**
 * Serializes a [DesignInteraction] back into a [YamlPayload.Mapping] for surgical write-back — the
 * faithful inverse of `InteractionMotionReader`. The `actions:` list form is always used (a single
 * code path that round-trips one or many actions). Default attributes the reader supplies
 * (`direction: left`, spring `mass: 1.0`) are omitted so re-reading yields the same IR.
 *
 * Returns `null` when the interaction cannot round-trip through the reader — a `CubicBezier` easing
 * (no reader path), a `DesignAction.Unknown`, a non-literal `SetVariable` value, or an overlay
 * carrying `offset`/`background` — so the caller falls back to an in-memory-only edit.
 */
internal object InteractionYamlWriter {

    fun interaction(interaction: DesignInteraction): YamlPayload.Mapping? {
        val actions = interaction.actions.map { actionPayload(it) ?: return null }
        return YamlPayload.Mapping(
            buildList {
                add("trigger" to str(triggerToken(interaction.trigger)))
                if (interaction.key.isNotEmpty()) add("key" to str(interaction.key))
                interaction.delayMs?.let { add("delayMs" to numOf(it)) }
                if (interaction.variable.isNotEmpty()) add("variable" to str(interaction.variable))
                add("actions" to YamlPayload.Sequence(actions))
            },
        )
    }

    private fun actionPayload(action: DesignAction): YamlPayload.Mapping? = when (action) {
        // A non-null transition that itself is inexpressible (CubicBezier) fails the whole action —
        // never silently drop the animation — so the caller falls back in-memory.
        is DesignAction.Navigate -> {
            val animation = action.transition?.let { transition(it) ?: return null }
            typed(
                "navigate",
                buildList {
                    add("to" to str(action.to))
                    animation?.let { add("animation" to it) }
                },
            )
        }
        is DesignAction.OpenOverlay -> {
            if (!overlayExpressible(action.overlay)) return null
            val animation = action.transition?.let { transition(it) ?: return null }
            typed(
                "openOverlay",
                buildList {
                    add("destination" to str(action.destination))
                    overlayPayload(action.overlay)?.let { add("overlay" to it) }
                    animation?.let { add("animation" to it) }
                },
            )
        }
        is DesignAction.SwapOverlay -> {
            val animation = action.transition?.let { transition(it) ?: return null }
            typed(
                "swapOverlay",
                buildList {
                    add("destination" to str(action.destination))
                    animation?.let { add("animation" to it) }
                },
            )
        }
        is DesignAction.CloseOverlay -> {
            val animation = action.transition?.let { transition(it) ?: return null }
            typed("closeOverlay", animation?.let { listOf("animation" to it) } ?: emptyList())
        }
        DesignAction.Back -> typed("back", emptyList())
        is DesignAction.OpenLink -> typed("openLink", listOf("url" to str(action.url)))
        is DesignAction.SetVariable -> {
            val value = action.value.literalOrNull() ?: return null
            typed("setVariable", listOf("variable" to str(action.variable), "value" to str(value)))
        }
        is DesignAction.ChangeToVariant -> typed(
            "changeToVariant",
            listOf(
                "target" to str(action.target),
                "variant" to YamlPayload.Mapping(action.variant.map { (k, v) -> k to str(v) }),
            ),
        )
        is DesignAction.ScrollTo -> typed(
            "scrollTo",
            buildList {
                add("target" to str(action.target))
                if (!action.animated) add("animated" to bool(false))
            },
        )
        is DesignAction.RunActionSet -> typed("runActionSet", listOf("actionSet" to str(action.actionSetId)))
        is DesignAction.Unknown -> null
    }

    /** null → the transition uses an unwritable easing (`CubicBezier`); the caller then fails. */
    private fun transition(spec: DesignTransition): YamlPayload.Mapping? {
        val easing: List<Pair<String, YamlPayload>> = when (val e = spec.easing) {
            is DesignEasing.Named -> listOf("easing" to str(easingToken(e.kind)))
            is DesignEasing.Spring -> buildList {
                add("easing" to str("spring"))
                if (e.mass != 1.0) add("mass" to numOf(e.mass))
                add("stiffness" to numOf(e.stiffness))
                add("damping" to numOf(e.damping))
            }
            is DesignEasing.CubicBezier -> return null
        }
        return YamlPayload.Mapping(
            buildList {
                add("type" to str(transitionTypeToken(spec.type)))
                if (spec.direction != TransitionDirection.Left) add("direction" to str(directionToken(spec.direction)))
                addAll(easing)
                add("durationMs" to numOf(spec.durationMs))
            },
        )
    }

    /** false → the overlay carries geometry the writer defers (`offset`/`background`) → action fails. */
    private fun overlayExpressible(overlay: OverlaySettings): Boolean =
        overlay.offset == null && overlay.background == null

    /** null → no non-default overlay keys to emit; the `overlay:` key is then omitted. */
    private fun overlayPayload(overlay: OverlaySettings): YamlPayload.Mapping? {
        val entries = buildList {
            if (overlay.position != OverlayPosition.Center) add("position" to str(overlayPositionToken(overlay.position)))
            if (!overlay.closeOnOutsideClick) add("closeOnOutsideClick" to bool(false))
        }
        return if (entries.isEmpty()) null else YamlPayload.Mapping(entries)
    }

    private fun typed(type: String, rest: List<Pair<String, YamlPayload>>): YamlPayload.Mapping =
        YamlPayload.Mapping(listOf("type" to str(type)) + rest)

    private fun triggerToken(trigger: InteractionTrigger): String = when (trigger) {
        InteractionTrigger.OnClick -> "onClick"
        InteractionTrigger.OnHover -> "onHover"
        InteractionTrigger.OnPress -> "onPress"
        InteractionTrigger.OnDrag -> "onDrag"
        InteractionTrigger.OnKey -> "onKey"
        InteractionTrigger.AfterDelay -> "afterDelay"
        InteractionTrigger.WhileHovering -> "whileHovering"
        InteractionTrigger.WhilePressed -> "whilePressed"
        InteractionTrigger.OnVariableChange -> "onVariableChange"
    }

    private fun transitionTypeToken(type: TransitionType): String = when (type) {
        TransitionType.Instant -> "instant"
        TransitionType.Dissolve -> "dissolve"
        TransitionType.SmartAnimate -> "smartAnimate"
        TransitionType.MoveIn -> "moveIn"
        TransitionType.MoveOut -> "moveOut"
        TransitionType.Push -> "push"
        TransitionType.SlideIn -> "slideIn"
        TransitionType.SlideOut -> "slideOut"
    }

    private fun directionToken(direction: TransitionDirection): String = when (direction) {
        TransitionDirection.Left -> "left"
        TransitionDirection.Right -> "right"
        TransitionDirection.Top -> "top"
        TransitionDirection.Bottom -> "bottom"
    }

    private fun easingToken(kind: EasingKind): String = when (kind) {
        EasingKind.Linear -> "linear"
        EasingKind.EaseIn -> "easeIn"
        EasingKind.EaseOut -> "easeOut"
        EasingKind.EaseInOut -> "easeInOut"
        EasingKind.EaseInBack -> "easeInBack"
        EasingKind.EaseOutBack -> "easeOutBack"
    }

    private fun overlayPositionToken(position: OverlayPosition): String = when (position) {
        OverlayPosition.Center -> "center"
        OverlayPosition.TopLeft -> "topLeft"
        OverlayPosition.TopCenter -> "topCenter"
        OverlayPosition.TopRight -> "topRight"
        OverlayPosition.BottomLeft -> "bottomLeft"
        OverlayPosition.BottomCenter -> "bottomCenter"
        OverlayPosition.BottomRight -> "bottomRight"
        OverlayPosition.Manual -> "manual"
    }

    private fun str(value: String): YamlPayload = YamlPayload.Scalar(YamlScalarValue.Str(value))
    private fun bool(value: Boolean): YamlPayload = YamlPayload.Scalar(YamlScalarValue.Bool(value))
    private fun numOf(value: Double): YamlPayload = YamlPayload.Scalar(YamlScalarValue.Num(value))
}

/**
 * Whether [interaction] can be written back into SLM (no `CubicBezier` easing, `Unknown` action,
 * bound `SetVariable` value, or overlay geometry). Public probe used by structural write-back to
 * keep a subtree carrying an inexpressible interaction from persisting a behavior-stripped section.
 */
fun isInteractionExpressibleInSlm(interaction: DesignInteraction): Boolean =
    InteractionYamlWriter.interaction(interaction) != null
