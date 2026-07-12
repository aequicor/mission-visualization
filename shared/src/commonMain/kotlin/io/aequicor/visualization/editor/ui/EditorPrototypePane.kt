package io.aequicor.visualization.editor.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.aequicor.visualization.MissionEditorStateHolder
import io.aequicor.visualization.editor.presentation.DefaultProtoTransition
import io.aequicor.visualization.editor.presentation.DesignEditorIntent
import io.aequicor.visualization.editor.presentation.EditorMode
import io.aequicor.visualization.editor.presentation.InspectorSection
import io.aequicor.visualization.editor.presentation.InteractionOp
import io.aequicor.visualization.editor.presentation.MotionOp
import io.aequicor.visualization.editor.presentation.isNodeLocked
import io.aequicor.visualization.editor.presentation.MotionPreset
import io.aequicor.visualization.editor.presentation.ProtoActionKind
import io.aequicor.visualization.editor.presentation.presetKeyframes
import io.aequicor.visualization.editor.ui.strings.LocalStrings
import io.aequicor.visualization.editor.ui.strings.PrototypeStrings
import io.aequicor.visualization.editor.ui.theme.LocalEditorColors
import io.aequicor.visualization.engine.ir.model.DesignAction
import io.aequicor.visualization.engine.ir.model.DesignEasing
import io.aequicor.visualization.engine.ir.model.DesignInteraction
import io.aequicor.visualization.engine.ir.model.DesignNode
import io.aequicor.visualization.engine.ir.model.DesignTransition
import io.aequicor.visualization.engine.ir.model.EasingKind
import io.aequicor.visualization.engine.ir.model.InteractionTrigger
import io.aequicor.visualization.engine.ir.model.TransitionDirection
import io.aequicor.visualization.engine.ir.model.TransitionType

/**
 * The Prototype inspector tab (design-book §19 authoring): the selected node's interactions and
 * motion are edited here and written straight back into SLM through the op-command intents — the
 * user never hand-edits source. Single-selection only (like Fill/Stroke/Typography). All controls
 * are reused from the inspector kit; colors via [LocalEditorColors].
 */
@Composable
internal fun InspectorPrototype(state: MissionEditorStateHolder) {
    val strings = LocalStrings.current
    val node = state.designState.selectedNode
        ?: return EmptyInspector(strings.prototype.emptySelect)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Section(state, InspectorSection.Interactions) { InteractionsSection(state, node) }
        Section(state, InspectorSection.Motion) { MotionSection(state, node) }
        PlayInSceneButton(state)
    }
}

@Composable
private fun InteractionsSection(state: MissionEditorStateHolder, node: DesignNode) {
    val strings = LocalStrings.current
    val nodeId = node.id
    val locked = state.designState.isNodeLocked(nodeId)
    val screens = state.designState.document?.pages?.map { it.id to it.name.ifBlank { it.id } }.orEmpty()
    val canNavigate = screens.size > 1

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeaderAdd(strings.labels.inspectorSection(InspectorSection.Interactions).full) {
            if (!locked && canNavigate) state.dispatch(DesignEditorIntent.InteractionCommand(nodeId, InteractionOp.Add))
        }
        when {
            !canNavigate -> MutedNote(strings.prototype.addScreenFirst)
            node.interactions.isEmpty() -> MutedNote(strings.prototype.noInteractions)
            else -> node.interactions.forEachIndexed { index, interaction ->
                InteractionCard(state, nodeId, index, interaction, screens, locked)
            }
        }
    }
}

