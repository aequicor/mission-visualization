package io.aequicor.visualization.engine.frontend.edit

import io.aequicor.visualization.engine.frontend.cnl.CnlElement
import io.aequicor.visualization.engine.frontend.cnl.CnlParser
import io.aequicor.visualization.engine.frontend.cnl.CnlProperty
import io.aequicor.visualization.engine.frontend.cnl.CnlPropertyKind
import io.aequicor.visualization.engine.frontend.cnl.CnlSpan
import io.aequicor.visualization.engine.frontend.diagnostics.DiagnosticCollector
import io.aequicor.visualization.engine.frontend.markdown.SlmSourceSpan
import io.aequicor.visualization.engine.ir.model.Bindable
import io.aequicor.visualization.engine.ir.model.DesignColor
import io.aequicor.visualization.engine.ir.model.DesignCornerRadius
import io.aequicor.visualization.engine.ir.model.DesignPaint
import io.aequicor.visualization.engine.ir.model.SizingMode
import io.aequicor.visualization.engine.ir.model.literalOrNull

/**
 * Surgical write-back into a CNL element sentence. Mirrors the typed-block patcher's
 * re-parse-on-demand design: given the sentence span, re-parse it, locate the property's
 * value token via its span, and replace it in place (or append a phrase when the property
 * is absent). Edits CNL cannot express return [WritePlan.Failed] so the patcher falls back
 * to inserting a typed block below the sentence (mixed authoring) or an in-memory edit.
 */
internal object CnlWriter {
    fun plan(source: String, sentenceSpan: SlmSourceSpan, edit: SlmEdit, lineIndex: LineIndex): WritePlan {
        val line = sentenceSpan.startLine
        val text = lineIndex.lineText(line)
        val element = CnlParser.parseElement(text, line, baseColumn = 1, DiagnosticCollector())
            ?: return failed(line)
        return when (edit) {
            is SetFills -> fillPlan(element, edit.fills, lineIndex, line)
            is SetStyleProperty -> when (edit.property) {
                StyleProp.FirstFillToken -> scalarText(edit.value)?.let { fillLiteralPlan(element, it, lineIndex, line) } ?: failed(line)
                StyleProp.Radius -> scalarPropPlan(element, CnlPropertyKind.Radius, "радиус", edit.value, lineIndex, line)
                StyleProp.Opacity -> scalarPropPlan(element, CnlPropertyKind.Opacity, "прозрачность", edit.value, lineIndex, line)
            }
            is SetCornerRadii -> radiusPlan(element, edit.radius, lineIndex, line)
            is SetLayoutProperty -> when (edit.property) {
                LayoutProp.Gap -> scalarPropPlan(element, CnlPropertyKind.Gap, "отступ", edit.value, lineIndex, line)
                else -> failed(line)
            }
            is SetSizing -> sizingPlan(element, edit, lineIndex, line)
            is SetNodePosition -> positionPlan(element, edit, lineIndex, line)
            is SetText -> textPlan(element, edit.defaultText, lineIndex, line)
            else -> failed(line)
        }
    }

    // --- per-edit plans ---

    private fun fillPlan(element: CnlElement, fills: List<DesignPaint>, lineIndex: LineIndex, line: Int): WritePlan {
        val color = (fills.singleOrNull() as? DesignPaint.Solid)?.let(::colorLiteral) ?: return failed(line)
        return fillLiteralPlan(element, color, lineIndex, line)
    }

    private fun fillLiteralPlan(element: CnlElement, color: String, lineIndex: LineIndex, line: Int): WritePlan {
        val fill = element.property(CnlPropertyKind.Fill)
        return if (fill != null) {
            replace(fill.values.first().span, color, lineIndex)
        } else {
            append(line, "цвет $color", lineIndex)
        }
    }

    private fun radiusPlan(element: CnlElement, radius: DesignCornerRadius, lineIndex: LineIndex, line: Int): WritePlan {
        val uniform = radius.topLeft.literalOrNull()
            ?.takeIf { radius.topRight.literalOrNull() == it && radius.bottomRight.literalOrNull() == it && radius.bottomLeft.literalOrNull() == it }
            ?: return failed(line)
        return scalarReplace(element, CnlPropertyKind.Radius, "радиус", formatNumber(uniform), lineIndex, line)
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
            append(line, "позиция ${formatNumber(edit.x)} ${formatNumber(edit.y)}", lineIndex)
        }
    }

    private fun textPlan(element: CnlElement, defaultText: String, lineIndex: LineIndex, line: Int): WritePlan {
        val literal = element.textLiteral ?: return failed(line)
        return replace(literal.span, defaultText, lineIndex)
    }

    // --- helpers ---

    private fun CnlElement.property(kind: CnlPropertyKind): CnlProperty? =
        properties.firstOrNull { it.kind == kind }

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

    private fun formatNumber(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
}
