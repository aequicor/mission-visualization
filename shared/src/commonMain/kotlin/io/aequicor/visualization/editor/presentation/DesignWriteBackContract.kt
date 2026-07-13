package io.aequicor.visualization.editor.presentation

import io.aequicor.visualization.editor.domain.EditorSlmExtensions
import io.aequicor.visualization.editor.domain.MissionDocumentSource
import io.aequicor.visualization.editor.domain.MissionDocuments
import io.aequicor.visualization.editor.domain.annotationLayersFrom
import io.aequicor.visualization.editor.domain.compileMissionDocuments
import io.aequicor.visualization.editor.domain.editorSlmCompileOptions
import io.aequicor.visualization.engine.frontend.compileSlm
import io.aequicor.visualization.engine.frontend.edit.ReemitNode
import io.aequicor.visualization.engine.frontend.edit.applySlmEdits
import io.aequicor.visualization.engine.ir.model.DesignDiagnostic
import io.aequicor.visualization.engine.ir.model.DesignDocument
import io.aequicor.visualization.engine.ir.model.DesignNode
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.engine.ir.model.DesignSeverity
import io.aequicor.visualization.subsystems.diagrams.slm.applyDiagramWriteBack

/** Persistence category for every editor command. The exhaustive `when` is the intent matrix. */
internal enum class DesignIntentPersistence {
    NonDocument,
    SourceManaged,
    Preview,
    WriteBack,
    BeginInteraction,
    EndInteraction,
    CancelInteraction,
    History,
    Unsupported,
}

/**
 * Central write-back policy. Adding a new top-level [DesignEditorIntent] cannot compile until it is
 * classified here, so persistence is no longer an optional convention inside individual cases.
 */
