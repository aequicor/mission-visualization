package io.aequicor.visualization.engine.frontend.edit

import io.aequicor.visualization.engine.ir.model.AlignItems
import io.aequicor.visualization.engine.ir.model.Bindable
import io.aequicor.visualization.engine.ir.model.DesignCornerRadius
import io.aequicor.visualization.engine.ir.model.DesignGap
import io.aequicor.visualization.engine.ir.model.DesignNode
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.engine.ir.model.DesignSizing
import io.aequicor.visualization.engine.ir.model.HorizontalConstraint
import io.aequicor.visualization.engine.ir.model.LayoutMode
import io.aequicor.visualization.engine.ir.model.ShapeType
import io.aequicor.visualization.engine.ir.model.SizingMode
import io.aequicor.visualization.engine.ir.model.VerticalConstraint

/**
 * Renders a [DesignNode] subtree into a fresh SLM `#`-heading section — the structural
 * counterpart of the surgical [StyleYamlWriter] / [TypographyYamlWriter]. It is the inverse
 * of the node / shape / layout / style / text readers, emitting exactly the typed blocks the
 * readers consume:
 *
 * - the heading carries a **single-word decorative prefix** (`Frame:` / `Shape:` / `Text:` …)
 *   so [io.aequicor.visualization.engine.frontend.semantics.SemanticExtractor.applyHeadingName]
 *   only *names* the node — a prefix-less or multi-word heading would attach a spurious
 *   `SectionTitle` i18n text node;
 * - the `node:` block always carries an **explicit `id:`** (id stability), plus the type/name
 *   and only the non-default contract fields (order/visible/locked/absolute position/constraints);
 * - `layout.sizing` is the canonical size carrier — `node.size` derives from `sizing.value`
 *   in the merge — so a `{ type: fixed, value }` axis round-trips back to the same size;
 * - `style:` reuses [StyleYamlWriter] (token colors -> `token:`, literals -> `#hex`), and
 *   `text:` reuses [TypographyYamlWriter].
 *
 * Positional framing (blank lines, insert offset) is [SectionWriter]'s job; this object only
 * produces the lines.
 */
internal object NodeSectionWriter {

    /**
     * Heading prefixes the extractor treats as component-*definition* markers (union of every
     * locale's `componentMarkers` in [io.aequicor.visualization.engine.frontend.semantics.Lexicons]);
     * emitting one would lift the node out of the page tree, so [headingPrefix] avoids them.
     */
    private val ComponentMarkers = setOf("component", "компонент")

    /** The node's own heading + typed blocks, then each child one level deeper. */
    fun emitSubtree(node: DesignNode, level: Int): List<String> = buildList {
        addAll(emit(node, level))
        node.children.forEach { child ->
            add("")
            addAll(emitSubtree(child, level + 1))
        }
    }

    /** One node's heading line followed by its contiguous typed blocks (no blank lines). */
    fun emit(node: DesignNode, level: Int): List<String> = buildList {
        add(headingLine(node, level))
        addAll(SlmBlockRenderer.entryLines("node", nodePayload(node), 0))
        shapePayload(node)?.let { addAll(SlmBlockRenderer.entryLines("shape", it, 0)) }
        layoutPayload(node)?.let { addAll(SlmBlockRenderer.entryLines("layout", it, 0)) }
        stylePayload(node)?.let { addAll(SlmBlockRenderer.entryLines("style", it, 0)) }
        textPayload(node)?.let { addAll(SlmBlockRenderer.entryLines("text", it, 0)) }
    }

    // --- heading ---

    private fun headingLine(node: DesignNode, level: Int): String {
        val hashes = "#".repeat(level.coerceIn(1, 6))
        val prefix = headingPrefix(node)
        return if (node.name.isBlank()) "$hashes $prefix:" else "$hashes $prefix: ${node.name}"
    }

    /**
     * A single-word, human-readable prefix derived from [DesignNode.type]. Must contain no
     * whitespace (single-word) or the extractor treats the whole heading as prose title text.
     *
     * A `component`-family type must NOT surface as its own heading prefix: the extractor reads a
     * `Component:` (or localized `Компонент:`) prefix as a component *definition* marker and lifts
     * the node out of the page tree ([io.aequicor.visualization.engine.frontend.semantics.SemanticExtractor.applyHeadingName],
     * lexicon [io.aequicor.visualization.engine.frontend.semantics.Lexicons]). Such a node is still a
     * live in-tree Frame (its kind comes from `node.type` via the merge, not the heading), so it
     * falls back to the neutral `Node:` prefix while `node: type:` preserves the authored type,
     * round-tripping faithfully instead of silently vanishing.
     */
    private fun headingPrefix(node: DesignNode): String {
        val type = node.type.trim()
        if (type.isEmpty() || type.any { it.isWhitespace() }) return "Node"
        if (type.lowercase() in ComponentMarkers) return "Node"
        return type.replaceFirstChar { it.uppercaseChar() }
    }

