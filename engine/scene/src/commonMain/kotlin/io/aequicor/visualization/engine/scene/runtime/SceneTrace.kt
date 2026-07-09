package io.aequicor.visualization.engine.scene.runtime

import io.aequicor.visualization.engine.ir.model.DesignAction
import io.aequicor.visualization.engine.ir.model.InteractionTrigger
import io.aequicor.visualization.engine.ir.model.TransitionType
import io.aequicor.visualization.engine.ir.model.VariableValue

/**
 * One deterministic, replay-verifiable trace record. Every entry is timestamped with the
 * timeline clock at which it happened, so the trace is self-describing: two runs from the same
 * initial state with the same events/deltas produce equal trace lists. Covers the spec's debug
 * questions — what event, what hit target, which trigger, which actions, which variables, which
 * transition, and *why an event was ignored*.
 */
sealed interface TraceEntry {
    val timeMs: Double

    data class EventReceived(override val timeMs: Double, val event: SceneEvent) : TraceEntry

    data class HitTarget(override val timeMs: Double, val nodeId: String) : TraceEntry

    data class TriggerMatched(
        override val timeMs: Double,
        val trigger: InteractionTrigger,
        val nodeId: String,
    ) : TraceEntry

    data class ActionExecuted(override val timeMs: Double, val action: DesignAction) : TraceEntry

    data class VariableSet(
        override val timeMs: Double,
        val name: String,
        val value: VariableValue,
    ) : TraceEntry

    data class TransitionStarted(
        override val timeMs: Double,
        val from: String,
        val to: String,
        val type: TransitionType,
    ) : TraceEntry

    data class TransitionEnded(override val timeMs: Double, val screenId: String) : TraceEntry

    /** Explains why an event produced no state change (spec: «почему событие проигнорировано»). */
    data class Ignored(override val timeMs: Double, val reason: String) : TraceEntry
}
