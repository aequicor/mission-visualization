package io.aequicor.visualization.engine.ir.validate

import io.aequicor.visualization.engine.ir.model.Bindable
import io.aequicor.visualization.engine.ir.model.DesignDiagnostic
import io.aequicor.visualization.engine.ir.model.DesignNode
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.engine.ir.model.DesignSizing
import io.aequicor.visualization.engine.ir.model.DesignStyle
import io.aequicor.visualization.engine.ir.model.PropValue
import io.aequicor.visualization.engine.ir.model.SizingMode
import io.aequicor.visualization.engine.ir.model.TextAlignHorizontal
import io.aequicor.visualization.engine.ir.model.TextAlignVertical
import io.aequicor.visualization.engine.ir.model.TextAutoResize
import io.aequicor.visualization.engine.ir.model.TextContent
import io.aequicor.visualization.engine.ir.model.literalOrNull
import io.aequicor.visualization.engine.ir.resolve.IcuLiteFormatter

/**
 * IR-I18N — text content, resources, ICU, typography, truncation.
 *
 * - IR-I18N-001 (warning): a used `TextContent.key` missing from a target locale bundle
 *   (an expected soft-fallback state — the resolver falls back to defaultText).
 * - IR-I18N-002 (warning): the same content key authored with different defaultText
 *   (the duplicate-key hazard that survives JSON parsing — a literal duplicate inside
 *   one bundle is collapsed by the JSON object semantics before validation can see it).
 * - IR-I18N-003 (warning, info-level): orphan resource key no text content references.
 * - IR-I18N-004 (warning): resource message with invalid ICU syntax (renders literally,
 *   so this is advice rather than a hard failure).
 * - IR-I18N-005 (warning): plural argument missing the locale's required categories
 *   (en: one/other; ru: one/few/many/other; others fall back to the English set,
 *   mirroring IcuLiteFormatter's plural rules).
 * - IR-I18N-006 (error): style range / link outside the authored text.
 * - IR-I18N-007 (warning): localized message shorter than an authored range.
 * - IR-I18N-008 (warning): bounded text (constrained width and height) without
 *   truncate or autoResize — long translations will clip silently.
 * - IR-I18N-009 (error/warning): invalid typography values (fontSize <= 0 is an
 *   error; fontWeight outside 1..1000 a warning).
 * - IR-I18N-010 (warning): negative decorationThickness on the node style or any
 *   inline style range.
 * - IR-I18N-011 (warning): non-default glyph alignment has no bounded text-box space
 *   on that axis, so the requested alignment is visually inert.
 */
internal object TextI18nChecks {

    fun check(ctx: ValidationContext): List<DesignDiagnostic> = buildList {
        val usedContents = collectTextContents(ctx)
        checkResourceKeys(this, ctx, usedContents)
        checkDuplicateDefaults(this, usedContents)
        checkOrphans(this, ctx, usedContents)
        checkIcuMessages(this, ctx)
        ctx.entries.forEach { entry ->
            (entry.node.kind as? DesignNodeKind.Text)?.let { text ->
                checkRanges(this, ctx, entry.node, text)
                checkTruncation(this, ctx, entry.node, text)
                checkTypography(this, ctx, entry.node, text)
                checkAlignmentBox(this, ctx, entry.node, text)
            }
        }
    }

    private fun collectTextContents(ctx: ValidationContext): List<Pair<TextContent, DesignNode>> =
        buildList {
            ctx.entries.forEach { entry ->
                when (val kind = entry.node.kind) {
                    is DesignNodeKind.Text -> kind.content?.let { add(it to entry.node) }
                    is DesignNodeKind.Media -> kind.media.alt?.let { add(it to entry.node) }
                    is DesignNodeKind.Instance -> kind.props.values.forEach { value ->
                        (value as? PropValue.Content)?.let { add(it.content to entry.node) }
                    }
                    else -> Unit
                }
            }
            ctx.document.components.values.forEach { component ->
                component.properties.values.forEach { definition ->
                    (definition.default as? PropValue.Content)?.let { add(it.content to component.root) }
                }
            }
        }

