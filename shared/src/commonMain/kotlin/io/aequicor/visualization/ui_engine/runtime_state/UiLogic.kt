package io.aequicor.visualization.ui_engine.runtime_state

import io.aequicor.visualization.ui_engine.mv_yaml_source.SampleUiYaml
import io.aequicor.visualization.ui_engine.ui_document_ir.UiComment
import io.aequicor.visualization.ui_engine.ui_document_ir.UiDocument
import io.aequicor.visualization.ui_engine.validator.UiLoadResult
import io.aequicor.visualization.ui_engine.ui_document_ir.UiPrompt
import io.aequicor.visualization.ui_engine.ui_document_ir.UiScenario
import io.aequicor.visualization.ui_engine.ui_document_ir.UiValue
import io.aequicor.visualization.ui_engine.ui_document_ir.stableUiId
import io.aequicor.visualization.ui_engine.ui_document_ir.title
import io.aequicor.visualization.ui_engine.validator.loadUiDocument

fun createUiVisualizationState(source: String = SampleUiYaml): UiVisualizationState {
    val loadResult = loadUiDocument(source)
    val document = (loadResult as? UiLoadResult.Success)?.document
    val firstScreen = document?.screens?.firstOrNull()
    return UiVisualizationState(
        source = source,
        loadResult = loadResult,
        selectedScreenId = firstScreen?.id.orEmpty(),
        selectedNodeId = firstScreen?.children?.firstOrNull()?.id.orEmpty(),
        selectedScenarioId = document?.scenarios?.firstOrNull()?.id.orEmpty(),
    )
}

fun reduceUiVisualization(
    state: UiVisualizationState,
    command: UiCommand,
): UiVisualizationState =
    when (command) {
        is UiCommand.LoadYaml -> {
            val loadResult = loadUiDocument(command.source)
            val document = (loadResult as? UiLoadResult.Success)?.document
            val firstScreen = document?.screens?.firstOrNull()
            state.copy(
                source = command.source,
                loadResult = loadResult,
                selectedScreenId = firstScreen?.id.orEmpty(),
                selectedNodeId = firstScreen?.children?.firstOrNull()?.id.orEmpty(),
                selectedScenarioId = document?.scenarios?.firstOrNull()?.id.orEmpty(),
                draftComment = "",
                inputValues = emptyMap(),
                latestPrompt = null,
            )
        }
        is UiCommand.SelectScreen -> {
            val screen = state.documentOrNull?.screenById(command.screenId)
            state.copy(
                selectedScreenId = screen?.id ?: command.screenId,
                selectedNodeId = screen?.children?.firstOrNull()?.id.orEmpty(),
            )
        }
        is UiCommand.SelectNode -> selectTarget(state, command.nodeId)
        is UiCommand.SelectTarget -> selectTarget(state, command.targetId)
        is UiCommand.SelectScenario -> state.copy(selectedScenarioId = command.scenarioId)
        is UiCommand.UpdateInputValue -> state.copy(
            inputValues = state.inputValues + (command.nodeId to command.value),
        )
        is UiCommand.UpdateDraftComment -> state.copy(draftComment = command.body)
        is UiCommand.AddComment -> addComment(state, command.targetId)
        is UiCommand.GeneratePrompt -> generatePrompt(state, command.targetId)
    }

private fun selectTarget(
    state: UiVisualizationState,
    targetId: String,
): UiVisualizationState {
    val target = state.documentOrNull?.findTarget(targetId)
        ?: return state.copy(selectedNodeId = targetId)
    return state.copy(
        selectedScreenId = target.screen.id,
        selectedNodeId = target.node?.id ?: target.screen.children.firstOrNull()?.id.orEmpty(),
    )
}

private fun addComment(
    state: UiVisualizationState,
    explicitTargetId: String,
): UiVisualizationState {
    val document = state.documentOrNull ?: return state
    val body = state.draftComment.trim()
    val targetId = explicitTargetId.ifBlank { state.selectedNodeId.ifBlank { state.selectedScreenId } }
    if (body.isEmpty() || targetId.isBlank()) return state

    val comment = UiComment(
        id = stableUiId("comment", targetId, body),
        targetId = targetId,
        body = body,
    )
    val updatedDocument = document.copy(comments = document.comments + comment)
    return state.copy(
        loadResult = UiLoadResult.Success(updatedDocument, state.diagnostics),
        draftComment = "",
    )
}

private fun generatePrompt(
    state: UiVisualizationState,
    explicitTargetId: String,
): UiVisualizationState {
    val document = state.documentOrNull ?: return state
    val targetId = explicitTargetId.ifBlank { state.selectedNodeId.ifBlank { state.selectedScreenId } }
    val prompt = if (targetId.isBlank() || targetId == "global") {
        buildGlobalUiPrompt(document, state.selectedScenario)
    } else {
        buildUiDesignPrompt(document, targetId, state.selectedScenario)
    }
    val updatedDocument = document.copy(
        prompts = document.prompts.filterNot { it.id == prompt.id } + prompt,
    )
    return state.copy(
        loadResult = UiLoadResult.Success(updatedDocument, state.diagnostics),
        latestPrompt = prompt,
    )
}

