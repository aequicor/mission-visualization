package io.aequicor.visualization.agent

import io.aequicor.visualization.editor.domain.MissionDocumentSource
import io.aequicor.visualization.editor.domain.compileMissionDocuments
import io.aequicor.visualization.editor.presentation.DesignEditorIntent
import io.aequicor.visualization.editor.presentation.DesignEditorState
import io.aequicor.visualization.editor.presentation.ScreenPreset
import io.aequicor.visualization.editor.presentation.createDesignEditorState
import io.aequicor.visualization.editor.presentation.reduceDesignEditor
import io.aequicor.visualization.engine.ir.layout.DesignLayoutEngine
import io.aequicor.visualization.engine.ir.layout.LayoutBox
import io.aequicor.visualization.engine.ir.model.DesignDiagnostic
import io.aequicor.visualization.engine.ir.model.DesignDocument
import io.aequicor.visualization.engine.ir.model.DesignSeverity
import io.aequicor.visualization.engine.ir.projection.ScreenComposer
import io.aequicor.visualization.engine.ir.validate.validateDesignDocument

/** One screen as the agent surface reports it. */
data class ScreenSummary(
    val id: String,
    val name: String,
    val width: Double?,
    val height: Double?,
    val nodeCount: Int,
    val sourceFile: String?,
)

/**
 * Headless facade over the pure editor pipeline: compile `*.layout.md` sources →
 * [DesignEditorState], answer read queries (screens/inspect/validate) and run the
 * few write intents the console exposes. No Compose, no UI — the same pure functions
 * the editor reducer tests drive.
 */
class AgentSession(initialSources: List<MissionDocumentSource>) {

    var state: DesignEditorState = createDesignEditorState(compileMissionDocuments(initialSources))
        private set

    val document: DesignDocument?
        get() = state.document

    fun sources(): List<MissionDocumentSource> = state.sources

    /** Applies one editor intent through the pure reducer (write-back included). */
    fun dispatch(intent: DesignEditorIntent) {
        state = reduceDesignEditor(state, intent)
    }

    fun screens(): List<ScreenSummary> {
        val doc = document ?: return emptyList()
        return doc.pages.map { page ->
            val root = page.children.firstOrNull()
            ScreenSummary(
                id = page.id,
                name = page.name.ifBlank { root?.name.orEmpty() },
                width = root?.size?.width,
                height = root?.size?.height,
                nodeCount = page.allNodes().size,
                sourceFile = sourceFileFor(page.id),
            )
        }
    }

    /**
     * Resolves + lays out [screenId] (Compose-free `ScreenComposer` path) and returns the
     * geometry tree; [nodeId] narrows to a subtree. Text-dependent (hug) sizes use the
     * approximate measurer, so text-driven extents are close, not pixel-exact.
     */
    fun inspect(screenId: String, nodeId: String? = null): LayoutBox? {
        val doc = document ?: return null
        val composed = ScreenComposer(doc, DesignLayoutEngine()).compose(screenId) ?: return null
        val root = composed.root
        return if (nodeId.isNullOrBlank()) root else findByAuthoredId(root, nodeId)
    }

    /**
     * Compile diagnostics + static IR validation ([validateDesignDocument]). With
     * [screenId] set, keeps diagnostics anchored in that screen's source file; a
     * file-less diagnostic survives the filter only when it is an error (dropping
     * those could hide real breakage, while file-less warnings from other screens
     * are just noise).
     */
    fun validate(screenId: String? = null): List<DesignDiagnostic> {
        val doc = document
        val all = state.diagnostics + (doc?.let { validateDesignDocument(it) }.orEmpty())
        if (screenId.isNullOrBlank()) return all
        val file = sourceFileFor(screenId) ?: return all
        return all.filter { diagnostic ->
            val diagnosticFile = diagnostic.location?.file.orEmpty()
            if (diagnosticFile.isNotBlank()) {
                diagnosticFile == file
            } else {
                diagnostic.severity == DesignSeverity.Error
            }
        }
    }

    /**
     * Creates a new screen through the editor's own [DesignEditorIntent.CreateScreen]
     * write-back (frontmatter + CNL body via `ScreenSourceWriter`, id-preservation veto
     * included). Returns the id of the created page.
     */
    fun createScreen(preset: ScreenPreset, title: String): String {
        dispatch(DesignEditorIntent.CreateScreen(preset, title))
        return state.selectedPageId
    }

    /** The `*.layout.md` file that authored [screenId], if any single source owns it. */
    fun sourceFileFor(screenId: String): String? {
        state.compiledResults.forEachIndexed { index, compiled ->
            if (compiled.document?.screen?.id == screenId) {
                return state.sources.getOrNull(index)?.fileName
            }
        }
        return null
    }

    private fun findByAuthoredId(box: LayoutBox, nodeId: String): LayoutBox? {
        if (box.node.sourceId == nodeId || box.node.id == nodeId) return box
        box.children.forEach { child ->
            findByAuthoredId(child, nodeId)?.let { return it }
        }
        return null
    }

    companion object {
        fun fromSamples(): AgentSession = AgentSession(AgentProject.samples())

        /** Maps a CLI preset word to the editor's [ScreenPreset]. */
        fun presetFor(word: String): ScreenPreset? =
            ScreenPreset.entries.firstOrNull { it.name.equals(word, ignoreCase = true) }
    }
}
