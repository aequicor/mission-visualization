package io.aequicor.visualization.engine.frontend.edit

import io.aequicor.visualization.engine.frontend.SlmCompileOptions
import io.aequicor.visualization.engine.frontend.SlmCompileResult
import io.aequicor.visualization.engine.frontend.blocks.TypedBlockKind
import io.aequicor.visualization.engine.frontend.compileSlm
import io.aequicor.visualization.engine.frontend.diagnostics.DiagnosticCollector
import io.aequicor.visualization.engine.frontend.fnv1a64
import io.aequicor.visualization.engine.frontend.yaml.YamlMap
import io.aequicor.visualization.engine.frontend.yaml.YamlScalar
import io.aequicor.visualization.engine.frontend.yaml.YamlValue
import io.aequicor.visualization.engine.ir.model.DesignDiagnostic
import io.aequicor.visualization.engine.ir.model.HorizontalConstraint
import io.aequicor.visualization.engine.ir.model.SizingMode
import io.aequicor.visualization.engine.ir.model.VerticalConstraint

/**
 * Applies one surgical [SlmEdit] to the SLM source text (design section J). SLM
 * text is the source of truth: the flow is edit intent -> `applySlmEdit` -> new
 * source -> [compileSlm] -> new IR -> re-render. The patcher never serializes
 * markdown from the IR and never creates or deletes nodes, so generated ids stay
 * stable across edit -> recompile.
 *
 * [compiled] must be the compile of exactly [source]: a fingerprint mismatch fails
 * hard ("Stale compile result"). After a successful apply the caller must
 * recompile — [SlmEditResult] deliberately carries no document.
 */
fun applySlmEdit(source: String, edit: SlmEdit, compiled: SlmCompileResult): SlmEditResult {
    staleResult(source, compiled)?.let { return it }
    return applyResolved(source, edit, compiled.editIndex, DefaultFileName)
}

/**
 * Applies [edits] sequentially with re-resolution: apply edit 1 -> internal
 * [compileSlm] recompile -> resolve edit 2 against the fresh result -> ... This is
 * unconditionally correct for same-anchor interactions (width + height, or a second
 * edit landing in a block the first created). A failing middle edit aborts the
 * whole batch: partial diagnostics, `newSource = null`. [options] configure the
 * internal recompiles.
 */
fun applySlmEdits(
    source: String,
    edits: List<SlmEdit>,
    compiled: SlmCompileResult,
    options: SlmCompileOptions = SlmCompileOptions(),
): SlmEditResult {
    staleResult(source, compiled)?.let { return it }
    if (edits.isEmpty()) return SlmEditResult(source, null, emptyList())
    var currentSource = source
    var currentIndex = compiled.editIndex
    val diagnostics = mutableListOf<DesignDiagnostic>()
    edits.forEachIndexed { index, edit ->
        val result = applyResolved(currentSource, edit, currentIndex, options.fileName)
        diagnostics += result.diagnostics
        currentSource = result.newSource
            ?: return SlmEditResult(newSource = null, appliedRange = null, diagnostics = diagnostics)
        if (index < edits.lastIndex) {
            val recompiled = compileSlm(currentSource, options)
            currentIndex = recompiled.editIndex
        }
    }
    return SlmEditResult(
        newSource = currentSource,
        appliedRange = diffRange(source, currentSource),
        diagnostics = diagnostics,
    )
}

/** Result of a patch attempt; [newSource] is null when nothing was applied. */
data class SlmEditResult(
    val newSource: String?,
    /** Range of the rewritten bytes, in NEW source coordinates. */
    val appliedRange: SlmTextRange?,
    val diagnostics: List<DesignDiagnostic>,
) {
    val isApplied: Boolean get() = newSource != null
}

/** Half-open `[startOffset, endOffset)` character range. */
data class SlmTextRange(val startOffset: Int, val endOffset: Int)

private const val DefaultFileName: String = "document.layout.md"

// --- single-edit application ---

private fun staleResult(source: String, compiled: SlmCompileResult): SlmEditResult? {
    if (fnv1a64(source) == compiled.sourceFingerprint) return null
    val diagnostics = DiagnosticCollector(DefaultFileName)
    diagnostics.error("Stale compile result: recompile before editing")
    return SlmEditResult(newSource = null, appliedRange = null, diagnostics = diagnostics.diagnostics)
}

