package io.aequicor.visualization.editor.domain

import io.aequicor.visualization.engine.frontend.SlmCompileResult
import io.aequicor.visualization.engine.frontend.compileSlm
import io.aequicor.visualization.engine.ir.model.DesignComponent
import io.aequicor.visualization.engine.ir.model.DesignDiagnostic
import io.aequicor.visualization.engine.ir.model.DesignDocument
import io.aequicor.visualization.engine.ir.model.DesignHandoff
import io.aequicor.visualization.engine.ir.model.DesignI18n
import io.aequicor.visualization.engine.ir.model.DesignNode
import io.aequicor.visualization.engine.ir.model.DesignSeverity
import io.aequicor.visualization.engine.ir.model.DesignVariables

/**
 * The mission design assembled from per-page SLM documents: the merged multi-page
 * [document] (null when every compile failed fatally), the raw [sources] and
 * per-document [compiled] results (kept for the resize write-back stage, which
 * needs each page's SLM source, fingerprint and edit index), and all diagnostics
 * (per-file compile diagnostics plus merge conflicts).
 */
data class MissionDocuments(
    val document: DesignDocument?,
    val sources: List<MissionDocumentSource>,
    val compiled: List<SlmCompileResult>,
    val diagnostics: List<DesignDiagnostic>,
) {
    val hasErrors: Boolean
        get() = document == null || diagnostics.any { it.severity == DesignSeverity.Error }
}

/**
 * Compiles each per-page SLM [sources] entry as a standalone document and merges them
 * into one multi-page [MissionDocuments]. Shared by the initial bundled load and by
 * draft restore (which recompiles the persisted SLM text). Annotation sidecars
 * (`*.annotations.md`) are never compiled as SLM: they get a placeholder compile entry
 * (null document, skipped by the merge, carrying the sidecar parse warnings as
 * diagnostics) that keeps the two lists index-aligned. Sidecar sources are normalized
 * once at this load boundary ([normalizeAnnotationSidecarSources]): synthesized ids are
 * pinned and cross-file id collisions re-minted, so annotation ids are stable and
 * globally unique before any surgical edit.
 */
fun compileMissionDocuments(sources: List<MissionDocumentSource>): MissionDocuments {
    val normalized = normalizeAnnotationSidecarSources(sources)
    val compiled = normalized.map { source ->
        if (isAnnotationSidecarFileName(source.fileName)) {
            annotationSidecarCompileResult(source.fileName, source.content)
        } else {
            compileSlm(source.content, editorSlmCompileOptions(source.fileName))
        }
    }
    return mergeMissionDocuments(normalized, compiled)
}

/**
 * Merges standalone-compiled SLM documents into one [DesignDocument]:
 * - pages are concatenated in source order, re-id'd from each document's screen meta;
 * - components/componentSets/styles/variable collections/assets and the rest of the
 *   top-level dictionaries merge by id — identical duplicates collapse (ignoring
 *   source maps), conflicting definitions keep the first and add a diagnostic;
 * - i18n resources merge per locale, key conflicts keep the first value;
 * - diagnostics are the compile diagnostics (already carrying their file names)
 *   plus the merge conflicts.
 */