internal fun DesignEditorIntent.persistenceCategory(): DesignIntentPersistence = when (this) {
    is DesignEditorIntent.SelectPage,
    is DesignEditorIntent.SelectNode,
    is DesignEditorIntent.SelectNodes,
    is DesignEditorIntent.ToggleNodeSelection,
    DesignEditorIntent.ClearSelection,
    DesignEditorIntent.SelectAll,
    is DesignEditorIntent.SetEditingText,
    is DesignEditorIntent.ToggleAnnotationExpanded,
    is DesignEditorIntent.SelectAnnotation,
    is DesignEditorIntent.SetAnnotationTool,
    -> DesignIntentPersistence.NonDocument

    is DesignEditorIntent.EditSource,
    is DesignEditorIntent.AddAnnotation,
    is DesignEditorIntent.SetAnnotationText,
    is DesignEditorIntent.SetAnnotationKind,
    is DesignEditorIntent.AttachAnnotationImage,
    is DesignEditorIntent.DetachAnnotationImage,
    is DesignEditorIntent.MoveAnnotation,
    is DesignEditorIntent.AttachAnnotationToNode,
    is DesignEditorIntent.DetachAnnotationAnchor,
    is DesignEditorIntent.AddAnnotationReference,
    is DesignEditorIntent.RemoveAnnotationReference,
    is DesignEditorIntent.DeleteAnnotation,
    -> DesignIntentPersistence.SourceManaged

    is DesignEditorIntent.UpdatePosition,
    is DesignEditorIntent.UpdateSize,
    is DesignEditorIntent.MoveNodes,
    is DesignEditorIntent.SetRotation,
    is DesignEditorIntent.PreviewCornerRadiusPerCorner,
    is DesignEditorIntent.MoveVectorPoint,
    is DesignEditorIntent.MoveVectorVertex,
    is DesignEditorIntent.MoveVectorHandle,
    -> DesignIntentPersistence.Preview

    DesignEditorIntent.BeginInteraction -> DesignIntentPersistence.BeginInteraction
    DesignEditorIntent.EndInteraction -> DesignIntentPersistence.EndInteraction
    DesignEditorIntent.CancelInteraction -> DesignIntentPersistence.CancelInteraction
    DesignEditorIntent.Undo,
    DesignEditorIntent.Redo,
    -> DesignIntentPersistence.History

    is DiagramEditorIntent,
    is DesignEditorIntent.PositionNode,
    is DesignEditorIntent.ResizeNode,
    is DesignEditorIntent.UpdateSizingMode,
    is DesignEditorIntent.UpdateConstraints,
    is DesignEditorIntent.RotateNode,
    is DesignEditorIntent.SetAbsolutePosition,
    is DesignEditorIntent.FlipHorizontal,
    is DesignEditorIntent.FlipVertical,
    is DesignEditorIntent.SetVisible,
    is DesignEditorIntent.SetLocked,
    is DesignEditorIntent.RenameNode,
    is DesignEditorIntent.DeleteNodes,
    is DesignEditorIntent.DuplicateNodes,
    is DesignEditorIntent.GroupNodes,
    is DesignEditorIntent.ReorderNode,
    is DesignEditorIntent.ReparentNode,
    is DesignEditorIntent.DetachInstance,
    is DesignEditorIntent.CreateObject,
    is DesignEditorIntent.CreateScreen,
    is DesignEditorIntent.DuplicateScreen,
    is DesignEditorIntent.DeleteScreen,
    is DesignEditorIntent.CreateDiagramObject,
    is DesignEditorIntent.AddResourceMedia,
    is DesignEditorIntent.ConvertContainer,
    is DesignEditorIntent.SetLayoutMode,
    is DesignEditorIntent.SetLayoutGap,
    is DesignEditorIntent.SetLayoutPadding,
    is DesignEditorIntent.SetLayoutAlign,
    is DesignEditorIntent.SetClipsContent,
    is DesignEditorIntent.SetSticky,
    is DesignEditorIntent.UpdateOpacity,
    is DesignEditorIntent.SetBlendMode,
    is DesignEditorIntent.UpdateCornerRadius,
    is DesignEditorIntent.UpdateCornerRadiusPerCorner,
    is DesignEditorIntent.UpdateSolidFill,
    is DesignEditorIntent.FillCommand,
    is DesignEditorIntent.UpdateStroke,
    is DesignEditorIntent.StrokeCommand,
    is DesignEditorIntent.UpdateTypography,
    is DesignEditorIntent.SetTextRangeStyleRef,
    is DesignEditorIntent.SetTextLink,
    is DesignEditorIntent.SetTextCharacters,
    is DesignEditorIntent.SetTextAutoResize,
    is DesignEditorIntent.SetTextTruncate,
    is DesignEditorIntent.SetTextList,
    is DesignEditorIntent.SetShapeType,
    is DesignEditorIntent.SetPointCount,
    is DesignEditorIntent.SetStarInnerRadius,
    is DesignEditorIntent.SetArcStart,
    is DesignEditorIntent.SetArcSweep,
    is DesignEditorIntent.SetArcRatio,
    is DesignEditorIntent.SetIconRef,
    is DesignEditorIntent.SetPathRef,
    is DesignEditorIntent.SetVectorViewBox,
    is DesignEditorIntent.SetBooleanOperation,
    is DesignEditorIntent.SetWindingRule,
    is DesignEditorIntent.SetVertexCornerRadius,
    is DesignEditorIntent.SetRegionFill,
    is DesignEditorIntent.ConvertToEditableVector,
    is DesignEditorIntent.FlattenNode,
    is DesignEditorIntent.OutlineStroke,
    is DesignEditorIntent.SetVertexMirror,
    is DesignEditorIntent.ToggleVertexCorner,
    is DesignEditorIntent.AddVectorVertex,
    is DesignEditorIntent.AppendVectorVertex,
    is DesignEditorIntent.DeleteVectorVertex,
    is DesignEditorIntent.CloseVectorNetwork,
    is DesignEditorIntent.CommitVectorNetwork,
    is DesignEditorIntent.InteractionCommand,
    is DesignEditorIntent.MotionCommand,
    -> DesignIntentPersistence.WriteBack

    is DesignEditorIntent.UpdateTypographyRange,
    is DesignEditorIntent.SetTextRangeFills,
    -> DesignIntentPersistence.Unsupported

    is DesignEditorIntent.EffectCommand -> when (op) {
        is EffectOp.ToggleAt -> DesignIntentPersistence.Unsupported
        else -> DesignIntentPersistence.WriteBack
    }

    is DesignEditorIntent.RegionFillCommand -> when (val operation = op) {
        is FillOp.SetType -> if (operation.type == FillKind.Solid) {
            DesignIntentPersistence.WriteBack
        } else {
            DesignIntentPersistence.Unsupported
        }
        else -> DesignIntentPersistence.WriteBack
    }
}

