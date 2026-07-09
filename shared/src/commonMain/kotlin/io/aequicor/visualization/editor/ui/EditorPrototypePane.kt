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
import io.aequicor.visualization.editor.ui.theme.LocalEditorColors
import io.aequicor.visualization.engine.ir.model.DesignAction
import io.aequicor.visualization.engine.ir.model.DesignEasing
import io.aequicor.visualization.engine.ir.model.DesignInteraction
import io.aequicor.visualization.engine.ir.model.DesignNode
import io.aequicor.visualization.engine.ir.model.DesignTransition
import io.aequicor.visualization.engine.ir.model.EasingKind
import io.aequicor.visualization.engine.ir.model.InteractionTrigger
import io.aequicor.visualization.engine.ir.model.MotionKeyframes
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
    val node = state.designState.selectedNode
        ?: return EmptyInspector("Select an object to add behavior.")
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Section(state, InspectorSection.Interactions) { InteractionsSection(state, node) }
        Section(state, InspectorSection.Motion) { MotionSection(state, node) }
        PlayInSceneButton(state)
    }
}

@Composable
private fun InteractionsSection(state: MissionEditorStateHolder, node: DesignNode) {
    val nodeId = node.id
    val locked = state.designState.isNodeLocked(nodeId)
    val screens = state.designState.document?.pages?.map { it.id to it.name.ifBlank { it.id } }.orEmpty()
    val canNavigate = screens.size > 1

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeaderAdd("Interactions") {
            if (!locked && canNavigate) state.dispatch(DesignEditorIntent.InteractionCommand(nodeId, InteractionOp.Add))
        }
        when {
            !canNavigate -> MutedNote("Add another screen first, then wire a tap to it.")
            node.interactions.isEmpty() -> MutedNote("No interactions yet. Add On click → Navigate with +, then press Play.")
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
                    value = triggerLabel(interaction.trigger),
                    options = TriggerLabels,
                    onSelect = { label -> triggerFromLabel(label)?.let { if (!locked) dispatch(InteractionOp.SetTrigger(index, it)) } },
                    modifier = Modifier.weight(1f),
                )
                RemoveButton { if (!locked) dispatch(InteractionOp.RemoveAt(index)) }
            }
            if (interaction.trigger == InteractionTrigger.AfterDelay) {
                InspectorCommitNumberField(
                    label = "Delay",
                    value = (interaction.delayMs ?: 800.0).toInt().toString(),
                    suffix = "ms",
                    resetKey = "delay-$index-${interaction.delayMs}",
                    enabled = !locked,
                ) { value -> dispatch(InteractionOp.SetTrigger(index, InteractionTrigger.AfterDelay, delayMs = value)) }
            }
            interaction.actions.forEachIndexed { actionIndex, action ->
                ActionEditor(state, nodeId, index, actionIndex, action, interaction.actions.size, screens, locked)
            }
            TinyButton("+ Add action", enabled = !locked) { dispatch(InteractionOp.AddAction(index)) }
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
    fun dispatch(op: InteractionOp) = state.dispatch(DesignEditorIntent.InteractionCommand(nodeId, op))
    Column(
        Modifier.fillMaxWidth().padding(start = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SelectField(
                value = actionKindLabel(action),
                options = ActionLabels,
                onSelect = { label -> actionKindFromLabel(label)?.let { if (!locked) dispatch(InteractionOp.SetActionType(i, j, it)) } },
                modifier = Modifier.weight(1f),
            )
            if (actionCount > 1) RemoveButton { if (!locked) dispatch(InteractionOp.RemoveAction(i, j)) }
        }
        if (action is DesignAction.Navigate) {
            val transition = action.transition ?: DefaultProtoTransition
            LabeledField("Target") {
                SelectField(
                    value = screens.firstOrNull { it.first == action.to }?.second ?: action.to.ifBlank { "Pick a screen" },
                    options = screens.map { it.second },
                    onSelect = { label -> screens.firstOrNull { it.second == label }?.first?.let { if (!locked) dispatch(InteractionOp.SetActionTarget(i, j, it)) } },
                )
            }
            LabeledField("Motion") {
                SelectField(
                    value = transitionTypeLabel(transition.type),
                    options = TransitionLabels,
                    onSelect = { label -> transitionTypeFromLabel(label)?.let { if (!locked) dispatch(InteractionOp.SetActionTransition(i, j, transition.copy(type = it))) } },
                )
            }
            SegmentedControl(
                options = TransitionDirection.entries.toList(),
                selected = transition.direction,
                label = ::directionLabel,
                onSelect = { direction -> if (!locked) dispatch(InteractionOp.SetActionTransition(i, j, transition.copy(direction = direction))) },
            )
            LabeledField("Easing") {
                SelectField(
                    value = easingLabel(transition.easing),
                    options = EasingLabels,
                    onSelect = { label -> easingFromLabel(label)?.let { if (!locked) dispatch(InteractionOp.SetActionTransition(i, j, transition.copy(easing = DesignEasing.Named(it)))) } },
                )
            }
            InspectorCommitNumberField(
                label = "Duration",
                value = transition.durationMs.toInt().toString(),
                suffix = "ms",
                resetKey = "dur-$i-$j-${transition.type}-${transition.durationMs}",
                enabled = !locked,
            ) { value -> dispatch(InteractionOp.SetActionTransition(i, j, transition.copy(durationMs = value))) }
        }
    }
}