    private fun checkResourceKeys(
        sink: MutableList<DesignDiagnostic>,
        ctx: ValidationContext,
        contents: List<Pair<TextContent, DesignNode>>,
    ) {
        ctx.document.i18n.targetLocales.forEach { locale ->
            val bundle = ctx.mergedResources[locale].orEmpty()
            contents.forEach { (content, node) ->
                if (content.key.isNotEmpty() && content.key !in bundle) {
                    sink += validationWarning(
                        "IR-I18N-001",
                        "i18n key '${content.key}' (used by '${node.id}') is missing from locale '$locale'",
                        ctx.location(node),
                    )
                }
            }
        }
    }

    private fun checkDuplicateDefaults(
        sink: MutableList<DesignDiagnostic>,
        contents: List<Pair<TextContent, DesignNode>>,
    ) {
        contents
            .filter { (content, _) -> content.key.isNotEmpty() && content.defaultText.isNotEmpty() }
            .groupBy { (content, _) -> content.key }
            .forEach { (key, occurrences) ->
                val defaults = occurrences.map { (content, _) -> content.defaultText }.distinct()
                if (defaults.size > 1) {
                    sink += validationWarning(
                        "IR-I18N-002",
                        "i18n key '$key' is authored with ${defaults.size} different defaultText values",
                    )
                }
            }
    }

    private fun checkOrphans(
        sink: MutableList<DesignDiagnostic>,
        ctx: ValidationContext,
        contents: List<Pair<TextContent, DesignNode>>,
    ) {
        val usedKeys = contents.map { (content, _) -> content.key }.filter { it.isNotEmpty() }.toSet()
        ctx.document.i18n.resources.forEach { (locale, bundle) ->
            bundle.keys.forEach { key ->
                if (key !in usedKeys) {
                    sink += validationWarning(
                        "IR-I18N-003",
                        "Resource key '$key' in locale '$locale' is not referenced by any text content",
                    )
                }
            }
        }
    }

    private fun checkIcuMessages(sink: MutableList<DesignDiagnostic>, ctx: ValidationContext) {
        ctx.mergedResources.forEach { (locale, bundle) ->
            val required = requiredPluralCategories(locale)
            bundle.forEach { (key, message) ->
                val inspection = IcuLiteFormatter.inspect(message)
                if (inspection.syntaxError != null) {
                    sink += validationWarning(
                        "IR-I18N-004",
                        "Malformed ICU message '$key' in locale '$locale': ${inspection.syntaxError}",
                    )
                }
                inspection.arguments
                    .filter { it.type == "plural" }
                    .forEach { argument ->
                        val categories = argument.selectors.filterNot { it.startsWith("=") }.toSet()
                        val missing = required - categories
                        if (missing.isNotEmpty()) {
                            sink += validationWarning(
                                "IR-I18N-005",
                                "Plural argument '${argument.name}' of '$key' in locale '$locale' " +
                                    "is missing categories $missing",
                            )
                        }
                    }
            }
        }
    }

    /** Mirrors [IcuLiteFormatter]'s CLDR subset: ru needs one/few/many/other, others one/other. */
    private fun requiredPluralCategories(locale: String): Set<String> =
        when (locale.substringBefore('-').substringBefore('_').trim().lowercase()) {
            "ru" -> setOf("one", "few", "many", "other")
            else -> setOf("one", "other")
        }

    private fun checkRanges(
        sink: MutableList<DesignDiagnostic>,
        ctx: ValidationContext,
        node: DesignNode,
        text: DesignNodeKind.Text,
    ) {
        val authored = text.content?.defaultText?.takeIf { it.isNotEmpty() }
            ?: text.characters.literalOrNull()
        val spans = text.styleRanges.map { Triple(it.start, it.end, "style range") } +
            text.links.map { Triple(it.start, it.end, "link") }
        spans.forEach { (start, end, what) ->
            if (start < 0 || end < start) {
                sink += validationError(
                    "IR-I18N-006",
                    "Invalid $what [$start, $end) on '${node.id}'",
                    ctx.location(node),
                )
                return@forEach
            }
            if (authored != null && end > authored.length) {
                sink += validationError(
                    "IR-I18N-006",
                    "A $what [$start, $end) on '${node.id}' exceeds the text length ${authored.length}",
                    ctx.location(node),
                )
            }
            val key = text.content?.key.orEmpty()
            if (key.isNotEmpty()) {
                ctx.mergedResources.forEach { (locale, bundle) ->
                    val message = bundle[key] ?: return@forEach
                    if (end > message.length) {
                        sink += validationWarning(
                            "IR-I18N-007",
                            "A $what [$start, $end) on '${node.id}' exceeds the '$locale' " +
                                "message length ${message.length}",
                            ctx.location(node),
                        )
                    }
                }
            }
        }
    }

