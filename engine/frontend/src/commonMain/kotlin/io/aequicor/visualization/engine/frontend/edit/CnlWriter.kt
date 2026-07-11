package io.aequicor.visualization.engine.frontend.edit

import io.aequicor.visualization.engine.frontend.cnl.CnlElement
import io.aequicor.visualization.engine.frontend.cnl.CnlEmitter
import io.aequicor.visualization.engine.frontend.cnl.CnlGrammar
import io.aequicor.visualization.engine.frontend.cnl.CnlParser
import io.aequicor.visualization.engine.frontend.cnl.CnlProperty
import io.aequicor.visualization.engine.frontend.cnl.CnlPropertyKind
import io.aequicor.visualization.engine.frontend.cnl.CnlSpan
import io.aequicor.visualization.engine.frontend.diagnostics.DiagnosticCollector
import io.aequicor.visualization.engine.frontend.markdown.SlmSourceSpan
import io.aequicor.visualization.engine.ir.model.Bindable
import io.aequicor.visualization.engine.ir.model.DesignColor
import io.aequicor.visualization.engine.ir.model.DesignCornerRadius
import io.aequicor.visualization.engine.ir.model.DesignNode
import io.aequicor.visualization.engine.ir.model.DesignPaint
import io.aequicor.visualization.engine.ir.model.DesignTextStyle
import io.aequicor.visualization.engine.ir.model.DesignUnit
import io.aequicor.visualization.engine.ir.model.SizingMode
import io.aequicor.visualization.engine.ir.model.TextAlignHorizontal
import io.aequicor.visualization.engine.ir.model.TextAlignVertical
import io.aequicor.visualization.engine.ir.model.UnitValue
import io.aequicor.visualization.engine.ir.model.literalOrNull

/**
 * Surgical write-back into a CNL element sentence. Mirrors the typed-block patcher's
 * re-parse-on-demand design in three tiers: tier-1 replaces a property's value token in place
 * (minimal diff), tier-2 appends a missing property phrase, and tier-3 regenerates the whole
 * sentence from the patched [DesignNode] via [CnlEmitter] when neither surgical form fits. A
 * CNL-owned node therefore never falls back to a YAML typed block; if even tier-3 is
 * unavailable (no patched node), the edit returns [WritePlan.Failed] for an in-memory fallback.
 */
internal object CnlWriter {
    fun plan(
        source: String,
        sentenceSpan: SlmSourceSpan,
        edit: SlmEdit,
        lineIndex: LineIndex,
        patchedNode: DesignNode? = null,
    ): WritePlan {
        val surgical = surgicalPlan(sentenceSpan, edit, lineIndex)
        if (surgical is WritePlan.Ops) return surgical
        // Tier-3: regenerate the sentence from the patched node. Only reached when the edit
        // is not surgically expressible; the caller's recompile-success gate vetoes a bad re-emit.
        patchedNode?.let { return reemitPlan(sentenceSpan, it, lineIndex) }
        return surgical
    }

    /** Tiers 1-2: replace a value in place or append a phrase; [WritePlan.Failed] otherwise. */
    private fun surgicalPlan(sentenceSpan: SlmSourceSpan, edit: SlmEdit, lineIndex: LineIndex): WritePlan {
        val line = sentenceSpan.startLine
        val text = lineIndex.lineText(line)
        val element = parseCnlElement(text, line)
            ?: return failed(line)
        return when (edit) {
            is SetFills -> fillPlan(element, edit.fills, lineIndex, line)
            is SetStyleProperty -> when (edit.property) {
                StyleProp.FirstFillToken -> scalarText(edit.value)?.let { fillLiteralPlan(element, it, lineIndex, line) } ?: failed(line)
                StyleProp.Radius -> scalarPropPlan(element, CnlPropertyKind.Radius, "radius", edit.value, lineIndex, line)
                StyleProp.Opacity -> scalarPropPlan(element, CnlPropertyKind.Opacity, "opacity", edit.value, lineIndex, line)
            }
            is SetCornerRadii -> radiusPlan(element, edit.radius, lineIndex, line)
            is SetLayoutProperty -> when (edit.property) {
                LayoutProp.Gap -> scalarPropPlan(element, CnlPropertyKind.Gap, "gap", edit.value, lineIndex, line)
                else -> failed(line)
            }
            is SetSizing -> sizingPlan(element, edit, lineIndex, line)
            is SetNodePosition -> positionPlan(element, edit, lineIndex, line)
            is SetText -> textPlan(element, edit.defaultText, lineIndex, line)
            is SetTextStyle -> textStylePlan(element, edit.style, lineIndex, line)
            else -> failed(line)
        }
    }

