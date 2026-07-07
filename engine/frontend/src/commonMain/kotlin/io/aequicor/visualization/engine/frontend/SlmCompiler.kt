package io.aequicor.visualization.engine.frontend

import io.aequicor.visualization.engine.frontend.diagnostics.DiagnosticCollector
import io.aequicor.visualization.engine.frontend.edit.SlmEditIndex
import io.aequicor.visualization.engine.frontend.frontmatter.readFrontmatter
import io.aequicor.visualization.engine.frontend.i18n.TextEntry
import io.aequicor.visualization.engine.frontend.markdown.SlmMarkdownParser
import io.aequicor.visualization.engine.frontend.normalize.IrNormalizer
import io.aequicor.visualization.engine.frontend.semantics.StructuralLexicon
import io.aequicor.visualization.engine.frontend.semantics.extractStructure
import io.aequicor.visualization.engine.ir.model.DesignDiagnostic
import io.aequicor.visualization.engine.ir.model.DesignDocument

/**
 * Compiles Semantic Layout Markdown into the design IR.
 *
 * Pure function; a fresh [DiagnosticCollector] per call. Only markdown catastrophes
 * (unclosed frontmatter, empty source) return `document = null` — anything else
 * compiles with diagnostics.
 */
fun compileSlm(source: String, options: SlmCompileOptions = SlmCompileOptions()): SlmCompileResult {
    val diagnostics = DiagnosticCollector(options.fileName)
    val fingerprint = fnv1a64(source)
    val parsed = SlmMarkdownParser(diagnostics).parse(source)

    val unclosedFrontmatter =
        source.lineSequence().firstOrNull()?.trim() == "---" && parsed.frontmatter == null
    val empty = parsed.frontmatter == null && parsed.blocks.isEmpty()
    if (unclosedFrontmatter || empty) {
        if (empty && !unclosedFrontmatter) {
            diagnostics.error("Empty SLM document", 1)
        }
        return SlmCompileResult(
            document = null,
            resources = emptyMap(),
            diagnostics = diagnostics.diagnostics,
            sourceFingerprint = fingerprint,
            editIndex = SlmEditIndex.Empty,
        )
    }

    val frontmatter = readFrontmatter(parsed.frontmatter, diagnostics)
    val screen = extractStructure(
        document = parsed,
        frontmatter = frontmatter,
        diagnostics = diagnostics,
        fallbackLocale = options.fallbackLocale,
        lexicon = options.structuralLexicon,
    )
    val normalized = IrNormalizer(diagnostics, options.fileName).normalize(screen)

    return SlmCompileResult(
        document = normalized.document,
        resources = placeholderResources(screen.sourceLocale, normalized.textEntries),
        diagnostics = diagnostics.diagnostics,
        sourceFingerprint = fingerprint,
        editIndex = normalized.editIndex,
    )
}

data class SlmCompileOptions(
    val fileName: String = "document.layout.md",
    val fallbackLocale: SlmLocale = SlmLocale("en-US"),
    /** Structural markers; the full semantic lexicons arrive with stage 7.7. */
    val structuralLexicon: StructuralLexicon = StructuralLexicon(),
)

data class SlmCompileResult(
    /** Null only on fatal structural failure (unclosed frontmatter, empty source). */
    val document: DesignDocument?,
    /** locale -> key -> text; keys are placeholders until the i18n stage (7.8). */
    val resources: Map<SlmLocale, Map<String, String>>,
    val diagnostics: List<DesignDiagnostic>,
    /** FNV-1a 64 of the exact source string; SlmPatcher's staleness gate. */
    val sourceFingerprint: Long,
    /** Opaque node-to-source index for SlmPatcher. */
    val editIndex: SlmEditIndex,
) {
    val isSuccess: Boolean get() = document != null
}

/**
 * PLACEHOLDER key scheme `slm.text.<nodeId>` — real key generation is stage 7.8.
 * The source-locale bundle carries every collected default text.
 */
internal const val PLACEHOLDER_TEXT_KEY_PREFIX = "slm.text."

private fun placeholderResources(
    sourceLocale: SlmLocale,
    entries: List<TextEntry>,
): Map<SlmLocale, Map<String, String>> {
    if (entries.isEmpty()) return mapOf(sourceLocale to emptyMap())
    val bundle = LinkedHashMap<String, String>()
    entries.forEach { entry ->
        var key = PLACEHOLDER_TEXT_KEY_PREFIX + entry.nodeId
        var suffix = 2
        while (bundle.containsKey(key) && bundle[key] != entry.defaultText) {
            key = "$PLACEHOLDER_TEXT_KEY_PREFIX${entry.nodeId}.$suffix"
            suffix++
        }
        bundle[key] = entry.defaultText
    }
    return mapOf(sourceLocale to bundle)
}

/** FNV-1a 64-bit hash over the UTF-8 bytes of [text]. */
fun fnv1a64(text: String): Long {
    var hash = -3750763034362895579L // 0xCBF29CE484222325
    text.encodeToByteArray().forEach { byte ->
        hash = hash xor (byte.toLong() and 0xFF)
        hash *= 1099511628211L // 0x00000100000001B3
    }
    return hash
}
