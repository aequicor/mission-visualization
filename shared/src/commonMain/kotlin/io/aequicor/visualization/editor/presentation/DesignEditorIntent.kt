package io.aequicor.visualization.editor.presentation

import io.aequicor.visualization.engine.ir.model.AlignItems
import io.aequicor.visualization.engine.ir.model.DesignColor
import io.aequicor.visualization.engine.ir.model.HorizontalConstraint
import io.aequicor.visualization.engine.ir.model.JustifyContent
import io.aequicor.visualization.engine.ir.model.LayoutMode
import io.aequicor.visualization.engine.ir.model.SizingMode
import io.aequicor.visualization.engine.ir.model.StrokeAlign
import io.aequicor.visualization.engine.ir.model.TextAlignHorizontal
import io.aequicor.visualization.engine.ir.model.TextAlignVertical
import io.aequicor.visualization.engine.ir.model.TextAutoResize
import io.aequicor.visualization.engine.ir.model.VerticalConstraint

/**
 * Every user action against the design document flows through one of these commands
 * into [reduceDesignEditor]. Most commands mutate the in-memory working
 * [io.aequicor.visualization.engine.ir.model.DesignDocument]; [ResizeNode] additionally
 * writes the change back into the owning SLM source (surgical patch + recompile).
 */
sealed interface DesignEditorIntent {

    // --- Selection ---------------------------------------------------------

    data class SelectPage(val pageId: String) : DesignEditorIntent

    /** Single selection (replaces the set); [nodeId] becomes primary. Blank clears. */
    data class SelectNode(val nodeId: String) : DesignEditorIntent

    /** Replaces the whole selection set; primary is the last id. */
    data class SelectNodes(val nodeIds: Set<String>) : DesignEditorIntent

    /** Shift+click semantics: add when absent, remove when present. */
    data class ToggleNodeSelection(val nodeId: String) : DesignEditorIntent

    data object ClearSelection : DesignEditorIntent

    /** Selects every top-level object on the current screen. */
    data object SelectAll : DesignEditorIntent

    /** Enters ("" exits) text editing mode for a text node. */
    data class SetEditingText(val nodeId: String) : DesignEditorIntent

    // --- Position / size / transform --------------------------------------

    data class UpdatePosition(val nodeId: String, val x: Double? = null, val y: Double? = null) : DesignEditorIntent

    /**
     * Position write-back: rewrites the owning SLM source with absolute x/y and updates
     * the working document. Used on drag release and inspector commits.
     */
    data class PositionNode(val nodeId: String, val x: Double, val y: Double) : DesignEditorIntent

    /** Typing an exact number pins the edited dimension to `fixed`, like Figma. */
    data class UpdateSize(val nodeId: String, val width: Double? = null, val height: Double? = null) : DesignEditorIntent

    /**
     * Resize write-back: unlike the in-memory [UpdateSize], this rewrites the owning
     * SLM document's source text (the source of truth) with `fixed` sizing for the
     * provided axes, recompiles it and remerges. Axes left null keep their sizing.
     */
    data class ResizeNode(val nodeId: String, val width: Double? = null, val height: Double? = null) : DesignEditorIntent

    /** Parent-relative translation applied to every node in [nodeIds] (drag / nudge). */
    data class MoveNodes(val nodeIds: Set<String>, val dx: Double, val dy: Double) : DesignEditorIntent

    data class UpdateSizingMode(
        val nodeId: String,
        val horizontal: SizingMode? = null,
        val vertical: SizingMode? = null,
    ) : DesignEditorIntent

    data class UpdateConstraints(
        val nodeId: String,
        val horizontal: HorizontalConstraint? = null,
        val vertical: VerticalConstraint? = null,
    ) : DesignEditorIntent

    data class SetRotation(val nodeId: String, val degrees: Double) : DesignEditorIntent

    data class FlipHorizontal(val nodeIds: Set<String>) : DesignEditorIntent

    data class FlipVertical(val nodeIds: Set<String>) : DesignEditorIntent

    // --- Visibility / lock / structure ------------------------------------

    data class SetVisible(val nodeId: String, val visible: Boolean) : DesignEditorIntent

    data class SetLocked(val nodeId: String, val locked: Boolean) : DesignEditorIntent

    data class RenameNode(val nodeId: String, val name: String) : DesignEditorIntent

