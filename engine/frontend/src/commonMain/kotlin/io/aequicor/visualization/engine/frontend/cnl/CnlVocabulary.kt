package io.aequicor.visualization.engine.frontend.cnl

/**
 * CNL keyword tables — the single source of truth for the grammar's words. English is the
 * sole authored language: nouns map to node types, keywords to [CnlPropertyKind], enum words
 * to IR spellings. Extend the maps to grow the vocabulary; the parser reads them to understand
 * text and [CnlGrammar]/[CnlEmitter] read the canonical spellings to regenerate it.
 */
internal object CnlVocabulary {
    private fun shape(kind: String) = CnlNoun(nodeType = "shape", shapeKind = kind)
    private fun node(type: String) = CnlNoun(nodeType = type)

    /** Element nouns (lowercased) → node identity. */
    val nouns: Map<String, CnlNoun> = mapOf(
        "rect" to shape("rectangle"), "rectangle" to shape("rectangle"),
        "ellipse" to shape("ellipse"), "circle" to shape("ellipse"),
        "line" to shape("line"),
        "star" to shape("star"),
        "polygon" to shape("polygon"),
        "arrow" to shape("arrow"),
        "text" to node("text"), "label" to node("text"),
        "button" to CnlNoun("text", role = "button"),
        "frame" to node("frame"), "container" to node("frame"),
        "group" to node("group"),
        "section" to node("section"),
        "screen" to node("screen"),
        "image" to node("media"),
        "icon" to node("vector"), "vector" to node("vector"),
        "instance" to node("instance"),
    )

    /**
     * Property keyword phrases (lowercased) → kind. Multi-word phrases (e.g. `corner radius`)
     * are matched greedily longest-first by the parser.
     */
    val propertyKeywords: Map<String, CnlPropertyKind> = mapOf(
        "color" to CnlPropertyKind.Fill, "fill" to CnlPropertyKind.Fill,
        // Mid-sentence paint keywords (never at token[0], where they'd be nouns): each takes a `( … )` group.
        "gradient" to CnlPropertyKind.Fill, "image" to CnlPropertyKind.Fill, "video" to CnlPropertyKind.Fill,
        "stroke" to CnlPropertyKind.Stroke, "border" to CnlPropertyKind.Stroke,
        "effect" to CnlPropertyKind.Effect,
        "radius" to CnlPropertyKind.Radius, "corner radius" to CnlPropertyKind.Radius,
        "smoothing" to CnlPropertyKind.Smoothing,
        "blend" to CnlPropertyKind.Blend,
        "styles" to CnlPropertyKind.StyleRefs,
        "rotate" to CnlPropertyKind.Rotation, "rotation" to CnlPropertyKind.Rotation,
        "padding" to CnlPropertyKind.Padding,
        "gap" to CnlPropertyKind.Gap,
        "width" to CnlPropertyKind.Width,
        "height" to CnlPropertyKind.Height,
        "size" to CnlPropertyKind.Size,
        "position" to CnlPropertyKind.Position,
        "opacity" to CnlPropertyKind.Opacity,
        "visible" to CnlPropertyKind.Visible,
        "locked" to CnlPropertyKind.Locked,
        "modes" to CnlPropertyKind.VariableModes,
        "variablemodes" to CnlPropertyKind.VariableModes,
        "align" to CnlPropertyKind.AlignParent,
        "id" to CnlPropertyKind.Id,
        "name" to CnlPropertyKind.NodeName,
        // Typography-deep (Text nodes).
        "font" to CnlPropertyKind.FontFamily,
        "weight" to CnlPropertyKind.FontWeight,
        "line-height" to CnlPropertyKind.LineHeight,
        "tracking" to CnlPropertyKind.Tracking,
        "paragraph-spacing" to CnlPropertyKind.ParagraphSpacing,
        "text-align" to CnlPropertyKind.TextAlign,
        "text-valign" to CnlPropertyKind.TextValign,
        "case" to CnlPropertyKind.TextCase,
        "decoration" to CnlPropertyKind.TextDecoration,
        "features" to CnlPropertyKind.Features,
        "axes" to CnlPropertyKind.Axes,
        "autosize" to CnlPropertyKind.AutoSize,
        "truncate" to CnlPropertyKind.Truncate,
        "maxlines" to CnlPropertyKind.MaxLines,
        "key" to CnlPropertyKind.TextKey,
        "text-style" to CnlPropertyKind.TextStyleRef,
        "list" to CnlPropertyKind.ListSettings,
        "characters" to CnlPropertyKind.Characters,
        "link" to CnlPropertyKind.Link,
        // Layout-deep.
        "wrap" to CnlPropertyKind.Wrap,
        "clip" to CnlPropertyKind.Clip,
        "absolute" to CnlPropertyKind.Absolute,
        "distribute" to CnlPropertyKind.Distribute,
        "justify" to CnlPropertyKind.Distribute,
        "anchor" to CnlPropertyKind.Anchor,
        "constraints" to CnlPropertyKind.Constraints,
        "overflow" to CnlPropertyKind.Overflow,
        "scroll" to CnlPropertyKind.Scroll,
        "columns" to CnlPropertyKind.Columns,
        "rows" to CnlPropertyKind.Rows,
        "place" to CnlPropertyKind.Place,
        "guides" to CnlPropertyKind.Guides,
        "grids" to CnlPropertyKind.Grids,
        // Components. Group sub-words (axis names, swap/text/key/data, min/max/allow)
        // resolve locally inside the consumers, never here (group-scoping keystone).
        "of" to CnlPropertyKind.ComponentRef,
        "library" to CnlPropertyKind.LibraryRef,
        "variant" to CnlPropertyKind.Variant,
        "props" to CnlPropertyKind.Props,
        "detach" to CnlPropertyKind.Detach,
        "reset" to CnlPropertyKind.ResetOverrides,
        "slot" to CnlPropertyKind.SlotOverride,
        "nested" to CnlPropertyKind.NestedOverride,
        "component-name" to CnlPropertyKind.ComponentName,
        "componentname" to CnlPropertyKind.ComponentName,
        "set" to CnlPropertyKind.ComponentSet,
        "axis" to CnlPropertyKind.ComponentAxis,
        "prop" to CnlPropertyKind.ComponentPropDefinition,
        // Media / shape params / vector / mask (P6). Group-internal words (asset, focus, video, crop, vertex,
        // segment, region, in, out, mirror, corner, loops, evenodd, alpha, subtract, clips, from…) resolve
        // LOCALLY inside consumers — do NOT add them here. "icon" is ALSO a noun; that is fine (nouns are only
        // matched at token[0], keywords only mid-sentence).
        "media" to CnlPropertyKind.Media,
        "points" to CnlPropertyKind.ShapePoints,
        "inner" to CnlPropertyKind.ShapeInner,
        "viewbox" to CnlPropertyKind.ViewBox,
        "icon" to CnlPropertyKind.IconRef,
        "svg" to CnlPropertyKind.PathRef,
        "path" to CnlPropertyKind.VectorPaths,
        "network" to CnlPropertyKind.VectorNetwork,
        "boolean" to CnlPropertyKind.BooleanOp,
        "mask" to CnlPropertyKind.Mask,
        // --- interactions & motion (P7) ---
        // Triggers: each leads a SEPARATE interaction; the outer parseFrom loop re-enters consumeInteraction
        // per trigger. Action verbs and record sub-keywords (navigate/openOverlay/animate/overlay/to/variant/
        // animated/duration/loop/frames/easing/spring/mass/stiffness/damping/closeOnOutside/offset/background)
        // are deliberately NOT here — they resolve group-locally inside consumeInteraction/consumeAction.
        "onclick" to CnlPropertyKind.Interactions,
        "onhover" to CnlPropertyKind.Interactions,
        "onpress" to CnlPropertyKind.Interactions,
        "ondrag" to CnlPropertyKind.Interactions,
        "onkey" to CnlPropertyKind.Interactions,
        "afterdelay" to CnlPropertyKind.Interactions,
        "whilehovering" to CnlPropertyKind.Interactions,
        "whilepressed" to CnlPropertyKind.Interactions,
        "onvariablechange" to CnlPropertyKind.Interactions,
        "motion" to CnlPropertyKind.Motion,
        // P10 clause-initiating keywords (group sub-keywords like breakpoint/at/from/png stay LOCAL to the consumers).
        "when" to CnlPropertyKind.Responsive,
        "export" to CnlPropertyKind.Export,
        "note" to CnlPropertyKind.Annotation,
        "measure" to CnlPropertyKind.Measurement,
        "code" to CnlPropertyKind.CodeHint,
    )

