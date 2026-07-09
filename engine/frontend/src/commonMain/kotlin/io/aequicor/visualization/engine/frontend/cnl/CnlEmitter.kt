package io.aequicor.visualization.engine.frontend.cnl

import io.aequicor.visualization.engine.ir.model.DesignDocument
import io.aequicor.visualization.engine.ir.model.DesignNode
import io.aequicor.visualization.engine.ir.model.DesignNodeKind

/**
 * Deterministic IR → CNL generator: the exact inverse of [CnlParser], reading the same
 * [CnlGrammar] registry so a keyword works in both directions. Every node renders to exactly
 * one sentence; tree structure comes from markdown heading nesting. This is the "generate"
 * half of full bidirectional CNL — it powers write-back of new nodes, whole-document
 * regeneration (YAML→CNL migration), and round-trip tests (`parse(emit(node)) ≡ node`).
 *
 * P0 emits the buckets the grammar registry currently covers; later phases extend the
 * registry and this emitter grows automatically with it.
 */
internal object CnlEmitter {
    /** Renders [node] as one CNL sentence: `Noun [id …] [«text»] phrase…`. */
    fun emitSentence(node: DesignNode, includeId: Boolean = false): String {
        val parts = mutableListOf(CnlGrammar.canonicalNoun(node) ?: "Frame")
        if (includeId && node.id.isNotEmpty()) parts += "id ${node.id}"
        CnlGrammar.textLiteral(node)?.let { parts += "«$it»" }
        parts += phrasesOf(node)
        return parts.joinToString(" ")
    }

    /** The property phrases of [node] in canonical order (no noun/name) — a heading suffix. */
    fun emitHeadingSuffix(node: DesignNode): String = phrasesOf(node).joinToString(" ")

    /** A container heading line at markdown [level]: `## Name suffix`. */
    fun emitHeadingLine(node: DesignNode, level: Int): String {
        val name = node.name.ifEmpty { "Section" }
        val suffix = emitHeadingSuffix(node)
        val heading = "#".repeat(level.coerceAtLeast(1))
        return if (suffix.isEmpty()) "$heading $name" else "$heading $name $suffix"
    }

    /**
     * Emits [node] as CNL source lines: a container becomes a heading followed by its
     * children (each recursively), a leaf becomes a single sentence. Blank lines separate
     * blocks so each element parses independently.
     */
    fun emitSubtree(node: DesignNode, level: Int, includeId: Boolean = false): List<String> {
        if (!isContainer(node)) return listOf(emitSentence(node, includeId))
        val lines = mutableListOf(emitHeadingLine(node, level))
        node.children.forEach { child ->
            lines += ""
            lines += emitSubtree(child, level + 1, includeId)
        }
        return lines
    }

    /**
     * Regenerates the CNL **body** (markdown heading tree + one sentence per node) for every
     * screen in [document]. This is the layout-authoring surface; document-level dictionaries
     * (variable collections, component definitions, shared styles) and the `---` frontmatter are
     * emitted separately by the migration harness. Node ids are included so a recompile keeps the
     * same id set (structural stability).
     */
    fun emitDocument(document: DesignDocument, includeId: Boolean = true): String =
        document.pages
            .flatMap { it.children }
            .joinToString("\n\n") { screen -> emitSubtree(screen, level = 1, includeId).joinToString("\n") }

    /** A node is a container (heading) when it is a frame or has children. */
    fun isContainer(node: DesignNode): Boolean =
        node.kind is DesignNodeKind.Frame || node.children.isNotEmpty()

    private fun phrasesOf(node: DesignNode): List<String> =
        CnlGrammar.descriptors.sortedBy { it.order }.mapNotNull { it.render(node) }
}