    data class DeleteNodes(val nodeIds: Set<String>) : DesignEditorIntent

    data class DuplicateNodes(val nodeIds: Set<String>) : DesignEditorIntent

    /** Steps a node within its sibling list; z-order = paint order (later = front). */
    data class ReorderNode(val nodeId: String, val move: ZOrderMove) : DesignEditorIntent

    /** Drag/drop in Layers: place [nodeId] under [newParentId] at [index] (-1 appends). */
    data class ReparentNode(val nodeId: String, val newParentId: String, val index: Int = -1) : DesignEditorIntent

    /** Creates a new object under [parentId] at parent-relative (x,y) with (w,h). */
    data class CreateObject(
        val kind: NewObjectKind,
        val parentId: String,
        val x: Double,
        val y: Double,
        val width: Double,
        val height: Double,
        val fixedWidthText: Boolean = false,
        val enterTextEditing: Boolean = false,
    ) : DesignEditorIntent

    /** Creates a new screen (top-level frame + its own page). */
    data class CreateScreen(val preset: ScreenPreset, val title: String) : DesignEditorIntent

    // --- Layout container --------------------------------------------------

    data class SetLayoutMode(val nodeId: String, val mode: EditorLayoutMode) : DesignEditorIntent

    data class SetLayoutGap(val nodeId: String, val gap: Double) : DesignEditorIntent

    data class SetLayoutPadding(val nodeId: String, val side: PaddingSide, val value: Double) : DesignEditorIntent

    data class SetLayoutAlign(
        val nodeId: String,
        val alignItems: AlignItems? = null,
        val justifyContent: JustifyContent? = null,
    ) : DesignEditorIntent

    data class SetClipsContent(val nodeId: String, val clips: Boolean) : DesignEditorIntent

    data class SetSticky(val nodeId: String, val sticky: Boolean) : DesignEditorIntent

    // --- Appearance / fill / stroke / effects -----------------------------

    data class UpdateOpacity(val nodeId: String, val opacity: Double) : DesignEditorIntent

    data class SetBlendMode(val nodeId: String, val blendMode: String) : DesignEditorIntent

    data class UpdateCornerRadius(val nodeId: String, val radius: Double) : DesignEditorIntent

    data class UpdateCornerRadiusPerCorner(
        val nodeId: String,
        val topLeft: Double? = null,
        val topRight: Double? = null,
        val bottomRight: Double? = null,
        val bottomLeft: Double? = null,
    ) : DesignEditorIntent

    /** Legacy convenience kept for the single-swatch inspector path. */
    data class UpdateSolidFill(val nodeId: String, val color: DesignColor) : DesignEditorIntent

    data class FillCommand(val nodeId: String, val op: FillOp) : DesignEditorIntent

    /** Legacy convenience: set stroke color/weight on the first paint. */
    data class UpdateStroke(
        val nodeId: String,
        val color: DesignColor? = null,
        val weight: Double? = null,
    ) : DesignEditorIntent

    data class StrokeCommand(val nodeId: String, val op: StrokeOp) : DesignEditorIntent

    data class EffectCommand(val nodeId: String, val op: EffectOp) : DesignEditorIntent

    // --- Typography --------------------------------------------------------

    data class UpdateTypography(val nodeId: String, val patch: TypographyPatch) : DesignEditorIntent

    data class SetTextCharacters(val nodeId: String, val text: String) : DesignEditorIntent

    data class SetTextAutoResize(val nodeId: String, val mode: TextAutoResize) : DesignEditorIntent

    // --- Vector ------------------------------------------------------------

    /** Moves one path point of a vector shape by a parent-relative delta. */
    data class MoveVectorPoint(
        val nodeId: String,
        val pathIndex: Int,
        val pointIndex: Int,
        val dx: Double,
        val dy: Double,
    ) : DesignEditorIntent

    // --- Interaction checkpoints (canvas drags) ----------------------------

    /** Starts a drag: takes one undo checkpoint; subsequent edits coalesce into it. */
    data object BeginInteraction : DesignEditorIntent

    /** Ends a drag: subsequent edits resume taking their own checkpoints. */
    data object EndInteraction : DesignEditorIntent

    /**
     * Aborts an in-progress drag (Escape): reverts the document to the checkpoint taken
     * at [BeginInteraction] and records no undo entry.
     */
    data object CancelInteraction : DesignEditorIntent

