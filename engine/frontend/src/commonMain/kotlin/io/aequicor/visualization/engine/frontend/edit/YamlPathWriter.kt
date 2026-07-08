package io.aequicor.visualization.engine.frontend.edit

import io.aequicor.visualization.engine.frontend.blocks.TypedBlockKind
import io.aequicor.visualization.engine.frontend.yaml.YamlList
import io.aequicor.visualization.engine.frontend.yaml.YamlMap
import io.aequicor.visualization.engine.frontend.yaml.YamlScalar
import io.aequicor.visualization.engine.frontend.yaml.YamlValue

/**
 * Desired YAML content of one edit, merged into the existing block text. Payload
 * trees stay small: they carry only the keys the edit writes.
 */
internal sealed interface YamlPayload {
    data class Scalar(val value: YamlScalarValue) : YamlPayload

    data class Mapping(val entries: List<Pair<String, YamlPayload>>) : YamlPayload

    /**
     * A list payload. With [replaceWhole] = false (the default) a write into an existing
     * list replaces only its first scalar item (single-token style edits); with
     * [replaceWhole] = true the entire existing list is rewritten from [items] — used by
     * fills/strokes/effects write-back, where the working document owns the full list.
     */
    data class Sequence(val items: List<YamlPayload>, val replaceWhole: Boolean = false) : YamlPayload
}

/** Wraps [leaf] into nested single-key mappings along [path]. */
internal fun nestedPayload(path: List<String>, leaf: YamlPayload): YamlPayload =
    path.foldRight(leaf) { key, inner -> YamlPayload.Mapping(listOf(key to inner)) }

/** One surgical text replacement; insertions have `start == end`. */
internal data class TextOp(val start: Int, val end: Int, val text: String)

internal sealed interface WritePlan {
    class Ops(val ops: List<TextOp>) : WritePlan

    class Failed(val message: String, val line: Int) : WritePlan
}

/**
 * The three-case write mechanics of design J.B, computed against one source
 * snapshot so a whole edit (e.g. sizing width + height) creates missing paths once:
 *
 * 1. Existing scalar — in-place token replacement of exactly `raw.length`
 *    characters; trailing ` # comments`, alignment and quote style survive.
 *    A shorthand scalar that needs a map is upgraded in place to a single-line
 *    inline map (`fill` -> `{ type: fixed, value: 320 }`).
 * 2. Partial path — missing keys are appended after the deepest existing prefix
 *    subtree's end line, at the prefix map's key indent, +2 spaces per level,
 *    canonical block style (inline flow maps grow before their closing brace).
 * 3. No entry of the kind — a new reserved-key entry is appended into the
 *    anchor's existing typed group (at group indent, after its last line, keeping
 *    the group contiguous), or a brand-new typed block is inserted on the line
 *    immediately after the anchor's last line, before any following blank line or
 *    paragraph.
 */
