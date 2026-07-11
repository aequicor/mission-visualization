package io.aequicor.visualization.engine.ir.serialization

import io.aequicor.visualization.engine.ir.model.Bindable
import io.aequicor.visualization.engine.ir.model.CanvasPlacement
import io.aequicor.visualization.engine.ir.model.DesignAsset
import io.aequicor.visualization.engine.ir.model.DesignBreakpoint
import io.aequicor.visualization.engine.ir.model.DesignColor
import io.aequicor.visualization.engine.ir.model.DesignDiagnostic
import io.aequicor.visualization.engine.ir.model.DesignDocument
import io.aequicor.visualization.engine.ir.model.DesignExpression
import io.aequicor.visualization.engine.ir.model.DesignFlow
import io.aequicor.visualization.engine.ir.model.DesignI18n
import io.aequicor.visualization.engine.ir.model.DesignLibrary
import io.aequicor.visualization.engine.ir.model.DesignPage
import io.aequicor.visualization.engine.ir.model.DesignPoint
import io.aequicor.visualization.engine.ir.model.DesignSeverity
import io.aequicor.visualization.engine.ir.model.DesignSize
import io.aequicor.visualization.engine.ir.model.DesignScreenMeta
import io.aequicor.visualization.engine.ir.model.DesignVariable
import io.aequicor.visualization.engine.ir.model.DesignVariables
import io.aequicor.visualization.engine.ir.model.DevicePreset
import io.aequicor.visualization.engine.ir.model.FramePreset
import io.aequicor.visualization.engine.ir.model.SourceLocation
import io.aequicor.visualization.engine.ir.model.VariableCollection
import io.aequicor.visualization.engine.ir.model.VariableType
import io.aequicor.visualization.engine.ir.model.VariableValue
import io.aequicor.visualization.engine.ir.model.bindable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

/**
 * Parses a Figma-like JSON design document into the typed IR.
 *
 * Forward compatibility contract: unknown fields are ignored, unknown enum values fall
 * back to a default and emit a warning diagnostic; only malformed JSON or a non-object
 * root fails the parse.
 */
fun parseDesignDocument(source: String): DesignParseResult {
    val json = Json { ignoreUnknownKeys = true }
    val root = try {
        json.parseToJsonElement(source)
    } catch (failure: Exception) {
        return DesignParseResult.Failure(
            listOf(
                DesignDiagnostic(
                    DesignSeverity.Error,
                    "Malformed JSON: ${failure.message.orEmpty().take(200)}",
                ),
            ),
        )
    }
    val rootObject = root as? JsonObject
        ?: return DesignParseResult.Failure(
            listOf(DesignDiagnostic(DesignSeverity.Error, "Document root must be a JSON object")),
        )
    val parser = DesignDocumentReader()
    val document = parser.readDocument(rootObject)
    return DesignParseResult.Success(document, parser.diagnostics)
}

/**
 * Parses a single node subtree (the ```ir escape hatch): the JSON root must be one node
 * object. [pointerBase] anchors diagnostic/source-map pointers; when the JSON carries no
 * `sourceMap` of its own, the root node is seeded with ([file], [line]).
 */
fun parseDesignNode(
    source: String,
    pointerBase: String = "",
    file: String = "",
    line: Int = 0,
): DesignNodeParseResult {
    val json = Json { ignoreUnknownKeys = true }
    val root = try {
        json.parseToJsonElement(source)
    } catch (failure: Exception) {
        return DesignNodeParseResult.Failure(
            listOf(
                DesignDiagnostic(
                    DesignSeverity.Error,
                    "Malformed JSON: ${failure.message.orEmpty().take(200)}",
                    SourceLocation(pointer = pointerBase, file = file, line = line),
                ),
            ),
        )
    }
    val reader = DesignDocumentReader()
    val node = reader.readNode(root, pointerBase)
        ?: return DesignNodeParseResult.Failure(reader.diagnostics)
    val seeded = if (node.sourceMap == null && (file.isNotEmpty() || line != 0)) {
        node.copy(sourceMap = SourceLocation(pointer = pointerBase, file = file, line = line))
    } else {
        node
    }
    return DesignNodeParseResult.Success(seeded, reader.diagnostics)
}

