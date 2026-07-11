package io.aequicor.visualization.editor.presentation

import io.aequicor.visualization.engine.ir.model.AlignItems
import io.aequicor.visualization.subsystems.figures.BooleanOperationKind
import io.aequicor.visualization.engine.ir.model.DesignColor
import io.aequicor.visualization.engine.ir.model.DesignPaint
import io.aequicor.visualization.engine.ir.model.DesignPoint
import io.aequicor.visualization.engine.ir.model.DesignSize
import io.aequicor.visualization.engine.ir.model.DesignTransition
import io.aequicor.visualization.subsystems.figures.DesignViewBox
import io.aequicor.visualization.subsystems.figures.HandleMirror
import io.aequicor.visualization.subsystems.figures.HandleSide
import io.aequicor.visualization.engine.ir.model.HorizontalConstraint
import io.aequicor.visualization.engine.ir.model.InteractionTrigger
import io.aequicor.visualization.engine.ir.model.MotionKeyframes
import io.aequicor.visualization.engine.ir.model.JustifyContent
import io.aequicor.visualization.engine.ir.model.LayoutMode
import io.aequicor.visualization.subsystems.figures.ShapeType
import io.aequicor.visualization.engine.ir.model.SizingMode
import io.aequicor.visualization.engine.ir.model.StrokeAlign
import io.aequicor.visualization.engine.ir.model.TextAlignHorizontal
import io.aequicor.visualization.engine.ir.model.TextAlignVertical
import io.aequicor.visualization.engine.ir.model.TextAutoResize
import io.aequicor.visualization.engine.ir.model.VerticalConstraint
import io.aequicor.visualization.subsystems.annotations.AnnotationAnchor
import io.aequicor.visualization.subsystems.annotations.AnnotationImage
import io.aequicor.visualization.subsystems.annotations.AnnotationKind

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

    /**
     * Rotation write-back: unlike the in-memory [SetRotation], this rewrites the owning SLM
     * source (`position.rotation`) and remerges. Dispatched on rotate-drag release / inspector commit.
     */
    data class RotateNode(val nodeId: String, val degrees: Double) : DesignEditorIntent

    /**
     * Pulls an Auto layout child out of the flow ([DesignLayoutChild.absolute]): an
     * explicit user action (design-book §18 "Auto layout boundary"), never automatic —
     * [x]/[y] are the child's current parent-relative position at the moment it detaches,
     * so it doesn't visually jump.
     */
    data class SetAbsolutePosition(val nodeId: String, val x: Double, val y: Double) : DesignEditorIntent

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

    /**
     * Places [nodeId] under [newParentId] at [index] (-1 appends). Canvas drag-out may
     * additionally provide target-parent-relative [position], fixed visual [size], and
     * compensated [rotation]; those geometry changes are committed in the same structural
     * source transaction.
     */
    data class ReparentNode(
        val nodeId: String,
        val newParentId: String,
        val index: Int = -1,
        val position: DesignPoint? = null,
        val size: DesignSize? = null,
        val rotation: Double? = null,
    ) : DesignEditorIntent

    /** Bakes a component instance into an editable Frame subtree (Figma "Detach instance"). */
    data class DetachInstance(val nodeId: String) : DesignEditorIntent

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

    /**
     * Creates a new diagram canvas node under [parentId] at parent-relative (x,y) with (w,h),
     * seeded with one diagram element of [payload] type centered in the canvas (an empty
     * `diagram:` block would not round-trip, and the picked shape appearing immediately is the
     * expected UX). Structural write-back emits the section with its `diagram:` typed block.
     */
    data class CreateDiagramObject(
        val parentId: String,
        val payload: io.aequicor.visualization.subsystems.diagrams.model.DiagramNodePayload,
        val x: Double,
        val y: Double,
        val width: Double,
        val height: Double,
        val elementWidth: Double = 160.0,
        val elementHeight: Double = 80.0,
        val elementLabel: String? = null,
    ) : DesignEditorIntent

    // --- Source ------------------------------------------------------------

    /** Replaces one authored SLM source file and recompiles it for the live preview. */
    data class EditSource(val sourceIndex: Int, val content: String) : DesignEditorIntent

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

    /**
     * Applies [patch] to the character range `[start, end)` of the node's text (Figma's
     * per-range styling). When the range is empty it behaves like [UpdateTypography]. The
     * reducer splits/merges the node's style ranges (via the typography subsystem) and
     * writes them back as `text.spans`.
     */
    data class UpdateTypographyRange(
        val nodeId: String,
        val start: Int,
        val end: Int,
        val patch: TypographyPatch,
    ) : DesignEditorIntent

    /** Sets the glyph fills of the character range `[start, end)` (mixed text color). */
    data class SetTextRangeFills(
        val nodeId: String,
        val start: Int,
        val end: Int,
        val fills: List<io.aequicor.visualization.engine.ir.model.DesignPaint>,
    ) : DesignEditorIntent

    /**
     * Applies the shared document text style [ref] to the character range `[start, end)`
     * (resolver precedence base < ref < inline). A blank [ref] clears the range's style
     * reference. The reducer splits/merges the node's style ranges and persists them as
     * `text.spans`.
     */
    data class SetTextRangeStyleRef(
        val nodeId: String,
        val start: Int,
        val end: Int,
        val ref: String,
    ) : DesignEditorIntent

    /** Adds/updates a hyperlink over `[start, end)`; a blank url and node both clear it. */
    data class SetTextLink(
        val nodeId: String,
        val start: Int,
        val end: Int,
        val url: String = "",
        val nodeTarget: String = "",
    ) : DesignEditorIntent

    data class SetTextCharacters(val nodeId: String, val text: String) : DesignEditorIntent

    data class SetTextAutoResize(val nodeId: String, val mode: TextAutoResize) : DesignEditorIntent

    data class SetTextTruncate(
        val nodeId: String,
        val truncate: io.aequicor.visualization.engine.ir.model.TextTruncate?,
    ) : DesignEditorIntent

    data class SetTextList(
        val nodeId: String,
        val list: io.aequicor.visualization.engine.ir.model.TextListSettings,
    ) : DesignEditorIntent

    // --- Vector ------------------------------------------------------------

    /** Moves one path point of a vector shape by a parent-relative delta. */
    data class MoveVectorPoint(
        val nodeId: String,
        val pathIndex: Int,
        val pointIndex: Int,
        val dx: Double,
        val dy: Double,
    ) : DesignEditorIntent

    /** Swaps a shape's parametric primitive kind (rectangle/ellipse/polygon/…). Tier-1. */
    data class SetShapeType(val nodeId: String, val shape: ShapeType) : DesignEditorIntent

    /** Sets a polygon/star's point count. Tier-1. */
    data class SetPointCount(val nodeId: String, val count: Int) : DesignEditorIntent

    /** Sets a star's inner-radius ratio (0..1). Tier-1. */
    data class SetStarInnerRadius(val nodeId: String, val ratio: Double) : DesignEditorIntent

    /** Sets an ellipse arc's start angle in degrees (0° = 3 o'clock). Tier-1. */
    data class SetArcStart(val nodeId: String, val degrees: Double) : DesignEditorIntent

    /** Sets an ellipse arc's sweep in degrees (0..360, clockwise). Tier-1. */
    data class SetArcSweep(val nodeId: String, val degrees: Double) : DesignEditorIntent

    /** Sets an ellipse's donut-hole ratio (0..1); reuses the shape's `innerRadius`. Tier-1. */
    data class SetArcRatio(val nodeId: String, val ratio: Double) : DesignEditorIntent

    /** Sets a vector shape's design-system icon reference. Tier-1. */
    data class SetIconRef(val nodeId: String, val ref: String) : DesignEditorIntent

    /** Sets a vector shape's SVG-asset path reference. Tier-1. */
    data class SetPathRef(val nodeId: String, val ref: String) : DesignEditorIntent

    /** Sets a vector shape's viewBox. Tier-1. */
    data class SetVectorViewBox(val nodeId: String, val viewBox: DesignViewBox) : DesignEditorIntent

    /** Sets the operator of a boolean-operation node. Tier-1. */
    data class SetBooleanOperation(val nodeId: String, val op: BooleanOperationKind) : DesignEditorIntent

    /** Sets a vector network's fill (winding) rule: "nonzero" | "evenodd". Tier-1. */
    data class SetWindingRule(val nodeId: String, val rule: String) : DesignEditorIntent

    /** Sets the corner-rounding radius of a single vector-network vertex. Tier-1. */
    data class SetVertexCornerRadius(val nodeId: String, val vertexIndex: Int, val radius: Double) : DesignEditorIntent

    /** Sets (or with empty [fills], clears) the fills of a vector network region. Tier-1. */
    data class SetRegionFill(val nodeId: String, val regionIndex: Int, val fills: List<DesignPaint>) : DesignEditorIntent

    /** Applies a [FillOp] to one vector-network region's paint list, then writes it back. Tier-1. */
    data class RegionFillCommand(val nodeId: String, val regionIndex: Int, val op: FillOp) : DesignEditorIntent

    /** Bakes a parametric shape into an editable [io.aequicor.visualization.subsystems.figures.VectorNetwork]. Tier-1. */
    data class ConvertToEditableVector(val nodeId: String) : DesignEditorIntent

    /** Flattens a boolean-operation node (or parametric shape) into a single vector shape. */
    data class FlattenNode(val nodeId: String) : DesignEditorIntent

    /** Converts a shape's stroke into a filled vector outline (Figma "Outline stroke"). */
    data class OutlineStroke(val nodeId: String) : DesignEditorIntent

    /** In-memory (per-drag frame): moves network vertex [vertexIndex] by a parent-relative delta. */
    data class MoveVectorVertex(
        val nodeId: String,
        val vertexIndex: Int,
        val dx: Double,
        val dy: Double,
    ) : DesignEditorIntent

    /** In-memory (per-drag frame): moves one bezier handle of vertex [vertexIndex] (mirror-aware). */
    data class MoveVectorHandle(
        val nodeId: String,
        val vertexIndex: Int,
        val side: HandleSide,
        val dx: Double,
        val dy: Double,
    ) : DesignEditorIntent

    /** Sets vertex [vertexIndex]'s handle-mirror mode, then commits the network. Tier-1. */
    data class SetVertexMirror(
        val nodeId: String,
        val vertexIndex: Int,
        val mirror: HandleMirror,
    ) : DesignEditorIntent

    /** Toggles vertex [vertexIndex]'s sharp-corner flag, then commits the network. Tier-1. */
    data class ToggleVertexCorner(val nodeId: String, val vertexIndex: Int) : DesignEditorIntent

    /** Splits segment [segmentIndex] at (x, y), inserting a vertex, then commits the network. Tier-1. */
    data class AddVectorVertex(
        val nodeId: String,
        val segmentIndex: Int,
        val x: Double,
        val y: Double,
    ) : DesignEditorIntent

    /**
     * Appends a vertex at network-space ([x], [y]) to the growing end of an open path (pen tool
     * click-to-place), then commits the network. Unlike [AddVectorVertex] (which splits an existing
     * segment), this extends the path from its last vertex. Tier-1.
     */
    data class AppendVectorVertex(
        val nodeId: String,
        val x: Double,
        val y: Double,
    ) : DesignEditorIntent

    /** Removes network vertex [vertexIndex], then commits the network. Tier-1. */
    data class DeleteVectorVertex(val nodeId: String, val vertexIndex: Int) : DesignEditorIntent

    /** Closes an open path (last vertex back to first), then commits the network. Tier-1. */
    data class CloseVectorNetwork(val nodeId: String) : DesignEditorIntent

    /** Persists the node's current in-memory network to SLM in one surgical rewrite (drag release). Tier-1. */
    data class CommitVectorNetwork(val nodeId: String) : DesignEditorIntent

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

    // --- Prototype behavior (interactions + motion) ------------------------

    /**
     * Mutates the node's interaction list via [op] and writes the node's whole `interaction:`
     * block set back to SLM (in-memory fallback when inexpressible). The op-command idiom keeps
     * the working document and the whole-set write-back payload in lockstep (like [FillCommand]).
     */
    data class InteractionCommand(val nodeId: String, val op: InteractionOp) : DesignEditorIntent

    /** Mutates the node's motion clip via [op] and writes the `motion:` block back to SLM. */
    data class MotionCommand(val nodeId: String, val op: MotionOp) : DesignEditorIntent

    // --- Annotations (review layer; write back to the sidecar source) ------

    /**
     * Creates an annotation of [kind] at [anchor] on the screen owned by
     * [screenFileName] (`*.layout.md`). The reducer mints a stable id and writes the
     * new section into the `*.annotations.md` sidecar, creating that source on the
     * screen's first annotation.
     */
    data class AddAnnotation(
        val screenFileName: String,
        val anchor: AnnotationAnchor,
        val kind: AnnotationKind,
    ) : DesignEditorIntent

    /** Replaces the plain-text body of the annotation. */
    data class SetAnnotationText(
        val screenFileName: String,
        val annotationId: String,
        val text: String,
    ) : DesignEditorIntent

    /** Switches the annotation between note and issue (visual + export participation). */
    data class SetAnnotationKind(
        val screenFileName: String,
        val annotationId: String,
        val kind: AnnotationKind,
    ) : DesignEditorIntent

    /** Attaches (or replaces) the annotation's embedded image. */
    data class AttachAnnotationImage(
        val screenFileName: String,
        val annotationId: String,
        val image: AnnotationImage,
    ) : DesignEditorIntent

    /** Removes the annotation's embedded image. */
    data class DetachAnnotationImage(
        val screenFileName: String,
        val annotationId: String,
    ) : DesignEditorIntent

    /**
     * Moves the annotation: a node-anchored one gets ([x], [y]) as its new offset from
     * the node's top-center, a free-point one as its new absolute point.
     */
    data class MoveAnnotation(
        val screenFileName: String,
        val annotationId: String,
        val x: Double,
        val y: Double,
    ) : DesignEditorIntent

    /** Re-pins the annotation to [nodeId] with the given offset from its top-center. */
    data class AttachAnnotationToNode(
        val screenFileName: String,
        val annotationId: String,
        val nodeId: String,
        val offsetX: Double = 0.0,
        val offsetY: Double = 0.0,
    ) : DesignEditorIntent

    /**
     * Detaches a node-anchored annotation into a free point at ([x], [y]) — the badge
     * position the caller resolved from the current node bounds, so it stays visually
     * in place. A free-point annotation is left unchanged.
     */
    data class DetachAnnotationAnchor(
        val screenFileName: String,
        val annotationId: String,
        val x: Double,
        val y: Double,
    ) : DesignEditorIntent

    /** Adds an extra node reference to the annotation (deduped). */
    data class AddAnnotationReference(
        val screenFileName: String,
        val annotationId: String,
        val nodeId: String,
    ) : DesignEditorIntent

    /** Removes an extra node reference from the annotation. */
    data class RemoveAnnotationReference(
        val screenFileName: String,
        val annotationId: String,
        val nodeId: String,
    ) : DesignEditorIntent

    /** Deletes the annotation (its sidecar section is dropped surgically). */
    data class DeleteAnnotation(
        val screenFileName: String,
        val annotationId: String,
    ) : DesignEditorIntent

    // --- Annotations (view; handled by reduceAnnotationWorkspace) ----------

    /** Collapses/expands the annotation card. View state, never the document. */
    data class ToggleAnnotationExpanded(val annotationId: String) : DesignEditorIntent

    /** Selects an annotation for the inspector; blank clears (like [SelectNode]). */
    data class SelectAnnotation(val annotationId: String) : DesignEditorIntent

    /** Activates ([AnnotationTool.Note]/[AnnotationTool.Issue]) or leaves annotation mode. */
    data class SetAnnotationTool(val tool: AnnotationTool) : DesignEditorIntent

    // --- History -----------------------------------------------------------

    data object Undo : DesignEditorIntent

    data object Redo : DesignEditorIntent
}

