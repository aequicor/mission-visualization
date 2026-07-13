package io.aequicor.visualization.editor.ui

import io.aequicor.visualization.editor.presentation.CanvasOperation
import io.aequicor.visualization.editor.presentation.CornerRadiusHandle
import io.aequicor.visualization.editor.presentation.EditorTool
import io.aequicor.visualization.editor.presentation.ResizeHandle
import kotlin.test.Test
import kotlin.test.assertEquals

class CanvasOperationResolutionTest {

    @Test
    fun modifierMarqueeWinsOverNodeAndSelectionHandles() {
        assertEquals(
            CanvasOperation.Marquee,
            resolveCanvasOperation(
                tool = EditorTool.Select,
                forcePan = false,
                forceMarquee = true,
                radiusHandle = CornerRadiusHandle.TopLeft,
                handle = ResizeHandle.TopLeft,
                rotateHit = true,
                hitId = "nested-node",
            ),
        )
    }

    @Test
    fun forcedPanStillWinsOverModifierMarquee() {
        assertEquals(
            CanvasOperation.Pan,
            resolveCanvasOperation(
                tool = EditorTool.Select,
                forcePan = true,
                forceMarquee = true,
                radiusHandle = null,
                handle = null,
                rotateHit = false,
                hitId = "nested-node",
            ),
        )
    }

    @Test
    fun regularPressOnNodeStillMoves() {
        assertEquals(
            CanvasOperation.Move,
            resolveCanvasOperation(
                tool = EditorTool.Select,
                forcePan = false,
                forceMarquee = false,
                radiusHandle = null,
                handle = null,
                rotateHit = false,
                hitId = "nested-node",
            ),
        )
    }

    @Test
    fun modifierMarqueeReplacesPreviouslySelectedContainer() {
        assertEquals(
            setOf("child-a", "child-b"),
            marqueeSelectionResult(
                existing = setOf("container"),
                hits = setOf("child-a", "child-b"),
                additive = false,
            ),
        )
    }

    @Test
    fun shiftModifierAddsMarqueeHitsToSelection() {
        assertEquals(
            setOf("container", "child"),
            marqueeSelectionResult(
                existing = setOf("container"),
                hits = setOf("child"),
                additive = true,
            ),
        )
    }
}
