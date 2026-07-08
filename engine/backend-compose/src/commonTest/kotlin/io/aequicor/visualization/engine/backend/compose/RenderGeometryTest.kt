package io.aequicor.visualization.engine.backend.compose

import io.aequicor.visualization.engine.ir.layout.LayoutBox
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.engine.ir.model.DesignPoint
import io.aequicor.visualization.engine.ir.model.ImageScaleMode
import io.aequicor.visualization.engine.ir.model.InteractionTrigger
import io.aequicor.visualization.engine.ir.model.LayoutGridAlignment
import io.aequicor.visualization.engine.ir.model.MaskType
import io.aequicor.visualization.engine.ir.model.ShapeType
import io.aequicor.visualization.engine.ir.geometry.PathCommand
import io.aequicor.visualization.engine.ir.geometry.PathGeometry
import io.aequicor.visualization.engine.ir.model.VectorPath
import io.aequicor.visualization.engine.ir.resolve.ResolvedInteraction
import io.aequicor.visualization.engine.ir.resolve.ResolvedMask
import io.aequicor.visualization.engine.ir.resolve.ResolvedNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RenderGeometryTest {

    // --- Overlay options ------------------------------------------------------

    @Test
    fun overlayOptionsDefaultToAllOff() {
        val options = DesignOverlayOptions()
        assertFalse(options.showGuides)
        assertFalse(options.showLayoutGrids)
        assertFalse(options.showAnnotations)
        assertFalse(options.anyEnabled)
        assertTrue(DesignOverlayOptions(showGuides = true).anyEnabled)
    }

    // --- Layout grid slices ----------------------------------------------------

    @Test
    fun stretchColumnsDivideMarginInsetExtent() {
        val slices = layoutGridSlices(
            offset = 0.0,
            extent = 1000.0,
            count = 4,
            size = null,
            gutter = 20.0,
            margin = 50.0,
            alignment = LayoutGridAlignment.Stretch,
        )
        assertEquals(4, slices.size)
        assertEquals(listOf(50.0, 280.0, 510.0, 740.0), slices.map { it.start })
        slices.forEach { assertEquals(210.0, it.size, 1e-9) }
        assertEquals(950.0, slices.last().start + slices.last().size, 1e-9)
    }

    @Test
    fun startAlignedColumnsUseFixedSize() {
        val slices = layoutGridSlices(
            offset = 10.0,
            extent = 500.0,
            count = 3,
            size = 60.0,
            gutter = 10.0,
            margin = 20.0,
            alignment = LayoutGridAlignment.Start,
        )
        assertEquals(listOf(30.0, 100.0, 170.0), slices.map { it.start })
        slices.forEach { assertEquals(60.0, it.size) }
    }

    @Test
    fun centerAlignedColumnsIgnoreMargin() {
        val slices = layoutGridSlices(
            offset = 0.0,
            extent = 500.0,
            count = 2,
            size = 100.0,
            gutter = 20.0,
            margin = 40.0,
            alignment = LayoutGridAlignment.Center,
        )
        assertEquals(listOf(140.0, 260.0), slices.map { it.start })
    }

    @Test
    fun endAlignedColumnsAnchorToFarEdge() {
        val slices = layoutGridSlices(
            offset = 0.0,
            extent = 500.0,
            count = 2,
            size = 100.0,
            gutter = 20.0,
            margin = 30.0,
            alignment = LayoutGridAlignment.End,
        )
        assertEquals(listOf(250.0, 370.0), slices.map { it.start })
        assertEquals(470.0, slices.last().start + slices.last().size, 1e-9)
    }

    @Test
    fun gridLinesFallOnEveryStepInsideTheFrame() {
        assertEquals(listOf(25.0, 50.0, 75.0), gridLinePositions(0.0, 100.0, 25.0))
        assertTrue(gridLinePositions(0.0, 20.0, 25.0).isEmpty())
    }

    // --- Focal crop window -------------------------------------------------------

    @Test
    fun fillModeCoversBoxAndClampsFocalShift() {
        val box = RenderRect(0.0, 0.0, 100.0, 100.0)
        val content = mediaContentRect(
            box = box,
            intrinsicWidth = 200.0,
            intrinsicHeight = 100.0,
            fillMode = ImageScaleMode.Fill,
            focalX = 1.0,
            focalY = 0.5,
        )
        // Covers the box at scale 1 (200x100) and clamps so the right edge stays visible.
        assertEquals(RenderRect(-100.0, 0.0, 200.0, 100.0), content)
        val (markerX, markerY) = focalMarker(content, 1.0, 0.5)
        assertEquals(100.0, markerX, 1e-9)
        assertEquals(50.0, markerY, 1e-9)
    }

    @Test
    fun fitModeLetterboxesCentered() {
        val content = mediaContentRect(
            box = RenderRect(0.0, 0.0, 100.0, 100.0),
            intrinsicWidth = 200.0,
            intrinsicHeight = 100.0,
            fillMode = ImageScaleMode.Fit,
            focalX = 0.9,
            focalY = 0.9,
        )
        assertEquals(RenderRect(0.0, 25.0, 100.0, 50.0), content)
    }

    @Test
    fun stretchModeMapsAssetOntoBoxExactly() {
        val box = RenderRect(10.0, 20.0, 100.0, 40.0)
        val content = mediaContentRect(
            box = box,
            intrinsicWidth = 640.0,
            intrinsicHeight = 480.0,
            fillMode = ImageScaleMode.Stretch,
        )
        assertEquals(box, content)
    }

    @Test
    fun missingIntrinsicSizeFallsBackToBox() {
        val box = RenderRect(0.0, 0.0, 50.0, 50.0)
        assertEquals(box, mediaContentRect(box, null, null, ImageScaleMode.Fill))
        assertEquals(box, mediaContentRect(box, 0.0, 100.0, ImageScaleMode.Crop))
    }

    // --- Checker tiles --------------------------------------------------------------

    @Test
    fun checkerCellsAlternateAndClipToBounds() {
        val cells = checkerDarkCells(RenderRect(0.0, 0.0, 20.0, 10.0), cell = 8.0)
        // 3x2 lattice, dark where (row + column) is even: (0,0), (0,2), (1,1).
        assertEquals(3, cells.size)
        assertEquals(RenderRect(0.0, 0.0, 8.0, 8.0), cells[0])
        assertEquals(RenderRect(16.0, 0.0, 4.0, 8.0), cells[1])
        assertEquals(RenderRect(8.0, 8.0, 8.0, 2.0), cells[2])
    }

    // --- Table hairlines --------------------------------------------------------------

    @Test
    fun interiorBoundariesDedupeEdgesAndCenterInGutter() {
        val boundaries = interiorTrackBoundaries(
            cellStarts = listOf(0.0, 0.0, 120.0, 120.3, 240.0),
            contentStart = 0.0,
            gap = 16.0,
        )
        assertEquals(listOf(112.0, 232.0), boundaries)
    }

    // --- Mask geometry ---------------------------------------------------------------

    @Test
    fun maskShapeFollowsNodeGeometry() {
        assertEquals(MaskShape.RoundedRect, maskShapeFor(node("frame")))
        assertEquals(
            MaskShape.RoundedRect,
            maskShapeFor(node("rect", shape = DesignNodeKind.Shape(ShapeType.Rectangle))),
        )
        assertEquals(
            MaskShape.Ellipse,
            maskShapeFor(node("oval", shape = DesignNodeKind.Shape(ShapeType.Ellipse))),
        )
        assertEquals(
            MaskShape.VectorPath,
            maskShapeFor(
                node(
                    "vec",
                    shape = DesignNodeKind.Shape(
                        ShapeType.Vector,
                        paths = listOf(VectorPath(d = "M0 0 L10 10 Z")),
                    ),
                    // resolve lowers inline paths into geometry; maskShapeFor keys off that.
                    geometry = PathGeometry(
                        listOf(
                            PathCommand.MoveTo(0.0, 0.0),
                            PathCommand.LineTo(10.0, 10.0),
                            PathCommand.Close,
                        ),
                    ),
                ),
            ),
        )
        assertEquals(
            MaskShape.BoundingBox,
            maskShapeFor(node("vec-empty", shape = DesignNodeKind.Shape(ShapeType.Vector))),
        )
        assertEquals(
            MaskShape.BoundingBox,
            maskShapeFor(node("star", shape = DesignNodeKind.Shape(ShapeType.Star))),
        )
    }

    @Test
    fun emptyAppliesToMasksFollowingSiblingsOnly() {
        val mask = box(node("mask", mask = ResolvedMask(MaskType.Alpha)))
        val before = box(node("before"))
        val after = box(node("after"))
        assertFalse(maskAppliesTo(mask, maskIndex = 1, sibling = before, siblingIndex = 0))
        assertTrue(maskAppliesTo(mask, maskIndex = 1, sibling = after, siblingIndex = 2))
    }

    @Test
    fun explicitAppliesToMasksListedIdsRegardlessOfOrder() {
        val mask = box(node("mask", mask = ResolvedMask(MaskType.Luminance, appliesTo = listOf("b"))))
        val target = box(node("b"))
        val other = box(node("c"))
        assertTrue(maskAppliesTo(mask, maskIndex = 2, sibling = target, siblingIndex = 0))
        assertFalse(maskAppliesTo(mask, maskIndex = 2, sibling = other, siblingIndex = 3))
    }

    // --- Interaction hit-testing --------------------------------------------------------

    @Test
    fun clickableInteractionWalksUpToNearestClickableAncestor() {
        val click = ResolvedInteraction(trigger = InteractionTrigger.OnClick)
        val leaf = box(node("leaf"))
        val button = box(node("button", interactions = listOf(click)), children = listOf(leaf))
        val root = box(node("root"), children = listOf(button))

        val hit = clickableInteractionAt(root, leaf)
        assertEquals(click, hit?.first)
        assertEquals("button", hit?.second?.node?.id)
    }

    @Test
    fun tapsWithoutClickLikeInteractionResolveToNull() {
        val hoverOnly = ResolvedInteraction(trigger = InteractionTrigger.OnHover)
        val leaf = box(node("leaf", interactions = listOf(hoverOnly)))
        val root = box(node("root"), children = listOf(leaf))
        assertNull(clickableInteractionAt(root, leaf))
    }

    @Test
    fun rootDocumentOriginTranslatesRootAndChildren() {
        val child = LayoutBox(
            node = node("child"),
            x = 10.0,
            y = 20.0,
            width = 30.0,
            height = 40.0,
        )
        val root = LayoutBox(
            node = node("root", position = DesignPoint(72.0, 32.0)),
            x = 0.0,
            y = 0.0,
            width = 100.0,
            height = 100.0,
            children = listOf(child),
        )

        val shifted = root.withRootDocumentOrigin()

        assertEquals(72.0, shifted.x)
        assertEquals(32.0, shifted.y)
        assertEquals(82.0, shifted.children.single().x)
        assertEquals(52.0, shifted.children.single().y)
    }

    // --- Helpers -----------------------------------------------------------------------

    private fun node(
        id: String,
        position: DesignPoint? = null,
        shape: DesignNodeKind.Shape? = null,
        mask: ResolvedMask? = null,
        interactions: List<ResolvedInteraction> = emptyList(),
        geometry: PathGeometry? = null,
    ): ResolvedNode =
        ResolvedNode(
            id = id,
            sourceId = id,
            type = "frame",
            name = id,
            position = position,
            shape = shape,
            mask = mask,
            interactions = interactions,
            geometry = geometry,
        )

    private fun box(node: ResolvedNode, children: List<LayoutBox> = emptyList()): LayoutBox =
        LayoutBox(node = node, x = 0.0, y = 0.0, width = 100.0, height = 100.0, children = children)
}