/** Which prototype action an interaction step performs (the v1 authorable subset). */
enum class ProtoActionKind { Navigate, Back }

/** A canned keyframe animation the Motion section offers as a one-click starting point. */
enum class MotionPreset { FadeIn, Pop, Float, Pulse, Spin }

/**
 * Interaction-list mutation. Interactions are addressed by index `i`; an action within an
 * interaction by `(i, j)`. Every op is applied to the whole `node.interactions` list, so the
 * reducer derives the SLM write-back payload from the same transform (no per-field drift).
 */
sealed interface InteractionOp {
    /** Append a default `onClick → Navigate` (target = first other screen). */
    data object Add : InteractionOp

    /** Append `onClick → Navigate([target], [transition])` — used by the P5 drag-to-screen gesture. */
    data class AddNavigate(val target: String, val transition: DesignTransition) : InteractionOp

    data class RemoveAt(val i: Int) : InteractionOp

    data class SetTrigger(
        val i: Int,
        val trigger: InteractionTrigger,
        val delayMs: Double? = null,
    ) : InteractionOp

    /** Append a default action (Navigate) to interaction [i]. */
    data class AddAction(val i: Int) : InteractionOp

    data class RemoveAction(val i: Int, val j: Int) : InteractionOp

    data class SetActionType(val i: Int, val j: Int, val kind: ProtoActionKind) : InteractionOp