    // --- node block ---

    private fun nodePayload(node: DesignNode): YamlPayload.Mapping = YamlPayload.Mapping(
        buildList {
            add("type" to str(node.type))
            add("id" to str(node.id))
            if (node.name.isNotEmpty()) add("name" to str(node.name))
            if (node.role.isNotEmpty()) add("role" to str(node.role))
            node.order?.let { add("order" to num(it.toDouble())) }
            val visible = node.visible
            if (visible is Bindable.Value && !visible.value) add("visible" to bool(false))
            if (node.locked) add("locked" to bool(true))
            val position = node.position
            if (node.layoutChild.absolute && position != null) {
                add(
                    "position" to YamlPayload.Mapping(
                        listOf(
                            "mode" to str("absolute"),
                            "x" to num(position.x),
                            "y" to num(position.y),
                        ),
                    ),
                )
            }
            val constraints = node.constraints
            if (constraints.horizontal != HorizontalConstraint.Left ||
                constraints.vertical != VerticalConstraint.Top
            ) {
                add(
                    "constraints" to YamlPayload.Mapping(
                        listOf(
                            "horizontal" to str(horizontalToken(constraints.horizontal)),
                            "vertical" to str(verticalToken(constraints.vertical)),
                        ),
                    ),
                )
            }
        },
    )

    // --- shape block ---

    private fun shapePayload(node: DesignNode): YamlPayload.Mapping? {
        val shape = node.kind as? DesignNodeKind.Shape ?: return null
        return YamlPayload.Mapping(
            buildList {
                add("kind" to str(shapeToken(shape.shape)))
                shape.pointCount?.let { add("pointCount" to num(it.toDouble())) }
                shape.innerRadius?.let { add("innerRadius" to num(it)) }
            },
        )
    }

    // --- layout block (auto layout + the canonical size carrier) ---

    private fun layoutPayload(node: DesignNode): YamlPayload.Mapping? {
        val layout = node.layout
        val entries = buildList {
            layoutModeToken(layout.mode)?.let { add("mode" to str(it)) }
            gapPayload(node)?.let { add("gap" to it) }
            alignPayload(node)?.let { add("align" to it) }
            sizingPayload(node)?.let { add("sizing" to it) }
        }
        return if (entries.isEmpty()) null else YamlPayload.Mapping(entries)
    }

    private fun gapPayload(node: DesignNode): YamlPayload? {
        if (node.layout.mode == LayoutMode.None) return null
        return when (val gap = node.layout.gap) {
            is DesignGap.Auto -> str("auto")
            is DesignGap.Fixed -> {
                val value = gap.value
                when {
                    value is Bindable.VarRef -> YamlPayload.Scalar(YamlScalarValue.TokenRef(value.id))
                    value is Bindable.Value && value.value != 0.0 -> num(value.value)
                    else -> null
                }
            }
        }
    }

    /** Cross-axis alignment maps back onto `align.inline` (the reader's cross slot). */
    private fun alignPayload(node: DesignNode): YamlPayload? {
        if (node.layout.mode == LayoutMode.None) return null
        val align = node.layout.alignItems
        if (align == AlignItems.Start) return null
        return YamlPayload.Mapping(listOf("inline" to str(alignToken(align))))
    }

    private fun sizingPayload(node: DesignNode): YamlPayload? {
        val sizing = node.sizing ?: DesignSizing()
        val width = axisPayload(sizing.horizontal, node.size.width)
        val height = axisPayload(sizing.vertical, node.size.height)
        val entries = buildList {
            width?.let { add("width" to it) }
            height?.let { add("height" to it) }
        }
        return if (entries.isEmpty()) null else YamlPayload.Mapping(entries)
    }

    /** `fill`/`hug` render as `{ type }`; `fixed` carries `value` (the size source). */
    private fun axisPayload(mode: SizingMode, value: Double?): YamlPayload? = when (mode) {
        SizingMode.Fill -> YamlPayload.Mapping(listOf("type" to str("fill")))
        SizingMode.Hug -> YamlPayload.Mapping(listOf("type" to str("hug")))
        SizingMode.Fixed ->
            if (value == null) {
                null
            } else {
                YamlPayload.Mapping(listOf("type" to str("fixed"), "value" to num(value)))
            }
    }

