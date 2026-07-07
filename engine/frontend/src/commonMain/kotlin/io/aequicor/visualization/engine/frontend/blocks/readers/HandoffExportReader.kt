package io.aequicor.visualization.engine.frontend.blocks.readers

import io.aequicor.visualization.engine.frontend.blocks.ExportPatch
import io.aequicor.visualization.engine.frontend.blocks.HandoffPatch
import io.aequicor.visualization.engine.frontend.yaml.YamlMap
import io.aequicor.visualization.engine.frontend.yaml.YamlValue
import io.aequicor.visualization.engine.ir.model.CodeHints
import io.aequicor.visualization.engine.ir.model.DesignAnnotation
import io.aequicor.visualization.engine.ir.model.DesignHandoff
import io.aequicor.visualization.engine.ir.model.DesignMeasurement
import io.aequicor.visualization.engine.ir.model.ExportSetting

/** `handoff:` block — annotations, measurements, code hints. */
internal fun readHandoffBlock(value: YamlValue, reading: BlockReading): HandoffPatch? {
    val map = value as? YamlMap ?: run {
        reading.error("`handoff` must be a map", value)
        return null
    }
    map.warnUnknownKeys(setOf("annotations", "measurements", "code"), reading)
    val annotations = map.listValue("annotations", reading)?.items?.mapNotNull { item ->
        val annotation = item as? YamlMap ?: run {
            reading.warning("`annotations` items must be maps", item)
            return@mapNotNull null
        }
        annotation.warnUnknownKeys(setOf("id", "target", "text", "audience"), reading)
        val text = annotation.string("text", reading) ?: run {
            reading.warning("Annotation needs `text`", annotation)
            return@mapNotNull null
        }
        DesignAnnotation(
            id = annotation.string("id", reading).orEmpty(),
            target = annotation.string("target", reading).orEmpty(),
            text = text,
            audience = annotation.string("audience", reading).orEmpty(),
        )
    } ?: emptyList()
    val measurements = map.listValue("measurements", reading)?.items?.mapNotNull { item ->
        val measurement = item as? YamlMap ?: run {
            reading.warning("`measurements` items must be maps", item)
            return@mapNotNull null
        }
        measurement.warnUnknownKeys(setOf("from", "to", "axis", "value"), reading)
        val from = measurement.string("from", reading)
        val to = measurement.string("to", reading)
        val axis = measurement.enum("axis", ReaderEnums.measureAxis, reading)
        if (from == null || to == null || axis == null) {
            reading.warning("Measurement needs `from`, `to` and `axis`", measurement)
            return@mapNotNull null
        }
        DesignMeasurement(
            from = from,
            to = to,
            axis = axis,
            value = measurement.bindableDouble("value", reading),
        )
    } ?: emptyList()
    val code = map.mapValue("code", reading)?.let { codeMap ->
        codeMap.warnUnknownKeys(setOf("framework", "componentHint"), reading)
        CodeHints(
            framework = codeMap.string("framework", reading).orEmpty(),
            componentHint = codeMap.string("componentHint", reading).orEmpty(),
        )
    }
    return HandoffPatch(
        DesignHandoff(
            annotations = annotations,
            measurements = measurements,
            code = code,
        ),
    )
}

/** `export:` block. */
internal fun readExportBlock(value: YamlValue, reading: BlockReading): ExportPatch? {
    val map = value as? YamlMap ?: run {
        reading.error("`export` must be a map", value)
        return null
    }
    map.warnUnknownKeys(setOf("enabled", "settings"), reading)
    val settings = map.listValue("settings", reading)?.items?.mapNotNull { item ->
        val setting = item as? YamlMap ?: run {
            reading.warning("`settings` items must be maps", item)
            return@mapNotNull null
        }
        setting.warnUnknownKeys(setOf("format", "scale", "suffix"), reading)
        val format = setting.enum("format", ReaderEnums.exportFormat, reading)
            ?: return@mapNotNull null
        ExportSetting(
            format = format,
            scale = setting.double("scale", reading) ?: 1.0,
            suffix = setting.string("suffix", reading).orEmpty(),
        )
    } ?: emptyList()
    return ExportPatch(
        enabled = map.boolean("enabled", reading),
        settings = settings,
    )
}