    /** Tier-3: replace the whole sentence line with [CnlEmitter]'s regeneration of [node]. */
    private fun reemitPlan(sentenceSpan: SlmSourceSpan, node: DesignNode, lineIndex: LineIndex): WritePlan {
        val line = sentenceSpan.startLine
        val text = lineIndex.lineText(line)
        val heading = headingMatch(text)
        val sentence = if (heading != null) {
            CnlEmitter.emitStableHeadingLine(node, level = heading.first, includeId = true)
        } else {
            CnlEmitter.emitSentence(node, includeId = true)
        }
        // A no-op (regenerated line equals the current one) is legitimate when a prior edit in a
        // multi-edit batch already wrote the whole re-emitted sentence; report an empty op. The
        // inexpressible case (the emitter genuinely cannot represent the edit, so the line does not
        // change) is caught downstream by the reducer's fidelity veto, which recompiles and keeps
        // the edit in-memory when the source diverges from the intended node.
        if (sentence == text) return WritePlan.Ops(emptyList())
        val start = lineIndex.offsetOf(line, 1)
        val end = lineIndex.offsetOf(line, text.length + 1)
        return WritePlan.Ops(listOf(TextOp(start, end, sentence)))
    }

    // --- per-edit plans ---

    private fun parseCnlElement(text: String, line: Int): CnlElement? {
        val heading = headingMatch(text)
        if (heading != null) {
            val (_, contentStart) = heading
            val content = text.substring(contentStart).trimEnd()
            return CnlParser.parseHeading(content, line, contentStart + 1, DiagnosticCollector())?.element
        }
        return CnlParser.parseElement(text, line, baseColumn = 1, DiagnosticCollector())
    }

    private fun headingMatch(text: String): Pair<Int, Int>? {
        val level = text.takeWhile { it == '#' }.length
        if (level == 0 || level > 6) return null
        if (level >= text.length || text[level] != ' ') return null
        var contentStart = level
        while (contentStart < text.length && text[contentStart] == ' ') contentStart++
        return level to contentStart
    }

    private fun fillPlan(element: CnlElement, fills: List<DesignPaint>, lineIndex: LineIndex, line: Int): WritePlan {
        val color = (fills.singleOrNull() as? DesignPaint.Solid)?.let(::colorLiteral) ?: return failed(line)
        return fillLiteralPlan(element, color, lineIndex, line)
    }

    private fun fillLiteralPlan(element: CnlElement, color: String, lineIndex: LineIndex, line: Int): WritePlan {
        val fill = element.property(CnlPropertyKind.Fill)
        return if (fill != null) {
            replace(fill.values.first().span, color, lineIndex)
        } else {
            append(line, "color $color", lineIndex)
        }
    }

    private fun radiusPlan(element: CnlElement, radius: DesignCornerRadius, lineIndex: LineIndex, line: Int): WritePlan {
        val uniform = radius.topLeft.literalOrNull()
            ?.takeIf { radius.topRight.literalOrNull() == it && radius.bottomRight.literalOrNull() == it && radius.bottomLeft.literalOrNull() == it }
            ?: return failed(line)
        return scalarReplace(element, CnlPropertyKind.Radius, "radius", formatNumber(uniform), lineIndex, line)
    }

    private fun scalarPropPlan(
        element: CnlElement,
        kind: CnlPropertyKind,
        keyword: String,
        value: YamlScalarValue,
        lineIndex: LineIndex,
        line: Int,
    ): WritePlan {
        val text = scalarText(value) ?: return failed(line)
        return scalarReplace(element, kind, keyword, text, lineIndex, line)
    }

