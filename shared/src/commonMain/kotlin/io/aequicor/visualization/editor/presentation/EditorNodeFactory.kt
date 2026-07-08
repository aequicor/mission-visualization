package io.aequicor.visualization.editor.presentation

import io.aequicor.visualization.engine.ir.model.DesignAutoLayout
import io.aequicor.visualization.engine.ir.model.DesignColor
import io.aequicor.visualization.engine.ir.model.DesignDocument
import io.aequicor.visualization.engine.ir.model.DesignNode
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.engine.ir.model.DesignPage
import io.aequicor.visualization.engine.ir.model.DesignPaint
import io.aequicor.visualization.engine.ir.model.DesignPoint
import io.aequicor.visualization.engine.ir.model.DesignSize
import io.aequicor.visualization.engine.ir.model.DesignSizing
import io.aequicor.visualization.engine.ir.model.DesignStrokes
import io.aequicor.visualization.engine.ir.model.LayoutMode
import io.aequicor.visualization.engine.ir.model.ShapeType
import io.aequicor.visualization.engine.ir.model.SizingMode
import io.aequicor.visualization.engine.ir.model.TextAutoResize
import io.aequicor.visualization.engine.ir.model.bindable

/** The object kinds the toolbar can create on the canvas. */
enum class NewObjectKind { Frame, Rectangle, Ellipse, Line, Arrow, Text }

/** A device/frame preset offered by the "new screen" flow. */
enum class ScreenPreset(val displayName: String, val width: Double, val height: Double) {
    Desktop("Desktop", 1440.0, 1024.0),
    Tablet("Tablet", 768.0, 1024.0),
    Mobile("Mobile", 375.0, 812.0),
    Square("Square", 1080.0, 1080.0),
}

/**
 * Builds fresh [DesignNode]s and [DesignPage]s for the in-memory editor. Ids are
 * derived deterministically from the document (first free `prefix_N`), so creating a
 * node is a pure function of the document state — same document + request always
 * yields the same id, which keeps the reducer referentially transparent and testable.
 */
object EditorNodeFactory {

    private val fillGrey = DesignColor.fromHex("#D5DEEA") ?: DesignColor(0xFFD5DEEA)
    private val strokeBlue = DesignColor.fromHex("#1E88FF") ?: DesignColor(0xFF1E88FF)
    private val frameWhite = DesignColor.fromHex("#FFFFFF") ?: DesignColor.Black
    private val textInk = DesignColor.fromHex("#1B2733") ?: DesignColor.Black

    /** First `prefix_N` (N from 1) not used by any node or page in [document]. */
    fun uniqueId(document: DesignDocument?, prefix: String): String {
        val used = buildSet {
            document?.pages?.forEach { page ->
                add(page.id)
                page.allNodes().forEach { add(it.id) }
            }
        }
        var n = 1
        while ("${prefix}_$n" in used) n++
        return "${prefix}_$n"
    }

    /**
     * Creates a new object node of [kind] at parent-relative ([x], [y]) with size
     * ([width], [height]). Absolute placement is used so the node sits at fixed
     * coordinates inside a free (or flow) parent frame.
     */
    fun newObject(
        document: DesignDocument?,
        kind: NewObjectKind,
        x: Double,
        y: Double,
        width: Double,
        height: Double,
        fixedWidthText: Boolean = false,
    ): DesignNode {
        val id = uniqueId(document, idPrefixFor(kind))
        val base = DesignNode(
            id = id,
            type = typeFor(kind),
            kind = kindFor(kind, fixedWidthText),
            name = defaultNameFor(kind),
            position = DesignPoint(x, y),
            size = DesignSize(width, height),
            sizing = sizingFor(kind, fixedWidthText),
            layoutChild = io.aequicor.visualization.engine.ir.model.DesignLayoutChild(absolute = true),
        )
        return decorate(base, kind)
    }

