package io.aequicor.visualization.engine.frontend.blocks.readers

import io.aequicor.visualization.engine.frontend.blocks.TypedBlockKind
import io.aequicor.visualization.engine.frontend.blocks.TypedBlockReader
import io.aequicor.visualization.engine.frontend.blocks.TypedPatch
import io.aequicor.visualization.engine.frontend.diagnostics.DiagnosticCollector
import io.aequicor.visualization.engine.frontend.markdown.SlmSourceSpan
import io.aequicor.visualization.engine.frontend.markdown.TypedEntry
import io.aequicor.visualization.engine.frontend.yaml.YamlMap
import io.aequicor.visualization.engine.frontend.yaml.parseSlmYaml

/**
 * `§` stands in for `$` so spec snippets can be pasted without Kotlin string
 * interpolation escapes.
 */
internal fun slm(text: String): String = text.trimIndent().replace('§', '$')

/**
 * Turns a reserved-key YAML snippet into the same [TypedEntry] shape CNL desugar emits, so
 * reader unit tests can exercise [TypedBlockReader.read] on arbitrary typed entries. This is a
 * test-only helper: it does not go through any markdown authoring parse path (raw typed blocks
 * are no longer an authoring surface). Each column-0 reserved key opens one entry whose slice is
 * that line plus its indented continuation lines.
 */
internal fun parseEntries(markdown: String): Pair<List<TypedEntry>, DiagnosticCollector> {
    val collector = DiagnosticCollector("test.layout.md")
    val lines = slm(markdown).split('\n')
    val entries = mutableListOf<TypedEntry>()
    var i = 0
    while (i < lines.size) {
        val key = reservedKeyAt(lines[i])
        if (key == null) {
            i++
            continue
        }
        var j = i + 1
        while (j < lines.size && lines[j].isNotBlank() && lines[j].first().isWhitespace()) j++
        val kind = TypedBlockKind.fromKey(key)
        if (kind != null) {
            val sliceText = lines.subList(i, j).joinToString("\n")
            val parsed = parseSlmYaml(sliceText, collector, startLine = i + 1)
            val value = (parsed as? YamlMap)?.entries?.get(key)
            if (value != null) {
                entries += TypedEntry(key, value, SlmSourceSpan(i + 1, j))
            }
        }
        i = j
    }
    return entries to collector
}

/** Leading reserved `key:` (colon then space or line end) at column 0, or null. */
private fun reservedKeyAt(text: String): String? {
    if (text.isEmpty() || !text[0].isLetter()) return null
    var i = 0
    while (i < text.length && (text[i].isLetterOrDigit() || text[i] == '_' || text[i] == '-')) i++
    if (i >= text.length || text[i] != ':') return null
    val after = text.getOrNull(i + 1)
    if (after != null && after != ' ') return null
    val word = text.take(i)
    return if (TypedBlockKind.fromKey(word) != null) word else null
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