/** Public reducer entrypoint with the invariant: committed document changes always have SLM. */
fun reduceDesignEditor(state: DesignEditorState, intent: DesignEditorIntent): DesignEditorState =
    when (intent.persistenceCategory()) {
        DesignIntentPersistence.NonDocument,
        DesignIntentPersistence.SourceManaged,
        -> reduceDesignEditorUnchecked(state, intent)

        DesignIntentPersistence.Preview -> reducePreview(state, intent)
        DesignIntentPersistence.WriteBack -> reduceCommitted(state, intent)
        DesignIntentPersistence.BeginInteraction -> beginAtomicInteraction(state)
        DesignIntentPersistence.EndInteraction -> endAtomicInteraction(state)
        DesignIntentPersistence.CancelInteraction -> cancelAtomicInteraction(state)
        DesignIntentPersistence.Unsupported -> state.writeBackRejected(
            intent,
            "operation does not support SLM write-back in the current CNL grammar",
        )
        DesignIntentPersistence.History -> when (intent) {
            DesignEditorIntent.Undo -> state.atomicUndo()
            DesignEditorIntent.Redo -> state.atomicRedo()
            else -> state
        }
    }

private fun reducePreview(state: DesignEditorState, intent: DesignEditorIntent): DesignEditorState {
    val candidate = reduceDesignEditorUnchecked(state, intent)
    if (candidate.document == state.document) return candidate
    if (!state.interacting) {
        return state.writeBackRejected(intent, "preview command used outside BeginInteraction/EndInteraction")
    }
    return candidate.copy(
        sources = state.sources,
        compiledResults = state.compiledResults,
        previousSources = state.previousSources,
        undoSourcesStack = state.undoSourcesStack,
        redoSourcesStack = state.redoSourcesStack,
    )
}

private fun reduceCommitted(state: DesignEditorState, intent: DesignEditorIntent): DesignEditorState {
    val candidate = reduceDesignEditorUnchecked(state, intent)
    if (state.interacting) {
        // No source escapes during a gesture. EndInteraction replays these prepared commands and
        // validates the complete multi-node/multi-file result before publishing it atomically. A
        // commit may be document-inert because its preview already applied the final value (for
        // example UpdateSize followed by ResizeNode); it still must be queued to patch the source.
        val pending = if (intent is DiagramEditorIntent || intent is DesignEditorIntent.CommitVectorNetwork) {
            state.interactionPendingIntents
        } else {
            state.interactionPendingIntents + intent
        }
        return candidate.copy(
            sources = state.sources,
            compiledResults = state.compiledResults,
            diagnostics = state.diagnostics,
            previousSources = state.previousSources,
            undoStack = state.undoStack,
            redoStack = state.redoStack,
            undoSourcesStack = state.undoSourcesStack,
            redoSourcesStack = state.redoSourcesStack,
            interactionSourcesCheckpoint = state.interactionSourcesCheckpoint,
            interactionCompiledCheckpoint = state.interactionCompiledCheckpoint,
            interactionPendingIntents = pending,
        )
    }
    if (candidate.document == state.document) return candidate
    return persistCandidate(state, candidate, intent)
}

private fun beginAtomicInteraction(state: DesignEditorState): DesignEditorState {
    if (state.interacting) return state
    val begun = reduceDesignEditorUnchecked(state, DesignEditorIntent.BeginInteraction)
    return begun.copy(
        undoSourcesStack = if (state.document == null) state.undoSourcesStack
        else (state.undoSourcesStack + listOf(state.sources)).takeLast(MaxDocumentHistory),
        redoSourcesStack = emptyList(),
        interactionSourcesCheckpoint = state.sources,
        interactionCompiledCheckpoint = state.compiledResults,
        interactionPendingIntents = emptyList(),
    )
}

