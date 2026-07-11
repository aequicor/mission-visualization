package io.aequicor.visualization.engine.frontend.edit

/** One surgical text replacement; insertions have `start == end`. */
internal data class TextOp(val start: Int, val end: Int, val text: String)

/**
 * The outcome of planning one surgical write: either a set of non-overlapping [TextOp]s to
 * apply, or a clean failure the caller turns into an in-memory fallback (source untouched).
 * Shared by [CnlWriter], [SectionWriter] and [SlmPatcher].
 */
internal sealed interface WritePlan {
    class Ops(val ops: List<TextOp>) : WritePlan

    class Failed(val message: String, val line: Int) : WritePlan
}
