package io.aequicor.visualization.engine.frontend.semantics

import io.aequicor.visualization.engine.frontend.SlmLocale
import io.aequicor.visualization.engine.frontend.ast.IrSpliceBlock
import io.aequicor.visualization.engine.frontend.ast.KeyHint
import io.aequicor.visualization.engine.frontend.ast.SemanticAction
import io.aequicor.visualization.engine.frontend.ast.SemanticKind
import io.aequicor.visualization.engine.frontend.ast.SemanticNode
import io.aequicor.visualization.engine.frontend.ast.SemanticScreen
import io.aequicor.visualization.engine.frontend.ast.SemanticText
import io.aequicor.visualization.engine.frontend.blocks.MediaPatch
import io.aequicor.visualization.engine.frontend.blocks.TypedPatch
import io.aequicor.visualization.engine.frontend.diagnostics.DiagnosticCollector
import io.aequicor.visualization.engine.frontend.expr.SlmExpression
import io.aequicor.visualization.engine.frontend.markdown.BlockquoteBlock
import io.aequicor.visualization.engine.frontend.markdown.CommentRun
import io.aequicor.visualization.engine.frontend.markdown.ExpressionRun
import io.aequicor.visualization.engine.frontend.markdown.FencedCodeBlock
import io.aequicor.visualization.engine.frontend.markdown.HeadingBlock
import io.aequicor.visualization.engine.frontend.markdown.HtmlCommentBlock
import io.aequicor.visualization.engine.frontend.markdown.ImageBlock
import io.aequicor.visualization.engine.frontend.markdown.LinkRun
import io.aequicor.visualization.engine.frontend.markdown.ListBlock
import io.aequicor.visualization.engine.frontend.markdown.ParagraphBlock
import io.aequicor.visualization.engine.frontend.markdown.SlmBlock
import io.aequicor.visualization.engine.frontend.markdown.SlmInline
import io.aequicor.visualization.engine.frontend.markdown.SlmListItem
import io.aequicor.visualization.engine.frontend.markdown.SlmMarkdownDocument
import io.aequicor.visualization.engine.frontend.markdown.SlmSourceSpan
import io.aequicor.visualization.engine.frontend.markdown.TableBlock
import io.aequicor.visualization.engine.frontend.markdown.TextRun
import io.aequicor.visualization.engine.frontend.markdown.TypedAttributeBlock
import io.aequicor.visualization.engine.frontend.frontmatter.SlmFrontmatter
import io.aequicor.visualization.engine.frontend.normalize.latinizeToCamelCase

/**
 * Language seam of the structural pass. The full [io.aequicor.visualization]
 * semantic lexicons (rules, noun/action slugs) arrive in the next stage; the
 * structural extractor only needs the markers below.
 */
data class StructuralLexicon(
    /** Condition paragraph lead words, `если {{...}}:` / `if {{...}}:`. */
    val conditionMarkers: List<String> = listOf("если", "if"),
    /** Heading prefixes marking a component definition subtree. */
    val componentMarkers: List<String> = listOf("component", "компонент"),
)

/**
 * Lexicon-free structural pass: maps the markdown CST to a [SemanticScreen] —
 * headings to sections, paragraphs to text nodes, lists to groups/repeats,
 * blockquotes to callouts, images/tables to media/table nodes, and binds typed
 * attribute blocks to the nearest preceding anchor-capable element.
 */
fun extractStructure(
    document: SlmMarkdownDocument,
    frontmatter: SlmFrontmatter,
    diagnostics: DiagnosticCollector,
    fallbackLocale: SlmLocale = SlmLocale("en-US"),
    lexicon: StructuralLexicon = StructuralLexicon(),
): SemanticScreen = StructuralExtractor(diagnostics, lexicon)
    .extract(document, frontmatter, frontmatter.sourceLocale ?: fallbackLocale)

