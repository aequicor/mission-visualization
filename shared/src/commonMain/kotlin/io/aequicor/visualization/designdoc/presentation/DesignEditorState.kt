package io.aequicor.visualization.designdoc.presentation

import io.aequicor.visualization.engine.ir.model.Bindable
import io.aequicor.visualization.engine.ir.model.DesignColor
import io.aequicor.visualization.engine.ir.model.DesignCornerRadius
import io.aequicor.visualization.engine.ir.model.DesignDiagnostic
import io.aequicor.visualization.engine.ir.model.DesignDocument
import io.aequicor.visualization.engine.ir.model.DesignNode
import io.aequicor.visualization.engine.ir.model.DesignPaint
import io.aequicor.visualization.engine.ir.model.DesignPoint
import io.aequicor.visualization.engine.ir.model.DesignSize
import io.aequicor.visualization.engine.ir.model.DesignSizing
import io.aequicor.visualization.engine.ir.model.DesignStrokes
import io.aequicor.visualization.engine.ir.model.HorizontalConstraint
import io.aequicor.visualization.engine.ir.model.SizingMode
import io.aequicor.visualization.engine.ir.model.VerticalConstraint
import io.aequicor.visualization.engine.ir.model.bindable
import io.aequicor.visualization.engine.ir.serialization.DesignParseResult
import io.aequicor.visualization.engine.ir.serialization.documentOrNull

/**
 * Immutable editor state over a parsed design document. Selection is tracked by
 * page id and authored node id; document edits go through [reduceDesignEditor].
 */
data class DesignEditorState(
    val document: DesignDocument? = null,
    val diagnostics: List<DesignDiagnostic> = emptyList(),
    val selectedPageId: String = "",
    val selectedNodeId: String = "",
) {
    val selectedNode: DesignNode?
        get() = document?.nodeById(selectedNodeId)
}

fun createDesignEditorState(parseResult: DesignParseResult): DesignEditorState {
    val document = parseResult.documentOrNull()
    val firstPage = document?.pages?.firstOrNull()
    return DesignEditorState(
        document = document,
        diagnostics = parseResult.diagnostics,
        selectedPageId = firstPage?.id.orEmpty(),
        selectedNodeId = firstPage?.children?.firstOrNull()?.id.orEmpty(),
    )
}

sealed interface DesignEditorIntent {
    data class SelectPage(val pageId: String) : DesignEditorIntent

    data class SelectNode(val nodeId: String) : DesignEditorIntent

    data class UpdatePosition(val nodeId: String, val x: Double? = null, val y: Double? = null) : DesignEditorIntent

    /** Typing an exact number pins the edited dimension to `fixed`, like Figma. */
    data class UpdateSize(val nodeId: String, val width: Double? = null, val height: Double? = null) : DesignEditorIntent

    data class UpdateSizingMode(
        val nodeId: String,
        val horizontal: SizingMode? = null,
        val vertical: SizingMode? = null,
    ) : DesignEditorIntent

    data class UpdateConstraints(
        val nodeId: String,
        val horizontal: HorizontalConstraint? = null,
        val vertical: VerticalConstraint? = null,
    ) : DesignEditorIntent

    data class UpdateOpacity(val nodeId: String, val opacity: Double) : DesignEditorIntent

    data class UpdateSolidFill(val nodeId: String, val color: DesignColor) : DesignEditorIntent

    data class UpdateStroke(
        val nodeId: String,
        val color: DesignColor? = null,
        val weight: Double? = null,
    ) : DesignEditorIntent

    data class UpdateCornerRadius(val nodeId: String, val radius: Double) : DesignEditorIntent

    data class SetClipsContent(val nodeId: String, val clips: Boolean) : DesignEditorIntent

    data class SetSticky(val nodeId: String, val sticky: Boolean) : DesignEditorIntent
}

