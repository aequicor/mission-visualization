package io.aequicor.visualization.engine.ir.projection

import io.aequicor.visualization.engine.ir.layout.LayoutBox

/**
 * Pure input to [CanvasProjection]. The `:shared` editor maps its `EditorWorkspaceState`
 * (the spec's CanvasEditorState) + `DesignEditorState` into this DTO so `:engine:ir` never
 * depends on the app layer.
 */
data class CanvasProjectionInput(
    /** The screen currently edited — from `DesignEditorState.selectedPageId`, NOT a global. */
    val screenId: String,
    val deviceWidth: Double? = null,
    val deviceHeight: Double? = null,
    val selectedSourceIds: Set<String> = emptySet(),
    val hoveredSourceId: String = "",
)

/**
 * Read model for editing one static screen. Editor manipulation overlays (selection boxes,
 * resize/rotate handles, guides, measurements) are drawn by the presentation layer over
 * [root]; they are not part of this pure model. Canvas-mode click SELECTS — this projection
 * never executes interactions.
 */
data class CanvasRenderModel(
    /** The edited screen id — carried on the model, never read as a global `currentScreenId`. */
    val screenId: String,
    /** Editable static frame; hit-test via `root.hitTest` / `root.findBySourceId`. */
    val root: LayoutBox,
    val selectedSourceIds: Set<String>,
    val hoveredSourceId: String,
)

/**
 * The Canvas sibling of `SceneProjection`: builds a [CanvasRenderModel] from IR + editor
 * state. A pure, recomputable read model — never a third source of truth.
 */
object CanvasProjection {
    fun project(input: CanvasProjectionInput, composer: ScreenComposer): CanvasRenderModel? {
        val screen = composer.compose(input.screenId, input.deviceWidth, input.deviceHeight)
            ?: return null
        return CanvasRenderModel(
            screenId = screen.screenId,
            root = screen.root,
            selectedSourceIds = input.selectedSourceIds,
            hoveredSourceId = input.hoveredSourceId,
        )
    }
}