fun mergeMissionDocuments(
    sources: List<MissionDocumentSource>,
    compiled: List<SlmCompileResult>,
): MissionDocuments {
    val diagnostics = compiled.flatMap { it.diagnostics }.toMutableList()
    val documents = compiled.mapIndexedNotNull { index, result ->
        result.document?.let { document ->
            (sources.getOrNull(index)?.fileName ?: document.id) to document
        }
    }
    if (documents.isEmpty()) {
        return MissionDocuments(null, sources, compiled, diagnostics)
    }

    val pages = documents.flatMap { (_, document) ->
        document.pages.map { page ->
            val screenId = document.screen?.id.orEmpty()
            if (screenId.isBlank()) page else page.copy(id = screenId)
        }
    }
    val merger = DocumentMerger(diagnostics)
    val first = documents.first().second
    val merged = DesignDocument(
        schemaVersion = first.schemaVersion,
        id = "doc_mission",
        name = "Mission Visualization",
        pages = pages,
        components = merger.mergeById("component", documents) { it.components },
        componentSets = merger.mergeById("component set", documents) { it.componentSets },
        styles = merger.mergeById("style", documents) { it.styles },
        variables = DesignVariables(
            collections = merger.mergeById("variable collection", documents) { it.variables.collections },
        ),
        assets = merger.mergeById("asset", documents) { it.assets },
        screen = first.screen,
        libraries = documents.flatMap { (_, document) -> document.libraries }.distinctBy { it.id },
        breakpoints = documents.flatMap { (_, document) -> document.breakpoints }.distinctBy { it.id },
        devicePresets = documents.flatMap { (_, document) -> document.devicePresets }.distinctBy { it.id },
        prototypeVariables = merger.mergeById("prototype variable", documents) { it.prototypeVariables },
        actionSets = merger.mergeById("action set", documents) { it.actionSets },
        i18n = DesignI18n(
            sourceLocale = first.i18n.sourceLocale,
            targetLocales = documents.flatMap { (_, document) -> document.i18n.targetLocales }.distinct(),
            resources = merger.mergeResources(documents),
        ),
        handoff = DesignHandoff(
            annotations = documents.flatMap { (_, document) -> document.handoff.annotations },
            measurements = documents.flatMap { (_, document) -> document.handoff.measurements },
            code = documents.firstNotNullOfOrNull { (_, document) -> document.handoff.code },
        ),
        motionRefs = merger.mergeById("motion ref", documents) { it.motionRefs },
    )
    return MissionDocuments(merged, sources, compiled, diagnostics)
}

/** Id-keyed dictionary merging with collapse-or-warn semantics. */
private class DocumentMerger(private val diagnostics: MutableList<DesignDiagnostic>) {

    fun <V : Any> mergeById(
        kind: String,
        documents: List<Pair<String, DesignDocument>>,
        select: (DesignDocument) -> Map<String, V>,
    ): Map<String, V> {
        val merged = LinkedHashMap<String, V>()
        val owners = mutableMapOf<String, String>()
        documents.forEach { (fileName, document) ->
            select(document).forEach { (id, value) ->
                val existing = merged[id]
                when {
                    existing == null -> {
                        merged[id] = value
                        owners[id] = fileName
                    }
                    existing.normalized() == value.normalized() -> Unit // identical duplicate
                    else -> diagnostics += DesignDiagnostic(
                        severity = DesignSeverity.Warning,
                        message = "Conflicting $kind \"$id\" in $fileName; " +
                            "keeping the definition from ${owners.getValue(id)}",
                    )
                }
            }
        }
        return merged
    }

    /** locale -> key -> message, merged across documents; key conflicts keep the first. */
    fun mergeResources(
        documents: List<Pair<String, DesignDocument>>,
    ): Map<String, Map<String, String>> {
        val merged = LinkedHashMap<String, LinkedHashMap<String, String>>()
        documents.forEach { (fileName, document) ->
            document.i18n.resources.forEach { (locale, bundle) ->
                val target = merged.getOrPut(locale) { LinkedHashMap() }
                bundle.forEach { (key, message) ->
                    val existing = target[key]
                    when {
                        existing == null -> target[key] = message
                        existing == message -> Unit
                        else -> diagnostics += DesignDiagnostic(
                            severity = DesignSeverity.Warning,
                            message = "Conflicting i18n resource \"$key\" ($locale) in $fileName; " +
                                "keeping the first value",
                        )
                    }
                }
            }
        }
        return merged
    }
}

/** Comparison form of a merged value: component trees compare without source maps. */
private fun Any.normalized(): Any = when (this) {
    is DesignComponent -> copy(root = root.withoutSourceMaps())
    else -> this
}

private fun DesignNode.withoutSourceMaps(): DesignNode = copy(
    sourceMap = null,
    blockSourceMaps = emptyMap(),
    interactions = interactions.map { it.copy(sourceMap = null) },
    responsive = responsive.map { it.copy(sourceMap = null) },
    children = children.map { it.withoutSourceMaps() },
)
