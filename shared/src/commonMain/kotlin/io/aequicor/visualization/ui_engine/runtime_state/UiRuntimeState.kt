package io.aequicor.visualization.ui_engine.runtime_state

import io.aequicor.visualization.ui_engine.ui_document_ir.UiDiagnostic
import io.aequicor.visualization.ui_engine.ui_document_ir.UiDocument
import io.aequicor.visualization.ui_engine.validator.UiLoadResult
import io.aequicor.visualization.ui_engine.ui_document_ir.UiNode
import io.aequicor.visualization.ui_engine.ui_document_ir.UiPrompt
import io.aequicor.visualization.ui_engine.ui_document_ir.UiScenario
import io.aequicor.visualization.ui_engine.ui_document_ir.UiScreen
import kotlinx.serialization.Serializable

@Serializable
sealed interface UiCommand {
    @Serializable
    data class LoadYaml(val source: String) : UiCommand

    @Serializable
    data class SelectScreen(val screenId: String) : UiCommand

    @Serializable
    data class SelectNode(val nodeId: String) : UiCommand

    @Serializable
    data class SelectTarget(val targetId: String) : UiCommand

    @Serializable
    data class SelectScenario(val scenarioId: String) : UiCommand

    @Serializable
    data class UpdateInputValue(val nodeId: String, val value: String) : UiCommand

    @Serializable
    data class UpdateDraftComment(val body: String) : UiCommand

    @Serializable
    data class AddComment(val targetId: String = "") : UiCommand

    @Serializable
    data class GeneratePrompt(val targetId: String = "") : UiCommand
}

data class UiVisualizationState(
    val source: String,
    val loadResult: UiLoadResult,
    val selectedScreenId: String = "",
    val selectedNodeId: String = "",
    val selectedScenarioId: String = "",
    val draftComment: String = "",
    val inputValues: Map<String, String> = emptyMap(),
    val latestPrompt: UiPrompt? = null,
) {
    val documentOrNull: UiDocument?
        get() = (loadResult as? UiLoadResult.Success)?.document

    val diagnostics: List<UiDiagnostic>
        get() = when (loadResult) {
            is UiLoadResult.Failure -> loadResult.diagnostics
            is UiLoadResult.Success -> loadResult.diagnostics
        }

    val selectedScreen: UiScreen?
        get() = documentOrNull?.screenById(selectedScreenId)

    val selectedNode: UiNode?
        get() = documentOrNull?.nodeById(selectedNodeId)

    val selectedScenario: UiScenario?
        get() = documentOrNull?.scenarios?.firstOrNull { it.id == selectedScenarioId }
            ?: documentOrNull?.scenarios?.firstOrNull()
}

