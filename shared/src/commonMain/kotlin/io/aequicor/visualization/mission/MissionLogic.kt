package io.aequicor.visualization.mission

fun createMissionVisualizationState(markdown: String = SampleMissionMarkdown): MissionVisualizationState {
    val parseResult = parseMissionMarkdown(markdown)
    val spec = (parseResult as? MissionParseResult.Success)?.spec
    return MissionVisualizationState(
        markdown = markdown,
        parseResult = parseResult,
        selectedScreenId = spec?.screens?.firstOrNull()?.id.orEmpty(),
        selectedComponentId = spec?.screens?.firstOrNull()?.components?.firstOrNull()?.id.orEmpty(),
        selectedScenarioId = spec?.scenarios?.firstOrNull()?.id.orEmpty(),
    )
}

fun reduceMissionVisualization(
    state: MissionVisualizationState,
    command: VisualizationCommand,
): MissionVisualizationState =
    when (command) {
        is VisualizationCommand.LoadDocument -> {
            val parsed = parseMissionMarkdown(command.markdown)
            val spec = (parsed as? MissionParseResult.Success)?.spec
            state.copy(
                markdown = command.markdown,
                parseResult = parsed,
                selectedScreenId = spec?.screens?.firstOrNull()?.id.orEmpty(),
                selectedComponentId = spec?.screens?.firstOrNull()?.components?.firstOrNull()?.id.orEmpty(),
                selectedScenarioId = spec?.scenarios?.firstOrNull()?.id.orEmpty(),
                inputValues = emptyMap(),
                latestPrompt = null,
                exportedMarkdown = "",
            )
        }
        is VisualizationCommand.SelectScreen -> {
            val screen = state.specOrNull?.screenById(command.screenId)
            state.copy(
                selectedScreenId = screen?.id ?: command.screenId,
                selectedComponentId = screen?.components?.firstOrNull()?.id.orEmpty(),
            )
        }
        is VisualizationCommand.SelectComponent -> selectTarget(state, command.componentId)
        is VisualizationCommand.SelectTarget -> selectTarget(state, command.targetId)
        is VisualizationCommand.SelectScenario -> state.copy(selectedScenarioId = command.scenarioId)
        is VisualizationCommand.UpdateInputValue -> state.copy(
            inputValues = state.inputValues + (command.componentId to command.value),
        )
        is VisualizationCommand.UpdateDraftComment -> state.copy(draftComment = command.body)
        is VisualizationCommand.AddComment -> addComment(state, command.targetId)
        is VisualizationCommand.GeneratePrompt -> generatePrompt(state, command.targetId)
        VisualizationCommand.ExportDocument -> exportDocument(state)
    }

private fun selectTarget(
    state: MissionVisualizationState,
    targetId: String,
): MissionVisualizationState {
    val target = state.specOrNull?.findTarget(targetId)
        ?: return state.copy(selectedComponentId = targetId)
    return state.copy(
        selectedScreenId = target.screen.id,
        selectedComponentId = target.component?.id ?: target.screen.components.firstOrNull()?.id.orEmpty(),
    )
}

private fun addComment(
    state: MissionVisualizationState,
    explicitTargetId: String,
): MissionVisualizationState {
    val body = state.draftComment.trim()
    val targetId = explicitTargetId.ifBlank { state.selectedComponentId.ifBlank { state.selectedScreenId } }
    val success = state.parseResult as? MissionParseResult.Success
    if (body.isEmpty() || targetId.isBlank() || success == null) return state

    val comment = ComponentComment(
        id = stableId("comment", targetId, body),
        targetId = targetId,
        body = body,
    )
    val updatedSpec = success.spec.copy(comments = success.spec.comments + comment)
    val updatedResult = success.copy(spec = updatedSpec)
    return state.copy(
        parseResult = updatedResult,
        draftComment = "",
        markdown = exportMissionMarkdown(success.document.originalMarkdown, updatedSpec),
        exportedMarkdown = "",
    )
}

