package io.aequicor.visualization.mission

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MissionParserTest {
    @Test
    fun parsesSampleMission() {
        val result = parseMissionMarkdown(SampleMissionMarkdown)

        val success = assertIs<MissionParseResult.Success>(result)
        assertEquals("Mission Control Onboarding", success.spec.title)
        assertEquals(2, success.spec.screens.size)
        assertEquals("review-flow", success.spec.scenarios.single().id)
        assertEquals(3, success.spec.comments.size)
        assertTrue(success.spec.comments.any { it.targetId == "review-button" })
    }

    @Test
    fun reportsMissingMissionBlock() {
        val result = parseMissionMarkdown("# No spec")

        val failure = assertIs<MissionParseResult.Failure>(result)
        assertTrue(failure.errors.single().message.contains("Expected exactly one"))
    }

    @Test
    fun reportsDuplicateMissionBlocks() {
        val markdown = """
            ```mission-visualization
            version: 1
            title: One
            ```
            ```mission-visualization
            version: 1
            title: Two
            ```
        """.trimIndent()

        val result = parseMissionMarkdown(markdown)

        val failure = assertIs<MissionParseResult.Failure>(result)
        assertTrue(failure.errors.single().message.contains("more than one"))
    }

    @Test
    fun reportsLineAwareIndentationErrors() {
        val markdown = """
            ```mission-visualization
            version: 1
              title: Bad indent
            ```
        """.trimIndent()

        val result = parseMissionMarkdown(markdown)

        val failure = assertIs<MissionParseResult.Failure>(result)
        assertEquals(3, failure.errors.single().line)
        assertTrue(failure.errors.single().message.contains("Unexpected indentation"))
    }

    @Test
    fun reportsUnknownFieldsAsWarnings() {
        val markdown = """
            ```mission-visualization
            version: 1
            title: Unknown fields
            unexpectedRoot: value
            screens: []
            scenarios: []
            ```
        """.trimIndent()

        val result = parseMissionMarkdown(markdown)

        val success = assertIs<MissionParseResult.Success>(result)
        assertEquals(1, success.warnings.size)
        assertTrue(success.warnings.single().message.contains("unexpectedRoot"))
    }

    @Test
    fun screenAndComponentCommentsSurviveExportAndImport() {
        val original = assertIs<MissionParseResult.Success>(parseMissionMarkdown(SampleMissionMarkdown))
        val exported = exportMissionMarkdown(SampleMissionMarkdown, original.spec)

        val imported = assertIs<MissionParseResult.Success>(parseMissionMarkdown(exported))

        assertTrue(imported.spec.comments.any { it.targetId == "dashboard" })
        assertTrue(imported.spec.comments.any { it.targetId == "review-button" })
        assertTrue(imported.spec.comments.any { it.targetId == "prompt-card" })
    }
}