    data class SetActionTarget(val i: Int, val j: Int, val target: String) : InteractionOp

    data class SetActionTransition(val i: Int, val j: Int, val transition: DesignTransition) : InteractionOp
}

/** Motion-clip mutation for the selected node's single `motion:` block. */
sealed interface MotionOp {
    /** Enable = create a default (Pulse) clip; disable = clear the clip. */
    data class SetEnabled(val enabled: Boolean) : MotionOp

    data class SetPreset(val preset: MotionPreset) : MotionOp

    data class SetDuration(val ms: Double) : MotionOp

    data class SetLoop(val loop: Boolean) : MotionOp

    /** Full keyframe replacement (used by the P6 timeline editor). */
    data class SetKeyframes(val keyframes: MotionKeyframes) : MotionOp
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

    /** Stroke corner join: "miter" | "round" | "bevel". */
    data class SetJoin(val join: String) : StrokeOp

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

/**
 * Line-height override: [auto] takes precedence (native metrics); otherwise [percent]
 * or [px] sets the value. All null = leave unchanged.
 */
data class LineHeightPatch(
    val auto: Boolean = false,
    val percent: Double? = null,
    val px: Double? = null,
)

/** Partial typography override; only non-null fields are applied. Covers the Figma text panel. */
data class TypographyPatch(
    val fontFamily: String? = null,
    val fontSize: Double? = null,
    val fontWeight: Double? = null,
    val italic: Boolean? = null,
    val lineHeightPercent: Double? = null,
    /** Richer line-height control (Auto / px / %); takes precedence over [lineHeightPercent]. */
    val lineHeight: LineHeightPatch? = null,
    val letterSpacing: Double? = null,
    /** Letter spacing expressed as percent of font size; takes precedence over [letterSpacing]. */
    val letterSpacingPercent: Double? = null,
    val paragraphSpacing: Double? = null,
    val paragraphIndent: Double? = null,
    val alignHorizontal: TextAlignHorizontal? = null,
    val alignVertical: TextAlignVertical? = null,
    val textCase: io.aequicor.visualization.engine.ir.model.TextCase? = null,
    val textDecoration: io.aequicor.visualization.engine.ir.model.TextDecorationKind? = null,
    val decorationStyle: io.aequicor.visualization.engine.ir.model.TextDecorationStyle? = null,
    val decorationColor: io.aequicor.visualization.engine.ir.model.DesignColor? = null,
    /** Sentinel to clear the decoration color back to "auto" (follows glyph fill). */
    val clearDecorationColor: Boolean = false,
    val decorationThickness: io.aequicor.visualization.engine.ir.model.UnitValue? = null,
    val decorationSkipInk: Boolean? = null,
    val textPosition: io.aequicor.visualization.engine.ir.model.TextScriptPosition? = null,
    val leadingTrim: io.aequicor.visualization.engine.ir.model.LeadingTrim? = null,
    val hangingPunctuation: Boolean? = null,
    val hangingList: Boolean? = null,
    /** OpenType feature toggles to set/override (merged over existing). */
    val fontFeatures: Map<String, Boolean> = emptyMap(),
    /** Variable-font axis values to set/override. */
    val variableAxes: Map<String, Double> = emptyMap(),
)