/** Legacy "1.x" documents and "slm-ir/1.x" documents parse identically. */
private val SupportedSchemaVersion = Regex("""(\d+\.|slm-ir/1\.).*""")

/**
 * Shared reading context for the per-area readers (NodeJsonReader, LayoutJsonReader,
 * StyleJsonReader, ComponentJsonReader, InteractionJsonReader, ResponsiveJsonReader,
 * HandoffJsonReader): one diagnostics sink plus the primitive/bindable helpers they use.
 */
internal class DesignDocumentReader {
    val diagnostics = mutableListOf<DesignDiagnostic>()

    fun readDocument(obj: JsonObject): DesignDocument {
        val schemaVersion = obj.stringOrDefault("schemaVersion", "slm-ir/1.0")
        if (!SupportedSchemaVersion.matches(schemaVersion)) {
            warn("/schemaVersion", "Unsupported schema version '$schemaVersion'; parsing as slm-ir/1.x")
        }
        return DesignDocument(
            schemaVersion = schemaVersion,
            id = obj.stringOrDefault("id"),
            name = obj.stringOrDefault("name"),
            pages = obj["pages"].asArray("/pages").mapIndexed { index, page ->
                readPage(page, "/pages/$index")
            },
            components = obj["components"].asObject().mapNotNull { (id, value) ->
                readComponent(value, "/components/$id")?.let { id to it }
            }.toMap(),
            componentSets = obj["componentSets"].asObject().mapValues { (id, value) ->
                readComponentSet(value, "/componentSets/$id")
            },
            styles = obj["styles"].asObject().mapNotNull { (id, value) ->
                readStyle(value, "/styles/$id")?.let { id to it }
            }.toMap(),
            variables = readVariables(obj["variables"], "/variables"),
            assets = obj["assets"].asObject().mapValues { (_, value) ->
                readAsset(value)
            },
            screen = readScreenMeta(obj["screen"], "/screen"),
            libraries = readLibraries(obj["libraries"], "/libraries"),
            breakpoints = readBreakpoints(obj["breakpoints"], "/breakpoints"),
            devicePresets = readDevicePresets(obj["devicePresets"], "/devicePresets"),
            prototypeVariables = readPrototypeVariables(obj["prototypeVariables"], "/prototypeVariables"),
            actionSets = readActionSets(obj["actionSets"], "/actionSets"),
            i18n = readI18n(obj),
            handoff = readHandoff(obj["handoff"], "/handoff"),
            motionRefs = obj["motionRefs"].asObject().mapValues { (_, ref) -> ref.asStringOrEmpty() },
        )
    }

    private fun readPage(element: JsonElement, pointer: String): DesignPage {
        val obj = element.asObject()
        return DesignPage(
            id = obj.stringOrDefault("id"),
            name = obj.stringOrDefault("name"),
            children = readChildren(obj, pointer),
        )
    }

    // --- Screen meta, libraries, breakpoints, i18n -------------------------

    private fun readScreenMeta(element: JsonElement?, pointer: String): DesignScreenMeta? {
        val obj = element as? JsonObject ?: return null
        return DesignScreenMeta(
            id = obj.stringOrDefault("id"),
            name = obj.stringOrDefault("name"),
            page = obj.stringOrDefault("page"),
            modes = obj["modes"].asObject().mapValues { (_, mode) -> mode.asStringOrEmpty() },
            frame = (obj["frame"] as? JsonObject)?.let { frame ->
                FramePreset(
                    preset = frame.stringOrDefault("preset"),
                    width = (frame["width"] as? JsonPrimitive)?.doubleOrNull,
                    height = (frame["height"] as? JsonPrimitive)?.doubleOrNull,
                )
            },
            canvas = (obj["canvas"] as? JsonObject)?.let { canvas ->
                CanvasPlacement(
                    section = canvas.stringOrDefault("section"),
                    x = canvas.plainDouble("x", "$pointer/canvas", 0.0),
                    y = canvas.plainDouble("y", "$pointer/canvas", 0.0),
                )
            },
            flow = (obj["flow"] as? JsonObject)?.let { flow ->
                DesignFlow(
                    id = flow.stringOrDefault("id"),
                    node = flow.stringOrDefault("node"),
                    next = flow["next"].asArray("$pointer/flow/next").map { it.asStringOrEmpty() },
                )
            },
        )
    }

