package io.aequicor.visualization.mission

import kotlinx.serialization.Serializable

@Serializable
data class MissionSpec(
    val version: Int = 1,
    val title: String = "Untitled mission",
    val description: String = "",
    val theme: MissionTheme = MissionTheme(),
    val screens: List<MissionScreen> = emptyList(),
    val scenarios: List<MissionScenario> = emptyList(),
    val comments: List<ComponentComment> = emptyList(),
    val prompts: List<DesignPrompt> = emptyList(),
) {
    fun screenById(id: String?): MissionScreen? =
        screens.firstOrNull { it.id == id } ?: screens.firstOrNull()

    fun componentById(id: String?): MissionComponent? =
        id?.let { componentId -> screens.firstNotNullOfOrNull { it.findComponent(componentId) } }

    fun findTarget(targetId: String): MissionTarget? {
        if (targetId.isBlank()) return null
        screens.firstOrNull { it.id == targetId }?.let { screen ->
            return MissionTarget(targetId = targetId, screen = screen, component = null)
        }
        screens.forEach { screen ->
            screen.findComponent(targetId)?.let { component ->
                return MissionTarget(targetId = targetId, screen = screen, component = component)
            }
        }
        return null
    }

    fun commentsFor(targetId: String): List<ComponentComment> =
        comments.filter { it.targetId == targetId }
}

data class MissionTarget(
    val targetId: String,
    val screen: MissionScreen,
    val component: MissionComponent?,
)

@Serializable
data class MissionTheme(
    val name: String = "Lazurite",
    val primary: String = "#1F5FA8",
    val accent: String = "#2BB8A8",
    val surface: String = "#F6FAFF",
    val mood: String = "precise, calm, product-focused",
)

@Serializable
data class MissionScreen(
    val id: String,
    val title: String,
    val description: String = "",
    val components: List<MissionComponent> = emptyList(),
) {
    fun findComponent(componentId: String): MissionComponent? =
        components.firstNotNullOfOrNull { it.findById(componentId) }
}

@Serializable
data class MissionComponent(
    val id: String,
    val type: String,
    val title: String = "",
    val text: String = "",
    val placeholder: String = "",
    val description: String = "",
    val variant: String = "",
    val items: List<String> = emptyList(),
    val children: List<MissionComponent> = emptyList(),
    val properties: Map<String, String> = emptyMap(),
) {
    fun findById(componentId: String): MissionComponent? {
        if (id == componentId) return this
        return children.firstNotNullOfOrNull { it.findById(componentId) }
    }
}

@Serializable
data class MissionScenario(
    val id: String,
    val title: String,
    val summary: String = "",
    val steps: List<ScenarioStep> = emptyList(),
)

@Serializable
data class ScenarioStep(
    val id: String = "",
    val screenId: String,
    val componentId: String = "",
    val action: String,
    val expectation: String = "",
    val note: String = "",
)

@Serializable
data class ComponentComment(
    val id: String,
    val targetId: String,
    val author: String = "agent",
    val body: String,
    val createdAt: String = "",
)

@Serializable
data class DesignPrompt(
    val id: String,
    val targetId: String,
    val title: String,
    val body: String,
    val createdAt: String = "",
)

data class MissionMarkdownDocument(
    val originalMarkdown: String,
    val yaml: String,
    val fenceStartLine: Int,
    val fenceEndLine: Int,
)

sealed interface MissionParseResult {
    data class Success(
        val spec: MissionSpec,
        val document: MissionMarkdownDocument,
        val warnings: List<MissionParseMessage> = emptyList(),
    ) : MissionParseResult

    data class Failure(
        val errors: List<MissionParseMessage>,
    ) : MissionParseResult
}

data class MissionParseMessage(
    val line: Int,
    val message: String,
)

@Serializable
sealed interface VisualizationCommand {
    @Serializable
    data class LoadDocument(val markdown: String) : VisualizationCommand

    @Serializable
    data class SelectScreen(val screenId: String) : VisualizationCommand

    @Serializable
    data class SelectComponent(val componentId: String) : VisualizationCommand

    @Serializable
    data class SelectTarget(val targetId: String) : VisualizationCommand

    @Serializable
    data class SelectScenario(val scenarioId: String) : VisualizationCommand

    @Serializable
    data class UpdateInputValue(val componentId: String, val value: String) : VisualizationCommand

    @Serializable
    data class UpdateDraftComment(val body: String) : VisualizationCommand

    @Serializable
    data class AddComment(val targetId: String = "") : VisualizationCommand

    @Serializable
    data class GeneratePrompt(val targetId: String = "") : VisualizationCommand

    @Serializable
    data object ExportDocument : VisualizationCommand
}

data class MissionVisualizationState(
    val markdown: String,
    val parseResult: MissionParseResult,
    val selectedScreenId: String = "",
    val selectedComponentId: String = "",
    val selectedScenarioId: String = "",
    val draftComment: String = "",
    val inputValues: Map<String, String> = emptyMap(),
    val latestPrompt: DesignPrompt? = null,
    val exportedMarkdown: String = "",
) {
    val specOrNull: MissionSpec?
        get() = (parseResult as? MissionParseResult.Success)?.spec

    val warnings: List<MissionParseMessage>
        get() = (parseResult as? MissionParseResult.Success)?.warnings.orEmpty()

    val selectedScreen: MissionScreen?
        get() = specOrNull?.screenById(selectedScreenId)

    val selectedComponent: MissionComponent?
        get() = specOrNull?.componentById(selectedComponentId)

    val selectedScenario: MissionScenario?
        get() = specOrNull?.scenarios?.firstOrNull { it.id == selectedScenarioId }
            ?: specOrNull?.scenarios?.firstOrNull()
}

val KnownMissionComponentTypes: Set<String> = setOf(
    "screen",
    "section",
    "text",
    "button",
    "input",
    "card",
    "list",
    "tabs",
    "topBar",
    "bottomBar",
    "imagePlaceholder",
    "form",
)

fun isKnownMissionComponentType(type: String): Boolean =
    type in KnownMissionComponentTypes
