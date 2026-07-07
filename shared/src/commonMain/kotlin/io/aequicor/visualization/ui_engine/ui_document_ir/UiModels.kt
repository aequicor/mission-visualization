package io.aequicor.visualization.ui_engine.ui_document_ir

import kotlinx.serialization.Serializable

@Serializable
data class UiDocument(
    val version: Int = 1,
    val title: String = "Untitled UI document",
    val description: String = "",
    val metadata: Map<String, String> = emptyMap(),
    val theme: UiTheme = UiTheme(),
    val screens: List<UiScreen> = emptyList(),
    val scenarios: List<UiScenario> = emptyList(),
    val comments: List<UiComment> = emptyList(),
    val prompts: List<UiPrompt> = emptyList(),
    val source: SourceSpan? = null,
) {
    fun screenById(id: String?): UiScreen? =
        screens.firstOrNull { it.id == id } ?: screens.firstOrNull()

    fun nodeById(id: String?): UiNode? =
        id?.let { nodeId -> screens.firstNotNullOfOrNull { it.findNode(nodeId) } }

    fun findTarget(targetId: String): UiTarget? {
        if (targetId.isBlank()) return null
        screens.firstOrNull { it.id == targetId }?.let { screen ->
            return UiTarget(targetId = targetId, screen = screen, node = null)
        }
        screens.forEach { screen ->
            screen.findNode(targetId)?.let { node ->
                return UiTarget(targetId = targetId, screen = screen, node = node)
            }
        }
        return null
    }

    fun commentsFor(targetId: String): List<UiComment> =
        comments.filter { it.targetId == targetId }

    fun allTargets(): Set<String> =
        screens.flatMap { screen -> listOf(screen.id) + screen.allNodes().map { it.id } }
            .filter { it.isNotBlank() }
            .toSet()
}

data class UiTarget(
    val targetId: String,
    val screen: UiScreen,
    val node: UiNode?,
)

@Serializable
data class UiTheme(
    val name: String = "Lazurite",
    val primary: String = "#1F5FA8",
    val accent: String = "#2BB8A8",
    val surface: String = "#F6FAFF",
    val mood: String = "precise, calm, product-focused",
    val tokens: Map<String, String> = emptyMap(),
)

@Serializable
data class UiScreen(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val layout: UiLayout = UiLayout(),
    val children: List<UiNode> = emptyList(),
    val source: SourceSpan? = null,
) {
    fun findNode(nodeId: String): UiNode? =
        children.firstNotNullOfOrNull { it.findById(nodeId) }

    fun allNodes(): List<UiNode> =
        children.flatMap { listOf(it) + it.allDescendants() }
}

@Serializable
data class UiNode(
    val id: String = "",
    val type: String = "",
    val props: Map<String, UiValue> = emptyMap(),
    val layout: UiLayout = UiLayout(),
    val style: UiStyle = UiStyle(),
    val actions: List<UiAction> = emptyList(),
    val children: List<UiNode> = emptyList(),
    val source: SourceSpan? = null,
) {
    fun findById(nodeId: String): UiNode? {
        if (id == nodeId) return this
        return children.firstNotNullOfOrNull { it.findById(nodeId) }
    }

    fun allDescendants(): List<UiNode> =
        children.flatMap { listOf(it) + it.allDescendants() }
}

@Serializable
sealed interface UiValue {
    @Serializable
    data class StringValue(val value: String) : UiValue

    @Serializable
    data class NumberValue(val value: Double) : UiValue

    @Serializable
    data class BooleanValue(val value: Boolean) : UiValue

    @Serializable
    data class ListValue(val value: List<UiValue>) : UiValue

    @Serializable
    data class ObjectValue(val value: Map<String, UiValue>) : UiValue

    @Serializable
    data object NullValue : UiValue
}

@Serializable
data class UiLayout(
    val type: String = "column",
    val padding: String = "",
    val gap: String = "",
    val width: String = "",
    val height: String = "",
    val weight: Float? = null,
    val align: String = "",
    val columns: Int? = null,
)

@Serializable
data class UiStyle(
    val tone: String = "",
    val variant: String = "",
    val size: String = "",
    val emphasis: String = "",
    val color: String = "",
    val background: String = "",
)

@Serializable
data class UiAction(
    val type: String = "none",
    val target: String = "",
    val value: String = "",
    val source: SourceSpan? = null,
)

@Serializable
data class UiScenario(
    val id: String = "",
    val title: String = "",
    val summary: String = "",
    val steps: List<UiScenarioStep> = emptyList(),
    val source: SourceSpan? = null,
)

@Serializable
data class UiScenarioStep(
    val id: String = "",
    val screenId: String = "",
    val nodeId: String = "",
    val action: String = "",
    val expectation: String = "",
    val note: String = "",
    val source: SourceSpan? = null,
)

@Serializable
data class UiComment(
    val id: String = "",
    val targetId: String = "",
    val author: String = "agent",
    val body: String = "",
    val createdAt: String = "",
    val source: SourceSpan? = null,
)

@Serializable
data class UiPrompt(
    val id: String = "",
    val targetId: String = "",
    val title: String = "Design prompt",
    val body: String = "",
    val createdAt: String = "",
    val source: SourceSpan? = null,
)

@Serializable
data class SourceSpan(
    val line: Int,
    val column: Int = 1,
)

@Serializable
enum class UiDiagnosticSeverity {
    Error,
    Warning,
}

@Serializable
data class UiDiagnostic(
    val severity: UiDiagnosticSeverity,
    val message: String,
    val source: SourceSpan = SourceSpan(1, 1),
)

val KnownUiNodeTypes: Set<String> = setOf(
    "screen",
    "section",
    "box",
    "text",
    "button",
    "input",
    "form",
    "card",
    "list",
    "tabs",
    "imagePlaceholder",
    "dataTable",
    "dialog",
    "sidebar",
    "timeline",
    "badge",
    "menu",
    "topBar",
    "bottomBar",
    "emptyState",
)

fun UiNode.propString(key: String, fallback: String = ""): String =
    when (val value = props[key]) {
        is UiValue.StringValue -> value.value
        is UiValue.NumberValue -> value.value.toString().trimEnd('0').trimEnd('.')
        is UiValue.BooleanValue -> value.value.toString()
        else -> fallback
    }

fun UiNode.propList(key: String): List<String> =
    when (val value = props[key]) {
        is UiValue.ListValue -> value.value.mapNotNull { item ->
            when (item) {
                is UiValue.StringValue -> item.value
                is UiValue.NumberValue -> item.value.toString().trimEnd('0').trimEnd('.')
                is UiValue.BooleanValue -> item.value.toString()
                else -> null
            }
        }
        is UiValue.StringValue -> value.value.split('|').map { it.trim() }.filter { it.isNotBlank() }
        else -> emptyList()
    }

fun UiNode.title(fallback: String = id): String =
    propString("title").ifBlank { propString("text").ifBlank { fallback } }

fun isKnownUiNodeType(type: String): Boolean =
    type in KnownUiNodeTypes

fun stableUiId(prefix: String, targetId: String, content: String): String {
    var hash = 17
    val source = "$prefix|$targetId|$content"
    for (char in source) {
        hash = hash * 31 + char.code
    }
    val positive = (hash.toLong() and 0xffffffffL).toString(16)
    return "$prefix-$targetId-$positive"
}
