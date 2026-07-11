package io.aequicor.visualization.editor.domain

import io.aequicor.visualization.engine.frontend.SlmCompileResult
import io.aequicor.visualization.engine.frontend.edit.SlmEditIndex
import io.aequicor.visualization.engine.frontend.fnv1a64
import io.aequicor.visualization.engine.ir.model.DesignDiagnostic
import io.aequicor.visualization.engine.ir.model.DesignSeverity
import io.aequicor.visualization.engine.ir.model.SourceLocation
import io.aequicor.visualization.subsystems.annotations.AnnotationLayer
import io.aequicor.visualization.subsystems.annotations.slm.AnnotationSlmParseResult
import io.aequicor.visualization.subsystems.annotations.slm.AnnotationSlmParser
import io.aequicor.visualization.subsystems.annotations.slm.AnnotationSlmPatcher
import io.aequicor.visualization.subsystems.annotations.slm.AnnotationSlmWriter

/**
 * Annotation sidecar convention: every screen `<screen>.layout.md` may carry a review
 * layer in `<screen>.annotations.md` next to it. Sidecars ride in the same
 * [MissionDocumentSource] list as the SLM screens — so drafts, Save/Reset and restore
 * persist them for free — but they are never compiled as SLM: [compileMissionDocuments]
 * routes them to [AnnotationSlmParser] via a placeholder compile result that keeps the
 * source/compile lists index-aligned.
 */

/** File suffix of an SLM screen source. */
const val ScreenLayoutSuffix: String = ".layout.md"

/** File suffix of an annotations sidecar source. */
const val AnnotationSidecarSuffix: String = ".annotations.md"

/** True when [fileName] is an annotations sidecar, not an SLM screen. */
fun isAnnotationSidecarFileName(fileName: String): Boolean =
    fileName.endsWith(AnnotationSidecarSuffix)

/** Sidecar file name for a screen: `mission.layout.md` -> `mission.annotations.md`. */
fun annotationSidecarFileName(screenFileName: String): String =
    screenFileName.removeSuffix(ScreenLayoutSuffix) + AnnotationSidecarSuffix

/** Screen file name a sidecar belongs to: `mission.annotations.md` -> `mission.layout.md`. */
fun screenFileNameForSidecar(sidecarFileName: String): String =
    sidecarFileName.removeSuffix(AnnotationSidecarSuffix) + ScreenLayoutSuffix

/**
 * Annotation layers of [sources], keyed by screen file name: every `*.layout.md` screen
 * gets a layer (empty when it has no sidecar), and every `*.annotations.md` sidecar is
 * parsed tolerantly — including a sidecar whose screen is absent, so annotations are
 * kept, never lost.
 */
fun annotationLayersFrom(sources: List<MissionDocumentSource>): Map<String, AnnotationLayer> {
    val layers = LinkedHashMap<String, AnnotationLayer>()
    sources.forEach { source ->
        if (source.fileName.endsWith(ScreenLayoutSuffix)) {
            layers.getOrPut(source.fileName) { AnnotationLayer(source.fileName) }
        }
    }
    sources.forEach { source ->
        if (isAnnotationSidecarFileName(source.fileName)) {
            val screenFileName = screenFileNameForSidecar(source.fileName)
            layers[screenFileName] = AnnotationSlmParser.parse(screenFileName, source.content).layer
        }
    }
    return layers
}

/**
 * Placeholder compile entry for a sidecar source: keeps `sources`/`compiledResults`
 * index-aligned without compiling annotation markdown as SLM. The null document is
 * skipped by [mergeMissionDocuments]; the empty edit index makes every SLM patch
 * attempt against the sidecar fail cleanly (write-back then tries the next source).
 * Recoverable sidecar parse problems ride in [SlmCompileResult.diagnostics], so they
 * surface on the same editor diagnostics list as SLM compile warnings.
 */
internal fun annotationSidecarCompileResult(fileName: String, content: String): SlmCompileResult =
    SlmCompileResult(
        document = null,
        resources = emptyMap(),
        diagnostics = annotationSidecarDiagnostics(fileName, content),
        sourceFingerprint = fnv1a64(content),
        editIndex = SlmEditIndex.Empty,
    )

/** Stable diagnostic code for annotation sidecar parse warnings. */
const val AnnotationSidecarDiagnosticCode: String = "ANN-PARSE"

/**
 * Sidecar parse warnings of [content] as editor [DesignDiagnostic]s (severity Warning,
 * location = sidecar file + 1-based line) — the same surface SLM validation issues use.
 */
fun annotationSidecarDiagnostics(fileName: String, content: String): List<DesignDiagnostic> =
    AnnotationSlmParser.parse(screenFileNameForSidecar(fileName), content).warnings.map { warning ->
        DesignDiagnostic(
            severity = DesignSeverity.Warning,
            message = warning.message,
            location = SourceLocation(file = fileName, line = warning.line),
            code = AnnotationSidecarDiagnosticCode,
        )
    }