    private fun readLibraries(element: JsonElement?, pointer: String): List<DesignLibrary> =
        element.asArray(pointer).mapIndexedNotNull { index, library ->
            val obj = library as? JsonObject ?: return@mapIndexedNotNull null
            val id = obj.stringOrDefault("id")
            if (id.isEmpty()) {
                warn("$pointer/$index", "Library without id is ignored")
                return@mapIndexedNotNull null
            }
            DesignLibrary(id = id, source = obj.stringOrDefault("source"))
        }

    private fun readBreakpoints(element: JsonElement?, pointer: String): List<DesignBreakpoint> =
        element.asArray(pointer).mapIndexedNotNull { index, breakpoint ->
            val obj = breakpoint as? JsonObject ?: return@mapIndexedNotNull null
            val id = obj.stringOrDefault("id")
            if (id.isEmpty()) {
                warn("$pointer/$index", "Breakpoint without id is ignored")
                return@mapIndexedNotNull null
            }
            DesignBreakpoint(
                id = id,
                minWidth = (obj["minWidth"] as? JsonPrimitive)?.doubleOrNull,
                maxWidth = (obj["maxWidth"] as? JsonPrimitive)?.doubleOrNull,
            )
        }

    private fun readDevicePresets(element: JsonElement?, pointer: String): List<DevicePreset> =
        element.asArray(pointer).mapIndexedNotNull { index, preset ->
            val obj = preset as? JsonObject ?: return@mapIndexedNotNull null
            val id = obj.stringOrDefault("id")
            if (id.isEmpty()) {
                warn("$pointer/$index", "Device preset without id is ignored")
                return@mapIndexedNotNull null
            }
            DevicePreset(
                id = id,
                width = obj.doubleOrDefault("width", 0.0),
                height = obj.doubleOrDefault("height", 0.0),
                platform = obj.stringOrDefault("platform"),
            )
        }

    /**
     * i18n lives in an "i18n" block, with the spec's shorthand spellings accepted:
     * source/target locales on "screen" and locale bundles in a top-level "resources".
     */
    private fun readI18n(obj: JsonObject): DesignI18n {
        val i18nObj = obj["i18n"] as? JsonObject
        val screenObj = obj["screen"] as? JsonObject
        val resources = mutableMapOf<String, Map<String, String>>()
        fun collect(element: JsonElement?) {
            (element as? JsonObject)?.forEach { (locale, bundle) ->
                resources[locale] = resources[locale].orEmpty() + bundle.asObject().mapValues { (_, message) ->
                    message.asStringOrEmpty()
                }
            }
        }
        collect(i18nObj?.get("resources"))
        collect(obj["resources"])
        val sourceLocale = i18nObj?.stringOrDefault("sourceLocale").orEmpty()
            .ifEmpty { screenObj?.stringOrDefault("sourceLocale").orEmpty() }
        val targetLocales = (i18nObj?.get("targetLocales") ?: screenObj?.get("targetLocales"))
            ?.asArray("/i18n/targetLocales")?.map { it.asStringOrEmpty() }
            ?: emptyList()
        return DesignI18n(
            sourceLocale = sourceLocale,
            targetLocales = targetLocales,
            resources = resources,
        )
    }

    // --- Variables and assets ----------------------------------------------

    private fun readVariables(element: JsonElement?, pointer: String): DesignVariables {
        val obj = element as? JsonObject ?: return DesignVariables()
        return DesignVariables(
            collections = obj["collections"].asObject().mapValues { (collectionId, collection) ->
                readVariableCollection(collection, "$pointer/collections/$collectionId")
            },
        )
    }

    private fun readVariableCollection(element: JsonElement, pointer: String): VariableCollection {
        val obj = element.asObject()
        val modes = obj["modes"].asArray("$pointer/modes").map { it.asStringOrEmpty() }
        return VariableCollection(
            name = obj.stringOrDefault("name"),
            modes = modes,
            defaultMode = obj.stringOrDefault("defaultMode", modes.firstOrNull().orEmpty()),
            vars = obj["vars"].asObject().mapNotNull { (varId, variable) ->
                readVariable(variable, "$pointer/vars/$varId")?.let { varId to it }
            }.toMap(),
        )
    }

