package io.aequicor.visualization.engine.frontend.edit

import io.aequicor.visualization.engine.frontend.cnl.CnlEmitter
import io.aequicor.visualization.engine.ir.model.DesignPage

/**
 * Renders a freshly-created screen [DesignPage] (a single root frame) into a **standalone**
 * SLM `*.layout.md` document — the whole-page counterpart of [NodeSectionWriter], which grafts
 * a subtree into an existing source. A new screen has no owning source to patch, so the reducer
 * compiles this text as its own document and appends it to the mission source list.
 *
 * The compiled document round-trips the page faithfully:
 * - `screen: <page.id>` frontmatter so the merged page id equals the minted screen id
 *   (mergeMissionDocuments re-ids each page from its document's `screen` meta);
 * - `frame:` frontmatter carries the root artboard size (IrNormalizer sizes the root frame
 *   from it), matching the authored mission documents;
 * - the plain `# <title>` H1 is the screen-root heading (no decorative prefix, so the extractor
 *   records a screen title, never a spurious `SectionTitle` i18n text node);
 * - the H1's trailing CNL phrases (emitted by [CnlEmitter.emitStableSubtree] with
 *   `includeId = true`, same stable form as [NodeSectionWriter]) carry the root frame's
 *   **explicit minted id**, the canonical size and fill phrases.
 *
 * Pure: same page + locale always yields the same text, so the reducer stays referentially
 * transparent and the write-back is testable.
 */
object ScreenSourceWriter {

    /**
     * The standalone SLM document for [page] (a minted screen with a single root frame),
     * authored in [sourceLocale]. Public so the reducer can compile it into a fresh source.
     */
    fun render(page: DesignPage, sourceLocale: String): String {
        val root = page.children.firstOrNull()
        val width = root?.size?.width
        val height = root?.size?.height
        val body = if (root == null) {
            listOf("# ${page.name}")
        } else {
            // The CNL body: the screen root's H1 carries the page name as its plain title (so the
            // extractor records a screen title, not a spurious SectionTitle) with the root's
            // properties as inline phrases; children (if any) follow as sub-headings.
            CnlEmitter.emitStableSubtree(root, level = 1, includeId = true, titleOverride = page.name)
        }
        return buildString {
            append("---\n")
            append("screen: ${scalar(page.id)}\n")
            if (page.name.isNotEmpty()) append("page: ${scalar(page.name)}\n")
            if (sourceLocale.isNotEmpty()) append("sourceLocale: ${scalar(sourceLocale)}\n")
            if (width != null || height != null) {
                append("frame:\n")
                width?.let { append("  width: ${num(it)}\n") }
                height?.let { append("  height: ${num(it)}\n") }
            }
            append("---\n\n")
            body.forEach { append(it).append('\n') }
        }
    }

    /**
     * Frontmatter values (screen/page/sourceLocale) render as canonical scalars, but with the
     * plain-string rule relaxed to allow spaces so a page title like `New Screen` stays
     * unquoted — matching the hand-authored `page: Mission Overview` style. Only characters the
     * frontmatter reader would choke on force quoting.
     */
    private fun scalar(value: String): String =
        if (value.isNotEmpty() && value.none { it == ':' || it == '#' || it == '"' || it == '\'' || it == '\n' }) {
            value
        } else {
            ScalarFormatter.format(YamlScalarValue.Str(value))
        }

    private fun num(value: Double): String = ScalarFormatter.format(YamlScalarValue.Num(value))
}