private fun cancelAtomicInteraction(state: DesignEditorState): DesignEditorState {
    if (!state.interacting) return state
    val canceled = reduceDesignEditorUnchecked(state, DesignEditorIntent.CancelInteraction)
    val hadDocumentCheckpoint = state.undoStack.isNotEmpty()
    return canceled.copy(
        sources = state.interactionSourcesCheckpoint ?: state.sources,
        compiledResults = state.interactionCompiledCheckpoint ?: state.compiledResults,
        undoSourcesStack = if (hadDocumentCheckpoint) state.undoSourcesStack.dropLast(1) else state.undoSourcesStack,
        interactionSourcesCheckpoint = null,
        interactionCompiledCheckpoint = null,
        interactionPendingIntents = emptyList(),
    )
}

private fun endAtomicInteraction(state: DesignEditorState): DesignEditorState {
    if (!state.interacting) return state
    val desired = state.document ?: return cancelAtomicInteraction(state)
    val checkpointDocument = state.undoStack.lastOrNull() ?: desired
    val checkpointSources = state.interactionSourcesCheckpoint ?: state.sources
    val checkpointCompiled = state.interactionCompiledCheckpoint ?: state.compiledResults

    var prepared = state.copy(
        document = checkpointDocument,
        sources = checkpointSources,
        compiledResults = checkpointCompiled,
        interacting = true,
        interactionPendingIntents = emptyList(),
    )
    state.interactionPendingIntents.forEach { pending ->
        prepared = reduceDesignEditorUnchecked(prepared, pending)
    }
    val attempt = prepareSourcesForDocument(prepared.sources, desired)
    if (attempt is WriteBackAttempt.Failure) {
        val canceled = cancelAtomicInteraction(state)
        return canceled.copy(diagnostics = canceled.diagnostics + attempt.diagnostic)
    }
    val documents = (attempt as WriteBackAttempt.Success).documents
    val inert = semanticallyEquivalent(checkpointDocument, desired) && documents.sources == checkpointSources
    val ended = reduceDesignEditorUnchecked(state, DesignEditorIntent.EndInteraction)
    return ended.copy(
        document = documents.document,
        diagnostics = documents.diagnostics,
        sources = documents.sources,
        compiledResults = documents.compiled,
        annotationLayers = annotationLayersFrom(documents.sources),
        undoSourcesStack = if (inert) state.undoSourcesStack.dropLast(1) else state.undoSourcesStack,
        previousSources = if (inert || documents.sources == checkpointSources) state.previousSources
        else (state.previousSources + listOf(checkpointSources)).takeLast(MaxSourceHistory),
        interactionSourcesCheckpoint = null,
        interactionCompiledCheckpoint = null,
        interactionPendingIntents = emptyList(),
    ).pruneSelectionAfterWriteBack()
}

