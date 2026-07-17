package io.aequicor.visualization.editor.presentation

import io.aequicor.visualization.subsystems.diagrams.layout.DiagramLayoutPreset
import io.aequicor.visualization.subsystems.diagrams.layout.LayoutDirection
import io.aequicor.visualization.subsystems.diagrams.layout.LayoutKind
import io.aequicor.visualization.subsystems.diagrams.model.DiagramArrowhead
import io.aequicor.visualization.subsystems.diagrams.model.DiagramEdgeLabelPosition
import io.aequicor.visualization.subsystems.diagrams.model.DiagramEndpoint
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodePayload
import io.aequicor.visualization.subsystems.diagrams.model.DiagramPort
import io.aequicor.visualization.subsystems.diagrams.model.DiagramRelation
import io.aequicor.visualization.subsystems.diagrams.model.DiagramRoutingStyle
import io.aequicor.visualization.subsystems.diagrams.model.DiagramStrokePattern
import io.aequicor.visualization.subsystems.diagrams.model.DiagramStyle
import io.aequicor.visualization.subsystems.diagrams.model.LineJumpStyle
import io.aequicor.visualization.subsystems.diagrams.model.TableColumn
import io.aequicor.visualization.subsystems.diagrams.model.TableRow
import io.aequicor.visualization.subsystems.diagrams.model.UmlMember
import io.aequicor.visualization.subsystems.diagrams.model.UmlVisibility
import io.aequicor.visualization.subsystems.diagrams.ops.DiagramEdgeEnd
import io.aequicor.visualization.subsystems.diagrams.ops.UmlClassMemberKind
import io.aequicor.visualization.subsystems.diagrams.path.DiagramPoint

/**
 * Diagram-canvas commands: every intent targets the [nodeId] of an IR node whose kind is
 * [io.aequicor.visualization.engine.ir.model.DesignNodeKind.Diagram] and mutates its
 * [io.aequicor.visualization.subsystems.diagrams.model.DiagramGraph] through the pure ops
 * of `:subsystems:diagrams`. Each applied edit writes the canonical `## Diagram: …` CNL
 * container body back to the owning SLM source (`applyDiagramWriteBack`, anti-corruption
 * round-trip veto) and
 * mirrors onto the working document in lock-step; an unaddressable node or any drift keeps
 * the edit in-memory only — the same fallback contract as structural edits.
 *
 * Diagram element ids ([elementId]/edgeId/layerId/groupId) are plain strings; the handlers
 * wrap them into the typed diagram ids. Unknown ids / wrong payload types are no-ops.
 */
sealed interface DiagramEditorIntent : DesignEditorIntent {
    /** IR node id of the diagram canvas node this command edits. */
    val nodeId: String

    // --- Elements ----------------------------------------------------------

    /** Adds a fresh diagram node; a taken/blank [elementId] is a no-op. */
    data class AddDiagramNode(
        override val nodeId: String,
        val elementId: String,
        val payload: DiagramNodePayload = DiagramNodePayload.BasicShape(),
        val x: Double = 0.0,
        val y: Double = 0.0,
        val width: Double = 120.0,
        val height: Double = 60.0,
        val label: String? = null,
        val ports: List<DiagramPort> = emptyList(),
    ) : DiagramEditorIntent

    /** Deletes diagram nodes (cascading subtree + attached edges) and/or edges. */
    data class DeleteDiagramElement(
        override val nodeId: String,
        val elementIds: Set<String> = emptySet(),
        val edgeIds: Set<String> = emptySet(),
    ) : DiagramEditorIntent

    /** Moves a diagram node (with its container subtree) by a graph-local delta. */
    data class MoveDiagramNode(
        override val nodeId: String,
        val elementId: String,
        val dx: Double,
        val dy: Double,
    ) : DiagramEditorIntent

    /** Sets a diagram node's bounds; [resizeChildren] scales the contained subtree. */
    data class ResizeDiagramNode(
        override val nodeId: String,
        val elementId: String,
        val x: Double,
        val y: Double,
        val width: Double,
        val height: Double,
        val resizeChildren: Boolean = false,
    ) : DiagramEditorIntent

