package io.aequicor.visualization.engine.scene.runtime

import io.aequicor.visualization.engine.ir.model.DesignAction
import io.aequicor.visualization.engine.ir.model.DesignDocument
import io.aequicor.visualization.engine.ir.model.DesignInteraction
import io.aequicor.visualization.engine.ir.model.DesignNode
import io.aequicor.visualization.engine.ir.model.DesignTransition
import io.aequicor.visualization.engine.ir.model.InteractionTrigger
import io.aequicor.visualization.engine.ir.model.TransitionType
import io.aequicor.visualization.engine.ir.model.VariableValue
import io.aequicor.visualization.engine.ir.model.literalOrNull
import io.aequicor.visualization.engine.ir.projection.ScreenComposer
import io.aequicor.visualization.engine.ir.resolve.ResolveContext

/**
 * The pure Scene interpreter. No Compose, no wall clock, no randomness: every "now" is an injected
 * delta ([advance]) or absolute time ([seek]); ids come from an injected [IdGenerator]. Therefore
 * the same session + same event/delta sequence yields the same [SceneStep] — deterministic replay.
 *
 * It reuses the SAME [ScreenComposer] (hence the same layout engine) as the Canvas projection, so
 * Scene snapshots and the edited screen share identical geometry.
 *
 * FUTURE-WORK (resolver prototypeState consumption): `SetVariable`/`ChangeToVariant` update the
 * runtime variable map and the trace but do not re-render, because the resolver does not yet
 * consume [ResolveContext.prototypeState]. Navigation and keyframe motion (the authored demo) do
 * not depend on it.
 */
