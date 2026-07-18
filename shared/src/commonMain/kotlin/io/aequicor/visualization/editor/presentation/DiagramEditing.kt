package io.aequicor.visualization.editor.presentation

import io.aequicor.visualization.editor.domain.EditorSlmExtensions
import io.aequicor.visualization.editor.domain.editorSlmCompileOptions
import io.aequicor.visualization.engine.frontend.compileSlm
import io.aequicor.visualization.engine.ir.model.DesignDiagnostic
import io.aequicor.visualization.engine.ir.model.DesignNode
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.engine.ir.model.DesignSeverity
import io.aequicor.visualization.subsystems.diagrams.layout.autoLayout
import io.aequicor.visualization.subsystems.diagrams.layout.tidyAlign
import io.aequicor.visualization.subsystems.diagrams.layout.toLayoutConfig
import io.aequicor.visualization.subsystems.diagrams.model.DiagramEdge
import io.aequicor.visualization.subsystems.diagrams.model.DiagramEdgeId
import io.aequicor.visualization.subsystems.diagrams.model.DiagramEdgeLabel
import io.aequicor.visualization.subsystems.diagrams.model.DiagramEndpoint
import io.aequicor.visualization.subsystems.diagrams.model.DiagramGraph
import io.aequicor.visualization.subsystems.diagrams.model.DiagramGroup
import io.aequicor.visualization.subsystems.diagrams.model.DiagramGroupId
import io.aequicor.visualization.subsystems.diagrams.model.DiagramLabel
import io.aequicor.visualization.subsystems.diagrams.model.DiagramLayer
import io.aequicor.visualization.subsystems.diagrams.model.DiagramLayerId
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNode
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodeId
import io.aequicor.visualization.subsystems.diagrams.model.DiagramPortId
import io.aequicor.visualization.subsystems.diagrams.model.updateEdge
import io.aequicor.visualization.subsystems.diagrams.model.updateNode
import io.aequicor.visualization.subsystems.diagrams.model.withEdge
import io.aequicor.visualization.subsystems.diagrams.model.withLayer
import io.aequicor.visualization.subsystems.diagrams.model.withNode
import io.aequicor.visualization.subsystems.diagrams.model.removeEdge
import io.aequicor.visualization.subsystems.diagrams.model.removeNode
import io.aequicor.visualization.subsystems.diagrams.path.DiagramPoint
import io.aequicor.visualization.subsystems.diagrams.path.DiagramRect
import io.aequicor.visualization.subsystems.diagrams.ops.addClassField
import io.aequicor.visualization.subsystems.diagrams.ops.addClassMethod
import io.aequicor.visualization.subsystems.diagrams.ops.addCustomPort
import io.aequicor.visualization.subsystems.diagrams.ops.addTableColumn
import io.aequicor.visualization.subsystems.diagrams.ops.addTableRow
import io.aequicor.visualization.subsystems.diagrams.ops.addWaypoint
import io.aequicor.visualization.subsystems.diagrams.ops.bringForward
import io.aequicor.visualization.subsystems.diagrams.ops.bringToFront
import io.aequicor.visualization.subsystems.diagrams.ops.cloneNodeAndConnect
import io.aequicor.visualization.subsystems.diagrams.ops.pasteElements
import io.aequicor.visualization.subsystems.diagrams.ops.dropIntoContainer
import io.aequicor.visualization.subsystems.diagrams.ops.groupNodes
import io.aequicor.visualization.subsystems.diagrams.ops.mergeTableCells
import io.aequicor.visualization.subsystems.diagrams.ops.moveEdgeLabel
import io.aequicor.visualization.subsystems.diagrams.ops.moveEdgeToLayer
import io.aequicor.visualization.subsystems.diagrams.ops.moveNode
import io.aequicor.visualization.subsystems.diagrams.ops.moveNodeToLayer
import io.aequicor.visualization.subsystems.diagrams.ops.moveWaypoint
import io.aequicor.visualization.subsystems.diagrams.ops.primaryText
import io.aequicor.visualization.subsystems.diagrams.ops.pullOutOfContainer
import io.aequicor.visualization.subsystems.diagrams.ops.reconnectEdge
import io.aequicor.visualization.subsystems.diagrams.ops.removeClassMember
import io.aequicor.visualization.subsystems.diagrams.ops.removeLayer
import io.aequicor.visualization.subsystems.diagrams.ops.removePort
import io.aequicor.visualization.subsystems.diagrams.ops.removeTableColumn
import io.aequicor.visualization.subsystems.diagrams.ops.removeTableRow
import io.aequicor.visualization.subsystems.diagrams.ops.removeWaypoint
import io.aequicor.visualization.subsystems.diagrams.ops.resizeNode
import io.aequicor.visualization.subsystems.diagrams.ops.reverseEdge
import io.aequicor.visualization.subsystems.diagrams.ops.sendBackward
import io.aequicor.visualization.subsystems.diagrams.ops.sendToBack
import io.aequicor.visualization.subsystems.diagrams.ops.setClassMemberVisibility
import io.aequicor.visualization.subsystems.diagrams.ops.setEdgeLabel
import io.aequicor.visualization.subsystems.diagrams.ops.setEdgeRelation
import io.aequicor.visualization.subsystems.diagrams.ops.setEdgeRouting
import io.aequicor.visualization.subsystems.diagrams.ops.setEdgeStyle
import io.aequicor.visualization.subsystems.diagrams.ops.setLayerLocked
import io.aequicor.visualization.subsystems.diagrams.ops.setLayerVisible
import io.aequicor.visualization.subsystems.diagrams.ops.setNodeStyle
import io.aequicor.visualization.subsystems.diagrams.ops.setNodeText
import io.aequicor.visualization.subsystems.diagrams.ops.setTableCellText
import io.aequicor.visualization.subsystems.diagrams.ops.splitTableCell
import io.aequicor.visualization.subsystems.diagrams.ops.ungroupNodes
import io.aequicor.visualization.subsystems.diagrams.ops.UmlClassMemberKind
import io.aequicor.visualization.subsystems.diagrams.slm.applyDiagramWriteBack
import io.aequicor.visualization.subsystems.diagrams.templates.diagramTemplates
import io.aequicor.visualization.subsystems.diagrams.text.TextDiagramResult
import io.aequicor.visualization.subsystems.diagrams.text.mermaidToDiagram
import io.aequicor.visualization.subsystems.diagrams.text.plantUmlToDiagram