private fun generatePrompt(
    state: MissionVisualizationState,
    explicitTargetId: String,
): MissionVisualizationState {
    val success = state.parseResult as? MissionParseResult.Success ?: return state
    val prompt = if (explicitTargetId.isBlank()) {
        buildGlobalDesignPrompt(success.spec, state.selectedScenario)
    } else {
        buildDesignPrompt(success.spec, explicitTargetId, state.selectedScenario)
    }
    val updatedSpec = success.spec.copy(prompts = success.spec.prompts.filterNot { it.id == prompt.id } + prompt)
    return state.copy(
        parseResult = success.copy(spec = updatedSpec),
        latestPrompt = prompt,
        exportedMarkdown = exportMissionMarkdown(success.document.originalMarkdown, updatedSpec),
    )
}

fun buildGlobalDesignPrompt(
    spec: MissionSpec,
    scenario: MissionScenario? = null,
): DesignPrompt {
    val scenarioContext = scenario?.steps
        ?.joinToString(separator = "\n") { step ->
            val target = step.componentId.ifBlank { step.screenId }
            "- [$target] ${step.action}${step.expectation.takeIf { it.isNotBlank() }?.let { " -> $it" }.orEmpty()}"
        }
        .orEmpty()

    val commentsBlock = if (spec.comments.isEmpty()) {
        "- No comments yet."
    } else {
        spec.comments.joinToString(separator = "\n") { comment ->
            val target = spec.findTarget(comment.targetId)
            val screenTitle = target?.screen?.title ?: "Unknown screen"
            val componentLabel = target?.component?.let { component ->
                val label = component.title.takeIf { it.isNotBlank() }
                    ?: component.text.takeIf { it.isNotBlank() }
                    ?: component.id
                " / ${component.type} '$label'"
            }.orEmpty()
            "- ${screenTitle}${componentLabel} (${comment.targetId}): ${comment.body}"
        }
    }

    val screenMap = spec.screens.joinToString(separator = "\n") { screen ->
        val components = screen.components.flattenComponents()
            .joinToString(separator = ", ") { "${it.type}:${it.id}" }
            .ifBlank { "no components" }
        "- ${screen.title} (${screen.id}): $components"
    }

    val body = buildString {
        appendLine("You are updating the complete mission-visualization design.")
        appendLine("Design tone: ${spec.theme.name} (${spec.theme.mood}); primary ${spec.theme.primary}, accent ${spec.theme.accent}.")
        appendLine("Mission: ${spec.title}.")
        if (spec.description.isNotBlank()) appendLine("Mission intent: ${spec.description}.")
        appendLine("Screens and components:")
        appendLine(screenMap)
        if (scenarioContext.isNotBlank()) {
            appendLine("Scenario context:")
            appendLine(scenarioContext)
        }
        appendLine("All comments to address:")
        appendLine(commentsBlock)
        appendLine("Return one cohesive design patch covering navigation, component states, layout, color/token changes, and copy adjustments.")
    }.trim()

    return DesignPrompt(
        id = stableId("prompt", "global", body),
        targetId = "global",
        title = "Revise complete mission",
        body = body,
    )
}

private fun exportDocument(state: MissionVisualizationState): MissionVisualizationState {
    val success = state.parseResult as? MissionParseResult.Success ?: return state
    return state.copy(exportedMarkdown = exportMissionMarkdown(success.document.originalMarkdown, success.spec))
}

fun buildDesignPrompt(
    spec: MissionSpec,
    targetId: String,
    scenario: MissionScenario? = null,
): DesignPrompt {
    val component = spec.componentById(targetId)
    val screen = spec.screens.firstOrNull { screen ->
        screen.id == targetId || screen.findComponent(targetId) != null
    }
    val comments = spec.comments.filter { it.targetId == targetId }
    val scenarioContext = scenario?.steps
        ?.filter { it.screenId == screen?.id || it.componentId == targetId }
        ?.joinToString(separator = "\n") { step ->
            "- ${step.action}${step.expectation.takeIf { it.isNotBlank() }?.let { " -> $it" }.orEmpty()}"
        }
        .orEmpty()

    val targetLabel = component?.title?.takeIf { it.isNotBlank() }
        ?: component?.text?.takeIf { it.isNotBlank() }
        ?: screen?.title
        ?: targetId
    val commentsBlock = comments.joinToString(separator = "\n") { "- ${it.body}" }.ifBlank { "- No comments yet." }

    val body = buildString {
        appendLine("You are updating the visual design for mission-visualization.")
        appendLine("Design tone: ${spec.theme.name} (${spec.theme.mood}); primary ${spec.theme.primary}, accent ${spec.theme.accent}.")
        appendLine("Mission: ${spec.title}.")
        if (screen != null) appendLine("Screen: ${screen.title} (${screen.id}).")
        if (component != null) {
            appendLine("Component: ${component.type} '${targetLabel}' (${component.id}).")
            if (component.description.isNotBlank()) appendLine("Component intent: ${component.description}.")
        } else {
            appendLine("Target: $targetLabel ($targetId).")
        }
        if (scenarioContext.isNotBlank()) {
            appendLine("Scenario context:")
            appendLine(scenarioContext)
        }
        appendLine("Comments to address:")
        appendLine(commentsBlock)
        appendLine("Return a concise design patch: layout changes, component states, color/token updates, and copy adjustments.")
    }.trim()

    return DesignPrompt(
        id = stableId("prompt", targetId, body),
        targetId = targetId,
        title = "Revise $targetLabel",
        body = body,
    )
}