private fun persistCandidate(
    before: DesignEditorState,
    candidate: DesignEditorState,
    intent: DesignEditorIntent,
): DesignEditorState {
    val desired = candidate.document ?: return before.writeBackRejected(intent, "edit produced no document")
    if (desired.pages.isEmpty() && intent is DesignEditorIntent.DeleteScreen) {
        val documents = compileMissionDocuments(candidate.sources)
        if (documents.document != null || documents.diagnostics.any { it.severity == DesignSeverity.Error }) {
            return before.writeBackRejected(intent, "deleting the final screen produced invalid remaining sources")
        }
        val checkpoint = before.document
        return candidate.copy(
            document = desired,
            diagnostics = documents.diagnostics,
            sources = documents.sources,
            compiledResults = documents.compiled,
            annotationLayers = annotationLayersFrom(documents.sources),
            undoStack = checkpoint?.let { (before.undoStack + it).takeLast(MaxDocumentHistory) } ?: before.undoStack,
            redoStack = emptyList(),
            undoSourcesStack = checkpoint?.let {
                (before.undoSourcesStack + listOf(before.sources)).takeLast(MaxDocumentHistory)
            } ?: before.undoSourcesStack,
            redoSourcesStack = emptyList(),
            previousSources = if (documents.sources == before.sources) before.previousSources
            else (before.previousSources + listOf(before.sources)).takeLast(MaxSourceHistory),
        ).pruneSelectionAfterWriteBack()
    }
    return when (val attempt = prepareSourcesForDocument(candidate.sources, desired)) {
        is WriteBackAttempt.Failure -> before.copy(
            diagnostics = before.diagnostics +
                candidate.diagnostics.filter { it.severity == DesignSeverity.Error } +
                attempt.diagnostic,
        )
        is WriteBackAttempt.Success -> {
            val documents = attempt.documents
            val checkpoint = before.document ?: candidate.undoStack.lastOrNull()
            candidate.copy(
                document = documents.document,
                diagnostics = documents.diagnostics,
                sources = documents.sources,
                compiledResults = documents.compiled,
                annotationLayers = annotationLayersFrom(documents.sources),
                undoStack = checkpoint?.let { (before.undoStack + it).takeLast(MaxDocumentHistory) } ?: before.undoStack,
                redoStack = emptyList(),
                undoSourcesStack = checkpoint?.let {
                    (before.undoSourcesStack + listOf(before.sources)).takeLast(MaxDocumentHistory)
                } ?: before.undoSourcesStack,
                redoSourcesStack = emptyList(),
                previousSources = if (documents.sources == before.sources) before.previousSources
                else (before.previousSources + listOf(before.sources)).takeLast(MaxSourceHistory),
            ).pruneSelectionAfterWriteBack()
        }
    }
}

private sealed interface WriteBackAttempt {
    data class Success(val documents: MissionDocuments) : WriteBackAttempt
    data class Failure(val diagnostic: DesignDiagnostic) : WriteBackAttempt
}

/**
 * Builds every changed source in memory, recompiles the complete project, and publishes nothing
 * unless the result is semantically equivalent to [desired]. This is the transaction boundary.
 */
private fun prepareSourcesForDocument(
    initialSources: List<MissionDocumentSource>,
    desired: DesignDocument,
): WriteBackAttempt {
    var documents = compileMissionDocuments(initialSources)
    val initial = documents.document
        ?: return failure("SLM write-back cannot compile the current project")
    if (documents.hasErrors) return failure("SLM write-back aborted because the current sources contain errors")
    if (semanticallyEquivalent(initial, desired)) return WriteBackAttempt.Success(documents)

    val initialLocations = initial.nodeLocations()
    val desiredLocations = desired.nodeLocations()
    if (initial.pages.map { it.id } != desired.pages.map { it.id } || initialLocations != desiredLocations) {
        return failure("operation changes document structure but its structural SLM write-back was not accepted")
    }

    val changed = desiredLocations.keys.mapNotNull { id ->
        val old = initial.nodeById(id) ?: return@mapNotNull null
        val next = desired.nodeById(id) ?: return@mapNotNull null
        next.takeIf { old.authoredSnapshot() != next.authoredSnapshot() }
    }
    var sources = documents.sources
    var compiled = documents.compiled
    for (node in changed) {
        val index = owningSourceIndex(node.id, desired, compiled)
            ?: return failure("node '${node.id}' has no stable owning SLM source")
        val source = sources[index]
        val newSource = when (val kind = node.kind) {
            is DesignNodeKind.Diagram -> {
                val result = applyDiagramWriteBack(
                    source = source.content,
                    nodeId = node.id,
                    graph = kind.graph,
                    extensions = EditorSlmExtensions,
                    fileName = source.fileName,
                )
                result.newSource ?: return failure(result.message ?: "diagram write-back failed for '${node.id}'")
            }
            else -> {
                val result = applySlmEdits(
                    source = source.content,
                    edits = listOf(ReemitNode(node.id)),
                    compiled = compiled[index],
                    options = editorSlmCompileOptions(source.fileName),
                    patchedNode = node,
                )
                result.newSource ?: return failure(
                    result.diagnostics.firstOrNull()?.message ?: "node '${node.id}' cannot be represented in SLM",
                )
            }
        }
        val recompiled = compileSlm(newSource, editorSlmCompileOptions(source.fileName))
        if (!recompiled.isSuccess || recompiled.document == null) {
            return failure(recompiled.diagnostics.firstOrNull()?.message ?: "write-back produced invalid SLM for ${source.fileName}")
        }
        sources = sources.toMutableList().apply { this[index] = source.copy(content = newSource) }.toList()
        compiled = compiled.toMutableList().apply { this[index] = recompiled }.toList()
    }
    documents = compileMissionDocuments(sources)
    val actual = documents.document
    if (documents.hasErrors || actual == null) return failure("write-back transaction produced invalid SLM")
    if (!semanticallyEquivalent(desired, actual)) {
        return failure(
            "SLM round-trip is not semantically equivalent to the requested document: " +
                semanticMismatchSummary(desired, actual),
        )
    }
    return WriteBackAttempt.Success(documents)
}

