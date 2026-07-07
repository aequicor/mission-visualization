package io.aequicor.visualization.engine.frontend

import io.aequicor.visualization.engine.frontend.diagnostics.DiagnosticCollector
import io.aequicor.visualization.engine.frontend.edit.SlmEditIndex
import io.aequicor.visualization.engine.frontend.frontmatter.readFrontmatter
import io.aequicor.visualization.engine.frontend.i18n.applyGeneratedKeys
import io.aequicor.visualization.engine.frontend.i18n.generateI18nResources
import io.aequicor.visualization.engine.frontend.markdown.SlmMarkdownParser
import io.aequicor.visualization.engine.frontend.normalize.IrNormalizer
import io.aequicor.visualization.engine.frontend.semantics.EnLexicon
import io.aequicor.visualization.engine.frontend.semantics.SemanticLexicon
import io.aequicor.visualization.engine.frontend.semantics.SemanticLexicons
import io.aequicor.visualization.engine.frontend.semantics.detectSourceLocale
import io.aequicor.visualization.engine.frontend.semantics.extractSemantics
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
    val sourceLocale = detectSourceLocale(
        document = parsed,
        frontmatter = frontmatter,
        diagnostics = diagnostics,
        fallbackLocale = options.fallbackLocale,
        cyrillicRatioThreshold = options.cyrillicRatioThreshold,
    )
    val lexicon = SemanticLexicons.forLocale(sourceLocale, options.lexicons)
        ?: SemanticLexicons.forLocale(options.fallbackLocale, options.lexicons)
        ?: options.lexicons.firstOrNull()
        ?: EnLexicon
    val screen = extractSemantics(
        document = parsed,
        frontmatter = frontmatter,
        sourceLocale = sourceLocale,
        lexicon = lexicon,
        diagnostics = diagnostics,
    )
    val normalized = IrNormalizer(diagnostics, options.fileName).normalize(screen)

    val i18n = generateI18nResources(
        entries = normalized.textEntries,
        screenId = frontmatter.screen.ifBlank { normalized.document.id },
        sourceLocale = sourceLocale,
        targetLocales = frontmatter.targetLocales,
        lexicon = lexicon,
        diagnostics = diagnostics,
    )
    var document = applyGeneratedKeys(normalized.document, i18n.assignments, sourceLocale)
    document = document.copy(
        i18n = document.i18n.copy(
            resources = i18n.resources.entries.associate { (locale, bundle) -> locale.tag to bundle },
        ),
    )

    return SlmCompileResult(
        document = document,
        resources = i18n.resources,
        diagnostics = diagnostics.diagnostics,
        sourceFingerprint = fingerprint,
        editIndex = normalized.editIndex,
    )
}

data class SlmCompileOptions(
    val fileName: String = "document.layout.md",
    /** Semantic lexicons; the detected source locale picks one of them. */
    val lexicons: List<SemanticLexicon> = SemanticLexicons.builtIn,
    val fallbackLocale: SlmLocale = SlmLocale("en-US"),
    /** Cyrillic-letter ratio at/above which prose is detected as `ru-RU`. */
    val cyrillicRatioThreshold: Double = 0.3,
)

data class SlmCompileResult(
    /** Null only on fatal structural failure (unclosed frontmatter, empty source). */
    val document: DesignDocument?,
    /** locale -> key -> message; the source locale carries every default text. */
    val resources: Map<SlmLocale, Map<String, String>>,
    val diagnostics: List<DesignDiagnostic>,
    /** FNV-1a 64 of the exact source string; SlmPatcher's staleness gate. */
    val sourceFingerprint: Long,
    /** Opaque node-to-source index for SlmPatcher. */
    val editIndex: SlmEditIndex,
) {
    val isSuccess: Boolean get() = document != null
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
