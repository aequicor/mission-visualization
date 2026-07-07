package io.aequicor.visualization.ui_engine.validator

import io.aequicor.visualization.ui_engine.ui_document_ir.SourceSpan
import io.aequicor.visualization.ui_engine.ui_document_ir.UiAction
import io.aequicor.visualization.ui_engine.ui_document_ir.UiDiagnostic
import io.aequicor.visualization.ui_engine.ui_document_ir.UiDiagnosticSeverity
import io.aequicor.visualization.ui_engine.ui_document_ir.UiDocument
import io.aequicor.visualization.ui_engine.ui_document_ir.UiLayout
import io.aequicor.visualization.ui_engine.ui_document_ir.UiNode
import io.aequicor.visualization.ui_engine.parser.UiParseResult
import io.aequicor.visualization.ui_engine.ui_document_ir.UiStyle
import io.aequicor.visualization.ui_engine.ui_document_ir.isKnownUiNodeType
import io.aequicor.visualization.ui_engine.parser.parseUiDocumentYaml

fun validateUiDocument(document: UiDocument): UiValidationResult {
    val diagnostics = mutableListOf<UiDiagnostic>()
    val targetIds = mutableSetOf<String>()
    val duplicateIds = mutableSetOf<String>()

    fun registerId(id: String, source: SourceSpan?, label: String) {
        if (id.isBlank()) {
            diagnostics += error("$label id is required.", source)
            return
        }
        if (!targetIds.add(id)) {
            duplicateIds += id
            diagnostics += error("Duplicate target id '$id'.", source)
        }
    }

    if (document.screens.isEmpty()) {
        diagnostics += error("At least one screen is required.", document.source)
    }

    document.screens.forEach { screen ->
        registerId(screen.id, screen.source, "Screen")
        validateLayout(screen.layout, screen.source, diagnostics)
        screen.children.forEach { node ->
            validateNode(node, targetIds, duplicateIds, diagnostics)
        }
    }

    val validTargets = document.allTargets()
    document.screens.forEach { screen ->
        screen.allNodes().forEach { node ->
            node.actions.forEach { action ->
                validateAction(action, validTargets, diagnostics)
            }
        }
    }
    validateScenarios(document, validTargets, diagnostics)
    validateComments(document, validTargets, diagnostics)
    validatePrompts(document, validTargets + "global", diagnostics)

    return UiValidationResult(diagnostics)
}

fun loadUiDocument(source: String): UiLoadResult {
    val parsed = parseUiDocumentYaml(source)
    return when (parsed) {
        is UiParseResult.Failure -> UiLoadResult.Failure(parsed.diagnostics)
        is UiParseResult.Success -> {
            val validation = validateUiDocument(parsed.document)
            val diagnostics = parsed.diagnostics + validation.diagnostics
            if (validation.hasErrors) {
                UiLoadResult.Failure(diagnostics)
            } else {
                UiLoadResult.Success(parsed.document, diagnostics)
            }
        }
    }
}

private fun validateNode(
    node: UiNode,
    targetIds: MutableSet<String>,
    duplicateIds: MutableSet<String>,
    diagnostics: MutableList<UiDiagnostic>,
) {
    registerNodeId(node, targetIds, duplicateIds, diagnostics)
    if (node.type.isBlank()) {
        diagnostics += error("Node type is required.", node.source)
    } else if (!isKnownUiNodeType(node.type)) {
        diagnostics += warning("Unknown node type '${node.type}' will use the fallback renderer.", node.source)
    }
    validateLayout(node.layout, node.source, diagnostics)
    validateStyle(node.style, node.source, diagnostics)
    node.children.forEach { child -> validateNode(child, targetIds, duplicateIds, diagnostics) }
}

private fun registerNodeId(
    node: UiNode,
    targetIds: MutableSet<String>,
    duplicateIds: MutableSet<String>,
    diagnostics: MutableList<UiDiagnostic>,
) {
    if (node.id.isBlank()) {
        diagnostics += error("Node id is required.", node.source)
        return
    }
    if (!targetIds.add(node.id)) {
        duplicateIds += node.id
        diagnostics += error("Duplicate target id '${node.id}'.", node.source)
    }
}

private fun validateLayout(
    layout: UiLayout,
    source: SourceSpan?,
    diagnostics: MutableList<UiDiagnostic>,
) {
    if (layout.type.isNotBlank() && layout.type !in KnownLayoutTypes) {
        diagnostics += error("Unknown layout type '${layout.type}'.", source)
    }
    if (layout.padding.isNotBlank() && layout.padding !in KnownSpacingTokens) {
        diagnostics += error("Unknown padding token '${layout.padding}'.", source)
    }
    if (layout.gap.isNotBlank() && layout.gap !in KnownSpacingTokens) {
        diagnostics += error("Unknown gap token '${layout.gap}'.", source)
    }
    if (layout.width.isNotBlank() && layout.width !in KnownSizeTokens) {
        diagnostics += error("Unknown width token '${layout.width}'.", source)
    }
    if (layout.height.isNotBlank() && layout.height !in KnownSizeTokens) {
        diagnostics += error("Unknown height token '${layout.height}'.", source)
    }
    if (layout.align.isNotBlank() && layout.align !in KnownAlignTokens) {
        diagnostics += error("Unknown align token '${layout.align}'.", source)
    }
    if (layout.columns != null && layout.columns < 1) {
        diagnostics += error("Layout columns must be greater than zero.", source)
    }
}

