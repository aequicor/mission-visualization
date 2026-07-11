package io.aequicor.visualization.editor.presentation

import io.aequicor.visualization.engine.ir.model.DesignColor
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodePayload
import io.aequicor.visualization.subsystems.diagrams.model.DiagramRelation

/**
 * Editor workspace / view state, kept strictly separate from the design document
 * (`DesignEditorState`). This is the user's personal layout of the workbench —
 * panel widths, collapse flags, focus mode, canvas zoom/pan, active tool — and per
 * the design book it is a user preference, never part of the document. Widths are
 * plain dp-values (Float) so this type stays Compose-free and unit-testable.
 */
data class EditorWorkspaceState(
    val sourceWidthDp: Float = 440f,
    val inspectorWidthDp: Float = 360f,
    val sourceCollapsed: Boolean = false,
    val inspectorCollapsed: Boolean = false,
    val focusMode: FocusMode = FocusMode.Normal,
    /**
     * Canvas edits the static screen (click selects); Scene plays prototype behavior (click
     * executes interactions). This is a view preference — switching never touches the document,
     * so it can never create an undo entry, and selection/viewport survive the switch for free.
     */
    val mode: EditorMode = EditorMode.Canvas,
    val tool: EditorTool = EditorTool.Select,
    /** The most recently used shape tool, shown in the toolbar's shape-flyout slot. */
    val lastShapeTool: EditorTool = EditorTool.Rectangle,
    val deviceMode: DeviceMode = DeviceMode.Pc,
    val sourceTab: SourceTab = SourceTab.Layers,
    val inspectorTab: InspectorTab = InspectorTab.Design,
    val expandedSections: Set<InspectorSection> = InspectorSection.entries.toSet(),
    val viewport: EditorViewport = EditorViewport(),
    /** True when W/H edits and canvas corner-resize keep the object's aspect ratio. */
    val lockAspectRatio: Boolean = false,
    /** Layer ids whose children are collapsed in the Layers tree (default: expanded). */
    val collapsedLayers: Set<String> = emptySet(),
    /** Node in vector edit mode, or "" when not editing a vector. */
    val vectorEditNodeId: String = "",
    /** Selected vector anchor (path index, point index) in legacy `d`-string vector edit mode, or null. */
    val vectorSelectedPoint: VectorPointRef? = null,
    /** Selected element (vertex anchor or a bezier handle) in structural-network vector edit mode, or null. */
    val vectorSelectedVertex: VectorVertexRef? = null,
    /**
     * Paint-bucket sub-mode of vector edit: while active, a canvas press inside a network region
     * fills that region with [vectorPaintBucketColor] instead of manipulating anchors/handles.
     * View-only preference; never part of the document.
     */
    val vectorPaintBucket: Boolean = false,
    /** Color the paint-bucket sub-mode applies to a clicked region. */
    val vectorPaintBucketColor: DesignColor = DesignColor(0xFF4C6EF5),
    /** A pending fit-to request the canvas applies on its next layout pass. */
    val pendingFit: PendingFit = PendingFit.None,
    /**
     * One-shot zoom target the canvas eases to, anchored on its center; null when idle.
     * Used by the +/-/1:1 controls so button zoom glides (and never drifts) instead of
     * snapping. Continuous wheel/trackpad zoom writes [viewport] directly and clears this.
     */
    val pendingZoomTo: Float? = null,
    /** Recently committed colors (most-recent first), surfaced by the color picker. */
    val recentColors: List<DesignColor> = emptyList(),
    /** Active caret/selection while editing text on the canvas; null when not editing text. */
    val textSelection: TextSelection? = null,
    /**
     * IR node id of the diagram currently in edit mode (double-click / diagram toolbar),
     * or "" when no diagram is being edited. Mirrors [vectorEditNodeId]: while set, the
     * diagram overlay owns canvas gestures inside the node's box.
     */
    val diagramEditNodeId: String = "",
    /** Active tool of the diagram canvas while a diagram node is being edited. */
    val diagramTool: DiagramTool = DiagramTool.Select,
    /**
     * Selected diagram elements *inside* the selected diagram node (graph node/edge ids).
     * A view concern like text selection — never part of the document — so undo/redo of
     * graph edits cannot resurrect a stale selection.
     */
    val diagramSelection: DiagramSelection = DiagramSelection.Empty,
) {
    val isMainOnly: Boolean get() = focusMode == FocusMode.MainOnly

    val zoom: Float get() = viewport.zoom

    val panXDp: Float get() = viewport.panOffsetXDp

    val panYDp: Float get() = viewport.panOffsetYDp

    /** Whether the tool creates an object on canvas press. */
    val isCreationTool: Boolean get() = tool.creates != null
}

/**
 * Text-edit caret/selection in the *rendered* string's offset space. A collapsed
 * selection ([start] == [end]) is a caret; otherwise the ordered range covers
 * `[min, max)`. Lives in workspace state — it is a view concern, never in the document.
 */
data class TextSelection(val nodeId: String, val start: Int, val end: Int) {
    val min: Int get() = minOf(start, end)
    val max: Int get() = maxOf(start, end)
    val isCollapsed: Boolean get() = start == end
}

/**
 * Diagram-canvas tools: [Select] manipulates existing elements; [AddNode] stamps a new
 * element of [AddNode.payload] on press; [DrawEdge] drags a new connector between nodes.
 */
