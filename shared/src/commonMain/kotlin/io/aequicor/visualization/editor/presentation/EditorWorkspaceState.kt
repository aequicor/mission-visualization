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
    val inspectorWidthDp: Float = 420f,
    val sourceCollapsed: Boolean = false,
    val inspectorCollapsed: Boolean = false,
    val focusMode: FocusMode = FocusMode.Normal,
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
    /** Selected vector anchor (path index, point index) in vector edit mode, or null. */
    val vectorSelectedPoint: VectorPointRef? = null,
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

/** Workspace focus: `Normal` shows all chrome; `MainOnly` leaves just the canvas. */
enum class FocusMode { Normal, MainOnly }

/** A one-shot fit request the canvas consumes once its size and layout are known. */
enum class PendingFit { None, Screen, Selection }

/**
 * Canvas tools. `Select`/`Hand` manipulate; the rest create an object of [creates]
 * on press/drag. `Hand` pans; `Select` also marquee-selects and drag-moves.
 */
enum class EditorTool(val label: String, val creates: NewObjectKind?) {
    Select("Move", null),
    Hand("Hand", null),
    Frame("Frame", NewObjectKind.Frame),
    Component("Component", null),
    Rectangle("Rectangle", NewObjectKind.Rectangle),
    Pen("Pen", NewObjectKind.Line),
    Text("Text", NewObjectKind.Text),
    Comment("Comment", null),
    Link("Link", null),
    Code("Code", null),
}

enum class SourceTab(val title: String) {
    Markdown("Markdown"),
    Resources("Resources"),
    Layers("Layers"),
}

enum class InspectorTab(val title: String) {
    Design("Design"),
    Comments("Comments"),
}

enum class DeviceMode(val title: String, val width: Double?, val height: Double?) {
    Pc("PC", null, null),
    Mob("MOB", 375.0, 812.0),
    Tab("TAB", 768.0, 1024.0),
}

enum class InspectorSection(val title: String) {
    Position("Position"),
    Layout("Layout"),
    Appearance("Appearance"),
    Fill("Fill"),
    Stroke("Stroke"),
    Effects("Effects"),
    Typography("Typography"),
    Constraints("Constraints"),
}

/** Minimum and maximum panel widths (dp) used by the splitter drag clamps. */
object WorkspaceLimits {
    const val MinPanelDp: Float = 220f
    const val MaxSourceDp: Float = 640f
    const val MaxInspectorDp: Float = 560f
    const val DefaultSourceDp: Float = 440f
    const val DefaultInspectorDp: Float = 420f
    const val MinZoom: Float = 0.05f
    const val MaxZoom: Float = 16f

    /** Exponential response of wheel/trackpad zoom: `factor = exp(clamp(scroll) * sensitivity)`. */
    const val ZoomWheelSensitivity: Float = 0.1f

    /** Upper bound on a single scroll event's magnitude, taming outlier pixel-mode deltas. */
    const val MaxZoomScrollStep: Float = 12f

    /** Multiplicative step per click of the +/- zoom buttons (animated around the canvas center). */
    const val ZoomButtonStep: Float = 1.2f
}