private fun validateStyle(
    style: UiStyle,
    source: SourceSpan?,
    diagnostics: MutableList<UiDiagnostic>,
) {
    if (style.tone.isNotBlank() && style.tone !in KnownToneTokens) {
        diagnostics += error("Unknown tone token '${style.tone}'.", source)
    }
    if (style.variant.isNotBlank() && style.variant !in KnownVariantTokens) {
        diagnostics += error("Unknown variant token '${style.variant}'.", source)
    }
    if (style.size.isNotBlank() && style.size !in KnownVisualSizeTokens) {
        diagnostics += error("Unknown size token '${style.size}'.", source)
    }
    if (style.emphasis.isNotBlank() && style.emphasis !in KnownEmphasisTokens) {
        diagnostics += error("Unknown emphasis token '${style.emphasis}'.", source)
    }
}

private fun validateActionShape(
    action: UiAction,
    diagnostics: MutableList<UiDiagnostic>,
) {
    if (action.type !in KnownActionTypes) {
        diagnostics += error("Unknown action type '${action.type}'.", action.source)
    }
    if (action.type in TargetedActionTypes && action.target.isBlank()) {
        diagnostics += error("Action '${action.type}' requires target.", action.source)
    }
}

private fun validateAction(
    action: UiAction,
    validTargets: Set<String>,
    diagnostics: MutableList<UiDiagnostic>,
) {
    validateActionShape(action, diagnostics)
    if (action.target.isNotBlank() && action.target !in validTargets) {
        diagnostics += error("Action target '${action.target}' does not exist.", action.source)
    }
}

private fun validateScenarios(
    document: UiDocument,
    validTargets: Set<String>,
    diagnostics: MutableList<UiDiagnostic>,
) {
    val scenarioIds = mutableSetOf<String>()
    document.scenarios.forEach { scenario ->
        if (scenario.id.isBlank()) {
            diagnostics += error("Scenario id is required.", scenario.source)
        } else if (!scenarioIds.add(scenario.id)) {
            diagnostics += error("Duplicate scenario id '${scenario.id}'.", scenario.source)
        }
        scenario.steps.forEach { step ->
            if (step.screenId.isBlank()) {
                diagnostics += error("Scenario step screenId is required.", step.source)
            } else if (document.screens.none { it.id == step.screenId }) {
                diagnostics += error("Scenario step screenId '${step.screenId}' does not exist.", step.source)
            }
            if (step.nodeId.isNotBlank() && step.nodeId !in validTargets) {
                diagnostics += error("Scenario step nodeId '${step.nodeId}' does not exist.", step.source)
            }
            if (step.action.isBlank()) {
                diagnostics += error("Scenario step action is required.", step.source)
            }
        }
    }
}

private fun validateComments(
    document: UiDocument,
    validTargets: Set<String>,
    diagnostics: MutableList<UiDiagnostic>,
) {
    document.comments.forEach { comment ->
        if (comment.targetId.isBlank()) {
            diagnostics += error("Comment targetId is required.", comment.source)
        } else if (comment.targetId !in validTargets) {
            diagnostics += error("Comment targetId '${comment.targetId}' does not exist.", comment.source)
        }
        if (comment.body.isBlank()) {
            diagnostics += error("Comment body is required.", comment.source)
        }
    }
}

private fun validatePrompts(
    document: UiDocument,
    validTargets: Set<String>,
    diagnostics: MutableList<UiDiagnostic>,
) {
    document.prompts.forEach { prompt ->
        if (prompt.targetId.isBlank()) {
            diagnostics += error("Prompt targetId is required.", prompt.source)
        } else if (prompt.targetId !in validTargets) {
            diagnostics += error("Prompt targetId '${prompt.targetId}' does not exist.", prompt.source)
        }
        if (prompt.body.isBlank()) {
            diagnostics += error("Prompt body is required.", prompt.source)
        }
    }
}

private fun error(message: String, source: SourceSpan?): UiDiagnostic =
    UiDiagnostic(UiDiagnosticSeverity.Error, message, source ?: SourceSpan(1, 1))

private fun warning(message: String, source: SourceSpan?): UiDiagnostic =
    UiDiagnostic(UiDiagnosticSeverity.Warning, message, source ?: SourceSpan(1, 1))

private val KnownLayoutTypes = setOf("column", "row", "grid", "stack")
private val KnownSpacingTokens = setOf("none", "xs", "sm", "md", "lg", "xl")
private val KnownSizeTokens = setOf("fill", "hug", "auto", "content")
private val KnownAlignTokens = setOf("start", "center", "end", "stretch", "spaceBetween")
private val KnownToneTokens = setOf("neutral", "primary", "accent", "info", "success", "warning", "danger")
private val KnownVariantTokens = setOf("primary", "secondary", "outline", "ghost", "filled", "tonal")
private val KnownVisualSizeTokens = setOf("xs", "sm", "md", "lg", "xl")
private val KnownEmphasisTokens = setOf("low", "medium", "high")
private val KnownActionTypes = setOf("none", "navigate", "select", "submit", "openDialog", "closeDialog")
private val TargetedActionTypes = setOf("navigate", "select", "openDialog")
