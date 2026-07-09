package io.aequicor.visualization.engine.scene.projection

import io.aequicor.visualization.engine.ir.projection.ScreenComposer
import io.aequicor.visualization.engine.ir.resolve.ResolveContext
import io.aequicor.visualization.engine.scene.runtime.RuntimePhase
import io.aequicor.visualization.engine.scene.runtime.SceneLocation
import io.aequicor.visualization.engine.scene.runtime.SceneRuntimeState
import io.aequicor.visualization.engine.scene.runtime.SceneSession
import io.aequicor.visualization.engine.scene.runtime.SceneSnapshot
import io.aequicor.visualization.engine.scene.runtime.TimelineState
import io.aequicor.visualization.engine.scene.runtime.TransitionInstance
import io.aequicor.visualization.engine.scene.sample.EasingSampler
import io.aequicor.visualization.engine.scene.sample.MotionSampler
import io.aequicor.visualization.engine.scene.sample.NodeOverride
import io.aequicor.visualization.engine.scene.sample.TransitionSampler

/**
 * The Scene sibling of `CanvasProjection`: builds a [SceneRenderModel] purely from a [SceneSession].
 * A stable frame is one screen layer (plus any overlays); a transition frame is two screen layers
 * with distinct sampled overrides — the multi-layer frame the spec requires. It reads the runtime's
 * `location`, never a Canvas `selectedPageId`, and is fully recomputable from the session — never a
 * third source of truth.
 */
class SceneProjection(private val composer: ScreenComposer) {

    fun project(session: SceneSession): SceneRenderModel = when (val phase = session.runtime.phase) {
        RuntimePhase.Stable -> projectStable(session.runtime, session.timeline)
        is RuntimePhase.Transitioning -> projectTransition(phase.instance, session.runtime, session.timeline)
    }

    private fun projectStable(runtime: SceneRuntimeState, timeline: TimelineState): SceneRenderModel {
        val layers = mutableListOf<SceneLayer>()
        val screen = runtime.stableSnapshot ?: composeSnapshot(runtime.location)
        if (screen != null) {
            layers += SceneLayer(
                id = "screen:${screen.screenId}",
                role = SceneLayerRole.Screen,
                snapshot = screen,
                nodeOverrides = sampleMotion(timeline),
                zIndex = 0,
                hitTestable = false,
            )
        }
        runtime.location.overlayStack.forEachIndexed { i, overlay ->
            val composed = composer.compose(overlay.destinationScreenId) ?: return@forEachIndexed
            val snapshot = SceneSnapshot(
                snapshotId = "overlay_${i}_${overlay.destinationScreenId}",
                screenId = overlay.destinationScreenId,
                composed = composed,
                variables = runtime.variables,
            )
            layers += SceneLayer(
                id = "overlay:${overlay.destinationScreenId}",
                role = SceneLayerRole.Overlay,
                snapshot = snapshot,
                zIndex = layers.size,
                hitTestable = false,
            )
        }
        // Only the topmost visible layer receives input in a stable frame.
        val routed = layers.mapIndexed { index, layer -> layer.copy(hitTestable = index == layers.lastIndex) }
        val debug = SceneDebugModel(
            currentScreenId = runtime.location.screenId,
            overlayScreenIds = runtime.location.overlayStack.map { it.destinationScreenId },
            activeHitTargetId = runtime.input.hoveredNodeId,
            activeAnimationIds = timeline.activeAnimations.map { it.id },
        )
        return SceneRenderModel(routed, debug, timeline.currentTimeMs)
    }

    private fun projectTransition(
        instance: TransitionInstance,
        runtime: SceneRuntimeState,
        timeline: TimelineState,
    ): SceneRenderModel {
        val duration = instance.spec.durationMs.coerceAtLeast(1e-6)
        val linear = ((timeline.currentTimeMs - instance.startTimeMs) / duration).coerceIn(0.0, 1.0)
        val eased = EasingSampler.evaluate(instance.spec.easing, linear)
        val viewportW = instance.from.composed.root.width
        val viewportH = instance.from.composed.root.height
        val sample = TransitionSampler.sample(instance.spec, eased, viewportW, viewportH)

        // Both layers are non-hit-testable: the input policy expressed in the model.
        val outgoing = SceneLayer(
            id = "out:${instance.from.screenId}",
            role = SceneLayerRole.OutgoingScreen,
            snapshot = instance.from,
            layerOverride = sample.from,
            zIndex = sample.fromZ,
            hitTestable = false,
        )
        val incoming = SceneLayer(
            id = "in:${instance.to.screenId}",
            role = SceneLayerRole.IncomingScreen,
            snapshot = instance.to,
            layerOverride = sample.to,
            zIndex = sample.toZ,
            hitTestable = false,
        )
        val layers = listOf(outgoing, incoming).sortedBy { it.zIndex }
        val debug = SceneDebugModel(
            currentScreenId = runtime.location.screenId,
            triggeredNodeId = runtime.input.pressedNodeId,
            transition = TransitionMarker(instance.from.screenId, instance.to.screenId, instance.spec.type, eased),
            activeAnimationIds = timeline.activeAnimations.map { it.id },
        )
        return SceneRenderModel(layers, debug, timeline.currentTimeMs)
    }

    private fun sampleMotion(timeline: TimelineState): List<NodeOverride> =
        timeline.activeAnimations.mapNotNull { anim ->
            val frames = anim.keyframes ?: return@mapNotNull null
            val nodeId = anim.nodeId ?: return@mapNotNull null
            val normalized = MotionSampler.localNormalized(anim, timeline.currentTimeMs)
            NodeOverride(nodeId, MotionSampler.sample(frames, normalized))
        }

    private fun composeSnapshot(location: SceneLocation): SceneSnapshot? {
        val composed = composer.compose(location.screenId, context = ResolveContext()) ?: return null
        return SceneSnapshot(
            snapshotId = "screen_${location.screenId}",
            screenId = location.screenId,
            composed = composed,
            overlays = location.overlayStack,
        )
    }
}
