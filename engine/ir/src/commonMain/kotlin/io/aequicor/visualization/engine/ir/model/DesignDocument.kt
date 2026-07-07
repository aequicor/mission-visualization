package io.aequicor.visualization.engine.ir.model

/**
 * Root of a Figma-like design document. Top-level dictionaries map id -> entity;
 * nodes reference them by id instead of duplicating content.
 */
data class DesignDocument(
    /** The parser accepts both legacy "1.x" and "slm-ir/1.x". */
    val schemaVersion: String = "slm-ir/1.0",
    val id: String = "",
    val name: String = "",
    val pages: List<DesignPage> = emptyList(),
    val components: Map<String, DesignComponent> = emptyMap(),
    val componentSets: Map<String, DesignComponentSet> = emptyMap(),
    val styles: Map<String, DesignStyle> = emptyMap(),
    val variables: DesignVariables = DesignVariables(),
    val assets: Map<String, DesignAsset> = emptyMap(),
    val screen: DesignScreenMeta? = null,
    val libraries: List<DesignLibrary> = emptyList(),
    val breakpoints: List<DesignBreakpoint> = emptyList(),
    val devicePresets: List<DevicePreset> = emptyList(),
    val prototypeVariables: Map<String, PrototypeVariable> = emptyMap(),
    val actionSets: Map<String, List<DesignAction>> = emptyMap(),
    val i18n: DesignI18n = DesignI18n(),
    val handoff: DesignHandoff = DesignHandoff(),
    /** Motion ref -> asset id. */
    val motionRefs: Map<String, String> = emptyMap(),
) {
    fun pageById(pageId: String?): DesignPage? =
        pages.firstOrNull { it.id == pageId } ?: pages.firstOrNull()

    fun nodeById(nodeId: String?): DesignNode? =
        nodeId?.let { id -> pages.firstNotNullOfOrNull { page -> page.findNode(id) } }

    fun pageOfNode(nodeId: String): DesignPage? =
        pages.firstOrNull { it.findNode(nodeId) != null }

    fun updateNode(nodeId: String, transform: (DesignNode) -> DesignNode): DesignDocument =
        copy(
            pages = pages.map { page ->
                page.copy(children = page.children.map { it.updateNode(nodeId, transform) })
            },
        )
}

data class DesignPage(
    val id: String = "",
    val name: String = "",
    val children: List<DesignNode> = emptyList(),
) {
    fun findNode(nodeId: String): DesignNode? =
        children.firstNotNullOfOrNull { it.findById(nodeId) }

    fun allNodes(): List<DesignNode> =
        children.flatMap { listOf(it) + it.allDescendants() }
}

/** Screen-level metadata from the SLM front matter. */
data class DesignScreenMeta(
    val id: String = "",
    val name: String = "",
    val page: String = "",
    /** Default dimension values, e.g. "theme" -> "light". */
    val modes: Map<String, String> = emptyMap(),
    val frame: FramePreset? = null,
    val canvas: CanvasPlacement? = null,
    val flow: DesignFlow? = null,
)

data class FramePreset(
    val preset: String = "",
    val width: Double? = null,
    val height: Double? = null,
)

data class CanvasPlacement(
    val section: String = "",
    val x: Double = 0.0,
    val y: Double = 0.0,
)

data class DesignFlow(
    val id: String = "",
    val node: String = "",
    val next: List<String> = emptyList(),
)

data class DesignLibrary(
    val id: String,
    val source: String = "",
)

data class DesignBreakpoint(
    val id: String,
    val minWidth: Double? = null,
    val maxWidth: Double? = null,
)

data class DevicePreset(
    val id: String,
    val width: Double,
    val height: Double,
    val platform: String = "",
)

/** Prototype-only variable; state lives in the interaction runtime, not in collections. */
data class PrototypeVariable(
    val type: VariableType,
    val default: VariableValue? = null,
)

/** A component is a template node plus declared properties wired via `{"$prop": name}`. */
data class DesignComponent(
    val name: String = "",
    /** "" = local component. */
    val libraryId: String = "",
    val properties: Map<String, ComponentPropertyDefinition> = emptyMap(),
    /** Id paths of nested instances exposed for direct overriding. */
    val exposedInstances: List<List<String>> = emptyList(),
    val root: DesignNode,
)

data class ComponentPropertyDefinition(
    val type: ComponentPropertyType,
    val default: PropValue? = null,
    /** For instanceSwap: preferred component refs. */
    val preferredValues: List<String> = emptyList(),
    /** For slot props. */
    val minItems: Int? = null,
    val maxItems: Int? = null,
    val allowedContent: List<String> = emptyList(),
)

enum class ComponentPropertyType { Text, Boolean, InstanceSwap, Variant, Slot, Number, RawString, DataBinding }

/** Variants of one component grouped by axes, e.g. `kind=primary,size=m` -> componentId. */
data class DesignComponentSet(
    val name: String = "",
    val axes: Map<String, List<String>> = emptyMap(),
    val variants: Map<String, String> = emptyMap(),
) {
    fun resolveVariant(selection: Map<String, String>): String? {
        val requested = axes.keys.associateWith { axis ->
            selection[axis] ?: axes[axis]?.firstOrNull().orEmpty()
        }
        return variants.entries.firstOrNull { (key, _) ->
            parseVariantKey(key) == requested
        }?.value ?: variants.values.firstOrNull()
    }

    companion object {
        fun parseVariantKey(key: String): Map<String, String> =
            key.split(',').mapNotNull { part ->
                val pieces = part.split('=', limit = 2)
                if (pieces.size == 2) pieces[0].trim() to pieces[1].trim() else null
            }.toMap()
    }
}

/** Shared style referenced through `textStyleId | fillStyleId | strokeStyleId | effectStyleId | gridStyleId`. */
sealed interface DesignStyle {
    data class Text(val value: DesignTextStyle) : DesignStyle

    data class Paint(val value: List<DesignPaint>) : DesignStyle

    data class Effect(val value: List<DesignEffect>) : DesignStyle

    data class Grid(val value: List<LayoutGridDefinition>) : DesignStyle
}

data class DesignVariables(
    val collections: Map<String, VariableCollection> = emptyMap(),
) {
    fun findVariable(varId: String): Pair<String, DesignVariable>? =
        collections.firstNotNullOfOrNull { (collectionId, collection) ->
            collection.vars[varId]?.let { collectionId to it }
        }
}

data class VariableCollection(
    val name: String = "",
    val modes: List<String> = emptyList(),
    val defaultMode: String = "",
    val vars: Map<String, DesignVariable> = emptyMap(),
)

data class DesignVariable(
    val type: VariableType,
    val values: Map<String, VariableValue> = emptyMap(),
)

enum class VariableType { Color, Number, Text, Bool }

/** A mode value; may itself alias another variable (primitives -> semantic tokens). */
sealed interface VariableValue {
    data class ColorValue(val value: DesignColor) : VariableValue

    data class NumberValue(val value: Double) : VariableValue

    data class TextValue(val value: String) : VariableValue

    data class BoolValue(val value: Boolean) : VariableValue

    data class Alias(val varId: String) : VariableValue
}

data class DesignAsset(
    /** "image" | "video" | "svg" | "motion". */
    val type: String = "image",
    val hash: String = "",
    val url: String = "",
    /** Intrinsic size, used for Hug sizing of media nodes. */
    val width: Double? = null,
    val height: Double? = null,
)