private fun List<MissionComponent>.flattenComponents(): List<MissionComponent> =
    flatMap { component -> listOf(component) + component.children.flattenComponents() }

fun exportMissionMarkdown(
    originalMarkdown: String?,
    spec: MissionSpec,
): String {
    val yaml = spec.toMissionYaml()
    if (originalMarkdown.isNullOrBlank()) {
        return "```mission-visualization\n$yaml\n```\n"
    }

    val lines = originalMarkdown.replace("\r\n", "\n").replace('\r', '\n').split('\n')
    val startIndex = lines.indexOfFirst { it.trim() == "```mission-visualization" }
    if (startIndex < 0) {
        return originalMarkdown.trimEnd() + "\n\n```mission-visualization\n$yaml\n```\n"
    }
    val endIndex = lines.drop(startIndex + 1).indexOfFirst { it.trim() == "```" }
    if (endIndex < 0) {
        return originalMarkdown.trimEnd() + "\n```\n"
    }
    val absoluteEndIndex = startIndex + 1 + endIndex
    val before = lines.take(startIndex)
    val after = lines.drop(absoluteEndIndex + 1)
    return (before + "```mission-visualization" + yaml.lines() + "```" + after)
        .joinToString("\n")
        .trimEnd() + "\n"
}

fun MissionSpec.toMissionYaml(): String =
    buildString {
        appendLine("version: $version")
        appendLine("title: ${title.yamlScalar()}")
        if (description.isNotBlank()) appendLine("description: ${description.yamlScalar()}")
        appendLine("theme:")
        appendLine("  name: ${theme.name.yamlScalar()}")
        appendLine("  primary: ${theme.primary.yamlScalar()}")
        appendLine("  accent: ${theme.accent.yamlScalar()}")
        appendLine("  surface: ${theme.surface.yamlScalar()}")
        appendLine("  mood: ${theme.mood.yamlScalar()}")
        appendLine("screens:")
        screens.forEach { appendScreen(it, 2) }
        appendLine("scenarios:")
        scenarios.forEach { appendScenario(it, 2) }
        if (comments.isNotEmpty()) {
            appendLine("comments:")
            comments.forEach { appendComment(it, 2) }
        }
        if (prompts.isNotEmpty()) {
            appendLine("prompts:")
            prompts.forEach { appendPrompt(it, 2) }
        }
    }.trimEnd()

private fun StringBuilder.appendScreen(screen: MissionScreen, indent: Int) {
    val prefix = " ".repeat(indent)
    appendLine("${prefix}- id: ${screen.id.yamlScalar()}")
    appendLine("$prefix  title: ${screen.title.yamlScalar()}")
    if (screen.description.isNotBlank()) appendLine("$prefix  description: ${screen.description.yamlScalar()}")
    appendLine("$prefix  components:")
    screen.components.forEach { appendComponent(it, indent + 4) }
}