    private fun readVariable(element: JsonElement, pointer: String): DesignVariable? {
        val obj = element as? JsonObject ?: return null
        val type = readVariableType(obj["type"], "$pointer/type")
        return DesignVariable(
            type = type,
            values = obj["values"].asObject().mapNotNull { (mode, value) ->
                readVariableValue(value, type, "$pointer/values/$mode")?.let { mode to it }
            }.toMap(),
        )
    }

    fun readVariableType(element: JsonElement?, pointer: String): VariableType =
        readEnum(
            element, pointer, VariableType.Text,
            mapOf(
                "color" to VariableType.Color,
                "number" to VariableType.Number,
                "string" to VariableType.Text,
                "boolean" to VariableType.Bool,
            ),
        )

    fun readVariableValue(element: JsonElement, type: VariableType, pointer: String): VariableValue? {
        if (element is JsonObject) {
            val alias = (element["\$var"] as? JsonPrimitive)?.takeIf { it.isString }?.content
            if (alias != null) return VariableValue.Alias(alias)
            warn(pointer, "Variable value object must be an alias {\"\$var\": id}")
            return null
        }
        val primitive = element as? JsonPrimitive ?: return null
        return when (type) {
            VariableType.Color -> DesignColor.fromHex(primitive.content)?.let { VariableValue.ColorValue(it) }
                ?: run {
                    warn(pointer, "Invalid color '${primitive.content}'")
                    null
                }
            VariableType.Number -> primitive.doubleOrNull?.let { VariableValue.NumberValue(it) }
            VariableType.Text -> VariableValue.TextValue(primitive.content)
            VariableType.Bool -> primitive.booleanOrNull?.let { VariableValue.BoolValue(it) }
        }
    }

    private fun readAsset(element: JsonElement): DesignAsset {
        val obj = element.asObject()
        return DesignAsset(
            type = obj.stringOrDefault("type", "image"),
            hash = obj.stringOrDefault("hash"),
            url = obj.stringOrDefault("url"),
            width = (obj["width"] as? JsonPrimitive)?.doubleOrNull,
            height = (obj["height"] as? JsonPrimitive)?.doubleOrNull,
        )
    }

    // --- Shared geometry ----------------------------------------------------

    fun readPoint(obj: JsonObject, pointer: String): DesignPoint =
        DesignPoint(
            x = readBindableDouble(obj["x"], 0.0),
            y = readBindableDouble(obj["y"], 0.0),
        )

    fun readSize(element: JsonElement?, pointer: String): DesignSize {
        val obj = element as? JsonObject ?: return DesignSize()
        warnIfBinding(obj["width"], "$pointer/width")
        warnIfBinding(obj["height"], "$pointer/height")
        return DesignSize(
            width = (obj["width"] as? JsonPrimitive)?.doubleOrNull,
            height = (obj["height"] as? JsonPrimitive)?.doubleOrNull,
        )
    }

    /** Reads `{"file": ..., "line": ...}` into a [SourceLocation] anchored at [pointer]. */
    fun readSourceLocation(element: JsonElement?, pointer: String): SourceLocation? {
        val obj = element as? JsonObject ?: return null
        return SourceLocation(
            pointer = pointer,
            file = obj.stringOrDefault("file"),
            line = obj.intOrDefault("line", 0),
        )
    }

    // --- Bindable scalars -------------------------------------------------

    fun readBindableDouble(element: JsonElement?, fallback: Double): Bindable<Double> =
        readBindable(element) { primitive -> primitive.doubleOrNull } ?: fallback.bindable()

    fun readBindableInt(element: JsonElement?, fallback: Int): Bindable<Int> =
        readBindable(element) { primitive -> primitive.intOrNull } ?: fallback.bindable()

    fun readBindableString(element: JsonElement?, fallback: String): Bindable<String> =
        readBindable(element) { primitive -> primitive.content.takeIf { primitive.isString } }
            ?: fallback.bindable()

    fun readBindableBoolean(element: JsonElement?, fallback: Boolean): Bindable<Boolean> =
        readBindable(element) { primitive -> primitive.booleanOrNull } ?: fallback.bindable()

