package io.aequicor.visualization.mission

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MissionReducerTest {
    @Test
    fun loadDocumentSelectsFirstScreenAndComponent() {
        val state = createMissionVisualizationState(SampleMissionMarkdown)

        assertEquals("dashboard", state.selectedScreenId)
        assertEquals("dashboard-topbar", state.selectedComponentId)
        assertIs<MissionParseResult.Success>(state.parseResult)
    }

    @Test
    fun selectScreenMovesSelectionToFirstScreenComponent() {
        val state = reduceMissionVisualization(
            createMissionVisualizationState(SampleMissionMarkdown),
            VisualizationCommand.SelectScreen("prompt-review"),
        )

        assertEquals("prompt-review", state.selectedScreenId)
        assertEquals("prompt-tabs", state.selectedComponentId)
    }

    @Test
    fun selectingComponentAlsoSelectsOwningScreen() {
        val state = reduceMissionVisualization(
            createMissionVisualizationState(SampleMissionMarkdown),
            VisualizationCommand.SelectComponent("prompt-card"),
        )

        assertEquals("prompt-review", state.selectedScreenId)
        assertEquals("prompt-card", state.selectedComponentId)
    }

    @Test
    fun selectingScenarioStepTargetNavigatesToScreenAndComponent() {
        val state = reduceMissionVisualization(
            createMissionVisualizationState(SampleMissionMarkdown),
            VisualizationCommand.SelectTarget("prompt-card"),
        )

        assertEquals("prompt-review", state.selectedScreenId)
        assertEquals("prompt-card", state.selectedComponentId)
    }

    @Test
    fun selectingCommentTargetNavigatesToScreenAndComponent() {
        val state = reduceMissionVisualization(
            createMissionVisualizationState(SampleMissionMarkdown),
            VisualizationCommand.SelectTarget("review-button"),
        )

        assertEquals("dashboard", state.selectedScreenId)
        assertEquals("review-button", state.selectedComponentId)
    }

    @Test
    fun selectingScreenTargetResetsToFirstScreenComponent() {
        val state = reduceMissionVisualization(
            createMissionVisualizationState(SampleMissionMarkdown),
            VisualizationCommand.SelectTarget("prompt-review"),
        )

        assertEquals("prompt-review", state.selectedScreenId)
        assertEquals("prompt-tabs", state.selectedComponentId)
    }

    @Test
    fun inputPreviewValueUpdatesWithoutBreakingSelection() {
        val selected = reduceMissionVisualization(
            createMissionVisualizationState(SampleMissionMarkdown),
            VisualizationCommand.SelectTarget("feedback-input"),
        )

        val updated = reduceMissionVisualization(
            selected,
            VisualizationCommand.UpdateInputValue("feedback-input", "Need stronger empty state copy"),
        )

        assertEquals("dashboard", updated.selectedScreenId)
        assertEquals("feedback-input", updated.selectedComponentId)
        assertEquals("Need stronger empty state copy", updated.inputValues["feedback-input"])
    }

    @Test
    fun addCommentUpdatesSpecAndMarkdown() {
        val withDraft = reduceMissionVisualization(
            createMissionVisualizationState(SampleMissionMarkdown),
            VisualizationCommand.UpdateDraftComment("Make this action read as the next obvious step."),
        )

        val updated = reduceMissionVisualization(withDraft, VisualizationCommand.AddComment("review-button"))

        val success = assertIs<MissionParseResult.Success>(updated.parseResult)
        assertEquals("", updated.draftComment)
        assertTrue(success.spec.comments.any { it.targetId == "review-button" && it.body.contains("next obvious step") })
        assertTrue(updated.markdown.contains("next obvious step"))
    }

    @Test
    fun generateGlobalPromptIncludesAllCommentsAcrossScreensAndComponents() {
        val state = reduceMissionVisualization(
            createMissionVisualizationState(SampleMissionMarkdown),
            VisualizationCommand.SelectComponent("review-button"),
        )

        val prompted = reduceMissionVisualization(state, VisualizationCommand.GeneratePrompt())

        val prompt = prompted.latestPrompt
        requireNotNull(prompt)
        assertTrue(prompt.body.contains("Lazurite"))
        assertTrue(prompt.body.contains("dashboard"))
        assertTrue(prompt.body.contains("review-button"))
        assertTrue(prompt.body.contains("prompt-card"))
        assertTrue(prompt.body.contains("next scenario step"))
        assertTrue(prompt.body.contains("primary CTA"))
        assertTrue(prompt.body.contains("clearer separation"))
    }

    @Test
    fun explicitTargetPromptRemainsTargetSpecific() {
        val prompted = reduceMissionVisualization(
            createMissionVisualizationState(SampleMissionMarkdown),
            VisualizationCommand.GeneratePrompt("review-button"),
        )

        val prompt = prompted.latestPrompt
        requireNotNull(prompt)
        assertEquals("review-button", prompt.targetId)
        assertTrue(prompt.body.contains("primary CTA"))
        assertFalse(prompt.body.contains("clearer separation"))
    }

    @Test
    fun exportDocumentProducesMissionBlock() {
        val exported = reduceMissionVisualization(
            createMissionVisualizationState(SampleMissionMarkdown),
            VisualizationCommand.ExportDocument,
        )

        assertTrue(exported.exportedMarkdown.contains("```mission-visualization"))
        assertTrue(exported.exportedMarkdown.contains("Mission Control Onboarding"))
    }
}
