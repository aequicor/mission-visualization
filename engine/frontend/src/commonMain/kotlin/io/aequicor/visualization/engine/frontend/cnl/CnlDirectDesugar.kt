package io.aequicor.visualization.engine.frontend.cnl

import io.aequicor.visualization.engine.frontend.blocks.InteractionPatch
import io.aequicor.visualization.engine.frontend.blocks.MaskPatch
import io.aequicor.visualization.engine.frontend.blocks.MediaPatch
import io.aequicor.visualization.engine.frontend.blocks.MotionPatch
import io.aequicor.visualization.engine.frontend.blocks.NestedInstancePatch
import io.aequicor.visualization.engine.frontend.blocks.ResponsiveVariantPatch
import io.aequicor.visualization.engine.frontend.blocks.SetOverridePatch
import io.aequicor.visualization.engine.frontend.blocks.SizingPatch
import io.aequicor.visualization.engine.frontend.blocks.SlotOverridePatch
import io.aequicor.visualization.engine.frontend.blocks.TextSpanPatch
import io.aequicor.visualization.engine.ir.model.Bindable
import io.aequicor.visualization.engine.ir.model.CodeHints
import io.aequicor.visualization.engine.ir.model.ComponentPropertyDefinition
import io.aequicor.visualization.engine.ir.model.DesignAnnotation
import io.aequicor.visualization.engine.ir.model.DesignColor
import io.aequicor.visualization.engine.ir.model.DesignCornerRadius
import io.aequicor.visualization.engine.ir.model.DesignEffect
import io.aequicor.visualization.engine.ir.model.DesignExpression
import io.aequicor.visualization.engine.ir.model.DesignInsets
import io.aequicor.visualization.engine.ir.model.DesignMeasurement
import io.aequicor.visualization.engine.ir.model.DesignPaint
import io.aequicor.visualization.engine.ir.model.DesignUnit
import io.aequicor.visualization.engine.ir.model.ExportSetting
import io.aequicor.visualization.engine.ir.model.GridTrack
import io.aequicor.visualization.engine.ir.model.GuideLine
import io.aequicor.visualization.engine.ir.model.LayoutGridDefinition
import io.aequicor.visualization.engine.ir.model.PropValue
import io.aequicor.visualization.engine.ir.model.ScrollOverflow
import io.aequicor.visualization.engine.ir.model.StrokeAlign
import io.aequicor.visualization.engine.ir.model.TextListSettings
import io.aequicor.visualization.engine.ir.model.UnitValue
import io.aequicor.visualization.engine.ir.model.bindable
import io.aequicor.visualization.subsystems.figures.DesignViewBox
import io.aequicor.visualization.subsystems.figures.VectorNetwork
import io.aequicor.visualization.subsystems.figures.VectorPath

// --- typed payloads carried on CnlProperty by the `( … )` consumers ---

/** A `( … )` fill paint (gradient/image/video/solid-with-props); null = invalid → dropped like the reader. */
data class CnlPaintPayload(val paint: DesignPaint?) : CnlPayload

/** A `stroke ( … )` record; attrs stay nullable so a flat `stroke #hex …` phrase can co-compose. */
data class CnlStrokesPayload(
    val paints: List<DesignPaint>,
    val weight: Bindable<Double>?,
    val align: StrokeAlign?,
    val dash: List<Double>?,
    val cap: String?,
    val join: String?,
    val weightPerSide: DesignInsets?,
) : CnlPayload

/** An `effect ( … )` group; null = invalid (e.g. shadow without color) → dropped like the reader. */
data class CnlEffectPayload(val effect: DesignEffect?) : CnlPayload

/** A per-corner `radius (tl tr br bl)` group. */
data class CnlRadiusPayload(val radius: DesignCornerRadius) : CnlPayload

/** A `width`/`height` sizing axis (mode word, bare number or `( … )` record). */
data class CnlSizingPayload(val sizing: SizingPatch) : CnlPayload

/** A `gap ( row N column N )` record. */
data class CnlGapPayload(val row: Bindable<Double>?, val column: Bindable<Double>?) : CnlPayload

/** Generic key/value token pairs (variableModes / constraints / anchor / align / overflow / style refs). */
data class CnlPairsPayload(val pairs: List<Pair<String, String>>) : CnlPayload

/** A `scroll ( … )` record. */
data class CnlScrollPayload(
    val direction: ScrollOverflow?,
    val sticky: Boolean,
    val fixedChildren: List<String>?,
) : CnlPayload

/** A `columns`/`rows` track axis, already resolved with the reader's count/track/auto rules. */
data class CnlTrackAxisPayload(
    val tracks: List<GridTrack>?,
    val gap: Bindable<Double>?,
    val implicitTrack: GridTrack?,
    val min: Bindable<Double>?,
) : CnlPayload

/** A `place ( … )` grid placement — carried as raw tokens, defaults applied at merge. */
data class CnlPlacePayload(val column: Int?, val row: Int?, val columnSpan: Int?, val rowSpan: Int?) : CnlPayload