/**
 * Reducer arm for [DiagramEditorIntent]: every handler is a pure `:subsystems:diagrams`
 * op over the target diagram node's graph, routed through [diagramWriteBack] (SLM
 * `diagram:` block replacement + round-trip veto, in-memory fallback on any drift).
 */
internal fun DesignEditorState.reduceDiagramIntent(intent: DiagramEditorIntent): DesignEditorState = when (intent) {
    // --- Elements ---
    is DiagramEditorIntent.AddDiagramNode -> diagramWriteBack(intent.nodeId) { graph ->
        val id = DiagramNodeId(intent.elementId)
        if (intent.elementId.isBlank() || graph.nodeById(id) != null) {
            graph
        } else {
            graph.withNode(
                DiagramNode(
                    id = id,
                    x = intent.x,
                    y = intent.y,
                    width = intent.width.coerceAtLeast(0.0),
                    height = intent.height.coerceAtLeast(0.0),
                    payload = intent.payload,
                    ports = intent.ports,
                    labels = intent.label?.let { listOf(DiagramLabel(it)) }.orEmpty(),
                ),
            )
        }
    }
    is DiagramEditorIntent.DeleteDiagramElement ->
        detachAnnotationsForDiagramNodeDelete(intent.nodeId, intent.elementIds).diagramWriteBack(intent.nodeId) { graph ->
            val afterNodes = intent.elementIds.fold(graph) { g, id -> g.removeNode(DiagramNodeId(id)) }
            intent.edgeIds.fold(afterNodes) { g, id -> g.removeEdge(DiagramEdgeId(id)) }
        }
    is DiagramEditorIntent.MoveDiagramNode -> diagramWriteBack(intent.nodeId) {
        it.moveNode(DiagramNodeId(intent.elementId), intent.dx, intent.dy)
    }
    is DiagramEditorIntent.ResizeDiagramNode -> diagramWriteBack(intent.nodeId) {
        it.resizeNode(
            id = DiagramNodeId(intent.elementId),
            bounds = DiagramRect(intent.x, intent.y, intent.width.coerceAtLeast(0.0), intent.height.coerceAtLeast(0.0)),
            resizeChildren = intent.resizeChildren,
        )
    }
    is DiagramEditorIntent.SetDiagramNodeLabel -> diagramWriteBack(intent.nodeId) {
        it.setNodeText(DiagramNodeId(intent.elementId), intent.text)
    }
    is DiagramEditorIntent.SetDiagramNodeSizing -> diagramWriteBack(intent.nodeId) { graph ->
        graph.updateNode(DiagramNodeId(intent.elementId)) { it.copy(sizing = intent.sizing) }
    }
    is DiagramEditorIntent.SetDiagramNodeStyle -> diagramWriteBack(intent.nodeId) {
        it.setNodeStyle(DiagramNodeId(intent.elementId), intent.style)
    }
    is DiagramEditorIntent.SetDiagramNodePayload -> diagramWriteBack(intent.nodeId) { graph ->
        // The new payload arrives as a palette template carrying a placeholder caption ("Use case"),
        // so carry the authored text across the type switch instead of stamping over it.
        val id = DiagramNodeId(intent.elementId)
        val authored = graph.nodeById(id)?.primaryText()
        val retyped = graph.updateNode(id) { element -> element.copy(payload = intent.payload) }
        if (authored.isNullOrBlank()) retyped else retyped.setNodeText(id, authored)
    }
    is DiagramEditorIntent.AddDiagramPort -> diagramWriteBack(intent.nodeId) {
        it.addCustomPort(DiagramNodeId(intent.elementId), intent.port)
    }
    is DiagramEditorIntent.RemoveDiagramPort -> diagramWriteBack(intent.nodeId) {
        it.removePort(DiagramNodeId(intent.elementId), DiagramPortId(intent.portId))
    }
    is DiagramEditorIntent.PasteDiagramElements -> diagramWriteBack(intent.nodeId) {
        it.pasteElements(
            nodes = intent.nodes,
            edges = intent.edges,
            nodeIds = intent.nodeIds.entries.associate { (old, new) -> DiagramNodeId(old) to DiagramNodeId(new) },
            edgeIds = intent.edgeIds.entries.associate { (old, new) -> DiagramEdgeId(old) to DiagramEdgeId(new) },
            offsetX = intent.offsetX,
            offsetY = intent.offsetY,
        )
    }
    is DiagramEditorIntent.CloneDiagramNodeAndConnect -> diagramWriteBack(intent.nodeId) {
        it.cloneNodeAndConnect(
            sourceId = DiagramNodeId(intent.sourceElementId),
            cloneId = DiagramNodeId(intent.cloneId),
            edgeId = DiagramEdgeId(intent.edgeId),
            offsetX = intent.offsetX,
            offsetY = intent.offsetY,
            relation = intent.relation,
            routing = intent.routing,
        )
    }

    // --- Edges ---
    is DiagramEditorIntent.ConnectDiagramNodes -> diagramWriteBack(intent.nodeId) { graph ->
        val id = DiagramEdgeId(intent.edgeId)
        val valid = intent.edgeId.isNotBlank() &&
            graph.edgeById(id) == null &&
            graph.endpointResolvable(intent.source) &&
            graph.endpointResolvable(intent.target)
        if (!valid) {
            graph
        } else {
            graph.withEdge(
                DiagramEdge(
                    id = id,
                    source = intent.source,
                    target = intent.target,
                    relation = intent.relation,
                    routing = intent.routing,
                    labels = intent.label?.let { listOf(DiagramEdgeLabel(DiagramLabel(it))) }.orEmpty(),
                ),
            )
        }
    }
    is DiagramEditorIntent.ReconnectDiagramEdge -> diagramWriteBack(intent.nodeId) {
        it.reconnectEdge(DiagramEdgeId(intent.edgeId), intent.end, intent.endpoint)
    }
    is DiagramEditorIntent.SetDiagramEdgeRelation -> diagramWriteBack(intent.nodeId) {
        it.setEdgeRelation(DiagramEdgeId(intent.edgeId), intent.relation)
    }
    is DiagramEditorIntent.SetDiagramEdgeRouting -> diagramWriteBack(intent.nodeId) {
        it.setEdgeRouting(DiagramEdgeId(intent.edgeId), intent.routing)
    }
    is DiagramEditorIntent.SetDiagramEdgePattern -> diagramWriteBack(intent.nodeId) { graph ->
        val edge = graph.edgeById(DiagramEdgeId(intent.edgeId))
        if (edge == null) graph
        else graph.setEdgeStyle(edge.id, edge.style.copy(pattern = intent.pattern))
    }
    is DiagramEditorIntent.SetDiagramEdgeStyle -> diagramWriteBack(intent.nodeId) {
        it.setEdgeStyle(DiagramEdgeId(intent.edgeId), intent.style)
    }
    is DiagramEditorIntent.SetDiagramEdgeArrowheads -> diagramWriteBack(intent.nodeId) { graph ->
        val id = DiagramEdgeId(intent.edgeId)
        graph.updateEdge(id) { edge ->
            edge.copy(
                sourceArrowhead = intent.source ?: edge.sourceArrowhead,
                targetArrowhead = intent.target ?: edge.targetArrowhead,
            )
        }
    }
    is DiagramEditorIntent.SetDiagramEdgeLineJumps -> diagramWriteBack(intent.nodeId) { graph ->
        graph.updateEdge(DiagramEdgeId(intent.edgeId)) { edge -> edge.copy(lineJumps = intent.lineJumps) }
    }
    is DiagramEditorIntent.AddDiagramWaypoint -> diagramWriteBack(intent.nodeId) {
        it.addWaypoint(DiagramEdgeId(intent.edgeId), intent.index, diagramPoint(intent.x, intent.y))
    }
    is DiagramEditorIntent.MoveDiagramWaypoint -> diagramWriteBack(intent.nodeId) {
        it.moveWaypoint(DiagramEdgeId(intent.edgeId), intent.index, diagramPoint(intent.x, intent.y))
    }
    is DiagramEditorIntent.RemoveDiagramWaypoint -> diagramWriteBack(intent.nodeId) {
        it.removeWaypoint(DiagramEdgeId(intent.edgeId), intent.index)
    }
    is DiagramEditorIntent.SetDiagramEdgeLabel -> diagramWriteBack(intent.nodeId) {
        it.setEdgeLabel(DiagramEdgeId(intent.edgeId), intent.position, intent.text, intent.markdown)
    }
    is DiagramEditorIntent.MoveDiagramEdgeLabel -> diagramWriteBack(intent.nodeId) {
        it.moveEdgeLabel(DiagramEdgeId(intent.edgeId), intent.position, intent.offsetX, intent.offsetY)
    }
    is DiagramEditorIntent.ReverseDiagramEdge -> diagramWriteBack(intent.nodeId) {
        it.reverseEdge(DiagramEdgeId(intent.edgeId))
    }

    // --- Structure ---
    is DiagramEditorIntent.GroupDiagramNodes -> diagramWriteBack(intent.nodeId) {
        it.groupNodes(DiagramGroupId(intent.groupId), intent.memberIds.map(::DiagramNodeId), intent.name)
    }
    is DiagramEditorIntent.UngroupDiagramNodes -> diagramWriteBack(intent.nodeId) {
        it.ungroupNodes(DiagramGroupId(intent.groupId))
    }
    is DiagramEditorIntent.ReorderDiagramNode -> diagramWriteBack(intent.nodeId) { graph ->
        val id = DiagramNodeId(intent.elementId)
        when (intent.move) {
            ZOrderMove.Forward -> graph.bringForward(id)
            ZOrderMove.Backward -> graph.sendBackward(id)
            ZOrderMove.ToFront -> graph.bringToFront(id)
            ZOrderMove.ToBack -> graph.sendToBack(id)
        }
    }
    is DiagramEditorIntent.AddDiagramLayer -> diagramWriteBack(intent.nodeId) { graph ->
        val id = DiagramLayerId(intent.layerId)
        if (intent.layerId.isBlank() || graph.layerById(id) != null) graph
        else graph.withLayer(DiagramLayer(id, intent.name.ifBlank { intent.layerId }))
    }
    is DiagramEditorIntent.RemoveDiagramLayer -> diagramWriteBack(intent.nodeId) {
        it.removeLayer(DiagramLayerId(intent.layerId))
    }
    is DiagramEditorIntent.SetDiagramLayerVisible -> diagramWriteBack(intent.nodeId) {
        it.setLayerVisible(DiagramLayerId(intent.layerId), intent.visible)
    }
    is DiagramEditorIntent.SetDiagramLayerLocked -> diagramWriteBack(intent.nodeId) {
        it.setLayerLocked(DiagramLayerId(intent.layerId), intent.locked)
    }
    is DiagramEditorIntent.MoveDiagramNodeToLayer -> diagramWriteBack(intent.nodeId) {
        it.moveNodeToLayer(DiagramNodeId(intent.elementId), intent.layerId?.let(::DiagramLayerId))
    }
    is DiagramEditorIntent.MoveDiagramEdgeToLayer -> diagramWriteBack(intent.nodeId) {
        it.moveEdgeToLayer(DiagramEdgeId(intent.edgeId), intent.layerId?.let(::DiagramLayerId))
    }
    is DiagramEditorIntent.DropDiagramNodeIntoContainer -> diagramWriteBack(intent.nodeId) {
        it.dropIntoContainer(DiagramNodeId(intent.elementId), DiagramNodeId(intent.containerId), intent.positionInContainer)
    }
    is DiagramEditorIntent.PullDiagramNodeOutOfContainer -> diagramWriteBack(intent.nodeId) {
        it.pullOutOfContainer(DiagramNodeId(intent.elementId))
    }

    // --- Tables ---
    is DiagramEditorIntent.AddDiagramTableRow -> diagramWriteBack(intent.nodeId) {
        it.addTableRow(DiagramNodeId(intent.elementId), intent.index, intent.row)
    }
    is DiagramEditorIntent.AddDiagramTableColumn -> diagramWriteBack(intent.nodeId) {
        it.addTableColumn(DiagramNodeId(intent.elementId), intent.index, intent.column)
    }
    is DiagramEditorIntent.RemoveDiagramTableRow -> diagramWriteBack(intent.nodeId) {
        it.removeTableRow(DiagramNodeId(intent.elementId), intent.index)
    }
    is DiagramEditorIntent.RemoveDiagramTableColumn -> diagramWriteBack(intent.nodeId) {
        it.removeTableColumn(DiagramNodeId(intent.elementId), intent.index)
    }
    is DiagramEditorIntent.MergeDiagramTableCells -> diagramWriteBack(intent.nodeId) {
        it.mergeTableCells(
            id = DiagramNodeId(intent.elementId),
            rowRange = intent.rowStart..intent.rowEnd,
            columnRange = intent.columnStart..intent.columnEnd,
        )
    }
    is DiagramEditorIntent.SplitDiagramTableCell -> diagramWriteBack(intent.nodeId) {
        it.splitTableCell(DiagramNodeId(intent.elementId), intent.row, intent.column)
    }
    is DiagramEditorIntent.SetDiagramTableCellText -> diagramWriteBack(intent.nodeId) {
        it.setTableCellText(DiagramNodeId(intent.elementId), intent.row, intent.column, intent.text)
    }

    // --- UML class members ---
    is DiagramEditorIntent.AddDiagramClassMember -> diagramWriteBack(intent.nodeId) { graph ->
        when (intent.kind) {
            UmlClassMemberKind.ATTRIBUTE -> graph.addClassField(DiagramNodeId(intent.elementId), intent.member, intent.index)
            UmlClassMemberKind.OPERATION -> graph.addClassMethod(DiagramNodeId(intent.elementId), intent.member, intent.index)
        }
    }
    is DiagramEditorIntent.RemoveDiagramClassMember -> diagramWriteBack(intent.nodeId) {
        it.removeClassMember(DiagramNodeId(intent.elementId), intent.kind, intent.index)
    }
    is DiagramEditorIntent.SetDiagramClassMemberVisibility -> diagramWriteBack(intent.nodeId) {
        it.setClassMemberVisibility(DiagramNodeId(intent.elementId), intent.kind, intent.index, intent.visibility)
    }

    // --- Generation ---
    is DiagramEditorIntent.ApplyDiagramAutoLayout -> diagramWriteBack(intent.nodeId) {
        autoLayout(it, intent.kind, intent.preset.toLayoutConfig(intent.direction))
    }
    is DiagramEditorIntent.ApplyDiagramTidyAlign -> diagramWriteBack(intent.nodeId) {
        tidyAlign(it)
    }
    is DiagramEditorIntent.InsertDiagramTemplate -> diagramWriteBack(intent.nodeId) { graph ->
        val template = diagramTemplates().firstOrNull { it.id == intent.templateId }
        if (template == null) graph else mergeDiagramGraphs(graph, template.graph)
    }
    is DiagramEditorIntent.ImportDiagramText -> {
        val parsed = when (intent.format) {
            DiagramTextFormat.Mermaid -> mermaidToDiagram(intent.source)
            DiagramTextFormat.PlantUml -> plantUmlToDiagram(intent.source)
        }
        when (parsed) {
            is TextDiagramResult.Success -> diagramWriteBack(intent.nodeId) { mergeDiagramGraphs(it, parsed.graph) }
            is TextDiagramResult.Failure -> {
                val first = parsed.diagnostics.firstOrNull()
                if (first == null) this
                else copy(
                    diagnostics = diagnostics + DesignDiagnostic(
                        severity = DesignSeverity.Warning,
                        message = "Diagram import failed (line ${first.line}): ${first.message}",
                    ),
                )
            }
        }
    }
}

