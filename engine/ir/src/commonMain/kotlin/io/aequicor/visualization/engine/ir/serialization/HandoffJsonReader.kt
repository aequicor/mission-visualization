package io.aequicor.visualization.engine.ir.serialization

import io.aequicor.visualization.engine.ir.model.CodeHints
import io.aequicor.visualization.engine.ir.model.DesignAnnotation
import io.aequicor.visualization.engine.ir.model.DesignHandoff
import io.aequicor.visualization.engine.ir.model.DesignMeasurement
import io.aequicor.visualization.engine.ir.model.ExportFormat
import io.aequicor.visualization.engine.ir.model.ExportSetting
import io.aequicor.visualization.engine.ir.model.MeasureAxis
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** Handoff metadata (annotations, measurements, code hints) and export settings. */

internal fun DesignDocumentReader.readHandoff(element: JsonElement?, pointer: String): DesignHandoff {
    val obj = element as? JsonObject ?: return DesignHandoff()
    return DesignHandoff(
        annotations = obj["annotations"].asArray("$pointer/annotations").mapIndexedNotNull { index, annotation ->
            readAnnotation(annotation, "$pointer/annotations/$index")
        },
        measurements = obj["measurements"].asArray("$pointer/measurements").mapIndexedNotNull { index, measurement ->
            readMeasurement(measurement, "$pointer/measurements/$index")
        },
        code = (obj["code"] as? JsonObject)?.let { code ->
            CodeHints(
                framework = code.stringOrDefault("framework"),
                componentHint = code.stringOrDefault("componentHint"),
            )
        },
    )
}

internal fun DesignDocumentReader.readAnnotation(element: JsonElement, pointer: String): DesignAnnotation? {
    val obj = element as? JsonObject ?: return null
    return DesignAnnotation(
        id = obj.stringOrDefault("id"),
        target = obj.stringOrDefault("target"),
        text = obj.stringOrDefault("text"),
        audience = obj.stringOrDefault("audience"),
    )
}

private fun DesignDocumentReader.readMeasurement(element: JsonElement, pointer: String): DesignMeasurement? {
    val obj = element as? JsonObject ?: return null
    return DesignMeasurement(
        from = obj.stringOrDefault("from"),
        to = obj.stringOrDefault("to"),
        axis = readEnum(
            obj["axis"], "$pointer/axis", MeasureAxis.Inline,
            mapOf("inline" to MeasureAxis.Inline, "block" to MeasureAxis.Block),
        ),
        value = obj["value"]?.let { readBindableDouble(it, 0.0) },
    )
}

internal fun DesignDocumentReader.readExportSettings(
    element: JsonElement?,
    pointer: String,
): List<ExportSetting> =
    element.asArray(pointer).mapIndexedNotNull { index, setting ->
        val obj = setting as? JsonObject ?: return@mapIndexedNotNull null
        ExportSetting(
            format = readEnum(
                obj["format"], "$pointer/$index/format", ExportFormat.Png,
                mapOf(
                    "png" to ExportFormat.Png,
                    "jpg" to ExportFormat.Jpg,
                    "svg" to ExportFormat.Svg,
                    "pdf" to ExportFormat.Pdf,
                ),
            ),
            scale = obj.doubleOrDefault("scale", 1.0),
            suffix = obj.stringOrDefault("suffix"),
        )
    }