/** `guides ( … ) ( … )…` — already validated like the reader (invalid items dropped). */
data class CnlGuidesPayload(val guides: List<GuideLine>) : CnlPayload

/** `grids ( … ) ( … )…` — already validated like the reader (typeless items dropped). */
data class CnlGridsPayload(val grids: List<LayoutGridDefinition>) : CnlPayload

/** `list ( … )` text list settings. */
data class CnlListSettingsPayload(val settings: TextListSettings) : CnlPayload

/** `features ( key on )…` OpenType flags (non-boolean values dropped like the reader). */
data class CnlFeaturesPayload(val features: Map<String, Boolean>) : CnlPayload

/** `axes ( wght 620 )…` variable-font axes (friendly names mapped to registered tags). */
data class CnlAxesPayload(val axes: Map<String, Double>) : CnlPayload

/** One `link ( … )` / `span ( … )` rich-text span. */
data class CnlSpanPayload(val span: TextSpanPatch) : CnlPayload

/** One `when ( … ) …` responsive variant. */
data class CnlVariantPayload(val variant: ResponsiveVariantPatch) : CnlPayload

/** One `override <target> ( … )` overrides.sets record. */
data class CnlSetOverridePayload(val set: SetOverridePatch) : CnlPayload

/** One `media ( … )` record; repeated phrases merge per-field (YAML duplicate-key last-wins). */
data class CnlMediaPayload(val media: MediaPatch) : CnlPayload

/** A `viewbox (x y w h)` group. */
data class CnlViewBoxPayload(val viewBox: DesignViewBox) : CnlPayload

/** One `path «d» …` chain → the whole `vector.paths` list (last chain wins). */
data class CnlVectorPathsPayload(val paths: List<VectorPath>) : CnlPayload

/** A `network ( … )` group: structural network plus per-region fills keyed by region index. */
data class CnlNetworkPayload(
    val network: VectorNetwork?,
    val regionFills: Map<Int, List<DesignPaint>>,
) : CnlPayload

/** One `mask …` phrase (type / clips / from); repeated phrases merge per-field. */
data class CnlMaskPayload(val mask: MaskPatch) : CnlPayload

/** One trigger phrase → one `interaction:` block (order preserved). */
data class CnlInteractionPayload(val interaction: InteractionPatch) : CnlPayload

/** A `motion (ref) …` phrase. */
data class CnlMotionPayload(val motion: MotionPatch) : CnlPayload

/** An instance `variant ( axis value … )` selection record. */
data class CnlVariantSelectionPayload(val variant: Map<String, String>) : CnlPayload

/** A `props ( name value … )` record (invalid values dropped like the reader). */
data class CnlPropsPayload(val props: Map<String, PropValue>) : CnlPayload

/** One `slot <name> ( … )…` overrides.slots entry (invalid fills dropped like the reader). */
data class CnlSlotPayload(val name: String, val fills: List<SlotOverridePatch>) : CnlPayload

/** One `nested <target> ( … )` overrides.nestedInstances entry. */
data class CnlNestedPayload(val target: String, val override: NestedInstancePatch) : CnlPayload

/** One definition-side `axis <name> ( … )` variants axis. */
data class CnlComponentAxisPayload(val axis: String, val values: List<String>) : CnlPayload

/** One definition-side `prop <name> ( … )` property declaration. */
data class CnlComponentPropPayload(val name: String, val definition: ComponentPropertyDefinition) : CnlPayload

/** One `export …` phrase: `off`, or `( … )` settings (format-less settings dropped like the reader). */
data class CnlExportPayload(val disabled: Boolean, val settings: List<ExportSetting>) : CnlPayload

/** One `note «…» ( … )` handoff annotation. */
data class CnlAnnotationPayload(val annotation: DesignAnnotation) : CnlPayload

/** One `measure ( … )` handoff measurement; null = required keys missing → dropped like the reader. */
data class CnlMeasurementPayload(val measurement: DesignMeasurement?) : CnlPayload

/** A `code ( … )` handoff code hint. */
data class CnlCodeHintPayload(val code: CodeHints) : CnlPayload

/**
 * Scalar coercion for the direct (typed) CNL desugar: raw CNL value tokens → [Bindable]s and
 * literals. Preserves the semantics of the retired YAML `ReaderSupport` coercions EXACTLY
 * (this is now the semantic source of truth): precedence is `$prop.`/`$prop:` prop ref → `$x` var ref →
 * `{{expr}}` data binding → literal. Literal number typing follows the SLM YAML scalar rule
 * (strict number regex), not Kotlin's `toDoubleOrNull`, so tokens are typed exactly as the old
 * fragment-string round trip typed them.
 */
internal object CnlScalars {
    private val yamlNumber = Regex("""-?\d+(\.\d+)?([eE][+-]?\d+)?""")

    fun propNameOf(text: String): String? = when {
        text.startsWith("\$prop.") -> text.removePrefix("\$prop.").takeIf { it.isNotEmpty() }
        text.startsWith("\$prop:") -> text.removePrefix("\$prop:").takeIf { it.isNotEmpty() }
        else -> null
    }

