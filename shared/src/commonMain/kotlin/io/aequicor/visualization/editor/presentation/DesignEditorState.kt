package io.aequicor.visualization.editor.presentation

import io.aequicor.visualization.editor.domain.MissionDocumentSource
import io.aequicor.visualization.editor.domain.MissionDocuments
import io.aequicor.visualization.editor.domain.annotationLayersFrom
import io.aequicor.visualization.engine.ir.model.DesignDiagnostic
import io.aequicor.visualization.engine.ir.model.DesignDocument
import io.aequicor.visualization.engine.ir.model.DesignNode
import io.aequicor.visualization.engine.frontend.SlmCompileResult
import io.aequicor.visualization.subsystems.annotations.AnnotationLayer

/** Undo history depth for source write-back edits (see [DesignEditorState.previousSources]). */
internal const val MaxSourceHistory: Int = 50

/** Undo history depth for in-memory document edits (see [DesignEditorState.undoStack]). */
internal const val MaxDocumentHistory: Int = 100

/**
 * Immutable editor state over the compiled mission design. Selection is tracked by
 * page id and authored node ids: [selectedNodeIds] is the full multi-selection set,
 * [selectedNodeId] the primary node (shown in single-value inspector fields). Document
 * edits go through [reduceDesignEditor]. SLM [sources] are the authored source of truth and
 * [compiledResults] provide their edit indices. Document/source undo and redo snapshots advance
 * together; a committed mutation is never represented by the document alone.
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
    /** SLM source snapshots aligned one-for-one with [undoStack]. */
    val undoSourcesStack: List<List<MissionDocumentSource>> = emptyList(),
    /** SLM source snapshots aligned one-for-one with [redoStack]. */
    val redoSourcesStack: List<List<MissionDocumentSource>> = emptyList(),
    /**
     * True between `BeginInteraction` and `EndInteraction` (a canvas drag). While set,
     * mutating edits skip the per-edit undo push, so a whole drag is one undo entry;
     * the checkpoint is taken once at `BeginInteraction`.
     */
    val interacting: Boolean = false,
    /** Sources/compiles captured at BeginInteraction for atomic commit or exact cancel. */
    val interactionSourcesCheckpoint: List<MissionDocumentSource>? = null,
    val interactionCompiledCheckpoint: List<SlmCompileResult>? = null,
    /** Final write-back commands staged during an interaction; previews are never staged. */
    val interactionPendingIntents: List<DesignEditorIntent> = emptyList(),
    /**
     * Unified comments/issues per screen, keyed by screen file name (`*.layout.md`).
     * Comments write into the screen source and issues into `*.annotations.md`
     * (see `writeBackAnnotations`).
     */
    val annotationLayers: Map<String, AnnotationLayer> = emptyMap(),
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
        annotationLayers = annotationLayersFrom(documents.sources),
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

/**
 * Like [editNode] but a no-op when the target is locked. Used for geometry, appearance,
 * typography and vector edits so a locked layer cannot be moved/resized/edited from the
 * canvas or the inspector (design-book §7). Visibility, lock and rename intentionally
 * stay on [editNode] so a locked layer can still be revealed, renamed or unlocked.
 */
internal fun DesignEditorState.editUnlockedNode(
    nodeId: String,
    transform: (DesignNode) -> DesignNode,
): DesignEditorState {
    if (document?.nodeById(nodeId)?.locked == true) return this
    return editNode(nodeId, transform)
}

/** True when the node exists and is locked (drives inspector field disabling). */
fun DesignEditorState.isNodeLocked(nodeId: String): Boolean =
    document?.nodeById(nodeId)?.locked == true

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
