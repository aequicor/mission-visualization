package io.aequicor.visualization.editor.presentation

import io.aequicor.visualization.editor.domain.MissionDocumentSource
import io.aequicor.visualization.editor.domain.annotationSidecarCompileResult
import io.aequicor.visualization.editor.domain.annotationSidecarFileName
import io.aequicor.visualization.editor.domain.screenFileNameForSidecar
import io.aequicor.visualization.subsystems.annotations.Annotation
import io.aequicor.visualization.subsystems.annotations.AnnotationAnchor
import io.aequicor.visualization.subsystems.annotations.AnnotationLayer
import io.aequicor.visualization.subsystems.annotations.AnnotationRect
import io.aequicor.visualization.subsystems.annotations.addAnnotation
import io.aequicor.visualization.subsystems.annotations.slm.AnnotationSlmParser
import io.aequicor.visualization.subsystems.annotations.slm.AnnotationSlmPatcher
import io.aequicor.visualization.subsystems.annotations.slm.AnnotationSlmWriter

/**
 * Annotation editing over [DesignEditorState]: every document-side annotation intent
 * funnels through [writeBackAnnotations], which applies a pure core operation to the
 * screen's [AnnotationLayer] and mirrors the change into the owning `*.annotations.md`
 * sidecar source via surgical [AnnotationSlmPatcher] splices — the annotation analogue
 * of `writeBackEdits`. The first annotation on a screen creates the sidecar source
 * entry (so it persists through drafts like every other source). Annotations never
 * touch the design document, so these edits record no document undo entry — only the
 * [DesignEditorState.previousSources] source history, like other write-backs.
 */

/**
 * Applies [transform] to the layer of [screenFileName] and patches the sidecar source
 * in lock-step. Unknown screens and no-op transforms return the state unchanged; a
 * source/compile list out of lock-step keeps the in-memory layer change only (the
 * same graceful fallback as `writeBackEdits`).
 */
internal fun DesignEditorState.writeBackAnnotations(
    screenFileName: String,
    transform: (AnnotationLayer) -> AnnotationLayer,
): DesignEditorState {
    // A known screen without a map entry (e.g. one created this session) starts empty;
    // an unknown screen is a no-op.
    val layer = annotationLayers[screenFileName]
        ?: if (sources.any { it.fileName == screenFileName }) AnnotationLayer(screenFileName) else return this
    val newLayer = transform(layer)
    if (newLayer == layer) return this
    val newLayers = annotationLayers + (screenFileName to newLayer)
    if (sources.size != compiledResults.size) return copy(annotationLayers = newLayers)

    // Surgical sidecar patch: delete dropped sections, re-render added/changed ones;
    // every untouched section (and any preamble) stays byte-identical.
    val sidecarName = annotationSidecarFileName(screenFileName)
    val index = sources.indexOfFirst { it.fileName == sidecarName }
    val oldById = layer.annotations.associateBy { it.id }
    val newIds = newLayer.annotations.map { it.id }.toSet()
    var text = if (index >= 0) sources[index].content else ""
    if (index >= 0) {
        val parsed = AnnotationSlmParser.parse(screenFileName, text)
        if (parsed.needsRewrite) {
            text = AnnotationSlmWriter.write(parsed.layer)
        }
    }
    layer.annotations.filterNot { it.id in newIds }.forEach { dropped ->
        text = AnnotationSlmPatcher.deleteSection(text, dropped.id)
    }
    newLayer.annotations.filter { oldById[it.id] != it }.forEach { changed ->
        text = AnnotationSlmPatcher.upsertSection(text, changed)
    }

    val compiled = annotationSidecarCompileResult(text)
    val newSources: List<MissionDocumentSource>
    val newCompiled = compiledResults.toMutableList()
    if (index >= 0) {
        newSources = sources.toMutableList().apply { this[index] = this[index].copy(content = text) }.toList()
        newCompiled[index] = compiled
    } else {
        newSources = sources + MissionDocumentSource(sidecarName, text)
        newCompiled += compiled
    }
    return copy(
        annotationLayers = newLayers,
        sources = newSources,
        compiledResults = newCompiled.toList(),
        previousSources = (previousSources + listOf(sources)).takeLast(MaxSourceHistory),
    )
}

/** Creates the annotation with a freshly minted stable id (see [mintAnnotationId]). */
internal fun DesignEditorState.addAnnotationWriteBack(
    intent: DesignEditorIntent.AddAnnotation,
): DesignEditorState {
    val annotation = Annotation(
        id = mintAnnotationId(),
        kind = intent.kind,
        anchor = intent.anchor,
    )
    return writeBackAnnotations(intent.screenFileName) { it.addAnnotation(annotation) }
}