private fun applyResolved(
    source: String,
    edit: SlmEdit,
    editIndex: SlmEditIndex,
    fileName: String,
): SlmEditResult {
    val diagnostics = DiagnosticCollector(fileName)

    fun failed(): SlmEditResult =
        SlmEditResult(newSource = null, appliedRange = null, diagnostics = diagnostics.diagnostics)

    val lineIndex = LineIndex(source)
    // Structural edits (create/delete/move) synthesize or drop whole heading sections and
    // resolve their footprint arithmetically; attribute edits patch a scalar/list in place.
    var anchorLine = 0
    val plan = if (edit is StructuralSlmEdit) {
        structuralPlan(edit, editIndex, source, lineIndex, fileName)
    } else {
        val target = when (val resolution = resolveEditTarget(source, edit.nodeId, editIndex, lineIndex, fileName)) {
            is EditTargetResolution.Failed -> {
                diagnostics.error(resolution.message)
                return failed()
            }
            is EditTargetResolution.Resolved -> resolution.target
        }
        anchorLine = target.anchorSpan.startLine
        // Interaction/motion edits rewrite whole typed-block entries addressed by their line span
        // (see typedBlockSetPlan); everything else merges a payload via YamlPathWriter.
        when (edit) {
            is SetInteractions -> {
                val payloads = edit.interactions.map { InteractionYamlWriter.interaction(it) }
                if (payloads.any { it == null }) {
                    diagnostics.error("Interaction is not expressible in SLM", anchorLine)
                    return failed()
                }
                typedBlockSetPlan(source, target, lineIndex, TypedBlockKind.Interaction, payloads.filterNotNull())
            }
            is SetMotion -> typedBlockSetPlan(
                source, target, lineIndex, TypedBlockKind.Motion,
                edit.motion?.let { listOf(MotionYamlWriter.motion(it)) } ?: emptyList(),
            )
            else -> {
                val compiledEdit = when (val outcome = editPayload(edit, target)) {
                    is PayloadOutcome.Invalid -> {
                        diagnostics.error(outcome.message, anchorLine)
                        return failed()
                    }
                    is PayloadOutcome.Ok -> outcome
                }
                YamlPathWriter(lineIndex).plan(target, compiledEdit.blockKind, compiledEdit.payload)
            }
        }
    }
    val ops = when (plan) {
        is WritePlan.Failed -> {
            diagnostics.error(plan.message, plan.line)
            return failed()
        }
        is WritePlan.Ops -> plan.ops
    }
    if (ops.isEmpty()) {
        return SlmEditResult(newSource = source, appliedRange = null, diagnostics = diagnostics.diagnostics)
    }
    val ordered = ops.sortedBy { it.start }
    ordered.zipWithNext().forEach { (previous, next) ->
        if (next.start < previous.end) {
            diagnostics.error("Internal patcher error: overlapping write operations", anchorLine)
            return failed()
        }
    }
    val (newSource, range) = applyOps(source, ordered)
    return SlmEditResult(newSource = newSource, appliedRange = range, diagnostics = diagnostics.diagnostics)
}

/**
 * Resolves a structural edit's anchor(s) via [SlmEditIndex.anchorOwners] and hands the footprint
 * arithmetic to [SectionWriter]. An unaddressable node (prose segment, ir-splice, missing sibling)
 * fails with the same "promote it to its own heading / edit the embedded JSON" guidance as the
 * attribute path, so the caller can fall back to an in-memory edit.
 */
private fun structuralPlan(
    edit: StructuralSlmEdit,
    editIndex: SlmEditIndex,
    source: String,
    lineIndex: LineIndex,
    fileName: String,
): WritePlan {
    val writer = SectionWriter(source, lineIndex, fileName)
    return when (edit) {
        is DeleteSection -> {
            val span = editIndex.anchorOwners[edit.nodeId]
                ?: return WritePlan.Failed(unaddressableMessage(edit.nodeId, editIndex), 0)
            writer.delete(span)
        }
        is InsertChildSubtree -> {
            val parentSpan = editIndex.anchorOwners[edit.nodeId]
                ?: return WritePlan.Failed(unaddressableMessage(edit.nodeId, editIndex), 0)
            val afterSpan = edit.afterSiblingId?.let { sibling ->
                editIndex.anchorOwners[sibling]
                    ?: return WritePlan.Failed(unaddressableMessage(sibling, editIndex), 0)
            }
            writer.insert(parentSpan, edit.subtree, afterSpan)
        }
        is MoveSection -> {
            val subtreeSpan = editIndex.anchorOwners[edit.nodeId]
                ?: return WritePlan.Failed(unaddressableMessage(edit.nodeId, editIndex), 0)
            val parentSpan = editIndex.anchorOwners[edit.newParentId]
                ?: return WritePlan.Failed(unaddressableMessage(edit.newParentId, editIndex), 0)
            val afterSpan = edit.afterSiblingId?.let { sibling ->
                editIndex.anchorOwners[sibling]
                    ?: return WritePlan.Failed(unaddressableMessage(sibling, editIndex), 0)
            }
            writer.move(subtreeSpan, parentSpan, afterSpan)
        }
    }
}