// --- Write-back ---------------------------------------------------------------

/**
 * Applies [transform] to the diagram node's graph and commits the result: the owning SLM
 * source is surgically patched with the canonical emission of the new graph — the CNL
 * `## Diagram: …` container body, the only diagram authoring surface
 * (`applyDiagramWriteBack`, which recompiles the patched source and
 * vetoes any round-trip drift), the source is recompiled with the editor's extension
 * registry, and the working document mirrors the change in lock-step (one undo entry).
 *
 * Falls back to an in-memory-only edit — every source byte-identical — when the node has
 * no owning/addressable source or the patch/recompile/veto fails, surfacing the write-back
 * reason as a warning diagnostic. A non-diagram or locked node, and an identity [transform],
 * are no-ops.
 */
internal fun DesignEditorState.diagramWriteBack(
    nodeId: String,
    transform: (DiagramGraph) -> DiagramGraph,
): DesignEditorState {
    val document = document ?: return this
    val node = document.nodeById(nodeId) ?: return this
    if (node.locked) return this
    val kind = node.kind as? DesignNodeKind.Diagram ?: return this
    val newGraph = transform(kind.graph)
    if (newGraph == kind.graph) return this

    val patch: (DesignNode) -> DesignNode = { n ->
        if (n.kind is DesignNodeKind.Diagram) n.copy(kind = DesignNodeKind.Diagram(newGraph)) else n
    }
    // Diagram drags can emit many graph updates. Keep only the preview in memory; the unified
    // interaction contract writes the final graph once at EndInteraction.
    if (interacting) return editUnlockedNode(nodeId, patch)
    if (sources.isEmpty() || sources.size != compiledResults.size) return editUnlockedNode(nodeId, patch)
    val index = owningSourceIndex(nodeId) ?: return editUnlockedNode(nodeId, patch)
    val source = sources[index]

    val result = applyDiagramWriteBack(
        source = source.content,
        nodeId = nodeId,
        graph = newGraph,
        extensions = EditorSlmExtensions,
        fileName = source.fileName,
    )
    val newSource = result.newSource ?: return editUnlockedNode(nodeId, patch).withDiagramFallbackWarning(result.message)
    val recompiled = compileSlm(newSource, editorSlmCompileOptions(source.fileName))
    if (!recompiled.isSuccess) return editUnlockedNode(nodeId, patch)

    val newSources = sources.toMutableList().apply { this[index] = source.copy(content = newSource) }.toList()
    val newCompiled = compiledResults.toMutableList().apply { this[index] = recompiled }.toList()
    return copy(
        document = document.updateNode(nodeId, patch),
        sources = newSources,
        compiledResults = newCompiled,
        previousSources = (previousSources + listOf(sources)).takeLast(MaxSourceHistory),
        // Fork in-memory history like every write-back edit (see writeBackEdits).
        undoStack = if (interacting) undoStack else (undoStack + document).takeLast(MaxDocumentHistory),
        redoStack = if (interacting) redoStack else emptyList(),
    )
}