    /** Replaces (or with null [text], clears) a diagram node's primary label. */
    data class SetDiagramNodeLabel(
        override val nodeId: String,
        val elementId: String,
        val text: String?,
    ) : DiagramEditorIntent

    data class SetDiagramNodeStyle(
        override val nodeId: String,
        val elementId: String,
        val style: DiagramStyle,
    ) : DiagramEditorIntent

    /** Replaces a diagram node's payload (type change from the inspector's type dropdown). */
    data class SetDiagramNodePayload(
        override val nodeId: String,
        val elementId: String,
        val payload: DiagramNodePayload,
    ) : DiagramEditorIntent

    /** Adds a custom connection point to a diagram node; duplicate port id is a no-op. */
    data class AddDiagramPort(
        override val nodeId: String,
        val elementId: String,
        val port: DiagramPort,
    ) : DiagramEditorIntent

    /** Removes a port; edges pinned to it stay attached as floating anchors. */
    data class RemoveDiagramPort(
        override val nodeId: String,
        val elementId: String,
        val portId: String,
    ) : DiagramEditorIntent

    /** Clones [sourceElementId] (drag-out clone-and-connect) and links original -> clone. */
    data class CloneDiagramNodeAndConnect(
        override val nodeId: String,
        val sourceElementId: String,
        val cloneId: String,
        val edgeId: String,
        val offsetX: Double = 40.0,
        val offsetY: Double = 40.0,
        val relation: DiagramRelation = DiagramRelation.Plain,
        val routing: DiagramRoutingStyle = DiagramRoutingStyle.ORTHOGONAL,
    ) : DiagramEditorIntent

    // --- Edges -------------------------------------------------------------

    /** Draws a new edge; a taken/blank [edgeId] or an unresolvable endpoint is a no-op. */
    data class ConnectDiagramNodes(
        override val nodeId: String,
        val edgeId: String,
        val source: DiagramEndpoint,
        val target: DiagramEndpoint,
        val relation: DiagramRelation = DiagramRelation.Plain,
        val routing: DiagramRoutingStyle = DiagramRoutingStyle.ORTHOGONAL,
        val label: String? = null,
    ) : DiagramEditorIntent

    /** Re-pins one end of an existing edge (validates the new endpoint). */
    data class ReconnectDiagramEdge(
        override val nodeId: String,
        val edgeId: String,
        val end: DiagramEdgeEnd,
        val endpoint: DiagramEndpoint,
    ) : DiagramEditorIntent

    data class SetDiagramEdgeRelation(
        override val nodeId: String,
        val edgeId: String,
        val relation: DiagramRelation,
    ) : DiagramEditorIntent

    data class SetDiagramEdgeRouting(
        override val nodeId: String,
        val edgeId: String,
        val routing: DiagramRoutingStyle,
    ) : DiagramEditorIntent

    /** Sets the edge's stroke pattern (solid/dashed/dotted) keeping the rest of the style. */
    data class SetDiagramEdgePattern(
        override val nodeId: String,
        val edgeId: String,
        val pattern: DiagramStrokePattern,
    ) : DiagramEditorIntent

    data class SetDiagramEdgeStyle(
        override val nodeId: String,
        val edgeId: String,
        val style: DiagramStyle,
    ) : DiagramEditorIntent

    /** Overrides one or both arrowheads; a null side keeps the current head. */
    data class SetDiagramEdgeArrowheads(
        override val nodeId: String,
        val edgeId: String,
        val source: DiagramArrowhead? = null,
        val target: DiagramArrowhead? = null,
    ) : DiagramEditorIntent

    /** Sets how the edge renders crossings with other edges (arc/gap/sharp jump or none). */
    data class SetDiagramEdgeLineJumps(
        override val nodeId: String,
        val edgeId: String,
        val lineJumps: LineJumpStyle,
    ) : DiagramEditorIntent

