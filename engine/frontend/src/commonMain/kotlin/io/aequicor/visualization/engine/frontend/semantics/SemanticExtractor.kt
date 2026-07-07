package io.aequicor.visualization.engine.frontend.semantics

import io.aequicor.visualization.engine.frontend.SlmLocale
import io.aequicor.visualization.engine.frontend.ast.IrSpliceBlock
import io.aequicor.visualization.engine.frontend.ast.KeyHint
import io.aequicor.visualization.engine.frontend.ast.SemanticAction
import io.aequicor.visualization.engine.frontend.ast.SemanticKind
import io.aequicor.visualization.engine.frontend.ast.SemanticNode
import io.aequicor.visualization.engine.frontend.ast.SemanticScreen
import io.aequicor.visualization.engine.frontend.ast.SemanticText
import io.aequicor.visualization.engine.frontend.blocks.LayoutPatch
import io.aequicor.visualization.engine.frontend.blocks.MediaPatch
import io.aequicor.visualization.engine.frontend.blocks.NodePatch
import io.aequicor.visualization.engine.frontend.blocks.TypedPatch
import io.aequicor.visualization.engine.frontend.diagnostics.DiagnosticCollector
import io.aequicor.visualization.engine.frontend.expr.SlmExpression
import io.aequicor.visualization.engine.frontend.frontmatter.SlmFrontmatter
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
import io.aequicor.visualization.engine.frontend.normalize.latinizeToCamelCase
import io.aequicor.visualization.engine.ir.model.JustifyContent

/**
 * Locale-neutral structural markers used when extraction runs without a full
 * semantic lexicon (structural unit tests): both built-in locales' condition and
 * component markers, no rules, no slug dictionaries.
 */
val StructuralMarkersLexicon: SemanticLexicon = SemanticLexicon(
    locale = SlmLocale("und"),
    conditionMarkers = listOf("если", "if"),
    componentMarkers = listOf("component", "компонент"),
    rules = emptyList(),
    nounSlugs = emptyMap(),
    actionSlugs = emptyMap(),
)

/**
 * Full semantic extraction: the structural mapping (headings to sections,
 * paragraphs/lists/blockquotes/images/tables to nodes, typed-block binding) plus
 * the lexicon rule layer of design section D — instruction paragraphs, empty-state
 * blockquotes, list-item mining, card repeats and mode instructions.
 */
fun extractSemantics(
    document: SlmMarkdownDocument,
    frontmatter: SlmFrontmatter,
    sourceLocale: SlmLocale,
    lexicon: SemanticLexicon,
    diagnostics: DiagnosticCollector,
): SemanticScreen = SemanticExtractor(diagnostics, lexicon)
    .extract(document, frontmatter, sourceLocale)

/**
 * Lexicon-free structural pass, kept as a seam for tests of the structural layer:
 * identical walk with only the structural markers active (no semantic rules).
 */
fun extractStructure(
    document: SlmMarkdownDocument,
    frontmatter: SlmFrontmatter,
    diagnostics: DiagnosticCollector,
    fallbackLocale: SlmLocale = SlmLocale("en-US"),
    lexicon: SemanticLexicon = StructuralMarkersLexicon,
): SemanticScreen = extractSemantics(
    document = document,
    frontmatter = frontmatter,
    sourceLocale = frontmatter.sourceLocale ?: fallbackLocale,
    lexicon = lexicon,
    diagnostics = diagnostics,
)

