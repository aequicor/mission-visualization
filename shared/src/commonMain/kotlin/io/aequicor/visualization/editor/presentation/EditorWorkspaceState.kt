package io.aequicor.visualization.editor.presentation

import io.aequicor.visualization.engine.ir.model.DesignColor

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
    val deviceMode: DeviceMode = DeviceMode.Pc,
    val sourceTab: SourceTab = SourceTab.Layers,
    val inspectorTab: InspectorTab = InspectorTab.Design,
    val expandedSections: Set<InspectorSection> = InspectorSection.entries.toSet(),
    val viewport: EditorViewport = EditorViewport(),
    /** True when W/H edits and canvas corner-resize keep the object's aspect ratio. */
    val lockAspectRatio: Boolean = false,
    /** Node currently hovered on the canvas or in Layers (outline feedback), or "". */
    val hoveredNodeId: String = "",
    /** Layer ids whose children are collapsed in the Layers tree (default: expanded). */
    val collapsedLayers: Set<String> = emptySet(),
    /** Node in vector edit mode, or "" when not editing a vector. */
    val vectorEditNodeId: String = "",
    /** Selected vector anchor (path index, point index) in legacy `d`-string vector edit mode, or null. */
    val vectorSelectedPoint: VectorPointRef? = null,
    /** Selected element (vertex anchor or a bezier handle) in structural-network vector edit mode, or null. */
    val vectorSelectedVertex: VectorVertexRef? = null,
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
) {
    val isMainOnly: Boolean get() = focusMode == FocusMode.MainOnly

    val zoom: Float get() = viewport.zoom

    val panXDp: Float get() = viewport.panOffsetXDp

    val panYDp: Float get() = viewport.panOffsetYDp

    /** Whether the tool creates an object on canvas press. */
    val isCreationTool: Boolean get() = tool.creates != null
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
    Pen("Pen", NewObjectKind.Line),
    Text("Text", NewObjectKind.Text),
    Comment("Comment", null),
    Link("Link", null),
    Code("Code", null),
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
    const val MinPanelDp: Float = 220f
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