    fun readBindableColor(element: JsonElement?, pointer: String): Bindable<DesignColor> {
        val bound = readBindable(element) { primitive ->
            DesignColor.fromHex(primitive.content)
        }
        if (bound == null && element != null) {
            warn(pointer, "Invalid color value")
        }
        return bound ?: DesignColor.Black.bindable()
    }

    private fun <T> readBindable(element: JsonElement?, convert: (JsonPrimitive) -> T?): Bindable<T>? =
        when (element) {
            is JsonObject -> {
                val varRef = (element["\$var"] as? JsonPrimitive)?.takeIf { it.isString }?.content
                val propRef = (element["\$prop"] as? JsonPrimitive)?.takeIf { it.isString }?.content
                val dataRef = (element["\$data"] as? JsonPrimitive)?.takeIf { it.isString }?.content
                when {
                    varRef != null -> Bindable.VarRef(varRef)
                    propRef != null -> Bindable.PropRef(propRef)
                    dataRef != null -> Bindable.DataRef(DesignExpression(dataRef.stripMustache()))
                    else -> null
                }
            }
            is JsonPrimitive -> convert(element)?.let { Bindable.Value(it) }
            else -> null
        }

    // --- JSON helpers -----------------------------------------------------

    fun JsonObject.isBindingRef(): Boolean =
        containsKey("\$var") || containsKey("\$prop") || containsKey("\$data")

    fun JsonElement?.asObject(): Map<String, JsonElement> =
        (this as? JsonObject) ?: emptyMap()

    fun JsonElement?.asArray(pointer: String): List<JsonElement> =
        when (this) {
            null -> emptyList()
            is JsonArray -> this
            else -> {
                warn(pointer, "Expected an array")
                emptyList()
            }
        }

    fun JsonElement.asStringOrEmpty(): String =
        (this as? JsonPrimitive)?.content.orEmpty()

    fun Map<String, JsonElement>.stringOrDefault(key: String, default: String = ""): String =
        ((this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content) ?: default

    fun Map<String, JsonElement>.doubleOrDefault(key: String, default: Double): Double =
        (this[key] as? JsonPrimitive)?.doubleOrNull ?: default

    fun Map<String, JsonElement>.intOrDefault(key: String, default: Int): Int =
        (this[key] as? JsonPrimitive)?.intOrNull ?: default

    fun Map<String, JsonElement>.booleanOrDefault(key: String, default: Boolean): Boolean =
        (this[key] as? JsonPrimitive)?.booleanOrNull ?: default

    /** Reads a scalar that does NOT support bindings, warning instead of silently dropping one. */
    fun Map<String, JsonElement>.plainDouble(key: String, pointer: String, default: Double): Double {
        warnIfBinding(this[key], "$pointer/$key")
        return (this[key] as? JsonPrimitive)?.doubleOrNull ?: default
    }

    fun warnIfBinding(element: JsonElement?, pointer: String) {
        if (element is JsonObject && element.isBindingRef()) {
            warn(pointer, "Bindings are not supported for this field; using default")
        }
    }

    /** Strips an optional `{{...}}` wrapper, leaving the raw expression. */
    fun String.stripMustache(): String {
        val trimmed = trim()
        return if (trimmed.startsWith("{{") && trimmed.endsWith("}}")) {
            trimmed.removePrefix("{{").removeSuffix("}}").trim()
        } else {
            trimmed
        }
    }

    fun String.isMustache(): Boolean {
        val trimmed = trim()
        return trimmed.startsWith("{{") && trimmed.endsWith("}}")
    }

    fun <T> readEnum(
        element: JsonElement?,
        pointer: String,
        fallback: T,
        values: Map<String, T>,
    ): T {
        val raw = (element as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return fallback
        val match = values[raw]
        if (match == null) {
            warn(pointer, "Unknown value '$raw', using fallback")
            return fallback
        }
        return match
    }

    fun warn(pointer: String, message: String) {
        diagnostics += DesignDiagnostic(DesignSeverity.Warning, message, SourceLocation(pointer))
    }

    fun error(pointer: String, message: String) {
        diagnostics += DesignDiagnostic(DesignSeverity.Error, message, SourceLocation(pointer))
    }
}
