package io.aequicor.visualization.ui_engine.runtime_state

import io.aequicor.visualization.ui_engine.mv_yaml_source.SampleUiYaml
import io.aequicor.visualization.ui_engine.runtime_state.UiCommand
import io.aequicor.visualization.ui_engine.validator.UiLoadResult
import io.aequicor.visualization.ui_engine.runtime_state.createUiVisualizationState
import io.aequicor.visualization.ui_engine.runtime_state.reduceUiVisualization
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class UiReducerTest {
    @Test
    fun loadYamlSelectsFirstScreenAndNode() {
        val state = createUiVisualizationState(SampleUiYaml)

        assertEquals("dashboard", state.selectedScreenId)
        assertEquals("dashboard-topbar", state.selectedNodeId)
        assertIs<UiLoadResult.Success>(state.loadResult)
    }

    @Test
    fun selectingNodeAlsoSelectsOwningScreen() {
        val state = reduceUiVisualization(
            createUiVisualizationState(SampleUiYaml),
            UiCommand.SelectNode("prompt-card"),
        )

        assertEquals("prompt-review", state.selectedScreenId)
        assertEquals("prompt-card", state.selectedNodeId)
    }

    @Test
    fun selectingScreenMovesSelectionToFirstScreenNode() {
        val state = reduceUiVisualization(
            createUiVisualizationState(SampleUiYaml),
            UiCommand.SelectScreen("prompt-review"),
        )

        assertEquals("prompt-review", state.selectedScreenId)
        assertEquals("prompt-tabs", state.selectedNodeId)
    }

    @Test
    fun inputValueUpdatesByNodeId() {
        val selected = reduceUiVisualization(
            createUiVisualizationState(SampleUiYaml),
            UiCommand.SelectNode("feedback-input"),
        )

        val updated = reduceUiVisualization(
            selected,
            UiCommand.UpdateInputValue("feedback-input", "Need stronger empty state copy"),
        )

        assertEquals("dashboard", updated.selectedScreenId)
        assertEquals("feedback-input", updated.selectedNodeId)
        assertEquals("Need stronger empty state copy", updated.inputValues["feedback-input"])
    }

    @Test
    fun addCommentUpdatesDocumentState() {
        val withDraft = reduceUiVisualization(
            createUiVisualizationState(SampleUiYaml),
            UiCommand.UpdateDraftComment("Make this action read as the next obvious step."),
        )

        val updated = reduceUiVisualization(withDraft, UiCommand.AddComment("review-button"))

        val document = assertIs<UiLoadResult.Success>(updated.loadResult).document
        assertEquals("", updated.draftComment)
        assertTrue(document.comments.any { it.targetId == "review-button" && it.body.contains("next obvious step") })
    }

    @Test
    fun generatePromptUsesSelectedIrTargetAndComments() {
        val selected = reduceUiVisualization(
            createUiVisualizationState(SampleUiYaml),
            UiCommand.SelectNode("review-button"),
        )

        val prompted = reduceUiVisualization(selected, UiCommand.GeneratePrompt())

        val prompt = prompted.latestPrompt
        requireNotNull(prompt)
        assertEquals("review-button", prompt.targetId)
        assertTrue(prompt.body.contains("Node: button"))
        assertTrue(prompt.body.contains("Review scenario"))
        assertTrue(prompt.body.contains("primary CTA"))
    }
}
