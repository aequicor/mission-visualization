package io.aequicor.visualization.engine.ir.validate

import io.aequicor.visualization.engine.ir.layout.DesignLayoutEngine
import io.aequicor.visualization.engine.ir.layout.LayoutBox
import io.aequicor.visualization.engine.ir.model.DesignDiagnostic
import io.aequicor.visualization.engine.ir.model.OverflowMode
import io.aequicor.visualization.engine.ir.model.TextAutoResize
import io.aequicor.visualization.engine.ir.resolve.DesignResolver

/**
 * IR-RESOLVE — opt-in dynamic checks: per-locale resolution and layout probes
 * (see [ValidationOptions]). Locales come from `options.locales`, falling back to
 * the document's `i18n.targetLocales`; RTL locales are resolved in their derived
 * direction, which exercises the resolver's logical->physical RTL mapping. With
 * `layoutProbe` (and no locales anywhere) the source locale is probed once.
 *
 * - IR-RESOLVE-001: a resolver diagnostic, wrapped with its locale ("[ru-RU] ...");
 *   the original severity is preserved.
 * - IR-RESOLVE-002 (warning): content overflows a clipped box — for text boxes only
 *   when nothing handles the overflow (no truncate, no autoResize); reported per locale.
 * - IR-RESOLVE-003 (warning): a min-size clamp keeps a child larger than its parent.
 */
internal object ResolvedChecks {

    private const val Epsilon = 0.5

    fun check(ctx: ValidationContext): List<DesignDiagnostic> {
        val locales = ctx.options.locales.ifEmpty { ctx.document.i18n.targetLocales }
        val probeLocales = locales.ifEmpty {
            if (ctx.options.layoutProbe) {
                listOf(ctx.resolveContext.locale ?: ctx.document.i18n.sourceLocale)
            } else {
                emptyList()
            }
        }
        if (probeLocales.isEmpty()) return emptyList()

        return buildList {
            probeLocales.forEach { locale ->
                val label = locale.ifEmpty { "default" }
                val resolver = DesignResolver(
                    ctx.document,
                    ctx.resolveContext.copy(locale = locale.takeIf { it.isNotEmpty() }),
                )
                // RTL probe: an RTL locale derives LayoutDirection.Rtl in the resolver,
                // exercising the logical->physical mapping while pages resolve.
                val roots = ctx.document.pages.flatMap { resolver.resolvePage(it) }
                resolver.diagnostics.forEach { diagnostic ->
                    add(diagnostic.copy(message = "[$label] ${diagnostic.message}", code = "IR-RESOLVE-001"))
                }
                if (ctx.options.layoutProbe) {
                    val engine = DesignLayoutEngine(ctx.options.textMeasurer)
                    roots.forEach { root -> probe(this, engine.layout(root), label) }
                }
            }
        }.distinct()
    }

    private fun probe(sink: MutableList<DesignDiagnostic>, box: LayoutBox, locale: String) {
        box.allBoxes().forEach { current ->
            checkOverflow(sink, current, locale)
            checkMinSize(sink, current, locale)
        }
    }

    private fun checkOverflow(sink: MutableList<DesignDiagnostic>, box: LayoutBox, locale: String) {
        val node = box.node
        val overflows = box.contentWidth > box.width + Epsilon || box.contentHeight > box.height + Epsilon
        if (!overflows) return
        val clips = node.layout.clipsContent ||
            node.scroll.overflowX == OverflowMode.Hidden ||
            node.scroll.overflowY == OverflowMode.Hidden
        val unhandledText = node.text != null &&
            node.text.truncate == null &&
            node.text.autoResize == TextAutoResize.None
        if (clips || unhandledText) {
            sink += validationWarning(
                "IR-RESOLVE-002",
                "[$locale] Content of '${node.sourceId}' " +
                    "(${box.contentWidth.pretty()}x${box.contentHeight.pretty()}) overflows its box " +
                    "(${box.width.pretty()}x${box.height.pretty()})",
                node.sourceMap,
            )
        }
    }

    private fun checkMinSize(sink: MutableList<DesignDiagnostic>, box: LayoutBox, locale: String) {
        box.children.forEach { child ->
            val minWidth = child.node.minSize?.width
            val minHeight = child.node.minSize?.height
            val widthBound = minWidth != null && child.width <= minWidth + Epsilon && child.right > box.right + Epsilon
            val heightBound = minHeight != null && child.height <= minHeight + Epsilon && child.bottom > box.bottom + Epsilon
            if (widthBound || heightBound) {
                sink += validationWarning(
                    "IR-RESOLVE-003",
                    "[$locale] Min size of '${child.node.sourceId}' keeps it larger than " +
                        "its parent '${box.node.sourceId}'",
                    child.node.sourceMap,
                )
            }
        }
    }

    private fun Double.pretty(): String =
        if (this == toLong().toDouble()) toLong().toString() else toString()
}