private fun applyOps(source: String, ordered: List<TextOp>): Pair<String, SlmTextRange> {
    val builder = StringBuilder(source.length + ordered.sumOf { it.text.length })
    var cursor = 0
    ordered.forEach { op ->
        builder.append(source, cursor, op.start)
        builder.append(op.text)
        cursor = op.end
    }
    builder.append(source, cursor, source.length)

    var delta = 0
    var start = Int.MAX_VALUE
    var end = -1
    ordered.forEach { op ->
        val newStart = op.start + delta
        start = minOf(start, newStart)
        end = maxOf(end, newStart + op.text.length)
        delta += op.text.length - (op.end - op.start)
    }
    return builder.toString() to SlmTextRange(start, end)
}

/** Smallest range in NEW coordinates outside of which [old] and [new] agree. */
private fun diffRange(old: String, new: String): SlmTextRange? {
    if (old == new) return null
    val shortest = minOf(old.length, new.length)
    var prefix = 0
    while (prefix < shortest && old[prefix] == new[prefix]) prefix++
    var suffix = 0
    while (suffix < shortest - prefix && old[old.length - 1 - suffix] == new[new.length - 1 - suffix]) suffix++
    return SlmTextRange(prefix, new.length - suffix)
}

// --- edit -> (block kind, payload) compilation ---

private sealed interface PayloadOutcome {
    class Ok(val blockKind: TypedBlockKind, val payload: YamlPayload) : PayloadOutcome

    class Invalid(val message: String) : PayloadOutcome
}

private fun editPayload(edit: SlmEdit, target: EditTarget): PayloadOutcome = when (edit) {
    is SetTypedBlockScalar -> PayloadOutcome.Ok(
        edit.blockKind,
        nestedPayload(edit.yamlPath, YamlPayload.Scalar(edit.scalar)),
    )

    is SetSizing -> sizingPayload(edit, target)

    is SetNodePosition -> PayloadOutcome.Ok(
        TypedBlockKind.Layout,
        YamlPayload.Mapping(
            listOf(
                "position" to YamlPayload.Mapping(
                    listOf(
                        "mode" to scalar(YamlScalarValue.Str("absolute")),
                        "x" to scalar(YamlScalarValue.Num(edit.x)),
                        "y" to scalar(YamlScalarValue.Num(edit.y)),
                    ),
                ),
            ),
        ),
    )

    is SetNodeConstraints -> constraintsPayload(edit)

    is SetLayoutProperty -> PayloadOutcome.Ok(
        TypedBlockKind.Layout,
        nestedPayload(edit.property.yamlPath, YamlPayload.Scalar(edit.value)),
    )

    is SetStyleProperty -> when (edit.property) {
        StyleProp.FirstFillToken -> PayloadOutcome.Ok(
            TypedBlockKind.Style,
            YamlPayload.Mapping(
                listOf("fills" to YamlPayload.Sequence(listOf(YamlPayload.Scalar(edit.value)))),
            ),
        )
        else -> PayloadOutcome.Ok(
            TypedBlockKind.Style,
            nestedPayload(edit.property.yamlPath, YamlPayload.Scalar(edit.value)),
        )
    }

    is RenameNode -> renamePayload(edit, target)

    is SetText -> PayloadOutcome.Ok(
        TypedBlockKind.Text,
        YamlPayload.Mapping(listOf("defaultText" to scalar(YamlScalarValue.Str(edit.defaultText)))),
    )

    is SetFills -> PayloadOutcome.Ok(
        TypedBlockKind.Style,
        YamlPayload.Mapping(listOf("fills" to StyleYamlWriter.fills(edit.fills))),
    )

    is SetStrokes -> PayloadOutcome.Ok(
        TypedBlockKind.Style,
        YamlPayload.Mapping(listOf("strokes" to StyleYamlWriter.strokes(edit.strokes))),
    )

    is SetEffects -> PayloadOutcome.Ok(
        TypedBlockKind.Style,
        YamlPayload.Mapping(listOf("effects" to StyleYamlWriter.effects(edit.effects))),
    )

    is SetTextStyle -> PayloadOutcome.Ok(
        TypedBlockKind.Text,
        YamlPayload.Mapping(listOf("typography" to TypographyYamlWriter.typography(edit.style))),
    )

    // Structural edits never reach here: they are dispatched to SectionWriter upstream.
    is StructuralSlmEdit -> PayloadOutcome.Invalid("Structural edits do not compile to a typed-block payload")

    // Interaction/motion edits never reach here: they are dispatched to typedBlockSetPlan upstream.
    is SetInteractions, is SetMotion ->
        PayloadOutcome.Invalid("Interaction/motion edits are dispatched via typedBlockSetPlan")
}

