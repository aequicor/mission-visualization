package io.aequicor.visualization.editor.data

import io.aequicor.visualization.editor.domain.AgentFileSelection
import io.aequicor.visualization.editor.domain.AgentSkillId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AgentFileComposerTest {

    @Test
    fun baseSkillIsAlwaysIncluded() {
        val output = composeAgentFile(AgentFileSelection())

        assertTrue(output.contains(sectionHeading(AgentSkillId.SLM)))
        AgentSkillCatalog.specialists.forEach { skill ->
            assertFalse(output.contains(sectionHeading(skill.id)))
        }
    }

    @Test
    fun oneSelectedSpecialistIsIncludedAfterBase() {
        val output = composeAgentFile(
            AgentFileSelection(includedSkillIds = setOf(AgentSkillId.DIAGRAMS)),
        )

        assertTrue(output.indexOf(sectionHeading(AgentSkillId.SLM)) < output.indexOf(sectionHeading(AgentSkillId.DIAGRAMS)))
        AgentSkillCatalog.specialists.filterNot { it.id == AgentSkillId.DIAGRAMS }.forEach { skill ->
            assertFalse(output.contains(sectionHeading(skill.id)))
        }
    }

    @Test
    fun allSpecialistsUseStableCatalogOrder() {
        val expectedOrder = listOf(
            AgentSkillId.SLM,
            AgentSkillId.DIAGRAMS,
            AgentSkillId.VECTOR_GRAPHICS,
            AgentSkillId.TYPOGRAPHY,
            AgentSkillId.ANNOTATIONS,
            AgentSkillId.EDITOR,
        )
        val output = composeAgentFile(
            AgentFileSelection(includedSkillIds = AgentSkillId.entries.toSet()),
        )
        val sectionOffsets = AgentSkillCatalog.all.map { skill -> output.indexOf(sectionHeading(skill.id)) }

        assertEquals(expectedOrder, AgentSkillCatalog.all.map { it.id })
        assertTrue(sectionOffsets.all { it >= 0 })
        assertEquals(sectionOffsets.sorted(), sectionOffsets)
    }

    @Test
    fun yamlFrontmatterIsNotEmitted() {
        val output = composeAgentFile(
            AgentFileSelection(includedSkillIds = AgentSkillId.entries.toSet()),
        )

        AgentSkillCatalog.all.forEach { skill ->
            val contentAfterHeading = output.substringAfter(sectionHeading(skill.id))
            assertFalse(
                contentAfterHeading.startsWith("\n---\n"),
                "Frontmatter leaked from ${skill.sourcePath}",
            )
        }
    }

    @Test
    fun outputDoesNotReferenceUnavailableSkillFilesOrUnselectedRouter() {
        val baseOnly = composeAgentFile(AgentFileSelection())
        val allSkills = composeAgentFile(
            AgentFileSelection(includedSkillIds = AgentSkillId.entries.toSet()),
        )

        listOf(baseOnly, allSkills).forEach { output ->
            assertFalse("Source:" in output)
            assertFalse("SKILLS/" in output)
            assertFalse(Regex("SLM-[A-Za-z-]+\\.md").containsMatchIn(output))
            assertFalse("Use the specialist guides" in output)
        }
        AgentSkillCatalog.specialists.forEach { skill ->
            assertFalse(baseOnly.contains(sectionHeading(skill.id)))
        }
    }

    @Test
    fun sourceOnlySkillLinksAreNeutralizedDefensively() {
        val prepared = prepareAgentSkillMarkdown(
            """
            ---
            name: sample
            description: sample
            ---

            Follow [SLM.md](SLM.md), then [SLM-diagrams.md](SLM-diagrams.md).
            """.trimIndent(),
        )

        assertEquals(
            "Follow the base SLM instructions above, then the relevant included subsystem instructions.",
            prepared,
        )
    }

    @Test
    fun requiredSkillAndSelectedSpecialistsAreNotDuplicated() {
        val output = composeAgentFile(
            AgentFileSelection(includedSkillIds = AgentSkillId.entries.toSet()),
        )

        AgentSkillCatalog.all.forEach { skill ->
            assertEquals(1, output.countOccurrences(sectionHeading(skill.id)))
        }
    }

    @Test
    fun frontmatterStripperLeavesOrdinaryMarkdownAndMalformedFrontmatterIntact() {
        assertEquals("# Body", stripYamlFrontMatter("# Body\n"))
        assertEquals("---\nname: unfinished\n# Body", stripYamlFrontMatter("---\nname: unfinished\n# Body\n"))
        assertEquals("# Body", stripYamlFrontMatter("---\r\nname: skill\r\n---\r\n\r\n# Body\r\n"))
    }

    private fun sectionHeading(id: AgentSkillId): String {
        val skill = AgentSkillCatalog.all.single { it.id == id }
        return "## Included skill: ${skill.title}\n"
    }

    private fun String.countOccurrences(needle: String): Int {
        var count = 0
        var offset = 0
        while (true) {
            val match = indexOf(needle, startIndex = offset)
            if (match < 0) return count
            count += 1
            offset = match + needle.length
        }
    }
}