private fun DesignEditorState.withDiagramFallbackWarning(message: String?): DesignEditorState =
    if (message == null) this
    else copy(
        diagnostics = diagnostics + DesignDiagnostic(
            severity = DesignSeverity.Warning,
            message = "Diagram edit kept in memory: $message",
        ),
    )

// --- Pure helpers ---------------------------------------------------------------

private fun diagramPoint(x: Double, y: Double): DiagramPoint = DiagramPoint(x, y)

/** True when [endpoint] can attach to this graph (node exists; fixed port declared). */
private fun DiagramGraph.endpointResolvable(endpoint: DiagramEndpoint): Boolean = when (endpoint) {
    is DiagramEndpoint.FreePoint -> true
    is DiagramEndpoint.FloatingAnchor -> nodeById(endpoint.nodeId) != null
    is DiagramEndpoint.FixedPort -> nodeById(endpoint.nodeId)?.portById(endpoint.portId) != null
}

/**
 * Merges [addition] into [base] (template insert / text-to-diagram import): an empty [base]
 * takes [addition] as-is; otherwise the addition lands to the right of the existing content
 * (top-aligned) and every colliding node/edge/layer/group id is re-minted with a `_n`
 * suffix, with all internal references (endpoints, parents, layers, group members) and
 * geometry (waypoints, free endpoints) remapped consistently.
 */