/**
 * Load-boundary normalization of annotation sidecar sources — the id-stability
 * invariant of the review layer:
 * - a section without an explicit `{id=...}` marker gets its parser-synthesized id
 *   pinned into the header ([AnnotationSlmPatcher.pinIds]), so a later surgical edit
 *   addresses the same section instead of appending a duplicate that silently reverts
 *   on reload;
 * - an id already claimed by an earlier sidecar is re-minted to a fresh `ann-<n>`
 *   (unique across every file) and pinned — workspace selection/expansion and prompt
 *   export are id-keyed and not screen-scoped, so two hand-authored sidecars both
 *   starting at `ann-1` would otherwise edit/delete the wrong screen's annotation.
 *
 * Non-sidecar sources pass through untouched; an already-canonical list returns
 * byte-identical contents. Callers run this once at load (no undo history exists yet),
 * so the pinned text persists through drafts like any other source content.
 */
fun normalizeAnnotationSidecarSources(sources: List<MissionDocumentSource>): List<MissionDocumentSource> {
    if (sources.none { isAnnotationSidecarFileName(it.fileName) }) return sources
    val pinned = sources.map { source ->
        if (!isAnnotationSidecarFileName(source.fileName)) return@map source
        val content = AnnotationSlmPatcher.pinIds(source.content)
        if (content == source.content) source else source.copy(content = content)
    }
    val parsedByIndex = pinned.mapIndexedNotNull { index, source ->
        if (!isAnnotationSidecarFileName(source.fileName)) return@mapIndexedNotNull null
        index to AnnotationSlmParser.parse(screenFileNameForSidecar(source.fileName), source.content)
    }
    // Fresh mints must dodge every id in every file, so an id kept by a later sidecar
    // is never manufactured into a new collision.
    val allIds = parsedByIndex.flatMapTo(mutableSetOf()) { (_, parsed) -> parsed.layer.annotations.map { it.id } }
    val claimed = mutableSetOf<String>()
    val result = pinned.toMutableList()
    parsedByIndex.forEach { (index, parsed) ->
        val renames = LinkedHashMap<String, String>()
        parsed.layer.annotations.forEach { annotation ->
            if (annotation.id in claimed) {
                val fresh = mintAnnotationIdAvoiding(allIds + claimed)
                renames[annotation.id] = fresh
                claimed += fresh
            } else {
                claimed += annotation.id
            }
        }
        if (renames.isNotEmpty()) {
            result[index] = result[index].copy(content = renameSidecarSections(result[index].content, parsed, renames))
        }
    }
    return result
}

/**
 * Normalizes a single sidecar's [content] (direct source editing): pins synthesized ids
 * and re-mints ids colliding with [idsUsedElsewhere] (annotations of other screens), so
 * a source edit can never introduce a cross-file duplicate id.
 */
fun normalizeAnnotationSidecarContent(
    fileName: String,
    content: String,
    idsUsedElsewhere: Set<String>,
): String {
    val pinnedContent = AnnotationSlmPatcher.pinIds(content)
    val parsed = AnnotationSlmParser.parse(screenFileNameForSidecar(fileName), pinnedContent)
    val ownIds = parsed.layer.annotations.mapTo(mutableSetOf()) { it.id }
    val minted = mutableSetOf<String>()
    val renames = LinkedHashMap<String, String>()
    parsed.layer.annotations.forEach { annotation ->
        if (annotation.id in idsUsedElsewhere) {
            val fresh = mintAnnotationIdAvoiding(idsUsedElsewhere + ownIds + minted)
            renames[annotation.id] = fresh
            minted += fresh
        }
    }
    return if (renames.isEmpty()) pinnedContent else renameSidecarSections(pinnedContent, parsed, renames)
}

/** First `ann-<n>` not in [used] — the same minting style as the editor and the parser. */
private fun mintAnnotationIdAvoiding(used: Set<String>): String {
    var n = 1
    while ("ann-$n" in used) n++
    return "ann-$n"
}

/**
 * Rewrites the header line of every renamed section with a canonical render carrying the
 * new id (body bytes stay untouched). The sections parsed successfully, so the parsed
 * [Annotation][io.aequicor.visualization.subsystems.annotations.Annotation] holds every
 * header field and the re-render loses nothing.
 */
private fun renameSidecarSections(
    content: String,
    parsed: AnnotationSlmParseResult,
    renames: Map<String, String>,
): String {
    val lines = content.split('\n').toMutableList()
    renames.forEach { (oldId, newId) ->
        val headerLine = parsed.sectionLines[oldId] ?: return@forEach
        val annotation = parsed.layer.annotations.firstOrNull { it.id == oldId } ?: return@forEach
        lines[headerLine - 1] = AnnotationSlmWriter.renderSection(annotation.copy(id = newId)).lineSequence().first()
    }
    return lines.joinToString("\n")
}
