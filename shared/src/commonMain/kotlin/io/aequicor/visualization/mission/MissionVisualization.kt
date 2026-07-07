package io.aequicor.visualization.mission

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
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

private val LapisBlue = Color(0xFF1F5FA8)
private val DeepLapis = Color(0xFF143A66)
private val SignalTeal = Color(0xFF2BB8A8)
private val CoralNote = Color(0xFFE97155)
private val Ink = Color(0xFF172033)
private val Cloud = Color(0xFFF6FAFF)
private val Mist = Color(0xFFEAF1F8)

@Stable
class MissionVisualizationStateHolder internal constructor(initialMarkdown: String) {
    var value by mutableStateOf(createMissionVisualizationState(initialMarkdown))
        private set

    fun dispatch(command: VisualizationCommand) {
        value = reduceMissionVisualization(value, command)
    }
}

@Composable
fun rememberMissionVisualizationState(
    initialMarkdown: String = SampleMissionMarkdown,
): MissionVisualizationStateHolder =
    remember(initialMarkdown) { MissionVisualizationStateHolder(initialMarkdown) }

@Composable
fun MissionVisualizationTheme(content: @Composable () -> Unit) {
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
fun MissionVisualization(
    spec: MissionSpec?,
    state: MissionVisualizationState,
    onEvent: (VisualizationCommand) -> Unit,
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
                CanvasPane(spec, state, onEvent, Modifier.fillMaxWidth().height(680.dp))
                InspectorPane(spec, state, onEvent, Modifier.fillMaxWidth().height(620.dp))
            }
        } else {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                SourcePane(state, onEvent, Modifier.width(340.dp).fillMaxHeight())
                CanvasPane(spec, state, onEvent, Modifier.weight(1f).fillMaxHeight())
                InspectorPane(spec, state, onEvent, Modifier.width(360.dp).fillMaxHeight())
            }
        }
    }
}