    // --- History -----------------------------------------------------------

    data object Undo : DesignEditorIntent

    data object Redo : DesignEditorIntent
}

/** Editor-facing layout modes; `Free` and `Stack` both map onto [LayoutMode.None]. */
enum class EditorLayoutMode(val displayName: String) {
    Free("Free"),
    Vertical("Vertical"),
    Horizontal("Horizontal"),
    Grid("Grid"),
    Stack("Stack"),
    ;

    fun toLayoutMode(): LayoutMode = when (this) {
        Free, Stack -> LayoutMode.None
        Vertical -> LayoutMode.Vertical
        Horizontal -> LayoutMode.Horizontal
        Grid -> LayoutMode.Grid
    }
}

enum class PaddingSide { Top, Right, Bottom, Left, All }

enum class ZOrderMove { Forward, Backward, ToFront, ToBack }

/** Fill-stack mutation, addressed by index into the node's bottom-to-top `fills` list. */
sealed interface FillOp {
    data object Add : FillOp

    data class RemoveAt(val index: Int) : FillOp

    data class ToggleAt(val index: Int) : FillOp

    data class Move(val from: Int, val to: Int) : FillOp

    data class SetColor(val index: Int, val color: DesignColor) : FillOp

    data class SetOpacity(val index: Int, val opacity: Double) : FillOp

    data class SetType(val index: Int, val type: FillKind) : FillOp

    data class AddGradientStop(val index: Int) : FillOp

    data class RemoveGradientStop(val index: Int, val stopIndex: Int) : FillOp

    data class SetGradientStopColor(val index: Int, val stopIndex: Int, val color: DesignColor) : FillOp

    data class SetGradientStopPosition(val index: Int, val stopIndex: Int, val position: Double) : FillOp

    data class ReverseGradient(val index: Int) : FillOp

    /** Direction as an angle in degrees (0 = left→right, 90 = top→bottom). */
    data class SetGradientAngle(val index: Int, val angleDegrees: Double) : FillOp
}

enum class FillKind(val displayName: String) {
    Solid("Solid"),
    LinearGradient("Linear"),
    RadialGradient("Radial"),
    Image("Image"),
}

/** Single-stroke mutation (the node's `strokes`); paints share one weight/position. */
sealed interface StrokeOp {
    data object Add : StrokeOp

    data object Remove : StrokeOp

    data class SetVisible(val visible: Boolean) : StrokeOp

    data class SetColor(val color: DesignColor) : StrokeOp

    data class SetOpacity(val opacity: Double) : StrokeOp

    data class SetWeight(val weight: Double) : StrokeOp

    data class SetAlign(val align: StrokeAlign) : StrokeOp

    data class SetCap(val cap: String) : StrokeOp

    data class SetDashed(val dashed: Boolean) : StrokeOp

    data class SetPerSide(
        val top: Double? = null,
        val right: Double? = null,
        val bottom: Double? = null,
        val left: Double? = null,
    ) : StrokeOp
}

/** Effect-stack mutation addressed by index into the node's `effects`. */
sealed interface EffectOp {
    data class Add(val type: EffectType) : EffectOp

    data class RemoveAt(val index: Int) : EffectOp

    data class ToggleAt(val index: Int) : EffectOp

    data class SetType(val index: Int, val type: EffectType) : EffectOp

    data class UpdateShadow(
        val index: Int,
        val dx: Double? = null,
        val dy: Double? = null,
        val blur: Double? = null,
        val spread: Double? = null,
        val color: DesignColor? = null,
    ) : EffectOp

    data class SetBlurRadius(val index: Int, val radius: Double) : EffectOp
}

enum class EffectType(val displayName: String) {
    DropShadow("Drop shadow"),
    InnerShadow("Inner shadow"),
    LayerBlur("Layer blur"),
    BackgroundBlur("Background blur"),
}

/** Partial typography override; only non-null fields are applied. */
data class TypographyPatch(
    val fontFamily: String? = null,
    val fontSize: Double? = null,
    val fontWeight: Double? = null,
    val lineHeightPercent: Double? = null,
    val letterSpacing: Double? = null,
    val alignHorizontal: TextAlignHorizontal? = null,
    val alignVertical: TextAlignVertical? = null,
)