private fun sizingPayload(edit: SetSizing, target: EditTarget): PayloadOutcome {
    if (edit.width == null && edit.height == null) {
        return PayloadOutcome.Invalid("SetSizing requires at least one of width or height")
    }
    val axes = buildList {
        edit.width?.let { spec ->
            add("width" to sizingAxisPayload(spec, existingValueAt(target, TypedBlockKind.Layout, listOf("sizing", "width"))))
        }
        edit.height?.let { spec ->
            add("height" to sizingAxisPayload(spec, existingValueAt(target, TypedBlockKind.Layout, listOf("sizing", "height"))))
        }
    }
    return PayloadOutcome.Ok(
        TypedBlockKind.Layout,
        YamlPayload.Mapping(listOf("sizing" to YamlPayload.Mapping(axes))),
    )
}

private fun constraintsPayload(edit: SetNodeConstraints): PayloadOutcome {
    val axes = buildList {
        edit.horizontal?.let { add("horizontal" to scalar(YamlScalarValue.Str(it.slmToken()))) }
        edit.vertical?.let { add("vertical" to scalar(YamlScalarValue.Str(it.slmToken()))) }
    }
    if (axes.isEmpty()) {
        return PayloadOutcome.Invalid("SetNodeConstraints requires at least one axis")
    }
    return PayloadOutcome.Ok(
        TypedBlockKind.Node,
        YamlPayload.Mapping(listOf("constraints" to YamlPayload.Mapping(axes))),
    )
}

private fun HorizontalConstraint.slmToken(): String = when (this) {
    HorizontalConstraint.Left -> "left"
    HorizontalConstraint.Right -> "right"
    HorizontalConstraint.Center -> "center"
    HorizontalConstraint.LeftRight -> "left-right"
    HorizontalConstraint.Scale -> "scale"
}

private fun VerticalConstraint.slmToken(): String = when (this) {
    VerticalConstraint.Top -> "top"
    VerticalConstraint.Bottom -> "bottom"
    VerticalConstraint.Center -> "center"
    VerticalConstraint.TopBottom -> "top-bottom"
    VerticalConstraint.Scale -> "scale"
}

/**
 * One sizing axis. Mode-only specs keep or produce the scalar shorthand
 * (`width: fill`) unless the axis already exists as a map, where `type` is merged;
 * dimensioned specs always take map form (upgrading a shorthand scalar in place).
 */
private fun sizingAxisPayload(spec: SizingSpec, existing: YamlValue?): YamlPayload {
    val mode = YamlScalarValue.Str(sizingModeToken(spec.mode))
    val dimensioned = spec.value != null || spec.min != null || spec.max != null
    if (!dimensioned && (existing == null || existing is YamlScalar)) {
        return YamlPayload.Scalar(mode)
    }
    return YamlPayload.Mapping(
        buildList {
            add("type" to scalar(mode))
            spec.value?.let { add("value" to scalar(YamlScalarValue.Num(it))) }
            spec.min?.let { add("min" to scalar(YamlScalarValue.Num(it))) }
            spec.max?.let { add("max" to scalar(YamlScalarValue.Num(it))) }
        },
    )
}

private fun sizingModeToken(mode: SizingMode): String = when (mode) {
    SizingMode.Fixed -> "fixed"
    SizingMode.Hug -> "hug"
    SizingMode.Fill -> "fill"
}

/** `node: frame` shorthand keeps its type when the rename upgrades it to a map. */
private fun renamePayload(edit: RenameNode, target: EditTarget): PayloadOutcome {
    val existing = existingValueAt(target, TypedBlockKind.Node, emptyList())
    val shorthandType = (existing as? YamlScalar)?.value as? String
    return PayloadOutcome.Ok(
        TypedBlockKind.Node,
        YamlPayload.Mapping(
            buildList {
                shorthandType?.let { add("type" to scalar(YamlScalarValue.Str(it))) }
                add("name" to scalar(YamlScalarValue.Str(edit.name)))
            },
        ),
    )
}

/** Existing YAML value at [path] inside the last bound [kind] entry, if any. */
private fun existingValueAt(target: EditTarget, kind: TypedBlockKind, path: List<String>): YamlValue? {
    var current: YamlValue = target.boundGroups
        .flatMap { it.entries }
        .lastOrNull { it.kind == kind }
        ?.value
        ?: return null
    path.forEach { key ->
        current = (current as? YamlMap)?.entries?.get(key) ?: return null
    }
    return current
}

private fun scalar(value: YamlScalarValue): YamlPayload = YamlPayload.Scalar(value)