    /** Longest keyword phrase (in words), so the parser tries 2-, 1-word matches. */
    val maxKeywordWords: Int = propertyKeywords.keys.maxOf { it.split(' ').size }

    /** Standalone direction words → `layout.mode` (no value token follows). */
    val directions: Map<String, String> = mapOf(
        "column" to "column",
        "row" to "row",
        "grid" to "grid",
        "free" to "none",
    )

    /** Standalone font-weight words → numeric weight. */
    val fontWeights: Map<String, Int> = mapOf(
        "bold" to 700, "semibold" to 600, "thin" to 300,
    )

    /** `align <dir>` → node constraint spelling. */
    val alignDirections: Map<String, Pair<String, String>> = mapOf(
        // word -> (axis, constraint value)
        "top" to ("vertical" to "top"),
        "bottom" to ("vertical" to "bottom"),
        "left" to ("horizontal" to "left"),
        "right" to ("horizontal" to "right"),
        "center" to ("both" to "center"),
    )

    /** Stroke alignment words after `stroke <color> [weight] <align>`. */
    val strokeAligns: Map<String, String> = mapOf(
        "inside" to "inside",
        "outside" to "outside",
        "center" to "center",
    )

    /** Size connectors in `<w> by <h>`. */
    val sizeConnectors: Set<String> = setOf("by", "x", "×", "*")

    /** Boolean words accepted inside `( … )` groups; canonical emit uses `no` (and omits `yes`). */
    val booleans: Map<String, Boolean> = mapOf(
        "yes" to true, "no" to false, "on" to true, "off" to false, "true" to true, "false" to false,
    )

    /** Degree markers in `<n> degrees`. */
    val degreeWords: Set<String> = setOf("°", "deg", "deg.", "degrees")
}
