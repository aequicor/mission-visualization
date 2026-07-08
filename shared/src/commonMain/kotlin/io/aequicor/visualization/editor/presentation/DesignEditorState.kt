package io.aequicor.visualization.editor.presentation

import io.aequicor.visualization.engine.ir.model.Bindable
import io.aequicor.visualization.engine.ir.model.DesignColor
import io.aequicor.visualization.engine.ir.model.DesignCornerRadius
import io.aequicor.visualization.engine.ir.model.DesignDiagnostic
import io.aequicor.visualization.engine.ir.model.DesignDocument
import io.aequicor.visualization.engine.ir.model.DesignNode
import io.aequicor.visualization.engine.ir.model.DesignPaint
import io.aequicor.visualization.engine.ir.model.DesignPoint
import io.aequicor.visualization.engine.ir.model.DesignSize
import io.aequicor.visualization.engine.ir.model.DesignSizing
import io.aequicor.visualization.engine.ir.model.DesignStrokes
import io.aequicor.visualization.editor.domain.MissionDocumentSource
import io.aequicor.visualization.editor.domain.MissionDocuments
import io.aequicor.visualization.editor.domain.mergeMissionDocuments
import io.aequicor.visualization.engine.frontend.SlmCompileOptions
import io.aequicor.visualization.engine.frontend.SlmCompileResult
import io.aequicor.visualization.engine.frontend.compileSlm
import io.aequicor.visualization.engine.frontend.edit.SetSizing
import io.aequicor.visualization.engine.frontend.edit.SizingSpec
import io.aequicor.visualization.engine.frontend.edit.applySlmEdit
import io.aequicor.visualization.engine.ir.model.HorizontalConstraint
import io.aequicor.visualization.engine.ir.model.SizingMode
import io.aequicor.visualization.engine.ir.model.VerticalConstraint
import io.aequicor.visualization.engine.ir.model.bindable

/** Undo history depth for source write-back edits (see [DesignEditorState.previousSources]). */
private const val MaxSourceHistory: Int = 50

/**
 * Immutable editor state over the compiled mission design. Selection is tracked
 * by page id and authored node id; document edits go through [reduceDesignEditor].
 * The per-document SLM [sources] and [compiledResults] (fingerprint + edit index)
 * feed the resize write-back path ([DesignEditorIntent.ResizeNode]).
 */
data class DesignEditorState(
    val document: DesignDocument? = null,
    val diagnostics: List<DesignDiagnostic> = emptyList(),
    val sources: List<MissionDocumentSource> = emptyList(),
    val compiledResults: List<SlmCompileResult> = emptyList(),
    val selectedPageId: String = "",
    val selectedNodeId: String = "",
    /**
     * Undo history of the SLM sources: the source lists as they were before each
     * successful write-back, oldest first, capped at [MaxSourceHistory]. Sources
     * alone suffice for undo — compiling them is deterministic.
     */
    val previousSources: List<List<MissionDocumentSource>> = emptyList(),
) {
    val selectedNode: DesignNode?
        get() = document?.nodeById(selectedNodeId)
}

fun createDesignEditorState(documents: MissionDocuments): DesignEditorState {
    val document = documents.document
    val firstPage = document?.pages?.firstOrNull()
    return DesignEditorState(
        document = document,
        diagnostics = documents.diagnostics,
        sources = documents.sources,
        compiledResults = documents.compiled,
        selectedPageId = firstPage?.id.orEmpty(),
        selectedNodeId = firstPage?.children?.firstOrNull()?.id.orEmpty(),
    )
}

sealed interface DesignEditorIntent {
    data class SelectPage(val pageId: String) : DesignEditorIntent

    data class SelectNode(val nodeId: String) : DesignEditorIntent

    data class UpdatePosition(val nodeId: String, val x: Double? = null, val y: Double? = null) : DesignEditorIntent

    /** Typing an exact number pins the edited dimension to `fixed`, like Figma. */
    data class UpdateSize(val nodeId: String, val width: Double? = null, val height: Double? = null) : DesignEditorIntent

    /**
     * Resize write-back: unlike the in-memory [UpdateSize], this rewrites the
     * owning SLM document's source text (the source of truth) with `fixed`
     * sizing for the provided axes, recompiles it and remerges the mission
     * document. Axes left null keep their authored sizing.
     */
    data class ResizeNode(val nodeId: String, val width: Double? = null, val height: Double? = null) : DesignEditorIntent

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