private data class NodeLocation(val pageId: String, val parentId: String?, val index: Int)

private fun DesignDocument.nodeLocations(): Map<String, NodeLocation> = buildMap {
    fun visit(pageId: String, parentId: String?, nodes: List<DesignNode>) {
        nodes.forEachIndexed { index, node ->
            put(node.id, NodeLocation(pageId, parentId, index))
            visit(pageId, node.id, node.children)
        }
    }
    pages.forEach { page -> visit(page.id, null, page.children) }
}

private fun DesignNode.authoredSnapshot(): DesignNode = copy(
    // Paint order is represented by the sibling list. The compiler may retain an authored
    // numeric order while structural editor operations normalize it; equal list order is the
    // semantic invariant used by [nodeLocations].
    order = null,
    sourceMap = null,
    blockSourceMaps = emptyMap(),
    interactions = interactions.map { it.copy(sourceMap = null) },
    responsive = responsive.map { it.copy(sourceMap = null) },
    children = emptyList(),
)

private fun DesignDocument.semanticSnapshot(): DesignDocument {
    fun strip(node: DesignNode): DesignNode = node.authoredSnapshot().copy(children = node.children.map(::strip))
    return copy(
        pages = pages.map { page -> page.copy(children = page.children.map(::strip)) },
        components = components.mapValues { (_, component) -> component.copy(root = strip(component.root)) },
    )
}

internal fun semanticallyEquivalent(expected: DesignDocument, actual: DesignDocument): Boolean =
    expected.semanticSnapshot() == actual.semanticSnapshot()

internal fun semanticMismatchSummary(expected: DesignDocument, actual: DesignDocument): String {
    val expectedHeader = expected.semanticSnapshot().copy(pages = emptyList())
    val actualHeader = actual.semanticSnapshot().copy(pages = emptyList())
    if (expectedHeader != actualHeader) return "document metadata differs (expected=$expectedHeader, actual=$actualHeader)"
    expected.pages.zip(actual.pages).forEach { (expectedPage, actualPage) ->
        if (expectedPage.copy(children = emptyList()) != actualPage.copy(children = emptyList())) {
            return "page metadata differs (expected=${expectedPage.copy(children = emptyList())}, " +
                "actual=${actualPage.copy(children = emptyList())})"
        }
    }
    val expectedLocations = expected.nodeLocations()
    val actualLocations = actual.nodeLocations()
    if (expectedLocations != actualLocations) {
        return "node structure differs (expected=$expectedLocations, actual=$actualLocations)"
    }
    expectedLocations.keys.forEach { id ->
        val expectedNode = expected.nodeById(id)?.authoredSnapshot()
        val actualNode = actual.nodeById(id)?.authoredSnapshot()
        if (expectedNode != actualNode) return "node '$id' differs (expected=$expectedNode, actual=$actualNode)"
    }
    return "unknown semantic difference"
}

private fun owningSourceIndex(
    nodeId: String,
    desired: DesignDocument,
    compiled: List<io.aequicor.visualization.engine.frontend.SlmCompileResult>,
): Int? {
    val pageId = desired.pageOfNode(nodeId)?.id ?: return null
    return compiled.indices.firstOrNull { index ->
        val document = compiled[index].document ?: return@firstOrNull false
        val compiledPageId = document.screen?.id.orEmpty()
        document.nodeById(nodeId) != null && document.pages.any { page -> compiledPageId.ifBlank { page.id } == pageId }
    }
}