internal fun mergeDiagramGraphs(base: DiagramGraph, addition: DiagramGraph): DiagramGraph {
    if (base == DiagramGraph.Empty) return addition
    if (addition.nodes.isEmpty() && addition.edges.isEmpty() && addition.layers.isEmpty() && addition.groups.isEmpty()) {
        return base
    }

    val baseRight = base.nodes.maxOfOrNull { it.x + it.width } ?: 0.0
    val baseTop = base.nodes.minOfOrNull { it.y } ?: 0.0
    val addLeft = addition.nodes.minOfOrNull { it.x } ?: 0.0
    val addTop = addition.nodes.minOfOrNull { it.y } ?: 0.0
    val dx = if (addition.nodes.isEmpty()) 0.0 else baseRight + MergedContentGap - addLeft
    val dy = if (addition.nodes.isEmpty()) 0.0 else baseTop - addTop

    val nodeIds = remapIds(base.nodes.map { it.id.value }, addition.nodes.map { it.id.value })
    val edgeIds = remapIds(base.edges.map { it.id.value }, addition.edges.map { it.id.value })
    val layerIds = remapIds(base.layers.map { it.id.value }, addition.layers.map { it.id.value })
    val groupIds = remapIds(base.groups.map { it.id.value }, addition.groups.map { it.id.value })

    fun nodeRef(id: DiagramNodeId): DiagramNodeId = DiagramNodeId(nodeIds[id.value] ?: id.value)
    fun layerRef(id: DiagramLayerId?): DiagramLayerId? = id?.let { DiagramLayerId(layerIds[it.value] ?: it.value) }
    fun endpoint(e: DiagramEndpoint): DiagramEndpoint = when (e) {
        is DiagramEndpoint.FloatingAnchor -> DiagramEndpoint.FloatingAnchor(nodeRef(e.nodeId))
        is DiagramEndpoint.FixedPort -> DiagramEndpoint.FixedPort(nodeRef(e.nodeId), e.portId)
        is DiagramEndpoint.FreePoint -> DiagramEndpoint.FreePoint(e.x + dx, e.y + dy)
    }

    val mergedNodes = addition.nodes.map { n ->
        n.copy(
            id = nodeRef(n.id),
            x = n.x + dx,
            y = n.y + dy,
            parentId = n.parentId?.let(::nodeRef),
            layerId = layerRef(n.layerId),
        )
    }
    val mergedEdges = addition.edges.map { e ->
        e.copy(
            id = DiagramEdgeId(edgeIds[e.id.value] ?: e.id.value),
            source = endpoint(e.source),
            target = endpoint(e.target),
            waypoints = e.waypoints.map { p -> p.copy(x = p.x + dx, y = p.y + dy) },
            layerId = layerRef(e.layerId),
        )
    }
    val mergedLayers = addition.layers.map { it.copy(id = DiagramLayerId(layerIds[it.id.value] ?: it.id.value)) }
    val mergedGroups = addition.groups.map { g ->
        DiagramGroup(
            id = DiagramGroupId(groupIds[g.id.value] ?: g.id.value),
            memberIds = g.memberIds.map(::nodeRef),
            name = g.name,
        )
    }
    return DiagramGraph(
        nodes = base.nodes + mergedNodes,
        edges = base.edges + mergedEdges,
        layers = base.layers + mergedLayers,
        groups = base.groups + mergedGroups,
    )
}

private const val MergedContentGap: Double = 80.0

/** old id -> fresh id for every [addition] id, colliding ones re-minted with a `_n` suffix. */
private fun remapIds(taken: List<String>, addition: List<String>): Map<String, String> {
    val used = taken.toMutableSet()
    return addition.associateWith { id ->
        if (used.add(id)) {
            id
        } else {
            var n = 2
            while (!used.add("${id}_$n")) n++
            "${id}_$n"
        }
    }
}