sealed interface DiagramTool {
    data object Select : DiagramTool

    data class AddNode(val payload: DiagramNodePayload) : DiagramTool

    data class DrawEdge(val relation: DiagramRelation = DiagramRelation.Plain) : DiagramTool
}

/** Selected elements of the diagram graph being edited (ids are graph-local strings). */
data class DiagramSelection(
    val elementIds: Set<String> = emptySet(),
    val edgeIds: Set<String> = emptySet(),
) {
    val isEmpty: Boolean get() = elementIds.isEmpty() && edgeIds.isEmpty()

    companion object {
        val Empty: DiagramSelection = DiagramSelection()
    }
}

/** Reference to a single editable vector anchor. */
data class VectorPointRef(val pathIndex: Int, val pointIndex: Int)

/** Which part of a network vertex a structural vector-edit selection targets. */
enum class VectorVertexPart { Anchor, InHandle, OutHandle }

/** Reference to a selected element (anchor or a bezier handle) of a structural vector network. */
data class VectorVertexRef(val vertexIndex: Int, val part: VectorVertexPart = VectorVertexPart.Anchor)

/** Workspace focus: `Normal` shows all chrome; `MainOnly` leaves just the canvas. */
enum class FocusMode { Normal, MainOnly }

/** The two editing modes (design-book §19): static [Canvas] editing vs. [Scene] prototype playback. */
enum class EditorMode(val title: String) { Canvas("Canvas"), Scene("Scene") }

/** A one-shot fit request the canvas consumes once its size and layout are known. */
enum class PendingFit { None, Screen, Selection }

/**
 * Canvas tools. `Select` manipulates existing objects; the rest create an object of
 * [creates] on press/drag. Canvas panning is a transient Ctrl-drag gesture.
 */
enum class EditorTool(val label: String, val creates: NewObjectKind?) {
    Select("Move", null),
    Frame("Frame", NewObjectKind.Frame),
    Rectangle("Rectangle", NewObjectKind.Rectangle),
    Ellipse("Ellipse", NewObjectKind.Ellipse),
    Polygon("Polygon", NewObjectKind.Polygon),
    Star("Star", NewObjectKind.Star),
    Line("Line", NewObjectKind.Line),
    Arrow("Arrow", NewObjectKind.Arrow),
    Pen("Pen", NewObjectKind.Vector),
    Text("Text", NewObjectKind.Text),
    Comment("Comment", null),
    Link("Link", null),
    Code("Code", null),
    ;

    /** A shape-drawing tool grouped under the toolbar's shape flyout. */
    val isShapeTool: Boolean
        get() = this == Rectangle || this == Ellipse || this == Polygon ||
            this == Star || this == Line || this == Arrow
}

enum class SourceTab(val label: CompactLabel) {
    Markdown(CompactLabel("Semantic Layout Markdown", "Markdown", "SLM")),
    Resources(CompactLabel("Resources", "Res", "Res")),
    Layers(CompactLabel("Layers", "Layers", "Lyr")),
    ;

    val title: String get() = label.full
}

enum class InspectorTab(val label: CompactLabel) {
    Design(CompactLabel("Design", "Design", "Dsgn")),
    Prototype(CompactLabel("Prototype", "Proto", "Prt")),
    Comments(CompactLabel("Comments", "Com", "Com")),
    ;

    val title: String get() = label.full
}

enum class DeviceMode(val title: String, val width: Double?, val height: Double?) {
    Pc("PC", null, null),
    Mob("MOB", 375.0, 812.0),
    Tab("TAB", 768.0, 1024.0),
}

enum class InspectorSection(val label: CompactLabel) {
    Position(CompactLabel("Position", "Pos", "Pos")),
    Layout(CompactLabel("Layout", "Layout", "Lay")),
    Appearance(CompactLabel("Appearance", "Appear", "App")),
    Fill(CompactLabel("Fill")),
    Stroke(CompactLabel("Stroke", "Stroke", "Str")),
    Effects(CompactLabel("Effects", "FX", "FX")),
    Typography(CompactLabel("Typography", "Type", "Typ")),
    Constraints(CompactLabel("Constraints", "Const", "Cnst")),
    Interactions(CompactLabel("Interactions", "Interact", "Int")),
    Motion(CompactLabel("Motion", "Motion", "Mot")),
    ;

    val title: String get() = label.full
}

/** Minimum and maximum panel widths (dp) used by the splitter drag clamps. */
object WorkspaceLimits {
    /** Keeps the project button and all three source-tab captions visible without ellipsis. */
    const val MinSourceDp: Float = 240f
    const val MinInspectorDp: Float = 220f
    const val MaxSourceDp: Float = 640f
    const val MaxInspectorDp: Float = 380f
    const val DefaultSourceDp: Float = 440f
    const val DefaultInspectorDp: Float = 360f
    const val MinZoom: Float = 0.05f
    const val MaxZoom: Float = 16f

    /** Exponential response of wheel/trackpad zoom: `factor = exp(clamp(scroll) * sensitivity)`. */
    const val ZoomWheelSensitivity: Float = 0.025f

    /** Upper bound on a single scroll event's magnitude, taming outlier pixel-mode deltas. */
    const val MaxZoomScrollStep: Float = 12f

    /** Multiplicative step per click of the +/- zoom buttons (animated around the canvas center). */
    const val ZoomButtonStep: Float = 1.2f
}