    /** Width- and height-constrained text must say what happens to long content. */
    private fun checkTruncation(
        sink: MutableList<DesignDiagnostic>,
        ctx: ValidationContext,
        node: DesignNode,
        text: DesignNodeKind.Text,
    ) {
        if (text.truncate != null || text.autoResize != TextAutoResize.None) return
        val sizing = node.sizing ?: DesignSizing()
        val widthBounded = sizing.horizontal == SizingMode.Fill ||
            (sizing.horizontal == SizingMode.Fixed && node.size.width != null)
        val heightBounded = sizing.vertical == SizingMode.Fill ||
            (sizing.vertical == SizingMode.Fixed && node.size.height != null)
        if (widthBounded && heightBounded) {
            sink += validationWarning(
                "IR-I18N-008",
                "Bounded text '${node.id}' has neither truncate nor autoResize; " +
                    "long translations will clip silently",
                ctx.location(node),
            )
        }
    }

    private fun checkTypography(
        sink: MutableList<DesignDiagnostic>,
        ctx: ValidationContext,
        node: DesignNode,
        text: DesignNodeKind.Text,
    ) {
        text.textStyle?.let { style ->
            (style.fontSize as? Bindable.Value)?.value?.let { fontSize ->
                if (fontSize <= 0.0) {
                    sink += validationError(
                        "IR-I18N-009",
                        "Text '${node.id}' has non-positive fontSize $fontSize",
                        ctx.location(node),
                    )
                }
            }
            (style.fontWeight as? Bindable.Value)?.value?.let { weight ->
                if (weight < 1.0 || weight > 1000.0) {
                    sink += validationWarning(
                        "IR-I18N-009",
                        "Text '${node.id}' has fontWeight $weight outside 1..1000",
                        ctx.location(node),
                    )
                }
            }
        }
        (listOfNotNull(text.textStyle) + text.styleRanges.map { it.style }).forEach { style ->
            style.decorationThickness?.value?.let { thickness ->
                if (thickness < 0.0) {
                    sink += validationWarning(
                        "IR-I18N-010",
                        "Text '${node.id}' has negative decorationThickness $thickness",
                        ctx.location(node),
                    )
                }
            }
        }
    }

    private fun checkAlignmentBox(
        sink: MutableList<DesignDiagnostic>,
        ctx: ValidationContext,
        node: DesignNode,
        text: DesignNodeKind.Text,
    ) {
        val inherited = (ctx.document.styles[text.textStyleId] as? DesignStyle.Text)?.value
        val style = inherited?.mergedWith(text.textStyle) ?: text.textStyle ?: return
        val sizing = node.sizing ?: DesignSizing()
        val hasHorizontalBox = sizing.horizontal == SizingMode.Fill ||
            (text.autoResize != TextAutoResize.WidthAndHeight && node.size.width != null)
        val hasVerticalBox = sizing.vertical == SizingMode.Fill ||
            (text.autoResize == TextAutoResize.None && node.size.height != null)

        val horizontal = style.textAlignHorizontal
        if (horizontal != null && horizontal != TextAlignHorizontal.Left && !hasHorizontalBox) {
            sink += validationWarning(
                "IR-I18N-011",
                "Text '${node.id}' uses horizontal ${horizontal.name.lowercase()} alignment " +
                    "without a fixed/fill-width box; alignment inside a content-width box is invisible",
                ctx.location(node),
            )
        }
        val vertical = style.textAlignVertical
        if (vertical != null && vertical != TextAlignVertical.Top && !hasVerticalBox) {
            sink += validationWarning(
                "IR-I18N-011",
                "Text '${node.id}' uses vertical ${vertical.name.lowercase()} alignment " +
                    "without a fixed/fill-height box; alignment inside a content-height box is invisible",
                ctx.location(node),
            )
        }
    }
}