@Composable
private fun SourcePane(
    state: MissionVisualizationState,
    onEvent: (VisualizationCommand) -> Unit,
    modifier: Modifier = Modifier,
) {
    Panel(modifier) {
        Text("Mission source", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(
            "Strict fenced YAML in Markdown",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        ParseStatus(state.parseResult, state.warnings)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = state.markdown,
            onValueChange = { onEvent(VisualizationCommand.LoadDocument(it)) },
            modifier = Modifier.fillMaxWidth().weight(1f),
            textStyle = MaterialTheme.typography.bodySmall,
            label = { Text("Markdown") },
            minLines = 18,
        )
        Spacer(Modifier.height(12.dp))
        val spec = state.specOrNull
        if (spec != null) {
            Text("Screens", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                spec.screens.forEach { screen ->
                    val selected = state.selectedScreen?.id == screen.id
                    SourceRow(
                        title = screen.title,
                        detail = "${screen.components.size} components",
                        selected = selected,
                        onClick = { onEvent(VisualizationCommand.SelectTarget(screen.id)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CanvasPane(
    spec: MissionSpec?,
    state: MissionVisualizationState,
    onEvent: (VisualizationCommand) -> Unit,
    modifier: Modifier = Modifier,
) {
    Panel(modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text(spec?.title ?: "Mission preview", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    state.selectedScreen?.title ?: "No valid mission spec loaded",
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
                Text("lazurite", modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium)
            }
        }
        Spacer(Modifier.height(14.dp))

        if (spec == null) {
            ParseFailure(state.parseResult, Modifier.fillMaxSize())
            return@Panel
        }

        ScenarioStrip(spec, state, onEvent)
        Spacer(Modifier.height(14.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFFEAF2FC)),
            contentAlignment = Alignment.TopCenter,
        ) {
            val screen = state.selectedScreen ?: spec.screens.firstOrNull()
            if (screen != null) {
                ScreenMock(screen, state, onEvent)
            }
        }
    }
}

@Composable
private fun InspectorPane(
    spec: MissionSpec?,
    state: MissionVisualizationState,
    onEvent: (VisualizationCommand) -> Unit,
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
                "Component notes and prompt output",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))

            if (spec == null) {
                ParseFailure(state.parseResult, Modifier.fillMaxWidth().heightIn(min = 180.dp))
                return@Column
            }

            TargetSummary(state)
            Spacer(Modifier.height(14.dp))
            CommentsBlock(spec, state, onEvent)
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = state.draftComment,
                onValueChange = { onEvent(VisualizationCommand.UpdateDraftComment(it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Design comment") },
                minLines = 3,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onEvent(VisualizationCommand.AddComment()) },
                    enabled = state.draftComment.isNotBlank() && state.selectedComponentId.isNotBlank(),
                ) {
                    Text("Attach")
                }
                OutlinedButton(
                    onClick = { onEvent(VisualizationCommand.GeneratePrompt()) },
                    enabled = state.selectedComponentId.isNotBlank() || state.selectedScreenId.isNotBlank(),
                ) {
                    Text("Prompt")
                }
            }
            Spacer(Modifier.height(16.dp))
            PromptBlock(state.latestPrompt ?: spec.prompts.lastOrNull())
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = { onEvent(VisualizationCommand.ExportDocument) }, modifier = Modifier.fillMaxWidth()) {
                Text("Export Markdown")
            }
            if (state.exportedMarkdown.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                CodeBlock(state.exportedMarkdown, Modifier.fillMaxWidth().heightIn(max = 260.dp))
            }
            if (state.warnings.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                MessageList("Warnings", state.warnings, MaterialTheme.colorScheme.tertiary)
            }
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
private fun ParseStatus(
    result: MissionParseResult,
    warnings: List<MissionParseMessage>,
) {
    val success = result is MissionParseResult.Success
    val color = if (success) SignalTeal else CoralNote
    Surface(
        color = color.copy(alpha = 0.12f),
        contentColor = if (success) Color(0xFF075047) else Color(0xFF74301F),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(if (success) "Parsed" else "Needs attention", fontWeight = FontWeight.SemiBold)
            Text(
                if (success) "${warnings.size} warnings" else "Fix the spec block to update the preview",
                style = MaterialTheme.typography.bodySmall,
            )
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
private fun ScenarioStrip(
    spec: MissionSpec,
    state: MissionVisualizationState,
    onEvent: (VisualizationCommand) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            spec.scenarios.forEach { scenario ->
                val selected = state.selectedScenario?.id == scenario.id
                if (selected) {
                    Button(onClick = { onEvent(VisualizationCommand.SelectScenario(scenario.id)) }) {
                        Text(scenario.title, maxLines = 1)
                    }
                } else {
                    OutlinedButton(onClick = { onEvent(VisualizationCommand.SelectScenario(scenario.id)) }) {
                        Text(scenario.title, maxLines = 1)
                    }
                }
            }
        }
        val scenario = state.selectedScenario
        if (scenario != null) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                scenario.steps.forEachIndexed { index, step ->
                    Surface(
                        modifier = Modifier.clickable {
                            onEvent(VisualizationCommand.SelectTarget(step.componentId.ifBlank { step.screenId }))
                        },
                        shape = RoundedCornerShape(8.dp),
                        color = if (step.componentId == state.selectedComponentId) MaterialTheme.colorScheme.secondaryContainer else Color.White,
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
private fun StepDot(number: Int) {
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        Text(number.toString(), color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ScreenMock(
    screen: MissionScreen,
    state: MissionVisualizationState,
    onEvent: (VisualizationCommand) -> Unit,
) {
    Column(
        modifier = Modifier
            .padding(20.dp)
            .widthIn(max = 520.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
            .border(1.dp, Color(0xFFC7D8E8), RoundedCornerShape(8.dp))
            .padding(18.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(screen.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = DeepLapis)
        if (screen.description.isNotBlank()) {
            Text(screen.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        screen.components.forEach { component ->
            ComponentPreview(component, state, onEvent, depth = 0)
        }
    }
}

@Composable
private fun ComponentPreview(
    component: MissionComponent,
    state: MissionVisualizationState,
    onEvent: (VisualizationCommand) -> Unit,
    depth: Int,
) {
    val selected = state.selectedComponentId == component.id
    val warnings = if (isKnownMissionComponentType(component.type)) 0 else 1
    val borderColor = when {
        selected -> MaterialTheme.colorScheme.primary
        warnings > 0 -> MaterialTheme.colorScheme.tertiary
        else -> Color(0xFFD6E4EE)
    }
    val modifier = Modifier
        .fillMaxWidth()
        .border(BorderStroke(if (selected) 2.dp else 1.dp, borderColor), RoundedCornerShape(8.dp))
        .clickable { onEvent(VisualizationCommand.SelectTarget(component.id)) }

    when (component.type) {
        "topBar" -> TopBarPreview(component, modifier)
        "section", "screen" -> SectionPreview(component, state, onEvent, modifier, depth)
        "card" -> CardPreview(component, modifier)
        "button" -> ButtonPreview(component, selected, onEvent)
        "input" -> InputPreview(component, state, onEvent, modifier)
        "list" -> ListPreview(component, modifier)
        "tabs" -> TabsPreview(component, modifier)
        "bottomBar" -> BottomBarPreview(component, modifier)
        "imagePlaceholder" -> ImagePlaceholderPreview(component, modifier)
        "form" -> FormPreview(component, state, onEvent, modifier, depth)
        "text" -> TextPreview(component, modifier)
        else -> UnknownPreview(component, modifier)
    }
}

@Composable
private fun TopBarPreview(component: MissionComponent, modifier: Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(8.dp), color = DeepLapis, contentColor = Color.White) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text(component.title.ifBlank { "Top bar" }, fontWeight = FontWeight.Bold)
                if (component.text.isNotBlank()) Text(component.text, style = MaterialTheme.typography.bodySmall, color = Color(0xFFC8E0F8))
            }
            Box(Modifier.size(28.dp).clip(CircleShape).background(SignalTeal))
        }
    }
}

@Composable
private fun SectionPreview(
    component: MissionComponent,
    state: MissionVisualizationState,
    onEvent: (VisualizationCommand) -> Unit,
    modifier: Modifier,
    depth: Int,
) {
    Surface(modifier = modifier, shape = RoundedCornerShape(8.dp), color = Color(0xFFF8FBFF)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(component.title.ifBlank { "Section" }, fontWeight = FontWeight.Bold, color = DeepLapis)
            if (component.description.isNotBlank()) {
                Text(component.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            component.children.forEach { child -> ComponentPreview(child, state, onEvent, depth + 1) }
        }
    }
}

@Composable
private fun CardPreview(component: MissionComponent, modifier: Modifier) {
    ElevatedCard(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = Color.White),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(component.title.ifBlank { "Card" }, fontWeight = FontWeight.Bold)
            Text(component.text.ifBlank { component.description.ifBlank { "Reusable content block" } }, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ButtonPreview(
    component: MissionComponent,
    selected: Boolean,
    onEvent: (VisualizationCommand) -> Unit,
) {
    val primary = component.variant == "primary"
    val colors = if (primary) {
        ButtonDefaults.buttonColors(containerColor = if (selected) DeepLapis else LapisBlue)
    } else {
        ButtonDefaults.outlinedButtonColors(contentColor = LapisBlue)
    }
    val text = component.text.ifBlank { component.title.ifBlank { "Button" } }
    if (primary) {
        Button(
            onClick = { onEvent(VisualizationCommand.SelectTarget(component.id)) },
            modifier = Modifier.fillMaxWidth(),
            colors = colors,
            shape = RoundedCornerShape(8.dp),
        ) {
            Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    } else {
        OutlinedButton(
            onClick = { onEvent(VisualizationCommand.SelectTarget(component.id)) },
            modifier = Modifier.fillMaxWidth(),
            colors = colors,
            shape = RoundedCornerShape(8.dp),
        ) {
            Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun InputPreview(
    component: MissionComponent,
    state: MissionVisualizationState,
    onEvent: (VisualizationCommand) -> Unit,
    modifier: Modifier,
) {
    Surface(modifier = modifier, shape = RoundedCornerShape(8.dp), color = Color.White) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(component.title.ifBlank { "Input" }, style = MaterialTheme.typography.labelLarge)
            OutlinedTextField(
                value = state.inputValues[component.id].orEmpty(),
                onValueChange = {
                    onEvent(VisualizationCommand.SelectTarget(component.id))
                    onEvent(VisualizationCommand.UpdateInputValue(component.id, it))
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        component.placeholder.ifBlank { "Placeholder" },
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun ListPreview(component: MissionComponent, modifier: Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(8.dp), color = Color.White) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(component.title.ifBlank { "List" }, fontWeight = FontWeight.Bold)
            component.items.forEach { item ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                    Box(Modifier.padding(top = 7.dp).size(6.dp).clip(CircleShape).background(SignalTeal))
                    Text(item, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun TabsPreview(component: MissionComponent, modifier: Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(8.dp), color = Color.White) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(component.title.ifBlank { "Tabs" }, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                component.items.ifEmpty { listOf("Tab one", "Tab two") }.forEachIndexed { index, item ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (index == 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Text(item, modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomBarPreview(component: MissionComponent, modifier: Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(8.dp), color = Color(0xFFF1F6FB)) {
        Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.SpaceAround) {
            component.items.ifEmpty { listOf("Home", "Notes", "Export") }.forEach {
                Text(it, style = MaterialTheme.typography.labelMedium, color = DeepLapis)
            }
        }
    }
}

@Composable
private fun ImagePlaceholderPreview(component: MissionComponent, modifier: Modifier) {
    Box(
        modifier = modifier
            .background(Color(0xFFE6F1F8), RoundedCornerShape(8.dp))
            .aspectRatio(16f / 9f),
        contentAlignment = Alignment.Center,
    ) {
        Text(component.title.ifBlank { "Image placeholder" }, color = DeepLapis, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun FormPreview(
    component: MissionComponent,
    state: MissionVisualizationState,
    onEvent: (VisualizationCommand) -> Unit,
    modifier: Modifier,
    depth: Int,
) {
    Surface(modifier = modifier, shape = RoundedCornerShape(8.dp), color = Color(0xFFFBFDFF)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(component.title.ifBlank { "Form" }, fontWeight = FontWeight.Bold)
            component.children.forEach { child -> ComponentPreview(child, state, onEvent, depth + 1) }
        }
    }
}

@Composable
private fun TextPreview(component: MissionComponent, modifier: Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(8.dp), color = Color.White) {
        Text(
            component.text.ifBlank { component.title.ifBlank { "Text" } },
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun UnknownPreview(component: MissionComponent, modifier: Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(8.dp), color = Color(0xFFFFF4EF)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Unknown component: ${component.type}", color = Color(0xFF8E3B25), fontWeight = FontWeight.Bold)
            Text(component.title.ifBlank { component.id }, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun TargetSummary(state: MissionVisualizationState) {
    val component = state.selectedComponent
    val screen = state.selectedScreen
    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(component?.title?.ifBlank { component.id } ?: screen?.title ?: "No target selected", fontWeight = FontWeight.Bold)
            Text(
                component?.let { "${it.type} / ${it.id}" } ?: screen?.id.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val description = component?.description?.takeIf { it.isNotBlank() } ?: screen?.description.orEmpty()
            if (description.isNotBlank()) Text(description, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun CommentsBlock(
    spec: MissionSpec,
    state: MissionVisualizationState,
    onEvent: (VisualizationCommand) -> Unit,
) {
    val groupedComments = spec.comments.groupBy { it.targetId }
    Text("All comments", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(8.dp))
    if (groupedComments.isEmpty()) {
        Text("No comments yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            groupedComments.forEach { (targetId, comments) ->
                val target = spec.findTarget(targetId)
                val selected = targetId == state.selectedComponentId || targetId == state.selectedScreenId
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = if (selected) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f) else Color(0xFFFFF9F2),
                    border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.secondary else Color(0xFFF4D3C6)),
                ) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            target?.component?.let { "${target.screen.title} / ${it.title.ifBlank { it.text.ifBlank { it.id } }}" }
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
                                    .clickable { onEvent(VisualizationCommand.SelectTarget(comment.targetId)) },
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
private fun PromptBlock(prompt: DesignPrompt?) {
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
private fun ParseFailure(
    result: MissionParseResult,
    modifier: Modifier = Modifier,
) {
    val errors = (result as? MissionParseResult.Failure)?.errors.orEmpty()
    Surface(modifier = modifier, shape = RoundedCornerShape(8.dp), color = Color(0xFFFFF4EF)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Spec cannot be rendered", fontWeight = FontWeight.Bold, color = Color(0xFF8E3B25))
            if (errors.isEmpty()) {
                Text("No parser details available.", style = MaterialTheme.typography.bodySmall)
            } else {
                errors.forEach { Text("Line ${it.line}: ${it.message}", style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

@Composable
private fun MessageList(
    title: String,
    messages: List<MissionParseMessage>,
    color: Color,
) {
    Surface(shape = RoundedCornerShape(8.dp), color = color.copy(alpha = 0.10f)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, color = color, fontWeight = FontWeight.Bold)
            messages.forEach {
                Text("Line ${it.line}: ${it.message}", style = MaterialTheme.typography.bodySmall)
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
