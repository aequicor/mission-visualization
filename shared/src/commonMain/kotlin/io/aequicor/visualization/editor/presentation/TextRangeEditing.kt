package io.aequicor.visualization.editor.presentation

import io.aequicor.visualization.engine.ir.model.DesignPaint
import io.aequicor.visualization.engine.ir.model.DesignTextStyle
import io.aequicor.visualization.engine.ir.model.TextLink
import io.aequicor.visualization.engine.ir.model.TextStyleRange
import io.aequicor.visualization.subsystems.typography.LinkSpan
import io.aequicor.visualization.subsystems.typography.OffsetHealing
import io.aequicor.visualization.subsystems.typography.StyleSpan

/**
 * Pure per-range styling algebra over a text node's [TextStyleRange] list, operating
 * directly on IR types (so `DesignPaint` fills and token-bound styles round-trip
 * exactly). Mirrors the typography subsystem's `SpanAlgebra`, but stays on the authoring
 * model the reducer edits.
 *
 * Offsets are UTF-16 indices; ranges are half-open `[start, end)`. Overlapping input
 * ranges compose in list order (later ranges override earlier ones).
 */
internal object TextRangeEditing {

    sealed interface FillsChange {
        data object Keep : FillsChange
        data object Clear : FillsChange
        data class Set(val fills: List<DesignPaint>) : FillsChange
    }

    private data class Run(
        val start: Int,
        val end: Int,
        val style: DesignTextStyle,
        val fills: List<DesignPaint>?,
        /** Shared document text-style id (null = none); IR "" round-trips as null here. */
        val styleRef: String? = null,
    )

    /** Applies [override] (+ [fillsChange]) to `[start, end)`, returning minimal ranges. */
    fun applyRange(
        ranges: List<TextStyleRange>,
        length: Int,
        start: Int,
        end: Int,
        override: DesignTextStyle,
        fillsChange: FillsChange = FillsChange.Keep,
    ): List<TextStyleRange> {
        val from = start.coerceIn(0, length)
        val to = end.coerceIn(0, length)
        if (to <= from) return normalize(ranges, length)

        val pieces = mutableListOf<Run>()
        coverage(ranges, length).forEach { run ->
            val cuts = listOf(run.start, run.end, from, to)
                .filter { it in run.start..run.end }
                .distinct()
                .sorted()
            for (i in 0 until cuts.size - 1) {
                val s = cuts[i]
                val e = cuts[i + 1]
                if (e <= s) continue
                if (s >= from && e <= to) {
                    val fills = when (fillsChange) {
                        FillsChange.Keep -> run.fills
                        FillsChange.Clear -> null
                        is FillsChange.Set -> fillsChange.fills
                    }
                    pieces += Run(s, e, run.style.mergedWith(override), fills, run.styleRef)
                } else {
                    pieces += run.copy(start = s, end = e)
                }
            }
        }
        return coalesce(pieces).toRanges()
    }

    /**
     * Sets the shared document text style [ref] on `[start, end)` (blank/null clears it),
     * preserving each covered piece's inline style + fills. Mirrors [applyRange] but writes
     * only [Run.styleRef], so the resolver's base < ref < inline precedence still applies.
     */
    fun applyStyleRef(
        ranges: List<TextStyleRange>,
        length: Int,
        start: Int,
        end: Int,
        ref: String?,
    ): List<TextStyleRange> {
        val from = start.coerceIn(0, length)
        val to = end.coerceIn(0, length)
        if (to <= from) return normalize(ranges, length)
        val target = ref?.takeIf { it.isNotBlank() }

        val pieces = mutableListOf<Run>()
        coverage(ranges, length).forEach { run ->
            val cuts = listOf(run.start, run.end, from, to)
                .filter { it in run.start..run.end }
                .distinct()
                .sorted()
            for (i in 0 until cuts.size - 1) {
                val s = cuts[i]
                val e = cuts[i + 1]
                if (e <= s) continue
                if (s >= from && e <= to) {
                    pieces += run.copy(start = s, end = e, styleRef = target)
                } else {
                    pieces += run.copy(start = s, end = e)
                }
            }
        }
        return coalesce(pieces).toRanges()
    }

    /** Flattens overlaps + coalesces adjacent equal ranges into minimal ranges. */
    fun normalize(ranges: List<TextStyleRange>, length: Int): List<TextStyleRange> =
        coalesce(styledRuns(ranges, length)).toRanges()

    /** Effective (base-merged) style at [offset]. */
    fun styleAt(base: DesignTextStyle, ranges: List<TextStyleRange>, length: Int, offset: Int): DesignTextStyle {
        val run = styledRuns(ranges, length).firstOrNull { offset >= it.start && offset < it.end }
        return if (run == null) base else base.mergedWith(run.style)
    }

    /** Span fills at [offset], or null when the base fill applies. */
    fun fillsAt(ranges: List<TextStyleRange>, length: Int, offset: Int): List<DesignPaint>? =
        styledRuns(ranges, length).firstOrNull { offset >= it.start && offset < it.end }?.fills

