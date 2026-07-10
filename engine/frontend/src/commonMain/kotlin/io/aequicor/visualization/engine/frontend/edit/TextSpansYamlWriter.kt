package io.aequicor.visualization.engine.frontend.edit

import io.aequicor.visualization.engine.ir.model.DesignTextStyle
import io.aequicor.visualization.engine.ir.model.TextLink
import io.aequicor.visualization.engine.ir.model.TextStyleRange

/**
 * Serializes a text node's `styleRanges` + `links` back into a single `spans:`
 * [YamlPayload.Sequence] for surgical write-back — the inverse of `readSpans`. Each
 * span carries `range: [start, end]` plus an optional inline `typography:` map
 * (via [TypographyYamlWriter]), inline `fills:` (via [StyleYamlWriter]) and a `link:`.
 *
 * Style ranges and links that share the same `[start, end)` collapse into one span
 * entry; the list is marked [YamlPayload.Sequence.replaceWhole] so the whole authored
 * `spans:` list is rewritten (the working document owns the authoritative set).
 */
internal object TextSpansYamlWriter {

    fun spans(ranges: List<TextStyleRange>, links: List<TextLink>): YamlPayload.Sequence {
        val keys = LinkedHashSet<Pair<Int, Int>>()
        ranges.forEach { keys += it.start to it.end }
        links.forEach { keys += it.start to it.end }
        val rangeByKey = ranges.associateBy { it.start to it.end }
        val linkByKey = links.associateBy { it.start to it.end }

        val items = keys.sortedWith(compareBy({ it.first }, { it.second })).map { key ->
            val range = rangeByKey[key]
            val link = linkByKey[key]
            YamlPayload.Mapping(
                buildList {
                    add(
                        "range" to YamlPayload.Sequence(
                            listOf(
                                YamlPayload.Scalar(YamlScalarValue.Num(key.first.toDouble())),
                                YamlPayload.Scalar(YamlScalarValue.Num(key.second.toDouble())),
                            ),
                        ),
                    )
                    range?.styleRef?.takeIf { it.isNotBlank() }?.let {
                        add("style" to YamlPayload.Scalar(YamlScalarValue.Str(it)))
                    }
                    range?.style?.takeUnless { it == DesignTextStyle() }?.let {
                        add("typography" to TypographyYamlWriter.typography(it))
                    }
                    range?.fills?.takeIf { it.isNotEmpty() }?.let { add("fills" to StyleYamlWriter.fills(it)) }
                    link?.let { add("link" to linkPayload(it)) }
                },
            )
        }
        return YamlPayload.Sequence(items, replaceWhole = true)
    }

    private fun linkPayload(link: TextLink): YamlPayload = YamlPayload.Mapping(
        if (link.nodeTarget.isNotEmpty()) {
            listOf(
                "type" to YamlPayload.Scalar(YamlScalarValue.Str("node")),
                "target" to YamlPayload.Scalar(YamlScalarValue.Str(link.nodeTarget)),
            )
        } else {
            listOf(
                "type" to YamlPayload.Scalar(YamlScalarValue.Str("url")),
                "href" to YamlPayload.Scalar(YamlScalarValue.Str(link.url)),
            )
        },
    )
}