    data class AddDiagramWaypoint(
        override val nodeId: String,
        val edgeId: String,
        val index: Int,
        val x: Double,
        val y: Double,
    ) : DiagramEditorIntent

    data class MoveDiagramWaypoint(
        override val nodeId: String,
        val edgeId: String,
        val index: Int,
        val x: Double,
        val y: Double,
    ) : DiagramEditorIntent

    data class RemoveDiagramWaypoint(
        override val nodeId: String,
        val edgeId: String,
        val index: Int,
    ) : DiagramEditorIntent

    /** Sets (null [text] removes) the edge label at [position]; manual offsets survive. */
    data class SetDiagramEdgeLabel(
        override val nodeId: String,
        val edgeId: String,
        val position: DiagramEdgeLabelPosition = DiagramEdgeLabelPosition.MIDDLE,
        val text: String? = null,
        val markdown: Boolean = false,
    ) : DiagramEditorIntent

    /** Drags the edge label at [position] to a manual offset from its anchor. */
    data class MoveDiagramEdgeLabel(
        override val nodeId: String,
        val edgeId: String,
        val position: DiagramEdgeLabelPosition,
        val offsetX: Double,
        val offsetY: Double,
    ) : DiagramEditorIntent

    /** Flips an edge: endpoints, arrowheads, waypoints and SOURCE/TARGET labels swap. */
    data class ReverseDiagramEdge(
        override val nodeId: String,
        val edgeId: String,
    ) : DiagramEditorIntent

    // --- Structure: groups / z-order / layers / containers ------------------

    data class GroupDiagramNodes(
        override val nodeId: String,
        val groupId: String,
        val memberIds: List<String>,
        val name: String? = null,
    ) : DiagramEditorIntent

    data class UngroupDiagramNodes(
        override val nodeId: String,
        val groupId: String,
    ) : DiagramEditorIntent

    /** Z-order step within the element's layer (the container subtree moves as a block). */
    data class ReorderDiagramNode(
        override val nodeId: String,
        val elementId: String,
        val move: ZOrderMove,
    ) : DiagramEditorIntent

    /** Adds a layer on top; a taken/blank [layerId] is a no-op. Blank [name] = [layerId]. */
    data class AddDiagramLayer(
        override val nodeId: String,
        val layerId: String,
        val name: String = "",
    ) : DiagramEditorIntent

    /** Removes a layer; its content moves to the default layer (nothing is deleted). */
    data class RemoveDiagramLayer(
        override val nodeId: String,
        val layerId: String,
    ) : DiagramEditorIntent

    data class SetDiagramLayerVisible(
        override val nodeId: String,
        val layerId: String,
        val visible: Boolean,
    ) : DiagramEditorIntent

    data class SetDiagramLayerLocked(
        override val nodeId: String,
        val layerId: String,
        val locked: Boolean,
    ) : DiagramEditorIntent

    /** Moves a diagram node (whole subtree) to [layerId]; null = the default layer. */
    data class MoveDiagramNodeToLayer(
        override val nodeId: String,
        val elementId: String,
        val layerId: String?,
    ) : DiagramEditorIntent

    data class MoveDiagramEdgeToLayer(
        override val nodeId: String,
        val edgeId: String,
        val layerId: String?,
    ) : DiagramEditorIntent

    /**
     * Drops a node into a container (drag-in). [positionInContainer] is the desired
     * top-left in the container's coordinates; null keeps the document position.
     */
    data class DropDiagramNodeIntoContainer(
        override val nodeId: String,
        val elementId: String,
        val containerId: String,
        val positionInContainer: DiagramPoint? = null,
    ) : DiagramEditorIntent

    /** Pulls a node out of its container (drag-out); visual position is kept. */
    data class PullDiagramNodeOutOfContainer(
        override val nodeId: String,
        val elementId: String,
    ) : DiagramEditorIntent

    // --- Tables --------------------------------------------------------------

    data class AddDiagramTableRow(
        override val nodeId: String,
        val elementId: String,
        val index: Int = Int.MAX_VALUE,
        val row: TableRow = TableRow(),
    ) : DiagramEditorIntent

