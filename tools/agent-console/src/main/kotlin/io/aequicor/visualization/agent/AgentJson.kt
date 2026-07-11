package io.aequicor.visualization.agent

import io.aequicor.visualization.engine.ir.layout.LayoutBox
import io.aequicor.visualization.engine.ir.model.DesignDiagnostic
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * JSON projections of the read surface (screens / inspect / validate). Built with
 * runtime `buildJsonObject` builders — no serialization plugin, no annotations on
 * engine types.
 */
object AgentJson {

    private val pretty: Json = Json { prettyPrint = true }

    fun render(element: JsonElement): String = pretty.encodeToString(JsonElement.serializer(), element)

    fun screens(screens: List<ScreenSummary>): JsonArray = buildJsonArray {
        screens.forEach { screen ->
            add(
                buildJsonObject {
                    put("id", screen.id)
                    put("name", screen.name)
                    screen.width?.let { put("width", it.rounded()) }
                    screen.height?.let { put("height", it.rounded()) }
                    put("nodeCount", screen.nodeCount)
                    screen.sourceFile?.let { put("sourceFile", it) }
                },
            )
        }
    }

    fun layoutTree(box: LayoutBox): JsonObject = buildJsonObject {
        put("id", box.node.sourceId.ifBlank { box.node.id })
        put("type", box.node.type)
        if (box.node.name.isNotBlank()) put("name", box.node.name)
        put("x", box.x.rounded())
        put("y", box.y.rounded())
        put("width", box.width.rounded())
        put("height", box.height.rounded())
        box.node.text?.characters?.takeIf { it.isNotBlank() }?.let { put("text", it) }
        if (box.children.isNotEmpty()) {
            put("children", buildJsonArray { box.children.forEach { add(layoutTree(it)) } })
        }
    }

    fun diagnostics(diagnostics: List<DesignDiagnostic>): JsonArray = buildJsonArray {
        diagnostics.forEach { diagnostic ->
            add(
                buildJsonObject {
                    put("severity", diagnostic.severity.name.lowercase())
                    if (diagnostic.code.isNotBlank()) put("code", diagnostic.code)
                    put("message", diagnostic.message)
                    diagnostic.location?.let { location ->
                        if (location.file.isNotBlank()) put("file", location.file)
                        if (location.line > 0) put("line", location.line)
                        if (location.pointer.isNotBlank()) put("pointer", location.pointer)
                    }
                },
            )
        }
    }

    /** Keeps geometry readable: 862.0000000000002 → 862, 12.25 stays 12.25. */
    private fun Double.rounded(): Double {
        val scaled = kotlin.math.round(this * 100.0) / 100.0
        return if (scaled == -0.0) 0.0 else scaled
    }
}