    // --- style block ---

    private fun stylePayload(node: DesignNode): YamlPayload.Mapping? {
        val entries = buildList {
            radiusPayload(node.cornerRadius)?.let { add("radius" to it) }
            node.fills?.takeIf { it.isNotEmpty() }?.let { add("fills" to StyleYamlWriter.fills(it)) }
            node.strokes?.let { strokes ->
                if (strokes.paints.isNotEmpty()) add("strokes" to StyleYamlWriter.strokes(strokes))
            }
            node.effects.takeIf { it.isNotEmpty() }?.let { add("effects" to StyleYamlWriter.effects(it)) }
        }
        return if (entries.isEmpty()) null else YamlPayload.Mapping(entries)
    }

    /** A uniform corner radius round-trips as a single `radius:` scalar. */
    private fun radiusPayload(radius: DesignCornerRadius?): YamlPayload? {
        if (radius == null) return null
        val corners = listOf(radius.topLeft, radius.topRight, radius.bottomRight, radius.bottomLeft)
        val uniform = corners.all { it == radius.topLeft }
        if (!uniform) return null
        return when (val corner = radius.topLeft) {
            is Bindable.VarRef -> YamlPayload.Scalar(YamlScalarValue.TokenRef(corner.id))
            is Bindable.Value -> if (corner.value == 0.0) null else num(corner.value)
            else -> null
        }
    }

    // --- text block ---

    private fun textPayload(node: DesignNode): YamlPayload.Mapping? {
        val text = node.kind as? DesignNodeKind.Text ?: return null
        // Prefer the i18n `content.defaultText`; fall back to a literal `characters` value (what
        // the editor's node factory carries) so a freshly-created text node round-trips its
        // rendered text instead of recompiling empty.
        val defaultText = text.content?.defaultText?.takeIf { it.isNotEmpty() }
            ?: (text.characters as? Bindable.Value)?.value?.takeIf { it.isNotEmpty() }
        val entries = buildList {
            defaultText?.let { add("defaultText" to str(it)) }
            text.textStyle?.let { add("typography" to TypographyYamlWriter.typography(it)) }
        }
        return if (entries.isEmpty()) null else YamlPayload.Mapping(entries)
    }

    // --- tokens (inverse of ReaderEnums; canonical spelling) ---

    private fun shapeToken(shape: ShapeType): String = when (shape) {
        ShapeType.Rectangle -> "rectangle"
        ShapeType.Ellipse -> "ellipse"
        ShapeType.Polygon -> "polygon"
        ShapeType.Star -> "star"
        ShapeType.Line -> "line"
        ShapeType.Arrow -> "arrow"
        ShapeType.Vector -> "vector"
    }

    private fun layoutModeToken(mode: LayoutMode): String? = when (mode) {
        LayoutMode.None -> null
        LayoutMode.Horizontal -> "row"
        LayoutMode.Vertical -> "column"
        LayoutMode.Grid -> "grid"
    }

    private fun alignToken(align: AlignItems): String = when (align) {
        AlignItems.Start -> "start"
        AlignItems.Center -> "center"
        AlignItems.End -> "end"
        AlignItems.Baseline -> "baseline"
        AlignItems.Stretch -> "stretch"
    }

    private fun horizontalToken(value: HorizontalConstraint): String = when (value) {
        HorizontalConstraint.Left -> "left"
        HorizontalConstraint.Right -> "right"
        HorizontalConstraint.Center -> "center"
        HorizontalConstraint.LeftRight -> "left-right"
        HorizontalConstraint.Scale -> "scale"
    }

    private fun verticalToken(value: VerticalConstraint): String = when (value) {
        VerticalConstraint.Top -> "top"
        VerticalConstraint.Bottom -> "bottom"
        VerticalConstraint.Center -> "center"
        VerticalConstraint.TopBottom -> "top-bottom"
        VerticalConstraint.Scale -> "scale"
    }

    // --- payload helpers (mirror StyleYamlWriter) ---

    private fun str(value: String): YamlPayload = YamlPayload.Scalar(YamlScalarValue.Str(value))
    private fun bool(value: Boolean): YamlPayload = YamlPayload.Scalar(YamlScalarValue.Bool(value))
    private fun num(value: Double): YamlPayload = YamlPayload.Scalar(YamlScalarValue.Num(value))
}
