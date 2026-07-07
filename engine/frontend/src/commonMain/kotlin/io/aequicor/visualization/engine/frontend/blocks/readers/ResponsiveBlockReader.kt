package io.aequicor.visualization.engine.frontend.blocks.readers

import io.aequicor.visualization.engine.frontend.blocks.ResponsivePatch
import io.aequicor.visualization.engine.frontend.blocks.ResponsiveVariantPatch
import io.aequicor.visualization.engine.frontend.yaml.YamlMap
import io.aequicor.visualization.engine.frontend.yaml.YamlValue
import io.aequicor.visualization.engine.ir.model.ResponsiveDimension

/** `responsive:` block — conditional layout/style/text overrides per mode selectors. */
internal fun readResponsiveBlock(value: YamlValue, reading: BlockReading): ResponsivePatch? {
    val map = value as? YamlMap ?: run {
        reading.error("`responsive` must be a map with `variants`", value)
        return null
    }
    map.warnUnknownKeys(setOf("variants"), reading)
    val list = map.listValue("variants", reading) ?: run {
        reading.error("`responsive` needs a `variants` list", map)
        return null
    }
    val variants = list.items.mapNotNull { item ->
        val variant = item as? YamlMap ?: run {
            reading.warning("`variants` items must be maps", item)
            return@mapNotNull null
        }
        variant.warnUnknownKeys(setOf("when", "layout", "style", "text"), reading)
        val selectors = readSelectors(variant, reading) ?: return@mapNotNull null
        ResponsiveVariantPatch(
            selectors = selectors,
            layout = variant.value("layout")?.let { readLayoutBlock(it, reading) },
            style = variant.value("style")?.let { readStyleBlock(it, reading) },
            text = variant.value("text")?.let { readTextBlock(it, reading) },
        )
    }
    return ResponsivePatch(variants)
}

private fun readSelectors(
    variant: YamlMap,
    reading: BlockReading,
): Map<ResponsiveDimension, String>? {
    val whenMap = variant.mapValue("when", reading) ?: run {
        reading.warning("Responsive variant needs a `when` selector map", variant)
        return null
    }
    val selectors = whenMap.entries.mapNotNull { (dimension, selector) ->
        val mapped = ReaderEnums.responsiveDimension[dimension]
        if (mapped == null) {
            reading.warning("Unknown responsive dimension \"$dimension\"", selector)
            return@mapNotNull null
        }
        val text = selector.stringOrNull() ?: run {
            reading.warning("Selector `$dimension` must be a string", selector)
            return@mapNotNull null
        }
        mapped to text
    }.toMap()
    if (selectors.isEmpty()) {
        reading.warning("Responsive variant `when` selects nothing", whenMap)
        return null
    }
    return selectors
}