@Composable
private fun InteractionCard(
    state: MissionEditorStateHolder,
    nodeId: String,
    index: Int,
    interaction: DesignInteraction,
    screens: List<Pair<String, String>>,
    locked: Boolean,
) {
    val colors = LocalEditorColors.current
    val strings = LocalStrings.current
    fun dispatch(op: InteractionOp) = state.dispatch(DesignEditorIntent.InteractionCommand(nodeId, op))
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = colors.raisedSurface,
        border = BorderStroke(1.dp, colors.panelStroke),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SelectField(
                    value = strings.prototype.trigger(interaction.trigger),
                    options = AuthorableTriggers.map { strings.prototype.trigger(it) },
                    onSelect = { label ->
                        AuthorableTriggers.firstOrNull { strings.prototype.trigger(it) == label }
                            ?.let { if (!locked) dispatch(InteractionOp.SetTrigger(index, it)) }
                    },
                    modifier = Modifier.weight(1f),
                )
                RemoveButton { if (!locked) dispatch(InteractionOp.RemoveAt(index)) }
            }
            if (interaction.trigger == InteractionTrigger.AfterDelay) {
                InspectorCommitNumberField(
                    label = strings.prototype.delay,
                    value = (interaction.delayMs ?: 800.0).toInt().toString(),
                    suffix = strings.prototype.ms,
                    resetKey = "delay-$index-${interaction.delayMs}",
                    enabled = !locked,
                ) { value -> dispatch(InteractionOp.SetTrigger(index, InteractionTrigger.AfterDelay, delayMs = value)) }
            }
            interaction.actions.forEachIndexed { actionIndex, action ->
                ActionEditor(state, nodeId, index, actionIndex, action, interaction.actions.size, screens, locked)
            }
            TinyButton(strings.prototype.addAction, enabled = !locked) { dispatch(InteractionOp.AddAction(index)) }
        }
    }
}

@Composable
private fun ActionEditor(
    state: MissionEditorStateHolder,
    nodeId: String,
    i: Int,
    j: Int,
    action: DesignAction,
    actionCount: Int,
    screens: List<Pair<String, String>>,
    locked: Boolean,
) {
    val colors = LocalEditorColors.current
    val strings = LocalStrings.current
    fun dispatch(op: InteractionOp) = state.dispatch(DesignEditorIntent.InteractionCommand(nodeId, op))
    Column(
        Modifier.fillMaxWidth().padding(start = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SelectField(
                value = strings.prototype.protoAction(actionKindOf(action)),
                options = AuthorableActions.map { strings.prototype.protoAction(it) },
                onSelect = { label ->
                    AuthorableActions.firstOrNull { strings.prototype.protoAction(it) == label }
                        ?.let { if (!locked) dispatch(InteractionOp.SetActionType(i, j, it)) }
                },
                modifier = Modifier.weight(1f),
            )
            if (actionCount > 1) RemoveButton { if (!locked) dispatch(InteractionOp.RemoveAction(i, j)) }
        }
        if (action is DesignAction.Navigate) {
            val transition = action.transition ?: DefaultProtoTransition
            LabeledField(strings.prototype.target) {
                SelectField(
                    value = screens.firstOrNull { it.first == action.to }?.second ?: action.to.ifBlank { strings.prototype.pickScreen },
                    options = screens.map { it.second },
                    onSelect = { label -> screens.firstOrNull { it.second == label }?.first?.let { if (!locked) dispatch(InteractionOp.SetActionTarget(i, j, it)) } },
                    leadingContent = { DropdownMenuIcon(EditorIcon.Screens) },
                    optionLeadingContent = { DropdownMenuIcon(EditorIcon.Screens) },
                )
            }
            LabeledField(strings.prototype.motion) {
                SelectField(
                    value = strings.prototype.transitionType(transition.type),
                    options = AuthorableTransitions.map { strings.prototype.transitionType(it) },
                    onSelect = { label ->
                        AuthorableTransitions.firstOrNull { strings.prototype.transitionType(it) == label }
                            ?.let { if (!locked) dispatch(InteractionOp.SetActionTransition(i, j, transition.copy(type = it))) }
                    },
                )
            }
            SegmentedControl(
                options = TransitionDirection.entries.toList(),
                selected = transition.direction,
                label = { strings.prototype.direction(it) },
                onSelect = { direction -> if (!locked) dispatch(InteractionOp.SetActionTransition(i, j, transition.copy(direction = direction))) },
            )
            LabeledField(strings.prototype.easing) {
                SelectField(
                    value = easingLabel(transition.easing, strings.prototype),
                    options = EasingKind.entries.map { strings.prototype.easingKind(it) },
                    onSelect = { label ->
                        EasingKind.entries.firstOrNull { strings.prototype.easingKind(it) == label }
                            ?.let { if (!locked) dispatch(InteractionOp.SetActionTransition(i, j, transition.copy(easing = DesignEasing.Named(it)))) }
                    },
                )
            }
            InspectorCommitNumberField(
                label = strings.prototype.duration,
                value = transition.durationMs.toInt().toString(),
                suffix = strings.prototype.ms,
                resetKey = "dur-$i-$j-${transition.type}-${transition.durationMs}",
                enabled = !locked,
            ) { value -> dispatch(InteractionOp.SetActionTransition(i, j, transition.copy(durationMs = value))) }
        }
    }
}

