package io.aequicor.visualization.mcp

import io.aequicor.visualization.editor.data.composeAgentFile
import io.aequicor.visualization.editor.data.AgentSkillCatalog
import io.aequicor.visualization.editor.domain.AgentFileSelection
import io.aequicor.visualization.editor.domain.AgentSkillId

/** A self-contained instruction bundle generated from the canonical repository SLM skills. */
data class SlmSkillBundle(
    val selector: String,
    val includedSkills: List<String>,
    val files: List<SlmSkillFile>,
    val markdown: String,
)

data class SlmSkillFile(
    val name: String,
    val markdown: String,
)

/**
 * Returns the base SLM instructions plus either every specialist or one selected specialist.
 * The Markdown is sourced from the build-generated skill catalog, so MCP and editor exports
 * always expose the same authoring contract.
 */
fun getSlmSkillBundle(selector: String = "all"): SlmSkillBundle {
    val normalized = selector.trim().lowercase().replace('-', '_')
    val specialists = when (normalized) {
        "all" -> AgentSkillId.entries.filterNot { it == AgentSkillId.SLM }.toSet()
        "slm" -> emptySet()
        "diagrams" -> setOf(AgentSkillId.DIAGRAMS)
        "vector_graphics" -> setOf(AgentSkillId.VECTOR_GRAPHICS)
        "typography" -> setOf(AgentSkillId.TYPOGRAPHY)
        "annotations" -> setOf(AgentSkillId.ANNOTATIONS)
        "editor" -> setOf(AgentSkillId.EDITOR)
        else -> throw IllegalArgumentException(
            "skill must be all, slm, diagrams, vector_graphics, typography, annotations, or editor",
        )
    }
    val included = listOf(AgentSkillId.SLM) + AgentSkillId.entries.filter { it in specialists }
    return SlmSkillBundle(
        selector = normalized,
        includedSkills = included.map { it.name.lowercase() },
        files = AgentSkillCatalog.all.filter { it.id in included }.map { skill ->
            SlmSkillFile(
                name = when (skill.id) {
                    AgentSkillId.SLM -> "slm"
                    AgentSkillId.DIAGRAMS -> "slm-diagrams"
                    AgentSkillId.VECTOR_GRAPHICS -> "slm-vector-graphics"
                    AgentSkillId.TYPOGRAPHY -> "slm-typography"
                    AgentSkillId.ANNOTATIONS -> "slm-annotations"
                    AgentSkillId.EDITOR -> "slm-editor"
                },
                markdown = skill.markdown.removePrefix("\uFEFF").trimEnd() + "\n",
            )
        },
        markdown = composeAgentFile(AgentFileSelection(includedSkillIds = specialists)),
    )
}