    data class UpdateOpacity(val nodeId: String, val opacity: Double) : DesignEditorIntent

    data class UpdateSolidFill(val nodeId: String, val color: DesignColor) : DesignEditorIntent

    data class UpdateStroke(
        val nodeId: String,
        val color: DesignColor? = null,
        val weight: Double? = null,
    ) : DesignEditorIntent

    data class UpdateCornerRadius(val nodeId: String, val radius: Double) : DesignEditorIntent

    data class SetClipsContent(val nodeId: String, val clips: Boolean) : DesignEditorIntent

    data class SetSticky(val nodeId: String, val sticky: Boolean) : DesignEditorIntent
}

fun reduceDesignEditor(state: DesignEditorState, intent: DesignEditorIntent): DesignEditorState =
    when (intent) {
        is DesignEditorIntent.SelectPage -> {
            val page = state.document?.pageById(intent.pageId)
            state.copy(
                selectedPageId = page?.id ?: intent.pageId,
                selectedNodeId = page?.children?.firstOrNull()?.id.orEmpty(),
            )
        }
        is DesignEditorIntent.SelectNode -> {
            val page = state.document?.pageOfNode(intent.nodeId)
            state.copy(
                selectedNodeId = intent.nodeId,
                selectedPageId = page?.id ?: state.selectedPageId,
            )
        }
        is DesignEditorIntent.UpdatePosition -> state.updateNode(intent.nodeId) { node ->
            val current = node.position ?: DesignPoint()
            node.copy(
                position = DesignPoint(
                    x = intent.x ?: current.x,
                    y = intent.y ?: current.y,
                ),
            )
        }
        is DesignEditorIntent.UpdateSize -> state.updateNode(intent.nodeId) { node ->
            val sizing = node.sizing ?: DesignSizing()
            node.copy(
                size = DesignSize(
                    width = intent.width ?: node.size.width,
                    height = intent.height ?: node.size.height,
                ),
                sizing = sizing.copy(
                    horizontal = if (intent.width != null) SizingMode.Fixed else sizing.horizontal,
                    vertical = if (intent.height != null) SizingMode.Fixed else sizing.vertical,
                ),
            )
        }
        is DesignEditorIntent.ResizeNode -> state.resizeNodeWriteBack(intent)
        is DesignEditorIntent.UpdateSizingMode -> state.updateNode(intent.nodeId) { node ->
            val sizing = node.sizing ?: DesignSizing()
            node.copy(
                sizing = sizing.copy(
                    horizontal = intent.horizontal ?: sizing.horizontal,
                    vertical = intent.vertical ?: sizing.vertical,
                ),
            )
        }
        is DesignEditorIntent.UpdateConstraints -> state.updateNode(intent.nodeId) { node ->
            node.copy(
                constraints = node.constraints.copy(
                    horizontal = intent.horizontal ?: node.constraints.horizontal,
                    vertical = intent.vertical ?: node.constraints.vertical,
                ),
            )
        }
        is DesignEditorIntent.UpdateOpacity -> state.updateNode(intent.nodeId) { node ->
            node.copy(opacity = intent.opacity.coerceIn(0.0, 1.0).bindable())
        }
        is DesignEditorIntent.UpdateSolidFill -> state.updateNode(intent.nodeId) { node ->
            val solid = DesignPaint.Solid(intent.color.bindable())
            val fills = node.fills.orEmpty()
            node.copy(
                fills = if (fills.isEmpty()) listOf(solid) else listOf(solid) + fills.drop(1),
                fillStyleId = "",
            )
        }
        is DesignEditorIntent.UpdateStroke -> state.updateNode(intent.nodeId) { node ->
            val current = node.strokes ?: DesignStrokes()
            val paints = if (intent.color != null) {
                listOf(DesignPaint.Solid(intent.color.bindable())) + current.paints.drop(1)
            } else {
                current.paints
            }
            node.copy(
                strokes = current.copy(
                    paints = paints,
                    weight = intent.weight?.bindable() ?: current.weight,
                ),
            )
        }
        is DesignEditorIntent.UpdateCornerRadius -> state.updateNode(intent.nodeId) { node ->
            node.copy(cornerRadius = DesignCornerRadius.all(intent.radius.coerceAtLeast(0.0).bindable()))
        }
        is DesignEditorIntent.SetClipsContent -> state.updateNode(intent.nodeId) { node ->
            node.copy(layout = node.layout.copy(clipsContent = intent.clips))
        }
        is DesignEditorIntent.SetSticky -> state.updateNode(intent.nodeId) { node ->
            node.copy(scroll = node.scroll.copy(sticky = intent.sticky))
        }
    }

