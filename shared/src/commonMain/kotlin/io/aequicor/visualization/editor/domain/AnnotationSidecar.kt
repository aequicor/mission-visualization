package io.aequicor.visualization.editor.domain

import io.aequicor.visualization.engine.frontend.SlmCompileResult
import io.aequicor.visualization.engine.frontend.edit.SlmEditIndex
import io.aequicor.visualization.engine.frontend.fnv1a64
import io.aequicor.visualization.subsystems.annotations.AnnotationLayer
import io.aequicor.visualization.subsystems.annotations.slm.AnnotationSlmParser

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
 */
internal fun annotationSidecarCompileResult(content: String): SlmCompileResult =
    SlmCompileResult(
        document = null,
        resources = emptyMap(),
        diagnostics = emptyList(),
        sourceFingerprint = fnv1a64(content),
        editIndex = SlmEditIndex.Empty,
    )
