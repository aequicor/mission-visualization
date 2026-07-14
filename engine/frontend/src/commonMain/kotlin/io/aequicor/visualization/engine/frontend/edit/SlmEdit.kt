package io.aequicor.visualization.engine.frontend.edit

import io.aequicor.visualization.engine.frontend.blocks.TypedBlockKind
import io.aequicor.visualization.subsystems.figures.BooleanOperationKind
import io.aequicor.visualization.engine.ir.model.DesignCornerRadius
import io.aequicor.visualization.engine.ir.model.DesignEffect
import io.aequicor.visualization.engine.ir.model.DesignInteraction
import io.aequicor.visualization.engine.ir.model.DesignMotion
import io.aequicor.visualization.engine.ir.model.DesignNode
import io.aequicor.visualization.engine.ir.model.DesignPaint
import io.aequicor.visualization.engine.ir.model.DesignStrokes
import io.aequicor.visualization.engine.ir.model.DesignTextStyle
import io.aequicor.visualization.subsystems.figures.DesignViewBox
import io.aequicor.visualization.engine.ir.model.HorizontalConstraint
import io.aequicor.visualization.engine.ir.model.SizingMode
import io.aequicor.visualization.subsystems.figures.VectorNetwork
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
 * Requests a deterministic whole-sentence CNL re-emit from the caller-supplied patched node.
 * This is the generic write-back escape hatch for editor properties that do not need a smaller
 * surgical edit. [CnlWriter] still rewrites only the owning sentence, and the editor must
 * recompile and prove semantic equivalence before accepting the result.
 */
data class ReemitNode(
    override val nodeId: String,
) : SlmEdit

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

/**
 * Sets the root artboard size in the screen's `frame:` frontmatter. The screen root may be a
 * synthetic node produced from a plain H1 and therefore have no ordinary CNL source anchor;
 * frontmatter is its canonical sizing contract. When the root H1 is also a CNL sentence, the
 * patcher keeps that duplicate size in sync in the same transaction.
 */
data class SetScreenFrame(
    override val nodeId: String,
    val width: Double? = null,
    val height: Double? = null,
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
 * Sets the node's rotation (degrees): writes `node: position: rotation` (the block `NodeBlockReader`
 * reads x/y/rotation from), merging into an existing `position:` map without disturbing its `x`/`y`.
 */