private fun DesignEditorState.updateNode(
    nodeId: String,
    transform: (DesignNode) -> DesignNode,
): DesignEditorState {
    val document = document ?: return this
    if (nodeId.isBlank()) return this
    return copy(document = document.updateNode(nodeId, transform))
}

/**
 * Applies [DesignEditorIntent.ResizeNode] as a surgical edit on the owning SLM
 * source: builds a [SetSizing] with `fixed` mode for the provided axes, patches
 * the source via [applySlmEdit], recompiles it with [compileSlm] and remerges
 * every document through [mergeMissionDocuments].
 *
 * Document ownership is resolved by page first: [mergeMissionDocuments]
 * concatenates pages in source order and re-ids each from its document's screen
 * meta, so the node's merged page id maps straight back to one compiled source
 * (see [candidateSourceIndices]). Nodes outside any page (shared component
 * definitions duplicated across documents) fall back to trying each
 * source+compiled pair in order — the first applied patch wins.
 *
 * Reducer purity: [applySlmEdit] and [compileSlm] are pure functions of their
 * arguments (no IO, clocks or shared mutable state), so calling them here keeps
 * `(State, Intent) -> State` referentially transparent — same state and intent
 * always produce the same state.
 *
 * On failure (unknown node id, stale compile result) the state only gains the
 * patcher diagnostics of the preferred candidate; sources, compiled results,
 * document and selection stay untouched.
 */
private fun DesignEditorState.resizeNodeWriteBack(
    intent: DesignEditorIntent.ResizeNode,
): DesignEditorState {
    if (intent.width == null && intent.height == null) return this
    if (intent.nodeId.isBlank() || sources.isEmpty() || sources.size != compiledResults.size) return this
    val edit = SetSizing(
        nodeId = intent.nodeId,
        width = intent.width?.let { SizingSpec(mode = SizingMode.Fixed, value = it) },
        height = intent.height?.let { SizingSpec(mode = SizingMode.Fixed, value = it) },
    )
    var preferredFailure: List<DesignDiagnostic> = emptyList()
    candidateSourceIndices(intent.nodeId).forEachIndexed { attempt, index ->
        val source = sources[index]
        val result = applySlmEdit(source.content, edit, compiledResults[index])
        val newSource = result.newSource
        if (newSource == null) {
            if (attempt == 0) preferredFailure = result.diagnostics
            return@forEachIndexed
        }
        val recompiled = compileSlm(newSource, SlmCompileOptions(fileName = source.fileName))
        if (!recompiled.isSuccess) {
            if (attempt == 0) preferredFailure = recompiled.diagnostics
            return@forEachIndexed
        }
        val newSources = sources.toMutableList().apply { this[index] = source.copy(content = newSource) }.toList()
        val newCompiled = compiledResults.toMutableList().apply { this[index] = recompiled }.toList()
        val merged = mergeMissionDocuments(newSources, newCompiled)
        return copy(
            document = merged.document,
            diagnostics = merged.diagnostics + result.diagnostics,
            sources = merged.sources,
            compiledResults = merged.compiled,
            previousSources = (previousSources + listOf(sources)).takeLast(MaxSourceHistory),
        )
    }
    return copy(diagnostics = diagnostics + preferredFailure)
}

/**
 * Source indices to try for [nodeId]: the page-owning document first — merged
 * page ids come from each compiled document's screen meta (falling back to the
 * page's own id when the meta is blank), mirroring [mergeMissionDocuments] —
 * then the remaining documents in source order as a fallback for nodes that
 * live outside pages.
 */
private fun DesignEditorState.candidateSourceIndices(nodeId: String): List<Int> {
    val indices = compiledResults.indices.toList()
    val pageId = document?.pageOfNode(nodeId)?.id ?: return indices
    val owner = indices.firstOrNull { index ->
        val compiledDocument = compiledResults[index].document ?: return@firstOrNull false
        val screenId = compiledDocument.screen?.id.orEmpty()
        compiledDocument.pages.any { page -> screenId.ifBlank { page.id } == pageId }
    } ?: return indices
    return listOf(owner) + indices.filterNot { it == owner }
}
