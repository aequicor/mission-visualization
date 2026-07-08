package io.aequicor.visualization.engine.frontend.edit

import io.aequicor.visualization.engine.frontend.blocks.TypedBlockKind
import io.aequicor.visualization.engine.ir.model.HorizontalConstraint
import io.aequicor.visualization.engine.ir.model.SizingMode
import io.aequicor.visualization.engine.ir.model.VerticalConstraint

/**
 * One programmatic edit against a compiled `.layout.md` document, addressed by node
 * id (design section J.A). Edits are surgical: `applySlmEdit` rewrites the smallest
 * byte range of the SLM source and never serializes markdown back from the IR, so
 * every byte outside [SlmEditResult.appliedRange] stays identical.
 */
sealed interface SlmEdit {
    val nodeId: String
}

/**
 * Sets explicit sizing on one or both axes in a single transaction: both axes land
 * under one `layout: sizing:` subtree, creating the path once. An existing
 * `width: fill` shorthand (scalar) is upgraded in place to an inline map
 * (`width: { type: fixed, value: 320 }`); a fresh write uses nested block form.
 */
data class SetSizing(
    override val nodeId: String,
    val width: SizingSpec? = null,
    val height: SizingSpec? = null,
) : SlmEdit

/** One sizing axis: mode plus optional fixed/min/max dimensions. */
data class SizingSpec(
    val mode: SizingMode,
    val value: Double? = null,
    val min: Double? = null,
    val max: Double? = null,
)

/**
 * Moves a node to an absolute position: writes `layout: position:` with
 * `mode: absolute`, `x` and `y` (merging into an existing `position:` map when one
 * is already written, whatever its form).
 */
data class SetNodePosition(
    override val nodeId: String,
    val x: Double,
    val y: Double,
) : SlmEdit

/**
 * Updates one or both Figma-style constraints under the node contract. Keeping the
 * two axes in one edit prevents horizontal/vertical constraint changes from creating
 * separate source patches and separate undo checkpoints.
 */
data class SetNodeConstraints(
    override val nodeId: String,
    val horizontal: HorizontalConstraint? = null,
    val vertical: VerticalConstraint? = null,
) : SlmEdit

/**
 * The generic primitive every other edit compiles down to: writes one scalar at
 * [yamlPath] inside the [blockKind] typed block bound to the node's anchor. An
 * empty [yamlPath] targets the block's own value (e.g. the `node: frame` shorthand).
 */
data class SetTypedBlockScalar(
    override val nodeId: String,
    val blockKind: TypedBlockKind,
    val yamlPath: List<String>,
    val scalar: YamlScalarValue,
) : SlmEdit

/** Writes one `layout:` property; see [LayoutProp] for the yaml path mapping. */
data class SetLayoutProperty(
    override val nodeId: String,
    val property: LayoutProp,
    val value: YamlScalarValue,
) : SlmEdit

/** Writes one `style:` property; see [StyleProp] for the yaml path mapping. */
data class SetStyleProperty(
    override val nodeId: String,
    val property: StyleProp,
    val value: YamlScalarValue,
) : SlmEdit

/**
 * Renames a node by writing `node.name` — never the heading or prose text, so the
 * document's narrative stays untouched. A `node: frame` type shorthand is upgraded
 * to `{ type: frame, name: ... }`, preserving the type.
 */
data class RenameNode(
    override val nodeId: String,
    val name: String,
) : SlmEdit

/**
 * Overrides a node's visible text by writing `text.defaultText` (creating the
 * `text:` block when absent). The i18n default regenerates on recompile under the
 * node's unchanged key.
 */
data class SetText(
    override val nodeId: String,
    val defaultText: String,
) : SlmEdit

/** Scalar written into YAML; rendering canon lives in [ScalarFormatter]. */
sealed interface YamlScalarValue {
    data class Num(val value: Double) : YamlScalarValue

    data class Str(val value: String) : YamlScalarValue

    data class Bool(val value: Boolean) : YamlScalarValue

    /** `$token` design-token reference, written verbatim (leading `$` added when missing). */
    data class TokenRef(val token: String) : YamlScalarValue
}

/**
 * `layout:` block properties addressable by [SetLayoutProperty].
 *
 * | Property | yaml path |
 * |---|---|
 * | [Mode] | `layout.mode` |
 * | [Gap] | `layout.gap` |
 * | [GapRow] | `layout.gap.row` |
 * | [GapColumn] | `layout.gap.column` |
 * | [PaddingTop] | `layout.padding.blockStart` |
 * | [PaddingRight] | `layout.padding.inlineEnd` |
 * | [PaddingBottom] | `layout.padding.blockEnd` |
 * | [PaddingLeft] | `layout.padding.inlineStart` |
 * | [PaddingInline] | `layout.padding.inline` |
 * | [PaddingBlock] | `layout.padding.block` |
 * | [AlignInline] | `layout.align.inline` |
 * | [AlignBlock] | `layout.align.block` |
 * | [Distribution] | `layout.distribution` |
 * | [Wrap] | `layout.wrap` |
 *
 * Physical padding sides are written under their logical keys (top -> blockStart,
 * right -> inlineEnd, ...) so a recompile emits no physical-to-logical hints.
 */
enum class LayoutProp(internal val yamlPath: List<String>) {
    Mode(listOf("mode")),
    Gap(listOf("gap")),
    GapRow(listOf("gap", "row")),
    GapColumn(listOf("gap", "column")),
    PaddingTop(listOf("padding", "blockStart")),
    PaddingRight(listOf("padding", "inlineEnd")),
    PaddingBottom(listOf("padding", "blockEnd")),
    PaddingLeft(listOf("padding", "inlineStart")),
    PaddingInline(listOf("padding", "inline")),
    PaddingBlock(listOf("padding", "block")),
    AlignInline(listOf("align", "inline")),
    AlignBlock(listOf("align", "block")),
    Distribution(listOf("distribution")),
    Wrap(listOf("wrap")),
}

/**
 * `style:` block properties addressable by [SetStyleProperty].
 *
 * | Property | yaml path |
 * |---|---|
 * | [FirstFillToken] | `style.fills[0]` (single-item list write) |
 * | [Radius] | `style.radius` |
 * | [Opacity] | `style.opacity` |
 */
enum class StyleProp(internal val yamlPath: List<String>) {
    FirstFillToken(listOf("fills")),
    Radius(listOf("radius")),
    Opacity(listOf("opacity")),
}
