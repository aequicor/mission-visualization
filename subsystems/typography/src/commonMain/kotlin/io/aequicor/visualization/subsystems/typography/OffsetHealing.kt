package io.aequicor.visualization.subsystems.typography

/**
 * Keeps span/link offsets consistent when the underlying text changes.
 *
 * All edits are modeled as a single replacement: `[editStart, editEnd)` is replaced by
 * [insertedLength] characters. Pure insertion has `editStart == editEnd`; pure deletion
 * has `insertedLength == 0`.
 *
 * Insertion at a boundary follows the typing convention: text typed at a span's end
 * extends the span (a caret inherits the style of the character before it), text typed
 * at a span's start pushes the span right.
 */
object OffsetHealing {

    fun healSpans(
        spans: List<StyleSpan>,
        editStart: Int,
        editEnd: Int,
        insertedLength: Int,
    ): List<StyleSpan> = spans.mapNotNull { span ->
        val healed = healRange(span.start, span.end, editStart, editEnd, insertedLength)
            ?: return@mapNotNull null
        span.copy(start = healed.first, end = healed.second)
    }

    fun healLinks(
        links: List<LinkSpan>,
        editStart: Int,
        editEnd: Int,
        insertedLength: Int,
    ): List<LinkSpan> = links.mapNotNull { link ->
        val healed = healRange(link.start, link.end, editStart, editEnd, insertedLength)
            ?: return@mapNotNull null
        link.copy(start = healed.first, end = healed.second)
    }

    /**
     * Maps a single offset through the edit (e.g. the caret position of a collaborator).
     * Offsets inside the replaced range collapse to the end of the insertion.
     */
    fun healOffset(offset: Int, editStart: Int, editEnd: Int, insertedLength: Int): Int {
        val delta = insertedLength - (editEnd - editStart)
        return when {
            offset <= editStart -> offset
            offset >= editEnd -> offset + delta
            else -> editStart + insertedLength
        }
    }

    /** Returns the healed `[start, end)` or null when the range collapses to nothing. */
    private fun healRange(
        start: Int,
        end: Int,
        editStart: Int,
        editEnd: Int,
        insertedLength: Int,
    ): Pair<Int, Int>? {
        val delta = insertedLength - (editEnd - editStart)
        val isInsertion = editStart == editEnd

        val newStart = when {
            // Typing exactly at the span start pushes the span right.
            isInsertion && start == editStart -> start + insertedLength
            start <= editStart -> start
            start >= editEnd -> start + delta
            else -> editStart + insertedLength
        }
        val newEnd = when {
            // Typing exactly at the span end extends the span over the typed text.
            isInsertion && end == editStart -> end + insertedLength
            end <= editStart -> end
            end >= editEnd -> end + delta
            else -> editStart + insertedLength
        }
        return if (newEnd <= newStart) null else newStart to newEnd
    }
}
