package io.aequicor.visualization.engine.ir.projection

import io.aequicor.visualization.engine.ir.layout.DesignLayoutEngine
import io.aequicor.visualization.engine.ir.layout.LayoutBox
import io.aequicor.visualization.engine.ir.model.DesignDocument
import io.aequicor.visualization.engine.ir.resolve.DesignResolver
import io.aequicor.visualization.engine.ir.resolve.ResolveContext

/**
 * One screen, resolved and laid out — the single read-model unit that BOTH the Canvas
 * and the Scene projections compose from. [root] is an immutable [LayoutBox] tree: once
 * captured (e.g. into a scene snapshot) later document edits build *new* trees and can
 * never mutate it.
 */
data class ComposedScreen(
    /** Page id == screen-meta id after merge. */
    val screenId: String,
    /** Authored id of the root frame, e.g. `frame_overview`. */
    val rootSourceId: String,
    /** Laid-out tree in root-frame coordinates; never mutated after build. */
    val root: LayoutBox,
    val deviceWidth: Double?,
    val deviceHeight: Double?,
)

/**
 * Named wrapper over the resolve → layout chain [DesignArtboard] runs inline. The **same**
 * injected [layoutEngine] instance (which holds the injected `DesignTextMeasurer`) is reused
 * for every compose call and by both projections, so Canvas and Scene geometry are identical
 * by construction.
 *
 * FUTURE-WORK (resolver prototypeState consumption): the resolver carries but does not yet
 * consume [ResolveContext.prototypeState]; once it does, threading runtime variables through
 * [context] makes a `SetVariable` re-resolve change rendered pixels — no call-site change here.
 */
class ScreenComposer(
    private val document: DesignDocument,
    private val layoutEngine: DesignLayoutEngine,
    val index: ScreenIndex = ScreenIndex(document),
) {
    /** Composes [screenId] (or any [ScreenIndex]-resolvable target); null when unresolved. */
    fun compose(
        screenId: String,
        deviceWidth: Double? = null,
        deviceHeight: Double? = null,
        context: ResolveContext = ResolveContext(),
    ): ComposedScreen? {
        val root = index.rootFrameFor(screenId) ?: return null
        val resolved = DesignResolver(document, context).resolveNodeTree(root) ?: return null
        val box = layoutEngine.layout(resolved, deviceWidth, deviceHeight)
        // Report the page id (screenIdFor collapses node-id / screen-meta targets to the page id).
        val canonicalScreenId = index.screenIdFor(screenId).ifBlank { screenId }
        return ComposedScreen(canonicalScreenId, root.id, box, deviceWidth, deviceHeight)
    }
}
