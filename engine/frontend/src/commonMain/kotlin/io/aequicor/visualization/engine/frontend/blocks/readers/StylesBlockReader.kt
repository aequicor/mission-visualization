package io.aequicor.visualization.engine.frontend.blocks.readers

import io.aequicor.visualization.engine.frontend.blocks.StylesPatch
import io.aequicor.visualization.engine.frontend.yaml.YamlMap
import io.aequicor.visualization.engine.frontend.yaml.YamlValue
import io.aequicor.visualization.engine.ir.model.DesignStyle

private val knownStyleKeys = setOf("type", "style", "text", "layout")

/** `styles:` block — document-scoped shared paint/text/effect/grid styles. */
internal fun readStylesBlock(value: YamlValue, reading: BlockReading): StylesPatch? {
    val map = value as? YamlMap ?: run {
        reading.error("`styles` must be a map", value)
        return null
    }
    val styles = linkedMapOf<String, DesignStyle>()
    map.entries.forEach { (id, raw) ->
        val spec = raw as? YamlMap ?: run {
            reading.warning("Style \"$id\" must be a map", raw)
            return@forEach
        }
        spec.warnUnknownKeys(knownStyleKeys, reading)
        when (val type = spec.string("type", reading)) {
            "paint" -> {
                val style = spec.value("style") ?: run {
                    reading.warning("Paint style \"$id\" needs a `style` body", spec)
                    return@forEach
                }
                val fills = readStyleBlock(style, reading)?.fills ?: run {
                    reading.warning("Paint style \"$id\" needs fill/color CNL", style)
                    return@forEach
                }
                styles[id] = DesignStyle.Paint(fills)
            }
            "text" -> {
                val text = spec.value("text") ?: run {
                    reading.warning("Text style \"$id\" needs a `text` body", spec)
                    return@forEach
                }
                val typography = readTextBlock(text, reading)?.typography ?: run {
                    reading.warning("Text style \"$id\" needs typography CNL", text)
                    return@forEach
                }
                styles[id] = DesignStyle.Text(typography)
            }
            "effect" -> {
                val style = spec.value("style") ?: run {
                    reading.warning("Effect style \"$id\" needs a `style` body", spec)
                    return@forEach
                }
                val effects = readStyleBlock(style, reading)?.effects ?: run {
                    reading.warning("Effect style \"$id\" needs effect CNL", style)
                    return@forEach
                }
                styles[id] = DesignStyle.Effect(effects)
            }
            "grid" -> {
                val layout = spec.value("layout") ?: run {
                    reading.warning("Grid style \"$id\" needs a `layout` body", spec)
                    return@forEach
                }
                val grids = readLayoutBlock(layout, reading)?.grids ?: run {
                    reading.warning("Grid style \"$id\" needs grids CNL", layout)
                    return@forEach
                }
                styles[id] = DesignStyle.Grid(grids)
            }
            null -> reading.warning("Style \"$id\" needs a `type`", spec)
            else -> reading.warning("Unknown shared style type \"$type\"", spec)
        }
    }
    return StylesPatch(styles)
}
