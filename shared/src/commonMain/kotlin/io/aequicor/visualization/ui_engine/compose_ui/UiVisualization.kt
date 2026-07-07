package io.aequicor.visualization.ui_engine.compose_ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.aequicor.visualization.ui_engine.compose_render_engine.Cloud
import io.aequicor.visualization.ui_engine.compose_render_engine.CoralNote
import io.aequicor.visualization.ui_engine.compose_render_engine.DeepLapis
import io.aequicor.visualization.ui_engine.compose_render_engine.DefaultUiRenderEngine
import io.aequicor.visualization.ui_engine.compose_render_engine.Ink
import io.aequicor.visualization.ui_engine.compose_render_engine.LapisBlue
import io.aequicor.visualization.ui_engine.compose_render_engine.Mist
import io.aequicor.visualization.ui_engine.compose_render_engine.SignalTeal
import io.aequicor.visualization.ui_engine.compose_render_engine.StepDot
import io.aequicor.visualization.ui_engine.mv_yaml_source.SampleUiYaml
import io.aequicor.visualization.ui_engine.runtime_state.UiCommand
import io.aequicor.visualization.ui_engine.ui_document_ir.UiDiagnostic
import io.aequicor.visualization.ui_engine.ui_document_ir.UiDiagnosticSeverity
import io.aequicor.visualization.ui_engine.ui_document_ir.UiDocument
import io.aequicor.visualization.ui_engine.ui_document_ir.UiPrompt
import io.aequicor.visualization.ui_engine.runtime_state.UiVisualizationState
import io.aequicor.visualization.ui_engine.ui_document_ir.propString
import io.aequicor.visualization.ui_engine.ui_document_ir.title
import io.aequicor.visualization.ui_engine.runtime_state.createUiVisualizationState
import io.aequicor.visualization.ui_engine.runtime_state.reduceUiVisualization

@Stable
class UiVisualizationStateHolder internal constructor(initialSource: String) {
    var value by mutableStateOf(createUiVisualizationState(initialSource))
        private set

    fun dispatch(command: UiCommand) {
        value = reduceUiVisualization(value, command)
    }
}

@Composable
fun rememberUiVisualizationState(
    initialSource: String = SampleUiYaml,
): UiVisualizationStateHolder =
    remember(initialSource) { UiVisualizationStateHolder(initialSource) }

@Composable
fun UiVisualizationTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = LapisBlue,
            onPrimary = Color.White,
            primaryContainer = Color(0xFFD9EAFF),
            onPrimaryContainer = DeepLapis,
            secondary = SignalTeal,
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFD8F5F1),
            onSecondaryContainer = Color(0xFF073F39),
            tertiary = CoralNote,
            surface = Cloud,
            onSurface = Ink,
            surfaceVariant = Mist,
            onSurfaceVariant = Color(0xFF4D5C70),
            background = Color(0xFFF9FBFF),
            onBackground = Ink,
            outline = Color(0xFFB8C7D8),
        ),
        content = content,
    )
}

@Composable
fun UiVisualization(
    document: UiDocument?,
    state: UiVisualizationState,
    onEvent: (UiCommand) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFFF8FBFF), Color(0xFFEFF7FA), Color(0xFFFDFEFE)),
                ),
            )
            .safeContentPadding()
            .padding(16.dp),
    ) {
        if (maxWidth < 980.dp) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                SourcePane(state, onEvent, Modifier.fillMaxWidth().height(520.dp))
                CanvasPane(document, state, onEvent, Modifier.fillMaxWidth().height(720.dp))
                InspectorPane(document, state, onEvent, Modifier.fillMaxWidth().height(620.dp))
            }
        } else {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                SourcePane(state, onEvent, Modifier.width(360.dp).fillMaxHeight())
                CanvasPane(document, state, onEvent, Modifier.weight(1f).fillMaxHeight())
                InspectorPane(document, state, onEvent, Modifier.width(360.dp).fillMaxHeight())
            }
        }
    }
}