    fun expressionBodyOf(text: String): String? {
        val trimmed = text.trim()
        if (!trimmed.startsWith("{{") || !trimmed.endsWith("}}")) return null
        return trimmed.substring(2, trimmed.length - 2).trim()
    }

    fun doubleOf(text: String): Double? = if (yamlNumber.matches(text)) text.toDouble() else null

    fun intOf(text: String): Int? = doubleOf(text)?.toInt()

    /** Mirrors YAML scalar typing + the readers' `string()`: `null`/`~` → null, anything else keeps its text. */
    fun stringOf(text: String): String? = if (text == "null" || text == "~") null else text

    /** Mirrors `stringList` item typing: only tokens YAML would type as strings survive. */
    fun plainStringOf(text: String): String? = when {
        text == "null" || text == "~" || text == "true" || text == "false" -> null
        yamlNumber.matches(text) -> null
        else -> text
    }

    fun bindableDoubleOf(text: String): Bindable<Double>? {
        propNameOf(text)?.let { return Bindable.PropRef(it) }
        if (text.startsWith("$")) return Bindable.VarRef(text.drop(1))
        expressionBodyOf(text)?.let { return Bindable.DataRef(DesignExpression(it)) }
        return doubleOf(text)?.bindable()
    }

    fun bindableIntOf(text: String): Bindable<Int>? {
        propNameOf(text)?.let { return Bindable.PropRef(it) }
        if (text.startsWith("$")) return Bindable.VarRef(text.drop(1))
        expressionBodyOf(text)?.let { return Bindable.DataRef(DesignExpression(it)) }
        return intOf(text)?.bindable()
    }

    /** A boolean word (`yes`/`on`/`true`…), `$prop`/`$var` ref or `{{expr}}` binding. */
    fun bindableBooleanOf(word: String): Bindable<Boolean>? {
        CnlVocabulary.booleans[word.lowercase()]?.let { return it.bindable() }
        propNameOf(word)?.let { return Bindable.PropRef(it) }
        if (word.startsWith("$")) return Bindable.VarRef(word.drop(1))
        expressionBodyOf(word)?.let { return Bindable.DataRef(DesignExpression(it)) }
        return null
    }

    fun bindableStringOf(text: String): Bindable<String> {
        propNameOf(text)?.let { return Bindable.PropRef(it) }
        if (text.startsWith("$")) return Bindable.VarRef(text.drop(1))
        expressionBodyOf(text)?.let { return Bindable.DataRef(DesignExpression(it)) }
        return text.bindable()
    }

    /** `#hex` / `$ref` / `{{expr}}` color token — mirrors `bindableColor` (no `$prop` branch there). */
    fun colorOf(text: String): Bindable<DesignColor>? {
        if (text.startsWith("$")) return Bindable.VarRef(text.drop(1))
        expressionBodyOf(text)?.let { return Bindable.DataRef(DesignExpression(it)) }
        if (text.startsWith("#")) return DesignColor.fromHex(text)?.bindable()
        return null
    }

    /** `135%` → percent, plain number → px; anything else null (mirrors `TextBlockReader.unitValue`). */
    fun unitValueOf(text: String): UnitValue? {
        if (text.endsWith("%")) return UnitValue(DesignUnit.Percent, doubleOf(text.dropLast(1)) ?: 0.0)
        return doubleOf(text)?.let { UnitValue(DesignUnit.Px, it) }
    }

    /** A grid-track token (copies `LayoutBlockReader.readTrack`/`flexTrackBody` semantics). */
    fun gridTrackOf(text: String): GridTrack? {
        if (text == "hug") return GridTrack.Hug
        flexTrackBody(text)?.let { body -> flexWeightOf(body)?.let { return GridTrack.Flex(it) } }
        return bindableDoubleOf(text)?.let { GridTrack.Fixed(it) }
    }

    private fun flexTrackBody(raw: String): String? {
        if (!raw.endsWith("fr")) return null
        val body = raw.removeSuffix("fr")
        return when {
            body.startsWith("\${") && body.endsWith("}") -> "$" + body.substring(2, body.length - 1)
            body.startsWith("{{") && body.endsWith("}}") -> body
            body.startsWith("$") -> null
            else -> body
        }
    }

    private fun flexWeightOf(text: String): Bindable<Double>? {
        val trimmed = text.trim()
        return when {
            trimmed.startsWith("\$prop.") ->
                trimmed.removePrefix("\$prop.").takeIf { it.isNotEmpty() }?.let { Bindable.PropRef(it) }
            trimmed.startsWith("\$prop:") ->
                trimmed.removePrefix("\$prop:").takeIf { it.isNotEmpty() }?.let { Bindable.PropRef(it) }
            trimmed.startsWith("$") -> trimmed.drop(1).takeIf { it.isNotEmpty() }?.let { Bindable.VarRef(it) }
            else -> expressionBodyOf(trimmed)?.let { Bindable.DataRef(DesignExpression(it)) }
                ?: trimmed.toDoubleOrNull()?.bindable()
        }
    }
}
