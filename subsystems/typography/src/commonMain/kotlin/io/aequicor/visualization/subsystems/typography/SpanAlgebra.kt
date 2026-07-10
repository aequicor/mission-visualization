package io.aequicor.visualization.subsystems.typography

/** Fields of [TypographyStyle] plus fills, for mixed-selection reporting. */
enum class TypographyField {
    FontFamily, FontWeight, Italic, FontSize, LineHeight, LetterSpacing,
    ParagraphSpacing, ParagraphIndent, AlignHorizontal, AlignVertical,
    Case, Decoration, Features, Axes, Position, LeadingTrim,
    HangingPunctuation, HangingLists, Fills,
}

/** How a range edit changes fills. */
sealed interface FillsPatch {
    /** Leave range fills untouched. */
    data object Keep : FillsPatch

    /** Drop range fills so the base fill shows through. */
    data object Clear : FillsPatch

    data class Set(val fills: List<TextFill>) : FillsPatch
}

/**
 * Pure algebra over [StyleSpan] lists: normalization into disjoint runs, range
 * application with minimal output, and mixed-value queries for selection UIs.
 *
 * Overlapping input spans compose in list order (later spans override earlier ones),
 * matching how overlapping `SpanStyle`s stack in an `AnnotatedString`.
 */
object SpanAlgebra {

    /** A maximal run of uniform styling; [style] is the span override only (base not merged). */
    data class Run(
        val start: Int,
        val end: Int,
        val style: TypographyStyle,
        val fills: List<TextFill>?,
    )

    /**
     * Clamps spans to `[0, length)`, flattens overlaps into disjoint sorted spans
     * (later spans win field-by-field) and coalesces adjacent equal spans.
     * Spans that end up styling nothing are dropped.
     */
    fun normalize(spans: List<StyleSpan>, length: Int): List<StyleSpan> =
        runs(spans, length)
            .filter { it.style != TypographyStyle.EMPTY || it.fills != null }
            .map { StyleSpan(start = it.start, end = it.end, style = it.style, fills = it.fills) }

    /**
     * Disjoint runs covering every styled interval of `[0, length)`; unstyled gaps are
     * omitted. Use [runsCovering] when full coverage (including gaps) is needed.
     */
    fun runs(spans: List<StyleSpan>, length: Int): List<Run> {
        val clamped = spans.mapNotNull { span ->
            val start = span.start.coerceIn(0, length)
            val end = span.end.coerceIn(0, length)
            if (end <= start) null else span.copy(start = start, end = end)
        }
        if (clamped.isEmpty()) return emptyList()

        val cuts = buildList {
            clamped.forEach { add(it.start); add(it.end) }
        }.distinct().sorted()

        val raw = buildList {
            for (i in 0 until cuts.size - 1) {
                val start = cuts[i]
                val end = cuts[i + 1]
                val covering = clamped.filter { it.start <= start && it.end >= end }
                if (covering.isEmpty()) continue
                var style = TypographyStyle.EMPTY
                var fills: List<TextFill>? = null
                covering.forEach { span ->
                    style = style.mergedWith(span.style)
                    if (span.fills != null) fills = span.fills
                }
                add(Run(start = start, end = end, style = style, fills = fills))
            }
        }
        return coalesce(raw)
    }

    /** Disjoint runs covering all of `[0, length)`, gaps included as empty-override runs. */
    fun runsCovering(spans: List<StyleSpan>, length: Int): List<Run> {
        if (length <= 0) return emptyList()
        val styled = runs(spans, length)
        val result = mutableListOf<Run>()
        var cursor = 0
        styled.forEach { run ->
            if (run.start > cursor) {
                result += Run(cursor, run.start, TypographyStyle.EMPTY, null)
            }
            result += run
            cursor = run.end
        }
        if (cursor < length) result += Run(cursor, length, TypographyStyle.EMPTY, null)
        return result
    }

    /**
     * Applies [patch] (and [fillsPatch]) to `[start, end)`, returning a minimal disjoint
     * span list. Patched fields that become raw-equal to the corresponding explicit
     * [base] field are pruned from the override so spans stay minimal.
     */
    fun applyToRange(
        base: TypographyStyle,
        spans: List<StyleSpan>,
        length: Int,
        start: Int,
        end: Int,
        patch: TypographyStyle,
        fillsPatch: FillsPatch = FillsPatch.Keep,
    ): List<StyleSpan> {
        val from = start.coerceIn(0, length)
        val to = end.coerceIn(0, length)
        if (to <= from) return normalize(spans, length)

        val existing = runsCovering(spans, length)
        val pieces = mutableListOf<Run>()
        existing.forEach { run ->
            val cutPoints = listOf(run.start, run.end, from, to)
                .filter { it in run.start..run.end }
                .distinct()
                .sorted()
            for (i in 0 until cutPoints.size - 1) {
                val s = cutPoints[i]
                val e = cutPoints[i + 1]
                if (e <= s) continue
                val inside = s >= from && e <= to
                if (!inside) {
                    pieces += run.copy(start = s, end = e)
                } else {
                    val patched = run.style.mergedWith(patch).prunedAgainst(base)
                    val fills = when (fillsPatch) {
                        FillsPatch.Keep -> run.fills
                        FillsPatch.Clear -> null
                        is FillsPatch.Set -> fillsPatch.fills
                    }
                    pieces += Run(start = s, end = e, style = patched, fills = fills)
                }
            }
        }
        return coalesce(pieces)
            .filter { it.style != TypographyStyle.EMPTY || it.fills != null }
            .map { StyleSpan(start = it.start, end = it.end, style = it.style, fills = it.fills) }
    }