private fun StringBuilder.appendComponent(component: MissionComponent, indent: Int) {
    val prefix = " ".repeat(indent)
    appendLine("${prefix}- id: ${component.id.yamlScalar()}")
    appendLine("$prefix  type: ${component.type.yamlScalar()}")
    if (component.title.isNotBlank()) appendLine("$prefix  title: ${component.title.yamlScalar()}")
    if (component.text.isNotBlank()) appendLine("$prefix  text: ${component.text.yamlScalar()}")
    if (component.placeholder.isNotBlank()) appendLine("$prefix  placeholder: ${component.placeholder.yamlScalar()}")
    if (component.description.isNotBlank()) appendLine("$prefix  description: ${component.description.yamlScalar()}")
    if (component.variant.isNotBlank()) appendLine("$prefix  variant: ${component.variant.yamlScalar()}")
    if (component.items.isNotEmpty()) {
        appendLine("$prefix  items:")
        component.items.forEach { appendLine("$prefix    - ${it.yamlScalar()}") }
    }
    if (component.properties.isNotEmpty()) {
        appendLine("$prefix  properties:")
        component.properties.forEach { (key, value) -> appendLine("$prefix    $key: ${value.yamlScalar()}") }
    }
    if (component.children.isNotEmpty()) {
        appendLine("$prefix  children:")
        component.children.forEach { appendComponent(it, indent + 4) }
    }
}

private fun StringBuilder.appendScenario(scenario: MissionScenario, indent: Int) {
    val prefix = " ".repeat(indent)
    appendLine("${prefix}- id: ${scenario.id.yamlScalar()}")
    appendLine("$prefix  title: ${scenario.title.yamlScalar()}")
    if (scenario.summary.isNotBlank()) appendLine("$prefix  summary: ${scenario.summary.yamlScalar()}")
    appendLine("$prefix  steps:")
    scenario.steps.forEach { step ->
        appendLine("$prefix    - screenId: ${step.screenId.yamlScalar()}")
        if (step.id.isNotBlank()) appendLine("$prefix      id: ${step.id.yamlScalar()}")
        if (step.componentId.isNotBlank()) appendLine("$prefix      componentId: ${step.componentId.yamlScalar()}")
        appendLine("$prefix      action: ${step.action.yamlScalar()}")
        if (step.expectation.isNotBlank()) appendLine("$prefix      expectation: ${step.expectation.yamlScalar()}")
        if (step.note.isNotBlank()) appendLine("$prefix      note: ${step.note.yamlScalar()}")
    }
}

private fun StringBuilder.appendComment(comment: ComponentComment, indent: Int) {
    val prefix = " ".repeat(indent)
    appendLine("${prefix}- id: ${comment.id.yamlScalar()}")
    appendLine("$prefix  targetId: ${comment.targetId.yamlScalar()}")
    appendLine("$prefix  author: ${comment.author.yamlScalar()}")
    appendLine("$prefix  body: ${comment.body.yamlScalar()}")
    if (comment.createdAt.isNotBlank()) appendLine("$prefix  createdAt: ${comment.createdAt.yamlScalar()}")
}

private fun StringBuilder.appendPrompt(prompt: DesignPrompt, indent: Int) {
    val prefix = " ".repeat(indent)
    appendLine("${prefix}- id: ${prompt.id.yamlScalar()}")
    appendLine("$prefix  targetId: ${prompt.targetId.yamlScalar()}")
    appendLine("$prefix  title: ${prompt.title.yamlScalar()}")
    appendLine("$prefix  body: ${prompt.body.yamlScalar()}")
    if (prompt.createdAt.isNotBlank()) appendLine("$prefix  createdAt: ${prompt.createdAt.yamlScalar()}")
}

private fun String.yamlScalar(): String {
    if (isEmpty()) return "\"\""
    val canStayPlain = all { char ->
        char.isLetterOrDigit() || char in setOf(' ', '-', '_', '.', '/', '#', '(', ')')
    } && first() !in setOf('-', '?', ':', '#', '[', ']', '{', '}', '&', '*', '!', '|', '>', '@', '`')
    return if (canStayPlain && trim() == this && !contains("  ")) {
        this
    } else {
        "\"" + replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""
    }
}

internal fun stableId(prefix: String, targetId: String, content: String): String {
    var hash = 17
    val source = "$prefix|$targetId|$content"
    for (char in source) {
        hash = hash * 31 + char.code
    }
    val positive = (hash.toLong() and 0xffffffffL).toString(16)
    return "$prefix-$targetId-$positive"
}
