package io.aequicor.visualization.editor.presentation

import io.aequicor.visualization.editor.domain.MissionDocumentSource
import io.aequicor.visualization.editor.domain.MissionDocuments
import io.aequicor.visualization.engine.ir.model.DesignDiagnostic
import io.aequicor.visualization.engine.ir.model.DesignDocument
import io.aequicor.visualization.engine.ir.model.DesignNode
import io.aequicor.visualization.engine.frontend.SlmCompileResult

/** Undo history depth for source write-back edits (see [DesignEditorState.previousSources]). */
internal const val MaxSourceHistory: Int = 50

/** Undo history depth for in-memory document edits (see [DesignEditorState.undoStack]). */
internal const val MaxDocumentHistory: Int = 100

/**
 * Immutable editor state over the compiled mission design. Selection is tracked by
 * page id and authored node ids: [selectedNodeIds] is the full multi-selection set,
 * [selectedNodeId] the primary node (shown in single-value inspector fields). Document
 * edits go through [reduceDesignEditor]. The per-document SLM [sources] and
 * [compiledResults] (fingerprint + edit index) feed the resize write-back path
 * ([DesignEditorIntent.ResizeNode]); [undoStack]/[redoStack] track the in-memory
 * document for structural and property edits that do not write back.
 */
data class DesignEditorState(
    val document: DesignDocument? = null,
    val diagnostics: List<DesignDiagnostic> = emptyList(),
    val sources: List<MissionDocumentSource> = emptyList(),
    val compiledResults: List<SlmCompileResult> = emptyList(),
    val selectedPageId: String = "",
    /** Primary selected node id: the one single-value inspector fields bind to. */
    val selectedNodeId: String = "",
    /** Full multi-selection; always contains [selectedNodeId] when it is non-blank. */
    val selectedNodeIds: Set<String> = emptySet(),
    /** Node currently in text-editing mode, or "" when none. */
    val editingTextNodeId: String = "",
    /**
     * Undo history of the SLM sources: the source lists as they were before each
     * successful write-back, oldest first, capped at [MaxSourceHistory].
     */
    val previousSources: List<List<MissionDocumentSource>> = emptyList(),
    /** In-memory document snapshots before each mutating edit (oldest first). */
    val undoStack: List<DesignDocument> = emptyList(),
    /** Documents undone and available to redo (most-recently-undone last). */
    val redoStack: List<DesignDocument> = emptyList(),
    /**
     * True between `BeginInteraction` and `EndInteraction` (a canvas drag). While set,
     * mutating edits skip the per-edit undo push, so a whole drag is one undo entry;
     * the checkpoint is taken once at `BeginInteraction`.
     */
    val interacting: Boolean = false,
) {
    val selectedNode: DesignNode?
        get() = document?.nodeById(selectedNodeId)

    val selectedNodes: List<DesignNode>
        get() = document?.let { doc -> selectedNodeIds.mapNotNull { doc.nodeById(it) } } ?: emptyList()

    val hasMultiSelection: Boolean
        get() = selectedNodeIds.size > 1
}

fun createDesignEditorState(documents: MissionDocuments): DesignEditorState {
    val document = documents.document
    val firstPage = document?.pages?.firstOrNull()
    val firstNode = firstPage?.children?.firstOrNull()?.id.orEmpty()
    return DesignEditorState(
        document = document,
        diagnostics = documents.diagnostics,
        sources = documents.sources,
        compiledResults = documents.compiled,
        selectedPageId = firstPage?.id.orEmpty(),
        selectedNodeId = firstNode,
        selectedNodeIds = if (firstNode.isBlank()) emptySet() else setOf(firstNode),
    )
}

/**
 * Commits [next] as the working document. Records an undo checkpoint of [prev] unless
 * an interaction is in progress (then the checkpoint was taken at `BeginInteraction`).
 * Clears the redo stack — a fresh edit forks history. No-op when nothing changed.
 */
internal fun DesignEditorState.commitDocument(prev: DesignDocument, next: DesignDocument): DesignEditorState {
    if (next === prev || next == prev) return this
    val base = if (interacting) this else copy(undoStack = (undoStack + prev).takeLast(MaxDocumentHistory), redoStack = emptyList())
    return base.copy(document = next)
}

/** Applies [transform] to node [nodeId] in the working document (undoable). */
internal fun DesignEditorState.editNode(
    nodeId: String,
    transform: (DesignNode) -> DesignNode,
): DesignEditorState {
    val document = document ?: return this
    if (nodeId.isBlank()) return this
    return commitDocument(document, document.updateNode(nodeId, transform))
}

/** Applies a whole-document [transform] (undoable). */
internal fun DesignEditorState.editDocument(
    transform: (DesignDocument) -> DesignDocument,
): DesignEditorState {
    val document = document ?: return this
    return commitDocument(document, transform(document))
}

internal fun DesignEditorState.pushHistory(snapshot: DesignDocument): DesignEditorState =
    copy(
        undoStack = (undoStack + snapshot).takeLast(MaxDocumentHistory),
        redoStack = emptyList(),
    )
