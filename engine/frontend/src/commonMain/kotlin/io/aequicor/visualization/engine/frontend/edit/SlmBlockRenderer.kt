package io.aequicor.visualization.engine.frontend.edit

/**
 * Renders a [YamlPayload] tree into canonical SLM/YAML lines. Extracted verbatim from
 * [YamlPathWriter] so both the surgical path writer and the structural
 * [NodeSectionWriter] share one encoder — in particular the load-bearing invariant that
 * a flow map after a `- ` dash is NOT accepted by the SLM parser in block context, so
 * map list items hoist their first entry onto the `- ` line and align the rest two
 * columns in (`- key: v` / `  key2: v2`).
 *
 * All renderers are pure (indent + payload -> lines); positional insertion is the
 * caller's job.
 */
internal object SlmBlockRenderer {

    /** `key: value` / `key:` + nested lines, indented [indent] spaces. */
    fun entryLines(key: String, payload: YamlPayload, indent: Int): List<String> {
        val pad = " ".repeat(indent)
        return when (payload) {
            is YamlPayload.Scalar -> listOf("$pad$key: ${ScalarFormatter.format(payload.value)}")
            is YamlPayload.Mapping -> buildList {
                add("$pad$key:")
                addAll(mappingLines(payload, indent + 2))
            }
            is YamlPayload.Sequence -> buildList {
                add("$pad$key:")
                addAll(sequenceLines(payload, indent + 2))
            }
        }
    }

    fun mappingLines(payload: YamlPayload.Mapping, indent: Int): List<String> =
        payload.entries.flatMap { (key, child) -> entryLines(key, child, indent) }

    fun sequenceLines(payload: YamlPayload.Sequence, indent: Int): List<String> =
        payload.items.flatMap { sequenceItem(it, indent) }

    /**
     * Renders one block-sequence item. Scalars render inline (`- value`); map items
     * hoist their first entry onto the `- ` line and align the rest two columns in
     * (`- key: v` / `  key2: v2`), so nested structures stay valid block YAML — a flow
     * map after a `- ` dash is not accepted by the SLM parser in block context.
     */
    private fun sequenceItem(item: YamlPayload, indent: Int): List<String> {
        val pad = " ".repeat(indent)
        return when (item) {
            is YamlPayload.Scalar -> listOf("$pad- ${ScalarFormatter.format(item.value)}")
            is YamlPayload.Sequence -> listOf("$pad- ${inline(item)}")
            is YamlPayload.Mapping -> {
                if (item.entries.isEmpty()) return listOf("$pad- {}")
                val entryLines = mappingLines(item, indent + 2)
                listOf("$pad- ${entryLines.first().trimStart()}") + entryLines.drop(1)
            }
        }
    }

    fun inline(payload: YamlPayload): String = when (payload) {
        is YamlPayload.Scalar -> ScalarFormatter.format(payload.value)
        is YamlPayload.Mapping ->
            "{ " + payload.entries.joinToString(", ") { (k, v) -> "$k: ${inline(v)}" } + " }"
        is YamlPayload.Sequence -> "[" + payload.items.joinToString(", ") { inline(it) } + "]"
    }
}
