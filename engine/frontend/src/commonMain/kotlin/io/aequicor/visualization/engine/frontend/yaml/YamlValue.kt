package io.aequicor.visualization.engine.frontend.yaml

/**
 * Parsed YAML-subset value. Every value carries its 1-based source position:
 * [line]/[column] point at the first character of the value, [endLine]/[endColumn]
 * describe the subtree extent — [endColumn] is exclusive (the column right after the
 * last character on [endLine]). Positions are absolute in the enclosing SLM file when
 * the parser is given the slice's start line/column.
 */
sealed interface YamlValue {
    val line: Int
    val column: Int
    val endLine: Int
    val endColumn: Int
}

/**
 * Scalar leaf. [value] is `null`, [Boolean], [Double], or [String] — numbers are kept
 * as [Double] uniformly, matching the IR. [raw] is the exact source token including
 * quotes but excluding any trailing ` # comment`, so surgical text edits can replace
 * exactly `raw.length` characters at [line]/[column].
 */
data class YamlScalar(
    val value: Any?,
    val raw: String,
    override val line: Int,
    override val column: Int,
    override val endLine: Int = line,
    override val endColumn: Int = column + raw.length,
) : YamlValue

/** Insertion-ordered map; [column] is the indent column of its keys. */
data class YamlMap(
    val entries: Map<String, YamlValue>,
    override val line: Int,
    override val column: Int,
    override val endLine: Int,
    override val endColumn: Int,
) : YamlValue

data class YamlList(
    val items: List<YamlValue>,
    override val line: Int,
    override val column: Int,
    override val endLine: Int,
    override val endColumn: Int,
) : YamlValue
