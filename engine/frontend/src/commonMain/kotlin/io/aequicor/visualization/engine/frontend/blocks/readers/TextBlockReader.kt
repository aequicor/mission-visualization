package io.aequicor.visualization.engine.frontend.blocks.readers

import io.aequicor.visualization.engine.frontend.blocks.TextPatch
import io.aequicor.visualization.engine.frontend.blocks.TextSpanPatch
import io.aequicor.visualization.engine.frontend.yaml.YamlList
import io.aequicor.visualization.engine.frontend.yaml.YamlMap
import io.aequicor.visualization.engine.frontend.yaml.YamlScalar
import io.aequicor.visualization.engine.frontend.yaml.YamlValue
import io.aequicor.visualization.engine.ir.model.DesignColor
import io.aequicor.visualization.engine.ir.model.DesignPaint
import io.aequicor.visualization.engine.ir.model.DesignTextStyle
import io.aequicor.visualization.engine.ir.model.DesignUnit
import io.aequicor.visualization.engine.ir.model.TextListSettings
import io.aequicor.visualization.engine.ir.model.TextListType
import io.aequicor.visualization.engine.ir.model.TextTruncate
import io.aequicor.visualization.engine.ir.model.UnitValue
import io.aequicor.visualization.engine.ir.model.bindable

private val knownKeys = setOf(
    "key", "defaultText", "characters", "style", "typography", "resizing", "maxLines", "overflow",
    "spans", "list",
)

private val typographyKeys = setOf(
    "fontFamily", "fontWeight", "italic", "fontSize", "lineHeight", "letterSpacing",
    "paragraphSpacing", "paragraphIndent", "horizontalAlign", "verticalAlign",
    "decoration", "decorationStyle", "decorationColor", "decorationThickness",
    "decorationSkipInk", "case", "position", "leadingTrim", "hangingPunctuation",
    "hangingList", "openType", "variableFont",
)

/** `text:` block — i18n content plus text-layer behavior. */
internal fun readTextBlock(value: YamlValue, reading: BlockReading): TextPatch? {
    val map = value as? YamlMap ?: run {
        reading.error("`text` must be a map", value)
        return null
    }
    map.warnUnknownKeys(knownKeys, reading)
    val defaultText = map.string("defaultText", reading)
    val resizing = map.mapValue("resizing", reading)
    val maxLines = map.int("maxLines", reading)
    val overflow = map.string("overflow", reading)
    if (overflow != null && overflow != "truncate" && overflow != "visible") {
        reading.warning("Unknown `overflow` value \"$overflow\"; expected truncate or visible", map)
    }
    val truncate = when {
        overflow == "truncate" -> TextTruncate(maxLines ?: 1)
        maxLines != null -> TextTruncate(maxLines, ellipsis = false)
        else -> null
    }
    return TextPatch(
        key = map.string("key", reading),
        defaultText = defaultText,
        characters = map.value("characters")?.let { bindableString(it, "characters", reading) },
        styleRef = map.string("style", reading),
        typography = readTypography(map, reading),
        resizingWidth = resizing?.enum("width", ReaderEnums.sizingMode, reading),
        resizingHeight = resizing?.enum("height", ReaderEnums.sizingMode, reading),
        truncate = truncate,
        spans = readSpans(map, defaultText, reading),
        list = readListSettings(map, reading),
    )
}

private fun readTypography(map: YamlMap, reading: BlockReading): DesignTextStyle? {
    val typography = map.mapValue("typography", reading) ?: return null
    return readTypographyMap(typography, reading)
}

/** Reads a `typography:` map's fields into a partial [DesignTextStyle]; reused by spans. */
internal fun readTypographyMap(typography: YamlMap, reading: BlockReading): DesignTextStyle {
    typography.warnUnknownKeys(typographyKeys, reading)
    val openType = typography.mapValue("openType", reading)
    val variableFont = typography.mapValue("variableFont", reading)
    return DesignTextStyle(
        fontFamily = typography.string("fontFamily", reading),
        fontWeight = typography.bindableDouble("fontWeight", reading),
        italic = typography.boolean("italic", reading),
        fontSize = typography.bindableDouble("fontSize", reading),
        lineHeight = typography.unitValue("lineHeight", reading),
        letterSpacing = typography.unitValue("letterSpacing", reading),
        paragraphSpacing = typography.double("paragraphSpacing", reading),
        paragraphIndent = typography.double("paragraphIndent", reading),
        textAlignHorizontal = typography.enum("horizontalAlign", ReaderEnums.textAlignHorizontal, reading),
        textAlignVertical = typography.enum("verticalAlign", ReaderEnums.textAlignVertical, reading),
        textCase = typography.enum("case", ReaderEnums.textCase, reading),
        textDecoration = typography.enum("decoration", ReaderEnums.textDecoration, reading),
        decorationStyle = typography.enum("decorationStyle", ReaderEnums.textDecorationStyle, reading),
        decorationColor = typography.color("decorationColor", reading),
        decorationThickness = typography.unitValue("decorationThickness", reading),
        decorationSkipInk = typography.boolean("decorationSkipInk", reading),
        textPosition = typography.enum("position", ReaderEnums.textScriptPosition, reading),
        leadingTrim = typography.enum("leadingTrim", ReaderEnums.leadingTrim, reading),
        hangingPunctuation = typography.boolean("hangingPunctuation", reading),
        hangingList = typography.boolean("hangingList", reading),
        fontFeatures = openType?.booleanEntries(reading) ?: emptyMap(),
        variableAxes = variableFont?.axisEntries(reading) ?: emptyMap(),
    )
}