private class StructuralExtractor(
    private val diagnostics: DiagnosticCollector,
    private val lexicon: StructuralLexicon,
) {
    private class NodeBuilder(
        var kind: SemanticKind,
        var span: SlmSourceSpan,
    ) {
        var role = ""
        var name = ""
        var slugHint = ""
        var text: SemanticText? = null
        var action: SemanticAction? = null
        var repeat: SlmExpression.Repeat? = null
        var condition: SlmExpression? = null
        var componentRef: String? = null
        var irSplice: IrSpliceBlock? = null
        var isAnchor = false
        var isComponentDef = false
        val explicitPatches = mutableListOf<io.aequicor.visualization.engine.frontend.markdown.TypedEntry>()
        val semanticPatches = mutableListOf<TypedPatch>()
        val children = mutableListOf<NodeBuilder>()

        fun build(defs: MutableList<SemanticNode>): SemanticNode {
            val builtChildren = mutableListOf<SemanticNode>()
            children.forEach { child ->
                val built = child.build(defs)
                if (child.isComponentDef) defs += built else builtChildren += built
            }
            return SemanticNode(
                kind = kind,
                role = role,
                name = name,
                slugHint = slugHint,
                text = text,
                action = action,
                repeat = repeat,
                condition = condition,
                componentRef = componentRef,
                explicitPatches = explicitPatches.toList(),
                semanticPatches = semanticPatches.toList(),
                irSplice = irSplice,
                isAnchor = isAnchor,
                isComponentDef = isComponentDef,
                children = builtChildren,
                span = span,
            )
        }
    }

    /** Pending single-shot bindings consumed by the next created node/text. */
    private class WalkState {
        var anchor: NodeBuilder? = null
        var pendingCondition: SlmExpression? = null
        var pendingKey: String? = null
    }

    private lateinit var root: NodeBuilder
    private var title: SemanticText? = null

    fun extract(
        document: SlmMarkdownDocument,
        frontmatter: SlmFrontmatter,
        sourceLocale: SlmLocale,
    ): SemanticScreen {
        val lastLine = document.blocks.lastOrNull()?.span?.endLine ?: 1
        root = NodeBuilder(SemanticKind.Screen, SlmSourceSpan(1, lastLine)).apply {
            name = frontmatter.screen
            isAnchor = true
        }
        val state = WalkState()
        // Section stack: (heading level, builder); root sits below every heading.
        val stack = ArrayDeque<Pair<Int, NodeBuilder>>()
        stack.addLast(1 to root)

        document.blocks.forEach { block ->
            when (block) {
                is HeadingBlock -> handleHeading(block, stack, state)
                else -> handleBlock(block, stack.last().second, state)
            }
        }
        if (state.pendingCondition != null) {
            diagnostics.warning("Condition paragraph is not followed by any block", root.span)
        }

        val defs = mutableListOf<SemanticNode>()
        val builtRoot = root.build(defs)
        return SemanticScreen(
            frontmatter = frontmatter,
            sourceLocale = sourceLocale,
            title = title,
            root = builtRoot,
            componentDefs = defs,
        )
    }

    // --- headings ---

    private fun handleHeading(
        block: HeadingBlock,
        stack: ArrayDeque<Pair<Int, NodeBuilder>>,
        state: WalkState,
    ) {
        val inline = readInlineText(block.inlines)
        if (block.level == 1 && title == null && stack.size == 1) {
            title = SemanticText(
                defaultText = inline.text,
                params = inline.params,
                explicitKey = inline.explicitKey ?: state.pendingKey,
                keyHint = KeyHint.ScreenTitle,
                span = block.span,
            )
            state.pendingKey = null
            // Typed blocks after the H1 bind to the screen root.
            root.isAnchor = true
            state.anchor = root
            return
        }
        while (stack.size > 1 && stack.last().first >= block.level) stack.removeLast()
        val parent = stack.last().second
        val section = NodeBuilder(SemanticKind.Section, block.span).apply { isAnchor = true }
        applyHeadingName(section, inline, block.span, stack)
        state.pendingCondition?.let {
            section.condition = it
            state.pendingCondition = null
        }
        parent.children += section
        stack.addLast(block.level to section)
        state.anchor = section
    }

    /** `Component:` marks a definition subtree; other `Word:` prefixes name the node. */
    private fun applyHeadingName(
        section: NodeBuilder,
        inline: InlineText,
        span: SlmSourceSpan,
        stack: ArrayDeque<Pair<Int, NodeBuilder>>,
    ) {
        val text = inline.text
        val colon = text.indexOf(':')
        val prefix = if (colon > 0) text.take(colon).trim() else ""
        val isSingleWordPrefix = prefix.isNotEmpty() && prefix.none { it.isWhitespace() }
        when {
            isSingleWordPrefix && prefix.lowercase() in lexicon.componentMarkers -> {
                section.isComponentDef = true
                section.name = text.drop(colon + 1).trim()
            }
            isSingleWordPrefix -> section.name = text.drop(colon + 1).trim()
            else -> {
                section.name = text
                val path = stack.drop(1).map { it.second.name } + text
                section.text = SemanticText(
                    defaultText = text,
                    params = inline.params,
                    explicitKey = inline.explicitKey,
                    keyHint = KeyHint.SectionTitle(path),
                    span = span,
                )
            }
        }
    }

    // --- non-heading blocks ---

    private fun handleBlock(block: SlmBlock, container: NodeBuilder, state: WalkState) {
        when (block) {
            is TypedAttributeBlock -> (state.anchor ?: root).explicitPatches += block.entries

            is ParagraphBlock -> handleParagraph(block, container, state)

            is ListBlock -> attach(container, state, listNode(block))

            is BlockquoteBlock -> {
                val callout = NodeBuilder(SemanticKind.Callout, block.span).apply {
                    role = "callout"
                    isAnchor = true
                }
                val nested = WalkState().apply { anchor = callout }
                block.blocks.forEach { inner ->
                    if (inner is HeadingBlock) {
                        handleNestedHeading(inner, callout, nested)
                    } else {
                        handleBlock(inner, callout, nested)
                    }
                }
                attach(container, state, callout)
                state.anchor = callout
            }

            is ImageBlock -> {
                val media = mediaNode(block, state)
                attach(container, state, media)
                state.anchor = media
            }

            is TableBlock -> {
                val table = tableNode(block)
                attach(container, state, table)
                state.anchor = table
            }

            is FencedCodeBlock -> if (block.info == "ir") {
                val splice = NodeBuilder(SemanticKind.IrSplice, block.span).apply {
                    irSplice = IrSpliceBlock(block.content, block.contentStartLine, block.span)
                }
                attach(container, state, splice)
            }

            is HtmlCommentBlock -> i18nKeyOf(block.text)?.let { state.pendingKey = it }

            is HeadingBlock -> handleNestedHeading(block, container, state)
        }
    }

    /** Headings inside list items/blockquotes become plain section children. */
    private fun handleNestedHeading(block: HeadingBlock, container: NodeBuilder, state: WalkState) {
        val section = NodeBuilder(SemanticKind.Section, block.span).apply { isAnchor = true }
        applyHeadingName(section, readInlineText(block.inlines), block.span, ArrayDeque())
        attach(container, state, section)
        state.anchor = section
    }

    private fun attach(container: NodeBuilder, state: WalkState, node: NodeBuilder) {
        state.pendingCondition?.let {
            node.condition = it
            state.pendingCondition = null
        }
        container.children += node
    }

    // --- paragraphs ---

    private fun handleParagraph(block: ParagraphBlock, container: NodeBuilder, state: WalkState) {
        conditionOf(block)?.let {
            if (state.pendingCondition != null) {
                diagnostics.warning("Condition paragraph replaces an unattached condition", block.span)
            }
            state.pendingCondition = it
            return
        }
        val inline = readInlineText(block.inlines)
        val actions = inline.links.map { link -> actionNode(link, block.span) }
        val node: NodeBuilder = if (inline.text.isBlank() && actions.isNotEmpty()) {
            // Link-only paragraph: the action nodes stand on their own.
            if (actions.size == 1) {
                actions.single()
            } else {
                NodeBuilder(SemanticKind.Group, block.span).also { it.children += actions }
            }
        } else {
            NodeBuilder(SemanticKind.Text, block.span).also { builder ->
                builder.text = SemanticText(
                    defaultText = inline.text,
                    params = inline.params,
                    explicitKey = inline.explicitKey ?: state.pendingKey,
                    keyHint = KeyHint.Plain,
                    span = block.span,
                )
                builder.children += actions
            }
        }
        state.pendingKey = null
        attach(container, state, node)
    }

    private fun actionNode(link: LinkRun, span: SlmSourceSpan): NodeBuilder {
        val labelText = readInlineText(link.label)
        val slug = routeSlug(link.target).ifEmpty { latinizeToCamelCase(labelText.text) }
        return NodeBuilder(SemanticKind.Action, span).apply {
            role = "action"
            name = labelText.text
            slugHint = slug
            action = SemanticAction(
                navigateTo = link.target,
                label = SemanticText(
                    defaultText = labelText.text,
                    params = labelText.params,
                    explicitKey = link.i18nKeyOverride,
                    keyHint = KeyHint.ActionLabel(slug),
                    span = span,
                ),
            )
        }
    }

    /** `^(если|if)\s*{{expr}}\s*:\s*$` — attaches to the NEXT block's node. */
    private fun conditionOf(block: ParagraphBlock): SlmExpression? {
        var expression: SlmExpression? = null
        val rendered = StringBuilder()
        block.inlines.forEach { inline ->
            when (inline) {
                is TextRun -> rendered.append(inline.text)
                is ExpressionRun -> {
                    if (expression != null) return null
                    expression = inline.expression
                    rendered.append("{{").append(inline.raw).append("}}")
                }
                else -> return null
            }
        }
        val markers = lexicon.conditionMarkers.joinToString("|") { Regex.escape(it) }
        val regex = Regex("""^($markers)\s*\{\{.+}}\s*:\s*$""", RegexOption.IGNORE_CASE)
        if (!regex.matches(rendered.toString().trim())) return null
        return expression
    }

    // --- lists ---

    private fun listNode(block: ListBlock): NodeBuilder {
        val group = NodeBuilder(SemanticKind.Group, block.span)
        block.items.forEach { item -> group.children += itemNode(item) }
        return group
    }

    private fun itemNode(item: SlmListItem): NodeBuilder {
        val inline = readInlineText(item.inlines)
        val repeatExpr = item.inlines
            .filterIsInstance<ExpressionRun>()
            .firstNotNullOfOrNull { it.expression as? SlmExpression.Repeat }
        val node = when {
            repeatExpr != null -> NodeBuilder(SemanticKind.Repeat, item.span).apply {
                repeat = repeatExpr
                name = inline.textWithoutParams
            }
            inline.text.isBlank() && inline.links.size == 1 && item.children.isEmpty() ->
                actionNode(inline.links.single(), item.span)
            item.children.isEmpty() -> NodeBuilder(SemanticKind.Text, item.span).apply {
                text = SemanticText(
                    defaultText = inline.text,
                    params = inline.params,
                    explicitKey = inline.explicitKey,
                    keyHint = KeyHint.Plain,
                    span = item.span,
                )
                children += inline.links.map { actionNode(it, item.span) }
            }
            else -> NodeBuilder(SemanticKind.Group, item.span).apply {
                name = inline.textWithoutParams
                if (inline.text.isNotBlank()) {
                    children += NodeBuilder(SemanticKind.Text, item.span).apply {
                        text = SemanticText(
                            defaultText = inline.text,
                            params = inline.params,
                            explicitKey = inline.explicitKey,
                            keyHint = KeyHint.Plain,
                            span = item.span,
                        )
                    }
                }
                children += inline.links.map { actionNode(it, item.span) }
            }
        }
        node.isAnchor = true
        val nested = WalkState().apply { anchor = node }
        item.children.forEach { child ->
            when {
                // Repeat items: nested list items become the card children directly.
                node.kind == SemanticKind.Repeat && child is ListBlock ->
                    child.items.forEach { node.children += itemNode(it) }
                else -> handleBlock(child, node, nested)
            }
        }
        return node
    }

    // --- images and tables ---

    private fun mediaNode(block: ImageBlock, state: WalkState): NodeBuilder =
        NodeBuilder(SemanticKind.Media, block.span).apply {
            isAnchor = true
            name = block.alt
            semanticPatches += MediaPatch(asset = block.path)
            if (block.alt.isNotBlank()) {
                text = SemanticText(
                    defaultText = block.alt,
                    explicitKey = block.i18nKeyOverride ?: state.pendingKey,
                    keyHint = KeyHint.MediaAlt,
                    span = block.span,
                )
                state.pendingKey = null
            }
        }

    private fun tableNode(block: TableBlock): NodeBuilder {
        val table = NodeBuilder(SemanticKind.Table, block.span).apply { isAnchor = true }
        table.children += tableRow(block.header, block.span, isHeader = true)
        block.rows.forEach { row -> table.children += tableRow(row, block.span, isHeader = false) }
        return table
    }

    private fun tableRow(
        cells: List<List<SlmInline>>,
        span: SlmSourceSpan,
        isHeader: Boolean,
    ): NodeBuilder {
        val row = NodeBuilder(SemanticKind.Group, span).apply {
            role = if (isHeader) "tableHeader" else "tableRow"
        }
        cells.forEach { cell ->
            val inline = readInlineText(cell)
            row.children += NodeBuilder(SemanticKind.Text, span).apply {
                text = SemanticText(
                    defaultText = inline.text,
                    params = inline.params,
                    explicitKey = inline.explicitKey,
                    keyHint = if (isHeader) KeyHint.TableHeader else KeyHint.Plain,
                    span = span,
                )
            }
        }
        return row
    }

    // --- inline runs -> text with {param} placeholders ---

    private class InlineText(
        val text: String,
        val textWithoutParams: String,
        val params: Map<String, SlmExpression>,
        val links: List<LinkRun>,
        val explicitKey: String?,
    )

    private fun readInlineText(inlines: List<SlmInline>): InlineText {
        val text = StringBuilder()
        val plain = StringBuilder()
        val params = LinkedHashMap<String, SlmExpression>()
        val links = mutableListOf<LinkRun>()
        var explicitKey: String? = null
        inlines.forEach { inline ->
            when (inline) {
                is TextRun -> {
                    text.append(inline.text)
                    plain.append(inline.text)
                }
                is ExpressionRun -> {
                    if (inline.expression !is SlmExpression.Repeat) {
                        val name = uniqueParamName(paramName(inline.expression, inline.raw), inline.expression, params)
                        params[name] = inline.expression
                        text.append('{').append(name).append('}')
                    }
                }
                is LinkRun -> links += inline
                is CommentRun -> i18nKeyOf(inline.text)?.let { explicitKey = it }
                else -> {}
            }
        }
        return InlineText(
            text = text.toString().trim(),
            textWithoutParams = plain.toString().trim().trimEnd(':', ',').trim(),
            params = params,
            links = links,
            explicitKey = explicitKey,
        )
    }

    private fun uniqueParamName(
        base: String,
        expression: SlmExpression,
        params: Map<String, SlmExpression>,
    ): String {
        if (params[base] == null || params[base] == expression) return base
        var index = 2
        while (params.containsKey("$base$index") && params["$base$index"] != expression) index++
        return "$base$index"
    }

    /** `missions.length` -> `missionsLength`; raw text is sanitized to camelCase. */
    private fun paramName(expression: SlmExpression, raw: String): String {
        val parts = when (expression) {
            is SlmExpression.Path -> expression.segments
            else -> raw.split(Regex("[^A-Za-z0-9]+")).filter { it.isNotEmpty() }
        }
        val joined = parts
            .mapIndexed { index, part ->
                if (index == 0) part else part.replaceFirstChar { it.uppercaseChar() }
            }
            .joinToString("")
        return when {
            joined.isEmpty() -> "param"
            joined.first().isDigit() -> "param$joined"
            else -> joined
        }
    }
}

/** `/missions/new` -> `missionsNew`; empty for external/empty targets. */
internal fun routeSlug(target: String): String {
    if (!target.startsWith("/")) return ""
    val parts = target.split('/', '-', '_', '.').filter { it.isNotEmpty() }
    return parts
        .mapIndexed { index, part ->
            if (index == 0) part.lowercase() else part.lowercase().replaceFirstChar { it.uppercaseChar() }
        }
        .joinToString("")
}

/** `<!-- i18n:key=missionDashboard.title -->` payload. */
internal fun i18nKeyOf(comment: String): String? =
    Regex("""i18n:key=(\S+)""").find(comment)?.groupValues?.get(1)
