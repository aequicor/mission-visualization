package io.aequicor.visualization.editor.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MarqueeSelectionTest {

    @Test
    fun marqueeRectNormalizesDraggingInAnyDirection() {
        val rect = DocumentRect.fromCorners(100.0, 120.0, 20.0, 40.0)

        assertEquals(DocumentRect(left = 20.0, top = 40.0, right = 100.0, bottom = 120.0), rect)
    }

    @Test
    fun selectsOnlyVisibleUnlockedIntersectingBounds() {
        val marquee = DocumentRect.fromCorners(0.0, 0.0, 100.0, 100.0)
        val result = marqueeSelection(
            marquee,
            listOf(
                SelectableBounds("inside", DocumentRect.fromCorners(10.0, 10.0, 20.0, 20.0)),
                SelectableBounds("overlap", DocumentRect.fromCorners(90.0, 90.0, 120.0, 120.0)),
                SelectableBounds("locked", DocumentRect.fromCorners(10.0, 10.0, 20.0, 20.0), locked = true),
                SelectableBounds("hidden", DocumentRect.fromCorners(10.0, 10.0, 20.0, 20.0), visible = false),
                SelectableBounds("outside", DocumentRect.fromCorners(120.0, 120.0, 140.0, 140.0)),
            ),
        )

        assertEquals(setOf("inside", "overlap"), result)
    }

    @Test
    fun edgeTouchWithoutAreaDoesNotSelect() {
        val marquee = DocumentRect.fromCorners(0.0, 0.0, 100.0, 100.0)

        assertTrue(
            marqueeSelection(
                marquee,
                listOf(SelectableBounds("touching", DocumentRect.fromCorners(100.0, 40.0, 120.0, 60.0))),
            ).isEmpty(),
        )
    }
}
