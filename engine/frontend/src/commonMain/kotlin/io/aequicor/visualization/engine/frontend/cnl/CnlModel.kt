package io.aequicor.visualization.engine.frontend.cnl

/**
 * Controlled-natural-language (CNL) model. A CNL element is one sentence describing a UI
 * node — a leading noun plus a sequence of `keyword value…` property phrases — e.g.
 * `Прямоугольник 120 на 15 цвет #00B843 радиус 15`. The same [CnlElement] feeds both the
 * compile path (desugar to typed patches) and surgical write-back (per-value source spans).
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
    Fill, Stroke, Radius, Opacity, Rotation,
    Padding, Gap, Direction, AlignParent, Position,
    FontSize, FontWeight, FontStyle,
}

/**
 * One property phrase. [values] are the parsed value tokens (with spans for write-back);
 * [keywordSpan] is the keyword phrase (null for keyword-less forms like `120 на 15`);
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