    /** New screen = a page holding a single root frame sized to the preset. */
    fun newScreen(document: DesignDocument?, preset: ScreenPreset, title: String): DesignPage {
        val pageId = uniqueId(document, "screen")
        val frameId = uniqueId(document, "frame")
        val rootFrame = DesignNode(
            id = frameId,
            type = "frame",
            kind = DesignNodeKind.Frame,
            name = title,
            position = DesignPoint(72.0, 72.0),
            size = DesignSize(preset.width, preset.height),
            sizing = DesignSizing(SizingMode.Fixed, SizingMode.Fixed),
            layout = DesignAutoLayout(mode = LayoutMode.None),
            fills = listOf(DesignPaint.Solid(frameWhite.bindable())),
        )
        return DesignPage(id = pageId, name = title, children = listOf(rootFrame))
    }

    private fun decorate(node: DesignNode, kind: NewObjectKind): DesignNode = when (kind) {
        NewObjectKind.Frame -> node.copy(
            fills = listOf(DesignPaint.Solid(frameWhite.bindable())),
            layout = DesignAutoLayout(mode = LayoutMode.None),
        )
        NewObjectKind.Rectangle, NewObjectKind.Ellipse -> node.copy(
            fills = listOf(DesignPaint.Solid(fillGrey.bindable())),
        )
        NewObjectKind.Line, NewObjectKind.Arrow -> node.copy(
            fills = null,
            strokes = DesignStrokes(
                paints = listOf(DesignPaint.Solid(strokeBlue.bindable())),
                weight = 2.0.bindable(),
                cap = if (kind == NewObjectKind.Arrow) "arrow" else "round",
            ),
        )
        NewObjectKind.Text -> node.copy(
            fills = listOf(DesignPaint.Solid(textInk.bindable())),
        )
    }

    private fun kindFor(kind: NewObjectKind, fixedWidthText: Boolean): DesignNodeKind = when (kind) {
        NewObjectKind.Frame -> DesignNodeKind.Frame
        NewObjectKind.Rectangle -> DesignNodeKind.Shape(ShapeType.Rectangle)
        NewObjectKind.Ellipse -> DesignNodeKind.Shape(ShapeType.Ellipse)
        NewObjectKind.Line -> DesignNodeKind.Shape(ShapeType.Line)
        NewObjectKind.Arrow -> DesignNodeKind.Shape(ShapeType.Arrow)
        NewObjectKind.Text -> DesignNodeKind.Text(
            characters = "Text".bindable(),
            autoResize = if (fixedWidthText) TextAutoResize.Height else TextAutoResize.WidthAndHeight,
        )
    }

    private fun sizingFor(kind: NewObjectKind, fixedWidthText: Boolean): DesignSizing = when (kind) {
        NewObjectKind.Text -> if (fixedWidthText) {
            DesignSizing(SizingMode.Fixed, SizingMode.Hug)
        } else {
            DesignSizing(SizingMode.Hug, SizingMode.Hug)
        }
        else -> DesignSizing(SizingMode.Fixed, SizingMode.Fixed)
    }

    private fun typeFor(kind: NewObjectKind): String = when (kind) {
        NewObjectKind.Frame -> "frame"
        NewObjectKind.Text -> "text"
        else -> "shape"
    }

    private fun idPrefixFor(kind: NewObjectKind): String = when (kind) {
        NewObjectKind.Frame -> "frame"
        NewObjectKind.Rectangle -> "rect"
        NewObjectKind.Ellipse -> "ellipse"
        NewObjectKind.Line -> "line"
        NewObjectKind.Arrow -> "arrow"
        NewObjectKind.Text -> "text"
    }

    private fun defaultNameFor(kind: NewObjectKind): String = when (kind) {
        NewObjectKind.Frame -> "Frame"
        NewObjectKind.Rectangle -> "Rectangle"
        NewObjectKind.Ellipse -> "Ellipse"
        NewObjectKind.Line -> "Line"
        NewObjectKind.Arrow -> "Arrow"
        NewObjectKind.Text -> "Text"
    }

    /** Default creation size for a click (no drag) with the given tool. */
    fun defaultSizeFor(kind: NewObjectKind): DesignSize = when (kind) {
        NewObjectKind.Frame -> DesignSize(240.0, 160.0)
        NewObjectKind.Rectangle, NewObjectKind.Ellipse -> DesignSize(120.0, 120.0)
        NewObjectKind.Line, NewObjectKind.Arrow -> DesignSize(160.0, 0.0)
        NewObjectKind.Text -> DesignSize(120.0, 28.0)
    }
}