data class SetNodeRotation(
    override val nodeId: String,
    val degrees: Double,
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

/**
 * Rewrites the whole `style.fills` list from the working node's paint layers, preserving
 * design-token colors as `token:` refs and literals as `#hex`. The list is replaced
 * wholesale (the working document owns the authoritative order and contents), creating
 * the `style:` block or the `fills:` key when absent.
 */
data class SetFills(
    override val nodeId: String,
    val fills: List<DesignPaint>,
) : SlmEdit

/** Rewrites the whole `style.strokes` list from the node's stroke layers + attributes. */
data class SetStrokes(
    override val nodeId: String,
    val strokes: DesignStrokes,
) : SlmEdit

/** Rewrites the whole `style.effects` list from the node's effects. */
data class SetEffects(
    override val nodeId: String,
    val effects: List<DesignEffect>,
) : SlmEdit

/**
 * Rewrites the node's `text.typography` block from a [DesignTextStyle]. The typography map
 * is merged field by field into any existing one (authored `openType`/`variableFont` the
 * editor never touches stay verbatim), creating the `text:` block or the `typography:` key
 * when absent. Token font weight/size round-trip as `$token` refs; a px `lineHeight` /
 * `letterSpacing` renders as a bare number, a percent as a `{ unit: percent, value }` map.
 * Writing a px value onto an authored percent map (or vice versa) is not expressible in
 * place and fails cleanly, so the caller can fall back in-memory.
 */
data class SetTextStyle(
    override val nodeId: String,
    val style: DesignTextStyle,
    /** Typography keys to delete from the source (e.g. "decorationColor" reset to auto). */
    val clearKeys: Set<String> = emptySet(),
) : SlmEdit

/**
 * Rewrites the node's whole rich-text spans from the working node's [styleRanges] and
 * [links]. This edit is not surgically expressible, so [CnlWriter] regenerates the whole
 * CNL sentence from the patched node (tier-3), re-emitting each `span (…)` / `link (…)`
 * phrase; the list is replaced wholesale (the working document owns the authoritative set).
 *
 * Offsets are authored against the source-locale `defaultText`, so the caller must gate
 * this out (falling back in-memory) when the node's text is ICU-formatted or resolved in
 * a non-source locale — offsets would not align with the rendered string.
 */
data class SetTextSpans(
    override val nodeId: String,
    val styleRanges: List<io.aequicor.visualization.engine.ir.model.TextStyleRange>,
    val links: List<io.aequicor.visualization.engine.ir.model.TextLink>,
) : SlmEdit

/**
 * Writes the node's text auto-resize as `text.resizing: { width, height }`. Maps the
 * Figma modes to sizing shorthands: WidthAndHeight -> both `hug`, Height -> height `hug`
 * (width `fixed`), None -> both `fixed`.
 */
data class SetTextAutoResize(
    override val nodeId: String,
    val mode: io.aequicor.visualization.engine.ir.model.TextAutoResize,
) : SlmEdit

/**
 * Writes text truncation as `text.maxLines` (+ `overflow: truncate` when ellipsis is on).
 * A null [truncate] removes truncation by writing `overflow: visible` and dropping maxLines.
 */
data class SetTextTruncate(
    override val nodeId: String,
    val truncate: io.aequicor.visualization.engine.ir.model.TextTruncate?,
) : SlmEdit

/** Writes `vector.viewBox: [x, y, w, h]` (creating the `vector:` block when absent). */
data class SetViewBox(
    override val nodeId: String,
    val viewBox: DesignViewBox,
) : SlmEdit

/**
 * Rewrites the whole `vector.network` sub-block from the working node's structural geometry.
 * Like [SetFills], the working document owns the authoritative vertices/segments/regions, so
 * the block is replaced wholesale — every interactive point/handle edit commits through this.
 */
data class SetVectorNetwork(
    override val nodeId: String,
    val network: VectorNetwork,
    /** Per-region fills keyed by region index, emitted inside each region mapping. */
    val regionFills: Map<Int, List<DesignPaint>> = emptyMap(),
) : SlmEdit

/** Writes `vector.boolean: { op, children }` for a boolean-operation node. */
data class SetBooleanOp(
    override val nodeId: String,
    val op: BooleanOperationKind,
    val children: List<String>,
) : SlmEdit

/**
 * Writes the node's corner radius as `style.radius`: a single scalar when all four corners are
 * equal, otherwise a `{ topLeft, topRight, bottomRight, bottomLeft }` map (the style reader
 * already accepts both). Promotes per-corner radius from in-memory-only to Tier-1 write-back.
 */
data class SetCornerRadii(
    override val nodeId: String,
    val radius: DesignCornerRadius,
) : SlmEdit

/**
 * Rewrites the node's WHOLE SET of `interaction:` typed-block entries from the working list.
 * Interactions accumulate as separate sibling `interaction:` blocks (the compiler appends rather
 * than last-wins), so unlike fills there is no single list to splice: every existing `interaction:`
 * entry bound to the node's anchor is removed and the list is re-emitted in order. An empty list
 * removes them all cleanly. Persists via [CnlWriter]'s tier-3 whole-sentence re-emit on a
 * CNL-authored node; not expressible (→ the caller falls back in-memory, source untouched) when
 * any action uses `CubicBezier` easing, a `DesignAction.Unknown`, a `PropRef` `SetVariable` value,
 * or an overlay with an unrenderable background — see [isInteractionExpressibleInSlm].
 */
data class SetInteractions(
    override val nodeId: String,
    val interactions: List<DesignInteraction>,
) : SlmEdit

/**
 * Replaces / creates / removes the single `motion:` entry (motion is last-win). A null [motion]
 * removes the entry. A ref-only motion or an inline keyframes fallback both round-trip; motion has
 * no inexpressible case.
 */
data class SetMotion(
    override val nodeId: String,
    val motion: DesignMotion?,
) : SlmEdit

/**
 * Structural edits that change the *set* of nodes (create/delete/move), not just their
 * attributes. Unlike the attribute edits they synthesize or remove whole `#`-heading
 * sections; they resolve differently in [applySlmEdit] (heading footprint arithmetic via
 * [SectionWriter], not the surgical [CnlWriter]). Every node whose id must survive a recompile is
 * addressed by an explicit `node: id:`, so ids never drift.
 */
sealed interface StructuralSlmEdit : SlmEdit

/**
 * Inserts [subtree] as a child heading section under the node [nodeId] (the parent). The
 * subtree is emitted as a fresh `#`-heading section (one level deeper than the parent, each
 * descendant one level deeper still) with a mandatory explicit `node: id:` for every node,
 * framed by blank lines so the SLM parser never absorbs the `node:` line as prose. When
 * [afterSiblingId] is non-null the section lands right after that sibling's footprint;
 * otherwise it is appended at the end of the parent's footprint. Fails cleanly (leaving the
 * source untouched) when the parent has no addressable heading anchor (prose / ir-splice) or
 * the resulting depth would exceed the ATX maximum of 6.
 */
data class InsertChildSubtree(
    override val nodeId: String,
    val subtree: DesignNode,
    val afterSiblingId: String? = null,
) : StructuralSlmEdit

/**
 * Removes the heading section owning [nodeId] together with its typed blocks and every
 * descendant, from the heading line through to (but not including) the next same-or-shallower
 * heading — the leading blank line before the section is left in place so siblings stay
 * cleanly separated. Fails cleanly for the screen root, list-item / prose / ir-splice anchors,
 * so the caller can fall back to an in-memory delete.
 */
data class DeleteSection(
    override val nodeId: String,
) : StructuralSlmEdit

/** Replaces one heading-owned subtree in place while keeping its root id and sibling slot. */
data class ReplaceSection(
    override val nodeId: String,
    val subtree: DesignNode,
) : StructuralSlmEdit

/**
 * Moves the heading section owning [nodeId] — with its whole subtree — to become a child of
 * [newParentId], re-leveling every heading line in the subtree by the depth delta so the moved
 * root lands exactly one level under the new parent (its descendants keep their relative depth).
 * When [afterSiblingId] is non-null the section lands right after that sibling's footprint;
 * otherwise it is appended at the end of the new parent's footprint. The subtree text (including
 * every explicit `node: id:`) is carried verbatim, so all moved ids survive the recompile.
 *
 * Fails cleanly (leaving the source untouched) when either end has no addressable heading anchor
 * (prose / ir-splice / the screen root), when the new parent is the moved node's own descendant,
 * or when the re-leveled depth would exceed the ATX maximum of 6 — so the caller can fall back to
 * an in-memory reparent. Cross-source (two-file) moves are not expressible by a single-source
 * patch and are gated out by the caller.
 */
data class MoveSection(
    override val nodeId: String,
    val newParentId: String,
    val afterSiblingId: String? = null,
) : StructuralSlmEdit

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
