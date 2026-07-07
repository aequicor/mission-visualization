package io.aequicor.visualization.engine.frontend.blocks.readers

import io.aequicor.visualization.engine.frontend.blocks.TypedBlockReader
import io.aequicor.visualization.engine.frontend.blocks.TypedPatch
import io.aequicor.visualization.engine.frontend.diagnostics.DiagnosticCollector
import io.aequicor.visualization.engine.frontend.markdown.SlmMarkdownParser
import io.aequicor.visualization.engine.frontend.markdown.TypedAttributeBlock
import io.aequicor.visualization.engine.frontend.markdown.TypedEntry

/**
 * `§` stands in for `$` so spec snippets can be pasted without Kotlin string
 * interpolation escapes.
 */
internal fun slm(text: String): String = text.trimIndent().replace('§', '$')

internal fun parseEntries(markdown: String): Pair<List<TypedEntry>, DiagnosticCollector> {
    val collector = DiagnosticCollector("test.layout.md")
    val document = SlmMarkdownParser(collector).parse(slm(markdown))
    val entries = document.blocks.filterIsInstance<TypedAttributeBlock>().flatMap { it.entries }
    return entries to collector
}

internal fun readPatches(markdown: String): Pair<List<TypedPatch?>, DiagnosticCollector> {
    val (entries, collector) = parseEntries(markdown)
    return entries.map { TypedBlockReader.read(it, collector) } to collector
}

internal fun readSingle(markdown: String): Pair<TypedPatch?, DiagnosticCollector> {
    val (entries, collector) = parseEntries(markdown)
    check(entries.size == 1) { "Expected one typed entry, got ${entries.size}" }
    return TypedBlockReader.read(entries.single(), collector) to collector
}