/**
 * Deterministic fresh annotation id: `ann-<n>`, unique across every layer (ids drive
 * workspace selection/expansion, which is not screen-scoped) — the same style the
 * sidecar parser synthesizes for unmarked sections.
 */
private fun DesignEditorState.mintAnnotationId(): String {
    val used = annotationLayers.values.flatMapTo(mutableSetOf()) { layer -> layer.annotations.map { it.id } }
    var n = 1
    while ("ann-$n" in used) n++
    return "ann-$n"
}

/**
 * Direct sidecar source editing (the SLM pane on a `*.annotations.md` file): replaces
 * the source text and re-parses the screen's layer tolerantly — a malformed section is
 * skipped with a warning, never failing the file. The sidecar is not SLM, so no
 * compile/merge runs; the placeholder compile entry is refreshed to keep the lists
 * index-aligned.
 */
internal fun DesignEditorState.editAnnotationSidecarSource(index: Int, content: String): DesignEditorState {
    val source = sources[index]
    val screenFileName = screenFileNameForSidecar(source.fileName)
    val parsed = AnnotationSlmParser.parse(screenFileName, content)
    val newSources = sources.toMutableList().apply { this[index] = source.copy(content = content) }.toList()
    val newCompiled = if (compiledResults.size == sources.size) {
        compiledResults.toMutableList().apply { this[index] = annotationSidecarCompileResult(content) }.toList()
    } else {
        compiledResults
    }
    return copy(
        sources = newSources,
        compiledResults = newCompiled,
        annotationLayers = annotationLayers + (screenFileName to parsed.layer),
    )
}

/** Screen file name per page id, from the per-source compiles (export node context). */
fun DesignEditorState.screenFileNamesByPageId(): Map<String, String> = buildMap {
    compiledResults.forEachIndexed { index, result ->
        val compiledDocument = result.document ?: return@forEachIndexed
        val fileName = sources.getOrNull(index)?.fileName ?: return@forEachIndexed
        val screenId = compiledDocument.screen?.id.orEmpty()
        compiledDocument.pages.forEach { page -> put(screenId.ifBlank { page.id }, fileName) }
    }
}

/**
 * Anchor for an annotation placed by a canvas press at ([docX], [docY]) in document
 * coordinates: pinned to the hit node (badge offset from the node's top-center keeps
 * the badge exactly where clicked) when the press landed on one, else a free point.
 * A hit whose bounds are unknown pins at the node's top-center (zero offset).
 */
internal fun annotationAnchorForPress(
    docX: Double,
    docY: Double,
    hitNodeId: String,
    nodeBounds: AnnotationRect?,
): AnnotationAnchor = when {
    hitNodeId.isBlank() -> AnnotationAnchor.FreePoint(docX, docY)
    nodeBounds == null -> AnnotationAnchor.NodeAnchor(hitNodeId)
    else -> AnnotationAnchor.NodeAnchor(
        nodeId = hitNodeId,
        offsetX = docX - nodeBounds.centerX,
        offsetY = docY - nodeBounds.top,
    )
}

/**
 * Pure workspace-side reducer for the annotation view intents (expansion, selection,
 * tool). Annotations split document/view like everything else in the editor: the
 * document intents go through [reduceDesignEditor], these three only ever touch
 * [EditorWorkspaceState]. [DesignEditorIntent.DeleteAnnotation] additionally prunes
 * the deleted id from the view state; every other intent returns [workspace] as is.
 */
fun reduceAnnotationWorkspace(
    workspace: EditorWorkspaceState,
    intent: DesignEditorIntent,
): EditorWorkspaceState = when (intent) {
    is DesignEditorIntent.ToggleAnnotationExpanded -> workspace.copy(
        expandedAnnotationIds = if (intent.annotationId in workspace.expandedAnnotationIds) {
            workspace.expandedAnnotationIds - intent.annotationId
        } else {
            workspace.expandedAnnotationIds + intent.annotationId
        },
    )
    is DesignEditorIntent.SelectAnnotation -> workspace.copy(selectedAnnotationId = intent.annotationId)
    is DesignEditorIntent.SetAnnotationTool -> workspace.copy(annotationTool = intent.tool)
    is DesignEditorIntent.DeleteAnnotation -> workspace.copy(
        expandedAnnotationIds = workspace.expandedAnnotationIds - intent.annotationId,
        selectedAnnotationId = if (workspace.selectedAnnotationId == intent.annotationId) "" else workspace.selectedAnnotationId,
    )
    else -> workspace
}