@Composable
private fun SourcePane(
    state: UiVisualizationState,
    onEvent: (UiCommand) -> Unit,
    modifier: Modifier = Modifier,
) {
    Panel(modifier) {
        Text("UI source", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(
            "Standalone strict YAML (.mv.yaml)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        DiagnosticsStatus(state.diagnostics)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = state.source,
            onValueChange = { onEvent(UiCommand.LoadYaml(it)) },
            modifier = Modifier.fillMaxWidth().weight(1f),
            textStyle = MaterialTheme.typography.bodySmall,
            label = { Text("YAML") },
            minLines = 18,
        )
        Spacer(Modifier.height(12.dp))
        val document = state.documentOrNull
        if (document != null) {
            Text("Screens", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                document.screens.forEach { screen ->
                    SourceRow(
                        title = screen.title.ifBlank { screen.id },
                        detail = "${screen.allNodes().size} nodes",
                        selected = state.selectedScreen?.id == screen.id,
                        onClick = { onEvent(UiCommand.SelectScreen(screen.id)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CanvasPane(
    document: UiDocument?,
    state: UiVisualizationState,
    onEvent: (UiCommand) -> Unit,
    modifier: Modifier = Modifier,
) {
    Panel(modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text(document?.title ?: "UI preview", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    state.selectedScreen?.title ?: "No valid UI document loaded",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = RoundedCornerShape(999.dp),
            ) {
                Text("ir + compose", modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium)
            }
        }
        Spacer(Modifier.height(14.dp))

        if (document == null) {
            DiagnosticsFailure(state.diagnostics, Modifier.fillMaxSize())
            return@Panel
        }

        ScenarioStrip(document, state, onEvent)
        Spacer(Modifier.height(14.dp))
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFFEAF2FC)),
        ) {
            DefaultUiRenderEngine.Render(document, state, onEvent)
        }
    }
}

@Composable
private fun InspectorPane(
    document: UiDocument?,
    state: UiVisualizationState,
    onEvent: (UiCommand) -> Unit,
    modifier: Modifier = Modifier,
) {
    Panel(modifier) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            Text("Inspector", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                "IR target, comments, and prompt output",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))

            if (document == null) {
                DiagnosticsFailure(state.diagnostics, Modifier.fillMaxWidth().heightIn(min = 180.dp))
                return@Column
            }

            TargetSummary(state)
            Spacer(Modifier.height(14.dp))
            CommentsBlock(document, state, onEvent)
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = state.draftComment,
                onValueChange = { onEvent(UiCommand.UpdateDraftComment(it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Design comment") },
                minLines = 3,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onEvent(UiCommand.AddComment()) },
                    enabled = state.draftComment.isNotBlank() && (state.selectedNodeId.isNotBlank() || state.selectedScreenId.isNotBlank()),
                ) {
                    Text("Attach")
                }
                OutlinedButton(
                    onClick = { onEvent(UiCommand.GeneratePrompt()) },
                    enabled = state.selectedNodeId.isNotBlank() || state.selectedScreenId.isNotBlank(),
                ) {
                    Text("Prompt")
                }
            }
            Spacer(Modifier.height(16.dp))
            PromptBlock(state.latestPrompt ?: document.prompts.lastOrNull())
            if (state.diagnostics.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                DiagnosticList("Diagnostics", state.diagnostics)
            }
        }
    }
}

@Composable
private fun ScenarioStrip(
    document: UiDocument,
    state: UiVisualizationState,
    onEvent: (UiCommand) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            document.scenarios.forEach { scenario ->
                val selected = state.selectedScenario?.id == scenario.id
                if (selected) {
                    Button(onClick = { onEvent(UiCommand.SelectScenario(scenario.id)) }) {
                        Text(scenario.title, maxLines = 1)
                    }
                } else {
                    OutlinedButton(onClick = { onEvent(UiCommand.SelectScenario(scenario.id)) }) {
                        Text(scenario.title, maxLines = 1)
                    }
                }
            }
        }
        state.selectedScenario?.let { scenario ->
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                scenario.steps.forEachIndexed { index, step ->
                    val selected = step.nodeId == state.selectedNodeId
                    Surface(
                        modifier = Modifier.clickable {
                            onEvent(UiCommand.SelectTarget(step.nodeId.ifBlank { step.screenId }))
                        },
                        shape = RoundedCornerShape(8.dp),
                        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.White,
                        border = BorderStroke(1.dp, Color(0xFFD6E4EE)),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            StepDot(index + 1)
                            Text(step.action, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TargetSummary(state: UiVisualizationState) {
    val node = state.selectedNode
    val screen = state.selectedScreen
    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(node?.title() ?: screen?.title ?: "No target selected", fontWeight = FontWeight.Bold)
            Text(
                node?.let { "${it.type} / ${it.id}" } ?: screen?.id.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val description = node?.propString("body")?.takeIf { it.isNotBlank() }
                ?: node?.propString("text")?.takeIf { it.isNotBlank() }
                ?: screen?.description.orEmpty()
            if (description.isNotBlank()) Text(description, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun CommentsBlock(
    document: UiDocument,
    state: UiVisualizationState,
    onEvent: (UiCommand) -> Unit,
) {
    val groupedComments = document.comments.groupBy { it.targetId }
    Text("All comments", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(8.dp))
    if (groupedComments.isEmpty()) {
        Text("No comments yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            groupedComments.forEach { (targetId, comments) ->
                val target = document.findTarget(targetId)
                val selected = targetId == state.selectedNodeId || targetId == state.selectedScreenId
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = if (selected) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f) else Color(0xFFFFF9F2),
                    border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.secondary else Color(0xFFF4D3C6)),
                ) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            target?.node?.let { "${target.screen.title} / ${it.title()}" }
                                ?: target?.screen?.title
                                ?: targetId,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else Color(0xFF8E3B25),
                            fontWeight = FontWeight.SemiBold,
                        )
                        comments.forEach { comment ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onEvent(UiCommand.SelectTarget(comment.targetId)) },
                                shape = RoundedCornerShape(8.dp),
                                color = Color.White.copy(alpha = 0.72f),
                            ) {
                                Column(Modifier.padding(9.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(comment.author, style = MaterialTheme.typography.labelMedium, color = Color(0xFF8E3B25))
                                    Text(comment.body, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PromptBlock(prompt: UiPrompt?) {
    Text("Prompt", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(8.dp))
    if (prompt == null) {
        Text("Generate a prompt from the selected target and comments.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(prompt.title, fontWeight = FontWeight.SemiBold, color = DeepLapis)
            CodeBlock(prompt.body, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun Panel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = Color.White.copy(alpha = 0.96f),
        tonalElevation = 2.dp,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, Color(0xFFD8E4F0)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            content = content,
        )
    }
}

@Composable
private fun DiagnosticsStatus(diagnostics: List<UiDiagnostic>) {
    val errors = diagnostics.count { it.severity == UiDiagnosticSeverity.Error }
    val warnings = diagnostics.count { it.severity == UiDiagnosticSeverity.Warning }
    val success = errors == 0
    val color = if (success) SignalTeal else CoralNote
    Surface(
        color = color.copy(alpha = 0.12f),
        contentColor = if (success) Color(0xFF075047) else Color(0xFF74301F),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(if (success) "Loaded" else "Needs attention", fontWeight = FontWeight.SemiBold)
            Text("$errors errors / $warnings warnings", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SourceRow(
    title: String,
    detail: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Transparent),
    ) {
        Column(Modifier.padding(10.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DiagnosticsFailure(
    diagnostics: List<UiDiagnostic>,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier, shape = RoundedCornerShape(8.dp), color = Color(0xFFFFF4EF)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Document cannot be rendered", fontWeight = FontWeight.Bold, color = Color(0xFF8E3B25))
            if (diagnostics.isEmpty()) {
                Text("No parser details available.", style = MaterialTheme.typography.bodySmall)
            } else {
                diagnostics.forEach { Text("${it.source.line}:${it.source.column} ${it.message}", style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

@Composable
private fun DiagnosticList(
    title: String,
    diagnostics: List<UiDiagnostic>,
) {
    Surface(shape = RoundedCornerShape(8.dp), color = CoralNote.copy(alpha = 0.10f)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, color = Color(0xFF8E3B25), fontWeight = FontWeight.Bold)
            diagnostics.forEach {
                Text("${it.severity}: ${it.source.line}:${it.source.column} ${it.message}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun CodeBlock(
    text: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF102033),
        contentColor = Color(0xFFE7F1FF),
    ) {
        SelectionContainer {
            Text(
                text,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
