package io.aequicor.visualization.editor.presentation

import io.aequicor.visualization.editor.domain.MissionDocumentSource
import io.aequicor.visualization.editor.domain.annotationSidecarCompileResult
import io.aequicor.visualization.editor.domain.annotationSidecarFileName
import io.aequicor.visualization.editor.domain.normalizeAnnotationSidecarContent
import io.aequicor.visualization.editor.domain.screenFileNameForSidecar
import io.aequicor.visualization.engine.ir.layout.DesignLayoutEngine
import io.aequicor.visualization.engine.ir.layout.LayoutBox
import io.aequicor.visualization.engine.ir.model.DesignDocument
import io.aequicor.visualization.engine.ir.resolve.DesignResolver
import io.aequicor.visualization.subsystems.annotations.Annotation
import io.aequicor.visualization.subsystems.annotations.AnnotationAnchor
import io.aequicor.visualization.subsystems.annotations.AnnotationLayer
import io.aequicor.visualization.subsystems.annotations.AnnotationPoint
import io.aequicor.visualization.subsystems.annotations.AnnotationRect
import io.aequicor.visualization.subsystems.annotations.addAnnotation
import io.aequicor.visualization.subsystems.annotations.annotationBadgePosition
import io.aequicor.visualization.subsystems.annotations.detachAnnotationsFromNodes
import io.aequicor.visualization.subsystems.annotations.slm.AnnotationSlmParser
import io.aequicor.visualization.subsystems.annotations.slm.AnnotationSlmPatcher

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
    layer.annotations.filterNot { it.id in newIds }.forEach { dropped ->
        text = AnnotationSlmPatcher.deleteSection(text, dropped.id)
    }
    newLayer.annotations.filter { oldById[it.id] != it }.forEach { changed ->
        text = AnnotationSlmPatcher.upsertSection(text, changed)
    }

    val compiled = annotationSidecarCompileResult(sidecarName, text)
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
 * skipped with a warning, never failing the file. The stored text is normalized the same
 * way the load boundary normalizes ([normalizeAnnotationSidecarContent]): synthesized
 * ids are pinned into the headers and ids colliding with other screens' annotations are
 * re-minted, keeping the id-stability invariant through hand edits. Parse warnings
 * refresh the editor diagnostics (file + 1-based line). The sidecar is not SLM, so no
 * compile/merge runs; the placeholder compile entry is refreshed to keep the lists
 * index-aligned. Like SLM `EditSource`, no source-history (undo) entry is recorded.
 */