    /** Effective style at [offset]: base merged with any covering span overrides. */
    fun styleAt(
        base: TypographyStyle,
        spans: List<StyleSpan>,
        length: Int,
        offset: Int,
    ): TypographyStyle {
        val run = runs(spans, length).firstOrNull { offset >= it.start && offset < it.end }
            ?: return base
        return base.mergedWith(run.style)
    }

    /** Span fills at [offset], or null when the base fill applies. */
    fun fillsAt(spans: List<StyleSpan>, length: Int, offset: Int): List<TextFill>? =
        runs(spans, length).firstOrNull { offset >= it.start && offset < it.end }?.fills

    /**
     * Which fields have more than one distinct effective value across `[start, end)`.
     * Powers the inspector's "Mixed" display.
     */
    fun mixedFields(
        base: TypographyStyle,
        spans: List<StyleSpan>,
        length: Int,
        start: Int,
        end: Int,
    ): Set<TypographyField> {
        val from = start.coerceIn(0, length)
        val to = end.coerceIn(0, length)
        if (to <= from) return emptySet()
        val selected = runsCovering(spans, length)
            .mapNotNull { run ->
                val s = maxOf(run.start, from)
                val e = minOf(run.end, to)
                if (e <= s) null else run
            }
        if (selected.size <= 1) return emptySet()
        val mixed = mutableSetOf<TypographyField>()
        TypographyField.entries.forEach { field ->
            val values = selected.map { run ->
                if (field == TypographyField.Fills) run.fills
                else base.mergedWith(run.style).fieldValue(field)
            }
            if (values.distinct().size > 1) mixed += field
        }
        return mixed
    }

    /**
     * Effective style over a selection: field values where the selection is uniform,
     * paired with the set of mixed fields. Uniform fields report the shared value;
     * mixed fields report the first run's value (Figma shows the first value greyed).
     */
    fun selectionStyle(
        base: TypographyStyle,
        spans: List<StyleSpan>,
        length: Int,
        start: Int,
        end: Int,
    ): SelectionStyle {
        val from = start.coerceIn(0, length)
        val to = end.coerceIn(0, length)
        val first = if (to <= from) base else styleAt(base, spans, length, from)
        return SelectionStyle(
            style = first,
            fills = if (to <= from) null else fillsAt(spans, length, from),
            mixed = mixedFields(base, spans, length, from, to),
        )
    }

    data class SelectionStyle(
        val style: TypographyStyle,
        val fills: List<TextFill>?,
        val mixed: Set<TypographyField>,
    )

    private fun coalesce(runs: List<Run>): List<Run> {
        val result = mutableListOf<Run>()
        runs.sortedBy { it.start }.forEach { run ->
            val last = result.lastOrNull()
            if (last != null && last.end == run.start &&
                last.style == run.style && last.fills == run.fills
            ) {
                result[result.size - 1] = last.copy(end = run.end)
            } else {
                result += run
            }
        }
        return result
    }
}

internal fun TypographyStyle.fieldValue(field: TypographyField): Any? = when (field) {
    TypographyField.FontFamily -> fontFamily
    TypographyField.FontWeight -> fontWeight
    TypographyField.Italic -> italic
    TypographyField.FontSize -> fontSize
    TypographyField.LineHeight -> lineHeight
    TypographyField.LetterSpacing -> letterSpacing
    TypographyField.ParagraphSpacing -> paragraphSpacing
    TypographyField.ParagraphIndent -> paragraphIndent
    TypographyField.AlignHorizontal -> alignHorizontal
    TypographyField.AlignVertical -> alignVertical
    TypographyField.Case -> case
    TypographyField.Decoration -> decoration
    TypographyField.Features -> features
    TypographyField.Axes -> axes
    TypographyField.Position -> position
    TypographyField.LeadingTrim -> leadingTrim
    TypographyField.HangingPunctuation -> hangingPunctuation
    TypographyField.HangingLists -> hangingLists
    TypographyField.Fills -> null
}

/**
 * Drops override fields that raw-equal the explicit [base] field, keeping spans minimal.
 * Comparison is against the base's *explicit* values only — a span field equal to an
 * implicit default is kept, which renders identically and stays predictable.
 *
 * Public because boundary adapters use it to reduce fully-merged range styles back to
 * minimal overrides.
 */
fun TypographyStyle.prunedAgainst(base: TypographyStyle): TypographyStyle = TypographyStyle(
    fontFamily = fontFamily.takeUnless { it == base.fontFamily },
    fontWeight = fontWeight.takeUnless { it == base.fontWeight },
    italic = italic.takeUnless { it == base.italic },
    fontSize = fontSize.takeUnless { it == base.fontSize },
    lineHeight = lineHeight.takeUnless { it == base.lineHeight },
    letterSpacing = letterSpacing.takeUnless { it == base.letterSpacing },
    paragraphSpacing = paragraphSpacing.takeUnless { it == base.paragraphSpacing },
    paragraphIndent = paragraphIndent.takeUnless { it == base.paragraphIndent },
    alignHorizontal = alignHorizontal.takeUnless { it == base.alignHorizontal },
    alignVertical = alignVertical.takeUnless { it == base.alignVertical },
    case = case.takeUnless { it == base.case },
    decoration = decoration.takeUnless { it == base.decoration },
    features = if (features == base.features) emptyMap() else features,
    axes = if (axes == base.axes) emptyMap() else axes,
    position = position.takeUnless { it == base.position },
    leadingTrim = leadingTrim.takeUnless { it == base.leadingTrim },
    hangingPunctuation = hangingPunctuation.takeUnless { it == base.hangingPunctuation },
    hangingLists = hangingLists.takeUnless { it == base.hangingLists },
)