    private fun scalarReplace(
        element: CnlElement,
        kind: CnlPropertyKind,
        keyword: String,
        value: String,
        lineIndex: LineIndex,
        line: Int,
    ): WritePlan {
        val property = element.property(kind)
        return if (property != null) {
            replace(property.values.first().span, value, lineIndex)
        } else {
            append(line, "$keyword $value", lineIndex)
        }
    }

    private fun sizingPlan(element: CnlElement, edit: SetSizing, lineIndex: LineIndex, line: Int): WritePlan {
        val width = edit.width?.takeIf { it.mode == SizingMode.Fixed }?.value
        val height = edit.height?.takeIf { it.mode == SizingMode.Fixed }?.value
        val size = element.property(CnlPropertyKind.Size)
        if (size == null || size.values.size != 2 || width == null || height == null) return failed(line)
        return WritePlan.Ops(
            listOf(
                op(size.values[0].span, formatNumber(width), lineIndex),
                op(size.values[1].span, formatNumber(height), lineIndex),
            ),
        )
    }

    private fun positionPlan(element: CnlElement, edit: SetNodePosition, lineIndex: LineIndex, line: Int): WritePlan {
        val position = element.property(CnlPropertyKind.Position)
        return if (position != null && position.values.size == 2) {
            WritePlan.Ops(
                listOf(
                    op(position.values[0].span, formatNumber(edit.x), lineIndex),
                    op(position.values[1].span, formatNumber(edit.y), lineIndex),
                ),
            )
        } else {
            append(line, "position ${formatNumber(edit.x)} ${formatNumber(edit.y)}", lineIndex)
        }
    }

    private fun textPlan(element: CnlElement, defaultText: String, lineIndex: LineIndex, line: Int): WritePlan {
        val literal = element.textLiteral ?: return failed(line)
        // The literal span covers the raw inner text of «…», so the new value must be re-escaped.
        return replace(literal.span, CnlGrammar.escapeText(defaultText), lineIndex)
    }

    private fun textStylePlan(element: CnlElement, style: DesignTextStyle, lineIndex: LineIndex, line: Int): WritePlan {
        // Variable axes and OpenType features render as `(key value)` groups with no single value
        // span the surgical tiers can replace or append cleanly; defer to tier-3 whole-sentence
        // re-emit, which regenerates `features (…)` / `axes (…)` from the patched node. Without a
        // patched node the caller falls back in-memory rather than silently dropping them.
        if (style.fontFeatures.isNotEmpty() || style.variableAxes.isNotEmpty()) return failed(line)
        val ops = mutableListOf<TextOp>()
        val appendPhrases = mutableListOf<String>()

        fun addValue(kind: CnlPropertyKind, keyword: String, value: String, appendValue: String = value) {
            val property = element.property(kind)
            if (property != null) {
                ops += op(property.values.first().span, value, lineIndex)
            } else {
                appendPhrases += "$keyword $appendValue"
            }
        }

        fun addPhrase(kind: CnlPropertyKind, phrase: String) {
            val property = element.property(kind)
            if (property != null) {
                ops += op(property.phraseSpan, phrase, lineIndex)
            } else {
                appendPhrases += phrase
            }
        }

        style.fontFamily?.takeIf { it.isNotEmpty() }?.let { family ->
            val property = element.property(CnlPropertyKind.FontFamily)
            if (property != null) {
                // A bare-token value span (`font Inter`) cannot absorb spaces or keywords;
                // rewrite it as a «…» literal unless the span already sits inside one.
                val span = property.values.first().span
                val quoted = isQuotedLiteral(lineIndex.lineText(line), span)
                val replacement = if (quoted) CnlGrammar.escapeText(family) else CnlGrammar.quoteText(family)
                ops += op(span, replacement, lineIndex)
            } else {
                appendPhrases += "font ${CnlGrammar.quoteText(family)}"
            }
        }
        style.fontSize?.literalOrNull()?.let { addValue(CnlPropertyKind.FontSize, "size", formatNumber(it)) }
        style.fontWeight?.literalOrNull()?.let { addPhrase(CnlPropertyKind.FontWeight, fontWeightPhrase(it)) }
        style.lineHeight?.let { addValue(CnlPropertyKind.LineHeight, "line-height", unitText(it)) }
        style.letterSpacing?.let { addValue(CnlPropertyKind.Tracking, "tracking", unitText(it)) }
        style.paragraphSpacing?.let {
            addValue(CnlPropertyKind.ParagraphSpacing, "paragraph-spacing", formatNumber(it))
        }
        style.textAlignHorizontal?.let {
            addValue(CnlPropertyKind.TextAlign, "text-align", horizontalAlignWord(it))
        }
        style.textAlignVertical?.let {
            addValue(CnlPropertyKind.TextValign, "text-valign", verticalAlignWord(it))
        }

        if (appendPhrases.isNotEmpty()) {
            val end = lineIndex.offsetOf(line, lineIndex.lineText(line).length + 1)
            ops += TextOp(end, end, " ${appendPhrases.joinToString(" ")}")
        }
        return WritePlan.Ops(ops)
    }