internal class YamlPathWriter(
    private val lineIndex: LineIndex,
) {
    fun plan(target: EditTarget, blockKind: TypedBlockKind, payload: YamlPayload): WritePlan {
        val entry = target.boundGroups.flatMap { it.entries }.lastOrNull { it.kind == blockKind }
            ?: return newBlockPlan(target, blockKind.key, payload)
        val ops = mutableListOf<TextOp>()
        val failure = merge(entry.value, payload, ops)
        return if (failure != null) WritePlan.Failed(failure.message, failure.line) else WritePlan.Ops(ops)
    }

    private class Failure(val message: String, val line: Int)

    // --- case 3: new entry / new block ---

    private fun newBlockPlan(target: EditTarget, key: String, payload: YamlPayload): WritePlan {
        val lastGroup = target.boundGroups.lastOrNull()
        val op = if (lastGroup != null) {
            val indent = lineIndex.indentOf(lastGroup.span.startLine)
            insertAt(lastGroup.span.endLine + 1, SlmBlockRenderer.entryLines(key, payload, indent))
        } else {
            insertAt(
                beforeLine = target.insertion.line,
                lines = SlmBlockRenderer.entryLines(key, payload, target.insertion.indent),
                blankBefore = target.insertion.blankLineBefore,
            )
        }
        return WritePlan.Ops(listOf(op))
    }

    // --- cases 1 + 2: merge into an existing entry ---

    private fun merge(existing: YamlValue, payload: YamlPayload, ops: MutableList<TextOp>): Failure? =
        when (payload) {
            is YamlPayload.Scalar -> mergeScalar(existing, payload, ops)
            is YamlPayload.Mapping -> mergeMapping(existing, payload, ops)
            is YamlPayload.Sequence -> mergeSequence(existing, payload, ops)
        }

    private fun mergeScalar(
        existing: YamlValue,
        payload: YamlPayload.Scalar,
        ops: MutableList<TextOp>,
    ): Failure? = when {
        existing is YamlScalar && existing.isEmptyValue -> {
            ops += fillEmptyValueOp(existing, ScalarFormatter.format(payload.value))
            null
        }
        existing is YamlScalar -> {
            ops += replaceScalarOp(existing, payload.value)
            null
        }
        else -> Failure(
            "Cannot replace a nested map or list with a scalar; write its keys individually",
            existing.line,
        )
    }

    private fun mergeMapping(
        existing: YamlValue,
        payload: YamlPayload.Mapping,
        ops: MutableList<TextOp>,
    ): Failure? = when {
        existing is YamlScalar && existing.isEmptyValue -> {
            val indent = lineIndex.indentOf(existing.line) + 2
            ops += insertAt(existing.line + 1, SlmBlockRenderer.mappingLines(payload, indent))
            null
        }
        // Shape upgrade: shorthand scalar needing a map -> single-line inline map.
        existing is YamlScalar -> {
            val start = lineIndex.offsetOf(existing.line, existing.column)
            ops += TextOp(start, start + existing.raw.length, SlmBlockRenderer.inline(payload))
            null
        }
        existing is YamlMap && isFlow(existing) -> mergeFlowMap(existing, payload, ops)
        existing is YamlMap -> mergeBlockMap(existing, payload, ops)
        else -> Failure("Cannot merge map content into a list", existing.line)
    }

    private fun mergeBlockMap(
        map: YamlMap,
        payload: YamlPayload.Mapping,
        ops: MutableList<TextOp>,
    ): Failure? {
        val missing = mutableListOf<Pair<String, YamlPayload>>()
        payload.entries.forEach { (key, child) ->
            val current = map.entries[key]
            if (current == null) {
                missing += key to child
            } else {
                merge(current, child, ops)?.let { return it }
            }
        }
        if (missing.isNotEmpty()) {
            // New keys append after the existing siblings' subtree end.
            val indent = map.column - 1
            val lines = missing.flatMap { (key, child) -> SlmBlockRenderer.entryLines(key, child, indent) }
            ops += insertAt(map.endLine + 1, lines)
        }
        return null
    }

    private fun mergeFlowMap(
        map: YamlMap,
        payload: YamlPayload.Mapping,
        ops: MutableList<TextOp>,
    ): Failure? {
        val missing = mutableListOf<Pair<String, YamlPayload>>()
        payload.entries.forEach { (key, child) ->
            val current = map.entries[key]
            if (current == null) {
                missing += key to child
            } else {
                merge(current, child, ops)?.let { return it }
            }
        }
        if (missing.isNotEmpty()) {
            val rendered = missing.joinToString(", ") { (key, child) -> "$key: ${SlmBlockRenderer.inline(child)}" }
            val lineText = lineIndex.lineText(map.endLine)
            // Insert after the last non-space character before the closing brace.
            var column = map.endColumn - 1
            while (column > 1 && lineText.getOrNull(column - 2) == ' ') column--
            val offset = lineIndex.offsetOf(map.endLine, column)
            ops += TextOp(offset, offset, if (map.entries.isEmpty()) " $rendered " else ", $rendered")
        }
        return null
    }

    private fun mergeSequence(
        existing: YamlValue,
        payload: YamlPayload.Sequence,
        ops: MutableList<TextOp>,
    ): Failure? = when {
        existing is YamlScalar && existing.isEmptyValue -> {
            val indent = lineIndex.indentOf(existing.line) + 2
            ops += insertAt(existing.line + 1, SlmBlockRenderer.sequenceLines(payload, indent))
            null
        }
        existing is YamlScalar -> {
            val start = lineIndex.offsetOf(existing.line, existing.column)
            ops += TextOp(start, start + existing.raw.length, SlmBlockRenderer.inline(payload))
            null
        }
        existing is YamlList && payload.replaceWhole -> replaceWholeList(existing, payload, ops)
        existing is YamlList -> {
            val item = payload.items.singleOrNull()
            val first = existing.items.firstOrNull()
            when {
                item !is YamlPayload.Scalar ->
                    Failure("Only single-scalar list writes are supported", existing.line)
                first == null -> Failure("Cannot extend an empty list in place", existing.line)
                first !is YamlScalar ->
                    Failure("Unsupported list item shape for an in-place write", first.line)
                else -> {
                    ops += replaceScalarOp(first, item.value)
                    null
                }
            }
        }
        else -> Failure("Cannot replace a map with a list", existing.line)
    }

    /**
     * Rewrites an entire existing list from [payload]. Block-style lists are re-rendered
     * as block items at the list's indent (each item an inline flow value); flow lists
     * (`[ ... ]`) are re-rendered inline. An empty payload fails so the caller can fall
     * back rather than leave a danging `key:`.
     */
    private fun replaceWholeList(
        list: YamlList,
        payload: YamlPayload.Sequence,
        ops: MutableList<TextOp>,
    ): Failure? {
        if (payload.items.isEmpty()) return Failure("Cannot write an empty list in place", list.line)
        val isFlow = lineIndex.lineText(list.line).getOrNull(list.column - 1) == '['
        if (isFlow) {
            val start = lineIndex.offsetOf(list.line, list.column)
            val end = lineIndex.offsetOf(list.endLine, list.endColumn)
            ops += TextOp(start, end, SlmBlockRenderer.inline(payload))
            return null
        }
        val body = SlmBlockRenderer.sequenceLines(payload, list.column - 1).joinToString("\n")
        // Replace from the start of the first item's line (indent included) through the
        // last item's end, so no stale item lines survive.
        val start = lineIndex.lineStartOffset(list.line)
        val end = lineIndex.offsetOf(list.endLine, list.endColumn)
        ops += TextOp(start, end, body)
        return null
    }

    // --- text op construction ---

    private fun replaceScalarOp(existing: YamlScalar, value: YamlScalarValue): TextOp {
        val start = lineIndex.offsetOf(existing.line, existing.column)
        val formatted = ScalarFormatter.format(value, existing.raw.takeIf { it.isNotEmpty() })
        return TextOp(start, start + existing.raw.length, formatted)
    }

    /** Writes a scalar into an empty `key:` entry, right after its colon. */
    private fun fillEmptyValueOp(existing: YamlScalar, token: String): TextOp {
        val text = lineIndex.lineText(existing.line)
        val marker = text.indexOf(':').takeIf { it >= 0 }
            ?: text.indexOf('-').takeIf { it >= 0 }
            ?: text.length - 1
        val offset = lineIndex.lineStartOffset(existing.line) + marker + 1
        return TextOp(offset, offset, " $token")
    }

    private fun insertAt(beforeLine: Int, lines: List<String>, blankBefore: Boolean = false): TextOp {
        val offset = lineIndex.lineStartOffset(beforeLine)
        val needsLeadingBreak = beforeLine > lineIndex.lineCount && !lineIndex.endsWithNewline
        val text = buildString {
            if (needsLeadingBreak) append('\n')
            if (blankBefore) append('\n')
            lines.forEach { line -> append(line).append('\n') }
        }
        return TextOp(offset, offset, text)
    }

    /** Flow maps start with `{` at their value column; block maps start at a key. */
    private fun isFlow(map: YamlMap): Boolean =
        lineIndex.lineText(map.line).getOrNull(map.column - 1) == '{'
}

private val YamlScalar.isEmptyValue: Boolean get() = raw.isEmpty() && value == null