class SceneRuntime(
    private val document: DesignDocument,
    private val composer: ScreenComposer,
    private val idGen: IdGenerator = SequentialIdGenerator(),
    private val deviceWidth: Double? = null,
    private val deviceHeight: Double? = null,
) {
    private val index get() = composer.index

    // --- Public API ---------------------------------------------------------

    fun start(entry: SceneEntry): SceneStep {
        val screenId = startScreenId(entry)
        val sceneId = when (entry) {
            is SceneEntry.Flow -> entry.flowId
            is SceneEntry.Screen -> "screen:${entry.screenId}"
        }
        val location = SceneLocation(screenId)
        val variables = seedVariables()
        val base = SceneRuntimeState(sceneId = sceneId, location = location)
        val (runtime, timeline) = buildStable(base, location, variables, emptyList(), 0.0, TimelineState())
        return SceneStep(SceneSession(runtime, timeline), emptyList())
    }

    fun dispatch(session: SceneSession, event: SceneEvent): SceneStep {
        val now = session.timeline.currentTimeMs
        return when (event) {
            is SceneEvent.Pointer -> when (event.kind) {
                PointerKind.Click -> activate(session, event.nodeId, now)
                PointerKind.HoverEnter, PointerKind.Move ->
                    SceneStep(session.withInput { it.copy(hoveredNodeId = event.nodeId) }, emptyList())
                PointerKind.HoverExit ->
                    SceneStep(session.withInput { it.copy(hoveredNodeId = "") }, emptyList())
                PointerKind.PressDown ->
                    SceneStep(session.withInput { it.copy(pressedNodeId = event.nodeId) }, emptyList())
                PointerKind.PressUp ->
                    SceneStep(session.withInput { it.copy(pressedNodeId = "") }, emptyList())
                PointerKind.DragStart ->
                    SceneStep(session.withInput { it.copy(draggingNodeId = event.nodeId) }, emptyList())
            }
            is SceneEvent.Key -> SceneStep(
                session,
                listOf(TraceEntry.EventReceived(now, event), TraceEntry.Ignored(now, "key routing not in MVP")),
            )
            is SceneEvent.VariableChanged -> SceneStep(session, emptyList())
            SceneEvent.Tick -> SceneStep(session, emptyList())
        }
    }

    fun advance(session: SceneSession, deltaMs: Double): SceneStep {
        if (deltaMs <= 0.0) return SceneStep(session, emptyList())
        val newTime = session.timeline.currentTimeMs + deltaMs * session.timeline.speed
        val trace = mutableListOf<TraceEntry>()
        var runtime = session.runtime
        var timeline = session.timeline.copy(currentTimeMs = newTime)

        if (runtime.phase is RuntimePhase.Stable) {
            val due = runtime.armedTimers.filter { it.fireAtMs <= newTime }
            if (due.isNotEmpty()) {
                runtime = runtime.copy(armedTimers = runtime.armedTimers - due.toSet())
                for (timer in due) {
                    val (outcome, actionTrace) = interpretActions(
                        runtime.location, runtime.variables, runtime.history, timer.actions, newTime,
                    )
                    trace += actionTrace
                    val started = beginTransitionIfAnimated(runtime, timeline, outcome, newTime)
                    if (started != null) {
                        runtime = started.first; timeline = started.second; trace += started.third
                        break
                    }
                    val (rt, tl) = buildStable(runtime, outcome.location, outcome.variables, outcome.history, newTime, timeline)
                    runtime = rt; timeline = tl
                }
            }
        }

        val phase = runtime.phase
        if (phase is RuntimePhase.Transitioning && newTime >= phase.instance.endTimeMs) {
            val inst = phase.instance
            val (rt, tl) = buildStable(runtime, inst.targetLocation, inst.targetVariables, inst.targetHistory, newTime, timeline)
            runtime = rt; timeline = tl
            trace += TraceEntry.TransitionEnded(newTime, inst.targetLocation.screenId)
        }
        return SceneStep(SceneSession(runtime, timeline), trace)
    }

    fun seek(session: SceneSession, timeMs: Double): SceneStep {
        return when (val phase = session.runtime.phase) {
            RuntimePhase.Stable -> {
                val clamped = timeMs.coerceAtLeast(0.0)
                SceneStep(session.copy(timeline = session.timeline.copy(currentTimeMs = clamped, playback = PlaybackState.Paused)), emptyList())
            }
            is RuntimePhase.Transitioning -> {
                val inst = phase.instance
                val clamped = timeMs.coerceIn(inst.startTimeMs, inst.endTimeMs)
                if (clamped >= inst.endTimeMs) {
                    val (rt, tl) = buildStable(session.runtime, inst.targetLocation, inst.targetVariables, inst.targetHistory, clamped, session.timeline)
                    SceneStep(
                        SceneSession(rt, tl.copy(playback = PlaybackState.Paused)),
                        listOf(TraceEntry.TransitionEnded(clamped, inst.targetLocation.screenId)),
                    )
                } else {
                    SceneStep(session.copy(timeline = session.timeline.copy(currentTimeMs = clamped, playback = PlaybackState.Paused)), emptyList())
                }
            }
        }
    }

    fun restart(session: SceneSession): SceneStep = start(entryOf(session.runtime))

    fun play(t: TimelineState): TimelineState = t.copy(playback = PlaybackState.Playing)
    fun pause(t: TimelineState): TimelineState = t.copy(playback = PlaybackState.Paused)
    fun setSpeed(t: TimelineState, speed: Double): TimelineState = t.copy(speed = speed.coerceIn(0.05, 4.0))

    // --- Activation (spec's from/to transition flow) ------------------------

    private fun activate(session: SceneSession, nodeId: String, now: Double): SceneStep {
        val trace = mutableListOf<TraceEntry>(TraceEntry.EventReceived(now, SceneEvent.Pointer(PointerKind.Click, nodeId)))
        // 1. Input gate — no input reaches the scene during a transition (MVP policy).
        if (session.runtime.phase is RuntimePhase.Transitioning) {
            trace += TraceEntry.Ignored(now, "input blocked during transition")
            return SceneStep(session, trace)
        }
        trace += TraceEntry.HitTarget(now, nodeId)
        val runtime = session.runtime
        val match = interactionForNode(runtime.location.screenId, nodeId)
        if (match == null) {
            trace += TraceEntry.Ignored(now, "no interaction on '$nodeId'")
            return SceneStep(session, trace)
        }
        val (interaction, ownerId) = match
        trace += TraceEntry.TriggerMatched(now, interaction.trigger, ownerId)

        val from = runtime.stableSnapshot ?: snapshot(runtime.location, runtime.variables)
        if (from == null) {
            trace += TraceEntry.Ignored(now, "cannot compose current screen '${runtime.location.screenId}'")
            return SceneStep(session, trace)
        }
        val (outcome, actionTrace) = interpretActions(runtime.location, runtime.variables, runtime.history, interaction.actions, now)
        trace += actionTrace

        val started = beginTransition(runtime, session.timeline, from, outcome, now)
        if (started != null) {
            trace += started.third
            return SceneStep(SceneSession(started.first, started.second), trace)
        }
        // No animated transition → commit the logical outcome immediately.
        val (rt, tl) = buildStable(runtime, outcome.location, outcome.variables, outcome.history, now, session.timeline)
        return SceneStep(SceneSession(rt, tl), trace)
    }

    /** Starts a transition when the outcome is an animated screen change; else null. */
    private fun beginTransition(
        runtime: SceneRuntimeState,
        timeline: TimelineState,
        from: SceneSnapshot,
        outcome: LogicalOutcome,
        now: Double,
    ): Triple<SceneRuntimeState, TimelineState, List<TraceEntry>>? {
        val spec = outcome.transitionSpec
        val locationChanged = outcome.location != runtime.location
        if (spec == null || spec.type == TransitionType.Instant || spec.durationMs <= 0.0 || !locationChanged) return null
        val to = snapshot(outcome.location, outcome.variables) ?: return null
        val instance = TransitionInstance(
            id = idGen.next("tr"),
            from = from,
            to = to,
            spec = spec,
            startTimeMs = now,
            targetLocation = outcome.location,
            targetVariables = outcome.variables,
            targetHistory = outcome.history,
        )
        val anim = ActiveAnimation(
            id = idGen.next("tra"), kind = AnimationKind.Transition,
            startTimeMs = now, durationMs = spec.durationMs,
        )
        val nextRuntime = runtime.copy(phase = RuntimePhase.Transitioning(instance))
        val nextTimeline = timeline.copy(
            playback = PlaybackState.Playing,
            currentTimeMs = now,
            durationMs = spec.durationMs,
            activeAnimations = listOf(anim),
        )
        val trace = listOf(TraceEntry.TransitionStarted(now, from.screenId, to.screenId, spec.type))
        return Triple(nextRuntime, nextTimeline, trace)
    }

    private fun beginTransitionIfAnimated(
        runtime: SceneRuntimeState,
        timeline: TimelineState,
        outcome: LogicalOutcome,
        now: Double,
    ): Triple<SceneRuntimeState, TimelineState, List<TraceEntry>>? {
        val from = runtime.stableSnapshot ?: snapshot(runtime.location, runtime.variables) ?: return null
        return beginTransition(runtime, timeline, from, outcome, now)
    }

    // --- Logical action interpretation --------------------------------------

    private data class LogicalOutcome(
        val location: SceneLocation,
        val variables: Map<String, VariableValue>,
        val history: List<SceneLocation>,
        val transitionSpec: DesignTransition?,
    )

    private fun interpretActions(
        location: SceneLocation,
        variables: Map<String, VariableValue>,
        history: List<SceneLocation>,
        actions: List<DesignAction>,
        timeMs: Double,
    ): Pair<LogicalOutcome, List<TraceEntry>> {
        var loc = location
        var vars = variables
        var hist = history
        var spec: DesignTransition? = null
        val trace = mutableListOf<TraceEntry>()

        fun run(list: List<DesignAction>, depth: Int) {
            if (depth > 8) return
            for (action in list) {
                when (action) {
                    is DesignAction.Navigate -> {
                        val target = index.screenIdFor(action.to)
                        if (target.isBlank()) {
                            trace += TraceEntry.Ignored(timeMs, "unknown screen '${action.to}'")
                        } else {
                            hist = hist + loc
                            loc = SceneLocation(target)
                            spec = action.transition ?: spec
                        }
                        trace += TraceEntry.ActionExecuted(timeMs, action)
                    }
                    is DesignAction.OpenOverlay -> {
                        val dest = index.screenIdFor(action.destination).ifBlank { action.destination }
                        loc = loc.copy(overlayStack = loc.overlayStack + OverlayFrame(dest, action.overlay))
                        spec = action.transition ?: spec
                        trace += TraceEntry.ActionExecuted(timeMs, action)
                    }
                    is DesignAction.SwapOverlay -> {
                        if (loc.overlayStack.isNotEmpty()) {
                            val dest = index.screenIdFor(action.destination).ifBlank { action.destination }
                            val top = loc.overlayStack.last()
                            loc = loc.copy(overlayStack = loc.overlayStack.dropLast(1) + OverlayFrame(dest, top.settings))
                            spec = action.transition ?: spec
                        }
                        trace += TraceEntry.ActionExecuted(timeMs, action)
                    }
                    is DesignAction.CloseOverlay -> {
                        if (loc.overlayStack.isNotEmpty()) loc = loc.copy(overlayStack = loc.overlayStack.dropLast(1))
                        spec = action.transition ?: spec
                        trace += TraceEntry.ActionExecuted(timeMs, action)
                    }
                    DesignAction.Back -> {
                        if (hist.isNotEmpty()) {
                            loc = hist.last()
                            hist = hist.dropLast(1)
                        }
                        trace += TraceEntry.ActionExecuted(timeMs, action)
                    }
                    is DesignAction.SetVariable -> {
                        val value = VariableValue.TextValue(action.value.literalOrNull() ?: "")
                        vars = vars + (action.variable to value)
                        trace += TraceEntry.VariableSet(timeMs, action.variable, value)
                        trace += TraceEntry.ActionExecuted(timeMs, action)
                    }
                    is DesignAction.RunActionSet -> {
                        trace += TraceEntry.ActionExecuted(timeMs, action)
                        document.actionSets[action.actionSetId]?.let { run(it, depth + 1) }
                    }
                    // FUTURE-WORK (resolver prototypeState): recorded, no visual effect yet.
                    is DesignAction.ChangeToVariant -> trace += TraceEntry.ActionExecuted(timeMs, action)
                    is DesignAction.ScrollTo -> trace += TraceEntry.ActionExecuted(timeMs, action)
                    is DesignAction.OpenLink -> trace += TraceEntry.ActionExecuted(timeMs, action)
                    is DesignAction.Unknown -> trace += TraceEntry.ActionExecuted(timeMs, action)
                }
            }
        }
        run(actions, 0)
        return LogicalOutcome(loc, vars, hist, spec) to trace
    }

    // --- Composition helpers ------------------------------------------------

    private fun startScreenId(entry: SceneEntry): String = when (entry) {
        is SceneEntry.Flow -> {
            val flow = document.screen?.flow?.takeIf { it.id == entry.flowId }
            val target = flow?.node?.takeIf { it.isNotBlank() }.orEmpty()
            index.screenIdFor(target).ifBlank { document.pages.firstOrNull()?.id.orEmpty() }
        }
        is SceneEntry.Screen -> index.screenIdFor(entry.screenId).ifBlank { entry.screenId }
    }

    private fun entryOf(runtime: SceneRuntimeState): SceneEntry =
        if (runtime.sceneId.startsWith("screen:")) {
            SceneEntry.Screen(runtime.sceneId.removePrefix("screen:"))
        } else {
            SceneEntry.Flow(runtime.sceneId)
        }

    private fun seedVariables(): Map<String, VariableValue> =
        document.prototypeVariables.mapNotNull { (name, proto) -> proto.default?.let { name to it } }.toMap()

    private fun snapshot(location: SceneLocation, variables: Map<String, VariableValue>): SceneSnapshot? {
        val composed = composer.compose(
            location.screenId, deviceWidth, deviceHeight,
            ResolveContext(prototypeState = variables),
        ) ?: return null
        return SceneSnapshot(
            snapshotId = idGen.next("snap"),
            screenId = location.screenId,
            composed = composed,
            variables = variables,
            overlays = location.overlayStack,
        )
    }

    private fun buildStable(
        prev: SceneRuntimeState,
        location: SceneLocation,
        variables: Map<String, VariableValue>,
        history: List<SceneLocation>,
        atTimeMs: Double,
        prevTimeline: TimelineState,
    ): Pair<SceneRuntimeState, TimelineState> {
        val snap = snapshot(location, variables)
        val motion = motionAnimationsFor(location.screenId, atTimeMs)
        // Arm AfterDelay timers only when entering a screen (start, or a screen change). On a
        // same-screen re-commit (e.g. after a non-navigating afterDelay fired) keep the surviving
        // timers so a one-shot timer does not re-arm and re-fire every period.
        val enteredScreen = prev.stableSnapshot == null || location.screenId != prev.location.screenId
        val timers = if (enteredScreen) armTimersFor(location.screenId, atTimeMs) else prev.armedTimers
        val runtime = prev.copy(
            location = location,
            variables = variables,
            history = history,
            phase = RuntimePhase.Stable,
            armedTimers = timers,
            stableSnapshot = snap,
        )
        val timeline = TimelineState(
            currentTimeMs = atTimeMs,
            // Keep the clock running while any time-based work is pending — motion clips OR armed
            // timers. The clock host only advances when Playing, so an armed AfterDelay timer on an
            // otherwise-static screen must keep playback Playing or it would never fire.
            playback = if (motion.isNotEmpty() || timers.isNotEmpty()) PlaybackState.Playing else PlaybackState.Idle,
            speed = prevTimeline.speed,
            durationMs = motion.maxOfOrNull { it.durationMs } ?: 0.0,
            activeAnimations = motion,
        )
        return runtime to timeline
    }

    private fun motionAnimationsFor(screenId: String, atTimeMs: Double): List<ActiveAnimation> {
        val root = index.rootFrameFor(screenId) ?: return emptyList()
        return screenNodes(root).mapNotNull { node ->
            val frames = node.motion?.fallback ?: return@mapNotNull null
            if (frames.frames.isEmpty() || frames.durationMs <= 0.0) return@mapNotNull null
            ActiveAnimation(
                id = idGen.next("mot"),
                kind = AnimationKind.Motion,
                nodeId = node.id,
                keyframes = frames,
                startTimeMs = atTimeMs,
                durationMs = frames.durationMs,
                loop = frames.loop,
            )
        }
    }

    private fun armTimersFor(screenId: String, atTimeMs: Double): List<ArmedTimer> {
        val root = index.rootFrameFor(screenId) ?: return emptyList()
        return screenNodes(root).flatMap { node ->
            node.interactions.filter { it.trigger == InteractionTrigger.AfterDelay }.map { interaction ->
                ArmedTimer(node.id, atTimeMs + (interaction.delayMs ?: 0.0), interaction.actions)
            }
        }
    }

    private fun interactionForNode(screenId: String, nodeId: String): Pair<DesignInteraction, String>? {
        val root = index.rootFrameFor(screenId) ?: return null
        val path = pathToNode(root, nodeId) ?: return null
        for (node in path.asReversed()) {
            val interaction = node.interactions.firstOrNull {
                it.trigger == InteractionTrigger.OnClick || it.trigger == InteractionTrigger.OnPress
            }
            if (interaction != null) return interaction to node.id
        }
        return null
    }

    private fun screenNodes(root: DesignNode): List<DesignNode> = listOf(root) + root.allDescendants()

    private fun pathToNode(root: DesignNode, id: String): List<DesignNode>? {
        if (root.id == id) return listOf(root)
        for (child in root.children) {
            pathToNode(child, id)?.let { return listOf(root) + it }
        }
        return null
    }
}

private fun SceneSession.withInput(transform: (SceneInputState) -> SceneInputState): SceneSession =
    copy(runtime = runtime.copy(input = transform(runtime.input)))