@Composable
private fun MotionSection(state: MissionEditorStateHolder, node: DesignNode) {
    val nodeId = node.id
    val locked = state.designState.isNodeLocked(nodeId)
    val motion = node.motion
    val fallback = motion?.fallback
    fun dispatch(op: MotionOp) = state.dispatch(DesignEditorIntent.MotionCommand(nodeId, op))

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CheckRow("Animate this layer", motion != null) { on -> if (!locked) dispatch(MotionOp.SetEnabled(on)) }
        if (motion != null) {
            LabeledField("Preset") {
                SelectField(
                    value = presetLabelOf(fallback),
                    options = PresetLabels,
                    onSelect = { label -> presetFromLabel(label)?.let { if (!locked) dispatch(MotionOp.SetPreset(it)) } },
                )
            }
            InspectorCommitNumberField(
                label = "Duration",
                value = (fallback?.durationMs ?: 0.0).toInt().toString(),
                suffix = "ms",
                resetKey = "mdur-${fallback?.durationMs}",
                enabled = !locked,
            ) { value -> dispatch(MotionOp.SetDuration(value)) }
            CheckRow("Loop", fallback?.loop == true) { on -> if (!locked) dispatch(MotionOp.SetLoop(on)) }
        }
    }
}

@Composable
private fun PlayInSceneButton(state: MissionEditorStateHolder) {
    val colors = LocalEditorColors.current
    Box(Modifier.fillMaxWidth().padding(18.dp)) {
        Surface(
            modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { state.updateWorkspace { it.copy(mode = EditorMode.Scene) } },
            shape = RoundedCornerShape(8.dp),
            color = colors.accent,
        ) {
            Text(
                "▶  Play in Scene",
                modifier = Modifier.fillMaxWidth().padding(vertical = 11.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

// --- enum <-> label maps (v1 authorable subset) ------------------------------

private val TriggerLabels = listOf("On click", "On press", "After delay")

private fun triggerLabel(trigger: InteractionTrigger): String = when (trigger) {
    InteractionTrigger.OnClick -> "On click"
    InteractionTrigger.OnPress -> "On press"
    InteractionTrigger.AfterDelay -> "After delay"
    else -> trigger.name
}

private fun triggerFromLabel(label: String): InteractionTrigger? = when (label) {
    "On click" -> InteractionTrigger.OnClick
    "On press" -> InteractionTrigger.OnPress
    "After delay" -> InteractionTrigger.AfterDelay
    else -> null
}

private val ActionLabels = listOf("Navigate", "Back")

private fun actionKindLabel(action: DesignAction): String = when (action) {
    is DesignAction.Navigate -> "Navigate"
    DesignAction.Back -> "Back"
    else -> "Navigate"
}

private fun actionKindFromLabel(label: String): ProtoActionKind? = when (label) {
    "Navigate" -> ProtoActionKind.Navigate
    "Back" -> ProtoActionKind.Back
    else -> null
}

private val TransitionLabels = listOf("Instant", "Dissolve", "Push", "Slide in", "Slide out", "Move in", "Move out", "Smart animate")

private fun transitionTypeLabel(type: TransitionType): String = when (type) {
    TransitionType.Instant -> "Instant"
    TransitionType.Dissolve -> "Dissolve"
    TransitionType.Push -> "Push"
    TransitionType.SlideIn -> "Slide in"
    TransitionType.SlideOut -> "Slide out"
    TransitionType.MoveIn -> "Move in"
    TransitionType.MoveOut -> "Move out"
    TransitionType.SmartAnimate -> "Smart animate"
}

private fun transitionTypeFromLabel(label: String): TransitionType? = when (label) {
    "Instant" -> TransitionType.Instant
    "Dissolve" -> TransitionType.Dissolve
    "Push" -> TransitionType.Push
    "Slide in" -> TransitionType.SlideIn
    "Slide out" -> TransitionType.SlideOut
    "Move in" -> TransitionType.MoveIn
    "Move out" -> TransitionType.MoveOut
    "Smart animate" -> TransitionType.SmartAnimate
    else -> null
}

private fun directionLabel(direction: TransitionDirection): String = when (direction) {
    TransitionDirection.Left -> "Left"
    TransitionDirection.Right -> "Right"
    TransitionDirection.Top -> "Top"
    TransitionDirection.Bottom -> "Bottom"
}

private val EasingLabels = listOf("Linear", "Ease in", "Ease out", "Ease in out", "Ease in back", "Ease out back")

private fun easingLabel(easing: DesignEasing): String = when (easing) {
    is DesignEasing.Named -> when (easing.kind) {
        EasingKind.Linear -> "Linear"
        EasingKind.EaseIn -> "Ease in"
        EasingKind.EaseOut -> "Ease out"
        EasingKind.EaseInOut -> "Ease in out"
        EasingKind.EaseInBack -> "Ease in back"
        EasingKind.EaseOutBack -> "Ease out back"
    }
    is DesignEasing.Spring -> "Spring"
    is DesignEasing.CubicBezier -> "Custom curve"
}

private fun easingFromLabel(label: String): EasingKind? = when (label) {
    "Linear" -> EasingKind.Linear
    "Ease in" -> EasingKind.EaseIn
    "Ease out" -> EasingKind.EaseOut
    "Ease in out" -> EasingKind.EaseInOut
    "Ease in back" -> EasingKind.EaseInBack
    "Ease out back" -> EasingKind.EaseOutBack
    else -> null
}

private val PresetLabels = listOf("Fade in", "Pop", "Float", "Pulse", "Spin")

private fun presetLabel(preset: MotionPreset): String = when (preset) {
    MotionPreset.FadeIn -> "Fade in"
    MotionPreset.Pop -> "Pop"
    MotionPreset.Float -> "Float"
    MotionPreset.Pulse -> "Pulse"
    MotionPreset.Spin -> "Spin"
}

private fun presetFromLabel(label: String): MotionPreset? = when (label) {
    "Fade in" -> MotionPreset.FadeIn
    "Pop" -> MotionPreset.Pop
    "Float" -> MotionPreset.Float
    "Pulse" -> MotionPreset.Pulse
    "Spin" -> MotionPreset.Spin
    else -> null
}

/** Shows which preset the clip currently matches, else "Custom" once duration/loop are tweaked. */
private fun presetLabelOf(fallback: MotionKeyframes?): String =
    MotionPreset.entries.firstOrNull { presetKeyframes(it) == fallback }?.let(::presetLabel) ?: "Custom"