    /**
     * Effective (base-merged) styles of each run overlapping `[start, end)`, plus its
     * fills — one entry per distinct run. The inspector uses this to show a value when the
     * selection is uniform and "Mixed" when it is not.
     */
    fun runsInRange(
        base: DesignTextStyle,
        ranges: List<TextStyleRange>,
        length: Int,
        start: Int,
        end: Int,
    ): List<Pair<DesignTextStyle, List<DesignPaint>?>> {
        val from = start.coerceIn(0, length)
        val to = end.coerceIn(0, length)
        if (to <= from) return listOf(base to null)
        return coverage(ranges, length)
            .filter { maxOf(it.start, from) < minOf(it.end, to) }
            .map { base.mergedWith(it.style) to it.fills }
            .ifEmpty { listOf(base to null) }
    }

    /** Disjoint runs covering only styled intervals of `[0, length)`. */
    private fun styledRuns(ranges: List<TextStyleRange>, length: Int): List<Run> {
        val clamped = ranges.mapNotNull { range ->
            val s = range.start.coerceIn(0, length)
            val e = range.end.coerceIn(0, length)
            if (e <= s) null else range.copy(start = s, end = e)
        }
        if (clamped.isEmpty()) return emptyList()
        val cuts = buildList { clamped.forEach { add(it.start); add(it.end) } }.distinct().sorted()
        val runs = buildList {
            for (i in 0 until cuts.size - 1) {
                val s = cuts[i]
                val e = cuts[i + 1]
                val covering = clamped.filter { it.start <= s && it.end >= e }
                if (covering.isEmpty()) continue
                var style = DesignTextStyle()
                var fills: List<DesignPaint>? = null
                var styleRef: String? = null
                covering.forEach { range ->
                    style = style.mergedWith(range.style)
                    if (range.fills != null) fills = range.fills
                    if (range.styleRef.isNotBlank()) styleRef = range.styleRef
                }
                add(Run(s, e, style, fills, styleRef))
            }
        }
        return coalesce(runs)
    }

    /** Disjoint runs covering all of `[0, length)`, gaps included as empty runs. */
    private fun coverage(ranges: List<TextStyleRange>, length: Int): List<Run> {
        if (length <= 0) return emptyList()
        val styled = styledRuns(ranges, length)
        val result = mutableListOf<Run>()
        var cursor = 0
        styled.forEach { run ->
            if (run.start > cursor) result += Run(cursor, run.start, DesignTextStyle(), null)
            result += run
            cursor = run.end
        }
        if (cursor < length) result += Run(cursor, length, DesignTextStyle(), null)
        return result
    }

    private fun coalesce(runs: List<Run>): List<Run> {
        val result = mutableListOf<Run>()
        runs.sortedBy { it.start }.forEach { run ->
            val last = result.lastOrNull()
            if (last != null && last.end == run.start && last.style == run.style && last.fills == run.fills && last.styleRef == run.styleRef) {
                result[result.size - 1] = last.copy(end = run.end)
            } else {
                result += run
            }
        }
        return result
    }

    private fun List<Run>.toRanges(): List<TextStyleRange> =
        filter { it.style != DesignTextStyle() || it.fills != null || it.styleRef != null }
            .map { TextStyleRange(start = it.start, end = it.end, style = it.style, fills = it.fills, styleRef = it.styleRef ?: "") }

    /**
     * Heals style-range and link offsets across a text content change (old -> new),
     * modeling it as a single-replacement edit and delegating the offset arithmetic to
     * the typography subsystem's [OffsetHealing].
     */
    fun healForTextChange(
        ranges: List<TextStyleRange>,
        links: List<TextLink>,
        oldText: String,
        newText: String,
    ): Pair<List<TextStyleRange>, List<TextLink>> {
        if (oldText == newText) return ranges to links
        val prefix = commonPrefix(oldText, newText)
        val suffix = commonSuffix(oldText, newText, prefix)
        val editStart = prefix
        val editEnd = oldText.length - suffix
        val insertedLength = newText.length - prefix - suffix
        val healedRanges = ranges.mapNotNull { range ->
            OffsetHealing.healSpans(listOf(StyleSpan(range.start, range.end)), editStart, editEnd, insertedLength)
                .firstOrNull()
                ?.let { range.copy(start = it.start, end = it.end) }
        }
        val healedLinks = links.mapNotNull { link ->
            OffsetHealing.healLinks(listOf(LinkSpan(link.start, link.end, link.url, link.nodeTarget)), editStart, editEnd, insertedLength)
                .firstOrNull()
                ?.let { link.copy(start = it.start, end = it.end) }
        }
        return healedRanges to healedLinks
    }

    private fun commonPrefix(a: String, b: String): Int {
        val max = minOf(a.length, b.length)
        var i = 0
        while (i < max && a[i] == b[i]) i++
        return i
    }

    private fun commonSuffix(a: String, b: String, prefix: Int): Int {
        val max = minOf(a.length, b.length) - prefix
        var i = 0
        while (i < max && a[a.length - 1 - i] == b[b.length - 1 - i]) i++
        return i
    }
}
