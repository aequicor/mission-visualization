package io.aequicor.visualization.engine.scene.projection

import io.aequicor.visualization.engine.ir.model.TransitionType
import io.aequicor.visualization.engine.scene.runtime.SceneSnapshot
import io.aequicor.visualization.engine.scene.sample.NodeOverride
import io.aequicor.visualization.engine.scene.sample.VisualOverride

/** Role of a layer in the composed frame. */
enum class SceneLayerRole { Screen, OutgoingScreen, IncomingScreen, OverlayBackdrop, Overlay }

/**
 * One drawable layer of the current scene frame. A layer carries its own screen id (via
 * [snapshot]); there is no global `currentScreenId` for a renderer to read. [hitTestable] is the
 * input policy expressed in the model: it is `false` for every layer during a transition, so the
 * renderer physically cannot route input mid-transition.
 */
data class SceneLayer(
    val id: String,
    val role: SceneLayerRole,
    val snapshot: SceneSnapshot,
    /** Whole-layer override (transition translation/opacity/scale). */
    val layerOverride: VisualOverride = VisualOverride.Identity,
    /** Per-node overrides (motion clips), matched by source id. */
    val nodeOverrides: List<NodeOverride> = emptyList(),
    /** Ascending paint order; larger draws on top. */
    val zIndex: Int,
    val hitTestable: Boolean,
)

data class TransitionMarker(
    val fromScreenId: String,
    val toScreenId: String,
    val type: TransitionType,
    val progress: Double,
)

data class TimelineMarker(val id: String, val atMs: Double, val label: String)

/** Everything the Scene debug/trace overlay needs to answer «что сейчас происходит». */
data class SceneDebugModel(
    val currentScreenId: String,
    val overlayScreenIds: List<String> = emptyList(),
    val triggeredNodeId: String = "",
    val activeHitTargetId: String = "",
    val transition: TransitionMarker? = null,
    val activeAnimationIds: List<String> = emptyList(),
    val timelineMarkers: List<TimelineMarker> = emptyList(),
)

/**
 * The read model the SceneRenderer draws: an ordered list of [layers] (one screen when stable,
 * two during a transition, plus overlays) with sampled overrides, a [debug] model, and the
 * timeline clock ([timeMs], a marker only). No global current-screen id — layers carry their own.
 */
data class SceneRenderModel(
    val layers: List<SceneLayer>,
    val debug: SceneDebugModel,
    val timeMs: Double,
)
