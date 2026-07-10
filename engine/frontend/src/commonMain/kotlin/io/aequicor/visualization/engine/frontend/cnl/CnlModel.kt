package io.aequicor.visualization.engine.frontend.cnl

/**
 * Controlled-natural-language (CNL) model. A CNL element is one sentence describing a UI
 * node — a leading noun plus a sequence of `keyword value…` property phrases — e.g.
 * `Rectangle 120 by 15 color #00B843 radius 15`. The same [CnlElement] feeds both the
 * compile path (desugar to typed patches) and surgical write-back (per-value source spans),
 * and — inverted through [CnlGrammar] — the [CnlEmitter] that regenerates the sentence from IR.
 */

/** 1-based source span of a token on a single line; [endColumn] is exclusive. */
data class CnlSpan(val line: Int, val startColumn: Int, val endColumn: Int)

/** A parsed value token (number, `#hex`, `$token`, enum word, quoted text) with its span. */
data class CnlValue(val raw: String, val span: CnlSpan)

/** Element noun → node identity. [shapeKind] set for shapes; [role] e.g. "button". */
data class CnlNoun(val nodeType: String, val shapeKind: String? = null, val role: String? = null)

/** The property kinds the CNL grammar recognizes. */
enum class CnlPropertyKind {
    Size, Width, Height,
    Visible, Locked, VariableModes,
    Fill, Stroke, Radius, Opacity, Rotation,
    Padding, Gap, Direction, AlignParent, Position,
    FontSize, FontWeight,
    /** Explicit node id — authored only by the structural writer for id-stable inserts. */
    Id,
    /** Explicit non-visible layer name; visible text remains the sentence text literal. */
    NodeName,
    /** A `( … )`-group fill (gradient/image/video/solid-with-props) whose value is a ready YAML fragment. */
    FillComplex,
    /** Corner smoothing scalar (`smoothing N`). */
    Smoothing,
    /** Node blend mode (`blend <mode>`). */
    Blend,
    /** Shared style references (`styles (fill … text … effect … grid …)`). */
    StyleRefs,
    /** A `stroke ( … )` record (stack/dash/cap/join) pre-lowered to a `strokes:` YAML fragment. */
    StrokeComplex,
    /** An `effect ( … )` group pre-lowered to an `effects:` list item. */
    Effect,
    // Typography-deep (Text nodes).
    FontFamily, LineHeight, Tracking, ParagraphSpacing,
    TextAlign, TextValign, TextCase, TextDecoration,
    Features, Axes,
    AutoSize, Truncate, MaxLines, TextKey, TextStyleRef, ListSettings, Characters,
    /** A `link ( … )` rich-text span, pre-lowered to a `text.spans[]` item. */
    Link,
    // Layout-deep.
    Wrap, Clip, Absolute, Distribute, Anchor, Constraints, ContainerAlign,
    // Layout-deep P4b: grid tracks + placement + guides + grid overlays + overflow + scroll.
    Overflow, Scroll, Columns, Rows, Place, Guides, Grids,
    // Components: instance side plus definition-side name/set/axes/property declarations.
    ComponentRef, LibraryRef, Variant, Props, Detach, ResetOverrides, SlotOverride, NestedOverride,
    ComponentName, ComponentSet, ComponentAxis, ComponentPropDefinition,
    // P6: media record / shape point+inner / vector viewBox·iconRef·pathRef·paths·network / boolean op / mask block.
    Media, ShapePoints, ShapeInner, ViewBox, IconRef, PathRef, VectorPaths, VectorNetwork, BooleanOp, Mask,
    // P7: interaction triggers (one per trigger phrase) + motion attachment.
    Interactions, Motion,
    // P10: responsive `when (dim value) …` variants + `export (…)` settings + handoff annotations.
    Responsive,   // `when (dim value) …` trailing clause → responsive.variants
    Export,        // `export (fmt at N «suffix»)` / `export off` → export.settings
    Annotation,    // parse-only: `note «…»` → handoff.annotations (never rendered)
    Measurement,   // parse-only: `measure (…)` → handoff.measurements (never rendered)
    CodeHint,       // parse-only: `code (…)` → handoff.code (never rendered)
}

/**
 * One property phrase. [values] are the parsed value tokens (with spans for write-back);
 * [keywordSpan] is the keyword phrase (null for keyword-less forms like `120 by 15`);
 * [phraseSpan] covers the whole phrase (for append/remove during write-back).
 */
data class CnlProperty(
    val kind: CnlPropertyKind,
    val values: List<CnlValue>,
    val keywordSpan: CnlSpan?,
    val phraseSpan: CnlSpan,
)

/**
 * One parsed CNL sentence. [noun] is null for a heading property suffix (name-only
 * container); [textLiteral] is the `«…»`/`"…"` visible text of a text element.
 */
data class CnlElement(
    val noun: CnlNoun?,
    val textLiteral: CnlValue?,
    val properties: List<CnlProperty>,
    val span: CnlSpan,
) {
    val isEmpty: Boolean get() = noun == null && textLiteral == null && properties.isEmpty()
}