fun buildGlobalUiPrompt(
    document: UiDocument,
    scenario: UiScenario? = null,
): UiPrompt {
    val screenMap = document.screens.joinToString(separator = "\n") { screen ->
        val nodes = screen.allNodes()
            .joinToString(separator = ", ") { "${it.type}:${it.id}" }
            .ifBlank { "no nodes" }
        "- ${screen.title} (${screen.id}): $nodes"
    }

    val scenarioContext = scenario?.steps
        ?.joinToString(separator = "\n") { step ->
            val target = step.nodeId.ifBlank { step.screenId }
            "- [$target] ${step.action}${step.expectation.takeIf { it.isNotBlank() }?.let { " -> $it" }.orEmpty()}"
        }
        .orEmpty()

    val commentsBlock = if (document.comments.isEmpty()) {
        "- No comments yet."
    } else {
        document.comments.joinToString(separator = "\n") { comment ->
            val target = document.findTarget(comment.targetId)
            val screenTitle = target?.screen?.title ?: "Unknown screen"
            val nodeLabel = target?.node?.let { node -> " / ${node.type} '${node.title()}'" }.orEmpty()
            "- ${screenTitle}${nodeLabel} (${comment.targetId}): ${comment.body}"
        }
    }

    val body = buildString {
        appendLine("You are updating the complete UI visualization document.")
        appendLine("Design tone: ${document.theme.name} (${document.theme.mood}); primary ${document.theme.primary}, accent ${document.theme.accent}.")
        appendLine("Document: ${document.title}.")
        if (document.description.isNotBlank()) appendLine("Intent: ${document.description}.")
        appendLine("Screens and nodes:")
        appendLine(screenMap)
        if (scenarioContext.isNotBlank()) {
            appendLine("Scenario context:")
            appendLine(scenarioContext)
        }
        appendLine("Comments to address:")
        appendLine(commentsBlock)
        appendLine("Return one cohesive design patch covering layout, component states, tokens, and copy changes.")
    }.trim()

    return UiPrompt(
        id = stableUiId("prompt", "global", body),
        targetId = "global",
        title = "Revise complete UI document",
        body = body,
    )
}

fun buildUiDesignPrompt(
    document: UiDocument,
    targetId: String,
    scenario: UiScenario? = null,
): UiPrompt {
    val target = document.findTarget(targetId)
    val screen = target?.screen
    val node = target?.node
    val comments = document.comments.filter { it.targetId == targetId }
    val scenarioContext = scenario?.steps
        ?.filter { it.screenId == screen?.id || it.nodeId == targetId }
        ?.joinToString(separator = "\n") { step ->
            "- ${step.action}${step.expectation.takeIf { it.isNotBlank() }?.let { " -> $it" }.orEmpty()}"
        }
        .orEmpty()
    val targetLabel = node?.title()
        ?: screen?.title
        ?: targetId
    val propsBlock = node?.props?.entries
        ?.joinToString(separator = "\n") { (key, value) -> "- $key: ${value.renderForPrompt()}" }
        .orEmpty()
    val commentsBlock = comments.joinToString(separator = "\n") { "- ${it.body}" }
        .ifBlank { "- No comments yet." }

    val body = buildString {
        appendLine("You are updating a UI visualization target.")
        appendLine("Design tone: ${document.theme.name} (${document.theme.mood}); primary ${document.theme.primary}, accent ${document.theme.accent}.")
        appendLine("Document: ${document.title}.")
        if (screen != null) appendLine("Screen: ${screen.title} (${screen.id}).")
        if (node != null) {
            appendLine("Node: ${node.type} '$targetLabel' (${node.id}).")
            appendLine("Layout: ${node.layout.type}, gap=${node.layout.gap.ifBlank { "default" }}, padding=${node.layout.padding.ifBlank { "default" }}.")
            appendLine("Style: variant=${node.style.variant.ifBlank { "default" }}, tone=${node.style.tone.ifBlank { "default" }}.")
            if (propsBlock.isNotBlank()) {
                appendLine("Props:")
                appendLine(propsBlock)
            }
        } else {
            appendLine("Target: $targetLabel ($targetId).")
        }
        if (scenarioContext.isNotBlank()) {
            appendLine("Scenario context:")
            appendLine(scenarioContext)
        }
        appendLine("Comments to address:")
        appendLine(commentsBlock)
        appendLine("Return a concise design patch: layout changes, component states, token updates, and copy adjustments.")
    }.trim()

    return UiPrompt(
        id = stableUiId("prompt", targetId, body),
        targetId = targetId,
        title = "Revise $targetLabel",
        body = body,
    )
}

private fun UiValue.renderForPrompt(): String =
    when (this) {
        is UiValue.BooleanValue -> value.toString()
        is UiValue.ListValue -> value.joinToString(prefix = "[", postfix = "]") { it.renderForPrompt() }
        UiValue.NullValue -> "null"
        is UiValue.NumberValue -> value.toString().trimEnd('0').trimEnd('.')
        is UiValue.ObjectValue -> value.entries.joinToString(prefix = "{", postfix = "}") { "${it.key}: ${it.value.renderForPrompt()}" }
        is UiValue.StringValue -> value
    }