private class SemanticExtractor(
    private val diagnostics: DiagnosticCollector,
    private val lexicon: SemanticLexicon,
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
        var variant: Map<String, String> = emptyMap()
        var irSplice: IrSpliceBlock? = null
        var isAnchor = false
        var isComponentDef = false
        val propBindings = LinkedHashMap<String, SlmExpression>()
        val explicitPatches = mutableListOf<io.aequicor.visualization.engine.frontend.markdown.TypedEntry>()
        val semanticPatches = mutableListOf<TypedPatch>()
        val children = mutableListOf<NodeBuilder>()

        /** Slug-preferring path element for i18n key paths. */
        val pathElement: String get() = slugHint.ifEmpty { name }

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
                variant = variant,
                propBindings = propBindings.toMap(),
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
    private val screenModes = LinkedHashMap<String, String>()
    private var tableCount = 0

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

        val blocks = document.blocks
        var index = 0
        while (index < blocks.size) {
            val block = blocks[index]
            if (block is HeadingBlock) {
                handleHeading(block, stack, state)
                index++
                continue
            }
            val next = blocks.getOrNull(index + 1)
            val consumedNext = handleBlock(block, stack.last().second, state, sectionPath(stack), next)
            index += if (consumedNext) 2 else 1
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
            modes = screenModes.toMap(),
        )
    }

    private fun sectionPath(stack: ArrayDeque<Pair<Int, NodeBuilder>>): List<String> =
        stack.drop(1).map { (_, builder) -> builder.pathElement }

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
        applyHeadingName(section, inline, block.span, sectionPath(stack))
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
        path: List<String>,
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
                section.text = SemanticText(
                    defaultText = text,
                    params = inline.params,
                    explicitKey = inline.explicitKey,
                    keyHint = KeyHint.SectionTitle(path + text),
                    span = span,
                )
            }
        }
    }

    // --- non-heading blocks ---

    /** Handles one block; returns true when it also consumed [next] (group lead). */
    private fun handleBlock(
        block: SlmBlock,
        container: NodeBuilder,
        state: WalkState,
        path: List<String>,
        next: SlmBlock?,
    ): Boolean {
        when (block) {
            is TypedAttributeBlock -> (state.anchor ?: root).explicitPatches += block.entries

            is ParagraphBlock -> return handleParagraph(block, container, state, path, next)

            is ListBlock -> attach(container, state, listNode(block, path))

            is BlockquoteBlock -> handleBlockquote(block, container, state, path)

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

            is HeadingBlock -> handleNestedHeading(block, container, state, path)
        }
        return false
    }

    /** Walk with one-block lookahead so lead paragraphs can absorb a following list. */
    private fun walkBlocks(
        blocks: List<SlmBlock>,
        container: NodeBuilder,
        state: WalkState,
        path: List<String>,
    ) {
        var index = 0
        while (index < blocks.size) {
            val next = blocks.getOrNull(index + 1)
            val consumed = handleBlock(blocks[index], container, state, path, next)
            index += if (consumed) 2 else 1
        }
    }

    /** Headings inside list items/blockquotes become plain section children. */
    private fun handleNestedHeading(
        block: HeadingBlock,
        container: NodeBuilder,
        state: WalkState,
        path: List<String>,
    ) {
        val section = NodeBuilder(SemanticKind.Section, block.span).apply { isAnchor = true }
        applyHeadingName(section, readInlineText(block.inlines), block.span, path)
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

    private fun handleParagraph(
        block: ParagraphBlock,
        container: NodeBuilder,
        state: WalkState,
        path: List<String>,
        next: SlmBlock?,
    ): Boolean {
        conditionOf(block)?.let {
            if (state.pendingCondition != null) {
                diagnostics.warning("Condition paragraph replaces an unattached condition", block.span)
            }
            state.pendingCondition = it
            return false
        }
        val full = readInlineText(block.inlines)

        // "Lead:" paragraph + list -> named group with the items as children.
        if (next is ListBlock && full.links.isEmpty() && full.text.endsWith(":") &&
            full.text.trimEnd(':').isNotBlank()
        ) {
            val group = groupLeadNode(block, full, path, next, state)
            attach(container, state, group)
            state.anchor = group
            state.pendingKey = null
            return true
        }

        val split = splitAtColon(block.inlines)
        val lead = readInlineText(split?.first ?: block.inlines)
        val leadMatches = lexicon.matchesIn(lead.text)
        if (leadMatches.isEmpty()) {
            if (split != null && lexicon.rules.isNotEmpty()) reportAmbiguity(full.text, block.span)
            attachPlainParagraph(block, full, container, state, path)
            return false
        }

        val node = if (split != null) {
            instructionNode(block, lead, leadMatches, split.second, path)
        } else {
            wholeInstructionNode(block, full, leadMatches, path)
        }
        if (node != null) {
            attach(container, state, node)
            state.anchor = node
        }
        state.pendingKey = null
        return false
    }

    /** Structural fallback: a text node with link children (or standalone actions). */
    private fun attachPlainParagraph(
        block: ParagraphBlock,
        inline: InlineText,
        container: NodeBuilder,
        state: WalkState,
        path: List<String>,
    ) {
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
                    keyHint = KeyHint.SectionText(path),
                    span = block.span,
                )
                builder.children += actions
            }
        }
        state.pendingKey = null
        attach(container, state, node)
    }

    /** `Фильтры:` + list -> group; slug via nounSlugs, else a single repeat's collection. */
    private fun groupLeadNode(
        block: ParagraphBlock,
        full: InlineText,
        path: List<String>,
        list: ListBlock,
        state: WalkState,
    ): NodeBuilder {
        val name = full.text.trimEnd(':').trim()
        val slug = lexicon.nounSlug(name)
            ?: list.items.firstNotNullOfOrNull { item ->
                item.inlines.filterIsInstance<ExpressionRun>().firstNotNullOfOrNull {
                    (it.expression as? SlmExpression.Repeat)?.collection?.segments?.lastOrNull()
                }
            }
            .orEmpty()
        val group = NodeBuilder(SemanticKind.Group, SlmSourceSpan(block.span.startLine, list.span.endLine))
        group.name = name
        group.slugHint = slug
        group.isAnchor = true
        group.text = SemanticText(
            defaultText = name,
            params = full.params,
            explicitKey = full.explicitKey ?: state.pendingKey,
            keyHint = KeyHint.SectionTitle(path + group.pathElement),
            span = block.span,
        )
        val groupPath = path + group.pathElement
        list.items.forEach { item -> group.children += itemNode(item, groupPath, cardCollection = null) }
        return group
    }

    /** `Lead: seg1, seg2` with a matched lead -> base node plus mined segment children. */
    private fun instructionNode(
        block: ParagraphBlock,
        lead: InlineText,
        leadMatches: List<RuleMatch>,
        tail: List<SlmInline>,
        path: List<String>,
    ): NodeBuilder? {
        val effects = leadMatches.flatMap { it.rule.effects }
        effects.filterIsInstance<SemanticEffect.SetMode>().forEach { screenModes[it.dimension] = it.value }
        // Mode-only instruction: nothing visible remains.
        if (!effects.hasStructuralEffect()) return null
        val leadSynth = leadMatches.firstOrNull { match ->
            match.rule.effects.any { it is SemanticEffect.SynthesizeInstance }
        }
        if (leadSynth != null) {
            // "Основная кнопка: [Создать](/x)" — the whole paragraph is the instance.
            return synthesizedInstance(
                effects = leadSynth.rule.effects,
                matchedPhrase = leadSynth.phrase,
                inline = readInlineText(block.inlines),
                labelText = null,
                labelHint = null,
                span = block.span,
            ).also { it.isAnchor = true }
        }
        val base = baseInstructionNode(effects, lead.text, block.span)
        processSegments(
            segments = splitSegments(tail),
            base = base,
            childPath = path + base.pathElement,
            emptyState = base.kind == SemanticKind.EmptyState,
            fallbackSpan = block.span,
        )
        return base
    }

    /** Whole-paragraph instruction (no colon): the matched paragraph is the node. */
    private fun wholeInstructionNode(
        block: ParagraphBlock,
        full: InlineText,
        matches: List<RuleMatch>,
        path: List<String>,
    ): NodeBuilder? {
        val effects = matches.flatMap { it.rule.effects }
        effects.filterIsInstance<SemanticEffect.SetMode>().forEach { screenModes[it.dimension] = it.value }
        val remainder = stripMatches(full.text, matches)
        if (!effects.hasStructuralEffect()) {
            if (remainder.isBlank() && full.links.isEmpty()) return null
            val plain = NodeBuilder(SemanticKind.Text, block.span)
            plain.text = SemanticText(
                defaultText = full.text,
                params = full.params,
                explicitKey = full.explicitKey,
                keyHint = KeyHint.SectionText(path),
                span = block.span,
            )
            plain.children += full.links.map { actionNode(it, block.span) }
            return plain
        }
        val synthRule = matches.firstOrNull { match ->
            match.rule.effects.any { it is SemanticEffect.SynthesizeInstance }
        }
        if (synthRule != null) {
            return synthesizedInstance(
                effects = synthRule.rule.effects,
                matchedPhrase = synthRule.phrase,
                inline = full,
                labelText = remainder.takeIf { it.isNotBlank() },
                labelHint = null,
                span = block.span,
            ).also { it.isAnchor = true }
        }
        val base = baseInstructionNode(effects, full.text, block.span)
        if (base.kind == SemanticKind.EmptyState && remainder.isNotBlank()) {
            base.children += emptyTitleNode(remainder, full, block.span)
        } else if (base.role == "title") {
            base.kind = SemanticKind.Text
            base.text = SemanticText(
                defaultText = remainder,
                params = full.params,
                explicitKey = full.explicitKey,
                keyHint = KeyHint.SectionTitle(path),
                span = block.span,
            )
        }
        return base
    }

    private fun baseInstructionNode(
        effects: List<SemanticEffect>,
        name: String,
        span: SlmSourceSpan,
    ): NodeBuilder {
        val role = effects.filterIsInstance<SemanticEffect.SetRole>().firstOrNull()?.role.orEmpty()
        val isEmptyState = effects.any { it is SemanticEffect.MarkEmptyState }
        val base = NodeBuilder(if (isEmptyState) SemanticKind.EmptyState else SemanticKind.Group, span)
        base.role = if (isEmptyState && role.isEmpty()) "emptyState" else role
        base.name = name
        base.slugHint = if (isEmptyState) "emptyState" else role
        // Rule-based containers are frames (spec's topbar extraction example).
        base.semanticPatches += NodePatch(type = "frame")
        base.semanticPatches += effects.filterIsInstance<SemanticEffect.Patch>().map { it.patch }
        base.isAnchor = true
        return base
    }

    /** Tail segments of an instruction paragraph (or an empty-state blockquote lead). */
    private fun processSegments(
        segments: List<List<SlmInline>>,
        base: NodeBuilder,
        childPath: List<String>,
        emptyState: Boolean,
        fallbackSpan: SlmSourceSpan,
    ) {
        var emptyTitleDone = false
        segments.forEach { segmentInlines ->
            val inline = readInlineText(segmentInlines)
            if (inline.text.isBlank() && inline.links.isEmpty()) return@forEach
            val span = spanOf(segmentInlines, fallbackSpan)
            val matches = lexicon.matchesIn(inline.text)
            val effects = matches.flatMap { it.rule.effects }
            if (effects.any { it is SemanticEffect.AlignTrailingEnd }) {
                base.semanticPatches += LayoutPatch(distribution = JustifyContent.SpaceBetween)
            }
            effects.filterIsInstance<SemanticEffect.SetMode>().forEach { screenModes[it.dimension] = it.value }
            val synthRule = matches.firstOrNull { match ->
                match.rule.effects.any { it is SemanticEffect.SynthesizeInstance }
            }
            val roleRule = matches.firstOrNull { match ->
                match.rule.effects.any { it is SemanticEffect.SetRole } &&
                    match.rule.effects.none { it is SemanticEffect.SynthesizeInstance }
            }
            when {
                synthRule != null -> base.children += synthesizedInstance(
                    effects = synthRule.rule.effects,
                    matchedPhrase = synthRule.phrase,
                    inline = inline,
                    labelText = null,
                    labelHint = null,
                    span = span,
                )
                roleRule != null -> {
                    val role = roleRule.rule.effects.filterIsInstance<SemanticEffect.SetRole>().first().role
                    val content = stripMatches(inline.text, listOf(roleRule))
                    base.children += NodeBuilder(SemanticKind.Text, span).apply {
                        this.role = role
                        slugHint = lexicon.nounSlugs[roleRule.phrase] ?: role
                        text = SemanticText(
                            defaultText = content,
                            params = inline.params,
                            explicitKey = inline.explicitKey,
                            keyHint = if (emptyState) KeyHint.EmptyTitle else KeyHint.SectionTitle(childPath),
                            span = span,
                        )
                    }
                    if (emptyState) emptyTitleDone = true
                }
                emptyState && !emptyTitleDone -> {
                    base.children += emptyTitleNode(inline.text, inline, span)
                    base.children += inline.links.map { actionNode(it, span) }
                    emptyTitleDone = true
                }
                inline.text.isBlank() && inline.links.isNotEmpty() ->
                    base.children += inline.links.map { actionNode(it, span) }
                else -> base.children += NodeBuilder(SemanticKind.Text, span).apply {
                    text = SemanticText(
                        defaultText = inline.text,
                        params = inline.params,
                        explicitKey = inline.explicitKey,
                        keyHint = KeyHint.SectionText(childPath),
                        span = span,
                    )
                    children += inline.links.map { actionNode(it, span) }
                }
            }
        }
    }

    private fun emptyTitleNode(content: String, inline: InlineText, span: SlmSourceSpan): NodeBuilder =
        NodeBuilder(SemanticKind.Text, span).apply {
            role = "title"
            slugHint = "emptyTitle"
            text = SemanticText(
                defaultText = content,
                params = inline.params,
                explicitKey = inline.explicitKey,
                keyHint = KeyHint.EmptyTitle,
                span = span,
            )
        }

    /**
     * Instance synthesized by a rule ([SemanticEffect.SynthesizeInstance]): a link
     * becomes its action + label, a lead label becomes its `label` prop, and the
     * first inline expression binds to `content` (badge) / `value` (other).
     */
    private fun synthesizedInstance(
        effects: List<SemanticEffect>,
        matchedPhrase: String,
        inline: InlineText,
        labelText: String?,
        labelHint: KeyHint?,
        span: SlmSourceSpan,
    ): NodeBuilder {
        val synth = effects.filterIsInstance<SemanticEffect.SynthesizeInstance>().first()
        val node = NodeBuilder(SemanticKind.Instance, span)
        node.role = effects.filterIsInstance<SemanticEffect.SetRole>().firstOrNull()?.role.orEmpty()
        node.componentRef = synth.componentRef
        node.variant = synth.variant
        node.slugHint = lexicon.nounSlugs[matchedPhrase] ?: node.role
        val link = inline.links.firstOrNull()
        if (link != null) {
            val label = readInlineText(link.label)
            val slug = actionSlugFor(label.text, link.target, span)
            node.name = label.text
            if (node.slugHint.isEmpty() || node.role.isNotEmpty()) node.slugHint = slug
            node.action = SemanticAction(
                navigateTo = link.target,
                label = SemanticText(
                    defaultText = label.text,
                    params = label.params,
                    explicitKey = link.i18nKeyOverride ?: inline.explicitKey,
                    keyHint = KeyHint.ActionLabel(slug),
                    span = span,
                ),
            )
        } else if (labelText != null) {
            node.name = labelText
            node.text = SemanticText(
                defaultText = labelText,
                params = emptyMap(),
                explicitKey = inline.explicitKey,
                keyHint = labelHint ?: KeyHint.ActionLabel(actionSlugFor(labelText, "", span)),
                span = span,
            )
        }
        inline.params.values.firstOrNull()?.let { expression ->
            val prop = if (effects.any { it is SemanticEffect.MarkBadge }) "content" else "value"
            node.propBindings[prop] = expression
        }
        return node
    }

    /** Ambiguity per spec: instruction shape ("Lead: ...") without a matching rule. */
    private fun reportAmbiguity(text: String, span: SlmSourceSpan) {
        val words = proseWords(text)
        val suggestions = lexicon.rules
            .map { rule ->
                rule to rule.phrases.maxOf { phrase ->
                    normalizeProse(phrase).split(' ').count { it in words }
                }
            }
            .sortedByDescending { (_, score) -> score }
            .take(3)
            .map { (rule, _) -> rule.phrases.first() }
        diagnostics.warning(
            "Ambiguous semantic instruction: \"$text\" — cannot infer node role. " +
                "Suggested fixes: " + suggestions.joinToString("; ") { "\"$it ...\"" },
            span,
        )
    }

    private fun proseWords(text: String): Set<String> {
        val words = mutableSetOf<String>()
        val current = StringBuilder()
        normalizeProse(text).forEach { char ->
            if (char.isLetterOrDigit()) {
                current.append(char)
            } else if (current.isNotEmpty()) {
                words += current.toString()
                current.clear()
            }
        }
        if (current.isNotEmpty()) words += current.toString()
        return words
    }

    private fun actionNode(link: LinkRun, span: SlmSourceSpan): NodeBuilder {
        val labelText = readInlineText(link.label)
        val slug = actionSlugFor(labelText.text, link.target, span)
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

    /** Action slug precedence: lexicon.actionSlugs > route slug > transliteration + info. */
    private fun actionSlugFor(label: String, target: String, span: SlmSourceSpan): String {
        lexicon.actionSlug(label)?.let { return it }
        if (!target.contains("{{")) {
            routeSlug(target).takeIf { it.isNotEmpty() }?.let { return it }
        }
        val slug = latinizeToCamelCase(label).ifEmpty { "action" }
        if (lexicon.rules.isNotEmpty()) {
            diagnostics.info(
                "Action label \"$label\" has no lexicon or route slug; using transliterated " +
                    "\"$slug\" — consider an explicit <!-- i18n:key=... --> override",
                span,
            )
        }
        return slug
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

    // --- blockquotes ---

    private fun handleBlockquote(
        block: BlockquoteBlock,
        container: NodeBuilder,
        state: WalkState,
        path: List<String>,
    ) {
        val first = block.blocks.firstOrNull() as? ParagraphBlock
        val split = first?.let { splitAtColon(it.inlines) }
        val leadInlines = split?.first ?: first?.inlines
        val leadMatches = leadInlines?.let { lexicon.matchesIn(readInlineText(it).text) }.orEmpty()
        val isEmptyState = leadMatches.any { match ->
            match.rule.effects.any { it is SemanticEffect.MarkEmptyState }
        }

        val callout: NodeBuilder
        if (isEmptyState && first != null) {
            callout = NodeBuilder(SemanticKind.EmptyState, block.span).apply {
                role = "emptyState"
                slugHint = "emptyState"
                name = readInlineText(leadInlines.orEmpty()).text
                isAnchor = true
            }
            processSegments(
                segments = splitSegments(split?.second ?: emptyList()),
                base = callout,
                childPath = path + callout.pathElement,
                emptyState = true,
                fallbackSpan = first.span,
            )
            val nested = WalkState().apply { anchor = callout }
            walkBlocks(block.blocks.drop(1), callout, nested, path + callout.pathElement)
        } else {
            callout = NodeBuilder(SemanticKind.Callout, block.span).apply {
                role = "callout"
                isAnchor = true
            }
            val nested = WalkState().apply { anchor = callout }
            walkBlocks(block.blocks, callout, nested, path)
        }
        attach(container, state, callout)
        state.anchor = callout
    }

    // --- lists ---

    private fun listNode(block: ListBlock, path: List<String>): NodeBuilder {
        val group = NodeBuilder(SemanticKind.Group, block.span)
        block.items.forEach { item -> group.children += itemNode(item, path, cardCollection = null) }
        return group
    }

    private fun itemNode(item: SlmListItem, path: List<String>, cardCollection: String?): NodeBuilder {
        val inline = readInlineText(item.inlines)
        val repeatExpr = item.inlines
            .filterIsInstance<ExpressionRun>()
            .firstNotNullOfOrNull { it.expression as? SlmExpression.Repeat }
        if (repeatExpr != null) return repeatNode(item, inline, repeatExpr, path)

        val split = splitAtColon(item.inlines)
        val lead = split?.let { readInlineText(it.first) }
        val tail = split?.let { readInlineText(it.second) }
        val fieldSlug = lead?.text?.let { lexicon.nounSlug(it) ?: lexicon.leadingNounSlug(it) }
            ?: lexicon.leadingNounSlug(inline.text)
        val matches = lexicon.matchesIn(inline.text)
        val synthRule = matches.firstOrNull { match ->
            match.rule.effects.any { it is SemanticEffect.SynthesizeInstance }
        }

        val node = when {
            synthRule != null && item.children.isEmpty() -> synthesizedInstance(
                effects = synthRule.rule.effects,
                matchedPhrase = synthRule.phrase,
                inline = inline,
                labelText = lead?.text?.takeIf { it.isNotBlank() },
                labelHint = fieldHint(cardCollection, fieldSlug, lead?.text.orEmpty(), path),
                span = item.span,
            ).also { synthesized ->
                if (fieldSlug != null) synthesized.slugHint = fieldSlug
            }
            // "Действие: [Открыть](/x)" — the lead labels a single action.
            tail != null && tail.text.isBlank() && tail.links.size == 1 && item.children.isEmpty() ->
                actionNode(tail.links.single(), item.span)
            inline.text.isBlank() && inline.links.size == 1 && item.children.isEmpty() ->
                actionNode(inline.links.single(), item.span)
            item.children.isEmpty() -> NodeBuilder(SemanticKind.Text, item.span).apply {
                slugHint = fieldSlug.orEmpty()
                text = SemanticText(
                    defaultText = inline.text,
                    params = inline.params,
                    explicitKey = inline.explicitKey,
                    keyHint = fieldHint(cardCollection, fieldSlug, lead?.text.orEmpty(), path),
                    span = item.span,
                )
                children += inline.links.map { actionNode(it, item.span) }
            }
            else -> NodeBuilder(SemanticKind.Group, item.span).apply {
                name = inline.textWithoutParams
                slugHint = fieldSlug ?: lexicon.nounSlug(name).orEmpty()
                if (inline.text.isNotBlank()) {
                    children += NodeBuilder(SemanticKind.Text, item.span).apply {
                        text = SemanticText(
                            defaultText = inline.text,
                            params = inline.params,
                            explicitKey = inline.explicitKey,
                            keyHint = fieldHint(cardCollection, fieldSlug, lead?.text.orEmpty(), path),
                            span = item.span,
                        )
                    }
                }
                children += inline.links.map { actionNode(it, item.span) }
            }
        }
        node.isAnchor = true
        val nested = WalkState().apply { anchor = node }
        walkBlocks(item.children, node, nested, path + listOfNotNull(node.pathElement.takeIf { it.isNotBlank() }))
        return node
    }

    /** `Карточка для каждой {{mission in missions}}:` -> card repeat node. */
    private fun repeatNode(
        item: SlmListItem,
        inline: InlineText,
        repeatExpr: SlmExpression.Repeat,
        path: List<String>,
    ): NodeBuilder {
        val node = NodeBuilder(SemanticKind.Repeat, item.span).apply {
            repeat = repeatExpr
            name = inline.textWithoutParams
            slugHint = latinizeToCamelCase("${repeatExpr.itemName} card")
            if (lexicon.matchesIn(inline.text).any { match ->
                    match.rule.effects.any { it is SemanticEffect.MarkCardRepeat }
                }
            ) {
                role = "card"
            }
            isAnchor = true
        }
        val collection = repeatExpr.collection.segments.lastOrNull() ?: repeatExpr.itemName
        val nested = WalkState().apply { anchor = node }
        item.children.forEach { child ->
            when (child) {
                // Repeat items: nested list items become the card children directly.
                is ListBlock -> child.items.forEach { node.children += itemNode(it, path, collection) }
                else -> handleBlock(child, node, nested, path, next = null)
            }
        }
        return node
    }

    private fun fieldHint(
        cardCollection: String?,
        fieldSlug: String?,
        leadText: String,
        path: List<String>,
    ): KeyHint = when {
        cardCollection != null && (fieldSlug != null || leadText.isNotBlank()) ->
            KeyHint.CardField(cardCollection, fieldSlug ?: latinizeToCamelCase(leadText))
        else -> KeyHint.SectionText(path)
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
        tableCount++
        val tableSlug = "table$tableCount"
        val table = NodeBuilder(SemanticKind.Table, block.span).apply {
            isAnchor = true
            slugHint = tableSlug
        }
        table.children += tableRow(block.header, block.span, tableSlug)
        block.rows.forEach { row -> table.children += tableRow(row, block.span, tableSlug = null) }
        return table
    }

    private fun tableRow(
        cells: List<List<SlmInline>>,
        span: SlmSourceSpan,
        tableSlug: String?,
    ): NodeBuilder {
        val row = NodeBuilder(SemanticKind.Group, span).apply {
            role = if (tableSlug != null) "tableHeader" else "tableRow"
        }
        cells.forEach { cell ->
            val inline = readInlineText(cell)
            row.children += NodeBuilder(SemanticKind.Text, span).apply {
                text = SemanticText(
                    defaultText = inline.text,
                    params = inline.params,
                    explicitKey = inline.explicitKey,
                    keyHint = tableSlug?.let { KeyHint.TableHeader(it, inline.text) } ?: KeyHint.Plain,
                    span = span,
                )
            }
        }
        return row
    }

    // --- inline segmentation ---

    /** Splits at the first ':' found in a text run; null when there is none. */
    private fun splitAtColon(inlines: List<SlmInline>): Pair<List<SlmInline>, List<SlmInline>>? {
        inlines.forEachIndexed { index, inline ->
            if (inline !is TextRun) return@forEachIndexed
            val colon = inline.text.indexOf(':')
            if (colon < 0) return@forEachIndexed
            val lead = inlines.take(index) + listOfNotNull(textRunSlice(inline, 0, colon))
            val tail = listOfNotNull(textRunSlice(inline, colon + 1, inline.text.length)) +
                inlines.drop(index + 1)
            return lead to tail
        }
        return null
    }

    /** Splits by ',' and '.' inside text runs; links/expressions never split. */
    private fun splitSegments(inlines: List<SlmInline>): List<List<SlmInline>> {
        val segments = mutableListOf<List<SlmInline>>()
        var current = mutableListOf<SlmInline>()
        inlines.forEach { inline ->
            if (inline is TextRun) {
                var start = 0
                inline.text.forEachIndexed { index, char ->
                    if (char == ',' || char == '.') {
                        textRunSlice(inline, start, index)?.let { current += it }
                        segments += current
                        current = mutableListOf()
                        start = index + 1
                    }
                }
                textRunSlice(inline, start, inline.text.length)?.let { current += it }
            } else {
                current += inline
            }
        }
        segments += current
        return segments.filter { it.isNotEmpty() }
    }

    private fun textRunSlice(run: TextRun, from: Int, to: Int): TextRun? {
        if (from >= to) return null
        val text = run.text.substring(from, to)
        if (text.isBlank()) return null
        return TextRun(text, run.line, run.column + from)
    }

    private fun spanOf(inlines: List<SlmInline>, fallback: SlmSourceSpan): SlmSourceSpan =
        if (inlines.isEmpty()) fallback else SlmSourceSpan(inlines.minOf { it.line }, inlines.maxOf { it.line })

    /** Removes matched phrase ranges from [text], trimming leftover separators. */
    private fun stripMatches(text: String, matches: List<RuleMatch>): String {
        if (matches.isEmpty()) return text.trim()
        val result = StringBuilder()
        var last = 0
        matches.sortedBy { it.range.first }.forEach { match ->
            if (match.range.first > last) result.append(text, last, match.range.first)
            last = match.range.last + 1
        }
        if (last < text.length) result.append(text, last, text.length)
        return result.toString().trim().trim(',', '.', ':', ';').trim()
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

/** Effects that materialize structure (vs. modes/alignment side effects only). */
private fun List<SemanticEffect>.hasStructuralEffect(): Boolean = any {
    it is SemanticEffect.SetRole || it is SemanticEffect.Patch ||
        it is SemanticEffect.SynthesizeInstance || it is SemanticEffect.MarkEmptyState ||
        it is SemanticEffect.MarkBadge || it is SemanticEffect.MarkCardRepeat
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