    // --- helpers ---

    private fun CnlElement.property(kind: CnlPropertyKind): CnlProperty? =
        properties.firstOrNull { it.kind == kind }

    /** True when [span] is the inner region of a `«…»`/`"…"` literal (opener right before it). */
    private fun isQuotedLiteral(lineText: String, span: CnlSpan): Boolean {
        val openerIndex = span.startColumn - 2
        val opener = lineText.getOrNull(openerIndex) ?: return false
        return opener == '«' || opener == '"'
    }

    private fun replace(span: CnlSpan, text: String, lineIndex: LineIndex): WritePlan =
        WritePlan.Ops(listOf(op(span, text, lineIndex)))

    private fun op(span: CnlSpan, text: String, lineIndex: LineIndex): TextOp =
        TextOp(lineIndex.offsetOf(span.line, span.startColumn), lineIndex.offsetOf(span.line, span.endColumn), text)

    private fun append(line: Int, phrase: String, lineIndex: LineIndex): WritePlan {
        val end = lineIndex.offsetOf(line, lineIndex.lineText(line).length + 1)
        return WritePlan.Ops(listOf(TextOp(end, end, " $phrase")))
    }

    private fun failed(line: Int): WritePlan =
        WritePlan.Failed("Edit is not expressible in the CNL sentence", line)

    private fun scalarText(value: YamlScalarValue): String? = when (value) {
        is YamlScalarValue.Num -> formatNumber(value.value)
        is YamlScalarValue.Str -> value.value
        is YamlScalarValue.TokenRef -> "$" + value.token.removePrefix("$")
        is YamlScalarValue.Bool -> null
    }

    private fun colorLiteral(paint: DesignPaint.Solid): String? = when (val color = paint.color) {
        is Bindable.Value -> hexOf(color.value)
        is Bindable.VarRef -> "$" + color.id
        else -> null
    }

    private fun hexOf(color: DesignColor): String {
        fun byte(v: Int) = v.toString(16).uppercase().padStart(2, '0')
        val base = "#${byte(color.red)}${byte(color.green)}${byte(color.blue)}"
        return if (color.alpha == 255) base else base + byte(color.alpha)
    }

    private fun unitText(value: UnitValue): String = when (value.unit) {
        DesignUnit.Px -> formatNumber(value.value)
        DesignUnit.Percent -> "${formatNumber(value.value)}%"
    }

    private fun fontWeightPhrase(weight: Double): String = when (weight) {
        700.0 -> "bold"
        600.0 -> "semibold"
        300.0 -> "thin"
        else -> "weight ${formatNumber(weight)}"
    }

    private fun horizontalAlignWord(value: TextAlignHorizontal): String = when (value) {
        TextAlignHorizontal.Left -> "left"
        TextAlignHorizontal.Center -> "center"
        TextAlignHorizontal.Right -> "right"
        TextAlignHorizontal.Justified -> "justified"
    }

    private fun verticalAlignWord(value: TextAlignVertical): String = when (value) {
        TextAlignVertical.Top -> "top"
        TextAlignVertical.Center -> "center"
        TextAlignVertical.Bottom -> "bottom"
    }

    private fun formatNumber(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
}