    data class AddDiagramTableColumn(
        override val nodeId: String,
        val elementId: String,
        val index: Int = Int.MAX_VALUE,
        val column: TableColumn = TableColumn(),
    ) : DiagramEditorIntent

    data class RemoveDiagramTableRow(
        override val nodeId: String,
        val elementId: String,
        val index: Int,
    ) : DiagramEditorIntent

    data class RemoveDiagramTableColumn(
        override val nodeId: String,
        val elementId: String,
        val index: Int,
    ) : DiagramEditorIntent

    /** Merges the fully-covered cell range into one spanning cell (invalid range = no-op). */
    data class MergeDiagramTableCells(
        override val nodeId: String,
        val elementId: String,
        val rowStart: Int,
        val rowEnd: Int,
        val columnStart: Int,
        val columnEnd: Int,
    ) : DiagramEditorIntent

    data class SplitDiagramTableCell(
        override val nodeId: String,
        val elementId: String,
        val row: Int,
        val column: Int,
    ) : DiagramEditorIntent

    /** Sets (null [text] clears) a cell's label, creating the cell when the slot is empty. */
    data class SetDiagramTableCellText(
        override val nodeId: String,
        val elementId: String,
        val row: Int,
        val column: Int,
        val text: String?,
    ) : DiagramEditorIntent

    // --- UML class members ----------------------------------------------------

    data class AddDiagramClassMember(
        override val nodeId: String,
        val elementId: String,
        val kind: UmlClassMemberKind,
        val member: UmlMember,
        val index: Int = Int.MAX_VALUE,
    ) : DiagramEditorIntent

    data class RemoveDiagramClassMember(
        override val nodeId: String,
        val elementId: String,
        val kind: UmlClassMemberKind,
        val index: Int,
    ) : DiagramEditorIntent

    data class SetDiagramClassMemberVisibility(
        override val nodeId: String,
        val elementId: String,
        val kind: UmlClassMemberKind,
        val index: Int,
        val visibility: UmlVisibility,
    ) : DiagramEditorIntent

    // --- Generation ------------------------------------------------------------

    /** Runs the deterministic auto-layout ("arrange") over the whole graph. */
    data class ApplyDiagramAutoLayout(
        override val nodeId: String,
        val kind: LayoutKind = LayoutKind.AUTO,
        val direction: LayoutDirection = LayoutDirection.TOP_DOWN,
        val preset: DiagramLayoutPreset = DiagramLayoutPreset.DEFAULT,
    ) : DiagramEditorIntent

    /**
     * Tidies the manual arrangement without re-laying it out: near-aligned rows/columns
     * snap onto shared axes, overlapping nodes separate, coordinates land on the grid
     * (topology-preserving, see [io.aequicor.visualization.subsystems.diagrams.layout.tidyAlign]).
     */
    data class ApplyDiagramTidyAlign(
        override val nodeId: String,
    ) : DiagramEditorIntent

    /**
     * Inserts a built-in starter template
     * ([io.aequicor.visualization.subsystems.diagrams.templates.diagramTemplates]) into the
     * graph: an empty canvas takes it as-is; otherwise it lands to the right of the existing
     * content with colliding ids re-minted. Unknown [templateId] is a no-op.
     */
    data class InsertDiagramTemplate(
        override val nodeId: String,
        val templateId: String,
    ) : DiagramEditorIntent

    /**
     * Text-to-diagram: parses [source] (Mermaid or PlantUML) into a laid-out graph and
     * inserts it with the same merge semantics as [InsertDiagramTemplate]. A parse failure
     * is a no-op that surfaces the first parser diagnostic as an editor warning.
     */
    data class ImportDiagramText(
        override val nodeId: String,
        val source: String,
        val format: DiagramTextFormat = DiagramTextFormat.Mermaid,
    ) : DiagramEditorIntent
}

/** Which text-to-diagram parser [DiagramEditorIntent.ImportDiagramText] runs. */
enum class DiagramTextFormat { Mermaid, PlantUml }
