package io.aequicor.visualization.editor.domain

/** Stable identifiers for the canonical SLM skill and its optional specialist extensions. */
internal enum class AgentSkillId {
    SLM,
    DIAGRAMS,
    VECTOR_GRAPHICS,
    TYPOGRAPHY,
    ANNOTATIONS,
    EDITOR,
}

/** One Markdown skill embedded in the application at build time. */
internal data class AgentSkill(
    val id: AgentSkillId,
    val title: String,
    val sourcePath: String,
    val markdown: String,
    val isRequired: Boolean,
)

/** Optional skills selected for an agent file. The base [AgentSkillId.SLM] skill is implicit. */
internal data class AgentFileSelection(
    val includedSkillIds: Set<AgentSkillId> = emptySet(),
)
