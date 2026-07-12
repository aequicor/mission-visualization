package io.aequicor.visualization.editor.data

import io.aequicor.visualization.editor.domain.AgentFileSelection

/**
 * Composes a self-contained agent instruction file from the build-generated skill catalog.
 *
 * The output deliberately has no file-name parameter: callers use the same bytes for
 * `AGENTS.md`, `CLAUDE.md`, and clipboard export.
 */
internal fun composeAgentFile(selection: AgentFileSelection): String {
    val selectedSkills = AgentSkillCatalog.all.filter { skill ->
        skill.isRequired || skill.id in selection.includedSkillIds
    }

    return buildString {
        appendLine("# SLM/CNL agent instructions")
        appendLine()
        appendLine("This file is generated from the canonical SLM skills and is self-contained.")
        selectedSkills.forEach { skill ->
            appendLine()
            appendLine("## Included skill: ${skill.title}")
            appendLine()
            append(prepareAgentSkillMarkdown(skill.markdown))
            appendLine()
        }
    }.trimEnd() + "\n"
}

/**
 * Produces standalone instructions: skill source files do not exist beside an exported
 * AGENTS.md/CLAUDE.md, so source-only routing and Markdown links must not escape the bundle.
 */
internal fun prepareAgentSkillMarkdown(markdown: String): String {
    val withoutFrontmatter = stripYamlFrontMatter(markdown)
    val withoutSourceRouter = withoutFrontmatter.replace(SPECIALIST_SOURCE_ROUTER, "")
    return withoutSourceRouter
        .replace(INTERNAL_BASE_SKILL_LINK, "the base SLM instructions above")
        .replace(INTERNAL_SPECIALIST_SKILL_LINK, "the relevant included subsystem instructions")
        .trim()
}

/** Removes only a well-formed YAML frontmatter block at the start of a Markdown document. */
internal fun stripYamlFrontMatter(markdown: String): String {
    val normalized = markdown
        .removePrefix("\uFEFF")
        .replace("\r\n", "\n")
        .replace('\r', '\n')
    val lines = normalized.lines()
    if (lines.firstOrNull()?.trim() != "---") return normalized.trim()

    val closingDelimiter = lines.indexOfFirstFrom(startIndex = 1) { it.trim() == "---" }
    if (closingDelimiter < 0) return normalized.trim()
    return lines.drop(closingDelimiter + 1).joinToString("\n").trim()
}

private inline fun <T> List<T>.indexOfFirstFrom(startIndex: Int, predicate: (T) -> Boolean): Int {
    for (index in startIndex until size) {
        if (predicate(this[index])) return index
    }
    return -1
}

private val SPECIALIST_SOURCE_ROUTER = Regex(
    pattern = "(?ms)^Use the specialist guides.*?(?=^## )",
)
private val INTERNAL_BASE_SKILL_LINK = Regex(
    pattern = "\\[SLM(?:\\.md)?]\\((?:SKILLS/)?SLM\\.md\\)",
    option = RegexOption.IGNORE_CASE,
)
private val INTERNAL_SPECIALIST_SKILL_LINK = Regex(
    pattern = "\\[SLM-[^]]+]\\((?:SKILLS/)?SLM-[^)]+\\.md\\)",
    option = RegexOption.IGNORE_CASE,
)