internal fun DesignEditorState.editAnnotationSidecarSource(index: Int, content: String): DesignEditorState {
    val source = sources[index]
    val screenFileName = screenFileNameForSidecar(source.fileName)
    val idsUsedElsewhere = annotationLayers
        .filterKeys { it != screenFileName }
        .values
        .flatMapTo(mutableSetOf()) { layer -> layer.annotations.map { it.id } }
    val normalized = normalizeAnnotationSidecarContent(source.fileName, content, idsUsedElsewhere)
    val parsed = AnnotationSlmParser.parse(screenFileName, normalized)
    val newSources = sources.toMutableList().apply { this[index] = source.copy(content = normalized) }.toList()
    val aligned = compiledResults.size == sources.size
    val newCompiled = if (aligned) {
        compiledResults.toMutableList().apply { this[index] = annotationSidecarCompileResult(source.fileName, normalized) }.toList()
    } else {
        compiledResults
    }
    return copy(
        sources = newSources,
        compiledResults = newCompiled,
        annotationLayers = annotationLayers + (screenFileName to parsed.layer),
        // Rebuild from the per-source compiles (the edited entry now carries the fresh
        // sidecar warnings) — the same surface SLM source-edit diagnostics land on.
        diagnostics = if (aligned) newCompiled.flatMap { it.diagnostics } else diagnostics,
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
 * Commit target of a badge drag-to-move: the `MoveAnnotation` intent's (x, y) for
 * [anchor] displaced by the accumulated document-space drag delta ([dx], [dy]) — the
 * new top-center offset for a node anchor, the new absolute point for a free point.
 * The drag itself is transient view state; exactly one intent commits on release.
 */
fun annotationMoveCommitTarget(anchor: AnnotationAnchor, dx: Double, dy: Double): AnnotationPoint = when (anchor) {
    is AnnotationAnchor.NodeAnchor -> AnnotationPoint(anchor.offsetX + dx, anchor.offsetY + dy)
    is AnnotationAnchor.FreePoint -> AnnotationPoint(anchor.x + dx, anchor.y + dy)
}

/**
 * Visual (post-rotation) bounds of the node [nodeId] within the root [layout]: the
 * axis-aligned bounding box of the node's layout box carried through its own and every
 * ancestor's rotation — the same effective transform the renderer nests and the
 * selection overlay follows. Null when the node does not resolve. Annotation anchors
 * are computed AND re-applied in this visual space, so a badge pinned inside a rotated
 * (or later-rotated) frame follows the node on screen instead of floating at its
 * pre-rotation layout position.
 */
fun annotationNodeVisualBounds(layout: LayoutBox?, nodeId: String): AnnotationRect? {
    if (nodeId.isBlank()) return null
    val path = layout?.pathToSource(nodeId) ?: return null
    val target = path.last()
    val ancestors = path.dropLast(1).asReversed().mapNotNull { box ->
        box.node.rotation.takeIf { it != 0.0 }?.let { degrees ->
            AncestorRotation(GeoPoint(box.x + box.width / 2.0, box.y + box.height / 2.0), degrees)
        }
    }
    val transform = effectiveTransform(
        box = BoundsBox(target.x, target.y, target.width, target.height),
        ownRotation = target.node.rotation,
        ancestors = ancestors,
    )
    val bounds = if (transform.rotation == 0.0) {
        transform.box
    } else {
        axisAlignedBounds(rotatedCorners(transform.box, transform.rotation))
    }
    return AnnotationRect.fromSize(bounds.x, bounds.y, bounds.width, bounds.height)
}

/** Path of layout boxes from this (root) box down to the node with [sourceId], inclusive. */
private fun LayoutBox.pathToSource(sourceId: String): List<LayoutBox>? {
    if (node.sourceId == sourceId) return listOf(this)
    children.forEach { child -> child.pathToSource(sourceId)?.let { return listOf(this) + it } }
    return null
}

/**
 * Freezes every annotation anchored to a node in [deletedIds] (or any of their
 * descendants) as a [AnnotationAnchor.FreePoint] at its current on-canvas badge
 * position, resolved from PRE-delete bounds — so deleting a node keeps its badges
 * where they were instead of collapsing them onto the dangling near-origin fallback.
 * Runs before the nodes leave the tree; each affected screen gets one sidecar
 * write-back. A node whose pre-delete bounds cannot be resolved keeps its (now
 * dangling) node anchor — the keep-not-lose behavior.
 */
internal fun DesignEditorState.detachAnnotationsForNodeDelete(deletedIds: Set<String>): DesignEditorState {
    val document = document ?: return this
    if (deletedIds.isEmpty()) return this
    val goneIds = deletedIds.flatMapTo(mutableSetOf()) { id ->
        document.nodeById(id)?.let { node -> listOf(node.id) + node.allDescendants().map { it.id } } ?: listOf(id)
    }
    val pageIdByScreen = screenFileNamesByPageId().entries.associate { (pageId, screen) -> screen to pageId }
    var state = this
    annotationLayers.forEach { (screenFileName, layer) ->
        val affected = layer.annotations.any { (it.anchor as? AnnotationAnchor.NodeAnchor)?.nodeId in goneIds }
        if (!affected) return@forEach
        // Pre-delete layout of the owning page, computed once per affected screen with
        // the same pure resolve+layout pipeline the artboard uses.
        val layout = pageIdByScreen[screenFileName]?.let { pageId -> pageLayoutFor(document, pageId) }
        state = state.writeBackAnnotations(screenFileName) { current ->
            current.detachAnnotationsFromNodes(goneIds) { annotation ->
                val anchor = annotation.anchor as? AnnotationAnchor.NodeAnchor
                    ?: return@detachAnnotationsFromNodes null
                annotationNodeVisualBounds(layout, anchor.nodeId)?.let { bounds ->
                    annotationBadgePosition(anchor, bounds)
                }
            }
        }
    }
    return state
}

/** Pure resolve + layout of the page [pageId]; null when the page or its root frame is absent. */
private fun pageLayoutFor(document: DesignDocument, pageId: String): LayoutBox? {
    val page = document.pages.firstOrNull { it.id == pageId } ?: return null
    val resolved = runCatching { DesignResolver(document).resolvePage(page).firstOrNull() }.getOrNull() ?: return null
    return runCatching { DesignLayoutEngine().layout(resolved) }.getOrNull()
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
