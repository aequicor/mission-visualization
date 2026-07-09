package io.aequicor.visualization.editor.presentation

import io.aequicor.visualization.engine.ir.model.DesignAction
import io.aequicor.visualization.engine.ir.model.DesignEasing
import io.aequicor.visualization.engine.ir.model.DesignInteraction
import io.aequicor.visualization.engine.ir.model.DesignMotion
import io.aequicor.visualization.engine.ir.model.DesignTransition
import io.aequicor.visualization.engine.ir.model.EasingKind
import io.aequicor.visualization.engine.ir.model.InteractionTrigger
import io.aequicor.visualization.engine.ir.model.MotionFrame
import io.aequicor.visualization.engine.ir.model.MotionKeyframes
import io.aequicor.visualization.engine.ir.model.TransitionDirection
import io.aequicor.visualization.engine.ir.model.TransitionType

/** The transition a freshly-authored Navigate starts with (a gentle left Push). */
val DefaultProtoTransition: DesignTransition = DesignTransition(
    type = TransitionType.Push,
    direction = TransitionDirection.Left,
    easing = DesignEasing.Named(EasingKind.EaseOut),
    durationMs = 300.0,
)

private const val DefaultDelayMs: Double = 800.0

/**
 * Applies one [InteractionOp] to a node's whole interaction list, purely. [defaultTarget] seeds a
 * fresh Navigate (the first other screen); [defaultTransition] its animation. Out-of-range indices
 * are no-ops, so a stale UI event can never corrupt the list.
 */
fun applyInteractionOp(
    interactions: List<DesignInteraction>,
    op: InteractionOp,
    defaultTarget: String,
    defaultTransition: DesignTransition = DefaultProtoTransition,
): List<DesignInteraction> {
    fun navigate(target: String = defaultTarget, transition: DesignTransition = defaultTransition) =
        DesignInteraction(trigger = InteractionTrigger.OnClick, actions = listOf(DesignAction.Navigate(target, transition)))

    return when (op) {
        is InteractionOp.Add -> interactions + navigate()
        is InteractionOp.AddNavigate ->
            interactions + DesignInteraction(InteractionTrigger.OnClick, actions = listOf(DesignAction.Navigate(op.target, op.transition)))
        is InteractionOp.RemoveAt -> interactions.removeIndex(op.i)
        is InteractionOp.SetTrigger -> interactions.updated(op.i) { interaction ->
            val delay = if (op.trigger == InteractionTrigger.AfterDelay) (op.delayMs ?: interaction.delayMs ?: DefaultDelayMs) else null
            interaction.copy(trigger = op.trigger, delayMs = delay)
        }
        is InteractionOp.AddAction -> interactions.updated(op.i) { interaction ->
            interaction.copy(actions = interaction.actions + DesignAction.Navigate(defaultTarget, defaultTransition))
        }
        is InteractionOp.RemoveAction -> interactions.updated(op.i) { interaction ->
            interaction.copy(actions = interaction.actions.removeIndex(op.j))
        }
        is InteractionOp.SetActionType -> interactions.updated(op.i) { interaction ->
            interaction.copy(actions = interaction.actions.updated(op.j) { previous -> makeAction(op.kind, previous, defaultTarget, defaultTransition) })
        }
        is InteractionOp.SetActionTarget -> interactions.updated(op.i) { interaction ->
            interaction.copy(actions = interaction.actions.updated(op.j) { action -> if (action is DesignAction.Navigate) action.copy(to = op.target) else action })
        }
        is InteractionOp.SetActionTransition -> interactions.updated(op.i) { interaction ->
            interaction.copy(actions = interaction.actions.updated(op.j) { action -> action.withTransition(op.transition) })
        }
    }
}

/** Applies one [MotionOp] to a node's single motion clip, purely. */
fun applyMotionOp(motion: DesignMotion?, op: MotionOp): DesignMotion? = when (op) {
    is MotionOp.SetEnabled -> if (op.enabled) motion ?: DesignMotion(fallback = presetKeyframes(MotionPreset.Pulse)) else null
    is MotionOp.SetPreset -> DesignMotion(fallback = presetKeyframes(op.preset))
    is MotionOp.SetDuration -> motion?.withFallback { it.copy(durationMs = op.ms) }
    is MotionOp.SetLoop -> motion?.withFallback { it.copy(loop = op.loop) }
    is MotionOp.SetKeyframes -> DesignMotion(fallback = op.keyframes)
}

/** Canned keyframes for a motion preset (inline, never a bare `ref`, so the runtime plays it). */
fun presetKeyframes(preset: MotionPreset): MotionKeyframes = when (preset) {
    MotionPreset.FadeIn -> keyframes(400.0, loop = false, frame(0.0, "opacity" to 0.0), frame(1.0, "opacity" to 1.0))
    MotionPreset.Pop -> keyframes(300.0, loop = false, frame(0.0, "scale" to 0.8), frame(0.6, "scale" to 1.05), frame(1.0, "scale" to 1.0))
    MotionPreset.Float -> keyframes(1600.0, loop = true, frame(0.0, "y" to 0.0), frame(0.5, "y" to -6.0), frame(1.0, "y" to 0.0))
    MotionPreset.Pulse -> keyframes(900.0, loop = true, frame(0.0, "opacity" to 0.4), frame(0.5, "opacity" to 1.0), frame(1.0, "opacity" to 0.4))
    MotionPreset.Spin -> keyframes(1200.0, loop = true, frame(0.0, "rotation" to 0.0), frame(1.0, "rotation" to 360.0))
}

private fun makeAction(
    kind: ProtoActionKind,
    previous: DesignAction,
    defaultTarget: String,
    defaultTransition: DesignTransition,
): DesignAction = when (kind) {
    ProtoActionKind.Navigate -> previous as? DesignAction.Navigate ?: DesignAction.Navigate(defaultTarget, defaultTransition)
    ProtoActionKind.Back -> DesignAction.Back
}

private fun DesignAction.withTransition(transition: DesignTransition): DesignAction = when (this) {
    is DesignAction.Navigate -> copy(transition = transition)
    is DesignAction.OpenOverlay -> copy(transition = transition)
    is DesignAction.SwapOverlay -> copy(transition = transition)
    is DesignAction.CloseOverlay -> copy(transition = transition)
    else -> this
}

private fun DesignMotion.withFallback(transform: (MotionKeyframes) -> MotionKeyframes): DesignMotion =
    fallback?.let { copy(fallback = transform(it)) } ?: this

private fun keyframes(durationMs: Double, loop: Boolean, vararg frames: MotionFrame): MotionKeyframes =
    MotionKeyframes(durationMs = durationMs, loop = loop, frames = frames.toList())

private fun frame(at: Double, vararg properties: Pair<String, Double>): MotionFrame =
    MotionFrame(at = at, properties = properties.toMap())

private inline fun <T> List<T>.updated(index: Int, transform: (T) -> T): List<T> =
    if (index in indices) mapIndexed { i, value -> if (i == index) transform(value) else value } else this

private fun <T> List<T>.removeIndex(index: Int): List<T> =
    if (index in indices) filterIndexed { i, _ -> i != index } else this