@Composable
private fun MotionSection(state: MissionEditorStateHolder, node: DesignNode) {
    val strings = LocalStrings.current
    val nodeId = node.id
    val locked = state.designState.isNodeLocked(nodeId)
    val motion = node.motion
    val fallback = motion?.fallback
    fun dispatch(op: MotionOp) = state.dispatch(DesignEditorIntent.MotionCommand(nodeId, op))

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CheckRow(strings.prototype.animateLayer, motion != null) { on -> if (!locked) dispatch(MotionOp.SetEnabled(on)) }
        if (motion != null) {
            LabeledField(strings.prototype.preset) {
                SelectField(
                    value = MotionPreset.entries.firstOrNull { presetKeyframes(it) == fallback }
                        ?.let { strings.prototype.motionPreset(it) } ?: strings.prototype.motionCustom,
                    options = MotionPreset.entries.map { strings.prototype.motionPreset(it) },
                    onSelect = { label ->
                        MotionPreset.entries.firstOrNull { strings.prototype.motionPreset(it) == label }
                            ?.let { if (!locked) dispatch(MotionOp.SetPreset(it)) }
                    },
                )
            }
            InspectorCommitNumberField(
                label = strings.prototype.duration,
                value = (fallback?.durationMs ?: 0.0).toInt().toString(),
                suffix = strings.prototype.ms,
                resetKey = "mdur-${fallback?.durationMs}",
                enabled = !locked,
            ) { value -> dispatch(MotionOp.SetDuration(value)) }
            CheckRow(strings.prototype.loop, fallback?.loop == true) { on -> if (!locked) dispatch(MotionOp.SetLoop(on)) }
        }
    }
}

@Composable
private fun PlayInSceneButton(state: MissionEditorStateHolder) {
    val colors = LocalEditorColors.current
    val strings = LocalStrings.current
    Box(Modifier.fillMaxWidth().padding(18.dp)) {
        Surface(
            modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { state.updateWorkspace { it.copy(mode = EditorMode.Scene) } },
            shape = RoundedCornerShape(8.dp),
            color = colors.accent,
        ) {
            Text(
                strings.prototype.playInScene,
                modifier = Modifier.fillMaxWidth().padding(vertical = 11.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

// --- enum option sets + label resolution (v1 authorable subset) --------------
// Display strings live in PrototypeStrings; here we only fix the option ORDER and
// map a value back to its enum. Round-trip selects resolve both the option list and
// the matcher through the same `strings.prototype.*` call within one composition.

private val AuthorableTriggers = listOf(
    InteractionTrigger.OnClick,
    InteractionTrigger.OnPress,
    InteractionTrigger.AfterDelay,
)

private val AuthorableActions = listOf(ProtoActionKind.Navigate, ProtoActionKind.Back)

private val AuthorableTransitions = listOf(
    TransitionType.Instant,
    TransitionType.Dissolve,
    TransitionType.Push,
    TransitionType.SlideIn,
    TransitionType.SlideOut,
    TransitionType.MoveIn,
    TransitionType.MoveOut,
    TransitionType.SmartAnimate,
)

private fun actionKindOf(action: DesignAction): ProtoActionKind = when (action) {
    is DesignAction.Navigate -> ProtoActionKind.Navigate
    DesignAction.Back -> ProtoActionKind.Back
    else -> ProtoActionKind.Navigate
}

/** Localized label for the currently applied easing, including non-selectable spring/custom values. */
private fun easingLabel(easing: DesignEasing, strings: PrototypeStrings): String = when (easing) {
    is DesignEasing.Named -> strings.easingKind(easing.kind)
    is DesignEasing.Spring -> strings.easingSpring
    is DesignEasing.CubicBezier -> strings.easingCustom
}
