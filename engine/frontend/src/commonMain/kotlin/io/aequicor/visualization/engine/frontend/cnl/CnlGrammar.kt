package io.aequicor.visualization.engine.frontend.cnl

import io.aequicor.visualization.engine.ir.model.AlignItems
import io.aequicor.visualization.engine.ir.model.BaselineAlign
import io.aequicor.visualization.engine.ir.model.Bindable
import io.aequicor.visualization.engine.ir.model.ContainerKind
import io.aequicor.visualization.engine.ir.model.DesignInsets
import io.aequicor.visualization.engine.ir.model.InstanceOverride
import io.aequicor.visualization.subsystems.figures.BooleanOperationKind
import io.aequicor.visualization.engine.ir.model.DesignAction
import io.aequicor.visualization.engine.ir.model.DesignColor
import io.aequicor.visualization.engine.ir.model.DesignEasing
import io.aequicor.visualization.engine.ir.model.DesignInteraction
import io.aequicor.visualization.engine.ir.model.DesignTransition
import io.aequicor.visualization.engine.ir.model.EasingKind
import io.aequicor.visualization.engine.ir.model.InteractionTrigger
import io.aequicor.visualization.engine.ir.model.MotionFrame
import io.aequicor.visualization.engine.ir.model.OverlayPosition
import io.aequicor.visualization.engine.ir.model.OverlaySettings
import io.aequicor.visualization.engine.ir.model.TransitionDirection
import io.aequicor.visualization.engine.ir.model.TransitionType
import io.aequicor.visualization.engine.ir.model.DesignGap
import io.aequicor.visualization.engine.ir.model.JustifyContent
import io.aequicor.visualization.engine.ir.model.DesignNode
import io.aequicor.visualization.engine.ir.model.DesignNodePatch
import io.aequicor.visualization.engine.ir.model.DesignEffect
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.engine.ir.model.ExportFormat
import io.aequicor.visualization.engine.ir.model.ResponsiveDimension
import io.aequicor.visualization.engine.ir.model.appliedTo
import io.aequicor.visualization.engine.ir.model.DesignPaint
import io.aequicor.visualization.engine.ir.model.DesignPoint
import io.aequicor.visualization.engine.ir.model.DesignStrokes
import io.aequicor.visualization.engine.ir.model.DesignTextStyle
import io.aequicor.visualization.engine.ir.model.DesignUnit
import io.aequicor.visualization.engine.ir.model.GradientKind
import io.aequicor.visualization.subsystems.figures.HandleMirror
import io.aequicor.visualization.engine.ir.model.HorizontalConstraint
import io.aequicor.visualization.engine.ir.model.ImageScaleMode
import io.aequicor.visualization.engine.ir.model.GridTrack
import io.aequicor.visualization.engine.ir.model.GuideOrientation
import io.aequicor.visualization.engine.ir.model.LayoutGridAlignment
import io.aequicor.visualization.engine.ir.model.LayoutGridDefinition
import io.aequicor.visualization.engine.ir.model.LayoutGridType
import io.aequicor.visualization.engine.ir.model.LayoutMode
import io.aequicor.visualization.engine.ir.model.MaskType
import io.aequicor.visualization.engine.ir.model.MediaKind
import io.aequicor.visualization.engine.ir.model.OverflowMode
import io.aequicor.visualization.engine.ir.model.ScrollOverflow
import io.aequicor.visualization.engine.ir.model.PropValue
import io.aequicor.visualization.subsystems.figures.ShapeType
import io.aequicor.visualization.engine.ir.model.SizingMode
import io.aequicor.visualization.engine.ir.model.StrokeAlign
import io.aequicor.visualization.engine.ir.model.TextAlignHorizontal
import io.aequicor.visualization.engine.ir.model.TextAlignVertical
import io.aequicor.visualization.engine.ir.model.TextAutoResize
import io.aequicor.visualization.engine.ir.model.TextCase
import io.aequicor.visualization.engine.ir.model.TextDecorationKind
import io.aequicor.visualization.engine.ir.model.TextListType
import io.aequicor.visualization.engine.ir.model.UnitValue
import io.aequicor.visualization.subsystems.figures.VectorRegion
import io.aequicor.visualization.subsystems.figures.VectorVertex
import io.aequicor.visualization.engine.ir.model.VerticalConstraint
import io.aequicor.visualization.engine.ir.model.bindable
import io.aequicor.visualization.engine.ir.model.literalOrNull
import io.aequicor.visualization.engine.ir.model.orZero

/**
 * The shared CNL grammar registry — the single source of truth that drives the emitter and
 * anchors the parser vocabulary. Each [Descriptor] declares a canonical English keyword and,
 * critically, how to **render** the corresponding IR field back into a CNL phrase; the parser
 * ([CnlVocabulary] + [CnlParser]) understands those same keywords. A grammar-symmetry test
 * keeps the two directions from drifting.
 *
 * P0 covers the buckets the parser already understands (geometry, single solid fill/stroke,
 * uniform radius/opacity/rotation, layout mode/gap/padding, parent-align, font size/weight).
 * Later phases add descriptors for the deep buckets (gradients, effects, components, …).
 */