private fun failure(message: String): WriteBackAttempt.Failure = WriteBackAttempt.Failure(
    DesignDiagnostic(
        severity = DesignSeverity.Error,
        message = "SLM write-back rejected: operation does not support SLM write-back: $message",
    ),
)

private fun DesignEditorState.writeBackRejected(intent: DesignEditorIntent, reason: String): DesignEditorState =
    copy(
        diagnostics = diagnostics + DesignDiagnostic(
            severity = DesignSeverity.Error,
            message = "SLM write-back rejected for $intent: $reason",
        ),
    )

private fun DesignEditorState.atomicUndo(): DesignEditorState {
    val previousDocument = undoStack.lastOrNull() ?: return this
    val historicalSources = undoSourcesStack.lastOrNull()
        ?: return writeBackRejected(DesignEditorIntent.Undo, "source history is unavailable")
    val documents = compileMissionDocuments(historicalSources)
    val compiledDocument = documents.document
    if (documents.hasErrors || compiledDocument == null || !semanticallyEquivalent(previousDocument, compiledDocument)) {
        return writeBackRejected(DesignEditorIntent.Undo, "historical SLM does not reproduce the historical document")
    }
    val current = document ?: return this
    return copy(
        document = compiledDocument,
        diagnostics = documents.diagnostics,
        sources = documents.sources,
        compiledResults = documents.compiled,
        annotationLayers = annotationLayersFrom(documents.sources),
        undoStack = undoStack.dropLast(1),
        redoStack = (redoStack + current).takeLast(MaxDocumentHistory),
        undoSourcesStack = undoSourcesStack.dropLast(1),
        redoSourcesStack = (redoSourcesStack + listOf(sources)).takeLast(MaxDocumentHistory),
        previousSources = (previousSources + listOf(sources)).takeLast(MaxSourceHistory),
    ).pruneSelectionAfterWriteBack()
}

private fun DesignEditorState.atomicRedo(): DesignEditorState {
    val nextDocument = redoStack.lastOrNull() ?: return this
    val nextSources = redoSourcesStack.lastOrNull()
        ?: return writeBackRejected(DesignEditorIntent.Redo, "source history is unavailable")
    val documents = compileMissionDocuments(nextSources)
    val compiledDocument = documents.document
        ?: nextDocument.takeIf { it.pages.isEmpty() && documents.diagnostics.none { diagnostic -> diagnostic.severity == DesignSeverity.Error } }
    if (compiledDocument == null || documents.diagnostics.any { it.severity == DesignSeverity.Error } || !semanticallyEquivalent(nextDocument, compiledDocument)) {
        return writeBackRejected(DesignEditorIntent.Redo, "historical SLM does not reproduce the historical document")
    }
    val current = document ?: return this
    return copy(
        document = compiledDocument,
        diagnostics = documents.diagnostics,
        sources = documents.sources,
        compiledResults = documents.compiled,
        annotationLayers = annotationLayersFrom(documents.sources),
        redoStack = redoStack.dropLast(1),
        undoStack = (undoStack + current).takeLast(MaxDocumentHistory),
        redoSourcesStack = redoSourcesStack.dropLast(1),
        undoSourcesStack = (undoSourcesStack + listOf(sources)).takeLast(MaxDocumentHistory),
        previousSources = (previousSources + listOf(sources)).takeLast(MaxSourceHistory),
    ).pruneSelectionAfterWriteBack()
}

private fun DesignEditorState.pruneSelectionAfterWriteBack(): DesignEditorState {
    val doc = document ?: return this
    val page = doc.pages.firstOrNull { it.id == selectedPageId } ?: doc.pages.firstOrNull()
    val kept = selectedNodeIds.filter { doc.nodeById(it) != null }.toSet()
    return copy(
        selectedPageId = page?.id.orEmpty(),
        selectedNodeIds = kept,
        selectedNodeId = selectedNodeId.takeIf { it in kept } ?: kept.firstOrNull().orEmpty(),
    )
}