/** Hex color scalar (`#RRGGBB` / `#RRGGBBAA`); unknown values warn and drop. */
private fun YamlMap.color(key: String, reading: BlockReading): DesignColor? {
    val text = string(key, reading) ?: return null
    return DesignColor.fromHex(text) ?: run {
        reading.warning("`$key` must be a hex color like #RRGGBB", entries[key] ?: this)
        null
    }
}

/** Numbers are px; `{ unit: percent, value: 135 }` maps stay explicit. */
private fun YamlMap.unitValue(key: String, reading: BlockReading): UnitValue? {
    val value = entries[key] ?: return null
    (value as? YamlScalar)?.let { scalar ->
        val number = scalar.value as? Double ?: run {
            reading.warning("`$key` must be a number or a {unit, value} map", value)
            return null
        }
        return UnitValue(DesignUnit.Px, number)
    }
    val map = value as? YamlMap ?: run {
        reading.warning("`$key` must be a number or a {unit, value} map", value)
        return null
    }
    val unit = when (map.string("unit", reading)) {
        "percent", "%" -> DesignUnit.Percent
        else -> DesignUnit.Px
    }
    return UnitValue(unit, map.double("value", reading) ?: 0.0)
}

private fun YamlMap.booleanEntries(reading: BlockReading): Map<String, Boolean> =
    entries.mapNotNull { (key, value) ->
        val flag = (value as? YamlScalar)?.value as? Boolean
        if (flag == null) {
            reading.warning("`openType.$key` must be true or false", value)
            null
        } else {
            key to flag
        }
    }.toMap()

/** Variable font axes; friendly names map to registered axis tags. */
private fun YamlMap.axisEntries(reading: BlockReading): Map<String, Double> =
    entries.mapNotNull { (key, value) ->
        val number = (value as? YamlScalar)?.value as? Double
        if (number == null) {
            reading.warning("`variableFont.$key` must be a number", value)
            null
        } else {
            val axis = when (key) {
                "weight" -> "wght"
                "opticalSize" -> "opsz"
                "width" -> "wdth"
                "slant" -> "slnt"
                "italic" -> "ital"
                else -> key
            }
            axis to number
        }
    }.toMap()

// --- rich text spans ---

private fun readSpans(map: YamlMap, defaultText: String?, reading: BlockReading): List<TextSpanPatch>? {
    val list = map.listValue("spans", reading) ?: return null
    return list.items.mapNotNull { item -> readSpan(item, defaultText, reading) }
}

private fun readSpan(item: YamlValue, defaultText: String?, reading: BlockReading): TextSpanPatch? {
    val span = item as? YamlMap ?: run {
        reading.warning("`spans` items must be maps", item)
        return null
    }
    span.warnUnknownKeys(setOf("range", "text", "style", "typography", "fills", "link"), reading)
    val range = readRange(span, defaultText, reading) ?: return null
    val link = span.mapValue("link", reading)
    var linkUrl: String? = null
    var linkNodeTarget: String? = null
    if (link != null) {
        when (val type = link.string("type", reading) ?: "url") {
            "url" -> linkUrl = link.string("href", reading) ?: link.string("url", reading)
            "node" -> linkNodeTarget = link.string("target", reading) ?: link.string("node", reading)
            else -> reading.warning("Unknown span link type \"$type\"", link)
        }
    }
    val inlineStyle = span.mapValue("typography", reading)?.let { readTypographyMap(it, reading) }
    return TextSpanPatch(
        start = range.first,
        end = range.second,
        styleRef = span.string("style", reading),
        style = inlineStyle,
        fills = readPaints(span, "fills", reading),
        linkUrl = linkUrl,
        linkNodeTarget = linkNodeTarget,
    )
}

/** `range: [start, end]` or `text: substring` resolved against `defaultText`. */
private fun readRange(span: YamlMap, defaultText: String?, reading: BlockReading): Pair<Int, Int>? {
    (span.value("range") as? YamlList)?.let { range ->
        val numbers = range.items.mapNotNull { (it as? YamlScalar)?.value as? Double }
        if (numbers.size != 2) {
            reading.warning("`range` must be [start, end]", range)
            return null
        }
        return numbers[0].toInt() to numbers[1].toInt()
    }
    val text = span.string("text", reading) ?: run {
        reading.warning("Span needs `range` or `text`", span)
        return null
    }
    val start = defaultText?.indexOf(text) ?: -1
    if (start < 0) {
        reading.warning("Span text \"$text\" not found in `defaultText`", span)
        return null
    }
    return start to start + text.length
}

private fun readListSettings(map: YamlMap, reading: BlockReading): TextListSettings? {
    val list = map.mapValue("list", reading) ?: return null
    list.warnUnknownKeys(setOf("type", "indent"), reading)
    return TextListSettings(
        type = list.enum("type", ReaderEnums.textListType, reading) ?: TextListType.None,
        indent = list.int("indent", reading) ?: 0,
    )
}