internal object CnlGrammar {
    /** Canonical number rendering: drop a trailing `.0`. */
    fun num(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

    /** `#RRGGBB`, or `#RRGGBBAA` when alpha ≠ FF; variable refs as `$id`. */
    fun colorToken(color: Bindable<DesignColor>): String? = when (color) {
        is Bindable.Value -> hex(color.value)
        is Bindable.VarRef -> "$" + color.id
        else -> null
    }

    /**
     * Solid-fill color token, extending [colorToken] with the data-binding forms a fill accepts:
     * `{{expr}}` (DataRef) round-trips through readPaint -> bindableColor -> expressionBody. `$prop`
     * (PropRef) is a reader gap (varRefOf yields VarRef, never PropRef) so it stays null -> ir-splice.
     * Confined to solid fills: strokes/effects/gradient stops keep [colorToken] because their parsers
     * (consumeStroke/consumeEffect/stops) do not yet accept `{{expr}}`.
     */
    private fun solidPaintToken(color: Bindable<DesignColor>): String? = when (color) {
        is Bindable.Value -> hex(color.value)
        is Bindable.VarRef -> "\$${color.id}"
        is Bindable.DataRef -> "{{${color.expression.raw}}}"
        is Bindable.PropRef -> null
    }

    fun hex(color: DesignColor): String {
        fun byte(v: Int) = v.toString(16).uppercase().padStart(2, '0')
        val base = "#${byte(color.red)}${byte(color.green)}${byte(color.blue)}"
        return if (color.alpha == 255) base else base + byte(color.alpha)
    }

    /**
     * Escapes a raw string for a `«…»` text literal: `\\`, `\»`, `\n`, `\r`. Inverse of the
     * escape handling in [scanTextLiteral]; every emit site that interpolates free-form text
     * into a literal must go through [quoteText] or the sentence breaks on reparse.
     */
    fun escapeText(value: String): String = buildString(value.length) {
        for (c in value) when (c) {
            '\\' -> append("\\\\")
            '»' -> append("\\»")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            else -> append(c)
        }
    }

    /** Renders a `«…»` text literal with [escapeText] applied. */
    fun quoteText(value: String): String = "«${escapeText(value)}»"

    /** Result of [scanTextLiteral]: unescaped text, index of the closing char, termination flag. */
    data class TextScan(val text: String, val closeIndex: Int, val terminated: Boolean)

    /**
     * Scans a `«…»`/`"…"` literal body starting at [start] (the index just after the opener),
     * honoring backslash escapes (`\<close>`, `\\`, `\n`, `\r`). Shared by every CNL tokenizer
     * so escaping stays symmetric with [escapeText].
     */
    fun scanTextLiteral(line: String, start: Int, close: Char): TextScan {
        val text = StringBuilder()
        var i = start
        while (i < line.length) {
            val c = line[i]
            if (c == '\\' && i + 1 < line.length) {
                val decoded = when (val next = line[i + 1]) {
                    close, '\\' -> next
                    'n' -> '\n'
                    'r' -> '\r'
                    else -> null
                }
                if (decoded != null) {
                    text.append(decoded)
                    i += 2
                    continue
                }
            }
            if (c == close) break
            text.append(c)
            i++
        }
        return TextScan(text.toString(), i, i < line.length)
    }

    /** The canonical noun for [node], or null when the node type has no CNL noun yet. */
    fun canonicalNoun(node: DesignNode): String? = when (val kind = node.kind) {
        is DesignNodeKind.Shape -> when (kind.shape) {
            ShapeType.Rectangle -> "Rectangle"
            ShapeType.Ellipse -> "Ellipse"
            ShapeType.Line -> "Line"
            ShapeType.Star -> "Star"
            ShapeType.Polygon -> "Polygon"
            ShapeType.Arrow -> "Arrow"
            ShapeType.Vector -> "Vector"
        }
        is DesignNodeKind.Text -> if (node.role == "button") "Button" else "Text"
        is DesignNodeKind.Media -> "Image"
        DesignNodeKind.Frame -> if (node.containerKind == ContainerKind.AutoLayout) "AutoLayout" else "Frame"
        is DesignNodeKind.Instance -> "Instance"
        is DesignNodeKind.Diagram -> "Diagram"
        else -> null
    }

    /** The authored visible text of a text node, for `Text «…»`. */
    fun textLiteral(node: DesignNode): String? {
        val text = node.kind as? DesignNodeKind.Text ?: return null
        text.content?.defaultText?.takeIf { it.isNotEmpty() }?.let { return it }
        return text.characters.literalOrNull()?.takeIf { it.isNotEmpty() }
    }

    /**
     * One property family's bidirectional binding. [keyword] is the canonical spelling the
     * parser must also accept (blank for keyword-less forms like `120 by 15` / `column`).
     * [render] reads the field from a node and returns the whole phrase, or null when unset
     * / at its default / not yet expressible in P0.
     */
    data class Descriptor(
        val kind: CnlPropertyKind,
        val keyword: String,
        val order: Int,
        val render: (DesignNode) -> String?,
    )

    val descriptors: List<Descriptor> = listOf(
        // Components (instance side) — emitted before geometry so `Instance of … variant … props …`
        // reads naturally; each renderer yields null for non-instance nodes.
        Descriptor(CnlPropertyKind.ComponentRef, "of", 2, ::renderComponentRef),
        Descriptor(CnlPropertyKind.LibraryRef, "library", 3, ::renderLibraryRef),
        Descriptor(CnlPropertyKind.Variant, "variant", 4, ::renderVariant),
        Descriptor(CnlPropertyKind.Props, "props", 5, ::renderProps),
        Descriptor(CnlPropertyKind.Detach, "detach", 6, ::renderDetach),
        Descriptor(CnlPropertyKind.ResetOverrides, "reset", 7, ::renderResetOverrides),
        Descriptor(CnlPropertyKind.SlotOverride, "slot", 8, ::renderSlotOverrides),
        Descriptor(CnlPropertyKind.SetOverride, "override", 9, ::renderSetOverrides),
        Descriptor(CnlPropertyKind.NestedOverride, "nested", 9, ::renderNestedOverrides),
        Descriptor(CnlPropertyKind.NodeName, "name", 9, ::renderNodeName),
        Descriptor(CnlPropertyKind.Size, "size", 10, ::renderSize),
        Descriptor(CnlPropertyKind.Position, "position", 20, ::renderPosition),
        Descriptor(CnlPropertyKind.Visible, "visible", 22, ::renderVisible),
        Descriptor(CnlPropertyKind.Locked, "locked", 23, ::renderLocked),
        Descriptor(CnlPropertyKind.Rotation, "rotation", 24, ::renderRotation),
        Descriptor(CnlPropertyKind.Absolute, "absolute", 26, ::renderAbsolute),
        Descriptor(CnlPropertyKind.Anchor, "anchor", 27, ::renderAnchor),
        Descriptor(CnlPropertyKind.AutoLayout, "auto-layout", 29, ::renderAutoLayoutMarker),
        Descriptor(CnlPropertyKind.Direction, "", 30, ::renderDirection),
        Descriptor(CnlPropertyKind.Wrap, "wrap", 31, ::renderWrap),
        Descriptor(CnlPropertyKind.Gap, "gap", 32, ::renderGap),
        Descriptor(CnlPropertyKind.Distribute, "distribute", 33, ::renderDistribute),
        Descriptor(CnlPropertyKind.Padding, "padding", 34, ::renderPadding),
        Descriptor(CnlPropertyKind.Gap, "gap", 36, ::renderGapAxes),
        Descriptor(CnlPropertyKind.Fill, "color", 40, ::renderFill),
        Descriptor(CnlPropertyKind.Stroke, "stroke", 42, ::renderStroke),
        Descriptor(CnlPropertyKind.Effect, "effect", 43, ::renderEffects),
        Descriptor(CnlPropertyKind.Radius, "radius", 44, ::renderRadius),
        Descriptor(CnlPropertyKind.Smoothing, "smoothing", 45, ::renderCornerSmoothing),
        Descriptor(CnlPropertyKind.Opacity, "opacity", 46, ::renderOpacity),
        Descriptor(CnlPropertyKind.Blend, "blend", 47, ::renderBlend),
        Descriptor(CnlPropertyKind.StyleRefs, "styles", 50, ::renderStyleRefs),
        Descriptor(CnlPropertyKind.AlignParent, "align", 48, ::renderAlign),
        Descriptor(CnlPropertyKind.Constraints, "constraints", 49, ::renderConstraints),
        Descriptor(CnlPropertyKind.Clip, "clip", 51, ::renderClip),
        Descriptor(CnlPropertyKind.ContainerAlign, "align", 52, ::renderContainerAlign),
        Descriptor(CnlPropertyKind.Overflow, "overflow", 53, ::renderOverflow),
        Descriptor(CnlPropertyKind.Scroll, "scroll", 54, ::renderScroll),
        Descriptor(CnlPropertyKind.Columns, "columns", 55, ::renderColumns),
        Descriptor(CnlPropertyKind.Rows, "rows", 56, ::renderRows),
        Descriptor(CnlPropertyKind.Place, "place", 57, ::renderPlace),
        Descriptor(CnlPropertyKind.Guides, "guides", 58, ::renderGuides),
        Descriptor(CnlPropertyKind.Grids, "grids", 59, ::renderGrids),
        Descriptor(CnlPropertyKind.VariableModes, "modes", 60, ::renderVariableModes),
        Descriptor(CnlPropertyKind.FontSize, "size", 60, ::renderFontSize),
        Descriptor(CnlPropertyKind.TextKey, "key", 61, ::renderTextKey),
        Descriptor(CnlPropertyKind.FontWeight, "weight", 62, ::renderFontWeight),
        Descriptor(CnlPropertyKind.FontFamily, "font", 63, ::renderFontFamily),
        Descriptor(CnlPropertyKind.LineHeight, "line-height", 64, ::renderLineHeight),
        Descriptor(CnlPropertyKind.Tracking, "tracking", 65, ::renderTracking),
        Descriptor(CnlPropertyKind.ParagraphSpacing, "paragraph-spacing", 66, ::renderParagraphSpacing),
        Descriptor(CnlPropertyKind.TextAlign, "text-align", 67, ::renderTextAlign),
        Descriptor(CnlPropertyKind.TextValign, "text-valign", 68, ::renderTextValign),
        Descriptor(CnlPropertyKind.TextCase, "case", 69, ::renderTextCase),
        Descriptor(CnlPropertyKind.TextDecoration, "decoration", 70, ::renderTextDecoration),
        Descriptor(CnlPropertyKind.Features, "features", 71, ::renderFeatures),
        Descriptor(CnlPropertyKind.Axes, "axes", 72, ::renderAxes),
        Descriptor(CnlPropertyKind.TextStyleRef, "text-style", 74, ::renderTextStyleRef),
        Descriptor(CnlPropertyKind.Characters, "characters", 75, ::renderCharacters),
        Descriptor(CnlPropertyKind.AutoSize, "autosize", 76, ::renderAutoSize),
        Descriptor(CnlPropertyKind.Truncate, "truncate", 77, ::renderTruncate),
        Descriptor(CnlPropertyKind.MaxLines, "maxLines", 78, ::renderMaxLines),
        Descriptor(CnlPropertyKind.ListSettings, "list", 79, ::renderListSettings),
        Descriptor(CnlPropertyKind.Link, "link", 82, ::renderLinks),
        Descriptor(CnlPropertyKind.Span, "span", 83, ::renderSpans),
        // Media / shape params / vector / boolean / mask (P6). Ties (Media 50 ↔ StyleRefs 50,
        // ShapePoints 52 ↔ ContainerAlign 52, Mask 70 ↔ TextDecoration 70) are harmless: renderers
        // are kind-gated, never co-occur, and sortedBy is stable.
        Descriptor(CnlPropertyKind.Media, "media", 50, ::renderMedia),
        Descriptor(CnlPropertyKind.ShapePoints, "points", 52, ::renderShapePoints),
        Descriptor(CnlPropertyKind.ShapeInner, "inner", 53, ::renderShapeInner),
        Descriptor(CnlPropertyKind.ShapeArc, "arc", 51, ::renderShapeArc),
        Descriptor(CnlPropertyKind.ViewBox, "viewbox", 54, ::renderViewBox),
        Descriptor(CnlPropertyKind.IconRef, "icon", 55, ::renderIconRef),
        Descriptor(CnlPropertyKind.PathRef, "svg", 56, ::renderPathRef),
        Descriptor(CnlPropertyKind.VectorPaths, "path", 57, ::renderVectorPaths),
        Descriptor(CnlPropertyKind.VectorNetwork, "network", 58, ::renderNetwork),
        Descriptor(CnlPropertyKind.BooleanOp, "boolean", 59, ::renderBooleanOp),
        Descriptor(CnlPropertyKind.Mask, "mask", 70, ::renderMask),
        // Interactions + motion (P7). Orders 90/92 avoid colliding with decoration=70 / axes=72 and
        // sort after every P4a text descriptor (max existing = list=79). The Interactions keyword is ""
        // (the trigger words are their own propertyKeywords entries), so the symmetry test skips it.
        Descriptor(CnlPropertyKind.Interactions, "", 90, ::renderInteractions),
        Descriptor(CnlPropertyKind.Motion, "motion", 92, ::renderMotion),
        // P10 responsive/export (orders 80/90 unused before P7 shifted them; handoff has NO descriptor —
        // not emittable, HandoffPatch lifts to DesignDocument.handoff with no per-node field).
        Descriptor(CnlPropertyKind.Responsive, "when", 94, ::renderResponsive),
        Descriptor(CnlPropertyKind.Export, "export", 96, ::renderExport),
    )

    private fun textStyleOf(node: DesignNode) = (node.kind as? DesignNodeKind.Text)?.textStyle
    private fun asText(node: DesignNode) = node.kind as? DesignNodeKind.Text
    private fun unit(u: UnitValue): String = if (u.unit == DesignUnit.Percent) "${num(u.value)}%" else num(u.value)

    // --- renderers (IR field -> CNL phrase) ---

    private fun renderNodeName(node: DesignNode): String? =
        node.name.takeIf { it.isNotEmpty() }?.let { "name ${quoteText(it)}" }

    private fun renderSize(node: DesignNode): String? {
        val sizing = node.sizing
        if (sizing == null && node.minSize == null && node.maxSize == null) return null
        val wMode = sizing?.horizontal ?: SizingMode.Fixed
        val hMode = sizing?.vertical ?: SizingMode.Fixed
        val wVal = node.size.width
        val hVal = node.size.height
        val wMin = node.minSize?.width
        val wMax = node.maxSize?.width
        val hMin = node.minSize?.height
        val hMax = node.maxSize?.height
        val wSimple = wMode == SizingMode.Fixed && wMin == null && wMax == null
        val hSimple = hMode == SizingMode.Fixed && hMin == null && hMax == null
        if (wSimple && hSimple && wVal != null && hVal != null) return "${num(wVal)} by ${num(hVal)}"
        val parts = listOfNotNull(
            renderAxis("width", wMode, wVal, wMin, wMax),
            renderAxis("height", hMode, hVal, hMin, hMax),
        )
        return if (parts.isEmpty()) null else parts.joinToString(" ")
    }

    private fun renderAxis(axis: String, mode: SizingMode, value: Double?, min: Double?, max: Double?): String? {
        if (mode == SizingMode.Fixed && min == null && max == null) return value?.let { "$axis ${num(it)}" }
        if (min == null && max == null && value == null && mode != SizingMode.Fixed) {
            return "$axis ${sizingModeWord(mode)}"
        }
        val parts = buildList {
            add(sizingModeWord(mode))
            value?.let { add(num(it)) }
            min?.let { add("min ${num(it)}") }
            max?.let { add("max ${num(it)}") }
        }
        return "$axis (" + parts.joinToString(" ") + ")"
    }

    private fun sizingModeWord(mode: SizingMode): String = when (mode) {
        SizingMode.Fixed -> "fixed"
        SizingMode.Hug -> "hug"
        SizingMode.Fill -> "fill"
    }

    private fun renderAbsolute(node: DesignNode): String? = if (node.layoutChild.absolute) "absolute" else null

    private fun renderVisible(node: DesignNode): String? = when (val visible = node.visible) {
        is Bindable.Value -> if (visible.value) null else "visible no"
        is Bindable.VarRef -> "visible \$${visible.id}"
        is Bindable.DataRef -> "visible {{${visible.expression.raw}}}"
        is Bindable.PropRef -> "visible \$prop.${visible.name}"
    }

    private fun renderLocked(node: DesignNode): String? = if (node.locked) "locked yes" else null

    private fun renderAnchor(node: DesignNode): String? {
        val anchors = node.anchors ?: return null
        val parts = buildList {
            anchors.inlineStart?.literalOrNull()?.let { add("inlineStart ${num(it)}") }
            anchors.inlineEnd?.literalOrNull()?.let { add("inlineEnd ${num(it)}") }
            anchors.blockStart?.literalOrNull()?.let { add("blockStart ${num(it)}") }
            anchors.blockEnd?.literalOrNull()?.let { add("blockEnd ${num(it)}") }
        }
        return if (parts.isEmpty()) null else "anchor (" + parts.joinToString(" ") + ")"
    }

    private fun renderWrap(node: DesignNode): String? = if (node.layout.wrap) "wrap" else null

    private fun renderClip(node: DesignNode): String? = if (node.layout.clipsContent) "clip" else null

    private fun renderDistribute(node: DesignNode): String? = when (node.layout.justifyContent) {
        JustifyContent.Start -> null
        JustifyContent.Center -> "distribute center"
        JustifyContent.End -> "distribute end"
        JustifyContent.SpaceBetween -> "distribute space-between"
    }

    private fun renderGapAxes(node: DesignNode): String? {
        if (node.layout.rowGap == null && node.layout.columnGap == null) return null
        val parts = buildList {
            node.layout.rowGap?.let { value -> numberToken(value)?.let { add("row $it") } }
            node.layout.columnGap?.let { value -> numberToken(value)?.let { add("column $it") } }
        }
        return if (parts.isEmpty()) null else "gap (" + parts.joinToString(" ") + ")"
    }

    private fun renderConstraints(node: DesignNode): String? {
        val h = node.constraints.horizontal
        val v = node.constraints.vertical
        // Default + the three combos P0 renderAlign (order 48) already emits → yield to avoid double-emit.
        if (h == HorizontalConstraint.Left && v == VerticalConstraint.Top) return null
        if (h == HorizontalConstraint.Center && v == VerticalConstraint.Center) return null
        if (h == HorizontalConstraint.Left && v == VerticalConstraint.Bottom) return null
        if (h == HorizontalConstraint.Right && v == VerticalConstraint.Top) return null
        return "constraints (horizontal ${horizontalWord(h)} vertical ${verticalWord(v)})"
    }

    private fun horizontalWord(c: HorizontalConstraint): String = when (c) {
        HorizontalConstraint.Left -> "left"
        HorizontalConstraint.Right -> "right"
        HorizontalConstraint.Center -> "center"
        HorizontalConstraint.LeftRight -> "left-right"
        HorizontalConstraint.Scale -> "scale"
    }

    private fun verticalWord(c: VerticalConstraint): String = when (c) {
        VerticalConstraint.Top -> "top"
        VerticalConstraint.Bottom -> "bottom"
        VerticalConstraint.Center -> "center"
        VerticalConstraint.TopBottom -> "top-bottom"
        VerticalConstraint.Scale -> "scale"
    }

    private fun renderContainerAlign(node: DesignNode): String? {
        val axisKey = if (node.layout.mode == LayoutMode.Horizontal) "block" else "inline"
        val parts = buildList {
            if (node.layout.alignItems != AlignItems.Start) add("$axisKey ${alignItemsWord(node.layout.alignItems)}")
            if (node.layout.baseline != BaselineAlign.First) add("baseline last")
        }
        return if (parts.isEmpty()) null else "align (" + parts.joinToString(" ") + ")"
    }

    private fun renderOverflow(node: DesignNode): String? {
        val parts = buildList {
            if (node.scroll.overflowX != OverflowMode.Visible) add("x ${overflowWord(node.scroll.overflowX)}")
            if (node.scroll.overflowY != OverflowMode.Visible) add("y ${overflowWord(node.scroll.overflowY)}")
        }
        return if (parts.isEmpty()) null else "overflow (" + parts.joinToString(" ") + ")"
    }

    private fun overflowWord(mode: OverflowMode): String = when (mode) {
        OverflowMode.Visible -> "visible"
        OverflowMode.Hidden -> "hidden"
        OverflowMode.Auto -> "auto"
    }

    private fun renderScroll(node: DesignNode): String? {
        val overflow = node.scroll.overflow
        val fixed = node.scroll.fixedChildren
        val sticky = node.scroll.sticky
        if (overflow == ScrollOverflow.None && fixed.isEmpty() && !sticky) return null
        val parts = buildList {
            if (overflow != ScrollOverflow.None) add("direction ${scrollWord(overflow)}")
            if (fixed.isNotEmpty()) add("fixedChildren (" + fixed.joinToString(" ") + ")")
            if (sticky) add("sticky")
        }
        return "scroll (" + parts.joinToString(" ") + ")"
    }

    private fun scrollWord(overflow: ScrollOverflow): String = when (overflow) {
        ScrollOverflow.None -> "none"
        ScrollOverflow.Horizontal -> "horizontal"
        ScrollOverflow.Vertical -> "vertical"
        ScrollOverflow.Both -> "both"
    }

    // Grid gap is owned by renderGapAxes (order 36).
    private fun renderColumns(node: DesignNode): String? {
        val tracks = node.layout.columns
        if (tracks.isEmpty()) return null
        val first = tracks.first()
        if (tracks.any { it != first }) return "columns (tracks (" + tracks.joinToString(" ") { trackWord(it) } + "))"
        val parts = buildList {
            if (tracks.size > 1) add("count ${tracks.size}")
            add("track ${trackWord(first)}")
        }
        return "columns (" + parts.joinToString(" ") + ")"
    }

    private fun renderRows(node: DesignNode): String? {
        val tracks = node.layout.rows
        if (tracks.isNotEmpty()) {
            val first = tracks.first()
            if (tracks.any { it != first }) return "rows (tracks (" + tracks.joinToString(" ") { trackWord(it) } + "))"
            val parts = buildList {
                if (tracks.size > 1) add("count ${tracks.size}")
                add("track ${trackWord(first)}")
            }
            return "rows (" + parts.joinToString(" ") + ")"
        }
        val implicit = node.layout.implicitRows ?: return null
        val parts = buildList {
            add("auto")
            if (implicit != GridTrack.Flex(1.0.bindable())) add("track ${trackWord(implicit)}")
            node.layout.implicitRowMin?.let { min -> numberToken(min)?.let { add("min $it") } }
        }
        return "rows (" + parts.joinToString(" ") + ")"
    }

    private fun trackWord(track: GridTrack): String = when (track) {
        is GridTrack.Fixed -> numberToken(track.value) ?: num(0.0)
        is GridTrack.Flex -> flexWord(track.value)
        GridTrack.Hug -> "hug"
    }

    /**
     * Flex-weight surface. The `fr` marker must follow a self-delimiting body so a re-read can tell
     * it apart from a ref id that merely ends in `fr` (`$railfr` = Fixed ref to `railfr`): a literal
     * (`1fr`), a `{{expr}}` binding (`{{w}}fr`), or a **braced** token ref (`${weight}fr` /
     * `${prop.w}fr`). A bare `$weightfr` would be indistinguishable from a Fixed ref, so token refs
     * are braced here — the CNL track grammar re-read relies on it.
     */
    private fun flexWord(value: Bindable<Double>): String = when (value) {
        is Bindable.Value -> "${num(value.value)}fr"
        is Bindable.DataRef -> "{{${value.expression.raw}}}fr"
        is Bindable.VarRef -> "\${${value.id}}fr"
        is Bindable.PropRef -> "\${prop.${value.name}}fr"
    }

    private fun renderPlace(node: DesignNode): String? {
        val placement = node.gridPlacement ?: return null
        val parts = buildList {
            if (placement.column != 0) add("column ${placement.column}")
            if (placement.row != 0) add("row ${placement.row}")
            if (placement.columnSpan != 1) add("columnSpan ${placement.columnSpan}")
            if (placement.rowSpan != 1) add("rowSpan ${placement.rowSpan}")
        }
        return if (parts.isEmpty()) null else "place (" + parts.joinToString(" ") + ")"
    }

    private fun renderGuides(node: DesignNode): String? {
        if (node.guides.isEmpty()) return null
        return "guides " + node.guides.joinToString(" ") { "(${guideWord(it.orientation)} ${num(it.position)})" }
    }

    private fun guideWord(orientation: GuideOrientation): String = when (orientation) {
        GuideOrientation.Horizontal -> "horizontal"
        GuideOrientation.Vertical -> "vertical"
    }

    private fun renderGrids(node: DesignNode): String? {
        if (node.layoutGrids.isEmpty()) return null
        return "grids " + node.layoutGrids.joinToString(" ") { gridGroup(it) }
    }

    private fun gridGroup(grid: LayoutGridDefinition): String {
        val parts = buildList {
            add(gridTypeWord(grid.type))
            grid.count?.let { c -> intToken(c)?.let { add("count $it") } }
            grid.size?.let { s -> numberToken(s)?.let { add("size $it") } }
            grid.gutter?.let { g -> if (g.literalOrNull() != 0.0) numberToken(g)?.let { add("gutter $it") } }
            grid.margin?.let { m -> if (m.literalOrNull() != 0.0) numberToken(m)?.let { add("margin $it") } }
            if (grid.alignment != LayoutGridAlignment.Stretch) add("alignment ${gridAlignWord(grid.alignment)}")
            grid.color?.let { add("color ${hex(it)}") }
            if (!grid.visible) add("visible false")
        }
        return "(" + parts.joinToString(" ") + ")"
    }

    private fun gridTypeWord(type: LayoutGridType): String = when (type) {
        LayoutGridType.Columns -> "columns"
        LayoutGridType.Rows -> "rows"
        LayoutGridType.Grid -> "grid"
    }

    private fun gridAlignWord(alignment: LayoutGridAlignment): String = when (alignment) {
        LayoutGridAlignment.Stretch -> "stretch"
        LayoutGridAlignment.Start -> "start"
        LayoutGridAlignment.Center -> "center"
        LayoutGridAlignment.End -> "end"
    }

    private fun alignItemsWord(a: AlignItems): String = when (a) {
        AlignItems.Start -> "start"
        AlignItems.Center -> "center"
        AlignItems.End -> "end"
        AlignItems.Baseline -> "baseline"
        AlignItems.Stretch -> "stretch"
    }

    private fun renderPosition(node: DesignNode): String? {
        val point = node.position ?: return null
        return "position ${num(point.x.orZero)} ${num(point.y.orZero)}"
    }

    private fun renderRotation(node: DesignNode): String? =
        if (node.rotation == 0.0) null else "rotation ${num(node.rotation)}"

    private fun renderDirection(node: DesignNode): String? = when (node.layout.mode) {
        LayoutMode.Vertical -> "column"
        LayoutMode.Horizontal -> "row"
        LayoutMode.Grid -> "grid"
        LayoutMode.None -> null
    }

    private fun renderAutoLayoutMarker(node: DesignNode): String? =
        // Fixed-noun containers (screen/component/section/group) cannot encode container kind in
        // their heading noun the way a plain `Frame`/`AutoLayout` heading does, so an Auto Layout
        // one carries an explicit `auto-layout` marker that round-trips back to containerKind.
        if (node.containerKind == ContainerKind.AutoLayout && node.type in setOf("screen", "component", "section", "group")) {
            "auto-layout"
        } else {
            null
        }

    private fun renderGap(node: DesignNode): String? {
        if (node.layout.rowGap != null || node.layout.columnGap != null) return null // → renderGapAxes
        return when (val gap = node.layout.gap) {
            is DesignGap.Auto -> "gap auto"
            is DesignGap.Fixed -> {
                if ((gap.value as? Bindable.Value)?.value == 0.0) null else numberToken(gap.value)?.let { "gap $it" }
            }
        }
    }

    private fun renderPadding(node: DesignNode): String? {
        val layout = node.layout
        val logical = layout.paddingLogical
        // Prefer the direction-aware form; fall back to physical `padding` (an ir-authored node has
        // no logical insets) mapped LTR — blockStart←top, inlineEnd←right, blockEnd←bottom, inlineStart←left.
        val blockStart = logical?.blockStart ?: layout.padding.top.takeIf { logical == null }
        val inlineEnd = logical?.inlineEnd ?: layout.padding.right.takeIf { logical == null }
        val blockEnd = logical?.blockEnd ?: layout.padding.bottom.takeIf { logical == null }
        val inlineStart = logical?.inlineStart ?: layout.padding.left.takeIf { logical == null }
        if (blockStart == null || inlineEnd == null || blockEnd == null || inlineStart == null) return null
        fun token(value: Bindable<Double>): String? = numberToken(value)
        return when {
            blockStart == inlineEnd && inlineEnd == blockEnd && blockEnd == inlineStart ->
                if ((blockStart as? Bindable.Value)?.value == 0.0) null else "padding ${token(blockStart) ?: return null}"
            blockStart == blockEnd && inlineStart == inlineEnd ->
                "padding ${token(blockStart) ?: return null} ${token(inlineEnd) ?: return null}"
            else ->
                "padding ${token(blockStart) ?: return null} ${token(inlineEnd) ?: return null} " +
                    "${token(blockEnd) ?: return null} ${token(inlineStart) ?: return null}"
        }
    }

    private fun renderFill(node: DesignNode): String? {
        val fills = node.fills?.takeIf { it.isNotEmpty() } ?: return null
        // A stack of paints (IR order), each its own phrase: color / gradient / image / video.
        val phrases = fills.map { renderPaint(it) ?: return null }
        return phrases.joinToString(" ")
    }

    private fun renderPaint(paint: DesignPaint): String? {
        return when (paint) {
        is DesignPaint.Solid -> {
            val token = solidPaintToken(paint.color) ?: return null
            val props = fillPropsWords(paint.opacity, paint.blendMode, paint.visible)
            if (props.isEmpty()) "color $token" else "color (" + (listOf(token) + props).joinToString(" ") + ")"
        }
        is DesignPaint.Gradient -> {
            val stops = paint.stops.map { s ->
                val color = colorToken(s.color) ?: return null
                "($color at ${num(s.position)})"
            }
            val inner = buildList {
                add(gradientWord(paint.gradientType))
                if (paint.from != DesignPoint(0.0, 0.0)) add("from (${num(paint.from.x.orZero)} ${num(paint.from.y.orZero)})")
                if (paint.to != DesignPoint(0.0, 1.0)) add("to (${num(paint.to.x.orZero)} ${num(paint.to.y.orZero)})")
                if (stops.isNotEmpty()) add("stops " + stops.joinToString(" "))
                addAll(fillPropsWords(paint.opacity, paint.blendMode, paint.visible))
            }
            "gradient (" + inner.joinToString(" ") + ")"
        }
        is DesignPaint.Image -> {
            val inner = buildList {
                add("asset ${quoteText(paint.assetId)}")
                fillModeWord(paint.scaleMode)?.let { add(it) }
                paint.focalPoint?.let { add("focus (${numberToken(it.x) ?: return null} ${numberToken(it.y) ?: return null})") }
                if (paint.replaceable) add("replaceable")
                addAll(fillPropsWords(paint.opacity, paint.blendMode, paint.visible))
            }
            "image (" + inner.joinToString(" ") + ")"
        }
        is DesignPaint.Video -> {
            val inner = buildList {
                add("asset ${quoteText(paint.assetId)}")
                fillModeWord(paint.scaleMode)?.let { add(it) }
                paint.focalPoint?.let { add("focus (${numberToken(it.x) ?: return null} ${numberToken(it.y) ?: return null})") }
                if (paint.posterAssetId.isNotEmpty()) add("poster ${quoteText(paint.posterAssetId)}")
                if (paint.autoplay) add("autoplay")
                if (paint.loop) add("loop")
                if (!paint.muted) add("muted no")
                addAll(fillPropsWords(paint.opacity, paint.blendMode, paint.visible))
            }
            "video (" + inner.joinToString(" ") + ")"
        }
        is DesignPaint.Unknown -> null
        }
    }

    private fun fillPropsWords(
        opacity: Bindable<Double>,
        blendMode: String,
        visible: Bindable<Boolean>,
    ): List<String> = buildList {
        opacity.literalOrNull()?.takeIf { it != 1.0 }?.let { add("opacity ${num(it)}") }
        if (blendMode != "normal") add("blend $blendMode")
        if (visible.literalOrNull() == false) add("visible no")
    }

    private fun gradientWord(kind: GradientKind): String = when (kind) {
        GradientKind.Linear -> "linear"
        GradientKind.Radial -> "radial"
        GradientKind.Angular -> "angular"
        GradientKind.Diamond -> "diamond"
    }

    private fun fillModeWord(mode: ImageScaleMode): String? = when (mode) {
        ImageScaleMode.Fill -> null
        ImageScaleMode.Fit -> "fit"
        ImageScaleMode.Crop -> "crop"
        ImageScaleMode.Tile -> "tile"
        ImageScaleMode.Stretch -> "stretch"
    }

    private fun renderStroke(node: DesignNode): String? {
        val strokes = node.strokes ?: return null
        if (strokes.paints.isEmpty()) return null
        // The flat `stroke #hex [N] [align]` form only fits a single plain solid with default
        // paint props; a paint stack, non-solid paint, per-paint props, dash/cap/join or
        // per-side weight uses the record form.
        val trivial = strokes.paints.size == 1 && strokes.dashPattern.isEmpty() &&
            strokes.cap == "butt" && strokes.join == "miter" && strokes.weightPerSide == null &&
            isPlainSolid(strokes.paints[0])
        if (trivial) {
            val paint = strokes.paints[0] as DesignPaint.Solid
            val token = colorToken(paint.color) ?: return renderStrokeRecord(strokes)
            val parts = mutableListOf("stroke", token)
            // Flat stroke alignment follows the optional numeric weight. Keep the default `1`
            // when an alignment token follows, otherwise `stroke #hex center` is parsed as a bad
            // numeric weight and silently falls back to Inside on round-trip.
            strokes.weight.literalOrNull()
                ?.takeIf { it != 1.0 || strokes.align != StrokeAlign.Inside }
                ?.let { parts += num(it) }
            when (strokes.align) {
                StrokeAlign.Inside -> {}
                StrokeAlign.Outside -> parts += "outside"
                StrokeAlign.Center -> parts += "center"
            }
            return parts.joinToString(" ")
        }
        return renderStrokeRecord(strokes)
    }

    /** A single-solid paint at every default (opacity 1, blend normal, visible) — the flat form. */
    private fun isPlainSolid(paint: DesignPaint): Boolean {
        val solid = paint as? DesignPaint.Solid ?: return false
        return solid.opacity.literalOrNull() == 1.0 && solid.blendMode == "normal" &&
            solid.visible.literalOrNull() != false
    }

    private fun renderStrokeRecord(strokes: DesignStrokes): String? {
        // Each layer is a full fill-style paint phrase (color/gradient/image/video with props).
        val layers = strokes.paints.map { renderPaint(it) ?: return null }
        val perSide = strokes.weightPerSide?.let { perSideClause(it) ?: return null }
        val parts = buildList {
            addAll(layers)
            strokes.weight.literalOrNull()?.takeIf { it != 1.0 }?.let { add("weight ${num(it)}") }
            perSide?.let { add(it) }
            when (strokes.align) {
                StrokeAlign.Inside -> {}
                StrokeAlign.Outside -> add("align outside")
                StrokeAlign.Center -> add("align center")
            }
            if (strokes.dashPattern.isNotEmpty()) add("dash (" + strokes.dashPattern.joinToString(" ") { num(it) } + ")")
            if (strokes.cap != "butt") add("cap ${strokes.cap}")
            if (strokes.join != "miter") add("join ${strokes.join}")
        }
        return "stroke (" + parts.joinToString(" ") + ")"
    }

    /** `weight-per-side (T R B L)` from literal insets; null when any side is a non-literal binding. */
    private fun perSideClause(insets: DesignInsets): String? {
        val top = insets.top.literalOrNull() ?: return null
        val right = insets.right.literalOrNull() ?: return null
        val bottom = insets.bottom.literalOrNull() ?: return null
        val left = insets.left.literalOrNull() ?: return null
        return "weight-per-side (${num(top)} ${num(right)} ${num(bottom)} ${num(left)})"
    }

    private fun renderEffects(node: DesignNode): String? {
        if (node.effects.isEmpty()) return null
        val bodies = node.effects.map { effectBody(it) ?: return null }
        return bodies.joinToString(" ") { "effect ($it)" }
    }

    private fun effectBody(effect: DesignEffect): String? {
        if (effect.visible.literalOrNull() == false) return null
        return when (effect) {
            is DesignEffect.DropShadow ->
                "dropShadow" + (shadowTail(effect.color, effect.offset, effect.blur, effect.spread) ?: return null)
            is DesignEffect.InnerShadow ->
                "innerShadow" + (shadowTail(effect.color, effect.offset, effect.blur, effect.spread) ?: return null)
            is DesignEffect.LayerBlur -> "layerBlur ${numberToken(effect.radius) ?: return null}"
            is DesignEffect.BackgroundBlur -> "backgroundBlur ${numberToken(effect.radius) ?: return null}"
            is DesignEffect.Unknown -> effect.rawType
        }
    }

    private fun shadowTail(
        color: Bindable<DesignColor>,
        offset: DesignPoint,
        blur: Bindable<Double>,
        spread: Bindable<Double>,
    ): String? {
        val colorTok = colorToken(color) ?: return null
        val parts = buildList {
            add("color $colorTok")
            // A ref axis has no literal (`literalOrNull() == null`), so it emits even though `orZero` is 0.
            if (offset.x.literalOrNull() != 0.0 || offset.y.literalOrNull() != 0.0) {
                add("offset (${numberToken(offset.x) ?: return null} ${numberToken(offset.y) ?: return null})")
            }
            if (blur.literalOrNull() != 0.0) add("blur ${numberToken(blur) ?: return null}")
            if (spread.literalOrNull() != 0.0) add("spread ${numberToken(spread) ?: return null}")
        }
        return " " + parts.joinToString(" ")
    }

    private fun renderRadius(node: DesignNode): String? {
        val radius = node.cornerRadius ?: return null
        val tl = radius.topLeft
        val tr = radius.topRight
        val br = radius.bottomRight
        val bl = radius.bottomLeft
        fun token(value: Bindable<Double>): String? = numberToken(value)
        return if (tl == tr && tr == br && br == bl) {
            if ((tl as? Bindable.Value)?.value == 0.0) null else "radius ${token(tl) ?: return null}"
        } else {
            "radius (${token(tl) ?: return null} ${token(tr) ?: return null} " +
                "${token(br) ?: return null} ${token(bl) ?: return null})"
        }
    }

    private fun numberToken(value: Bindable<Double>): String? = when (value) {
        is Bindable.Value -> num(value.value)
        is Bindable.VarRef -> "\$${value.id}"
        is Bindable.DataRef -> "{{${value.expression.raw}}}"
        is Bindable.PropRef -> "\$prop.${value.name}"
    }

    /** Integer counterpart of [numberToken]: a literal count, or `$id`/`{{expr}}`/`$prop.x` for refs. */
    private fun intToken(value: Bindable<Int>): String? = when (value) {
        is Bindable.Value -> value.value.toString()
        is Bindable.VarRef -> "\$${value.id}"
        is Bindable.DataRef -> "{{${value.expression.raw}}}"
        is Bindable.PropRef -> "\$prop.${value.name}"
    }

    private fun renderCornerSmoothing(node: DesignNode): String? {
        val smoothing = node.cornerRadius?.smoothing ?: return null
        return if (smoothing == 0.0) null else "smoothing ${num(smoothing)}"
    }

    private fun renderBlend(node: DesignNode): String? =
        if (node.blendMode == "normal") null else "blend ${node.blendMode}"

    private fun renderStyleRefs(node: DesignNode): String? {
        val parts = buildList {
            node.fillStyleId.takeIf { it.isNotEmpty() }?.let { add("fill $it") }
            node.strokeStyleId.takeIf { it.isNotEmpty() }?.let { add("stroke $it") }
            (node.kind as? DesignNodeKind.Text)?.textStyleId?.takeIf { it.isNotEmpty() }?.let { add("text $it") }
            node.effectStyleId.takeIf { it.isNotEmpty() }?.let { add("effect $it") }
            node.gridStyleId.takeIf { it.isNotEmpty() }?.let { add("grid $it") }
        }
        return if (parts.isEmpty()) null else "styles (${parts.joinToString(" ")})"
    }

    private fun renderOpacity(node: DesignNode): String? = when (val o = node.opacity) {
        is Bindable.Value -> if (o.value == 1.0) null else "opacity ${num(o.value)}"
        is Bindable.VarRef -> "opacity \$${o.id}"
        is Bindable.DataRef -> "opacity {{${o.expression.raw}}}"
        is Bindable.PropRef -> "opacity \$prop.${o.name}"
    }

    private fun renderAlign(node: DesignNode): String? {
        val horizontal = node.constraints.horizontal
        val vertical = node.constraints.vertical
        return when {
            horizontal == HorizontalConstraint.Center && vertical == VerticalConstraint.Center -> "align center"
            horizontal == HorizontalConstraint.Left && vertical == VerticalConstraint.Bottom -> "align bottom"
            horizontal == HorizontalConstraint.Right && vertical == VerticalConstraint.Top -> "align right"
            horizontal == HorizontalConstraint.Left && vertical == VerticalConstraint.Top -> null
            else -> null // richer per-axis constraints land in a later phase
        }
    }

    private fun renderFontSize(node: DesignNode): String? {
        val text = node.kind as? DesignNodeKind.Text ?: return null
        val size = text.textStyle?.fontSize?.literalOrNull() ?: return null
        return "size ${num(size)}"
    }

    private fun renderTextKey(node: DesignNode): String? {
        val text = node.kind as? DesignNodeKind.Text ?: return null
        return text.content?.key?.takeIf { it.isNotEmpty() }?.let { "key $it" }
    }

    private fun renderFontWeight(node: DesignNode): String? {
        val text = node.kind as? DesignNodeKind.Text ?: return null
        val weight = text.textStyle?.fontWeight?.literalOrNull() ?: return null
        return when (weight) {
            700.0 -> "bold"
            600.0 -> "semibold"
            300.0 -> "thin"
            else -> "weight ${num(weight)}"
        }
    }

    private fun renderFontFamily(node: DesignNode): String? =
        textStyleOf(node)?.fontFamily?.takeIf { it.isNotEmpty() }?.let { "font ${quoteText(it)}" }

    private fun renderLineHeight(node: DesignNode): String? =
        textStyleOf(node)?.lineHeight?.let { "line-height ${unit(it)}" }

    private fun renderTracking(node: DesignNode): String? =
        textStyleOf(node)?.letterSpacing?.let { "tracking ${unit(it)}" }

    private fun renderParagraphSpacing(node: DesignNode): String? =
        textStyleOf(node)?.paragraphSpacing?.let { "paragraph-spacing ${num(it)}" }

    private fun renderTextAlign(node: DesignNode): String? =
        textStyleOf(node)?.textAlignHorizontal?.let {
            val word = when (it) {
                TextAlignHorizontal.Left -> "left"
                TextAlignHorizontal.Center -> "center"
                TextAlignHorizontal.Right -> "right"
                TextAlignHorizontal.Justified -> "justified"
            }
            "text-align $word"
        }

    private fun renderTextValign(node: DesignNode): String? =
        textStyleOf(node)?.textAlignVertical?.let {
            val word = when (it) {
                TextAlignVertical.Top -> "top"
                TextAlignVertical.Center -> "center"
                TextAlignVertical.Bottom -> "bottom"
            }
            "text-valign $word"
        }

    private fun renderTextCase(node: DesignNode): String? = when (textStyleOf(node)?.textCase) {
        TextCase.Upper -> "case upper"
        TextCase.Lower -> "case lower"
        TextCase.Title -> "case title"
        else -> null
    }

    private fun renderTextDecoration(node: DesignNode): String? = when (textStyleOf(node)?.textDecoration) {
        TextDecorationKind.Underline -> "decoration underline"
        TextDecorationKind.Strikethrough -> "decoration strikethrough"
        else -> null
    }

    private fun renderFeatures(node: DesignNode): String? {
        val features = textStyleOf(node)?.fontFeatures?.takeIf { it.isNotEmpty() } ?: return null
        val groups = features.entries.sortedBy { it.key }
            .joinToString(" ") { "(${it.key} ${if (it.value) "on" else "off"})" }
        return "features $groups"
    }

    private fun renderAxes(node: DesignNode): String? {
        val axes = textStyleOf(node)?.variableAxes?.takeIf { it.isNotEmpty() } ?: return null
        val groups = axes.entries.sortedBy { it.key }.joinToString(" ") { "(${it.key} ${num(it.value)})" }
        return "axes $groups"
    }

    private fun renderTextStyleRef(node: DesignNode): String? =
        asText(node)?.textStyleId?.takeIf { it.isNotEmpty() }?.let { "text-style $$it" }

    private fun renderCharacters(node: DesignNode): String? {
        val characters = asText(node)?.characters ?: return null
        return when (characters) {
            is Bindable.Value -> null
            is Bindable.VarRef -> "characters \$${characters.id}"
            is Bindable.DataRef -> "characters {{${characters.expression.raw}}}"
            is Bindable.PropRef -> "characters \$prop.${characters.name}"
        }
    }

    private fun renderVariableModes(node: DesignNode): String? {
        if (node.variableModes.isEmpty()) return null
        val pairs = node.variableModes.entries.sortedBy { it.key }.joinToString(" ") { "${it.key} ${it.value}" }
        return "modes ($pairs)"
    }

    private fun renderAutoSize(node: DesignNode): String? = when (asText(node)?.autoResize) {
        TextAutoResize.Height -> "autosize height"
        TextAutoResize.WidthAndHeight -> "autosize both"
        else -> null
    }

    private fun renderTruncate(node: DesignNode): String? =
        asText(node)?.truncate?.takeIf { it.ellipsis }?.let { "truncate ${it.maxLines}" }

    private fun renderMaxLines(node: DesignNode): String? =
        asText(node)?.truncate?.takeIf { !it.ellipsis }?.let { "maxLines ${it.maxLines}" }

    private fun renderLinks(node: DesignNode): String? {
        val links = asText(node)?.links?.takeIf { it.isNotEmpty() } ?: return null
        return links.sortedWith(compareBy({ it.start }, { it.end }))
            .joinToString(" ") { link ->
                val target = if (link.url.isNotEmpty()) "url ${quoteText(link.url)}" else "to ${link.nodeTarget}"
                "link (range (${link.start} ${link.end}) $target)"
            }
    }

    private fun renderSpans(node: DesignNode): String? {
        // Emit in IR list order (a faithful inverse of the parser/merger, which preserve authored
        // order). Sorting here would reorder unsorted authored spans on round-trip and flip overlap
        // precedence — the IR list order is load-bearing for both fidelity and write-back veto.
        val ranges = asText(node)?.styleRanges?.takeIf { it.isNotEmpty() } ?: return null
        val rendered = ranges.map { range ->
            val style = range.style
            // CNL currently exposes the inline properties the canvas inspector edits directly.
            // Refuse a lossy sentence for deeper range properties; the write-back fidelity gate
            // will keep the source/document transaction atomic until their grammar lands too.
            if (
                style.copy(fontFamily = null, fontWeight = null, italic = null, fontSize = null) !=
                DesignTextStyle()
            ) return null
            val parts = buildList {
                add("range (${range.start} ${range.end})")
                range.styleRef.takeIf { it.isNotEmpty() }?.let { add("style $it") }
                style.fontFamily?.let { add("font ${quoteText(it)}") }
                style.fontWeight?.let { add("weight ${numberToken(it) ?: return null}") }
                style.italic?.let { add("italic ${if (it) "yes" else "no"}") }
                style.fontSize?.let { add("size ${numberToken(it) ?: return null}") }
                range.fills?.let { fills ->
                    if (fills.isEmpty()) {
                        add("fills none")
                    } else {
                        val paints = fills.map { renderPaint(it) ?: return null }
                        add("fills (${paints.joinToString(" ")})")
                    }
                }
            }
            if (parts.size == 1) return null
            "span (${parts.joinToString(" ")})"
        }
        return rendered.joinToString(" ")
    }

    private fun renderListSettings(node: DesignNode): String? {
        val list = asText(node)?.list?.takeIf { it.type != TextListType.None || it.indent != 0 } ?: return null
        val type = when (list.type) {
            TextListType.Bullet -> "bullet"
            TextListType.Ordered -> "ordered"
            TextListType.None -> "none"
        }
        return if (list.indent == 0) "list ($type)" else "list ($type indent ${list.indent})"
    }

    // --- components (instance side) ---

    private fun asInstance(node: DesignNode) = node.kind as? DesignNodeKind.Instance

    private fun renderComponentRef(node: DesignNode): String? {
        val instance = asInstance(node) ?: return null
        // A data-bound / var-ref componentId is not reader-expressible (`ref` is a plain String).
        val ref = instance.componentId.literalOrNull()?.takeIf { it.isNotEmpty() } ?: return null
        return "of $ref"
    }

    private fun renderLibraryRef(node: DesignNode): String? =
        asInstance(node)?.libraryRef?.takeIf { it.isNotEmpty() }?.let { "library $it" }

    private fun renderVariant(node: DesignNode): String? {
        val variant = asInstance(node)?.variant?.takeIf { it.isNotEmpty() } ?: return null
        return "variant (" + variantWords(variant) + ")"
    }

    private fun renderProps(node: DesignNode): String? {
        val instance = asInstance(node) ?: return null
        return propsPhrase(instance.props)
    }

    private fun renderDetach(node: DesignNode): String? =
        if (asInstance(node)?.detach == true) "detach" else null

    private fun renderResetOverrides(node: DesignNode): String? =
        if (asInstance(node)?.resetOverrides == true) "reset" else null

    private fun renderSlotOverrides(node: DesignNode): String? {
        val instance = asInstance(node) ?: return null
        val phrases = instance.overrides.mapNotNull { override ->
            val fills = override.slotContent ?: return@mapNotNull null
            val slotName = override.target.firstOrNull() ?: return@mapNotNull null
            val fillPhrases = fills.map { slotFillPhrase(it) ?: return null }
            "slot $slotName " + fillPhrases.joinToString(" ")
        }
        return phrases.takeIf { it.isNotEmpty() }?.joinToString(" ")
    }

    private fun renderNestedOverrides(node: DesignNode): String? {
        val instance = asInstance(node) ?: return null
        val phrases = instance.overrides.mapNotNull { override ->
            // Slot fills render via `slot`; property-group overrides (with their variant/props)
            // render via `override` — this phrase covers pure variant/props nested overrides.
            if (override.slotContent != null || override.hasSetGroups()) return@mapNotNull null
            val variant = override.variant
            val props = override.props
            if (variant.isNullOrEmpty() && props.isNullOrEmpty()) return@mapNotNull null
            val target = override.target.joinToString("/")
            val propsPart = if (props.isNullOrEmpty()) null else (propsPhrase(props) ?: return null)
            val inner = buildList {
                if (!variant.isNullOrEmpty()) add("variant (" + variantWords(variant) + ")")
                propsPart?.let { add(it) }
            }
            "nested $target (" + inner.joinToString(" ") + ")"
        }
        return phrases.takeIf { it.isNotEmpty() }?.joinToString(" ")
    }

    private fun InstanceOverride.hasSetGroups(): Boolean =
        fills != null || strokes != null || opacity != null || visible != null ||
            characters != null || textStyle != null || cornerRadius != null

    /** The property-group kinds an `override ( … )` group may carry, in canonical order. */
    private val overrideBucketKinds = setOf(
        CnlPropertyKind.Fill, CnlPropertyKind.FillComplex,
        CnlPropertyKind.Stroke, CnlPropertyKind.StrokeComplex,
        CnlPropertyKind.Radius, CnlPropertyKind.Opacity, CnlPropertyKind.Visible, CnlPropertyKind.Characters,
        CnlPropertyKind.FontFamily, CnlPropertyKind.FontSize, CnlPropertyKind.FontWeight,
        CnlPropertyKind.LineHeight, CnlPropertyKind.Tracking, CnlPropertyKind.ParagraphSpacing,
        CnlPropertyKind.TextAlign, CnlPropertyKind.TextValign, CnlPropertyKind.TextCase,
        CnlPropertyKind.TextDecoration, CnlPropertyKind.Features, CnlPropertyKind.Axes,
    )
    private val overrideBucketDescriptors by lazy {
        descriptors.filter { it.kind in overrideBucketKinds }.sortedBy { it.order }
    }

    /** `override <target> ( … )` — one phrase per [InstanceOverride] with property groups set. */
    private fun renderSetOverrides(node: DesignNode): String? {
        val instance = asInstance(node) ?: return null
        val phrases = instance.overrides.mapNotNull { override ->
            if (override.slotContent != null || !override.hasSetGroups()) return@mapNotNull null
            val target = override.target.joinToString("/").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val proxy = overrideProxy(override)
            val inner = mutableListOf<String>()
            overrideBucketDescriptors.forEach { descriptor -> descriptor.render(proxy)?.let { inner += it } }
            // Values the node renderers treat as defaults and skip, but an override states explicitly.
            if ((override.visible as? Bindable.Value)?.value == true) inner += "visible yes"
            if ((override.opacity as? Bindable.Value)?.value == 1.0) inner += "opacity 1"
            (override.characters as? Bindable.Value)?.let { inner += "characters ${quoteText(it.value)}" }
            if (override.cornerRadius != null && renderRadius(proxy) == null) inner += "radius 0"
            override.variant?.takeIf { it.isNotEmpty() }?.let { inner += "variant (" + variantWords(it) + ")" }
            override.props?.takeIf { it.isNotEmpty() }?.let { inner += propsPhrase(it) ?: return null }
            // Empty groups (e.g. `fills: []`) have no phrase form: the whole sentence is unexpressible.
            if (inner.isEmpty()) return null
            "override $target (" + inner.joinToString(" ") + ")"
        }
        return phrases.takeIf { it.isNotEmpty() }?.joinToString(" ")
    }

    /** A synthetic node carrying just the override's property groups, so bucket renderers apply. */
    private fun overrideProxy(override: InstanceOverride): DesignNode {
        val textish = override.characters != null || override.textStyle != null
        val kind = if (textish) {
            DesignNodeKind.Text(characters = override.characters ?: "".bindable(), textStyle = override.textStyle)
        } else {
            DesignNodeKind.Frame
        }
        return DesignNode(
            id = "",
            type = if (textish) "text" else "frame",
            kind = kind,
            fills = override.fills,
            strokes = override.strokes,
            opacity = override.opacity ?: 1.0.bindable(),
            visible = override.visible ?: true.bindable(),
            cornerRadius = override.cornerRadius,
        )
    }

    private fun slotFillPhrase(fill: DesignNode): String? {
        val instance = fill.kind as? DesignNodeKind.Instance ?: return null
        val ref = instance.componentId.literalOrNull()?.takeIf { it.isNotEmpty() } ?: return null
        val propsPart = if (instance.props.isEmpty()) null else (propsPhrase(instance.props) ?: return null)
        val parts = buildList {
            add(ref)
            if (instance.variant.isNotEmpty()) add("variant (" + variantWords(instance.variant) + ")")
            propsPart?.let { add(it) }
        }
        return "(" + parts.joinToString(" ") + ")"
    }

    /** Canonical (key-sorted) `axis value …` words for a variant selection. */
    private fun variantWords(variant: Map<String, String>): String =
        variant.entries.sortedBy { it.key }.joinToString(" ") { "${it.key} ${it.value}" }

    /** `props ( … )` in canonical (key-sorted) order, or null when empty / not expressible. */
    private fun propsPhrase(props: Map<String, PropValue>): String? {
        if (props.isEmpty()) return null
        val words = props.entries.sortedBy { it.key }.map { propWord(it.key, it.value) ?: return null }
        return "props (" + words.joinToString(" ") + ")"
    }

    private fun propWord(name: String, value: PropValue): String? = when (value) {
        is PropValue.Text -> "$name ${quoteText(value.value)}"
        is PropValue.Bool -> "$name ${value.value}"
        is PropValue.Number -> "$name ${num(value.value)}"
        is PropValue.Data -> "$name {{${value.expression.raw}}}"
        is PropValue.Reference -> "$name (swap ${value.value})"
        is PropValue.Content -> "$name (text ${quoteText(value.content.defaultText)} key ${value.content.key})"
        is PropValue.SlotContent -> null // authored via `slot`, not inline props
    }

    // --- P6 renderers (media node / shape params / vector / boolean / mask) ---

    private fun renderMedia(node: DesignNode): String? {
        val m = (node.kind as? DesignNodeKind.Media)?.media ?: return null
        val inner = buildList {
            mediaIdWord("asset", m.assetId)?.let { add(it) }
            if (m.kind == MediaKind.Video) add("video")
            fillModeWord(m.fillMode)?.let { add(it) }
            m.focalPoint?.let { fp ->
                add(
                    if (fp.x.literalOrNull() == 0.5 && fp.y.literalOrNull() == 0.5) "focus center"
                    else "focus (${numberToken(fp.x) ?: return null} ${numberToken(fp.y) ?: return null})",
                )
            }
            m.alt?.let { alt -> if (alt.key.isEmpty() && alt.defaultText.isNotEmpty()) add("alt ${quoteText(alt.defaultText)}") }
            m.opacity.literalOrNull()?.takeIf { it != 1.0 }?.let { add("opacity ${num(it)}") }
            if (m.blendMode != "normal") add("blend ${m.blendMode}")
            mediaIdWord("poster", m.posterAssetId)?.let { add(it) }
            if (m.autoplay) add("autoplay")
            if (m.loop) add("loop")
            if (m.replaceable) add("replaceable")
            if (!m.muted) add("unmuted")
        }
        if (inner.isEmpty()) return null // a bare, all-default Media node emits just `Image`
        return "media (" + inner.joinToString(" ") + ")"
    }

    /** `asset <id>` / `poster <id>` — a literal id, `$var`, or `{{expr}}`; empty literal is omitted. */
    private fun mediaIdWord(word: String, value: Bindable<String>): String? {
        val literal = value.literalOrNull()
        if (literal != null) return if (literal.isEmpty()) null else "$word $literal"
        return "$word ${stringToken(value)}"
    }

    private fun stringToken(value: Bindable<String>): String = when (value) {
        is Bindable.Value -> value.value
        is Bindable.VarRef -> "\$${value.id}"
        is Bindable.DataRef -> "{{${value.expression.raw}}}"
        is Bindable.PropRef -> "\$prop.${value.name}"
    }

    private fun renderShapePoints(node: DesignNode): String? =
        (node.kind as? DesignNodeKind.Shape)?.pointCount?.let { "points $it" }

    private fun renderShapeInner(node: DesignNode): String? =
        (node.kind as? DesignNodeKind.Shape)?.innerRadius?.let { "inner ${num(it)}" }

    /** `arc (start sweep)` — emitted whenever either arc endpoint is authored (ellipse pie/donut). */
    private fun renderShapeArc(node: DesignNode): String? {
        val shape = node.kind as? DesignNodeKind.Shape ?: return null
        if (shape.arcStartDeg == null && shape.arcSweepDeg == null) return null
        return "arc (${num(shape.arcStartDeg ?: 0.0)} ${num(shape.arcSweepDeg ?: 0.0)})"
    }

    private fun renderViewBox(node: DesignNode): String? {
        val vb = (node.kind as? DesignNodeKind.Shape)?.viewBox ?: return null
        return "viewbox (${num(vb.x)} ${num(vb.y)} ${num(vb.width)} ${num(vb.height)})"
    }

    private fun renderIconRef(node: DesignNode): String? =
        (node.kind as? DesignNodeKind.Shape)?.iconRef?.takeIf { it.isNotEmpty() }?.let { "icon $it" }

    private fun renderPathRef(node: DesignNode): String? =
        (node.kind as? DesignNodeKind.Shape)?.pathRef?.takeIf { it.isNotEmpty() }?.let { "svg $it" }

    private fun renderVectorPaths(node: DesignNode): String? {
        val paths = (node.kind as? DesignNodeKind.Shape)?.paths?.takeIf { it.isNotEmpty() } ?: return null
        return paths.joinToString(" ") { p -> "path ${quoteText(p.d)}" + if (p.windingRule == "evenodd") " evenodd" else "" }
    }

    private fun renderNetwork(node: DesignNode): String? {
        val shape = node.kind as? DesignNodeKind.Shape ?: return null
        val n = shape.network?.takeIf { it.isNotEmpty() } ?: return null
        val regionFills = shape.regionFills
        val parts = buildList {
            n.vertices.forEach { v -> add(renderVertex(v)) }
            n.segments.forEach { s -> add("segment (${s.from} ${s.to})") }
            // A region fill that is not a plain solid (gradient/image, or a solid with per-fill
            // props) is a CNL gap → yield the whole descriptor so the node keeps its retained YAML.
            n.regions.forEachIndexed { index, r -> add(renderRegion(r, regionFills[index]) ?: return null) }
        }
        return "network (" + parts.joinToString(" ") + ")"
    }

    private fun renderVertex(v: VectorVertex): String {
        val parts = buildList {
            add(num(v.x))
            add(num(v.y))
            v.inHandle?.let { add("in (${num(it.dx)} ${num(it.dy)})") }
            v.outHandle?.let { add("out (${num(it.dx)} ${num(it.dy)})") }
            when (v.mirror) {
                HandleMirror.None -> {}
                HandleMirror.Angle -> add("mirror angle")
                HandleMirror.AngleAndLength -> add("mirror angleAndLength")
            }
            if (v.corner) add("corner")
            if (v.cornerRadius != 0.0) add("radius ${num(v.cornerRadius)}")
        }
        return "vertex (" + parts.joinToString(" ") + ")"
    }

    private fun renderRegion(r: VectorRegion, fills: List<DesignPaint>?): String? {
        val loops = r.loops.joinToString(" ") { l -> "(" + l.joinToString(" ") + ")" }
        val prefix = if (r.windingRule == "evenodd") "region evenodd" else "region"
        val fillPhrase = if (fills.isNullOrEmpty()) {
            ""
        } else {
            val tokens = fills.map { regionFillToken(it) ?: return null }
            " " + tokens.joinToString(" ") { "fill $it" }
        }
        return "$prefix loops $loops$fillPhrase"
    }

    /** A region fill token: `#hex` (solid value) or `$id` (solid var-ref); null for non-solid / prop-bearing. */
    private fun regionFillToken(paint: DesignPaint): String? {
        val solid = paint as? DesignPaint.Solid ?: return null
        if (solid.opacity.literalOrNull() != 1.0 || solid.blendMode != "normal" ||
            solid.visible.literalOrNull() == false
        ) {
            return null
        }
        return colorToken(solid.color)
    }

    private fun renderBooleanOp(node: DesignNode): String? {
        val op = (node.kind as? DesignNodeKind.BooleanOperation)?.operation ?: return null
        val word = when (op) {
            BooleanOperationKind.Union -> "union"
            BooleanOperationKind.Subtract -> "subtract"
            BooleanOperationKind.Intersect -> "intersect"
            BooleanOperationKind.Exclude -> "exclude"
        }
        return "boolean $word"
    }

    private fun renderMask(node: DesignNode): String? {
        val mk = node.mask ?: return null
        val typeWord = when (mk.type) {
            MaskType.Alpha -> "alpha"
            MaskType.Vector -> "vector"
            MaskType.Luminance -> "luminance"
        }
        val clips = mk.appliesTo.takeIf { it.isNotEmpty() }?.let { " clips (${it.joinToString(" ")})" }.orEmpty()
        val from = mk.source.takeIf { it.isNotEmpty() }?.let { " from $it" }.orEmpty()
        return "mask $typeWord$clips$from"
    }

    // --- interactions & motion (P7) ---

    /**
     * Whether [interaction] renders to a CNL phrase (i.e. survives structural re-emit). `false`
     * mirrors [renderInteraction] returning `null` — the interaction is not expressible in CNL and
     * a subtree carrying it must stay in-memory rather than persist a behavior-stripped section.
     */
    fun canRenderInteraction(interaction: DesignInteraction): Boolean = renderInteraction(interaction) != null

    private fun renderInteractions(node: DesignNode): String? {
        if (node.interactions.isEmpty()) return null
        // If ANY interaction is non-expressible (cubic-bezier easing, PropRef/DataRef value, unknown
        // action) the whole descriptor yields null so the node falls back to ir-splice / retained YAML.
        val phrases = node.interactions.map { renderInteraction(it) ?: return null }
        return phrases.joinToString(" ")
    }

    private fun renderInteraction(interaction: DesignInteraction): String? {
        val head = buildString {
            append(triggerWord(interaction.trigger))
            when (interaction.trigger) {
                InteractionTrigger.OnKey -> if (interaction.key.isNotEmpty()) append(" (${interaction.key})")
                InteractionTrigger.AfterDelay -> interaction.delayMs?.let { append(" (${num(it)})") }
                InteractionTrigger.OnVariableChange ->
                    if (interaction.variable.isNotEmpty()) append(" (${interaction.variable})")
                else -> {}
            }
        }
        val actions = interaction.actions.map { renderAction(it) ?: return null }
        return if (actions.isEmpty()) head else head + " " + actions.joinToString(" ")
    }

    private fun renderAction(action: DesignAction): String? {
        return when (action) {
            is DesignAction.Navigate -> {
                val transition = renderTransition(action.transition) ?: return null
                "navigate (${action.to})$transition"
            }
            is DesignAction.OpenOverlay -> {
                val overlay = renderOverlay(action.overlay) ?: return null
                val transition = renderTransition(action.transition) ?: return null
                "openOverlay (${action.destination})$overlay$transition"
            }
            is DesignAction.SwapOverlay -> {
                val transition = renderTransition(action.transition) ?: return null
                "swapOverlay (${action.destination})$transition"
            }
            is DesignAction.CloseOverlay -> {
                val transition = renderTransition(action.transition) ?: return null
                "closeOverlay$transition"
            }
            DesignAction.Back -> "back"
            is DesignAction.OpenLink -> "openLink (${action.url})"
            is DesignAction.SetVariable -> {
                val value = renderBindableString(action.value) ?: return null
                "setVariable (${action.variable}) to ($value)"
            }
            is DesignAction.ChangeToVariant -> {
                val variant = action.variant.entries.sortedBy { it.key }.joinToString(" ") { "${it.key} ${it.value}" }
                "changeToVariant (${action.target}) variant ($variant)"
            }
            is DesignAction.ScrollTo ->
                "scrollTo (${action.target})" + if (!action.animated) " animated (false)" else ""
            is DesignAction.RunActionSet -> "runActionSet (${action.actionSetId})"
            is DesignAction.Unknown -> null // not round-trippable → ir-splice
        }
    }

    /** "" = no transition; " animate (…)" otherwise; null = not expressible (cubic-bezier easing). */
    private fun renderTransition(transition: DesignTransition?): String? {
        if (transition == null) return ""
        val easingPart: String? = when (val easing = transition.easing) {
            is DesignEasing.Named -> if (easing.kind == EasingKind.EaseOut) null else "easing ${easingWord(easing.kind)}"
            is DesignEasing.Spring -> buildList {
                add("easing spring")
                if (easing.mass != 1.0) add("mass ${num(easing.mass)}")
                if (easing.stiffness != 100.0) add("stiffness ${num(easing.stiffness)}")
                if (easing.damping != 15.0) add("damping ${num(easing.damping)}")
            }.joinToString(" ")
            is DesignEasing.CubicBezier -> return null // reader gap → ir-splice
        }
        val parts = buildList {
            if (transition.type != TransitionType.Instant) add("type ${transitionTypeWord(transition.type)}")
            easingPart?.let { add(it) }
            if (transition.durationMs != 300.0) add("duration ${num(transition.durationMs)}")
            if (transition.direction != TransitionDirection.Left) add("direction ${directionWord(transition.direction)}")
        }
        return " animate (" + parts.joinToString(" ") + ")"
    }

    /** "" = default overlay (omit); " overlay (…)" otherwise; null = unrenderable background → ir-splice. */
    private fun renderOverlay(overlay: OverlaySettings): String? {
        if (overlay == OverlaySettings()) return ""
        val background = overlay.background
        val backgroundToken = if (background != null) colorToken(background) ?: return null else null
        val parts = buildList {
            if (overlay.position != OverlayPosition.Center) add("position ${overlayPositionWord(overlay.position)}")
            overlay.offset?.let { add("offset (${num(it.x.orZero)} ${num(it.y.orZero)})") }
            if (!overlay.closeOnOutsideClick) add("closeOnOutside (false)")
            backgroundToken?.let { add("background $it") }
        }
        return " overlay (" + parts.joinToString(" ") + ")"
    }

    private fun renderBindableString(value: Bindable<String>): String? = when (value) {
        // Space/paren-bearing literals & expressions can't survive the whitespace tokenizer → ir-splice.
        is Bindable.Value -> value.value.takeIf { ' ' !in it && '(' !in it && ')' !in it }
        is Bindable.VarRef -> "$" + value.id
        is Bindable.DataRef -> ("{{" + value.expression.raw + "}}").takeIf { ' ' !in it }
        is Bindable.PropRef -> null
    }

    private fun renderMotion(node: DesignNode): String? {
        val motion = node.motion ?: return null
        val head = if (motion.ref.isEmpty()) "motion" else "motion (${motion.ref})"
        val fallback = motion.fallback ?: return if (motion.ref.isEmpty()) null else head
        // Frame property keys outside the known 5 are droppable by the emitter → ir-splice rather than lose them.
        if (fallback.frames.any { frame -> frame.properties.keys.any { it !in motionFrameOrder } }) return null
        val parts = buildList {
            if (fallback.durationMs != 0.0) add("duration ${num(fallback.durationMs)}")
            if (fallback.loop) add("loop")
            if (fallback.frames.isNotEmpty()) add("frames " + fallback.frames.joinToString(" ") { motionFrameWords(it) })
        }
        // A present-but-empty fallback is indistinguishable from null on re-parse → ir-splice.
        if (parts.isEmpty()) return null
        return "$head " + parts.joinToString(" ")
    }

    private val motionFrameOrder = listOf("opacity", "x", "y", "scale", "rotation")

    private fun motionFrameWords(frame: MotionFrame): String {
        val props = motionFrameOrder
            .filter { frame.properties.containsKey(it) }
            .joinToString("") { " $it ${num(frame.properties.getValue(it))}" }
        return "(${num(frame.at)}$props)"
    }

    private fun triggerWord(trigger: InteractionTrigger): String = when (trigger) {
        InteractionTrigger.OnClick -> "onClick"
        InteractionTrigger.OnHover -> "onHover"
        InteractionTrigger.OnPress -> "onPress"
        InteractionTrigger.OnDrag -> "onDrag"
        InteractionTrigger.OnKey -> "onKey"
        InteractionTrigger.AfterDelay -> "afterDelay"
        InteractionTrigger.WhileHovering -> "whileHovering"
        InteractionTrigger.WhilePressed -> "whilePressed"
        InteractionTrigger.OnVariableChange -> "onVariableChange"
    }

    private fun easingWord(kind: EasingKind): String = when (kind) {
        EasingKind.Linear -> "linear"
        EasingKind.EaseIn -> "easeIn"
        EasingKind.EaseOut -> "easeOut"
        EasingKind.EaseInOut -> "easeInOut"
        EasingKind.EaseInBack -> "easeInBack"
        EasingKind.EaseOutBack -> "easeOutBack"
    }

    private fun transitionTypeWord(type: TransitionType): String = when (type) {
        TransitionType.Instant -> "instant"
        TransitionType.Dissolve -> "dissolve"
        TransitionType.SmartAnimate -> "smartAnimate"
        TransitionType.MoveIn -> "moveIn"
        TransitionType.MoveOut -> "moveOut"
        TransitionType.Push -> "push"
        TransitionType.SlideIn -> "slideIn"
        TransitionType.SlideOut -> "slideOut"
    }

    private fun directionWord(direction: TransitionDirection): String = when (direction) {
        TransitionDirection.Left -> "left"
        TransitionDirection.Right -> "right"
        TransitionDirection.Top -> "top"
        TransitionDirection.Bottom -> "bottom"
    }

    private fun overlayPositionWord(position: OverlayPosition): String = when (position) {
        OverlayPosition.Center -> "center"
        OverlayPosition.TopLeft -> "topLeft"
        OverlayPosition.TopCenter -> "topCenter"
        OverlayPosition.TopRight -> "topRight"
        OverlayPosition.BottomLeft -> "bottomLeft"
        OverlayPosition.BottomCenter -> "bottomCenter"
        OverlayPosition.BottomRight -> "bottomRight"
        OverlayPosition.Manual -> "manual"
    }

    // --- P10 renderers (responsive variants + export settings) ---

    private fun renderExport(node: DesignNode): String? {
        if (node.exportSettings.isEmpty()) return null
        val settings = node.exportSettings.joinToString(" ") { s ->
            val inner = buildString {
                append(exportFormatWord(s.format))
                if (s.scale != 1.0) append(" at ${num(s.scale)}")
                if (s.suffix.isNotEmpty()) append(" ${quoteText(s.suffix)}")
            }
            "($inner)"
        }
        return "export $settings"
    }

    private fun exportFormatWord(format: ExportFormat): String = when (format) {
        ExportFormat.Png -> "png"
        ExportFormat.Jpg -> "jpg"
        ExportFormat.Svg -> "svg"
        ExportFormat.Pdf -> "pdf"
    }

    private fun renderResponsive(node: DesignNode): String? {
        if (node.responsive.isEmpty()) return null
        return node.responsive.joinToString(" ") { variant ->
            val selectors = responsiveSelectorPhrase(variant.selectors)
            val overrides = responsiveOverrides(variant.patch)
            if (overrides.isEmpty()) "when ($selectors)" else "when ($selectors) $overrides"
        }
    }

    /** Selectors emitted in ResponsiveDimension declaration order (canonicalized). */
    private fun responsiveSelectorPhrase(selectors: Map<ResponsiveDimension, String>): String =
        ResponsiveDimension.entries
            .filter { selectors.containsKey(it) }
            .joinToString(" ") { "${responsiveDimensionWord(it)} ${selectors.getValue(it)}" }

    private fun responsiveDimensionWord(dim: ResponsiveDimension): String = when (dim) {
        ResponsiveDimension.Breakpoint -> "breakpoint"
        ResponsiveDimension.DevicePreset -> "devicePreset"
        ResponsiveDimension.Platform -> "platform"
        ResponsiveDimension.Theme -> "theme"
        ResponsiveDimension.Density -> "density"
        ResponsiveDimension.Locale -> "locale"
        ResponsiveDimension.Direction -> "direction"
        ResponsiveDimension.Brand -> "brand"
        ResponsiveDimension.State -> "state"
    }

    /**
     * Applies the variant patch to a canonical probe (Text when a text-style override is present,
     * else Frame) and runs the base renderers in canonical order, GATED on the patch group being
     * non-null so unset families never leak probe defaults.
     */
    private fun responsiveOverrides(patch: DesignNodePatch): String {
        val base = if (patch.textStyle != null) {
            DesignNode(id = "", type = "text", kind = DesignNodeKind.Text())
        } else {
            DesignNode(id = "", type = "frame", kind = DesignNodeKind.Frame)
        }
        val probe = patch.appliedTo(base)
        val phrases = buildList {
            if (patch.size != null || patch.sizing != null) renderSize(probe)?.let { add(it) }
            if (patch.layout != null) {
                renderDirection(probe)?.let { add(it) }
                renderGap(probe)?.let { add(it) }
                renderGapAxes(probe)?.let { add(it) }
                renderPadding(probe)?.let { add(it) }
            }
            if (patch.fills != null) renderFill(probe)?.let { add(it) }
            if (patch.strokes != null) renderStroke(probe)?.let { add(it) }
            if (patch.cornerRadius != null) renderRadius(probe)?.let { add(it) }
            if (patch.opacity != null) renderOpacity(probe)?.let { add(it) }
            if (patch.textStyle != null) {
                renderFontSize(probe)?.let { add(it) }
                renderFontWeight(probe)?.let { add(it) }
            }
        }
        return phrases.joinToString(" ")
    }
}
