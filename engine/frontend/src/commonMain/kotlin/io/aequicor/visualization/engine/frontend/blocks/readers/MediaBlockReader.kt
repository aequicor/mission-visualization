package io.aequicor.visualization.engine.frontend.blocks.readers

import io.aequicor.visualization.engine.frontend.blocks.MediaPatch
import io.aequicor.visualization.engine.frontend.yaml.YamlMap
import io.aequicor.visualization.engine.frontend.yaml.YamlScalar
import io.aequicor.visualization.engine.frontend.yaml.YamlValue
import io.aequicor.visualization.engine.ir.model.TextContent

private val knownKeys = setOf(
    "asset", "kind", "fillMode", "focalPoint", "alt", "replaceable", "opacity",
    "blendMode", "poster", "autoplay", "loop", "muted",
)

/** `media:` block — image/video convenience layer over fill behavior. */
internal fun readMediaBlock(value: YamlValue, reading: BlockReading): MediaPatch? {
    val map = value as? YamlMap ?: run {
        reading.error("`media` must be a map", value)
        return null
    }
    map.warnUnknownKeys(knownKeys, reading)
    return MediaPatch(
        asset = map.string("asset", reading),
        kind = map.enum("kind", ReaderEnums.mediaKind, reading),
        fillMode = map.enum("fillMode", ReaderEnums.fillMode, reading),
        focalPoint = readFocalPoint(map.value("focalPoint"), reading),
        alt = readAlt(map.value("alt"), reading),
        replaceable = map.boolean("replaceable", reading),
        opacity = map.bindableDouble("opacity", reading),
        blendMode = map.string("blendMode", reading),
        poster = map.string("poster", reading),
        autoplay = map.boolean("autoplay", reading),
        loop = map.boolean("loop", reading),
        muted = map.boolean("muted", reading),
    )
}

/** `alt: plain text` or `alt: { key, defaultText }`. */
private fun readAlt(value: YamlValue?, reading: BlockReading): TextContent? {
    if (value == null) return null
    (value as? YamlScalar)?.let { scalar ->
        val text = scalar.value as? String ?: run {
            reading.warning("`alt` must be a string or a {key, defaultText} map", value)
            return null
        }
        return TextContent(defaultText = text)
    }
    val map = value as? YamlMap ?: run {
        reading.warning("`alt` must be a string or a {key, defaultText} map", value)
        return null
    }
    map.warnUnknownKeys(setOf("key", "defaultText"), reading)
    return TextContent(
        key = map.string("key", reading).orEmpty(),
        defaultText = map.string("defaultText", reading).orEmpty(),
    )
}
