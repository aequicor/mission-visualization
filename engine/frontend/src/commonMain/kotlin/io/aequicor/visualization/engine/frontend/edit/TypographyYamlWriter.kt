package io.aequicor.visualization.engine.frontend.edit

import io.aequicor.visualization.engine.ir.model.Bindable
import io.aequicor.visualization.engine.ir.model.DesignColor
import io.aequicor.visualization.engine.ir.model.DesignTextStyle
import io.aequicor.visualization.engine.ir.model.DesignUnit
import io.aequicor.visualization.engine.ir.model.LeadingTrim
import io.aequicor.visualization.engine.ir.model.TextAlignHorizontal
import io.aequicor.visualization.engine.ir.model.TextAlignVertical
import io.aequicor.visualization.engine.ir.model.TextCase
import io.aequicor.visualization.engine.ir.model.TextDecorationKind
import io.aequicor.visualization.engine.ir.model.TextDecorationStyle
import io.aequicor.visualization.engine.ir.model.TextScriptPosition
import io.aequicor.visualization.engine.ir.model.UnitValue

/**
 * Serializes a [DesignTextStyle] back into a `typography:` [YamlPayload.Mapping] for
 * surgical write-back — the faithful inverse of `TextBlockReader.readTypography`. Only
 * non-null / non-empty fields are emitted (a null field is left untouched by the merge, so
 * authored keys the editor never changes survive), so re-reading the payload yields the
 * same [DesignTextStyle] fields that were written.
 *
 * Conventions mirror [StyleYamlWriter]: token font weight/size round-trip as `$token`
 * refs (via [num]); a px `lineHeight` / `letterSpacing` renders as a bare number (the
 * reader's px default), a percent as an explicit `{ unit: percent, value }` map; `openType`
 * feature flags and `variableFont` axes ride as nested maps keyed verbatim (raw tags such
 * as `wght`/`opsz` re-read to themselves).
 */
internal object TypographyYamlWriter {

    fun typography(style: DesignTextStyle): YamlPayload.Mapping = YamlPayload.Mapping(
        buildList {
            style.fontFamily?.let { add("fontFamily" to str(it)) }
            style.fontWeight?.let { add("fontWeight" to num(it)) }
            style.italic?.let { add("italic" to bool(it)) }
            style.fontSize?.let { add("fontSize" to num(it)) }
            style.lineHeight?.let { add("lineHeight" to unitValue(it)) }
            style.letterSpacing?.let { add("letterSpacing" to unitValue(it)) }
            style.paragraphSpacing?.let { add("paragraphSpacing" to numOf(it)) }
            style.paragraphIndent?.let { add("paragraphIndent" to numOf(it)) }
            style.textAlignHorizontal?.let { add("horizontalAlign" to str(horizontalAlignToken(it))) }
            style.textAlignVertical?.let { add("verticalAlign" to str(verticalAlignToken(it))) }
            style.textCase?.let { add("case" to str(caseToken(it))) }
            style.textDecoration?.let { add("decoration" to str(decorationToken(it))) }
            style.decorationStyle?.let { add("decorationStyle" to str(decorationStyleToken(it))) }
            style.decorationColor?.let { add("decorationColor" to str(hex(it))) }
            style.decorationThickness?.let { add("decorationThickness" to unitValue(it)) }
            style.decorationSkipInk?.let { add("decorationSkipInk" to bool(it)) }
            style.textPosition?.let { add("position" to str(positionToken(it))) }
            style.leadingTrim?.let { add("leadingTrim" to str(leadingTrimToken(it))) }
            style.hangingPunctuation?.let { add("hangingPunctuation" to bool(it)) }
            style.hangingList?.let { add("hangingList" to bool(it)) }
            if (style.fontFeatures.isNotEmpty()) {
                add("openType" to YamlPayload.Mapping(style.fontFeatures.map { (feature, on) -> feature to bool(on) }))
            }
            if (style.variableAxes.isNotEmpty()) {
                add("variableFont" to YamlPayload.Mapping(style.variableAxes.map { (axis, value) -> axis to numOf(value) }))
            }
        },
    )

    /** Px is a bare number (the reader's default); percent stays an explicit `{ unit, value }` map. */
    private fun unitValue(value: UnitValue): YamlPayload = when (value.unit) {
        DesignUnit.Px -> numOf(value.value)
        DesignUnit.Percent -> YamlPayload.Mapping(
            listOf("unit" to str("percent"), "value" to numOf(value.value)),
        )
    }

    // --- token tables (inverse of ReaderEnums; canonical spelling) ---

    private fun horizontalAlignToken(value: TextAlignHorizontal): String = when (value) {
        TextAlignHorizontal.Left -> "left"
        TextAlignHorizontal.Center -> "center"
        TextAlignHorizontal.Right -> "right"
        TextAlignHorizontal.Justified -> "justified"
    }

    private fun verticalAlignToken(value: TextAlignVertical): String = when (value) {
        TextAlignVertical.Top -> "top"
        TextAlignVertical.Center -> "center"
        TextAlignVertical.Bottom -> "bottom"
    }

    private fun caseToken(value: TextCase): String = when (value) {
        TextCase.None -> "none"
        TextCase.Upper -> "upper"
        TextCase.Lower -> "lower"
        TextCase.Title -> "title"
        TextCase.SmallCaps -> "smallCaps"
        TextCase.SmallCapsForced -> "smallCapsForced"
    }

    private fun decorationToken(value: TextDecorationKind): String = when (value) {
        TextDecorationKind.None -> "none"
        TextDecorationKind.Underline -> "underline"
        TextDecorationKind.Strikethrough -> "strikethrough"
    }

    private fun decorationStyleToken(value: TextDecorationStyle): String = when (value) {
        TextDecorationStyle.Solid -> "solid"
        TextDecorationStyle.Dashed -> "dashed"
        TextDecorationStyle.Dotted -> "dotted"
        TextDecorationStyle.Wavy -> "wavy"
    }

    private fun positionToken(value: TextScriptPosition): String = when (value) {
        TextScriptPosition.None -> "none"
        TextScriptPosition.Superscript -> "superscript"
        TextScriptPosition.Subscript -> "subscript"
    }

    private fun leadingTrimToken(value: LeadingTrim): String = when (value) {
        LeadingTrim.None -> "none"
        LeadingTrim.CapHeight -> "capHeight"
    }

    private fun hex(color: DesignColor): String {
        val rgb = (color.argb and 0xFFFFFF).toString(16).uppercase().padStart(6, '0')
        return if (color.alpha == 0xFF) "#$rgb" else "#$rgb${color.alpha.toString(16).uppercase().padStart(2, '0')}"
    }

    // --- payload helpers (mirror StyleYamlWriter) ---

    private fun str(value: String): YamlPayload = YamlPayload.Scalar(YamlScalarValue.Str(value))
    private fun bool(value: Boolean): YamlPayload = YamlPayload.Scalar(YamlScalarValue.Bool(value))
    private fun numOf(value: Double): YamlPayload = YamlPayload.Scalar(YamlScalarValue.Num(value))
    private fun num(value: Bindable<Double>): YamlPayload = when (value) {
        is Bindable.VarRef -> YamlPayload.Scalar(YamlScalarValue.TokenRef(value.id))
        is Bindable.Value -> numOf(value.value)
        else -> numOf(0.0)
    }
}