fun reduceDesignEditor(state: DesignEditorState, intent: DesignEditorIntent): DesignEditorState =
    when (intent) {
        is DesignEditorIntent.SelectPage -> {
            val page = state.document?.pageById(intent.pageId)
            state.copy(
                selectedPageId = page?.id ?: intent.pageId,
                selectedNodeId = page?.children?.firstOrNull()?.id.orEmpty(),
            )
        }
        is DesignEditorIntent.SelectNode -> {
            val page = state.document?.pageOfNode(intent.nodeId)
            state.copy(
                selectedNodeId = intent.nodeId,
                selectedPageId = page?.id ?: state.selectedPageId,
            )
        }
        is DesignEditorIntent.UpdatePosition -> state.updateNode(intent.nodeId) { node ->
            val current = node.position ?: DesignPoint()
            node.copy(
                position = DesignPoint(
                    x = intent.x ?: current.x,
                    y = intent.y ?: current.y,
                ),
            )
        }
        is DesignEditorIntent.UpdateSize -> state.updateNode(intent.nodeId) { node ->
            val sizing = node.sizing ?: DesignSizing()
            node.copy(
                size = DesignSize(
                    width = intent.width ?: node.size.width,
                    height = intent.height ?: node.size.height,
                ),
                sizing = sizing.copy(
                    horizontal = if (intent.width != null) SizingMode.Fixed else sizing.horizontal,
                    vertical = if (intent.height != null) SizingMode.Fixed else sizing.vertical,
                ),
            )
        }
        is DesignEditorIntent.UpdateSizingMode -> state.updateNode(intent.nodeId) { node ->
            val sizing = node.sizing ?: DesignSizing()
            node.copy(
                sizing = sizing.copy(
                    horizontal = intent.horizontal ?: sizing.horizontal,
                    vertical = intent.vertical ?: sizing.vertical,
                ),
            )
        }
        is DesignEditorIntent.UpdateConstraints -> state.updateNode(intent.nodeId) { node ->
            node.copy(
                constraints = node.constraints.copy(
                    horizontal = intent.horizontal ?: node.constraints.horizontal,
                    vertical = intent.vertical ?: node.constraints.vertical,
                ),
            )
        }
        is DesignEditorIntent.UpdateOpacity -> state.updateNode(intent.nodeId) { node ->
            node.copy(opacity = intent.opacity.coerceIn(0.0, 1.0).bindable())
        }
        is DesignEditorIntent.UpdateSolidFill -> state.updateNode(intent.nodeId) { node ->
            val solid = DesignPaint.Solid(intent.color.bindable())
            val fills = node.fills.orEmpty()
            node.copy(
                fills = if (fills.isEmpty()) listOf(solid) else listOf(solid) + fills.drop(1),
                fillStyleId = "",
            )
        }
        is DesignEditorIntent.UpdateStroke -> state.updateNode(intent.nodeId) { node ->
            val current = node.strokes ?: DesignStrokes()
            val paints = if (intent.color != null) {
                listOf(DesignPaint.Solid(intent.color.bindable())) + current.paints.drop(1)
            } else {
                current.paints
            }
            node.copy(
                strokes = current.copy(
                    paints = paints,
                    weight = intent.weight?.bindable() ?: current.weight,
                ),
            )
        }
        is DesignEditorIntent.UpdateCornerRadius -> state.updateNode(intent.nodeId) { node ->
            node.copy(cornerRadius = DesignCornerRadius.all(intent.radius.coerceAtLeast(0.0).bindable()))
        }
        is DesignEditorIntent.SetClipsContent -> state.updateNode(intent.nodeId) { node ->
            node.copy(layout = node.layout.copy(clipsContent = intent.clips))
        }
        is DesignEditorIntent.SetSticky -> state.updateNode(intent.nodeId) { node ->
            node.copy(scroll = node.scroll.copy(sticky = intent.sticky))
        }
    }

private fun DesignEditorState.updateNode(
    nodeId: String,
    transform: (DesignNode) -> DesignNode,
): DesignEditorState {
    val document = document ?: return this
    if (nodeId.isBlank()) return this
    return copy(document = document.updateNode(nodeId, transform))
}
